package com.nuvio.app.features.simkl

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class SimklAuthenticationMethod(val storageValue: String) {
    BROWSER_REDIRECT("browser_redirect"),
    DEVICE_CODE("device_code"),
    ;

    companion object {
        fun fromStorageValue(value: String?): SimklAuthenticationMethod =
            entries.firstOrNull { it.storageValue.equals(value, ignoreCase = true) } ?: BROWSER_REDIRECT
    }
}

internal const val SIMKL_PIN_VERIFICATION_URL = "https://simkl.com/pin"

internal const val SIMKL_DEFAULT_PIN_POLL_INTERVAL_SECONDS = 5
internal const val SIMKL_DEFAULT_PIN_EXPIRES_IN_SECONDS = 900

/** RFC 8628 section 3.5: a `slow_down` response adds five seconds to the polling interval. */
internal const val SIMKL_DEVICE_SLOW_DOWN_INCREMENT_SECONDS = 5

internal const val SIMKL_GRANT_AUTHORIZATION_CODE = "authorization_code"
internal const val SIMKL_GRANT_REFRESH_TOKEN = "refresh_token"
internal const val SIMKL_GRANT_DEVICE_CODE = "urn:ietf:params:oauth:grant-type:device_code"

internal const val SIMKL_PIN_STATE_PREFIX = "pin:"

internal fun buildPendingPinState(userCode: String): String = "$SIMKL_PIN_STATE_PREFIX$userCode"

internal fun pendingPinUserCode(state: String?): String? =
    state?.takeIf { it.startsWith(SIMKL_PIN_STATE_PREFIX) }?.removePrefix(SIMKL_PIN_STATE_PREFIX)

/** Request body for `POST /oauth2/device`. */
@Serializable
internal data class SimklDeviceAuthorizationRequest(
    @SerialName("client_id") val clientId: String,
    val scope: String = SIMKL_OAUTH2_SCOPE,
)

/**
 * Response of `POST /oauth2/device` (RFC 8628 section 3.2).
 *
 * [deviceCode] is a secret: it is only kept in memory and must never be shown or logged.
 */
@Serializable
internal data class SimklDeviceAuthorizationResponse(
    @SerialName("device_code") val deviceCode: String = "",
    @SerialName("user_code") val userCode: String = "",
    @SerialName("verification_uri") val verificationUri: String? = null,
    @SerialName("verification_uri_complete") val verificationUriComplete: String? = null,
    @SerialName("expires_in") val expiresIn: Int? = null,
    val interval: Int? = null,
)

/**
 * Request body for `POST /oauth2/token`. Simkl accepts JSON as long as every value is a plain
 * string, so only the fields relevant to the grant are populated and nulls are omitted.
 */
@Serializable
internal data class SimklTokenRequest(
    @SerialName("grant_type") val grantType: String,
    @SerialName("client_id") val clientId: String,
    val code: String? = null,
    @SerialName("redirect_uri") val redirectUri: String? = null,
    @SerialName("code_verifier") val codeVerifier: String? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("device_code") val deviceCode: String? = null,
)

/** Shared success response of every `POST /oauth2/token` grant (RFC 6749 section 5.1). */
@Serializable
internal data class SimklTokenResponse(
    @SerialName("access_token") val accessToken: String = "",
    @SerialName("token_type") val tokenType: String? = null,
    val scope: String? = null,
    @SerialName("expires_in") val expiresIn: Long? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
)

/** Request body for `POST /oauth2/revoke` (RFC 7009). */
@Serializable
internal data class SimklRevokeRequest(
    val token: String,
    @SerialName("client_id") val clientId: String,
)

internal enum class SimklDevicePollOutcome {
    PENDING,
    SLOW_DOWN,
    EXPIRED,
    DENIED,
    FAILED,
}

/** Maps an `/oauth2/token` device-grant error code to how polling should continue. */
internal fun simklDevicePollOutcome(errorCode: String?): SimklDevicePollOutcome = when (errorCode) {
    "authorization_pending" -> SimklDevicePollOutcome.PENDING
    "slow_down" -> SimklDevicePollOutcome.SLOW_DOWN
    "expired_token" -> SimklDevicePollOutcome.EXPIRED
    "access_denied" -> SimklDevicePollOutcome.DENIED
    else -> SimklDevicePollOutcome.FAILED
}

/**
 * Whether an `/oauth2/token` refresh failure means the grant is gone for good (revoked or expired
 * refresh token, or a client that is not registered for AUTH V2). Anything else is transient and
 * must not throw away the 180-day refresh token.
 */
internal fun isSimklDefinitiveTokenRejection(status: Int?, errorCode: String?): Boolean =
    status == 401 || errorCode in setOf("invalid_grant", "invalid_client", "unauthorized_client")
