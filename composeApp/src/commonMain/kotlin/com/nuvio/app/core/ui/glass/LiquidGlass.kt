package com.nuvio.app.core.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireDensity
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.opacity
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.isRenderEffectSupported
import com.kyant.backdrop.isRuntimeShaderSupported
import com.kyant.backdrop.shadow.Shadow

object LiquidGlassSettings {
    var enabled: Boolean by mutableStateOf(true)

    /** Profile-scoped per-area overrides; null fields preserve each original component recipe. */
    internal var surfaceOverrides: LiquidGlassSurfacesSettings by mutableStateOf(LiquidGlassSurfacesSettings())
}

fun isLiquidGlassAvailable(): Boolean = isRenderEffectSupported()

fun isLiquidGlassSupported(): Boolean = LiquidGlassSettings.enabled && isLiquidGlassAvailable()

/**
 * Keep actual rendering gated by platform support while letting Studio previews ignore the
 * app-wide switch. This never forces effects onto platforms without RenderEffect support.
 */
internal fun shouldUseLiquidGlassRenderer(
    preview: Boolean,
    masterEnabled: Boolean,
    platformSupported: Boolean,
): Boolean = platformSupported && (preview || masterEnabled)

fun isLiquidRefractionSupported(): Boolean = isRuntimeShaderSupported()

interface LiquidGlassBackdropHost {
    val isReady: Boolean
    fun acquire() {}
    fun release() {}
}

/** True only when a glass surface has a usable backdrop source on this platform. */
internal fun isLiquidGlassBackdropReady(backdrop: Backdrop?): Boolean {
    if (backdrop == null || !isLiquidGlassAvailable()) return false
    val host = backdrop as? LiquidGlassBackdropHost
    return host?.isReady ?: true
}

val LocalLiquidGlassBackdrop = staticCompositionLocalOf<Backdrop?> { null }

@Stable
class NuvioBackdrop internal constructor(
    internal val graphicsLayer: GraphicsLayer,
) : Backdrop, LiquidGlassBackdropHost {

    override val isCoordinatesDependent: Boolean = true

    internal var sourceCoordinates: LayoutCoordinates? by mutableStateOf(null)

    override val isReady: Boolean
        get() = sourceCoordinates != null

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?,
    ) {
        val target = coordinates ?: return
        val source = sourceCoordinates ?: return
        if (!target.isAttached || !source.isAttached) return
        val offset = offsetBetween(source, target)
        translate(-offset.x, -offset.y) {
            drawLayer(graphicsLayer)
        }
    }

    private fun offsetBetween(source: LayoutCoordinates, target: LayoutCoordinates): Offset {
        if (source.findRootCoordinates() === target.findRootCoordinates()) {
            try {
                return source.localPositionOf(target, Offset.Zero)
            } catch (_: Exception) {
            }
        }
        val targetOnScreen = target.positionOnScreen()
        val sourceOnScreen = source.positionOnScreen()
        return if (targetOnScreen.isSpecified && sourceOnScreen.isSpecified) {
            targetOnScreen - sourceOnScreen
        } else {
            target.positionInWindow() - source.positionInWindow()
        }
    }
}

@Composable
fun rememberNuvioBackdrop(): NuvioBackdrop {
    val graphicsLayer = rememberGraphicsLayer()
    return remember(graphicsLayer) { NuvioBackdrop(graphicsLayer) }
}

fun Modifier.nuvioBackdropSource(backdrop: NuvioBackdrop): Modifier =
    this then NuvioBackdropSourceElement(backdrop)

/**
 * Paints only the source backdrop into a rectangular underlay before a rounded glass surface.
 * This is intended for pinned headers: clipped glass corners must reveal the screen background,
 * never the scrolling items that happen to be underneath the sticky header.
 */
internal fun Modifier.liquidGlassBackdropUnderlay(backdrop: Backdrop?): Modifier {
    val host = backdrop as? LiquidGlassBackdropHost
    return if (backdrop == null || !isLiquidGlassAvailable() || (host != null && !host.isReady)) {
        this
    } else {
        drawBackdrop(
            backdrop = backdrop,
            shape = { RectangleShape },
            effects = { opacity(1f) },
        )
    }
}

private class NuvioBackdropSourceElement(
    val backdrop: NuvioBackdrop,
) : ModifierNodeElement<NuvioBackdropSourceNode>() {

    override fun create(): NuvioBackdropSourceNode = NuvioBackdropSourceNode(backdrop)

    override fun update(node: NuvioBackdropSourceNode) {
        if (node.backdrop != backdrop) {
            node.backdrop.sourceCoordinates = null
            node.backdrop = backdrop
        }
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "nuvioBackdropSource"
        properties["backdrop"] = backdrop
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NuvioBackdropSourceElement) return false
        return backdrop == other.backdrop
    }

    override fun hashCode(): Int = backdrop.hashCode()
}

private class NuvioBackdropSourceNode(
    var backdrop: NuvioBackdrop,
) : DrawModifierNode, GlobalPositionAwareModifierNode, Modifier.Node() {

    override fun ContentDrawScope.draw() {
        drawContent()
        val nodeDensity = requireDensity()
        val contentScope = this
        backdrop.graphicsLayer.record {
            val previousDensity = drawContext.density
            drawContext.density = nodeDensity
            try {
                contentScope.drawContent()
            } finally {
                drawContext.density = previousDensity
            }
        }
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        if (coordinates.isAttached) {
            backdrop.sourceCoordinates = coordinates
        }
    }

    override fun onDetach() {
        backdrop.sourceCoordinates = null
    }
}

@kotlinx.serialization.Serializable
enum class LiquidGlassMaterial(val key: String) {
    LIQUID("liquid"),
    FROSTED("frosted"),
    CLEAR("clear"),
    SOLID("solid"),
    ;

    companion object {
        fun fromKey(key: String?): LiquidGlassMaterial =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: LIQUID
    }
}

@Immutable
data class LiquidGlassStyle(
    val material: LiquidGlassMaterial = LiquidGlassMaterial.LIQUID,
    val vibrancy: Boolean = true,
    val edgeHighlight: Boolean = true,
    val shadow: Boolean = true,
    val selectionLens: Boolean = true,
) {
    val usesBackdrop: Boolean
        get() = material != LiquidGlassMaterial.SOLID

    val usesBlur: Boolean
        get() = material == LiquidGlassMaterial.LIQUID || material == LiquidGlassMaterial.FROSTED

    val usesLens: Boolean
        get() = material == LiquidGlassMaterial.LIQUID || material == LiquidGlassMaterial.CLEAR

    fun encode(): String = listOf(
        EncodingVersion,
        material.key,
        vibrancy.encodeFlag(),
        edgeHighlight.encodeFlag(),
        shadow.encodeFlag(),
        selectionLens.encodeFlag(),
    ).joinToString(Separator)

    companion object {
        val Default = LiquidGlassStyle()

        private const val EncodingVersion = "2"
        private const val LegacyEncodingVersion = "1"
        internal const val Separator = ";"

        fun decode(raw: String?): LiquidGlassStyle {
            if (raw.isNullOrBlank()) return Default
            val parts = raw.split(Separator)
            val (vibrancyAt, highlightAt, shadowAt, lensAt) = when (parts.firstOrNull()) {
                EncodingVersion -> listOf(2, 3, 4, 5)
                LegacyEncodingVersion -> listOf(5, 7, 8, 9)
                else -> return Default
            }
            fun flag(index: Int, fallback: Boolean): Boolean =
                when (parts.getOrNull(index)) {
                    "1" -> true
                    "0" -> false
                    else -> fallback
                }
            return LiquidGlassStyle(
                material = LiquidGlassMaterial.fromKey(parts.getOrNull(1)),
                vibrancy = flag(vibrancyAt, Default.vibrancy),
                edgeHighlight = flag(highlightAt, Default.edgeHighlight),
                shadow = flag(shadowAt, Default.shadow),
                selectionLens = flag(lensAt, Default.selectionLens),
            )
        }

        internal fun isLegacyEncoding(raw: String?): Boolean =
            raw?.split(Separator)?.firstOrNull() == LegacyEncodingVersion

        private fun Boolean.encodeFlag(): String = if (this) "1" else "0"
    }
}

internal val DefaultGlassHighlight: () -> Highlight? = { Highlight.Default }

internal val DefaultGlassShadow: () -> Shadow? = { Shadow.Default }

/**
 * Small adaptive light/dark film that keeps regular glass legible when the sampled backdrop has
 * almost no detail (notably AMOLED black). It complements, rather than replaces, backdrop blur,
 * refraction, and the shape highlight.
 */
internal data class LiquidGlassAdaptiveFilm(
    val bodyTintAlpha: Float,
    val topSheenAlpha: Float,
    val bottomShadeAlpha: Float,
)

internal fun liquidGlassAdaptiveFilm(background: Color): LiquidGlassAdaptiveFilm {
    // Transparent fallbacks do not describe the pixels sampled by the backdrop. Bias slightly dark
    // so translucent controls retain a restrained edge sheen without assuming a remote wallpaper.
    val colorLuminance = background.luminance()
    // Treat transparency as a dark app canvas underneath; a partially transparent tint must not
    // be judged as fully bright merely because its uncomposited RGB channels are white.
    val luminance = colorLuminance * background.alpha + 0.2f * (1f - background.alpha)
    return LiquidGlassAdaptiveFilm(
        bodyTintAlpha = ((0.55f - luminance) / 0.55f).coerceIn(0f, 1f) * 0.08f,
        topSheenAlpha = ((0.58f - luminance) / 0.58f).coerceIn(0f, 1f) * 0.14f,
        bottomShadeAlpha = ((luminance - 0.42f) / 0.58f).coerceIn(0f, 1f) * 0.08f,
    )
}

internal fun Modifier.liquidGlassSurface(
    backdrop: Backdrop?,
    shape: Shape,
    tint: Color,
    fallback: Color,
    blurRadius: Dp,
    refractionHeight: Dp = 0.dp,
    refractionAmount: Dp = refractionHeight,
    withVibrancy: Boolean = true,
    withHighlight: Boolean = true,
    withShadow: Boolean = false,
    fallbackBrush: Brush? = null,
    backdropOpacity: Float = 1f,
    chromaticAberration: Boolean = false,
    depthEffect: Boolean = false,
    preview: Boolean = false,
    /**
     * False (default) renders the original, shipped glass recipe exactly as before the experimental
     * surfaces work: no adaptive film and the original lens clamp. Only experimental surfaces opt in.
     */
    experimentalRecipe: Boolean = false,
): Modifier =
    if (!experimentalRecipe) {
        legacyLiquidGlassSurface(
            backdrop = backdrop,
            shape = shape,
            tint = tint,
            fallback = fallback,
            blurRadius = blurRadius,
            refractionHeight = refractionHeight,
            refractionAmount = refractionAmount,
            withVibrancy = withVibrancy,
            withHighlight = withHighlight,
            withShadow = withShadow,
            fallbackBrush = fallbackBrush,
            backdropOpacity = backdropOpacity,
            platformEnabled = shouldUseLiquidGlassRenderer(
                preview = preview,
                masterEnabled = LiquidGlassSettings.enabled,
                platformSupported = isLiquidGlassAvailable(),
            ),
        )
    } else if (
        !shouldUseLiquidGlassRenderer(
            preview = preview,
            masterEnabled = LiquidGlassSettings.enabled,
            platformSupported = isLiquidGlassAvailable(),
        )
    ) {
        if (fallbackBrush != null) clip(shape).background(fallbackBrush) else clip(shape).background(fallback, shape)
    } else if (backdrop == null) {
        // No source means there is nothing to blur or refract. Keep the app's fallback fill, but
        // retain a restrained adaptive film so regular glass remains distinguishable on black.
        val adaptiveFilm = liquidGlassAdaptiveFilm(fallback)
        val base = if (fallbackBrush != null) {
            clip(shape).background(fallbackBrush)
        } else {
            clip(shape).background(fallback, shape)
        }
        base.drawWithContent {
            drawContent()
            if (tint.alpha > 0f) drawRect(tint)
            drawAdaptiveFilm(adaptiveFilm)
        }
    } else {
        val adaptiveFilm = liquidGlassAdaptiveFilm(fallback)
        drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                if (withVibrancy) vibrancy()
                if (blurRadius > 0.dp) blur(blurRadius.toPx())
                val lensShape = shape as? CornerBasedShape
                if (lensShape != null && isLiquidRefractionSupported()) {
                    val parameters = constrainLiquidGlassLensParameters(
                        refractionHeightPx = refractionHeight.toPx(),
                        refractionAmountPx = refractionAmount.toPx(),
                        minimumCornerRadiusPx = minimumCornerRadiusPx(lensShape),
                        minimumDimensionPx = size.minDimension,
                    )
                    if (parameters.refractionHeightPx > 0f && parameters.refractionAmountPx > 0f) {
                        lens(
                            refractionHeight = parameters.refractionHeightPx,
                            refractionAmount = parameters.refractionAmountPx,
                            chromaticAberration = chromaticAberration,
                            depthEffect = depthEffect,
                        )
                    }
                }
                if (backdropOpacity < 1f) opacity(backdropOpacity.coerceIn(0f, 1f))
            },
            highlight = if (withHighlight) DefaultGlassHighlight else null,
            shadow = if (withShadow) DefaultGlassShadow else null,
            onDrawSurface = {
                if (tint.alpha > 0f) drawRect(tint)
                drawAdaptiveFilm(adaptiveFilm)
            },
        )
    }

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawAdaptiveFilm(film: LiquidGlassAdaptiveFilm) {
    if (film.bodyTintAlpha > 0f) drawRect(Color.White.copy(alpha = film.bodyTintAlpha))
    if (film.topSheenAlpha > 0f || film.bottomShadeAlpha > 0f) {
        drawRect(
            Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = film.topSheenAlpha),
                    Color.Transparent,
                    Color.Black.copy(alpha = film.bottomShadeAlpha),
                ),
            ),
        )
    }
}

/** Byte-for-byte the recipe that shipped before experimental surfaces (see origin/enhanced). */
private fun Modifier.legacyLiquidGlassSurface(
    backdrop: Backdrop?,
    shape: Shape,
    tint: Color,
    fallback: Color,
    blurRadius: Dp,
    refractionHeight: Dp,
    refractionAmount: Dp,
    withVibrancy: Boolean,
    withHighlight: Boolean,
    withShadow: Boolean,
    fallbackBrush: Brush?,
    backdropOpacity: Float,
    platformEnabled: Boolean,
): Modifier =
    if (backdrop == null || !platformEnabled) {
        if (fallbackBrush != null) clip(shape).background(fallbackBrush) else clip(shape).background(fallback)
    } else {
        drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                if (withVibrancy) vibrancy()
                if (blurRadius > 0.dp) blur(blurRadius.toPx())
                if (shape is CornerBasedShape && refractionHeight > 0.dp && refractionAmount > 0.dp) {
                    lens(
                        refractionHeight = refractionHeight.toPx().coerceAtMost(size.minDimension / 2f),
                        refractionAmount = refractionAmount.toPx(),
                    )
                }
                if (backdropOpacity < 1f) opacity(backdropOpacity.coerceIn(0f, 1f))
            },
            highlight = if (withHighlight) DefaultGlassHighlight else null,
            shadow = if (withShadow) DefaultGlassShadow else null,
            onDrawSurface = if (tint.alpha > 0f) {
                { drawRect(tint) }
            } else {
                null
            },
        )
    }

private fun BackdropEffectScope.minimumCornerRadiusPx(shape: CornerBasedShape): Float {
    val shapeSize = size
    val maximumRadius = (shapeSize.minDimension / 2f).coerceAtLeast(0f)
    val isLtr = layoutDirection == LayoutDirection.Ltr
    val corners = listOf(
        if (isLtr) shape.topStart else shape.topEnd,
        if (isLtr) shape.topEnd else shape.topStart,
        if (isLtr) shape.bottomEnd else shape.bottomStart,
        if (isLtr) shape.bottomStart else shape.bottomEnd,
    )
    return corners.minOf { corner -> corner.toPx(shapeSize, this).coerceIn(0f, maximumRadius) }
}

internal fun Modifier.liquidGlassBlur(
    backdrop: Backdrop?,
    radius: Dp,
    alphaProvider: () -> Float = { 1f },
): Modifier =
    if (backdrop == null || !isLiquidGlassAvailable()) {
        this
    } else {
        graphicsLayer { alpha = alphaProvider() }
            .drawPlainBackdrop(
                backdrop = backdrop,
                shape = { RectangleShape },
                effects = { blur(radius.toPx()) },
            )
    }

internal fun shouldUseLiquidGlassBackdrop(
    surface: LiquidGlassSurface,
    overrides: LiquidGlassSurfaceOverrides,
    preview: Boolean,
    masterEnabled: Boolean,
    platformSupported: Boolean,
    backdropReady: Boolean,
): Boolean = platformSupported && backdropReady &&
    shouldRenderLiquidGlassSurface(
        surface = surface,
        overrides = overrides,
        preview = preview,
        masterEnabled = masterEnabled,
        platformSupported = platformSupported,
    )

@Composable
internal fun isAmbientLiquidGlassActive(): Boolean {
    val backdrop = LocalLiquidGlassBackdrop.current ?: return false
    val host = backdrop as? LiquidGlassBackdropHost
    val previewSettings = LocalLiquidGlassPreviewSettings.current
    val previewOverrides = LocalLiquidGlassPreviewOverrides.current
    val preview = previewSettings != null || previewOverrides != null
    val overrides = previewSettings?.forSurface(LiquidGlassSurface.PLAYER)
        ?: previewOverrides
        ?: liquidGlassRuntimeOverrides(LiquidGlassSurface.PLAYER)

    return shouldUseLiquidGlassBackdrop(
        surface = LiquidGlassSurface.PLAYER,
        overrides = overrides,
        preview = preview,
        masterEnabled = LiquidGlassSettings.enabled,
        platformSupported = isLiquidGlassAvailable(),
        backdropReady = host?.isReady ?: true,
    )
}

@Composable
internal fun Modifier.ambientLiquidGlass(
    shape: Shape,
    tint: Color,
    fallback: Color,
    blurRadius: Dp = 16.dp,
    refractionHeight: Dp = 10.dp,
    refractionAmount: Dp = refractionHeight * 1.6f,
    withVibrancy: Boolean = true,
    withHighlight: Boolean = true,
    withShadow: Boolean = false,
    fallbackBrush: Brush? = null,
    backdropOpacity: Float = 1f,
    chromaticAberration: Boolean = false,
    depthEffect: Boolean = false,
    preview: Boolean = false,
    experimentalRecipe: Boolean = false,
): Modifier {
    val backdrop = LocalLiquidGlassBackdrop.current
    val host = backdrop as? LiquidGlassBackdropHost
    if (host != null) {
        DisposableEffect(host) {
            host.acquire()
            onDispose { host.release() }
        }
    }
    val ready = backdrop != null && (host?.isReady ?: true)
    return liquidGlassSurface(
        backdrop = if (ready) backdrop else null,
        shape = shape,
        tint = tint,
        fallback = fallback,
        blurRadius = blurRadius,
        refractionHeight = refractionHeight,
        refractionAmount = refractionAmount,
        withVibrancy = withVibrancy,
        withHighlight = withHighlight,
        withShadow = withShadow,
        fallbackBrush = fallbackBrush,
        backdropOpacity = backdropOpacity,
        chromaticAberration = chromaticAberration,
        depthEffect = depthEffect,
        preview = preview,
        experimentalRecipe = experimentalRecipe,
    )
}
