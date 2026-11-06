package com.nuvio.app.core.ui

import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

import androidx.compose.ui.graphics.drawscope.clipPath

@Composable
fun rememberCardDepthStyleUiState(): CardDepthStyleUiState {
    CardDepthStyleRepository.ensureLoaded()
    val shared = LocalCardDepthStyleState.current
    if (shared != null) return shared.value
    return CardDepthStyleRepository.uiState.collectAsState().value
}

@Composable
fun Modifier.nuvioCardDepth(
    shape: Shape,
    surface: NuvioCardDepthSurface,
    fallbackBorderAlpha: Float = 0f,
): Modifier {
    val state = rememberCardDepthStyleUiState()
    if (!state.isEnabledFor(surface)) {
        return if (fallbackBorderAlpha > 0f) {
            border(
                width = 1.dp,
                color = Color.White.copy(alpha = fallbackBorderAlpha),
                shape = shape,
            )
        } else {
            this
        }
    }

    return cardDepthVisual(
        shape = shape,
        edgeStrength = state.edgeStrength.toFloat(),
        sheenStrength = state.sheenStrength.toFloat(),
        edgeCoverage = state.edgeCoverage.toFloat(),
    )
}

fun Modifier.cardDepthVisual(
    shape: Shape,
    edgeStrength: Float,
    sheenStrength: Float,
    edgeCoverage: Float = DefaultCardDepthEdgeCoverage.toFloat(),
): Modifier {
    val edgeTop = edgeStrength.coerceIn(0f, 100f) / 100f
    val sheen = sheenStrength.coerceIn(0f, 100f) / 100f
    val coverage = edgeCoverage.coerceIn(0f, 100f) / 100f

    val withEdge = if (edgeTop > 0f) {
        border(
            width = 1.dp,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = edgeTop),
                    Color.White.copy(alpha = edgeTop * (0.33f + 0.67f * coverage)),
                    Color.White.copy(alpha = edgeTop * coverage),
                ),
            ),
            shape = shape,
        )
    } else {
        this
    }

    return if (sheen > 0f) {
        withEdge.drawWithCache {
            val sheenHeight = size.height * 0.22f
            if (sheenHeight <= 0f) {
                return@drawWithCache onDrawWithContent { drawContent() }
            }
            val shapePath = shape.createOutline(size, layoutDirection, this).toPath()
            val sheenBrush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = sheen),
                    Color.Transparent,
                ),
                startY = 0f,
                endY = sheenHeight,
            )
            val sheenSize = Size(size.width, sheenHeight)
            onDrawWithContent {
                drawContent()
                clipPath(shapePath) {
                    drawRect(brush = sheenBrush, size = sheenSize)
                }
            }
        }
    } else {
        withEdge
    }
}

private fun androidx.compose.ui.graphics.Outline.toPath(): androidx.compose.ui.graphics.Path = when (this) {
    is androidx.compose.ui.graphics.Outline.Rectangle -> androidx.compose.ui.graphics.Path().apply { addRect(rect) }
    is androidx.compose.ui.graphics.Outline.Rounded -> androidx.compose.ui.graphics.Path().apply { addRoundRect(roundRect) }
    is androidx.compose.ui.graphics.Outline.Generic -> path
}

