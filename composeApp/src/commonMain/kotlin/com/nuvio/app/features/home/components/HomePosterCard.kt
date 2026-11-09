package com.nuvio.app.features.home.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.nuvio.app.core.format.formatReleaseDateForDisplay
import com.nuvio.app.core.ui.NuvioPosterCard
import com.nuvio.app.core.ui.NuvioPosterShape
import com.nuvio.app.core.ui.PosterCardStyleUiState
import com.nuvio.app.core.ui.PosterPrefetchTarget
import com.nuvio.app.core.ui.rememberPosterCardStyleUiState
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.PosterShape

@Composable
fun HomePosterCard(
    item: MetaPreview,
    modifier: Modifier = Modifier,
    useLandscapeBackdropMode: Boolean = false,
    isWatched: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    showLandscapeOverlay: Boolean = true,
) {
    val posterCardStyle = rememberPosterCardStyleUiState()
    val isLandscapeMode = useLandscapeBackdropMode || posterCardStyle.catalogLandscapeModeEnabled
    val effectiveLandscapePoster = if (posterCardStyle.alwaysShowLandscapeClearlogo) null else item.landscapePoster
    val images = item.homePosterImages(posterCardStyle, useLandscapeBackdropMode)

    NuvioPosterCard(
        title = item.name,
        imageUrl = images.imageUrl,
        modifier = modifier,
        fallbackImageUrl = images.fallbackImageUrl,
        shape = images.shape,
        detailLine = if (isLandscapeMode || posterCardStyle.hideLabelsEnabled) null else item.releaseInfo?.let { formatReleaseDateForDisplay(it) },
        showTitleBelow = !posterCardStyle.hideLabelsEnabled,
        bottomLeftLogoUrl = if (isLandscapeMode && showLandscapeOverlay && effectiveLandscapePoster.isNullOrBlank()) item.logo else null,
        bottomLeftText = if (isLandscapeMode && showLandscapeOverlay && effectiveLandscapePoster.isNullOrBlank() && item.logo.isNullOrBlank() && !posterCardStyle.hideLabelsEnabled) item.name else null,
        isWatched = isWatched,
        onClick = onClick,
        onLongClick = onLongClick,
    )
}

internal data class HomePosterImages(
    val imageUrl: String?,
    val fallbackImageUrl: String?,
    val shape: NuvioPosterShape,
)

internal fun MetaPreview.homePosterImages(
    posterCardStyle: PosterCardStyleUiState,
    useLandscapeBackdropMode: Boolean,
): HomePosterImages {
    val isLandscapeMode = useLandscapeBackdropMode || posterCardStyle.catalogLandscapeModeEnabled
    val effectiveLandscapePoster = if (posterCardStyle.alwaysShowLandscapeClearlogo) null else landscapePoster
    val imageUrl = if (isLandscapeMode) {
        effectiveLandscapePoster ?: banner ?: poster
    } else poster
    val fallbackImageUrl = if (isLandscapeMode && !effectiveLandscapePoster.isNullOrBlank()) {
        // Landscape custom poster -> fall back to original backdrop, then portrait
        banner ?: rawPosterUrl
    } else {
        rawPosterUrl
    }
    return HomePosterImages(
        imageUrl = imageUrl,
        fallbackImageUrl = fallbackImageUrl,
        shape = if (isLandscapeMode) NuvioPosterShape.Landscape else posterShape.toNuvioPosterShape(),
    )
}

internal fun HomePosterImages.toPrefetchTarget(): PosterPrefetchTarget? {
    val url = imageUrl?.takeIf { it.isNotBlank() } ?: return null
    val fallback = fallbackImageUrl?.takeIf { it.isNotBlank() && it != url }
    return PosterPrefetchTarget(imageUrl = url, fallbackImageUrl = fallback, shape = shape)
}

private fun PosterShape.toNuvioPosterShape(): NuvioPosterShape =
    when (this) {
        PosterShape.Poster -> NuvioPosterShape.Poster
        PosterShape.Square -> NuvioPosterShape.Square
        PosterShape.Landscape -> NuvioPosterShape.Landscape
    }
