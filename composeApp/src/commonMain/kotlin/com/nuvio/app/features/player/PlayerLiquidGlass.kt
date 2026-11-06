package com.nuvio.app.features.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.nuvio.app.core.ui.glass.LiquidGlassBackdropHost
import com.nuvio.app.core.ui.glass.ambientLiquidGlass
import kotlin.math.roundToInt

internal val PlayerGlassTint = Color(0xFF101014).copy(alpha = 0.52f)

// ---------------------------------------------------------------------------------------------
// Player liquid glass: clear glass with a light blur over the live video.
// ---------------------------------------------------------------------------------------------

/** Light blur so the video stays readable through the glass. */
internal val PlayerGlassBlur: Dp = 6.dp

/** Near-clear: a faint white sheen instead of a dark fill. */
internal val PlayerGlassSurfaceTint = Color.White.copy(alpha = 0.05f)

/**
 * How strongly the blurred video copy is drawn inside the glass. Below 1 the live video shows through,
 * so the glass stays translucent (and never turns dark if a captured frame comes back black).
 */
internal const val PlayerGlassBackdropOpacity = 0.55f

/** Used when there is no live backdrop (Android < 12, protected/DRM video, glass turned off). */
internal val PlayerGlassFallback = Color.White.copy(alpha = 0.08f)

/** Hairline rim that sells the glass edge on top of Kyant's highlight. */
internal val PlayerGlassRim = Color.White.copy(alpha = 0.22f)

/** False when "Glass Backgrounds on Controls" is off: player buttons and pills draw no backing. */
internal val LocalPlayerControlGlassEnabled = staticCompositionLocalOf { true }

/**
 * The one glass recipe every player control uses: Kyant backdrop with vibrancy, a light blur, lens
 * refraction and edge highlight, sampled from the live video frame provided by
 * [rememberVideoFrameBackdrop]. Falls back to a translucent fill when the backdrop isn't available.
 */
@Composable
internal fun Modifier.playerLiquidGlass(
    shape: Shape,
    blurRadius: Dp = PlayerGlassBlur,
    refractionHeight: Dp = 8.dp,
    refractionAmount: Dp = 12.dp,
    tint: Color = PlayerGlassSurfaceTint,
    fallback: Color = PlayerGlassFallback,
    backdropOpacity: Float = PlayerGlassBackdropOpacity,
    rim: Boolean = true,
    // Panels (e.g. chapters) keep their glass when the control backings are turned off: their
    // text needs a backing to stay readable over the video.
    isControl: Boolean = true,
): Modifier {
    if (isControl && !LocalPlayerControlGlassEnabled.current) return this
    val glass = clip(shape).ambientLiquidGlass(
        shape = shape,
        tint = tint,
        fallback = fallback,
        blurRadius = blurRadius,
        refractionHeight = refractionHeight,
        refractionAmount = refractionAmount,
        withVibrancy = true,
        withHighlight = true,
        withShadow = false,
        backdropOpacity = backdropOpacity,
    )
    return if (rim) glass.border(0.75.dp, PlayerGlassRim, shape) else glass
}

// Keep the captured frame identical to the real picture so the glass reads as transparent (no dimming).
private val VideoFrameColorFilter: ColorFilter? = null

@Stable
class VideoFrameBackdrop : Backdrop, LiquidGlassBackdropHost {

    override val isCoordinatesDependent: Boolean = true

    private var frame: ImageBitmap? by mutableStateOf(null, neverEqualPolicy())

    private var frameBounds: Rect = Rect.Zero

    private var ready: Boolean by mutableStateOf(false)

    override val isReady: Boolean
        get() = ready

    internal var consumers by mutableIntStateOf(0)
        private set

    override fun acquire() {
        consumers += 1
    }

    override fun release() {
        consumers = (consumers - 1).coerceAtLeast(0)
    }

    internal fun publishFrame(image: ImageBitmap, boundsOnScreen: Rect) {
        frameBounds = boundsOnScreen
        frame = image
        if (!ready) ready = true
    }

    internal fun clearFrame() {
        frame = null
        frameBounds = Rect.Zero
        if (ready) ready = false
    }

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?,
    ) {
        val target = coordinates ?: return
        if (!target.isAttached) return
        val image = frame ?: return
        val bounds = frameBounds
        if (bounds.isEmpty) return
        val onScreen = target.positionOnScreen()
        val origin = if (onScreen.isSpecified) onScreen else target.positionInWindow()
        drawImage(
            image = image,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(image.width, image.height),
            dstOffset = IntOffset(
                (bounds.left - origin.x).roundToInt(),
                (bounds.top - origin.y).roundToInt(),
            ),
            dstSize = IntSize(bounds.width.roundToInt(), bounds.height.roundToInt()),
            colorFilter = VideoFrameColorFilter,
            filterQuality = FilterQuality.Medium,
        )
    }
}

@Composable
internal expect fun rememberVideoFrameBackdrop(enabled: Boolean): VideoFrameBackdrop?
