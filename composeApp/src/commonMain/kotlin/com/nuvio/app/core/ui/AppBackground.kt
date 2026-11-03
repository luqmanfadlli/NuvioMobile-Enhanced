package com.nuvio.app.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.nuvio.app.features.settings.ThemeSettingsRepository
import com.kyant.backdrop.Backdrop
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.BoxScope
import com.nuvio.app.core.ui.glass.isLiquidGlassAvailable
import com.nuvio.app.core.ui.glass.nuvioBackdropSource
import com.nuvio.app.core.ui.glass.rememberNuvioBackdrop

val LocalAppBackgroundActive = staticCompositionLocalOf { false }

val LocalAppBackgroundBackdrop = staticCompositionLocalOf<Backdrop?> { null }

private const val AppBackgroundScrimAlpha = 0.28f

@Composable
fun rememberAppBackgroundImageUri(): String? {
    val uri by remember {
        ThemeSettingsRepository.ensureLoaded()
        ThemeSettingsRepository.customSettingsBackgroundImageUri
    }.collectAsStateWithLifecycle()
    return uri
}

@Composable
fun AppBackgroundImage(modifier: Modifier = Modifier) {
    val imageUri = rememberAppBackgroundImageUri()
    val revision by remember {
        ThemeSettingsRepository.customSettingsBackgroundImageRevision
    }.collectAsStateWithLifecycle()
    val platformContext = LocalPlatformContext.current
    val request = remember(imageUri, revision, platformContext) {
        imageUri?.let { uri ->
            ImageRequest.Builder(platformContext)
                .data(uri)
                .memoryCacheKey("$uri#app-background-$revision")
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.DISABLED)
                .build()
        }
    }
    var loaded by remember(imageUri, revision) { mutableStateOf(false) }
    Box(modifier = modifier.background(MaterialTheme.colorScheme.background)) {
        request?.let { imageRequest ->
            AsyncImage(
                model = imageRequest,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                onSuccess = { loaded = true },
                onError = { loaded = false },
            )
            if (loaded) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = AppBackgroundScrimAlpha)),
                )
            }
        }
    }
}

@Composable
fun nuvioScreenBackground(ownsBackground: Boolean = false): Color =
    if (!ownsBackground && LocalAppBackgroundActive.current) {
        Color.Transparent
    } else {
        MaterialTheme.colorScheme.background
    }

@Composable
fun AppRouteBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    if (!LocalAppBackgroundActive.current) {
        Box(modifier = modifier.background(MaterialTheme.colorScheme.background), content = content)
        return
    }
    val backdrop = rememberNuvioBackdrop()
    val captureBackdrop = isLiquidGlassAvailable()
    Box(modifier = modifier) {
        AppBackgroundImage(
            modifier = Modifier
                .matchParentSize()
                .then(if (captureBackdrop) Modifier.nuvioBackdropSource(backdrop) else Modifier),
        )
        CompositionLocalProvider(
            LocalAppBackgroundBackdrop provides if (captureBackdrop) backdrop else null,
        ) {
            content()
        }
    }
}

@Composable
fun nuvioFieldContainerColor(solid: Color): Color =
    if (LocalAppBackgroundActive.current) {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    } else {
        solid
    }
