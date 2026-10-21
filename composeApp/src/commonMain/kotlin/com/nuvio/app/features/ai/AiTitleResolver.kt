package com.nuvio.app.features.ai

import com.nuvio.app.features.addons.httpGetText
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.PosterShape
import com.nuvio.app.features.tmdb.TmdbSettingsRepository
import com.nuvio.app.features.tmdb.buildTmdbUrl
import com.nuvio.app.features.tmdb.normalizeTmdbLanguage
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

/**
 * LLMs hallucinate titles, so every suggestion is verified against TMDB. Anything that does not
 * resolve to a real, poster-bearing entry is dropped. IDs are emitted as `tmdb:<id>`, the same
 * convention the TMDB collection sources already use, so details/streams work unchanged.
 */
internal object AiTitleResolver {
    private val json = Json { ignoreUnknownKeys = true }
    private val permits = Semaphore(6)

    suspend fun resolveAll(suggestions: List<AiSuggestion>): List<MetaPreview?> = coroutineScope {
        suggestions.map { suggestion ->
            async { permits.withPermit { runCatchingNonCancel { resolve(suggestion) } } }
        }.awaitAll()
    }

    private suspend fun resolve(suggestion: AiSuggestion): MetaPreview? {
        val name = suggestion.name.trim().takeIf { it.isNotBlank() } ?: return null
        val apiKey = TmdbSettingsRepository.effectiveApiKey().takeIf { it.isNotBlank() } ?: return null
        val language = normalizeTmdbLanguage(TmdbSettingsRepository.snapshot().language)
        val isSeries = suggestion.type.trim().lowercase() in SERIES_TYPES
        val endpoint = if (isSeries) "search/tv" else "search/movie"
        val yearParam = if (isSeries) "first_air_date_year" else "year"

        suspend fun search(withYear: Boolean): List<SearchResult> {
            val query = buildMap {
                put("query", name.encodeURLParameter())
                put("language", language)
                if (withYear && suggestion.year != null) put(yearParam, suggestion.year.toString())
            }
            val url = buildTmdbUrl(endpoint = endpoint, apiKey = apiKey, query = query)
            return json.decodeFromString<SearchResponse>(httpGetText(url)).results
        }

        val wanted = normalizeAiTitle(name)
        val withYear = if (suggestion.year != null) search(withYear = true) else emptyList()
        val match = withYear.firstOrNull { it.hasArtwork() }
            // Year was missing or wrong: only accept an exact title match so we never attach a
            // random popular title to a made-up name.
            ?: search(withYear = false).firstOrNull { result ->
                result.hasArtwork() && result.displayNames().any { normalizeAiTitle(it) == wanted }
            }
            ?: return null
        return match.toPreview(isSeries)
    }

    private fun SearchResult.hasArtwork(): Boolean = !posterPath.isNullOrBlank()

    private fun SearchResult.displayNames(): List<String> =
        listOfNotNull(title, name, originalTitle, originalName).filter { it.isNotBlank() }

    private fun SearchResult.toPreview(isSeries: Boolean): MetaPreview? {
        val displayName = displayNames().firstOrNull() ?: return null
        val rawDate = if (isSeries) firstAirDate else releaseDate
        return MetaPreview(
            id = "tmdb:$id",
            type = if (isSeries) "series" else "movie",
            name = displayName,
            poster = imageUrl(posterPath, "w500"),
            banner = imageUrl(backdropPath, "w1280"),
            posterShape = PosterShape.Poster,
            description = overview?.takeIf { it.isNotBlank() },
            releaseInfo = rawDate?.take(4),
            rawReleaseDate = rawDate,
            popularity = popularity,
            voteCount = voteCount,
            imdbRating = voteAverage?.let { ((it * 10).roundToInt() / 10.0).toString() },
        )
    }

    private fun imageUrl(path: String?, size: String): String? =
        path?.takeIf { it.isNotBlank() }?.let { "https://image.tmdb.org/t/p/$size$it" }

    private val SERIES_TYPES = setOf("series", "show", "tv", "tvshow", "anime")

    @Serializable
    private data class SearchResponse(val results: List<SearchResult> = emptyList())

    @Serializable
    private data class SearchResult(
        val id: Int,
        val title: String? = null,
        val name: String? = null,
        @SerialName("original_title") val originalTitle: String? = null,
        @SerialName("original_name") val originalName: String? = null,
        val overview: String? = null,
        @SerialName("poster_path") val posterPath: String? = null,
        @SerialName("backdrop_path") val backdropPath: String? = null,
        @SerialName("release_date") val releaseDate: String? = null,
        @SerialName("first_air_date") val firstAirDate: String? = null,
        val popularity: Double? = null,
        @SerialName("vote_average") val voteAverage: Double? = null,
        @SerialName("vote_count") val voteCount: Int? = null,
    )
}

internal inline fun <T> runCatchingNonCancel(block: () -> T): T? =
    try {
        block()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Throwable) {
        null
    }
