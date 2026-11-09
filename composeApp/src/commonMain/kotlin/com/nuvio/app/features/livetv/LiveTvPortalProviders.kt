package com.nuvio.app.features.livetv

import com.nuvio.app.features.addons.httpGetTextWithHeaders
import com.nuvio.app.features.addons.httpRequestRaw
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
            headers = normalized.streamHeaders(),
            streamType = extension,
            epgId = item.string("epg_channel_id"),
        )
    }.distinctBy { it.streamUrl }
}

internal fun xtreamEpgUrl(settings: LiveTvXtreamSettings): String {
    val normalized = settings.normalized()
    return "${normalized.serverUrl}/xmltv.php?username=${normalized.username.encodeURLParameter()}&password=${normalized.password.encodeURLParameter()}"
}

/**
 * Many IPTV panels answer 403 (or an HTML page) to unknown clients. Try the well-known player User-Agents in turn,
 * starting with the one that worked last time, and remember the one that gets through.
 */
private val xtreamUserAgentCandidates = listOf(
    "IPTVSmartersPro",
    "VLC/3.0.20 LibVLC/3.0.20",
    "okhttp/4.12.0",
    "Nuvio/1.0",
    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36",
    "TiviMate/5.1.0 (Android 14)",
    "Lavf/60.16.100",
)
private var xtreamWorkingUserAgent: String? = null
private val httpStatusPattern = Regex("HTTP (\\d{3})")

/** The HTTP status carried by a failed request's message ("Request failed with HTTP 403"), if any. */
internal fun httpStatusOf(error: Throwable): Int? =
    httpStatusPattern.find(error.message.orEmpty())?.groupValues?.get(1)?.toIntOrNull()

/** A failed provider request that keeps what the panel said about why (status, reason header, page text). */
internal class ProviderHttpException(val status: Int, val panelReason: String?) :
    RuntimeException("Request failed with HTTP $status${panelReason?.let { ": $it" }.orEmpty()}")

/** A short, user-facing reason for a provider failure. */
internal fun describeProviderFailure(error: Throwable): String {
    val status = ((error as? ProviderHttpException) ?: (error.cause as? ProviderHttpException))?.status ?: httpStatusOf(error)
    val panelReason = ((error as? ProviderHttpException) ?: (error.cause as? ProviderHttpException))?.panelReason
    return when {
        panelReason != null && status != null -> "the provider answered HTTP $status: $panelReason"
        status == 401 || status == 403 -> "the provider refused the request (HTTP $status) without naming a reason. It usually means this network or device is not allowed for the line, or the login is wrong"
        status != null -> "the provider answered HTTP $status"
        error.message.orEmpty().contains("timed out", ignoreCase = true) -> "the provider did not answer in time"
        else -> error.message?.take(120)?.ifBlank { null } ?: "unexpected response from the provider"
    }
}

private fun LiveTvXtreamSettings.streamHeaders(): Map<String, String> =
    streamRequestHeaders + ("User-Agent" to (xtreamWorkingUserAgent ?: "IPTVSmartersPro"))

private suspend fun xtreamRequest(settings: LiveTvXtreamSettings, action: String): JsonElement {
    val url = "${settings.serverUrl}/player_api.php?username=${settings.username.encodeURLParameter()}&password=${settings.password.encodeURLParameter()}&action=${action.encodeURLParameter()}"
    val candidates = (listOfNotNull(xtreamWorkingUserAgent) + xtreamUserAgentCandidates).distinct()
    var lastError: Throwable? = null
    for (userAgent in candidates) {
        try {
            val response = httpRequestRaw(
                method = "GET",
                url = url,
                headers = playlistRequestHeaders + ("User-Agent" to userAgent),
                body = "",
                maxResponseBodyBytes = 64 * 1024 * 1024,
            )
            if (response.status !in 200..299) {
                val reason = response.headers["x-panel-reason-message"]?.takeIf(String::isNotBlank)
                    ?: response.body.replace(Regex("(?is)<(style|script)[^>]*>.*?</\\1>"), " ").replace(Regex("<[^>]*>"), " ").trim().take(120).takeIf(String::isNotBlank)
                throw ProviderHttpException(response.status, reason)
            }
            val element = portalJson.parseToJsonElement(response.body)
            xtreamWorkingUserAgent = userAgent
            return element
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            lastError = error
            val status = httpStatusOf(error)
            // Only a rejection (or a non-JSON page) is worth retrying with another client name.
            val retryable = status in setOf(401, 403, 406, 429) || (status == null && error is IllegalArgumentException)
            if (!retryable) break
        }
    }
    throw IllegalStateException(describeProviderFailure(lastError ?: IllegalStateException("no response")), lastError)
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
