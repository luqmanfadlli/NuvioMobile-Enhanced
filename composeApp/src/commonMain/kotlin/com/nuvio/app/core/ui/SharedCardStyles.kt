package com.nuvio.app.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.staticCompositionLocalOf
import com.nuvio.app.features.settings.ThemeSettingsRepository

internal val LocalPosterCardStyleState = staticCompositionLocalOf<State<PosterCardStyleUiState>?> { null }
internal val LocalCardDepthStyleState = staticCompositionLocalOf<State<CardDepthStyleUiState>?> { null }
internal val LocalShowCatalogAccentState = staticCompositionLocalOf<State<Boolean>?> { null }

@Composable
internal fun SharedCardStylesProvider(content: @Composable () -> Unit) {
    PosterCardStyleRepository.ensureLoaded()
    CardDepthStyleRepository.ensureLoaded()
    val posterCardStyle = PosterCardStyleRepository.uiState.collectAsState()
    val cardDepthStyle = CardDepthStyleRepository.uiState.collectAsState()
    val showCatalogAccent = ThemeSettingsRepository.showCatalogAccentEnabled.collectAsState()
    CompositionLocalProvider(
        LocalPosterCardStyleState provides posterCardStyle,
        LocalCardDepthStyleState provides cardDepthStyle,
        LocalShowCatalogAccentState provides showCatalogAccent,
        content = content,
    )
}

@Composable
internal fun rememberShowCatalogAccentEnabled(): Boolean {
    val shared = LocalShowCatalogAccentState.current
    if (shared != null) return shared.value
    return ThemeSettingsRepository.showCatalogAccentEnabled.collectAsState().value
}
