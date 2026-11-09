package com.nuvio.app.features.podcasts

import com.nuvio.app.features.music.data.model.Song
import com.nuvio.app.features.music.playback.MusicPlayer
import com.nuvio.app.features.music.playback.PODCAST_ID_PREFIX
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Episodes play through the same engine as music, so they get background audio, the lock screen and
 * remote controls with no second player to keep in step. An episode is a song whose address is the
 * episode's audio file.
 */
object PodcastPlayback {
    private val _nowPlayingOpen = MutableStateFlow(false)
    /** Whether the full-screen player is showing; the mini player opens it. */
    val nowPlayingOpen: StateFlow<Boolean> = _nowPlayingOpen.asStateFlow()

    fun openNowPlaying() { _nowPlayingOpen.value = true }
    fun closeNowPlaying() { _nowPlayingOpen.value = false }

    fun play(episode: PodcastEpisode) {
        MusicPlayer.play(listOf(toSong(episode)))
        PodcastRepository.recordPlayed(episode)
    }

    fun isCurrent(episode: PodcastEpisode, currentSongId: String?): Boolean =
        currentSongId == PODCAST_ID_PREFIX + episode.id

    private fun toSong(e: PodcastEpisode) = Song(
        videoId = PODCAST_ID_PREFIX + e.id,
        title = e.title,
        artist = e.podcastTitle,
        thumbnailUrl = e.podcastImage,
        durationText = e.clock.ifBlank { null },
        albumName = e.podcastTitle,
        localUri = e.audioUrl,
    )
}
