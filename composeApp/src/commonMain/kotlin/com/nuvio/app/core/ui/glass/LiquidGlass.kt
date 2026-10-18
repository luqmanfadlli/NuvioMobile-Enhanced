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
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
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
}

fun isLiquidGlassAvailable(): Boolean = isRenderEffectSupported()

fun isLiquidGlassSupported(): Boolean = LiquidGlassSettings.enabled && isLiquidGlassAvailable()

fun isLiquidRefractionSupported(): Boolean = isRuntimeShaderSupported()

interface LiquidGlassBackdropHost {
    val isReady: Boolean
    fun acquire() {}
    fun release() {}
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
    val blurRadius: Int = 10,
    val refraction: Int = 20,
    val tintPercent: Int = 40,
    val vibrancy: Boolean = true,
    val chromaticAberration: Boolean = false,
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
        blurRadius.toString(),
        refraction.toString(),
        tintPercent.toString(),
        vibrancy.encodeFlag(),
        chromaticAberration.encodeFlag(),
        edgeHighlight.encodeFlag(),
        shadow.encodeFlag(),
        selectionLens.encodeFlag(),
    ).joinToString(Separator)

    companion object {
        val Default = LiquidGlassStyle()
        val BlurRange = 0..40
        val RefractionRange = 0..40
        val TintRange = 0..100

        private const val EncodingVersion = "1"
        private const val Separator = ";"

        fun decode(raw: String?): LiquidGlassStyle {
            if (raw.isNullOrBlank()) return Default
            val parts = raw.split(Separator)
            if (parts.firstOrNull() != EncodingVersion) return Default
            fun int(index: Int, fallback: Int, range: IntRange): Int =
                parts.getOrNull(index)?.toIntOrNull()?.coerceIn(range) ?: fallback
            fun flag(index: Int, fallback: Boolean): Boolean =
                when (parts.getOrNull(index)) {
                    "1" -> true
                    "0" -> false
                    else -> fallback
                }
            return LiquidGlassStyle(
                material = LiquidGlassMaterial.fromKey(parts.getOrNull(1)),
                blurRadius = int(2, Default.blurRadius, BlurRange),
                refraction = int(3, Default.refraction, RefractionRange),
                tintPercent = int(4, Default.tintPercent, TintRange),
                vibrancy = flag(5, Default.vibrancy),
                chromaticAberration = flag(6, Default.chromaticAberration),
                edgeHighlight = flag(7, Default.edgeHighlight),
                shadow = flag(8, Default.shadow),
                selectionLens = flag(9, Default.selectionLens),
            )
        }

        private fun Boolean.encodeFlag(): String = if (this) "1" else "0"
    }
}

internal val DefaultGlassHighlight: () -> Highlight? = { Highlight.Default }

internal val DefaultGlassShadow: () -> Shadow? = { Shadow.Default }

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
): Modifier =
    if (backdrop == null || !isLiquidGlassSupported()) {
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

@Composable
internal fun isAmbientLiquidGlassActive(): Boolean {
    val backdrop = LocalLiquidGlassBackdrop.current ?: return false
    val host = backdrop as? LiquidGlassBackdropHost
    return isLiquidGlassSupported() && (host?.isReady ?: true)
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
    )
}
