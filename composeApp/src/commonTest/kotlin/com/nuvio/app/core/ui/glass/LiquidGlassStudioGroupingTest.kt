package com.nuvio.app.core.ui.glass

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import com.nuvio.app.features.settings.shouldUseLiquidGlassCustomizeSheetCardOverlay

class LiquidGlassStudioGroupingTest {
    @Test
    fun customizeSheetCardsUseThinOverlayOnlyWhenParentGlassIsReady() {
        assertTrue(
            shouldUseLiquidGlassCustomizeSheetCardOverlay(
                sheetGlassEnabled = true,
                backdropReady = true,
                nativeSheet = false,
            ),
        )
        assertFalse(
            shouldUseLiquidGlassCustomizeSheetCardOverlay(
                sheetGlassEnabled = true,
                backdropReady = false,
                nativeSheet = false,
            ),
        )
        assertFalse(
            shouldUseLiquidGlassCustomizeSheetCardOverlay(
                sheetGlassEnabled = false,
                backdropReady = true,
                nativeSheet = false,
            ),
        )
        assertFalse(
            shouldUseLiquidGlassCustomizeSheetCardOverlay(
                sheetGlassEnabled = true,
                backdropReady = true,
                nativeSheet = true,
            ),
        )
    }

    @Test
    fun settingsSurfaceOverrideControlsGlassVisibility() {
        val defaults = LiquidGlassSurfacesSettings()
        val surface = LiquidGlassSurface.SETTINGS

        assertFalse(
            shouldRenderLiquidGlassSurface(
                surface = surface,
                overrides = defaults.forSurface(surface),
                preview = false,
                masterEnabled = true,
            ),
        )

        val enabled = defaults.withSurface(
            surface,
            LiquidGlassSurfaceOverrides(enabled = true),
        )
        assertTrue(
            shouldRenderLiquidGlassSurface(
                surface = surface,
                overrides = enabled.forSurface(surface),
                preview = false,
                masterEnabled = true,
            ),
        )
        assertFalse(
            shouldRenderLiquidGlassSurface(
                surface = surface,
                overrides = enabled.forSurface(surface),
                preview = false,
                masterEnabled = false,
            ),
        )
    }

    @Test
    fun collapsedRootListContainsOnlySurfacesOutsideTheFourGroups() {
        val allSurfaces = LiquidGlassSurface.entries
        val groupedSurfaces = LiquidGlassSurfaceGroup.entries.flatMap { it.surfaces }
        val standalone = liquidGlassStandaloneSurfaces(allSurfaces)

        assertEquals(
            listOf(
                LiquidGlassSurface.PLAYER,
                LiquidGlassSurface.SEARCH,
                LiquidGlassSurface.SETTINGS,
                LiquidGlassSurface.BOTTOM_SHEETS,
            ),
            standalone,
        )
        assertEquals(allSurfaces.toSet(), (groupedSurfaces + standalone).toSet())
        assertEquals(allSurfaces.size, groupedSurfaces.size + standalone.size)
    }

    @Test
    fun settingsTogglePersistsWithoutCardSpecificSettings() {
        val encoded = encodeLiquidGlassSurfaceSettings(
            LiquidGlassSurfacesSettings().withSurface(
                LiquidGlassSurface.SETTINGS,
                LiquidGlassSurfaceOverrides(enabled = true),
            ),
        )
        val decoded = decodeLiquidGlassSurfaceSettings(encoded)

        assertTrue(decoded.forSurface(LiquidGlassSurface.SETTINGS).enabled == true)
        assertFalse(encoded.contains("profileWatchTime"))
        assertFalse(encoded.contains("supporterMembership"))
    }
}
