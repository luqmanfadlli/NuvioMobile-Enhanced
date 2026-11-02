package com.nuvio.app.features.settings

import kotlinx.serialization.json.JsonObject

internal const val CUSTOM_SETTINGS_BACKGROUND_MAX_IMAGE_BYTES = 16 * 1024 * 1024

/** Only local file-scheme sources are allowed for user-selected background images. */
internal fun isLocalBackgroundImageUri(uri: String?): Boolean =
    uri?.startsWith("file:", ignoreCase = true) == true

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
    fun loadNavBarDefaultIconColorEnabled(): Boolean?
    fun saveNavBarDefaultIconColorEnabled(enabled: Boolean)
    fun loadNavBarDefaultIconColor(): String?
    fun saveNavBarDefaultIconColor(color: String)
    fun loadNavBarLiquidGlassBarEffects(): String?
    fun saveNavBarLiquidGlassBarEffects(settings: String)
    fun loadNavBarLiquidGlassSelectionEffects(): String?
    fun saveNavBarLiquidGlassSelectionEffects(settings: String)
    fun loadNavBarGlass(): String?
    fun saveNavBarGlass(value: String)
    fun loadLiquidGlassEffectsEnabled(): Boolean?
    fun saveLiquidGlassEffectsEnabled(enabled: Boolean)
    fun loadLiquidGlassSurfaceSettings(): String?
    fun saveLiquidGlassSurfaceSettings(settings: String)
    fun loadCustomSettingsBackgroundImageUri(): String?
    fun saveCustomSettingsBackgroundImage(bytes: ByteArray): String?
    fun saveCustomSettingsBackgroundImage(bytes: ByteArray, profileId: Int): String?
    fun clearCustomSettingsBackgroundImage(): Boolean
    fun clearCustomSettingsBackgroundImage(profileIndex: Int): Boolean
    fun loadCustomLibraryCloudBackgroundImageUri(): String?
    fun saveCustomLibraryCloudBackgroundImage(bytes: ByteArray): String?
    fun saveCustomLibraryCloudBackgroundImage(bytes: ByteArray, profileId: Int): String?
    fun clearCustomLibraryCloudBackgroundImage(): Boolean
    fun clearCustomLibraryCloudBackgroundImage(profileIndex: Int): Boolean
    fun exportToSyncPayload(): JsonObject
    fun replaceFromSyncPayload(payload: JsonObject)
}
