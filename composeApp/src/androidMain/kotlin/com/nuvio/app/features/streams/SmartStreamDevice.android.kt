package com.nuvio.app.features.streams

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.hardware.display.DisplayManager
import android.media.MediaCodecInfo
import android.media.MediaCodecInfo.CodecProfileLevel
import android.media.MediaCodecList
import android.os.Build
import android.view.Display

internal object SmartStreamDeviceContext {
    var context: Context? = null
}

private const val MIME_HEVC = "video/hevc"
private const val MIME_AV1 = "video/av01"
private const val MIME_DOLBY_VISION = "video/dolby-vision"

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
    val configuration = context?.resources?.configuration
    val smallestWidthDp = configuration?.smallestScreenWidthDp ?: (shortSide / metrics.density).toInt()
    val externalDisplay = (displayManager?.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)?.size ?: 0) > 0
    val decoders = hardwareVideoDecoders()
    val hevcDecoders = decoders.filter { it.supports(MIME_HEVC) }
    val av1Decoders = decoders.filter { it.supports(MIME_AV1) }
    val decodes4k = hevcDecoders.any { it.decodes4k(MIME_HEVC, CodecProfileLevel.HEVCProfileMain10) } ||
        av1Decoders.any { it.decodes4k(MIME_AV1, CodecProfileLevel.AV1ProfileMain10) }
    return SmartScreenInfo(
        longSidePx = longSide,
        shortSidePx = shortSide,
        hdrFormats = displayHdrFormats(display, hasDolbyVisionDecoder = decoders.any { it.supports(MIME_DOLBY_VISION) }),
        hevcHardware = hevcDecoders.isNotEmpty(),
        av1Hardware = av1Decoders.isNotEmpty(),
        decodes4k = decodes4k,
        externalDisplay = externalDisplay,
        isTablet = smallestWidthDp >= 600 ||
            ((configuration?.screenLayout ?: 0) and Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE,
    )
}

@Suppress("DEPRECATION")
private fun displayHdrFormats(display: Display?, hasDolbyVisionDecoder: Boolean): Set<SmartHdr> {
    val types = runCatching { display?.hdrCapabilities?.supportedHdrTypes }.getOrNull() ?: return emptySet()
    return buildSet {
        types.forEach { type ->
            when (type) {
                Display.HdrCapabilities.HDR_TYPE_HDR10 -> add(SmartHdr.HDR10)
                Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS -> add(SmartHdr.HDR10_PLUS)
                Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION -> if (hasDolbyVisionDecoder) add(SmartHdr.DOLBY_VISION)
            }
        }
    }
}

private fun hardwareVideoDecoders(): List<MediaCodecInfo> = runCatching {
    MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { info ->
        !info.isEncoder && info.isHardwareDecoder()
    }
}.getOrDefault(emptyList())

private fun MediaCodecInfo.isHardwareDecoder(): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        isHardwareAccelerated
    } else {
        val lower = name.lowercase()
        !lower.startsWith("omx.google.") && !lower.startsWith("c2.android.") && !lower.contains(".sw.")
    }

private fun MediaCodecInfo.supports(mime: String): Boolean =
    supportedTypes.any { it.equals(mime, ignoreCase = true) }

private fun MediaCodecInfo.decodes4k(mime: String, main10Profile: Int): Boolean = runCatching {
    val type = supportedTypes.first { it.equals(mime, ignoreCase = true) }
    val capabilities = getCapabilitiesForType(type)
    val video = capabilities.videoCapabilities ?: return@runCatching false
    val sizeOk = video.isSizeSupported(3840, 2160) || video.isSizeSupported(3840, 1600)
    val rateOk = video.areSizeAndRateSupported(3840, 2160, 23.976) ||
        video.areSizeAndRateSupported(3840, 1600, 23.976)
    val main10 = capabilities.profileLevels.any { it.profile == main10Profile }
    sizeOk && rateOk && main10
}.getOrDefault(false)
