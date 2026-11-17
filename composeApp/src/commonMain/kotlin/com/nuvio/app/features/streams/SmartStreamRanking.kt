package com.nuvio.app.features.streams

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlin.math.log2
import kotlin.math.min

internal data class SmartRankedStream(
    val stream: StreamItem,
    val info: SmartStreamInfo,
    val score: Double,
    val index: Int,
)

internal data class SmartAutoPlayRanking(
    val ranked: List<SmartRankedStream>,
    val goodEnough: Boolean,
)

internal sealed interface SmartReason {
    data object Instant : SmartReason
    data object NotInstant : SmartReason
    data class SharpestForScreen(val resolution: String, val lines: Int) : SmartReason
    data class SharpestWithinLimit(val resolution: String) : SmartReason
    data class DecodedByChip(val codec: String) : SmartReason
    data object H264Smooth : SmartReason
    data class HdrShown(val format: String) : SmartReason
    data class EnoughMemory(val memory: String) : SmartReason
    data object KeptTo1080 : SmartReason
    data class SensibleSize(val size: String, val onWifi: Boolean) : SmartReason
}

internal data class SmartPick(
    val stream: StreamItem,
    val info: SmartStreamInfo,
    val reasons: List<SmartReason>,
)

internal data class SmartStreamsResult(
    val listState: StreamsUiState,
    val sortedStreams: List<StreamItem>?,
    val best: SmartPick?,
    val shownCount: Int,
    val totalCount: Int,
    val emptyBecauseOfFilters: Boolean,
)

internal data class SmartTarget(
    val lines: Int,
    val explicitLimit: Int?,
    val sweetLowMbps: Double,
    val sweetHighMbps: Double,
    val saveData: Boolean,
)

internal object SmartStreamRanking {
    private const val BINGE_GROUP_BONUS = 25.0
    private const val HEAVY_4K_MBPS = 40.0

    val dispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1)

    fun runtimeSeconds(isEpisode: Boolean): Int = if (isEpisode) 45 * 60 else 120 * 60

    internal fun target(device: SmartDeviceProfile, settings: SmartStreamSettings): SmartTarget {
        val saveData = !device.onWifi && settings.mobileData == SmartMobileData.SAVE_DATA
        val lines = when (settings.resolutionLimit) {
            SmartResolutionLimit.AUTO -> {
                val capable = if (device.memoryTier == SmartMemoryTier.AMPLE) device.maxComfortableLines else 1080
                if (saveData) min(capable, 1080) else capable
            }
            SmartResolutionLimit.P1080 -> 1080
            SmartResolutionLimit.P4K -> 2160
        }
        val explicitLimit = if (settings.resolutionLimit == SmartResolutionLimit.P1080) 1080 else null
        var low: Double
        var high: Double
        when {
            lines <= 800 -> { low = 1.5; high = 5.0 }
            lines <= 1300 -> { low = 4.0; high = 12.0 }
            lines <= 1700 -> { low = 6.0; high = 25.0 }
            else -> { low = 12.0; high = 40.0 }
        }
        if (saveData) {
            low = min(low, 2.5)
            high = min(high, 6.0)
        }
        return SmartTarget(lines, explicitLimit, low, high, saveData)
    }

    internal fun score(
        info: SmartStreamInfo,
        device: SmartDeviceProfile,
        target: SmartTarget,
        runtimeSeconds: Int,
    ): Double {
        var score = 0.0

        score += when (info.instant) {
            SmartInstant.YES -> 40.0
            SmartInstant.UNKNOWN -> 15.0
            SmartInstant.NO -> 0.0
        }

        val lines = info.resolution?.lines
        score += if (lines == null) {
            15.0
        } else {
            30.0 * min(lines, target.lines).toDouble() / target.lines
        }
        if (target.explicitLimit != null && lines != null && lines > target.explicitLimit) score -= 25.0
        if (lines != null && lines > device.maxComfortableLines) score -= 60.0

        val mbps = info.sizeBytes?.let { it * 8.0 / runtimeSeconds / 1_000_000.0 }
        score += when {
            mbps == null -> 4.0
            mbps < target.sweetLowMbps -> 10.0 * mbps / target.sweetLowMbps
            mbps <= target.sweetHighMbps -> 10.0
            else -> {
                val perDoubling = if (target.saveData) 20.0 else 10.0
                10.0 - min(40.0, perDoubling * log2(mbps / target.sweetHighMbps))
            }
        }

        score += when (info.codec) {
            SmartCodec.AV1 -> when {
                device.screen.av1Hardware -> 6.0
                lines != null && lines >= 2160 -> -50.0
                else -> -30.0
            }
            SmartCodec.HEVC -> if (device.screen.hevcHardware) 6.0 else -30.0
            SmartCodec.H264 -> 4.0
            SmartCodec.OTHER -> -5.0
            null -> 0.0
        }

        if (info.hdr != null && device.screen.hdr) {
            if (info.hdr in device.screen.hdrFormats) {
                val bonus = 10.0 + 4.0 * info.hdr.rank
                score += if (target.saveData) bonus / 2 else bonus
            } else if (info.hdr == SmartHdr.DOLBY_VISION) {
                score -= 10.0
            } else {
                score += 4.0
            }
        }

        if (device.memoryTier != SmartMemoryTier.AMPLE && lines != null && lines >= 2160) {
            val heavy = info.source == SmartSource.REMUX || (mbps != null && mbps > HEAVY_4K_MBPS)
            if (heavy) score -= 20.0
        }

        score += when (info.source) {
            SmartSource.REMUX, SmartSource.BLURAY, SmartSource.WEBDL -> 6.0
            SmartSource.WEBRIP -> 3.0
            SmartSource.HDTV -> 1.0
            null -> 2.0
        }
        score += when {
            SmartAudio.ATMOS in info.audio || SmartAudio.DOLBY_DIGITAL in info.audio -> 3.0
            info.audio.isNotEmpty() -> 1.0
            else -> 0.0
        }

        if (info.isLowQuality) score -= 100.0
        return score
    }

    internal fun matches(info: SmartStreamInfo, filters: SmartStreamFilters): Boolean {
        if (filters.hideLowQuality && info.isLowQuality) return false
        if (filters.instantOnly && info.instant != SmartInstant.YES) return false
        if (filters.resolutions.isNotEmpty() && info.resolution !in filters.resolutions) return false
        if (filters.hdr.isNotEmpty()) {
            val kind = when (info.hdr) {
                SmartHdr.DOLBY_VISION -> SmartHdrFilter.DOLBY_VISION
                SmartHdr.HDR10, SmartHdr.HDR10_PLUS -> SmartHdrFilter.HDR10
                null -> SmartHdrFilter.SDR
            }
            if (kind !in filters.hdr) return false
        }
        if (filters.codecs.isNotEmpty() && info.codec !in filters.codecs) return false
        if (filters.audio.isNotEmpty() && filters.audio.none { it in info.audio }) return false
        if (filters.sources.isNotEmpty() && info.source !in filters.sources) return false
        val maxGb = filters.maxSizeGb
        if (maxGb != null) {
            val size = info.sizeBytes ?: return false
            if (size > maxGb * 1_000_000_000L) return false
        }
        return true
    }

    private fun sorted(ranked: List<SmartRankedStream>, mode: SmartSortMode): List<SmartRankedStream> = when (mode) {
        SmartSortMode.BEST_MATCH, SmartSortMode.ADDON_ORDER ->
            ranked.sortedWith(compareByDescending<SmartRankedStream> { it.score }.thenBy { it.index })
        SmartSortMode.HIGHEST_QUALITY -> ranked.sortedWith(
            compareByDescending<SmartRankedStream> { it.info.resolution?.lines ?: 0 }
                .thenByDescending { it.info.hdr?.rank ?: 0 }
                .thenByDescending { it.info.source?.rank ?: 0 }
                .thenByDescending { it.info.sizeBytes ?: 0L }
                .thenBy { it.index },
        )
        SmartSortMode.SMALLEST_FILE -> ranked.sortedWith(
            compareBy<SmartRankedStream> { it.info.sizeBytes ?: Long.MAX_VALUE }
                .thenByDescending { it.score }
                .thenBy { it.index },
        )
        SmartSortMode.LARGEST_FILE -> ranked.sortedWith(
            compareByDescending<SmartRankedStream> { it.info.sizeBytes ?: -1L }
                .thenByDescending { it.score }
                .thenBy { it.index },
        )
    }

    private fun reasons(info: SmartStreamInfo, device: SmartDeviceProfile, target: SmartTarget): List<SmartReason> = buildList {
        when (info.instant) {
            SmartInstant.YES -> add(SmartReason.Instant)
            SmartInstant.NO -> add(SmartReason.NotInstant)
            SmartInstant.UNKNOWN -> Unit
        }
        info.resolution?.let { resolution ->
            if (resolution.lines > target.lines) {
                add(SmartReason.SharpestForScreen(resolution.label, device.videoLines))
            } else {
                add(SmartReason.SharpestWithinLimit(resolution.label))
            }
        }
        when (info.codec) {
            SmartCodec.HEVC -> if (device.screen.hevcHardware) add(SmartReason.DecodedByChip("HEVC"))
            SmartCodec.H264 -> add(SmartReason.H264Smooth)
            SmartCodec.AV1 -> if (device.screen.av1Hardware) add(SmartReason.DecodedByChip("AV1"))
            else -> Unit
        }
        if (info.hdr != null && info.hdr in device.screen.hdrFormats) add(SmartReason.HdrShown(info.hdr.label))
        if (info.resolution != null && info.resolution.lines >= 2160 && device.memoryTier == SmartMemoryTier.AMPLE) {
            add(SmartReason.EnoughMemory(device.memoryLabel))
        }
        if (device.maxComfortableLines < 2160 || device.memoryTier != SmartMemoryTier.AMPLE) {
            if (info.resolution != null && info.resolution.lines <= 1080) add(SmartReason.KeptTo1080)
        }
        info.sizeBytes?.let { size ->
            add(SmartReason.SensibleSize(formatSmartSize(size), device.onWifi))
        }
    }

    fun rankForAutoPlay(
        streams: List<StreamItem>,
        settings: SmartStreamSettings,
        device: SmartDeviceProfile,
        debridEnabled: Boolean,
        isEpisode: Boolean,
        preferredBingeGroup: String? = null,
    ): SmartAutoPlayRanking {
        val bingeGroup = preferredBingeGroup?.trim()?.takeIf { it.isNotEmpty() }
        val runtime = runtimeSeconds(isEpisode)
        val target = target(device, settings)
        val ranked = streams
            .filter { it.isSelectableForPlayback(debridEnabled) }
            .mapIndexed { index, stream ->
                val info = SmartStreamQuality.cached(stream)
                val bingeBonus = if (bingeGroup != null && stream.behaviorHints.bingeGroup == bingeGroup) BINGE_GROUP_BONUS else 0.0
                SmartRankedStream(stream, info, score(info, device, target, runtime) + bingeBonus, index)
            }
            .filter { matches(it.info, settings.filters.copy(hideLowQuality = true)) }
            .sortedWith(compareByDescending<SmartRankedStream> { it.score }.thenBy { it.index })
            .let { all -> all.filter { it.info.instant != SmartInstant.NO }.ifEmpty { all } }
        val best = ranked.firstOrNull()
        val goodEnough = best != null &&
            best.info.instant == SmartInstant.YES &&
            (best.info.resolution?.lines ?: 0) >= minOf(target.lines, device.maxComfortableLines)
        return SmartAutoPlayRanking(ranked = ranked, goodEnough = goodEnough)
    }

    fun compute(
        listState: StreamsUiState,
        allState: StreamsUiState,
        settings: SmartStreamSettings,
        device: SmartDeviceProfile,
        debridEnabled: Boolean,
        isEpisode: Boolean,
    ): SmartStreamsResult {
        val runtime = runtimeSeconds(isEpisode)
        val target = target(device, settings)
        fun infoOf(stream: StreamItem): SmartStreamInfo = SmartStreamQuality.cached(stream)

        val candidates = allState.groups.flatMap { it.streams }
            .filter { it.isSelectableForPlayback(debridEnabled) }
            .mapIndexed { index, stream ->
                val info = infoOf(stream)
                SmartRankedStream(stream, info, score(info, device, target, runtime), index)
            }
        val decent = candidates.filterNot { it.info.isLowQuality }.ifEmpty { candidates }
        val top = decent.maxWithOrNull(compareBy<SmartRankedStream> { it.score }.thenByDescending { it.index })
        val best = top?.let { SmartPick(it.stream, it.info, reasons(it.info, device, target)) }

        val filters = settings.filters
        val filteredGroups = listState.groups.map { group ->
            group.copy(streams = group.streams.filter { matches(infoOf(it), filters) })
        }
        val filteredState = listState.copy(groups = filteredGroups)
        val total = listState.filteredGroups.sumOf { it.streams.size }
        val shown = filteredState.filteredGroups.sumOf { it.streams.size }

        val sortedStreams = if (settings.sortMode == SmartSortMode.ADDON_ORDER) {
            null
        } else {
            val ranked = filteredState.filteredGroups.flatMap { it.streams }.mapIndexed { index, stream ->
                val info = infoOf(stream)
                SmartRankedStream(stream, info, score(info, device, target, runtime), index)
            }
            sorted(ranked, settings.sortMode).map { it.stream }
        }

        return SmartStreamsResult(
            listState = filteredState,
            sortedStreams = sortedStreams,
            best = best,
            shownCount = shown,
            totalCount = total,
            emptyBecauseOfFilters = total > 0 && shown == 0,
        )
    }
}
