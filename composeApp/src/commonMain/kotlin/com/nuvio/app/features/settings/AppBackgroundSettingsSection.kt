package com.nuvio.app.features.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.DialogButton
import com.nuvio.app.core.ui.DialogButtonStyle
import com.nuvio.app.core.ui.DialogButtons
import com.nuvio.app.core.ui.DialogSurface
import com.nuvio.app.core.ui.NuvioBottomSheetActionRow
import com.nuvio.app.core.ui.NuvioInputField
import com.nuvio.app.core.ui.NuvioModalBottomSheet
import com.nuvio.app.core.ui.NuvioStatusModal
import com.nuvio.app.core.ui.background.AppBackgroundRepository
import com.nuvio.app.core.ui.background.AppBackgroundSource
import com.nuvio.app.core.ui.background.GlassUiPreset
import com.nuvio.app.core.ui.background.rememberAppBackgroundImagePicker
import com.nuvio.app.core.ui.dismissNuvioBottomSheet
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_cancel
import nuvio.composeapp.generated.resources.action_save
import nuvio.composeapp.generated.resources.cd_selected
import nuvio.composeapp.generated.resources.settings_app_background
import nuvio.composeapp.generated.resources.settings_app_background_choose_photo
import nuvio.composeapp.generated.resources.settings_app_background_error
import nuvio.composeapp.generated.resources.settings_app_background_local
import nuvio.composeapp.generated.resources.settings_app_background_none
import nuvio.composeapp.generated.resources.settings_app_background_remove
import nuvio.composeapp.generated.resources.settings_app_background_url
import nuvio.composeapp.generated.resources.settings_app_background_url_hint
import nuvio.composeapp.generated.resources.settings_app_background_use_url
import nuvio.composeapp.generated.resources.settings_glass_ui_section
import nuvio.composeapp.generated.resources.settings_glass_ui
import nuvio.composeapp.generated.resources.settings_glass_ui_description
import nuvio.composeapp.generated.resources.settings_glass_ui_style
import nuvio.composeapp.generated.resources.settings_glass_ui_style_balanced
import nuvio.composeapp.generated.resources.settings_glass_ui_style_dark
import nuvio.composeapp.generated.resources.settings_glass_ui_style_vivid
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private val GlassUiPreset.labelRes: StringResource
    get() = when (this) {
        GlassUiPreset.DARK -> Res.string.settings_glass_ui_style_dark
        GlassUiPreset.BALANCED -> Res.string.settings_glass_ui_style_balanced
        GlassUiPreset.VIVID -> Res.string.settings_glass_ui_style_vivid
    }

private val AppBackgroundSource.labelRes: StringResource
    get() = when (this) {
        AppBackgroundSource.NONE -> Res.string.settings_app_background_none
        AppBackgroundSource.LOCAL -> Res.string.settings_app_background_local
        AppBackgroundSource.URL -> Res.string.settings_app_background_url
    }

internal fun LazyListScope.appBackgroundSettingsItem(isTablet: Boolean) {
    item(key = "app-background") {
        val config by remember {
            AppBackgroundRepository.ensureLoaded()
            AppBackgroundRepository.config
        }.collectAsStateWithLifecycle()
        var showSourceSheet by remember { mutableStateOf(false) }
        var showUrlDialog by remember { mutableStateOf(false) }
        var showStyleSheet by remember { mutableStateOf(false) }
        var showError by remember { mutableStateOf(false) }
        val pickImage = rememberAppBackgroundImagePicker(
            onPicked = AppBackgroundRepository::setLocalImage,
            onError = { showError = true },
        )

        SettingsSection(
            title = stringResource(Res.string.settings_glass_ui_section),
            isTablet = isTablet,
        ) {
            SettingsGroup(isTablet = isTablet) {
                SettingsSwitchRow(
                    title = stringResource(Res.string.settings_glass_ui),
                    description = stringResource(Res.string.settings_glass_ui_description),
                    checked = config.glassEnabled,
                    isTablet = isTablet,
                    onCheckedChange = AppBackgroundRepository::setGlassEnabled,
                )
                if (config.glassEnabled) {
                    SettingsGroupDivider(isTablet = isTablet)
                    SettingsNavigationRow(
                        title = stringResource(Res.string.settings_app_background),
                        description = stringResource(config.source.labelRes),
                        isTablet = isTablet,
                        onClick = { showSourceSheet = true },
                    )
                    SettingsGroupDivider(isTablet = isTablet)
                    SettingsNavigationRow(
                        title = stringResource(Res.string.settings_glass_ui_style),
                        description = stringResource(config.glassPreset.labelRes),
                        isTablet = isTablet,
                        onClick = { showStyleSheet = true },
                    )
                }
            }
        }

        if (showSourceSheet) {
            val options = buildList {
                add(Res.string.settings_app_background_choose_photo to { pickImage() })
                add(Res.string.settings_app_background_use_url to { showUrlDialog = true })
                if (config.source != AppBackgroundSource.NONE) {
                    add(Res.string.settings_app_background_remove to { AppBackgroundRepository.clear() })
                }
            }
            ChoiceSheet(
                options = options.map { (label, action) -> SheetChoice(stringResource(label), false, action) },
                onDismiss = { showSourceSheet = false },
            )
        }

        if (showStyleSheet) {
            ChoiceSheet(
                options = GlassUiPreset.entries.map { preset ->
                    SheetChoice(stringResource(preset.labelRes), preset == config.glassPreset) {
                        AppBackgroundRepository.setGlassPreset(preset)
                    }
                },
                onDismiss = { showStyleSheet = false },
            )
        }

        if (showUrlDialog) {
            BackgroundUrlDialog(
                initial = config.url,
                onSave = { url ->
                    AppBackgroundRepository.setUrl(url)
                    showUrlDialog = false
                },
                onDismiss = { showUrlDialog = false },
            )
        }

        NuvioStatusModal(
            title = stringResource(Res.string.settings_app_background),
            message = stringResource(Res.string.settings_app_background_error),
            isVisible = showError,
            onConfirm = { showError = false },
        )
    }
}

private class SheetChoice(val title: String, val selected: Boolean, val onClick: () -> Unit)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChoiceSheet(
    options: List<SheetChoice>,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    NuvioModalBottomSheet(
        onDismissRequest = {
            scope.launch { dismissNuvioBottomSheet(sheetState = sheetState, onDismiss = onDismiss) }
        },
        sheetState = sheetState,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            options.forEach { option ->
                NuvioBottomSheetActionRow(
                    title = option.title,
                    selected = option.selected,
                    onClick = {
                        scope.launch {
                            dismissNuvioBottomSheet(sheetState = sheetState, onDismiss = onDismiss)
                            option.onClick()
                        }
                    },
                    trailingContent = if (option.selected) {
                        {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = stringResource(Res.string.cd_selected),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

@Composable
private fun BackgroundUrlDialog(
    initial: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    val valid = value.trim().let {
        it.startsWith("https://", ignoreCase = true) || it.startsWith("http://", ignoreCase = true)
    }
    DialogSurface(
        onDismissRequest = onDismiss,
        title = stringResource(Res.string.settings_app_background_url),
    ) {
        NuvioInputField(
            value = value,
            onValueChange = { value = it },
            placeholder = stringResource(Res.string.settings_app_background_url_hint),
        )
        DialogButtons {
            DialogButton(
                text = stringResource(Res.string.action_cancel),
                onClick = onDismiss,
            )
            DialogButton(
                text = stringResource(Res.string.action_save),
                onClick = { onSave(value) },
                style = DialogButtonStyle.Primary,
                enabled = valid,
            )
        }
    }
}
