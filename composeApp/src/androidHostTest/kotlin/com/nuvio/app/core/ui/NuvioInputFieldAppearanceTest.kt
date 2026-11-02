package com.nuvio.app.core.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NuvioInputFieldAppearanceTest {
    @Test
    fun glassUsesTheOuterRimWithoutADuplicateMaterialOutline() {
        assertFalse(
            shouldDrawNuvioInputFieldOutline(
                glassSurfaceEnabled = true,
                glassRimCanRender = true,
            ),
        )
    }

    @Test
    fun nativeOutlineRemainsWhenGlassIsDisabledOrFallsBack() {
        assertTrue(
            shouldDrawNuvioInputFieldOutline(
                glassSurfaceEnabled = false,
                glassRimCanRender = true,
            ),
        )
        assertTrue(
            shouldDrawNuvioInputFieldOutline(
                glassSurfaceEnabled = true,
                glassRimCanRender = false,
            ),
        )
    }
}
