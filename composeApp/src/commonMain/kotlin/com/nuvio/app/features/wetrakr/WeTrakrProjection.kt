package com.nuvio.app.features.wetrakr

import com.nuvio.app.features.home.PosterShape
import com.nuvio.app.features.library.LibraryItem
import com.nuvio.app.features.library.LibrarySection
import com.nuvio.app.features.tracking.TrackingListStatus
import com.nuvio.app.features.tracking.TrackingProviderId
import com.nuvio.app.features.watched.WatchedItem
import com.nuvio.app.features.watched.watchedItemKeys
import com.nuvio.app.features.watchprogress.WatchProgressEntry
import com.nuvio.app.features.watchprogress.buildPlaybackVideoId

internal const val WETRAKR_STATUS_SELECTION_GROUP = "wetrakr:status"
internal const val WETRAKR_SOURCE_PLAYBACK = "wetrakr_playback"
internal const val WETRAKR_SOURCE_PROGRESS = "wetrakr_progress"
internal const val WETRAKR_SOURCE_LOCAL = "wetrakr_local"
private const val WETRAKR_MAX_PLAYBACK_PERCENT = 97.0

internal data class WeTrakrStatusDefinition(
    val status: String,
    val key: String,
    val title: String,
    val trackingStatus: TrackingListStatus?,
    val contentTypes: Set<String>,
    val isMembershipDestination: Boolean = true,
)

private val weTrakrAllTypes = setOf("movie", "series", "tv", "show", "anime")
private val weTrakrSeriesTypes = setOf("series", "tv", "show", "anime")

internal val weTrakrStatusDefinitions = listOf(
    WeTrakrStatusDefinition(WeTrakrStatus.PLANNING, "wetrakr:status:planning", "Planning", TrackingListStatus.PLAN_TO_WATCH, weTrakrAllTypes),
    WeTrakrStatusDefinition(WeTrakrStatus.WATCHING, "wetrakr:status:watching", "Watching", TrackingListStatus.WATCHING, weTrakrSeriesTypes),
    WeTrakrStatusDefinition(WeTrakrStatus.WAITING, "wetrakr:status:waiting", "Waiting", null, weTrakrSeriesTypes, isMembershipDestination = false),
    WeTrakrStatusDefinition(WeTrakrStatus.WATCHED, "wetrakr:status:watched", "Watched", TrackingListStatus.COMPLETED, weTrakrAllTypes),
    WeTrakrStatusDefinition(WeTrakrStatus.PAUSED, "wetrakr:status:paused", "Paused", TrackingListStatus.ON_HOLD, weTrakrSeriesTypes),
    WeTrakrStatusDefinition(WeTrakrStatus.DROPPED, "wetrakr:status:dropped", "Dropped", TrackingListStatus.DROPPED, weTrakrAllTypes),
)

internal fun weTrakrStatusDefinition(key: String): WeTrakrStatusDefinition? =
    weTrakrStatusDefinitions.firstOrNull { it.key == key }

internal fun weTrakrStatusDefinitionFor(status: String?): WeTrakrStatusDefinition? =
    weTrakrStatusDefinitions.firstOrNull { it.status.equals(status, ignoreCase = true) }

internal fun weTrakrStatusFor(status: TrackingListStatus, isMovie: Boolean): String = when (status) {
    TrackingListStatus.PLAN_TO_WATCH -> WeTrakrStatus.PLANNING
    TrackingListStatus.COMPLETED -> WeTrakrStatus.WATCHED
    TrackingListStatus.DROPPED -> WeTrakrStatus.DROPPED
    TrackingListStatus.WATCHING -> if (isMovie) WeTrakrStatus.PLANNING else WeTrakrStatus.WATCHING
    TrackingListStatus.ON_HOLD -> if (isMovie) WeTrakrStatus.PLANNING else WeTrakrStatus.PAUSED
}

internal fun weTrakrTypeMatches(isMovie: Boolean, contentType: String?): Boolean {
    val normalized = contentType?.trim()?.lowercase()?.takeIf(String::isNotEmpty) ?: return true
    return if (normalized == "movie" || normalized == "film") isMovie else !isMovie
}

internal fun weTrakrShowKey(contentId: String): String = "show:${normalizeWeTrakrContentId(contentId)}"

internal class WeTrakrProjection(val snapshot: WeTrakrSyncSnapshot) {
    val listEntries: List<WeTrakrListEntry> = snapshot.entries
        .filter { entry -> weTrakrStatusDefinitionFor(entry.status) != null && entry.media.ids.contentId() != null }
        .groupBy { entry -> "${entry.media.type.lowercase()}:${entry.media.id}" }
        .map { (_, group) -> group.maxBy { maxOf(it.addedAtEpochMs, it.watchedAtEpochMs) } }

    private val aliasIndex: Map<String, List<WeTrakrListEntry>> = buildMap<String, MutableList<WeTrakrListEntry>> {
        listEntries.forEach { entry ->
            entry.media.ids.aliases().forEach { alias ->
                getOrPut(alias.lowercase()) { mutableListOf() }.add(entry)
            }
        }
    }

    private val showMedia: Map<Long, WeTrakrMedia> = buildMap {
        snapshot.playback.map(WeTrakrPlayback::media).filterNot(WeTrakrMedia::isMovie).forEach { put(it.id, it) }
        listEntries.map(WeTrakrListEntry::media).filterNot(WeTrakrMedia::isMovie).forEach { put(it.id, it) }
    }

    private val showMediaByAlias: Map<String, WeTrakrMedia> = buildMap {
        showMedia.values.forEach { media ->
            media.ids.aliases().forEach { alias ->
                val key = alias.lowercase()
                if (!containsKey(key)) put(key, media)
            }
        }
    }

    private val episodesByShow: Map<String, List<WeTrakrWatchedEpisode>> = snapshot.watchedEpisodes
        .mapNotNull { episode -> showContentId(episode)?.let { normalizeWeTrakrContentId(it) to episode } }
        .groupBy({ it.first }, { it.second })

    fun entriesFor(contentId: String, contentType: String? = null): List<WeTrakrListEntry> =
        aliasIndex[normalizeWeTrakrContentId(contentId)].orEmpty()
            .filter { weTrakrTypeMatches(it.media.isMovie, contentType) }

    fun entryFor(contentId: String, contentType: String? = null): WeTrakrListEntry? =
        entriesFor(contentId, contentType).maxByOrNull { maxOf(it.addedAtEpochMs, it.watchedAtEpochMs) }

    fun membershipKeys(contentId: String, contentType: String? = null): Set<String> =
        entriesFor(contentId, contentType).mapNotNullTo(linkedSetOf()) { weTrakrStatusDefinitionFor(it.status)?.key }

    fun mediaFor(contentId: String, contentType: String? = null): WeTrakrMedia? =
        entryFor(contentId, contentType)?.media
            ?: if (contentType == null || !weTrakrTypeMatches(true, contentType)) {
                showMediaByAlias[normalizeWeTrakrContentId(contentId)]
            } else {
                null
            }

    val libraryItems: List<LibraryItem>
    val librarySections: List<LibrarySection>

    init {
        val sections = weTrakrStatusDefinitions.mapNotNull { definition ->
            val items = listEntries
                .filter { it.status.equals(definition.status, ignoreCase = true) }
                .mapNotNull { it.toLibraryItem(definition.key) }
                .distinctBy { "${it.type}|${it.id}" }
                .sortedByDescending(LibraryItem::savedAtEpochMs)
            items.takeIf(List<LibraryItem>::isNotEmpty)?.let {
                LibrarySection(type = definition.key, displayTitle = definition.title, items = it)
            }
        }
        librarySections = sections
        libraryItems = sections.flatMap(LibrarySection::items).sortedByDescending(LibraryItem::savedAtEpochMs)
    }

    fun libraryItem(contentId: String): LibraryItem? {
        val entry = entryFor(contentId) ?: return null
        val key = weTrakrStatusDefinitionFor(entry.status)?.key ?: return null
        return entry.toLibraryItem(key)
    }

    val watchedItems: List<WatchedItem> = buildWatchedItems()

    val watchedKeys: Set<String> = buildWatchedKeys()

    val showIdSiblings: Map<String, Set<String>> = buildMap {
        showMedia.values.forEach { media ->
            val aliases = media.ids.aliases().toSet()
            if (aliases.size > 1) aliases.forEach { alias -> put(alias, aliases) }
        }
    }

    val continueWatching: List<WatchProgressEntry> = buildContinueWatching()

    fun progressEntries(localPlayback: List<WatchProgressEntry>): List<WatchProgressEntry> {
        val local = localPlayback.filterNot { it.isEffectivelyCompleted }
        val localContentIds = local.mapTo(mutableSetOf()) { it.parentMetaId.lowercase() }
        return (local + continueWatching.filter { candidate ->
            candidate.source == WETRAKR_SOURCE_PROGRESS || candidate.parentMetaId.lowercase() !in localContentIds
        }).sortedByDescending(WatchProgressEntry::lastUpdatedEpochMs)
    }

    fun playbackFor(entry: WatchProgressEntry): WeTrakrPlayback? = snapshot.playback.firstOrNull { playback ->
        val contentId = playback.media.ids.contentId() ?: return@firstOrNull false
        normalizeWeTrakrContentId(contentId) == normalizeWeTrakrContentId(entry.parentMetaId) &&
            (playback.media.isMovie || (playback.season == entry.seasonNumber && playback.number == entry.episodeNumber))
    }

    private fun showContentId(episode: WeTrakrWatchedEpisode): String? =
        episode.showId?.let(showMedia::get)?.ids?.contentId()
            ?: episode.showIds.contentId()

    private fun showFor(episode: WeTrakrWatchedEpisode): WeTrakrMedia? =
        episode.showId?.let(showMedia::get)
            ?: episode.showIds.aliases().firstNotNullOfOrNull { showMediaByAlias[it.lowercase()] }

    private fun buildWatchedItems(): List<WatchedItem> {
        val movies = listEntries
            .filter { it.media.isMovie && it.status.equals(WeTrakrStatus.WATCHED, ignoreCase = true) }
            .mapNotNull { entry ->
                val contentId = entry.media.ids.contentId() ?: return@mapNotNull null
                WatchedItem(
                    id = contentId,
                    type = "movie",
                    name = entry.media.displayTitle(),
                    poster = entry.media.posterUrl(),
                    releaseInfo = entry.media.year?.toString(),
                    markedAtEpochMs = entry.watchedAtEpochMs.takeIf { it > 0L } ?: entry.addedAtEpochMs,
                    trackingProviderId = TrackingProviderId.WETRAKR.storageId,
                    trackingProviderItemId = entry.media.providerItemId(),
                    trackingSourceUrl = WeTrakrConfig.WEBSITE_URL,
                )
            }
        val episodes = snapshot.watchedEpisodes.mapNotNull { episode ->
            val contentId = showContentId(episode) ?: return@mapNotNull null
            val show = showFor(episode)
            WatchedItem(
                id = contentId,
                type = "series",
                name = show?.displayTitle() ?: episode.showTitle ?: contentId,
                poster = show?.posterUrl(),
                releaseInfo = show?.year?.toString(),
                season = episode.season,
                episode = episode.number,
                videoId = buildPlaybackVideoId(contentId, episode.season, episode.number),
                markedAtEpochMs = episode.watchedAtEpochMs,
                trackingProviderId = TrackingProviderId.WETRAKR.storageId,
                trackingProviderItemId = show?.providerItemId() ?: episode.showId?.let { "wetrakr:show:$it" },
                trackingSourceUrl = WeTrakrConfig.WEBSITE_URL,
            )
        }
        return (movies + episodes).sortedByDescending(WatchedItem::markedAtEpochMs)
    }

    private fun buildWatchedKeys(): Set<String> {
        val keys = linkedSetOf<String>()
        listEntries
            .filter { it.media.isMovie && it.status.equals(WeTrakrStatus.WATCHED, ignoreCase = true) }
            .forEach { entry -> entry.media.ids.aliases().forEach { alias -> keys += watchedItemKeys("movie", alias) } }
        listEntries
            .filter { !it.media.isMovie && it.status.equals(WeTrakrStatus.WATCHED, ignoreCase = true) }
            .forEach { entry -> entry.media.ids.aliases().forEach { alias -> keys += watchedItemKeys("series", alias) } }
        snapshot.watchedEpisodes.forEach { episode ->
            val aliases = (showFor(episode)?.ids?.aliases().orEmpty() + episode.showIds.aliases()).distinct()
            aliases.forEach { alias -> keys += watchedItemKeys("series", alias, episode.season, episode.number) }
        }
        return keys
    }

    private fun buildContinueWatching(): List<WatchProgressEntry> {
        val playback = snapshot.playback.mapNotNull { it.toProgressEntry() }
        val playingShows = playback.filter { it.contentType == "series" }
            .mapTo(mutableSetOf()) { normalizeWeTrakrContentId(it.parentMetaId) }
        val seeds = listEntries
            .filter { entry ->
                !entry.media.isMovie && (
                    entry.status.equals(WeTrakrStatus.WATCHING, ignoreCase = true) ||
                        entry.status.equals(WeTrakrStatus.WAITING, ignoreCase = true)
                    )
            }
            .mapNotNull { entry ->
                val contentId = entry.media.ids.contentId() ?: return@mapNotNull null
                val normalized = normalizeWeTrakrContentId(contentId)
                if (normalized in playingShows) return@mapNotNull null
                val episodes = entry.media.ids.aliases()
                    .flatMap { alias -> episodesByShow[normalizeWeTrakrContentId(alias)].orEmpty() }
                    .distinctBy { it.season to it.number }
                val latest = (episodes.filter { it.season > 0 }.ifEmpty { episodes })
                    .maxWithOrNull(
                        compareBy<WeTrakrWatchedEpisode> { it.watchedAtEpochMs }
                            .thenBy { it.season }
                            .thenBy { it.number },
                    ) ?: return@mapNotNull null
                if (snapshot.dismissed[weTrakrShowKey(contentId)] == latest.watchedAtEpochMs) return@mapNotNull null
                val runtimeMs = (entry.media.runtimeMinutes ?: 0).toLong() * 60_000L
                WatchProgressEntry(
                    contentType = "series",
                    parentMetaId = contentId,
                    parentMetaType = "series",
                    videoId = buildPlaybackVideoId(contentId, latest.season, latest.number),
                    title = entry.media.displayTitle(),
                    poster = entry.media.posterUrl(),
                    background = entry.media.backdropUrl(),
                    seasonNumber = latest.season,
                    episodeNumber = latest.number,
                    lastPositionMs = runtimeMs,
                    durationMs = runtimeMs,
                    lastUpdatedEpochMs = latest.watchedAtEpochMs,
                    isCompleted = true,
                    progressPercent = 100f,
                    source = WETRAKR_SOURCE_PROGRESS,
                    trackingProviderId = TrackingProviderId.WETRAKR.storageId,
                    trackingProviderItemId = entry.media.providerItemId(),
                    trackingSourceUrl = WeTrakrConfig.WEBSITE_URL,
                )
            }
        return (playback + seeds).sortedByDescending(WatchProgressEntry::lastUpdatedEpochMs)
    }

    private fun WeTrakrPlayback.toProgressEntry(): WatchProgressEntry? {
        if (progressPercent <= 0.0 || progressPercent >= WETRAKR_MAX_PLAYBACK_PERCENT) return null
        val contentId = media.ids.contentId() ?: return null
        val durationMs = runtimeSeconds?.takeIf { it > 0L }?.times(1_000L)
            ?: (media.runtimeMinutes ?: 0).toLong() * 60_000L
        val positionMs = (durationMs * (progressPercent / 100.0)).toLong()
        val isEpisode = type.equals(WeTrakrMediaType.EPISODE, ignoreCase = true)
        if (isEpisode && (season == null || number == null)) return null
        return WatchProgressEntry(
            contentType = if (isEpisode) "series" else "movie",
            parentMetaId = contentId,
            parentMetaType = if (isEpisode) "series" else "movie",
            videoId = if (isEpisode) buildPlaybackVideoId(contentId, season, number) else contentId,
            title = media.displayTitle(),
            poster = media.posterUrl(),
            background = media.backdropUrl(),
            seasonNumber = if (isEpisode) season else null,
            episodeNumber = if (isEpisode) number else null,
            episodeTitle = if (isEpisode) episodeTitle else null,
            lastPositionMs = positionMs,
            durationMs = durationMs,
            lastUpdatedEpochMs = trackedAtEpochMs,
            isCompleted = false,
            progressPercent = progressPercent.toFloat(),
            source = WETRAKR_SOURCE_PLAYBACK,
            trackingProviderId = TrackingProviderId.WETRAKR.storageId,
            trackingProviderItemId = media.providerItemId(),
            trackingSourceUrl = WeTrakrConfig.WEBSITE_URL,
        )
    }

    private fun WeTrakrListEntry.toLibraryItem(listKey: String): LibraryItem? {
        val contentId = media.ids.contentId() ?: return null
        return LibraryItem(
            id = contentId,
            type = media.contentType,
            name = media.displayTitle(),
            poster = media.posterUrl(),
            banner = media.backdropUrl(),
            releaseInfo = media.year?.toString(),
            genres = media.genres,
            posterShape = PosterShape.Poster,
            listKeys = setOf(listKey),
            imdbId = media.ids.imdb,
            tmdbId = media.ids.tmdb?.toInt(),
            rawPosterUrl = media.posterUrl(),
            trackingProviderId = TrackingProviderId.WETRAKR.storageId,
            trackingProviderItemId = media.providerItemId(),
            trackingSourceUrl = WeTrakrConfig.WEBSITE_URL,
            savedAtEpochMs = addedAtEpochMs.takeIf { it > 0L } ?: watchedAtEpochMs,
        )
    }

    companion object {
        val Empty = WeTrakrProjection(WeTrakrSyncSnapshot(userId = 0L))
    }
}
