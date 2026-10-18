package com.nuvio.app.core.ui.glass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy

internal val GlassSurfaceColor = Color(0xFF1C1C1E)

internal val GlassBarShape = RoundedCornerShape(50)

/**
 * The backdrop of the floating navigation pill: Kyant's liquid glass (vibrancy, blur and lens
 * refraction) tuned by [LiquidGlassStyle]. Without a backdrop, on devices without RenderEffect or
 * with the solid material it is [SolidGlassBar].
 */
@Composable
internal fun GlassBarSurface(
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    style: LiquidGlassStyle = LiquidGlassStyle.Default,
    exportedBackdrop: LayerBackdrop? = null,
) {
    if (backdrop != null && style.usesBackdrop && isLiquidGlassSupported()) {
        LiquidGlassBar(backdrop, style, exportedBackdrop, modifier)
    } else {
        SolidGlassBar(modifier, style.edgeHighlight)
    }
}

@Composable
private fun LiquidGlassBar(
    backdrop: Backdrop,
    style: LiquidGlassStyle,
    exportedBackdrop: LayerBackdrop?,
    modifier: Modifier,
) {
    Box(
        modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { GlassBarShape },
            effects = {
                if (style.vibrancy) vibrancy()
                if (style.usesBlur && style.blurRadius > 0) blur(style.blurRadius.dp.toPx())
                if (style.usesLens && style.refraction > 0) {
                    val refraction = style.refraction.dp.toPx()
                    lens(
                        refractionHeight = refraction.coerceAtMost(size.minDimension / 2f),
                        refractionAmount = refraction,
                        chromaticAberration = style.chromaticAberration,
                    )
                }
            },
            highlight = if (style.edgeHighlight) DefaultGlassHighlight else null,
            shadow = if (style.shadow) DefaultGlassShadow else null,
            exportedBackdrop = exportedBackdrop,
            onDrawSurface = {
                drawRect(GlassSurfaceColor.copy(alpha = style.tintPercent / 100f))
            },
        ),
    )
}

/** A dark fill and a thin top-lit edge: the glass bar without any backdrop. */
@Composable
internal fun SolidGlassBar(modifier: Modifier, edgeHighlight: Boolean = true) {
    Box(
        modifier
            .drawWithCache {
                val fill = GlassSurfaceColor.copy(alpha = 0.82f)
                val edge = Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.27f), Color.White.copy(alpha = 0.02f)),
                )
                val width = 0.75.dp.toPx()
                onDrawBehind {
                    drawRoundRect(fill, cornerRadius = CornerRadius(size.height / 2))
                    if (edgeHighlight) {
                        drawRoundRect(
                            brush = edge,
                            topLeft = Offset(width / 2, width / 2),
                            size = Size(size.width - width, size.height - width),
                            cornerRadius = CornerRadius((size.height - width) / 2),
                            style = Stroke(width),
                        )
                    }
                }
            },
    )
}
