package com.nuvio.app.features.livetv

import co.touchlab.kermit.Logger
import com.nuvio.app.features.addons.httpGetText
import io.ktor.http.decodeURLQueryComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlin.random.Random
import kotlin.time.Clock

object LiveTvRepository {
    private val log = Logger.withTag("LiveTvRepository")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _uiState = MutableStateFlow(LiveTvUiState())
    val uiState: StateFlow<LiveTvUiState> = _uiState.asStateFlow()

    private var hasLoaded = false
    private var channelsRequested = false
    private var channelsSignature: String? = null
    private var refreshJob: Job? = null
    private var restoredEpg: LiveTvCachedEpg? = null
    private var epgJob: Job? = null
    private var detectedEpgUrls: List<String> = emptyList()
    private val epgGuideCache = mutableMapOf<String, LiveTvCachedEpgGuide>()

    fun ensureLoaded() {
        if (hasLoaded) return
        hasLoaded = true
        val playlists = loadSavedPlaylists()
        _uiState.value = LiveTvUiState(
            playlistUrl = playlists.firstEnabledUrlSource(),
            playlists = playlists,
            stalkerSettings = LiveTvStorage.loadStalkerSettings(),
            xtreamSettings = LiveTvStorage.loadXtreamSettings(),
            favoriteChannelIds = loadFavoriteChannelIds(),
            lastWatchedChannelId = LiveTvStorage.loadLastWatchedChannelId(),
            recentChannelIds = loadRecentChannelIds(),
            isChannelGridEnabled = LiveTvStorage.loadChannelViewMode() == "grid",
            isNavigationEnabled = LiveTvStorage.loadNavigationEnabled() ?: true,
            epgSources = decodeEpgSources(LiveTvStorage.loadEpgSourcesBlob().orEmpty()),
            isEpgAutoDetectEnabled = LiveTvStorage.loadEpgAutoDetectEnabled() ?: true,
        )
        publishNavigationVisibility()
    }

    fun ensureChannelsLoaded() {
        ensureLoaded()
        if (channelsRequested) return
        channelsRequested = true
        val state = _uiState.value
        if (!state.hasPlaylist) return
        val signature = liveTvChannelSourceSignature(state)
        _uiState.value = state.copy(isLoading = true, errorMessage = null)
        refreshJob = scope.launch {
            val cachedChannels = withContext(Dispatchers.Default) { LiveTvCacheStore.loadChannels() }
                ?.takeIf { it.signature == signature && it.channels.isNotEmpty() }
            restoredEpg = withContext(Dispatchers.Default) { LiveTvCacheStore.loadEpg() }
            if (cachedChannels != null && liveTvChannelSourceSignature(_uiState.value) == signature) {
                detectedEpgUrls = cachedChannels.epgUrls
                channelsSignature = signature
                _uiState.value = _uiState.value.copy(
                    channels = cachedChannels.channels,
                    isLoading = false,
                    errorMessage = null,
                )
                refreshEpg(force = false)
                return@launch
            }
            refresh()
        }
    }

    fun savePlaylistUrl(url: String) {
        ensureLoaded()
        val normalized = url.trim()
        val playlists = if (normalized.isBlank()) {
            emptyList()
        } else {
            listOf(createUrlPlaylist(normalized))
        }
        persistPlaylists(playlists)
        _uiState.value = _uiState.value.copy(
            playlistUrl = playlists.firstEnabledUrlSource(),
            playlists = playlists,
            channels = emptyList(),
            isLoading = false,
            errorMessage = null,
        )
        publishNavigationVisibility()
        if (playlists.isNotEmpty()) {
            refresh()
        }
    }

    fun addPlaylistUrl(url: String) {
        addPlaylistUrl(name = null, url = url)
    }

    fun addPlaylistUrl(name: String?, url: String) {
        ensureLoaded()
        val normalized = url.trim()
        if (normalized.isBlank()) return

        val current = _uiState.value.playlists
        if (current.any { it.type == LiveTvPlaylistType.Url && it.source.equals(normalized, ignoreCase = true) }) {
            return
        }

        val playlists = current + createUrlPlaylist(normalized, name)
        persistPlaylists(playlists)
        _uiState.value = _uiState.value.copy(
            playlistUrl = playlists.firstEnabledUrlSource(),
            playlists = playlists,
            errorMessage = null,
        )
        publishNavigationVisibility()
        refresh()
    }

    fun addLocalPlaylist(fileName: String?, content: String) {
        addLocalPlaylist(name = null, fileName = fileName, content = content)
    }

    fun addLocalPlaylist(name: String?, fileName: String?, content: String) {
        ensureLoaded()
        val normalizedContent = content.trim()
        if (normalizedContent.isBlank()) return

        val fallbackName = name?.trim()?.takeIf(String::isNotBlank)
            ?: fileName
                ?.let { file -> file.substringBeforeLast('.', missingDelimiterValue = file) }
                ?.trim()
                ?.takeIf(String::isNotBlank)
            ?: "Local playlist"
        val playlist = LiveTvPlaylist(
            id = stablePlaylistId("local:${fallbackName}:${normalizedContent.hashCode()}:${Random.nextInt()}", _uiState.value.playlists.size),
            name = fallbackName,
            type = LiveTvPlaylistType.LocalFile,
            source = normalizedContent,
            isEnabled = true,
        )
        val playlists = _uiState.value.playlists + playlist
        persistPlaylists(playlists)
        _uiState.value = _uiState.value.copy(
            playlistUrl = playlists.firstEnabledUrlSource(),
            playlists = playlists,
            errorMessage = null,
        )
        publishNavigationVisibility()
        refresh()
    }

    fun updatePlaylist(playlistId: String, name: String, source: String) {
        ensureLoaded()
        val current = _uiState.value.playlists
        val existing = current.firstOrNull { it.id == playlistId } ?: return
        val normalizedName = name.trim().ifBlank { existing.name }
        val normalizedSource = source.trim()
        if (normalizedSource.isBlank()) return
        if (existing.type == LiveTvPlaylistType.Url && current.any {
                it.id != playlistId &&
                    it.type == LiveTvPlaylistType.Url &&
                    it.source.equals(normalizedSource, ignoreCase = true)
            }
        ) {
            return
        }

        val playlists = current.map { playlist ->
            if (playlist.id == playlistId) {
                playlist.copy(
                    name = normalizedName,
                    source = normalizedSource,
                )
            } else {
                playlist
            }
        }
        persistPlaylists(playlists)
        _uiState.value = _uiState.value.copy(
            playlistUrl = playlists.firstEnabledUrlSource(),
            playlists = playlists,
            errorMessage = null,
        )
        publishNavigationVisibility()
        refresh()
    }

    fun removePlaylist(playlistId: String) {
        ensureLoaded()
        val playlists = _uiState.value.playlists.filterNot { it.id == playlistId }
        persistPlaylists(playlists)
        _uiState.value = _uiState.value.copy(
            playlistUrl = playlists.firstEnabledUrlSource(),
            playlists = playlists,
            channels = emptyList(),
            isLoading = false,
            errorMessage = null,
        )
        publishNavigationVisibility()
        if (playlists.any { it.isEnabled }) {
            refresh()
        }
    }

    fun setPlaylistEnabled(playlistId: String, isEnabled: Boolean) {
        ensureLoaded()
        val current = _uiState.value.playlists
        if (current.none { it.id == playlistId }) return

        val playlists = current.map { playlist ->
            if (playlist.id == playlistId) {
                playlist.copy(isEnabled = isEnabled)
            } else {
                playlist
            }
        }
        persistPlaylists(playlists)
        _uiState.value = _uiState.value.copy(
            playlistUrl = playlists.firstEnabledUrlSource(),
            playlists = playlists,
            channels = emptyList(),
            isLoading = false,
            errorMessage = null,
        )
        publishNavigationVisibility()
        if (playlists.any { it.isEnabled }) {
            refresh()
        }
    }

    fun setNavigationEnabled(enabled: Boolean) {
        ensureLoaded()
        if (_uiState.value.isNavigationEnabled == enabled) return

        LiveTvStorage.saveNavigationEnabled(enabled)
        _uiState.value = _uiState.value.copy(isNavigationEnabled = enabled)
        publishNavigationVisibility()
    }

    fun saveStalkerSettings(settings: LiveTvStalkerSettings) {
        ensureLoaded()
        val normalized = settings.copy(
            portalUrl = settings.portalUrl.trim().trimEnd('/'),
            macAddress = settings.macAddress.trim().uppercase(),
            username = settings.username.trim(),
            password = settings.password.trim(),
        )
        LiveTvStorage.saveStalkerSettings(normalized)
        _uiState.value = _uiState.value.copy(stalkerSettings = normalized, errorMessage = null)
        publishNavigationVisibility()
        refresh()
    }

    fun saveXtreamSettings(settings: LiveTvXtreamSettings) {
        ensureLoaded()
        val normalized = settings.copy(
            serverUrl = settings.serverUrl.trim().trimEnd('/').substringBefore("/player_api.php").trimEnd('/'),
            username = settings.username.trim(),
            password = settings.password.trim(),
        )
        LiveTvStorage.saveXtreamSettings(normalized)
        _uiState.value = _uiState.value.copy(xtreamSettings = normalized, errorMessage = null)
        publishNavigationVisibility()
        refresh()
    }

    fun removeStalker() = saveStalkerSettings(LiveTvStalkerSettings())
    fun removeXtream() = saveXtreamSettings(LiveTvXtreamSettings())

    fun reloadChannels() {
        refresh()
    }

    fun reloadEpg() {
        refreshEpg(force = true)
    }

    suspend fun prepareForPlayback(channel: LiveTvChannel): LiveTvChannel =
        if (channel.stalkerCommand.isNullOrBlank()) channel
        else preparePortalChannelForPlayback(channel, _uiState.value.stalkerSettings)

    fun refresh() {
        ensureLoaded()
        val currentState = _uiState.value
        val playlists = currentState.playlists
        if (!currentState.hasPlaylist) {
            _uiState.value = _uiState.value.copy(
                playlistUrl = "",
                playlists = emptyList(),
                channels = emptyList(),
                isLoading = false,
                errorMessage = null,
            )
            publishNavigationVisibility()
            return
        }

        val enabledPlaylists = playlists.filter { it.isEnabled }
        val hasEnabledPortal = (currentState.xtreamSettings.isConfigured && currentState.xtreamSettings.isEnabled) ||
            (currentState.stalkerSettings.isConfigured && currentState.stalkerSettings.isEnabled)
        if (enabledPlaylists.isEmpty() && !hasEnabledPortal) {
            _uiState.value = _uiState.value.copy(
                playlistUrl = "",
                playlists = playlists,
                channels = emptyList(),
                isLoading = false,
                errorMessage = null,
            )
            return
        }

        channelsRequested = true
        val signature = liveTvChannelSourceSignature(currentState)
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        refreshJob?.cancel()
        refreshJob = scope.launch {
            val loadedChannels = mutableListOf<LiveTvChannel>()
            val failedPlaylistNames = mutableListOf<String>()
            val playlistEpgUrls = mutableListOf<String>()

            val playlistResults = enabledPlaylists.map { playlist ->
                async {
                    runCatching {
                        val payload = when (playlist.type) {
                            LiveTvPlaylistType.Url -> httpGetText(playlist.source)
                            LiveTvPlaylistType.LocalFile -> playlist.source
                        }
                        withContext(Dispatchers.Default) {
                            parseM3uEpgUrls(payload) to parseM3uPlaylist(payload, playlist)
                        }
                    }
                }
            }
            val xtreamResult = if (currentState.xtreamSettings.isConfigured && currentState.xtreamSettings.isEnabled) {
                async { runCatching { withContext(Dispatchers.Default) { fetchXtreamChannels(currentState.xtreamSettings) } } }
            } else {
                null
            }
            val stalkerResult = if (currentState.stalkerSettings.isConfigured && currentState.stalkerSettings.isEnabled) {
                async { runCatching { withContext(Dispatchers.Default) { fetchStalkerChannels(currentState.stalkerSettings) } } }
            } else {
                null
            }

            enabledPlaylists.zip(playlistResults.awaitAll()).forEach { (playlist, result) ->
                result.fold(
                    onSuccess = { (epgUrls, channels) ->
                        playlistEpgUrls += epgUrls
                        loadedChannels += channels
                    },
                    onFailure = { error ->
                        if (error is CancellationException) throw error
                        failedPlaylistNames += playlist.name
                        log.w(error) { "Failed to load live TV playlist ${playlist.name}" }
                    },
                )
            }

            xtreamResult?.await()?.fold(
                onSuccess = { loadedChannels += it },
                onFailure = { error ->
                    if (error is CancellationException) throw error
                    failedPlaylistNames += "Xtream"
                    log.w(error) { "Failed to load Xtream provider" }
                },
            )
            stalkerResult?.await()?.fold(
                onSuccess = { loadedChannels += it },
                onFailure = { error ->
                    if (error is CancellationException) throw error
                    failedPlaylistNames += "Stalker Portal"
                    log.w(error) { "Failed to load Stalker provider" }
                },
            )

            val fetchedChannels = withContext(Dispatchers.Default) { loadedChannels.distinctBy { it.streamUrl } }
            val previousChannels = _uiState.value.channels
            val keepPrevious = fetchedChannels.isEmpty() &&
                failedPlaylistNames.isNotEmpty() &&
                previousChannels.isNotEmpty() &&
                channelsSignature == signature
            val channels = if (keepPrevious) previousChannels else fetchedChannels
            _uiState.value = _uiState.value.copy(
                playlistUrl = playlists.firstEnabledUrlSource(),
                playlists = playlists,
                channels = channels,
                isLoading = false,
                errorMessage = when {
                    channels.isEmpty() && failedPlaylistNames.isNotEmpty() -> "Playlist could not be loaded."
                    channels.isEmpty() -> "No channels found in these playlists."
                    failedPlaylistNames.isNotEmpty() -> "Some playlists could not be loaded: ${failedPlaylistNames.joinToString()}"
                    else -> null
                },
            )
            if (!keepPrevious) {
                val channelsChanged = previousChannels.isNotEmpty() &&
                    withContext(Dispatchers.Default) { previousChannels != fetchedChannels }
                if (channelsChanged) restoredEpg = null
                detectedEpgUrls = playlistEpgUrls.distinct()
                channelsSignature = signature
                if (fetchedChannels.isNotEmpty()) {
                    val epgUrls = detectedEpgUrls
                    val savedAt = Clock.System.now().toEpochMilliseconds()
                    withContext(Dispatchers.Default) {
                        LiveTvCacheStore.saveChannels(signature, savedAt, epgUrls, fetchedChannels)
                    }
                }
            }
            refreshEpg(force = false)
        }
    }

    fun addEpgSource(name: String?, url: String) {
        ensureLoaded()
        val normalized = url.trim()
        if (normalized.isBlank()) return
        val current = _uiState.value.epgSources
        if (current.any { it.url.equals(normalized, ignoreCase = true) }) return
        val source = LiveTvEpgSource(
            id = stableEpgSourceId(normalized, current.size),
            name = name?.trim()?.takeIf(String::isNotBlank) ?: epgSourceNameFromUrl(normalized),
            url = normalized,
        )
        updateEpgSources(current + source)
    }

    fun removeEpgSource(sourceId: String) {
        ensureLoaded()
        updateEpgSources(_uiState.value.epgSources.filterNot { it.id == sourceId })
    }

    fun setEpgSourceEnabled(sourceId: String, isEnabled: Boolean) {
        ensureLoaded()
        val current = _uiState.value.epgSources
        if (current.none { it.id == sourceId }) return
        updateEpgSources(current.map { source -> if (source.id == sourceId) source.copy(isEnabled = isEnabled) else source })
    }

    fun setEpgAutoDetectEnabled(enabled: Boolean) {
        ensureLoaded()
        if (_uiState.value.isEpgAutoDetectEnabled == enabled) return
        LiveTvStorage.saveEpgAutoDetectEnabled(enabled)
        _uiState.value = _uiState.value.copy(isEpgAutoDetectEnabled = enabled)
        refreshEpg(force = false)
    }

    fun refreshEpg(force: Boolean = true) {
        ensureLoaded()
        val state = _uiState.value
        val requests = buildEpgRequests(state)
        epgJob?.cancel()
        epgGuideCache.keys.retainAll(requests.map { it.cacheKey }.toSet())
        if (state.channels.isEmpty() || requests.isEmpty()) {
            _uiState.value = state.copy(
                epgPrograms = emptyMap(),
                isEpgLoading = false,
                epgErrorMessage = null,
            )
            return
        }

        if (!force && applyRestoredEpg()) return
        val epgSignature = liveTvEpgSignature(state, requests)

        _uiState.value = state.copy(isEpgLoading = true, epgErrorMessage = null)
        epgJob = scope.launch {
            val nowMillis = Clock.System.now().toEpochMilliseconds()
            val guides = mutableListOf<LiveTvEpgGuide>()
            val failedGuideNames = mutableListOf<String>()
            val results = requests.map { request ->
                val cached = epgGuideCache[request.cacheKey]
                    ?.takeIf { !force && nowMillis - it.loadedAtMillis < LIVE_TV_EPG_CACHE_TTL_MILLIS }
                if (cached != null) {
                    async { Result.success(cached.guide) }
                } else {
                    async { runCatching { withContext(Dispatchers.Default) { request.load(nowMillis) } } }
                }
            }.awaitAll()
            requests.zip(results).forEach { (request, result) ->
                result.fold(
                    onSuccess = { guide ->
                        if (epgGuideCache[request.cacheKey]?.guide !== guide) {
                            epgGuideCache[request.cacheKey] = LiveTvCachedEpgGuide(guide, nowMillis)
                        }
                        guides += guide
                    },
                    onFailure = { error ->
                        if (error is CancellationException) throw error
                        failedGuideNames += request.label
                        log.w(error) { "Failed to load live TV guide ${request.label}" }
                    },
                )
            }
            val channels = _uiState.value.channels
            val programs = withContext(Dispatchers.Default) { matchEpgToChannels(channels, guides) }
            _uiState.value = _uiState.value.copy(
                epgPrograms = programs,
                isEpgLoading = false,
                epgErrorMessage = if (failedGuideNames.isNotEmpty()) {
                    "Some guides could not be loaded: ${failedGuideNames.joinToString()}"
                } else {
                    null
                },
            )
            if (programs.isNotEmpty()) {
                restoredEpg = LiveTvCachedEpg(epgSignature, nowMillis, programs)
                withContext(Dispatchers.Default) {
                    LiveTvCacheStore.saveEpg(epgSignature, nowMillis, programs)
                }
            }
        }
    }

    private fun applyRestoredEpg(): Boolean {
        val state = _uiState.value
        val restored = restoredEpg ?: return false
        if (state.channels.isEmpty()) return false
        val requests = buildEpgRequests(state)
        if (requests.isEmpty()) return false
        if (restored.signature != liveTvEpgSignature(state, requests)) return false
        if (Clock.System.now().toEpochMilliseconds() - restored.savedAtMillis >= LIVE_TV_EPG_CACHE_TTL_MILLIS) return false
        _uiState.value = state.copy(
            epgPrograms = restored.programs,
            isEpgLoading = false,
            epgErrorMessage = null,
        )
        return true
    }

    private fun buildEpgRequests(state: LiveTvUiState): List<LiveTvEpgRequest> {
        val urls = linkedMapOf<String, String>()
        state.epgSources.filter { it.isEnabled }.forEach { source -> urls.getOrPut(source.url) { source.name } }
        if (state.isEpgAutoDetectEnabled) {
            detectedEpgUrls.forEach { url -> urls.getOrPut(url) { epgSourceNameFromUrl(url) } }
            if (state.xtreamSettings.isConfigured && state.xtreamSettings.isEnabled) {
                urls.getOrPut(xtreamEpgUrl(state.xtreamSettings)) { "Xtream" }
            }
        }
        return buildList {
            urls.forEach { (url, label) ->
                add(LiveTvEpgRequest(cacheKey = "xmltv:$url", label = label) { nowMillis -> fetchXmltvGuide(url, nowMillis) })
            }
            val stalkerSettings = state.stalkerSettings
            if (state.isEpgAutoDetectEnabled && stalkerSettings.isConfigured && stalkerSettings.isEnabled) {
                add(
                    LiveTvEpgRequest(
                        cacheKey = "stalker:${stalkerSettings.portalUrl}:${stalkerSettings.macAddress}",
                        label = "Stalker Portal",
                    ) { nowMillis ->
                        fetchStalkerEpgGuide(
                            settings = stalkerSettings,
                            windowStartMillis = nowMillis - LIVE_TV_EPG_PAST_WINDOW_MILLIS,
                            windowEndMillis = nowMillis + LIVE_TV_EPG_FUTURE_WINDOW_MILLIS,
                        )
                    },
                )
            }
        }
    }

    private fun updateEpgSources(sources: List<LiveTvEpgSource>) {
        LiveTvStorage.saveEpgSourcesBlob(encodeEpgSources(sources))
        _uiState.value = _uiState.value.copy(epgSources = sources)
        refreshEpg(force = false)
    }

    fun toggleFavoriteChannel(channelId: String) {
        ensureLoaded()
        val favorites = _uiState.value.favoriteChannelIds
            .let { current ->
                if (channelId in current) {
                    current - channelId
                } else {
                    current + channelId
                }
            }
        persistFavoriteChannelIds(favorites)
        _uiState.value = _uiState.value.copy(favoriteChannelIds = favorites)
    }

    fun markChannelWatched(channel: LiveTvChannel) {
        ensureLoaded()
        LiveTvStorage.saveLastWatchedChannelId(channel.id)
        val recents = (listOf(channel.id) + _uiState.value.recentChannelIds.filterNot { it == channel.id })
            .take(LIVE_TV_MAX_RECENT_CHANNELS)
        LiveTvStorage.saveRecentChannelIdsBlob(recents.joinToString("\n"))
        _uiState.value = _uiState.value.copy(
            lastWatchedChannelId = channel.id,
            recentChannelIds = recents,
        )
    }

    fun setChannelGridEnabled(enabled: Boolean) {
        ensureLoaded()
        LiveTvStorage.saveChannelViewMode(if (enabled) "grid" else "list")
        _uiState.value = _uiState.value.copy(isChannelGridEnabled = enabled)
    }

    fun clearRecentChannels() {
        ensureLoaded()
        LiveTvStorage.saveRecentChannelIdsBlob("")
        _uiState.value = _uiState.value.copy(recentChannelIds = emptyList())
    }

    private fun loadRecentChannelIds(): List<String> =
        LiveTvStorage.loadRecentChannelIdsBlob()
            .orEmpty()
            .lineSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
            .take(LIVE_TV_MAX_RECENT_CHANNELS)
            .toList()

    private fun publishNavigationVisibility() {
        LiveTvStorage.publishNavigationVisibility(_uiState.value.showInNavigation)
    }

    private fun loadSavedPlaylists(): List<LiveTvPlaylist> {
        val saved = decodePlaylists(LiveTvStorage.loadPlaylistsBlob().orEmpty())
        if (saved.isNotEmpty()) return saved

        val legacyUrl = LiveTvStorage.loadPlaylistUrl()?.trim().orEmpty()
        return if (legacyUrl.isBlank()) emptyList() else listOf(createUrlPlaylist(legacyUrl))
    }

    private fun persistPlaylists(playlists: List<LiveTvPlaylist>) {
        LiveTvStorage.savePlaylistsBlob(encodePlaylists(playlists))
        LiveTvStorage.savePlaylistUrl(playlists.firstUrlSource())
    }

    private fun loadFavoriteChannelIds(): Set<String> =
        LiveTvStorage.loadFavoriteChannelIdsBlob()
            .orEmpty()
            .lineSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .toSet()

    private fun persistFavoriteChannelIds(channelIds: Set<String>) {
        LiveTvStorage.saveFavoriteChannelIdsBlob(channelIds.sorted().joinToString("\n"))
    }
}

private const val LIVE_TV_MAX_RECENT_CHANNELS = 30

private const val LIVE_TV_EPG_CACHE_TTL_MILLIS = 24L * 60L * 60L * 1000L

private fun liveTvEpgSignature(state: LiveTvUiState, requests: List<LiveTvEpgRequest>): String =
    liveTvChannelSourceSignature(state) + "#" + requests.joinToString("|") { it.cacheKey }.let { "${it.length}:${it.hashCode()}" }

internal data class LiveTvCachedEpgGuide(
    val guide: LiveTvEpgGuide,
    val loadedAtMillis: Long,
)

internal class LiveTvEpgRequest(
    val cacheKey: String,
    val label: String,
    val load: suspend (Long) -> LiveTvEpgGuide,
)

internal fun parseM3uEpgUrls(payload: String): List<String> {
    val header = payload.lineSequence()
        .map(String::trim)
        .firstOrNull(String::isNotBlank)
        ?: return emptyList()
    if (!header.startsWith("#EXTM3U", ignoreCase = true)) return emptyList()
    return listOf("url-tvg", "x-tvg-url", "tvg-url")
        .mapNotNull { key -> readM3uAttribute(header, key) }
        .flatMap { value -> value.split(',') }
        .map(String::trim)
        .filter { it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true) }
        .distinct()
}

internal fun parseM3uPlaylist(
    payload: String,
    playlist: LiveTvPlaylist? = null,
): List<LiveTvChannel> {
    val channels = mutableListOf<LiveTvChannel>()
    var pendingInfo: M3uInfo? = null
    val pendingHeaders = LinkedHashMap<String, String>()
    var pendingStreamType: String? = null

    payload.lineSequence()
        .map(String::trim)
        .filter(String::isNotBlank)
        .forEach { line ->
            when {
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    val info = parseExtInf(line)
                    pendingInfo = info
                    info.headers.forEach { (key, value) -> pendingHeaders.putM3uHeader(key, value) }
                }
                line.startsWith("#EXTVLCOPT", ignoreCase = true) -> {
                    parseVlcOption(line.substringAfter(':', missingDelimiterValue = ""))
                        ?.let { (key, value) -> pendingHeaders.putM3uHeader(key, value) }
                }
                line.startsWith("#EXTHTTP", ignoreCase = true) -> {
                    parseExtHttp(line.substringAfter(':', missingDelimiterValue = ""))
                        .forEach { (key, value) -> pendingHeaders.putM3uHeader(key, value) }
                }
                line.startsWith("#KODIPROP", ignoreCase = true) -> {
                    parseKodiProp(line.substringAfter(':', missingDelimiterValue = ""), pendingHeaders)
                        ?.let { pendingStreamType = it }
                }
                line.startsWith("#") -> Unit
                else -> {
                    val (streamUrl, urlHeaders) = splitM3uStreamUrl(line)
                    if (streamUrl.isBlank()) return@forEach
                    urlHeaders.forEach { (key, value) -> pendingHeaders.putM3uHeader(key, value) }
                    val info = pendingInfo
                    val name = info?.name?.takeIf(String::isNotBlank)
                        ?: streamUrl.substringAfterLast('/').substringBefore('?').ifBlank { "Channel" }
                    channels += LiveTvChannel(
                        id = stableChannelId(streamUrl, channels.size),
                        name = name,
                        streamUrl = streamUrl,
                        logoUrl = info?.logoUrl?.takeIf(String::isNotBlank),
                        group = info?.group?.takeIf(String::isNotBlank),
                        playlistId = playlist?.id,
                        playlistName = playlist?.name,
                        // The playlist's own headers win; otherwise the channel opens as VLC does.
                        headers = defaultLiveTvStreamHeaders(streamUrl) + pendingHeaders,
                        streamType = pendingStreamType,
                        epgId = info?.epgId?.takeIf(String::isNotBlank),
                    )
                    pendingInfo = null
                    pendingHeaders.clear()
                    pendingStreamType = null
                }
            }
        }

    return channels.distinctBy { it.streamUrl }
}

private data class M3uInfo(
    val name: String,
    val logoUrl: String?,
    val group: String?,
    val epgId: String? = null,
    val headers: Map<String, String> = emptyMap(),
)

private fun parseExtInf(line: String): M3uInfo {
    val name = line.substring(extInfNameDelimiterIndex(line) + 1)
        .trim()
        .ifBlank {
            readM3uAttribute(line, "tvg-name").orEmpty()
        }
    val headers = buildMap {
        (readM3uAttribute(line, "http-user-agent") ?: readM3uAttribute(line, "user-agent"))
            ?.let { put("User-Agent", it) }
        (
            readM3uAttribute(line, "http-referrer")
                ?: readM3uAttribute(line, "http-referer")
                ?: readM3uAttribute(line, "referrer")
                ?: readM3uAttribute(line, "referer")
            )?.let { put("Referer", it) }
    }
    return M3uInfo(
        name = name,
        logoUrl = readM3uAttribute(line, "tvg-logo"),
        group = readM3uAttribute(line, "group-title"),
        epgId = readM3uAttribute(line, "tvg-id"),
        headers = headers,
    )
}

private fun extInfNameDelimiterIndex(line: String): Int {
    var inQuotes = false
    line.forEachIndexed { index, char ->
        when {
            char == '"' -> inQuotes = !inQuotes
            char == ',' && !inQuotes -> return index
        }
    }
    return line.lastIndex
}

private fun parseVlcOption(option: String): Pair<String, String>? {
    val key = option.substringBefore('=', missingDelimiterValue = "").trim().lowercase()
    val value = option.substringAfter('=', missingDelimiterValue = "").trim().trim('"')
    if (value.isBlank()) return null
    return when (key) {
        "http-user-agent" -> "User-Agent" to value
        "http-referrer", "http-referer" -> "Referer" to value
        "http-origin" -> "Origin" to value
        "http-cookie" -> "Cookie" to value
        else -> null
    }
}

private fun parseExtHttp(json: String): Map<String, String> = runCatching {
    val element = m3uJson.parseToJsonElement(json.trim()) as? JsonObject ?: return emptyMap()
    element.mapNotNull { (key, value) ->
        val content = (value as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotBlank)
        content?.let { key.trim() to it }
    }.toMap()
}.getOrDefault(emptyMap())

private fun parseKodiProp(property: String, headers: MutableMap<String, String>): String? {
    val key = property.substringBefore('=', missingDelimiterValue = "").trim().lowercase()
    val value = property.substringAfter('=', missingDelimiterValue = "").trim()
    if (value.isBlank()) return null
    return when (key) {
        "inputstream.adaptive.manifest_type" -> when (value.lowercase()) {
            "mpd", "dash" -> "dash"
            "hls", "m3u8" -> "hls"
            "ism", "isml", "smoothstreaming" -> "ss"
            else -> null
        }
        "mimetype" -> when {
            value.contains("mpegurl", ignoreCase = true) -> "hls"
            value.contains("dash", ignoreCase = true) -> "dash"
            else -> null
        }
        "inputstream.adaptive.stream_headers",
        "inputstream.adaptive.common_headers",
        "inputstream.adaptive.manifest_headers" -> {
            parseM3uHeaderQuery(value).forEach { (headerKey, headerValue) -> headers.putM3uHeader(headerKey, headerValue) }
            null
        }
        else -> null
    }
}

private fun splitM3uStreamUrl(line: String): Pair<String, Map<String, String>> {
    val separator = line.indexOf('|')
    if (separator < 0) return line.trim() to emptyMap()
    return line.substring(0, separator).trim() to parseM3uHeaderQuery(line.substring(separator + 1))
}

private fun parseM3uHeaderQuery(query: String): Map<String, String> =
    query.split('&')
        .mapNotNull { parameter ->
            val key = parameter.substringBefore('=', missingDelimiterValue = "").trim().decodeM3uComponent()
            val value = parameter.substringAfter('=', missingDelimiterValue = "").trim().trim('"').decodeM3uComponent()
            if (key.isBlank() || value.isBlank()) null else key to value
        }
        .toMap()

private fun String.decodeM3uComponent(): String =
    if ('%' in this) runCatching { decodeURLQueryComponent() }.getOrDefault(this) else this

private fun MutableMap<String, String>.putM3uHeader(rawKey: String, value: String) {
    val trimmedValue = value.trim()
    if (trimmedValue.isBlank()) return
    val key = when (rawKey.trim().lowercase()) {
        "user-agent", "useragent", "http-user-agent" -> "User-Agent"
        "referer", "referrer", "http-referer", "http-referrer" -> "Referer"
        "origin", "http-origin" -> "Origin"
        "cookie", "http-cookie" -> "Cookie"
        else -> rawKey.trim()
    }
    if (key.isBlank() || key.any { it.isWhitespace() || it == ':' }) return
    keys.firstOrNull { it.equals(key, ignoreCase = true) }?.let(::remove)
    put(key, trimmedValue)
}

private val m3uJson = Json { ignoreUnknownKeys = true; isLenient = true }

private fun readM3uAttribute(line: String, key: String): String? {
    val marker = "$key=\""
    val start = line.indexOf(marker, ignoreCase = true)
    if (start < 0) return null
    val valueStart = start + marker.length
    val valueEnd = line.indexOf('"', startIndex = valueStart).takeIf { it >= 0 } ?: return null
    return line.substring(valueStart, valueEnd).trim()
}

private fun createUrlPlaylist(url: String, customName: String? = null): LiveTvPlaylist =
    LiveTvPlaylist(
        id = stablePlaylistId(url, 0),
        name = customName?.trim()?.takeIf(String::isNotBlank) ?: playlistNameFromUrl(url),
        type = LiveTvPlaylistType.Url,
        source = url,
    )

private fun playlistNameFromUrl(url: String): String {
    val trimmed = url.trim()
    val fileName = trimmed
        .substringBefore('?')
        .substringAfterLast('/')
        .substringBeforeLast('.', missingDelimiterValue = "")
        .trim()
    if (fileName.isNotBlank()) return fileName

    return trimmed
        .substringAfter("://", missingDelimiterValue = trimmed)
        .substringBefore('/')
        .trim()
        .ifBlank { "M3U playlist" }
}

private fun List<LiveTvPlaylist>.firstUrlSource(): String =
    firstOrNull { it.type == LiveTvPlaylistType.Url }?.source.orEmpty()

private fun List<LiveTvPlaylist>.firstEnabledUrlSource(): String =
    firstOrNull { it.isEnabled && it.type == LiveTvPlaylistType.Url }?.source.orEmpty()

private fun decodePlaylistEnabled(value: String?): Boolean =
    value?.equals("false", ignoreCase = true) != true

private const val playlistRecordSeparator = "\u001E"
private const val playlistFieldSeparator = "\u001F"

private fun encodePlaylists(playlists: List<LiveTvPlaylist>): String =
    playlists.joinToString(playlistRecordSeparator) { playlist ->
        listOf(
            playlist.id,
            playlist.name,
            playlist.type.name,
            playlist.source,
            playlist.isEnabled.toString(),
        ).joinToString(playlistFieldSeparator) { escapePlaylistField(it) }
    }

private fun decodePlaylists(blob: String): List<LiveTvPlaylist> =
    blob
        .split(playlistRecordSeparator)
        .mapNotNull { record ->
            if (record.isBlank()) return@mapNotNull null
            val fields = record.split(playlistFieldSeparator).map(::unescapePlaylistField)
            val type = fields.getOrNull(2)?.let { raw ->
                runCatching { LiveTvPlaylistType.valueOf(raw) }.getOrNull()
            } ?: return@mapNotNull null
            LiveTvPlaylist(
                id = fields.getOrNull(0)?.takeIf(String::isNotBlank) ?: return@mapNotNull null,
                name = fields.getOrNull(1)?.takeIf(String::isNotBlank) ?: "M3U playlist",
                type = type,
                source = fields.getOrNull(3)?.takeIf(String::isNotBlank) ?: return@mapNotNull null,
                isEnabled = decodePlaylistEnabled(fields.getOrNull(4)),
            )
        }

private fun escapePlaylistField(value: String): String =
    value
        .replace("%", "%25")
        .replace(playlistRecordSeparator, "%1E")
        .replace(playlistFieldSeparator, "%1F")

private fun unescapePlaylistField(value: String): String =
    value
        .replace("%1F", playlistFieldSeparator)
        .replace("%1E", playlistRecordSeparator)
        .replace("%25", "%")

private fun encodeEpgSources(sources: List<LiveTvEpgSource>): String =
    sources.joinToString(playlistRecordSeparator) { source ->
        listOf(
            source.id,
            source.name,
            source.url,
            source.isEnabled.toString(),
        ).joinToString(playlistFieldSeparator) { escapePlaylistField(it) }
    }

private fun decodeEpgSources(blob: String): List<LiveTvEpgSource> =
    blob
        .split(playlistRecordSeparator)
        .mapNotNull { record ->
            if (record.isBlank()) return@mapNotNull null
            val fields = record.split(playlistFieldSeparator).map(::unescapePlaylistField)
            val url = fields.getOrNull(2)?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            LiveTvEpgSource(
                id = fields.getOrNull(0)?.takeIf(String::isNotBlank) ?: return@mapNotNull null,
                name = fields.getOrNull(1)?.takeIf(String::isNotBlank) ?: epgSourceNameFromUrl(url),
                url = url,
                isEnabled = decodePlaylistEnabled(fields.getOrNull(3)),
            )
        }

private fun epgSourceNameFromUrl(url: String): String {
    val trimmed = url.trim()
    val fileName = trimmed
        .substringBefore('?')
        .substringAfterLast('/')
        .removeSuffix(".gz")
        .removeSuffix(".GZ")
        .substringBeforeLast('.', missingDelimiterValue = "")
        .trim()
    if (fileName.isNotBlank() && !fileName.equals("xmltv", ignoreCase = true)) return fileName

    return trimmed
        .substringAfter("://", missingDelimiterValue = trimmed)
        .substringBefore('/')
        .trim()
        .ifBlank { "EPG" }
}

private fun stableEpgSourceId(source: String, index: Int): String =
    "epg:${source.hashCode().toUInt().toString(16)}:$index"

private fun stableChannelId(streamUrl: String, index: Int): String =
    "live:${streamUrl.hashCode().toUInt().toString(16)}:$index"

private fun stablePlaylistId(source: String, index: Int): String =
    "playlist:${source.hashCode().toUInt().toString(16)}:$index"
