package com.nuvio.app.features.music.lastfm

import com.nuvio.app.features.music.data.MusicHttp
import com.nuvio.app.features.music.data.MusicStorage
import com.nuvio.app.features.music.data.md5Hex
import com.nuvio.app.features.music.data.model.Song
import com.nuvio.app.features.music.data.musicNowMs
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LastFmConfig(
    val apiKey: String = "",
    val secret: String = "",
    val sessionKey: String = "",
    val enabled: Boolean = false,
) {
    val configured: Boolean get() = apiKey.isNotBlank() && secret.isNotBlank() && sessionKey.isNotBlank()
}

/**
 * Last.fm scrobbling. The player calls [nowPlaying] when a track starts and [scrobble] once half of
 * it (or four minutes) has played; both sign the request with the standard MD5 api_sig.
 */
object MusicLastFm {
    private const val STORE = "lastfm"
    private const val ENDPOINT = "https://ws.audioscrobbler.com/2.0/"

    private val _config = MutableStateFlow(load())
    val config: StateFlow<LastFmConfig> = _config.asStateFlow()

    private fun load() = LastFmConfig(
        apiKey = MusicStorage.getString(STORE, "api_key").orEmpty(),
        secret = MusicStorage.getString(STORE, "secret").orEmpty(),
        sessionKey = MusicStorage.getString(STORE, "session_key").orEmpty(),
        enabled = MusicStorage.getString(STORE, "enabled") == "true",
    )

    /** Re-reads storage (Android calls this once its preferences are ready). */
    fun refresh() {
        _config.value = load()
    }

    fun save(apiKey: String, secret: String, sessionKey: String) {
        val updated = LastFmConfig(apiKey.trim(), secret.trim(), sessionKey.trim(), _config.value.enabled)
        _config.value = updated
        MusicStorage.putString(STORE, "api_key", updated.apiKey)
        MusicStorage.putString(STORE, "secret", updated.secret)
        MusicStorage.putString(STORE, "session_key", updated.sessionKey)
    }

    fun setEnabled(enabled: Boolean) {
        _config.value = _config.value.copy(enabled = enabled)
        MusicStorage.putString(STORE, "enabled", enabled.toString())
    }

    fun clear() {
        _config.value = LastFmConfig()
        MusicStorage.clear(STORE)
    }

    /** The scrobble point for a track: half its length, or four minutes, whichever is first. */
    fun scrobbleThresholdMs(durationMs: Long): Long? =
        if (durationMs < 30_000L) null else minOf(durationMs / 2L, 4 * 60_000L)

    suspend fun nowPlaying(song: Song): Result<Unit> = send("track.updateNowPlaying", song, null)

    suspend fun scrobble(song: Song): Result<Unit> = send("track.scrobble", song, musicNowMs() / 1000L)

    private suspend fun send(method: String, song: Song, timestamp: Long?): Result<Unit> {
        val config = _config.value
        if (!config.enabled || !config.configured) return Result.success(Unit)
        return runCatching {
            val params = linkedMapOf(
                "method" to method,
                "api_key" to config.apiKey,
                "sk" to config.sessionKey,
                "artist" to song.artist,
                "track" to song.title,
            )
            if (timestamp != null) params["timestamp"] = timestamp.toString()
            val signed = params + ("api_sig" to signature(params, config.secret)) + ("format" to "json")
            val form = signed.entries.joinToString("&") { (k, v) -> "${k.encodeURLParameter()}=${v.encodeURLParameter()}" }
            val response = MusicHttp.request(
                "POST",
                ENDPOINT,
                mapOf("User-Agent" to "BannerTV/1.0", "Content-Type" to "application/x-www-form-urlencoded"),
                form,
            )
            require(response.isSuccessful) { "$method failed: HTTP ${response.status}" }
            require(!response.body.contains("\"error\"")) { "$method error: ${response.body.take(200)}" }
        }
    }

    internal fun signature(params: Map<String, String>, secret: String): String {
        val raw = params.entries.sortedBy { it.key }.joinToString("") { (k, v) -> k + v } + secret
        return md5Hex(raw)
    }
}
