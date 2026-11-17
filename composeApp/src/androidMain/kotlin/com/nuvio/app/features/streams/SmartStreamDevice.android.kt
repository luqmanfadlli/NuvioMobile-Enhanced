package com.nuvio.app.features.streams

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.hardware.display.DisplayManager
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import android.view.Display

internal object SmartStreamDeviceContext {
    var context: Context? = null
}

internal actual fun readSmartScreenInfo(): SmartScreenInfo {
    val context = SmartStreamDeviceContext.context
    val displayManager = context?.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
    val display = displayManager?.getDisplay(Display.DEFAULT_DISPLAY)
    val mode = display?.mode
    val metrics = Resources.getSystem().displayMetrics
    val width = mode?.physicalWidth ?: metrics.widthPixels
    val height = mode?.physicalHeight ?: metrics.heightPixels
    val longSide = maxOf(width, height)
    val shortSide = minOf(width, height)
    val smallestWidthDp = context?.resources?.configuration?.smallestScreenWidthDp
        ?: (shortSide / metrics.density).toInt()
    val externalDisplay = (displayManager?.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)?.size ?: 0) > 0
    return SmartScreenInfo(
        longSidePx = longSide,
        shortSidePx = shortSide,
        hdr = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && display?.isHdr == true,
        av1Hardware = hasHardwareAv1Decoder(),
        externalDisplay = externalDisplay,
        isTablet = smallestWidthDp >= 600 ||
            (context?.resources?.configuration?.screenLayout?.and(Configuration.SCREENLAYOUT_SIZE_MASK) ?: 0) >=
            Configuration.SCREENLAYOUT_SIZE_LARGE,
    )
}

private fun hasHardwareAv1Decoder(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && runCatching {
    MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { info ->
        !info.isEncoder &&
            info.supportedTypes.any { it.equals(MediaFormat.MIMETYPE_VIDEO_AV1, ignoreCase = true) } &&
            info.isHardwareAccelerated
    }
}.getOrDefault(false)
