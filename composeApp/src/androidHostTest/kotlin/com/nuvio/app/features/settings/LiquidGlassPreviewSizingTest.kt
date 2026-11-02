package com.nuvio.app.features.settings

import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.glass.LiquidGlassSurface
import com.nuvio.app.core.ui.glass.LiquidGlassSurfaceOverrides
import com.nuvio.app.core.ui.glass.LiquidGlassSurfaceRecipe
import com.nuvio.app.core.ui.glass.liquidGlassSurfaceRecipe
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LiquidGlassPreviewSizingTest {
    @Test
    fun streamSearchAndResumePreviewIsTallEnoughForBothControls() {
        assertEquals(152f, LIQUID_GLASS_STREAM_SELECTION_PREVIEW_HEIGHT_DP, 0f)
    }

    @Test
    fun playerPreviewHeightPreservesLandscapeSixteenByNineRatio() {
        assertEquals(180.dp, liquidGlassPlayerPreviewHeight(320.dp))
        assertEquals(202.5.dp, liquidGlassPlayerPreviewHeight(360.dp))
    }

    @Test
    fun playerPreviewScaleUsesAnEightHundredDpLandscapeCanvas() {
        assertEquals(0.4f, liquidGlassPlayerPreviewScale(320.dp), 0.001f)
        assertEquals(0.45f, liquidGlassPlayerPreviewScale(360.dp), 0.001f)
        assertEquals(1f, liquidGlassPlayerPreviewScale(800.dp), 0.001f)
    }

    @Test
    fun highlightSwitchDefaultsMatchProductionSurfaceRecipes() {
        assertEquals(false, LiquidGlassSurface.DETAIL_HEADER.defaultWithHighlight)
        assertEquals(true, LiquidGlassSurface.DETAILS.defaultWithHighlight)
        assertEquals(true, LiquidGlassSurface.BOTTOM_SHEETS.defaultWithHighlight)
        assertEquals(true, LiquidGlassSurface.STREAM_RESULTS.defaultWithHighlight)
    }

    @Test
    fun everyLiquidGlassSurfaceHasAValidPreviewRecipe() {
        LiquidGlassSurface.entries.forEach { surface ->
            val recipe = liquidGlassSurfaceRecipe(surface, LiquidGlassSurfaceOverrides())
            assertTrue(recipe.cornerRadiusPercent.isFinite() && recipe.cornerRadiusPercent in 0f..50f, surface.name)
            assertTrue(recipe.blurRadiusDp.isFinite() && recipe.blurRadiusDp in 0f..32f, surface.name)
            assertTrue(recipe.refractionHeightDp.isFinite() && recipe.refractionHeightDp in 0f..48f, surface.name)
            assertTrue(recipe.refractionAmountDp.isFinite() && recipe.refractionAmountDp in 0f..48f, surface.name)
            assertTrue(recipe.tintOpacity.isFinite() && recipe.tintOpacity in 0f..1f, surface.name)
        }
    }

    @Test
    fun libraryPreviewRecipesMatchTheirProductionGlassDefaults() {
        val defaults = LiquidGlassSurfaceOverrides()
        assertEquals(
            LiquidGlassSurfaceRecipe(36f, 12f, 6f, 9f, 0.32f),
            liquidGlassSurfaceRecipe(LiquidGlassSurface.LIBRARY_SAVED_TYPE_FILTER, defaults),
        )
        assertEquals(
            LiquidGlassSurfaceRecipe(36f, 12f, 6f, 9f, 0.32f),
            liquidGlassSurfaceRecipe(LiquidGlassSurface.LIBRARY_SAVED_SORT_FILTER, defaults),
        )
        assertEquals(
            LiquidGlassSurfaceRecipe(36f, 12f, 6f, 9f, 0.32f),
            liquidGlassSurfaceRecipe(LiquidGlassSurface.LIBRARY_CLOUD_FILTERS, defaults),
        )
        assertEquals(
            LiquidGlassSurfaceRecipe(36f, 12f, 6f, 9f, 0.34f),
            liquidGlassSurfaceRecipe(LiquidGlassSurface.LIBRARY_CLOUD_SEARCH, defaults),
        )
        assertEquals(
            LiquidGlassSurfaceRecipe(15f, 12f, 6f, 9f, 0.38f),
            liquidGlassSurfaceRecipe(LiquidGlassSurface.LIBRARY_CLOUD_CARDS, defaults),
        )
    }

    @Test
    fun detailActionPreviewTargetsMatchTheConfiguredProductionActionLayout() {
        val detailsOnly = setOf(LiquidGlassSurface.DETAILS)
        val detailsAndDownload = setOf(
            LiquidGlassSurface.DETAILS,
            LiquidGlassSurface.DETAIL_PRIMARY_ACTION,
        )

        assertEquals(
            detailsOnly,
            liquidGlassDetailActionPreviewSelectableSurfaces(
                iconActionRow = true,
                downloadActionAvailable = true,
            ),
        )
        assertEquals(
            detailsOnly,
            liquidGlassDetailActionPreviewSelectableSurfaces(
                iconActionRow = true,
                downloadActionAvailable = false,
            ),
        )
        assertEquals(
            detailsAndDownload,
            liquidGlassDetailActionPreviewSelectableSurfaces(
                iconActionRow = false,
                downloadActionAvailable = true,
            ),
        )
        assertEquals(
            detailsOnly,
            liquidGlassDetailActionPreviewSelectableSurfaces(
                iconActionRow = false,
                downloadActionAvailable = false,
            ),
        )
    }
}
