package com.nuvio.app.core.ui.glass

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LiquidGlassExperimentalGateTest {
    private val legacy = LiquidGlassSurface.entries.filter { it.isLegacyGlass }
    private val experimental = LiquidGlassSurface.entries.filterNot { it.isLegacyGlass }

    @Test
    fun experimentalSurfacesAreOffByDefault() {
        val settings = LiquidGlassSurfacesSettings()
        assertFalse(settings.experimentalEnabled)
        experimental.forEach { surface ->
            assertEquals(false, liquidGlassRuntimeOverrides(surface, settings).enabled, surface.name)
        }
    }

    @Test
    fun shippedSurfacesAlwaysPassThroughUnlessCustomizedInAdvanced() {
        val customized = LiquidGlassSurfaceOverrides(blurRadiusDp = 3f)
        legacy.forEach { surface ->
            val withCustomization = LiquidGlassSurfacesSettings().withSurface(surface, customized)
            // Off, on, and on+general: the original call-site recipe is used.
            assertTrue(isLiquidGlassLegacyPassthrough(surface, withCustomization), surface.name)
            assertTrue(
                isLiquidGlassLegacyPassthrough(surface, withCustomization.copy(experimentalEnabled = true)),
                surface.name,
            )
            // Only an explicit Advanced customization opts a shipped surface into the new renderer.
            assertFalse(
                isLiquidGlassLegacyPassthrough(
                    surface,
                    withCustomization.copy(experimentalEnabled = true, advancedEnabled = true),
                ),
                surface.name,
            )
            assertTrue(
                isLiquidGlassLegacyPassthrough(
                    surface,
                    LiquidGlassSurfacesSettings(experimentalEnabled = true, advancedEnabled = true),
                ),
                surface.name,
            )
        }
    }

    @Test
    fun generalStyleAppliesUntilAdvancedIsOn() {
        val surface = LiquidGlassSurface.SEARCH
        val perSurface = LiquidGlassSurfaceOverrides(tintOpacity = 0.9f)
        val base = LiquidGlassSurfacesSettings(
            experimentalEnabled = true,
            generalStyle = LiquidGlassGeneralStyle.VIVID,
        ).withSurface(surface, perSurface)

        val general = liquidGlassRuntimeOverrides(surface, base)
        assertEquals(LiquidGlassGeneralStyle.VIVID.baseOverrides(surface), general)

        val advanced = liquidGlassRuntimeOverrides(surface, base.copy(advancedEnabled = true))
        assertEquals(0.9f, advanced.tintOpacity)
        assertEquals(LiquidGlassMaterial.LIQUID, advanced.material)
    }

    @Test
    fun readableStyleIsDenserThanTheDefaultRecipe() {
        experimental.forEach { surface ->
            val defaults = liquidGlassSurfaceRecipe(surface)
            val readable = liquidGlassSurfaceRecipe(surface, LiquidGlassGeneralStyle.READABLE.baseOverrides(surface))
            assertTrue(readable.tintOpacity > defaults.tintOpacity, surface.name)
            assertTrue(readable.blurRadiusDp >= defaults.blurRadiusDp, surface.name)
        }
    }

    @Test
    fun generalChoicesRoundTripThroughStorage() {
        val settings = LiquidGlassSurfacesSettings(
            experimentalEnabled = true,
            advancedEnabled = true,
            generalStyle = LiquidGlassGeneralStyle.BALANCED,
        )
        assertEquals(settings, decodeLiquidGlassSurfaceSettings(encodeLiquidGlassSurfaceSettings(settings)))
    }
}
