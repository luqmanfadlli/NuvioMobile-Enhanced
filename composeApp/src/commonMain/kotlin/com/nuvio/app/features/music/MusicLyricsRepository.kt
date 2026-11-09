package com.nuvio.app.features.music

import com.nuvio.app.features.music.data.SyncMap
import com.nuvio.app.features.music.data.lyrics.BetterLyrics
import com.nuvio.app.features.music.data.lyrics.BiniLyrics
import com.nuvio.app.features.music.data.lyrics.LrcLib
import com.nuvio.app.features.music.data.lyrics.LyricLine
import com.nuvio.app.features.music.data.lyrics.YouTubeMusicLyrics
import com.nuvio.app.features.music.data.lyrics.YouTubeTranscriptLyrics
import com.nuvio.app.features.music.data.lyrics.artistForLyricsSearch
import com.nuvio.app.features.music.data.lyrics.forLyricsSearch
import com.nuvio.app.features.music.data.model.Song
import com.nuvio.app.features.music.data.model.durationMillis
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Looks lyrics up across every provider at once and keeps the best answer: word-synced first
 * (Apple Music via BiniLyrics, BetterLyrics), then line-synced (LRCLIB), then captions and the
 * music catalogue's own text.
 */
object MusicLyricsRepository {
    data class Lyrics(
        val source: String,
        val lines: List<LyricLine>,
    ) {
        val hasTiming: Boolean get() = lines.any { it.timeMs > 0L }
        val wordSynced: Boolean get() = lines.any { it.isWordSynced }
    }

    private const val PROVIDER_TIMEOUT_MS = 5_000L
    private const val YOUTUBE_TIMEOUT_MS = 4_000L

    private val cache = SyncMap<String, Lyrics>()

    fun cached(videoId: String): Lyrics? = cache[videoId]

    suspend fun lyrics(song: Song): Lyrics? {
        cache[song.videoId]?.let { return it }
        val title = song.title.forLyricsSearch()
        val artist = song.artist.artistForLyricsSearch()
        val durationMs = song.durationMillis()
        val album = song.albumName

        return coroutineScope {
            val bini = async {
                withTimeoutOrNull(PROVIDER_TIMEOUT_MS) {
                    runCatching { BiniLyrics.lyrics(title, artist, durationMs, album) }.getOrNull()?.lines
                }
            }
            val better = async {
                withTimeoutOrNull(PROVIDER_TIMEOUT_MS) {
                    runCatching { BetterLyrics.lyrics(title, artist, durationMs, album) }.getOrNull()
                }
            }
            val lrcLib = async {
                withTimeoutOrNull(PROVIDER_TIMEOUT_MS) {
                    runCatching { LrcLib.lyrics(title, artist, durationMs) }.getOrNull()
                }
            }
            val youtubeMusic = async {
                withTimeoutOrNull(YOUTUBE_TIMEOUT_MS) {
                    runCatching { YouTubeMusicLyrics.lyrics(song.videoId) }.getOrNull()
                }
            }
            val youtubeTranscript = async {
                withTimeoutOrNull(YOUTUBE_TIMEOUT_MS) {
                    runCatching { YouTubeTranscriptLyrics.lyrics(song.videoId) }.getOrNull()
                }
            }

            fun found(lines: List<LyricLine>?, source: String): Lyrics? {
                val usable = lines?.takeIf { list -> list.any { it.text.isNotBlank() } } ?: return null
                return Lyrics(source, usable).also { cache[song.videoId] = it }
            }

            val biniLines = bini.await()
            if (biniLines?.any { it.isWordSynced } == true) {
                return@coroutineScope found(biniLines, "Apple Music · BiniLyrics")
            }
            val betterLines = better.await()
            if (betterLines?.any { it.isWordSynced } == true) {
                return@coroutineScope found(betterLines, "BetterLyrics")
            }
            found(biniLines, "Apple Music · BiniLyrics")?.let { return@coroutineScope it }
            found(betterLines, "BetterLyrics")?.let { return@coroutineScope it }
            found(lrcLib.await(), "LRCLIB")?.let { return@coroutineScope it }
            found(youtubeTranscript.await(), "Captions")?.let { return@coroutineScope it }
            found(youtubeMusic.await(), "Music catalogue")?.let { return@coroutineScope it }
            null
        }
    }
}
