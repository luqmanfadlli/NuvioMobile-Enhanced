package com.nuvio.app.features.sportsfocus

import com.nuvio.app.features.news.NewsArticle
import com.nuvio.app.features.news.NewsHttp
import com.nuvio.app.features.news.NewsRanking
import com.nuvio.app.features.news.NewsSources
import com.nuvio.app.features.news.NewsState
import com.nuvio.app.features.news.NewsText
import com.nuvio.app.features.sports.League
import com.nuvio.app.features.sports.SportsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/**
 * Sports news from several publishers at once: ESPN's feed for each league that matters to you,
 * plus CBS, FOX, Sky, BBC and NBC. Each source reports as it finishes, so the page fills in rather
 * than waiting on the slowest one. Stories are de-duplicated and listed newest first.
 */
object SportsNews {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(NewsState())
    val state: StateFlow<NewsState> = _state.asStateFlow()
    private var loadedKey: String? = null
    private var loadJob: Job? = null
    private var loadedAtMs = 0L

    /** Leagues to pull ESPN's feed for: the ones you follow first, then the big ones. */
    fun leaguesForFeed(): List<League> {
        val followed = SportsRepository.followedLeagueIds()
        val all = SportsRepository.allEspnLeagues()
        val top = listOf(
            "football/nfl", "basketball/nba", "hockey/nhl", "baseball/mlb", "soccer/eng.1",
            "soccer/usa.1", "racing/f1", "basketball/wnba", "football/college-football",
        )
        val chosen = (all.filter { it.id in followed } + all.filter { it.id in top }).distinctBy { it.id }
        return chosen.take(12).ifEmpty { all.take(8) }
    }

    fun ensureLoaded(force: Boolean = false) {
        val leagues = leaguesForFeed()
        val key = leagues.joinToString(",") { it.id }
        val fresh = loadedAtMs > 0 && _state.value.articles.isNotEmpty() && nowMs() - loadedAtMs < REFRESH_MS
        if (!force && key == loadedKey && fresh) return
        loadedKey = key
        loadedAtMs = nowMs()
        loadJob?.cancel()
        _state.value = _state.value.copy(loading = true, error = null)
        loadJob = scope.launch {
            val total = leagues.size + NewsSources.feeds.size
            val results = Channel<Pair<String, List<NewsArticle>>>(Channel.UNLIMITED)
            leagues.forEach { league ->
                launch { results.send("espn:${league.id}" to runCatching { fetchEspn(league) }.getOrDefault(emptyList())) }
            }
            NewsSources.feeds.forEachIndexed { i, feed ->
                launch { results.send("rss:$i" to runCatching { NewsSources.fetch(feed) }.getOrDefault(emptyList())) }
            }
            val collected = HashMap<String, List<NewsArticle>>()
            repeat(total) {
                val (source, list) = results.receive()
                collected[source] = list
                val merged = NewsRanking.dedupe(NewsRanking.newestFirst(collected.values.flatten()))
                val done = collected.size >= total
                _state.value = NewsState(
                    loading = !done,
                    error = if (done && merged.isEmpty()) "Couldn't load sports news right now." else null,
                    articles = merged,
                )
            }
        }
    }

    /** All stories, newest first, one of each. */
    fun merged(state: NewsState): List<NewsArticle> = state.articles

    private suspend fun fetchEspn(league: League): List<NewsArticle> {
        val path = league.espnPath ?: return emptyList()
        val body = NewsHttp.get("https://site.api.espn.com/apis/site/v2/sports/$path/news?limit=20", "application/json") ?: return emptyList()
        val articles = (json.parseToJsonElement(body).jsonObject["articles"] as? JsonArray).orEmpty()
        return articles.mapNotNull { element ->
            val a = element as? JsonObject ?: return@mapNotNull null
            val headline = a.str("headline") ?: return@mapNotNull null
            val link = ((a["links"] as? JsonObject)?.get("web") as? JsonObject)?.str("href")
            if (link != null && "/video/" in link) return@mapNotNull null
            if (a.str("type")?.lowercase() in setOf("media", "video", "highlight")) return@mapNotNull null
            val image = (a["images"] as? JsonArray)?.firstOrNull()?.let { (it as? JsonObject)?.str("url") }
            NewsArticle(
                id = a.str("id")?.let { "espn:$it" } ?: ("espn:" + league.id + headline.hashCode()),
                headline = headline,
                summary = a.str("description").orEmpty(),
                imageUrl = image,
                url = link,
                publishedMs = NewsText.parseDateMs(a.str("published")),
                leagueId = league.id,
                leagueShort = league.short,
                publisher = "ESPN",
                author = a.str("byline"),
                sportKey = league.sport.name,
            )
        }
    }

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun nowMs(): Long = com.nuvio.app.features.sports.compat.LiveTvClock.nowMs()

    private const val REFRESH_MS = 10 * 60 * 1000L
}
