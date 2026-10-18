package com.nuvio.app.features.settings

import kotlinx.serialization.json.JsonObject

internal expect object ThemeSettingsStorage {
    fun loadSelectedTheme(): String?
    fun saveSelectedTheme(themeName: String)
    fun loadCustomThemeColors(): String?
    fun saveCustomThemeColors(colors: String)
    fun loadAmoledEnabled(): Boolean?
    fun saveAmoledEnabled(enabled: Boolean)
    fun loadLiquidGlassNativeTabBarEnabled(): Boolean?
    fun saveLiquidGlassNativeTabBarEnabled(enabled: Boolean)
    fun loadTabBarBehavior(): String?
    fun saveTabBarBehavior(behaviorKey: String)
    fun isTabBarBehaviorDeviceLocal(): Boolean
    fun loadDynamicArtworkBackgroundEnabled(): Boolean?
    fun saveDynamicArtworkBackgroundEnabled(enabled: Boolean)
    fun loadShowCatalogAccentEnabled(): Boolean?
    fun saveShowCatalogAccentEnabled(enabled: Boolean)
    fun loadSelectedAppLanguage(): String?
    fun saveSelectedAppLanguage(languageCode: String)
    fun applySelectedAppLanguage(languageCode: String)
    fun loadNavBarStyle(): String?
    fun saveNavBarStyle(styleKey: String)
    fun loadNavBarPosition(): String?
    fun saveNavBarPosition(positionKey: String)
    fun loadNavBarGlowEnabled(): Boolean?
    fun saveNavBarGlowEnabled(enabled: Boolean)
    fun loadNavBarGlass(): String?
    fun saveNavBarGlass(value: String)
    fun exportToSyncPayload(): JsonObject
    fun replaceFromSyncPayload(payload: JsonObject)
}
