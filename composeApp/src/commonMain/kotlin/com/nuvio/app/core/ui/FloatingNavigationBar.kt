package com.nuvio.app.core.ui

import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.glass.LiquidGlassStyle
import com.nuvio.app.core.ui.glass.SolidGlassBar
import com.nuvio.app.core.ui.glass.isLiquidGlassSupported
import com.nuvio.app.core.ui.glass.isLiquidRefractionSupported
import com.nuvio.app.core.ui.glass.LiquidGlassEffectDefaults
import com.nuvio.app.core.ui.glass.LiquidGlassEffectSettings
import com.nuvio.app.core.ui.glass.effectiveLiquidGlassCornerRadiusPercent
import com.nuvio.app.core.ui.glass.LiquidGlassIconRefractionSurface
import com.nuvio.app.core.ui.glass.KeyedLiquidGlassRadius
import com.nuvio.app.core.ui.glass.LiquidGlassFrostedBar
import com.nuvio.app.core.ui.glass.LiquidGlassSelectionSurface
import com.nuvio.app.core.ui.glass.shouldCaptureFloatingBar
import com.nuvio.app.core.ui.glass.shouldCaptureTabIcons
import com.nuvio.app.core.ui.jelly.JellyMotion
import com.nuvio.app.core.ui.jelly.JellySelectionSource
import com.nuvio.app.core.ui.jelly.JellyTabRow
import com.nuvio.app.core.ui.jelly.JellyTabTargets
import com.nuvio.app.core.ui.jelly.drawJellyGlow
import com.nuvio.app.core.ui.jelly.drawJellyPill
import com.nuvio.app.core.ui.jelly.jellyPillPath
import kotlin.math.abs
import kotlin.math.max
import org.jetbrains.compose.resources.DrawableResource

internal class FloatingNavigationItem(
    val label: String,
    val selected: Boolean,
    val onClick: () -> Unit,
    val icon: ImageVector? = null,
    val drawable: DrawableResource? = null,
    val content: (@Composable (onClick: () -> Unit, defaultIconColorOverride: Color?) -> Unit)? = null,
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
// drag-to-select, spring motion and haze glass. Only the glass backdrop is per platform.
@Composable
internal fun FloatingNavigationBar(
    items: List<FloatingNavigationItem>,
    modifier: Modifier = Modifier,
    scrollState: NuvioNavBarScrollState? = null,
    backdrop: Backdrop? = null,
    glassStyle: LiquidGlassStyle = LiquidGlassStyle.Default,
    contentPadding: PaddingValues = floatingNavigationBarPadding(),
    compactSize: Boolean = false,
    glowEnabled: Boolean = true,
    liquidGlassBarEffects: LiquidGlassEffectSettings = LiquidGlassEffectDefaults.floatingBar,
    liquidGlassSelectionEffects: LiquidGlassEffectSettings = LiquidGlassEffectDefaults.selectionLens,
    inlineLabels: Boolean = false,
    defaultIconColorOverride: Color? = null,
    scrollBounceTrigger: Int = 0,
    scrollBounceDirection: Float = 0f,
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
    val motion = remember(items.size, isRtl) { JellyMotion(visualSelectedIndex, items.size) }
    val lastScrollBounceTrigger = remember(motion) { mutableIntStateOf(scrollBounceTrigger) }
    LaunchedEffect(motion, scrollBounceTrigger) {
        if (scrollBounceTrigger == lastScrollBounceTrigger.intValue) return@LaunchedEffect
        lastScrollBounceTrigger.intValue = scrollBounceTrigger
        motion.bounce(scrollBounceDirection)
    }
    val glassMode = resolveNavigationGlass(
        style = glassStyle,
        hasBackdrop = backdrop != null,
        hasSelection = selectedIndex >= 0,
        glassSupported = isLiquidGlassSupported(),
        refractionSupported = isLiquidRefractionSupported(),
    )
    val floatingBarEffects = liquidGlassBarEffects.sanitized(
        LiquidGlassEffectDefaults.floatingBar,
        LiquidGlassEffectDefaults.floatingBarLimits,
    ).forNavigationMaterial(glassStyle, glassMode.barRefraction)
    val selectionLensEffects = liquidGlassSelectionEffects.sanitized(
        LiquidGlassEffectDefaults.selectionLens,
        LiquidGlassEffectDefaults.selectionLensLimits,
    )
    val liquidGlassBarBackdrop = if (glassMode.selectionLens && selectionLensEffects.shouldCaptureFloatingBar()) {
        rememberLayerBackdrop()
    } else {
        null
    }
    val liquidGlassIconsBackdrop = if (glassMode.selectionLens && selectionLensEffects.shouldCaptureTabIcons()) {
        rememberLayerBackdrop()
    } else {
        null
    }
    val floatingBarCornerRadiusPercent = if (glassMode.bar) {
        effectiveLiquidGlassCornerRadiusPercent(floatingBarEffects.cornerRadiusPercent)
    } else {
        50
    }
    val selectionLensCornerRadiusPercent = if (glassMode.selectionLens) {
        effectiveLiquidGlassCornerRadiusPercent(selectionLensEffects.cornerRadiusPercent)
    } else {
        50
    }
    val floatingBarShape = remember(floatingBarCornerRadiusPercent) {
        RoundedCornerShape(floatingBarCornerRadiusPercent)
    }
    val selectionLensShape = remember(selectionLensCornerRadiusPercent) {
        RoundedCornerShape(selectionLensCornerRadiusPercent)
    }
    // The primary lens always samples the screen. Only the optional floating-bar source is
    // composited into this pass; tab icons use a separate refraction-only overlay below so the
    // selected-lens blur cannot smear glyphs when icon refraction is enabled.
    val liquidGlassSelectionBackdrop = if (glassMode.selectionLens) {
        val screenBackdrop = backdrop!!
        if (liquidGlassBarBackdrop != null) {
            rememberCombinedBackdrop(screenBackdrop, liquidGlassBarBackdrop)
        } else {
            screenBackdrop
        }
    } else {
        null
    }
    val selectionPillCornerRadiusPercent = selectionLensCornerRadiusPercent.toFloat()
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
        BoxWithConstraints(
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
            contentAlignment = Alignment.TopStart,
        ) {
            val liquidGlassSelectionTabWidth = ((maxWidth - 8.dp) / items.size).coerceAtLeast(0.dp)
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
                        Box(Modifier.matchParentSize()) {
                            KeyedLiquidGlassRadius(
                                cornerRadiusPercent = floatingBarCornerRadiusPercent.toFloat(),
                            ) {
                                Box(
                                    Modifier.matchParentSize()
                                        .drawWithContent {
                                            drawContent()
                                            val outline = floatingBarShape.createOutline(size, layoutDirection, this)
                                            clipPath(Path().apply { addOutline(outline) }) {
                                                drawJellyGlow(motion.frame, accentColor.copy(alpha = accentColor.alpha * glowStrength))
                                            }
                                        },
                                ) {
                                    if (glassMode.bar) {
                                        LiquidGlassFrostedBar(
                                            backdrop = backdrop!!,
                                            modifier = Modifier.matchParentSize(),
                                            glowStrength = glowStrength,
                                            settings = floatingBarEffects,
                                            style = glassStyle,
                                        )
                                        if (liquidGlassBarBackdrop != null) {
                                            // Capture the frosted glass surface without its own lens effect.
                                            // The selected lens then refracts this source once, rather than
                                            // sampling an already-refracted copy of the visible bar.
                                            Box(
                                                Modifier.matchParentSize()
                                                    .graphicsLayer { alpha = 0f }
                                                    .layerBackdrop(liquidGlassBarBackdrop),
                                            ) {
                                                LiquidGlassFrostedBar(
                                                    backdrop = backdrop!!,
                                                    modifier = Modifier.matchParentSize(),
                                                    glowStrength = glowStrength,
                                                    settings = floatingBarEffects,
                                                    style = glassStyle.copy(shadow = false),
                                                    includeRefraction = false,
                                                )
                                            }
                                        }
                                    } else {
                                        SolidGlassBar(Modifier.matchParentSize(), glassStyle.edgeHighlight)
                                    }
                                }
                            }
                            Box(
                                Modifier.matchParentSize().drawWithContent {
                                    if (selectedIndex >= 0) {
                                        clipPath(
                                            jellyPillPath(motion.frame, items.size, selectionPillCornerRadiusPercent),
                                            ClipOp.Difference,
                                        ) {
                                            this@drawWithContent.drawContent()
                                        }
                                    } else {
                                        drawContent()
                                    }
                                },
                            ) {
                                JellyTabRow(items, labelFraction, motion, active = false, compactSize = compactSize, modifier = Modifier.matchParentSize(), inlineLabels = inlineLabels, inactiveColorOverride = defaultIconColorOverride)
                            }
                            if (liquidGlassIconsBackdrop != null) {
                                // Capture the active tab row only when explicitly requested.
                                // It is a lens source, not a second visible set of icons.
                                Box(
                                    Modifier
                                        .matchParentSize()
                                        .clearAndSetSemantics {}
                                        .graphicsLayer { alpha = 0f }
                                        .layerBackdrop(liquidGlassIconsBackdrop),
                                ) {
                                    Box(Modifier.matchParentSize()) {
                                        // Match the visible row: inactive tabs must not remain
                                        // underneath the active icon source inside the selected pill.
                                        Box(
                                            Modifier.matchParentSize().drawWithContent {
                                                if (selectedIndex >= 0) {
                                                    clipPath(
                                                        jellyPillPath(
                                                            motion.frame,
                                                            items.size,
                                                            selectionPillCornerRadiusPercent,
                                                        ),
                                                        ClipOp.Difference,
                                                    ) {
                                                        this@drawWithContent.drawContent()
                                                    }
                                                } else {
                                                    drawContent()
                                                }
                                            },
                                        ) {
                                            JellyTabRow(
                                                items,
                                                labelFraction,
                                                motion,
                                                active = false,
                                                compactSize = compactSize,
                                                modifier = Modifier.matchParentSize(),
                                                inlineLabels = inlineLabels,
                                                inactiveColorOverride = defaultIconColorOverride,
                                            )
                                        }
                                        Box(
                                            Modifier.matchParentSize().drawWithContent {
                                                if (selectedIndex >= 0) {
                                                    clipPath(
                                                        jellyPillPath(
                                                            motion.frame,
                                                            items.size,
                                                            selectionPillCornerRadiusPercent,
                                                        ),
                                                    ) {
                                                        this@drawWithContent.drawContent()
                                                    }
                                                } else {
                                                    drawContent()
                                                }
                                            },
                                        ) {
                                            JellyTabRow(
                                                items,
                                                labelFraction,
                                                motion,
                                                active = true,
                                                compactSize = compactSize,
                                                modifier = Modifier.matchParentSize(),
                                                inlineLabels = inlineLabels,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        if (selectedIndex >= 0) {
                            if (liquidGlassSelectionBackdrop != null) {
                                KeyedLiquidGlassRadius(
                                    cornerRadiusPercent = selectionLensCornerRadiusPercent.toFloat(),
                                ) {
                                    Box(
                                        Modifier
                                            .width(liquidGlassSelectionTabWidth)
                                            .height(trackHeight - 8.dp),
                                    ) {
                                        LiquidGlassSelectionSurface(
                                            backdrop = liquidGlassSelectionBackdrop,
                                            pressProgress = { ((motion.frame.contentScale - 1f) / 0.2f).coerceIn(0f, 1f) },
                                            shape = { selectionLensShape },
                                            modifier = Modifier.matchParentSize(),
                                            settings = selectionLensEffects,
                                            layerBlock = {
                                                // Apply the moving lens transform inside drawBackdrop so
                                                // Kyant's backdrop sampler can invert it for source mapping.
                                                val frame = motion.frame
                                                // This lens is inside the same parent layer that applies panelOffset.
                                                translationX = liquidGlassSelectionTranslationX(
                                                    position = frame.position,
                                                    count = items.size,
                                                    tabWidthPx = liquidGlassSelectionTabWidth.toPx(),
                                                    insetPx = 4.dp.toPx(),
                                                    isRtl = isRtl,
                                                )
                                                translationY = 4.dp.toPx()
                                                scaleX = frame.pillScaleX
                                                scaleY = frame.pillScaleY
                                            },
                                        )
                                    }
                                }
                            }

                            // Keep icon refraction separate from the main screen/bar lens: the
                            // configured lens blur must not blur this icon-only source a second time.
                            if (liquidGlassIconsBackdrop != null && motion.tabIconRefractionActive) {
                                Box(
                                    Modifier
                                        .width(liquidGlassSelectionTabWidth)
                                        .height(trackHeight - 8.dp),
                                ) {
                                    LiquidGlassIconRefractionSurface(
                                        backdrop = liquidGlassIconsBackdrop,
                                        shape = { selectionLensShape },
                                        modifier = Modifier.matchParentSize(),
                                        settings = selectionLensEffects,
                                        layerBlock = {
                                            val frame = motion.frame
                                            translationX = liquidGlassSelectionTranslationX(
                                                position = frame.position,
                                                count = items.size,
                                                tabWidthPx = liquidGlassSelectionTabWidth.toPx(),
                                                insetPx = 4.dp.toPx(),
                                                isRtl = isRtl,
                                            )
                                            translationY = 4.dp.toPx()
                                            scaleX = frame.pillScaleX
                                            scaleY = frame.pillScaleY
                                        },
                                    )
                                }
                            }
                            // Keep the selected row crisp at rest and during vertical scrolling.
                            // Hide this duplicate only while a horizontal lens drag is refracting
                            // the captured icon source.
                            if (liquidGlassIconsBackdrop == null || !motion.tabIconRefractionActive) {
                                Box(
                                    Modifier.matchParentSize()
                                        .clearAndSetSemantics {}
                                        .then(
                                            if (liquidGlassSelectionBackdrop != null) {
                                                Modifier.drawWithContent {
                                                    clipPath(
                                                        jellyPillPath(
                                                            motion.frame,
                                                            items.size,
                                                            selectionPillCornerRadiusPercent,
                                                        ),
                                                    ) {
                                                        this@drawWithContent.drawContent()
                                                    }
                                                }
                                            } else {
                                                Modifier.drawWithContent {
                                                    drawJellyPill(
                                                        motion.frame,
                                                        items.size,
                                                        selectedSurface,
                                                        accentColor.copy(alpha = accentColor.alpha * glowStrength),
                                                    ) { this@drawWithContent.drawContent() }
                                                }
                                            },
                                        ),
                                ) {
                                    JellyTabRow(items, labelFraction, motion, active = true, compactSize = compactSize, modifier = Modifier.matchParentSize(), inlineLabels = inlineLabels)
                                }
                            }
                        }
                        JellyTabTargets(
                            items,
                            labelFraction,
                            motion,
                            compactSize,
                            Modifier.matchParentSize(),
                            inlineLabels = inlineLabels,
                            defaultIconColorOverride = defaultIconColorOverride,
                        )
                    }
                }
            }
        }
    }
}


internal data class NavigationGlassMode(
    val bar: Boolean,
    val barRefraction: Boolean,
    val selectionLens: Boolean,
)

/**
 * The material is the only nav bar switch: SOLID (or the app-wide glass toggle being off, or no
 * RenderEffect) draws the solid pill. Lens refraction needs RuntimeShader (Android 13+); below
 * that the bar keeps its blur and the selected tab falls back to the solid jelly pill.
 */
internal fun resolveNavigationGlass(
    style: LiquidGlassStyle,
    hasBackdrop: Boolean,
    hasSelection: Boolean,
    glassSupported: Boolean,
    refractionSupported: Boolean,
): NavigationGlassMode {
    val bar = hasBackdrop && style.usesBackdrop && glassSupported
    return NavigationGlassMode(
        bar = bar,
        barRefraction = bar && style.usesLens && refractionSupported,
        selectionLens = bar && refractionSupported && style.selectionLens && hasSelection,
    )
}

internal fun LiquidGlassEffectSettings.forNavigationMaterial(
    style: LiquidGlassStyle,
    refraction: Boolean,
): LiquidGlassEffectSettings = copy(
    blurRadiusDp = if (style.usesBlur) blurRadiusDp else 0f,
    refractionHeightDp = if (refraction) refractionHeightDp else 0f,
    refractionAmountDp = if (refraction) refractionAmountDp else 0f,
)

internal fun visualNavIndex(logicalIndex: Int, count: Int, isRtl: Boolean): Int =
    if (logicalIndex in 0 until count && isRtl) count - 1 - logicalIndex else logicalIndex

internal fun logicalNavIndex(visualIndex: Int, count: Int, isRtl: Boolean): Int =
    if (visualIndex in 0 until count && isRtl) count - 1 - visualIndex else visualIndex

/**
 * Converts the selected visual slot into an offset from Box's logical-start alignment.
 * That alignment is left in LTR and right in RTL, so the physical translation must mirror.
 */
/**
 * Converts a selected visual slot to an offset local to the already translated navigation row.
 * JellyMotion's physical panelOffset is applied once by the shared parent graphics layer; applying
 * it here as well would make the selected lens drift twice as far as the visible tabs.
 */
internal fun liquidGlassSelectionTranslationX(
    position: Float,
    count: Int,
    tabWidthPx: Float,
    insetPx: Float,
    isRtl: Boolean,
): Float = if (isRtl) {
    -insetPx - tabWidthPx * (count - 1 - position)
} else {
    insetPx + tabWidthPx * position
}

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
