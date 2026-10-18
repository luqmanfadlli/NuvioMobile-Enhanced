package com.nuvio.app.features.settings

import com.nuvio.app.core.ui.AppTheme
import com.nuvio.app.core.ui.CustomThemeColors
import com.nuvio.app.core.ui.NativeTabBridge
import com.nuvio.app.core.ui.ThemeColors
import com.nuvio.app.core.ui.glass.LiquidGlassStyle
import com.nuvio.app.core.ui.nativeAccentGradientHex
import com.nuvio.app.features.membership.MemberAccessRepository
import com.nuvio.app.features.membership.resolveCustomThemeColors
import com.nuvio.app.features.membership.resolveAppTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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

    private val _selectedAppLanguage = MutableStateFlow(AppLanguage.DEVICE)
    val selectedAppLanguage: StateFlow<AppLanguage> = _selectedAppLanguage.asStateFlow()

    private val _navBarStyle = MutableStateFlow(NavBarStyle.ADAPTIVE)
    val navBarStyle: StateFlow<NavBarStyle> = _navBarStyle.asStateFlow()

    private val _navBarPosition = MutableStateFlow(NavBarPosition.BOTTOM)
    val navBarPosition: StateFlow<NavBarPosition> = _navBarPosition.asStateFlow()

    private val _navBarGlowEnabled = MutableStateFlow(true)
    val navBarGlowEnabled: StateFlow<Boolean> = _navBarGlowEnabled.asStateFlow()

    private val _navBarGlass = MutableStateFlow(LiquidGlassStyle.Default)
    val navBarGlass: StateFlow<LiquidGlassStyle> = _navBarGlass.asStateFlow()

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
        NativeTabBridge.publishAccentColor(
            hexColor = AppTheme.WHITE.nativeTabAccentHex(),
            gradientHexColors = ThemeColors.getColorPalette(AppTheme.WHITE).nativeAccentGradientHex(),
        )
        NativeTabBridge.publishTabBarBehavior(NuvioTabBarBehavior.Default)
        NativeTabBridge.publishLiquidGlassEnabled(NuvioTabBarBehavior.Default.isEnabled)
        _selectedAppLanguage.value = AppLanguage.DEVICE
        _navBarGlowEnabled.value = true
        _navBarStyle.value = NavBarStyle.ADAPTIVE
        _navBarPosition.value = NavBarPosition.BOTTOM
        _navBarGlass.value = LiquidGlassStyle.Default
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
        val appLanguage = AppLanguage.fromCode(ThemeSettingsStorage.loadSelectedAppLanguage())
        ThemeSettingsStorage.applySelectedAppLanguage(appLanguage.code)
        _selectedAppLanguage.value = appLanguage
        _navBarGlowEnabled.value = ThemeSettingsStorage.loadNavBarGlowEnabled() ?: true
        _navBarStyle.value = NavBarStyle.fromKey(ThemeSettingsStorage.loadNavBarStyle())
        _navBarPosition.value = NavBarPosition.fromKey(ThemeSettingsStorage.loadNavBarPosition())
        _navBarGlass.value = LiquidGlassStyle.decode(ThemeSettingsStorage.loadNavBarGlass())
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

    fun setNavBarGlass(style: LiquidGlassStyle) {
        ensureLoaded()
        if (_navBarGlass.value == style) return
        _navBarGlass.value = style
        ThemeSettingsStorage.saveNavBarGlass(style.encode())
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
