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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
        // Scale layout margins for short viewports, but keep the user's font size unchanged.
        val layoutScale = (maxHeight / 360.dp).coerceAtMost(1f)
        Box(
            Modifier.fillMaxSize().padding(
                horizontal = 16.dp * layoutScale,
                vertical = maxHeight * (0.0533f + style.bottomOffset.coerceIn(0, 200) / 1000f),
            ),
            contentAlignment = Alignment.BottomCenter,
        ) {
            StyledSubtitleText(text, style, scale = layoutScale, fontFamily = fontFamily)
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

        val outline = with(density) { style.outlineWidth.dp.toPx() }
        val outlineEnabled = style.outlineEnabled && outline > 0f
        val outlineBlur = style.appearance.outlineBlur.coerceIn(
            SUBTITLE_OUTLINE_BLUR_MIN,
            SUBTITLE_OUTLINE_BLUR_MAX,
        )
        // The blur is a soft, low-opacity halo around the outline stroke. It does not
        // enlarge the stroke itself or blur the foreground glyph fill.
        val outlineBlurRadiusPx = with(density) {
            subtitleOutlineBlurRadiusDp(outlineBlur).dp.toPx()
        }
        val blurBleedPx = if (outlineEnabled && outlineBlur > 0) {
            outlineBlurRadiusPx * SUBTITLE_OUTLINE_BLUR_BLEED_FACTOR
        } else {
            0f
        }
        // Reserve room for both the actual stroke and its soft edge so neither is clipped.
        val edgePaddingPx = if (outlineEnabled) outline + blurBleedPx else 0f
        val edgePadding = with(density) { edgePaddingPx.toDp() }

        val availableTextWidth = (
            availableWidth - paddingX * 2 - edgePaddingPx * 2
        ).coerceAtLeast(1f).toInt()
        val layout = measurer.measure(
            text = AnnotatedString(text),
            style = TextStyle(
                fontFamily = fontFamily,
                // Match the native subtitle renderer: fontSizeSp is an exact SP preference,
                // not a value to scale with the player container height.
                fontSize = style.fontSizeSp.sp,
                fontWeight = if (style.bold) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                textDirection = TextDirection.ContentOrLtr,
            ),
            constraints = Constraints(maxWidth = availableTextWidth),
        )
        val contentHeight = with(density) { (layout.size.height + paddingY * 2).toDp() }
        val contentWidth = layout.size.width.toFloat() + paddingX * 2
        val boxWidth = contentWidth + edgePaddingPx * 2
        val left = (availableWidth - boxWidth).coerceAtLeast(0f) / 2f
        val origin = Offset(
            left + edgePaddingPx + paddingX,
            paddingY + edgePaddingPx,
        )
        val radius = with(density) { style.appearance.backgroundRadius.dp.toPx() }
        val shadowSize = with(density) { style.appearance.shadowSize.dp.toPx() }

        Box(
            Modifier.fillMaxWidth()
                .height(contentHeight + edgePadding * 2)
                // Keep the visible subtitle baseline stable as the outline reserves extra space.
                .offset(y = edgePadding)
                .semantics { this.text = AnnotatedString(text) },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawRoundRect(
                    color = style.backgroundColor,
                    topLeft = Offset(left + edgePaddingPx, edgePaddingPx),
                    size = androidx.compose.ui.geometry.Size(
                        contentWidth,
                        (size.height - edgePaddingPx * 2).coerceAtLeast(0f),
                    ),
                    cornerRadius = CornerRadius(radius),
                )
            }

            if (outlineEnabled) {
                Canvas(Modifier.fillMaxSize()) {
                    val outlineColor = style.outlineColor.copy(
                        alpha = style.outlineColor.alpha * style.textColor.alpha,
                    )
                    val outlineShadow = if (outlineBlurRadiusPx > 0f) {
                        Shadow(
                            color = outlineColor.copy(
                                alpha = outlineColor.alpha * SUBTITLE_OUTLINE_BLUR_SHADOW_ALPHA,
                            ),
                            offset = Offset.Zero,
                            blurRadius = outlineBlurRadiusPx,
                        )
                    } else {
                        Shadow.None
                    }
                    drawText(
                        layout,
                        color = outlineColor,
                        topLeft = origin,
                        drawStyle = Stroke(outline * 2f),
                        shadow = outlineShadow,
                    )
                }
            }

            // Draw the subtitle fill separately so outline blur can never soften the glyphs.
            Canvas(Modifier.fillMaxSize()) {
                drawText(
                    layout,
                    color = style.textColor,
                    topLeft = origin,
                    drawStyle = Fill,
                    shadow = if (style.appearance.shadowEnabled) {
                        Shadow(
                            color = Color.Black.copy(
                                alpha = style.appearance.shadowOpacity / 100f * style.textColor.alpha,
                            ),
                            offset = Offset(shadowSize, shadowSize),
                            blurRadius = shadowSize * 2,
                        )
                    } else {
                        Shadow.None
                    },
                )
            }
        }
    }
}
