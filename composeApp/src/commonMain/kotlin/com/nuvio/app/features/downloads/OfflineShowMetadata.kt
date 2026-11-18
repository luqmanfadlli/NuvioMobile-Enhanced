package com.nuvio.app.features.downloads

import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.details.MetaPerson
import com.nuvio.app.features.details.MetaVideo
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val MAX_STORED_VIDEOS = 2_000
private const val MAX_STORED_CAST = 50
private const val MAX_STORED_DESCRIPTION_LENGTH = 4_000
private const val MAX_STORED_TITLE_LENGTH = 500

private fun String?.boundedDescription(): String? = this?.take(MAX_STORED_DESCRIPTION_LENGTH)
private fun String.boundedTitle(): String = take(MAX_STORED_TITLE_LENGTH)

@Serializable
data class StoredDownloadedVideoMeta(
    val id: String,
    val title: String,
    val released: String? = null,
    val thumbnail: String? = null,
    val seasonPoster: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val overview: String? = null,
    val runtime: Int? = null,
    val tmdbRating: Double? = null,
    val imdbRating: Double? = null,
    val rating: Double? = null,
)

@Serializable
data class StoredDownloadedCastMeta(
    val name: String,
    val role: String? = null,
    val photo: String? = null,
    val tmdbId: Int? = null,
)

@Serializable
data class StoredOfflineMeta(
    val showId: String,
    val title: String,
    val type: String = "series",
    val poster: String? = null,
    val background: String? = null,
    val logo: String? = null,
    val description: String? = null,
    val releaseInfo: String? = null,
    val status: String? = null,
    val imdbRating: String? = null,
    val genres: List<String> = emptyList(),
    val seasonPosters: Map<Int, String> = emptyMap(),
    val videos: List<StoredDownloadedVideoMeta> = emptyList(),
    val cast: List<StoredDownloadedCastMeta> = emptyList(),
)

/**
 * Offline copies of title details, kept only for titles that have downloads so their
 * details page still works without a connection.
 */
internal class OfflineMetadataStore {
    private val byShowId = mutableMapOf<String, StoredOfflineMeta>()

    operator fun get(showId: String): StoredOfflineMeta? = byShowId[showId]

    fun snapshot(): Map<String, StoredOfflineMeta> = byShowId.toMap()

    fun replaceAll(stored: Map<String, StoredOfflineMeta>) {
        byShowId.clear()
        byShowId.putAll(stored)
    }

    fun clear() = byShowId.clear()

    fun remove(showId: String) {
        byShowId.remove(showId)
    }

    /** Merges fresh details into the stored copy, keeping stored values the new copy lacks. */
    fun merge(meta: MetaDetails): Boolean {
        val existing = byShowId[meta.id]
        val updated = StoredOfflineMeta(
            showId = meta.id,
            title = meta.name.boundedTitle(),
            type = meta.type,
            poster = meta.poster ?: existing?.poster,
            background = meta.background ?: existing?.background,
            logo = meta.logo ?: existing?.logo,
            description = (meta.description ?: existing?.description).boundedDescription(),
            releaseInfo = meta.releaseInfo ?: existing?.releaseInfo,
            status = meta.status ?: existing?.status,
            imdbRating = meta.imdbRating ?: existing?.imdbRating,
            genres = meta.genres.ifEmpty { existing?.genres.orEmpty() },
            seasonPosters = existing?.seasonPosters.orEmpty() + meta.seasonPosters,
            videos = (meta.videos.map { it.toStored() }.ifEmpty { existing?.videos.orEmpty() }).take(MAX_STORED_VIDEOS),
            cast = (meta.cast.map { it.toStored() }.ifEmpty { existing?.cast.orEmpty() }).take(MAX_STORED_CAST),
        )
        if (updated == existing) return false
        byShowId[meta.id] = updated
        return true
    }

    /** Records what is known about a title when no full details are cached, e.g. from a deep link. */
    fun recordBasic(
        showId: String,
        type: String,
        title: String,
        logo: String?,
        poster: String?,
        background: String?,
        episode: StoredDownloadedVideoMeta?,
    ) {
        val existing = byShowId[showId] ?: StoredOfflineMeta(showId = showId, title = title.boundedTitle(), type = type)
        val isNewEpisode = episode != null &&
            existing.videos.none { it.season == episode.season && it.episode == episode.episode }
        byShowId[showId] = existing.copy(
            logo = existing.logo ?: logo,
            poster = existing.poster ?: poster,
            background = existing.background ?: background,
            videos = (if (isNewEpisode) existing.videos + listOfNotNull(episode?.copy(
                title = episode.title.boundedTitle(),
                overview = episode.overview.boundedDescription(),
            )) else existing.videos).take(MAX_STORED_VIDEOS),
        )
    }
}

/** Persists offline title details independently from frequently updated download progress. */
internal object OfflineMetadataRepository {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val store = OfflineMetadataStore()
    private var downloads: List<DownloadItem> = emptyList()
    private var loaded = false

    fun ensureLoaded() {
        if (loaded) return
        loaded = true
        store.replaceAll(runCatching {
            DownloadsStorage.loadOfflineMetadataPayload()?.let {
                json.decodeFromString<Map<String, StoredOfflineMeta>>(it)
            }.orEmpty()
        }.getOrDefault(emptyMap()))
    }

    fun reload() {
        loaded = false
        ensureLoaded()
    }

    fun clear() {
        store.clear()
        downloads = emptyList()
        loaded = true
    }

    operator fun get(showId: String): StoredOfflineMeta? {
        ensureLoaded()
        return store[showId]
    }

    fun getSavedMetaDetails(showId: String): MetaDetails? {
        val showItems = downloads.filter { it.parentMetaId == showId }
        return get(showId)?.toMetaDetails(showItems)
            ?: showItems.takeIf { it.isNotEmpty() }?.toOfflineMetaDetails(showId)
    }

    fun setDownloads(items: List<DownloadItem>) { downloads = items }

    fun onMetaLoaded(meta: MetaDetails) {
        if (downloads.none { it.parentMetaId == meta.id } && get(meta.id) == null) return
        merge(meta)
    }

    fun merge(meta: MetaDetails): Boolean {
        ensureLoaded()
        if (!store.merge(meta)) return false
        persist()
        return true
    }

    fun recordBasic(
        showId: String,
        type: String,
        title: String,
        logo: String?,
        poster: String?,
        background: String?,
        episode: StoredDownloadedVideoMeta?,
    ) {
        ensureLoaded()
        val before = store.snapshot()
        store.recordBasic(showId, type, title, logo, poster, background, episode)
        if (store.snapshot() != before) persist()
    }

    fun remove(showId: String) {
        ensureLoaded()
        val before = store[showId] ?: return
        store.remove(showId)
        persist()
    }

    fun migrateLegacy(metadata: Map<String, StoredOfflineMeta>) {
        ensureLoaded()
        if (metadata.isEmpty()) return
        val merged = store.snapshot() + metadata.filterKeys { it !in store.snapshot() }
        if (merged != store.snapshot()) {
            store.replaceAll(merged)
            persist()
        }
    }

    private fun persist() = DownloadsStorage.saveOfflineMetadataPayload(json.encodeToString(store.snapshot()))
}

/** Minimal episode details for a download whose show metadata doesn't list it. */
fun DownloadItem.toMetaVideo(): MetaVideo =
    MetaVideo(
        id = videoId,
        title = displayTitle(),
        available = true,
        thumbnail = episodeThumbnail ?: background ?: poster,
        season = seasonNumber,
        episode = episodeNumber,
    )

fun MetaVideo.toStored(): StoredDownloadedVideoMeta =
    StoredDownloadedVideoMeta(
        id = id,
        title = title.boundedTitle(),
        released = released,
        thumbnail = thumbnail,
        seasonPoster = seasonPoster,
        season = season,
        episode = episode,
        overview = overview.boundedDescription(),
        runtime = runtime,
        tmdbRating = tmdbRating,
        imdbRating = imdbRating,
        rating = rating,
    )

fun StoredDownloadedVideoMeta.toMetaVideo(): MetaVideo =
    MetaVideo(
        id = id,
        title = title,
        released = released,
        available = true,
        thumbnail = thumbnail,
        seasonPoster = seasonPoster,
        season = season,
        episode = episode,
        overview = overview,
        runtime = runtime,
        tmdbRating = tmdbRating,
        imdbRating = imdbRating,
        rating = rating ?: tmdbRating ?: imdbRating,
    )

fun MetaPerson.toStored(): StoredDownloadedCastMeta =
    StoredDownloadedCastMeta(
        name = name.boundedTitle(),
        role = role,
        photo = photo,
        tmdbId = tmdbId,
    )

fun StoredDownloadedCastMeta.toMetaPerson(): MetaPerson =
    MetaPerson(
        name = name,
        role = role,
        photo = photo,
        tmdbId = tmdbId,
    )

/** Builds offline details from the stored show metadata plus the show's downloaded items. */
fun StoredOfflineMeta.toMetaDetails(showDownloads: List<DownloadItem>): MetaDetails {
    val downloadedEpisodes = showDownloads
        .filter { it.isEpisode }
        .sortedForSeriesDownloads()

    val storedVideos = videos.map { it.toMetaVideo() }
    val existingKeys = storedVideos.mapNotNull { v ->
        if (v.season != null && v.episode != null) "${v.season}:${v.episode}" else v.id.takeIf { it.isNotBlank() }
    }.toSet()

    val missingDownloaded = downloadedEpisodes
        .filter { ep ->
            val key = if (ep.seasonNumber != null && ep.episodeNumber != null) "${ep.seasonNumber}:${ep.episodeNumber}" else ep.videoId
            key !in existingKeys
        }
        .map { it.toMetaVideo() }

    val allVideos = if (storedVideos.isNotEmpty()) {
        (storedVideos + missingDownloaded).sortedWith(
            compareBy<MetaVideo> { it.season ?: Int.MAX_VALUE }
                .thenBy { it.episode ?: Int.MAX_VALUE }
                .thenBy { it.id }
        )
    } else {
        downloadedEpisodes.map { it.toMetaVideo() }
    }


    return MetaDetails(
        id = showId,
        type = type,
        name = title,
        poster = poster,
        background = background,
        logo = logo,
        description = description,
        releaseInfo = releaseInfo,
        status = status,
        imdbRating = imdbRating,
        genres = genres,
        seasonPosters = seasonPosters,
        videos = allVideos,
        cast = cast.map { it.toMetaPerson() },
    )
}

/** Basic details for downloads saved without show metadata (e.g. before it was stored). */
fun List<DownloadItem>.toOfflineMetaDetails(showId: String): MetaDetails {
    val showEpisodes = this
    val first = showEpisodes.firstOrNull()
    return MetaDetails(
        id = showId,
        type = first?.parentMetaType ?: "series",
        name = first?.title.orEmpty(),
        poster = showEpisodes.firstNotNullOfOrNull { it.poster },
        background = showEpisodes.firstNotNullOfOrNull { it.background },
        logo = showEpisodes.firstNotNullOfOrNull { it.logo },
        videos = showEpisodes.sortedForSeriesDownloads().map { it.toMetaVideo() },
    )
}
