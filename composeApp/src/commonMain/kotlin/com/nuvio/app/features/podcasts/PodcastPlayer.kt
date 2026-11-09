package com.nuvio.app.features.podcasts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Forward30
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.NativeTabBridge
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.PlatformBackHandler
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.music.playback.MusicPlayer

private val SPEEDS = listOf(0.8f, 1f, 1.25f, 1.5f, 1.75f, 2f)

private fun speedLabel(rate: Float): String =
    if (rate == rate.toInt().toFloat()) "${rate.toInt()}x" else "${rate}x"

private fun clock(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "$h:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}" else "$m:${s.toString().padStart(2, '0')}"
}

@Composable
private fun RoundIcon(icon: ImageVector, description: String, size: Int, onClick: () -> Unit, tint: androidx.compose.ui.graphics.Color) {
    Icon(
        icon,
        contentDescription = description,
        tint = tint,
        modifier = Modifier.size(size.dp).clip(CircleShape).clickable(onClick = onClick).padding((size / 6).dp),
    )
}

/** The bar that shows while an episode is loaded: what's playing, back 10, play or pause, forward 30. */
@Composable
fun PodcastMiniPlayer(modifier: Modifier = Modifier) {
    val state by MusicPlayer.state.collectAsStateWithLifecycle()
    if (!state.isPodcast) return
    val song = state.song ?: return
    val tokens = MaterialTheme.nuvio
    val shape = RoundedCornerShape(NuvioTokens.Radius.xl)
    Box(
        modifier.padding(horizontal = 12.dp).fillMaxWidth().clip(shape)
            .background(tokens.colors.surfaceElevated)
            .border(NuvioTokens.Border.hairline, tokens.colors.borderSubtle, shape)
            .clickable { PodcastPlayback.openNowPlaying() },
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ShowArtwork(song.thumbnailUrl, 44.dp, radius = NuvioTokens.Radius.md)
            Column(Modifier.weight(1f)) {
                Text(song.title, style = MaterialTheme.typography.bodyMedium, color = tokens.colors.textPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(state.error ?: song.artist, style = MaterialTheme.typography.bodySmall, color = if (state.error != null) tokens.colors.danger else tokens.colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            RoundIcon(Icons.Rounded.Replay10, "Back 10 seconds", 36, { MusicPlayer.skipBy(-10_000) }, tokens.colors.textPrimary)
            if (state.isBuffering) {
                CircularProgressIndicator(color = tokens.colors.textPrimary, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
            } else {
                RoundIcon(if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play or pause", 40, { MusicPlayer.togglePlay() }, tokens.colors.textPrimary)
            }
            RoundIcon(Icons.Rounded.Forward30, "Forward 30 seconds", 36, { MusicPlayer.skipBy(30_000) }, tokens.colors.textPrimary)
        }
        val progress = if (state.durationMs > 0) (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f
        Box(Modifier.align(Alignment.BottomStart).padding(horizontal = 10.dp).fillMaxWidth().height(2.dp).background(tokens.colors.textMuted.copy(alpha = 0.35f))) {
            Box(Modifier.fillMaxWidth(progress).height(2.dp).background(tokens.colors.accent))
        }
    }
}

/** Slides the full-screen player over everything when the mini player is tapped. Place once, at the app's root. */
@Composable
fun PodcastNowPlayingHost(modifier: Modifier = Modifier) {
    val open by PodcastPlayback.nowPlayingOpen.collectAsStateWithLifecycle()
    val state by MusicPlayer.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.isPodcast, open) {
        if (open && !state.isPodcast) PodcastPlayback.closeNowPlaying()
    }
    AnimatedVisibility(
        visible = open && state.isPodcast,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it }),
        modifier = modifier,
    ) {
        PodcastNowPlayingScreen(onClose = PodcastPlayback::closeNowPlaying)
    }
}

@Composable
private fun PodcastNowPlayingScreen(onClose: () -> Unit) {
    val tokens = MaterialTheme.nuvio
    val state by MusicPlayer.state.collectAsStateWithLifecycle()
    val song = state.song
    PlatformBackHandler(enabled = true, onBack = onClose)
    DisposableEffect(Unit) {
        NativeTabBridge.publishTabBarVisible(false)
        onDispose { NativeTabBridge.publishTabBarVisible(true) }
    }
    var scrubbing by remember { mutableStateOf<Float?>(null) }
    val duration = state.durationMs
    val fraction = scrubbing ?: if (duration > 0) (state.positionMs.toFloat() / duration).coerceIn(0f, 1f) else 0f

    Column(
        Modifier.fillMaxSize().background(tokens.colors.background).statusBarsPadding().padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            RoundIcon(Icons.Rounded.KeyboardArrowDown, "Close", 44, onClose, tokens.colors.textPrimary)
            Text(
                "Now playing",
                style = MaterialTheme.typography.labelLarge,
                color = tokens.colors.textMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.size(44.dp))
        }
        Spacer(Modifier.height(20.dp))
        Box(
            Modifier.fillMaxWidth(0.82f).aspectRatio(1f).clip(RoundedCornerShape(NuvioTokens.Radius.xl)).background(tokens.colors.surfaceElevated),
            contentAlignment = Alignment.Center,
        ) {
            if (song?.thumbnailUrl != null) {
                AsyncImage(model = song.thumbnailUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Icon(Icons.Rounded.Mic, contentDescription = null, tint = tokens.colors.textMuted, modifier = Modifier.size(72.dp))
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(
            song?.title.orEmpty(),
            style = MaterialTheme.typography.titleLarge,
            color = tokens.colors.textPrimary,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(song?.artist.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = tokens.colors.textMuted, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
        state.error?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = tokens.colors.danger, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(20.dp))
        Slider(
            value = fraction,
            onValueChange = { scrubbing = it },
            onValueChangeFinished = {
                scrubbing?.let { MusicPlayer.seekTo((it * duration).toLong()) }
                scrubbing = null
            },
            enabled = duration > 0,
            colors = SliderDefaults.colors(
                thumbColor = tokens.colors.textPrimary,
                activeTrackColor = tokens.colors.accent,
                inactiveTrackColor = tokens.colors.textMuted.copy(alpha = 0.35f),
            ),
        )
        Row(Modifier.fillMaxWidth()) {
            Text(clock((fraction * duration).toLong()), style = MaterialTheme.typography.labelSmall, color = tokens.colors.textMuted, modifier = Modifier.weight(1f))
            Text("-" + clock((duration - (fraction * duration).toLong()).coerceAtLeast(0L)), style = MaterialTheme.typography.labelSmall, color = tokens.colors.textMuted)
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            RoundIcon(Icons.Rounded.Replay10, "Back 10 seconds", 56, { MusicPlayer.skipBy(-10_000) }, tokens.colors.textPrimary)
            Box(
                Modifier.size(76.dp).clip(CircleShape).background(tokens.colors.accent).clickable { MusicPlayer.togglePlay() },
                contentAlignment = Alignment.Center,
            ) {
                if (state.isBuffering) {
                    CircularProgressIndicator(color = tokens.colors.onAccent, strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
                } else {
                    Icon(if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play or pause", tint = tokens.colors.onAccent, modifier = Modifier.size(40.dp))
                }
            }
            RoundIcon(Icons.Rounded.Forward30, "Forward 30 seconds", 56, { MusicPlayer.skipBy(30_000) }, tokens.colors.textPrimary)
        }
        Spacer(Modifier.height(24.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            SPEEDS.forEach { rate ->
                val selected = state.speed == rate
                val shape = RoundedCornerShape(999.dp)
                Box(
                    Modifier.clip(shape)
                        .background(if (selected) tokens.colors.accent else tokens.colors.surfaceElevated)
                        .clickable { MusicPlayer.setSpeed(rate) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(
                        speedLabel(rate),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) tokens.colors.onAccent else tokens.colors.textPrimary,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    )
                }
            }
        }
    }
}
