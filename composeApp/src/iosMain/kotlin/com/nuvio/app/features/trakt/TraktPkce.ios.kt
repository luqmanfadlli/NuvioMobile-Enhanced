package com.nuvio.app.features.trakt

import com.nuvio.app.features.plugins.cryptointerop.CC_SHA256
import com.nuvio.app.features.plugins.cryptointerop.CC_SHA256_DIGEST_LENGTH
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.refTo
import kotlinx.cinterop.usePinned
import platform.posix.arc4random_buf

@OptIn(ExperimentalForeignApi::class)
internal actual fun traktSecureRandomBytes(size: Int): ByteArray {
    require(size > 0)
    return ByteArray(size).also { bytes ->
        bytes.usePinned { pinned ->
            arc4random_buf(pinned.addressOf(0), bytes.size.convert())
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun traktSha256(input: ByteArray): ByteArray {
    val output = UByteArray(CC_SHA256_DIGEST_LENGTH.toInt())
    CC_SHA256(input.refTo(0), input.size.toUInt(), output.refTo(0))
    return ByteArray(output.size) { output[it].toByte() }
}
