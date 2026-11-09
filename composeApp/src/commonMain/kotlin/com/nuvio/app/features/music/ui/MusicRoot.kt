package com.nuvio.app.features.music.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.PlatformBackHandler
import com.nuvio.app.core.ui.nuvioSafeBottomPadding
import com.nuvio.app.features.music.playback.MusicPlayer

/** The bottom space a music screen must leave for the tab bar (and the mini player when it shows). */
internal val LocalMusicBottomPad = compositionLocalOf { 0.dp }

internal enum class MusicTab { Home, Search, Library }

/**
 * One tab of the music section: the tab's screen, the pages pushed on top of it, the mini player and
 * the full Now Playing screen.
 */
@Composable
internal fun MusicRoot(tab: MusicTab, modifier: Modifier = Modifier) {
    MusicHost(modifier) { push ->
        when (tab) {
            MusicTab.Home -> MusicHomeScreen(push)
            MusicTab.Search -> MusicSearchScreen(push)
            MusicTab.Library -> MusicLibraryScreen(push)
        }
    }
}

/**
 * The shell shared by every screen that can open music pages: it keeps the page stack, the mini
 * player and Now Playing. [content] is the base screen and gets a function that pushes a page.
 */
@Composable
internal fun MusicHost(modifier: Modifier = Modifier, content: @Composable (push: (MusicDest) -> Unit) -> Unit) {
    val state by MusicPlayer.state.collectAsStateWithLifecycle()
    var stack by remember { mutableStateOf(listOf<MusicDest>()) }
    var showPlayer by remember { mutableStateOf(false) }
    val barPad: Dp = nuvioSafeBottomPadding()
    val contentPad = barPad + if (state.hasSong) 72.dp else 0.dp
    val push: (MusicDest) -> Unit = { stack = stack + it }

    PlatformBackHandler(enabled = stack.isNotEmpty() && !showPlayer, onBack = { stack = stack.dropLast(1) })

    CompositionLocalProvider(LocalMusicBottomPad provides contentPad) {
        Box(modifier.fillMaxSize().background(Sp.bg)) {
            val top = stack.lastOrNull()
            if (top != null) {
                MusicDestScreen(top, onBack = { stack = stack.dropLast(1) }, onOpen = push)
            } else {
                content(push)
            }
            if (state.hasSong) {
                MiniPlayer(onOpen = { showPlayer = true }, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = barPad + 6.dp))
            }
            AnimatedVisibility(
                visible = showPlayer,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
            ) {
                NowPlayingScreen(onClose = { showPlayer = false })
            }
        }
    }
}
