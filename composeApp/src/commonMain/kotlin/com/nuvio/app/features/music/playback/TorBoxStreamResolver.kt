package com.nuvio.app.features.music.playback

import com.nuvio.app.features.addons.AddonRepository
import com.nuvio.app.features.debrid.DebridSettingsRepository
import com.nuvio.app.features.music.data.MusicHttp
import com.nuvio.app.features.music.data.MusicStorage
import com.nuvio.app.features.music.data.SyncMap
import com.nuvio.app.features.music.data.model.Song
import com.nuvio.app.features.music.data.musicNowMs
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

internal object TorBoxStreamResolver {

    private const val STORE = "torbox"
    private const val KEY_API_KEY = "api_key"
    private const val API_BASE = "https://api.torbox.app/v1/api"
    private const val MAX_POLL_ATTEMPTS = 30
    private const val POLL_INTERVAL_MS = 3000L
    private const val MAX_CACHE_ENTRIES = 64

    private val json = Json { ignoreUnknownKeys = true }
    private val cache = SyncMap<String, ResolvedStream>()

    /** Where the key in use came from: music shares Nuvio's own TorBox connection instead of asking again. */
    enum class Source(val label: String) {
        Debrid("Nuvio's TorBox connection (Settings \u203A Debrid)"),
        Addon("your TorBox addon"),
        Saved("a key saved earlier in Music"),
        None("not connected"),
    }

    private val ADDON_KEY = Regex("""/([A-Za-z0-9_-]{20,})/""")
    private val QUERY_KEY = Regex("""[?&](?:api_?key|apikey|key|token)=([A-Za-z0-9_-]{16,})""", RegexOption.IGNORE_CASE)

    private fun debridKey(): String = runCatching { DebridSettingsRepository.snapshot().torboxApiKey.trim() }.getOrDefault("")

    /** TorBox's Stremio addon carries the account key in its address: .../<key>/manifest.json */
    private fun addonKey(): String = runCatching {
        AddonRepository.uiState.value.addons
            .filter { it.enabled && it.manifestUrl.contains("torbox", ignoreCase = true) }
            .firstNotNullOfOrNull { addon ->
                QUERY_KEY.find(addon.manifestUrl)?.groupValues?.get(1)
                    ?: ADDON_KEY.find(addon.manifestUrl.substringAfter("://"))?.groupValues?.get(1)
            }
    }.getOrNull().orEmpty()

    private fun savedKey(): String = MusicStorage.getString(STORE, KEY_API_KEY).orEmpty()

    fun source(): Source = when {
        debridKey().isNotBlank() -> Source.Debrid
        addonKey().isNotBlank() -> Source.Addon
        savedKey().isNotBlank() -> Source.Saved
        else -> Source.None
    }

    val isConfigured: Boolean get() = source() != Source.None

    fun getApiKey(): String = debridKey().ifBlank { addonKey() }.ifBlank { savedKey() }

    fun setApiKey(key: String) {
        if (key.isBlank()) MusicStorage.remove(STORE, KEY_API_KEY)
        else MusicStorage.putString(STORE, KEY_API_KEY, key.trim())
    }

    suspend fun resolve(song: Song): Result<ResolvedStream> {
        val apiKey = getApiKey()
        if (apiKey.isBlank()) return Result.failure(IllegalStateException("TorBox API key not set. Add it in Music Settings."))

        val id = song.videoId
        cache[id]?.takeIf { it.isFresh }?.let { return Result.success(it) }

        return try {
            val stream = resolveViaTorrent(song, apiKey)
                ?: return Result.failure(IllegalStateException("Couldn't find a playable audio source for this track."))
            if (cache.size >= MAX_CACHE_ENTRIES) cache.clear()
            cache[id] = stream
            Result.success(stream)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            Result.failure(error)
        }
    }

    suspend fun warm(song: Song) {
        if (song.localUri != null) return
        if (!isConfigured) return
        if (cache[song.videoId]?.isFresh == true) return
        try { resolve(song) } catch (_: Throwable) {}
    }

    fun clearCache() = cache.clear()

    private suspend fun resolveViaTorrent(song: Song, apiKey: String): ResolvedStream? {
        val title = song.title
        val artist = song.artist

        val existingStream = findInExistingTorrents(title, artist, apiKey)
        if (existingStream != null) return existingStream

        val queries = buildSearchQueries(title, artist)
        for (query in queries) {
            val results = TorrentSearch.searchMusic(query)
            if (results.isEmpty()) continue

            val scored = results
                .map { it to TorrentSearch.scoreTorrentForTrack(it, title, artist) }
                .filter { it.second > 0 }
                .sortedByDescending { it.second }

            for ((torrent, _) in scored.take(3)) {
                checkTorrentCached(torrent.infoHash, apiKey)

                val torrentId = addTorrent(torrent.magnetLink, apiKey) ?: continue

                val stream = waitForTorrentAndGetStream(torrentId, title, artist, apiKey)
                if (stream != null) return stream
            }
        }
        return null
    }

    private fun buildSearchQueries(title: String, artist: String): List<String> {
        val cleanTitle = title
            .replace(Regex("\\(.*?\\)"), "")
            .replace(Regex("\\[.*?]"), "")
            .replace(Regex("feat\\.?.*", RegexOption.IGNORE_CASE), "")
            .trim()

        val queries = mutableListOf<String>()
        if (artist.isNotBlank()) {
            queries.add("$artist $cleanTitle")
            queries.add("$artist mp3")
            queries.add("$artist flac")
        }
        if (cleanTitle.isNotBlank()) {
            queries.add(cleanTitle)
        }
        return queries.distinct()
    }

    private suspend fun findInExistingTorrents(title: String, artist: String, apiKey: String): ResolvedStream? {
        val response = MusicHttp.get(
            "$API_BASE/torrents/mylist",
            mapOf("Authorization" to "Bearer $apiKey"),
        )
        if (!response.isSuccessful) return null

        val root = try { json.parseToJsonElement(response.body).jsonObject } catch (_: Throwable) { return null }
        val success = (root["success"] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull() ?: false
        if (!success) return null

        val items = try { root["data"]?.jsonArray } catch (_: Throwable) { return null } ?: return null

        for (item in items) {
            val obj = item.jsonObject
            val status = (obj["download_state"] as? JsonPrimitive)?.contentOrNull
            if (status != "completed" && status != "cached" && status != "uploading") continue

            val torrentId = (obj["id"] as? JsonPrimitive)?.intOrNull ?: continue
            val files = try { obj["files"]?.jsonArray } catch (_: Throwable) { continue } ?: continue

            val matchedFile = findMatchingAudioFile(files.map { it.jsonObject }, title, artist)
            if (matchedFile != null) {
                val cdnUrl = requestTorrentDownloadLink(torrentId, matchedFile, apiKey)
                if (cdnUrl != null) return ResolvedStream(cdnUrl, emptyMap(), null, "torbox")
            }
        }
        return null
    }

    private fun findMatchingAudioFile(files: List<JsonObject>, title: String, artist: String): Int? {
        val audioFiles = files.filter { file ->
            val name = (file["name"] as? JsonPrimitive)?.contentOrNull.orEmpty()
            TorrentSearch.isAudioFile(name)
        }

        if (audioFiles.isEmpty()) return null

        val titleLower = title.lowercase()
            .replace(Regex("\\(.*?\\)"), "")
            .replace(Regex("\\[.*?]"), "")
            .replace(Regex("feat\\.?.*", RegexOption.IGNORE_CASE), "")
            .trim()
        val titleWords = titleLower.split(Regex("\\s+")).filter { it.length > 2 }

        var bestFile: JsonObject? = null
        var bestScore = -1

        for (file in audioFiles) {
            val name = (file["name"] as? JsonPrimitive)?.contentOrNull.orEmpty()
            val nameLower = name.substringAfterLast('/').lowercase()
                .replace(Regex("\\d+\\.?\\s*-?\\s*"), "")
                .replace('_', ' ')
                .replace('-', ' ')

            var score = 0

            if (nameLower.contains(titleLower)) {
                score += 20
            } else {
                val matchedWords = titleWords.count { nameLower.contains(it) }
                score += matchedWords * 4
            }

            val ext = name.substringAfterLast('.', "").lowercase()
            if (ext == "mp3") score += 2
            if (ext == "m4a" || ext == "aac") score += 3
            if (ext == "flac") score += 1

            if (score > bestScore) {
                bestScore = score
                bestFile = file
            }
        }

        if (bestScore < 4 && audioFiles.size == 1) {
            bestFile = audioFiles.first()
        } else if (bestScore < 4) {
            return null
        }

        return bestFile?.let { (it["id"] as? JsonPrimitive)?.intOrNull }
    }

    private suspend fun checkTorrentCached(infoHash: String, apiKey: String): Boolean? {
        val url = "$API_BASE/torrents/checkcached?hash=$infoHash&format=object"
        val response = try {
            MusicHttp.get(url, mapOf("Authorization" to "Bearer $apiKey"))
        } catch (_: Throwable) {
            return null
        }
        if (!response.isSuccessful) return null

        val root = try { json.parseToJsonElement(response.body).jsonObject } catch (_: Throwable) { return null }
        val data = root["data"]?.jsonObject ?: return false
        return data.containsKey(infoHash.lowercase()) || data.containsKey(infoHash.uppercase())
    }

    private suspend fun addTorrent(magnetLink: String, apiKey: String): Int? {
        val formBody = "magnet=${magnetLink.encodeURLParameter()}"

        val response = MusicHttp.request(
            "POST",
            "$API_BASE/torrents/createtorrent",
            mapOf(
                "Authorization" to "Bearer $apiKey",
                "Content-Type" to "application/x-www-form-urlencoded",
            ),
            formBody,
        )

        if (response.body.isBlank()) return null

        val root = try {
            json.parseToJsonElement(response.body).jsonObject
        } catch (_: Throwable) {
            return null
        }

        val success = (root["success"] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull() ?: false

        if (!success) {
            val error = (root["error"] as? JsonPrimitive)?.contentOrNull
            val detail = (root["detail"] as? JsonPrimitive)?.contentOrNull
            if (error == "ACTIVE_LIMIT") return null
            if (error == "DUPLICATE_ITEM") {
                val existingId = root["data"]?.let { data ->
                    try {
                        (data.jsonObject["torrent_id"] as? JsonPrimitive)?.intOrNull
                            ?: (data.jsonObject["id"] as? JsonPrimitive)?.intOrNull
                    } catch (_: Throwable) { null }
                }
                if (existingId != null) return existingId
            }
            return null
        }

        return root["data"]?.let { data ->
            try {
                when {
                    data is JsonPrimitive -> data.intOrNull
                    else -> {
                        val obj = data.jsonObject
                        (obj["torrent_id"] as? JsonPrimitive)?.intOrNull
                            ?: (obj["id"] as? JsonPrimitive)?.intOrNull
                    }
                }
            } catch (_: Throwable) { null }
        }
    }

    private suspend fun waitForTorrentAndGetStream(
        torrentId: Int,
        title: String,
        artist: String,
        apiKey: String,
    ): ResolvedStream? {
        for (attempt in 0 until MAX_POLL_ATTEMPTS) {
            delay(POLL_INTERVAL_MS)

            val info = getTorrentInfo(torrentId, apiKey) ?: continue
            val status = (info["download_state"] as? JsonPrimitive)?.contentOrNull

            when (status) {
                "completed", "cached", "uploading" -> {
                    val files = try { info["files"]?.jsonArray?.map { it.jsonObject } } catch (_: Throwable) { null }
                    if (files.isNullOrEmpty()) return null

                    val fileId = findMatchingAudioFile(files, title, artist) ?: return null

                    val cdnUrl = requestTorrentDownloadLink(torrentId, fileId, apiKey)
                    if (cdnUrl != null) return ResolvedStream(cdnUrl, emptyMap(), null, "torbox")
                    return null
                }
                "downloading", "metaDL", "checkingResumeData", "stalled (no seeds)", "queued" -> continue
                else -> {
                    if (status?.contains("error", ignoreCase = true) == true) return null
                    continue
                }
            }
        }

        return null
    }

    private suspend fun getTorrentInfo(torrentId: Int, apiKey: String): JsonObject? {
        val response = MusicHttp.get(
            "$API_BASE/torrents/mylist?id=$torrentId&bypass_cache=true",
            mapOf("Authorization" to "Bearer $apiKey"),
        )
        if (!response.isSuccessful) return null

        val root = try { json.parseToJsonElement(response.body).jsonObject } catch (_: Throwable) { return null }
        return root["data"]?.jsonObject
    }

    private suspend fun requestTorrentDownloadLink(torrentId: Int, fileId: Int, apiKey: String): String? {
        val url = "$API_BASE/torrents/requestdl?token=$apiKey&torrent_id=$torrentId&file_id=$fileId&zip_link=false"

        val response = MusicHttp.get(url)
        if (!response.isSuccessful) return null

        val root = try { json.parseToJsonElement(response.body).jsonObject } catch (_: Throwable) { return null }
        val success = (root["success"] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull() ?: false
        if (!success) return null

        return (root["data"] as? JsonPrimitive)?.contentOrNull
    }
}
