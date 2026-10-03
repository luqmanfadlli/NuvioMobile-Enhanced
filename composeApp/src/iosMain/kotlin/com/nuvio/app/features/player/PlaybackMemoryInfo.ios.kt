package com.nuvio.app.features.player

import platform.Foundation.NSProcessInfo

internal actual object PlaybackMemoryInfo {
    actual fun deviceMemoryMb(): Int = (NSProcessInfo.processInfo.physicalMemory / 1048576UL).toInt()

    actual fun javaHeapBufferLimitMb(): Int = deviceMemoryMb() / 8
}
