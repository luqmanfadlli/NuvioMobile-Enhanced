package com.nuvio.app.features.settings

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.LocalNuvioTabletNavLayout
import com.nuvio.app.core.ui.NuvioBottomSheetActionRow
import com.nuvio.app.core.ui.NuvioBottomSheetDivider
import com.nuvio.app.core.ui.NuvioModalBottomSheet
import com.nuvio.app.core.ui.dismissNuvioBottomSheet
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.cd_selected
import nuvio.composeapp.generated.resources.settings_appearance_tab_bar_behavior
import nuvio.composeapp.generated.resources.settings_appearance_tab_bar_behavior_sheet_title
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.floatingNavigationGlowSupported
import com.nuvio.app.core.ui.labelRes
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.collections_header
import nuvio.composeapp.generated.resources.compose_settings_page_continue_watching
import nuvio.composeapp.generated.resources.compose_settings_page_homescreen
import nuvio.composeapp.generated.resources.compose_settings_page_meta_screen
import nuvio.composeapp.generated.resources.compose_settings_page_poster_customization
import nuvio.composeapp.generated.resources.compose_settings_page_streams
import nuvio.composeapp.generated.resources.compose_settings_root_streams_description
import nuvio.composeapp.generated.resources.settings_appearance_continue_watching_description
import nuvio.composeapp.generated.resources.settings_appearance_nav_bar_style
import nuvio.composeapp.generated.resources.settings_appearance_poster_customization_description
import nuvio.composeapp.generated.resources.settings_appearance_section_detail_page
import nuvio.composeapp.generated.resources.settings_appearance_section_home
import nuvio.composeapp.generated.resources.settings_appearance_dynamic_artwork_background
import nuvio.composeapp.generated.resources.settings_appearance_dynamic_artwork_background_description
import nuvio.composeapp.generated.resources.settings_appearance_catalog_accent
import nuvio.composeapp.generated.resources.settings_appearance_catalog_accent_description
import nuvio.composeapp.generated.resources.settings_appearance_section_streams
import nuvio.composeapp.generated.resources.settings_content_discovery_collections_description
import nuvio.composeapp.generated.resources.settings_content_discovery_homescreen_description
import nuvio.composeapp.generated.resources.settings_content_discovery_meta_screen_description
import nuvio.composeapp.generated.resources.settings_nav_bar_glow_off
import nuvio.composeapp.generated.resources.settings_nav_bar_glow_on
import nuvio.composeapp.generated.resources.settings_nav_bar_summary
import org.jetbrains.compose.resources.stringResource

internal fun LazyListScope.layoutSettingsContent(
    isTablet: Boolean,
    liquidGlassNativeTabBarSupported: Boolean,
    onHomescreenClick: () -> Unit,
    onMetaScreenClick: () -> Unit,
    onStreamsClick: () -> Unit,
    onCollectionsClick: () -> Unit,
    onContinueWatchingClick: () -> Unit,
    onPosterCustomizationClick: () -> Unit,
) {
    item {
        val dynamicArtworkBackgroundEnabled by ThemeSettingsRepository.dynamicArtworkBackgroundEnabled.collectAsStateWithLifecycle()
        val showCatalogAccentEnabled by ThemeSettingsRepository.showCatalogAccentEnabled.collectAsStateWithLifecycle()
        SettingsSection(
            title = stringResource(Res.string.settings_appearance_section_home),
            isTablet = isTablet,
        ) {
            SettingsGroup(isTablet = isTablet) {
                SettingsNavigationRow(
                    title = stringResource(Res.string.compose_settings_page_homescreen),
                    description = stringResource(Res.string.settings_content_discovery_homescreen_description),
                    isTablet = isTablet,
                    onClick = onHomescreenClick,
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.collections_header),
                    description = stringResource(Res.string.settings_content_discovery_collections_description),
                    isTablet = isTablet,
                    onClick = onCollectionsClick,
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.compose_settings_page_continue_watching),
                    description = stringResource(Res.string.settings_appearance_continue_watching_description),
                    isTablet = isTablet,
                    onClick = onContinueWatchingClick,
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.compose_settings_page_poster_customization),
                    description = stringResource(Res.string.settings_appearance_poster_customization_description),
                    isTablet = isTablet,
                    onClick = onPosterCustomizationClick,
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsSwitchRow(
                    title = stringResource(Res.string.settings_appearance_dynamic_artwork_background),
                    description = stringResource(
                        Res.string.settings_appearance_dynamic_artwork_background_description,
                    ),
                    checked = dynamicArtworkBackgroundEnabled,
                    isTablet = isTablet,
                    onCheckedChange = ThemeSettingsRepository::setDynamicArtworkBackground,
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsSwitchRow(
                    title = stringResource(Res.string.settings_appearance_catalog_accent),
                    description = stringResource(Res.string.settings_appearance_catalog_accent_description),
                    checked = showCatalogAccentEnabled,
                    isTablet = isTablet,
                    onCheckedChange = ThemeSettingsRepository::setShowCatalogAccent,
                )
            }
        }
    }
    item {
        SettingsSection(
            title = stringResource(Res.string.settings_appearance_section_streams),
            isTablet = isTablet,
        ) {
            SettingsGroup(isTablet = isTablet) {
                SettingsNavigationRow(
                    title = stringResource(Res.string.compose_settings_page_streams),
                    description = stringResource(Res.string.compose_settings_root_streams_description),
                    isTablet = isTablet,
                    onClick = onStreamsClick,
                )
            }
        }
    }
    item {
        SettingsSection(
            title = stringResource(Res.string.settings_appearance_section_detail_page),
            isTablet = isTablet,
        ) {
            SettingsGroup(isTablet = isTablet) {
                SettingsNavigationRow(
                    title = stringResource(Res.string.compose_settings_page_meta_screen),
                    description = stringResource(Res.string.settings_content_discovery_meta_screen_description),
                    isTablet = isTablet,
                    onClick = onMetaScreenClick,
                )
            }
        }
    }
    item {
        var showNavBarStyleSheet by remember { mutableStateOf(false) }
        var showTabBarBehaviorSheet by remember { mutableStateOf(false) }
        val selectedNavBarStyle by ThemeSettingsRepository.navBarStyle.collectAsStateWithLifecycle()
        val glowEnabled by ThemeSettingsRepository.navBarGlowEnabled.collectAsStateWithLifecycle()
        val tabBarBehavior by ThemeSettingsRepository.tabBarBehavior.collectAsStateWithLifecycle()
        val navBarPosition by ThemeSettingsRepository.navBarPosition.collectAsStateWithLifecycle()
        val navBarGlass by ThemeSettingsRepository.navBarGlass.collectAsStateWithLifecycle()
        val navBarDefaultIconColorEnabled by ThemeSettingsRepository.navBarDefaultIconColorEnabled.collectAsStateWithLifecycle()
        val navBarDefaultIconColor by ThemeSettingsRepository.navBarDefaultIconColor.collectAsStateWithLifecycle()
        // Upstream pins tablets to COMPACT because there the bar is a top rail. This fork puts
        // the floating pill at the bottom on every size, so the chosen style still applies.
        val effectiveNavBarStyle = selectedNavBarStyle
        val navBarStyleAvailable = !liquidGlassNativeTabBarSupported || !tabBarBehavior.isEnabled
        SettingsSection(
            title = stringResource(Res.string.settings_appearance_nav_bar_style),
            isTablet = isTablet,
        ) {
            SettingsGroup(isTablet = isTablet) {
                if (liquidGlassNativeTabBarSupported) {
                    SettingsNavigationRow(
                        title = stringResource(Res.string.settings_appearance_tab_bar_behavior),
                        description = stringResource(tabBarBehavior.labelRes),
                        isTablet = isTablet,
                        onClick = { showTabBarBehaviorSheet = true },
                    )
                }
                if (navBarStyleAvailable) {
                    if (liquidGlassNativeTabBarSupported) SettingsGroupDivider(isTablet = isTablet)
                    SettingsNavigationRow(
                        title = stringResource(Res.string.settings_appearance_nav_bar_style),
                        description = if (floatingNavigationGlowSupported && effectiveNavBarStyle != NavBarStyle.CLASSIC) {
                            stringResource(
                                Res.string.settings_nav_bar_summary,
                                stringResource(effectiveNavBarStyle.labelRes),
                                stringResource(if (glowEnabled) Res.string.settings_nav_bar_glow_on else Res.string.settings_nav_bar_glow_off),
                            )
                        } else {
                            stringResource(effectiveNavBarStyle.labelRes)
                        },
                        isTablet = isTablet,
                        onClick = { showNavBarStyleSheet = true },
                    )
                }
            }
        }
        if (showTabBarBehaviorSheet) {
            TabBarBehaviorBottomSheet(
                selectedBehavior = tabBarBehavior,
                onBehaviorSelected = {
                    ThemeSettingsRepository.setTabBarBehavior(it)
                    showTabBarBehaviorSheet = false
                },
                onDismiss = { showTabBarBehaviorSheet = false },
            )
        }
        if (showNavBarStyleSheet) {
            NavigationBarLiquidGlassEffectDrafts { barEffects, selectionEffects ->
                NavigationBarSettingsSheet(
                    // Passing false keeps the style list visible on tablets: the sheet hides it when
                    // isTablet is true, which only fits upstream's tablet top rail.
                    isTablet = false,
                    tabletLayout = LocalNuvioTabletNavLayout.current,
                    selectedStyle = effectiveNavBarStyle,
                    onStyleSelected = ThemeSettingsRepository::setNavBarStyle,
                    glowEnabled = glowEnabled,
                    onGlowChanged = ThemeSettingsRepository::setNavBarGlowEnabled,
                    selectedPosition = navBarPosition,
                    onPositionSelected = ThemeSettingsRepository::setNavBarPosition,
                    glassStyle = navBarGlass,
                    onGlassStyleChanged = ThemeSettingsRepository::setNavBarGlass,
                    defaultIconColorEnabled = navBarDefaultIconColorEnabled,
                    onDefaultIconColorEnabledChanged = ThemeSettingsRepository::setNavBarDefaultIconColorEnabled,
                    defaultIconColor = navBarDefaultIconColor,
                    onDefaultIconColorChanged = ThemeSettingsRepository::updateNavBarDefaultIconColor,
                    onSaveDefaultIconColor = ThemeSettingsRepository::saveNavBarDefaultIconColorSettings,
                    floatingBarEffects = barEffects,
                    onFloatingBarEffectsChanged = ThemeSettingsRepository::updateNavBarLiquidGlassBarEffects,
                    selectionLensEffects = selectionEffects,
                    onSelectionLensEffectsChanged = ThemeSettingsRepository::updateNavBarLiquidGlassSelectionEffects,
                    onSaveLiquidGlassEffectSettings = ThemeSettingsRepository::saveNavBarLiquidGlassEffectSettings,
                    onDismiss = { showNavBarStyleSheet = false },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TabBarBehaviorBottomSheet(
    selectedBehavior: NuvioTabBarBehavior,
    onBehaviorSelected: (NuvioTabBarBehavior) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()

    NuvioModalBottomSheet(
        onDismissRequest = {
            coroutineScope.launch {
                dismissNuvioBottomSheet(sheetState = sheetState, onDismiss = onDismiss)
            }
        },
        sheetState = sheetState,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
        ) {
            item {
                Text(
                    text = stringResource(Res.string.settings_appearance_tab_bar_behavior_sheet_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                )
            }

            itemsIndexed(NuvioTabBarBehavior.entries.toList()) { index, behavior ->
                if (index > 0) {
                    NuvioBottomSheetDivider()
                }
                NuvioBottomSheetActionRow(
                    title = stringResource(behavior.labelRes),
                    onClick = {
                        onBehaviorSelected(behavior)
                        coroutineScope.launch {
                            dismissNuvioBottomSheet(sheetState = sheetState, onDismiss = onDismiss)
                        }
                    },
                    trailingContent = {
                        if (behavior == selectedBehavior) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = stringResource(Res.string.cd_selected),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                )
            }
        }
    }
}
