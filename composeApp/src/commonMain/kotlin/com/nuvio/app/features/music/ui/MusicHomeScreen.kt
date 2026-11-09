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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
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
import com.nuvio.app.features.music.data.model.ShelfLayout
import com.nuvio.app.features.music.data.model.Song
import com.nuvio.app.features.music.playback.MusicPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

internal data class MusicHomeState(
    val shelves: List<HomeShelf> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

/** The catalogue's home feed, loaded once and shared by every host that shows it. */
internal object MusicHomeData {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(MusicHomeState())
    val state: StateFlow<MusicHomeState> = _state.asStateFlow()

    fun ensureLoaded() {
        val s = _state.value
        if (s.loading || s.shelves.isNotEmpty()) return
        refresh()
    }

    fun refresh() {
        _state.value = _state.value.copy(loading = true, error = null)
        scope.launch {
            YtMusicRepository.home().fold(
                onSuccess = { feed -> _state.value = MusicHomeState(shelves = feed.shelves.filter { it.items.isNotEmpty() }) },
                onFailure = { e -> _state.value = _state.value.copy(loading = false, error = e.message ?: "Couldn't load music.") },
            )
        }
    }
}

private enum class HomeChip(val label: String) { All("All"), Recent("Recent"), Liked("Liked") }

private fun greeting(): String {
    val hour = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).hour
    return when {
        hour < 5 -> "Good evening"
        hour < 12 -> "Good morning"
        hour < 18 -> "Good afternoon"
        else -> "Good evening"
    }
}

@Composable
internal fun MusicHomeScreen(onOpen: (MusicDest) -> Unit) {
    val recent by MusicLibrary.recent.collectAsStateWithLifecycle()
    val liked by MusicLibrary.liked.collectAsStateWithLifecycle()
    val home by MusicHomeData.state.collectAsStateWithLifecycle()
    var chip by remember { mutableStateOf(HomeChip.All) }
    LaunchedEffect(Unit) { MusicHomeData.ensureLoaded() }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Sp.bg),
        contentPadding = PaddingValues(bottom = LocalMusicBottomPad.current),
    ) {
        item {
            Column(Modifier.statusBarsPadding().padding(top = 8.dp)) {
                Text(
                    greeting(),
                    color = Sp.text,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 16.dp, end = 72.dp, top = 8.dp, bottom = 12.dp),
                )
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HomeChip.entries.forEach { entry -> Pill(entry.label, chip == entry) { chip = entry } }
                }
                Spacer(Modifier.height(14.dp))
            }
        }
        when (chip) {
            HomeChip.Recent -> songList(recent, "Nothing played yet.")
            HomeChip.Liked -> songList(liked, "Songs you like show up here.")
            HomeChip.All -> {
                item { QuickGrid(recent, liked.size, onOpen) }
                if (recent.isNotEmpty()) {
                    item {
                        Shelf(
                            HomeShelf("Recently played", recent.take(20).map { s ->
                                com.nuvio.app.features.music.data.model.ShelfItem(s.title, s.artist, s.thumbnailUrl, s.videoId, null)
                            }),
                            onItem = { item -> item.toSong()?.let { MusicPlayer.play(recent, recent.indexOfFirst { r -> r.videoId == it.videoId }.coerceAtLeast(0)) } },
                        )
                    }
                }
                when {
                    home.loading && home.shelves.isEmpty() -> item {
                        Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Sp.green)
                        }
                    }
                    home.error != null && home.shelves.isEmpty() -> item {
                        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(home.error.orEmpty(), color = Sp.sub, fontSize = 14.sp)
                            Spacer(Modifier.height(12.dp))
                            Pill("Try again", true) { MusicHomeData.refresh() }
                        }
                    }
                    else -> home.shelves.forEach { shelf ->
                        item(key = "shelf-" + shelf.title) {
                            if (shelf.layout == ShelfLayout.LIST) {
                                val songs = shelf.items.mapNotNull { it.toSong() }
                                Column {
                                    SectionTitle(shelf.title)
                                    songs.take(5).forEachIndexed { i, song -> SongRow(song, onClick = { MusicPlayer.play(songs, i) }) }
                                }
                            } else {
                                Shelf(shelf, onItem = { item ->
                                    val song = item.toSong()
                                    if (song != null) {
                                        val songs = shelf.items.mapNotNull { it.toSong() }
                                        MusicPlayer.play(songs, songs.indexOfFirst { it.videoId == song.videoId }.coerceAtLeast(0))
                                    } else item.browseId?.let { onOpen(MusicDest.Browse(it, item.title, item.thumbnailUrl)) }
                                })
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.songList(songs: List<Song>, empty: String) {
    if (songs.isEmpty()) {
        item { Text(empty, color = Sp.sub, fontSize = 14.sp, modifier = Modifier.padding(16.dp)) }
    } else {
        items(songs.size) { i -> SongRow(songs[i], onClick = { MusicPlayer.play(songs, i) }) }
    }
}

/** Spotify's two-column grid of quick-access tiles: Liked Songs first, then what was played lately. */
@Composable
private fun QuickGrid(recent: List<Song>, likedCount: Int, onOpen: (MusicDest) -> Unit) {
    data class Tile(val title: String, val cover: String?, val liked: Boolean, val onClick: () -> Unit)
    val tiles = buildList {
        add(Tile("Liked Songs", null, true) { onOpen(MusicDest.Liked) })
        recent.take(5).forEachIndexed { i, s -> add(Tile(s.title, s.thumbnailUrl, false) { MusicPlayer.play(recent, i) }) }
    }
    Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tiles.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { tile ->
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Sp.chip)
                            .clickable(onClick = tile.onClick),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (tile.liked) {
                            Box(
                                Modifier.size(56.dp).background(Brush.linearGradient(listOf(Color(0xFF450AF5), Color(0xFFC4EFD9)))),
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Rounded.Favorite, null, tint = Color.White, modifier = Modifier.size(24.dp)) }
                        } else {
                            Cover(tile.cover, Modifier.size(56.dp), shape = RoundedCornerShape(0.dp))
                        }
                        Text(
                            tile.title,
                            color = Sp.text,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 10.dp),
                        )
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}
