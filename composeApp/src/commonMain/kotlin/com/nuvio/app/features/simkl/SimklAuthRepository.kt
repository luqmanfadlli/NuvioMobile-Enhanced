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
    private var refreshToken: String? = null
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
        refreshToken = null
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
            scope.launch { startDeviceAuthorization() }
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

    /** AUTH V2 device flow (RFC 8628): `POST /oauth2/device`, then poll `POST /oauth2/token`. */
    private suspend fun startDeviceAuthorization() {
        val response = try {
            SimklApi.client.execute(
                SimklApiRequest(
                    method = SimklHttpMethod.POST,
                    path = SIMKL_OAUTH2_DEVICE_PATH,
                    body = json.encodeToString(
                        SimklDeviceAuthorizationRequest(clientId = SimklConfig.CLIENT_ID),
                    ),
                    requiresAuthentication = false,
                    retryPolicy = SimklRetryPolicy.NEVER,
                ),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            log.w { "Simkl device authorization request failed: ${error.message}" }
            failPinAuthorization()
            return
        }

        val parsed = runCatching { json.decodeFromString<SimklDeviceAuthorizationResponse>(response.body) }
            .getOrNull()
            ?.takeIf { it.userCode.isNotBlank() && it.deviceCode.isNotBlank() }
        if (parsed == null) {
            log.w { "Simkl device authorization response was not usable" }
            failPinAuthorization()
            return
        }

        storedState = storedState.copy(
            pendingAuthorizationState = buildPendingPinState(parsed.userCode),
            pendingAuthorizationStartedAtEpochMs = SimklPlatformClock.nowEpochMs(),
            pendingVerificationUrl = parsed.verificationUriComplete?.takeIf(String::isNotBlank)
                ?: parsed.verificationUri?.takeIf(String::isNotBlank),
        )
        persistMetadata()
        publish(isLoading = false, error = null)
        startDevicePolling(
            userCode = parsed.userCode,
            deviceCode = parsed.deviceCode,
            intervalSeconds = parsed.interval,
            expiresInSeconds = parsed.expiresIn,
        )
    }

    private fun failPinAuthorization(error: SimklAuthError = SimklAuthError.TOKEN_EXCHANGE_FAILED) {
        clearPendingAuthorization()
        persistMetadata()
        publish(isLoading = false, error = error)
    }

    private fun startDevicePolling(
        userCode: String,
        deviceCode: String,
        intervalSeconds: Int?,
        expiresInSeconds: Int?,
    ) {
        pinPollingJob?.cancel()
        pinPollingJob = scope.launch {
            var pollSeconds = (intervalSeconds ?: SIMKL_DEFAULT_PIN_POLL_INTERVAL_SECONDS)
                .coerceAtLeast(SIMKL_DEFAULT_PIN_POLL_INTERVAL_SECONDS)
            val expiresInMillis = (expiresInSeconds ?: SIMKL_DEFAULT_PIN_EXPIRES_IN_SECONDS)
                .coerceAtLeast(pollSeconds) * 1_000L
            val startedAt = SimklPlatformClock.nowEpochMs()

            while (
                pendingPinUserCode(storedState.pendingAuthorizationState) == userCode &&
                SimklPlatformClock.nowEpochMs() - startedAt < expiresInMillis
            ) {
                delay(pollSeconds * 1_000L)
                if (pendingPinUserCode(storedState.pendingAuthorizationState) != userCode) return@launch
                when (pollDeviceToken(deviceCode)) {
                    null -> return@launch
                    SimklDevicePollOutcome.PENDING -> Unit
                    SimklDevicePollOutcome.SLOW_DOWN -> pollSeconds += SIMKL_DEVICE_SLOW_DOWN_INCREMENT_SECONDS
                    SimklDevicePollOutcome.EXPIRED -> break
                    SimklDevicePollOutcome.DENIED,
                    SimklDevicePollOutcome.FAILED,
                    -> {
                        if (pendingPinUserCode(storedState.pendingAuthorizationState) == userCode) {
                            failPinAuthorization()
                        }
                        return@launch
                    }
                }
            }

            if (pendingPinUserCode(storedState.pendingAuthorizationState) == userCode) {
                failPinAuthorization(SimklAuthError.AUTHORIZATION_EXPIRED)
            }
        }
    }

    /** Returns null once the device grant succeeded, otherwise how polling should continue. */
    private suspend fun pollDeviceToken(deviceCode: String): SimklDevicePollOutcome? {
        val response = try {
            SimklApi.client.execute(
                SimklApiRequest(
                    method = SimklHttpMethod.POST,
                    path = SIMKL_OAUTH2_TOKEN_PATH,
                    body = json.encodeToString(
                        SimklTokenRequest(
                            grantType = SIMKL_GRANT_DEVICE_CODE,
                            clientId = SimklConfig.CLIENT_ID,
                            deviceCode = deviceCode,
                        ),
                    ),
                    requiresAuthentication = false,
                    retryPolicy = SimklRetryPolicy.NEVER,
                ),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: SimklApiException) {
            val status = error.status
            if (status == null || status == 429 || status >= 500) {
                log.d { "Simkl device token poll failed: ${error.message}" }
                return SimklDevicePollOutcome.PENDING
            }
            val outcome = simklDevicePollOutcome(error.errorCode)
            if (outcome == SimklDevicePollOutcome.FAILED) {
                log.w { "Simkl device token poll rejected: HTTP ${error.status} ${error.errorCode}" }
            }
            return outcome
        } catch (error: Throwable) {
            log.d { "Simkl device token poll failed: ${error.message}" }
            return SimklDevicePollOutcome.PENDING
        }

        val token = parseTokenResponse(response.body) ?: return SimklDevicePollOutcome.FAILED
        authorizationMutex.withLock { applyTokens(token) }
        return null
    }

    fun pendingAuthorizationUrl(): String? {
        ensureLoaded()
        val state = storedState.pendingAuthorizationState?.takeIf(String::isNotBlank) ?: return null
        if (pendingPinUserCode(state) != null) return pendingDeviceVerificationUrl()
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

    /** The page the user opens to approve the device code; prefers `verification_uri_complete`. */
    fun pendingDeviceVerificationUrl(): String {
        ensureLoaded()
        return storedState.pendingVerificationUrl?.takeIf(String::isNotBlank) ?: SIMKL_PIN_VERIFICATION_URL
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
        // Revoking either token of an AUTH V2 grant revokes both (RFC 7009); best effort only.
        val tokenToRevoke = refreshToken ?: accessToken
        if (!tokenToRevoke.isNullOrBlank()) scope.launch { revokeToken(tokenToRevoke) }
        accessToken = null
        refreshToken = null
        SimklAuthStorage.saveAccessToken(null)
        SimklAuthStorage.saveRefreshToken(null)
        clearPendingAuthorization()
        storedState = SimklStoredAuthState()
        persistMetadata()
        SimklSyncRepository.clearLocalState()
        publish(error = null)
    }

    /**
     * Returns a usable access token. AUTH V2 access tokens last seven days, so an expired (or
     * nearly expired) token is transparently refreshed with the stored refresh token first.
     */
    internal suspend fun authorizedAccessToken(): String? {
        ensureLoaded()
        val token = accessToken?.takeIf(String::isNotBlank)
        if (token != null && !isAccessTokenExpired()) return token
        if (token == null && refreshToken.isNullOrBlank()) return null
        return refreshAccessToken(rejectedToken = token)
    }

    /**
     * Exchanges the stored refresh token for a new access token (`grant_type=refresh_token`).
     *
     * Simkl refresh tokens do not rotate and slide for 180 days, but each refresh immediately
     * invalidates the previous access token, so refreshes are serialized and a caller holding a
     * token that was already replaced simply receives the current one.
     */
    internal suspend fun refreshAccessToken(rejectedToken: String?): String? = refreshMutex.withLock {
        ensureLoaded()
        val current = accessToken?.takeIf(String::isNotBlank)
        if (current != null && current != rejectedToken && !isAccessTokenExpired()) {
            return@withLock current
        }
        val storedRefreshToken = refreshToken?.takeIf(String::isNotBlank)
        if (storedRefreshToken == null) {
            // Legacy AUTH V1 tokens (or a lost refresh token) cannot be refreshed: re-authorize.
            invalidateCredentials(
                if (rejectedToken != null && !isAccessTokenExpired()) {
                    SimklAuthError.AUTHORIZATION_REVOKED
                } else {
                    SimklAuthError.AUTHORIZATION_EXPIRED
                },
            )
            return@withLock null
        }

        val response = try {
            SimklApi.client.execute(
                SimklApiRequest(
                    method = SimklHttpMethod.POST,
                    path = SIMKL_OAUTH2_TOKEN_PATH,
                    body = json.encodeToString(
                        SimklTokenRequest(
                            grantType = SIMKL_GRANT_REFRESH_TOKEN,
                            clientId = SimklConfig.CLIENT_ID,
                            refreshToken = storedRefreshToken,
                        ),
                    ),
                    requiresAuthentication = false,
                    retryPolicy = SimklRetryPolicy.NEVER,
                ),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: SimklApiException) {
            val status = error.status
            if (isSimklDefinitiveTokenRejection(status, error.errorCode)) {
                log.w { "Simkl token refresh rejected: HTTP $status ${error.errorCode}" }
                invalidateCredentials(SimklAuthError.AUTHORIZATION_REVOKED)
            } else {
                log.w { "Simkl token refresh failed: ${error.message}" }
                markAccessTokenStale(rejectedToken)
            }
            return@withLock null
        } catch (error: Throwable) {
            log.w { "Simkl token refresh failed: ${error.message}" }
            markAccessTokenStale(rejectedToken)
            return@withLock null
        }

        val token = parseTokenResponse(response.body)
        if (token == null) {
            log.w { "Simkl token refresh response was not usable" }
            markAccessTokenStale(rejectedToken)
            return@withLock null
        }
        storeTokens(token)
        publish(error = null)
        token.accessToken
    }

    internal fun onUnauthorizedResponse() {
        invalidateCredentials(SimklAuthError.AUTHORIZATION_REVOKED)
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

            val request = SimklTokenRequest(
                grantType = SIMKL_GRANT_AUTHORIZATION_CODE,
                clientId = SimklConfig.CLIENT_ID,
                code = callback.code,
                redirectUri = SimklConfig.REDIRECT_URI,
                codeVerifier = verifier,
            )
            val response = try {
                SimklApi.client.execute(
                    SimklApiRequest(
                        method = SimklHttpMethod.POST,
                        path = SIMKL_OAUTH2_TOKEN_PATH,
                        body = json.encodeToString(request),
                        requiresAuthentication = false,
                        retryPolicy = SimklRetryPolicy.NEVER,
                    ),
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                log.w { "Simkl token exchange failed: ${error.message}" }
                clearPendingAuthorization()
                persistMetadata()
                publish(isLoading = false, error = SimklAuthError.TOKEN_EXCHANGE_FAILED)
                return@withLock
            }
            val token = parseTokenResponse(response.body)
            if (token == null) {
                clearPendingAuthorization()
                persistMetadata()
                publish(isLoading = false, error = SimklAuthError.INVALID_TOKEN_RESPONSE)
                return@withLock
            }

            applyTokens(token)
        }

    private fun parseTokenResponse(body: String): SimklTokenResponse? =
        runCatching { json.decodeFromString<SimklTokenResponse>(body) }
            .getOrNull()
            ?.takeIf { it.accessToken.isNotBlank() }

    /** Persists an `/oauth2/token` response; the refresh token is kept when one isn't returned. */
    private fun storeTokens(token: SimklTokenResponse) {
        accessToken = token.accessToken
        SimklAuthStorage.saveAccessToken(token.accessToken)
        token.refreshToken?.takeIf(String::isNotBlank)?.let { newRefreshToken ->
            refreshToken = newRefreshToken
            SimklAuthStorage.saveRefreshToken(newRefreshToken)
        }
        storedState = storedState.copy(
            tokenExpiresAtEpochMs = token.expiresIn
                ?.takeIf { seconds -> seconds > 0L }
                ?.let { seconds -> SimklPlatformClock.nowEpochMs() + seconds * 1_000L },
        )
        persistMetadata()
    }

    private fun isAccessTokenExpired(): Boolean {
        val expiresAt = storedState.tokenExpiresAtEpochMs ?: return false
        return SimklPlatformClock.nowEpochMs() >= expiresAt - TOKEN_EXPIRY_SKEW_MS
    }

    /**
     * A refresh failed for a transient reason: keep the refresh token so the session survives, but
     * make sure a token the server already rejected is refreshed before it is sent again.
     */
    private fun markAccessTokenStale(rejectedToken: String?) {
        if (rejectedToken == null || rejectedToken != accessToken) return
        storedState = storedState.copy(tokenExpiresAtEpochMs = 0L)
        persistMetadata()
    }

    private suspend fun revokeToken(token: String) {
        try {
            SimklApi.client.execute(
                SimklApiRequest(
                    method = SimklHttpMethod.POST,
                    path = SIMKL_OAUTH2_REVOKE_PATH,
                    body = json.encodeToString(
                        SimklRevokeRequest(token = token, clientId = SimklConfig.CLIENT_ID),
                    ),
                    requiresAuthentication = false,
                    retryPolicy = SimklRetryPolicy.NEVER,
                ),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            log.d { "Simkl token revocation failed: ${error.message}" }
        }
    }

    private suspend fun applyTokens(token: SimklTokenResponse) {
        clearPendingAuthorization()
        storeTokens(token)
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
        refreshToken = SimklAuthStorage.loadRefreshToken()?.takeIf(String::isNotBlank)
        // An expired AUTH V2 access token is refreshed on next use; without a refresh token the
        // session is over and the user has to reconnect.
        if (accessToken != null && refreshToken == null && isAccessTokenExpired()) {
            accessToken = null
            SimklAuthStorage.saveAccessToken(null)
            storedState = SimklStoredAuthState(authenticationMethod = storedState.authenticationMethod)
            persistMetadata()
        }
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
        refreshToken = null
        SimklAuthStorage.saveAccessToken(null)
        SimklAuthStorage.saveRefreshToken(null)
        clearPendingAuthorization()
        storedState = SimklStoredAuthState(authenticationMethod = storedState.authenticationMethod)
        persistMetadata()
        SimklSyncRepository.clearLocalState()
        publish(isLoading = false, error = error)
    }

    private fun clearPendingAuthorization() {
        SimklAuthStorage.saveCodeVerifier(null)
        storedState = storedState.copy(
            pendingAuthorizationState = null,
            pendingAuthorizationStartedAtEpochMs = null,
            pendingVerificationUrl = null,
        )
    }

    private fun persistMetadata() {
        SimklAuthStorage.saveMetadataPayload(json.encodeToString(storedState))
    }

    private fun publish(
        isLoading: Boolean = _uiState.value.isLoading,
        error: SimklAuthError? = _uiState.value.error,
    ) {
        val authenticated = !accessToken.isNullOrBlank() || !refreshToken.isNullOrBlank()
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
}

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
