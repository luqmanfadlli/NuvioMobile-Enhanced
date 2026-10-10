package com.nuvio.app.features.profiles

import com.nuvio.app.features.plugins.cryptointerop.CC_SHA256
import com.nuvio.app.features.plugins.cryptointerop.CC_SHA256_DIGEST_LENGTH
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.refTo
import platform.Security.SecRandomCopyBytes
import platform.Security.errSecSuccess
import platform.Security.kSecRandomDefault

actual object ProfilePinCrypto {
    @OptIn(ExperimentalForeignApi::class)
    actual fun sha256Hex(value: String): String {
        val input = value.encodeToByteArray()
        val output = UByteArray(CC_SHA256_DIGEST_LENGTH.toInt())
        CC_SHA256(input.refTo(0), input.size.toUInt(), output.refTo(0))
        return output.joinToString(separator = "") { byte ->
            byte.toString(16).padStart(2, '0')
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun secureRandomBytes(size: Int): ByteArray {
        require(size > 0) { "Random byte count must be positive" }
        val output = ByteArray(size)
        val status = SecRandomCopyBytes(
            kSecRandomDefault,
            size.toULong(),
            output.refTo(0),
        )
        check(status == errSecSuccess) {
            "SecRandomCopyBytes failed with status $status"
        }
        return output
    }
}
