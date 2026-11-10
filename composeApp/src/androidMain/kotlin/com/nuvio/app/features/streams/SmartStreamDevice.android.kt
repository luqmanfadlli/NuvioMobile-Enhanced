package com.nuvio.app.features.streams

import android.content.res.Resources

// This fork targets iOS; Android gets a simple screen-size reading and conservative defaults.
internal actual fun readSmartScreenInfo(): SmartScreenInfo {
    val metrics = Resources.getSystem().displayMetrics
    val longSide = maxOf(metrics.widthPixels, metrics.heightPixels)
    val shortSide = minOf(metrics.widthPixels, metrics.heightPixels)
    return SmartScreenInfo(
        longSidePx = longSide,
        shortSidePx = shortSide,
        hdr = false,
        av1Hardware = false,
        externalDisplay = false,
        isTablet = shortSide / metrics.density >= 600f,
    )
}
