package com.nuvio.app.features.anilist

import com.nuvio.app.features.profiles.ProfileRepository
import com.nuvio.app.features.tracking.TrackingAuthProvider
import com.nuvio.app.features.tracking.TrackingCapability
import com.nuvio.app.features.tracking.TrackingProviderDescriptor
import com.nuvio.app.features.tracking.TrackingProviderId
import com.nuvio.app.features.tracking.TrackingProviderRegistry
import com.nuvio.app.features.tracking.TrackingRefreshIntent
import io.ktor.http.decodeURLQueryComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.time.Clock

private const val ANILIST_AUTOMATIC_REFRESH_WINDOW_MS = 120_000L

internal fun parseAniListAuthCallback(url: String): AniListAuthCallback {
    val trimmed = url.trim()
    if (!trimmed.startsWith(AniListConfig.REDIRECT_URI, ignoreCase = true)) return AniListAuthCallback.NotAniList
    val rest = trimmed.substring(AniListConfig.REDIRECT_URI.length)
    val parameters = if ('#' in rest) rest.substringAfter('#') else rest.substringAfter('?', "")
    val values = parameters.split('&')
        .filter { it.contains('=') }
        .associate { pair ->
            pair.substringBefore('=').decodeURLQueryComponent() to pair.substringAfter('=').decodeURLQueryComponent()
        }
    val token = values["access_token"]?.trim()?.takeIf { it.isNotEmpty() } ?: return AniListAuthCallback.Invalid
    return AniListAuthCallback.Token(token, values["expires_in"]?.toLongOrNull())
}

object AniListTracker : TrackingAuthProvider {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val refreshMutex = Mutex()
    private val writeMutex = Mutex()
    private var loadedProfileId: Int? = null
    private val storedState = MutableStateFlow(AniListStoredState())
    private val authUi = MutableStateFlow(AniListAuthUiState())
    private val syncUi = MutableStateFlow(AniListSyncState())
    private val authenticated = MutableStateFlow(false)

    internal val authState: StateFlow<AniListAuthUiState> = authUi.asStateFlow()
    internal val syncState: StateFlow<AniListSyncState> = syncUi.asStateFlow()
    internal val api = AniListApi(accessToken = ::validToken, onUnauthorized = ::handleUnauthorized)

    override val isAuthenticated: StateFlow<Boolean> = authenticated.asStateFlow()
    override val descriptor = TrackingProviderDescriptor(
        id = TrackingProviderId.ANILIST,
        displayName = "AniList",
        capabilities = setOf(
            TrackingCapability.AUTHENTICATION,
            TrackingCapability.LIBRARY_READ,
            TrackingCapability.LIBRARY_WRITE,
            TrackingCapability.WATCHED_READ,
            TrackingCapability.WATCHED_WRITE,
            TrackingCapability.PROGRESS_READ,
            TrackingCapability.PROGRESS_WRITE,
            TrackingCapability.SCROBBLE,
            TrackingCapability.RATINGS,
        ),
    )

    private val library = AniListTrackingLibraryProvider()
    private val watched = AniListWatchedSyncAdapter()
    private val progress = AniListTrackingProgressProvider()
    private val writes = AniListTrackingWrites()
    private val ratings = AniListRatingsProvider()

    fun register() {
        if (TrackingProviderRegistry.authProvider(providerId) === this) return
        TrackingProviderRegistry.register(this)
        TrackingProviderRegistry.registerListWriter(writes)
        TrackingProviderRegistry.registerHistoryWriter(writes)
        TrackingProviderRegistry.registerScrobbler(writes)
        TrackingProviderRegistry.registerLibraryProvider(library)
        TrackingProviderRegistry.registerWatchedProvider(watched)
        TrackingProviderRegistry.registerProgressProvider(progress)
        TrackingProviderRegistry.registerRatingProvider(ratings)
    }

    private fun now(): Long = Clock.System.now().toEpochMilliseconds()

    private fun sessionIsValid(session: AniListSession?): Boolean {
        if (session == null) return false
        val expiresAt = session.expiresAtEpochMs ?: return true
        return now() < expiresAt
    }

    private fun validToken(): String? {
        ensureLoaded()
        val session = storedState.value.session
        return session?.takeIf { sessionIsValid(it) }?.accessToken
    }

    private fun handleUnauthorized() {
        storedState.update { it.copy(session = null) }
        persist()
        publish()
        authUi.update { it.copy(error = AniListError.AUTHORIZATION_EXPIRED, isWorking = false) }
    }

    private fun persist() {
        val profileId = loadedProfileId ?: return
        AniListStorage.savePayload(profileId, json.encodeToString(storedState.value))
    }

    private fun publish() {
        val state = storedState.value
        val session = state.session?.takeIf { sessionIsValid(it) }
        authenticated.value = session != null
        authUi.update {
            it.copy(
                isAuthenticated = session != null,
                isAwaitingAuthorization = if (session != null) false else it.isAwaitingAuthorization,
                userName = session?.userName,
                avatarUrl = session?.avatarUrl,
            )
        }
        syncUi.update {
            it.copy(
                entries = state.entries,
                hasLoaded = state.syncedAtEpochMs != null,
                syncedAtEpochMs = state.syncedAtEpochMs,
            )
        }
    }

    private fun loadProfile(profileId: Int) {
        loadedProfileId = profileId
        val payload = AniListStorage.loadPayload(profileId)
        storedState.value = payload
            ?.let { runCatching { json.decodeFromString<AniListStoredState>(it) }.getOrNull() }
            ?: AniListStoredState()
        authUi.value = AniListAuthUiState()
        syncUi.value = AniListSyncState()
        publish()
    }

    override fun ensureLoaded() {
        val profileId = ProfileRepository.activeProfileId
        if (loadedProfileId != profileId) loadProfile(profileId)
    }

    override fun onProfileChanged() {
        loadedProfileId = null
        ensureLoaded()
    }

    override fun clearLocalState() {
        AniListStorage.clearAll()
        loadedProfileId = null
        storedState.value = AniListStoredState()
        authUi.value = AniListAuthUiState()
        syncUi.value = AniListSyncState()
        publish()
        ensureLoaded()
    }

    override fun removeStoredProfile(profileId: Int) {
        AniListStorage.removeProfile(profileId)
        if (loadedProfileId == profileId) {
            loadedProfileId = null
            storedState.value = AniListStoredState()
            authUi.value = AniListAuthUiState()
            syncUi.value = AniListSyncState()
            publish()
        }
    }

    internal fun beginAuthorization(): String? {
        ensureLoaded()
        if (AniListConfig.CLIENT_ID.isBlank()) {
            authUi.update { it.copy(isAwaitingAuthorization = false, error = AniListError.MISSING_CLIENT_ID) }
            return null
        }
        authUi.update { it.copy(isAwaitingAuthorization = true, error = null) }
        return authorizationUrl()
    }

    internal fun authorizationUrl(): String =
        "${AniListConfig.AUTHORIZE_URL}?client_id=${AniListConfig.CLIENT_ID}&response_type=token"

    internal fun cancelAuthorization() {
        authUi.update { it.copy(isAwaitingAuthorization = false, isWorking = false, error = null) }
    }

    internal fun clearError() {
        authUi.update { it.copy(error = null) }
    }

    override fun handleAuthCallback(url: String): Boolean =
        when (val callback = parseAniListAuthCallback(url)) {
            AniListAuthCallback.NotAniList -> false
            AniListAuthCallback.Invalid -> {
                authUi.update {
                    it.copy(isAwaitingAuthorization = false, isWorking = false, error = AniListError.INVALID_CALLBACK)
                }
                true
            }
            is AniListAuthCallback.Token -> {
                scope.launch { completeSignIn(callback) }
                true
            }
        }

    private suspend fun completeSignIn(callback: AniListAuthCallback.Token) {
        ensureLoaded()
        authUi.update { it.copy(isWorking = true, error = null) }
        val expiresAt = callback.expiresInSeconds?.let { now() + it * 1_000L }
        storedState.update { AniListStoredState(session = AniListSession(callback.accessToken, expiresAt)) }
        try {
            val viewer = api.query(ANILIST_VIEWER_QUERY)["Viewer"]?.jsonObject
                ?: throw AniListApiException(0, "Missing viewer")
            val avatar = viewer["avatar"] as? JsonObject
            storedState.update { state ->
                state.copy(
                    session = state.session?.copy(
                        userId = (viewer["id"] as? JsonPrimitive)?.intOrNull,
                        userName = (viewer["name"] as? JsonPrimitive)?.contentOrNull,
                        avatarUrl = (avatar?.get("large") as? JsonPrimitive)?.contentOrNull
                            ?: (avatar?.get("medium") as? JsonPrimitive)?.contentOrNull,
                    ),
                )
            }
            persist()
            publish()
            authUi.update { it.copy(isAwaitingAuthorization = false, isWorking = false, error = null) }
            refresh(TrackingRefreshIntent.INVALIDATED)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            storedState.value = AniListStoredState()
            persist()
            publish()
            authUi.update {
                it.copy(isAwaitingAuthorization = false, isWorking = false, error = AniListError.SIGN_IN_FAILED)
            }
        }
    }

    internal fun disconnect() {
        ensureLoaded()
        storedState.value = AniListStoredState()
        loadedProfileId?.let { AniListStorage.removeProfile(it) }
        authUi.value = AniListAuthUiState()
        syncUi.value = AniListSyncState()
        publish()
    }

    internal suspend fun refresh(intent: TrackingRefreshIntent) {
        ensureLoaded()
        if (!authenticated.value) return
        fun fresh(): Boolean {
            val last = storedState.value.syncedAtEpochMs ?: return false
            return intent == TrackingRefreshIntent.AUTOMATIC && now() - last < ANILIST_AUTOMATIC_REFRESH_WINDOW_MS
        }
        if (fresh()) return
        refreshMutex.withLock {
            if (fresh()) return
            syncUi.update { it.copy(isLoading = true, error = null) }
            try {
                val userId = storedState.value.session?.userId ?: fetchViewerId()
                val data = api.query(ANILIST_COLLECTION_QUERY, buildJsonObject { put("userId", userId) })
                val lists = data["MediaListCollection"]?.jsonObject?.get("lists") as? JsonArray
                val entries = lists.orEmpty()
                    .flatMap { list -> (list as? JsonObject)?.get("entries") as? JsonArray ?: JsonArray(emptyList()) }
                    .mapNotNull { (it as? JsonObject)?.toAniListEntry() }
                    .groupBy(AniListEntry::mediaId)
                    .map { (_, group) -> group.maxBy(AniListEntry::updatedAtSeconds) }
                storedState.update { it.copy(entries = entries, syncedAtEpochMs = now()) }
                persist()
                publish()
                syncUi.update { it.copy(isLoading = false, error = null) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: AniListApiException) {
                syncUi.update {
                    it.copy(
                        isLoading = false,
                        error = if (error.status == 429) AniListError.RATE_LIMITED else AniListError.SYNC_FAILED,
                    )
                }
            } catch (_: Exception) {
                syncUi.update { it.copy(isLoading = false, error = AniListError.SYNC_FAILED) }
            }
        }
    }

    private suspend fun fetchViewerId(): Int {
        val viewer = api.query(ANILIST_VIEWER_QUERY)["Viewer"]?.jsonObject
            ?: throw AniListApiException(0, "Missing viewer")
        val id = (viewer["id"] as? JsonPrimitive)?.intOrNull ?: throw AniListApiException(0, "Missing viewer id")
        storedState.update { state -> state.copy(session = state.session?.copy(userId = id)) }
        return id
    }

    internal suspend fun ensureSynced() {
        if (storedState.value.syncedAtEpochMs == null) refresh(TrackingRefreshIntent.AUTOMATIC)
    }

    internal fun entries(): List<AniListEntry> {
        ensureLoaded()
        return storedState.value.entries
    }

    internal fun entryFor(mediaId: Int): AniListEntry? = entries().firstOrNull { it.mediaId == mediaId }

    internal fun requireProfile(profileId: Int) {
        if (profileId != ProfileRepository.activeProfileId) throw CancellationException("AniList profile changed")
        ensureLoaded()
    }

    internal suspend fun saveEntry(
        mediaId: Int,
        status: String? = null,
        progress: Int? = null,
        scoreRaw: Int? = null,
    ): AniListEntry = writeMutex.withLock {
        val variables = buildJsonObject {
            put("mediaId", mediaId)
            status?.let { put("status", it) }
            progress?.let { put("progress", it) }
            scoreRaw?.let { put("scoreRaw", it) }
        }
        val data = api.query(ANILIST_SAVE_MUTATION, variables)
        val entry = (data["SaveMediaListEntry"] as? JsonObject)?.toAniListEntry()
            ?: throw AniListApiException(0, "Invalid save response")
        storedState.update { state ->
            state.copy(entries = state.entries.filterNot { it.mediaId == entry.mediaId } + entry)
        }
        persist()
        publish()
        entry
    }

    internal suspend fun removeEntry(mediaId: Int) {
        val existing = entryFor(mediaId) ?: return
        writeMutex.withLock {
            api.query(ANILIST_DELETE_MUTATION, buildJsonObject { put("id", existing.entryId) })
            storedState.update { state -> state.copy(entries = state.entries.filterNot { it.mediaId == mediaId }) }
            persist()
            publish()
        }
    }
}
