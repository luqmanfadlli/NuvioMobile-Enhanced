package com.nuvio.app.features.streams

import kotlin.math.log2
import kotlin.math.min

data class SmartRankedStream(
    val stream: StreamItem,
    val info: SmartStreamInfo,
    val score: Double,
    /** Position in the original addon order, used to break ties. */
    val index: Int,
)

data class SmartPick(
    val stream: StreamItem,
    val info: SmartStreamInfo,
    val reasons: List<String>,
)

data class SmartStreamsResult(
    /** The streams state with filters applied (addon groups kept, for loading/empty handling). */
    val listState: StreamsUiState,
    /** One merged, sorted list when the sort isn't "Addon order"; null keeps the addon sections. */
    val sortedStreams: List<StreamItem>?,
    val best: SmartPick?,
    val shownCount: Int,
    val totalCount: Int,
    val emptyBecauseOfFilters: Boolean,
)

/**
 * What "best" means for this device right now: how many picture lines are worth having,
 * which bitrate range looks good without wasting data, and whether data is precious.
 */
internal data class SmartTarget(
    val lines: Int,
    val explicitLimit: Int?,
    val sweetLowMbps: Double,
    val sweetHighMbps: Double,
    val saveData: Boolean,
)

object SmartStreamRanking {

    fun runtimeSeconds(isEpisode: Boolean): Int = if (isEpisode) 45 * 60 else 120 * 60

    internal fun target(device: SmartDeviceProfile, settings: SmartStreamSettings): SmartTarget {
        val saveData = !device.onWifi && settings.mobileData == SmartMobileData.SAVE_DATA
        val screenLines = device.videoLines
        val lines = when (settings.resolutionLimit) {
            SmartResolutionLimit.AUTO -> if (saveData) min(screenLines, 1080) else screenLines
            SmartResolutionLimit.P1080 -> 1080
            SmartResolutionLimit.P4K -> 2160
        }
        val explicitLimit = if (settings.resolutionLimit == SmartResolutionLimit.P1080) 1080 else null
        // Bitrate (Mbps) where the picture already looks its best on this many lines.
        var low: Double
        var high: Double
        when {
            lines <= 800 -> { low = 1.5; high = 5.0 }
            lines <= 1300 -> { low = 4.0; high = 12.0 }
            lines <= 1700 -> { low = 6.0; high = 18.0 }
            else -> { low = 12.0; high = 40.0 }
        }
        if (saveData) {
            low = min(low, 2.5)
            high = min(high, 5.0)
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

        // 1. Instant streams start without waiting.
        score += when (info.instant) {
            SmartInstant.YES -> 40.0
            SmartInstant.UNKNOWN -> 15.0
            SmartInstant.NO -> 0.0
        }

        // 2. Resolution only counts up to what the screen can show.
        val lines = info.resolution?.lines
        score += if (lines == null) {
            15.0
        } else {
            30.0 * min(lines, target.lines).toDouble() / target.lines
        }
        if (target.explicitLimit != null && lines != null && lines > target.explicitLimit) score -= 25.0

        // 3. Bitrate: below the sweet spot looks blocky, above it only wastes data.
        val mbps = info.sizeBytes?.let { it * 8.0 / runtimeSeconds / 1_000_000.0 }
        score += when {
            mbps == null -> 4.0
            mbps < target.sweetLowMbps -> 10.0 * mbps / target.sweetLowMbps
            mbps <= target.sweetHighMbps -> 10.0
            else -> {
                val perDoubling = if (target.saveData) 30.0 else 10.0
                10.0 - min(40.0, perDoubling * log2(mbps / target.sweetHighMbps))
            }
        }

        // 4. Formats the chip plays smoothly.
        score += when (info.codec) {
            SmartCodec.AV1 -> when {
                device.screen.av1Hardware -> 6.0
                lines != null && lines >= 2160 -> -50.0
                else -> -30.0
            }
            SmartCodec.HEVC -> 6.0
            SmartCodec.H264 -> 4.0
            SmartCodec.OTHER -> -5.0
            null -> 0.0
        }

        // 5. HDR only helps on a screen that shows it (worth less when saving data).
        if (info.hdr != null && device.screen.hdr) {
            val bonus = 10.0 + 4.0 * info.hdr.rank
            score += if (target.saveData) bonus / 2 else bonus
        }

        // 6. Small extras: better source and audio.
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

    private fun reasons(info: SmartStreamInfo, device: SmartDeviceProfile, target: SmartTarget): List<String> = buildList {
        when (info.instant) {
            SmartInstant.YES -> add("Instant, starts with no waiting")
            SmartInstant.NO -> add("Needs to load first (no instant stream found)")
            SmartInstant.UNKNOWN -> Unit
        }
        info.resolution?.let { resolution ->
            if (resolution.lines > target.lines) {
                add("${resolution.label}; your screen shows about ${device.videoLines} lines")
            } else {
                add("${resolution.label}, the sharpest within your limit")
            }
        }
        when (info.codec) {
            SmartCodec.HEVC, SmartCodec.H264 -> add("${info.codec?.label}, played smoothly by the chip")
            SmartCodec.AV1 -> if (device.screen.av1Hardware) add("AV1, decoded by your chip")
            else -> Unit
        }
        if (info.hdr != null && device.screen.hdr) add("${info.hdr.label}, and your screen shows HDR")
        info.sizeBytes?.let { size ->
            add("${formatSmartSize(size)}, sensible for ${if (device.onWifi) "Wi-Fi" else "mobile data"}")
        }
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
        val infoCache = HashMap<StreamItem, SmartStreamInfo>()
        fun infoOf(stream: StreamItem): SmartStreamInfo = infoCache.getOrPut(stream) { SmartStreamQuality.read(stream) }

        // Best for this device: across every addon, ignoring the user's filters.
        val candidates = allState.groups.flatMap { it.streams }
            .filter { it.isSelectableForPlayback(debridEnabled) }
            .mapIndexed { index, stream ->
                val info = infoOf(stream)
                SmartRankedStream(stream, info, score(info, device, target, runtime), index)
            }
        val decent = candidates.filterNot { it.info.isLowQuality }.ifEmpty { candidates }
        val top = decent.maxWithOrNull(compareBy<SmartRankedStream> { it.score }.thenByDescending { it.index })
        val best = top?.let { SmartPick(it.stream, it.info, reasons(it.info, device, target)) }

        // The list: filters first, then the chosen order.
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
