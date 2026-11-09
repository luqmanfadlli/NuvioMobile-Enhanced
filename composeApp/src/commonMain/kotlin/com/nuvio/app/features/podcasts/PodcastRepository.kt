package com.nuvio.app.features.podcasts

import com.nuvio.app.features.music.data.MusicStorage
import com.nuvio.app.features.news.NewsHttp
import com.nuvio.app.features.news.NewsText
import com.nuvio.app.features.news.RssItem
import com.nuvio.app.features.news.RssParser
import com.nuvio.app.features.news.TeamNames
import com.nuvio.app.features.sports.FollowedTeam
import com.nuvio.app.features.sports.Sport
import com.nuvio.app.features.sports.compat.LiveTvClock
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import nuvio.composeapp.generated.resources.Res

/**
 * Sports podcasts: a bundled catalogue of publisher feeds (each checked to be live and recent when it
 * was assembled), Apple's podcast directory for search and for team shows, and the shows' own RSS for
 * episodes. What you follow and what you played last is kept on the device.
 */
object PodcastRepository {
    private const val STORE = "podcasts"
    private const val SHOW_CACHE_MS = 20 * 60 * 1000L
    private const val MAX_EPISODES = 150
    private const val MAX_RECENT = 12

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Serializable
    private data class CatalogEntry(
        val id: String,
        @SerialName("t") val title: String,
        @SerialName("u") val feedUrl: String,
        @SerialName("s") val sport: String = "",
        @SerialName("l") val league: String = "",
        @SerialName("g") val tags: List<String> = emptyList(),
        @SerialName("a") val author: String = "",
        @SerialName("i") val image: String = "",
        @SerialName("m") val teams: List<String> = emptyList(),
    )

    private val _catalog = MutableStateFlow<List<Podcast>>(emptyList())
    val catalog: StateFlow<List<Podcast>> = _catalog.asStateFlow()
    private var teamsByShow: Map<String, List<String>> = emptyMap()
    private var catalogLoaded = false

    private val _subscriptions = MutableStateFlow(readList(Podcast.serializer(), "subs"))
    val subscriptions: StateFlow<List<Podcast>> = _subscriptions.asStateFlow()

    private val _recent = MutableStateFlow(readList(PodcastEpisode.serializer(), "recent"))
    val recent: StateFlow<List<PodcastEpisode>> = _recent.asStateFlow()

    private val _played = MutableStateFlow(readList(String.serializer(), "played").toSet())
    /** Ids of episodes you have started, for the Unplayed filter. */
    val played: StateFlow<Set<String>> = _played.asStateFlow()

    private val showCache = HashMap<String, Pair<Long, PodcastShow>>()
    private val teamCache = HashMap<String, List<Podcast>>()

    // ---- catalogue ------------------------------------------------------------------------------

    suspend fun ensureCatalogLoaded() {
        if (catalogLoaded) return
        val entries = runCatching {
            json.decodeFromString(ListSerializer(CatalogEntry.serializer()), Res.readBytes("files/podcast_catalog.json").decodeToString())
        }.getOrDefault(emptyList())
        if (entries.isEmpty()) return
        teamsByShow = entries.filter { it.teams.isNotEmpty() }.associate { it.feedUrl to it.teams }
        _catalog.value = entries.map {
            Podcast(
                id = it.feedUrl,
                title = it.title,
                publisher = it.author,
                imageUrl = it.image.ifBlank { null },
                sport = it.sport.ifBlank { null },
                league = it.league.ifBlank { null },
                tags = it.tags,
            )
        }
        catalogLoaded = true
    }

    /** The sport names the catalogue groups shows under, for a league's sport. */
    fun catalogSport(sport: Sport): String? = when (sport) {
        Sport.HOCKEY -> "Hockey"
        Sport.BASKETBALL -> "Basketball"
        Sport.AMERICAN_FOOTBALL -> "American Football"
        Sport.BASEBALL -> "Baseball"
        Sport.FOOTBALL -> "Soccer"
        Sport.GOLF -> "Golf"
        Sport.TENNIS -> "Tennis"
        Sport.RACING -> "Motorsport"
        Sport.FIGHTING -> "Combat Sports"
        Sport.WRESTLING -> "Pro Wrestling"
        else -> null
    }

    fun sports(): List<String> = _catalog.value.mapNotNull { it.sport }.distinct().sorted()

    /** Shows for the given catalogue sports, ones about a followed team first. */
    fun forSports(sports: Set<String>, teams: List<FollowedTeam> = emptyList(), limit: Int = 24): List<Podcast> {
        val all = _catalog.value
        val teamNames = teams.flatMap { listOf(TeamNames.full(it).lowercase(), it.name.lowercase()) }
        val aboutTeam = all.filter { show -> teamsByShow[show.id].orEmpty().any { it.lowercase() in teamNames } }
        val inSports = if (sports.isEmpty()) all else all.filter { it.sport in sports }
        return (aboutTeam + inSports).distinctBy { it.id }.take(limit)
    }

    /** Catalogue shows made about one team. */
    fun aboutTeam(team: FollowedTeam): List<Podcast> {
        val names = listOf(TeamNames.full(team).lowercase(), team.name.lowercase())
        return _catalog.value.filter { show -> teamsByShow[show.id].orEmpty().any { it.lowercase() in names } }
    }

    fun markPlayed(id: String) {
        val played = (_played.value + id).toList().takeLast(600)
        _played.value = played.toSet()
        write(String.serializer(), "played", played)
    }

    // ---- shows and episodes ---------------------------------------------------------------------

    suspend fun loadShow(podcast: Podcast): PodcastShow? {
        val now = LiveTvClock.nowMs()
        showCache[podcast.id]?.takeIf { now - it.first < SHOW_CACHE_MS }?.let { return it.second }
        val xml = NewsHttp.get(podcast.id, "application/rss+xml, application/xml, text/xml") ?: return null
        val feed = runCatching { RssParser.parse(xml, MAX_EPISODES) }.getOrNull() ?: return null
        val enriched = podcast.copy(
            title = podcast.title.ifBlank { feed.title },
            publisher = podcast.publisher.ifBlank { feed.author.orEmpty() },
            imageUrl = podcast.imageUrl ?: feed.imageUrl,
            description = podcast.description.ifBlank { feed.description },
        )
        val episodes = feed.items.mapNotNull { episode(enriched, it) }
        if (episodes.isEmpty()) return null
        val show = PodcastShow(enriched, episodes.sortedByDescending { it.publishedMs })
        showCache[podcast.id] = now to show
        return show
    }

    private fun episode(podcast: Podcast, item: RssItem): PodcastEpisode? {
        val audio = item.enclosureUrl?.takeIf { it.startsWith("http") } ?: return null
        val type = item.enclosureType.orEmpty().lowercase()
        if (type.isNotEmpty() && !type.startsWith("audio") && !type.startsWith("video")) return null
        val key = (item.guid ?: audio).hashCode().toUInt().toString(16)
        return PodcastEpisode(
            id = podcast.id.hashCode().toUInt().toString(16) + "-" + key,
            podcastId = podcast.id,
            podcastTitle = podcast.title,
            podcastImage = podcast.imageUrl,
            title = item.title,
            description = item.summary,
            audioUrl = audio,
            isVideo = type.startsWith("video"),
            durationSec = parseDuration(item.duration),
            publishedMs = item.publishedMs,
        )
    }

    private fun parseDuration(raw: String?): Int {
        val parts = raw?.trim()?.split(":")?.map { it.trim().toDoubleOrNull() ?: return 0 } ?: return 0
        return when (parts.size) {
            1 -> parts[0].toInt()
            2 -> (parts[0] * 60 + parts[1]).toInt()
            3 -> (parts[0] * 3600 + parts[1] * 60 + parts[2]).toInt()
            else -> 0
        }.coerceAtLeast(0)
    }

    // ---- search ---------------------------------------------------------------------------------

    /** Catalogue shows matching [query] straight away. */
    fun searchCatalog(query: String): List<Podcast> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        val words = q.split(' ').filter { it.isNotBlank() }
        return _catalog.value.filter { show ->
            val hay = (show.title + " " + show.publisher + " " + show.league.orEmpty() + " " + show.tags.joinToString(" ")).lowercase()
            words.all { it in hay }
        }
    }

    /** Apple's podcast directory: any show, not only the catalogue's. */
    suspend fun searchDirectory(query: String, limit: Int = 25): List<Podcast> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val body = NewsHttp.get(
            "https://itunes.apple.com/search?term=${q.encodeURLParameter()}&media=podcast&entity=podcast&limit=$limit",
            "application/json",
        ) ?: return emptyList()
        return parseDirectory(body)
    }

    /** Shows about a team, found in Apple's directory and ranked by how directly they name it. */
    suspend fun teamShows(team: FollowedTeam): List<Podcast> {
        val key = team.key
        teamCache[key]?.let { return it }
        val name = TeamNames.full(team).trim()
        val terms = listOf("$name podcast", "Locked On $name")
        val found = LinkedHashMap<String, Pair<Podcast, Int>>()
        for (term in terms) {
            val body = NewsHttp.get(
                "https://itunes.apple.com/search?term=${term.encodeURLParameter()}&media=podcast&entity=podcast&limit=30",
                "application/json",
            ) ?: continue
            parseDirectory(body).forEach { show ->
                val score = teamScore(show, name)
                if (score >= 70) found[show.id] = show to score
            }
        }
        val ranked = found.values.sortedByDescending { it.second }.map { it.first }.take(10)
        if (ranked.isNotEmpty()) teamCache[key] = ranked
        return ranked
    }

    private fun teamScore(show: Podcast, team: String): Int {
        val text = (show.title + " " + show.publisher).lowercase()
        var score = if (team.lowercase() in text) 140 else 0
        val nickname = team.trim().split(' ').lastOrNull()?.lowercase()
        if (nickname != null && nickname.length >= 4 && nickname in text) score += 55
        if ("locked on" in text) score += 12
        return score
    }

    private fun parseDirectory(body: String): List<Podcast> {
        val results = (runCatching { json.parseToJsonElement(body).jsonObject["results"] }.getOrNull() as? JsonArray).orEmpty()
        return results.mapNotNull { element ->
            val o = element as? JsonObject ?: return@mapNotNull null
            val feed = o.str("feedUrl")?.takeIf { it.startsWith("http") } ?: return@mapNotNull null
            Podcast(
                id = feed,
                title = o.str("collectionName") ?: o.str("trackName") ?: return@mapNotNull null,
                publisher = o.str("artistName").orEmpty(),
                imageUrl = o.str("artworkUrl600") ?: o.str("artworkUrl100"),
                sport = null,
                league = o.str("primaryGenreName"),
            )
        }
    }

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    // ---- what you follow and played ------------------------------------------------------------

    fun isSubscribed(id: String): Boolean = _subscriptions.value.any { it.id == id }

    fun toggleSubscription(podcast: Podcast) {
        val current = _subscriptions.value
        val next = if (current.any { it.id == podcast.id }) current.filterNot { it.id == podcast.id } else listOf(podcast) + current
        _subscriptions.value = next
        write(Podcast.serializer(), "subs", next)
    }

    fun recordPlayed(episode: PodcastEpisode) {
        val next = (listOf(episode) + _recent.value.filterNot { it.id == episode.id }).take(MAX_RECENT)
        _recent.value = next
        write(PodcastEpisode.serializer(), "recent", next)
        val played = (_played.value + episode.id).toList().takeLast(600)
        _played.value = played.toSet()
        write(String.serializer(), "played", played)
    }

    private fun <T> readList(serializer: kotlinx.serialization.KSerializer<T>, key: String): List<T> =
        MusicStorage.getString(STORE, key)?.let { raw ->
            runCatching { json.decodeFromString(ListSerializer(serializer), raw) }.getOrNull()
        }.orEmpty()

    private fun <T> write(serializer: kotlinx.serialization.KSerializer<T>, key: String, value: List<T>) {
        MusicStorage.putString(STORE, key, json.encodeToString(ListSerializer(serializer), value))
    }

    fun relativeDate(publishedMs: Long): String = NewsText.relative(publishedMs, LiveTvClock.nowMs())
}
