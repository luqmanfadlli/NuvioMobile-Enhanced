package com.nuvio.app.features.music.navidrome

import com.nuvio.app.features.music.data.MusicHttp
import com.nuvio.app.features.music.data.MusicStorage
import com.nuvio.app.features.music.data.md5Hex
import com.nuvio.app.features.music.data.model.Song
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlin.random.Random

/** A linked Navidrome / Subsonic server. Only the derived token and salt are kept, never the password. */
data class NavidromeConfig(
    val url: String = "",
    val username: String = "",
    val token: String = "",
    val salt: String = "",
) {
    val configured: Boolean
        get() = url.isNotBlank() && username.isNotBlank() && token.isNotBlank() && salt.isNotBlank()
}

data class NavidromeAlbum(
    val id: String,
    val name: String,
    val artist: String,
    val coverArt: String?,
    val songCount: Int,
)

data class NavidromePlaylist(
    val id: String,
    val name: String,
    val owner: String?,
    val songCount: Int,
    val coverArt: String?,
)

data class NavidromeAlbumDetail(val album: NavidromeAlbum, val songs: List<Song>)

data class NavidromePlaylistDetail(val playlist: NavidromePlaylist, val songs: List<Song>)

/**
 * Subsonic-API client for Navidrome (and any Subsonic-compatible server).
 *
 * Auth is the Subsonic token scheme: `t = md5(password + salt)` with a random `s = salt`, generated
 * here, so the password is checked once when linking and then discarded.
 */
object MusicNavidrome {
    private const val STORE = "navidrome"
    private const val KEY_URL = "url"
    private const val KEY_USER = "user"
    private const val KEY_TOKEN = "token"
    private const val KEY_SALT = "salt"
    private const val API_VERSION = "1.16.1"
    private const val CLIENT = "BannerTV"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val _config = MutableStateFlow(load())
    val config: StateFlow<NavidromeConfig> = _config.asStateFlow()

    private fun load() = NavidromeConfig(
        url = MusicStorage.getString(STORE, KEY_URL).orEmpty(),
        username = MusicStorage.getString(STORE, KEY_USER).orEmpty(),
        token = MusicStorage.getString(STORE, KEY_TOKEN).orEmpty(),
        salt = MusicStorage.getString(STORE, KEY_SALT).orEmpty(),
    )

    /** Re-reads storage (Android calls this once its preferences are ready). */
    fun refresh() {
        _config.value = load()
    }

    suspend fun connect(url: String, username: String, password: String): Result<Unit> {
        val base = normalizeUrl(url).getOrElse { return Result.failure(it) }
        val user = username.trim()
        if (user.isBlank()) return Result.failure(IllegalArgumentException("Enter a username."))
        if (password.isBlank()) return Result.failure(IllegalArgumentException("Enter a password."))
        val salt = newSalt()
        val candidate = NavidromeConfig(base, user, md5Hex(password + salt), salt)
        return request(candidate, "ping", emptyMap()).map {
            _config.value = candidate
            MusicStorage.putString(STORE, KEY_URL, candidate.url)
            MusicStorage.putString(STORE, KEY_USER, candidate.username)
            MusicStorage.putString(STORE, KEY_TOKEN, candidate.token)
            MusicStorage.putString(STORE, KEY_SALT, candidate.salt)
        }
    }

    fun disconnect() {
        _config.value = NavidromeConfig()
        MusicStorage.clear(STORE)
    }

    suspend fun albums(limit: Int = 60): Result<List<NavidromeAlbum>> =
        withConfig { config ->
            request(config, "getAlbumList2", mapOf("type" to "newest", "size" to limit.toString(), "offset" to "0"))
                .map { root ->
                    root.obj("albumList2").arr("album").objects().map { item ->
                        NavidromeAlbum(
                            id = item.str("id").orEmpty(),
                            name = item.str("name") ?: item.str("title").orEmpty(),
                            artist = item.str("artist").orEmpty(),
                            coverArt = item.str("coverArt"),
                            songCount = item.int("songCount"),
                        )
                    }
                }
        }

    suspend fun playlists(): Result<List<NavidromePlaylist>> =
        withConfig { config ->
            request(config, "getPlaylists", emptyMap()).map { root ->
                root.obj("playlists").arr("playlist").objects().map { item ->
                    NavidromePlaylist(
                        id = item.str("id").orEmpty(),
                        name = item.str("name").orEmpty(),
                        owner = item.str("owner"),
                        songCount = item.int("songCount"),
                        coverArt = item.str("coverArt"),
                    )
                }
            }
        }

    suspend fun album(id: String): Result<NavidromeAlbumDetail> =
        withConfig { config ->
            request(config, "getAlbum", mapOf("id" to id)).map { root ->
                val album = root.obj("album")
                    ?: return@map NavidromeAlbumDetail(NavidromeAlbum(id, "", "", null, 0), emptyList())
                val songs = album.arr("song").toSongs(config)
                NavidromeAlbumDetail(
                    album = NavidromeAlbum(
                        id = album.str("id") ?: id,
                        name = album.str("name") ?: album.str("title").orEmpty(),
                        artist = album.str("artist").orEmpty(),
                        coverArt = album.str("coverArt"),
                        songCount = songs.size,
                    ),
                    songs = songs,
                )
            }
        }

    suspend fun playlist(id: String): Result<NavidromePlaylistDetail> =
        withConfig { config ->
            request(config, "getPlaylist", mapOf("id" to id)).map { root ->
                val playlist = root.obj("playlist")
                    ?: return@map NavidromePlaylistDetail(NavidromePlaylist(id, "", null, 0, null), emptyList())
                val songs = playlist.arr("entry").toSongs(config)
                NavidromePlaylistDetail(
                    playlist = NavidromePlaylist(
                        id = playlist.str("id") ?: id,
                        name = playlist.str("name").orEmpty(),
                        owner = playlist.str("owner"),
                        songCount = songs.size,
                        coverArt = playlist.str("coverArt"),
                    ),
                    songs = songs,
                )
            }
        }

    /** `submission = false` is "now playing"; `true` is a completed play. */
    suspend fun scrobble(song: Song, submission: Boolean = true): Result<Unit> {
        val config = _config.value
        if (!config.configured) return Result.success(Unit)
        val id = song.videoId.removePrefix("navidrome:")
        return request(config, "scrobble", mapOf("id" to id, "submission" to submission.toString())).map { }
    }

    suspend fun setStarred(song: Song, starred: Boolean): Result<Unit> {
        val config = _config.value
        if (!config.configured) return Result.success(Unit)
        val id = song.videoId.removePrefix("navidrome:")
        return request(config, if (starred) "star" else "unstar", mapOf("id" to id)).map { }
    }

    fun coverUrl(config: NavidromeConfig, coverId: String?, size: Int = 480): String? {
        if (!config.configured || coverId.isNullOrBlank()) return null
        return apiUrl(config, "getCoverArt", mapOf("id" to coverId, "size" to size.toString()))
    }

    /** The direct stream URL for a track id (without the `navidrome:` prefix). */
    fun streamUrl(config: NavidromeConfig, id: String): String =
        apiUrl(config, "stream", mapOf("id" to id, "format" to "raw"))

    private suspend fun <T> withConfig(block: suspend (NavidromeConfig) -> Result<T>): Result<T> {
        val config = _config.value
        if (!config.configured) return Result.failure(IllegalStateException("Navidrome isn't connected."))
        return block(config)
    }

    private suspend fun request(
        config: NavidromeConfig,
        endpoint: String,
        params: Map<String, String>,
    ): Result<JsonObject> = runCatching {
        val response = MusicHttp.get(apiUrl(config, endpoint, params), mapOf("User-Agent" to "BannerTV/1.0"))
        require(response.isSuccessful) { "Navidrome returned HTTP ${response.status}." }
        val root = (json.parseToJsonElement(response.body) as? JsonObject)?.obj("subsonic-response")
            ?: error("Navidrome returned an unexpected response.")
        if (root.str("status") != "ok") {
            val message = root.obj("error").str("message")
            error(message ?: "Navidrome rejected the connection.")
        }
        root
    }

    private fun apiUrl(config: NavidromeConfig, endpoint: String, params: Map<String, String>): String {
        val query = linkedMapOf(
            "u" to config.username,
            "t" to config.token,
            "s" to config.salt,
            "v" to API_VERSION,
            "c" to CLIENT,
            "f" to "json",
        ) + params
        val queryString = query.entries.joinToString("&") { (k, v) -> "${k.encodeURLParameter()}=${v.encodeURLParameter()}" }
        return "${config.url.trimEnd('/')}/rest/$endpoint?$queryString"
    }

    private fun JsonArray?.toSongs(config: NavidromeConfig): List<Song> =
        objects().mapNotNull { item ->
            val id = item.str("id") ?: return@mapNotNull null
            Song(
                videoId = "navidrome:$id",
                title = item.str("title") ?: "Unknown track",
                artist = item.str("artist") ?: "Navidrome",
                thumbnailUrl = coverUrl(config, item.str("coverArt")),
                durationText = formatDuration(item.int("duration")),
                albumId = item.str("albumId"),
                albumName = item.str("album"),
                localUri = streamUrl(config, id),
            )
        }

    private fun normalizeUrl(raw: String): Result<String> {
        val trimmed = raw.trim().trimEnd('/')
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            return Result.failure(IllegalArgumentException("The server URL must start with http:// or https://."))
        }
        return Result.success(trimmed)
    }

    private fun newSalt(): String = Random.nextBytes(8).joinToString("") { b ->
        val v = b.toInt() and 0xFF
        "0123456789abcdef"[v ushr 4].toString() + "0123456789abcdef"[v and 0x0F]
    }

    private fun formatDuration(seconds: Int): String? {
        if (seconds <= 0) return null
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        val ss = s.toString().padStart(2, '0')
        return if (h > 0) "$h:${m.toString().padStart(2, '0')}:$ss" else "$m:$ss"
    }
}

// ---- tolerant JSON readers (Subsonic omits empty fields and sometimes sends numbers as strings) ----

private fun JsonObject?.obj(key: String): JsonObject? = this?.get(key) as? JsonObject
private fun JsonObject?.arr(key: String): JsonArray? = this?.get(key) as? JsonArray
private fun JsonObject?.str(key: String): String? =
    (this?.get(key) as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
private fun JsonObject?.int(key: String): Int = (this?.get(key) as? JsonPrimitive)?.intOrNull ?: 0
private fun JsonArray?.objects(): List<JsonObject> = this?.mapNotNull { (it as? JsonElement)?.let { e -> e as? JsonObject } }.orEmpty()
