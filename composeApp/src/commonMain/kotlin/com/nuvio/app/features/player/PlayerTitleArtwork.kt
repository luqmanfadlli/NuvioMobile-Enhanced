package com.nuvio.app.features.player

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.nuvio.app.features.details.components.loadedBackdropImageBitmap
import com.nuvio.app.core.ui.background.softwareBitmap
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.size.Scale
import coil3.size.Size

/**
 * Replaces the title only after Coil has loaded the artwork successfully.
 * Until then (or after a failure), only the text title participates in layout.
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
) {
    val url = logoUrl?.takeIf { it.isNotBlank() }
    if (showLogo && url != null) {
        val platformContext = LocalPlatformContext.current
        val density = LocalDensity.current
        val requestWidth = with(density) { maxLogoWidth.roundToPx() }
        val requestHeight = with(density) { maxLogoHeight.roundToPx() }
        val imageRequest = remember(url, requestWidth, requestHeight, platformContext) {
            ImageRequest.Builder(platformContext)
                .data(url)
                .size(Size(requestWidth, requestHeight))
                .scale(Scale.FIT)
                .softwareBitmap()
                .build()
        }
        val painter = rememberAsyncImagePainter(
            model = imageRequest,
            contentScale = ContentScale.Fit,
        )
        val painterState by painter.state.collectAsState()
        val showArtwork = showPlayerTitleLogoArtwork(
            enabled = showLogo,
            logoUrl = url,
            imageLoaded = painterState is AsyncImagePainter.State.Success,
        )
        val loadedResult = (painterState as? AsyncImagePainter.State.Success)?.result
        val lightenLogo = remember(loadedResult) {
            loadedResult?.let { result ->
                runCatching {
                    val bitmap = loadedBackdropImageBitmap(result) ?: return@runCatching false
                    val pixels = IntArray(bitmap.width * bitmap.height)
                    bitmap.readPixels(pixels)
                    isDarkTitleLogo(pixels)
                }.getOrDefault(false)
            } ?: false
        }
        Box(
            modifier = modifier.fillMaxWidth().animateContentSize(),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (showArtwork) {
                Image(
                    painter = painter,
                    contentDescription = title,
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.CenterStart,
                    colorFilter = if (lightenLogo) ColorFilter.tint(Color.White, BlendMode.SrcIn) else null,
                    modifier = Modifier
                        .height(maxLogoHeight)
                        .widthIn(max = maxLogoWidth),
                )
            } else {
                PlayerTitleText(
                    title = title,
                    textStyle = textStyle,
                    textColor = textColor,
                    maxLines = maxLines,
                )
            }
        }
    } else {
        PlayerTitleText(
            title = title,
            textStyle = textStyle,
            textColor = textColor,
            maxLines = maxLines,
            modifier = modifier,
        )
    }
}

@Composable
private fun PlayerTitleText(
    title: String,
    textStyle: TextStyle,
    textColor: Color,
    maxLines: Int,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        style = textStyle,
        color = textColor,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

internal fun showPlayerTitleLogoArtwork(
    enabled: Boolean,
    logoUrl: String?,
    imageLoaded: Boolean,
): Boolean = enabled && !logoUrl.isNullOrBlank() && imageLoaded

/**
 * True when the visible (mostly opaque) pixels of a logo are dark on average, so it would be
 * unreadable on the dark player overlay. Pixels are ARGB; a sparse grid keeps it cheap.
 */
internal fun isDarkTitleLogo(pixels: IntArray, step: Int = 7): Boolean {
    var luminanceSum = 0.0
    var count = 0
    var index = 0
    while (index < pixels.size) {
        val pixel = pixels[index]
        if ((pixel ushr 24) >= 160) {
            val red = (pixel ushr 16) and 255
            val green = (pixel ushr 8) and 255
            val blue = pixel and 255
            luminanceSum += (0.2126 * red + 0.7152 * green + 0.0722 * blue) / 255.0
            count++
        }
        index += step
    }
    return count >= 16 && luminanceSum / count < 0.22
}
