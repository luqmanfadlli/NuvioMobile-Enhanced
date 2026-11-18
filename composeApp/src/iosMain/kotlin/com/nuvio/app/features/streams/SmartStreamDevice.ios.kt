package com.nuvio.app.features.streams

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.UIKit.UIDevice
import platform.UIKit.UIScreen
import platform.UIKit.UIUserInterfaceIdiomPad
import platform.VideoToolbox.VTIsHardwareDecodeSupported

private const val CODEC_HEVC: UInt = 0x68766331u
private const val CODEC_AV1: UInt = 0x61763031u

@OptIn(ExperimentalForeignApi::class)
internal actual fun readSmartScreenInfo(): SmartScreenInfo {
    val screen = UIScreen.mainScreen
    val size = screen.nativeBounds.useContents { size.width to size.height }
    val longSide = maxOf(size.first, size.second).toInt()
    val shortSide = minOf(size.first, size.second).toInt()
    val hevcHardware = hardwareDecodes(CODEC_HEVC)
    val hdrScreen = screen.potentialEDRHeadroom > 1.0
    return SmartScreenInfo(
        longSidePx = longSide,
        shortSidePx = shortSide,
        hdrFormats = if (hdrScreen && hevcHardware) SmartHdr.entries.toSet() else emptySet(),
        hevcHardware = hevcHardware,
        av1Hardware = hardwareDecodes(CODEC_AV1),
        decodes4k = hevcHardware,
        externalDisplay = UIScreen.screens.size > 1,
        isTablet = UIDevice.currentDevice.userInterfaceIdiom == UIUserInterfaceIdiomPad,
    )
}

@OptIn(ExperimentalForeignApi::class)
private fun hardwareDecodes(codec: UInt): Boolean = runCatching { VTIsHardwareDecodeSupported(codec) }.getOrDefault(false)
