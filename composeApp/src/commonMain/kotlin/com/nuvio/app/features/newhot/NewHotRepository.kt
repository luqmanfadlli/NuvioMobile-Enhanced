package com.nuvio.app.features.newhot

import com.nuvio.app.features.addons.httpGetText
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.sports.compat.LiveTvClock
import com.nuvio.app.features.tmdb.TmdbService
import com.nuvio.app.features.tmdb.TmdbSettingsRepository
import com.nuvio.app.features.tmdb.buildTmdbUrl
import com.nuvio.app.features.watchprogress.CurrentDateProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

data class NewHotState(
    /** Titles not out yet, soonest first. */
    val comingSoon: List<MetaPreview> = emptyList(),
    /** What people are watching this week, across movies and shows. */
    val everyoneWatching: List<MetaPreview> = emptyList(),
    val topMovies: List<MetaPreview> = emptyList(),
    val topShows: List<MetaPreview> = emptyList(),
    val isLoading: Boolean = false,
)

/**
 * The data behind the New & Hot tab, all from TMDB: what is coming out, what everyone is watching this week,
 * and today's top ten movies and shows. Refreshed a few times a day.
 */
object NewHotRepository {
    private const val MAX_AGE_MS = 3L * 60 * 60 * 1000

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(NewHotState())
    val state: StateFlow<NewHotState> = _state.asStateFlow()
    private var loadedAtMs = 0L
    private var job: Job? = null

    fun ensureLoaded() {
        if (job?.isActive == true) return
        val s = _state.value
        val hasData = s.comingSoon.isNotEmpty() || s.topMovies.isNotEmpty()
        if (hasData && LiveTvClock.nowMs() - loadedAtMs < MAX_AGE_MS) return
        _state.value = s.copy(isLoading = !hasData)
        job = scope.launch {
            val fresh = runCatching { fetchAll() }.getOrNull()
            if (fresh != null && (fresh.comingSoon.isNotEmpty() || fresh.topMovies.isNotEmpty())) {
                _state.value = fresh
                loadedAtMs = LiveTvClock.nowMs()
            } else {
                _state.value = _state.value.copy(isLoading = false)
            }
        }
    }

    private suspend fun fetchAll(): NewHotState = coroutineScope {
        val today = CurrentDateProvider.todayIsoDate()
        val soonMovies = async { discoverUpcoming("movie", "primary_release_date.gte", today) }
        val soonShows = async { discoverUpcoming("tv", "first_air_date.gte", today) }
        val watching = async { trending("all", "week", limit = 15) }
        val movies = async { trending("movie", "day", limit = 10) }
        val shows = async { trending("tv", "day", limit = 10) }
        val coming = (soonMovies.await() + soonShows.await())
            .sortedByDescending { it.popularity ?: 0.0 }
            .take(24)
            .sortedBy { it.rawReleaseDate ?: "9999" }
        NewHotState(
            comingSoon = coming,
            everyoneWatching = watching.await(),
            topMovies = movies.await(),
            topShows = shows.await(),
            isLoading = false,
        )
    }

    private suspend fun results(endpoint: String, query: Map<String, String>): List<JsonObject> {
        val key = TmdbSettingsRepository.effectiveApiKey()
        if (key.isBlank()) return emptyList()
        val q = mapOf("language" to TmdbSettingsRepository.snapshot().language) + query
        val text = httpGetText(buildTmdbUrl(endpoint = endpoint, apiKey = key, query = q))
        val arr = json.parseToJsonElement(text).jsonObject["results"] as? JsonArray ?: return emptyList()
        return arr.mapNotNull { it as? JsonObject }
    }

    /** Titles that are not out yet and have artwork and a description to show. */
    private suspend fun discoverUpcoming(mediaType: String, dateParam: String, today: String): List<MetaPreview> =
        results(
            "discover/$mediaType",
            mapOf("sort_by" to "popularity.desc", dateParam to today, "include_adult" to "false", "vote_count.gte" to "0"),
        ).mapNotNull { o ->
            if (o.str("backdrop_path") == null || o.str("overview") == null) return@mapNotNull null
            val id = (o["id"] as? JsonPrimitive)?.intOrNull ?: return@mapNotNull null
            o.toPreview(id = "tmdb:$id", tv = mediaType == "tv")
        }.take(16)

    /** Trending titles, resolved to the IMDb ids the rest of the app opens them by. */
    private suspend fun trending(mediaType: String, window: String, limit: Int): List<MetaPreview> {
        val picks = results("trending/$mediaType/$window", emptyMap())
            .filter { it.str("poster_path") != null && (mediaType != "all" || it.str("media_type") in setOf("movie", "tv")) }
            .take(limit + 6)
        return coroutineScope {
            picks.map { o ->
                async {
                    val tmdbId = (o["id"] as? JsonPrimitive)?.intOrNull ?: return@async null
                    val tv = (o.str("media_type") ?: if (mediaType == "tv") "tv" else "movie") == "tv"
                    val imdb = runCatching { TmdbService.tmdbToImdb(tmdbId, if (tv) "tv" else "movie") }.getOrNull() ?: return@async null
                    o.toPreview(imdb, tv)
                }
            }.awaitAll().filterNotNull().take(limit)
        }
    }

    private fun JsonObject.toPreview(id: String, tv: Boolean): MetaPreview? {
        val name = str("title") ?: str("name") ?: return null
        val poster = str("poster_path")?.let { "https://image.tmdb.org/t/p/w500$it" }
        val date = str("release_date") ?: str("first_air_date")
        return MetaPreview(
            id = id,
            type = if (tv) "series" else "movie",
            name = name,
            poster = poster,
            rawPosterUrl = poster,
            banner = str("backdrop_path")?.let { "https://image.tmdb.org/t/p/w780$it" },
            description = str("overview"),
            releaseInfo = date?.take(4),
            rawReleaseDate = date,
            popularity = (this["popularity"] as? JsonPrimitive)?.doubleOrNull,
            voteCount = (this["vote_count"] as? JsonPrimitive)?.intOrNull,
        )
    }

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
}
