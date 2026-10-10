package com.nuvio.app.features.profiles

internal expect object ProfilePinCrypto {
    fun sha256Hex(value: String): String
    fun secureRandomBytes(size: Int): ByteArray
}
