package com.nuvio.app.features.anilist

import com.nuvio.app.features.tracking.TrackingHistoryItem
import com.nuvio.app.features.tracking.TrackingProviderId
import com.nuvio.app.features.tracking.TrackingRefreshIntent
import com.nuvio.app.features.tracking.buildTrackingMediaReference
import com.nuvio.app.features.watched.WatchedItem
import com.nuvio.app.features.watched.watchedItemKeys
import com.nuvio.app.features.tracking.TrackingWatchedProvider
import com.nuvio.app.features.watchprogress.buildPlaybackVideoId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private const val ANILIST_MAX_WATCHED_EPISODES = 3000

internal class AniListWatchedSyncAdapter : TrackingWatchedProvider {
    override val providerId = TrackingProviderId.ANILIST
    private val writes = AniListTrackingWrites()

    private fun watchedEpisodeCount(entry: AniListEntry): Int {
        val base = when {
            entry.progress > 0 -> entry.progress
            entry.status == ANILIST_STATUS_COMPLETED -> entry.episodes ?: 0
            else -> 0
        }
        return base.coerceAtMost(ANILIST_MAX_WATCHED_EPISODES)
    }

    private fun toWatchedItems(entries: List<AniListEntry>): List<WatchedItem> =
        entries.flatMap { entry ->
            val markedAt = entry.updatedAtSeconds * 1_000L
            if (entry.isMovie) {
                if (entry.status != ANILIST_STATUS_COMPLETED && entry.progress < 1) emptyList()
                else listOf(
                    WatchedItem(
                        id = entry.contentId,
                        type = entry.contentType,
                        name = entry.title,
                        poster = entry.poster,
                        releaseInfo = entry.year?.toString(),
                        trackingProviderId = providerId.storageId,
                        trackingProviderItemId = entry.providerItemId,
                        trackingSourceUrl = entry.siteUrl,
                        markedAtEpochMs = markedAt,
                    ),
                )
            } else {
                (1..watchedEpisodeCount(entry)).map { episode ->
                    WatchedItem(
                        id = entry.contentId,
                        type = entry.contentType,
                        name = entry.title,
                        poster = entry.poster,
                        releaseInfo = entry.year?.toString(),
                        season = 1,
                        episode = episode,
                        videoId = buildPlaybackVideoId(entry.contentId, 1, episode),
                        trackingProviderId = providerId.storageId,
                        trackingProviderItemId = entry.providerItemId,
                        trackingSourceUrl = entry.siteUrl,
                        markedAtEpochMs = markedAt,
                    )
                }
            }
        }.sortedByDescending(WatchedItem::markedAtEpochMs)

    private fun extraKeys(entries: List<AniListEntry>): Set<String> =
        entries.filter { it.idMal != null }.flatMapTo(linkedSetOf()) { entry ->
            if (entry.isMovie) {
                if (entry.status == ANILIST_STATUS_COMPLETED) watchedItemKeys(entry.contentType, entry.providerItemId)
                else emptySet()
            } else {
                (1..watchedEpisodeCount(entry)).flatMap { episode ->
                    watchedItemKeys(entry.contentType, entry.providerItemId, 1, episode)
                }
            }
        }

    override suspend fun pull(profileId: Int, pageSize: Int): List<WatchedItem> {
        AniListTracker.ensureLoaded()
        AniListTracker.refresh(TrackingRefreshIntent.AUTOMATIC)
        return toWatchedItems(AniListTracker.entries())
    }

    override suspend fun pullExtraWatchedKeys(profileId: Int): Set<String> {
        AniListTracker.ensureLoaded()
        return extraKeys(AniListTracker.entries())
    }

    override fun observeExtraWatchedKeys(profileId: Int): Flow<Set<String>> =
        AniListTracker.syncState.map { extraKeys(it.entries) }.distinctUntilChanged()

    override suspend fun push(profileId: Int, items: Collection<WatchedItem>) {
        writes.addToHistory(
            profileId,
            items.map { item ->
                TrackingHistoryItem(
                    media = buildTrackingMediaReference(
                        contentType = item.type,
                        parentMetaId = item.id,
                        videoId = item.videoId,
                        title = item.name,
                        releaseInfo = item.releaseInfo,
                        seasonNumber = item.season,
                        episodeNumber = item.episode,
                    ),
                    watchedAtEpochMs = item.markedAtEpochMs,
                )
            },
        )
    }

    override suspend fun delete(profileId: Int, items: Collection<WatchedItem>) {
        writes.removeFromHistory(
            profileId,
            items.map { item ->
                buildTrackingMediaReference(
                    contentType = item.type,
                    parentMetaId = item.id,
                    videoId = item.videoId,
                    title = item.name,
                    releaseInfo = item.releaseInfo,
                    seasonNumber = item.season,
                    episodeNumber = item.episode,
                )
            },
        )
    }
}
