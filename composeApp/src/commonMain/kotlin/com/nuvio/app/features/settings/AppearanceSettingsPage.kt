package com.nuvio.app.features.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import com.nuvio.app.core.ui.glass.LiquidGlassGeneralStyle
import nuvio.composeapp.generated.resources.settings_liquid_glass_experimental
import nuvio.composeapp.generated.resources.settings_liquid_glass_experimental_description
import nuvio.composeapp.generated.resources.settings_liquid_glass_advanced
import nuvio.composeapp.generated.resources.settings_liquid_glass_advanced_description
import nuvio.composeapp.generated.resources.settings_liquid_glass_style
import nuvio.composeapp.generated.resources.settings_liquid_glass_style_description
import nuvio.composeapp.generated.resources.settings_liquid_glass_style_advanced_note
import nuvio.composeapp.generated.resources.settings_liquid_glass_style_readable
import nuvio.composeapp.generated.resources.settings_liquid_glass_style_balanced
import nuvio.composeapp.generated.resources.settings_liquid_glass_style_vivid
import nuvio.composeapp.generated.resources.settings_liquid_glass_customize_description
import nuvio.composeapp.generated.resources.settings_liquid_glass_customize
import nuvio.composeapp.generated.resources.settings_appearance_custom_settings_background_clear
import nuvio.composeapp.generated.resources.settings_appearance_custom_settings_background_choose
import nuvio.composeapp.generated.resources.settings_appearance_custom_settings_background_saving
import nuvio.composeapp.generated.resources.settings_appearance_custom_settings_background_error
import nuvio.composeapp.generated.resources.settings_appearance_custom_settings_background_preview
import nuvio.composeapp.generated.resources.settings_appearance_custom_settings_background_description
import nuvio.composeapp.generated.resources.settings_appearance_custom_settings_background
import kotlinx.coroutines.CancellationException
import com.nuvio.app.features.profiles.ProfileRepository
import coil3.request.ImageRequest
import coil3.request.CachePolicy
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.material3.TextButton
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.AppTheme
import com.nuvio.app.core.ui.NuvioBottomSheetActionRow
import com.nuvio.app.core.ui.NuvioModalBottomSheet
import com.nuvio.app.core.ui.NuvioStatusModal
import com.nuvio.app.core.ui.dismissNuvioBottomSheet
import com.nuvio.app.core.ui.labelRes
import com.nuvio.app.isIos
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.glass.isLiquidGlassAvailable
import com.nuvio.app.core.ui.isNativeLiquidGlassControlsSupportedByOs
import com.nuvio.app.core.ui.shouldExposeLiquidGlassSettings
import nuvio.composeapp.generated.resources.settings_appearance_dynamic_artwork_background
import nuvio.composeapp.generated.resources.settings_appearance_dynamic_artwork_background_description
import nuvio.composeapp.generated.resources.settings_appearance_catalog_accent
import nuvio.composeapp.generated.resources.settings_appearance_catalog_accent_description
import nuvio.composeapp.generated.resources.settings_appearance_liquid_glass_effects
import nuvio.composeapp.generated.resources.settings_appearance_liquid_glass_effects_description
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.cd_selected
import nuvio.composeapp.generated.resources.settings_appearance_amoled_black
import nuvio.composeapp.generated.resources.settings_appearance_amoled_description
import nuvio.composeapp.generated.resources.settings_appearance_app_icon
import nuvio.composeapp.generated.resources.settings_appearance_app_language
import nuvio.composeapp.generated.resources.settings_appearance_app_language_restart_message
import nuvio.composeapp.generated.resources.settings_appearance_app_language_restart_title
import nuvio.composeapp.generated.resources.settings_appearance_app_language_sheet_title
import nuvio.composeapp.generated.resources.settings_appearance_section_display
import nuvio.composeapp.generated.resources.settings_appearance_section_theme
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

internal fun LazyListScope.appearanceSettingsContent(
    isTablet: Boolean,
    selectedTheme: AppTheme,
    onThemeSelected: (AppTheme) -> Unit,
    amoledEnabled: Boolean,
    onAmoledToggle: (Boolean) -> Unit,
    appIconState: AppIconSettingsState,
    onAppIconSelected: (AppIconOption) -> Unit,
    onAppIconFailureDismissed: () -> Unit,
    selectedAppLanguage: AppLanguage,
    onAppLanguageSelected: (AppLanguage) -> Unit,
) {
    item {
        SettingsSection(
            title = stringResource(Res.string.settings_appearance_section_theme),
            isTablet = isTablet,
        ) {
            SettingsGroup(isTablet = isTablet) {
                AppearanceThemeSelector(
                    isTablet = isTablet,
                    selectedTheme = selectedTheme,
                    onThemeSelected = onThemeSelected,
                )
            }
        }
    }
    item {
        var showLanguageSheet by remember { mutableStateOf(false) }
        var showLanguageRestartDialog by remember { mutableStateOf(false) }
        val layoutDirection = LocalLayoutDirection.current
        var showLiquidGlassSurfacesSheet by remember { mutableStateOf(false) }
        val customSettingsBackgroundImageUri by remember {
            ThemeSettingsRepository.ensureLoaded()
            ThemeSettingsRepository.customSettingsBackgroundImageUri
        }.collectAsStateWithLifecycle()
        val customSettingsBackgroundImageRevision by remember {
            ThemeSettingsRepository.customSettingsBackgroundImageRevision
        }.collectAsStateWithLifecycle()
        val settingsBackgroundImageContext = LocalPlatformContext.current
        val settingsBackgroundImageRequest = remember(
            customSettingsBackgroundImageUri,
            customSettingsBackgroundImageRevision,
            settingsBackgroundImageContext,
        ) {
            customSettingsBackgroundImageUri?.takeIf { isLocalBackgroundImageUri(it) }?.let { imageUri ->
                ImageRequest.Builder(settingsBackgroundImageContext)
                    .data(imageUri)
                    .memoryCacheKey("$imageUri#settings-background-$customSettingsBackgroundImageRevision")
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.DISABLED)
                    .build()
            }
        }
        var isSavingSettingsBackground by remember { mutableStateOf(false) }
        var settingsBackgroundImageError by remember { mutableStateOf(false) }
        val settingsBackgroundScope = rememberCoroutineScope()
        val settingsBackgroundImagePicker = rememberSettingsBackgroundImagePicker(
            onImageSelected = { bytes ->
                val targetProfileId = ProfileRepository.activeProfileId
                settingsBackgroundImageError = false
                isSavingSettingsBackground = true
                settingsBackgroundScope.launch {
                    val saved = try {
                        ThemeSettingsRepository.saveCustomSettingsBackgroundImageForStudio(bytes, targetProfileId)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        false
                    }
                    isSavingSettingsBackground = false
                    if (ProfileRepository.activeProfileId == targetProfileId) {
                        settingsBackgroundImageError = !saved
                    }
                }
            },
            onError = { settingsBackgroundImageError = true },
        )
        var showAppIconPicker by remember { mutableStateOf(false) }
        val liquidGlassEffectsEnabled by ThemeSettingsRepository.liquidGlassEffectsEnabled.collectAsStateWithLifecycle()
        val liquidGlassSurfaceSettings by ThemeSettingsRepository.liquidGlassSurfaceSettings.collectAsStateWithLifecycle()
        SettingsSection(
            title = stringResource(Res.string.settings_appearance_section_display),
            isTablet = isTablet,
        ) {
            SettingsGroup(isTablet = isTablet) {
                SettingsSwitchRow(
                    title = stringResource(Res.string.settings_appearance_amoled_black),
                    description = stringResource(Res.string.settings_appearance_amoled_description),
                    checked = amoledEnabled,
                    isTablet = isTablet,
                    onCheckedChange = onAmoledToggle,
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.settings_appearance_custom_settings_background),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(Res.string.settings_appearance_custom_settings_background_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    settingsBackgroundImageRequest?.let { imageRequest ->
                        AsyncImage(
                            model = imageRequest,
                            contentDescription = stringResource(Res.string.settings_appearance_custom_settings_background_preview),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(112.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop,
                        )
                    }
                    if (settingsBackgroundImageError) {
                        Text(
                            text = stringResource(Res.string.settings_appearance_custom_settings_background_error),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    if (isSavingSettingsBackground) {
                        Text(
                            text = stringResource(Res.string.settings_appearance_custom_settings_background_saving),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            enabled = !isSavingSettingsBackground,
                            onClick = { settingsBackgroundImagePicker.launch() },
                        ) {
                            Text(stringResource(Res.string.settings_appearance_custom_settings_background_choose))
                        }
                        TextButton(
                            enabled = customSettingsBackgroundImageUri != null && !isSavingSettingsBackground,
                            onClick = {
                                val targetProfileId = ProfileRepository.activeProfileId
                                settingsBackgroundImageError = false
                                isSavingSettingsBackground = true
                                settingsBackgroundScope.launch {
                                    val cleared = try {
                                        ThemeSettingsRepository.clearCustomSettingsBackgroundImageForStudio(targetProfileId)
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (_: Exception) {
                                        false
                                    }
                                    isSavingSettingsBackground = false
                                    if (ProfileRepository.activeProfileId == targetProfileId) {
                                        settingsBackgroundImageError = !cleared
                                    }
                                }
                            },
                        ) {
                            Text(stringResource(Res.string.settings_appearance_custom_settings_background_clear))
                        }
                    }
                }
                if (shouldExposeLiquidGlassSettings(isLiquidGlassAvailable(), isNativeLiquidGlassControlsSupportedByOs())) {
                    SettingsGroupDivider(isTablet = isTablet)
                    LiquidGlassSettingsSwitchRow(
                        title = stringResource(Res.string.settings_appearance_liquid_glass_effects),
                        description = stringResource(Res.string.settings_appearance_liquid_glass_effects_description),
                        checked = liquidGlassEffectsEnabled,
                        isTablet = isTablet,
                        onCheckedChange = ThemeSettingsRepository::setLiquidGlassEffectsEnabled,
                    )
                    if (liquidGlassEffectsEnabled) {
                        SettingsGroupDivider(isTablet = isTablet)
                        LiquidGlassSettingsSwitchRow(
                            title = stringResource(Res.string.settings_liquid_glass_experimental),
                            description = stringResource(Res.string.settings_liquid_glass_experimental_description),
                            checked = liquidGlassSurfaceSettings.experimentalEnabled,
                            isTablet = isTablet,
                            onCheckedChange = ThemeSettingsRepository::setLiquidGlassExperimentalSurfacesEnabled,
                        )
                        if (liquidGlassSurfaceSettings.experimentalEnabled) {
                            SettingsGroupDivider(isTablet = isTablet)
                            LiquidGlassGeneralStyleRow(
                                selected = liquidGlassSurfaceSettings.generalStyle,
                                enabled = !liquidGlassSurfaceSettings.advancedEnabled,
                                isTablet = isTablet,
                                onSelected = ThemeSettingsRepository::setLiquidGlassGeneralStyle,
                            )
                            SettingsGroupDivider(isTablet = isTablet)
                            LiquidGlassSettingsSwitchRow(
                                title = stringResource(Res.string.settings_liquid_glass_advanced),
                                description = stringResource(Res.string.settings_liquid_glass_advanced_description),
                                checked = liquidGlassSurfaceSettings.advancedEnabled,
                                isTablet = isTablet,
                                onCheckedChange = ThemeSettingsRepository::setLiquidGlassAdvancedEnabled,
                            )
                            if (liquidGlassSurfaceSettings.advancedEnabled) {
                                SettingsGroupDivider(isTablet = isTablet)
                                SettingsNavigationRow(
                                    title = stringResource(Res.string.settings_liquid_glass_customize),
                                    description = stringResource(Res.string.settings_liquid_glass_customize_description),
                                    isTablet = isTablet,
                                    onClick = { showLiquidGlassSurfacesSheet = true },
                                )
                            }
                        }
                    }
                }
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.settings_appearance_app_icon),
                    description = stringResource(appIconState.selected.labelResource),
                    enabled = appIconState.pending == null,
                    isTablet = isTablet,
                    trailingContent = {
                        AppIconThumbnail(
                            icon = appIconState.selected,
                            modifier = Modifier.size(if (isTablet) 44.dp else 40.dp),
                            cornerRadius = if (isTablet) 11.dp else 10.dp,
                        )
                    },
                    onClick = {
                        onAppIconFailureDismissed()
                        showAppIconPicker = true
                    },
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.settings_appearance_app_language),
                    description = stringResource(selectedAppLanguage.labelRes),
                    isTablet = isTablet,
                    onClick = { showLanguageSheet = true },
                )
            }
        }

        if (showLanguageSheet) {
            AppearanceLanguageBottomSheet(
                selectedLanguage = selectedAppLanguage,
                onLanguageSelected = {
                    onAppLanguageSelected(it)
                    showLanguageSheet = false
                    val newLayoutDirection = if (it.isRightToLeft()) LayoutDirection.Rtl else LayoutDirection.Ltr
                    if (isIos && newLayoutDirection != layoutDirection) showLanguageRestartDialog = true
                },
                onDismiss = { showLanguageSheet = false },
            )
        }

        NuvioStatusModal(
            title = stringResource(Res.string.settings_appearance_app_language_restart_title),
            message = stringResource(Res.string.settings_appearance_app_language_restart_message),
            isVisible = showLanguageRestartDialog,
            onConfirm = { showLanguageRestartDialog = false },
        )

        if (showAppIconPicker) {
            AppIconPicker(
                isTablet = isTablet,
                state = appIconState,
                onSelected = onAppIconSelected,
                onDismiss = {
                    onAppIconFailureDismissed()
                    showAppIconPicker = false
                },
            )
        }

        if (showLiquidGlassSurfacesSheet) {
            LiquidGlassSurfaceSettingsDrafts { surfaceSettings ->
                LiquidGlassSurfacesSettingsSheet(
                    isTablet = isTablet,
                    settings = surfaceSettings,
                    onSurfaceChanged = ThemeSettingsRepository::updateLiquidGlassSurfaceOverrides,
                    onSave = ThemeSettingsRepository::saveLiquidGlassSurfaceSettings,
                    onDismiss = { showLiquidGlassSurfacesSheet = false },
                )
            }
        }
    }
}

private data class AppLanguageSheetOption(
    val language: AppLanguage,
    val labelRes: StringResource,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppearanceLanguageBottomSheet(
    selectedLanguage: AppLanguage,
    onLanguageSelected: (AppLanguage) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()
    val options = remember {
        AppLanguage.entries.map { language ->
            AppLanguageSheetOption(
                language = language,
                labelRes = language.labelRes,
            )
        }
    }

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
                    text = stringResource(Res.string.settings_appearance_app_language_sheet_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                )
            }

            items(options) { option ->
                NuvioBottomSheetActionRow(
                    title = stringResource(option.labelRes),
                    selected = option.language == selectedLanguage,
                    onClick = {
                        onLanguageSelected(option.language)
                        coroutineScope.launch {
                            dismissNuvioBottomSheet(sheetState = sheetState, onDismiss = onDismiss)
                        }
                    },
                    trailingContent = {
                        if (option.language == selectedLanguage) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LiquidGlassGeneralStyleRow(
    selected: LiquidGlassGeneralStyle,
    enabled: Boolean,
    isTablet: Boolean,
    onSelected: (LiquidGlassGeneralStyle) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = if (isTablet) 20.dp else 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(Res.string.settings_liquid_glass_style),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(
                if (enabled) {
                    Res.string.settings_liquid_glass_style_description
                } else {
                    Res.string.settings_liquid_glass_style_advanced_note
                },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            val styles = LiquidGlassGeneralStyle.entries
            styles.forEachIndexed { index, style ->
                SegmentedButton(
                    selected = style == selected,
                    onClick = { onSelected(style) },
                    enabled = enabled,
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = styles.size),
                ) {
                    Text(
                        text = stringResource(
                            when (style) {
                                LiquidGlassGeneralStyle.READABLE -> Res.string.settings_liquid_glass_style_readable
                                LiquidGlassGeneralStyle.BALANCED -> Res.string.settings_liquid_glass_style_balanced
                                LiquidGlassGeneralStyle.VIVID -> Res.string.settings_liquid_glass_style_vivid
                            },
                        ),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
