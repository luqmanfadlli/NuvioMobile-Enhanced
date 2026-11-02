package com.nuvio.app.core.ui.glass

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

class LiquidGlassSurfaceSettingsTest {
    @Test
    fun cornerRadiusUsesTheSamePercentRangeAsNavigationGlass() {
        assertEquals(0, effectiveLiquidGlassCornerRadiusPercent(0f))
        assertEquals(15, effectiveLiquidGlassCornerRadiusPercent(15f))
        assertEquals(36, effectiveLiquidGlassCornerRadiusPercent(36.2f))
        assertEquals(50, effectiveLiquidGlassCornerRadiusPercent(50f))
        assertEquals(50, effectiveLiquidGlassCornerRadiusPercent(Float.NaN))
        assertEquals(50, effectiveLiquidGlassCornerRadiusPercent(Float.POSITIVE_INFINITY))
        assertEquals(0, effectiveLiquidGlassCornerRadiusPercent(Float.NEGATIVE_INFINITY))
    }

    @Test
    fun cornerRadiusScalesTheNativeShapeInsteadOfRoundingRelativeToTheWholeCard() {
        val shape = RoundedCornerShape(24.dp) as CornerBasedShape
        val canvas = Size(width = 360f, height = 720f)
        val density = Density(1f)

        val nativeCorner = shape.topStart.toPx(canvas, density)
        val zero = scaleLiquidGlassCornerShape(shape, 0f) as CornerBasedShape
        val half = scaleLiquidGlassCornerShape(shape, 25f) as CornerBasedShape
        val original = scaleLiquidGlassCornerShape(shape, 50f) as CornerBasedShape

        assertEquals(24f, nativeCorner, 0.01f)
        assertEquals(0f, zero.topStart.toPx(canvas, density), 0.01f)
        assertEquals(12f, half.topStart.toPx(canvas, density), 0.01f)
        assertEquals(24f, original.topStart.toPx(canvas, density), 0.01f)
        assertEquals(0f, effectiveLiquidGlassCornerRadiusScale(0f), 0.001f)
        assertEquals(0.5f, effectiveLiquidGlassCornerRadiusScale(25f), 0.001f)
        assertEquals(1f, effectiveLiquidGlassCornerRadiusScale(50f), 0.001f)
    }

    @Test
    fun scaledShapeEqualityTracksRadiusValueForLiveUpdates() {
        val shape = RoundedCornerShape(24.dp)

        val radius25A = scaleLiquidGlassCornerShape(shape, 25f)
        val radius25B = scaleLiquidGlassCornerShape(shape, 25f)
        val radius8 = scaleLiquidGlassCornerShape(shape, 8f)

        assertEquals(radius25A, radius25B)
        assertNotEquals(radius25A, radius8)
    }

    @Test
    fun lensParametersStayInsideCornerRadiusAndComponentBounds() {
        val params = constrainLiquidGlassLensParameters(
            refractionHeightPx = 32f,
            refractionAmountPx = 120f,
            minimumCornerRadiusPx = 7.5f,
            minimumDimensionPx = 80f,
        )
        assertEquals(7.5f, params.refractionHeightPx)
        assertEquals(80f, params.refractionAmountPx)

        val squareShape = constrainLiquidGlassLensParameters(
            refractionHeightPx = 12f,
            refractionAmountPx = 18f,
            minimumCornerRadiusPx = 0f,
            minimumDimensionPx = 80f,
        )
        assertEquals(0f, squareShape.refractionHeightPx)
        assertEquals(18f, squareShape.refractionAmountPx)

        val unsafe = constrainLiquidGlassLensParameters(
            refractionHeightPx = Float.NaN,
            refractionAmountPx = Float.POSITIVE_INFINITY,
            minimumCornerRadiusPx = Float.NaN,
            minimumDimensionPx = Float.POSITIVE_INFINITY,
        )
        assertEquals(0f, unsafe.refractionHeightPx)
        assertEquals(0f, unsafe.refractionAmountPx)
    }

    @Test
    fun surfaceControlsMatchActualCapabilities() {
        val tintableSurfaces = setOf(
            LiquidGlassSurface.PLAYER,
            LiquidGlassSurface.DETAILS,
            LiquidGlassSurface.DETAIL_HEADER,
            LiquidGlassSurface.DETAIL_PRIMARY_ACTION,
            LiquidGlassSurface.DETAIL_TRAILER_SELECTOR,
            LiquidGlassSurface.DETAIL_EPISODE_SEARCH,
            LiquidGlassSurface.SEARCH,
            LiquidGlassSurface.LIBRARY,
            LiquidGlassSurface.LIBRARY_SAVED_TYPE_FILTER,
            LiquidGlassSurface.LIBRARY_SAVED_SORT_FILTER,
            LiquidGlassSurface.LIBRARY_CLOUD_FILTERS,
            LiquidGlassSurface.LIBRARY_CLOUD_SEARCH,
        )
        LiquidGlassSurface.entries.forEach { surface ->
            assertEquals(surface in tintableSurfaces, surface.supportsIconTint, surface.name)
        }
        assertFalse(LiquidGlassSurface.DETAIL_HEADER.supportsLens)
        assertFalse(LiquidGlassSurface.DETAIL_HEADER.defaultWithHighlight)
        assertTrue(LiquidGlassSurface.DETAILS.defaultWithHighlight)
    }

    @Test
    fun clearMaterialFallsBackWhenLensCannotActuallyRun() {
        assertEquals(
            LiquidGlassMaterial.LIQUID,
            effectiveLiquidGlassMaterial(
                LiquidGlassSurface.DETAIL_HEADER,
                LiquidGlassMaterial.CLEAR,
                refractionSupported = true,
                cornerRadiusPercent = 50f,
            ),
        )
        assertEquals(
            LiquidGlassMaterial.LIQUID,
            effectiveLiquidGlassMaterial(
                LiquidGlassSurface.SEARCH,
                LiquidGlassMaterial.CLEAR,
                refractionSupported = false,
                cornerRadiusPercent = 36f,
            ),
        )
        assertEquals(
            LiquidGlassMaterial.LIQUID,
            effectiveLiquidGlassMaterial(
                LiquidGlassSurface.SEARCH,
                LiquidGlassMaterial.CLEAR,
                refractionSupported = true,
                cornerRadiusPercent = 0f,
            ),
        )
        assertEquals(
            LiquidGlassMaterial.CLEAR,
            effectiveLiquidGlassMaterial(
                LiquidGlassSurface.SEARCH,
                LiquidGlassMaterial.CLEAR,
                refractionSupported = true,
                cornerRadiusPercent = 36f,
            ),
        )
    }

    @Test
    fun adaptiveGlassFilmAddsSheenOnBlackAndShadeOnWhite() {
        val blackFilm = liquidGlassAdaptiveFilm(Color.Black)
        val whiteFilm = liquidGlassAdaptiveFilm(Color.White)
        val translucentWhiteFilm = liquidGlassAdaptiveFilm(Color.White.copy(alpha = 0.08f))

        assertTrue(blackFilm.bodyTintAlpha > whiteFilm.bodyTintAlpha)
        assertTrue(blackFilm.topSheenAlpha > whiteFilm.topSheenAlpha)
        assertTrue(whiteFilm.bottomShadeAlpha > blackFilm.bottomShadeAlpha)
        assertTrue(translucentWhiteFilm.bodyTintAlpha > 0f)
        assertTrue(translucentWhiteFilm.bodyTintAlpha < blackFilm.bodyTintAlpha)
        assertTrue(translucentWhiteFilm.topSheenAlpha > 0f)
        assertTrue(blackFilm.bodyTintAlpha in 0f..0.08f)
        assertTrue(blackFilm.topSheenAlpha in 0f..0.14f)
        assertTrue(whiteFilm.bottomShadeAlpha in 0f..0.08f)
    }

    @Test
    fun glassDefaultsPrioritizeControlsAndKeepPassiveContentSurfacesQuiet() {
        val enabledByDefault = setOf(
            LiquidGlassSurface.PLAYER,
            LiquidGlassSurface.STREAM_FILTERS,
            LiquidGlassSurface.STREAM_SELECTION,
            LiquidGlassSurface.STREAM_RESULTS,
            LiquidGlassSurface.DETAILS,
            LiquidGlassSurface.DETAIL_HEADER,
            LiquidGlassSurface.DETAIL_PRIMARY_ACTION,
            LiquidGlassSurface.LIBRARY,
            LiquidGlassSurface.LIBRARY_SAVED_TYPE_FILTER,
            LiquidGlassSurface.LIBRARY_SAVED_SORT_FILTER,
            LiquidGlassSurface.LIBRARY_CLOUD_FILTERS,
            LiquidGlassSurface.LIBRARY_CLOUD_SEARCH,
            LiquidGlassSurface.LIBRARY_CALENDAR,
            LiquidGlassSurface.SEARCH,
            LiquidGlassSurface.BOTTOM_SHEETS,
            LiquidGlassSurface.DETAIL_TRAILER_SELECTOR,
            LiquidGlassSurface.DETAIL_TRAILER_MENU,
            LiquidGlassSurface.DETAIL_EPISODE_SEARCH,
            LiquidGlassSurface.DETAIL_EPISODE_GO_TO,
            LiquidGlassSurface.DETAIL_EPISODE_SORT,
            LiquidGlassSurface.CATEGORY_HEADER_TITLE,
            LiquidGlassSurface.CATEGORY_HEADER_BACK_BUTTON,
        )

        LiquidGlassSurface.entries.forEach { surface ->
            assertEquals(surface in enabledByDefault, surface.defaultEnabled, surface.name)
        }
    }

    @Test
    fun streamResultsFollowTheMasterGlassSwitchAndKeepPerSurfaceOptOut() {
        val surface = LiquidGlassSurface.STREAM_RESULTS
        val defaults = LiquidGlassSurfaceOverrides()

        assertTrue(surface.defaultEnabled)
        assertTrue(
            shouldRenderLiquidGlassSurface(
                surface = surface,
                overrides = defaults,
                preview = false,
                masterEnabled = true,
            ),
        )
        assertFalse(
            shouldRenderLiquidGlassSurface(
                surface = surface,
                overrides = defaults,
                preview = false,
                masterEnabled = false,
            ),
        )
        assertFalse(
            shouldRenderLiquidGlassSurface(
                surface = surface,
                overrides = defaults.copy(enabled = false),
                preview = false,
                masterEnabled = true,
            ),
        )
    }

    @Test
    fun defaultMaterialsReserveRefractionForSmallAndMediaFacingControls() {
        val liquidByDefault = setOf(
            LiquidGlassSurface.PLAYER,
            LiquidGlassSurface.DETAILS,
            LiquidGlassSurface.DETAIL_PRIMARY_ACTION,
            LiquidGlassSurface.DETAIL_TRAILER_SELECTOR,
            LiquidGlassSurface.LIBRARY,
            LiquidGlassSurface.CATEGORY_HEADER_BACK_BUTTON,
        )

        LiquidGlassSurface.entries.forEach { surface ->
            val expected = if (surface in liquidByDefault) {
                LiquidGlassMaterial.LIQUID
            } else {
                LiquidGlassMaterial.FROSTED
            }
            assertEquals(expected, surface.defaultMaterial, surface.name)
        }

        assertEquals(
            LiquidGlassMaterial.FROSTED,
            effectiveLiquidGlassMaterial(
                surface = LiquidGlassSurface.LIBRARY_CLOUD_FILTERS,
                material = null,
                refractionSupported = true,
                cornerRadiusPercent = 36f,
            ),
        )
        assertEquals(
            LiquidGlassMaterial.LIQUID,
            effectiveLiquidGlassMaterial(
                surface = LiquidGlassSurface.PLAYER,
                material = null,
                refractionSupported = true,
                cornerRadiusPercent = 50f,
            ),
        )
    }

    @Test
    fun librarySourceSwitchDefaultsToCapsuleLiquidGlass() {
        val surface = LiquidGlassSurface.LIBRARY
        val recipe = liquidGlassSurfaceRecipe(surface)

        assertEquals(LiquidGlassMaterial.LIQUID, surface.defaultMaterial)
        assertTrue(surface.supportsLens)
        assertEquals(50f, recipe.cornerRadiusPercent)
        assertEquals(12f, recipe.blurRadiusDp)
        assertEquals(0.22f, recipe.tintOpacity)
    }

    @Test
    fun defaultSurfaceRecipeSuppliesRuntimeValuesWhenNoOverridesAreSaved() {
        val surface = LiquidGlassSurface.STREAM_RESULTS
        val recipe = liquidGlassSurfaceRecipe(surface, LiquidGlassSurfaceOverrides())

        assertEquals(15f, recipe.cornerRadiusPercent)
        assertEquals(14f, recipe.blurRadiusDp)
        assertEquals(6f, recipe.refractionHeightDp)
        assertEquals(9f, recipe.refractionAmountDp)
        assertEquals(0.32f, recipe.tintOpacity)

        val nativeShape = RoundedCornerShape(12.dp)
        val effectiveShape = effectiveLiquidGlassSurfaceShape(
            shape = nativeShape,
            cornerRadiusPercent = recipe.cornerRadiusPercent,
            enabled = true,
        ) as CornerBasedShape
        val effectiveRadiusPx = effectiveShape.topStart.toPx(Size(100f, 100f), Density(1f))
        assertTrue(kotlin.math.abs(effectiveRadiusPx - 3.6f) < 0.01f)
    }

    @Test
    fun textBearingGlassDefaultsUseMoreTintThanTheMediaPlayer() {
        val defaults = LiquidGlassSurface.entries.associateWith { surface ->
            liquidGlassSurfaceRecipe(surface)
        }

        assertTrue(defaults.getValue(LiquidGlassSurface.PLAYER).tintOpacity < 0.10f)
        assertTrue(defaults.getValue(LiquidGlassSurface.SEARCH).tintOpacity >= 0.30f)
        assertTrue(defaults.getValue(LiquidGlassSurface.DETAIL_TRAILER_MENU).tintOpacity >= 0.40f)
        assertTrue(defaults.getValue(LiquidGlassSurface.BOTTOM_SHEETS).tintOpacity >= 0.45f)
    }

    @Test
    fun explicitSurfaceOverridesTakePrecedenceOverTheDefaultRecipe() {
        val recipe = liquidGlassSurfaceRecipe(
            surface = LiquidGlassSurface.SEARCH,
            overrides = LiquidGlassSurfaceOverrides(
                cornerRadiusPercent = 40f,
                blurRadiusDp = 20f,
                refractionHeightDp = 7f,
                refractionAmountDp = 11f,
                tintOpacity = 0.41f,
            ),
        )

        assertEquals(40f, recipe.cornerRadiusPercent)
        assertEquals(20f, recipe.blurRadiusDp)
        assertEquals(7f, recipe.refractionHeightDp)
        assertEquals(11f, recipe.refractionAmountDp)
        assertEquals(0.41f, recipe.tintOpacity)
    }

    @Test
    fun libraryCalendarDefaultLetsTheBackdropRemainVisibleThroughRealBlur() {
        val surface = LiquidGlassSurface.LIBRARY_CALENDAR
        val recipe = liquidGlassSurfaceRecipe(surface)

        assertTrue(surface.defaultEnabled)
        assertEquals(LiquidGlassMaterial.FROSTED, surface.defaultMaterial)
        assertEquals(44f, recipe.cornerRadiusPercent)
        assertEquals(20f, recipe.blurRadiusDp)
        assertEquals(3f, recipe.refractionHeightDp)
        assertEquals(5f, recipe.refractionAmountDp)
        assertEquals(0.28f, recipe.tintOpacity)
        assertTrue(surface in LiquidGlassSurfaceGroup.LIBRARY.surfaces)

        val customized = LiquidGlassSurfaceOverrides(
            enabled = false,
            tintOpacity = 0.62f,
            blurRadiusDp = 20f,
        )
        val updated = LiquidGlassSurfacesSettings().withSurface(surface, customized)
        assertEquals(customized, updated.forSurface(surface))
        assertEquals(LiquidGlassSurfaceOverrides(), updated.forSurface(LiquidGlassSurface.LIBRARY))
    }

    @Test
    fun libraryPreviewRecipesRemainIndependentOfTheCurrentlySelectedSurface() {
        val settings = LiquidGlassSurfacesSettings().withSurface(
            LiquidGlassSurface.LIBRARY_CLOUD_CARDS,
            LiquidGlassSurfaceOverrides(blurRadiusDp = 24f, tintOpacity = 0.70f),
        )

        val libraryRecipe = liquidGlassSurfaceRecipe(
            LiquidGlassSurface.LIBRARY,
            settings.forSurface(LiquidGlassSurface.LIBRARY),
        )
        val cloudCardsRecipe = liquidGlassSurfaceRecipe(
            LiquidGlassSurface.LIBRARY_CLOUD_CARDS,
            settings.forSurface(LiquidGlassSurface.LIBRARY_CLOUD_CARDS),
        )

        assertEquals(12f, libraryRecipe.blurRadiusDp)
        assertEquals(0.30f, libraryRecipe.tintOpacity)
        assertEquals(24f, cloudCardsRecipe.blurRadiusDp)
        assertEquals(0.70f, cloudCardsRecipe.tintOpacity)
    }

    @Test
    fun cloudLibraryCardShadowDefaultMatchesItsLiveSurface() {
        LiquidGlassSurface.entries.forEach { surface ->
            assertEquals(
                surface == LiquidGlassSurface.LIBRARY_CLOUD_CARDS,
                surface.defaultWithShadow,
                surface.name,
            )
        }
    }

    @Test
    fun previewRespectsSurfaceToggleButIgnoresTheGlobalMasterSwitch() {
        val surface = LiquidGlassSurface.DETAILS

        assertTrue(shouldRenderLiquidGlassSurface(surface, LiquidGlassSurfaceOverrides(), preview = true, masterEnabled = false))
        assertFalse(shouldRenderLiquidGlassSurface(surface, LiquidGlassSurfaceOverrides(enabled = false), preview = true, masterEnabled = true))
        assertTrue(shouldRenderLiquidGlassSurface(surface, LiquidGlassSurfaceOverrides(enabled = true), preview = false, masterEnabled = true))
        assertFalse(shouldRenderLiquidGlassSurface(surface, LiquidGlassSurfaceOverrides(enabled = true), preview = false, masterEnabled = false))
        assertFalse(shouldRenderLiquidGlassSurface(surface, LiquidGlassSurfaceOverrides(enabled = false), preview = false, masterEnabled = true))
        assertFalse(
            shouldRenderLiquidGlassSurface(
                surface,
                LiquidGlassSurfaceOverrides(enabled = true),
                preview = false,
                masterEnabled = true,
                platformSupported = false,
            ),
        )
        assertTrue(
            shouldRenderLiquidGlassSurface(
                surface,
                LiquidGlassSurfaceOverrides(enabled = true),
                preview = true,
                masterEnabled = false,
                platformSupported = false,
            ),
        )

        val settingsSurface = LiquidGlassSurface.SETTINGS
        assertFalse(
            shouldRenderLiquidGlassSurface(
                settingsSurface,
                LiquidGlassSurfaceOverrides(),
                preview = true,
                masterEnabled = false,
                platformSupported = false,
            ),
        )
        assertTrue(
            shouldRenderLiquidGlassSurface(
                settingsSurface,
                LiquidGlassSurfaceOverrides(enabled = true),
                preview = true,
                masterEnabled = false,
                platformSupported = false,
            ),
        )
    }

    @Test
    fun playerGlassBackdropActivityRespectsPreviewTogglePlatformAndReadiness() {
        val enabled = LiquidGlassSurfaceOverrides(enabled = true)
        val disabled = LiquidGlassSurfaceOverrides(enabled = false)

        assertTrue(
            shouldUseLiquidGlassBackdrop(
                surface = LiquidGlassSurface.PLAYER,
                overrides = enabled,
                preview = true,
                masterEnabled = false,
                platformSupported = true,
                backdropReady = true,
            ),
        )
        assertFalse(
            shouldUseLiquidGlassBackdrop(
                surface = LiquidGlassSurface.PLAYER,
                overrides = disabled,
                preview = true,
                masterEnabled = true,
                platformSupported = true,
                backdropReady = true,
            ),
        )
        assertFalse(
            shouldUseLiquidGlassBackdrop(
                surface = LiquidGlassSurface.PLAYER,
                overrides = enabled,
                preview = false,
                masterEnabled = false,
                platformSupported = true,
                backdropReady = true,
            ),
        )
        assertFalse(
            shouldUseLiquidGlassBackdrop(
                surface = LiquidGlassSurface.PLAYER,
                overrides = enabled,
                preview = true,
                masterEnabled = false,
                platformSupported = false,
                backdropReady = true,
            ),
        )
        assertFalse(
            shouldUseLiquidGlassBackdrop(
                surface = LiquidGlassSurface.PLAYER,
                overrides = enabled,
                preview = true,
                masterEnabled = false,
                platformSupported = true,
                backdropReady = false,
            ),
        )
    }

    @Test
    fun previewMayBypassTheMasterSwitchButNeverPlatformSupport() {
        assertTrue(
            shouldUseLiquidGlassRenderer(
                preview = true,
                masterEnabled = false,
                platformSupported = true,
            ),
        )
        assertFalse(
            shouldUseLiquidGlassRenderer(
                preview = true,
                masterEnabled = false,
                platformSupported = false,
            ),
        )
        assertFalse(
            shouldUseLiquidGlassRenderer(
                preview = false,
                masterEnabled = false,
                platformSupported = true,
            ),
        )
        assertTrue(
            shouldUseLiquidGlassRenderer(
                preview = false,
                masterEnabled = true,
                platformSupported = true,
            ),
        )
        assertFalse(
            shouldUseLiquidGlassRenderer(
                preview = false,
                masterEnabled = true,
                platformSupported = false,
            ),
        )
    }

    @Test
    fun surfaceMaterialSelectionIsIndependentAndRoundTrips() {
        val initial = LiquidGlassSurfacesSettings()
        assertEquals(null, initial.forSurface(LiquidGlassSurface.SEARCH).material)

        val selected = initial.withSurface(
            LiquidGlassSurface.SEARCH,
            LiquidGlassSurfaceOverrides(material = LiquidGlassMaterial.CLEAR),
        )

        assertEquals(LiquidGlassMaterial.CLEAR, selected.forSurface(LiquidGlassSurface.SEARCH).material)
        assertEquals(null, selected.forSurface(LiquidGlassSurface.LIBRARY).material)
        assertEquals(selected, decodeLiquidGlassSurfaceSettings(encodeLiquidGlassSurfaceSettings(selected)))
    }

    @Test
    fun solidMaterialIsNotAvailableForSurfaceOverrides() {
        assertEquals(
            null,
            LiquidGlassSurfaceOverrides(material = LiquidGlassMaterial.SOLID).sanitized().material,
        )
    }

    @Test
    fun materialModesMatchNavigationBarBlurAndRefractionRules() {
        val liquid = LiquidGlassStyle(material = LiquidGlassMaterial.LIQUID)
        val frosted = LiquidGlassStyle(material = LiquidGlassMaterial.FROSTED)
        val clear = LiquidGlassStyle(material = LiquidGlassMaterial.CLEAR)

        assertEquals(true, liquid.usesBlur)
        assertEquals(true, liquid.usesLens)
        assertEquals(true, frosted.usesBlur)
        assertEquals(false, frosted.usesLens)
        assertEquals(false, clear.usesBlur)
        assertEquals(true, clear.usesLens)
    }

    @Test
    fun unsetOverridesInheritTheOriginalRecipeAndCanBeResetIndependently() {
        val initial = LiquidGlassSurfacesSettings()
        assertEquals(LiquidGlassSurfaceOverrides(), initial.forSurface(LiquidGlassSurface.PLAYER))
        assertEquals(LiquidGlassSurfaceOverrides(), initial.forSurface(LiquidGlassSurface.SEARCH))

        val customized = initial.withSurface(
            LiquidGlassSurface.SEARCH,
            LiquidGlassSurfaceOverrides(enabled = true, blurRadiusDp = 12f),
        )
        assertEquals(true, customized.forSurface(LiquidGlassSurface.SEARCH).enabled)
        assertEquals(12f, customized.forSurface(LiquidGlassSurface.SEARCH).blurRadiusDp)
        assertEquals(LiquidGlassSurfaceOverrides(), customized.forSurface(LiquidGlassSurface.PLAYER))

        assertEquals(initial, customized.withSurface(LiquidGlassSurface.SEARCH, LiquidGlassSurfaceOverrides()))
    }

    @Test
    fun detailAndStreamSurfacesCanBeCustomizedIndependently() {
        val initial = LiquidGlassSurfacesSettings()
        val customized = initial
            .withSurface(
                LiquidGlassSurface.DETAILS,
                LiquidGlassSurfaceOverrides(enabled = false, cornerRadiusPercent = 50f),
            )
            .withSurface(
                LiquidGlassSurface.DETAIL_HEADER,
                LiquidGlassSurfaceOverrides(enabled = true, blurRadiusDp = 10f),
            )
            .withSurface(
                LiquidGlassSurface.DETAIL_CHIPS,
                LiquidGlassSurfaceOverrides(enabled = false, cornerRadiusPercent = 24f),
            )
            .withSurface(
                LiquidGlassSurface.DETAIL_CONTENT_CARDS,
                LiquidGlassSurfaceOverrides(enabled = true, cornerRadiusPercent = 16f),
            )
            .withSurface(
                LiquidGlassSurface.DETAIL_PRIMARY_ACTION,
                LiquidGlassSurfaceOverrides(enabled = true, cornerRadiusPercent = 50f),
            )
            .withSurface(
                LiquidGlassSurface.STREAM_SELECTION,
                LiquidGlassSurfaceOverrides(enabled = false, blurRadiusDp = 4f),
            )
            .withSurface(
                LiquidGlassSurface.STREAM_RESULTS,
                LiquidGlassSurfaceOverrides(enabled = true, blurRadiusDp = 20f),
            )

        assertEquals(false, customized.forSurface(LiquidGlassSurface.DETAILS).enabled)
        assertEquals(10f, customized.forSurface(LiquidGlassSurface.DETAIL_HEADER).blurRadiusDp)
        assertEquals(24f, customized.forSurface(LiquidGlassSurface.DETAIL_CHIPS).cornerRadiusPercent)
        assertEquals(16f, customized.forSurface(LiquidGlassSurface.DETAIL_CONTENT_CARDS).cornerRadiusPercent)
        assertEquals(50f, customized.forSurface(LiquidGlassSurface.DETAIL_PRIMARY_ACTION).cornerRadiusPercent)
        assertEquals(4f, customized.forSurface(LiquidGlassSurface.STREAM_SELECTION).blurRadiusDp)
        assertEquals(20f, customized.forSurface(LiquidGlassSurface.STREAM_RESULTS).blurRadiusDp)
        assertEquals(LiquidGlassSurfaceOverrides(), initial.forSurface(LiquidGlassSurface.STREAM_RESULTS))
    }

    @Test
    fun detailTrailerAndEpisodeControlsHaveIndependentGlassSettings() {
        val initial = LiquidGlassSurfacesSettings()
        val surfaces = listOf(
            LiquidGlassSurface.DETAIL_TRAILER_SELECTOR,
            LiquidGlassSurface.DETAIL_TRAILER_MENU,
            LiquidGlassSurface.DETAIL_EPISODE_SEARCH,
            LiquidGlassSurface.DETAIL_EPISODE_GO_TO,
            LiquidGlassSurface.DETAIL_EPISODE_SORT,
        )

        surfaces.forEach { surface ->
            assertTrue(surface.defaultEnabled, surface.name)
            assertEquals(LiquidGlassSurfaceOverrides(), initial.forSurface(surface), surface.name)
        }

        val customized = initial
            .withSurface(
                LiquidGlassSurface.DETAIL_TRAILER_SELECTOR,
                LiquidGlassSurfaceOverrides(enabled = false, blurRadiusDp = 5f),
            )
            .withSurface(
                LiquidGlassSurface.DETAIL_TRAILER_MENU,
                LiquidGlassSurfaceOverrides(enabled = true, blurRadiusDp = 22f),
            )
            .withSurface(
                LiquidGlassSurface.DETAIL_EPISODE_SEARCH,
                LiquidGlassSurfaceOverrides(enabled = false, cornerRadiusPercent = 28f),
            )
            .withSurface(
                LiquidGlassSurface.DETAIL_EPISODE_GO_TO,
                LiquidGlassSurfaceOverrides(enabled = true, tintOpacity = 0.42f),
            )
            .withSurface(
                LiquidGlassSurface.DETAIL_EPISODE_SORT,
                LiquidGlassSurfaceOverrides(enabled = false, withShadow = true),
            )

        assertEquals(false, customized.forSurface(LiquidGlassSurface.DETAIL_TRAILER_SELECTOR).enabled)
        assertEquals(5f, customized.forSurface(LiquidGlassSurface.DETAIL_TRAILER_SELECTOR).blurRadiusDp)
        assertEquals(22f, customized.forSurface(LiquidGlassSurface.DETAIL_TRAILER_MENU).blurRadiusDp)
        assertEquals(28f, customized.forSurface(LiquidGlassSurface.DETAIL_EPISODE_SEARCH).cornerRadiusPercent)
        assertEquals(0.42f, customized.forSurface(LiquidGlassSurface.DETAIL_EPISODE_GO_TO).tintOpacity)
        assertEquals(true, customized.forSurface(LiquidGlassSurface.DETAIL_EPISODE_SORT).withShadow)
        assertEquals(customized, decodeLiquidGlassSurfaceSettings(encodeLiquidGlassSurfaceSettings(customized)))
        assertEquals(initial, initial.withSurface(LiquidGlassSurface.DETAIL_TRAILER_MENU, LiquidGlassSurfaceOverrides()))
    }

    @Test
    fun categoryHeaderTitleAndBackButtonHaveIndependentGlassSettings() {
        val initial = LiquidGlassSurfacesSettings()
        assertTrue(LiquidGlassSurface.CATEGORY_HEADER_TITLE.defaultEnabled)
        assertTrue(LiquidGlassSurface.CATEGORY_HEADER_BACK_BUTTON.defaultEnabled)

        val customized = initial
            .withSurface(
                LiquidGlassSurface.CATEGORY_HEADER_TITLE,
                LiquidGlassSurfaceOverrides(enabled = false, cornerRadiusPercent = 18f),
            )
            .withSurface(
                LiquidGlassSurface.CATEGORY_HEADER_BACK_BUTTON,
                LiquidGlassSurfaceOverrides(enabled = true, cornerRadiusPercent = 50f, defaultIconColorArgb = 0xFF123456.toInt()),
            )

        assertEquals(false, customized.forSurface(LiquidGlassSurface.CATEGORY_HEADER_TITLE).enabled)
        assertEquals(18f, customized.forSurface(LiquidGlassSurface.CATEGORY_HEADER_TITLE).cornerRadiusPercent)
        assertEquals(true, customized.forSurface(LiquidGlassSurface.CATEGORY_HEADER_BACK_BUTTON).enabled)
        assertEquals(50f, customized.forSurface(LiquidGlassSurface.CATEGORY_HEADER_BACK_BUTTON).cornerRadiusPercent)
        assertEquals(0xFF123456.toInt(), customized.forSurface(LiquidGlassSurface.CATEGORY_HEADER_BACK_BUTTON).defaultIconColorArgb)
        assertEquals(customized, decodeLiquidGlassSurfaceSettings(encodeLiquidGlassSurfaceSettings(customized)))
        assertEquals(LiquidGlassSurfaceOverrides(), initial.forSurface(LiquidGlassSurface.CATEGORY_HEADER_TITLE))
        assertEquals(LiquidGlassSurfaceOverrides(), initial.forSurface(LiquidGlassSurface.CATEGORY_HEADER_BACK_BUTTON))
    }

    @Test
    fun categoryHeaderPreviewGroupContainsTheTitleAndBackButtonSeparately() {
        assertEquals(
            listOf(
                LiquidGlassSurface.CATEGORY_HEADER_TITLE,
                LiquidGlassSurface.CATEGORY_HEADER_BACK_BUTTON,
            ),
            LiquidGlassSurfaceGroup.CATEGORY_HEADER.surfaces,
        )
    }

    @Test
    fun providerFiltersHaveTheirOwnDefaultAndCanBeToggledWithoutChangingResults() {
        val initial = LiquidGlassSurfacesSettings()
        assertTrue(LiquidGlassSurface.STREAM_FILTERS.defaultEnabled)
        assertTrue(LiquidGlassSurface.STREAM_SELECTION.defaultEnabled)
        assertTrue(LiquidGlassSurface.STREAM_RESULTS.defaultEnabled)

        val updated = initial.withSurface(
            LiquidGlassSurface.STREAM_FILTERS,
            LiquidGlassSurfaceOverrides(enabled = false, blurRadiusDp = 4f),
        )

        assertEquals(false, updated.forSurface(LiquidGlassSurface.STREAM_FILTERS).enabled)
        assertEquals(4f, updated.forSurface(LiquidGlassSurface.STREAM_FILTERS).blurRadiusDp)
        assertEquals(LiquidGlassSurfaceOverrides(), updated.forSurface(LiquidGlassSurface.STREAM_SELECTION))
        assertEquals(LiquidGlassSurfaceOverrides(), updated.forSurface(LiquidGlassSurface.STREAM_RESULTS))
    }

    @Test
    fun interactiveLayoutGroupsContainOnlyTheirOwnGlassSurfaces() {
        assertEquals(
            listOf(
                LiquidGlassSurface.DETAIL_HEADER,
                LiquidGlassSurface.DETAILS,
                LiquidGlassSurface.DETAIL_PRIMARY_ACTION,
                LiquidGlassSurface.DETAIL_TRAILER_SELECTOR,
                LiquidGlassSurface.DETAIL_TRAILER_MENU,
                LiquidGlassSurface.DETAIL_CHIPS,
                LiquidGlassSurface.DETAIL_EPISODE_SEARCH,
                LiquidGlassSurface.DETAIL_EPISODE_GO_TO,
                LiquidGlassSurface.DETAIL_EPISODE_SORT,
                LiquidGlassSurface.DETAIL_CONTENT_CARDS,
            ),
            LiquidGlassSurfaceGroup.DETAIL_PAGE.surfaces,
        )
        assertEquals(
            listOf(
                LiquidGlassSurface.STREAM_FILTERS,
                LiquidGlassSurface.STREAM_SELECTION,
                LiquidGlassSurface.STREAM_RESULTS,
            ),
            LiquidGlassSurfaceGroup.STREAM_PICKER.surfaces,
        )
    }

    @Test
    fun libraryPreviewGroupContainsIndependentlyEditableSavedAndCloudControls() {
        assertEquals(
            listOf(
                LiquidGlassSurface.LIBRARY,
                LiquidGlassSurface.LIBRARY_SAVED_TYPE_FILTER,
                LiquidGlassSurface.LIBRARY_SAVED_SORT_FILTER,
                LiquidGlassSurface.LIBRARY_CLOUD_FILTERS,
                LiquidGlassSurface.LIBRARY_CLOUD_SEARCH,
                LiquidGlassSurface.LIBRARY_CLOUD_CARDS,
            ),
            LiquidGlassSurfaceGroup.LIBRARY.surfaces,
        )
    }

    @Test
    fun libraryGlassControlsCanBeChangedIndependentlyAndLegacyLibrarySettingsAreInherited() {
        val legacy = decodeLiquidGlassSurfaceSettings(
            """{"library":{"enabled":true,"blurRadiusDp":18.0,"cornerRadiusPercent":24.0}}""",
        )
        assertEquals(legacy.library, legacy.librarySavedTypeFilter)
        assertEquals(legacy.library, legacy.librarySavedSortFilter)
        assertEquals(legacy.library, legacy.libraryCloudFilters)
        assertEquals(legacy.library, legacy.libraryCloudSearch)
        assertEquals(legacy.library, legacy.libraryCloudCards)

        val custom = legacy.withSurface(
            LiquidGlassSurface.LIBRARY_CLOUD_SEARCH,
            LiquidGlassSurfaceOverrides(enabled = false, cornerRadiusPercent = 12f),
        )
        assertEquals(false, custom.libraryCloudSearch.enabled)
        assertEquals(12f, custom.libraryCloudSearch.cornerRadiusPercent)
        assertEquals(legacy.library, custom.library)
        assertEquals(legacy.library, custom.libraryCloudFilters)
        assertEquals(custom, decodeLiquidGlassSurfaceSettings(encodeLiquidGlassSurfaceSettings(custom)))
    }

    @Test
    fun linkedGlassEffectLeavesEachComponentShapeAndIconColorIndependent() {
        val local = LiquidGlassSurfaceOverrides(
            enabled = true,
            cornerRadiusPercent = 0f,
            defaultIconColorArgb = 0xFF123456.toInt(),
        )
        val shared = LiquidGlassSurfaceOverrides(
            enabled = false,
            blurRadiusDp = 20f,
            refractionHeightDp = 14f,
            refractionAmountDp = 24f,
            tintOpacity = 0.32f,
            withVibrancy = false,
            withHighlight = false,
            withShadow = true,
            chromaticAberration = true,
            depthEffect = true,
        )

        val linked = local.withGlassEffectFrom(shared)

        assertEquals(false, linked.enabled)
        assertEquals(20f, linked.blurRadiusDp)
        assertEquals(14f, linked.refractionHeightDp)
        assertEquals(24f, linked.refractionAmountDp)
        assertEquals(0.32f, linked.tintOpacity)
        assertEquals(false, linked.withVibrancy)
        assertEquals(false, linked.withHighlight)
        assertEquals(true, linked.withShadow)
        assertEquals(true, linked.chromaticAberration)
        assertEquals(true, linked.depthEffect)
        assertEquals(0f, linked.cornerRadiusPercent)
        assertEquals(0xFF123456.toInt(), linked.defaultIconColorArgb)
    }

    @Test
    fun olderSettingsMigrateDetailAndStreamOverridesToNewlySplitSurfaces() {
        val legacyJson = """
            {
              "details": {"enabled": false, "blurRadiusDp": 11.0, "cornerRadiusPercent": 32.0},
              "streamSelection": {"enabled": false, "blurRadiusDp": 7.0}
            }
        """.trimIndent()

        val restored = decodeLiquidGlassSurfaceSettings(legacyJson)

        assertEquals(restored.details, restored.detailHeader)
        assertEquals(restored.details, restored.detailChips)
        assertEquals(restored.details, restored.detailContentCards)
        assertEquals(restored.details, restored.detailPrimaryAction)
        assertEquals(restored.streamSelection, restored.streamResults)
    }

    @Test
    fun olderLibraryPreferencesSeedCalendarOverrides() {
        val legacy = decodeLiquidGlassSurfaceSettings(
            """{"library":{"enabled":false,"blurRadiusDp":9.0}}""",
        )

        assertEquals(legacy.library, legacy.libraryCalendar)
    }

    @Test
    fun sanitizingClampsNumericValuesWithoutInventingOverrides() {
        val sanitized = LiquidGlassSurfaceOverrides(
            cornerRadiusPercent = 80f,
            blurRadiusDp = -4f,
            refractionHeightDp = 200f,
            refractionAmountDp = Float.NaN,
            tintOpacity = 2f,
            defaultIconColorArgb = 0x006699CC,
        ).sanitized()

        assertEquals(50f, sanitized.cornerRadiusPercent)
        assertEquals(0f, sanitized.blurRadiusDp)
        assertEquals(48f, sanitized.refractionHeightDp)
        assertNull(sanitized.refractionAmountDp)
        assertEquals(1f, sanitized.tintOpacity)
        assertEquals(0xFF6699CC.toInt(), sanitized.defaultIconColorArgb)

        assertEquals(null, LiquidGlassSurfaceOverrides().sanitized().blurRadiusDp)
    }

    @Test
    fun surfacePreferencesRoundTripAndMalformedStorageFallsBackSafely() {
        val settings = LiquidGlassSurfacesSettings().withSurface(
            LiquidGlassSurface.PLAYER,
            LiquidGlassSurfaceOverrides(enabled = false, blurRadiusDp = 7.5f),
        ).withSurface(
            LiquidGlassSurface.LIBRARY,
            LiquidGlassSurfaceOverrides(enabled = true, defaultIconColorArgb = 0xFF336699.toInt()),
        ).withSurface(
            LiquidGlassSurface.BOTTOM_SHEETS,
            LiquidGlassSurfaceOverrides(
                enabled = false,
                cornerRadiusPercent = 22f,
                withVibrancy = false,
                withHighlight = false,
                withShadow = true,
            ),
        ).withSurface(
            LiquidGlassSurface.LIBRARY_CALENDAR,
            LiquidGlassSurfaceOverrides(
                enabled = true,
                blurRadiusDp = 16f,
                refractionHeightDp = 3f,
                refractionAmountDp = 5f,
                tintOpacity = 0.58f,
            ),
        )

        val encoded = encodeLiquidGlassSurfaceSettings(settings)
        assertEquals(settings, decodeLiquidGlassSurfaceSettings(encoded))
        assertEquals(LiquidGlassSurfacesSettings(), decodeLiquidGlassSurfaceSettings("{broken-json"))
        assertEquals(LiquidGlassSurfacesSettings(), decodeLiquidGlassSurfaceSettings(null))
    }
}
