package com.nuvio.app.features.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage

/**
 * Reuses artwork already resolved by player metadata. The title stays visible until
 * Coil confirms that the replacement artwork has loaded successfully.
 */
@Composable
internal fun PlayerTitleArtwork(
    title: String,
    logoUrl: String?,
    showLogo: Boolean,
    textStyle: TextStyle,
    textColor: Color,
    maxLines: Int,
    maxLogoHeight: Dp,
    maxLogoWidth: Dp,
    modifier: Modifier = Modifier,
    imageLoadedState: MutableState<Boolean>? = null,
) {
    val url = logoUrl?.takeIf { it.isNotBlank() }
    val localImageLoaded = remember(url, showLogo) { mutableStateOf(false) }
    val loadedState = imageLoadedState ?: localImageLoaded
    val showArtwork = showPlayerTitleLogoArtwork(showLogo, url, loadedState.value)

    if (showLogo && url != null) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.CenterStart,
        ) {
            if (!showArtwork) {
                Text(
                    text = title,
                    style = textStyle,
                    color = textColor,
                    maxLines = maxLines,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            AsyncImage(
                model = url,
                contentDescription = if (showArtwork) title else null,
                contentScale = ContentScale.Fit,
                alignment = Alignment.CenterStart,
                onSuccess = { loadedState.value = true },
                modifier = Modifier
                    .wrapContentSize(Alignment.CenterStart)
                    .sizeIn(
                        maxWidth = maxLogoWidth,
                        maxHeight = maxLogoHeight,
                    )
                    .graphicsLayer {
                        alpha = if (showArtwork) 1f else 0f
                    },
            )
        }
    } else {
        Text(
            text = title,
            style = textStyle,
            color = textColor,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier,
        )
    }
}

internal fun showPlayerTitleLogoArtwork(
    enabled: Boolean,
    logoUrl: String?,
    imageLoaded: Boolean,
): Boolean = enabled && !logoUrl.isNullOrBlank() && imageLoaded
