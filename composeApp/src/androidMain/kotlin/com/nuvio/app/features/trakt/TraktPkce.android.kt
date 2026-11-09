package com.nuvio.app.features.trakt

import java.security.MessageDigest
import java.security.SecureRandom

internal actual fun traktSecureRandomBytes(size: Int): ByteArray {
    require(size > 0)
    return ByteArray(size).also { SecureRandom().nextBytes(it) }
}

internal actual fun traktSha256(input: ByteArray): ByteArray =
    MessageDigest.getInstance("SHA-256").digest(input)
