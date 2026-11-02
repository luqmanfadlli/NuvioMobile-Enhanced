package com.nuvio.app.features.settings

import com.nuvio.app.core.ui.AppTheme
import com.nuvio.app.core.ui.CustomThemeColors
import com.nuvio.app.core.ui.NativeTabBridge
import com.nuvio.app.core.ui.ThemeColors
import com.nuvio.app.core.ui.glass.LiquidGlassSettings
import com.nuvio.app.core.ui.glass.LiquidGlassStyle
import com.nuvio.app.core.ui.glass.LiquidGlassEffectDefaults
import com.nuvio.app.core.ui.glass.LiquidGlassEffectSettings
import com.nuvio.app.core.ui.glass.LiquidGlassSurface
import com.nuvio.app.core.ui.glass.LiquidGlassSurfaceOverrides
import com.nuvio.app.core.ui.glass.LiquidGlassSurfacesSettings
import com.nuvio.app.core.ui.glass.LiquidGlassGeneralStyle
import com.nuvio.app.core.ui.glass.decodeLiquidGlassSurfaceSettings
import com.nuvio.app.core.ui.glass.encodeLiquidGlassSurfaceSettings
import com.nuvio.app.core.ui.glass.decodeLiquidGlassEffectSettings
import com.nuvio.app.core.ui.glass.encodeLiquidGlassEffectSettings
import com.nuvio.app.core.ui.glass.migrateLegacyNavBarGlassEffects
import com.nuvio.app.core.ui.formatHexColor
import com.nuvio.app.core.ui.parseHexColor
import com.nuvio.app.core.ui.nativeAccentGradientHex
import com.nuvio.app.features.membership.MemberAccessRepository
import com.nuvio.app.features.profiles.ProfileRepository
import com.nuvio.app.features.membership.resolveCustomThemeColors
import com.nuvio.app.features.membership.resolveAppTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal const val DEFAULT_NAV_BAR_ICON_COLOR = 0x969CA3

object ThemeSettingsRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _selectedThemePreference = MutableStateFlow<AppTheme?>(null)
    val selectedThemePreference: StateFlow<AppTheme?> = _selectedThemePreference.asStateFlow()
    private val _selectedTheme = MutableStateFlow(AppTheme.WHITE)
    val selectedTheme: StateFlow<AppTheme> = _selectedTheme.asStateFlow()

    private val _customThemePreference = MutableStateFlow(CustomThemeColors.Default)
    val customThemePreference: StateFlow<CustomThemeColors> = _customThemePreference.asStateFlow()
    private val _customThemeColors = MutableStateFlow(CustomThemeColors.solid(CustomThemeColors.Default.second))
    val customThemeColors: StateFlow<CustomThemeColors> = _customThemeColors.asStateFlow()

    private val _amoledEnabled = MutableStateFlow(false)
    val amoledEnabled: StateFlow<Boolean> = _amoledEnabled.asStateFlow()

    private val _tabBarBehavior = MutableStateFlow(NuvioTabBarBehavior.Default)
    val tabBarBehavior: StateFlow<NuvioTabBarBehavior> = _tabBarBehavior.asStateFlow()

    private val _liquidGlassNativeTabBarEnabled = MutableStateFlow(NuvioTabBarBehavior.Default.isEnabled)
    val liquidGlassNativeTabBarEnabled: StateFlow<Boolean> = _liquidGlassNativeTabBarEnabled.asStateFlow()

    private val _dynamicArtworkBackgroundEnabled = MutableStateFlow(false)
    val dynamicArtworkBackgroundEnabled: StateFlow<Boolean> = _dynamicArtworkBackgroundEnabled.asStateFlow()

    private val _showCatalogAccentEnabled = MutableStateFlow(false)
    val showCatalogAccentEnabled: StateFlow<Boolean> = _showCatalogAccentEnabled.asStateFlow()

    private val _customSettingsBackgroundImageUri = MutableStateFlow<String?>(null)
    val customSettingsBackgroundImageUri: StateFlow<String?> = _customSettingsBackgroundImageUri.asStateFlow()
    private val _customSettingsBackgroundImageRevision = MutableStateFlow(0L)
    val customSettingsBackgroundImageRevision: StateFlow<Long> =
        _customSettingsBackgroundImageRevision.asStateFlow()

    private val _customLibraryCloudBackgroundImageUri = MutableStateFlow<String?>(null)
    val customLibraryCloudBackgroundImageUri: StateFlow<String?> =
        _customLibraryCloudBackgroundImageUri.asStateFlow()
    private val _customLibraryCloudBackgroundImageRevision = MutableStateFlow(0L)
    val customLibraryCloudBackgroundImageRevision: StateFlow<Long> =
        _customLibraryCloudBackgroundImageRevision.asStateFlow()

    private val _selectedAppLanguage = MutableStateFlow(AppLanguage.DEVICE)
    val selectedAppLanguage: StateFlow<AppLanguage> = _selectedAppLanguage.asStateFlow()

    private val _navBarStyle = MutableStateFlow(NavBarStyle.ADAPTIVE)
    val navBarStyle: StateFlow<NavBarStyle> = _navBarStyle.asStateFlow()

    private val _navBarPosition = MutableStateFlow(NavBarPosition.BOTTOM)
    val navBarPosition: StateFlow<NavBarPosition> = _navBarPosition.asStateFlow()

    private val _navBarGlowEnabled = MutableStateFlow(true)
    val navBarGlowEnabled: StateFlow<Boolean> = _navBarGlowEnabled.asStateFlow()

    private val _navBarDefaultIconColorEnabled = MutableStateFlow(false)
    val navBarDefaultIconColorEnabled: StateFlow<Boolean> = _navBarDefaultIconColorEnabled.asStateFlow()

    private val _navBarDefaultIconColor = MutableStateFlow(DEFAULT_NAV_BAR_ICON_COLOR)
    val navBarDefaultIconColor: StateFlow<Int> = _navBarDefaultIconColor.asStateFlow()

    private val _navBarDefaultIconColorRevision = MutableStateFlow(0L)
    internal val navBarDefaultIconColorRevision: StateFlow<Long> = _navBarDefaultIconColorRevision.asStateFlow()
    private var navBarDefaultIconColorDraftDirty = false

    private val _navBarLiquidGlassBarEffects = MutableStateFlow(LiquidGlassEffectDefaults.floatingBar)
    internal val navBarLiquidGlassBarEffects: StateFlow<LiquidGlassEffectSettings> = _navBarLiquidGlassBarEffects.asStateFlow()

    private val _navBarLiquidGlassSelectionEffects = MutableStateFlow(LiquidGlassEffectDefaults.selectionLens)
    internal val navBarLiquidGlassSelectionEffects: StateFlow<LiquidGlassEffectSettings> = _navBarLiquidGlassSelectionEffects.asStateFlow()

    private val _navBarLiquidGlassEffectsRevision = MutableStateFlow(0L)
    internal val navBarLiquidGlassEffectsRevision: StateFlow<Long> = _navBarLiquidGlassEffectsRevision.asStateFlow()

    private val _navBarGlass = MutableStateFlow(LiquidGlassStyle.Default)
    val navBarGlass: StateFlow<LiquidGlassStyle> = _navBarGlass.asStateFlow()

    private val _liquidGlassSurfaceSettings = MutableStateFlow(LiquidGlassSurfacesSettings())
    internal val liquidGlassSurfaceSettings: StateFlow<LiquidGlassSurfacesSettings> =
        _liquidGlassSurfaceSettings.asStateFlow()

    private val _liquidGlassEffectsEnabled = MutableStateFlow(true)
    val liquidGlassEffectsEnabled: StateFlow<Boolean> = _liquidGlassEffectsEnabled.asStateFlow()

    private var hasLoaded = false
    private var observesMembership = false

    fun ensureLoaded() {
        observeMembership()
        if (hasLoaded) return
        loadFromDisk()
    }

    fun onProfileChanged() {
        loadFromDisk()
    }

    fun clearLocalState() {
        hasLoaded = false
        _selectedThemePreference.value = null
        _selectedTheme.value = AppTheme.WHITE
        _customThemePreference.value = CustomThemeColors.Default
        _customThemeColors.value = CustomThemeColors.solid(CustomThemeColors.Default.second)
        _amoledEnabled.value = false
        _tabBarBehavior.value = NuvioTabBarBehavior.Default
        _liquidGlassNativeTabBarEnabled.value = NuvioTabBarBehavior.Default.isEnabled
        _dynamicArtworkBackgroundEnabled.value = false
        _showCatalogAccentEnabled.value = false
        _customSettingsBackgroundImageUri.value = null
        _customSettingsBackgroundImageRevision.value += 1L
        _customLibraryCloudBackgroundImageUri.value = null
        _customLibraryCloudBackgroundImageRevision.value += 1L
        NativeTabBridge.publishAccentColor(
            hexColor = AppTheme.WHITE.nativeTabAccentHex(),
            gradientHexColors = ThemeColors.getColorPalette(AppTheme.WHITE).nativeAccentGradientHex(),
        )
        NativeTabBridge.publishTabBarBehavior(NuvioTabBarBehavior.Default)
        NativeTabBridge.publishLiquidGlassEnabled(NuvioTabBarBehavior.Default.isEnabled)
        _selectedAppLanguage.value = AppLanguage.DEVICE
        _navBarGlowEnabled.value = true
        _navBarDefaultIconColorEnabled.value = false
        _navBarDefaultIconColor.value = DEFAULT_NAV_BAR_ICON_COLOR
        navBarDefaultIconColorDraftDirty = false
        _navBarLiquidGlassBarEffects.value = LiquidGlassEffectDefaults.floatingBar
        _navBarLiquidGlassSelectionEffects.value = LiquidGlassEffectDefaults.selectionLens
        _navBarStyle.value = NavBarStyle.ADAPTIVE
        _navBarPosition.value = NavBarPosition.BOTTOM
        _navBarGlass.value = LiquidGlassStyle.Default
        _liquidGlassSurfaceSettings.value = LiquidGlassSurfacesSettings()
        LiquidGlassSettings.surfaceOverrides = LiquidGlassSurfacesSettings()
        applyLiquidGlassEffectsEnabled(true)
    }

    private fun loadFromDisk() {
        hasLoaded = true
        val stored = ThemeSettingsStorage.loadSelectedTheme()
        val theme = if (stored != null) {
            try {
                AppTheme.valueOf(stored)
            } catch (_: IllegalArgumentException) {
                null
            }
        } else {
            null
        }
        _selectedThemePreference.value = theme
        _customThemePreference.value = CustomThemeColors.decode(ThemeSettingsStorage.loadCustomThemeColors())
        applyEffectiveTheme()
        _amoledEnabled.value = ThemeSettingsStorage.loadAmoledEnabled() ?: false
        val behavior = NuvioTabBarBehavior.fromKey(ThemeSettingsStorage.loadTabBarBehavior())
            ?: NuvioTabBarBehavior.fromLegacyEnabled(
                ThemeSettingsStorage.loadLiquidGlassNativeTabBarEnabled(),
            )
        applyTabBarBehavior(behavior)
        _dynamicArtworkBackgroundEnabled.value =
            ThemeSettingsStorage.loadDynamicArtworkBackgroundEnabled() ?: false
        _showCatalogAccentEnabled.value =
            ThemeSettingsStorage.loadShowCatalogAccentEnabled() ?: false
        _customSettingsBackgroundImageUri.value = ThemeSettingsStorage.loadCustomSettingsBackgroundImageUri()
        _customSettingsBackgroundImageRevision.value += 1L
        _customLibraryCloudBackgroundImageUri.value = ThemeSettingsStorage.loadCustomLibraryCloudBackgroundImageUri()
        _customLibraryCloudBackgroundImageRevision.value += 1L
        val appLanguage = AppLanguage.fromCode(ThemeSettingsStorage.loadSelectedAppLanguage())
        ThemeSettingsStorage.applySelectedAppLanguage(appLanguage.code)
        _selectedAppLanguage.value = appLanguage
        _navBarGlowEnabled.value = ThemeSettingsStorage.loadNavBarGlowEnabled() ?: true
        _navBarStyle.value = NavBarStyle.fromKey(ThemeSettingsStorage.loadNavBarStyle())
        _navBarPosition.value = NavBarPosition.fromKey(ThemeSettingsStorage.loadNavBarPosition())
        _navBarDefaultIconColorEnabled.value = ThemeSettingsStorage.loadNavBarDefaultIconColorEnabled() ?: false
        _navBarDefaultIconColor.value = ThemeSettingsStorage.loadNavBarDefaultIconColor()
            ?.let(::parseHexColor) ?: DEFAULT_NAV_BAR_ICON_COLOR
        navBarDefaultIconColorDraftDirty = false
        _navBarLiquidGlassBarEffects.value = decodeLiquidGlassEffectSettings(
            ThemeSettingsStorage.loadNavBarLiquidGlassBarEffects(),
            LiquidGlassEffectDefaults.floatingBar,
            LiquidGlassEffectDefaults.floatingBarLimits,
        )
        _navBarLiquidGlassSelectionEffects.value = decodeLiquidGlassEffectSettings(
            ThemeSettingsStorage.loadNavBarLiquidGlassSelectionEffects(),
            LiquidGlassEffectDefaults.selectionLens,
            LiquidGlassEffectDefaults.selectionLensLimits,
        )
        val storedNavBarGlass = ThemeSettingsStorage.loadNavBarGlass()
        _navBarGlass.value = LiquidGlassStyle.decode(storedNavBarGlass)
        migrateLegacyNavBarGlass(storedNavBarGlass)

        val surfaceSettings = decodeLiquidGlassSurfaceSettings(ThemeSettingsStorage.loadLiquidGlassSurfaceSettings())
        _liquidGlassSurfaceSettings.value = surfaceSettings
        LiquidGlassSettings.surfaceOverrides = surfaceSettings

        applyLiquidGlassEffectsEnabled(ThemeSettingsStorage.loadLiquidGlassEffectsEnabled() ?: true)
    }

    fun setCustomTheme(colors: CustomThemeColors) {
        ensureLoaded()
        val selectedColors = resolveCustomThemeColors(
            colors,
            MemberAccessRepository.access.value.tier,
        )
        ThemeSettingsStorage.saveCustomThemeColors(selectedColors.encode())
        ThemeSettingsStorage.saveSelectedTheme(AppTheme.CUSTOM.name)
        _customThemePreference.value = selectedColors
        _selectedThemePreference.value = AppTheme.CUSTOM
        applyEffectiveTheme()
    }

    fun setTheme(theme: AppTheme) {
        ensureLoaded()
        if (_selectedThemePreference.value == theme) return
        _selectedThemePreference.value = theme
        ThemeSettingsStorage.saveSelectedTheme(theme.name)
        applyEffectiveTheme()
    }

    fun setAmoled(enabled: Boolean) {
        ensureLoaded()
        if (_amoledEnabled.value == enabled) return
        _amoledEnabled.value = enabled
        ThemeSettingsStorage.saveAmoledEnabled(enabled)
    }

    fun setTabBarBehavior(behavior: NuvioTabBarBehavior) {
        ensureLoaded()
        if (_tabBarBehavior.value == behavior) return
        applyTabBarBehavior(behavior)
        ThemeSettingsStorage.saveTabBarBehavior(behavior.key)
        if (!ThemeSettingsStorage.isTabBarBehaviorDeviceLocal()) {
            ThemeSettingsStorage.saveLiquidGlassNativeTabBarEnabled(behavior.isEnabled)
        }
    }

    private fun applyTabBarBehavior(behavior: NuvioTabBarBehavior) {
        _tabBarBehavior.value = behavior
        _liquidGlassNativeTabBarEnabled.value = behavior.isEnabled
        NativeTabBridge.publishTabBarBehavior(behavior)
        NativeTabBridge.publishLiquidGlassEnabled(behavior.isEnabled)
    }

    fun setDynamicArtworkBackground(enabled: Boolean) {
        ensureLoaded()
        if (_dynamicArtworkBackgroundEnabled.value == enabled) return
        _dynamicArtworkBackgroundEnabled.value = enabled
        ThemeSettingsStorage.saveDynamicArtworkBackgroundEnabled(enabled)
    }

    fun setShowCatalogAccent(enabled: Boolean) {
        ensureLoaded()
        if (_showCatalogAccentEnabled.value == enabled) return
        _showCatalogAccentEnabled.value = enabled
        ThemeSettingsStorage.saveShowCatalogAccentEnabled(enabled)
    }

    /**
     * Performs only local persistence. Call this from a background dispatcher; publish the
     * returned local URI on the UI dispatcher after the I/O completes.
     */
    internal fun storeCustomSettingsBackgroundImageForProfile(bytes: ByteArray, profileId: Int): String? {
        if (bytes.isEmpty() || bytes.size > CUSTOM_SETTINGS_BACKGROUND_MAX_IMAGE_BYTES) return null
        return ThemeSettingsStorage.saveCustomSettingsBackgroundImage(bytes, profileId)
            ?.takeIf(::isLocalBackgroundImageUri)
    }

    internal fun removeCustomSettingsBackgroundImageForProfile(profileId: Int): Boolean =
        ThemeSettingsStorage.clearCustomSettingsBackgroundImage(profileId)

    internal fun publishCustomSettingsBackgroundImageForProfile(profileId: Int, imageUri: String?) {
        if (ProfileRepository.activeProfileId != profileId) return
        if (imageUri != null && !isLocalBackgroundImageUri(imageUri)) return
        _customSettingsBackgroundImageUri.value = imageUri
        _customSettingsBackgroundImageRevision.value += 1L
    }

    /**
     * Persist off the UI thread and publish the completed result on the caller's dispatcher.
     * A synchronous storage operation can finish just as the caller is cancelled while
     * withContext dispatches back; preserve that completed result so the preview cannot go stale.
     */
    private suspend fun persistBackgroundImageForStudio(
        store: () -> String?,
        publish: (String?) -> Unit,
    ): Boolean {
        var savedUri: String? = null
        try {
            withContext(Dispatchers.IO) {
                savedUri = store()
            }
        } catch (cancelled: CancellationException) {
            savedUri?.let { uri ->
                withContext(NonCancellable) {
                    publish(uri)
                }
            }
            throw cancelled
        }
        val uri = savedUri ?: return false
        publish(uri)
        return true
    }

    private suspend fun removeBackgroundImageForStudio(
        remove: () -> Boolean,
        publish: (String?) -> Unit,
    ): Boolean {
        var removed = false
        try {
            withContext(Dispatchers.IO) {
                removed = remove()
            }
        } catch (cancelled: CancellationException) {
            if (removed) {
                withContext(NonCancellable) {
                    publish(null)
                }
            }
            throw cancelled
        }
        if (!removed) return false
        publish(null)
        return true
    }

    internal suspend fun saveCustomSettingsBackgroundImageForStudio(
        bytes: ByteArray,
        profileId: Int,
    ): Boolean {
        if (bytes.isEmpty() || bytes.size > CUSTOM_SETTINGS_BACKGROUND_MAX_IMAGE_BYTES) return false
        return persistBackgroundImageForStudio(
            store = { storeCustomSettingsBackgroundImageForProfile(bytes, profileId) },
            publish = { uri -> publishCustomSettingsBackgroundImageForProfile(profileId, uri) },
        )
    }

    internal suspend fun clearCustomSettingsBackgroundImageForStudio(profileId: Int): Boolean =
        removeBackgroundImageForStudio(
            remove = { removeCustomSettingsBackgroundImageForProfile(profileId) },
            publish = { uri -> publishCustomSettingsBackgroundImageForProfile(profileId, uri) },
        )

    internal suspend fun saveCustomLibraryCloudBackgroundImageForStudio(
        bytes: ByteArray,
        profileId: Int,
    ): Boolean {
        if (bytes.isEmpty() || bytes.size > CUSTOM_SETTINGS_BACKGROUND_MAX_IMAGE_BYTES) return false
        return persistBackgroundImageForStudio(
            store = { storeCustomLibraryCloudBackgroundImageForProfile(bytes, profileId) },
            publish = { uri -> publishCustomLibraryCloudBackgroundImageForProfile(profileId, uri) },
        )
    }

    internal suspend fun clearCustomLibraryCloudBackgroundImageForStudio(profileId: Int): Boolean =
        removeBackgroundImageForStudio(
            remove = { removeCustomLibraryCloudBackgroundImageForProfile(profileId) },
            publish = { uri -> publishCustomLibraryCloudBackgroundImageForProfile(profileId, uri) },
        )

    fun setCustomSettingsBackgroundImage(
        bytes: ByteArray,
        profileId: Int = ProfileRepository.activeProfileId,
    ): Boolean {
        ensureLoaded()
        val localImageUri = storeCustomSettingsBackgroundImageForProfile(bytes, profileId) ?: return false
        publishCustomSettingsBackgroundImageForProfile(profileId, localImageUri)
        return true
    }

    fun clearCustomSettingsBackgroundImage(
        profileId: Int = ProfileRepository.activeProfileId,
    ): Boolean {
        ensureLoaded()
        val deleted = removeCustomSettingsBackgroundImageForProfile(profileId)
        if (!deleted) return false
        publishCustomSettingsBackgroundImageForProfile(profileId, null)
        return true
    }

    /**
     * Performs only local persistence. Call this from a background dispatcher; publish the
     * returned local URI on the UI dispatcher after the I/O completes.
     */
    internal fun storeCustomLibraryCloudBackgroundImageForProfile(bytes: ByteArray, profileId: Int): String? {
        if (bytes.isEmpty() || bytes.size > CUSTOM_SETTINGS_BACKGROUND_MAX_IMAGE_BYTES) return null
        return ThemeSettingsStorage.saveCustomLibraryCloudBackgroundImage(bytes, profileId)
            ?.takeIf(::isLocalBackgroundImageUri)
    }

    internal fun removeCustomLibraryCloudBackgroundImageForProfile(profileId: Int): Boolean =
        ThemeSettingsStorage.clearCustomLibraryCloudBackgroundImage(profileId)

    internal fun publishCustomLibraryCloudBackgroundImageForProfile(profileId: Int, imageUri: String?) {
        if (ProfileRepository.activeProfileId != profileId) return
        if (imageUri != null && !isLocalBackgroundImageUri(imageUri)) return
        _customLibraryCloudBackgroundImageUri.value = imageUri
        _customLibraryCloudBackgroundImageRevision.value += 1L
    }

    fun setCustomLibraryCloudBackgroundImage(
        bytes: ByteArray,
        profileId: Int = ProfileRepository.activeProfileId,
    ): Boolean {
        ensureLoaded()
        val localImageUri = storeCustomLibraryCloudBackgroundImageForProfile(bytes, profileId) ?: return false
        publishCustomLibraryCloudBackgroundImageForProfile(profileId, localImageUri)
        return true
    }

    fun clearCustomLibraryCloudBackgroundImage(
        profileId: Int = ProfileRepository.activeProfileId,
    ): Boolean {
        ensureLoaded()
        val deleted = removeCustomLibraryCloudBackgroundImageForProfile(profileId)
        if (!deleted) return false
        publishCustomLibraryCloudBackgroundImageForProfile(profileId, null)
        return true
    }

    fun setAppLanguage(language: AppLanguage) {
        ensureLoaded()
        if (_selectedAppLanguage.value == language) return
        ThemeSettingsStorage.saveSelectedAppLanguage(language.code)
        ThemeSettingsStorage.applySelectedAppLanguage(language.code)
        _selectedAppLanguage.value = language
    }

    fun setNavBarStyle(style: NavBarStyle) {
        ensureLoaded()
        if (_navBarStyle.value == style) return
        _navBarStyle.value = style
        ThemeSettingsStorage.saveNavBarStyle(style.key)
    }

    fun setNavBarPosition(position: NavBarPosition) {
        ensureLoaded()
        if (_navBarPosition.value == position) return
        _navBarPosition.value = position
        ThemeSettingsStorage.saveNavBarPosition(position.key)
    }

    fun setNavBarGlowEnabled(enabled: Boolean) {
        ensureLoaded()
        if (_navBarGlowEnabled.value == enabled) return
        _navBarGlowEnabled.value = enabled
        ThemeSettingsStorage.saveNavBarGlowEnabled(enabled)
    }

    fun setNavBarDefaultIconColorEnabled(enabled: Boolean) {
        ensureLoaded()
        if (_navBarDefaultIconColorEnabled.value == enabled) return
        ThemeSettingsStorage.saveNavBarDefaultIconColorEnabled(enabled)
        _navBarDefaultIconColorEnabled.value = enabled
    }

    internal fun updateNavBarDefaultIconColor(color: Int) {
        ensureLoaded()
        val safeColor = color.coerceIn(0, 0xFFFFFF)
        if (_navBarDefaultIconColor.value == safeColor) return
        _navBarDefaultIconColor.value = safeColor
        navBarDefaultIconColorDraftDirty = true
    }

    internal fun saveNavBarDefaultIconColorSettings() {
        ensureLoaded()
        if (!navBarDefaultIconColorDraftDirty) return
        ThemeSettingsStorage.saveNavBarDefaultIconColor(formatHexColor(_navBarDefaultIconColor.value))
        navBarDefaultIconColorDraftDirty = false
        _navBarDefaultIconColorRevision.value += 1L
    }

    private fun migrateLegacyNavBarGlass(raw: String?) {
        val (bar, lens) = migrateLegacyNavBarGlassEffects(raw) ?: return
        if (ThemeSettingsStorage.loadNavBarLiquidGlassBarEffects() == null) {
            _navBarLiquidGlassBarEffects.value = bar
            ThemeSettingsStorage.saveNavBarLiquidGlassBarEffects(
                encodeLiquidGlassEffectSettings(bar, LiquidGlassEffectDefaults.floatingBar, LiquidGlassEffectDefaults.floatingBarLimits),
            )
        }
        if (ThemeSettingsStorage.loadNavBarLiquidGlassSelectionEffects() == null) {
            _navBarLiquidGlassSelectionEffects.value = lens
            ThemeSettingsStorage.saveNavBarLiquidGlassSelectionEffects(
                encodeLiquidGlassEffectSettings(lens, LiquidGlassEffectDefaults.selectionLens, LiquidGlassEffectDefaults.selectionLensLimits),
            )
        }
        ThemeSettingsStorage.saveNavBarGlass(_navBarGlass.value.encode())
    }

    internal fun updateNavBarLiquidGlassBarEffects(settings: LiquidGlassEffectSettings) {
        ensureLoaded()
        _navBarLiquidGlassBarEffects.value = settings.sanitized(
            LiquidGlassEffectDefaults.floatingBar,
            LiquidGlassEffectDefaults.floatingBarLimits,
        )
    }

    internal fun updateNavBarLiquidGlassSelectionEffects(settings: LiquidGlassEffectSettings) {
        ensureLoaded()
        _navBarLiquidGlassSelectionEffects.value = settings.sanitized(
            LiquidGlassEffectDefaults.selectionLens,
            LiquidGlassEffectDefaults.selectionLensLimits,
        )
    }

    internal fun saveNavBarLiquidGlassEffectSettings() {
        ensureLoaded()
        val barEffects = _navBarLiquidGlassBarEffects.value.sanitized(
            LiquidGlassEffectDefaults.floatingBar,
            LiquidGlassEffectDefaults.floatingBarLimits,
        )
        val selectionEffects = _navBarLiquidGlassSelectionEffects.value.sanitized(
            LiquidGlassEffectDefaults.selectionLens,
            LiquidGlassEffectDefaults.selectionLensLimits,
        )
        val persistedBarEffects = decodeLiquidGlassEffectSettings(
            ThemeSettingsStorage.loadNavBarLiquidGlassBarEffects(),
            LiquidGlassEffectDefaults.floatingBar,
            LiquidGlassEffectDefaults.floatingBarLimits,
        )
        val persistedSelectionEffects = decodeLiquidGlassEffectSettings(
            ThemeSettingsStorage.loadNavBarLiquidGlassSelectionEffects(),
            LiquidGlassEffectDefaults.selectionLens,
            LiquidGlassEffectDefaults.selectionLensLimits,
        )
        var changed = false

        if (barEffects != persistedBarEffects) {
            ThemeSettingsStorage.saveNavBarLiquidGlassBarEffects(
                encodeLiquidGlassEffectSettings(
                    barEffects,
                    LiquidGlassEffectDefaults.floatingBar,
                    LiquidGlassEffectDefaults.floatingBarLimits,
                ),
            )
            changed = true
        }
        if (selectionEffects != persistedSelectionEffects) {
            ThemeSettingsStorage.saveNavBarLiquidGlassSelectionEffects(
                encodeLiquidGlassEffectSettings(
                    selectionEffects,
                    LiquidGlassEffectDefaults.selectionLens,
                    LiquidGlassEffectDefaults.selectionLensLimits,
                ),
            )
            changed = true
        }

        if (changed) _navBarLiquidGlassEffectsRevision.value += 1L
    }

    fun setNavBarGlass(style: LiquidGlassStyle) {
        ensureLoaded()
        if (_navBarGlass.value == style) return
        _navBarGlass.value = style
        ThemeSettingsStorage.saveNavBarGlass(style.encode())
    }

    fun setLiquidGlassEffectsEnabled(enabled: Boolean) {
        ensureLoaded()
        if (_liquidGlassEffectsEnabled.value == enabled) return
        applyLiquidGlassEffectsEnabled(enabled)
        ThemeSettingsStorage.saveLiquidGlassEffectsEnabled(enabled)
    }

    private fun applyLiquidGlassEffectsEnabled(enabled: Boolean) {
        _liquidGlassEffectsEnabled.value = enabled
        LiquidGlassSettings.enabled = enabled
    }

    internal fun updateLiquidGlassSurfaceOverrides(
        surface: LiquidGlassSurface,
        overrides: LiquidGlassSurfaceOverrides,
    ) {
        ensureLoaded()
        val updated = _liquidGlassSurfaceSettings.value.withSurface(surface, overrides).sanitized()
        if (updated == _liquidGlassSurfaceSettings.value) return
        _liquidGlassSurfaceSettings.value = updated
        LiquidGlassSettings.surfaceOverrides = updated
    }

    internal fun saveLiquidGlassSurfaceSettings() {
        ensureLoaded()
        val settings = _liquidGlassSurfaceSettings.value.sanitized()
        val encoded = encodeLiquidGlassSurfaceSettings(settings)
        if (ThemeSettingsStorage.loadLiquidGlassSurfaceSettings() != encoded) {
            ThemeSettingsStorage.saveLiquidGlassSurfaceSettings(encoded)
        }
        _liquidGlassSurfaceSettings.value = settings
        LiquidGlassSettings.surfaceOverrides = settings
    }

    private fun updateLiquidGlassSurfacesGeneral(
        transform: (LiquidGlassSurfacesSettings) -> LiquidGlassSurfacesSettings,
    ) {
        ensureLoaded()
        val updated = transform(_liquidGlassSurfaceSettings.value).sanitized()
        if (updated == _liquidGlassSurfaceSettings.value) return
        _liquidGlassSurfaceSettings.value = updated
        LiquidGlassSettings.surfaceOverrides = updated
        saveLiquidGlassSurfaceSettings()
    }

    /** Experimental surfaces master switch. Off keeps every screen exactly as it shipped. */
    internal fun setLiquidGlassExperimentalSurfacesEnabled(enabled: Boolean) =
        updateLiquidGlassSurfacesGeneral { it.copy(experimentalEnabled = enabled) }

    /** Advanced mode exposes per-surface customization; saved per-surface values are kept either way. */
    internal fun setLiquidGlassAdvancedEnabled(enabled: Boolean) =
        updateLiquidGlassSurfacesGeneral { it.copy(advancedEnabled = enabled) }

    internal fun setLiquidGlassGeneralStyle(style: LiquidGlassGeneralStyle) =
        updateLiquidGlassSurfacesGeneral { it.copy(generalStyle = style) }

    /** Clears every per-surface customization while keeping the General choices. */
    internal fun resetLiquidGlassSurfaceCustomizations() =
        updateLiquidGlassSurfacesGeneral { current ->
            LiquidGlassSurfacesSettings(
                experimentalEnabled = current.experimentalEnabled,
                advancedEnabled = current.advancedEnabled,
                generalStyle = current.generalStyle,
            )
        }

    private fun observeMembership() {
        if (observesMembership) return
        observesMembership = true
        MemberAccessRepository.ensureStarted()
        scope.launch {
            MemberAccessRepository.access.collect {
                if (hasLoaded) applyEffectiveTheme()
            }
        }
    }

    private fun applyEffectiveTheme() {
        val access = MemberAccessRepository.access.value
        val effective = resolveAppTheme(
            selectedTheme = _selectedThemePreference.value,
            entitlements = access.entitlements,
        )
        _customThemeColors.value = resolveCustomThemeColors(_customThemePreference.value, access.tier)
        _selectedTheme.value = effective
        val palette = ThemeColors.getColorPalette(effective, _customThemeColors.value)
        NativeTabBridge.publishAccentColor(
            hexColor = palette.nativeAccentHex,
            gradientHexColors = palette.nativeAccentGradientHex(),
        )
    }
}

private fun AppTheme.nativeTabAccentHex(): String =
    ThemeColors.getColorPalette(this).nativeAccentHex
