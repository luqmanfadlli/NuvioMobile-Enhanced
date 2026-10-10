package com.nuvio.app.features.profiles

import kotlinx.serialization.Serializable

@Serializable
internal data class CachedProfilePinPayload(
    val salt: String,
    val digest: String,
    val profileUserId: String = "",
    val profileUpdatedAt: String = "",
)

internal fun generateProfilePinSalt(): String =
    ProfilePinCrypto.secureRandomBytes(32)
        .joinToString(separator = "") { byte ->
            byte.toUByte().toString(16).padStart(2, '0')
        }

internal fun hashProfilePin(profileIndex: Int, salt: String, pin: String): String =
    ProfilePinCrypto.sha256Hex("profile:$profileIndex:$salt:$pin")