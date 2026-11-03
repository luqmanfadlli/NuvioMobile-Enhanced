package com.nuvio.app.features.cloud

import com.nuvio.app.core.ui.nuvioFieldContainerColor
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import com.nuvio.app.core.ui.DialogButton
import com.nuvio.app.core.ui.DialogButtonStyle
import com.nuvio.app.core.ui.DialogButtons
import com.nuvio.app.core.ui.DialogSurface
import com.nuvio.app.features.debrid.DebridCredentialValidator
import com.nuvio.app.features.debrid.DebridProviders
import com.nuvio.app.features.debrid.DebridSettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_cancel
import nuvio.composeapp.generated.resources.action_save
import nuvio.composeapp.generated.resources.action_saving
import nuvio.composeapp.generated.resources.cloud_library_torbox_api_key_hint
import nuvio.composeapp.generated.resources.settings_debrid_dialog_placeholder
import nuvio.composeapp.generated.resources.settings_debrid_dialog_subtitle
import nuvio.composeapp.generated.resources.settings_debrid_dialog_title
import nuvio.composeapp.generated.resources.settings_debrid_key_invalid
import org.jetbrains.compose.resources.stringResource

private const val TORBOX_API_KEY_SETTINGS_URL = "https://torbox.app/settings"

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun TorboxCloudConnectDialog(
    onDismiss: () -> Unit,
    onConnected: () -> Unit = {},
) {
    val provider = DebridProviders.Torbox
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    var draft by rememberSaveable { mutableStateOf("") }
    var isValidating by rememberSaveable { mutableStateOf(false) }
    var validationMessage by rememberSaveable { mutableStateOf<String?>(null) }
    val normalizedDraft = draft.trim()
    val invalidMessage = stringResource(Res.string.settings_debrid_key_invalid)

    val connect: () -> Unit = {
        scope.launch {
            isValidating = true
            validationMessage = null
            val valid = normalizedDraft.isNotBlank() && try {
                DebridCredentialValidator.validateProvider(provider.id, normalizedDraft)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                false
            }
            if (valid) {
                connectTorboxCloudLibrary(normalizedDraft)
                isValidating = false
                onConnected()
                onDismiss()
            } else {
                validationMessage = invalidMessage
                isValidating = false
            }
        }
    }

    DialogSurface(
        onDismissRequest = onDismiss,
        title = stringResource(Res.string.settings_debrid_dialog_title, provider.displayName),
    ) {
        Text(
            text = stringResource(Res.string.settings_debrid_dialog_subtitle, provider.displayName),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = draft,
            onValueChange = {
                draft = it
                validationMessage = null
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !isValidating,
            placeholder = { Text(stringResource(Res.string.settings_debrid_dialog_placeholder, provider.displayName)) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f),
                focusedContainerColor = nuvioFieldContainerColor(MaterialTheme.colorScheme.surface),
                unfocusedContainerColor = nuvioFieldContainerColor(MaterialTheme.colorScheme.surface),
                disabledContainerColor = nuvioFieldContainerColor(MaterialTheme.colorScheme.surface),
            ),
        )
        validationMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Text(
            text = stringResource(Res.string.cloud_library_torbox_api_key_hint),
            modifier = Modifier.clickable {
                runCatching { uriHandler.openUri(TORBOX_API_KEY_SETTINGS_URL) }
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
        DialogButtons {
            DialogButton(
                text = stringResource(Res.string.action_cancel),
                onClick = onDismiss,
            )
            DialogButton(
                text = if (isValidating) {
                    stringResource(Res.string.action_saving)
                } else {
                    stringResource(Res.string.action_save)
                },
                onClick = connect,
                enabled = normalizedDraft.isNotBlank() && !isValidating,
                style = DialogButtonStyle.Primary,
            )
        }
    }
}

fun connectTorboxCloudLibrary(apiKey: String) {
    val normalized = apiKey.trim()
    if (normalized.isBlank()) return
    DebridSettingsRepository.setProviderApiKey(DebridProviders.TORBOX_ID, normalized)
    DebridSettingsRepository.setCloudLibraryEnabled(true)
    CloudLibraryRepository.refresh()
}
