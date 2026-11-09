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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.nuvio.app.core.ui.NativeTabBridge
import com.nuvio.app.core.ui.PlatformBackHandler
import com.nuvio.app.features.music.MusicLibrary
import com.nuvio.app.features.music.MusicLyricsRepository
import com.nuvio.app.features.music.playback.MusicPlayer
import com.nuvio.app.features.music.playback.RepeatMode

private fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "${total / 60}:${(total % 60).toString().padStart(2, '0')}"
}

/** The compact player bar above the tab bar. */
@Composable
internal fun MiniPlayer(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val state by MusicPlayer.state.collectAsStateWithLifecycle()
    val liked by MusicLibrary.liked.collectAsStateWithLifecycle()
    val song = state.song ?: return
    val isLiked = liked.any { it.videoId == song.videoId }
    Box(modifier.padding(horizontal = 8.dp).fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFF3B3B3B)).clickable(onClick = onOpen)) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Cover(song.thumbnailUrl, Modifier.size(44.dp), shape = RoundedCornerShape(4.dp))
            Column(Modifier.weight(1f)) {
                Text(song.title, color = Sp.text, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(state.error ?: song.artist, color = if (state.error != null) Color(0xFFFF7B7B) else Sp.sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(
                if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                "Like",
                tint = if (isLiked) Sp.green else Sp.text,
                modifier = Modifier.size(26.dp).clip(CircleShape).clickable { MusicLibrary.toggleLike(song) },
            )
            if (state.isBuffering) {
                CircularProgressIndicator(color = Sp.text, strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
            } else {
                Icon(
                    if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    "Play or pause",
                    tint = Sp.text,
                    modifier = Modifier.size(32.dp).clip(CircleShape).clickable { MusicPlayer.togglePlay() },
                )
            }
        }
        val progress = if (state.durationMs > 0) (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f
        Box(Modifier.align(Alignment.BottomStart).padding(horizontal = 8.dp).fillMaxWidth().height(2.dp).background(Color(0x40FFFFFF))) {
            Box(Modifier.fillMaxWidth(progress).height(2.dp).background(Sp.text))
        }
    }
}

/** The full-screen player: big artwork, scrubber, transport, synced lyrics and the queue. */
@Composable
internal fun NowPlayingScreen(onClose: () -> Unit) {
    val state by MusicPlayer.state.collectAsStateWithLifecycle()
    val liked by MusicLibrary.liked.collectAsStateWithLifecycle()
    val song = state.song
    PlatformBackHandler(enabled = true, onBack = onClose)
    DisposableEffect(Unit) {
        NativeTabBridge.publishTabBarVisible(false)
        onDispose { NativeTabBridge.publishTabBarVisible(true) }
    }
    if (song == null) {
        LaunchedEffect(Unit) { onClose() }
        return
    }
    var scrubbing by remember { mutableStateOf<Float?>(null) }
    var showQueue by remember { mutableStateOf(false) }
    val isLiked = liked.any { it.videoId == song.videoId }
    var lyrics by remember(song.videoId) { mutableStateOf<MusicLyricsRepository.Lyrics?>(MusicLyricsRepository.cached(song.videoId)) }
    var lyricsLoading by remember(song.videoId) { mutableStateOf(lyrics == null) }
    LaunchedEffect(song.videoId) {
        if (lyrics == null) {
            lyrics = MusicLyricsRepository.lyrics(song)
            lyricsLoading = false
        }
    }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF5A4A3A), Color(0xFF2A2A2A), Sp.bg)))) {
        LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.KeyboardArrowDown, "Close", tint = Sp.text, modifier = Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onClose))
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("PLAYING FROM", color = Sp.text, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Text(song.albumName ?: song.artist, color = Sp.text, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(Icons.AutoMirrored.Rounded.QueueMusic, "Queue", tint = Sp.text, modifier = Modifier.size(28.dp).clip(CircleShape).clickable { showQueue = !showQueue })
                }
            }
            item {
                Cover(song.thumbnailUrl, Modifier.padding(horizontal = 24.dp, vertical = 16.dp).fillMaxWidth().aspectRatio(1f), shape = RoundedCornerShape(8.dp))
            }
            item {
                Row(Modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(song.title, color = Sp.text, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(song.artist, color = Sp.sub, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(
                        if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        "Like",
                        tint = if (isLiked) Sp.green else Sp.text,
                        modifier = Modifier.size(30.dp).clip(CircleShape).clickable { MusicLibrary.toggleLike(song) },
                    )
                }
                state.error?.let { Text(it, color = Color(0xFFFF7B7B), fontSize = 13.sp, modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)) }
            }
            item {
                val duration = state.durationMs.coerceAtLeast(1L)
                val fraction = scrubbing ?: (state.positionMs.toFloat() / duration).coerceIn(0f, 1f)
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Slider(
                        value = fraction,
                        onValueChange = { scrubbing = it },
                        onValueChangeFinished = { scrubbing?.let { MusicPlayer.seekTo((it * duration).toLong()) }; scrubbing = null },
                        colors = SliderDefaults.colors(thumbColor = Sp.text, activeTrackColor = Sp.text, inactiveTrackColor = Color(0x55FFFFFF)),
                    )
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(formatTime((fraction * duration).toLong()), color = Sp.sub, fontSize = 12.sp)
                        Text(formatTime(state.durationMs), color = Sp.sub, fontSize = 12.sp)
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Shuffle, "Shuffle", tint = if (state.shuffle) Sp.green else Sp.text, modifier = Modifier.size(28.dp).clip(CircleShape).clickable { MusicPlayer.toggleShuffle() })
                    Icon(Icons.Rounded.SkipPrevious, "Previous", tint = Sp.text, modifier = Modifier.size(44.dp).clip(CircleShape).clickable { MusicPlayer.previous() })
                    Box(Modifier.size(68.dp).clip(CircleShape).background(Sp.text).clickable { MusicPlayer.togglePlay() }, contentAlignment = Alignment.Center) {
                        if (state.isBuffering) CircularProgressIndicator(color = Color.Black, strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
                        else Icon(if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play or pause", tint = Color.Black, modifier = Modifier.size(40.dp))
                    }
                    Icon(Icons.Rounded.SkipNext, "Next", tint = Sp.text, modifier = Modifier.size(44.dp).clip(CircleShape).clickable { MusicPlayer.next() })
                    Icon(
                        if (state.repeat == RepeatMode.One) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                        "Repeat",
                        tint = if (state.repeat == RepeatMode.Off) Sp.text else Sp.green,
                        modifier = Modifier.size(28.dp).clip(CircleShape).clickable { MusicPlayer.cycleRepeat() },
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
            item { LyricsCard(lyrics, lyricsLoading, state.positionMs) }
            if (showQueue) {
                item { Text("Next in queue", color = Sp.text, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) }
                itemsIndexed(state.queue) { index, queued ->
                    if (index > state.index) SongRow(queued, onClick = { MusicPlayer.jumpTo(index) })
                }
            }
        }
    }
}

@Composable
private fun LyricsCard(lyrics: MusicLyricsRepository.Lyrics?, loading: Boolean, positionMs: Long) {
    Column(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF7A5A3A)).padding(20.dp),
    ) {
        Text("Lyrics", color = Sp.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        when {
            loading -> CircularProgressIndicator(color = Sp.text, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
            lyrics == null -> Text("No lyrics for this track.", color = Color(0xCCFFFFFF), fontSize = 15.sp)
            else -> {
                val lines = lyrics.lines.filter { it.text.isNotBlank() }
                if (!lyrics.hasTiming) {
                    lines.take(14).forEach { Text(it.text, color = Sp.text, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 4.dp)) }
                } else {
                    val current = lines.indexOfLast { it.timeMs <= positionMs }.coerceAtLeast(0)
                    lines.drop((current - 1).coerceAtLeast(0)).take(8).forEachIndexed { offset, line ->
                        val absolute = (current - 1).coerceAtLeast(0) + offset
                        Text(
                            line.text,
                            color = when {
                                absolute == current -> Sp.text
                                absolute < current -> Color(0x99FFFFFF)
                                else -> Color(0xB3FFFFFF)
                            },
                            fontSize = if (absolute == current) 24.sp else 20.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 5.dp),
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(lyrics.source, color = Color(0xB3FFFFFF), fontSize = 11.sp)
            }
        }
    }
    Spacer(Modifier.height(16.dp))
}
