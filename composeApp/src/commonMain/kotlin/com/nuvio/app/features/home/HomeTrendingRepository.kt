package com.nuvio.app.features.home

import com.nuvio.app.features.addons.httpGetText
import com.nuvio.app.features.sports.compat.LiveTvClock
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

data class HomeTrendingState(
    /** Titles not out yet, soonest first. */
    val comingSoon: List<MetaPreview> = emptyList(),
    val topMovies: List<MetaPreview> = emptyList(),
    val topShows: List<MetaPreview> = emptyList(),
    val isLoading: Boolean = false,
)

/**
 * The trending rows on Home, all from TMDB: films about to reach digital, and today's top ten movies and shows.
 * Everything here can be watched at home. Refreshed a few times a day.
 */
object HomeTrendingRepository {
    private const val MAX_AGE_MS = 3L * 60 * 60 * 1000
    private val TALK_AND_NEWS = setOf(10767, 10763)

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(HomeTrendingState())
    val state: StateFlow<HomeTrendingState> = _state.asStateFlow()
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

    private suspend fun fetchAll(): HomeTrendingState = coroutineScope {
        val soon = async { comingToDigital() }
        val movies = async { trendingAvailable("movie", "day", limit = 10) }
        val shows = async { trendingAvailable("tv", "day", limit = 10) }
        HomeTrendingState(
            comingSoon = soon.await(),
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

    /**
     * Movies whose digital release (streaming, rent or buy) is still to come, soonest first: what the
     * viewer can look forward to watching at home, not what is only in cinemas.
     */
    private suspend fun comingToDigital(): List<MetaPreview> {
        val region = DigitalAvailability.region()
        val today = CurrentDateProvider.todayIsoDate()
        val picks = results(
            "discover/movie",
            mapOf(
                "region" to region,
                "with_release_type" to "4",
                "release_date.gte" to tomorrow(today),
                "release_date.lte" to today.plusDays(90),
                "sort_by" to "popularity.desc",
                "include_adult" to "false",
            ),
        ).filter { it.str("backdrop_path") != null && it.str("overview") != null }.take(18)
        return coroutineScope {
            picks.map { o ->
                async {
                    val id = (o["id"] as? JsonPrimitive)?.intOrNull ?: return@async null
                    val date = DigitalAvailability.digitalReleaseDate(id) ?: return@async null
                    // Only dates still ahead: a movie whose digital release has arrived belongs on the available rows.
                    if (date <= today) return@async null
                    o.toPreview(id = "tmdb:$id", tv = false)?.copy(rawReleaseDate = date)
                }
            }.awaitAll().filterNotNull().sortedBy { it.rawReleaseDate }.take(14)
        }
    }

    /** Trending titles that can be watched at home today, in trending order, with the ids the app opens them by. */
    private suspend fun trendingAvailable(mediaType: String, window: String, limit: Int): List<MetaPreview> {
        val picks = results("trending/$mediaType/$window", emptyMap())
            .filter { it.str("poster_path") != null && (mediaType != "all" || it.str("media_type") in setOf("movie", "tv")) }
            // Late-night and news shows chart every day and say nothing about what is worth watching.
            .filterNot { o -> (o["genre_ids"] as? JsonArray).orEmpty().any { (it as? JsonPrimitive)?.intOrNull in TALK_AND_NEWS } }
            .take(limit * 3)
        return coroutineScope {
            picks.map { o ->
                async {
                    val tmdbId = (o["id"] as? JsonPrimitive)?.intOrNull ?: return@async null
                    val tv = (o.str("media_type") ?: if (mediaType == "tv") "tv" else "movie") == "tv"
                    val available = DigitalAvailability.check(if (tv) "tv" else "movie", tmdbId) ?: return@async null
                    o.toPreview(available.imdbId, tv)
                }
            }.awaitAll().filterNotNull().take(limit)
        }
    }

    private fun tomorrow(today: String): String = today.plusDays(1)

    /** ISO date arithmetic, enough for "tomorrow" and "ninety days out". */
    private fun String.plusDays(days: Int): String {
        val (y, m, d) = split("-").map { it.toInt() }
        var year = y; var month = m; var day = d + days
        fun dim(yr: Int, mo: Int) = when (mo) {
            1, 3, 5, 7, 8, 10, 12 -> 31
            4, 6, 9, 11 -> 30
            else -> if (yr % 4 == 0 && (yr % 100 != 0 || yr % 400 == 0)) 29 else 28
        }
        while (day > dim(year, month)) { day -= dim(year, month); month++; if (month > 12) { month = 1; year++ } }
        return "${year.toString().padStart(4, '0')}-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
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
