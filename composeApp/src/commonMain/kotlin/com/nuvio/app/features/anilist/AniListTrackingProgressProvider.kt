package com.nuvio.app.features.anilist

import com.nuvio.app.features.details.MetaDetailsRepository
import com.nuvio.app.features.tracking.TrackingProgressProvider
import com.nuvio.app.features.tracking.TrackingProgressSnapshot
import com.nuvio.app.features.tracking.TrackingProviderId
import com.nuvio.app.features.tracking.TrackingRefreshIntent
import com.nuvio.app.features.watchprogress.WatchProgressEntry
import com.nuvio.app.features.watchprogress.buildPlaybackVideoId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val ANILIST_PROGRESS_SOURCE = "anilist_history"

internal class AniListTrackingProgressProvider : TrackingProgressProvider {
    override val providerId = TrackingProviderId.ANILIST
    override val changes: Flow<Unit>
        get() = AniListTracker.syncState.map { }
    override val providesCompleteMetadata = true

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
        val active = state.entries.filter { entry ->
            !entry.isMovie && entry.progress > 0 &&
                (entry.status == ANILIST_STATUS_CURRENT || entry.status == ANILIST_STATUS_REPEATING)
        }
        val superseded = supersededMediaIds(active, state.entries)
        val seeds = active
            .filter { entry -> entry.mediaId !in superseded }
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

    override suspend fun prepareNextUpProgressEntries(
        entries: List<WatchProgressEntry>,
        contentId: String,
    ): List<WatchProgressEntry> {
        val targets = entries.filter { entry ->
            entry.source == ANILIST_PROGRESS_SOURCE && entry.parentMetaId == contentId
        }
        if (targets.isEmpty()) return entries
        val fetched = try {
            MetaDetailsRepository.fetch(type = targets.first().parentMetaType, id = contentId)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            null
        }
        val meta = fetched ?: return entries
        return entries.map { entry ->
            if (entry !in targets) return@map entry
            val episodeIds = listOfNotNull(
                "${entry.parentMetaId}:${entry.episodeNumber}",
                entry.trackingProviderItemId?.let { "$it:${entry.episodeNumber}" },
            )
            val season = AniListTracker.entries()
                .firstOrNull { it.providerItemId == entry.trackingProviderItemId }
                ?.let(::franchiseSeasonOf)
            val video = meta.videos.firstOrNull { video ->
                video.id in episodeIds && video.season != null && video.episode != null
            } ?: meta.videos.firstOrNull { video ->
                season != null && video.season == season && video.episode == entry.episodeNumber
            } ?: return@map entry
            entry.copy(
                seasonNumber = video.season,
                episodeNumber = video.episode,
                videoId = buildPlaybackVideoId(entry.parentMetaId, video.season, video.episode),
            )
        }
    }

    private fun franchiseSeasonOf(entry: AniListEntry): Int {
        val byId = AniListTracker.entries().associateBy { it.mediaId }
        val seen = mutableSetOf(entry.mediaId)
        var count = 1
        var current = entry
        while (true) {
            val prequelId = current.prequelIds.firstOrNull { it !in seen } ?: break
            seen += prequelId
            val prequel = byId[prequelId]
            if (prequel == null) {
                count++
                break
            }
            if (prequel.format == null || prequel.format == "TV") count++
            current = prequel
        }
        return count
    }

    private fun supersededMediaIds(active: List<AniListEntry>, all: List<AniListEntry>): Set<Int> {
        val byId = all.associateBy { it.mediaId }
        val result = mutableSetOf<Int>()
        active.forEach { entry ->
            val stack = ArrayDeque(entry.prequelIds)
            val seen = mutableSetOf<Int>()
            while (stack.isNotEmpty()) {
                val id = stack.removeLast()
                if (!seen.add(id)) continue
                val prequel = byId[id]
                if (prequel != null && prequel.updatedAtSeconds <= entry.updatedAtSeconds) result += id
                prequel?.prequelIds?.let(stack::addAll)
            }
        }
        return result
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
