package com.nuvio.app.features.details.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier

@Composable
actual fun HeroTrailerPlayerSurface(
    sourceUrl: String,
    sourceAudioUrl: String?,
    playWhenReady: Boolean,
    muted: Boolean,
    modifier: Modifier,
    startPositionMs: Long,
    onReady: () -> Unit,
    onEnded: () -> Unit,
    onError: () -> Unit,
    onPositionUpdate: (Long) -> Unit,
) {
    LaunchedEffect(sourceUrl) {
        onError()
    }
}
