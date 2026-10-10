package com.nuvio.app.features.anilist

import com.nuvio.app.features.player.skip.SkipIntroApi
import com.nuvio.app.features.tracking.TrackingMediaKind
import com.nuvio.app.features.tracking.TrackingMediaReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

internal fun normalizeAniListTitle(value: String?): String =
    value.orEmpty().lowercase().filter { it.isLetterOrDigit() }

private const val ANILIST_SEQUEL_QUERY =
    "query(\$id:Int){Media(id:\$id,type:ANIME){id relations{edges{relationType(version:2) node{id type format status episodes startDate{year month day}}}}}}"

private data class AniListSeasonTarget(val id: Int, val episodes: Int?, val released: Boolean)

private val ANILIST_SEASON_FORMATS = listOf("TV", "TV_SHORT", "ONA")

internal object AniListMediaResolver {
    private val mutex = Mutex()
    private val cache = mutableMapOf<String, Int>()
    private val kitsuCache = mutableMapOf<Long, Int>()
    private val seasonCache = mutableMapOf<String, AniListSeasonTarget>()

    suspend fun resolve(media: TrackingMediaReference): Int? {
        val base = resolveBase(media) ?: return null
        val season = media.episode?.season
        if (media.kind == TrackingMediaKind.MOVIE || season == null || season <= 1) return base
        return resolveSeason(base, season, media.episode?.number)
    }

    private suspend fun resolveSeason(baseId: Int, season: Int, episodeNumber: Int?): Int? {
        val key = "$baseId:$season"
        val target = mutex.withLock { seasonCache[key] } ?: run {
            var current: AniListSeasonTarget? = null
            var currentId = baseId
            repeat(season - 1) {
                current = try {
                    nextSeason(currentId)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    null
                } ?: return null
                currentId = current!!.id
            }
            val resolved = current ?: return null
            mutex.withLock { seasonCache[key] = resolved }
            resolved
        }
        if (!target.released) return null
        if (episodeNumber != null && target.episodes != null && episodeNumber > target.episodes) return null
        return target.id
    }

    private suspend fun nextSeason(mediaId: Int): AniListSeasonTarget? {
        val data = AniListTracker.api.query(
            ANILIST_SEQUEL_QUERY,
            buildJsonObject { put("id", mediaId) },
            authenticated = false,
        )
        val edges = (((data["Media"] as? JsonObject)?.get("relations") as? JsonObject)?.get("edges") as? JsonArray)
            .orEmpty()
            .mapNotNull { it as? JsonObject }
        val sequels = edges.mapNotNull { edge ->
            if ((edge["relationType"] as? JsonPrimitive)?.contentOrNull != "SEQUEL") return@mapNotNull null
            val node = edge["node"] as? JsonObject ?: return@mapNotNull null
            if ((node["type"] as? JsonPrimitive)?.contentOrNull != "ANIME") return@mapNotNull null
            val format = (node["format"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            if (format !in ANILIST_SEASON_FORMATS) return@mapNotNull null
            val id = (node["id"] as? JsonPrimitive)?.intOrNull ?: return@mapNotNull null
            val status = (node["status"] as? JsonPrimitive)?.contentOrNull
            val start = node["startDate"] as? JsonObject
            fun part(name: String) = (start?.get(name) as? JsonPrimitive)?.intOrNull ?: Int.MAX_VALUE
            val order = part("year").toLong() * 10000 + part("month") * 100L + part("day")
            order to AniListSeasonTarget(
                id = id,
                episodes = (node["episodes"] as? JsonPrimitive)?.intOrNull,
                released = status == "FINISHED" || status == "RELEASING",
            )
        }
        return sequels.minByOrNull { it.first }?.second
    }

    private suspend fun resolveBase(media: TrackingMediaReference): Int? {
        media.ids.anilist?.takeIf { it > 0L }?.let { return it.toInt() }
        media.ids.kitsu?.let { kitsu -> resolveKitsu(kitsu) }?.let { return it }
        val entries = AniListTracker.entries()
        media.ids.mal?.let { mal ->
            entries.firstOrNull { it.idMal?.toLong() == mal }?.let { return it.mediaId }
        }
        media.catalog?.contentId?.let { contentId ->
            entries.firstOrNull { it.contentId == contentId || it.providerItemId == contentId }
                ?.let { return it.mediaId }
        }
        val season = media.episode?.season
        val hasAnimeIds = media.ids.mal != null || media.ids.kitsu != null || media.ids.anidb != null
        if (!hasAnimeIds && season != null && season > 1) return null
        val normalizedTitle = normalizeAniListTitle(media.title)
        if (normalizedTitle.isNotEmpty()) {
            entries.filter { entry ->
                entry.synonyms.any { normalizeAniListTitle(it) == normalizedTitle } ||
                    normalizeAniListTitle(entry.title) == normalizedTitle
            }.let { matches ->
                val byYear = media.year?.let { year -> matches.firstOrNull { it.year == null || kotlin.math.abs(it.year - year) <= 1 } }
                (byYear ?: matches.firstOrNull().takeIf { media.year == null })?.let { return it.mediaId }
            }
        }
        val key = media.stableKey
        mutex.withLock { cache[key] }?.let { return it }
        val resolved = try {
            remote(media, normalizedTitle)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
        if (resolved != null) mutex.withLock { cache[key] = resolved }
        return resolved
    }

    private suspend fun resolveKitsu(kitsuId: Long): Int? {
        mutex.withLock { kitsuCache[kitsuId] }?.let { return it }
        val anilistId = SkipIntroApi.resolveKitsuToAnilist(kitsuId.toString())?.anilist?.takeIf { it > 0 }
            ?: return null
        mutex.withLock { kitsuCache[kitsuId] = anilistId }
        return anilistId
    }

    private suspend fun remote(media: TrackingMediaReference, normalizedTitle: String): Int? {
        media.ids.mal?.let { mal ->
            val data = AniListTracker.api.query(
                ANILIST_MEDIA_BY_MAL_QUERY,
                buildJsonObject { put("mal", mal.toInt()) },
                authenticated = false,
            )
            return ((data["Media"] as? JsonObject)?.get("id") as? JsonPrimitive)?.intOrNull
        }
        val title = media.title?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val data = AniListTracker.api.query(
            ANILIST_SEARCH_QUERY,
            buildJsonObject { put("search", title) },
            authenticated = false,
        )
        val candidates = ((data["Page"] as? JsonObject)?.get("media") as? JsonArray).orEmpty()
            .mapNotNull { it as? JsonObject }
        val matches = candidates.filter { candidate ->
            val titles = candidate["title"] as? JsonObject
            val names = buildList {
                listOf("romaji", "english", "native").forEach { field ->
                    (titles?.get(field) as? JsonPrimitive)?.takeUnless { it is JsonNull }?.contentOrNull?.let(::add)
                }
                (candidate["synonyms"] as? JsonArray)?.forEach { element ->
                    (element as? JsonPrimitive)?.contentOrNull?.let(::add)
                }
            }
            names.any { normalizeAniListTitle(it) == normalizedTitle }
        }
        val year = media.year
        val chosen = if (year != null) {
            matches.firstOrNull { candidate ->
                val candidateYear = (candidate["seasonYear"] as? JsonPrimitive)?.intOrNull
                    ?: ((candidate["startDate"] as? JsonObject)?.get("year") as? JsonPrimitive)?.intOrNull
                candidateYear != null && kotlin.math.abs(candidateYear - year) <= 1
            }
        } else {
            matches.firstOrNull()
        }
        if (media.kind == TrackingMediaKind.MOVIE && chosen != null) {
            val format = (chosen["format"] as? JsonPrimitive)?.contentOrNull
            if (format != null && format != "MOVIE" && format != "SPECIAL" && format != "OVA" && format != "ONA") return null
        }
        return (chosen?.get("id") as? JsonPrimitive)?.intOrNull
    }
}
