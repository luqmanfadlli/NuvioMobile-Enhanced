package com.nuvio.app.features.wetrakr

import com.nuvio.app.features.tracking.TrackingHistoryItem
import com.nuvio.app.features.tracking.TrackingProviderId
import com.nuvio.app.features.tracking.TrackingRefreshIntent
import com.nuvio.app.features.tracking.TrackingWatchedProvider
import com.nuvio.app.features.tracking.buildTrackingMediaReference
import com.nuvio.app.features.watched.WatchedItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

class WeTrakrWatchedSyncAdapter internal constructor(
    private val sync: WeTrakrSyncRepository,
    private val writes: WeTrakrTrackingWrites,
    auth: WeTrakrAuthStore,
    private val activeProfile: StateFlow<Int>,
) : TrackingWatchedProvider {
    override val providerId = TrackingProviderId.WETRAKR
    private val changes = combine(sync.projectionState, auth.state, activeProfile) { _, _, _ -> Unit }

    override suspend fun pull(profileId: Int, pageSize: Int): List<WatchedItem> {
        val scope = scope(profileId)
        sync.refresh(TrackingRefreshIntent.AUTOMATIC)
        if (sync.currentScope() != scope) throw CancellationException("WeTrakr account changed")
        val state = sync.state.value
        if (!state.hasLoaded && state.error != null) throw WeTrakrApiException(0, message = "WeTrakr sync unavailable")
        return sync.currentProjection().watchedItems
    }

    override suspend fun pullExtraWatchedKeys(profileId: Int): Set<String> {
        scope(profileId)
        return sync.currentProjection().watchedKeys
    }

    override fun observeExtraWatchedKeys(profileId: Int): Flow<Set<String>> =
        combine(changes, activeProfile) { _, active ->
            if (profileId == active) sync.currentProjection().watchedKeys else emptySet()
        }.distinctUntilChanged()

    override suspend fun push(profileId: Int, items: Collection<WatchedItem>) {
        val pushable = items.filterNot(WatchedItem::isWholeSeriesMark)
        if (pushable.isEmpty()) return
        writes.addToHistory(profileId, pushable.map { TrackingHistoryItem(it.reference(), it.markedAtEpochMs) })
    }

    override suspend fun delete(profileId: Int, items: Collection<WatchedItem>) {
        if (items.isEmpty()) return
        writes.removeFromHistory(profileId, items.map { it.reference() })
    }

    private fun scope(profileId: Int) = sync.currentScope().also {
        if (it.profileId != profileId) throw CancellationException("WeTrakr profile changed")
    }

    private fun WatchedItem.reference() = buildTrackingMediaReference(
        contentType = type,
        parentMetaId = id,
        videoId = videoId,
        title = name,
        releaseInfo = releaseInfo,
        seasonNumber = season,
        episodeNumber = episode,
    )
}

private fun WatchedItem.isWholeSeriesMark(): Boolean =
    season == null && episode == null && type.trim().lowercase() !in setOf("movie", "film")
