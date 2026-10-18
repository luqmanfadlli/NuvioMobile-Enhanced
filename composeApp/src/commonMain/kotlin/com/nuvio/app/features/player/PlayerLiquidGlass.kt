package com.nuvio.app.features.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.kyant.backdrop.Backdrop
import com.nuvio.app.core.ui.glass.LiquidGlassBackdropHost
import kotlin.math.roundToInt

internal val PlayerGlassTint = Color(0xFF101014).copy(alpha = 0.52f)

private val VideoFrameColorFilter = ColorFilter.colorMatrix(
    ColorMatrix().apply {
        setToSaturation(0.3f)
        timesAssign(ColorMatrix().apply { setToScale(0.95f, 0.95f, 0.95f, 1f) })
    },
)

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
            filterQuality = FilterQuality.Low,
        )
    }
}

@Composable
internal expect fun rememberVideoFrameBackdrop(enabled: Boolean): VideoFrameBackdrop?
