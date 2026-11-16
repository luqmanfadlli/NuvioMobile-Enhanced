package com.nuvio.app.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import kotlinx.coroutines.launch

data class ProfileMeshPalette(
    val base: Color,
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
)

@Composable
fun ProfileMeshBackground(
    profileColor: Color,
    palette: ProfileMeshPalette,
    modifier: Modifier = Modifier,
) {
    val animatedProfileColor = animateColorAsState(
        targetValue = profileColor,
        animationSpec = tween(durationMillis = 520),
        label = "profileMeshBackgroundColor",
    )
    // Start from a balanced night palette, then ease into the current time-of-day colors on entry.
    val animatedBase = remember { Animatable(Color(0xFF090D16)) }
    val animatedPrimary = remember { Animatable(Color(0xFF50617A)) }
    val animatedSecondary = remember { Animatable(Color(0xFF665C83)) }
    val animatedTertiary = remember { Animatable(Color(0xFF315C68)) }

    LaunchedEffect(palette) {
        val duration = 1_500
        launch {
            animatedBase.animateTo(
                palette.base,
                animationSpec = tween(durationMillis = duration, easing = FastOutSlowInEasing),
            )
        }
        launch {
            animatedPrimary.animateTo(
                palette.primary,
                animationSpec = tween(durationMillis = duration, easing = FastOutSlowInEasing),
            )
        }
        launch {
            animatedSecondary.animateTo(
                palette.secondary,
                animationSpec = tween(durationMillis = duration, easing = FastOutSlowInEasing),
            )
        }
        launch {
            animatedTertiary.animateTo(
                palette.tertiary,
                animationSpec = tween(durationMillis = duration, easing = FastOutSlowInEasing),
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                val maxDimension = maxOf(size.width, size.height)
                val baseColor = animatedBase.value
                val primaryTone = animatedPrimary.value
                val secondaryTone = animatedSecondary.value
                val tertiaryTone = animatedTertiary.value
                val profileAccent = lerp(animatedProfileColor.value, primaryTone, 0.3f)
                val primaryMeshColor = lerp(baseColor, primaryTone, 0.68f)
                val secondaryMeshColor = lerp(secondaryTone, profileAccent, 0.2f)
                val tertiaryMeshColor = lerp(tertiaryTone, profileAccent, 0.14f)

                drawRect(color = baseColor, size = size)
                drawRect(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0f to primaryMeshColor.copy(alpha = 0.46f),
                            0.38f to primaryMeshColor.copy(alpha = 0.2f),
                            0.72f to primaryMeshColor.copy(alpha = 0.05f),
                            1f to Color.Transparent,
                        ),
                        center = Offset(size.width * -0.08f, size.height * -0.02f),
                        radius = maxDimension * 0.7f,
                    ),
                    size = size,
                )
                drawRect(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0f to profileAccent.copy(alpha = 0.22f),
                            0.44f to profileAccent.copy(alpha = 0.085f),
                            0.78f to profileAccent.copy(alpha = 0.02f),
                            1f to Color.Transparent,
                        ),
                        center = Offset(size.width * 0.16f, size.height * 0.18f),
                        radius = maxDimension * 0.46f,
                    ),
                    size = size,
                )
                drawRect(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0f to secondaryMeshColor.copy(alpha = 0.16f),
                            0.5f to secondaryMeshColor.copy(alpha = 0.05f),
                            1f to Color.Transparent,
                        ),
                        center = Offset(size.width * 0.9f, size.height * 0.12f),
                        radius = maxDimension * 0.36f,
                    ),
                    size = size,
                )
                drawRect(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0f to tertiaryMeshColor.copy(alpha = 0.08f),
                            0.52f to tertiaryMeshColor.copy(alpha = 0.03f),
                            1f to Color.Transparent,
                        ),
                        center = Offset(size.width * 0.28f, size.height * 0.4f),
                        radius = maxDimension * 0.28f,
                    ),
                    size = size,
                )
            },
    )
}
