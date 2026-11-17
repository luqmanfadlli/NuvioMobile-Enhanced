package com.nuvio.app.features.streams

import androidx.compose.runtime.Composable
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

internal class SmartLabels(
    private val limitAuto: String,
    private val saveData: String,
    private val bestQuality: String,
    private val codecOther: String,
    private val audioStereo: String,
    private val underSizeTemplate: String,
    private val sizeUnderTemplate: String,
    val sizeAny: String,
    val instantOnly: String,
    val showingLowQuality: String,
) {
    fun limit(value: SmartResolutionLimit): String = when (value) {
        SmartResolutionLimit.AUTO -> limitAuto
        SmartResolutionLimit.P1080 -> "1080p"
        SmartResolutionLimit.P4K -> "4K"
    }

    fun mobile(value: SmartMobileData): String = when (value) {
        SmartMobileData.SAVE_DATA -> saveData
        SmartMobileData.BEST_QUALITY -> bestQuality
    }

    fun codec(value: SmartCodec): String = if (value == SmartCodec.OTHER) codecOther else value.label

    fun audio(value: SmartAudio): String = if (value == SmartAudio.STEREO) audioStereo else value.label

    fun underSize(gb: Int): String = underSizeTemplate.fill(gb)

    fun sizeUnder(gb: Int): String = sizeUnderTemplate.fill(gb)
}

private fun String.fill(vararg values: Any): String =
    values.foldIndexed(this) { index, text, value ->
        text.replace("%${index + 1}\$d", value.toString()).replace("%${index + 1}\$s", value.toString())
    }

@Composable
internal fun rememberSmartLabels(): SmartLabels = SmartLabels(
    limitAuto = stringResource(Res.string.smart_streams_limit_auto),
    saveData = stringResource(Res.string.smart_streams_mobile_save_data),
    bestQuality = stringResource(Res.string.smart_streams_mobile_best_quality),
    codecOther = stringResource(Res.string.smart_streams_codec_other),
    audioStereo = stringResource(Res.string.smart_streams_audio_stereo),
    underSizeTemplate = stringResource(Res.string.smart_streams_chip_under_size),
    sizeUnderTemplate = stringResource(Res.string.smart_streams_size_under),
    sizeAny = stringResource(Res.string.smart_streams_size_any),
    instantOnly = stringResource(Res.string.smart_streams_instant_only),
    showingLowQuality = stringResource(Res.string.smart_streams_showing_low_quality),
)

@Composable
internal fun smartSortTitle(mode: SmartSortMode): String = stringResource(
    when (mode) {
        SmartSortMode.BEST_MATCH -> Res.string.smart_streams_sort_best
        SmartSortMode.HIGHEST_QUALITY -> Res.string.smart_streams_sort_quality
        SmartSortMode.SMALLEST_FILE -> Res.string.smart_streams_sort_smallest
        SmartSortMode.LARGEST_FILE -> Res.string.smart_streams_sort_largest
        SmartSortMode.ADDON_ORDER -> Res.string.smart_streams_sort_addon
    },
)

@Composable
internal fun smartSortDescription(mode: SmartSortMode): String = stringResource(
    when (mode) {
        SmartSortMode.BEST_MATCH -> Res.string.smart_streams_sort_best_description
        SmartSortMode.HIGHEST_QUALITY -> Res.string.smart_streams_sort_quality_description
        SmartSortMode.SMALLEST_FILE -> Res.string.smart_streams_sort_smallest_description
        SmartSortMode.LARGEST_FILE -> Res.string.smart_streams_sort_largest_description
        SmartSortMode.ADDON_ORDER -> Res.string.smart_streams_sort_addon_description
    },
)

@Composable
internal fun smartConnectionLabel(onWifi: Boolean): String =
    stringResource(if (onWifi) Res.string.smart_streams_wifi else Res.string.smart_streams_mobile_data)

@Composable
internal fun smartScreenLabel(device: SmartDeviceProfile): String {
    val screen = device.screen
    val tvConnected = stringResource(Res.string.smart_streams_tv_connected)
    return buildString {
        if (screen.hdr) {
            append(SmartHdr.entries.filter { it in screen.hdrFormats }.joinToString(", ") { it.label })
            append(" · ")
        }
        append("${screen.longSidePx} × ${screen.shortSidePx}")
        if (screen.externalDisplay) append(" · ").append(tvConnected)
    }
}

@Composable
internal fun smartVideoLabel(device: SmartDeviceProfile): String {
    val screen = device.screen
    val hevc = if (screen.hevcHardware) "HEVC" else stringResource(Res.string.smart_streams_software, "HEVC")
    val codecs = listOfNotNull(hevc, "H.264", "AV1".takeIf { screen.av1Hardware }).joinToString(", ")
    val upTo = stringResource(Res.string.smart_streams_up_to, if (screen.decodes4k) "4K" else "1080p")
    return "$codecs · $upTo"
}

@Composable
internal fun smartReasonText(reason: SmartReason): String = when (reason) {
    SmartReason.Instant -> stringResource(Res.string.smart_streams_reason_instant)
    SmartReason.NotInstant -> stringResource(Res.string.smart_streams_reason_not_instant)
    is SmartReason.SharpestForScreen ->
        stringResource(Res.string.smart_streams_reason_sharpest_screen, reason.resolution, reason.lines)
    is SmartReason.SharpestWithinLimit ->
        stringResource(Res.string.smart_streams_reason_sharpest_limit, reason.resolution)
    is SmartReason.DecodedByChip -> stringResource(Res.string.smart_streams_reason_codec_chip, reason.codec)
    SmartReason.H264Smooth -> stringResource(Res.string.smart_streams_reason_h264)
    is SmartReason.HdrShown -> stringResource(Res.string.smart_streams_reason_hdr, reason.format)
    is SmartReason.EnoughMemory -> stringResource(Res.string.smart_streams_reason_memory, reason.memory)
    SmartReason.KeptTo1080 -> stringResource(Res.string.smart_streams_reason_kept_1080)
    is SmartReason.SensibleSize -> stringResource(
        Res.string.smart_streams_reason_size,
        reason.size,
        smartConnectionLabel(reason.onWifi),
    )
}
