package com.nuvio.app.features.player

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

/**
 * Reuses artwork already resolved by player metadata. Text remains the default and fallback.
 */
@Composable
internal fun PlayerTitleArtwork(
    title: String,
    logoUrl: String?,
    showLogo: Boolean,
    textStyle: TextStyle,
    textColor: Color,
    maxLines: Int,
    modifier: Modifier = Modifier,
) {
    var imageLoadFailed by remember(logoUrl) { mutableStateOf(false) }
    val url = logoUrl?.takeIf { it.isNotBlank() }
    if (showPlayerTitleLogoArtwork(showLogo, url, imageLoadFailed)) {
        val maxLogoHeight = with(LocalDensity.current) { textStyle.fontSize.toDp() * 2.4f }
        AsyncImage(
            model = url,
            contentDescription = title,
            contentScale = ContentScale.Fit,
            onError = { imageLoadFailed = true },
            modifier = modifier
                .heightIn(max = maxLogoHeight)
                .widthIn(max = 280.dp),
        )
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
    imageLoadFailed: Boolean,
): Boolean = enabled && !logoUrl.isNullOrBlank() && !imageLoadFailed
