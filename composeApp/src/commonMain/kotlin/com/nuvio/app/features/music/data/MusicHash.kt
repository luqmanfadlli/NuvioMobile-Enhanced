package com.nuvio.app.features.music.data

/** SHA-1 and MD5 in plain Kotlin (YouTube's SAPISIDHASH, Subsonic tokens, Last.fm signatures). */
internal fun sha1Hex(text: String): String = sha1(text.encodeToByteArray()).toHex()

internal fun md5Hex(text: String): String = md5(text.encodeToByteArray()).toHex()

private fun ByteArray.toHex(): String {
    val digits = "0123456789abcdef"
    val sb = StringBuilder(size * 2)
    for (b in this) {
        val v = b.toInt() and 0xFF
        sb.append(digits[v ushr 4]).append(digits[v and 0x0F])
    }
    return sb.toString()
}

private fun padMessage(data: ByteArray, bigEndianLength: Boolean): ByteArray {
    val bitLength = data.size.toLong() * 8
    val padded = ByteArray(((data.size + 8) / 64 + 1) * 64)
    data.copyInto(padded)
    padded[data.size] = 0x80.toByte()
    for (i in 0 until 8) {
        val shift = if (bigEndianLength) (7 - i) * 8 else i * 8
        padded[padded.size - 8 + i] = (bitLength ushr shift).toByte()
    }
    return padded
}

private fun rotl(x: Int, n: Int): Int = (x shl n) or (x ushr (32 - n))

internal fun sha1(data: ByteArray): ByteArray {
    var h0 = 0x67452301
    var h1 = 0xEFCDAB89.toInt()
    var h2 = 0x98BADCFE.toInt()
    var h3 = 0x10325476
    var h4 = 0xC3D2E1F0.toInt()
    val msg = padMessage(data, bigEndianLength = true)
    val w = IntArray(80)
    var offset = 0
    while (offset < msg.size) {
        for (i in 0 until 16) {
            val j = offset + i * 4
            w[i] = ((msg[j].toInt() and 0xFF) shl 24) or ((msg[j + 1].toInt() and 0xFF) shl 16) or
                ((msg[j + 2].toInt() and 0xFF) shl 8) or (msg[j + 3].toInt() and 0xFF)
        }
        for (i in 16 until 80) w[i] = rotl(w[i - 3] xor w[i - 8] xor w[i - 14] xor w[i - 16], 1)
        var a = h0; var b = h1; var c = h2; var d = h3; var e = h4
        for (i in 0 until 80) {
            val f: Int
            val k: Int
            when {
                i < 20 -> { f = (b and c) or (b.inv() and d); k = 0x5A827999 }
                i < 40 -> { f = b xor c xor d; k = 0x6ED9EBA1 }
                i < 60 -> { f = (b and c) or (b and d) or (c and d); k = 0x8F1BBCDC.toInt() }
                else -> { f = b xor c xor d; k = 0xCA62C1D6.toInt() }
            }
            val temp = rotl(a, 5) + f + e + k + w[i]
            e = d; d = c; c = rotl(b, 30); b = a; a = temp
        }
        h0 += a; h1 += b; h2 += c; h3 += d; h4 += e
        offset += 64
    }
    val out = ByteArray(20)
    intArrayOf(h0, h1, h2, h3, h4).forEachIndexed { i, h ->
        out[i * 4] = (h ushr 24).toByte(); out[i * 4 + 1] = (h ushr 16).toByte()
        out[i * 4 + 2] = (h ushr 8).toByte(); out[i * 4 + 3] = h.toByte()
    }
    return out
}

private val MD5_S = intArrayOf(
    7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22,
    5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20,
    4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23,
    6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21,
)

private val MD5_K = IntArray(64) { i ->
    // floor(2^32 * abs(sin(i + 1)))
    (kotlin.math.abs(kotlin.math.sin((i + 1).toDouble())) * 4294967296.0).toLong().toInt()
}

internal fun md5(data: ByteArray): ByteArray {
    var a0 = 0x67452301
    var b0 = 0xEFCDAB89.toInt()
    var c0 = 0x98BADCFE.toInt()
    var d0 = 0x10325476
    val msg = padMessage(data, bigEndianLength = false)
    val m = IntArray(16)
    var offset = 0
    while (offset < msg.size) {
        for (i in 0 until 16) {
            val j = offset + i * 4
            m[i] = (msg[j].toInt() and 0xFF) or ((msg[j + 1].toInt() and 0xFF) shl 8) or
                ((msg[j + 2].toInt() and 0xFF) shl 16) or ((msg[j + 3].toInt() and 0xFF) shl 24)
        }
        var a = a0; var b = b0; var c = c0; var d = d0
        for (i in 0 until 64) {
            var f: Int
            val g: Int
            when {
                i < 16 -> { f = (b and c) or (b.inv() and d); g = i }
                i < 32 -> { f = (d and b) or (d.inv() and c); g = (5 * i + 1) % 16 }
                i < 48 -> { f = b xor c xor d; g = (3 * i + 5) % 16 }
                else -> { f = c xor (b or d.inv()); g = (7 * i) % 16 }
            }
            f = f + a + MD5_K[i] + m[g]
            a = d; d = c; c = b
            b += rotl(f, MD5_S[i])
        }
        a0 += a; b0 += b; c0 += c; d0 += d
        offset += 64
    }
    val out = ByteArray(16)
    intArrayOf(a0, b0, c0, d0).forEachIndexed { i, v ->
        out[i * 4] = v.toByte(); out[i * 4 + 1] = (v ushr 8).toByte()
        out[i * 4 + 2] = (v ushr 16).toByte(); out[i * 4 + 3] = (v ushr 24).toByte()
    }
    return out
}
