package com.nuvio.app.features.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.DialogButton
import com.nuvio.app.core.ui.DialogButtonStyle
import com.nuvio.app.core.ui.DialogButtons
import com.nuvio.app.core.ui.DialogSurface
import com.nuvio.app.core.ui.NuvioToastController
import com.nuvio.app.core.ui.nuvio
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_cancel
import nuvio.composeapp.generated.resources.settings_backup_export
import nuvio.composeapp.generated.resources.settings_backup_export_description
import nuvio.composeapp.generated.resources.settings_backup_export_done
import nuvio.composeapp.generated.resources.settings_backup_export_failed
import nuvio.composeapp.generated.resources.settings_backup_import
import nuvio.composeapp.generated.resources.settings_backup_import_confirm_action
import nuvio.composeapp.generated.resources.settings_backup_import_confirm_message
import nuvio.composeapp.generated.resources.settings_backup_import_confirm_message_sensitive
import nuvio.composeapp.generated.resources.settings_backup_import_confirm_title
import nuvio.composeapp.generated.resources.settings_backup_import_description
import nuvio.composeapp.generated.resources.settings_backup_import_failed
import nuvio.composeapp.generated.resources.settings_backup_import_invalid
import nuvio.composeapp.generated.resources.settings_backup_importing
import nuvio.composeapp.generated.resources.settings_backup_include_sensitive
import nuvio.composeapp.generated.resources.settings_backup_include_sensitive_description
import nuvio.composeapp.generated.resources.settings_backup_section
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SettingsConfigBackupSection(isTablet: Boolean) {
    var includeSensitiveData by rememberSaveable { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<SettingsConfigImport?>(null) }
    val isImporting by SettingsConfigTransfer.isImporting.collectAsStateWithLifecycle()

    val exportDoneText = stringResource(Res.string.settings_backup_export_done)
    val exportFailedText = stringResource(Res.string.settings_backup_export_failed)
    val importFailedText = stringResource(Res.string.settings_backup_import_failed)
    val importInvalidText = stringResource(Res.string.settings_backup_import_invalid)

    val fileLauncher = rememberSettingsConfigFileLauncher(
        onSaved = { NuvioToastController.show(exportDoneText) },
        onSaveFailed = { NuvioToastController.show(exportFailedText) },
        onLoaded = { content ->
            val config = SettingsConfigTransfer.parse(content)
            if (config == null) {
                NuvioToastController.show(importInvalidText)
            } else {
                pendingImport = config
            }
        },
        onLoadFailed = { NuvioToastController.show(importFailedText) },
    )

    SettingsSection(
        title = stringResource(Res.string.settings_backup_section),
        isTablet = isTablet,
    ) {
        SettingsGroup(isTablet = isTablet) {
            SettingsSwitchRow(
                title = stringResource(Res.string.settings_backup_include_sensitive),
                description = stringResource(Res.string.settings_backup_include_sensitive_description),
                checked = includeSensitiveData,
                isTablet = isTablet,
                onCheckedChange = { includeSensitiveData = it },
            )
            SettingsGroupDivider(isTablet = isTablet)
            SettingsNavigationRow(
                title = stringResource(Res.string.settings_backup_export),
                description = stringResource(Res.string.settings_backup_export_description),
                isTablet = isTablet,
                onClick = {
                    val content = runCatching { SettingsConfigTransfer.export(includeSensitiveData) }.getOrNull()
                    if (content == null) {
                        NuvioToastController.show(exportFailedText)
                    } else {
                        fileLauncher.save(SettingsConfigTransfer.exportFileName(), content)
                    }
                },
            )
            SettingsGroupDivider(isTablet = isTablet)
            SettingsNavigationRow(
                title = stringResource(Res.string.settings_backup_import),
                description = stringResource(
                    if (isImporting) Res.string.settings_backup_importing else Res.string.settings_backup_import_description,
                ),
                enabled = !isImporting,
                isTablet = isTablet,
                onClick = fileLauncher::pick,
            )
        }
    }

    pendingImport?.let { config ->
        DialogSurface(
            onDismissRequest = { pendingImport = null },
            title = stringResource(Res.string.settings_backup_import_confirm_title),
        ) {
            Text(
                text = stringResource(
                    if (config.includesSensitiveData) {
                        Res.string.settings_backup_import_confirm_message_sensitive
                    } else {
                        Res.string.settings_backup_import_confirm_message
                    },
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.nuvio.colors.textMuted,
            )
            DialogButtons {
                DialogButton(
                    text = stringResource(Res.string.action_cancel),
                    onClick = { pendingImport = null },
                )
                DialogButton(
                    text = stringResource(Res.string.settings_backup_import_confirm_action),
                    onClick = {
                        pendingImport = null
                        SettingsConfigTransfer.startImport(config.settings)
                    },
                    style = DialogButtonStyle.Primary,
                )
            }
        }
    }
}
