package com.nuvio.app.core.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NativeLiquidGlassOptionMenuTest {
    @Test
    fun nativeSystemMenusRequireIos26OrNewer() {
        assertFalse(shouldUseNativeLiquidGlassOptionMenu(25L, surfaceGlassEnabled = true))
        assertTrue(shouldUseNativeLiquidGlassOptionMenu(26L, surfaceGlassEnabled = true))
        assertTrue(shouldUseNativeLiquidGlassOptionMenu(27L, surfaceGlassEnabled = true))
    }

    @Test
    fun disabledSurfaceKeepsTheComposeMenuFallback() {
        assertFalse(shouldUseNativeLiquidGlassOptionMenu(26L, surfaceGlassEnabled = false))
        assertFalse(shouldUseNativeLiquidGlassOptionMenu(25L, surfaceGlassEnabled = false))
    }

    @Test
    fun nativeDropdownRequiresAnEnabledRuntimeGlassSurface() {
        assertTrue(shouldUseNativeLiquidGlassDropdown(true, true, false, true, true))
        assertFalse(shouldUseNativeLiquidGlassDropdown(true, true, false, true, false))
        assertFalse(shouldUseNativeLiquidGlassDropdown(true, true, true, true, true))
        assertFalse(shouldUseNativeLiquidGlassDropdown(false, true, false, true, true))
        assertFalse(shouldUseNativeLiquidGlassDropdown(true, false, false, true, true))
        assertFalse(shouldUseNativeLiquidGlassDropdown(true, true, false, false, true))
    }
}
