package com.nuvio.app.features.player

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlayerTitleArtworkLayoutTest {

    @Test
    fun `logo bounds follow the existing player width breakpoints`() {
        val phone = PlayerLayoutMetrics.fromWidth(390.dp)
        assertEquals(48.dp, phone.titleLogoMaxHeight)
        assertEquals(34.dp, phone.timelineLogoMaxHeight)
        assertEquals(280.dp, phone.logoMaxWidth)

        val tablet = PlayerLayoutMetrics.fromWidth(800.dp)
        assertEquals(56.dp, tablet.titleLogoMaxHeight)
        assertEquals(40.dp, tablet.timelineLogoMaxHeight)
        assertEquals(320.dp, tablet.logoMaxWidth)

        val largeTablet = PlayerLayoutMetrics.fromWidth(1280.dp)
        assertEquals(60.dp, largeTablet.titleLogoMaxHeight)
        assertEquals(44.dp, largeTablet.timelineLogoMaxHeight)
        assertEquals(360.dp, largeTablet.logoMaxWidth)

        val desktop = PlayerLayoutMetrics.fromWidth(1600.dp)
        assertEquals(64.dp, desktop.titleLogoMaxHeight)
        assertEquals(48.dp, desktop.timelineLogoMaxHeight)
        assertEquals(380.dp, desktop.logoMaxWidth)
    }

    @Test
    fun `single line timeline artwork is shorter than the main title artwork`() {
        listOf(390.dp, 800.dp, 1280.dp, 1600.dp).forEach { width ->
            val metrics = PlayerLayoutMetrics.fromWidth(width)
            assertTrue(metrics.timelineLogoMaxHeight < metrics.titleLogoMaxHeight)
        }
    }
}
