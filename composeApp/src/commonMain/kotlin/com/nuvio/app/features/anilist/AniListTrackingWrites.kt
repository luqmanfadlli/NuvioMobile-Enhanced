package com.nuvio.app.features.anilist

import co.touchlab.kermit.Logger
import com.nuvio.app.features.tracking.TrackingHistoryItem
import com.nuvio.app.features.tracking.TrackingHistoryWriter
import com.nuvio.app.features.tracking.TrackingListStatus
import com.nuvio.app.features.tracking.TrackingListWriter
import com.nuvio.app.features.tracking.TrackingMediaKind
import com.nuvio.app.features.tracking.TrackingMediaReference
import com.nuvio.app.features.tracking.TrackingMutationResolution
import com.nuvio.app.features.tracking.TrackingMutationResult
import com.nuvio.app.features.tracking.TrackingProviderId
import com.nuvio.app.features.tracking.TrackingScrobbleAction
import com.nuvio.app.features.tracking.TrackingScrobbleEvent
import com.nuvio.app.features.tracking.TrackingScrobbler
import kotlinx.coroutines.CancellationException

private const val ANILIST_SCROBBLE_COMPLETE_PERCENT = 80.0

private val log = Logger.withTag("AniListScrobble")

internal class AniListTrackingWrites : TrackingListWriter, TrackingHistoryWriter, TrackingScrobbler {
    override val providerId = TrackingProviderId.ANILIST

    override suspend fun moveToList(
        profileId: Int,
        items: Collection<TrackingMediaReference>,
        destination: TrackingListStatus,
    ): TrackingMutationResult {
        AniListTracker.requireProfile(profileId)
        AniListTracker.ensureSynced()
        var attempted = 0
        var notFound = 0
        val resolutions = mutableListOf<TrackingMutationResolution>()
        items.forEach { media ->
            val mediaId = AniListMediaResolver.resolve(media)
            if (mediaId == null) {
                notFound++
                return@forEach
            }
            val existing = AniListTracker.entryFor(mediaId)
            val status = destination.toAniListStatus()
            val progress = if (destination == TrackingListStatus.COMPLETED) existing?.episodes else null
            val saved = AniListTracker.saveEntry(mediaId, status = status, progress = progress)
            attempted++
            resolutions += TrackingMutationResolution(
                listStatus = saved.status.toTrackingListStatus(),
                mediaKind = if (saved.isMovie) TrackingMediaKind.MOVIE else TrackingMediaKind.ANIME,
                providerSubtype = saved.format,
            )
        }
        return TrackingMutationResult(attempted, notFound, resolutions)
    }

    override suspend fun removeFromList(
        profileId: Int,
        items: Collection<TrackingMediaReference>,
    ): TrackingMutationResult {
        AniListTracker.requireProfile(profileId)
        AniListTracker.ensureSynced()
        var attempted = 0
        var notFound = 0
        items.forEach { media ->
            val mediaId = AniListMediaResolver.resolve(media)
            if (mediaId == null) {
                notFound++
                return@forEach
            }
            AniListTracker.removeEntry(mediaId)
            attempted++
        }
        return TrackingMutationResult(attempted, notFound)
    }

    override suspend fun addToHistory(
        profileId: Int,
        items: Collection<TrackingHistoryItem>,
    ): TrackingMutationResult {
        AniListTracker.requireProfile(profileId)
        AniListTracker.ensureSynced()
        var notFound = 0
        val targets = linkedMapOf<Int, HistoryTarget>()
        items.forEach { item ->
            val mediaId = AniListMediaResolver.resolve(item.media)
            if (mediaId == null) {
                notFound++
                return@forEach
            }
            val episode = item.media.episode?.number
            val wholeTitle = episode == null || item.media.kind == TrackingMediaKind.MOVIE
            val current = targets[mediaId] ?: HistoryTarget()
            targets[mediaId] = current.copy(
                wholeTitle = current.wholeTitle || wholeTitle,
                episode = if (wholeTitle) current.episode else maxOf(current.episode ?: 0, episode ?: 0),
            )
        }
        var attempted = 0
        val resolutions = mutableListOf<TrackingMutationResolution>()
        targets.forEach { (mediaId, target) ->
            val saved = applyWatched(mediaId, target)
            attempted++
            if (saved != null) {
                resolutions += TrackingMutationResolution(
                    listStatus = saved.status.toTrackingListStatus(),
                    mediaKind = if (saved.isMovie) TrackingMediaKind.MOVIE else TrackingMediaKind.ANIME,
                    providerSubtype = saved.format,
                )
            }
        }
        return TrackingMutationResult(attempted, notFound, resolutions)
    }

    override suspend fun removeFromHistory(
        profileId: Int,
        items: Collection<TrackingMediaReference>,
    ): TrackingMutationResult {
        AniListTracker.requireProfile(profileId)
        AniListTracker.ensureSynced()
        var attempted = 0
        var notFound = 0
        val lowest = linkedMapOf<Int, Int?>()
        items.forEach { media ->
            val mediaId = AniListMediaResolver.resolve(media)
            if (mediaId == null) {
                notFound++
                return@forEach
            }
            val episode = media.episode?.number?.takeIf { media.kind != TrackingMediaKind.MOVIE }
            val previous = if (lowest.containsKey(mediaId)) lowest[mediaId] else Int.MAX_VALUE
            lowest[mediaId] = when {
                episode == null -> null
                previous == null -> null
                else -> minOf(previous, episode)
            }
        }
        lowest.forEach { (mediaId, episode) ->
            val existing = AniListTracker.entryFor(mediaId) ?: return@forEach
            val newProgress = if (episode == null) 0 else (episode - 1).coerceAtLeast(0)
            if (episode != null && existing.progress < episode) return@forEach
            val status = if (newProgress == 0) ANILIST_STATUS_PLANNING else ANILIST_STATUS_CURRENT
            AniListTracker.saveEntry(mediaId, status = status, progress = newProgress)
            attempted++
        }
        return TrackingMutationResult(attempted, notFound)
    }

    override suspend fun scrobble(
        profileId: Int,
        action: TrackingScrobbleAction,
        event: TrackingScrobbleEvent,
    ) {
        AniListTracker.requireProfile(profileId)
        when (action) {
            TrackingScrobbleAction.STOP -> {
                if (event.progressPercent >= ANILIST_SCROBBLE_COMPLETE_PERCENT) {
                    addToHistory(profileId, listOf(TrackingHistoryItem(event.media)))
                }
            }
            TrackingScrobbleAction.START -> {
                try {
                    AniListTracker.ensureSynced()
                    val mediaId = AniListMediaResolver.resolve(event.media)
                    if (mediaId == null) {
                        log.w { "START unresolved media ids=${event.media.ids} title=${event.media.title}" }
                        return
                    }
                    val existing = AniListTracker.entryFor(mediaId)
                    log.d { "START mediaId=$mediaId existingStatus=${existing?.status}" }
                    if (existing == null || existing.status in setOf(ANILIST_STATUS_PLANNING, ANILIST_STATUS_PAUSED, ANILIST_STATUS_DROPPED)) {
                        AniListTracker.saveEntry(mediaId, status = ANILIST_STATUS_CURRENT)
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    log.e(error) { "START failed" }
                }
            }
            TrackingScrobbleAction.PAUSE -> Unit
        }
    }

    private data class HistoryTarget(
        val wholeTitle: Boolean = false,
        val episode: Int? = null,
    )

    private suspend fun applyWatched(mediaId: Int, target: HistoryTarget): AniListEntry? {
        val existing = AniListTracker.entryFor(mediaId)
        if (target.wholeTitle || target.episode == null) {
            if (existing?.status == ANILIST_STATUS_COMPLETED) return existing
            val progress = existing?.episodes ?: if (existing?.isMovie == true) 1 else null
            return AniListTracker.saveEntry(mediaId, status = ANILIST_STATUS_COMPLETED, progress = progress)
        }
        val episode = target.episode
        if (existing != null && existing.progress >= episode &&
            existing.status in setOf(ANILIST_STATUS_CURRENT, ANILIST_STATUS_COMPLETED, ANILIST_STATUS_REPEATING)
        ) {
            return existing
        }
        val newProgress = maxOf(existing?.progress ?: 0, episode)
        val total = existing?.episodes
        val status = when {
            total != null && newProgress >= total -> ANILIST_STATUS_COMPLETED
            existing?.status == ANILIST_STATUS_REPEATING -> ANILIST_STATUS_REPEATING
            else -> ANILIST_STATUS_CURRENT
        }
        val saved = AniListTracker.saveEntry(mediaId, status = status, progress = newProgress)
        val savedTotal = saved.episodes
        if (savedTotal != null && newProgress >= savedTotal && saved.status != ANILIST_STATUS_COMPLETED) {
            return AniListTracker.saveEntry(mediaId, status = ANILIST_STATUS_COMPLETED, progress = savedTotal)
        }
        return saved
    }
}
