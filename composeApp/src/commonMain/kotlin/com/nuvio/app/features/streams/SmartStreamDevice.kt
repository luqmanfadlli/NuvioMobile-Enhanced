package com.nuvio.app.features.streams

import com.nuvio.app.features.downloads.DownloadNetworkGuard

/** What the screen and chip of this device can actually show and play smoothly. */
data class SmartScreenInfo(
    /** Screen size in real pixels, long side first. */
    val longSidePx: Int,
    val shortSidePx: Int,
    val hdr: Boolean,
    /** True when the chip decodes AV1 in hardware (newer iPhones and iPads). */
    val av1Hardware: Boolean,
    /** A TV or monitor is connected (cable or screen mirroring). */
    val externalDisplay: Boolean,
    val isTablet: Boolean,
)

internal expect fun readSmartScreenInfo(): SmartScreenInfo

data class SmartDeviceProfile(
    val screen: SmartScreenInfo,
    val onWifi: Boolean,
) {
    /**
     * How many picture lines of a widescreen (16:9) video this screen can really show.
     * A recent iPhone (2556 x 1179) shows about 1179 lines, so 4K looks the same as 1080p there.
     */
    val videoLines: Int
        get() = if (screen.externalDisplay) {
            2160
        } else {
            minOf(screen.shortSidePx, screen.longSidePx * 9 / 16).coerceAtLeast(480)
        }

    val screenLabel: String
        get() = buildString {
            if (screen.hdr) append("HDR · ")
            append("${screen.longSidePx} × ${screen.shortSidePx}")
            if (screen.externalDisplay) append(" · TV connected")
        }

    val videoLabel: String
        get() = if (screen.av1Hardware) "HEVC, H.264, AV1" else "HEVC, H.264 (no AV1)"

    val connectionLabel: String
        get() = if (onWifi) "Wi-Fi" else "Mobile data"
}

internal fun currentSmartDeviceProfile(): SmartDeviceProfile = SmartDeviceProfile(
    screen = runCatching { readSmartScreenInfo() }.getOrElse {
        SmartScreenInfo(1920, 1080, hdr = false, av1Hardware = false, externalDisplay = false, isTablet = false)
    },
    onWifi = runCatching { DownloadNetworkGuard.isOnWifi() }.getOrDefault(true),
)
