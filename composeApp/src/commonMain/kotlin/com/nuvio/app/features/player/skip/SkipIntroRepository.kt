package com.nuvio.app.features.player.skip

import com.nuvio.app.core.logging.InAppLogger
import com.nuvio.app.features.player.PlayerSettingsRepository
import kotlinx.coroutines.CancellationException
import com.nuvio.app.features.tmdb.TmdbService
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

object SkipIntroRepository {

    private val cache = HashMap<String, List<SkipInterval>>()
    private val imdbEntriesCache = HashMap<String, List<ArmEntry>>()
    private val animeSkipShowIdCache = HashMap<String, String>()
    private const val NO_ID = "__none__"
    private const val INTRO_DB_TIMEOUT_MS = 5_000L
    private const val ARM_LOOKUP_TIMEOUT_MS = 3_000L
    private const val ANISKIP_TIMEOUT_MS = 3_000L
    private const val ANIME_SKIP_TIMEOUT_MS = 3_000L
    private const val ANIME_SKIP_SHOW_LOOKUP_TIMEOUT_MS = 3_000L

    private val introDbConfigured: Boolean
        get() = IntroDbConfig.URL.isNotBlank()

    suspend fun getMovieSkipIntervals(
        contentId: String?,
        videoId: String?,
        requireSkipIntroEnabled: Boolean = true,
    ): List<SkipInterval> {
        if (!introDbConfigured ||
            (requireSkipIntroEnabled && !PlayerSettingsRepository.uiState.value.skipIntroEnabled)
        ) return emptyList()
        val imdbId = resolveMovieSkipImdbId(
            contentId, videoId,
            resolveTmdb = { TmdbService.tmdbToImdb(it, "movie") },
            resolveAnime = { source, id -> SimklIdResolver.resolveIds(source, id)?.imdb },
        ) ?: return emptyList()
        val cacheKey = "movie:$imdbId"
        cache[cacheKey]?.let { return it }
        val data = SkipIntroApi.getIntroDbMovieSegments(imdbId) ?: return emptyList()
        return data.movieSkipIntervals().also { cache[cacheKey] = it }
    }

    suspend fun getSkipIntervals(
        imdbId: String?,
        season: Int,
        episode: Int,
        requireSkipIntroEnabled: Boolean = true,
    ): List<SkipInterval> = coroutineScope {
        if (imdbId == null) {
            InAppLogger.debug("Player/SkipIntro", "skip lookup ignored: imdbId missing s=$season e=$episode")
            return@coroutineScope emptyList()
        }
        val settings = PlayerSettingsRepository.uiState.value
        if (requireSkipIntroEnabled && !settings.skipIntroEnabled) {
            InAppLogger.debug("Player/SkipIntro", "skip lookup disabled imdb=$imdbId s=$season e=$episode")
            return@coroutineScope emptyList()
        }

        val cacheKey = "$imdbId:$season:$episode"
        cache[cacheKey]?.let { cached ->
            InAppLogger.debug("Player/SkipIntro", "skip lookup cache hit imdb=$imdbId s=$season e=$episode count=${cached.size}")
            return@coroutineScope cached
        }
        InAppLogger.info(
            "Player/SkipIntro",
            "skip lookup imdb=$imdbId s=$season e=$episode introDb=$introDbConfigured " +
                "animeSkip=${settings.animeSkipEnabled}",
        )

        val introDbDeferred = async {
            if (introDbConfigured) fetchFromIntroDb(imdbId, season, episode) else emptyList()
        }
        // Resolve IMDB -> season-specific MAL/AniList via Simkl full_anime_seasons.
        // Kept as a deferred so it runs alongside IntroDB: the fork returns early when IntroDB
        // already has an opening, and cancels this lookup instead of waiting for it.
        val simklIdsDeferred = async { SimklIdResolver.resolveIdsForImdbEpisode(imdbId, season, episode) }

        val introDb = introDbDeferred.await()
        if (introDb.hasOpeningSegment()) {
            simklIdsDeferred.cancel()
            InAppLogger.info(
                "Player/SkipIntro",
                "skip lookup fast result imdb=$imdbId s=$season e=$episode count=${introDb.size} provider=introdb",
            )
            cache[cacheKey] = introDb
            return@coroutineScope introDb
        }

        val simklIds = simklIdsDeferred.await()
        val malId = simklIds?.mal
        val anilistId = simklIds?.anilist

        // Remap the TVDB episode number to the anime-entry-local episode number.
        val animeEpisode = if (simklIds != null) {
            val mapping = SimklIdResolver.getEpisodeMapping(simklIds.simklId, simklIds.type)
            mapping.firstOrNull { it.tvdbSeason == season && it.tvdbEpisode == episode }
                ?.animeEpisode
                ?: episode
        } else episode

        val aniSkipDeferred = async {
            if (malId != null) fetchFromAniSkip(malId, animeEpisode) else emptyList()
        }
        val animeSkipDeferred = async {
            if (anilistId != null) fetchFromAnimeSkip(anilistId, animeEpisode, season = null) else emptyList()
        }

        val animeSkip = animeSkipDeferred.await()
        val aniSkip = aniSkipDeferred.await()
        return@coroutineScope mergeByPriority(
            introDb,
            aniSkip,
            animeSkip,
        ).also { merged ->
            InAppLogger.info(
                "Player/SkipIntro",
                "skip lookup result imdb=$imdbId s=$season e=$episode count=${merged.size} " +
                    "introdb=${introDb.size} animeskip=${animeSkip.size} aniskip=${aniSkip.size}",
            )
            cache[cacheKey] = merged
        }
    }

    suspend fun getSkipIntervalsForMal(
        malId: String,
        episode: Int,
        requireSkipIntroEnabled: Boolean = true,
        imdbId: String? = null,
        imdbSeason: Int? = null,
        imdbEpisode: Int? = null,
    ): List<SkipInterval> = coroutineScope {
        val settings = PlayerSettingsRepository.uiState.value
        if (requireSkipIntroEnabled && !settings.skipIntroEnabled) {
            InAppLogger.debug("Player/SkipIntro", "skip lookup disabled mal=$malId e=$episode")
            return@coroutineScope emptyList()
        }

        val cacheKey = "mal:$malId:$episode"
        cache[cacheKey]?.let { cached ->
            InAppLogger.debug("Player/SkipIntro", "skip lookup cache hit mal=$malId e=$episode count=${cached.size}")
            return@coroutineScope cached
        }
        InAppLogger.info("Player/SkipIntro", "skip lookup mal=$malId e=$episode")

        val aniSkipDeferred = async { fetchFromAniSkip(malId, episode) }

        val imdbIdDeferred = async {
            try {
                SkipIntroApi.resolveMalToImdb(malId)?.imdb
            } catch (_: Exception) { null }
        }

        var introDb = emptyList<SkipInterval>()
        var animeSkip = emptyList<SkipInterval>()
        val imdbId = imdbIdDeferred.await()
        if (imdbId != null) {
            val entries = resolveImdbEntries(imdbId)
            val season = entries.indexOfFirst { it.myanimelist == malId.toIntOrNull() } + 1
            val introDbDeferred = async {
                if (introDbConfigured) fetchFromIntroDb(imdbId, season, episode) else emptyList()
            }
            val animeSkipDeferred = async { fetchAnimeSkipForEntries(entries, season, episode) }
            introDb = introDbDeferred.await()
            animeSkip = animeSkipDeferred.await()
        } else {
            val anilistId = try {
                SkipIntroApi.resolveMalToAnilist(malId)?.anilist?.toString()
            } catch (_: Exception) { null }
            if (anilistId != null) animeSkip = fetchFromAnimeSkip(anilistId, episode, season = null)
        }

        val aniSkip = aniSkipDeferred.await()
        return@coroutineScope mergeByPriority(introDb, aniSkip, animeSkip).also { merged ->
            InAppLogger.info(
                "Player/SkipIntro",
                "skip lookup result mal=$malId e=$episode count=${merged.size} " +
                    "introdb=${introDb.size} animeskip=${animeSkip.size} aniskip=${aniSkip.size}",
            )
            cache[cacheKey] = merged
        }
    }

    suspend fun getSkipIntervalsForKitsu(
        kitsuId: String,
        episode: Int,
        requireSkipIntroEnabled: Boolean = true,
        imdbId: String? = null,
        imdbSeason: Int? = null,
        imdbEpisode: Int? = null,
    ): List<SkipInterval> = coroutineScope {
        val settings = PlayerSettingsRepository.uiState.value
        if (requireSkipIntroEnabled && !settings.skipIntroEnabled) {
            InAppLogger.debug("Player/SkipIntro", "skip lookup disabled kitsu=$kitsuId e=$episode")
            return@coroutineScope emptyList()
        }

        val cacheKey = "kitsu:$kitsuId:$episode"
        cache[cacheKey]?.let { cached ->
            InAppLogger.debug("Player/SkipIntro", "skip lookup cache hit kitsu=$kitsuId e=$episode count=${cached.size}")
            return@coroutineScope cached
        }
        InAppLogger.info("Player/SkipIntro", "skip lookup kitsu=$kitsuId e=$episode")

        val malIdDeferred = async {
            try {
                SkipIntroApi.resolveKitsuToMal(kitsuId)?.myanimelist?.toString()
            } catch (_: Exception) { null }
        }
        val imdbIdDeferred = async {
            try {
                SkipIntroApi.resolveKitsuToImdb(kitsuId)?.imdb
            } catch (_: Exception) { null }
        }
        val aniSkipDeferred = async {
            malIdDeferred.await()?.let { fetchFromAniSkip(it, episode) } ?: emptyList()
        }

        var introDb = emptyList<SkipInterval>()
        var animeSkip = emptyList<SkipInterval>()
        val imdbId = imdbIdDeferred.await()
        if (imdbId != null) {
            val entries = resolveImdbEntries(imdbId)
            val season = entries.indexOfFirst { it.kitsu == kitsuId.toIntOrNull() } + 1
            val introDbDeferred = async {
                if (introDbConfigured) fetchFromIntroDb(imdbId, season, episode) else emptyList()
            }
            val animeSkipDeferred = async { fetchAnimeSkipForEntries(entries, season, episode) }
            introDb = introDbDeferred.await()
            animeSkip = animeSkipDeferred.await()
        } else {
            val anilistId = try {
                SkipIntroApi.resolveKitsuToAnilist(kitsuId)?.anilist?.toString()
            } catch (_: Exception) { null }
            if (anilistId != null) animeSkip = fetchFromAnimeSkip(anilistId, episode, season = null)
        }

        val aniSkip = aniSkipDeferred.await()
        return@coroutineScope mergeByPriority(introDb, aniSkip, animeSkip).also { merged ->
            InAppLogger.info(
                "Player/SkipIntro",
                "skip lookup result kitsu=$kitsuId e=$episode count=${merged.size} " +
                    "introdb=${introDb.size} animeskip=${animeSkip.size} aniskip=${aniSkip.size}",
            )
            cache[cacheKey] = merged
        }
    }

    /**
     * Merge provider results into one best-of: fill each segment category (opening / ending /
     * recap) from the highest-priority provider that has it. Arguments MUST be passed in priority
     * order (IntroDB has the broadest coverage, then Anime-Skip, then AniSkip), so a partial
     * result from one provider never shadows a complete segment from another.
     */
    private fun mergeByPriority(vararg providerResults: List<SkipInterval>): List<SkipInterval> {
        val chosen = LinkedHashMap<String, SkipInterval>()
        for (result in providerResults) {
            for (interval in result) {
                val category = segmentCategory(interval.type) ?: continue
                if (category !in chosen) chosen[category] = interval
            }
        }
        return chosen.values.toList()
    }

    private fun List<SkipInterval>.hasOpeningSegment(): Boolean = any { interval ->
        segmentCategory(interval.type) == "opening"
    }

    private suspend fun <T> withSkipProviderTimeout(
        provider: String,
        timeoutMs: Long,
        fallback: T,
        block: suspend () -> T,
    ): T {
        val result = withTimeoutOrNull(timeoutMs) { block() }
        if (result == null) {
            InAppLogger.warn("Player/SkipIntro", "$provider timed out after ${timeoutMs}ms")
            return fallback
        }
        return result
    }

    // AnimeSkip: season-specific AniList ID first, then season-1 as a season-filtered fallback.
    private suspend fun fetchAnimeSkipForEntries(
        entries: List<ArmEntry>,
        season: Int,
        episode: Int
    ): List<SkipInterval> {
        val seasonAnilistId = entries.getOrNull(season - 1)?.anilist?.toString()
        val fallbackAnilistId = entries.firstOrNull()?.anilist?.toString()
        for ((anilistId, seasonFilter) in listOfNotNull(
            seasonAnilistId?.let { it to null },
            if (fallbackAnilistId != null && fallbackAnilistId != seasonAnilistId) fallbackAnilistId to season else null
        )) {
            val result = fetchFromAnimeSkip(anilistId, episode, season = seasonFilter)
            if (result.isNotEmpty()) return result
        }
        return emptyList()
    }
    private suspend fun fetchFromIntroDb(imdbId: String, season: Int, episode: Int): List<SkipInterval> {
        return withSkipProviderTimeout("IntroDB", INTRO_DB_TIMEOUT_MS, emptyList()) {
            try {
                InAppLogger.debug("Player/SkipIntro", "IntroDB fetch imdb=$imdbId s=$season e=$episode")
                val data = SkipIntroApi.getIntroDbSegments(imdbId, season, episode)
                if (data == null) {
                    InAppLogger.debug("Player/SkipIntro", "IntroDB empty imdb=$imdbId s=$season e=$episode")
                    emptyList()
                } else {
                    listOfNotNull(
                        data.intro.toSkipIntervalOrNull("intro"),
                        data.recap.toSkipIntervalOrNull("recap"),
                        data.outro.toSkipIntervalOrNull("outro"),
                    ).also { result ->
                        InAppLogger.debug("Player/SkipIntro", "IntroDB result imdb=$imdbId s=$season e=$episode count=${result.size}")
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                InAppLogger.warn(
                    "Player/SkipIntro",
                    "IntroDB failed imdb=$imdbId s=$season e=$episode error=${InAppLogger.throwableSummary(error)}",
                )
                emptyList()
            }
        }
    }

    private fun IntroDbSegment?.toSkipIntervalOrNull(type: String): SkipInterval? {
        if (this == null) return null
        val start = startSec ?: startMs?.let { it / 1000.0 }
        val end = endSec ?: endMs?.let { it / 1000.0 }
        if (start == null || end == null || !start.isFinite() || !end.isFinite() || start < 0 || end <= start) return null
        return SkipInterval(startTime = start, endTime = end, type = type, provider = "introdb")
    }

    private suspend fun fetchFromAniSkip(malId: String, episode: Int): List<SkipInterval> {
        return withSkipProviderTimeout("AniSkip", ANISKIP_TIMEOUT_MS, emptyList()) {
            try {
                InAppLogger.debug("Player/SkipIntro", "AniSkip fetch mal=$malId e=$episode")
                val response = SkipIntroApi.getAniSkipTimes(malId, episode)
                when {
                    response == null -> {
                        InAppLogger.debug("Player/SkipIntro", "AniSkip empty mal=$malId e=$episode")
                        emptyList()
                    }
                    !response.found -> {
                        InAppLogger.debug("Player/SkipIntro", "AniSkip not found mal=$malId e=$episode")
                        emptyList()
                    }
                    else -> {
                        response.results?.map { result ->
                            SkipInterval(
                                startTime = result.interval.startTime,
                                endTime = result.interval.endTime,
                                type = result.skipType,
                                provider = "aniskip",
                            )
                        }?.also { result ->
                            InAppLogger.debug("Player/SkipIntro", "AniSkip result mal=$malId e=$episode count=${result.size}")
                        } ?: emptyList()
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                InAppLogger.warn(
                    "Player/SkipIntro",
                    "AniSkip failed mal=$malId e=$episode error=${InAppLogger.throwableSummary(error)}",
                )
                emptyList()
            }
        }
    }

    private suspend fun fetchFromAnimeSkip(anilistId: String, episode: Int, season: Int?): List<SkipInterval> {
        val settings = PlayerSettingsRepository.uiState.value
        val clientId = settings.animeSkipClientId.trim()
        if (clientId.isBlank()) {
            InAppLogger.debug("Player/SkipIntro", "AnimeSkip skipped: client id missing anilist=$anilistId e=$episode")
            return emptyList()
        }
        if (!settings.animeSkipEnabled) {
            InAppLogger.debug("Player/SkipIntro", "AnimeSkip disabled anilist=$anilistId e=$episode")
            return emptyList()
        }

        return withSkipProviderTimeout("AnimeSkip", ANIME_SKIP_TIMEOUT_MS, emptyList()) {
            try {
                InAppLogger.debug("Player/SkipIntro", "AnimeSkip fetch anilist=$anilistId e=$episode season=${season ?: 0}")
                val showIds = resolveAnimeSkipShowIds(anilistId, clientId)
                if (showIds.isEmpty()) {
                    InAppLogger.debug("Player/SkipIntro", "AnimeSkip show id not found anilist=$anilistId")
                    return@withSkipProviderTimeout emptyList()
                }

                for (showId in showIds) {
                    val query = "{ findEpisodesByShowId(showId: \"$showId\") { season number baseDuration timestamps { at type { name } } } }"
                    val response = SkipIntroApi.queryAnimeSkip(clientId, query) ?: continue
                    val episodes = response.data?.findEpisodesByShowId ?: continue

                    val result = episodes
                        .filter { ep ->
                            ep.number?.toIntOrNull() == episode &&
                                (season == null || ep.season?.toIntOrNull() == season)
                        }
                        .bestAnimeSkipIntervals()
                    if (result.isNotEmpty()) {
                        InAppLogger.debug(
                            "Player/SkipIntro",
                            "AnimeSkip result anilist=$anilistId showId=$showId e=$episode count=${result.size}",
                        )
                        return@withSkipProviderTimeout result
                    }
                }
                InAppLogger.debug("Player/SkipIntro", "AnimeSkip empty anilist=$anilistId e=$episode")
                emptyList()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                InAppLogger.warn(
                    "Player/SkipIntro",
                    "AnimeSkip failed anilist=$anilistId e=$episode error=${InAppLogger.throwableSummary(error)}",
                )
                emptyList()
            }
        }
    }

    private suspend fun resolveAnimeSkipShowIds(anilistId: String, clientId: String): List<String> {
        animeSkipShowIdCache[anilistId]?.let { cached ->
            InAppLogger.debug("Player/SkipIntro", "AnimeSkip show id cache hit anilist=$anilistId value=$cached")
            return if (cached == NO_ID) emptyList() else listOf(cached)
        }
        val query = "{ findShowsByExternalId(service: ANILIST, serviceId: \"$anilistId\") { id } }"
        val showIds = withSkipProviderTimeout(
            provider = "AnimeSkip show resolve",
            timeoutMs = ANIME_SKIP_SHOW_LOOKUP_TIMEOUT_MS,
            fallback = emptyList(),
        ) {
            try {
                SkipIntroApi.queryAnimeSkip(clientId, query)
                    ?.data?.findShowsByExternalId?.map { it.id } ?: emptyList()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                InAppLogger.warn(
                    "Player/SkipIntro",
                    "AnimeSkip show id resolve failed anilist=$anilistId error=${InAppLogger.throwableSummary(error)}",
                )
                emptyList()
            }
        }

        if (showIds.size == 1) animeSkipShowIdCache[anilistId] = showIds[0]
        else if (showIds.isEmpty()) animeSkipShowIdCache[anilistId] = NO_ID
        return showIds
    }

    private suspend fun resolveImdbEntries(imdbId: String): List<ArmEntry> {
        imdbEntriesCache[imdbId]?.let { cached ->
            InAppLogger.debug("Player/SkipIntro", "ARM cache hit imdb=$imdbId entries=${cached.size}")
            return cached
        }
        return withSkipProviderTimeout("ARM resolve", ARM_LOOKUP_TIMEOUT_MS, emptyList()) {
            try {
                SkipIntroApi.resolveImdbToAll(imdbId)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                InAppLogger.warn("Player/SkipIntro", "ARM resolve failed imdb=$imdbId error=${InAppLogger.throwableSummary(error)}")
                emptyList()
            }
        }.also { entries ->
            InAppLogger.debug("Player/SkipIntro", "ARM resolve imdb=$imdbId entries=${entries.size}")
            imdbEntriesCache[imdbId] = entries
        }
    }

    suspend fun submitIntro(
        imdbId: String,
        season: Int,
        episode: Int,
        startSec: Double,
        endSec: Double,
        segmentType: String,
    ): Boolean {
        val settings = PlayerSettingsRepository.uiState.value
        val apiKey = settings.introDbApiKey.trim()
        if (!settings.introSubmitEnabled || apiKey.isBlank()) {
            InAppLogger.warn(
                "Player/SkipIntro",
                "submit skipped imdb=$imdbId s=$season e=$episode enabled=${settings.introSubmitEnabled} apiKey=${apiKey.isNotBlank()}",
            )
            return false
        }

        InAppLogger.info(
            "Player/SkipIntro",
            "submit requested imdb=$imdbId s=$season e=$episode type=$segmentType start=$startSec end=$endSec",
        )

        val request = SubmitIntroRequest(
            imdbId = imdbId,
            season = season,
            episode = episode,
            startSec = startSec,
            endSec = endSec,
            startMs = (startSec * 1000).toLong(),
            endMs = (endSec * 1000).toLong(),
            segmentType = segmentType,
        )

        return try {
            SkipIntroApi.submitIntro(apiKey, request).also { success ->
                InAppLogger.info(
                    "Player/SkipIntro",
                    "submit result imdb=$imdbId s=$season e=$episode type=$segmentType success=$success",
                )
            }
        } catch (error: Exception) {
            InAppLogger.warn(
                "Player/SkipIntro",
                "submit failed imdb=$imdbId s=$season e=$episode type=$segmentType " +
                    "error=${InAppLogger.throwableSummary(error)}",
            )
            throw error
        }
    }

    suspend fun verifyIntroDbApiKey(apiKey: String): Boolean {
        InAppLogger.debug("Player/SkipIntro", "verify IntroDB api key requested hasKey=${apiKey.isNotBlank()}")
        return try {
            SkipIntroApi.verifyIntroDbApiKey(apiKey).also { success ->
                InAppLogger.info("Player/SkipIntro", "verify IntroDB api key result success=$success")
            }
        } catch (error: Exception) {
            InAppLogger.warn("Player/SkipIntro", "verify IntroDB api key failed error=${InAppLogger.throwableSummary(error)}")
            throw error
        }
    }

    fun clearCache() {
        cache.clear()
        imdbEntriesCache.clear()
        animeSkipShowIdCache.clear()
        InAppLogger.info("Player/SkipIntro", "skip caches cleared")
    }
}

internal suspend fun resolveMovieSkipImdbId(
    contentId: String?,
    videoId: String?,
    resolveTmdb: suspend (Int) -> String?,
    resolveAnime: suspend (String, String) -> String?,
): String? {
    val ids = listOfNotNull(contentId, videoId).map(String::trim).distinct()
    val imdbPattern = Regex("tt[0-9]+")
    ids.map { it.substringBefore(':') }.firstOrNull { imdbPattern.matches(it) }?.let { return it }
    return ids.firstNotNullOfOrNull { id ->
        val parts = id.split(':')
        val value = parts.getOrNull(1)?.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }
            ?: return@firstNotNullOfOrNull null
        when (parts.first().lowercase()) {
            "tmdb" -> value.toIntOrNull()?.takeIf { it > 0 }?.let { resolveTmdb(it) }
            "mal", "kitsu" -> resolveAnime(parts.first().lowercase(), value)
            else -> null
        }?.trim()?.takeIf { imdbPattern.matches(it) }
    }
}

/**
 * A show can hold several community entries for the same episode number, some of them incomplete.
 * Pick the entry covering the most segment categories (opening / ending / recap), then the one
 * with the most segments; on a tie the earlier entry wins.
 */
internal fun List<AnimeSkipEpisode>.bestAnimeSkipIntervals(): List<SkipInterval> =
    map { it.timestamps.orEmpty().toAnimeSkipIntervals(it.baseDuration) }
        .filter { it.isNotEmpty() }
        .maxWithOrNull(
            compareBy<List<SkipInterval>>(
                { intervals -> intervals.mapNotNull { segmentCategory(it.type) }.toSet().size },
                { it.size }
            )
        )
        .orEmpty()

/**
 * Anime-Skip timestamps only mark where a segment starts; each one ends where the next begins.
 * The last timestamp has no known end. Only an ending may run to the end of the episode, and only
 * when it starts in the second half of it (if the duration is known); an open-ended intro or recap
 * is dropped, since stretching it would skip the whole episode.
 */
internal fun List<AnimeSkipTimestamp>.toAnimeSkipIntervals(baseDuration: Double? = null): List<SkipInterval> {
    val sorted = sortedBy { it.at }
    return sorted.mapIndexedNotNull { i, ts ->
        val type = when (ts.type.name.lowercase()) {
            "intro", "new intro" -> "op"
            "credits", "new credits" -> "ed"
            "mixed intro" -> "mixed-op"
            "mixed credits" -> "mixed-ed"
            "recap" -> "recap"
            else -> return@mapIndexedNotNull null
        }
        val next = sorted.getOrNull(i + 1)
        val endTime = when {
            next != null -> next.at.takeIf { it > ts.at } ?: return@mapIndexedNotNull null
            segmentCategory(type) == "ending" && (baseDuration == null || ts.at >= baseDuration / 2) ->
                Double.MAX_VALUE
            else -> return@mapIndexedNotNull null
        }
        SkipInterval(startTime = ts.at, endTime = endTime, type = type, provider = "animeskip")
    }
}

internal fun segmentCategory(type: String): String? = when (type.lowercase()) {
    "intro", "op", "mixed-op" -> "opening"
    "outro", "ed", "mixed-ed", "credits", "ending" -> "ending"
    "recap" -> "recap"
    else -> null
}
