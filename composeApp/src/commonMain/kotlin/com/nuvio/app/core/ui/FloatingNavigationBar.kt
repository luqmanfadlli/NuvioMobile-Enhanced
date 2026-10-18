package com.nuvio.app.core.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.nuvio.app.core.ui.glass.GlassBarShape
import com.nuvio.app.core.ui.glass.GlassBarSurface
import com.nuvio.app.core.ui.glass.LiquidGlassStyle
import com.nuvio.app.core.ui.glass.isLiquidGlassSupported
import com.nuvio.app.core.ui.glass.isLiquidRefractionSupported
import com.nuvio.app.core.ui.jelly.JellyMotion
import com.nuvio.app.core.ui.jelly.JellySelectionSource
import com.nuvio.app.core.ui.jelly.JellyTabRow
import com.nuvio.app.core.ui.jelly.JellyTabTargets
import com.nuvio.app.core.ui.jelly.drawJellyGlow
import com.nuvio.app.core.ui.jelly.drawJellyPill
import com.nuvio.app.core.ui.jelly.jellyPillPath
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.DrawableResource

internal class FloatingNavigationItem(
    val label: String,
    val selected: Boolean,
    val onClick: () -> Unit,
    val icon: ImageVector? = null,
    val drawable: DrawableResource? = null,
    val content: (@Composable (onClick: () -> Unit) -> Unit)? = null,
)

/**
 * Whether the pill's accent glow can be switched off. Where it cannot, the glow stays on
 * (see [FloatingNavigationBar]).
 */
internal val LocalNuvioTabletNavLayout = staticCompositionLocalOf { false }

internal expect val floatingNavigationGlowSupported: Boolean

@Composable
internal fun floatingNavigationBarPadding(): PaddingValues = PaddingValues(
    bottom = nuvioBottomNavigationBarInsets().asPaddingValues().calculateBottomPadding() +
        nuvioBottomNavigationExtraVerticalPadding + 8.dp,
)

// Shared by Android and iOS (the iPad pill and iPhone before iOS 26): the jelly pill with
// drag-to-select, spring motion and liquid glass.
@Composable
internal fun FloatingNavigationBar(
    items: List<FloatingNavigationItem>,
    modifier: Modifier = Modifier,
    scrollState: NuvioNavBarScrollState? = null,
    backdrop: Backdrop? = null,
    contentPadding: PaddingValues = floatingNavigationBarPadding(),
    compactSize: Boolean = false,
    glowEnabled: Boolean = true,
    inlineLabels: Boolean = false,
    glassStyle: LiquidGlassStyle = LiquidGlassStyle.Default,
) {
    if (items.isEmpty()) return
    val showGlow = !floatingNavigationGlowSupported || glowEnabled
    val glowStrength by animateFloatAsState(
        targetValue = if (showGlow) 1f else 0f,
        animationSpec = tween(420, easing = NuvioTokens.Motion.standard),
        label = "nav_glow_strength",
    )
    val tokens = MaterialTheme.nuvio
    val accentColor = tokens.colors.accent
    val selectedSurface = accentColor.copy(alpha = NuvioTokens.Opacity.selected)
    val labelFraction by animateFloatAsState(
        targetValue = scrollState?.labelVisibility ?: 1f,
        animationSpec = tween(NuvioTokens.Motion.sheetEnterMillis, easing = NuvioTokens.Motion.standard),
        label = "jelly_labels",
    )
    val layoutDirection = LocalLayoutDirection.current
    val isRtl = layoutDirection == LayoutDirection.Rtl
    val selectedIndex = items.indexOfFirst { it.selected }
    val visualSelectedIndex = visualNavIndex(selectedIndex, items.size, isRtl)
    val barBackdrop = rememberLayerBackdrop()
    val selectionLensActive = backdrop != null &&
        selectedIndex >= 0 &&
        glassStyle.usesBackdrop &&
        glassStyle.selectionLens &&
        isLiquidGlassSupported() &&
        isLiquidRefractionSupported()
    val motion = remember(items.size, isRtl) { JellyMotion(visualSelectedIndex, items.size) }
    val currentItems by rememberUpdatedState(items)
    val currentIsRtl by rememberUpdatedState(isRtl)
    val density = LocalDensity.current
    val trackHeight = if (inlineLabels) 48.dp + 4.dp * labelFraction
    else 48.dp + (if (compactSize) 8.dp else 16.dp) * labelFraction
    val horizontalPadding = 58.dp - 30.dp * labelFraction
    // Inline (tablet) pill: expanded fits icon + label per tab, compact shrinks to icon-only
    // slots instead of keeping most of the expanded width.
    val inlineMaxWidth = run {
        val compactWidth = 64.dp * items.size + 8.dp
        val expandedWidth = 640.dp
        compactWidth + (expandedWidth - compactWidth) * labelFraction
    }

    SideEffect {
        if (visualSelectedIndex >= 0 && JellySelectionSource.lastDragCommit == visualSelectedIndex) {
            motion.snap(visualSelectedIndex)
        }
    }
    LaunchedEffect(visualSelectedIndex, items.size) {
        if (JellySelectionSource.lastDragCommit == visualSelectedIndex) {
            motion.snap(visualSelectedIndex)
        } else {
            motion.select(visualSelectedIndex)
        }
    }
    LaunchedEffect(motion.running) {
        if (!motion.running) return@LaunchedEffect
        var previous = withFrameNanos { it }
        while (motion.running) {
            withFrameNanos { now ->
                motion.advance((now - previous) / 1_000_000_000.0)
                previous = now
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(contentPadding)
            .padding(horizontal = horizontalPadding),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = if (inlineLabels) inlineMaxWidth else 400.dp)
                .fillMaxWidth()
                .height(trackHeight)
                .onSizeChanged {
                    motion.resize(it.width / density.density, it.height / density.density, items.size)
                }
                .pointerInput(motion, density, items.size, isRtl) {
                    detectJellyTabGestures(motion, density.density, { currentItems }, { currentIsRtl })
                },
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        val frame = motion.frame
                        scaleX = frame.trackScale
                        scaleY = frame.trackScale
                    },
            ) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer {
                            val frame = motion.frame
                            transformOrigin = TransformOrigin(
                                if (size.width > 0) frame.originX * density.density / size.width else 0.5f,
                                0.5f,
                            )
                            scaleX = frame.trackScaleX
                            translationY = frame.trackOffsetY * density.density
                        },
                ) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .graphicsLayer { translationX = motion.frame.panelOffset * density.density },
                    ) {
                        GlassBarSurface(
                            backdrop = backdrop,
                            modifier = Modifier.matchParentSize(),
                            style = glassStyle,
                            exportedBackdrop = if (selectionLensActive) barBackdrop else null,
                        )
                        Box(
                            Modifier.matchParentSize()
                                .clip(RoundedCornerShape(50))
                                .drawWithContent {
                                    drawContent()
                                    drawJellyGlow(motion.frame, accentColor.copy(alpha = accentColor.alpha * glowStrength))
                                },
                        )
                        if (selectionLensActive && backdrop != null) {
                            JellySelectionLens(
                                backdrop = rememberCombinedBackdrop(backdrop, barBackdrop),
                                motion = motion,
                                count = items.size,
                                style = glassStyle,
                                modifier = Modifier.matchParentSize(),
                            )
                        }
                        Box(
                            Modifier.matchParentSize().drawWithContent {
                                if (selectedIndex >= 0) {
                                    clipPath(jellyPillPath(motion.frame, items.size), ClipOp.Difference) {
                                        this@drawWithContent.drawContent()
                                    }
                                } else {
                                    drawContent()
                                }
                            },
                        ) {
                            JellyTabRow(items, labelFraction, motion, active = false, compactSize = compactSize, modifier = Modifier.matchParentSize(), inlineLabels = inlineLabels)
                        }
                        if (selectedIndex >= 0) {
                            Box(
                                Modifier.matchParentSize()
                                    .clearAndSetSemantics {}
                                    .drawWithContent {
                                        drawJellyPill(
                                            motion.frame,
                                            items.size,
                                            selectedSurface,
                                            accentColor.copy(alpha = accentColor.alpha * glowStrength),
                                        ) { drawContent() }
                                    },
                            ) {
                                JellyTabRow(items, labelFraction, motion, active = true, compactSize = compactSize, modifier = Modifier.matchParentSize(), inlineLabels = inlineLabels)
                            }
                        }
                        JellyTabTargets(items, labelFraction, motion, compactSize, Modifier.matchParentSize(), inlineLabels = inlineLabels)
                    }
                }
            }
        }
    }
}

private val SelectionLensHighlight: () -> Highlight? = { Highlight.Default.copy(alpha = 0.7f) }

private val SelectionLensInnerShadow: () -> InnerShadow? = { InnerShadow(radius = 6.dp, alpha = 0.5f) }

@Composable
private fun JellySelectionLens(
    backdrop: Backdrop,
    motion: JellyMotion,
    count: Int,
    style: LiquidGlassStyle,
    modifier: Modifier,
) {
    Box(
        modifier
            .clearAndSetSemantics {}
            .layout { measurable, constraints ->
                val frame = motion.frame
                val inset = 4.dp.toPx()
                val tabWidth = (constraints.maxWidth - 2 * inset) / count
                val itemHeight = constraints.maxHeight - 2 * inset
                val width = (tabWidth * frame.pillScaleX).roundToInt().coerceAtLeast(0)
                val height = (itemHeight * frame.pillScaleY).roundToInt().coerceAtLeast(0)
                val centerX = inset + (frame.position + 0.5f) * tabWidth
                val placeable = measurable.measure(Constraints.fixed(width, height))
                layout(constraints.maxWidth, constraints.maxHeight) {
                    placeable.place(
                        (centerX - width / 2f).roundToInt(),
                        ((constraints.maxHeight - height) / 2f).roundToInt(),
                    )
                }
            }
            .drawBackdrop(
                backdrop = backdrop,
                shape = { GlassBarShape },
                effects = {
                    lens(
                        refractionHeight = size.minDimension * 0.3f,
                        refractionAmount = size.minDimension * 0.5f,
                        depthEffect = true,
                        chromaticAberration = style.chromaticAberration,
                    )
                },
                highlight = if (style.edgeHighlight) SelectionLensHighlight else null,
                shadow = null,
                innerShadow = SelectionLensInnerShadow,
            ),
    )
}

internal fun visualNavIndex(logicalIndex: Int, count: Int, isRtl: Boolean): Int =
    if (logicalIndex in 0 until count && isRtl) count - 1 - logicalIndex else logicalIndex

internal fun logicalNavIndex(visualIndex: Int, count: Int, isRtl: Boolean): Int =
    if (visualIndex in 0 until count && isRtl) count - 1 - visualIndex else visualIndex

internal suspend fun PointerInputScope.detectJellyTabGestures(
    motion: JellyMotion,
    density: Float,
    currentItems: () -> List<FloatingNavigationItem>,
    isRtl: () -> Boolean,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        motion.begin(down.position.x / density, down.position.y / density)
        var claimed = false
        var finished = false
        try {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (!motion.dragging) {
                    finished = true
                    break
                }
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (event.changes.count { it.pressed } > 1) break
                val delta = change.position - down.position
                if (max(abs(delta.x), abs(delta.y)) > viewConfiguration.touchSlop) claimed = true
                if (claimed) change.consume()
                awaitPointerEvent(PointerEventPass.Main)
                if (!motion.dragging) {
                    finished = true
                    break
                }
                if (change.isConsumed && !claimed) break
                motion.drag(delta.x / density, delta.y / density)
                if (!change.pressed) {
                    val visualIndex = motion.finish()
                    val items = currentItems()
                    val logicalIndex = logicalNavIndex(visualIndex, items.size, isRtl())
                    finished = true
                    items.getOrNull(logicalIndex)?.onClick?.invoke()
                    change.consume()
                    break
                }
            }
        } finally {
            if (!finished) {
                val items = currentItems()
                val selectedIndex = items.indexOfFirst { it.selected }
                motion.cancel(visualNavIndex(selectedIndex, items.size, isRtl()))
            }
        }
    }
}
