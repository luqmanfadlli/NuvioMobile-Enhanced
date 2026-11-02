package com.nuvio.app.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import com.nuvio.app.features.settings.ThemeSettingsRepository

val LocalDynamicArtworkBackgroundActive = staticCompositionLocalOf { false }

val LocalDynamicArtworkBackgroundColor = staticCompositionLocalOf<State<Color>?> { null }

private const val DOMINANT_BLEND_FRACTION = 0.42f

private const val COLOR_TRANSITION_MS = 600

@Composable
fun rememberDynamicArtworkBackgroundEnabled(): Boolean {
    val enabled by ThemeSettingsRepository.dynamicArtworkBackgroundEnabled.collectAsState()
    return enabled
}

@Composable
fun DynamicArtworkBackground(
    artworkUrl: String?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val active = rememberDynamicArtworkBackgroundEnabled()
    if (!active) {
        CompositionLocalProvider(LocalDynamicArtworkBackgroundActive provides LocalAppBackgroundActive.current) {
            content()
        }
        return
    }

    val colorScheme = MaterialTheme.colorScheme
    val baseColor = colorScheme.background
    val tint = rememberArtworkTint(artworkUrl)
    var lastTint by remember { mutableStateOf<Color?>(null) }
    val currentTint: Color? = when (tint) {
        ArtworkTint.Pending -> lastTint
        ArtworkTint.None -> null
        is ArtworkTint.Tinted -> tint.value
    }
    if (tint != ArtworkTint.Pending) lastTint = currentTint
    val targetColor = currentTint
        ?.let { baseColor.blendTowards(it, DOMINANT_BLEND_FRACTION) }
        ?: baseColor

    val washColor = animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(durationMillis = COLOR_TRANSITION_MS, easing = LinearOutSlowInEasing),
        label = "dynamic_artwork_wash_color",
    )

    Box(modifier = modifier.fillMaxSize().drawBehind { drawRect(washColor.value) }) {
        CompositionLocalProvider(
            LocalDynamicArtworkBackgroundActive provides true,
            LocalDynamicArtworkBackgroundColor provides washColor,
        ) {
            content()
        }
    }
}

private fun Color.blendTowards(target: Color, fraction: Float): Color {
    val clamped = fraction.coerceIn(0f, 1f)
    return Color(
        red = red + (target.red - red) * clamped,
        green = green + (target.green - green) * clamped,
        blue = blue + (target.blue - blue) * clamped,
        alpha = alpha + (target.alpha - alpha) * clamped,
    )
}
