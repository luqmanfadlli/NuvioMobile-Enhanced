package com.nuvio.app.features.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.features.music.data.YtMusicRepository
import com.nuvio.app.features.music.data.model.BrowseType
import com.nuvio.app.features.music.data.model.MoodGenre
import com.nuvio.app.features.music.data.model.SearchFilter
import com.nuvio.app.features.music.data.model.SearchResult
import com.nuvio.app.features.music.data.model.Song
import com.nuvio.app.features.music.playback.MusicPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The genre and mood tiles under "Browse all", loaded once. */
internal object MusicBrowseData {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _genres = MutableStateFlow<List<MoodGenre>>(emptyList())
    val genres: StateFlow<List<MoodGenre>> = _genres.asStateFlow()
    private var requested = false

    fun ensureLoaded() {
        if (requested) return
        requested = true
        scope.launch {
            YtMusicRepository.moodAndGenres().onSuccess { sections ->
                _genres.value = sections.flatMap { it.items }.distinctBy { it.browseId + it.params }
            }.onFailure { requested = false }
        }
    }
}

private val tilePalette = listOf(
    Color(0xFFE13300), Color(0xFF1E3264), Color(0xFF8D67AB), Color(0xFFDC148C), Color(0xFF148A08),
    Color(0xFFBA5D07), Color(0xFF477D95), Color(0xFF503750), Color(0xFFE8115B), Color(0xFF0D73EC),
    Color(0xFF7358FF), Color(0xFF1E8A6B),
)

private fun tileColor(genre: MoodGenre): Color =
    genre.stripeColor?.let { Color(it.toInt()).copy(alpha = 1f) } ?: tilePalette[(genre.title.hashCode().and(0x7fffffff)) % tilePalette.size]

@Composable
internal fun MusicSearchScreen(onOpen: (MusicDest) -> Unit) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(SearchFilter.ALL) }
    var rows by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val genres by MusicBrowseData.genres.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { MusicBrowseData.ensureLoaded() }

    LaunchedEffect(query, filter) {
        val q = query.trim()
        if (q.isEmpty()) {
            rows = emptyList(); loading = false; error = null
            return@LaunchedEffect
        }
        delay(350)
        loading = true
        error = null
        YtMusicRepository.searchPage(q, filter).fold(
            onSuccess = { rows = it.rows },
            onFailure = { error = it.message ?: "Search failed." },
        )
        loading = false
    }

    Column(Modifier.fillMaxSize().background(Sp.bg).statusBarsPadding()) {
        Text("Search", color = Sp.text, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, end = 72.dp, top = 16.dp, bottom = 12.dp))
        Row(
            Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(48.dp).clip(RoundedCornerShape(6.dp)).background(Color.White).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Rounded.Search, null, tint = Color.Black, modifier = Modifier.size(24.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) Text("What do you want to listen to?", color = Color(0xFF6A6A6A), fontSize = 15.sp, fontWeight = FontWeight.Medium)
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Color.Black, fontSize = 16.sp, fontWeight = FontWeight.Medium),
                    cursorBrush = SolidColor(Color.Black),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {}),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (query.isNotEmpty()) {
                Icon(Icons.Rounded.Close, "Clear", tint = Color.Black, modifier = Modifier.size(22.dp).clip(CircleShape).clickable { query = "" })
            }
        }
        Spacer(Modifier.height(12.dp))

        if (query.isBlank()) {
            LazyColumn(contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = LocalMusicBottomPad.current)) {
                item { Text("Browse all", color = Sp.text, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)) }
                if (genres.isEmpty()) item { Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Sp.green) } }
                items(genres.chunked(2)) { pair ->
                    Row(Modifier.padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        pair.forEach { g ->
                            Box(
                                Modifier.weight(1f).aspectRatio(1.9f).clip(RoundedCornerShape(6.dp)).background(tileColor(g))
                                    .clickable { onOpen(MusicDest.Genre(g.browseId, g.params, g.title)) }.padding(10.dp),
                            ) {
                                Text(g.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        } else {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(SearchFilter.entries.toList()) { f -> Pill(f.label, filter == f) { filter = f } }
            }
            Spacer(Modifier.height(8.dp))
            val songs: List<Song> = rows.mapNotNull { (it as? SearchResult.Track)?.song ?: (it as? SearchResult.TopTrack)?.song }
            LazyColumn(contentPadding = PaddingValues(bottom = LocalMusicBottomPad.current)) {
                if (loading && rows.isEmpty()) item { Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Sp.green) } }
                error?.let { msg -> item { Text(msg, color = Sp.sub, fontSize = 14.sp, modifier = Modifier.padding(16.dp)) } }
                if (!loading && error == null && rows.isEmpty()) item { Text("No results for \"${query.trim()}\".", color = Sp.sub, fontSize = 14.sp, modifier = Modifier.padding(16.dp)) }
                items(rows.size) { i ->
                    when (val row = rows[i]) {
                        is SearchResult.TopTrack -> SongRow(row.song, onClick = { MusicPlayer.play(songs, songs.indexOfFirst { it.videoId == row.song.videoId }.coerceAtLeast(0)) })
                        is SearchResult.Track -> SongRow(row.song, onClick = { MusicPlayer.play(songs, songs.indexOfFirst { it.videoId == row.song.videoId }.coerceAtLeast(0)) })
                        is SearchResult.Browse -> BrowseRow(row, onOpen)
                    }
                }
            }
        }
    }
}

@Composable
private fun BrowseRow(row: SearchResult.Browse, onOpen: (MusicDest) -> Unit) {
    val item = row.item
    val round = item.type == BrowseType.ARTIST
    Row(
        Modifier.fillMaxWidth().clickable { onOpen(MusicDest.Browse(item.browseId, item.title, item.thumbnailUrl)) }.padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Cover(item.thumbnailUrl, Modifier.size(52.dp), shape = if (round) CircleShape else RoundedCornerShape(4.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, color = Sp.text, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.subtitle.ifBlank { item.type.name.lowercase().replaceFirstChar { it.uppercase() } }, color = Sp.sub, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
