package com.nuvio.app.features.discord

import com.nuvio.app.core.logging.InAppLogger
import com.nuvio.app.core.network.createApiHttpClient
import com.nuvio.app.features.player.PlayerSettingsRepository
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.discord_rpc_activity_competing
import nuvio.composeapp.generated.resources.discord_rpc_activity_listening
import nuvio.composeapp.generated.resources.discord_rpc_activity_playing
import nuvio.composeapp.generated.resources.discord_rpc_activity_watching
import nuvio.composeapp.generated.resources.discord_rpc_button_imdb
import nuvio.composeapp.generated.resources.discord_rpc_presence_live
import nuvio.composeapp.generated.resources.discord_rpc_presence_paused
import nuvio.composeapp.generated.resources.discord_rpc_presence_playing
import nuvio.composeapp.generated.resources.discord_rpc_status_dnd
import nuvio.composeapp.generated.resources.discord_rpc_status_idle
import nuvio.composeapp.generated.resources.discord_rpc_status_invisible
import nuvio.composeapp.generated.resources.discord_rpc_status_online
import nuvio.composeapp.generated.resources.discord_rpc_test_subtitle
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import kotlin.math.abs
import kotlin.time.Clock

internal enum class DiscordRpcActivityType(
    val value: Int,
    val labelRes: StringResource,
) {
    Watching(3, Res.string.discord_rpc_activity_watching),
    Playing(0, Res.string.discord_rpc_activity_playing),
    Listening(2, Res.string.discord_rpc_activity_listening),
    Competing(5, Res.string.discord_rpc_activity_competing),
    ;

    companion object {
        fun fromValue(value: Int): DiscordRpcActivityType =
            entries.firstOrNull { it.value == value } ?: Watching
    }
}

internal enum class DiscordRpcStatus(
    val value: String,
    val labelRes: StringResource,
) {
    Online("online", Res.string.discord_rpc_status_online),
    Idle("idle", Res.string.discord_rpc_status_idle),
    DoNotDisturb("dnd", Res.string.discord_rpc_status_dnd),
    Invisible("invisible", Res.string.discord_rpc_status_invisible),
    ;

    companion object {
        fun fromValue(value: String): DiscordRpcStatus =
            entries.firstOrNull { it.value == value } ?: Online
    }
}

@Serializable
internal data class DiscordRpcSettings(
    @SerialName("enabled")
    val enabled: Boolean = false,
    @SerialName("activity_type")
    val activityType: Int = DiscordRpcActivityType.Watching.value,
    @SerialName("status")
    val status: String = DiscordRpcStatus.Online.value,
    @SerialName("show_title_as_name")
    val showTitleAsName: Boolean = false,
    @SerialName("show_poster")
    val showPoster: Boolean = true,
    @SerialName("show_playback_state")
    val showPlaybackState: Boolean = true,
    @SerialName("show_timestamps")
    val showTimestamps: Boolean = true,
    @SerialName("hide_on_pause")
    val hideOnPause: Boolean = false,
    @SerialName("show_imdb_button")
    val showImdbButton: Boolean = true,
    @SerialName("application_id")
    val applicationId: String = "",
)

@Serializable
internal data class DiscordRpcUser(
    @SerialName("id")
    val id: String,
    @SerialName("username")
    val username: String? = null,
    @SerialName("global_name")
    val globalName: String? = null,
    @SerialName("avatar")
    val avatar: String? = null,
) {
    val displayName: String
        get() = globalName?.takeIf { it.isNotBlank() } ?: username?.takeIf { it.isNotBlank() } ?: id

    val avatarUrl: String?
        get() = avatar?.takeIf { it.isNotBlank() }?.let { hash ->
            val extension = if (hash.startsWith("a_")) "gif" else "png"
            "https://cdn.discordapp.com/avatars/$id/$hash.$extension?size=128"
        }
}

internal data class DiscordRpcUiState(
    val isSupported: Boolean = false,
    val isLoggedIn: Boolean = false,
    val isLoggingIn: Boolean = false,
    val user: DiscordRpcUser? = null,
    val settings: DiscordRpcSettings = DiscordRpcSettings(),
    val connectionState: DiscordGatewayState = DiscordGatewayState.Idle,
    val tokenExpired: Boolean = false,
    val isPresenceActive: Boolean = false,
    val lastError: String? = null,
    val isTestActive: Boolean = false,
)

internal data class DiscordPlaybackInfo(
    val title: String,
    val subtitle: String? = null,
    val artworkUrl: String? = null,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isLive: Boolean = false,
    val imdbId: String? = null,
    val isTest: Boolean = false,
) {
    val isPaused: Boolean
        get() = !isPlaying && !isLoading
}

internal object DiscordRpcRepository {
    private const val TAG = "DiscordRPC"
    private const val UPDATE_DEBOUNCE_MS = 1_000L
    private const val SEEK_TOLERANCE_MS = 3_000L
    private const val DURATION_BUCKET_MS = 5_000L
    private const val FAILURE_COOLDOWN_MS = 60_000L
    private const val TEST_PRESENCE_DURATION_MS = 60_000L

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
    private val httpClient: HttpClient by lazy { createApiHttpClient() }

    private val _uiState = MutableStateFlow(DiscordRpcUiState())
    val uiState: StateFlow<DiscordRpcUiState> = _uiState.asStateFlow()

    private var hasLoaded = false
    private var token: String? = null
    private var playback: DiscordPlaybackInfo? = null
    private var rpc: DiscordRpc? = null
    private var rpcStateJob: Job? = null
    private var rpcErrorJob: Job? = null
    private var testJob: Job? = null
    private var updateJob: Job? = null
    private var lastSignature: Any? = null
    private var lastStartEpochMs: Long? = null
    private var lastHiddenReason: String? = null
    private var retryAfterEpochMs = 0L

    fun ensureLoaded() {
        if (hasLoaded) return
        hasLoaded = true
        val settings = DiscordRpcStorage.loadSettingsPayload()
            ?.let { payload -> runCatching { json.decodeFromString<DiscordRpcSettings>(payload) }.getOrNull() }
            ?: DiscordRpcSettings()
        val user = DiscordRpcStorage.loadUserPayload()
            ?.let { payload -> runCatching { json.decodeFromString<DiscordRpcUser>(payload) }.getOrNull() }
        val storedToken = runCatching { DiscordRpcStorage.loadToken() }.getOrNull()?.takeIf { it.isNotBlank() }
        token = storedToken
        _uiState.value = DiscordRpcUiState(
            isSupported = DiscordRpcPlatform.isSupported,
            isLoggedIn = storedToken != null,
            user = user.takeIf { storedToken != null },
            settings = settings,
        )
    }

    fun login(newToken: String) {
        ensureLoaded()
        val normalized = newToken.trim().removeSurrounding("\"")
        if (normalized.isBlank()) return
        _uiState.update { it.copy(isLoggingIn = true, tokenExpired = false) }
        scope.launch {
            val user = fetchUser(normalized)
            if (user == null) {
                _uiState.update { it.copy(isLoggingIn = false, tokenExpired = true) }
                return@launch
            }
            val enabledSettings = _uiState.value.settings.copy(enabled = true)
            DiscordRpcStorage.saveSettingsPayload(json.encodeToString(enabledSettings))
            runCatching { DiscordRpcStorage.saveToken(normalized) }
                .onFailure { InAppLogger.error(TAG, "Unable to save Discord token") }
            DiscordRpcStorage.saveUserPayload(json.encodeToString(user))
            token = normalized
            InAppLogger.info(TAG, "Logged in as ${user.displayName}")
            _uiState.update {
                it.copy(
                    isLoggedIn = true,
                    isLoggingIn = false,
                    user = user,
                    tokenExpired = false,
                    lastError = null,
                    settings = enabledSettings,
                )
            }
            scheduleUpdate(force = true)
        }
    }

    fun logout() {
        ensureLoaded()
        scope.launch {
            signOut(expired = false)
        }
    }

    fun setEnabled(enabled: Boolean) = updateSettings { it.copy(enabled = enabled) }

    fun setActivityType(type: DiscordRpcActivityType) = updateSettings { it.copy(activityType = type.value) }

    fun setStatus(status: DiscordRpcStatus) = updateSettings { it.copy(status = status.value) }

    fun setShowTitleAsName(enabled: Boolean) = updateSettings { it.copy(showTitleAsName = enabled) }

    fun setShowPoster(enabled: Boolean) = updateSettings { it.copy(showPoster = enabled) }

    fun setShowPlaybackState(enabled: Boolean) = updateSettings { it.copy(showPlaybackState = enabled) }

    fun setShowTimestamps(enabled: Boolean) = updateSettings { it.copy(showTimestamps = enabled) }

    fun setHideOnPause(enabled: Boolean) = updateSettings { it.copy(hideOnPause = enabled) }

    fun setShowImdbButton(enabled: Boolean) = updateSettings { it.copy(showImdbButton = enabled) }

    fun setApplicationId(applicationId: String) = updateSettings {
        it.copy(applicationId = applicationId.trim().filter(Char::isDigit))
    }

    fun updatePlayback(info: DiscordPlaybackInfo) {
        ensureLoaded()
        scope.launch {
            if (testJob != null) {
                testJob?.cancel()
                testJob = null
                _uiState.update { it.copy(isTestActive = false) }
            }
            playback = info
            scheduleUpdate(force = false)
        }
    }

    fun clearPlayback() {
        scope.launch {
            InAppLogger.debug(TAG, "Playback cleared")
            playback = null
            stopRpc()
        }
    }

    fun sendTestPresence() {
        ensureLoaded()
        scope.launch {
            if (token == null) return@launch
            val currentPlayback = playback
            if (currentPlayback != null && !currentPlayback.isTest) {
                scheduleUpdate(force = true)
                return@launch
            }
            testJob?.cancel()
            playback = DiscordPlaybackInfo(
                title = DiscordRpcConfig.APP_NAME,
                subtitle = getString(Res.string.discord_rpc_test_subtitle),
                isPlaying = true,
                isTest = true,
            )
            retryAfterEpochMs = 0L
            _uiState.update { it.copy(isTestActive = true, lastError = null) }
            scheduleUpdate(force = true)
            testJob = scope.launch {
                delay(TEST_PRESENCE_DURATION_MS)
                if (playback?.isTest == true) {
                    playback = null
                    stopRpc()
                }
                testJob = null
                _uiState.update { it.copy(isTestActive = false) }
            }
        }
    }

    private fun updateSettings(transform: (DiscordRpcSettings) -> DiscordRpcSettings) {
        ensureLoaded()
        val current = _uiState.value.settings
        val updated = transform(current)
        if (updated == current) return
        _uiState.update { it.copy(settings = updated) }
        DiscordRpcStorage.saveSettingsPayload(json.encodeToString(updated))
        scope.launch {
            if (updated.applicationId != current.applicationId) stopRpc()
            scheduleUpdate(force = true)
        }
    }

    private fun scheduleUpdate(force: Boolean) {
        val info = playback
        val currentToken = token
        val state = _uiState.value
        val settings = state.settings
        val privateViewing = PlayerSettingsRepository.uiState.value.privateViewingEnabled
        val isTest = info?.isTest == true
        val shouldShow = info != null &&
            currentToken != null &&
            state.isSupported &&
            (settings.enabled || isTest) &&
            (!privateViewing || isTest) &&
            info.title.isNotBlank() &&
            !(settings.hideOnPause && info.isPaused)
        if (info == null || !shouldShow) {
            val reason = when {
                info == null -> "no playback"
                currentToken == null -> "not logged in"
                !state.isSupported -> "unsupported platform"
                !(settings.enabled || isTest) -> "disabled in settings"
                privateViewing && !isTest -> "private viewing enabled"
                info.title.isBlank() -> "blank title"
                else -> "hidden on pause"
            }
            if (reason != lastHiddenReason) {
                lastHiddenReason = reason
                InAppLogger.debug(TAG, "Presence not shown: $reason")
            }
            if (rpc != null || updateJob != null) stopRpc()
            return
        }
        lastHiddenReason = null
        if (!force && rpc == null && nowEpochMs() < retryAfterEpochMs) return

        val startEpochMs = if (info.isPlaying) nowEpochMs() - info.positionMs.coerceAtLeast(0L) else null
        val signature = info.copy(
            positionMs = 0L,
            durationMs = info.durationMs / DURATION_BUCKET_MS,
        ) to settings
        val sameStart = when {
            startEpochMs == null && lastStartEpochMs == null -> true
            startEpochMs != null && lastStartEpochMs != null -> abs(startEpochMs - lastStartEpochMs!!) < SEEK_TOLERANCE_MS
            else -> false
        }
        if (!force && signature == lastSignature && sameStart) return

        lastSignature = signature
        lastStartEpochMs = startEpochMs
        updateJob?.cancel()
        updateJob = scope.launch {
            delay(UPDATE_DEBOUNCE_MS)
            applyPresence()
        }
    }

    private suspend fun applyPresence() {
        val info = playback ?: return
        val currentToken = token ?: return
        val settings = _uiState.value.settings
        val applicationId = settings.applicationId
            .takeIf { it.length in DiscordRpcConfig.APPLICATION_ID_LENGTH_RANGE }
            ?: DiscordRpcConfig.APPLICATION_ID

        val client = rpc?.takeIf { it.token == currentToken } ?: run {
            rpc?.closeRPC()
            rpcStateJob?.cancel()
            rpcErrorJob?.cancel()
            DiscordRpc(currentToken).also { created ->
                rpc = created
                rpcStateJob = scope.launch {
                    created.state.collect { state -> onGatewayStateChanged(created, state) }
                }
                rpcErrorJob = scope.launch {
                    created.lastError.collect { error ->
                        if (rpc === created && error != null) {
                            _uiState.update { it.copy(lastError = error) }
                        }
                    }
                }
            }
        }

        try {
            client.updateRPC(
                commonRpc = buildCommonRpc(info, settings),
                status = settings.status,
                applicationId = applicationId,
            )
            _uiState.update { it.copy(isPresenceActive = true) }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            val message = error.message ?: error::class.simpleName.orEmpty()
            InAppLogger.error(TAG, "Unable to update presence: $message")
            _uiState.update { it.copy(lastError = message) }
        }
    }

    private suspend fun onGatewayStateChanged(client: DiscordRpc, state: DiscordGatewayState) {
        if (rpc !== client) return
        _uiState.update {
            it.copy(
                connectionState = state,
                lastError = if (state == DiscordGatewayState.Ready) null else it.lastError,
            )
        }
        when (state) {
            DiscordGatewayState.AuthenticationFailed -> {
                InAppLogger.warn(TAG, "Token Seems to be invalid, Please Login again")
                signOut(expired = true)
            }
            DiscordGatewayState.Failed -> {
                retryAfterEpochMs = nowEpochMs() + FAILURE_COOLDOWN_MS
                stopRpc()
            }
            DiscordGatewayState.Ready -> retryAfterEpochMs = 0L
            else -> Unit
        }
    }

    private suspend fun buildCommonRpc(
        info: DiscordPlaybackInfo,
        settings: DiscordRpcSettings,
    ): CommonDiscordRpc {
        val appName = DiscordRpcConfig.APP_NAME
        val title = info.title.trim()
        val subtitle = info.subtitle?.trim()?.takeIf { it.isNotEmpty() }
        val now = nowEpochMs()

        val time = if (settings.showTimestamps && info.isPlaying) {
            val start = now - info.positionMs.coerceAtLeast(0L)
            val end = if (!info.isLive && info.durationMs > 0L) start + info.durationMs else null
            DiscordRpcTimestamps(start = start, end = end)
        } else {
            null
        }

        val playbackLabel = when {
            info.isLive -> getString(Res.string.discord_rpc_presence_live)
            info.isPaused -> getString(Res.string.discord_rpc_presence_paused)
            else -> getString(Res.string.discord_rpc_presence_playing)
        }

        val largeImage = info.artworkUrl
            ?.takeIf { settings.showPoster && it.isNotBlank() }
            ?.let { DiscordRpcImage.ExternalImage(it) }
        val smallImage = if (settings.showPlaybackState) {
            DiscordRpcImage.DiscordImage(
                if (info.isPaused) DiscordRpcConfig.PAUSE_ASSET else DiscordRpcConfig.PLAY_ASSET,
            )
        } else {
            null
        }

        val buttons = if (settings.showImdbButton && !info.imdbId.isNullOrBlank()) {
            listOf(
                DiscordRpcButton(
                    label = getString(Res.string.discord_rpc_button_imdb),
                    url = "https://www.imdb.com/title/${info.imdbId}/",
                ),
            )
        } else {
            emptyList()
        }

        val pausedState = playbackLabel.takeIf { info.isPaused && !settings.showPlaybackState }

        return if (settings.showTitleAsName) {
            CommonDiscordRpc(
                name = title,
                type = settings.activityType,
                details = subtitle,
                state = pausedState,
                largeImage = largeImage,
                smallImage = smallImage,
                largeText = appName,
                smallText = playbackLabel.takeIf { smallImage != null },
                time = time,
                buttons = buttons,
            )
        } else {
            CommonDiscordRpc(
                name = appName,
                type = settings.activityType,
                details = title,
                state = subtitle ?: pausedState,
                largeImage = largeImage,
                smallImage = smallImage,
                largeText = title,
                smallText = playbackLabel.takeIf { smallImage != null },
                time = time,
                buttons = buttons,
            )
        }
    }

    private suspend fun fetchUser(candidateToken: String): DiscordRpcUser? =
        try {
            val response = httpClient.get("${DiscordRpcConfig.DISCORD_API_URL}/users/@me") {
                header(HttpHeaders.Authorization, candidateToken)
            }
            if (response.status.value !in 200..299) {
                InAppLogger.warn(TAG, "User lookup failed with ${response.status.value}")
                _uiState.update { it.copy(lastError = "Discord login check failed (HTTP ${response.status.value})") }
                null
            } else {
                json.decodeFromString<DiscordRpcUser>(response.bodyAsText())
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            InAppLogger.error(TAG, "User lookup failed: ${error.message.orEmpty()}")
            _uiState.update { it.copy(lastError = error.message ?: "Discord login check failed") }
            null
        }

    private fun signOut(expired: Boolean) {
        testJob?.cancel()
        testJob = null
        playback = playback?.takeIf { !it.isTest }
        stopRpc()
        token = null
        runCatching { DiscordRpcStorage.saveToken(null) }
        DiscordRpcStorage.saveUserPayload(null)
        DiscordExternalAssets.clear()
        DiscordRpcPlatform.clearLoginSession()
        _uiState.update {
            it.copy(
                isLoggedIn = false,
                isLoggingIn = false,
                user = null,
                tokenExpired = expired,
                connectionState = DiscordGatewayState.Idle,
                isPresenceActive = false,
                isTestActive = false,
            )
        }
    }

    private fun stopRpc() {
        updateJob?.cancel()
        updateJob = null
        rpcStateJob?.cancel()
        rpcStateJob = null
        rpcErrorJob?.cancel()
        rpcErrorJob = null
        rpc?.closeRPC()
        rpc = null
        lastSignature = null
        lastStartEpochMs = null
        _uiState.update {
            it.copy(
                connectionState = DiscordGatewayState.Idle,
                isPresenceActive = false,
            )
        }
    }

    private fun nowEpochMs(): Long = Clock.System.now().toEpochMilliseconds()
}
