package com.nuvio.app.features.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SubtitleZoomBoundsTest {
    @Test
    fun pinchResizeModeUsesHysteresisThresholds() {
        assertNull(resolvePinchResizeMode(1.10f, PlayerResizeMode.Fit))
        assertEquals(PlayerResizeMode.Zoom, resolvePinchResizeMode(PINCH_ZOOM_IN_THRESHOLD, PlayerResizeMode.Fit))
        assertNull(resolvePinchResizeMode(PINCH_ZOOM_IN_THRESHOLD, PlayerResizeMode.Zoom))
        assertNull(resolvePinchResizeMode(0.90f, PlayerResizeMode.Zoom))
        assertEquals(PlayerResizeMode.Fit, resolvePinchResizeMode(PINCH_ZOOM_OUT_THRESHOLD, PlayerResizeMode.Zoom))
        assertNull(resolvePinchResizeMode(PINCH_ZOOM_OUT_THRESHOLD, PlayerResizeMode.Fit))
    }
    @Test
    fun zoomPreservesAspectInsideViewportAndFillReturnsFullBounds() {
        val zoomBounds = assertNotNull(
            computeFitVideoBoundsFraction(
                viewWidth = 2400f,
                viewHeight = 1080f,
                aspectRatio = 16f / 9f,
                resizeMode = PlayerResizeMode.Zoom,
            ),
        )
        assertEquals(0.1f, zoomBounds.left, 0.001f)
        assertEquals(0f, zoomBounds.top, 0.0001f)
        assertEquals(0.9f, zoomBounds.right, 0.001f)
        assertEquals(1f, zoomBounds.bottom, 0.0001f)

        val fillBounds = assertNotNull(
            computeFitVideoBoundsFraction(
                viewWidth = 2400f,
                viewHeight = 1080f,
                aspectRatio = 16f / 9f,
                resizeMode = PlayerResizeMode.Fill,
            ),
        )
        assertEquals(1f, fillBounds.width(), 0.0001f)
        assertEquals(1f, fillBounds.height(), 0.0001f)
    }

    @Test
    fun fitModeComputesPillarboxAndLetterboxBounds() {
        val pillarbox = assertNotNull(
            computeFitVideoBoundsFraction(
                viewWidth = 2400f,
                viewHeight = 1080f,
                aspectRatio = 16f / 9f,
                resizeMode = PlayerResizeMode.Fit,
            ),
        )
        assertEquals(0.1f, pillarbox.left, 0.001f)
        assertEquals(0.9f, pillarbox.right, 0.001f)
        assertEquals(0f, pillarbox.top, 0.001f)
        assertEquals(1f, pillarbox.bottom, 0.001f)
    }
}
