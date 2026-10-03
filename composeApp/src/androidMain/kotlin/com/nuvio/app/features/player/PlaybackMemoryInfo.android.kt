package com.nuvio.app.features.player

import java.io.File

internal actual object PlaybackMemoryInfo {
    actual fun deviceMemoryMb(): Int = runCatching {
        File("/proc/meminfo").useLines { lines ->
            lines.first { it.startsWith("MemTotal:") }.filter(Char::isDigit).toLong() / 1024L
        }.toInt()
    }.getOrDefault(4096)

    actual fun javaHeapBufferLimitMb(): Int =
        (Runtime.getRuntime().maxMemory() / (4L * 1024L * 1024L)).toInt().coerceAtLeast(32)
}
