package com.nuvio.app.features.music.playback

import com.nuvio.app.features.music.data.MusicHttp
import com.nuvio.app.features.music.data.MusicStorage
import com.nuvio.app.features.music.data.SyncMap
import com.nuvio.app.features.music.data.model.Song
import com.nuvio.app.features.music.data.musicNowMs
import com.nuvio.app.features.music.webdav.MusicWebDav
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

data class ResolvedStream(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val mimeType: String? = null,
    val client: String? = null,
    val lookedUpAt: Long = musicNowMs(),
) {
    val isFresh: Boolean get() = musicNowMs() - lookedUpAt < CACHE_MS
    companion object {
        const val CACHE_MS = 20 * 60 * 1000L
    }
}

enum class StreamSource(val label: String) {
    YouTube("YouTube Direct"),
    TorBox("TorBox CDN"),
    Both("Both (fallback)"),
    ;
    companion object {
        private const val STORE = "stream_source"
        private const val KEY = "mode"
        fun current(): StreamSource {
            val raw = MusicStorage.getString(STORE, KEY) ?: return Both
            return entries.firstOrNull { it.name == raw } ?: Both
        }
        fun set(mode: StreamSource) = MusicStorage.putString(STORE, KEY, mode.name)
    }
}

/**
 * Turns a [Song] into something the platform player can open.
 *
 * Server/local tracks carry an address already. Catalogue tracks are resolved through YouTube's
 * player endpoint, trying multiple clients (up to [MAX_CLIENT_SWITCHES] fallbacks) in order
 * of reliability, matching the upstream Nuvio-One approach. Source order: HLS manifest first
 * (AVPlayer native), then direct audio URL (itag 140 AAC preferred).
 */
object MusicStreamResolver {
    private val json = Json { ignoreUnknownKeys = true }
    private val cache = SyncMap<String, ResolvedStream>()
    private const val MAX_CLIENT_SWITCHES = 5
    private const val MAX_CACHE_ENTRIES = 64

    private data class Client(
        val name: String,
        val version: String,
        val numericId: String,
        val userAgent: String,
        val extra: Map<String, String>,
        val sdk: Int? = null,
        val thirdPartyEmbedUrl: String? = null,
    )

    private val clients = listOf(
        Client(
            name = "ANDROID_MUSIC",
            version = "7.27.52",
            numericId = "21",
            userAgent = "com.google.android.apps.youtube.music/7.27.52 (Linux; U; Android 14; Pixel 8 Build/AP2A.240805.005) gzip",
            extra = mapOf("deviceMake" to "Google", "deviceModel" to "Pixel 8", "osName" to "Android", "osVersion" to "14"),
            sdk = 34,
        ),
        Client(
            name = "ANDROID",
            version = "19.44.38",
            numericId = "3",
            userAgent = "com.google.android.youtube/19.44.38 (Linux; U; Android 14; Pixel 8 Build/AP2A.240805.005) gzip",
            extra = mapOf("deviceMake" to "Google", "deviceModel" to "Pixel 8", "osName" to "Android", "osVersion" to "14"),
            sdk = 34,
        ),
        Client(
            name = "IOS",
            version = "20.10.4",
            numericId = "5",
            userAgent = "com.google.ios.youtube/20.10.4 (iPhone16,1; U; CPU iOS 18_3_2 like Mac OS X;)",
            extra = mapOf("deviceMake" to "Apple", "deviceModel" to "iPhone 15 Pro", "osName" to "iOS", "osVersion" to "18.3.2"),
        ),
        Client(
            name = "TVHTML5_SIMPLY_EMBEDDED_PLAYER",
            version = "2.0",
            numericId = "85",
            userAgent = "Mozilla/5.0 (CrKey armv7l 1.5.44178) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.6167.160 Safari/537.36",
            extra = mapOf("deviceMake" to "Google", "deviceModel" to "Chromecast", "osName" to "SmartTV", "osVersion" to "2.0"),
            thirdPartyEmbedUrl = "https://www.youtube.com",
        ),
        Client(
            name = "TVHTML5",
            version = "7.20250219.14.00",
            numericId = "7",
            userAgent = "Mozilla/5.0 (SmartTV; Linux; Tizen 7.0) AppleWebKit/537.36 (KHTML, like Gecko) SamsungBrowser/5.0 Chrome/121.0.6167.160 Safari/537.36",
            extra = mapOf("deviceMake" to "Samsung", "deviceModel" to "SmartTV", "osName" to "Tizen", "osVersion" to "7.0"),
        ),
        Client(
            name = "ANDROID_VR",
            version = "1.60.19",
            numericId = "28",
            userAgent = "com.google.android.apps.youtube.vr.oculus/1.60.19 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip",
            extra = mapOf("deviceMake" to "Oculus", "deviceModel" to "Quest 3", "osName" to "Android", "osVersion" to "12L"),
            sdk = 32,
        ),
    )

    class SignInRequiredException(message: String) : Exception(message)

    private sealed interface Outcome {
        data class Stream(val resolved: ResolvedStream) : Outcome
        data class NeedsSignIn(val reason: String?) : Outcome
        data class Unplayable(val reason: String?) : Outcome
        data object NoStream : Outcome
    }

    suspend fun resolve(song: Song): Result<ResolvedStream> {
        song.localUri?.takeIf { it.isNotBlank() }?.let { uri ->
            return Result.success(ResolvedStream(uri, MusicWebDav.headersFor(uri)))
        }

        return when (StreamSource.current()) {
            StreamSource.YouTube -> resolveYouTube(song)
            StreamSource.TorBox -> resolveTorBox(song)
            StreamSource.Both -> resolveBoth(song)
        }
    }

    suspend fun warm(song: Song) {
        if (song.localUri != null) return
        if (cache[song.videoId]?.isFresh == true) return
        try {
            resolve(song)
        } catch (_: Throwable) {}
    }

    private suspend fun resolveYouTube(song: Song): Result<ResolvedStream> {
        val id = song.videoId
        cache[id]?.takeIf { it.isFresh }?.let { return Result.success(it) }
        return try {
            val stream = resolveFromYouTube(id)
                ?: return Result.failure(IllegalStateException("This track can't be played right now."))
            if (cache.size >= MAX_CACHE_ENTRIES) cache.clear()
            cache[id] = stream
            Result.success(stream)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            Result.failure(error)
        }
    }

    private suspend fun resolveTorBox(song: Song): Result<ResolvedStream> {
        if (!TorBoxStreamResolver.isConfigured) {
            return Result.failure(IllegalStateException("TorBox API key not set. Add it in Music Settings."))
        }
        return TorBoxStreamResolver.resolve(song)
    }

    private suspend fun resolveBoth(song: Song): Result<ResolvedStream> {
        val ytResult = resolveYouTube(song)
        if (ytResult.isSuccess) return ytResult

        if (TorBoxStreamResolver.isConfigured) {
            val tbResult = TorBoxStreamResolver.resolve(song)
            if (tbResult.isSuccess) return tbResult
        }

        return ytResult
    }

    fun clearCache() = cache.clear()

    private suspend fun resolveFromYouTube(videoId: String): ResolvedStream? {
        var signInCount = 0
        var lastReason: String? = null

        for (attempt in 0..MAX_CLIENT_SWITCHES) {
            val client = clients[attempt.coerceAtMost(clients.lastIndex)]
            val outcome = tryClient(videoId, client)
            when (outcome) {
                is Outcome.Stream -> return outcome.resolved
                is Outcome.NeedsSignIn -> {
                    signInCount++
                    lastReason = outcome.reason
                }
                is Outcome.Unplayable -> lastReason = outcome.reason
                is Outcome.NoStream -> {}
            }
        }

        if (signInCount > 0) {
            throw SignInRequiredException(
                "YouTube is asking for a sign-in before it will play this on this network" +
                    (lastReason?.let { " (\"$it\")" } ?: "") +
                    ". Songs from a Navidrome, WebDAV or local source still play.",
            )
        }

        lastReason?.let { throw IllegalStateException(friendlyReason(it)) }
        return null
    }

    private suspend fun tryClient(videoId: String, client: Client): Outcome {
        val body = buildJsonObject {
            put("context", buildJsonObject {
                put("client", buildJsonObject {
                    put("clientName", client.name)
                    put("clientVersion", client.version)
                    client.extra.forEach { (k, v) -> put(k, v) }
                    client.sdk?.let { put("androidSdkVersion", it) }
                    put("hl", "en")
                    put("gl", "US")
                })
                if (client.thirdPartyEmbedUrl != null) {
                    put("thirdParty", buildJsonObject {
                        put("embedUrl", client.thirdPartyEmbedUrl)
                    })
                }
            })
            put("videoId", videoId)
            put("contentCheckOk", true)
            put("racyCheckOk", true)
        }.toString()

        val response = try {
            MusicHttp.request(
                "POST",
                "https://www.youtube.com/youtubei/v1/player?prettyPrint=false",
                mapOf(
                    "Content-Type" to "application/json",
                    "User-Agent" to client.userAgent,
                    "X-YouTube-Client-Name" to client.numericId,
                    "X-YouTube-Client-Version" to client.version,
                ),
                body,
            )
        } catch (_: Throwable) {
            return Outcome.NoStream
        }

        if (!response.isSuccessful) return Outcome.NoStream

        val root = try {
            json.parseToJsonElement(response.body).jsonObject
        } catch (_: Throwable) {
            return Outcome.NoStream
        }

        val playability = root["playabilityStatus"]?.jsonObject
        val status = playability?.get("status")?.let { (it as? JsonPrimitive)?.contentOrNull }
        if (status != null && status != "OK") {
            val reason = playability["reason"]?.let { (it as? JsonPrimitive)?.contentOrNull }
            return when {
                status == "LOGIN_REQUIRED" -> Outcome.NeedsSignIn(reason)
                else -> Outcome.Unplayable(reason)
            }
        }

        val streaming = root["streamingData"] as? JsonObject ?: return Outcome.NoStream
        val expiresIn = (streaming["expiresInSeconds"] as? JsonPrimitive)?.contentOrNull?.toLongOrNull() ?: 6 * 3600L
        val expiresAt = musicNowMs() + minOf(expiresIn * 1000L, ResolvedStream.CACHE_MS)

        // Prefer HLS (AVPlayer native) over direct URLs
        val hlsUrl = (streaming["hlsManifestUrl"] as? JsonPrimitive)?.contentOrNull
        if (hlsUrl != null) {
            return Outcome.Stream(
                ResolvedStream(hlsUrl, emptyMap(), "application/x-mpegURL", client.name, expiresAt)
            )
        }

        // Direct audio: itag 140 (AAC 128k) preferred, then highest bitrate
        val formats = (streaming["adaptiveFormats"] as? JsonArray)?.map { it.jsonObject }.orEmpty()
        val audioFormats = formats.filter { f ->
            val mime = (f["mimeType"] as? JsonPrimitive)?.contentOrNull.orEmpty()
            mime.startsWith("audio/") && f["url"] != null
        }
        val itag140 = audioFormats.firstOrNull { (it["itag"] as? JsonPrimitive)?.intOrNull == 140 }
        val bestAudio = itag140
            ?: audioFormats.maxByOrNull { (it["bitrate"] as? JsonPrimitive)?.doubleOrNull ?: 0.0 }

        if (bestAudio != null) {
            val url = (bestAudio["url"] as? JsonPrimitive)?.contentOrNull
            if (url != null) {
                val mime = (bestAudio["mimeType"] as? JsonPrimitive)?.contentOrNull
                    ?.substringBefore(';') ?: "audio/mp4"
                return Outcome.Stream(
                    ResolvedStream(url, emptyMap(), mime, client.name, expiresAt)
                )
            }
        }

        return Outcome.NoStream
    }

    private fun friendlyReason(reason: String?): String {
        val r = reason.orEmpty().lowercase()
        return when {
            "age" in r || "confirm your age" in r || "inappropriate" in r ->
                "This track is age-restricted and can't be played here."
            "country" in r || "region" in r || "location" in r ->
                "This track isn't available in your country."
            "private" in r -> "This track is private."
            "copyright" in r -> "This track was removed over a copyright claim."
            "removed" in r || "terminated" in r || "no longer available" in r || "unavailable" in r ->
                "This track has been removed or isn't available."
            "sign in" in r -> "Sign in to play this track."
            reason.isNullOrBlank() -> "This track can't be played."
            else -> reason
        }
    }
}
