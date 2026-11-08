package com.nuvio.app.features.simkl

import co.touchlab.kermit.Logger
import com.nuvio.app.features.tracking.TrackingAuthProvider
import com.nuvio.app.features.tracking.TrackingCapability
import com.nuvio.app.features.tracking.TrackingProviderDescriptor
import com.nuvio.app.features.tracking.TrackingProviderId
import com.nuvio.app.features.tracking.TrackingProviderRegistry
import com.nuvio.app.features.tracking.TrackingRefreshIntent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object SimklAuthRepository : TrackingAuthProvider {
    private val log = Logger.withTag("SimklAuth")
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val authorizationMutex = Mutex()
    private val refreshMutex = Mutex()

    private val _uiState = MutableStateFlow(SimklAuthUiState())
    val uiState: StateFlow<SimklAuthUiState> = _uiState.asStateFlow()

    private val _isAuthenticated = MutableStateFlow(false)
    override val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

    override val descriptor = TrackingProviderDescriptor(
        id = TrackingProviderId.SIMKL,
        displayName = "Simkl",
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

    private var hasLoaded = false
    private var storedState = SimklStoredAuthState()
    private var accessToken: String? = null
    private var authenticationMethod = SimklAuthenticationMethod.BROWSER_REDIRECT
    private var pinPollingJob: Job? = null

    init {
        TrackingProviderRegistry.register(this)
    }

    override fun ensureLoaded() {
        if (hasLoaded) return
        loadFromDisk()
    }

    override fun onProfileChanged() {
        loadFromDisk()
    }

    override fun clearLocalState() {
        hasLoaded = false
        pinPollingJob?.cancel()
        pinPollingJob = null
        storedState = SimklStoredAuthState()
        accessToken = null
        SimklAuthStorage.saveAccessToken(null)
        SimklAuthStorage.saveRefreshToken(null)
        authenticationMethod = SimklAuthenticationMethod.BROWSER_REDIRECT
        publish()
    }

    fun selectedAuthenticationMethod(): SimklAuthenticationMethod {
        ensureLoaded()
        return authenticationMethod
    }

    fun setAuthenticationMethod(method: SimklAuthenticationMethod) {
        ensureLoaded()
        if (authenticationMethod == method) return
        pinPollingJob?.cancel()
        pinPollingJob = null
        authenticationMethod = method
        storedState = storedState.copy(authenticationMethod = method.storageValue)
        clearPendingAuthorization()
        persistMetadata()
        publish(isLoading = false, error = null)
    }

    fun pendingDeviceUserCode(): String? {
        ensureLoaded()
        return pendingPinUserCode(storedState.pendingAuthorizationState)
    }

    override fun removeStoredProfile(profileId: Int) {
        SimklAuthStorage.removeProfile(profileId)
    }

    fun snapshot(): SimklAuthUiState {
        ensureLoaded()
        return uiState.value
    }

    fun hasRequiredCredentials(): Boolean = SimklConfig.CLIENT_ID.isNotBlank()

    fun onConnectRequested(): String? {
        ensureLoaded()
        if (!hasRequiredCredentials()) {
            publish(error = SimklAuthError.MISSING_CLIENT_ID)
            return null
        }

        pinPollingJob?.cancel()
        pinPollingJob = null
        clearPendingAuthorization()

        if (authenticationMethod == SimklAuthenticationMethod.DEVICE_CODE) {
            publish(isLoading = true, error = null)
            scope.launch { startPinAuthorization() }
            return null
        }

        val material = generateSimklPkceMaterial()
        SimklAuthStorage.saveCodeVerifier(material.verifier)
        storedState = storedState.copy(
            pendingAuthorizationState = material.state,
            pendingAuthorizationStartedAtEpochMs = SimklPlatformClock.nowEpochMs(),
        )
        persistMetadata()
        publish(error = null)
        return authorizationUrl(material)
    }

    private suspend fun startPinAuthorization() {
        val material = generateSimklPkceMaterial()
        SimklAuthStorage.saveCodeVerifier(material.verifier)
        val response = try {
            SimklApi.authClient.execute(SimklApiRequest(method = SimklHttpMethod.POST, path = "/oauth2/device",
                body = simklFormBody(mapOf("client_id" to SimklConfig.CLIENT_ID, "scope" to SIMKL_AUTH_SCOPE,
                    "code_challenge" to material.challenge, "code_challenge_method" to "S256")),
                contentType = SIMKL_FORM_CONTENT_TYPE, requiresAuthentication = false, retryPolicy = SimklRetryPolicy.NEVER))
        } catch (error: CancellationException) { throw error }
        catch (error: Throwable) { log.w { "Simkl device authorization failed: ${error.message}" }; failPinAuthorization(); return }
        val parsed = runCatching { json.decodeFromString<SimklPinResponse>(response.body) }.getOrNull()
            ?.takeIf { it.userCode.isNotBlank() && it.deviceCode.isNotBlank() }
        if (parsed == null) { log.w { "Simkl device authorization response was not usable" }; failPinAuthorization(); return }
        storedState = storedState.copy(pendingAuthorizationState = buildPendingPinState(parsed.userCode, parsed.deviceCode),
            pendingAuthorizationStartedAtEpochMs = SimklPlatformClock.nowEpochMs())
        persistMetadata(); publish(isLoading = false, error = null)
        startPinPolling(parsed.userCode, parsed.deviceCode, parsed.interval, parsed.expiresIn)
    }

    private fun failPinAuthorization() {
        clearPendingAuthorization()
        persistMetadata()
        publish(isLoading = false, error = SimklAuthError.TOKEN_EXCHANGE_FAILED)
    }

    private fun startPinPolling(userCode: String, deviceCode: String, intervalSeconds: Int?, expiresInSeconds: Int?) {
        pinPollingJob?.cancel()
        pinPollingJob = scope.launch {
            var pollSeconds = (intervalSeconds ?: SIMKL_DEFAULT_PIN_POLL_INTERVAL_SECONDS).coerceAtLeast(SIMKL_DEFAULT_PIN_POLL_INTERVAL_SECONDS)
            val expiresInMillis = (expiresInSeconds ?: SIMKL_DEFAULT_PIN_EXPIRES_IN_SECONDS).coerceAtLeast(pollSeconds) * 1_000L
            val startedAt = SimklPlatformClock.nowEpochMs()
            while (pendingPinUserCode(storedState.pendingAuthorizationState) == userCode && SimklPlatformClock.nowEpochMs() - startedAt < expiresInMillis) {
                delay(pollSeconds * 1_000L)
                when (pollDeviceToken(deviceCode)) {
                    DevicePollResult.AUTHORIZED -> return@launch
                    DevicePollResult.PENDING -> Unit
                    DevicePollResult.SLOW_DOWN -> pollSeconds += SIMKL_DEVICE_POLL_SLOW_DOWN_SECONDS
                    DevicePollResult.EXPIRED -> { clearPendingAuthorization(); persistMetadata(); publish(isLoading = false, error = SimklAuthError.AUTHORIZATION_EXPIRED); return@launch }
                    DevicePollResult.FAILED -> { failPinAuthorization(); return@launch }
                }
            }
            if (pendingPinUserCode(storedState.pendingAuthorizationState) == userCode) {
                clearPendingAuthorization(); persistMetadata(); publish(isLoading = false, error = SimklAuthError.AUTHORIZATION_EXPIRED)
            }
        }
    }

    private suspend fun pollDeviceToken(deviceCode: String): DevicePollResult {
        val verifier = SimklAuthStorage.loadCodeVerifier()?.takeIf(String::isNotBlank) ?: return DevicePollResult.FAILED
        return try {
            val response = SimklApi.authClient.execute(SimklApiRequest(method = SimklHttpMethod.POST, path = "/oauth2/token",
                body = simklFormBody(mapOf("grant_type" to SIMKL_DEVICE_GRANT_TYPE, "client_id" to SimklConfig.CLIENT_ID,
                    "device_code" to deviceCode, "code_verifier" to verifier)), contentType = SIMKL_FORM_CONTENT_TYPE,
                requiresAuthentication = false, retryPolicy = SimklRetryPolicy.NEVER))
            val token = runCatching { json.decodeFromString<SimklTokenResponse>(response.body) }.getOrNull()
                ?.takeIf { it.accessToken.isNotBlank() } ?: return DevicePollResult.FAILED
            applyAccessToken(token.accessToken, token.expiresIn, token.refreshToken); DevicePollResult.AUTHORIZED
        } catch (error: CancellationException) { throw error }
        catch (error: SimklApiException) {
            when (error.errorCode) {
                "authorization_pending" -> DevicePollResult.PENDING
                "slow_down" -> DevicePollResult.SLOW_DOWN
                "expired_token", "access_denied" -> DevicePollResult.EXPIRED
                else -> { log.w { "Simkl device token poll failed: status=${error.status} code=${error.errorCode}" }; DevicePollResult.FAILED }
            }
        } catch (error: Throwable) { log.w { "Simkl device token poll failed: ${error.message}" }; DevicePollResult.FAILED }
    }

    fun pendingAuthorizationUrl(): String? {
        ensureLoaded()
        val state = storedState.pendingAuthorizationState?.takeIf(String::isNotBlank) ?: return null
        if (pendingPinUserCode(state) != null) return SIMKL_PIN_VERIFICATION_URL
        val verifier = SimklAuthStorage.loadCodeVerifier()?.takeIf(String::isNotBlank) ?: run {
            clearPendingAuthorization()
            persistMetadata()
            publish(error = SimklAuthError.AUTHORIZATION_EXPIRED)
            return null
        }
        if (isSimklAuthorizationExpired(
                startedAtEpochMs = storedState.pendingAuthorizationStartedAtEpochMs,
                nowEpochMs = SimklPlatformClock.nowEpochMs(),
            )
        ) {
            clearPendingAuthorization()
            persistMetadata()
            publish(error = SimklAuthError.AUTHORIZATION_EXPIRED)
            return null
        }
        return authorizationUrl(
            SimklPkceMaterial(
                verifier = verifier,
                challenge = SimklPkceCrypto.sha256(verifier.encodeToByteArray()).base64UrlWithoutPadding(),
                state = state,
            ),
        )
    }

    fun onCancelAuthorization() {
        ensureLoaded()
        pinPollingJob?.cancel()
        pinPollingJob = null
        clearPendingAuthorization()
        persistMetadata()
        publish(isLoading = false, error = null)
    }

    override fun handleAuthCallback(url: String): Boolean {
        ensureLoaded()
        if (pendingPinUserCode(storedState.pendingAuthorizationState) != null) return false
        return when (val callback = parseSimklAuthCallback(url, SimklConfig.REDIRECT_URI)) {
            SimklAuthCallback.NotSimkl -> false
            SimklAuthCallback.Invalid -> {
                clearPendingAuthorization()
                persistMetadata()
                publish(error = SimklAuthError.INVALID_CALLBACK)
                true
            }
            is SimklAuthCallback.AuthorizationCode -> {
                scope.launch { completeAuthorization(callback) }
                true
            }
        }
    }

    fun onDisconnectRequested() {
        ensureLoaded()
        accessToken = null
        SimklAuthStorage.saveAccessToken(null)
        SimklAuthStorage.saveRefreshToken(null)
        clearPendingAuthorization()
        storedState = SimklStoredAuthState()
        persistMetadata()
        SimklSyncRepository.clearLocalState()
        publish(error = null)
    }

    internal fun authorizedAccessToken(): String? {
        ensureLoaded()
        val token = accessToken?.takeIf(String::isNotBlank) ?: return null
        val expiresAt = storedState.tokenExpiresAtEpochMs
        if (expiresAt != null && SimklPlatformClock.nowEpochMs() >= expiresAt - TOKEN_EXPIRY_SKEW_MS) {
            invalidateCredentials(SimklAuthError.AUTHORIZATION_EXPIRED)
            return null
        }
        return token
    }

    internal fun onUnauthorizedResponse() {
        invalidateCredentials(SimklAuthError.AUTHORIZATION_REVOKED)
    }

    internal suspend fun refreshAccessTokenIfNeeded(): Boolean = refreshAccessToken(force = false)

    internal suspend fun refreshAccessToken(force: Boolean): Boolean = refreshMutex.withLock {
        ensureLoaded()
        val refreshToken = SimklAuthStorage.loadRefreshToken()?.takeIf(String::isNotBlank) ?: return@withLock false
        val expiry = storedState.tokenExpiresAtEpochMs
        if (!force && expiry != null && SimklPlatformClock.nowEpochMs() < expiry - SIMKL_REFRESH_EARLY_WINDOW_MS) return@withLock true
        try {
            val response = SimklApi.authClient.execute(SimklApiRequest(method = SimklHttpMethod.POST, path = "/oauth2/token",
                body = simklFormBody(mapOf("grant_type" to "refresh_token", "client_id" to SimklConfig.CLIENT_ID, "refresh_token" to refreshToken)),
                contentType = SIMKL_FORM_CONTENT_TYPE, requiresAuthentication = false, retryPolicy = SimklRetryPolicy.NEVER))
            val token = runCatching { json.decodeFromString<SimklTokenResponse>(response.body) }.getOrNull()
                ?.takeIf { it.accessToken.isNotBlank() } ?: return@withLock false
            accessToken = token.accessToken; SimklAuthStorage.saveAccessToken(token.accessToken)
            token.refreshToken?.takeIf(String::isNotBlank)?.let(SimklAuthStorage::saveRefreshToken)
            storedState = storedState.copy(tokenExpiresAtEpochMs = token.expiresIn?.takeIf { it > 0L }?.let { SimklPlatformClock.nowEpochMs() + it * 1_000L })
            persistMetadata(); publish(isLoading = false, error = null); true
        } catch (error: CancellationException) { throw error }
        catch (error: Throwable) { val e = error as? SimklApiException; log.w { "Simkl token refresh failed: status=${e?.status} code=${e?.errorCode ?: "transport_failure"}" }; false }
    }

    suspend fun refreshUserSettings(): String? {
        authorizedAccessToken() ?: return null
        return if (fetchAndStoreUserSettings()) storedState.username else null
    }

    internal suspend fun synchronizeUserSettings(activityWatermark: String?) {
        authorizedAccessToken() ?: return
        when (simklSettingsRefreshAction(storedState, activityWatermark)) {
            SimklSettingsRefreshAction.NONE -> Unit
            SimklSettingsRefreshAction.RECORD_WATERMARK -> {
                storedState = storedState.copy(settingsActivityWatermark = activityWatermark)
                persistMetadata()
            }
            SimklSettingsRefreshAction.FETCH -> {
                fetchAndStoreUserSettings(activityWatermark)
            }
        }
    }

    private suspend fun completeAuthorization(callback: SimklAuthCallback.AuthorizationCode) =
        authorizationMutex.withLock {
            publish(isLoading = true, error = null)
            val expectedState = storedState.pendingAuthorizationState
            val verifier = SimklAuthStorage.loadCodeVerifier()
            val isExpired = isSimklAuthorizationExpired(
                startedAtEpochMs = storedState.pendingAuthorizationStartedAtEpochMs,
                nowEpochMs = SimklPlatformClock.nowEpochMs(),
            )
            if (expectedState.isNullOrBlank() || verifier.isNullOrBlank() || isExpired) {
                clearPendingAuthorization()
                persistMetadata()
                publish(isLoading = false, error = SimklAuthError.AUTHORIZATION_EXPIRED)
                return@withLock
            }
            if (!constantTimeEquals(callback.state, expectedState)) {
                clearPendingAuthorization()
                persistMetadata()
                publish(isLoading = false, error = SimklAuthError.INVALID_CALLBACK_STATE)
                return@withLock
            }

            val requestBody = simklFormBody(mapOf("grant_type" to "authorization_code", "code" to callback.code,
                "client_id" to SimklConfig.CLIENT_ID, "code_verifier" to verifier,
                "redirect_uri" to SimklConfig.REDIRECT_URI, "scope" to SIMKL_AUTH_SCOPE))
            val response = try {
                SimklApi.client.execute(
                    SimklApiRequest(
                        method = SimklHttpMethod.POST,
                        path = "/oauth2/token",
                        body = requestBody,
                        contentType = SIMKL_FORM_CONTENT_TYPE,
                        requiresAuthentication = false,
                        retryPolicy = SimklRetryPolicy.NEVER,
                    ),
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                val apiError = error as? SimklApiException
                log.w { "Simkl token exchange failed: status=${apiError?.status} code=${apiError?.errorCode ?: "transport_failure"} message=${error.message}" }
                clearPendingAuthorization()
                persistMetadata()
                publish(isLoading = false, error = SimklAuthError.TOKEN_EXCHANGE_FAILED)
                return@withLock
            }
            val token = runCatching { json.decodeFromString<SimklTokenResponse>(response.body) }
                .getOrNull()
                ?.takeIf { it.accessToken.isNotBlank() }
            if (token == null) {
                clearPendingAuthorization()
                persistMetadata()
                publish(isLoading = false, error = SimklAuthError.INVALID_TOKEN_RESPONSE)
                return@withLock
            }

            applyAccessToken(token.accessToken, token.expiresIn, token.refreshToken)
        }

    private suspend fun applyAccessToken(token: String, expiresInSeconds: Long?, refreshToken: String?) {
        accessToken = token
        SimklAuthStorage.saveAccessToken(token)
        refreshToken?.takeIf(String::isNotBlank)?.let(SimklAuthStorage::saveRefreshToken)
        clearPendingAuthorization()
        storedState = storedState.copy(
            tokenExpiresAtEpochMs = expiresInSeconds
                ?.takeIf { seconds -> seconds > 0L }
                ?.let { seconds -> SimklPlatformClock.nowEpochMs() + seconds * 1_000L },
        )
        persistMetadata()
        publish(isLoading = false, error = null)
        fetchAndStoreUserSettings()
        SimklSyncRepository.refreshAsync(
            intent = TrackingRefreshIntent.INVALIDATED,
            origin = SimklRefreshOrigin.AUTHORIZATION,
        )
    }

    private suspend fun fetchAndStoreUserSettings(activityWatermark: String? = null): Boolean {
        val response = try {
            SimklApi.client.execute(
                SimklApiRequest(
                    method = SimklHttpMethod.POST,
                    path = "/users/settings",
                ),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            log.w { "Failed to fetch Simkl user settings: ${error.message}" }
            return false
        }
        val settings = runCatching { json.decodeFromString<SimklUserSettingsResponse>(response.body) }
            .getOrNull() ?: return false
        storedState = storedState.copy(
            username = settings.user?.name,
            accountId = settings.account?.id,
            hasFetchedUserSettings = true,
            settingsActivityWatermark = activityWatermark ?: storedState.settingsActivityWatermark,
        )
        persistMetadata()
        publish(error = null)
        return true
    }

    private fun loadFromDisk() {
        hasLoaded = true
        storedState = SimklAuthStorage.loadMetadataPayload()
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.let { payload ->
                runCatching { json.decodeFromString<SimklStoredAuthState>(payload) }
                    .onFailure { error -> log.w { "Failed to parse Simkl auth metadata: ${error.message}" } }
                    .getOrNull()
            }
            ?: SimklStoredAuthState()
        authenticationMethod = SimklAuthenticationMethod.fromStorageValue(storedState.authenticationMethod)
        accessToken = SimklAuthStorage.loadAccessToken()?.takeIf(String::isNotBlank)
        if (accessToken != null && storedState.tokenExpiresAtEpochMs?.let { expiresAt ->
                SimklPlatformClock.nowEpochMs() >= expiresAt - TOKEN_EXPIRY_SKEW_MS
            } == true
        ) { accessToken = null; SimklAuthStorage.saveAccessToken(null) }
        if (storedState.hasPendingAuthorization && isSimklAuthorizationExpired(
                startedAtEpochMs = storedState.pendingAuthorizationStartedAtEpochMs,
                nowEpochMs = SimklPlatformClock.nowEpochMs(),
            )
        ) {
            clearPendingAuthorization()
            persistMetadata()
        }
        publish(error = null)
    }

    private fun invalidateCredentials(error: SimklAuthError) {
        accessToken = null
        SimklAuthStorage.saveAccessToken(null)
        SimklAuthStorage.saveRefreshToken(null)
        clearPendingAuthorization()
        storedState = SimklStoredAuthState()
        persistMetadata()
        SimklSyncRepository.clearLocalState()
        publish(isLoading = false, error = error)
    }

    private fun clearPendingAuthorization() {
        SimklAuthStorage.saveCodeVerifier(null)
        storedState = storedState.copy(
            pendingAuthorizationState = null,
            pendingAuthorizationStartedAtEpochMs = null,
        )
    }

    private fun persistMetadata() {
        SimklAuthStorage.saveMetadataPayload(json.encodeToString(storedState))
    }

    private fun publish(
        isLoading: Boolean = _uiState.value.isLoading,
        error: SimklAuthError? = _uiState.value.error,
    ) {
        val authenticated = !accessToken.isNullOrBlank()
        _isAuthenticated.value = authenticated
        _uiState.value = SimklAuthUiState(
            mode = when {
                authenticated -> SimklConnectionMode.CONNECTED
                storedState.hasPendingAuthorization -> SimklConnectionMode.AWAITING_APPROVAL
                else -> SimklConnectionMode.DISCONNECTED
            },
            credentialsConfigured = hasRequiredCredentials(),
            isLoading = isLoading,
            username = storedState.username,
            accountId = storedState.accountId,
            tokenExpiresAtEpochMs = storedState.tokenExpiresAtEpochMs,
            pendingAuthorizationStartedAtEpochMs = storedState.pendingAuthorizationStartedAtEpochMs,
            error = error,
        )
    }

    private fun authorizationUrl(material: SimklPkceMaterial): String =
        buildSimklAuthorizationUrl(
            clientId = SimklConfig.CLIENT_ID,
            redirectUri = SimklConfig.REDIRECT_URI,
            appName = SimklConfig.APP_NAME,
            appVersion = simklAppVersion,
            material = material,
        )

    private const val TOKEN_EXPIRY_SKEW_MS = 60_000L
    private const val SIMKL_REFRESH_EARLY_WINDOW_MS = 24L * 60L * 60L * 1_000L
    private const val SIMKL_AUTH_SCOPE = "media:read media:write"
    private const val SIMKL_FORM_CONTENT_TYPE = "application/x-www-form-urlencoded"
    private const val SIMKL_DEVICE_GRANT_TYPE = "urn:ietf:params:oauth:grant-type:device_code"
    private const val SIMKL_DEVICE_POLL_SLOW_DOWN_SECONDS = 5
}

@Serializable
private data class SimklTokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String? = null,
    val scope: String? = null,
    @SerialName("expires_in") val expiresIn: Long? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
)

private enum class DevicePollResult { AUTHORIZED, PENDING, SLOW_DOWN, EXPIRED, FAILED }

@Serializable
private data class SimklUserSettingsResponse(
    val user: SimklUser? = null,
    val account: SimklAccount? = null,
)

@Serializable
private data class SimklUser(
    val name: String? = null,
)

@Serializable
private data class SimklAccount(
    val id: Long? = null,
)
