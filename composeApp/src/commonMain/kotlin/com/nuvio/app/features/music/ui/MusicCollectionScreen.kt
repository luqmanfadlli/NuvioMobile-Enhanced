package com.nuvio.app.features.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.features.music.MusicLibrary
import com.nuvio.app.features.music.data.YtMusicRepository
import com.nuvio.app.features.music.data.model.HomeShelf
import com.nuvio.app.features.music.data.model.Song
import com.nuvio.app.features.music.navidrome.MusicNavidrome
import com.nuvio.app.features.music.playback.MusicPlayer
import com.nuvio.app.features.music.toSong
import com.nuvio.app.features.music.webdav.MusicWebDav
import com.nuvio.app.features.music.webdav.WebDavEntry

/** Where a page inside the music section goes. */
internal sealed interface MusicDest {
    data class Browse(val browseId: String, val title: String, val thumb: String?) : MusicDest
    data class Genre(val browseId: String, val params: String?, val title: String) : MusicDest
    data object Liked : MusicDest
    data object Recent : MusicDest
    data class Local(val id: String) : MusicDest
    data object NavidromeHome : MusicDest
    data class NavAlbum(val id: String, val title: String) : MusicDest
    data class NavPlaylist(val id: String, val title: String) : MusicDest
    data class WebDav(val url: String, val title: String) : MusicDest
    data object Settings : MusicDest
}

private data class CollectionData(
    val title: String,
    val subtitle: String = "",
    val artwork: String? = null,
    val songs: List<Song> = emptyList(),
    val shelves: List<HomeShelf> = emptyList(),
    val entries: List<WebDavEntry> = emptyList(),
    val canDelete: String? = null,
    val liked: Boolean = false,
)

@Composable
internal fun MusicDestScreen(dest: MusicDest, onBack: () -> Unit, onOpen: (MusicDest) -> Unit) {
    when (dest) {
        MusicDest.Settings -> MusicSettingsScreen(onBack)
        MusicDest.NavidromeHome -> NavidromeHomeScreen(onBack, onOpen)
        else -> CollectionScreen(dest, onBack, onOpen)
    }
}

@Composable
private fun CollectionScreen(dest: MusicDest, onBack: () -> Unit, onOpen: (MusicDest) -> Unit) {
    val liked by MusicLibrary.liked.collectAsStateWithLifecycle()
    val recent by MusicLibrary.recent.collectAsStateWithLifecycle()
    val playlists by MusicLibrary.playlists.collectAsStateWithLifecycle()
    var remote by remember(dest) { mutableStateOf<CollectionData?>(null) }
    var error by remember(dest) { mutableStateOf<String?>(null) }

    LaunchedEffect(dest) {
        when (dest) {
            is MusicDest.Browse -> {
                if (dest.browseId.startsWith("UC")) {
                    YtMusicRepository.artistPage(dest.browseId).fold(
                        onSuccess = { page ->
                            remote = CollectionData(page.name ?: dest.title, page.monthlyListenerCount.orEmpty(), page.thumbnailUrl ?: dest.thumb, page.songs, page.sections)
                        },
                        onFailure = { error = it.message ?: "Couldn't load this artist." },
                    )
                } else {
                    YtMusicRepository.browseSongs(dest.browseId).fold(
                        onSuccess = { page -> remote = CollectionData(dest.title, "${page.songs.size} songs", dest.thumb, page.songs) },
                        onFailure = { error = it.message ?: "Couldn't load this page." },
                    )
                }
            }
            is MusicDest.Genre -> YtMusicRepository.moodGenreShelves(dest.browseId, dest.params).fold(
                onSuccess = { shelves -> remote = CollectionData(dest.title, shelves = shelves) },
                onFailure = { error = it.message ?: "Couldn't load this genre." },
            )
            is MusicDest.NavAlbum -> MusicNavidrome.album(dest.id).fold(
                onSuccess = { d -> remote = CollectionData(d.album.name.ifBlank { dest.title }, d.album.artist, MusicNavidrome.coverUrl(MusicNavidrome.config.value, d.album.coverArt), d.songs) },
                onFailure = { error = it.message },
            )
            is MusicDest.NavPlaylist -> MusicNavidrome.playlist(dest.id).fold(
                onSuccess = { d -> remote = CollectionData(d.playlist.name.ifBlank { dest.title }, d.playlist.owner.orEmpty(), MusicNavidrome.coverUrl(MusicNavidrome.config.value, d.playlist.coverArt), d.songs) },
                onFailure = { error = it.message },
            )
            is MusicDest.WebDav -> MusicWebDav.list(dest.url).fold(
                onSuccess = { entries ->
                    val songs = entries.filter { !it.isDirectory && MusicWebDav.isAudio(it.name) }.map { e ->
                        Song(videoId = "webdav:" + e.url, title = e.name.substringBeforeLast('.'), artist = dest.title, thumbnailUrl = null, localUri = e.url)
                    }
                    remote = CollectionData(dest.title, "${songs.size} tracks", null, songs, entries = entries.filter { it.isDirectory })
                },
                onFailure = { error = it.message },
            )
            else -> Unit
        }
    }

    val data: CollectionData? = when (dest) {
        MusicDest.Liked -> CollectionData("Liked Songs", "${liked.size} songs", null, liked, liked = true)
        MusicDest.Recent -> CollectionData("Recently played", "${recent.size} songs", recent.firstOrNull()?.thumbnailUrl, recent)
        is MusicDest.Local -> playlists.firstOrNull { it.id == dest.id }?.let { p ->
            CollectionData(p.name, "${p.songs.size} songs", p.songs.firstOrNull()?.thumbnailUrl, p.songs.map { it.toSong() }, canDelete = p.id)
        } ?: CollectionData("Playlist")
        else -> remote
    }

    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF3A3A3A), Sp.bg), endY = 900f))) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = LocalMusicBottomPad.current)) {
            item {
                Column(Modifier.statusBarsPadding().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Sp.text, modifier = Modifier.size(32.dp).clip(CircleShape).clickable(onClick = onBack).padding(2.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    when {
                        data == null && error == null -> Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Sp.green) }
                        error != null && data == null -> Text(error.orEmpty(), color = Sp.sub, fontSize = 14.sp, modifier = Modifier.padding(24.dp))
                        data != null -> Header(data, dest)
                    }
                }
            }
            if (data != null) {
                items(data.entries.size) { i ->
                    val e = data.entries[i]
                    Row(
                        Modifier.fillMaxWidth().clickable { onOpen(MusicDest.WebDav(e.url, e.name)) }.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(Icons.Rounded.Folder, null, tint = Sp.sub, modifier = Modifier.size(28.dp))
                        Text(e.name, color = Sp.text, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                items(data.songs.size) { i ->
                    val song = data.songs[i]
                    SongRow(
                        song = song,
                        index = i,
                        showCover = false,
                        onClick = { MusicPlayer.play(data.songs, i) },
                        onMore = data.canDelete?.let { id -> { MusicLibrary.removeFromPlaylist(id, song.videoId) } },
                    )
                }
                if (data.songs.isEmpty() && data.shelves.isEmpty() && data.entries.isEmpty() && error == null && dest !is MusicDest.Browse && dest !is MusicDest.Genre) {
                    item { Text("Nothing here yet.", color = Sp.sub, fontSize = 14.sp, modifier = Modifier.padding(16.dp)) }
                }
                data.shelves.forEach { shelf ->
                    item(key = "s-" + shelf.title) {
                        Shelf(shelf, onItem = { item ->
                            val s = item.toSong()
                            if (s != null) MusicPlayer.play(listOf(s)) else item.browseId?.let { onOpen(MusicDest.Browse(it, item.title, item.thumbnailUrl)) }
                        })
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(data: CollectionData, dest: MusicDest) {
    val liked by MusicLibrary.liked.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (data.liked) {
            Box(Modifier.size(220.dp).clip(RoundedCornerShape(4.dp)).background(Brush.linearGradient(listOf(Color(0xFF450AF5), Color(0xFFC4EFD9)))), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Favorite, null, tint = Color.White, modifier = Modifier.size(90.dp))
            }
        } else {
            Cover(data.artwork, Modifier.size(220.dp), shape = if (dest is MusicDest.Browse && dest.browseId.startsWith("UC")) CircleShape else RoundedCornerShape(4.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(data.title, color = Sp.text, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
        if (data.subtitle.isNotBlank()) Text(data.subtitle, color = Sp.sub, fontSize = 14.sp, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            if (data.songs.isNotEmpty()) {
                Icon(Icons.Rounded.Shuffle, "Shuffle", tint = Sp.green, modifier = Modifier.size(30.dp).clip(CircleShape).clickable { MusicPlayer.play(data.songs, 0, shuffle = true) })
            }
            if (!data.liked && data.songs.size == 1) {
                val song = data.songs.first()
                val isLiked = liked.any { it.videoId == song.videoId }
                Icon(if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Like", tint = if (isLiked) Sp.green else Sp.sub, modifier = Modifier.size(28.dp).clickable { MusicLibrary.toggleLike(song) })
            }
            data.canDelete?.let { id ->
                Icon(Icons.Rounded.Delete, "Delete playlist", tint = Sp.sub, modifier = Modifier.size(26.dp).clickable { MusicLibrary.deletePlaylist(id) })
            }
            Box(Modifier.weight(1f))
            if (data.songs.isNotEmpty()) {
                Box(
                    Modifier.size(56.dp).clip(CircleShape).background(Sp.green).clickable { MusicPlayer.play(data.songs, 0) },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.PlayArrow, "Play", tint = Color.Black, modifier = Modifier.size(34.dp)) }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun NavidromeHomeScreen(onBack: () -> Unit, onOpen: (MusicDest) -> Unit) {
    var albums by remember { mutableStateOf<List<com.nuvio.app.features.music.navidrome.NavidromeAlbum>>(emptyList()) }
    var lists by remember { mutableStateOf<List<com.nuvio.app.features.music.navidrome.NavidromePlaylist>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    val config by MusicNavidrome.config.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        MusicNavidrome.albums().onSuccess { albums = it }.onFailure { error = it.message }
        MusicNavidrome.playlists().onSuccess { lists = it }
        loading = false
    }
    LazyColumn(Modifier.fillMaxSize().background(Sp.bg), contentPadding = PaddingValues(bottom = LocalMusicBottomPad.current)) {
        item {
            Row(Modifier.statusBarsPadding().padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Sp.text, modifier = Modifier.size(32.dp).clip(CircleShape).clickable(onClick = onBack).padding(2.dp))
                Text("Navidrome", color = Sp.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (loading) item { Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Sp.green) } }
        error?.let { msg -> item { Text(msg, color = Sp.sub, fontSize = 14.sp, modifier = Modifier.padding(16.dp)) } }
        if (lists.isNotEmpty()) item { SectionTitle("Playlists") }
        items(lists.size) { i ->
            val p = lists[i]
            LibraryRow(p.name, "Playlist • ${p.songCount} songs", { Cover(MusicNavidrome.coverUrl(config, p.coverArt), Modifier.size(60.dp)) }) { onOpen(MusicDest.NavPlaylist(p.id, p.name)) }
        }
        if (albums.isNotEmpty()) item { SectionTitle("Newest albums") }
        items(albums.size) { i ->
            val a = albums[i]
            LibraryRow(a.name, "Album • ${a.artist}", { Cover(MusicNavidrome.coverUrl(config, a.coverArt), Modifier.size(60.dp)) }) { onOpen(MusicDest.NavAlbum(a.id, a.name)) }
        }
    }
}
