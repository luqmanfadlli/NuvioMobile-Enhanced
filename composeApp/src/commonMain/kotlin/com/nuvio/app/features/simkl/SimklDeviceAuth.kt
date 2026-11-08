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

internal const val SIMKL_PIN_STATE_PREFIX = "device:"

internal fun buildPendingPinState(userCode: String, deviceCode: String): String = "$SIMKL_PIN_STATE_PREFIX$userCode|$deviceCode"

internal fun pendingPinUserCode(state: String?): String? =
    state?.takeIf { it.startsWith(SIMKL_PIN_STATE_PREFIX) }?.removePrefix(SIMKL_PIN_STATE_PREFIX)?.substringBefore('|')?.takeIf(String::isNotBlank)

@Serializable
internal data class SimklPinResponse(
    @SerialName("device_code") val deviceCode: String = "",
    @SerialName("user_code") val userCode: String = "",
    @SerialName("verification_uri") val verificationUri: String? = null,
    @SerialName("verification_uri_complete") val verificationUriComplete: String? = null,
    @SerialName("expires_in") val expiresIn: Int? = null,
    val interval: Int? = null,
)
