package com.nuvio.app.features.anilist

import com.nuvio.app.features.library.LibraryItem
import com.nuvio.app.features.library.LibrarySection
import com.nuvio.app.features.tracking.TrackingLibraryProvider
import com.nuvio.app.features.tracking.TrackingLibrarySnapshot
import com.nuvio.app.features.tracking.TrackingLibraryTab
import com.nuvio.app.features.tracking.TrackingLibraryTabKind
import com.nuvio.app.features.tracking.TrackingMembershipRemovalConfirmation
import com.nuvio.app.features.tracking.TrackingMembershipRemovalImpact
import com.nuvio.app.features.tracking.TrackingMembershipResolution
import com.nuvio.app.features.tracking.TrackingProviderId
import com.nuvio.app.features.tracking.TrackingRefreshIntent
import com.nuvio.app.features.tracking.buildTrackingMediaReference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal val AniListLibraryTabs: List<TrackingLibraryTab> = listOf(
    ANILIST_WATCHING_KEY to "Watching",
    ANILIST_PLANNING_KEY to "Plan to Watch",
    ANILIST_COMPLETED_KEY to "Completed",
    ANILIST_PAUSED_KEY to "On Hold",
    ANILIST_DROPPED_KEY to "Dropped",
).map { (key, title) ->
    TrackingLibraryTab(
        key = key,
        title = title,
        providerId = TrackingProviderId.ANILIST,
        kind = TrackingLibraryTabKind.STATUS,
        selectionGroup = ANILIST_STATUS_GROUP,
    )
}

internal fun AniListEntry.toLibraryItem(): LibraryItem = LibraryItem(
    id = contentId,
    type = contentType,
    name = title,
    poster = poster,
    banner = banner,
    releaseInfo = year?.toString(),
    listKeys = setOf(aniListStatusKey(status)),
    mediaCategory = "anime",
    rawPosterUrl = poster,
    trackingProviderId = TrackingProviderId.ANILIST.storageId,
    trackingProviderItemId = providerItemId,
    trackingSourceUrl = siteUrl,
    savedAtEpochMs = updatedAtSeconds * 1_000L,
)

internal fun AniListEntry.matchesContentId(contentId: String): Boolean =
    contentId == this.contentId || contentId == providerItemId || (idMal != null && contentId == "mal:$idMal")

internal class AniListTrackingLibraryProvider : TrackingLibraryProvider {
    override val providerId = TrackingProviderId.ANILIST
    override val changes: Flow<Unit>
        get() = AniListTracker.syncState.map { }
    override val connectionRefreshIntent = TrackingRefreshIntent.AUTOMATIC

    override fun ensureLoaded() = AniListTracker.ensureLoaded()

    override suspend fun refresh(intent: TrackingRefreshIntent) = AniListTracker.refresh(intent)

    override fun snapshot(): TrackingLibrarySnapshot {
        val state = AniListTracker.syncState.value
        val items = state.entries
            .map(AniListEntry::toLibraryItem)
            .sortedByDescending(LibraryItem::savedAtEpochMs)
        val sections = AniListLibraryTabs.mapNotNull { tab ->
            items.filter { tab.key in it.listKeys }
                .takeIf { it.isNotEmpty() }
                ?.let { LibrarySection(type = tab.key, displayTitle = tab.title, items = it) }
        }
        return TrackingLibrarySnapshot(
            items = items,
            sections = sections,
            tabs = AniListLibraryTabs,
            hasLoaded = state.hasLoaded,
            isLoading = state.isLoading,
            errorMessage = state.error?.let { "AniList sync failed" },
        )
    }

    override fun contains(contentId: String, contentType: String?): Boolean =
        AniListTracker.entries().any { it.matchesContentId(contentId) }

    override fun find(contentId: String): LibraryItem? =
        AniListTracker.entries().firstOrNull { it.matchesContentId(contentId) }?.toLibraryItem()

    private fun entryFor(item: LibraryItem): AniListEntry? {
        val entries = AniListTracker.entries()
        return entries.firstOrNull { it.matchesContentId(item.id) }
            ?: item.trackingProviderItemId?.let { providerItemId -> entries.firstOrNull { it.providerItemId == providerItemId } }
    }

    override suspend fun membership(item: LibraryItem): Map<String, Boolean> {
        val entry = entryFor(item)
        val activeKey = entry?.let { aniListStatusKey(it.status) }
        return AniListLibraryTabs.associate { tab -> tab.key to (tab.key == activeKey) }
    }

    override fun toggledDefaultMembership(currentMembership: Map<String, Boolean>): Map<String, Boolean> {
        val isMember = currentMembership.values.any { it }
        return AniListLibraryTabs.associate { tab ->
            tab.key to (!isMember && tab.key == ANILIST_PLANNING_KEY)
        }
    }

    override fun membershipRemovalConfirmation(
        item: LibraryItem,
        desiredMembership: Map<String, Boolean>,
    ): TrackingMembershipRemovalConfirmation? {
        if (desiredMembership.values.any { it }) return null
        val entry = entryFor(item) ?: return null
        val impacts = buildSet {
            if (entry.progress > 0) add(TrackingMembershipRemovalImpact.WATCHED_HISTORY)
            if (entry.scoreRaw > 0) add(TrackingMembershipRemovalImpact.RATING)
        }
        if (impacts.isEmpty()) return null
        return TrackingMembershipRemovalConfirmation(providerId, impacts)
    }

    override suspend fun applyMembership(
        profileId: Int,
        item: LibraryItem,
        desiredMembership: Map<String, Boolean>,
        destructiveRemovalConfirmed: Boolean,
    ): TrackingMembershipResolution? {
        AniListTracker.requireProfile(profileId)
        AniListTracker.ensureSynced()
        val media = buildTrackingMediaReference(
            contentType = item.type,
            parentMetaId = item.id,
            title = item.name,
            releaseInfo = item.releaseInfo,
        )
        val mediaId = entryFor(item)?.mediaId
            ?: AniListMediaResolver.resolve(media)
            ?: throw IllegalStateException("AniList could not match this title")
        val chosenKey = desiredMembership.entries.firstOrNull { it.value && it.key in AniListStatusKeys.values }?.key
        if (chosenKey == null) {
            AniListTracker.removeEntry(mediaId)
            return null
        }
        val status = aniListStatusFor(chosenKey) ?: return null
        val existing = AniListTracker.entryFor(mediaId)
        val progress = if (status == ANILIST_STATUS_COMPLETED) existing?.episodes else null
        AniListTracker.saveEntry(mediaId, status = status, progress = progress)
        return null
    }
}
