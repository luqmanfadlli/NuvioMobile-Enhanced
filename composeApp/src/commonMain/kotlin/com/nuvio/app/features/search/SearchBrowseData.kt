package com.nuvio.app.features.search

import com.nuvio.app.features.addons.httpGetText
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.sports.compat.LiveTvClock
import com.nuvio.app.features.tmdb.TmdbSettingsRepository
import com.nuvio.app.features.tmdb.buildTmdbUrl
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

/** One TMDB list the Search tab can show: its own endpoint and filters. */
internal data class BrowseSpec(
    val key: String,
    val title: String,
    val endpoint: String,
    val query: Map<String, String> = emptyMap(),
    val tv: Boolean = false,
)

/** The four collections on the Search home. Each opens its own curated page (see [COLLECTION_DEFS]); Movie Universes opens the list of franchises. */
internal enum class SearchCollection(
    val title: String,
    /** The collection page it opens, or null for the franchise list. */
    val defId: String?,
    /** The title whose artwork is the card's cover: chosen by hand, so the cover always suits the collection. */
    val coverTitle: String,
    val coverYear: Int,
    val coverIsSeries: Boolean = false,
) {
    Under90("90 Minutes or Less", "under90", "Toy Story", 1995),
    HiddenGems("Hidden Gems", "gems", "Moon", 2009),
    OneNightBinges("One-Night Binges", "binges", "Chernobyl", 2019, coverIsSeries = true),
    MovieUniverses("Movie Universes", null, "Avengers: Endgame", 2019),
}

internal class SearchGenre(val name: String, val movieId: Int?, val tvId: Int?)

internal val SEARCH_GENRES = listOf(
    SearchGenre("Action & Adventure", 28, 10759),
    SearchGenre("Animation", 16, 16),
    SearchGenre("Comedy", 35, 35),
    SearchGenre("Crime", 80, 80),
    SearchGenre("Documentary", 99, 99),
    SearchGenre("Drama", 18, 18),
    SearchGenre("Family", 10751, 10751),
    SearchGenre("Fantasy & Sci-Fi", 878, 10765),
    SearchGenre("History", 36, null),
    SearchGenre("Horror", 27, null),
    SearchGenre("Kids", null, 10762),
    SearchGenre("Music", 10402, null),
    SearchGenre("Mystery", 9648, 9648),
    SearchGenre("Reality", null, 10764),
    SearchGenre("Romance", 10749, null),
    SearchGenre("Thriller", 53, null),
    SearchGenre("War", 10752, 10768),
    SearchGenre("Western", 37, 37),
)

internal val SEARCH_DECADES = listOf(2020, 2010, 2000, 1990, 1980, 1970, 1960, 1950)

/** The chips under "Popular searches": each opens a list rather than running a text search, which these phrases would not match. */
internal val SEARCH_POPULAR = listOf(
    BrowseSpec("pop-crime", "Crime Thrillers", "discover/movie", mapOf("with_genres" to "80,53", "sort_by" to "popularity.desc", "vote_count.gte" to "300")),
    BrowseSpec("pop-feelgood", "Feel-Good", "discover/movie", mapOf("with_genres" to "35|10751", "vote_average.gte" to "6.8", "sort_by" to "popularity.desc", "vote_count.gte" to "500")),
    BrowseSpec("pop-true", "True Stories", "discover/movie", mapOf("with_keywords" to "9672", "sort_by" to "popularity.desc", "vote_count.gte" to "300")),
    BrowseSpec("pop-scifi", "Sci-Fi Mysteries", "discover/movie", mapOf("with_genres" to "878,9648", "sort_by" to "popularity.desc", "vote_count.gte" to "300")),
    BrowseSpec("pop-horror", "Scary Good Horror", "discover/movie", mapOf("with_genres" to "27", "vote_average.gte" to "6.3", "sort_by" to "popularity.desc", "vote_count.gte" to "500")),
    BrowseSpec("pop-family", "Family Movie Night", "discover/movie", mapOf("with_genres" to "10751", "sort_by" to "popularity.desc", "vote_count.gte" to "500")),
)

internal fun genreSpec(genre: SearchGenre, tv: Boolean): BrowseSpec? {
    val id = (if (tv) genre.tvId else genre.movieId) ?: return null
    val extra = if (tv) mapOf("without_genres" to "10767,10763") else emptyMap()
    return BrowseSpec(
        key = "genre-${if (tv) "tv" else "movie"}-$id",
        title = genre.name,
        endpoint = if (tv) "discover/tv" else "discover/movie",
        query = mapOf("with_genres" to id.toString(), "sort_by" to "popularity.desc", "vote_count.gte" to "200", "include_adult" to "false") + extra,
        tv = tv,
    )
}

internal fun decadeSpec(start: Int, tv: Boolean): BrowseSpec {
    val from = "$start-01-01"
    val to = "${start + 9}-12-31"
    val dates = if (tv) mapOf("first_air_date.gte" to from, "first_air_date.lte" to to, "without_genres" to "10767,10763")
    else mapOf("primary_release_date.gte" to from, "primary_release_date.lte" to to)
    return BrowseSpec(
        key = "decade-${if (tv) "tv" else "movie"}-$start",
        title = "${start}s",
        endpoint = if (tv) "discover/tv" else "discover/movie",
        query = dates + mapOf("sort_by" to "popularity.desc", "vote_count.gte" to "300", "include_adult" to "false"),
        tv = tv,
    )
}

/**
 * The data behind the Search home, all from TMDB: this week's trending titles, the collection and genre lists,
 * the people lists and the films of each franchise. Answers are kept for a few hours.
 */
internal object SearchBrowseData {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val permits = Semaphore(6)
    private val lock = Mutex()
    private val lists = HashMap<String, Pair<Long, List<MetaPreview>>>()
    private val people = HashMap<String, Pair<Long, List<PersonHit>>>()
    private val films = HashMap<String, MetaPreview?>()
    private val covers = HashMap<String, String>()
    private const val TTL_MS = 3L * 60 * 60 * 1000

    private suspend fun results(endpoint: String, query: Map<String, String>): List<JsonObject> {
        val key = TmdbSettingsRepository.effectiveApiKey()
        if (key.isBlank()) return emptyList()
        val q = mapOf("language" to TmdbSettingsRepository.snapshot().language) + query
        val text = permits.withPermit { httpGetText(buildTmdbUrl(endpoint = endpoint, apiKey = key, query = q)) }
        val array = json.parseToJsonElement(text).jsonObject["results"] as? JsonArray ?: return emptyList()
        return array.mapNotNull { it as? JsonObject }
    }

    /** Today's date two years back, as TMDB wants it (YYYY-MM-DD). */
    private fun twoYearsAgo(): String {
        val today = com.nuvio.app.features.watchprogress.CurrentDateProvider.todayIsoDate()
        val year = today.take(4).toIntOrNull() ?: return today
        return (year - 2).toString().padStart(4, '0') + today.drop(4)
    }

    /** True when there is a TMDB key to ask with. */
    fun available(): Boolean = TmdbSettingsRepository.effectiveApiKey().isNotBlank()

    suspend fun list(spec: BrowseSpec, limit: Int = 60): List<MetaPreview> {
        val now = LiveTvClock.nowMs()
        lock.withLock { lists[spec.key]?.takeIf { now - it.first < TTL_MS }?.let { return it.second } }
        val found = runCatching {
            val filters = spec.query.mapValues { (_, v) -> v.replace("{TWO_YEARS_AGO}", twoYearsAgo()) }
            // Two pages so a full grid is there without asking again while scrolling.
            val first = results(spec.endpoint, filters + ("page" to "1"))
            val second = if (limit > 20) runCatching { results(spec.endpoint, filters + ("page" to "2")) }.getOrDefault(emptyList()) else emptyList()
            (first + second)
                .filterNot { it["adult"]?.toString() == "true" }
                .mapNotNull { it.toPreview(spec.tv) }.distinctBy { it.id }
                // A franchise fills a list with its sequels; one entry per title family keeps the picks varied.
                .distinctBy { it.name.substringBefore(":").replace(Regex("""[\\s]+(Part\\s+)?[IVX0-9]+$"""), "").trim().lowercase() }
                .take(limit)
        }.getOrDefault(emptyList())
        if (found.isNotEmpty()) lock.withLock { lists[spec.key] = now to found }
        return found
    }

    /** What TMDB's users are looking at most this week, films and series together. */
    suspend fun trending(): List<MetaPreview> {
        val now = LiveTvClock.nowMs()
        lock.withLock { lists["trending"]?.takeIf { now - it.first < TTL_MS }?.let { return it.second } }
        val found = runCatching {
            results("trending/all/week", emptyMap())
                .filter { it.str("media_type") == "movie" || it.str("media_type") == "tv" }
                .filter { it.str("poster_path") != null }
                // Late-night and news shows chart every day and say nothing about what is worth watching.
                .filterNot { o -> (o["genre_ids"] as? JsonArray).orEmpty().any { (it as? JsonPrimitive)?.intOrNull in setOf(10767, 10763) } }
                .mapNotNull { it.toPreview(it.str("media_type") == "tv") }
                .take(20)
        }.getOrDefault(emptyList())
        if (found.isNotEmpty()) lock.withLock { lists["trending"] = now to found }
        return found
    }

    /** A film's or series' backdrop by title and year (`size` is a TMDB image size such as w780 or w1280); null when TMDB has none. */
    suspend fun backdrop(title: String, year: Int, series: Boolean, size: String = "w780"): String? {
        val key = "cover|$title|$year|$size"
        lock.withLock { covers[key]?.let { return it } }
        val found = runCatching {
            val query = mapOf("query" to title.encodeURLParameter(), "include_adult" to "false")
            val hit = if (series) {
                results("search/tv", query + ("first_air_date_year" to year.toString())).firstOrNull()
            } else {
                results("search/movie", query + ("primary_release_year" to year.toString())).firstOrNull()
            }
            hit?.str("backdrop_path")?.let { "https://image.tmdb.org/t/p/$size$it" }
        }.getOrNull()
        if (found != null) lock.withLock { covers[key] = found }
        return found
    }

    /** A film by title and year, for the franchise lists; null when TMDB doesn't have it. */
    suspend fun film(title: String, year: Int): MetaPreview? {
        val key = "$title|$year"
        lock.withLock { if (films.containsKey(key)) return films[key] }
        val found = runCatching {
            val query = mapOf("query" to title.encodeURLParameter(), "include_adult" to "false")
            (results("search/movie", query + ("primary_release_year" to year.toString())).firstOrNull()
                ?: results("search/movie", query).firstOrNull { (it.str("release_date")?.take(4)?.toIntOrNull() ?: 0) in (year - 1)..(year + 1) })
                ?.toPreview(tv = false)
        }.getOrNull()
        // A failed lookup (no network) is asked again next time; only a real answer is kept.
        if (found != null) lock.withLock { films[key] = found }
        return found
    }

    suspend fun films(entries: List<FranchiseFilm>): List<MetaPreview> = coroutineScope {
        entries.map { async { film(it.title, it.year) } }.awaitAll().filterNotNull().distinctBy { it.id }
    }

    suspend fun popularActors(): List<PersonHit> = peopleByName("actors", ACTOR_NAMES, "Acting")

    suspend fun directors(): List<PersonHit> = peopleByName("directors", DIRECTOR_NAMES, "Directing")

    /** Well-known people looked up by name, in the order given; TMDB's own "popular" chart is a daily list of unknowns. */
    private suspend fun peopleByName(cacheKey: String, names: List<String>, department: String): List<PersonHit> {
        val now = LiveTvClock.nowMs()
        lock.withLock { people[cacheKey]?.takeIf { now - it.first < TTL_MS }?.let { return it.second } }
        val found = coroutineScope {
            names.map { name ->
                async {
                    runCatching {
                        results("search/person", mapOf("query" to name.encodeURLParameter(), "include_adult" to "false"))
                            .firstOrNull { it.str("known_for_department") == department }?.toPerson()
                    }.getOrNull()
                }
            }.awaitAll().filterNotNull().distinctBy { it.id }
        }
        if (found.isNotEmpty()) lock.withLock { people[cacheKey] = now to found }
        return found
    }

    private fun JsonObject.toPerson(): PersonHit? {
        val id = (this["id"] as? JsonPrimitive)?.intOrNull ?: return null
        val name = str("name") ?: return null
        val knownFor = (this["known_for"] as? JsonArray).orEmpty()
            .mapNotNull { (it as? JsonObject)?.let { k -> k.str("title") ?: k.str("name") } }
            .take(2).joinToString(", ").ifBlank { null }
        return PersonHit(id = id, name = name, photo = str("profile_path")?.let { "https://image.tmdb.org/t/p/w185$it" }, subtitle = knownFor)
    }

    private fun JsonObject.toPreview(tv: Boolean): MetaPreview? {
        val id = (this["id"] as? JsonPrimitive)?.intOrNull ?: return null
        val name = str("title") ?: str("name") ?: return null
        val posterPath = str("poster_path") ?: return null
        val poster = "https://image.tmdb.org/t/p/w500$posterPath"
        val date = str("release_date") ?: str("first_air_date")
        return MetaPreview(
            id = "tmdb:$id",
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

    private val ACTOR_NAMES = listOf(
        "Tom Hanks", "Leonardo DiCaprio", "Meryl Streep", "Denzel Washington", "Brad Pitt", "Scarlett Johansson",
        "Robert Downey Jr.", "Margot Robbie", "Ryan Gosling", "Zendaya", "Timothée Chalamet", "Cillian Murphy",
        "Emma Stone", "Jennifer Lawrence", "Chris Hemsworth", "Dwayne Johnson", "Keanu Reeves", "Tom Cruise",
        "Morgan Freeman", "Samuel L. Jackson", "Natalie Portman", "Anne Hathaway", "Christian Bale", "Joaquin Phoenix",
        "Pedro Pascal", "Florence Pugh", "Anya Taylor-Joy", "Sydney Sweeney", "Ryan Reynolds", "Hugh Jackman",
        "Will Smith", "Jason Momoa", "Idris Elba", "Viola Davis", "Austin Butler", "Jenna Ortega", "Paul Mescal",
        "Harrison Ford", "Al Pacino", "Robert De Niro", "Julia Roberts", "Sandra Bullock", "Nicole Kidman",
        "Cate Blanchett", "Michael B. Jordan", "Chris Pratt", "Zoe Saldaña", "Jake Gyllenhaal", "Mark Ruffalo",
        "Chris Evans", "Jeremy Renner", "Benedict Cumberbatch", "Tom Hardy", "Emily Blunt", "Amy Adams", "Matt Damon",
    )

    private val DIRECTOR_NAMES = listOf(
        "Christopher Nolan", "Steven Spielberg", "Martin Scorsese", "Quentin Tarantino", "Denis Villeneuve", "James Cameron",
        "Ridley Scott", "David Fincher", "Greta Gerwig", "Jordan Peele", "Wes Anderson", "Guillermo del Toro", "Peter Jackson",
        "Stanley Kubrick", "Alfred Hitchcock", "Francis Ford Coppola", "Joel Coen", "Taika Waititi", "Patty Jenkins",
        "Kathryn Bigelow", "Spike Lee", "Clint Eastwood", "Tim Burton", "Edgar Wright", "Bong Joon-ho", "Ryan Coogler",
        "Zack Snyder", "Sofia Coppola", "Paul Thomas Anderson", "George Lucas",
    )
}
