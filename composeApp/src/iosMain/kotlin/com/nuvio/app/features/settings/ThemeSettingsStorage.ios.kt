package com.nuvio.app.features.settings

import com.nuvio.app.core.sync.decodeSyncBoolean
import com.nuvio.app.core.sync.decodeSyncString
import com.nuvio.app.core.sync.encodeSyncBoolean
import com.nuvio.app.core.sync.encodeSyncString
import com.nuvio.app.core.storage.ProfileScopedKey
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import platform.Foundation.NSUserDefaults
import platform.UIKit.UIDevice
import platform.UIKit.UIUserInterfaceIdiomPad

actual object ThemeSettingsStorage {
    private const val selectedThemeKey = "selected_theme"
    private const val customThemeColorsKey = "custom_theme_colors"
    private const val amoledEnabledKey = "amoled_enabled"
    private const val liquidGlassNativeTabBarEnabledKey = "liquid_glass_native_tab_bar_enabled"
    private const val dynamicArtworkBackgroundEnabledKey = "dynamic_artwork_background_enabled"
    private const val showCatalogAccentEnabledKey = "show_catalog_accent_enabled"
    private const val tabBarBehaviorKey = "tab_bar_behavior"
    private const val selectedAppLanguageKey = "selected_app_language"
    private const val navBarStyleKey = "nav_bar_style"
    private const val navBarPositionKey = "nav_bar_position"

    private const val localNavBarStyleKey = "enhanced_local_nav_bar_style"
    private const val localNavBarPositionKey = "enhanced_local_nav_bar_position"
    private const val localNavBarGlassKey = "enhanced_local_nav_bar_glass"
    private const val localLiquidGlassEffectsKey = "enhanced_local_liquid_glass_effects"
    private const val localTabletTabBarBehaviorKey = "enhanced_local_tablet_tab_bar_behavior"
    private const val tabletTabBarBehaviorDefault = "off"
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
        navBarStyleKey,
        navBarPositionKey,
    )

    actual fun loadSelectedTheme(): String? =
        NSUserDefaults.standardUserDefaults.stringForKey(ProfileScopedKey.of(selectedThemeKey))

    actual fun saveSelectedTheme(themeName: String) {
        NSUserDefaults.standardUserDefaults.setObject(themeName, forKey = ProfileScopedKey.of(selectedThemeKey))
    }

    actual fun loadCustomThemeColors(): String? =
        NSUserDefaults.standardUserDefaults.stringForKey(ProfileScopedKey.of(customThemeColorsKey))

    actual fun saveCustomThemeColors(colors: String) {
        NSUserDefaults.standardUserDefaults.setObject(colors, forKey = ProfileScopedKey.of(customThemeColorsKey))
    }

    actual fun loadAmoledEnabled(): Boolean? {
        val defaults = NSUserDefaults.standardUserDefaults
        val key = ProfileScopedKey.of(amoledEnabledKey)
        return if (defaults.objectForKey(key) != null) {
            defaults.boolForKey(key)
        } else {
            null
        }
    }

    actual fun saveAmoledEnabled(enabled: Boolean) {
        NSUserDefaults.standardUserDefaults.setBool(enabled, forKey = ProfileScopedKey.of(amoledEnabledKey))
    }

    actual fun loadNavBarGlowEnabled(): Boolean? {
        val defaults = NSUserDefaults.standardUserDefaults
        val key = ProfileScopedKey.of(navBarGlowEnabledKey)
        return if (defaults.objectForKey(key) != null) {
            defaults.boolForKey(key)
        } else {
            null
        }
    }

    actual fun loadNavBarDefaultIconColorEnabled(): Boolean? {
        val defaults = NSUserDefaults.standardUserDefaults
        val key = ProfileScopedKey.of(navBarDefaultIconColorEnabledKey)
        return if (defaults.objectForKey(key) != null) {
            defaults.boolForKey(key)
        } else {
            null
        }
    }

    actual fun saveNavBarDefaultIconColorEnabled(enabled: Boolean) {
        NSUserDefaults.standardUserDefaults.setBool(
            enabled,
            forKey = ProfileScopedKey.of(navBarDefaultIconColorEnabledKey),
        )
    }

    actual fun loadNavBarDefaultIconColor(): String? = loadRawString(navBarDefaultIconColorKey)

    actual fun saveNavBarDefaultIconColor(color: String) {
        saveRawString(navBarDefaultIconColorKey, color)
    }

    actual fun saveNavBarGlowEnabled(enabled: Boolean) {
        NSUserDefaults.standardUserDefaults.setBool(enabled, forKey = ProfileScopedKey.of(navBarGlowEnabledKey))
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

    actual fun loadLiquidGlassNativeTabBarEnabled(): Boolean? {
        val defaults = NSUserDefaults.standardUserDefaults
        val key = ProfileScopedKey.of(liquidGlassNativeTabBarEnabledKey)
        return if (defaults.objectForKey(key) != null) {
            defaults.boolForKey(key)
        } else {
            null
        }
    }

    actual fun saveLiquidGlassNativeTabBarEnabled(enabled: Boolean) {
        NSUserDefaults.standardUserDefaults.setBool(
            enabled,
            forKey = ProfileScopedKey.of(liquidGlassNativeTabBarEnabledKey),
        )
    }

    actual fun isTabBarBehaviorDeviceLocal(): Boolean =
        UIDevice.currentDevice.userInterfaceIdiom == UIUserInterfaceIdiomPad

    actual fun loadTabBarBehavior(): String? {
        if (isTabBarBehaviorDeviceLocal()) {
            return loadRawString(localTabletTabBarBehaviorKey) ?: tabletTabBarBehaviorDefault
        }
        return NSUserDefaults.standardUserDefaults.stringForKey(ProfileScopedKey.of(tabBarBehaviorKey))
    }

    actual fun saveTabBarBehavior(behaviorKey: String) {
        if (isTabBarBehaviorDeviceLocal()) {
            saveRawString(localTabletTabBarBehaviorKey, behaviorKey)
            return
        }
        NSUserDefaults.standardUserDefaults.setObject(
            behaviorKey,
            forKey = ProfileScopedKey.of(tabBarBehaviorKey),
        )
    }

    actual fun loadDynamicArtworkBackgroundEnabled(): Boolean? {
        val defaults = NSUserDefaults.standardUserDefaults
        val key = ProfileScopedKey.of(dynamicArtworkBackgroundEnabledKey)
        return if (defaults.objectForKey(key) != null) {
            defaults.boolForKey(key)
        } else {
            null
        }
    }

    actual fun saveDynamicArtworkBackgroundEnabled(enabled: Boolean) {
        NSUserDefaults.standardUserDefaults.setBool(
            enabled,
            forKey = ProfileScopedKey.of(dynamicArtworkBackgroundEnabledKey),
        )
    }

    actual fun loadShowCatalogAccentEnabled(): Boolean? {
        val defaults = NSUserDefaults.standardUserDefaults
        val key = ProfileScopedKey.of(showCatalogAccentEnabledKey)
        return if (defaults.objectForKey(key) != null) {
            defaults.boolForKey(key)
        } else {
            null
        }
    }

    actual fun saveShowCatalogAccentEnabled(enabled: Boolean) {
        NSUserDefaults.standardUserDefaults.setBool(
            enabled,
            forKey = ProfileScopedKey.of(showCatalogAccentEnabledKey),
        )
    }

    actual fun loadSelectedAppLanguage(): String? {
        val value = NSUserDefaults.standardUserDefaults.stringForKey(selectedAppLanguageKey)
        if (value != null) return value
        val legacy = NSUserDefaults.standardUserDefaults.stringForKey(ProfileScopedKey.of(selectedAppLanguageKey))
        if (legacy != null) saveSelectedAppLanguage(legacy)
        return legacy
    }

    actual fun saveSelectedAppLanguage(languageCode: String) {
        NSUserDefaults.standardUserDefaults.setObject(languageCode, forKey = selectedAppLanguageKey)
    }

    actual fun applySelectedAppLanguage(languageCode: String) {
        if (languageCode.equals("device", ignoreCase = true)) {
            NSUserDefaults.standardUserDefaults.removeObjectForKey("AppleLanguages")
            NSUserDefaults.standardUserDefaults.synchronize()
            return
        }
        val normalizedCode = languageCode
            .trim()
            .takeIf { it.isNotBlank() }
            ?: AppLanguage.ENGLISH.code
        NSUserDefaults.standardUserDefaults.setObject(
            listOf(normalizedCode),
            forKey = "AppleLanguages",
        )
        NSUserDefaults.standardUserDefaults.synchronize()
    }

    actual fun loadNavBarStyle(): String? =
        loadRawString(localNavBarStyleKey) ?: loadRawString(navBarStyleKey)

    actual fun saveNavBarStyle(styleKey: String) {
        saveRawString(localNavBarStyleKey, styleKey)
    }

    actual fun loadNavBarPosition(): String? =
        loadRawString(localNavBarPositionKey) ?: loadRawString(navBarPositionKey)

    actual fun saveNavBarPosition(positionKey: String) {
        saveRawString(localNavBarPositionKey, positionKey)
    }

    actual fun loadNavBarGlass(): String? =
        loadRawString(localNavBarGlassKey)

    actual fun saveNavBarGlass(value: String) {
        saveRawString(localNavBarGlassKey, value)
    }

    actual fun loadLiquidGlassEffectsEnabled(): Boolean? {
        val defaults = NSUserDefaults.standardUserDefaults
        val key = ProfileScopedKey.of(localLiquidGlassEffectsKey)
        return if (defaults.objectForKey(key) != null) {
            defaults.boolForKey(key)
        } else {
            null
        }
    }

    actual fun saveLiquidGlassEffectsEnabled(enabled: Boolean) {
        NSUserDefaults.standardUserDefaults.setBool(enabled, forKey = ProfileScopedKey.of(localLiquidGlassEffectsKey))
    }

    private fun loadRawString(key: String): String? =
        NSUserDefaults.standardUserDefaults.stringForKey(ProfileScopedKey.of(key))

    private fun saveRawString(key: String, value: String) {
        NSUserDefaults.standardUserDefaults.setObject(value, forKey = ProfileScopedKey.of(key))
    }

    actual fun exportToSyncPayload(): JsonObject = buildJsonObject {
        loadSelectedTheme()?.let { put(selectedThemeKey, encodeSyncString(it)) }
        loadCustomThemeColors()?.let { put(customThemeColorsKey, encodeSyncString(it)) }
        loadNavBarGlowEnabled()?.let { put(navBarGlowEnabledKey, encodeSyncBoolean(it)) }
        loadAmoledEnabled()?.let { put(amoledEnabledKey, encodeSyncBoolean(it)) }
        loadLiquidGlassNativeTabBarEnabled()?.let { put(liquidGlassNativeTabBarEnabledKey, encodeSyncBoolean(it)) }
        loadRawString(navBarStyleKey)?.let { put(navBarStyleKey, encodeSyncString(it)) }
        loadRawString(navBarPositionKey)?.let { put(navBarPositionKey, encodeSyncString(it)) }
    }

    actual fun replaceFromSyncPayload(payload: JsonObject) {
        profileScopedSyncKeys.forEach { key ->
            NSUserDefaults.standardUserDefaults.removeObjectForKey(ProfileScopedKey.of(key))
        }

        payload.decodeSyncString(selectedThemeKey)?.let(::saveSelectedTheme)
        payload.decodeSyncString(customThemeColorsKey)?.let(::saveCustomThemeColors)
        payload.decodeSyncBoolean(navBarGlowEnabledKey)?.let(::saveNavBarGlowEnabled)
        payload.decodeSyncBoolean(amoledEnabledKey)?.let(::saveAmoledEnabled)
        payload.decodeSyncBoolean(liquidGlassNativeTabBarEnabledKey)?.let(::saveLiquidGlassNativeTabBarEnabled)
        payload.decodeSyncString(navBarStyleKey)?.let { saveRawString(navBarStyleKey, it) }
        payload.decodeSyncString(navBarPositionKey)?.let { saveRawString(navBarPositionKey, it) }
        applySelectedAppLanguage(loadSelectedAppLanguage() ?: AppLanguage.DEVICE.code)
    }
}
