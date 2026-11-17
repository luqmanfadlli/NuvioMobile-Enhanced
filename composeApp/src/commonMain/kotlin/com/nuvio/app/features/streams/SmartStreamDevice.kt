package com.nuvio.app.features.streams

import com.nuvio.app.features.downloads.DownloadNetworkGuard

internal data class SmartScreenInfo(
    val longSidePx: Int,
    val shortSidePx: Int,
    val hdr: Boolean,
    val av1Hardware: Boolean,
    val externalDisplay: Boolean,
    val isTablet: Boolean,
)

internal expect fun readSmartScreenInfo(): SmartScreenInfo

internal data class SmartDeviceProfile(
    val screen: SmartScreenInfo,
    val onWifi: Boolean,
) {
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
