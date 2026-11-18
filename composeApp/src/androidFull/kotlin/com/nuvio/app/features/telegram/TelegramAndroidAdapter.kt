package com.nuvio.app.features.telegram

import android.content.Context
import android.util.Log
import com.nuvio.app.features.streams.StreamBehaviorHints
import com.nuvio.app.features.streams.StreamItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.drinkless.tdlib.TdApi
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

internal class TelegramAndroidAdapter(context: Context) : TelegramPlatformAdapter {
    companion object {
        private const val TAG = "NuvioTelegram"
        private const val PREFS = "telegram_private_settings"
        private const val API_ID = "api_id"
        private const val API_HASH = "api_hash"
        private const val SELECTED_CHAT_IDS = "selected_chat_ids"
        private const val CHAT_LIMIT = 200
        private const val SEARCH_LIMIT = 40
        private const val MAX_SEARCH_PAGES = 3
        private const val MAX_PARALLEL_QUERIES = 2
        private const val INDEX_BATCH_SIZE = 100
        private const val INDEX_MAX_BATCHES_PER_RUN = 50
        private const val INDEX_FRESH_MS = 5 * 60 * 1000L
        private const val CACHE_TTL_MS = 5 * 60 * 1000L
        private const val CACHE_MAX_ENTRIES = 96
    }

    private data class CachedLookup(val expiresAt: Long, val streams: List<StreamItem>)

    private data class ChatSearchResult(
        val candidates: List<VideoCandidate>,
        val complete: Boolean,
    )

    private data class VideoCandidate(
        val chatId: Long,
        val messageId: Long,
        val fileId: Int,
        val fileName: String,
        val fileSize: Long,
        val mimeType: String,
        val caption: String,
        val durationSeconds: Int = 0,
        val width: Int = 0,
        val height: Int = 0,
        val hasAudioTag: Boolean = false,
        val hasSubtitleTag: Boolean = false,
    )

    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val cacheDirectory = File(appContext.filesDir, "telegram/files")
    private val tdClient = TelegramTdClient(
        context = appContext,
        apiIdProvider = { preferences.getInt(API_ID, 0) },
        apiHashProvider = { preferences.getString(API_HASH, "").orEmpty() },
    )
    private val proxy = TelegramStreamingProxy(appContext, tdClient)
    private val mediaIndex = TelegramMediaIndex(appContext)
    private val lookupCache = ConcurrentHashMap<String, CachedLookup>()
    private val lookupLocks = ConcurrentHashMap<String, Mutex>()
    private val _snapshot = MutableStateFlow(buildInitialSnapshot())
    override val snapshot: StateFlow<TelegramIntegrationSnapshot> = _snapshot.asStateFlow()

    init {
        // The loopback server starts lazily when a Telegram stream URL is registered.
        // TDLib is initialized once by the bootstrap after the adapter is registered.
        tdClient.authState.collectInBackground()
    }

    private fun buildInitialSnapshot(): TelegramIntegrationSnapshot = TelegramIntegrationSnapshot(
        configured = preferences.getInt(API_ID, 0) > 0 &&
            preferences.getString(API_HASH, "").orEmpty().matches(Regex("[a-fA-F0-9]{32}")),
        authenticated = false,
        authState = "Idle",
        selectedChatIds = readSelectedChatIds(),
        cacheSizeBytes = calculateCacheSize(),
        indexedFileCount = mediaIndex.count(),
        indexStatus = if (mediaIndex.count() > 0) "Index available" else "Not indexed yet",
    )

    private fun kotlinx.coroutines.flow.StateFlow<TelegramTdAuthState>.collectInBackground() {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO).launch {
            collect { state -> publishAuthState(state) }
        }
    }

    private fun publishAuthState(state: TelegramTdAuthState) {
        val current = _snapshot.value
        val next = when (state) {
            TelegramTdAuthState.Idle -> current.copy(authState = "Idle", authenticated = false, accountName = null, qrLink = null)
            TelegramTdAuthState.Initializing -> current.copy(authState = "Connecting", error = null, isBusy = true)
            TelegramTdAuthState.WaitPhone -> current.copy(authState = "WaitPhone", authenticated = false, qrLink = null, error = null, isBusy = false)
            is TelegramTdAuthState.WaitCode -> current.copy(authState = "WaitCode", authenticated = false, qrLink = null, error = null, isBusy = false)
            TelegramTdAuthState.WaitPassword -> current.copy(authState = "WaitPassword", authenticated = false, qrLink = null, error = null, isBusy = false)
            is TelegramTdAuthState.WaitQr -> current.copy(authState = "WaitQr", authenticated = false, qrLink = state.link, error = null, isBusy = false)
            is TelegramTdAuthState.Ready -> current.copy(authState = "Ready", authenticated = true, accountName = state.firstName, qrLink = null, error = null, isBusy = false)
            is TelegramTdAuthState.Failed -> current.copy(authState = "Error", authenticated = false, qrLink = null, error = state.message, isBusy = false)
        }
        _snapshot.value = next.copy(
            configured = preferences.getInt(API_ID, 0) > 0 &&
                preferences.getString(API_HASH, "").orEmpty().matches(Regex("[a-fA-F0-9]{32}")),
            selectedChatIds = readSelectedChatIds(),
            cacheSizeBytes = calculateCacheSize(),
        )
        if (state is TelegramTdAuthState.Ready) {
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO).launch {
                refreshChats()
            }
        }
    }

    override fun initialize() {
        val configured = preferences.getInt(API_ID, 0) > 0 &&
            preferences.getString(API_HASH, "").orEmpty().matches(Regex("[a-fA-F0-9]{32}"))
        _snapshot.value = _snapshot.value.copy(
            configured = configured,
            authState = if (configured) _snapshot.value.authState else "Idle",
            authenticated = if (configured) _snapshot.value.authenticated else false,
            error = if (configured) _snapshot.value.error else null,
            isBusy = if (configured) _snapshot.value.isBusy else false,
        )
        if (configured) tdClient.initialize()
    }

    override suspend fun configureAndConnect(apiId: String, apiHash: String): Boolean {
        val parsedId = apiId.trim().toIntOrNull()?.takeIf { it > 0 } ?: return fail("API ID must be a positive integer.")
        val cleanHash = apiHash.trim()
        if (!cleanHash.matches(Regex("[a-fA-F0-9]{32}"))) {
            return fail("API hash must contain exactly 32 hexadecimal characters.")
        }
        preferences.edit().putInt(API_ID, parsedId).putString(API_HASH, cleanHash).apply()
        lookupCache.clear()
        tdClient.resetAndWait()
        _snapshot.value = _snapshot.value.copy(
            configured = true,
            authState = "Connecting",
            authenticated = false,
            error = null,
            isBusy = true,
        )
        tdClient.initialize()
        return true
    }

    override suspend fun signInWithPhone(phone: String) {
        val value = phone.trim()
        if (value.isBlank()) {
            fail("Enter your phone number with country code.")
            return
        }
        runAuthAction { tdClient.submitPhone(value) }
    }

    override suspend fun signInWithCode(code: String) {
        val value = code.trim()
        if (value.isBlank()) {
            fail("Enter the Telegram login code.")
            return
        }
        runAuthAction { tdClient.submitCode(value) }
    }

    override suspend fun signInWithPassword(password: String) {
        if (password.isBlank()) {
            fail("Enter your Telegram two-step verification password.")
            return
        }
        runAuthAction { tdClient.submitPassword(password) }
    }

    override suspend fun signInWithBotToken(token: String) {
        val value = token.trim()
        if (value.isBlank()) {
            fail("Enter a bot token.")
            return
        }
        runAuthAction { tdClient.submitBotToken(value) }
    }

    override suspend fun requestQrLogin() {
        if (!snapshot.value.configured) {
            fail("Configure your Telegram API ID and hash first.")
            return
        }
        runAuthAction { tdClient.requestQrCode() }
    }

    private suspend fun runAuthAction(action: suspend () -> Unit) {
        try {
            action()
            _snapshot.value = _snapshot.value.copy(error = null)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.w(TAG, "Telegram authentication action failed", error)
            _snapshot.value = _snapshot.value.copy(
                error = error.message ?: "Telegram authentication failed. Try again.",
                isBusy = false,
            )
        }
    }

    override suspend fun refreshChats() {
        if (!tdClient.isAuthenticated()) return
        _snapshot.value = _snapshot.value.copy(isBusy = true, error = null)
        try {
            val response = tdClient.sendRequest(TdApi.GetChats().also { it.limit = CHAT_LIMIT }) as? TdApi.Chats
            val selected = readSelectedChatIds()
            // Preserve explicitly selected sources even when they are outside the first chat-list page.
            val listedChatIds = (response?.chatIds?.toList().orEmpty() + selected).distinct()
            val chatList = listedChatIds.mapNotNull { chatId ->
                val chat = tdClient.sendRequest(TdApi.GetChat(chatId)) as? TdApi.Chat ?: return@mapNotNull null
                val isChannel = when (val kind = chat.type) {
                    is TdApi.ChatTypeSupergroup -> kind.isChannel
                    is TdApi.ChatTypeBasicGroup -> false
                    else -> return@mapNotNull null
                }
                TelegramChatSource(
                    id = chat.id,
                    title = chat.title,
                    username = null,
                    isChannel = isChannel,
                    isSelected = chat.id in selected,
                )
            }.sortedBy { it.title.lowercase(Locale.ROOT) }
            _snapshot.value = _snapshot.value.copy(
                chats = chatList,
                selectedChatIds = selected,
                isBusy = false,
                cacheSizeBytes = calculateCacheSize(),
                error = null,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.w(TAG, "Could not load Telegram chats", error)
            _snapshot.value = _snapshot.value.copy(isBusy = false, error = error.message ?: "Could not load Telegram chats.")
        }
    }

    override suspend fun addChatSource(identifier: String): Boolean {
        if (!tdClient.isAuthenticated()) return fail("Connect Telegram before adding a channel.")
        val raw = identifier.trim()
        if (raw.isBlank()) return fail("Enter a Telegram channel username or chat ID.")
        val chat = try {
            val cleanLink = when {
                raw.startsWith("https://t.me/", ignoreCase = true) -> raw
                raw.startsWith("http://t.me/", ignoreCase = true) -> raw.replaceFirst("http://", "https://")
                raw.startsWith("t.me/", ignoreCase = true) -> "https://$raw"
                raw.startsWith("+") -> "https://t.me/$raw"
                else -> null
            }
            val inviteLink = cleanLink?.takeIf {
                it.contains("/+") || it.contains("/joinchat/")
            }
            if (inviteLink != null) {
                // Invite links are joined only after the user explicitly adds them in this screen.
                tdClient.sendRequest(TdApi.JoinChatByInviteLink(inviteLink)) as? TdApi.Chat
            } else {
                val cleanPath = cleanLink?.removePrefix("https://t.me/") ?: raw
                val isPrivateLink = cleanPath.startsWith("c/")
                val normalized = cleanPath
                    .removePrefix("c/")
                    .removePrefix("@")
                    .substringBefore('/')
                    .trim()
                val numericId = if (isPrivateLink) {
                    normalized.toLongOrNull()?.let { "-100$it".toLongOrNull() }
                } else {
                    normalized.toLongOrNull()
                }
                if (numericId != null) {
                    tdClient.sendRequest(TdApi.GetChat(numericId)) as? TdApi.Chat
                } else {
                    tdClient.sendRequest(TdApi.SearchPublicChat(normalized)) as? TdApi.Chat
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            return fail(error.message ?: "Telegram could not find that chat.")
        } ?: return fail("Telegram could not resolve that channel. Join it in Telegram first, then retry.")

        when (chat.type) {
            is TdApi.ChatTypeSupergroup, is TdApi.ChatTypeBasicGroup -> Unit
            else -> return fail("Only Telegram groups and channels can be added as stream sources.")
        }
        val selected = readSelectedChatIds() + chat.id
        saveSelectedChatIds(selected)
        refreshChats()
        return true
    }

    override suspend fun toggleChatSource(chatId: Long, selected: Boolean) {
        val ids = readSelectedChatIds().toMutableSet()
        if (selected) ids += chatId else ids -= chatId
        saveSelectedChatIds(ids)
        _snapshot.value = _snapshot.value.copy(
            selectedChatIds = ids,
            chats = _snapshot.value.chats.map { it.copy(isSelected = it.id in ids) },
        )
        lookupCache.clear()
    }

    /**
     * Resumable on-device indexing inspired by Telegram-Stremio's scanner. This Android version
     * never sends probe messages or modifies channel content; it stores only media metadata.
     */
    override fun indexedMedia(): List<TelegramLibraryEntry> =
        mediaIndex.entries(readSelectedChatIds()).map { entry ->
            TelegramLibraryEntry(
                chatId = entry.chatId,
                messageId = entry.messageId,
                fileName = entry.fileName,
                fileSize = entry.fileSize,
                mimeType = entry.mimeType,
                caption = entry.caption,
            )
        }

    override suspend fun indexSelectedChats() {
        if (!tdClient.isAuthenticated()) {
            fail("Connect Telegram before indexing channels.")
            return
        }
        val selected = readSelectedChatIds()
        if (selected.isEmpty()) {
            fail("Select at least one Telegram channel or group before indexing.")
            return
        }
        _snapshot.value = _snapshot.value.copy(isBusy = true, error = null, indexStatus = "Preparing index…")
        try {
            var processed = 0
            var hasPendingChannels = false
            for (chatId in selected) {
                val recent = tdClient.sendRequest(
                    TdApi.GetChatHistory().also { request ->
                        request.chatId = chatId
                        request.fromMessageId = 0L
                        request.offset = 0
                        request.limit = INDEX_BATCH_SIZE
                        request.onlyLocal = false
                    },
                    timeoutMs = 30_000L,
                ) as? TdApi.Messages ?: throw IllegalStateException("Telegram did not return recent history for chat $chatId.")
                val recentMessages = recent.messages.filterNotNull()
                mediaIndex.upsert(recentMessages.mapNotNull(::extractCandidate).map(::toIndexedMedia), prepend = true)
                mediaIndex.touch()

                var cursor = mediaIndex.cursor(chatId)
                if (cursor == -1L) {
                    _snapshot.value = _snapshot.value.copy(
                        indexedFileCount = mediaIndex.count(),
                        indexStatus = "Updated recent posts in selected channels",
                    )
                    continue
                }
                if (cursor == 0L) cursor = recentMessages.minOfOrNull { it.id } ?: 0L

                var batches = 0
                var finished = false
                while (batches < INDEX_MAX_BATCHES_PER_RUN && !finished) {
                    val page = tdClient.sendRequest(
                        TdApi.GetChatHistory().also { request ->
                            request.chatId = chatId
                            request.fromMessageId = cursor
                            request.offset = 0
                            request.limit = INDEX_BATCH_SIZE
                            request.onlyLocal = false
                        },
                        timeoutMs = 30_000L,
                    ) as? TdApi.Messages ?: throw IllegalStateException("Telegram history request failed for chat $chatId.")
                    if (page.messages.isEmpty()) {
                        mediaIndex.setCursor(chatId, -1L)
                        finished = true
                        break
                    }
                    val historyMessages = page.messages.filterNotNull()
                    if (historyMessages.isEmpty()) {
                        hasPendingChannels = true
                        _snapshot.value = _snapshot.value.copy(
                            indexStatus = "Paused safely: Telegram returned unavailable messages for channel " + chatId,
                        )
                        finished = true
                        break
                    }
                    val oldestId = historyMessages.minOf { it.id }
                    mediaIndex.upsert(historyMessages.mapNotNull(::extractCandidate).map(::toIndexedMedia))
                    if (oldestId <= 0L || oldestId == cursor) {
                        // TDLib may return a short page before the end of history. Never mark
                        // the channel complete unless a subsequent request explicitly returns empty.
                        hasPendingChannels = true
                        _snapshot.value = _snapshot.value.copy(
                            indexStatus = "Paused safely: Telegram returned no older page for channel " + chatId,
                        )
                        finished = true
                    } else {
                        cursor = oldestId
                        mediaIndex.setCursor(chatId, cursor)
                    }
                    processed += historyMessages.size
                    batches += 1
                    _snapshot.value = _snapshot.value.copy(
                        indexedFileCount = mediaIndex.count(),
                        indexStatus = "Indexed " + processed + " messages; channel " + chatId,
                    )
                }
                if (!finished) {
                    hasPendingChannels = true
                    _snapshot.value = _snapshot.value.copy(
                        indexStatus = "Paused safely at channel " + chatId + "; tap index again to continue",
                    )
                }
            }
            _snapshot.value = _snapshot.value.copy(
                indexedFileCount = mediaIndex.count(),
                indexStatus = if (hasPendingChannels) "Index partially complete; tap index again to continue" else "Index updated successfully",
                isBusy = false,
                error = null,
            )
            lookupCache.clear()
        } catch (cancelled: CancellationException) {
            _snapshot.value = _snapshot.value.copy(
                indexedFileCount = mediaIndex.count(),
                indexStatus = "Index paused; progress saved",
                isBusy = false,
            )
            throw cancelled
        } catch (error: Exception) {
            Log.w(TAG, "Telegram media indexing failed", error)
            _snapshot.value = _snapshot.value.copy(
                indexedFileCount = mediaIndex.count(),
                indexStatus = "Index incomplete; progress saved",
                isBusy = false,
                error = error.message ?: "Telegram indexing failed.",
            )
        }
    }

    private fun toIndexedMedia(candidate: VideoCandidate) = TelegramIndexedMedia(
        chatId = candidate.chatId,
        messageId = candidate.messageId,
        fileId = candidate.fileId,
        fileName = candidate.fileName,
        fileSize = candidate.fileSize,
        mimeType = candidate.mimeType,
        caption = candidate.caption,
        durationSeconds = candidate.durationSeconds,
        width = candidate.width,
        height = candidate.height,
        hasAudioTag = candidate.hasAudioTag,
        hasSubtitleTag = candidate.hasSubtitleTag,
    )

    private fun TelegramIndexedMedia.toCandidate() = VideoCandidate(
        chatId = chatId,
        messageId = messageId,
        fileId = fileId,
        fileName = fileName,
        fileSize = fileSize,
        mimeType = mimeType,
        caption = caption,
        durationSeconds = durationSeconds,
        width = width,
        height = height,
        hasAudioTag = hasAudioTag,
        hasSubtitleTag = hasSubtitleTag,
    )

    override suspend fun clearCache() {
        // Evict only TDLib file IDs known to this proxy; never touch Nuvio downloads or account data.
        proxy.clearCache()
        lookupCache.clear()
        _snapshot.value = _snapshot.value.copy(cacheSizeBytes = calculateCacheSize())
    }

    override suspend fun disconnect() {
        tdClient.resetAndWait(logOut = true)
        proxy.stop()
        proxy.forgetRegisteredFiles()
        lookupCache.clear()
        mediaIndex.clear()
        _snapshot.value = _snapshot.value.copy(
            indexedFileCount = 0,
            indexStatus = "Not indexed yet",
            authenticated = false,
            authState = "Idle",
            accountName = null,
            qrLink = null,
            chats = emptyList(),
            selectedChatIds = emptySet(),
            error = null,
            isBusy = false,
        )
        withContext(Dispatchers.IO) {
            File(appContext.filesDir, "telegram/session_ready").delete()
            File(appContext.filesDir, "telegram/tdlib").deleteRecursively()
            cacheDirectory.deleteRecursively()
        }
        saveSelectedChatIds(emptySet())
    }

    override suspend fun resolveStreams(
        type: String,
        videoId: String,
        title: String,
        year: Int?,
        season: Int?,
        episode: Int?,
        imdbId: String?,
    ): List<StreamItem> {
        if (!tdClient.isAuthenticated() || title.isBlank()) return emptyList()
        val selectedChatIds = readSelectedChatIds()
        if (selectedChatIds.isEmpty()) return emptyList()

        val normalizedType = normalizeMediaType(type)
        val key = listOf(
            normalizedType,
            videoId,
            imdbId.orEmpty(),
            title.lowercase(Locale.ROOT),
            year?.toString().orEmpty(),
            season?.toString().orEmpty(),
            episode?.toString().orEmpty(),
            selectedChatIds.sorted().joinToString(","),
        ).joinToString("|")
        val lookupLock = lookupLocks.getOrPut(key) { Mutex() }
        return lookupLock.withLock {
            lookupCache[key]?.takeIf { System.currentTimeMillis() < it.expiresAt }?.let { return@withLock it.streams }

        val indexedCandidates = mediaIndex.entries(selectedChatIds).map { it.toCandidate() }
        val indexedMatches = indexedCandidates.filter { candidate ->
            matchCandidate(candidate, title, year, season, episode, normalizedType) > 0
        }
        // Prefer a recently refreshed, complete local index. A stale or partial index is combined
        // with live Telegram search so new uploads and not-yet-scanned history remain discoverable.
        val indexComplete = selectedChatIds.all { mediaIndex.isComplete(it) }
        val indexFresh = mediaIndex.isFresh(INDEX_FRESH_MS)
        val (candidates, searchComplete) = if (indexComplete && indexFresh && indexedMatches.isNotEmpty()) {
            indexedMatches to true
        } else {
            val queries = buildQueries(title, year, season, episode, normalizedType)
            val semaphore = Semaphore(permits = MAX_PARALLEL_QUERIES)
            suspend fun searchBatch(batch: List<String>): List<ChatSearchResult> = coroutineScope {
                batch.map { query ->
                    async(Dispatchers.IO) {
                        semaphore.withPermit { searchQuery(query, selectedChatIds) }
                    }
                }.map { deferred -> deferred.await() }
            }
            val coreQueries = queries.take(2)
            val fallbackQueries = queries.drop(2)
            val coreResults = searchBatch(coreQueries)
            val coreCandidates = coreResults.flatMap(ChatSearchResult::candidates)
            val coreHasMatch = coreCandidates.any { candidate ->
                matchCandidate(candidate, title, year, season, episode, normalizedType) > 0
            }
            val fallbackResults = if (coreHasMatch) emptyList() else searchBatch(fallbackQueries)
            val usedResults = coreResults + fallbackResults
            (indexedMatches + coreCandidates + fallbackResults.flatMap(ChatSearchResult::candidates)) to
                usedResults.all(ChatSearchResult::complete)
        }

        val matched = candidates
            .distinctBy { "${it.chatId}:${it.messageId}:${it.fileId}" }
            .mapNotNull { candidate ->
                val score = matchCandidate(candidate, title, year, season, episode, normalizedType)
                if (score <= 0) return@mapNotNull null
                candidate to score
            }
            .sortedWith(
                compareByDescending<Pair<VideoCandidate, Int>> { it.second }
                    .thenByDescending { it.first.height }
                    .thenByDescending { it.first.fileSize },
            )
            .map { (candidate, _) -> candidate.toStreamItem() }

        if (searchComplete) {
            lookupCache[key] = CachedLookup(System.currentTimeMillis() + CACHE_TTL_MS, matched)
            if (lookupCache.size > CACHE_MAX_ENTRIES) {
                val oldestKeys = lookupCache.entries.sortedBy { it.value.expiresAt }
                    .take(lookupCache.size - CACHE_MAX_ENTRIES).map { it.key }
                oldestKeys.forEach(lookupCache::remove)
            }
        }
        matched
        }
    }


    private suspend fun searchQuery(query: String, selectedChatIds: Set<Long>): ChatSearchResult {
        val found = linkedMapOf<String, VideoCandidate>()
        var offset = ""
        var page = 0
        var complete = true

        do {
            val response = try {
                tdClient.sendRequest(TdApi.SearchMessages().also { request ->
                    request.chatList = null
                    request.query = query
                    request.offset = offset
                    request.limit = SEARCH_LIMIT
                    request.filter = null
                }, timeoutMs = 20_000L)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w(TAG, "Telegram global search failed for query '" + query + "' (page " + (page + 1) + ")", error)
                return ChatSearchResult(found.values.toList(), complete = false)
            } ?: run {
                Log.w(TAG, "Telegram global search timed out for query '" + query + "' (page " + (page + 1) + ")")
                return ChatSearchResult(found.values.toList(), complete = false)
            }

            val result = response as? TdApi.FoundMessages
                ?: return ChatSearchResult(found.values.toList(), complete = false)

            result.messages.forEach { message ->
                if (message == null) return@forEach
                // Global search spans all chats; never expose or stream a result outside the allowlist.
                if (message.chatId !in selectedChatIds) return@forEach
                extractCandidate(message)?.let { candidate ->
                    found[candidate.chatId.toString() + ":" + candidate.messageId + ":" + candidate.fileId] = candidate
                }
            }

            offset = result.nextOffset
            page += 1
        } while (offset.isNotEmpty() && page < MAX_SEARCH_PAGES)

        if (offset.isNotEmpty()) {
            complete = false
            Log.i(TAG, "Telegram search for '" + query + "' reached the " + MAX_SEARCH_PAGES + "-page safety limit; keeping partial results")
        }
        Log.d(TAG, "Telegram search '" + query + "': " + found.size + " selected-channel files across " + page + " page(s)")
        return ChatSearchResult(found.values.toList(), complete = complete)
    }

    private fun extractCandidate(message: TdApi.Message): VideoCandidate? {
        val caption: String
        val fileName: String
        val fileId: Int
        val fileSize: Long
        val mimeType: String
        var duration = 0
        var width = 0
        var height = 0
        val videoExtensions = setOf("mkv", "mp4", "m4v", "avi", "mov", "webm", "ts", "m2ts", "wmv", "mpg", "mpeg", "3gp")
        fun isVideoName(value: String): Boolean = value.substringAfterLast('.', "").lowercase(Locale.ROOT) in videoExtensions
        fun captionName(value: String): String = value.lineSequence().firstOrNull()?.trim().orEmpty()

        when (val content = message.content) {
            is TdApi.MessageVideo -> {
                val video = content.video
                caption = content.caption.text
                fileName = video.fileName.takeIf(String::isNotBlank)
                    ?: captionName(caption).takeIf(String::isNotBlank)
                    ?: "Telegram_video_" + message.id + ".mp4"
                fileId = video.video.id
                fileSize = video.video.size
                mimeType = video.mimeType
                duration = video.duration
                width = video.width
                height = video.height
            }
            is TdApi.MessageDocument -> {
                val document = content.document
                caption = content.caption.text
                val captionFileName = captionName(caption)
                val originalName = document.fileName
                fileName = when {
                    isVideoName(originalName) -> originalName
                    isVideoName(captionFileName) -> captionFileName
                    document.mimeType.startsWith("video/") || document.mimeType == "application/x-matroska" ->
                        originalName.ifBlank { captionFileName.ifBlank { "Telegram_video_" + message.id + ".mkv" } }
                    else -> originalName
                }
                fileId = document.document.id
                fileSize = document.document.size
                mimeType = document.mimeType
            }
            else -> return null
        }

        if (fileId <= 0 || fileSize <= 0L || fileName.isBlank()) return null
        val videoExtension = isVideoName(fileName)
        if (!mimeType.startsWith("video/") && mimeType != "application/x-matroska" && !videoExtension) return null
        val audioTagged = Regex("(?i)\\b(dual[ ._-]?audio|multi[ ._-]?audio|dubbed|aac|ac3|eac3|dts|truehd|atmos)\\b")
            .containsMatchIn(fileName + " " + caption)
        val subtitleTagged = Regex("(?i)\\b(subs?|subbed|subtitle|softsubs?|hardsubs?|engsub|multisub)\\b")
            .containsMatchIn(fileName + " " + caption)
        return VideoCandidate(
            chatId = message.chatId, messageId = message.id, fileId = fileId,
            fileName = fileName, fileSize = fileSize, mimeType = mimeType, caption = caption,
            durationSeconds = duration, width = width, height = height,
            hasAudioTag = audioTagged, hasSubtitleTag = subtitleTagged,
        )
    }
    private fun VideoCandidate.toStreamItem(): StreamItem {
        val heightValue = max(height, parseHeight(fileName))
        val quality = when {
            heightValue >= 2160 -> "2160p"
            heightValue >= 1440 -> "1440p"
            heightValue >= 1080 -> "1080p"
            heightValue >= 720 -> "720p"
            heightValue >= 480 -> "480p"
            heightValue > 0 -> "${heightValue}p"
            else -> "Unknown quality"
        }
        val codec = Regex("(?i)\\b(x265|h\\.?265|hevc|x264|h\\.?264|av1)\\b")
            .find(fileName)?.value?.uppercase(Locale.ROOT)
        val details = buildList {
            add(quality)
            if (codec != null) add(codec)
            add(humanBytes(fileSize))
            if (durationSeconds > 0) add("${durationSeconds / 60} min")
            if (hasAudioTag) add("Audio tagged")
            if (hasSubtitleTag) add("Subs tagged")
        }.joinToString(" · ")
        return StreamItem(
            name = "[Telegram] $details",
            title = fileName,
            description = details,
            url = proxy.streamUrl(fileId, mimeType),
            sourceName = "Telegram",
            addonName = "Telegram",
            addonId = "telegram_native",
            streamType = "direct",
            behaviorHints = StreamBehaviorHints(
                filename = fileName,
                videoSize = fileSize,
            ),
        )
    }

    private fun matchCandidate(
        candidate: VideoCandidate,
        title: String,
        year: Int?,
        season: Int?,
        episode: Int?,
        type: String,
    ): Int = TelegramSearchMatcher.score(
        fileName = candidate.fileName,
        caption = candidate.caption,
        title = title,
        year = year,
        season = season,
        episode = episode,
        type = type,
    )

    private fun buildQueries(title: String, year: Int?, season: Int?, episode: Int?, type: String): List<String> =
        TelegramSearchMatcher.buildQueries(title, year, season, episode, type)

    private fun normalizeMediaType(type: String): String =
        if (type.equals("tv", true) || type.equals("series", true)) "tv" else "movie"

    private fun parseHeight(fileName: String): Int =
        Regex("(?i)(2160|1440|1080|720|576|480|360)\\s*p")
            .find(fileName)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0

    private fun humanBytes(bytes: Long): String = when {
        bytes >= 1024L * 1024L * 1024L -> String.format(Locale.ROOT, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        bytes >= 1024L * 1024L -> String.format(Locale.ROOT, "%.0f MB", bytes / (1024.0 * 1024.0))
        else -> "${bytes / 1024L} KB"
    }

    private fun readSelectedChatIds(): Set<Long> =
        preferences.getString(SELECTED_CHAT_IDS, "")
            .orEmpty()
            .split(',')
            .mapNotNull(String::toLongOrNull)
            .toSet()

    private fun saveSelectedChatIds(ids: Set<Long>) {
        preferences.edit().putString(SELECTED_CHAT_IDS, ids.sorted().joinToString(",")).apply()
    }

    private fun calculateCacheSize(): Long =
        if (cacheDirectory.exists()) cacheDirectory.walkTopDown().filter(File::isFile).sumOf(File::length) else 0L

    private fun fail(message: String): Boolean {
        _snapshot.value = _snapshot.value.copy(error = message, isBusy = false)
        return false
    }
}
