package com.nuvio.app.features.livetv

import com.nuvio.app.features.addons.httpGetTextWithHeaders
import io.ktor.http.encodeURLParameter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

private const val STALKER_PLAYLIST_ID = "provider:stalker"
private const val XTREAM_PLAYLIST_ID = "provider:xtream"

private val portalJson = Json { ignoreUnknownKeys = true; isLenient = true }
private val playlistRequestHeaders = mapOf("Accept" to "application/json, text/plain, */*", "User-Agent" to "Nuvio/1.0")
private val streamRequestHeaders = mapOf("User-Agent" to "Mozilla/5.0", "Accept" to "*/*")

internal suspend fun fetchXtreamChannels(settings: LiveTvXtreamSettings): List<LiveTvChannel> {
    val normalized = settings.normalized()
    val categories = xtreamRequest(normalized, "get_live_categories").arrayOrEmpty().mapNotNull { element ->
        val item = element as? JsonObject ?: return@mapNotNull null
        val id = item.string("category_id") ?: item.string("id") ?: return@mapNotNull null
        id to (item.string("category_name") ?: item.string("name") ?: id)
    }.toMap()
    return xtreamRequest(normalized, "get_live_streams").arrayOrEmpty().mapIndexedNotNull { index, element ->
        val item = element as? JsonObject ?: return@mapIndexedNotNull null
        val name = item.string("name") ?: return@mapIndexedNotNull null
        val streamId = item.string("stream_id") ?: item.string("id") ?: return@mapIndexedNotNull null
        val extension = item.string("container_extension")?.trim()?.trimStart('.')?.ifBlank { null } ?: "ts"
        val directSource = item.string("direct_source")?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
        LiveTvChannel(
            id = "xtream:$streamId",
            name = name,
            streamUrl = directSource ?: normalized.liveStreamUrl(streamId, extension),
            logoUrl = item.string("stream_icon") ?: item.string("logo"),
            group = item.string("category_id")?.let(categories::get),
            playlistId = XTREAM_PLAYLIST_ID,
            playlistName = "Xtream",
            headers = streamRequestHeaders,
            streamType = extension,
            epgId = item.string("epg_channel_id"),
        )
    }.distinctBy { it.streamUrl }
}

internal fun xtreamEpgUrl(settings: LiveTvXtreamSettings): String {
    val normalized = settings.normalized()
    return "${normalized.serverUrl}/xmltv.php?username=${normalized.username.encodeURLParameter()}&password=${normalized.password.encodeURLParameter()}"
}

private suspend fun xtreamRequest(
    settings: LiveTvXtreamSettings,
    action: String,
    params: Map<String, String> = emptyMap(),
): JsonElement {
    val extra = params.entries.joinToString("") { (key, value) -> "&${key.encodeURLParameter()}=${value.encodeURLParameter()}" }
    val url = "${settings.serverUrl}/player_api.php?username=${settings.username.encodeURLParameter()}&password=${settings.password.encodeURLParameter()}&action=${action.encodeURLParameter()}$extra"
    return portalJson.parseToJsonElement(httpGetTextWithHeaders(url, playlistRequestHeaders))
}

private data class StalkerSession(val settings: LiveTvStalkerSettings, val token: String)
private var cachedStalkerSession: StalkerSession? = null

internal suspend fun fetchStalkerChannels(settings: LiveTvStalkerSettings): List<LiveTvChannel> {
    val session = stalkerSession(settings.normalized())
    val genres = stalkerRequest(session.settings, session.token, "itv", "get_genres")
        .js().array("data").mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val id = item.string("id") ?: item.string("alias") ?: return@mapNotNull null
            id to (item.string("title") ?: item.string("name") ?: id)
        }.toMap()
    val channels = mutableListOf<LiveTvChannel>()
    for (page in 1..20) {
        val data = stalkerRequest(session.settings, session.token, "itv", "get_ordered_list", mapOf("p" to page.toString()))
            .js().array("data")
        if (data.isEmpty()) break
        data.forEachIndexed { index, element ->
            val item = element as? JsonObject ?: return@forEachIndexed
            val command = item.string("cmd") ?: item.string("mc_cmd") ?: item.string("url") ?: return@forEachIndexed
            val url = command.playableUrl()
            if (url.isBlank()) return@forEachIndexed
            LiveTvChannel(
                id = "stalker:${item.string("id") ?: "$page:$index"}",
                name = item.string("name") ?: item.string("title") ?: return@forEachIndexed,
                streamUrl = url,
                logoUrl = item.string("logo") ?: item.string("logo_url"),
                group = (item.string("tv_genre_id") ?: item.string("genre_id"))?.let(genres::get),
                playlistId = STALKER_PLAYLIST_ID,
                playlistName = "Stalker Portal",
                headers = stalkerHeaders(session.settings, session.token),
                stalkerCommand = command,
                epgId = item.string("xmltv_id"),
            ).also(channels::add)
        }
    }
    return channels.distinctBy { it.id }
}

internal suspend fun fetchStalkerEpgGuide(
    settings: LiveTvStalkerSettings,
    windowStartMillis: Long,
    windowEndMillis: Long,
): LiveTvEpgGuide {
    val session = stalkerSession(settings.normalized())
    val data = stalkerRequest(session.settings, session.token, "itv", "get_epg_info", mapOf("period" to "48"))
        .js()["data"] as? JsonObject ?: return LiveTvEpgGuide()
    val programs = data.mapNotNull { (channelId, element) ->
        val items = (element as? JsonArray)?.mapNotNull { entry ->
            val item = entry as? JsonObject ?: return@mapNotNull null
            val title = item.string("name") ?: return@mapNotNull null
            val start = item.string("start_timestamp")?.toLongOrNull()?.times(1000L) ?: return@mapNotNull null
            val stop = item.string("stop_timestamp")?.toLongOrNull()?.times(1000L) ?: return@mapNotNull null
            LiveTvEpgProgram(
                title = title,
                startMillis = start,
                endMillis = stop,
                description = item.string("descr"),
                category = item.string("category"),
            )
        }.orEmpty()
            .filter { it.endMillis >= windowStartMillis && it.startMillis <= windowEndMillis }
            .sortedBy { it.startMillis }
        if (items.isEmpty()) null else "stalker:$channelId" to items
    }.toMap()
    return LiveTvEpgGuide(programsByChannelId = programs)
}

internal suspend fun preparePortalChannelForPlayback(channel: LiveTvChannel, settings: LiveTvStalkerSettings): LiveTvChannel {
    val command = channel.stalkerCommand?.takeIf(String::isNotBlank) ?: return channel
    val session = stalkerSession(settings.normalized())
    val result = stalkerRequest(session.settings, session.token, "itv", "create_link", mapOf("cmd" to command)).js()
    val url = (result.string("cmd") ?: result.string("url") ?: result.string("stream_url"))?.playableUrl()
    return channel.copy(
        streamUrl = url?.takeIf(String::isNotBlank) ?: channel.streamUrl,
        headers = channel.headers + stalkerHeaders(session.settings, session.token),
    )
}

private suspend fun stalkerSession(settings: LiveTvStalkerSettings): StalkerSession {
    cachedStalkerSession?.takeIf { it.settings == settings && it.token.isNotBlank() }?.let { return it }
    val token = stalkerRequest(settings, null, "stb", "handshake").js().string("token").orEmpty()
    require(token.isNotBlank()) { "Stalker Portal did not return an authorization token." }
    return StalkerSession(settings, token).also { cachedStalkerSession = it }
}

private suspend fun stalkerRequest(
    settings: LiveTvStalkerSettings,
    token: String?,
    type: String,
    action: String,
    extra: Map<String, String> = emptyMap(),
): JsonObject {
    val parameters = buildMap {
        put("type", type); put("action", action); put("JsHttpRequest", "1-xml")
        if (!token.isNullOrBlank()) put("token", token)
        if (settings.username.isNotBlank()) put("login", settings.username)
        if (settings.password.isNotBlank()) put("password", settings.password)
        putAll(extra)
    }
    val endpoint = settings.portalEndpoint()
    val query = parameters.entries.joinToString("&", prefix = if ('?' in endpoint) "&" else "?") { (key, value) ->
        "${key.encodeURLParameter()}=${value.encodeURLParameter()}"
    }
    return portalJson.parseToJsonElement(httpGetTextWithHeaders(endpoint + query, stalkerHeaders(settings, token))).jsonObject
}

private fun stalkerHeaders(settings: LiveTvStalkerSettings, token: String?): Map<String, String> = buildMap {
    put("User-Agent", "Mozilla/5.0 (QtEmbedded; U; Linux; MAG254; en) AppleWebKit/533.3 MAG200 stbapp ver: 4 rev: 2721 Mobile Safari/533.3")
    put("X-User-Agent", "Model: MAG254; Link: Ethernet")
    put("Referer", settings.portalBaseUrl())
    put("Cookie", "mac=${settings.macAddress}; stb_lang=en; timezone=Europe%2FIstanbul")
    if (!token.isNullOrBlank()) put("Authorization", "Bearer $token")
}

private fun LiveTvXtreamSettings.normalized() = copy(
    serverUrl = serverUrl.trim().trimEnd('/').substringBefore("/player_api.php").trimEnd('/'),
    username = username.trim(), password = password.trim(),
)
private fun LiveTvXtreamSettings.liveStreamUrl(id: String, extension: String) =
    "$serverUrl/live/${username.encodeURLParameter()}/${password.encodeURLParameter()}/${id.encodeURLParameter()}.${extension.encodeURLParameter()}"
private fun LiveTvStalkerSettings.normalized() = copy(
    portalUrl = portalUrl.trim().trimEnd('/'), macAddress = macAddress.trim().uppercase(),
    username = username.trim(), password = password.trim(),
)
private fun LiveTvStalkerSettings.portalEndpoint(): String {
    val normalized = portalUrl.trim().trimEnd('/')
    return when {
        normalized.contains("portal.php", ignoreCase = true) -> normalized
        normalized.contains("server/load.php", ignoreCase = true) -> normalized
        normalized.endsWith("/c", ignoreCase = true) -> normalized.dropLast(2) + "/portal.php"
        else -> "$normalized/portal.php"
    }
}
private fun LiveTvStalkerSettings.portalBaseUrl(): String {
    val base = portalUrl.trim()
        .substringBefore("/portal.php")
        .substringBefore("/server/load.php")
        .trimEnd('/')
        .removeSuffix("/c")
    return "$base/c/"
}
private fun String.playableUrl() = trim().removePrefix("ffmpeg ").removePrefix("auto ").substringBefore(' ').trim()
private fun JsonObject.js() = (this["js"] as? JsonObject) ?: this
private fun JsonObject.string(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotBlank)
private fun JsonObject.array(key: String) = (this[key] as? JsonArray)?.toList().orEmpty()
private fun JsonElement.arrayOrEmpty() = (this as? JsonArray)?.toList() ?: (this as? JsonObject)?.array("data").orEmpty()


internal suspend fun fetchXtreamVodCatalog(
    settings: LiveTvXtreamSettings,
    kind: LiveTvVodKind,
): LiveTvVodCatalog {
    val normalized = settings.normalized()
    val (categoryAction, listAction) = when (kind) {
        LiveTvVodKind.Movies -> "get_vod_categories" to "get_vod_streams"
        LiveTvVodKind.Series -> "get_series_categories" to "get_series"
    }
    val categories = xtreamRequest(normalized, categoryAction).arrayOrEmpty().mapNotNull { element ->
        val item = element as? JsonObject ?: return@mapNotNull null
        val id = item.string("category_id") ?: return@mapNotNull null
        LiveTvVodCategory(id = id, name = item.string("category_name") ?: id)
    }
    val items = xtreamRequest(normalized, listAction).arrayOrEmpty().mapNotNull { element ->
        val item = element as? JsonObject ?: return@mapNotNull null
        val name = item.string("name") ?: item.string("title") ?: return@mapNotNull null
        val remoteId = when (kind) {
            LiveTvVodKind.Movies -> item.string("stream_id")
            LiveTvVodKind.Series -> item.string("series_id")
        } ?: return@mapNotNull null
        LiveTvVodItem(
            id = "xtream-${kind.name.lowercase()}:$remoteId",
            remoteId = remoteId,
            kind = kind,
            name = name,
            posterUrl = item.string("stream_icon") ?: item.string("cover"),
            categoryId = item.string("category_id"),
            rating = item.string("rating")?.toDoubleOrNull()?.takeIf { it > 0.0 },
            plot = item.string("plot"),
            year = (item.string("year") ?: item.string("releaseDate") ?: item.string("release_date"))
                ?.take(4)
                ?.takeIf { year -> year.all(Char::isDigit) },
            extension = item.string("container_extension")?.trim()?.trimStart('.')?.ifBlank { null },
            addedSeconds = item.string("added")?.toLongOrNull() ?: item.string("last_modified")?.toLongOrNull(),
        )
    }.distinctBy { it.id }
    val usedCategoryIds = items.mapNotNull { it.categoryId }.toSet()
    return LiveTvVodCatalog(
        categories = categories.filter { it.id in usedCategoryIds },
        items = items,
    )
}

internal suspend fun fetchXtreamSeriesEpisodes(
    settings: LiveTvXtreamSettings,
    seriesId: String,
): List<LiveTvVodEpisode> {
    val normalized = settings.normalized()
    val root = xtreamRequest(normalized, "get_series_info", mapOf("series_id" to seriesId)) as? JsonObject
        ?: return emptyList()
    val episodes = root["episodes"]
    val groups: List<Pair<String?, JsonElement>> = when (episodes) {
        is JsonObject -> episodes.entries.map { (season, value) -> season to value }
        is JsonArray -> episodes.map { null to it }
        else -> emptyList()
    }
    return groups.flatMap { (seasonKey, value) ->
        val list = (value as? JsonArray)?.toList() ?: listOf(value)
        list.mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val id = item.string("id") ?: return@mapNotNull null
            val info = item["info"] as? JsonObject
            val season = item.string("season")?.toIntOrNull() ?: seasonKey?.toIntOrNull() ?: 1
            val number = item.string("episode_num")?.toIntOrNull() ?: 0
            val extension = item.string("container_extension")?.trim()?.trimStart('.')?.ifBlank { null } ?: "mp4"
            LiveTvVodEpisode(
                id = id,
                season = season,
                episodeNumber = number,
                title = item.string("title")?.takeIf(String::isNotBlank) ?: "Episode $number",
                plot = info?.string("plot"),
                imageUrl = info?.string("movie_image"),
                durationText = info?.string("duration"),
                streamUrl = item.string("direct_source")?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
                    ?: normalized.seriesStreamUrl(id, extension),
                extension = extension,
            )
        }
    }.sortedWith(compareBy({ it.season }, { it.episodeNumber }))
}

internal fun xtreamMovieStreamUrl(settings: LiveTvXtreamSettings, item: LiveTvVodItem): String {
    val normalized = settings.normalized()
    val extension = item.extension ?: "mp4"
    return "${normalized.serverUrl}/movie/${normalized.username.encodeURLParameter()}/${normalized.password.encodeURLParameter()}/${item.remoteId.encodeURLParameter()}.${extension.encodeURLParameter()}"
}

internal val liveTvVodRequestHeaders: Map<String, String> get() = streamRequestHeaders

private fun LiveTvXtreamSettings.seriesStreamUrl(id: String, extension: String) =
    "$serverUrl/series/${username.encodeURLParameter()}/${password.encodeURLParameter()}/${id.encodeURLParameter()}.${extension.encodeURLParameter()}"
