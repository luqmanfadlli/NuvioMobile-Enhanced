package com.nuvio.app.features.music.playback

import com.nuvio.app.features.music.data.MusicHttp
import io.ktor.http.encodeURLParameter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

internal data class TorrentResult(
    val name: String,
    val infoHash: String,
    val size: Long,
    val seeders: Int,
    val leechers: Int,
) {
    val magnetLink: String
        get() = "magnet:?xt=urn:btih:$infoHash&dn=${name.encodeURLParameter()}"
}

internal object TorrentSearch {
    private const val API_BASE = "https://apibay.org"
    private val json = Json { ignoreUnknownKeys = true }

    private val audioExtensions = setOf("mp3", "flac", "m4a", "aac", "ogg", "wav", "opus", "wma", "alac")

    suspend fun searchMusic(query: String): List<TorrentResult> {
        val url = "$API_BASE/q.php?q=${query.encodeURLParameter()}&cat=101"
        val response = try {
            MusicHttp.get(url)
        } catch (_: Throwable) {
            return emptyList()
        }

        if (!response.isSuccessful) return emptyList()

        val items = try {
            json.parseToJsonElement(response.body).jsonArray
        } catch (_: Throwable) {
            return emptyList()
        }

        return items.mapNotNull { element ->
            val obj = element.jsonObject
            val name = (obj["name"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            val hash = (obj["info_hash"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            if (hash == "0000000000000000000000000000000000000000") return@mapNotNull null
            val size = (obj["size"] as? JsonPrimitive)?.longOrNull ?: 0L
            val seeders = (obj["seeders"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 0
            val leechers = (obj["leechers"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 0
            TorrentResult(name, hash, size, seeders, leechers)
        }
    }

    suspend fun getFileList(infoHash: String): List<TorrentFile> {
        val url = "$API_BASE/f.php?id=$infoHash"
        val response = try {
            MusicHttp.get(url)
        } catch (_: Throwable) {
            return emptyList()
        }
        if (!response.isSuccessful) return emptyList()

        val items = try {
            json.parseToJsonElement(response.body).jsonArray
        } catch (_: Throwable) {
            return emptyList()
        }

        return items.mapNotNull { element ->
            val obj = element.jsonObject
            val name = (obj["name"] as? JsonPrimitive)?.contentOrNull
                ?.takeIf { it.isNotEmpty() && it != "." } ?: return@mapNotNull null
            val size = (obj["size"] as? JsonPrimitive)?.longOrNull ?: 0L
            TorrentFile(name, size)
        }
    }

    fun isAudioFile(filename: String): Boolean {
        val ext = filename.substringAfterLast('.', "").lowercase()
        return ext in audioExtensions
    }

    fun scoreTorrentForTrack(torrent: TorrentResult, title: String, artist: String): Int {
        val nameLower = torrent.name.lowercase()
        val titleLower = title.lowercase()
        val artistLower = artist.lowercase()
        var score = 0

        if (artistLower.isNotBlank() && nameLower.contains(artistLower)) score += 10
        if (titleLower.isNotBlank() && nameLower.contains(titleLower)) score += 5

        if (torrent.seeders > 10) score += 3
        if (torrent.seeders > 50) score += 2

        val nameLc = nameLower
        if (nameLc.contains("mp3") || nameLc.contains("flac") || nameLc.contains("320")) score += 2
        if (nameLc.contains("discography")) score -= 5
        if (nameLc.contains("complete")) score -= 3
        if (torrent.size > 2_000_000_000L) score -= 5

        return score
    }
}

internal data class TorrentFile(
    val name: String,
    val size: Long,
)
