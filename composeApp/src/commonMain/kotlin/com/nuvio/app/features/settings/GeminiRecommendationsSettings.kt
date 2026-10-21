package com.nuvio.app.features.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.features.ai.AI_ITEMS_PER_ROW_RANGE
import com.nuvio.app.features.ai.AI_ROW_COUNT_RANGE
import com.nuvio.app.features.ai.AiRecommendationsRepository
import com.nuvio.app.features.ai.AiRowKind
import com.nuvio.app.features.ai.AiSettingsRepository

@Composable
internal fun GeminiRecommendationsCollapsibleRow(isTablet: Boolean) {
    val settings by remember {
        AiSettingsRepository.ensureLoaded()
        AiSettingsRepository.uiState
    }.collectAsStateWithLifecycle()
    val aiState by remember { AiRecommendationsRepository.uiState }.collectAsStateWithLifecycle()
    var apiKey by remember(settings.apiKey) { mutableStateOf(settings.apiKey) }
    var model by remember(settings.model) { mutableStateOf(settings.model) }
    val hasChanges = apiKey.trim() != settings.apiKey || model.trim() != settings.model

    var expanded by remember { mutableStateOf(false) }

    Column {
        SettingsNavigationRow(
            title = "Gemini Recommendations",
            description = null,
            icon = Icons.Rounded.AutoAwesome,
            isTablet = isTablet,
            trailingContent = {
                Icon(
                    imageVector = if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            onClick = { expanded = !expanded },
        )
        AnimatedVisibility(visible = expanded) {
            Column {
                SettingsGroupDivider(isTablet = isTablet)
                SettingsSwitchRow(
                    title = "Enable recommendations",
                    description = "Adds themed rows to Home based on what you watch and save. " +
                        "Titles you watched or saved are sent to Google's Gemini API using your own key.",
                    checked = settings.enabled,
                    isTablet = isTablet,
                    onCheckedChange = AiSettingsRepository::setEnabled,
                )
                SettingsGroupDivider(isTablet = isTablet)
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SettingsSecretTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = "Gemini API key",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = model,
                        onValueChange = { model = it },
                        label = { Text("Model") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Button(
                            onClick = { AiSettingsRepository.saveCredentials(apiKey, model) },
                            enabled = apiKey.isNotBlank() && hasChanges,
                        ) { Text("Save") }
                        OutlinedButton(
                            onClick = { AiRecommendationsRepository.refresh(force = true) },
                            enabled = settings.isUsable && !aiState.isLoading,
                        ) { Text(if (aiState.isLoading) "Generating\u2026" else "Refresh now") }
                    }
                    aiState.errorMessage?.let { message ->
                        Text(
                            text = message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                SettingsGroupDivider(isTablet = isTablet)
                SettingsSliderRow(
                    title = "Themed rows",
                    description = "How many themed rows to show",
                    value = settings.rowCount,
                    valueRange = AI_ROW_COUNT_RANGE,
                    step = 1,
                    isTablet = isTablet,
                    onValueChange = AiSettingsRepository::setRowCount,
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsSliderRow(
                    title = "Titles per row",
                    description = "Most titles shown in each row",
                    value = settings.itemsPerRow,
                    valueRange = AI_ITEMS_PER_ROW_RANGE,
                    step = 5,
                    isTablet = isTablet,
                    onValueChange = AiSettingsRepository::setItemsPerRow,
                )
                AiRowKind.values().forEach { kind ->
                    SettingsGroupDivider(isTablet = isTablet)
                    SettingsSwitchRow(
                        title = kind.label,
                        checked = kind in settings.enabledRowKinds,
                        isTablet = isTablet,
                        onCheckedChange = { AiSettingsRepository.setRowKindEnabled(kind, it) },
                    )
                }
                SettingsGroupDivider(isTablet = isTablet)
                Text(
                    text = "Row types take effect right away. Row count and titles per row take effect " +
                        "after Refresh now.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
    }
}
