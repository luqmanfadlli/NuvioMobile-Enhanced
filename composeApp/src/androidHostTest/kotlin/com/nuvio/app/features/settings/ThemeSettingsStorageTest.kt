package com.nuvio.app.features.settings

import android.app.Application
import android.content.Context
import com.nuvio.app.core.storage.ProfileScopedKey
import com.nuvio.app.core.storage.clearProfileBackgroundImages
import com.nuvio.app.core.sync.decodeSyncBoolean
import com.nuvio.app.core.sync.decodeSyncString
import com.nuvio.app.core.sync.encodeSyncString
import com.nuvio.app.core.ui.CustomThemeColors
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ThemeSettingsStorageTest {
    @BeforeTest
    fun initialize() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("nuvio_theme_settings", Context.MODE_PRIVATE).edit().clear().commit()
        ThemeSettingsStorage.initialize(context)
        ThemeSettingsStorage.clearCustomSettingsBackgroundImage()
        ThemeSettingsStorage.clearCustomLibraryCloudBackgroundImage()
    }

    @Test
    fun glowPreferenceSurvivesReloadAndSync() {
        ThemeSettingsStorage.saveNavBarGlowEnabled(false)
        ThemeSettingsStorage.initialize(RuntimeEnvironment.getApplication())
        assertEquals(false, ThemeSettingsStorage.loadNavBarGlowEnabled())
        val payload = ThemeSettingsStorage.exportToSyncPayload()
        assertEquals(false, payload.decodeSyncBoolean("nav_bar_glow_enabled"))

        ThemeSettingsStorage.saveNavBarGlowEnabled(true)
        ThemeSettingsStorage.replaceFromSyncPayload(payload)
        assertEquals(false, ThemeSettingsStorage.loadNavBarGlowEnabled())
    }

    @Test
    fun olderPayloadClearsOnlyTheCurrentProfilesGlowPreference() {
        val preferences = RuntimeEnvironment.getApplication()
            .getSharedPreferences("nuvio_theme_settings", Context.MODE_PRIVATE)
        val otherProfileKey = ProfileScopedKey.of("nav_bar_glow_enabled", 99)
        preferences.edit().putBoolean(otherProfileKey, false).commit()
        ThemeSettingsStorage.saveNavBarGlowEnabled(false)

        ThemeSettingsStorage.replaceFromSyncPayload(buildJsonObject {})

        assertNull(ThemeSettingsStorage.loadNavBarGlowEnabled())
        assertEquals(false, preferences.getBoolean(otherProfileKey, true))
    }


    @Test
    fun liquidGlassEffectSettingsSurviveReloadAndStayOutOfSync() {
        val bar = """{"cornerRadiusPercent":44.0,"blurRadiusDp":12.0}"""
        val lens = """{"cornerRadiusPercent":28.0,"refractionAmountDp":19.0}"""

        ThemeSettingsStorage.saveNavBarLiquidGlassBarEffects(bar)
        ThemeSettingsStorage.saveNavBarLiquidGlassSelectionEffects(lens)
        ThemeSettingsStorage.initialize(RuntimeEnvironment.getApplication())

        assertEquals(bar, ThemeSettingsStorage.loadNavBarLiquidGlassBarEffects())
        assertEquals(lens, ThemeSettingsStorage.loadNavBarLiquidGlassSelectionEffects())
        assertTrue(ThemeSettingsStorage.exportToSyncPayload().keys.none { "effects" in it })

        ThemeSettingsStorage.replaceFromSyncPayload(buildJsonObject {})
        assertEquals(bar, ThemeSettingsStorage.loadNavBarLiquidGlassBarEffects())
        assertEquals(lens, ThemeSettingsStorage.loadNavBarLiquidGlassSelectionEffects())
    }

    @Test
    fun defaultNavigationIconColorSurvivesReloadAndStaysOutOfSync() {
        ThemeSettingsStorage.saveNavBarDefaultIconColorEnabled(true)
        ThemeSettingsStorage.saveNavBarDefaultIconColor("#F141F5")
        ThemeSettingsStorage.initialize(RuntimeEnvironment.getApplication())

        assertTrue(ThemeSettingsStorage.exportToSyncPayload().keys.none { "icon_color" in it })

        ThemeSettingsStorage.replaceFromSyncPayload(buildJsonObject {
            put("nav_bar_default_icon_color", encodeSyncString("#123456"))
        })
        assertEquals(true, ThemeSettingsStorage.loadNavBarDefaultIconColorEnabled())
        assertEquals("#F141F5", ThemeSettingsStorage.loadNavBarDefaultIconColor())
    }

    @Test
    fun savedGradientRoundTripsThroughProfileSync() {
        val colors = CustomThemeColors(0x123456, 0xABCDEF, 0x654321).encode()
        ThemeSettingsStorage.saveCustomThemeColors(colors)
        ThemeSettingsStorage.saveSelectedTheme("CUSTOM")
        val payload = ThemeSettingsStorage.exportToSyncPayload()

        assertEquals(colors, payload.decodeSyncString("custom_theme_colors"))
        assertEquals("CUSTOM", payload.decodeSyncString("selected_theme"))

        ThemeSettingsStorage.saveCustomThemeColors(CustomThemeColors.Default.encode())
        ThemeSettingsStorage.replaceFromSyncPayload(payload)

        assertEquals(colors, ThemeSettingsStorage.loadCustomThemeColors())
        assertEquals("CUSTOM", ThemeSettingsStorage.loadSelectedTheme())
    }

    @Test
    fun choosingAnotherThemeKeepsTheSavedGradient() {
        val colors = CustomThemeColors(0x112233, 0x445566, 0x778899).encode()
        ThemeSettingsStorage.saveCustomThemeColors(colors)
        ThemeSettingsStorage.saveSelectedTheme("CUSTOM")
        ThemeSettingsStorage.saveSelectedTheme("WHITE")

        assertEquals(colors, ThemeSettingsStorage.loadCustomThemeColors())
        assertEquals(colors, ThemeSettingsStorage.exportToSyncPayload().decodeSyncString("custom_theme_colors"))
    }

    @Test
    fun replacingAnOlderPayloadClearsOnlyTheCurrentProfilesGradient() {
        val context = RuntimeEnvironment.getApplication()
        val preferences = context.getSharedPreferences("nuvio_theme_settings", Context.MODE_PRIVATE)
        val otherProfileKey = ProfileScopedKey.of("custom_theme_colors", 99)
        preferences.edit().putString(otherProfileKey, "#111111,#222222,#333333").commit()
        ThemeSettingsStorage.saveCustomThemeColors(CustomThemeColors.Default.encode())

        ThemeSettingsStorage.replaceFromSyncPayload(buildJsonObject {
            put("selected_theme", encodeSyncString("WHITE"))
        })

        assertNull(ThemeSettingsStorage.loadCustomThemeColors())
        assertEquals("WHITE", ThemeSettingsStorage.loadSelectedTheme())
        assertEquals("#111111,#222222,#333333", preferences.getString(otherProfileKey, null))
    }

    @Test
    fun liquidGlassSurfaceSettingsSurviveReloadRemainProfileScopedAndStayOutOfSync() {
        val context = RuntimeEnvironment.getApplication()
        val preferences = context.getSharedPreferences("nuvio_theme_settings", Context.MODE_PRIVATE)
        val settings = """{"search":{"enabled":true,"blurRadiusDp":12.0}}"""
        val settingsKey = "enhanced_local_liquid_glass_surface_settings"
        val currentProfileKey = ProfileScopedKey.of(settingsKey)
        val otherProfileKey = ProfileScopedKey.of(settingsKey, 99)

        ThemeSettingsStorage.saveLiquidGlassSurfaceSettings(settings)
        ThemeSettingsStorage.initialize(context)

        assertEquals(settings, ThemeSettingsStorage.loadLiquidGlassSurfaceSettings())
        assertEquals(settings, preferences.getString(currentProfileKey, null))
        assertNull(preferences.getString(otherProfileKey, null))
        assertTrue(ThemeSettingsStorage.exportToSyncPayload().keys.none { "surface_settings" in it })

        ThemeSettingsStorage.replaceFromSyncPayload(buildJsonObject {})

        assertEquals(settings, ThemeSettingsStorage.loadLiquidGlassSurfaceSettings())
    }


    @Test
    fun cloudLibraryBackgroundImageIsSeparateProfileLocalAndExcludedFromSync() {
        val context = RuntimeEnvironment.getApplication()
        val otherProfileFile = java.io.File(
            context.noBackupFilesDir.resolve("library_cloud_backgrounds"),
            "${ProfileScopedKey.of("enhanced_local_library_cloud_background_image", 99)}.jpg",
        )
        otherProfileFile.parentFile?.mkdirs()
        otherProfileFile.writeBytes(byteArrayOf(1, 2, 3))

        val settingsBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x11, 0x22, 0xFF.toByte(), 0xD9.toByte())
        val cloudBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x33, 0x44, 0xFF.toByte(), 0xD9.toByte())
        val settingsUri = ThemeSettingsStorage.saveCustomSettingsBackgroundImage(settingsBytes)
        val cloudUri = ThemeSettingsStorage.saveCustomLibraryCloudBackgroundImage(cloudBytes)

        assertTrue(settingsUri?.startsWith("file:") == true)
        assertTrue(cloudUri?.startsWith("file:") == true)
        assertTrue(settingsUri != cloudUri, "Cloud background must use a separate file from the Settings wallpaper.")
        assertEquals(cloudUri, ThemeSettingsStorage.loadCustomLibraryCloudBackgroundImageUri())

        val storedCloudBytes = java.io.File(java.net.URI(cloudUri!!)).readBytes()
        assertTrue(storedCloudBytes.contentEquals(cloudBytes))

        val otherProfileCloudBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x55, 0x66, 0xFF.toByte(), 0xD9.toByte())
        val otherProfileCloudUri = ThemeSettingsStorage.saveCustomLibraryCloudBackgroundImage(otherProfileCloudBytes, 98)
        assertTrue(otherProfileCloudUri?.startsWith("file:") == true)
        assertTrue(otherProfileCloudUri != cloudUri)
        assertEquals(cloudUri, ThemeSettingsStorage.loadCustomLibraryCloudBackgroundImageUri())
        assertTrue(java.io.File(java.net.URI(otherProfileCloudUri!!)).readBytes().contentEquals(otherProfileCloudBytes))
        assertTrue(ThemeSettingsStorage.clearCustomLibraryCloudBackgroundImage(98))
        assertEquals(cloudUri, ThemeSettingsStorage.loadCustomLibraryCloudBackgroundImageUri())
        assertTrue(
            ThemeSettingsStorage.exportToSyncPayload().keys.none {
                "cloud_background" in it || "library_background" in it
            },
            "The Cloud Library artwork must stay local to this profile.",
        )

        ThemeSettingsStorage.replaceFromSyncPayload(buildJsonObject {})
        assertEquals(cloudUri, ThemeSettingsStorage.loadCustomLibraryCloudBackgroundImageUri())
        assertEquals(settingsUri, ThemeSettingsStorage.loadCustomSettingsBackgroundImageUri())

        assertTrue(ThemeSettingsStorage.clearCustomLibraryCloudBackgroundImage(99))
        assertTrue(!otherProfileFile.exists(), "Clearing one profile must remove only that profile's Cloud background.")
        assertEquals(cloudUri, ThemeSettingsStorage.loadCustomLibraryCloudBackgroundImageUri())
        assertEquals(settingsUri, ThemeSettingsStorage.loadCustomSettingsBackgroundImageUri())

        assertTrue(ThemeSettingsStorage.clearCustomLibraryCloudBackgroundImage())
        assertNull(ThemeSettingsStorage.loadCustomLibraryCloudBackgroundImageUri())
        assertEquals(settingsUri, ThemeSettingsStorage.loadCustomSettingsBackgroundImageUri())
        ThemeSettingsStorage.clearCustomSettingsBackgroundImage()
    }

    @Test
    fun accountProfileBackgroundCleanupRemovesSettingsAndCloudImages() {
        val profileId = 97
        val imageBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x17, 0x18, 0xFF.toByte(), 0xD9.toByte())
        val settingsUri = ThemeSettingsStorage.saveCustomSettingsBackgroundImage(imageBytes, profileId)
        val cloudUri = ThemeSettingsStorage.saveCustomLibraryCloudBackgroundImage(imageBytes, profileId)

        assertTrue(settingsUri?.startsWith("file:") == true)
        assertTrue(cloudUri?.startsWith("file:") == true)

        try {
            val allRemoved = clearProfileBackgroundImages(
                profileIndices = profileId..profileId,
                removeSettingsImage = { id -> ThemeSettingsStorage.clearCustomSettingsBackgroundImage(id) },
                removeCloudImage = { id -> ThemeSettingsStorage.clearCustomLibraryCloudBackgroundImage(id) },
            )

            assertTrue(allRemoved)
            assertTrue(!java.io.File(java.net.URI(settingsUri!!)).exists())
            assertTrue(!java.io.File(java.net.URI(cloudUri!!)).exists())
        } finally {
            ThemeSettingsStorage.clearCustomSettingsBackgroundImage(profileId)
            ThemeSettingsStorage.clearCustomLibraryCloudBackgroundImage(profileId)
        }
    }

    @Test
    fun customSettingsBackgroundImageIsProfileLocalAndExcludedFromSync() {
        val context = RuntimeEnvironment.getApplication()
        val otherProfileFile = java.io.File(
            context.noBackupFilesDir.resolve("settings_backgrounds"),
            "${ProfileScopedKey.of("enhanced_local_settings_background_image", 99)}.jpg",
        )
        otherProfileFile.parentFile?.mkdirs()
        otherProfileFile.writeBytes(byteArrayOf(1, 2, 3))
        val bytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte())

        val imageUri = ThemeSettingsStorage.saveCustomSettingsBackgroundImage(bytes)
        assertTrue(imageUri?.startsWith("file:") == true)
        assertEquals(imageUri, ThemeSettingsStorage.loadCustomSettingsBackgroundImageUri())

        val replacementBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x01, 0x02, 0xFF.toByte(), 0xD9.toByte())
        assertEquals(imageUri, ThemeSettingsStorage.saveCustomSettingsBackgroundImage(replacementBytes))
        val storedBytes = java.io.File(java.net.URI(imageUri!!)).readBytes()
        assertTrue(storedBytes.contentEquals(replacementBytes), "Selecting another image must replace the current profile's file.")

        assertTrue(ThemeSettingsStorage.exportToSyncPayload().keys.none { "settings_background" in it })

        ThemeSettingsStorage.replaceFromSyncPayload(buildJsonObject {})
        assertEquals(imageUri, ThemeSettingsStorage.loadCustomSettingsBackgroundImageUri())

        assertTrue(ThemeSettingsStorage.clearCustomSettingsBackgroundImage(99))
        assertTrue(!otherProfileFile.exists(), "Deleting one profile should remove its background image.")
        assertEquals(imageUri, ThemeSettingsStorage.loadCustomSettingsBackgroundImageUri())

        ThemeSettingsStorage.clearCustomSettingsBackgroundImage()
        assertNull(ThemeSettingsStorage.loadCustomSettingsBackgroundImageUri())
    }


}
