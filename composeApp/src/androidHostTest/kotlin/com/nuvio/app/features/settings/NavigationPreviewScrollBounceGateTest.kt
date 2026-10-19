package com.nuvio.app.features.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NavigationPreviewScrollBounceGateTest {
    @Test
    fun `bounces once when leaving top and once when returning to top`() {
        val gate = NavigationPreviewScrollBounceGate()

        // Opening the sheet at the top must not animate the preview.
        assertNull(gate.onScrollabilityChanged(canScrollBackward = false))

        // The first downward movement triggers one bounce, not one per scroll update.
        assertEquals(1f, gate.onScrollabilityChanged(canScrollBackward = true))
        assertNull(gate.onScrollabilityChanged(canScrollBackward = true))
        assertNull(gate.onScrollabilityChanged(canScrollBackward = true))

        // Reaching the very top triggers the return bounce and resets the gate.
        assertEquals(-1f, gate.onScrollabilityChanged(canScrollBackward = false))
        assertNull(gate.onScrollabilityChanged(canScrollBackward = false))

        // It may trigger again only after the user leaves the top on a later scroll.
        assertEquals(1f, gate.onScrollabilityChanged(canScrollBackward = true))
    }
}
