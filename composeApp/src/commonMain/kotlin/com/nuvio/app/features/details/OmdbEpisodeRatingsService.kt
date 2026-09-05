package com.nuvio.app.features.details

import co.touchlab.kermit.Logger
import com.nuvio.app.features.addons.httpGetText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal object OmdbEpisodeRatingsService {
    private const val MaxCachedSeasons = 400

    private val log = Logger.withTag("OmdbEpisodeRatings")
    private val json = Json { ignoreUnknownKeys = true }
    private val imdbIdRegex = Regex("tt\\d+")

    private val seasonCache = LinkedHashMap<String, Map<Int, Double>>()
    private var hasLoadedFromDisk = false

    val hasApiKey: Boolean
        get() = OmdbSettingsRepository.effectiveApiKey().isNotBlank()

    fun extractImdbId(vararg candidates: String?): String? =
        candidates.firstNotNullOfOrNull { candidate ->
            candidate?.let(imdbIdRegex::find)?.value
        }

    suspend fun fetchRatings(
        imdbId: String,
        seasonNumbers: List<Int>,
    ): Map<Pair<Int, Int>, Double> = withContext(Dispatchers.Default) {
        val apiKey = OmdbSettingsRepository.effectiveApiKey()
        if (apiKey.isBlank()) return@withContext emptyMap()

        val normalizedSeasons = seasonNumbers.distinct().sorted()
        if (normalizedSeasons.isEmpty()) return@withContext emptyMap()

        ensureLoadedFromDisk()

        val perSeason = coroutineScope {
            normalizedSeasons.map { season ->
                async { season to fetchSeason(imdbId = imdbId, season = season, apiKey = apiKey) }
            }.awaitAll()
        }

        perSeason.flatMap { (season, episodeRatings) ->
            episodeRatings.map { (episode, rating) -> (season to episode) to rating }
        }.toMap()
    }

    private fun ensureLoadedFromDisk() {
        if (hasLoadedFromDisk) return
        hasLoadedFromDisk = true
        val payload = OmdbEpisodeRatingsStorage.loadPayload()?.trim().orEmpty()
        if (payload.isEmpty()) return
        runCatching { json.decodeFromString<Map<String, Map<Int, Double>>>(payload) }
            .onSuccess { seasonCache.putAll(it) }
            .onFailure { error -> log.w { "Failed to load cached IMDb ratings: ${error.message}" } }
    }

    private fun persistToDisk() {
        val payload = runCatching { json.encodeToString<Map<String, Map<Int, Double>>>(seasonCache) }
            .getOrNull() ?: return
        OmdbEpisodeRatingsStorage.savePayload(payload)
    }

    private suspend fun fetchSeason(
        imdbId: String,
        season: Int,
        apiKey: String,
    ): Map<Int, Double> {
        val cacheKey = "$imdbId:$season"
        seasonCache[cacheKey]?.let { return it }

        val url = "https://www.omdbapi.com/?i=$imdbId&Season=$season&apikey=$apiKey"
        val response = runCatching {
            json.decodeFromString<OmdbSeasonResponse>(httpGetText(url))
        }.onFailure { error ->
            log.w { "OMDb season request failed for $imdbId season $season: ${error.message}" }
        }.getOrNull() ?: return emptyMap()

        if (!response.response.equals("True", ignoreCase = true)) return emptyMap()

        val ratings = response.episodes.orEmpty()
            .mapNotNull { episode ->
                val episodeNumber = episode.episode?.toIntOrNull() ?: return@mapNotNull null
                val rating = episode.imdbRating
                    ?.takeIf { it.isNotBlank() && !it.equals("N/A", ignoreCase = true) }
                    ?.toDoubleOrNull()
                    ?.takeIf { it > 0.0 }
                    ?: return@mapNotNull null
                episodeNumber to rating
            }
            .toMap()

        if (ratings.isNotEmpty()) {
            seasonCache[cacheKey] = ratings
            while (seasonCache.size > MaxCachedSeasons) {
                val oldestKey = seasonCache.keys.firstOrNull() ?: break
                seasonCache.remove(oldestKey)
            }
            persistToDisk()
        }
        return ratings
    }
}

@Serializable
private data class OmdbSeasonResponse(
    @SerialName("Response") val response: String? = null,
    @SerialName("Episodes") val episodes: List<OmdbEpisodeEntry>? = null,
)

@Serializable
private data class OmdbEpisodeEntry(
    @SerialName("Episode") val episode: String? = null,
    @SerialName("imdbRating") val imdbRating: String? = null,
)
