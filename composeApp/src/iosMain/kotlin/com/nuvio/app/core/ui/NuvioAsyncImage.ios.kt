package com.nuvio.app.core.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage

@Composable
internal actual fun NuvioAsyncImage(
    imageUrl: String,
    contentDescription: String,
    modifier: Modifier,
    contentScale: ContentScale,
    animateIfPossible: Boolean,
    fallbackImageUrl: String?,
) {
    if (!animateIfPossible || !imageUrl.looksAnimated()) {
        StaticImageWithFallback(
            imageUrl = imageUrl,
            fallbackImageUrl = fallbackImageUrl,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
        )
        return
    }

    BoxWithConstraints(modifier = modifier) {
        val targetWidth = constraints.maxWidth
            .takeIf { it in 1..MaxAnimationTargetEdgePx }
            ?: DefaultAnimationTargetEdgePx
        val targetHeight = constraints.maxHeight
            .takeIf { it in 1..MaxAnimationTargetEdgePx }
            ?: DefaultAnimationTargetEdgePx

        val state = rememberAnimatedFrame(imageUrl, targetWidth, targetHeight)

        when {
            state.unavailable -> StaticImageWithFallback(
                imageUrl = imageUrl,
                fallbackImageUrl = fallbackImageUrl,
                contentDescription = contentDescription,
                modifier = Modifier.matchParentSize(),
                contentScale = contentScale,
            )

            state.bitmap != null -> Image(
                bitmap = state.bitmap,
                contentDescription = contentDescription,
                modifier = Modifier.matchParentSize(),
                contentScale = contentScale,
            )
        }
    }
}

@Composable
private fun StaticImageWithFallback(
    imageUrl: String,
    fallbackImageUrl: String?,
    contentDescription: String,
    modifier: Modifier,
    contentScale: ContentScale,
) {
    val fallback = fallbackImageUrl?.takeIf { it.isNotBlank() && it != imageUrl }
    var useFallback by remember(imageUrl, fallback) { mutableStateOf(false) }
    AsyncImage(
        model = if (useFallback && fallback != null) fallback else imageUrl,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        onError = { if (fallback != null) useFallback = true },
    )
}

private fun String.looksAnimated(): Boolean {
    val cleanUrl = substringBefore('?').substringBefore('#')
    return cleanUrl.endsWith(".gif", ignoreCase = true) ||
        cleanUrl.endsWith(".webp", ignoreCase = true)
}
