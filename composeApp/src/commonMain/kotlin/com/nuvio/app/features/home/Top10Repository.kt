package com.nuvio.app.features.home

import com.nuvio.app.features.addons.httpGetText
import com.nuvio.app.features.sports.compat.LiveTvClock
import com.nuvio.app.features.tmdb.TmdbSettingsRepository
import com.nuvio.app.features.tmdb.buildTmdbUrl
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

/**
 * The "Top 10 Today" row: the ten movies and shows people are watching most today.
 *
 * Netflix publishes its own Top 10 only as a weekly list with no API, so this uses TMDB's daily
 * trending chart instead. It covers every service, refreshes each day, and every title carries the
 * ids the rest of the app needs to open its page.
 */
object Top10Repository {
    private const val MAX_AGE_MS = 3L * 60 * 60 * 1000
    private const val SIZE = 10

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _items = MutableStateFlow<List<MetaPreview>>(emptyList())
    val items: StateFlow<List<MetaPreview>> = _items.asStateFlow()
    private var loadedAtMs = 0L
    private var job: Job? = null

    /** Loads the chart, or refreshes it when it is a few hours old. Safe to call whenever Home shows. */
    fun ensureLoaded() {
        if (job?.isActive == true) return
        if (_items.value.isNotEmpty() && LiveTvClock.nowMs() - loadedAtMs < MAX_AGE_MS) return
        job = scope.launch {
            val list = runCatching { fetch() }.getOrDefault(emptyList())
            if (list.isNotEmpty()) {
                _items.value = list
                loadedAtMs = LiveTvClock.nowMs()
            }
        }
    }

    private suspend fun fetch(): List<MetaPreview> {
        val key = TmdbSettingsRepository.effectiveApiKey()
        if (key.isBlank()) return emptyList()
        val url = buildTmdbUrl(
            endpoint = "trending/all/day",
            apiKey = key,
            query = mapOf("language" to TmdbSettingsRepository.snapshot().language),
        )
        val results = json.parseToJsonElement(httpGetText(url)).jsonObject["results"] as? JsonArray ?: return emptyList()
        // A few spare titles, since some have no IMDb id the catalogs could open.
        val picks = results.mapNotNull { (it as? JsonObject)?.takeIf { o -> o.str("media_type") in setOf("movie", "tv") && o.str("poster_path") != null } }
            // Late-night and news shows chart every day and say nothing about what is worth watching.
            .filterNot { o -> (o["genre_ids"] as? JsonArray).orEmpty().any { (it as? JsonPrimitive)?.intOrNull in setOf(10767, 10763) } }
            .take(SIZE * 3)
        return coroutineScope {
            picks.map { o ->
                async {
                    val tmdbId = (o["id"] as? JsonPrimitive)?.intOrNull ?: return@async null
                    val tv = o.str("media_type") == "tv"
                    // Only what can be watched at home today: a film still in cinemas has no business in a Top 10 to press Play on.
                    val available = com.nuvio.app.features.newhot.DigitalAvailability.check(if (tv) "tv" else "movie", tmdbId) ?: return@async null
                    o.toPreview(available.imdbId, tv)
                }
            }.awaitAll().filterNotNull().take(SIZE)
        }
    }

    private fun JsonObject.toPreview(imdbId: String, tv: Boolean): MetaPreview? {
        val name = str("title") ?: str("name") ?: return null
        val poster = str("poster_path")?.let { "https://image.tmdb.org/t/p/w500$it" } ?: return null
        val date = str("release_date") ?: str("first_air_date")
        return MetaPreview(
            id = imdbId,
            type = if (tv) "series" else "movie",
            name = name,
            poster = poster,
            rawPosterUrl = poster,
            banner = str("backdrop_path")?.let { "https://image.tmdb.org/t/p/w1280$it" },
            description = str("overview"),
            releaseInfo = date?.take(4),
            rawReleaseDate = date,
            popularity = (this["popularity"] as? JsonPrimitive)?.doubleOrNull,
            voteCount = (this["vote_count"] as? JsonPrimitive)?.intOrNull,
        )
    }

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
}
