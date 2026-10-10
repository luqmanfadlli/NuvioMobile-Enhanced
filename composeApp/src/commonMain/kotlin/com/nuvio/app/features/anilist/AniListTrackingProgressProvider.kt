package com.nuvio.app.features.anilist

import com.nuvio.app.features.tracking.TrackingProgressProvider
import com.nuvio.app.features.tracking.TrackingProgressSnapshot
import com.nuvio.app.features.tracking.TrackingProviderId
import com.nuvio.app.features.tracking.TrackingRefreshIntent
import com.nuvio.app.features.watchprogress.WatchProgressEntry
import com.nuvio.app.features.watchprogress.buildPlaybackVideoId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val ANILIST_PROGRESS_SOURCE = "anilist_history"

internal class AniListTrackingProgressProvider : TrackingProgressProvider {
    override val providerId = TrackingProviderId.ANILIST
    override val changes: Flow<Unit>
        get() = AniListTracker.syncState.map { }
    override val providesCompleteMetadata = true
    override val ownsCompletedHistoryProjection: Boolean = true

    override fun shouldUseAsNextUpSeed(entry: WatchProgressEntry, nowEpochMs: Long): Boolean = false

    override fun showIdSiblings(): Map<String, Set<String>> =
        AniListTracker.entries().filter { it.idMal != null }.associate { entry ->
            val aliases = setOf(entry.contentId, entry.providerItemId, "mal:${entry.idMal}")
            entry.contentId to aliases
        }

    override fun ensureLoaded() = AniListTracker.ensureLoaded()

    override suspend fun refresh(force: Boolean, sourceChanged: Boolean) {
        AniListTracker.refresh(if (force) TrackingRefreshIntent.USER_INITIATED else TrackingRefreshIntent.AUTOMATIC)
    }

    override fun snapshot(): TrackingProgressSnapshot {
        val state = AniListTracker.syncState.value
        val seeds = state.entries
            .filter { entry ->
                !entry.isMovie && entry.progress > 0 &&
                    (entry.status == ANILIST_STATUS_CURRENT || entry.status == ANILIST_STATUS_REPEATING)
            }
            .map { entry ->
                WatchProgressEntry(
                    contentType = entry.contentType,
                    parentMetaId = entry.contentId,
                    parentMetaType = entry.contentType,
                    videoId = buildPlaybackVideoId(entry.contentId, 1, entry.progress),
                    title = entry.title,
                    poster = entry.poster,
                    background = entry.banner,
                    seasonNumber = 1,
                    episodeNumber = entry.progress,
                    lastPositionMs = 0L,
                    durationMs = 0L,
                    lastUpdatedEpochMs = entry.updatedAtSeconds * 1_000L,
                    isCompleted = true,
                    progressPercent = 100f,
                    source = ANILIST_PROGRESS_SOURCE,
                    trackingProviderId = TrackingProviderId.ANILIST.storageId,
                    trackingProviderItemId = entry.providerItemId,
                    trackingSourceUrl = entry.siteUrl,
                )
            }
        return TrackingProgressSnapshot(
            entries = seeds,
            hiddenContentIds = hiddenContentIds(),
            hasLoadedRemoteProgress = state.hasLoaded,
            errorMessage = state.error?.let { "AniList sync failed" },
        )
    }

    private fun hiddenContentIds(): Set<String> =
        AniListTracker.entries()
            .filter { it.status == ANILIST_STATUS_DROPPED || it.status == ANILIST_STATUS_PAUSED }
            .flatMapTo(linkedSetOf()) { entry ->
                listOfNotNull(entry.contentId, entry.providerItemId, entry.idMal?.let { "mal:$it" })
            }

    override suspend fun removeProgress(entries: Collection<WatchProgressEntry>) {
        entries.mapNotNull { entry ->
            entry.trackingProviderItemId?.substringAfter("anilist:", "")?.toIntOrNull()
        }.distinct().forEach { mediaId ->
            AniListTracker.saveEntry(mediaId, status = ANILIST_STATUS_PAUSED)
        }
    }

    override fun isHiddenFromProgress(contentId: String): Boolean =
        AniListTracker.entries().any { entry ->
            (entry.status == ANILIST_STATUS_DROPPED || entry.status == ANILIST_STATUS_PAUSED) &&
                entry.matchesContentId(contentId)
        }

    override suspend fun refreshEpisodeProgress(contentId: String, forceRefresh: Boolean) {
        AniListTracker.refresh(if (forceRefresh) TrackingRefreshIntent.USER_INITIATED else TrackingRefreshIntent.AUTOMATIC)
    }
}
