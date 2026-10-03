package com.nuvio.app.features.player

internal expect object PlaybackMemoryInfo {
    fun deviceMemoryMb(): Int
    fun javaHeapBufferLimitMb(): Int
}

internal fun PlaybackMemoryInfo.safeBufferLimitMb(): Int =
    (deviceMemoryMb() / 5).coerceIn(256, 2000)
