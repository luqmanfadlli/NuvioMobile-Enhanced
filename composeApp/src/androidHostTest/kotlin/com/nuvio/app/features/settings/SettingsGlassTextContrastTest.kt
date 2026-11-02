package com.nuvio.app.features.settings

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.material3.darkColorScheme
import com.nuvio.app.core.ui.ThemeColors
import com.nuvio.app.core.ui.defaultNuvioThemeTokens
import com.nuvio.app.core.ui.glass.withLiquidGlassTextContrast
import com.nuvio.app.core.ui.glass.withLiquidGlassTextShadow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsGlassTextContrastTest {
    private val original = defaultNuvioThemeTokens(
        palette = ThemeColors.White,
        amoled = false,
        colorScheme = null,
    )

    @Test
    fun glassContrastKeepsPrimaryAndSecondaryHierarchy() {
        val readable = original.withSettingsGlassTextContrast(enabled = true)

        assertEquals(original.colors.textPrimary, readable.colors.textPrimary)
        assertEquals(original.colors.textSecondary, readable.colors.textSecondary)
        assertEquals(original.colors.surfaceCard, readable.colors.surfaceCard)
        assertEquals(original.colors.accent, readable.colors.accent)
    }

    @Test
    fun glassContrastOnlyLiftsLowEmphasisTextTowardsSecondary() {
        val readable = original.withLiquidGlassTextContrast(true)

        assertTrue(readable.colors.textMuted.luminance() >= original.colors.textMuted.luminance())
        assertTrue(readable.colors.textMuted.luminance() <= original.colors.textSecondary.luminance())
        assertTrue(readable.colors.textSecondary.luminance() >= readable.colors.textMuted.luminance())
    }

    @Test
    fun disabledGlassContrastIsIdentity() {
        assertEquals(original, original.withLiquidGlassTextContrast(false))
        assertEquals(original, original.withSettingsGlassTextContrast(enabled = false))
        val scheme = darkColorScheme()
        assertEquals(scheme, scheme.withLiquidGlassTextContrast(true))
    }

    @Test
    fun glassTextNeverGetsADropShadow() {
        val style = TextStyle(color = Color.White)
        assertNull(style.withLiquidGlassTextShadow(true).shadow)
        assertNull(style.withSettingsGlassTextShadow(enabled = true).shadow)
        assertEquals(style, style.withLiquidGlassTextShadow(true))
    }
}
