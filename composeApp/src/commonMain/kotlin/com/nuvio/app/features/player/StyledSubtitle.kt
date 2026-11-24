package com.nuvio.app.features.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.sqrt

private const val TOP_SUBTITLE_INSET_FRACTION = 0.08f
private const val SIDE_SUBTITLE_INSET_FRACTION = 0.05f

@Composable
internal fun StyledSubtitleOverlay(
    cues: StyledSubtitleCues,
    style: SubtitleStyleState,
    modifier: Modifier = Modifier,
    videoAspectRatio: Float = 0f,
    videoResizeMode: PlayerResizeMode = PlayerResizeMode.Fit,
    controlsVisible: Boolean = false,
) {
    val fontFamily = rememberSubtitleFontFamily(style.appearance.fontId)
    val density = LocalDensity.current
    val animatedControlsLift = androidx.compose.animation.core.animateDpAsState(
        targetValue = if (controlsVisible) 108.dp else 0.dp,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 200),
        label = "subtitleControlsLift",
    ).value
    if (cues.isEmpty) return
    BoxWithConstraints(modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout)) {
        val containerWidthPx = with(density) { maxWidth.toPx() }
        val containerHeightPx = with(density) { maxHeight.toPx() }
        val fittedVideoBounds = subtitleVideoBoundsPx(
            containerWidthPx = containerWidthPx,
            containerHeightPx = containerHeightPx,
            videoAspectRatio = videoAspectRatio,
            resizeMode = videoResizeMode,
        )
        val scale = (maxHeight / 360.dp).coerceAtMost(1f)
        val controlsLift = animatedControlsLift * scale
        val bottomInset = (maxHeight * (0.0533f + style.bottomOffset.coerceIn(0, 200) / 1000f) + controlsLift)
            .coerceAtMost(maxHeight * 0.45f)
        val topInset = maxHeight * TOP_SUBTITLE_INSET_FRACTION
        val sideInset = maxWidth * SIDE_SUBTITLE_INSET_FRACTION
        cues.blocks.forEach { block ->
            val horizontalInset = 16.dp * scale + if (block.horizontal == SubtitleHorizontalPosition.CENTER) 0.dp else sideInset
            Box(
                Modifier.fillMaxSize().padding(
                    start = horizontalInset,
                    end = horizontalInset,
                    top = if (block.vertical == SubtitleVerticalPosition.TOP) topInset else 0.dp,
                    bottom = if (block.vertical == SubtitleVerticalPosition.BOTTOM) bottomInset else 0.dp,
                ),
                contentAlignment = block.alignment(),
            ) {
                StyledSubtitleText(
                    text = block.text,
                    style = style,
                    scale = scale,
                    videoWidthPx = fittedVideoBounds.width.coerceAtMost(containerWidthPx),
                    videoHeightPx = fittedVideoBounds.height.coerceAtMost(containerHeightPx),
                    fontFamily = fontFamily,
                    horizontal = block.horizontal,
                )
            }
        }
    }
}

private fun StyledSubtitleBlock.alignment(): Alignment = when (vertical) {
    SubtitleVerticalPosition.TOP -> when (horizontal) {
        SubtitleHorizontalPosition.START -> Alignment.TopStart
        SubtitleHorizontalPosition.CENTER -> Alignment.TopCenter
        SubtitleHorizontalPosition.END -> Alignment.TopEnd
    }
    SubtitleVerticalPosition.MIDDLE -> when (horizontal) {
        SubtitleHorizontalPosition.START -> Alignment.CenterStart
        SubtitleHorizontalPosition.CENTER -> Alignment.Center
        SubtitleHorizontalPosition.END -> Alignment.CenterEnd
    }
    SubtitleVerticalPosition.BOTTOM -> when (horizontal) {
        SubtitleHorizontalPosition.START -> Alignment.BottomStart
        SubtitleHorizontalPosition.CENTER -> Alignment.BottomCenter
        SubtitleHorizontalPosition.END -> Alignment.BottomEnd
    }
}

@Composable
internal fun StyledSubtitleText(
    text: String,
    style: SubtitleStyleState,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
    videoWidthPx: Float = SUBTITLE_BLUR_REFERENCE_WIDTH_PX,
    videoHeightPx: Float = SUBTITLE_BLUR_REFERENCE_HEIGHT_PX,
    fontFamily: FontFamily = rememberSubtitleFontFamily(style.appearance.fontId),
) {
    StyledSubtitleText(
        text = AnnotatedString(text),
        style = style,
        modifier = modifier,
        scale = scale,
        videoWidthPx = videoWidthPx,
        videoHeightPx = videoHeightPx,
        fontFamily = fontFamily,
    )
}

@Composable
internal fun StyledSubtitleText(
    text: AnnotatedString,
    style: SubtitleStyleState,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
    videoWidthPx: Float = SUBTITLE_BLUR_REFERENCE_WIDTH_PX,
    videoHeightPx: Float = SUBTITLE_BLUR_REFERENCE_HEIGHT_PX,
    fontFamily: FontFamily = rememberSubtitleFontFamily(style.appearance.fontId),
    horizontal: SubtitleHorizontalPosition = SubtitleHorizontalPosition.CENTER,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val paddingX = with(density) { (8.dp * scale).toPx() }
        val paddingY = with(density) { (4.dp * scale).toPx() }
        val availableWidth = constraints.maxWidth.toFloat()

        val outline = with(density) { (style.outlineWidth.dp * scale).toPx() } / 2f
        val outlineEnabled = style.outlineEnabled && outline > 0f
        val outlineBlur = style.appearance.outlineBlur.coerceIn(
            SUBTITLE_OUTLINE_BLUR_MIN,
            SUBTITLE_OUTLINE_BLUR_MAX,
        )
        val outlineBlurSigmaXPx = subtitleOutlineBlurSigmaXPx(outlineBlur, videoWidthPx)
        val outlineBlurSigmaYPx = subtitleOutlineBlurSigmaYPx(outlineBlur, videoHeightPx)
        val outlineBlurSigmaXDp = with(density) { outlineBlurSigmaXPx.toDp() }
        val outlineBlurSigmaYDp = with(density) { outlineBlurSigmaYPx.toDp() }
        val legacyBlurRadiusPx = sqrt(outlineBlurSigmaXPx * outlineBlurSigmaYPx)
        val useNativeBlur = supportsNativeSubtitleBlur()
        val blurBleedPx = if (outlineEnabled && outlineBlur > 0) {
            maxOf(outlineBlurSigmaXPx, outlineBlurSigmaYPx) * SUBTITLE_BLUR_OUTSET_SIGMAS
        } else {
            0f
        }
        val edgePaddingPx = if (outlineEnabled) outline + blurBleedPx else 0f
        val edgePadding = with(density) { edgePaddingPx.toDp() }

        val textStyle = TextStyle(
            fontFamily = fontFamily,
            fontSize = (style.fontSizeSp * scale).sp,
            fontWeight = if (style.bold) FontWeight.Bold else FontWeight.Normal,
            textAlign = when (horizontal) {
                SubtitleHorizontalPosition.START -> TextAlign.Left
                SubtitleHorizontalPosition.CENTER -> TextAlign.Center
                SubtitleHorizontalPosition.END -> TextAlign.Right
            },
            textDirection = TextDirection.ContentOrLtr,
        )
        val availableTextWidth = (availableWidth - paddingX * 2 - edgePaddingPx * 2).coerceAtLeast(1f).toInt()
        val textConstraints = Constraints(maxWidth = availableTextWidth)
        val textAlpha = style.textColor.alpha
        val layout = measurer.measure(
            text = text.mapColors { subtitleFileColor(it, textAlpha) },
            style = textStyle,
            constraints = textConstraints,
        )
        val plainLayout = if (text.spanStyles.any { it.item.color.isSpecified }) {
            measurer.measure(text = text.mapColors { Color.Unspecified }, style = textStyle, constraints = textConstraints)
        } else {
            layout
        }
        val lineLeft = (0 until layout.lineCount).minOfOrNull { layout.getLineLeft(it) } ?: 0f
        val lineRight = (0 until layout.lineCount).maxOfOrNull { layout.getLineRight(it) } ?: 0f
        val (contentWidth, textInset) = when (horizontal) {
            SubtitleHorizontalPosition.CENTER -> (layout.size.width + paddingX * 2) to paddingX
            else -> (lineRight - lineLeft + paddingX * 2) to (paddingX - lineLeft)
        }
        val contentHeight = with(density) { (layout.size.height + paddingY * 2).toDp() }
        val boxWidthPx = contentWidth + edgePaddingPx * 2
        val boxWidth = with(density) { boxWidthPx.toDp() }
        val leftPx = when (horizontal) {
            SubtitleHorizontalPosition.START -> 0f
            SubtitleHorizontalPosition.CENTER -> (availableWidth - boxWidthPx) / 2f
            SubtitleHorizontalPosition.END -> availableWidth - boxWidthPx
        }
        val left = with(density) { leftPx.toDp() }
        val origin = Offset(edgePaddingPx + textInset, paddingY + edgePaddingPx)
        val radius = with(density) { (style.appearance.backgroundRadius.dp * scale).toPx() }
        val shadowSize = with(density) { (style.appearance.shadowSize.dp * scale).toPx() }
        val shadowOpacity = style.appearance.shadowOpacity.coerceIn(0, 100) / 100f

        Box(
            Modifier.requiredWidth(boxWidth)
                .height(contentHeight + edgePadding * 2)
                .offset(x = left, y = edgePadding)
                .semantics { this.text = text },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawRoundRect(
                    color = style.backgroundColor,
                    topLeft = Offset(edgePaddingPx, edgePaddingPx),
                    size = androidx.compose.ui.geometry.Size(
                        contentWidth,
                        (size.height - edgePaddingPx * 2).coerceAtLeast(0f),
                    ),
                    cornerRadius = CornerRadius(radius),
                )
            }

            if (style.appearance.shadowEnabled && shadowOpacity > 0f) {
                Canvas(Modifier.fillMaxSize()) {
                    drawText(
                        plainLayout,
                        color = Color.Transparent,
                        topLeft = origin,
                        drawStyle = Fill,
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = shadowOpacity),
                            offset = Offset(shadowSize * 0.55f, shadowSize * 0.55f),
                            blurRadius = (shadowSize * 0.35f).coerceAtLeast(0.5f),
                        ),
                    )
                    drawText(
                        plainLayout,
                        color = Color.Transparent,
                        topLeft = origin,
                        drawStyle = Fill,
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = shadowOpacity * 0.45f),
                            offset = Offset(shadowSize, shadowSize),
                            blurRadius = shadowSize * 1.8f,
                        ),
                    )
                }
            }

            if (outlineEnabled) {
                val outlineBlurOpacity = subtitleOutlineBlurOpacity(outlineBlur)
                val outlineColor = style.outlineColor.copy(
                    alpha = style.outlineColor.alpha * style.textColor.alpha * outlineBlurOpacity,
                )
                if (outlineBlur > 0) {
                    if (useNativeBlur) {
                        Canvas(
                            Modifier.fillMaxSize().blur(
                                radiusX = outlineBlurSigmaXDp,
                                radiusY = outlineBlurSigmaYDp,
                                edgeTreatment = BlurredEdgeTreatment.Unbounded,
                            ),
                        ) {
                            drawText(
                                plainLayout,
                                color = outlineColor,
                                topLeft = origin,
                                drawStyle = Stroke(outline * 2f),
                                shadow = Shadow.None,
                            )
                        }
                    } else {
                        val maskShadowColor = outlineColor.copy(
                            alpha = outlineColor.alpha.coerceAtMost(0.999f),
                        )
                        Canvas(Modifier.fillMaxSize()) {
                            drawText(
                                plainLayout,
                                color = Color.Transparent,
                                topLeft = origin,
                                drawStyle = Stroke(outline * 2f),
                                shadow = Shadow(
                                    color = maskShadowColor,
                                    offset = Offset.Zero,
                                    blurRadius = legacyBlurRadiusPx,
                                ),
                            )
                        }
                    }
                } else {
                    Canvas(Modifier.fillMaxSize()) {
                        drawText(
                            plainLayout,
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
                    layout,
                    color = style.textColor,
                    topLeft = origin,
                    drawStyle = Fill,
                    shadow = Shadow.None,
                )
            }
        }
    }
}

internal fun subtitleFileColor(color: Color, textAlpha: Float): Color =
    color.copy(alpha = color.alpha * textAlpha)

private fun AnnotatedString.mapColors(transform: (Color) -> Color): AnnotatedString = buildAnnotatedString {
    append(this@mapColors.text)
    this@mapColors.spanStyles.forEach { range ->
        val color = range.item.color
        addStyle(
            if (color.isSpecified) range.item.copy(color = transform(color)) else range.item,
            range.start,
            range.end,
        )
    }
}
