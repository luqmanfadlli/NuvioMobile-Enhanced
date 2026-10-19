package com.nuvio.app.features.settings

import android.app.Application
import android.content.Context
import com.nuvio.app.core.sync.decodeSyncString
import com.nuvio.app.core.ui.AppTheme
import com.nuvio.app.core.ui.CustomThemeColors
import com.nuvio.app.core.ui.glass.LiquidGlassEffectDefaults
import com.nuvio.app.core.ui.glass.decodeLiquidGlassEffectSettings
import com.nuvio.app.features.membership.MemberAccessRepository
import com.nuvio.app.features.membership.MemberAssetStorage
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ThemeSettingsRepositoryTest {
    @BeforeTest
    fun initialize() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("nuvio_theme_settings", Context.MODE_PRIVATE).edit().clear().commit()
        ThemeSettingsStorage.initialize(context)
        MemberAssetStorage.initialize(context)
        MemberAccessRepository.clearLocalState()
        ThemeSettingsRepository.clearLocalState()
    }

    @AfterTest
    fun clearState() {
        ThemeSettingsRepository.clearLocalState()
    }

    @Test
    fun glowDefaultsOnAndRetainsItsPreferenceAcrossStyleChangesAndReloads() {
        ThemeSettingsRepository.ensureLoaded()
        assertEquals(true, ThemeSettingsRepository.navBarGlowEnabled.value)

        ThemeSettingsRepository.setNavBarGlowEnabled(false)
        ThemeSettingsRepository.setNavBarStyle(NavBarStyle.CLASSIC)
        ThemeSettingsRepository.setNavBarStyle(NavBarStyle.COMPACT)
        ThemeSettingsRepository.clearLocalState()
        ThemeSettingsRepository.ensureLoaded()

        assertEquals(false, ThemeSettingsRepository.navBarGlowEnabled.value)
        assertEquals(NavBarStyle.COMPACT, ThemeSettingsRepository.navBarStyle.value)

        ThemeSettingsStorage.replaceFromSyncPayload(kotlinx.serialization.json.buildJsonObject {})
        ThemeSettingsRepository.onProfileChanged()
        assertEquals(true, ThemeSettingsRepository.navBarGlowEnabled.value)
    }

    @Test
    fun legacyNavBarGlassIsMigratedOnceOnLoad() {
        ThemeSettingsStorage.saveNavBarGlass("1;liquid;14;8;55;1;0;1;1;1")
        ThemeSettingsRepository.clearLocalState()
        ThemeSettingsRepository.ensureLoaded()

        assertEquals(14f, ThemeSettingsRepository.navBarLiquidGlassBarEffects.value.blurRadiusDp)
        assertEquals(0.55f, ThemeSettingsRepository.navBarLiquidGlassBarEffects.value.tintOpacity)
        assertEquals(com.nuvio.app.core.ui.glass.LiquidGlassMaterial.LIQUID, ThemeSettingsRepository.navBarGlass.value.material)
        assertEquals(false, com.nuvio.app.core.ui.glass.LiquidGlassStyle.isLegacyEncoding(ThemeSettingsStorage.loadNavBarGlass()))
    }

    @Test
    fun liquidGlassEffectDraftsPersistOnlyWhenCommittedAndReload() {
        ThemeSettingsRepository.ensureLoaded()
        val persistedRevision = ThemeSettingsRepository.navBarLiquidGlassEffectsRevision.value
        val bar = LiquidGlassEffectDefaults.floatingBar.copy(
            blurRadiusDp = 17f,
            tintOpacity = 0.7f,
        )
        val lens = LiquidGlassEffectDefaults.selectionLens.copy(
            cornerRadiusPercent = 23f,
            chromaticAberration = false,
        )

        val defaultLens = ThemeSettingsRepository.navBarLiquidGlassSelectionEffects.value
        ThemeSettingsRepository.updateNavBarLiquidGlassBarEffects(bar)
        assertEquals(defaultLens, ThemeSettingsRepository.navBarLiquidGlassSelectionEffects.value)

        ThemeSettingsRepository.updateNavBarLiquidGlassSelectionEffects(lens)
        assertEquals(bar, ThemeSettingsRepository.navBarLiquidGlassBarEffects.value)

        assertEquals(null, ThemeSettingsStorage.loadNavBarLiquidGlassBarEffects())
        assertEquals(null, ThemeSettingsStorage.loadNavBarLiquidGlassSelectionEffects())
        assertEquals(persistedRevision, ThemeSettingsRepository.navBarLiquidGlassEffectsRevision.value)

        ThemeSettingsRepository.saveNavBarLiquidGlassEffectSettings()

        assertEquals(persistedRevision + 1L, ThemeSettingsRepository.navBarLiquidGlassEffectsRevision.value)
        assertEquals(
            bar,
            decodeLiquidGlassEffectSettings(
                ThemeSettingsStorage.loadNavBarLiquidGlassBarEffects(),
                LiquidGlassEffectDefaults.floatingBar,
                LiquidGlassEffectDefaults.floatingBarLimits,
            ),
        )
        assertEquals(
            lens,
            decodeLiquidGlassEffectSettings(
                ThemeSettingsStorage.loadNavBarLiquidGlassSelectionEffects(),
                LiquidGlassEffectDefaults.selectionLens,
                LiquidGlassEffectDefaults.selectionLensLimits,
            ),
        )

        ThemeSettingsRepository.clearLocalState()
        ThemeSettingsRepository.ensureLoaded()
        assertEquals(bar, ThemeSettingsRepository.navBarLiquidGlassBarEffects.value)
        assertEquals(lens, ThemeSettingsRepository.navBarLiquidGlassSelectionEffects.value)
    }

    @Test
    fun savingUnchangedLiquidGlassEffectsIsIdempotent() {
        ThemeSettingsRepository.ensureLoaded()
        val revision = ThemeSettingsRepository.navBarLiquidGlassEffectsRevision.value

        assertEquals(null, ThemeSettingsStorage.loadNavBarLiquidGlassBarEffects())
        assertEquals(null, ThemeSettingsStorage.loadNavBarLiquidGlassSelectionEffects())

        // Dismissing the sheet without editing either group must not persist defaults or trigger sync.
        ThemeSettingsRepository.saveNavBarLiquidGlassEffectSettings()
        assertEquals(null, ThemeSettingsStorage.loadNavBarLiquidGlassBarEffects())
        assertEquals(null, ThemeSettingsStorage.loadNavBarLiquidGlassSelectionEffects())
        assertEquals(revision, ThemeSettingsRepository.navBarLiquidGlassEffectsRevision.value)

        // A changed group commits once; dismissing again with identical values is a no-op.
        ThemeSettingsRepository.updateNavBarLiquidGlassBarEffects(
            LiquidGlassEffectDefaults.floatingBar.copy(blurRadiusDp = 6f),
        )
        ThemeSettingsRepository.saveNavBarLiquidGlassEffectSettings()
        val committedRevision = revision + 1L
        assertEquals(committedRevision, ThemeSettingsRepository.navBarLiquidGlassEffectsRevision.value)
        assertEquals(null, ThemeSettingsStorage.loadNavBarLiquidGlassSelectionEffects())

        ThemeSettingsRepository.saveNavBarLiquidGlassEffectSettings()
        assertEquals(committedRevision, ThemeSettingsRepository.navBarLiquidGlassEffectsRevision.value)
    }

    @Test
    fun selectedLensBackdropEffectsDefaultOnAndPersistIndependently() {
        ThemeSettingsRepository.ensureLoaded()
        val loadedDefaults = ThemeSettingsRepository.navBarLiquidGlassSelectionEffects.value
        assertEquals(com.nuvio.app.core.ui.glass.LiquidGlassEffectDefaults.selectionLens, loadedDefaults)

        // A custom theme must be unaffected by selected-lens-only settings.
        ThemeSettingsRepository.setCustomTheme(CustomThemeColors.solid(0x2255AA))
        val themePreferenceBefore = ThemeSettingsRepository.selectedThemePreference.value
        val effectiveThemeBefore = ThemeSettingsRepository.selectedTheme.value
        val themeColorsBefore = ThemeSettingsRepository.customThemeColors.value

        val defaults = LiquidGlassEffectDefaults.selectionLens
        assertEquals(
            true,
            decodeLiquidGlassEffectSettings(
                """{"refractionAmountDp":10}""",
                defaults,
                LiquidGlassEffectDefaults.selectionLensLimits,
            ).affectTabIcons,
        )
        assertEquals(
            true,
            decodeLiquidGlassEffectSettings(
                """{"refractionAmountDp":10}""",
                defaults,
                LiquidGlassEffectDefaults.selectionLensLimits,
            ).affectFloatingBar,
        )

        val configurations = listOf(
            defaults.copy(affectTabIcons = false, affectFloatingBar = true),
            defaults.copy(affectTabIcons = true, affectFloatingBar = false),
            defaults.copy(affectTabIcons = true, affectFloatingBar = true),
        )
        configurations.forEach { settings ->
            ThemeSettingsRepository.updateNavBarLiquidGlassSelectionEffects(settings)
            assertEquals(themePreferenceBefore, ThemeSettingsRepository.selectedThemePreference.value)
            assertEquals(effectiveThemeBefore, ThemeSettingsRepository.selectedTheme.value)
            assertEquals(themeColorsBefore, ThemeSettingsRepository.customThemeColors.value)

            ThemeSettingsRepository.saveNavBarLiquidGlassEffectSettings()
            ThemeSettingsRepository.clearLocalState()
            ThemeSettingsRepository.ensureLoaded()
            assertEquals(settings, ThemeSettingsRepository.navBarLiquidGlassSelectionEffects.value)
            assertEquals(themePreferenceBefore, ThemeSettingsRepository.selectedThemePreference.value)
            assertEquals(effectiveThemeBefore, ThemeSettingsRepository.selectedTheme.value)
            assertEquals(themeColorsBefore, ThemeSettingsRepository.customThemeColors.value)
        }
    }

    @Test
    fun defaultNavigationIconColorIsProfileScopedAndDoesNotChangeThemeColors() {
        ThemeSettingsRepository.ensureLoaded()
        assertEquals(false, ThemeSettingsRepository.navBarDefaultIconColorEnabled.value)
        // Must match NuvioTokens.textMuted so enabling the override does not change grey by default.
        assertEquals(0x969CA3, ThemeSettingsRepository.navBarDefaultIconColor.value)

        val colors = CustomThemeColors.solid(0x2255AA)
        ThemeSettingsRepository.setCustomTheme(colors)
        val selectedThemeBefore = ThemeSettingsRepository.selectedTheme.value
        val preferenceBefore = ThemeSettingsRepository.selectedThemePreference.value
        val themeColorsBefore = ThemeSettingsRepository.customThemeColors.value

        ThemeSettingsRepository.setNavBarDefaultIconColorEnabled(true)
        ThemeSettingsRepository.updateNavBarDefaultIconColor(0xF141F5)

        // The color is a draft until the sheet exits; both theme preference and palette stay intact.
        assertEquals(null, ThemeSettingsStorage.loadNavBarDefaultIconColor())
        assertEquals(selectedThemeBefore, ThemeSettingsRepository.selectedTheme.value)
        assertEquals(preferenceBefore, ThemeSettingsRepository.selectedThemePreference.value)
        assertEquals(themeColorsBefore, ThemeSettingsRepository.customThemeColors.value)

        ThemeSettingsRepository.saveNavBarDefaultIconColorSettings()
        assertEquals("#F141F5", ThemeSettingsStorage.loadNavBarDefaultIconColor())

        ThemeSettingsRepository.clearLocalState()
        ThemeSettingsRepository.ensureLoaded()
        assertEquals(true, ThemeSettingsRepository.navBarDefaultIconColorEnabled.value)
        assertEquals(0xF141F5, ThemeSettingsRepository.navBarDefaultIconColor.value)
        assertEquals(preferenceBefore, ThemeSettingsRepository.selectedThemePreference.value)
        assertEquals(themeColorsBefore, ThemeSettingsRepository.customThemeColors.value)
    }

    @Test
    fun defaultNavigationIconColorDraftsPersistOnlyWhenCommitted() {
        ThemeSettingsRepository.ensureLoaded()
        val revision = ThemeSettingsRepository.navBarDefaultIconColorRevision.value

        ThemeSettingsRepository.updateNavBarDefaultIconColor(0x123456)

        assertEquals(null, ThemeSettingsStorage.loadNavBarDefaultIconColor())
        assertEquals(revision, ThemeSettingsRepository.navBarDefaultIconColorRevision.value)

        ThemeSettingsRepository.saveNavBarDefaultIconColorSettings()

        assertEquals("#123456", ThemeSettingsStorage.loadNavBarDefaultIconColor())
        assertEquals(revision + 1L, ThemeSettingsRepository.navBarDefaultIconColorRevision.value)
    }

    @Test
    fun nonMembersCanSaveAndReloadASolidCustomTheme() {
        val colors = CustomThemeColors.solid(0x2255AA)

        ThemeSettingsRepository.setCustomTheme(colors)
        ThemeSettingsRepository.onProfileChanged()

        assertEquals(AppTheme.CUSTOM, ThemeSettingsRepository.selectedTheme.value)
        assertEquals(colors, ThemeSettingsRepository.customThemeColors.value)
        assertEquals(colors, ThemeSettingsRepository.customThemePreference.value)
        assertEquals(colors.encode(), ThemeSettingsStorage.loadCustomThemeColors())
    }

    @Test
    fun savingAGradientWithoutMembershipStoresOnlyItsMainColor() {
        ThemeSettingsRepository.setCustomTheme(CustomThemeColors(0x112233, 0x445566, 0x778899))
        val expected = CustomThemeColors.solid(0x445566)

        assertEquals(AppTheme.CUSTOM, ThemeSettingsRepository.selectedTheme.value)
        assertEquals(expected, ThemeSettingsRepository.customThemeColors.value)
        assertEquals(expected, ThemeSettingsRepository.customThemePreference.value)
        assertEquals(expected.encode(), ThemeSettingsStorage.loadCustomThemeColors())
    }

    @Test
    fun loadingASyncedGradientWithoutMembershipPreservesTheSavedStops() {
        val saved = CustomThemeColors(0x112233, 0x445566, 0x778899)
        ThemeSettingsStorage.saveCustomThemeColors(saved.encode())
        ThemeSettingsStorage.saveSelectedTheme(AppTheme.CUSTOM.name)

        ThemeSettingsRepository.ensureLoaded()
        ThemeSettingsRepository.setTheme(AppTheme.WHITE)
        ThemeSettingsRepository.setTheme(AppTheme.CUSTOM)

        assertEquals(AppTheme.CUSTOM, ThemeSettingsRepository.selectedTheme.value)
        assertEquals(CustomThemeColors.solid(0x445566), ThemeSettingsRepository.customThemeColors.value)
        assertEquals(saved, ThemeSettingsRepository.customThemePreference.value)
        assertEquals(saved.encode(), ThemeSettingsStorage.exportToSyncPayload().decodeSyncString("custom_theme_colors"))
    }
}
