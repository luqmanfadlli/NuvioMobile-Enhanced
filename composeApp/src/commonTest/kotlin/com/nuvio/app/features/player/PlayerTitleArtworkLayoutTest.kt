package com.nuvio.app.features.player

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlayerTitleArtworkLayoutTest {

    @Test
    fun `logo bounds follow the existing player width breakpoints`() {
        val phone = PlayerLayoutMetrics.fromWidth(390.dp)
        assertEquals(36.dp, phone.titleLogoMaxHeight)
        assertEquals(26.dp, phone.timelineLogoMaxHeight)
        assertEquals(240.dp, phone.logoMaxWidth)

        val tablet = PlayerLayoutMetrics.fromWidth(800.dp)
        assertEquals(44.dp, tablet.titleLogoMaxHeight)
        assertEquals(32.dp, tablet.timelineLogoMaxHeight)
        assertEquals(280.dp, tablet.logoMaxWidth)

        val largeTablet = PlayerLayoutMetrics.fromWidth(1280.dp)
        assertEquals(48.dp, largeTablet.titleLogoMaxHeight)
        assertEquals(36.dp, largeTablet.timelineLogoMaxHeight)
        assertEquals(300.dp, largeTablet.logoMaxWidth)

        val desktop = PlayerLayoutMetrics.fromWidth(1600.dp)
        assertEquals(56.dp, desktop.titleLogoMaxHeight)
        assertEquals(40.dp, desktop.timelineLogoMaxHeight)
        assertEquals(320.dp, desktop.logoMaxWidth)
    }

    @Test
    fun `single line timeline artwork is shorter than the main title artwork`() {
        listOf(390.dp, 800.dp, 1280.dp, 1600.dp).forEach { width ->
            val metrics = PlayerLayoutMetrics.fromWidth(width)
            assertTrue(metrics.timelineLogoMaxHeight < metrics.titleLogoMaxHeight)
        }
    }
}
