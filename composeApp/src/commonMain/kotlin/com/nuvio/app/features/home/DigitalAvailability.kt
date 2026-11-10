package com.nuvio.app.features.home

import com.nuvio.app.features.addons.httpGetText
import com.nuvio.app.features.music.data.systemLanguageTag
import com.nuvio.app.features.tmdb.TmdbSettingsRepository
import com.nuvio.app.features.tmdb.buildTmdbUrl
import com.nuvio.app.features.watchprogress.CurrentDateProvider
import com.nuvio.app.features.music.data.MusicStorage
import com.nuvio.app.features.sports.compat.LiveTvClock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/** A title that can be watched at home right now, with the id the rest of the app opens it by. */
data class AvailableTitle(
    val imdbId: String,
    /** The date it came to digital (movies); null for shows. */
    val digitalDate: String?,
)

/**
 * Whether a title can actually be watched at home today, as opposed to only being in cinemas or on TV.
 *
 * - A movie counts once it has a digital release dated today or earlier in the viewer's country (the US is the
 *   fallback), or when streaming, rental or purchase services list it there.
 * - A show counts once it has aired and a service lists it there.
 *
 * The answers are kept for the session, since Home's Top 10 and New & Hot ask about the same titles.
 */
object DigitalAvailability {
    private const val TYPE_DIGITAL = 4
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private class Entry(val title: AvailableTitle?, val at: Long)

    private const val STORE = "digital_availability"
    private const val KEY = "entries"
    private const val AVAILABLE_TTL_MS = 24L * 60 * 60 * 1000
    private const val UNAVAILABLE_TTL_MS = 6L * 60 * 60 * 1000
    private const val MAX_ENTRIES = 1200
    private val cache = HashMap<String, Entry>().also { loadInto(it) }

    /** "key|imdb|date|at" lines; an empty imdb means "checked, not available yet". */
    private fun loadInto(into: HashMap<String, Entry>) {
        MusicStorage.getString(STORE, KEY)?.lineSequence()?.forEach { line ->
            val p = line.split('|')
            if (p.size < 4) return@forEach
            val at = p[3].toLongOrNull() ?: return@forEach
            into[p[0]] = Entry(if (p[1].isBlank()) null else AvailableTitle(p[1], p[2].ifBlank { null }), at)
        }
    }

    /** Writes what has been learned to the device, so the next launch doesn't ask about the same titles again. */
    suspend fun persist() {
        val text = cacheLock.withLock {
            cache.entries.sortedByDescending { it.value.at }.take(MAX_ENTRIES)
                .joinToString("\n") { (k, e) -> "$k|${e.title?.imdbId.orEmpty()}|${e.title?.digitalDate.orEmpty()}|${e.at}" }
        }
        MusicStorage.putString(STORE, KEY, text)
    }

    /** The viewer's two-letter country, from the device language ("en-CA" -> "CA"); the US when it isn't known. */
    fun region(): String {
        val tag = runCatching { systemLanguageTag() }.getOrDefault("")
        return tag.replace('_', '-').split('-').drop(1).firstOrNull { it.length == 2 }?.uppercase() ?: "US"
    }

    /** The title's availability, or null when it can't be watched at home yet. */
    suspend fun check(mediaType: String, tmdbId: Int): AvailableTitle? {
        val key = "$mediaType:$tmdbId"
        val now = LiveTvClock.nowMs()
        cacheLock.withLock {
            cache[key]?.let { e ->
                val ttl = if (e.title != null) AVAILABLE_TTL_MS else UNAVAILABLE_TTL_MS
                if (now - e.at < ttl) return e.title
            }
        }
        // A lookup that fails (no network) throws and is not remembered, so it is asked again next time.
        val result = runCatching { load(mediaType, tmdbId) }.getOrElse { return null }
        cacheLock.withLock { cache[key] = Entry(result, now) }
        return result
    }

    private val cacheLock = Mutex()

    private suspend fun load(mediaType: String, tmdbId: Int): AvailableTitle? {
        val apiKey = TmdbSettingsRepository.effectiveApiKey()
        if (apiKey.isBlank()) return null
        val movie = mediaType == "movie"
        val url = buildTmdbUrl(
            endpoint = "$mediaType/$tmdbId",
            apiKey = apiKey,
            query = mapOf("append_to_response" to if (movie) "release_dates,watch/providers" else "watch/providers,external_ids"),
        )
        val root = json.parseToJsonElement(httpGetText(url)).jsonObject
        val region = region()
        val today = CurrentDateProvider.todayIsoDate()
        val imdb = (root["imdb_id"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: (root["external_ids"] as? JsonObject)?.get("imdb_id")?.let { (it as? JsonPrimitive)?.contentOrNull }?.takeIf { it.isNotBlank() }
            ?: return null

        val providers = (root["watch/providers"] as? JsonObject)?.get("results") as? JsonObject
        val listed = (providers?.get(region) ?: providers?.get("US")) as? JsonObject
        val onServices = listed != null && listOf("flatrate", "ads", "rent", "buy", "free").any { (listed[it] as? JsonArray)?.isNotEmpty() == true }

        if (!movie) {
            val aired = (root["first_air_date"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }?.let { it <= today } == true
            return if (aired && onServices) AvailableTitle(imdb, null) else null
        }
        val digital = digitalDate(root, region) ?: digitalDate(root, "US")
        val onDigital = digital != null && digital.take(10) <= today
        return if (onDigital || onServices) AvailableTitle(imdb, digital?.take(10)) else null
    }

    /** The earliest digital release date listed for [country], as an ISO date. */
    internal fun digitalDate(root: JsonObject, country: String): String? {
        val countries = (root["release_dates"] as? JsonObject)?.get("results") as? JsonArray ?: return null
        val entry = countries.mapNotNull { it as? JsonObject }
            .firstOrNull { (it["iso_3166_1"] as? JsonPrimitive)?.contentOrNull == country } ?: return null
        return (entry["release_dates"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
            .filter { (it["type"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() == TYPE_DIGITAL }
            .mapNotNull { (it["release_date"] as? JsonPrimitive)?.contentOrNull?.take(10) }
            .minOrNull()
    }

    /** The digital release date of a movie in the viewer's country (US as a fallback), for the Coming Soon row. */
    suspend fun digitalReleaseDate(tmdbId: Int): String? {
        val apiKey = TmdbSettingsRepository.effectiveApiKey()
        if (apiKey.isBlank()) return null
        val url = buildTmdbUrl(endpoint = "movie/$tmdbId", apiKey = apiKey, query = mapOf("append_to_response" to "release_dates"))
        val root = runCatching { json.parseToJsonElement(httpGetText(url)).jsonObject }.getOrNull() ?: return null
        return digitalDate(root, region()) ?: digitalDate(root, "US")
    }
}
