package com.nuvio.app.features.anilist

import com.nuvio.app.features.tracking.TrackingExternalIds
import com.nuvio.app.features.tracking.TrackingMediaReference
import com.nuvio.app.features.tracking.TrackingProviderId
import com.nuvio.app.features.tracking.TrackingRatingException
import com.nuvio.app.features.tracking.TrackingRatingProvider
import com.nuvio.app.features.tracking.TrackingRatingRecord
import com.nuvio.app.features.tracking.TrackingRatingScope
import com.nuvio.app.features.tracking.TrackingRatingTarget
import com.nuvio.app.features.tracking.TrackingRefreshIntent
import kotlin.math.roundToInt

internal class AniListRatingsProvider : TrackingRatingProvider {
    override val providerId = TrackingProviderId.ANILIST
    override val supportedScopes = setOf(TrackingRatingScope.MOVIE, TrackingRatingScope.SHOW)

    override fun canRate(target: TrackingRatingTarget): Boolean =
        target.scope in supportedScopes &&
            (target.ids.anilist != null || target.ids.mal != null || target.ids.kitsu != null ||
                !target.title.isNullOrBlank())

    override suspend fun normalize(target: TrackingRatingTarget): TrackingRatingTarget {
        if (target.ids.anilist != null) return target
        AniListTracker.ensureSynced()
        val mediaId = AniListMediaResolver.resolve(target.toReference()) ?: return target
        val idMal = AniListTracker.entryFor(mediaId)?.idMal?.toLong()
        return target.copy(
            ids = target.ids.mergeMissing(TrackingExternalIds(anilist = mediaId.toLong(), mal = idMal)),
        )
    }

    override suspend fun fetchRatings(profileId: Int, scope: TrackingRatingScope): List<TrackingRatingRecord> {
        AniListTracker.requireProfile(profileId)
        AniListTracker.refresh(TrackingRefreshIntent.AUTOMATIC)
        return AniListTracker.entries()
            .filter { it.scoreRaw > 0 }
            .filter { (if (it.isMovie) TrackingRatingScope.MOVIE else TrackingRatingScope.SHOW) == scope }
            .map { entry ->
                TrackingRatingRecord(
                    scope = scope,
                    ids = TrackingExternalIds(anilist = entry.mediaId.toLong(), mal = entry.idMal?.toLong()),
                    rating = (entry.scoreRaw / 10.0).roundToInt().coerceIn(1, 10),
                )
            }
    }

    override suspend fun setRating(profileId: Int, target: TrackingRatingTarget, rating: Int) {
        AniListTracker.requireProfile(profileId)
        val mediaId = mediaId(target)
        AniListTracker.saveEntry(mediaId, scoreRaw = rating.coerceIn(1, 10) * 10)
    }

    override suspend fun removeRating(profileId: Int, target: TrackingRatingTarget) {
        AniListTracker.requireProfile(profileId)
        val mediaId = mediaId(target)
        if (AniListTracker.entryFor(mediaId) == null) return
        AniListTracker.saveEntry(mediaId, scoreRaw = 0)
    }

    private suspend fun mediaId(target: TrackingRatingTarget): Int {
        AniListTracker.ensureSynced()
        return target.ids.anilist?.toInt()
            ?: AniListMediaResolver.resolve(target.toReference())
            ?: throw TrackingRatingException("AniList could not match this title")
    }

    private fun TrackingRatingTarget.toReference() = TrackingMediaReference(
        kind = kind,
        title = title,
        year = year,
        ids = ids,
        catalog = catalog,
    )
}
