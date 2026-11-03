package com.nuvio.app.core.ui.glass

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LiquidGlassExperimentalGateTest {
    private val legacy = LiquidGlassSurface.entries.filter { it.isLegacyGlass }
    private val experimental = LiquidGlassSurface.entries.filterNot { it.isLegacyGlass }

    @Test
    fun experimentalSurfacesAreOffByDefault() {
        val settings = LiquidGlassSurfacesSettings()
        experimental.forEach { surface ->
            assertEquals(false, liquidGlassRuntimeOverrides(surface, settings).enabled, surface.name)
        }
    }

    @Test
    fun turningTheExperimentOnEnablesEverySurfaceIncludingSettings() {
        val settings = LiquidGlassSurfacesSettings(experimentalEnabled = true)
        experimental.forEach { surface ->
            assertEquals(true, liquidGlassRuntimeOverrides(surface, settings).enabled, surface.name)
        }
        assertEquals(true, liquidGlassRuntimeOverrides(LiquidGlassSurface.SETTINGS, settings).enabled)
    }

    @Test
    fun shippedSurfacesAlwaysKeepTheirOriginalRecipe() {
        LiquidGlassGeneralStyle.entries.forEach { style ->
            listOf(false, true).forEach { experimentalOn ->
                val settings = LiquidGlassSurfacesSettings(experimentalEnabled = experimentalOn, generalStyle = style)
                legacy.forEach { surface ->
                    assertTrue(isLiquidGlassLegacyPassthrough(surface, settings), surface.name)
                    assertEquals(LiquidGlassSurfaceOverrides(), liquidGlassRuntimeOverrides(surface, settings))
                }
            }
        }
    }

    @Test
    fun theSelectedStyleDrivesEveryExperimentalSurface() {
        LiquidGlassGeneralStyle.entries.forEach { style ->
            val settings = LiquidGlassSurfacesSettings(experimentalEnabled = true, generalStyle = style)
            experimental.forEach { surface ->
                assertEquals(
                    style.baseOverrides(surface).copy(enabled = true),
                    liquidGlassRuntimeOverrides(surface, settings),
                    "${style.name} ${surface.name}",
                )
            }
        }
    }

    @Test
    fun savedPerSurfaceValuesAreIgnored() {
        val surface = LiquidGlassSurface.SEARCH
        val settings = LiquidGlassSurfacesSettings(experimentalEnabled = true)
            .withSurface(surface, LiquidGlassSurfaceOverrides(enabled = false, tintOpacity = 0.9f))
        assertEquals(
            settings.generalStyle.baseOverrides(surface).copy(enabled = true),
            liquidGlassRuntimeOverrides(surface, settings),
        )
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
            generalStyle = LiquidGlassGeneralStyle.VIVID,
        )
        assertEquals(settings, decodeLiquidGlassSurfaceSettings(encodeLiquidGlassSurfaceSettings(settings)))
    }
}
