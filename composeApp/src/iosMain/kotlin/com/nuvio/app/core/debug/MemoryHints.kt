package com.nuvio.app.core.debug

import com.nuvio.app.features.details.MetaDetailsRepository
import com.nuvio.app.features.home.HomeRepository
import com.nuvio.app.features.streams.StreamsRepository
import com.nuvio.app.features.library.LibraryRepository
import com.nuvio.app.features.livetv.LiveTvRepository
import com.nuvio.app.features.mdblist.MdbListTracker
import com.nuvio.app.features.simkl.SimklSyncRepository
import com.nuvio.app.features.trakt.TraktLibraryRepository
import com.nuvio.app.features.watched.WatchedRepository

/**
 * Debug: sizes of the largest long-lived Kotlin collections, logged with the memory breakdown to
 * find what holds the ~450 MB Kotlin heap (VM tag 246). Called from Swift (MemoryHintsKt).
 */
@OptIn(kotlin.native.runtime.NativeRuntimeApi::class, kotlin.ExperimentalStdlibApi::class)
fun memoryHints(): String {
    fun part(name: String, block: () -> Any?): String =
        "$name=" + (runCatching { block() }.getOrNull() ?: "?")
    return listOf(
        part("liveChannels") { LiveTvRepository.uiState.value.channels.size },
        part("liveEpg") { LiveTvRepository.uiState.value.epgPrograms.values.sumOf { it.size } },
        part("homeSections") { HomeRepository.uiState.value.sections.size },
        part("homeItems") { HomeRepository.uiState.value.sections.sumOf { it.items.size } },
        part("library") { LibraryRepository.uiState.value.items.size },
        part("watched") { WatchedRepository.uiState.value.items.size },
        part("watchedKeys") { WatchedRepository.uiState.value.watchedKeys.size },
        part("traktItems") { TraktLibraryRepository.uiState.value.allItems.size },
        part("simklEntries") { SimklSyncRepository.state.value.snapshot.entries.size },
        part("mdbWatched") { MdbListTracker.sync.currentSnapshot()?.watched?.size },
        part("mdbPlayback") { MdbListTracker.sync.currentSnapshot()?.playback?.size },
        part("metaCache") { MetaDetailsRepository.debugCachedMetaCount },
        part("metaCacheVideos") { MetaDetailsRepository.debugCachedVideoCount },
        part("streamGroups") { StreamsRepository.uiState.value.groups.size },
        part("gcLiveMB") {
            kotlin.native.runtime.GC.lastGCInfo?.memoryUsageAfter?.values
                ?.sumOf { it.totalObjectsSizeBytes }?.div(1048576)
        },
        part("gcBeforeMB") {
            kotlin.native.runtime.GC.lastGCInfo?.memoryUsageBefore?.values
                ?.sumOf { it.totalObjectsSizeBytes }?.div(1048576)
        },
    ).joinToString(" ", prefix = "kotlin[", postfix = "]")
}
