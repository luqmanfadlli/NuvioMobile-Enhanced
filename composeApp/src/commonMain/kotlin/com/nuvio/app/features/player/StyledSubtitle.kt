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
internal fun StyledSubtitleOverlay(
    text: String,
    style: SubtitleStyleState,
    modifier: Modifier = Modifier,
    videoAspectRatio: Float = 0f,
) {
    val fontFamily = rememberSubtitleFontFamily(style.appearance.fontId)
    if (text.isBlank()) return
    val density = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxSize()) {
        // Use the fitted video height as the 720-line reference scale. This keeps blur visually
        // consistent when portrait or letterboxed video is displayed inside a larger surface.
        val containerWidthPx = with(density) { maxWidth.toPx() }
        val containerHeightPx = with(density) { maxHeight.toPx() }
        val fittedVideoHeightPx = if (videoAspectRatio.isFinite() && videoAspectRatio > 0f) {
            minOf(containerHeightPx, containerWidthPx / videoAspectRatio)
        } else {
            containerHeightPx
        }
        // Scale layout margins for short viewports, but keep the user's font size unchanged.
        val layoutScale = (maxHeight / 360.dp).coerceAtMost(1f)
        Box(
            Modifier.fillMaxSize().padding(
                horizontal = 16.dp * layoutScale,
                vertical = maxHeight * (0.0533f + style.bottomOffset.coerceIn(0, 200) / 1000f),
            ),
            contentAlignment = Alignment.BottomCenter,
        ) {
            StyledSubtitleText(
                text = text,
                style = style,
                scale = layoutScale,
                videoHeightPx = fittedVideoHeightPx,
                fontFamily = fontFamily,
            )
        }
    }
}

@Composable
internal fun StyledSubtitleText(
    text: String,
    style: SubtitleStyleState,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
    videoHeightPx: Float = SUBTITLE_BLUR_REFERENCE_HEIGHT_PX,
    fontFamily: FontFamily = rememberSubtitleFontFamily(style.appearance.fontId),
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(modifier) {
        val paddingX = with(density) { (8.dp * scale).toPx() }
        val paddingY = with(density) { (4.dp * scale).toPx() }
        val availableWidth = constraints.maxWidth.toFloat()

        // Match the outline's video-relative sizing used by MPV/libass and Umbra's preview.
        // Device density must not make the same outline preference look thicker on one tablet.
        val outline = subtitleOutlineWidthPx(style.outlineWidth, videoHeightPx)
        val outlineEnabled = style.outlineEnabled && outline > 0f
        val outlineBlur = style.appearance.outlineBlur.coerceIn(
            SUBTITLE_OUTLINE_BLUR_MIN,
            SUBTITLE_OUTLINE_BLUR_MAX,
        )
        // Convert the mpv/libass blur factor to the same Gaussian sigma at the fitted
        // video height. Blur the outline as its own layer, never as an added shadow.
        val outlineBlurSigmaPx = subtitleOutlineBlurSigmaPx(outlineBlur, videoHeightPx)
        val outlineBlurSigmaDp = with(density) { outlineBlurSigmaPx.toDp() }
        val useNativeBlur = supportsNativeSubtitleBlur()
        val blurBleedPx = if (outlineEnabled && outlineBlur > 0) {
            outlineBlurSigmaPx * SUBTITLE_BLUR_OUTSET_SIGMAS
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
                val outlineColor = style.outlineColor.copy(
                    alpha = style.outlineColor.alpha * style.textColor.alpha,
                )
                if (outlineBlur > 0) {
                    // Render a restrained, low-opacity copy as the soft Gaussian halo. Keeping
                    // this pass separate prevents blur from thickening the actual outline.
                    val haloColor = outlineColor.copy(
                        alpha = outlineColor.alpha * SUBTITLE_BLUR_HALO_ALPHA,
                    )
                    if (useNativeBlur) {
                        // Android 12+: hardware-accelerated RenderEffect blur on the halo only.
                        Canvas(
                            Modifier.fillMaxSize().blur(
                                outlineBlurSigmaDp,
                                BlurredEdgeTreatment.Unbounded,
                            ),
                        ) {
                            drawText(
                                layout,
                                color = haloColor,
                                topLeft = origin,
                                drawStyle = Stroke(outline * 2f),
                                shadow = Shadow.None,
                            )
                        }
                    } else {
                        // Older Android and other Compose targets: keep the same halo geometry
                        // with a zero-offset Canvas text shadow. This is not a stack of expanded
                        // outline strokes, so it cannot accumulate into a bold border.
                        Canvas(Modifier.fillMaxSize()) {
                            drawText(
                                layout,
                                color = haloColor,
                                topLeft = origin,
                                drawStyle = Stroke(outline * 2f),
                                shadow = Shadow(
                                    color = haloColor,
                                    offset = Offset.Zero,
                                    blurRadius = outlineBlurSigmaPx,
                                ),
                            )
                        }
                    }
                }
                // Preserve the original edge at full opacity. Only the halo is softened.
                Canvas(Modifier.fillMaxSize()) {
                    drawText(
                        layout,
                        color = outlineColor,
                        topLeft = origin,
                        drawStyle = Stroke(outline * 2f),
                        shadow = Shadow.None,
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
