package com.nuvio.app.features.sportsfocus

import com.nuvio.app.features.news.NewsHttp
import com.nuvio.app.features.news.NewsText
import io.ktor.http.encodeURLParameter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/** One result from ESPN's search: a player, a team or a story, with where to read more. */
data class SearchHit(
    val title: String,
    val subtitle: String?,
    val imageUrl: String?,
    val url: String?,
    val publishedMs: Long = 0L,
)

data class RemoteSearch(
    val players: List<SearchHit> = emptyList(),
    val teams: List<SearchHit> = emptyList(),
    val articles: List<SearchHit> = emptyList(),
) {
    val isEmpty: Boolean get() = players.isEmpty() && teams.isEmpty() && articles.isEmpty()
}

/** ESPN's public search: players (with their team and a headshot), teams and stories for any word. */
internal object SportsSearchData {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val cache = HashMap<String, RemoteSearch>()

    suspend fun remote(query: String): RemoteSearch {
        val q = query.trim()
        if (q.length < 2) return RemoteSearch()
        cache[q.lowercase()]?.let { return it }
        val body = NewsHttp.get(
            "https://site.web.api.espn.com/apis/search/v2?query=${q.encodeURLParameter()}&limit=8",
            "application/json",
        ) ?: return RemoteSearch()
        val results = runCatching { json.parseToJsonElement(body).jsonObject["results"] as? JsonArray }.getOrNull().orEmpty()
        fun hits(type: String): List<SearchHit> =
            results.firstOrNull { ((it as? JsonObject)?.get("type") as? JsonPrimitive)?.contentOrNull == type }
                ?.let { (it as JsonObject)["contents"] as? JsonArray }.orEmpty().mapNotNull { e ->
                    val o = e as? JsonObject ?: return@mapNotNull null
                    val title = o.str("displayName") ?: return@mapNotNull null
                    SearchHit(
                        title = title,
                        subtitle = listOfNotNull(o.str("subtitle"), o.str("description")?.takeIf { it != o.str("subtitle") }).joinToString(" · ").ifBlank { null },
                        imageUrl = (o["image"] as? JsonObject)?.str("default"),
                        url = (o["link"] as? JsonObject)?.str("web")?.replace("http://", "https://"),
                        publishedMs = NewsText.parseDateMs(o.str("date")),
                    )
                }
        return RemoteSearch(hits("player"), hits("team"), hits("article")).also { if (!it.isEmpty) cache[q.lowercase()] = it }
    }

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
}
