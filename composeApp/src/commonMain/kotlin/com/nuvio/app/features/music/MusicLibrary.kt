package com.nuvio.app.features.music

import com.nuvio.app.features.music.data.MusicStorage
import com.nuvio.app.features.music.data.model.Song
import com.nuvio.app.features.music.data.musicNowMs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** The part of a track worth remembering on the device. */
@Serializable
data class SavedSong(
    val videoId: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String? = null,
    val durationText: String? = null,
    val artistId: String? = null,
    val albumId: String? = null,
    val albumName: String? = null,
    val localUri: String? = null,
)

@Serializable
data class LocalPlaylist(
    val id: String,
    val name: String,
    val songs: List<SavedSong> = emptyList(),
    val createdAt: Long = 0L,
)

internal fun Song.toSaved() = SavedSong(videoId, title, artist, thumbnailUrl, durationText, artistId, albumId, albumName, localUri)

internal fun SavedSong.toSong() = Song(
    videoId = videoId,
    title = title,
    artist = artist,
    thumbnailUrl = thumbnailUrl,
    durationText = durationText,
    artistId = artistId,
    albumId = albumId,
    albumName = albumName,
    localUri = localUri,
)

/** Liked songs, playlists and listening history, kept on the device. */
object MusicLibrary {
    private const val STORE = "library"
    private const val RECENT_LIMIT = 60

    private val json = Json { ignoreUnknownKeys = true }
    private val songsSerializer = ListSerializer(SavedSong.serializer())
    private val playlistsSerializer = ListSerializer(LocalPlaylist.serializer())

    private val _liked = MutableStateFlow(loadSongs("liked"))
    val liked: StateFlow<List<Song>> = _liked.asStateFlow()

    private val _recent = MutableStateFlow(loadSongs("recent"))
    val recent: StateFlow<List<Song>> = _recent.asStateFlow()

    private val _playlists = MutableStateFlow(loadPlaylists())
    val playlists: StateFlow<List<LocalPlaylist>> = _playlists.asStateFlow()

    private fun loadSongs(key: String): List<Song> =
        runCatching {
            MusicStorage.getString(STORE, key)?.let { json.decodeFromString(songsSerializer, it) }
                ?.map { it.toSong() }
        }.getOrNull().orEmpty()

    private fun loadPlaylists(): List<LocalPlaylist> =
        runCatching {
            MusicStorage.getString(STORE, "playlists")?.let { json.decodeFromString(playlistsSerializer, it) }
        }.getOrNull().orEmpty()

    /** Re-reads storage (Android calls this once its preferences are ready). */
    fun refresh() {
        _liked.value = loadSongs("liked")
        _recent.value = loadSongs("recent")
        _playlists.value = loadPlaylists()
    }

    private fun saveSongs(key: String, songs: List<Song>) {
        MusicStorage.putString(STORE, key, json.encodeToString(songsSerializer, songs.map { it.toSaved() }))
    }

    private fun savePlaylists(list: List<LocalPlaylist>) {
        MusicStorage.putString(STORE, "playlists", json.encodeToString(playlistsSerializer, list))
    }

    fun isLiked(videoId: String): Boolean = _liked.value.any { it.videoId == videoId }

    fun toggleLike(song: Song) {
        val current = _liked.value
        val next = if (current.any { it.videoId == song.videoId }) {
            current.filterNot { it.videoId == song.videoId }
        } else {
            listOf(song) + current
        }
        _liked.value = next
        saveSongs("liked", next)
    }

    fun recordPlayed(song: Song) {
        val next = (listOf(song) + _recent.value.filterNot { it.videoId == song.videoId }).take(RECENT_LIMIT)
        _recent.value = next
        saveSongs("recent", next)
    }

    fun clearRecent() {
        _recent.value = emptyList()
        saveSongs("recent", emptyList())
    }

    fun createPlaylist(name: String): LocalPlaylist {
        val playlist = LocalPlaylist(
            id = "local-" + musicNowMs().toString(36) + "-" + _playlists.value.size,
            name = name.trim().ifBlank { "My playlist #${_playlists.value.size + 1}" },
            createdAt = musicNowMs(),
        )
        val next = listOf(playlist) + _playlists.value
        _playlists.value = next
        savePlaylists(next)
        return playlist
    }

    fun addToPlaylist(playlistId: String, song: Song): Boolean {
        var added = false
        val next = _playlists.value.map { playlist ->
            if (playlist.id != playlistId || playlist.songs.any { it.videoId == song.videoId }) {
                playlist
            } else {
                added = true
                playlist.copy(songs = playlist.songs + song.toSaved())
            }
        }
        if (added) {
            _playlists.value = next
            savePlaylists(next)
        }
        return added
    }

    fun removeFromPlaylist(playlistId: String, videoId: String) {
        val next = _playlists.value.map { playlist ->
            if (playlist.id == playlistId) playlist.copy(songs = playlist.songs.filterNot { it.videoId == videoId }) else playlist
        }
        _playlists.value = next
        savePlaylists(next)
    }

    fun renamePlaylist(playlistId: String, name: String) {
        val next = _playlists.value.map { if (it.id == playlistId) it.copy(name = name.trim().ifBlank { it.name }) else it }
        _playlists.value = next
        savePlaylists(next)
    }

    fun deletePlaylist(playlistId: String) {
        val next = _playlists.value.filterNot { it.id == playlistId }
        _playlists.value = next
        savePlaylists(next)
    }

    fun playlist(id: String): LocalPlaylist? = _playlists.value.firstOrNull { it.id == id }
}
