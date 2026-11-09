package com.nuvio.app.features.sports.compat

import com.nuvio.app.features.livetv.LiveTvChannel
import com.nuvio.app.features.livetv.LiveTvEpgProgram
import com.nuvio.app.features.livetv.LiveTvRepository as NuvioLiveTv
import com.nuvio.app.features.livetv.LiveTvUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The seam between the Sports hub (ported from NuvioPlusIPTV-Mobile) and Nuvio Enhanced's own IPTV: one channel list,
 * one guide, owned by `com.nuvio.app.features.livetv.LiveTvRepository`. The hub reads them through the same names it
 * used before, and nothing here fetches, parses or stores a playlist or guide a second time.
 */

/** JSON settings the hub's saved files use (unknown keys ignored, lenient). */
internal val liveTvJson: kotlinx.serialization.json.Json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; isLenient = true }

/** What the hub needs to know about Live TV loading. */
internal data class LiveTvStatus(val loading: Boolean, val loadedOnce: Boolean)

internal fun LiveTvChannel.toSportsChannel(number: Int): LiveChannel {
    val source = playlistId.orEmpty()
    val groupName = group?.takeIf { it.isNotBlank() } ?: "Channels"
    return LiveChannel(
        key = id, sourceId = source, sourceName = playlistName.orEmpty(), name = name,
        tvgId = epgId, tvgName = null, logo = logoUrl,
        groupId = "$source:$groupName", group = groupName, number = number, url = streamUrl, headers = headers,
    )
}

internal fun LiveTvEpgProgram.toSportsProgram() = EpgProgram(
    startMs = startMillis, stopMs = endMillis, title = title, description = description, category = category,
)

internal object LiveTvRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val channels: StateFlow<List<LiveChannel>> = NuvioLiveTv.uiState
        .map { it.channels }
        .distinctUntilChanged()
        .map { list -> list.mapIndexed { index, channel -> channel.toSportsChannel(index + 1) } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** Channels as shown to the user. Nuvio has no hidden or locked channels, so this is the whole list. */
    val displayChannels: StateFlow<List<LiveChannel>> get() = channels

    /** Changes whenever Nuvio loads or refreshes the guide, so the matcher knows to look again. */
    val guideGeneration: StateFlow<Long> = NuvioLiveTv.uiState
        .map { guideSignature(it) }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, 0L)

    val status: StateFlow<LiveTvStatus> = NuvioLiveTv.uiState
        .map { LiveTvStatus(loading = it.isLoading || it.isEpgLoading, loadedOnce = it.channels.isNotEmpty() && !it.isLoading) }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, LiveTvStatus(loading = false, loadedOnce = false))

    fun ensureLoaded() {
        NuvioLiveTv.ensureLoaded()
        NuvioLiveTv.ensureChannelsLoaded()
    }

    /** Guide listings for [keys] overlapping the window. Keys are Nuvio channel ids. */
    fun programsIn(keys: Collection<String>, fromMs: Long, toMs: Long): Map<String, List<EpgProgram>> {
        val guide = NuvioLiveTv.uiState.value.epgPrograms
        val out = HashMap<String, List<EpgProgram>>()
        for (key in keys) {
            val list = guide[key] ?: continue
            val inWindow = list.filter { it.endMillis > fromMs && it.startMillis < toMs }
            if (inWindow.isNotEmpty()) out[key] = inWindow.map { it.toSportsProgram() }
        }
        return out
    }

    private fun guideSignature(state: LiveTvUiState): Long {
        var hash = state.epgPrograms.size.toLong()
        for ((id, list) in state.epgPrograms) hash = hash * 31 + id.hashCode() + list.size * 7L + (list.firstOrNull()?.startMillis ?: 0L)
        return hash
    }
}

/** What's on now and next on one channel. */
data class NowNext(val now: EpgProgram?, val next: EpgProgram?)

/** Searches the guide Nuvio holds in memory (3 hours back, 48 hours ahead) the way the stored guide used to be searched. */
internal object EpgStore {
    private fun snapshot(): Map<String, List<LiveTvEpgProgram>> = NuvioLiveTv.uiState.value.epgPrograms

    fun range(fromMs: Long, toMs: Long, light: Boolean, channels: Collection<String>): Map<String, List<EpgProgram>> {
        val guide = snapshot()
        val out = HashMap<String, List<EpgProgram>>()
        for (key in channels) {
            val list = guide[key] ?: continue
            val hits = list.filter { it.endMillis > fromMs && it.startMillis < toMs }
            if (hits.isNotEmpty()) out[key] = hits.map { it.toSportsProgram() }
        }
        return out
    }

    fun findByWords(fromMs: Long, toMs: Long, minStopMs: Long, words: Collection<String>, inDescription: Boolean = true): List<Pair<String, EpgProgram>> {
        val clean = words.map { it.trim().lowercase() }.filter { it.length >= 3 }.distinct()
        if (clean.isEmpty()) return emptyList()
        val out = ArrayList<Pair<String, EpgProgram>>()
        for ((key, list) in snapshot()) for (p in list) {
            if (p.startMillis < fromMs || p.startMillis >= toMs || p.endMillis <= minStopMs) continue
            val text = (p.title + if (inDescription) " " + p.description.orEmpty() else "").lowercase()
            if (clean.any { it in text }) out += key to p.toSportsProgram()
        }
        return out
    }

    fun scanListings(fromMs: Long, toMs: Long, minStopMs: Long, onRow: (String, Long, Long, String, String?, String?) -> Unit) {
        for ((key, list) in snapshot()) for (p in list) {
            if (p.startMillis < fromMs || p.startMillis >= toMs || p.endMillis <= minStopMs) continue
            onRow(key, p.startMillis, p.endMillis, p.title, p.description, p.category)
        }
    }
}

/** The failover hooks the hub sets and Nuvio's player can call. Not wired into Nuvio's player yet. */
internal object LiveTvPlayback {
    var gameFailover: ((failedKey: String, tried: Set<String>) -> LiveChannel?)? = null
    var currentKey: String? = null
}
