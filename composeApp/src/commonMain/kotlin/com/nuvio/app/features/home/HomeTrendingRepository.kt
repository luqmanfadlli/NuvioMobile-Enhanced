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
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

/** One curated row: a title and what is in it. */
data class CuratedRow(val key: String, val title: String, val items: List<MetaPreview>)

data class HomeTrendingState(
    /** Films whose digital release is still ahead, soonest first. */
    val comingSoon: List<MetaPreview> = emptyList(),
    val topMovies: List<MetaPreview> = emptyList(),
    val topShows: List<MetaPreview> = emptyList(),
    /** The rest of Home's curated lists, in the order they are shown. */
    val rows: List<CuratedRow> = emptyList(),
    /** True once the first load has finished, whether or not it found anything. */
    val loadedOnce: Boolean = false,
)

/**
 * Home's own lists, the same for every user and built from TMDB rather than from whichever catalog addons a
 * person has installed. Everything in them can be watched at home today (see [DigitalAvailability]); the one
 * exception, Coming to Digital, is by definition what is about to be.
 *
 * The order follows how Netflix paces a page: the two Top 10s first, then new and trending titles, with movies and
 * series taking turns so neither runs on, then themed rows ("Feel-Good Comedies", "Binge-Worthy Series") that
 * each have a mood rather than a catalog name, and the broad popular and top-rated lists last. Personal
 * "Because you watched" rows are slotted in between by the Home screen. A title appears in only one row.
 */
object HomeTrendingRepository {
    private const val MAX_AGE_MS = 3L * 60 * 60 * 1000
    private val TALK_AND_NEWS = setOf(10767, 10763)

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val permits = Semaphore(10)
    private val _state = MutableStateFlow(HomeTrendingState())
    val state: StateFlow<HomeTrendingState> = _state.asStateFlow()
    private var loadedAtMs = 0L
    private var job: Job? = null

    fun ensureLoaded() {
        if (job?.isActive == true) return
        val s = _state.value
        if (s.loadedOnce && s.topMovies.isNotEmpty() && LiveTvClock.nowMs() - loadedAtMs < MAX_AGE_MS) return
        job = scope.launch {
            runCatching { load() }
            _state.value = _state.value.copy(loadedOnce = true)
            runCatching { DigitalAvailability.persist() }
        }
    }

    private suspend fun load() = coroutineScope {
        val today = CurrentDateProvider.todayIsoDate()
        val region = DigitalAvailability.region()

        // First the rows at the top of the page, so they appear before the rest has finished.
        val movies = async { available("trending/movie/day", emptyMap(), "movie", 10) }
        val shows = async { available("trending/tv/day", emptyMap(), "tv", 10) }
        val soon = async { comingToDigital(today, region) }
        val topMovies = movies.await()
        val topShows = shows.await()
        _state.value = _state.value.copy(topMovies = topMovies, topShows = topShows)
        _state.value = _state.value.copy(comingSoon = soon.await())

        val digitalNow = mapOf(
            "region" to region, "with_release_type" to "4",
            "release_date.gte" to today.plusDays(-45), "release_date.lte" to today, "sort_by" to "popularity.desc",
        )
        val newShows = mapOf(
            "first_air_date.gte" to today.plusDays(-120), "first_air_date.lte" to today,
            "sort_by" to "popularity.desc", "without_genres" to "10767,10763",
        )
        fun movies(genres: String, extra: Map<String, String> = emptyMap()) =
            mapOf("with_genres" to genres, "sort_by" to "popularity.desc", "vote_count.gte" to "300", "include_adult" to "false") + extra
        fun series(extra: Map<String, String>) =
            mapOf("sort_by" to "popularity.desc", "vote_count.gte" to "300", "without_genres" to "10767,10763") + extra
        val wanted = 14
        val fill = wanted + 6
        class Spec(val key: String, val title: String, val fetch: suspend () -> List<MetaPreview>)
        fun m(key: String, title: String, endpoint: String, q: Map<String, String> = emptyMap()) =
            Spec(key, title) { available(endpoint, q, "movie", fill) }
        fun t(key: String, title: String, endpoint: String, q: Map<String, String> = emptyMap()) =
            Spec(key, title) { available(endpoint, q, "tv", fill) }
        // Movies and series take turns, personal rows and Coming to Digital are slotted in by the Home screen.
        val specs = listOf(
            m("new-digital", "New on Digital", "discover/movie", digitalNow),
            t("trending-series", "Trending Series", "trending/tv/week"),
            m("trending-movies", "Trending Movies", "trending/movie/week"),
            t("new-series", "New Series", "discover/tv", newShows),
            m("comedies", "Feel-Good Comedies", "discover/movie", movies("35", mapOf("without_genres" to "27,53,80,10752"))),
            t("binge", "Binge-Worthy Series", "discover/tv", series(mapOf("vote_average.gte" to "8", "vote_count.gte" to "1500"))),
            m("thrillers", "Edge-of-Your-Seat Thrillers", "discover/movie", movies("53")),
            t("crime", "Crime Dramas", "discover/tv", series(mapOf("with_genres" to "80"))),
            m("scifi", "Sci-Fi & Fantasy", "discover/movie", movies("878|14")),
            t("anime", "Anime", "discover/tv", series(mapOf("with_genres" to "16", "with_original_language" to "ja"))),
            m("family", "Family Movie Night", "discover/movie", movies("10751")),
            t("kdramas", "Korean Dramas", "discover/tv", series(mapOf("with_genres" to "18", "with_original_language" to "ko"))),
            m("acclaimed", "Critically Acclaimed Movies", "movie/top_rated"),
            m("documentaries", "Documentaries", "discover/movie", movies("99")),
            m("horror", "Scary Good Horror", "discover/movie", movies("27")),
            t("popular-series", "Popular Series", "tv/popular"),
            m("popular-movies", "Popular Movies", "movie/popular"),
        )

        // Fetched a few at a time and shown as each batch lands, so the top of the page doesn't wait for the bottom.
        // Each title shows once down the page: a later row skips anything an earlier one (or a Top 10) already has.
        val seen = (topMovies + topShows).mapTo(HashSet()) { it.id }
        val rows = ArrayList<CuratedRow>()
        for (batch in specs.chunked(4)) {
            val fetched = batch.map { spec -> async { spec to runCatching { spec.fetch() }.getOrDefault(emptyList()) } }.awaitAll()
            fetched.forEach { (spec, items) ->
                val fresh = items.filter { seen.add(it.id) }.take(wanted)
                if (fresh.size >= 4) rows += CuratedRow(spec.key, spec.title, fresh)
            }
            _state.value = _state.value.copy(rows = rows.toList())
        }
        loadedAtMs = LiveTvClock.nowMs()
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
     * A TMDB list narrowed to what can be watched at home, in the list's own order, with the ids the app opens
     * titles by. Candidates are checked ten at a time and the checking stops as soon as the row is full.
     */
    private suspend fun available(endpoint: String, query: Map<String, String>, mediaType: String, limit: Int): List<MetaPreview> {
        val candidates = results(endpoint, query)
            .filter { it.str("poster_path") != null }
            // Late-night and news shows chart every day and say nothing about what is worth watching.
            .filterNot { o -> (o["genre_ids"] as? JsonArray).orEmpty().any { (it as? JsonPrimitive)?.intOrNull in TALK_AND_NEWS } }
        val found = ArrayList<MetaPreview>()
        for (window in candidates.chunked(10)) {
            if (found.size >= limit) break
            coroutineScope {
                window.map { o ->
                    async {
                        val tmdbId = (o["id"] as? JsonPrimitive)?.intOrNull ?: return@async null
                        val ok = permits.withPermit { DigitalAvailability.check(mediaType, tmdbId) } ?: return@async null
                        o.toPreview(ok.imdbId, mediaType == "tv")
                    }
                }.awaitAll().filterNotNull()
            }.let { found += it }
        }
        return found.take(limit)
    }

    /**
     * Films whose digital release (streaming, rent or buy) is still to come, soonest first: what the viewer
     * can look forward to watching at home, not what is only in cinemas.
     */
    private suspend fun comingToDigital(today: String, region: String): List<MetaPreview> {
        val picks = results(
            "discover/movie",
            mapOf(
                "region" to region, "with_release_type" to "4",
                "release_date.gte" to today.plusDays(1), "release_date.lte" to today.plusDays(90),
                "sort_by" to "popularity.desc", "include_adult" to "false",
            ),
        ).filter { it.str("backdrop_path") != null && it.str("overview") != null }.take(18)
        return coroutineScope {
            picks.map { o ->
                async {
                    val id = (o["id"] as? JsonPrimitive)?.intOrNull ?: return@async null
                    val date = permits.withPermit { DigitalAvailability.digitalReleaseDate(id) } ?: return@async null
                    // A date that has already arrived belongs on the available rows instead.
                    if (date <= today) return@async null
                    o.toPreview(id = "tmdb:$id", tv = false)?.copy(rawReleaseDate = date)
                }
            }.awaitAll().filterNotNull().sortedBy { it.rawReleaseDate }.take(14)
        }
    }

    /** ISO date arithmetic, enough for "45 days ago" and "ninety days out". */
    private fun String.plusDays(days: Int): String {
        val (y, m, d) = split("-").map { it.toInt() }
        var year = y; var month = m; var day = d + days
        fun dim(yr: Int, mo: Int) = when (mo) {
            1, 3, 5, 7, 8, 10, 12 -> 31
            4, 6, 9, 11 -> 30
            else -> if (yr % 4 == 0 && (yr % 100 != 0 || yr % 400 == 0)) 29 else 28
        }
        while (day > dim(year, month)) { day -= dim(year, month); month++; if (month > 12) { month = 1; year++ } }
        while (day < 1) { month--; if (month < 1) { month = 12; year-- }; day += dim(year, month) }
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
