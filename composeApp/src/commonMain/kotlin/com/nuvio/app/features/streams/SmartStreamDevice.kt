package com.nuvio.app.features.streams

import com.nuvio.app.features.downloads.DownloadNetworkGuard
import com.nuvio.app.features.player.PlaybackMemoryInfo

internal data class SmartScreenInfo(
    val longSidePx: Int,
    val shortSidePx: Int,
    val hdrFormats: Set<SmartHdr>,
    val hevcHardware: Boolean,
    val av1Hardware: Boolean,
    val decodes4k: Boolean,
    val externalDisplay: Boolean,
    val isTablet: Boolean,
) {
    val hdr: Boolean
        get() = hdrFormats.isNotEmpty()
}

internal expect fun readSmartScreenInfo(): SmartScreenInfo

internal data class SmartDeviceProfile(
    val screen: SmartScreenInfo,
    val onWifi: Boolean,
    val memoryMb: Int,
) {
    val memoryTier: SmartMemoryTier
        get() = when {
            memoryMb < LOW_MEMORY_MB -> SmartMemoryTier.LOW
            memoryMb < AMPLE_MEMORY_MB -> SmartMemoryTier.LIMITED
            else -> SmartMemoryTier.AMPLE
        }

    val maxComfortableLines: Int
        get() = when {
            !screen.decodes4k -> 1080
            memoryTier == SmartMemoryTier.LOW -> 1080
            else -> 2160
        }

    val videoLines: Int
        get() = if (screen.externalDisplay) {
            2160
        } else {
            minOf(screen.shortSidePx, screen.longSidePx * 9 / 16).coerceAtLeast(480)
        }

    val screenLabel: String
        get() = buildString {
            if (screen.hdr) {
                append(SmartHdr.entries.filter { it in screen.hdrFormats }.joinToString(", ") { it.label })
                append(" · ")
            }
            append("${screen.longSidePx} × ${screen.shortSidePx}")
            if (screen.externalDisplay) append(" · TV connected")
        }

    val videoLabel: String
        get() = buildList {
            add(if (screen.hevcHardware) "HEVC" else "HEVC (software)")
            add("H.264")
            if (screen.av1Hardware) add("AV1")
        }.joinToString(", ") + if (screen.decodes4k) " · up to 4K" else " · up to 1080p"

    val memoryLabel: String
        get() {
            val tenths = (memoryMb * 10 + 512) / 1024
            return "${tenths / 10}.${tenths % 10} GB"
        }

    val connectionLabel: String
        get() = if (onWifi) "Wi-Fi" else "Mobile data"

    companion object {
        const val LOW_MEMORY_MB = 3000
        const val AMPLE_MEMORY_MB = 5500
    }
}

internal enum class SmartMemoryTier { LOW, LIMITED, AMPLE }

internal fun currentSmartDeviceProfile(): SmartDeviceProfile = SmartDeviceProfile(
    screen = runCatching { readSmartScreenInfo() }.getOrElse {
        SmartScreenInfo(
            longSidePx = 1920,
            shortSidePx = 1080,
            hdrFormats = emptySet(),
            hevcHardware = true,
            av1Hardware = false,
            decodes4k = false,
            externalDisplay = false,
            isTablet = false,
        )
    },
    onWifi = runCatching { DownloadNetworkGuard.isOnWifi() }.getOrDefault(true),
    memoryMb = runCatching { PlaybackMemoryInfo.deviceMemoryMb() }.getOrDefault(4096),
)
