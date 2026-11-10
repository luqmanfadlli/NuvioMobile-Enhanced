package com.nuvio.app.features.streams

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import kotlinx.cinterop.useContents
import platform.UIKit.UIDevice
import platform.UIKit.UIScreen
import platform.UIKit.UIUserInterfaceIdiomPad
import platform.posix.uname
import platform.posix.utsname

@OptIn(ExperimentalForeignApi::class)
internal actual fun readSmartScreenInfo(): SmartScreenInfo {
    val screen = UIScreen.mainScreen
    val size = screen.nativeBounds.useContents { size.width to size.height }
    val longSide = maxOf(size.first, size.second).toInt()
    val shortSide = minOf(size.first, size.second).toInt()
    // Screens that can show HDR report extra brightness headroom above 1.0.
    val hdr = screen.potentialEDRHeadroom > 1.0
    val model = memScoped {
        val info = alloc<utsname>()
        uname(info.ptr)
        info.machine.toKString()
    }
    return SmartScreenInfo(
        longSidePx = longSide,
        shortSidePx = shortSide,
        hdr = hdr,
        av1Hardware = modelDecodesAv1(model),
        externalDisplay = UIScreen.screens.size > 1,
        isTablet = UIDevice.currentDevice.userInterfaceIdiom == UIUserInterfaceIdiomPad,
    )
}

/**
 * AV1 hardware decoding arrived with the A17 Pro / M3 chips: "iPhone16,x" (iPhone 15 Pro) and newer,
 * "iPad16,x" and newer. Older or unknown models count as no AV1, which only makes AV1 streams rank lower.
 */
internal fun modelDecodesAv1(model: String): Boolean {
    val family = when {
        model.startsWith("iPhone") -> "iPhone"
        model.startsWith("iPad") -> "iPad"
        else -> return false
    }
    val major = model.removePrefix(family).substringBefore(',').toIntOrNull() ?: return false
    return major >= 16
}
