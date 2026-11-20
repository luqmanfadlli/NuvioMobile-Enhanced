package com.nuvio.app.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.request.ImageRequest

@Composable
internal actual fun NuvioAsyncImage(
    imageUrl: String,
    contentDescription: String,
    modifier: Modifier,
    contentScale: ContentScale,
    animateIfPossible: Boolean,
    fallbackImageUrl: String?,
) {
    val context = LocalContext.current
    val fallback = fallbackImageUrl?.takeIf { it.isNotBlank() && it != imageUrl }
    var useFallback by remember(imageUrl, fallback) { mutableStateOf(false) }
    val activeUrl = if (useFallback && fallback != null) fallback else imageUrl
    val request: ImageRequest = remember(context, activeUrl) {
        ImageRequest.Builder(context)
            .data(activeUrl)
            .memoryCacheKey("home-collection:$activeUrl")
            .diskCacheKey(activeUrl)
            .build()
    }

    AsyncImage(
        model = request,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        onError = { if (fallback != null) useFallback = true },
    )
}