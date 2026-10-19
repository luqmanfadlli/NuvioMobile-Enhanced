package com.nuvio.app.core.ui.glass

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class LiquidGlassEffectSettingsTest {
    @Test
    fun floatingBarAndSelectionLensMatchTheDisplayedResetDefaults() {
        val bar = LiquidGlassEffectDefaults.floatingBar
        val lens = LiquidGlassEffectDefaults.selectionLens

        assertEquals(50f, bar.cornerRadiusPercent)
        assertEquals(10f, bar.blurRadiusDp)
        assertEquals(20f, bar.refractionHeightDp)
        assertEquals(20f, bar.refractionAmountDp)
        assertEquals(0.4f, bar.tintOpacity)
        assertEquals(false, bar.chromaticAberration)
        assertEquals(false, bar.depthEffect)

        assertEquals(50f, lens.cornerRadiusPercent)
        assertEquals(0f, lens.blurRadiusDp)
        assertEquals(12f, lens.refractionHeightDp)
        assertEquals(20f, lens.refractionAmountDp)
        assertEquals(0f, lens.tintOpacity)
        assertEquals(false, lens.chromaticAberration)
        assertEquals(true, lens.depthEffect)
        assertEquals(true, lens.affectTabIcons)
        assertEquals(true, lens.affectFloatingBar)
        assertNotEquals(bar, lens)
    }

    @Test
    fun optionalSourcesRespectIndependentTogglesAndRequireActiveRefraction() {
        val defaults = LiquidGlassEffectDefaults.selectionLens
        val bothEnabled = defaults.copy(affectTabIcons = true, affectFloatingBar = true)

        assertEquals(true, bothEnabled.shouldCaptureTabIcons())
        assertEquals(true, bothEnabled.shouldCaptureFloatingBar())

        // Turning either source toggle off must not affect the other one.
        assertEquals(false, bothEnabled.copy(affectTabIcons = false).shouldCaptureTabIcons())
        assertEquals(true, bothEnabled.copy(affectTabIcons = false).shouldCaptureFloatingBar())
        assertEquals(true, bothEnabled.copy(affectFloatingBar = false).shouldCaptureTabIcons())
        assertEquals(false, bothEnabled.copy(affectFloatingBar = false).shouldCaptureFloatingBar())

        // Kyant's lens is a no-op if either refraction dimension is zero.
        assertEquals(false, bothEnabled.copy(refractionHeightDp = 0f).shouldCaptureTabIcons())
        assertEquals(false, bothEnabled.copy(refractionAmountDp = 0f).shouldCaptureTabIcons())
        assertEquals(false, bothEnabled.copy(refractionHeightDp = 0f).shouldCaptureFloatingBar())
        assertEquals(false, bothEnabled.copy(refractionAmountDp = 0f).shouldCaptureFloatingBar())

        // Blur belongs to the main lens background; it must not turn on icon/bar refraction
        // sources by itself or cause crisp icon content to be hidden.
        val blurOnly = bothEnabled.copy(refractionHeightDp = 0f, blurRadiusDp = 4f)
        assertEquals(false, blurOnly.shouldCaptureTabIcons())
        assertEquals(false, blurOnly.shouldCaptureFloatingBar())
    }

    @Test
    fun effectiveCornerRadiusPercentMatchesRoundedShapeGeometry() {
        assertEquals(8, effectiveLiquidGlassCornerRadiusPercent(8.4f))
        assertEquals(9, effectiveLiquidGlassCornerRadiusPercent(8.5f))
        assertEquals(0, effectiveLiquidGlassCornerRadiusPercent(Float.NEGATIVE_INFINITY))
        assertEquals(50, effectiveLiquidGlassCornerRadiusPercent(Float.NaN))
    }

    @Test
    fun sanitizingClampsOutOfRangeValuesAndRestoresNonFiniteValues() {
        val defaults = LiquidGlassEffectDefaults.floatingBar
        val value = defaults.copy(
            cornerRadiusPercent = Float.POSITIVE_INFINITY,
            blurRadiusDp = -8f,
            refractionHeightDp = 100f,
            refractionAmountDp = Float.NaN,
            tintOpacity = 2f,
        )

        assertEquals(
            defaults.copy(
                blurRadiusDp = 0f,
                refractionHeightDp = 48f,
                tintOpacity = 1f,
            ),
            value.sanitized(defaults, LiquidGlassEffectDefaults.floatingBarLimits),
        )
    }

    @Test
    fun decodeUsesGroupSpecificDefaultsForMissingFields() {
        val defaults = LiquidGlassEffectDefaults.selectionLens
        val decoded = decodeLiquidGlassEffectSettings(
            """{"blurRadiusDp":12.0}""",
            defaults,
            LiquidGlassEffectDefaults.selectionLensLimits,
        )

        assertEquals(defaults.copy(blurRadiusDp = 12f), decoded)
    }

    @Test
    fun malformedPayloadFallsBackToTheGroupDefaults() {
        val defaults = LiquidGlassEffectDefaults.selectionLens

        assertEquals(
            defaults,
            decodeLiquidGlassEffectSettings("{broken-json", defaults, LiquidGlassEffectDefaults.selectionLensLimits),
        )
        assertEquals(
            defaults,
            decodeLiquidGlassEffectSettings(null, defaults, LiquidGlassEffectDefaults.selectionLensLimits),
        )
    }

    @Test
    fun serializedSettingsRoundTripAfterSanitizing() {
        val defaults = LiquidGlassEffectDefaults.selectionLens
        val custom = defaults.copy(
            cornerRadiusPercent = 23f,
            blurRadiusDp = 6.5f,
            refractionHeightDp = 18f,
            refractionAmountDp = 22f,
            chromaticAberration = false,
            depthEffect = true,
            tintOpacity = 0.32f,
        )
        val encoded = encodeLiquidGlassEffectSettings(
            custom,
            defaults,
            LiquidGlassEffectDefaults.selectionLensLimits,
        )

        assertEquals(
            custom,
            decodeLiquidGlassEffectSettings(encoded, defaults, LiquidGlassEffectDefaults.selectionLensLimits),
        )
    }

    @Test
    fun legacyV1NavBarGlassNumbersMigrateIntoEffectSettings() {
        val (bar, lens) = migrateLegacyNavBarGlassEffects("1;frosted;14;8;55;1;1;0;1;0")!!

        assertEquals(14f, bar.blurRadiusDp)
        assertEquals(8f, bar.refractionHeightDp)
        assertEquals(8f, bar.refractionAmountDp)
        assertEquals(0.55f, bar.tintOpacity)
        assertEquals(true, bar.chromaticAberration)
        assertEquals(true, lens.chromaticAberration)
    }

    @Test
    fun onlyLegacyV1StylesMigrate() {
        assertEquals(null, migrateLegacyNavBarGlassEffects(null))
        assertEquals(null, migrateLegacyNavBarGlassEffects(LiquidGlassStyle.Default.encode()))
    }

    @Test
    fun styleDecodesFlagsFromBothEncodings() {
        val legacy = LiquidGlassStyle.decode("1;clear;14;8;55;0;1;0;1;0")
        assertEquals(LiquidGlassStyle(LiquidGlassMaterial.CLEAR, vibrancy = false, edgeHighlight = false, shadow = true, selectionLens = false), legacy)
        assertEquals(legacy, LiquidGlassStyle.decode(legacy.encode()))
    }
}
