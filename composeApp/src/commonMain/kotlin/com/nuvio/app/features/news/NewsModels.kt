package com.nuvio.app.features.news

import com.nuvio.app.features.sports.FollowedTeam
import com.nuvio.app.features.sports.SportsFollows
import com.nuvio.app.features.sports.SportsRepository

/** A sports story from any publisher. */
data class NewsArticle(
    val id: String,
    val headline: String,
    val summary: String,
    val imageUrl: String?,
    val url: String?,
    val publishedMs: Long,
    val leagueId: String?,
    val leagueShort: String?,
    val publisher: String = "ESPN",
    val author: String? = null,
    /** A `Sport` enum name, so stories from sources that name no league can still be grouped. */
    val sportKey: String? = null,
)

data class NewsState(
    val loading: Boolean = false,
    val error: String? = null,
    val articles: List<NewsArticle> = emptyList(),
)

/** A filter chip on the Discover page. */
internal data class NewsTopic(val key: String, val label: String, val matches: (NewsArticle) -> Boolean)

internal object NewsTopics {
    private fun league(key: String, label: String, vararg ids: String) =
        NewsTopic(key, label) { it.leagueId in ids }

    private fun sport(key: String, label: String, vararg sports: String) =
        NewsTopic(key, label) { it.sportKey in sports }

    val all: List<NewsTopic> = listOf(
        league("nhl", "NHL", "hockey/nhl"),
        league("nfl", "NFL", "football/nfl"),
        league("nba", "NBA", "basketball/nba", "basketball/wnba"),
        league("mlb", "MLB", "baseball/mlb"),
        sport("soccer", "Soccer", "FOOTBALL"),
        NewsTopic("college", "College") {
            it.leagueId in setOf("football/college-football", "basketball/mens-college-basketball", "basketball/womens-college-basketball")
        },
        sport("racing", "Racing", "RACING"),
        sport("golf", "Golf", "GOLF"),
        sport("tennis", "Tennis", "TENNIS"),
        sport("combat", "Combat", "FIGHTING", "WRESTLING"),
    )

    /** Only the topics that have a story right now, so no chip leads to an empty page. */
    fun available(articles: List<NewsArticle>): List<NewsTopic> = all.filter { topic -> articles.any(topic.matches) }
}

/** A followed team's full name ("Ottawa Senators") when a game of theirs is loaded, else the short name it was followed under. */
internal object TeamNames {
    fun full(team: FollowedTeam): String {
        val info = SportsRepository.known.value.asSequence()
            .flatMap { it.sides.asSequence() }
            .map { it.team }
            .firstOrNull { it.id == team.teamId && it.leagueId == team.leagueId }
        return info?.fullName?.takeIf { it.isNotBlank() } ?: team.name
    }
}

internal object NewsRanking {
    private val TRACKING = setOf("utm_source", "utm_medium", "utm_campaign", "utm_content", "utm_term", "cmpid", "ref", "at_medium", "at_campaign", "fbclid", "gclid")
    private val NON_WORD = Regex("[^\\p{L}\\p{N} ]")
    private val SPACES = Regex(" +")

    fun urlKey(url: String?): String {
        if (url.isNullOrBlank()) return ""
        val noScheme = url.substringAfter("://", url).substringBefore('#')
        val host = noScheme.substringBefore('/').lowercase().removePrefix("www.")
        val rest = noScheme.substringAfter('/', "")
        val path = rest.substringBefore('?').trimEnd('/')
        val query = rest.substringAfter('?', "").split('&')
            .filter { it.isNotEmpty() && it.substringBefore('=').lowercase() !in TRACKING }
            .sorted().joinToString("&")
        return "$host/$path" + if (query.isNotEmpty()) "?$query" else ""
    }

    fun titleKey(title: String): String =
        SPACES.replace(NON_WORD.replace(title.lowercase(), ""), " ").trim()

    /** One of each story: the same address, or the same headline, only once. The first one seen wins. */
    fun dedupe(articles: List<NewsArticle>): List<NewsArticle> {
        val urls = HashSet<String>()
        val titles = HashSet<String>()
        val out = ArrayList<NewsArticle>(articles.size)
        for (a in articles) {
            val u = urlKey(a.url)
            val t = titleKey(a.headline)
            if (u.isNotEmpty() && !urls.add(u)) continue
            if (t.isNotEmpty() && !titles.add(t)) continue
            out.add(a)
        }
        return out
    }

    /** Newest first; stories with no date go last. Same-minute ties keep a stable order. */
    fun newestFirst(articles: List<NewsArticle>): List<NewsArticle> =
        articles.sortedWith(compareByDescending<NewsArticle> { it.publishedMs > 0 }.thenByDescending { it.publishedMs })

    private val GENERIC = setOf("city", "club", "united", "state", "college", "fc", "sc")

    private fun phrases(team: FollowedTeam): List<String> {
        val full = TeamNames.full(team)
        val out = LinkedHashSet<String>()
        out += full.lowercase()
        out += team.name.lowercase()
        val nickname = full.trim().split(' ').lastOrNull { it.isNotBlank() }?.lowercase()
        if (nickname != null && nickname.length >= 4 && nickname !in GENERIC) out += nickname
        return out.filter { it.length >= 3 }
    }

    fun mentions(article: NewsArticle, teams: List<FollowedTeam>): Boolean {
        if (teams.isEmpty()) return false
        val text = " " + titleKey(article.headline + " " + article.summary) + " "
        return teams.any { team ->
            (article.leagueId == null || article.leagueId == team.leagueId || team.leagueId.startsWith("soccer/")) &&
                phrases(team).any { p -> " ${titleKey(p)} " in text }
        }
    }

    /** Stories about what you follow first, in their own order, then everything else. */
    fun forYou(articles: List<NewsArticle>, follows: SportsFollows = SportsRepository.follows.value): List<NewsArticle> {
        if (follows.teams.isEmpty() && follows.leagues.isEmpty()) return articles
        val followedLeagues = SportsRepository.followedLeagueIds(follows)
        val personal = articles.filter { a -> mentions(a, follows.teams) || (a.leagueId != null && a.leagueId in followedLeagues) }
        val ids = personal.mapTo(HashSet()) { it.id }
        return personal + articles.filter { it.id !in ids }
    }
}
