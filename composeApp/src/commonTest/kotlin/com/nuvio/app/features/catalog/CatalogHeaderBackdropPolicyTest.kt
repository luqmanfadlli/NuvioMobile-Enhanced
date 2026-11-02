package com.nuvio.app.features.catalog

import com.nuvio.app.core.ui.glass.LiquidGlassSurface
import com.nuvio.app.core.ui.glass.LiquidGlassSurfaceOverrides
import com.nuvio.app.core.ui.glass.shouldRenderLiquidGlassSurface
import com.nuvio.app.features.home.PosterShape
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CatalogHeaderBackdropPolicyTest {
    @Test
    fun categoryHeaderTitleClipIsRemovedWhenGlassIsOff() {
        val surface = LiquidGlassSurface.CATEGORY_HEADER_TITLE
        val defaultOverrides = LiquidGlassSurfaceOverrides()

        val enabled = shouldRenderLiquidGlassSurface(
            surface = surface,
            overrides = defaultOverrides,
            preview = false,
            masterEnabled = true,
            platformSupported = true,
        )
        val globallyDisabled = shouldRenderLiquidGlassSurface(
            surface = surface,
            overrides = defaultOverrides,
            preview = false,
            masterEnabled = false,
            platformSupported = true,
        )
        val individuallyDisabled = shouldRenderLiquidGlassSurface(
            surface = surface,
            overrides = defaultOverrides.copy(enabled = false),
            preview = false,
            masterEnabled = true,
            platformSupported = true,
        )

        assertTrue(categoryHeaderTitleClipRequired(enabled))
        assertFalse(categoryHeaderTitleClipRequired(globallyDisabled))
        assertFalse(categoryHeaderTitleClipRequired(individuallyDisabled))
    }

    @Test
    fun categoryHeaderPreviewUsesDeterministicPosterArtworkForCatalogTiles() {
        val items = categoryHeaderPreviewItems()

        assertEquals(9, items.size)
        assertTrue(items.all { it.name.isNotBlank() })
        assertTrue(items.all { it.posterShape == PosterShape.Poster })
        assertTrue(
            items.all { item ->
                item.poster?.startsWith("https://image.tmdb.org/t/p/w342/") == true
            },
        )
    }

    @Test
    fun posterBackdropIsAvailableToBothCategoryHeaderGlassSurfacesOnly() {
        assertTrue(categoryHeaderUsesPosterBackdrop(LiquidGlassSurface.CATEGORY_HEADER_BACK_BUTTON))
        assertTrue(categoryHeaderUsesPosterBackdrop(LiquidGlassSurface.CATEGORY_HEADER_TITLE))
        assertFalse(categoryHeaderUsesPosterBackdrop(LiquidGlassSurface.DETAILS))
        assertFalse(categoryHeaderUsesPosterBackdrop(LiquidGlassSurface.SETTINGS))
    }

    @Test
    fun studioPreviewGlassIsScopedToTheHighlightedCategoryHeaderSurface() {
        assertTrue(
            categoryHeaderPreviewUsesGlass(
                LiquidGlassSurface.CATEGORY_HEADER_TITLE,
                previewMode = true,
                selectedPreviewSurface = LiquidGlassSurface.CATEGORY_HEADER_TITLE,
            ),
        )
        assertFalse(
            categoryHeaderPreviewUsesGlass(
                LiquidGlassSurface.CATEGORY_HEADER_BACK_BUTTON,
                previewMode = true,
                selectedPreviewSurface = LiquidGlassSurface.CATEGORY_HEADER_TITLE,
            ),
        )
        assertTrue(
            categoryHeaderPreviewUsesGlass(
                LiquidGlassSurface.CATEGORY_HEADER_BACK_BUTTON,
                previewMode = true,
                selectedPreviewSurface = LiquidGlassSurface.CATEGORY_HEADER_BACK_BUTTON,
            ),
        )
        assertFalse(
            categoryHeaderPreviewUsesGlass(
                LiquidGlassSurface.CATEGORY_HEADER_TITLE,
                previewMode = true,
                selectedPreviewSurface = LiquidGlassSurface.CATEGORY_HEADER_BACK_BUTTON,
            ),
        )
    }

    @Test
    fun liveCategoryHeaderGlassIsIndependentOfEditorSelection() {
        assertTrue(
            categoryHeaderPreviewUsesGlass(
                LiquidGlassSurface.CATEGORY_HEADER_TITLE,
                previewMode = false,
                selectedPreviewSurface = LiquidGlassSurface.CATEGORY_HEADER_BACK_BUTTON,
            ),
        )
        assertTrue(
            categoryHeaderPreviewUsesGlass(
                LiquidGlassSurface.CATEGORY_HEADER_BACK_BUTTON,
                previewMode = false,
                selectedPreviewSurface = LiquidGlassSurface.CATEGORY_HEADER_TITLE,
            ),
        )
    }
}
