package com.nuvio.app.features.player

internal class ByteRateSampler {
    private var lastBytes = -1L
    private var lastAtMs = 0L
    private var rate: Long? = null

    fun sample(totalBytes: Long, nowMs: Long): Long? {
        if (lastBytes < 0L || totalBytes < lastBytes) {
            lastBytes = totalBytes
            lastAtMs = nowMs
            return rate
        }
        val elapsedMs = nowMs - lastAtMs
        if (elapsedMs < 500L) return rate
        val instant = (totalBytes - lastBytes) * 1000L / elapsedMs
        rate = rate?.let { previous -> (previous * 0.4 + instant * 0.6).toLong() } ?: instant
        lastBytes = totalBytes
        lastAtMs = nowMs
        return rate
    }
}

internal fun formatNetworkSpeed(bytesPerSecond: Long): String {
    val value = bytesPerSecond.coerceAtLeast(0L)
    return when {
        value >= 1_000_000L -> {
            val tenths = (value + 50_000L) / 100_000L
            "${tenths / 10}.${tenths % 10} MB/s"
        }
        value >= 1_000L -> "${(value + 500L) / 1_000L} KB/s"
        else -> "$value B/s"
    }
}
