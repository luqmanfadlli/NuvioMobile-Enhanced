package com.nuvio.app.features.settings

import android.content.Context
import android.content.SharedPreferences
import java.io.File
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.nuvio.app.core.sync.decodeSyncBoolean
import com.nuvio.app.core.sync.decodeSyncString
import com.nuvio.app.core.sync.encodeSyncBoolean
import com.nuvio.app.core.sync.encodeSyncString
import com.nuvio.app.core.storage.ProfileScopedKey
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

actual object ThemeSettingsStorage {
    private const val preferencesName = "nuvio_theme_settings"
    private const val selectedThemeKey = "selected_theme"
    private const val customThemeColorsKey = "custom_theme_colors"
    private const val amoledEnabledKey = "amoled_enabled"
    private const val liquidGlassNativeTabBarEnabledKey = "liquid_glass_native_tab_bar_enabled"
    private const val dynamicArtworkBackgroundEnabledKey = "dynamic_artwork_background_enabled"
    private const val showCatalogAccentEnabledKey = "show_catalog_accent_enabled"
    private const val tabBarBehaviorKey = "tab_bar_behavior"
    private const val selectedAppLanguageKey = "selected_app_language"
    private const val NAV_BAR_STYLE_KEY = "nav_bar_style"
    private const val NAV_BAR_POSITION_KEY = "nav_bar_position"

    private const val localNavBarStyleKey = "enhanced_local_nav_bar_style"
    private const val localNavBarPositionKey = "enhanced_local_nav_bar_position"
    private const val localNavBarGlassKey = "enhanced_local_nav_bar_glass"
    private const val localLiquidGlassEffectsKey = "enhanced_local_liquid_glass_effects"
    private const val localLiquidGlassSurfaceSettingsKey = "enhanced_local_liquid_glass_surface_settings"
    private const val legacyCustomSettingsBackgroundUrlKey = "enhanced_local_settings_background_url"
    private const val localCustomSettingsBackgroundImageKey = "enhanced_local_settings_background_image"
    private const val localCustomLibraryCloudBackgroundImageKey = "enhanced_local_library_cloud_background_image"
    private const val navBarGlowEnabledKey = "nav_bar_glow_enabled"
    private const val navBarDefaultIconColorEnabledKey = "enhanced_local_nav_bar_default_icon_color_enabled"
    private const val navBarDefaultIconColorKey = "enhanced_local_nav_bar_default_icon_color"
    private const val navBarLiquidGlassBarEffectsKey = "enhanced_local_nav_bar_liquid_glass_bar_effects"
    private const val navBarLiquidGlassSelectionEffectsKey = "enhanced_local_nav_bar_liquid_glass_selection_effects"
    private val profileScopedSyncKeys = listOf(
        selectedThemeKey,
        customThemeColorsKey,
        amoledEnabledKey,
        navBarGlowEnabledKey,
        liquidGlassNativeTabBarEnabledKey,
        NAV_BAR_STYLE_KEY,
        NAV_BAR_POSITION_KEY,
    )

    private var preferences: SharedPreferences? = null
    private var appContext: Context? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
        preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
        applySelectedAppLanguage(loadSelectedAppLanguage() ?: AppLanguage.DEVICE.code)
    }

    actual fun loadSelectedTheme(): String? =
        preferences?.getString(ProfileScopedKey.of(selectedThemeKey), null)

    actual fun saveSelectedTheme(themeName: String) {
        preferences
            ?.edit()
            ?.putString(ProfileScopedKey.of(selectedThemeKey), themeName)
            ?.apply()
    }

    actual fun loadCustomThemeColors(): String? =
        preferences?.getString(ProfileScopedKey.of(customThemeColorsKey), null)

    actual fun saveCustomThemeColors(colors: String) {
        preferences
            ?.edit()
            ?.putString(ProfileScopedKey.of(customThemeColorsKey), colors)
            ?.apply()
    }

    actual fun loadAmoledEnabled(): Boolean? =
        preferences?.let { prefs ->
            val key = ProfileScopedKey.of(amoledEnabledKey)
            if (prefs.contains(key)) prefs.getBoolean(key, false) else null
        }

    actual fun saveAmoledEnabled(enabled: Boolean) {
        preferences
            ?.edit()
            ?.putBoolean(ProfileScopedKey.of(amoledEnabledKey), enabled)
            ?.apply()
    }

    actual fun loadNavBarGlowEnabled(): Boolean? =
        preferences?.let { prefs ->
            val key = ProfileScopedKey.of(navBarGlowEnabledKey)
            if (prefs.contains(key)) prefs.getBoolean(key, false) else null
        }

    actual fun saveNavBarGlowEnabled(enabled: Boolean) {
        preferences
            ?.edit()
            ?.putBoolean(ProfileScopedKey.of(navBarGlowEnabledKey), enabled)
            ?.apply()
    }

    actual fun loadNavBarDefaultIconColorEnabled(): Boolean? =
        preferences?.let { prefs ->
            val key = ProfileScopedKey.of(navBarDefaultIconColorEnabledKey)
            if (prefs.contains(key)) prefs.getBoolean(key, false) else null
        }

    actual fun saveNavBarDefaultIconColorEnabled(enabled: Boolean) {
        preferences
            ?.edit()
            ?.putBoolean(ProfileScopedKey.of(navBarDefaultIconColorEnabledKey), enabled)
            ?.apply()
    }

    actual fun loadNavBarDefaultIconColor(): String? = loadRawString(navBarDefaultIconColorKey)

    actual fun saveNavBarDefaultIconColor(color: String) {
        saveRawString(navBarDefaultIconColorKey, color)
    }


    actual fun loadNavBarLiquidGlassBarEffects(): String? = loadRawString(navBarLiquidGlassBarEffectsKey)

    actual fun saveNavBarLiquidGlassBarEffects(settings: String) {
        saveRawString(navBarLiquidGlassBarEffectsKey, settings)
    }

    actual fun loadNavBarLiquidGlassSelectionEffects(): String? =
        loadRawString(navBarLiquidGlassSelectionEffectsKey)

    actual fun saveNavBarLiquidGlassSelectionEffects(settings: String) {
        saveRawString(navBarLiquidGlassSelectionEffectsKey, settings)
    }

    actual fun loadLiquidGlassNativeTabBarEnabled(): Boolean? =
        preferences?.let { prefs ->
            val key = ProfileScopedKey.of(liquidGlassNativeTabBarEnabledKey)
            if (prefs.contains(key)) prefs.getBoolean(key, false) else null
        }

    actual fun saveLiquidGlassNativeTabBarEnabled(enabled: Boolean) {
        preferences
            ?.edit()
            ?.putBoolean(ProfileScopedKey.of(liquidGlassNativeTabBarEnabledKey), enabled)
            ?.apply()
    }

    actual fun loadTabBarBehavior(): String? =
        preferences?.getString(ProfileScopedKey.of(tabBarBehaviorKey), null)

    actual fun saveTabBarBehavior(behaviorKey: String) {
        preferences
            ?.edit()
            ?.putString(ProfileScopedKey.of(tabBarBehaviorKey), behaviorKey)
            ?.apply()
    }

    actual fun isTabBarBehaviorDeviceLocal(): Boolean = false

    actual fun loadDynamicArtworkBackgroundEnabled(): Boolean? =
        preferences?.let { prefs ->
            val key = ProfileScopedKey.of(dynamicArtworkBackgroundEnabledKey)
            if (prefs.contains(key)) prefs.getBoolean(key, false) else null
        }

    actual fun saveDynamicArtworkBackgroundEnabled(enabled: Boolean) {
        preferences
            ?.edit()
            ?.putBoolean(ProfileScopedKey.of(dynamicArtworkBackgroundEnabledKey), enabled)
            ?.apply()
    }

    actual fun loadShowCatalogAccentEnabled(): Boolean? =
        preferences?.let { prefs ->
            val key = ProfileScopedKey.of(showCatalogAccentEnabledKey)
            if (prefs.contains(key)) prefs.getBoolean(key, false) else null
        }

    actual fun saveShowCatalogAccentEnabled(enabled: Boolean) {
        preferences
            ?.edit()
            ?.putBoolean(ProfileScopedKey.of(showCatalogAccentEnabledKey), enabled)
            ?.apply()
    }

    actual fun loadSelectedAppLanguage(): String? {
        val value = preferences?.getString(selectedAppLanguageKey, null)
        if (value != null) return value
        val legacy = preferences?.getString(ProfileScopedKey.of(selectedAppLanguageKey), null)
        if (legacy != null) saveSelectedAppLanguage(legacy)
        return legacy
    }

    actual fun saveSelectedAppLanguage(languageCode: String) {
        preferences
            ?.edit()
            ?.putString(selectedAppLanguageKey, languageCode)
            ?.apply()
    }

    actual fun applySelectedAppLanguage(languageCode: String) {
        if (languageCode.equals("device", ignoreCase = true)) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
        } else {
            AppCompatDelegate.setApplicationLocales(
                LocaleListCompat.forLanguageTags(languageCode),
            )
        }
    }

    actual fun loadNavBarStyle(): String? =
        loadRawString(localNavBarStyleKey) ?: loadRawString(NAV_BAR_STYLE_KEY)

    actual fun saveNavBarStyle(styleKey: String) {
        saveRawString(localNavBarStyleKey, styleKey)
    }

    actual fun loadNavBarPosition(): String? =
        loadRawString(localNavBarPositionKey) ?: loadRawString(NAV_BAR_POSITION_KEY)

    actual fun saveNavBarPosition(positionKey: String) {
        saveRawString(localNavBarPositionKey, positionKey)
    }

    actual fun loadNavBarGlass(): String? =
        loadRawString(localNavBarGlassKey)

    actual fun saveNavBarGlass(value: String) {
        saveRawString(localNavBarGlassKey, value)
    }

    actual fun loadLiquidGlassEffectsEnabled(): Boolean? =
        preferences?.let { prefs ->
            val key = ProfileScopedKey.of(localLiquidGlassEffectsKey)
            if (prefs.contains(key)) prefs.getBoolean(key, true) else null
        }

    actual fun saveLiquidGlassEffectsEnabled(enabled: Boolean) {
        preferences
            ?.edit()
            ?.putBoolean(ProfileScopedKey.of(localLiquidGlassEffectsKey), enabled)
            ?.apply()
    }

    actual fun loadLiquidGlassSurfaceSettings(): String? =
        loadRawString(localLiquidGlassSurfaceSettingsKey)

    actual fun saveLiquidGlassSurfaceSettings(settings: String) {
        saveRawString(localLiquidGlassSurfaceSettingsKey, settings)
    }

    actual fun loadCustomSettingsBackgroundImageUri(): String? {
        clearLegacyCustomSettingsBackgroundUrl()
        return settingsBackgroundImageFile()
            ?.takeIf { it.isFile && it.length() in 1..CUSTOM_SETTINGS_BACKGROUND_MAX_IMAGE_BYTES.toLong() }
            ?.toURI()
            ?.toString()
    }

    actual fun saveCustomSettingsBackgroundImage(bytes: ByteArray): String? =
        writeCustomSettingsBackgroundImage(bytes, profileId = null)

    actual fun saveCustomSettingsBackgroundImage(bytes: ByteArray, profileId: Int): String? =
        writeCustomSettingsBackgroundImage(bytes, profileId = profileId)

    private fun writeCustomSettingsBackgroundImage(bytes: ByteArray, profileId: Int?): String? {
        if (bytes.isEmpty() || bytes.size > CUSTOM_SETTINGS_BACKGROUND_MAX_IMAGE_BYTES) return null
        if (profileId == null) clearLegacyCustomSettingsBackgroundUrl()
        else clearLegacyCustomSettingsBackgroundUrl(profileId)
        val target = settingsBackgroundImageFile(profileId) ?: return null
        val directory = target.parentFile ?: return null
        if (!directory.isDirectory && !directory.mkdirs()) return null
        val temporary = directory.resolve(".${target.name}.tmp")
        return try {
            temporary.writeBytes(bytes)
            if (!temporary.renameTo(target)) {
                // Some filesystems don't replace an existing destination on rename. Keep the
                // same profile-scoped path usable when the user selects a replacement image.
                temporary.copyTo(target, overwrite = true)
            }
            target.takeIf {
                it.isFile && it.length() in 1..CUSTOM_SETTINGS_BACKGROUND_MAX_IMAGE_BYTES.toLong()
            }?.toURI()?.toString()
        } catch (_: Exception) {
            null
        } finally {
            temporary.delete()
        }
    }

    actual fun clearCustomSettingsBackgroundImage(): Boolean {
        val deleted = deleteSettingsBackgroundImage(settingsBackgroundImageFile())
        clearLegacyCustomSettingsBackgroundUrl()
        return deleted
    }

    actual fun clearCustomSettingsBackgroundImage(profileIndex: Int): Boolean {
        val deleted = deleteSettingsBackgroundImage(settingsBackgroundImageFile(profileIndex))
        clearLegacyCustomSettingsBackgroundUrl(profileIndex)
        return deleted
    }

    actual fun loadCustomLibraryCloudBackgroundImageUri(): String? =
        libraryCloudBackgroundImageFile()
            ?.takeIf { it.isFile && it.length() in 1..CUSTOM_SETTINGS_BACKGROUND_MAX_IMAGE_BYTES.toLong() }
            ?.toURI()
            ?.toString()

    actual fun saveCustomLibraryCloudBackgroundImage(bytes: ByteArray): String? =
        writeCustomLibraryCloudBackgroundImage(bytes, profileId = null)

    actual fun saveCustomLibraryCloudBackgroundImage(bytes: ByteArray, profileId: Int): String? =
        writeCustomLibraryCloudBackgroundImage(bytes, profileId = profileId)

    private fun writeCustomLibraryCloudBackgroundImage(bytes: ByteArray, profileId: Int?): String? {
        if (bytes.isEmpty() || bytes.size > CUSTOM_SETTINGS_BACKGROUND_MAX_IMAGE_BYTES) return null
        val target = libraryCloudBackgroundImageFile(profileId) ?: return null
        val directory = target.parentFile ?: return null
        if (!directory.isDirectory && !directory.mkdirs()) return null
        val temporary = directory.resolve(".${target.name}.tmp")
        return try {
            temporary.writeBytes(bytes)
            if (!temporary.renameTo(target)) temporary.copyTo(target, overwrite = true)
            target.takeIf {
                it.isFile && it.length() in 1..CUSTOM_SETTINGS_BACKGROUND_MAX_IMAGE_BYTES.toLong()
            }?.toURI()?.toString()
        } catch (_: Exception) {
            null
        } finally {
            temporary.delete()
        }
    }

    actual fun clearCustomLibraryCloudBackgroundImage(): Boolean =
        deleteLibraryCloudBackgroundImage(libraryCloudBackgroundImageFile())

    actual fun clearCustomLibraryCloudBackgroundImage(profileIndex: Int): Boolean =
        deleteLibraryCloudBackgroundImage(libraryCloudBackgroundImageFile(profileIndex))

    private fun deleteLibraryCloudBackgroundImage(target: File?): Boolean {
        if (target == null) return false
        val temporary = target.parentFile?.resolve(".${target.name}.tmp")
        val imageRemoved = !target.exists() || target.delete()
        val temporaryRemoved = temporary?.let { !it.exists() || it.delete() } ?: true
        return imageRemoved && temporaryRemoved
    }

    private fun libraryCloudBackgroundImageFile(profileIndex: Int? = null): File? {
        val key = profileIndex?.let { ProfileScopedKey.of(localCustomLibraryCloudBackgroundImageKey, it) }
            ?: ProfileScopedKey.of(localCustomLibraryCloudBackgroundImageKey)
        return appContext?.noBackupFilesDir?.resolve("library_cloud_backgrounds")?.resolve("$key.jpg")
    }

    private fun deleteSettingsBackgroundImage(target: File?): Boolean {
        if (target == null) return false
        val temporary = target.parentFile?.resolve(".${target.name}.tmp")
        val imageRemoved = !target.exists() || target.delete()
        val temporaryRemoved = temporary?.let { !it.exists() || it.delete() } ?: true
        return imageRemoved && temporaryRemoved
    }

    private fun settingsBackgroundImageFile(profileIndex: Int? = null): File? {
        val key = profileIndex?.let { ProfileScopedKey.of(localCustomSettingsBackgroundImageKey, it) }
            ?: ProfileScopedKey.of(localCustomSettingsBackgroundImageKey)
        return appContext?.noBackupFilesDir?.resolve("settings_backgrounds")?.resolve("$key.jpg")
    }

    private fun clearLegacyCustomSettingsBackgroundUrl(profileIndex: Int? = null) {
        val key = profileIndex?.let { ProfileScopedKey.of(legacyCustomSettingsBackgroundUrlKey, it) }
            ?: ProfileScopedKey.of(legacyCustomSettingsBackgroundUrlKey)
        preferences?.edit()?.remove(key)?.apply()
    }

    private fun loadRawString(key: String): String? =
        preferences?.getString(ProfileScopedKey.of(key), null)

    private fun saveRawString(key: String, value: String) {
        preferences
            ?.edit()
            ?.putString(ProfileScopedKey.of(key), value)
            ?.apply()
    }

    actual fun exportToSyncPayload(): JsonObject = buildJsonObject {
        loadSelectedTheme()?.let { put(selectedThemeKey, encodeSyncString(it)) }
        loadCustomThemeColors()?.let { put(customThemeColorsKey, encodeSyncString(it)) }
        loadNavBarGlowEnabled()?.let { put(navBarGlowEnabledKey, encodeSyncBoolean(it)) }
        loadAmoledEnabled()?.let { put(amoledEnabledKey, encodeSyncBoolean(it)) }
        loadLiquidGlassNativeTabBarEnabled()?.let { put(liquidGlassNativeTabBarEnabledKey, encodeSyncBoolean(it)) }
        loadRawString(NAV_BAR_STYLE_KEY)?.let { put(NAV_BAR_STYLE_KEY, encodeSyncString(it)) }
        loadRawString(NAV_BAR_POSITION_KEY)?.let { put(NAV_BAR_POSITION_KEY, encodeSyncString(it)) }
    }

    actual fun replaceFromSyncPayload(payload: JsonObject) {
        preferences?.edit()?.apply {
            profileScopedSyncKeys.forEach { remove(ProfileScopedKey.of(it)) }
        }?.apply()

        payload.decodeSyncString(selectedThemeKey)?.let(::saveSelectedTheme)
        payload.decodeSyncString(customThemeColorsKey)?.let(::saveCustomThemeColors)
        payload.decodeSyncBoolean(navBarGlowEnabledKey)?.let(::saveNavBarGlowEnabled)
        payload.decodeSyncBoolean(amoledEnabledKey)?.let(::saveAmoledEnabled)
        payload.decodeSyncBoolean(liquidGlassNativeTabBarEnabledKey)?.let(::saveLiquidGlassNativeTabBarEnabled)
        payload.decodeSyncString(NAV_BAR_STYLE_KEY)?.let { saveRawString(NAV_BAR_STYLE_KEY, it) }
        payload.decodeSyncString(NAV_BAR_POSITION_KEY)?.let { saveRawString(NAV_BAR_POSITION_KEY, it) }
        applySelectedAppLanguage(loadSelectedAppLanguage() ?: AppLanguage.DEVICE.code)
    }
}
