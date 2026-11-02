package com.nuvio.app.features.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class LiquidGlassEditorScrollTargetTest {
    @Test
    fun freshPopupStartsAtTopEvenWhenPreviousSessionHadScrolled() {
        assertEquals(
            LiquidGlassEditorScrollTarget(index = 0, scrollOffset = 0),
            liquidGlassEditorScrollTarget(
                resetToTop = true,
                savedIndex = 14,
                savedScrollOffset = 83,
            ),
        )
    }

    @Test
    fun returningFromAnEditorRestoresSavedItemAndPixelOffset() {
        assertEquals(
            LiquidGlassEditorScrollTarget(index = 14, scrollOffset = 83),
            liquidGlassEditorScrollTarget(
                resetToTop = false,
                savedIndex = 14,
                savedScrollOffset = 83,
            ),
        )
    }

    @Test
    fun scrollTargetClampsInvalidSavedValuesToNonNegativeBounds() {
        assertEquals(
            LiquidGlassEditorScrollTarget(index = 0, scrollOffset = 0),
            liquidGlassEditorScrollTarget(
                resetToTop = false,
                savedIndex = -1,
                savedScrollOffset = -25,
            ),
        )
    }
}
