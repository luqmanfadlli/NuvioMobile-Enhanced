package com.nuvio.app.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState

@Composable
internal fun rememberPosterCardStyleUiState(): PosterCardStyleUiState {
    PosterCardStyleRepository.ensureLoaded()
    val shared = LocalPosterCardStyleState.current
    if (shared != null) return shared.value
    return PosterCardStyleRepository.uiState.collectAsState().value
}
