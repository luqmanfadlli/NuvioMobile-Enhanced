package com.nuvio.app.features.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun StyledSubtitleOverlay(text: String, style: SubtitleStyleState, modifier: Modifier = Modifier) {
    val fontFamily = rememberSubtitleFontFamily(style.appearance.fontId)
    if (text.isBlank()) return
    BoxWithConstraints(modifier.fillMaxSize()) {
        val scale = (maxHeight / 360.dp).coerceAtMost(1f)
        Box(
            Modifier.fillMaxSize().padding(
                horizontal = 16.dp * scale,
                vertical = maxHeight * (0.0533f + style.bottomOffset.coerceIn(0, 200) / 1000f),
            ),
            contentAlignment = Alignment.BottomCenter,
        ) {
            StyledSubtitleText(text, style, scale = scale, fontFamily = fontFamily)
        }
    }
}

@Composable
internal fun StyledSubtitleText(
    text: String,
    style: SubtitleStyleState,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
    fontFamily: FontFamily = rememberSubtitleFontFamily(style.appearance.fontId),
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(modifier) {
        val paddingX = with(density) { (8.dp * scale).toPx() }
        val paddingY = with(density) { (4.dp * scale).toPx() }
        val availableWidth = constraints.maxWidth.toFloat()
        val maxTextWidth = (availableWidth - paddingX * 2).toInt().coerceAtLeast(1)
        val layout = measurer.measure(
            text = AnnotatedString(text),
            style = TextStyle(
                fontFamily = fontFamily,
                fontSize = (style.fontSizeSp * scale).sp,
                fontWeight = if (style.bold) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                textDirection = TextDirection.ContentOrLtr,
            ),
            constraints = Constraints(maxWidth = maxTextWidth),
        )
        val textHeight = with(density) { (layout.size.height + paddingY * 2).toDp() }
        val radius = with(density) { (style.appearance.backgroundRadius.dp * scale).toPx() }
        val outline = with(density) { (style.outlineWidth.dp * scale).toPx() }
        val outlineBlur = style.appearance.outlineBlur.coerceIn(
            SUBTITLE_OUTLINE_BLUR_MIN,
            SUBTITLE_OUTLINE_BLUR_MAX,
        )
        val outlineBlurDp = outlineBlur.dp * scale
        val outlineBlurPx = with(density) { outlineBlurDp.toPx() }
        val useNativeBlur = supportsNativeSubtitleBlur()
        val shadowSize = with(density) { (style.appearance.shadowSize.dp * scale).toPx() }
        val outlinePadding = outlineBlurDp
        Box(
            Modifier.fillMaxWidth()
                .height(textHeight + outlinePadding * 2)
                .offset(y = outlinePadding)
                .semantics { this.text = AnnotatedString(text) },
        ) {
            val boxWidth = layout.size.width + paddingX * 2
            val left = (availableWidth - boxWidth).coerceAtLeast(0f) / 2f
            val origin = Offset(left + paddingX, paddingY + outlineBlurPx)
            Canvas(Modifier.fillMaxSize()) {
                drawRoundRect(
                    color = style.backgroundColor,
                    topLeft = Offset(left, outlineBlurPx),
                    size = androidx.compose.ui.geometry.Size(
                        boxWidth,
                        (size.height - outlineBlurPx * 2).coerceAtLeast(0f),
                    ),
                    cornerRadius = CornerRadius(radius),
                )
            }
            if (style.outlineEnabled && outline > 0f) {
                val outlineModifier = if (outlineBlur > 0 && useNativeBlur) {
                    Modifier.blur(outlineBlurDp, BlurredEdgeTreatment.Unbounded)
                } else {
                    Modifier
                }
                Canvas(Modifier.fillMaxSize().then(outlineModifier)) {
                    val outlineColor = style.outlineColor.copy(
                        alpha = style.outlineColor.alpha * style.textColor.alpha,
                    )
                    if (outlineBlur > 0 && !useNativeBlur) {
                        // Fallback below Android 12: low-opacity outline passes approximate a soft
                        // Gaussian edge while keeping the foreground subtitle glyphs crisp.
                        val samples = 16
                        val rings = 3
                        val ringAlpha = 0.72f / rings / samples
                        for (ring in 1..rings) {
                            val distance = outlineBlurPx * ring / rings
                            for (sample in 0 until samples) {
                                val angle = sample * (2f * Math.PI.toFloat() / samples)
                                val dx = kotlin.math.cos(angle) * distance
                                val dy = kotlin.math.sin(angle) * distance
                                drawText(
                                    layout,
                                    color = outlineColor.copy(alpha = outlineColor.alpha * ringAlpha),
                                    topLeft = Offset(origin.x + dx, origin.y + dy),
                                    drawStyle = Stroke(outline * 2f),
                                    shadow = Shadow.None,
                                )
                            }
                        }
                    } else {
                        drawText(
                            layout,
                            color = outlineColor,
                            topLeft = origin,
                            drawStyle = Stroke(outline * 2f),
                            shadow = Shadow.None,
                        )
                    }
                }
            }
            Canvas(Modifier.fillMaxSize()) {
                drawText(
                    layout, color = style.textColor, topLeft = origin, drawStyle = Fill,
                    shadow = if (style.appearance.shadowEnabled) Shadow(
                        color = Color.Black.copy(alpha = style.appearance.shadowOpacity / 100f * style.textColor.alpha),
                        offset = Offset(shadowSize, shadowSize),
                        blurRadius = shadowSize * 2,
                    ) else Shadow.None,
                )
            }
        }
    }
}
