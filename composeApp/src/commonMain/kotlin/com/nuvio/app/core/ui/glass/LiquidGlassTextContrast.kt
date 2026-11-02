package com.nuvio.app.core.ui.glass

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import com.nuvio.app.core.ui.LocalNuvioThemeTokens
import com.nuvio.app.core.ui.NuvioThemeTokens
import com.nuvio.app.core.ui.nuvio

/**
 * Lifts only the low-emphasis text tokens (muted/disabled) that can sink into bright artwork behind
 * glass. Primary and secondary text keep the theme's values so the title/subtitle hierarchy stays
 * intact; legibility over bright pixels is handled by the glass tint, not by recoloring all text.
 */
internal fun NuvioThemeTokens.withLiquidGlassTextContrast(enabled: Boolean): NuvioThemeTokens =
    if (!enabled) {
        this
    } else {
        copy(
            colors = colors.copy(
                textMuted = lerp(colors.textMuted, colors.textSecondary, 0.5f),
                textDisabled = lerp(colors.textDisabled, colors.textMuted, 0.5f),
            ),
        )
    }

/**
 * Kept for API compatibility. Text on glass no longer gets a drop shadow: on small type it read as
 * a blurry outline and reduced legibility instead of improving it.
 */
@Suppress("UNUSED_PARAMETER")
internal fun TextStyle.withLiquidGlassTextShadow(enabled: Boolean): TextStyle = this

/** Kept for API compatibility; Material semantic colors are left unchanged. */
@Suppress("UNUSED_PARAMETER")
internal fun ColorScheme.withLiquidGlassTextContrast(enabled: Boolean): ColorScheme = this

/**
 * Apply the readable text treatment only to content sitting on an experimental glass surface.
 * It is a no-op while experimental surfaces are off, and for shipped surfaces ([surface] with
 * [LiquidGlassSurface.isLegacyGlass]) that still render their original recipe.
 */
@Composable
internal fun LiquidGlassTextContrastScope(
    enabled: Boolean,
    surface: LiquidGlassSurface? = null,
    content: @Composable () -> Unit,
) {
    val inPreview = LocalLiquidGlassPreviewSettings.current != null ||
        LocalLiquidGlassPreviewOverrides.current != null
    val experimentalActive = inPreview || LiquidGlassSettings.surfaceOverrides.experimentalEnabled
    val legacyPassthrough = !inPreview && surface != null && isLiquidGlassLegacyPassthrough(surface)
    if (!enabled || !experimentalActive || legacyPassthrough) {
        content()
        return
    }
    CompositionLocalProvider(
        LocalNuvioThemeTokens provides MaterialTheme.nuvio.withLiquidGlassTextContrast(enabled = true),
    ) {
        content()
    }
}
