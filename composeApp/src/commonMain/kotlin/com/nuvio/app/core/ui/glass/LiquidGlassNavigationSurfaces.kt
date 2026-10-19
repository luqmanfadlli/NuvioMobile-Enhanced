package com.nuvio.app.core.ui.glass

import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateMeasurementForSubtree
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.nuvio.app.core.ui.nuvio

private val LiquidGlassBarEdgeBrush = Brush.verticalGradient(
    listOf(Color.White.copy(alpha = 0.27f), Color.White.copy(alpha = 0.02f)),
)

/**
 * Invalidate measurements below a stable Backdrop layer when its effective shape key changes.
 * For the floating bar, also use [KeyedLiquidGlassRadius] around the complete visible/capture
 * group: nested Backdrop layers can retain stale rounded geometry even after a local remeasure.
 */
internal fun Modifier.remeasureOnKeyChange(key: Int): Modifier =
    this then RemeasureOnKeyChangeElement(key)

/**
 * Recreate radius-dependent glass layers when their effective corner radius changes.
 *
 * Backdrop installs its clip shape into a measured graphics layer. Radius-only edits keep the
 * same layout constraints, so a subtree remeasure can leave that layer stale on some frames.
 * Key by the same rounded percent used by RoundedCornerShape: the visible surface and its
 * optional capture copy are rebuilt together only when their rendered geometry changes.
 */
@Composable
internal fun KeyedLiquidGlassRadius(
    cornerRadiusPercent: Float,
    content: @Composable () -> Unit,
) {
    key(effectiveLiquidGlassCornerRadiusPercent(cornerRadiusPercent)) {
        content()
    }
}

private class RemeasureOnKeyChangeElement(
    private val key: Int,
) : ModifierNodeElement<RemeasureOnKeyChangeNode>() {
    override fun create(): RemeasureOnKeyChangeNode = RemeasureOnKeyChangeNode(key)

    override fun update(node: RemeasureOnKeyChangeNode) {
        if (node.key != key) {
            node.key = key
            node.invalidateMeasurementForSubtree()
        }
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "remeasureOnKeyChange"
        properties["key"] = key
    }

    override fun equals(other: Any?): Boolean =
        this === other || (other is RemeasureOnKeyChangeElement && key == other.key)

    override fun hashCode(): Int = key
}

private class RemeasureOnKeyChangeNode(
    var key: Int,
) : LayoutModifierNode, Modifier.Node() {
    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) {
            placeable.place(0, 0)
        }
    }
}

/**
 * The navigation bar's glass, adapted from Kyant0's LiquidBottomTabs to Nuvio's pill geometry:
 * vibrancy, background blur, capsule refraction and a theme-aware tint. [settings] holds the
 * numbers (already filtered by the material), [style] the vibrancy/highlight/shadow flags.
 * Nuvio's existing JellyMotion and glow remain responsible for navigation interaction.
 */
@Composable
internal fun LiquidGlassFrostedBar(
    backdrop: Backdrop,
    modifier: Modifier,
    glowStrength: Float,
    settings: LiquidGlassEffectSettings = LiquidGlassEffectDefaults.floatingBar,
    style: LiquidGlassStyle = LiquidGlassStyle.Default,
    includeRefraction: Boolean = true,
) {
    val safeSettings = settings.sanitized(
        LiquidGlassEffectDefaults.floatingBar,
        LiquidGlassEffectDefaults.floatingBarLimits,
    )
    // Nuvio owns its palette independently from Android/iOS system appearance.
    // Tint the glass with the active Nuvio surface, not the device's light/dark setting.
    val containerColor = MaterialTheme.nuvio.colors.surface.copy(alpha = safeSettings.tintOpacity)
    val cornerRadiusPercent = effectiveLiquidGlassCornerRadiusPercent(safeSettings.cornerRadiusPercent)
    val cornerShape = RoundedCornerShape(cornerRadiusPercent)

    Box(
        modifier
            // drawBackdrop caches its clip Shape in a measured graphics layer.
            // A radius edit does not change constraints, so refresh that layer immediately.
            .remeasureOnKeyChange(cornerRadiusPercent)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { cornerShape },
                effects = {
                    if (style.vibrancy) vibrancy()
                    if (safeSettings.blurRadiusDp > 0f) {
                        blur(safeSettings.blurRadiusDp.dp.toPx())
                    }
                    if (includeRefraction && safeSettings.refractionHeightDp > 0f && safeSettings.refractionAmountDp > 0f) {
                        lens(
                            refractionHeight = safeSettings.refractionHeightDp.dp.toPx(),
                            refractionAmount = safeSettings.refractionAmountDp.dp.toPx(),
                            depthEffect = safeSettings.depthEffect,
                            chromaticAberration = safeSettings.chromaticAberration,
                        )
                    }
                },
                highlight = if (style.edgeHighlight) DefaultGlassHighlight else null,
                shadow = if (style.shadow) DefaultGlassShadow else null,
                onDrawSurface = {
                    // The optional layerBackdrop captures this rendered child separately, so
                    // draw the tint with capsule geometry instead of relying on an outer clip.
                    val cornerRadius = size.minDimension * (cornerRadiusPercent / 100f)
                    drawRoundRect(
                        color = containerColor,
                        cornerRadius = CornerRadius(cornerRadius),
                    )
                },
                onDrawFront = {
                    // Keep the rim in this rendered child so the optional layerBackdrop source
                    // captures the complete visible floating-bar glass surface.
                    val width = 0.75.dp.toPx()
                    val cornerRadius = size.minDimension * (cornerRadiusPercent / 100f)
                    drawRoundRect(
                        brush = LiquidGlassBarEdgeBrush,
                        topLeft = Offset(width / 2, width / 2),
                        size = Size(size.width - width, size.height - width),
                        cornerRadius = CornerRadius((cornerRadius - width / 2).coerceAtLeast(0f)),
                        style = Stroke(width),
                        alpha = glowStrength,
                    )
                },
            ),
    )
}

/**
 * Refractive selected-tab lens based on Kyant0's LiquidBottomTabs implementation.
 * The frosted capsule stays behind it; Nuvio continues to own the tab icons and labels.
 */
@Composable
internal fun LiquidGlassSelectionSurface(
    backdrop: Backdrop,
    pressProgress: () -> Float,
    shape: () -> Shape,
    modifier: Modifier = Modifier,
    settings: LiquidGlassEffectSettings = LiquidGlassEffectDefaults.selectionLens,
    layerBlock: GraphicsLayerScope.() -> Unit = {},
) {
    val safeSettings = settings.sanitized(
        LiquidGlassEffectDefaults.selectionLens,
        LiquidGlassEffectDefaults.selectionLensLimits,
    )
    // Match the floating glass surface: tint comes from Nuvio's active palette, not system appearance.
    val containerColor = MaterialTheme.nuvio.colors.surface.copy(alpha = safeSettings.tintOpacity)
    Box(
        modifier
            .remeasureOnKeyChange(effectiveLiquidGlassCornerRadiusPercent(safeSettings.cornerRadiusPercent))
            .drawBackdrop(
                backdrop = backdrop,
                shape = shape,
                effects = {
                    if (safeSettings.blurRadiusDp > 0f) {
                        blur(safeSettings.blurRadiusDp.dp.toPx())
                    }
                    if (safeSettings.refractionHeightDp > 0f && safeSettings.refractionAmountDp > 0f) {
                        // Keep the configured lens active at rest so the settings controls have an
                        // immediate visible effect; pressProgress still drives the animated highlights
                        // and shadows below.
                        lens(
                            refractionHeight = safeSettings.refractionHeightDp.dp.toPx(),
                            refractionAmount = safeSettings.refractionAmountDp.dp.toPx(),
                            depthEffect = safeSettings.depthEffect,
                            chromaticAberration = safeSettings.chromaticAberration,
                        )
                    }
                },
                layerBlock = layerBlock,
                highlight = {
                    Highlight.Default.copy(alpha = pressProgress().coerceIn(0f, 1f))
                },
                shadow = {
                    Shadow(alpha = pressProgress().coerceIn(0f, 1f))
                },
                innerShadow = {
                    val progress = pressProgress().coerceIn(0f, 1f)
                    InnerShadow(radius = 8f.dp * progress, alpha = progress)
                },
                onDrawSurface = {
                    val progress = pressProgress().coerceIn(0f, 1f)
                    drawRect(
                        containerColor,
                        alpha = 1f - progress,
                    )
                    drawRect(Color.Black.copy(alpha = 0.03f * progress))
                },
            ),
    )
}

/**
 * Refract only the optional tab-icon source. Keep this pass separate from the primary lens so the
 * selected-lens blur affects the screen/bar backdrop, not the icon glyphs that should remain legible.
 * Like Kyant's selected-tab lens, this uses the same moving graphics-layer transform; unlike the
 * primary lens it draws no tint, blur, highlight, or shadow over the icon content.
 */
@Composable
internal fun LiquidGlassIconRefractionSurface(
    backdrop: Backdrop,
    shape: () -> Shape,
    modifier: Modifier = Modifier,
    settings: LiquidGlassEffectSettings = LiquidGlassEffectDefaults.selectionLens,
    layerBlock: GraphicsLayerScope.() -> Unit = {},
) {
    val safeSettings = settings.sanitized(
        LiquidGlassEffectDefaults.selectionLens,
        LiquidGlassEffectDefaults.selectionLensLimits,
    )
    Box(
        modifier
            .remeasureOnKeyChange(effectiveLiquidGlassCornerRadiusPercent(safeSettings.cornerRadiusPercent))
            .drawBackdrop(
                backdrop = backdrop,
                shape = shape,
                effects = {
                    if (safeSettings.refractionHeightDp > 0f && safeSettings.refractionAmountDp > 0f) {
                        lens(
                            refractionHeight = safeSettings.refractionHeightDp.dp.toPx(),
                            refractionAmount = safeSettings.refractionAmountDp.dp.toPx(),
                            depthEffect = safeSettings.depthEffect,
                            chromaticAberration = safeSettings.chromaticAberration,
                        )
                    }
                },
                highlight = { null },
                shadow = { null },
                layerBlock = layerBlock,
            ),
    )
}

internal val GlassSurfaceColor = Color(0xFF1C1C1E)

/**
 * A dark fill and a thin top-lit edge: the navigation pill without any backdrop (SOLID material,
 * glass turned off app-wide, or no RenderEffect). The glass version is [LiquidGlassFrostedBar].
 */
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
