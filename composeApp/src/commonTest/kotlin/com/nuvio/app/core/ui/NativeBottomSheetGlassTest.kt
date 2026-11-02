package com.nuvio.app.core.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NativeBottomSheetGlassTest {
    @Test
    fun nativeSystemGlassRequiresIos26OrNewer() {
        assertFalse(shouldUseNativeLiquidGlassSheetBackground(25L, surfaceGlassEnabled = true))
        assertTrue(shouldUseNativeLiquidGlassSheetBackground(26L, surfaceGlassEnabled = true))
        assertTrue(shouldUseNativeLiquidGlassSheetBackground(27L, surfaceGlassEnabled = true))
    }

    @Test
    fun disabledBottomSheetGlassKeepsTheConfiguredOpaqueBackground() {
        assertFalse(shouldUseNativeLiquidGlassSheetBackground(26L, surfaceGlassEnabled = false))
        assertFalse(shouldUseNativeLiquidGlassSheetBackground(25L, surfaceGlassEnabled = false))
    }
}
