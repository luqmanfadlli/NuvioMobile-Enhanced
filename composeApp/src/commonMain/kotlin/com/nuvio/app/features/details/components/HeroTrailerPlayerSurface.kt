package com.nuvio.app.features.details.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun HeroTrailerPlayerSurface(
    sourceUrl: String,
    sourceAudioUrl: String?,
    playWhenReady: Boolean,
    muted: Boolean,
    modifier: Modifier,
    startPositionMs: Long = 0L,
    onReady: () -> Unit,
    onEnded: () -> Unit,
    onError: () -> Unit,
    onPositionUpdate: (Long) -> Unit = {},
)
