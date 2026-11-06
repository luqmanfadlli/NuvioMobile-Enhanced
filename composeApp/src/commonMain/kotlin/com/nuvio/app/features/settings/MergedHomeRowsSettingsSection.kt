package com.nuvio.app.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.features.home.HomeCatalogSettingsItem
import com.nuvio.app.features.home.HomeCatalogSettingsRepository
import com.nuvio.app.features.home.MAX_MERGED_HOME_ROW_SOURCES
import com.nuvio.app.features.home.MergedHomeRowBlendMode
import com.nuvio.app.features.home.MergedHomeRowContentType
import com.nuvio.app.features.home.MergedHomeRowDefinition
import com.nuvio.app.features.home.MergedHomeRowDisplayMode
import com.nuvio.app.features.home.mergedHomeRowKey

@Composable
internal fun MergedHomeRowsSettingsSection(isTablet: Boolean) {
    val state by HomeCatalogSettingsRepository.uiState.collectAsStateWithLifecycle()
    var editingRow by remember { mutableStateOf<MergedHomeRowDefinition?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf<MergedHomeRowDefinition?>(null) }
    val sourceRows = remember(state.items) {
        state.items.filter { item ->
            item.addonId != null &&
                !item.addonId.startsWith("cloudstream:") &&
                !item.isCollection &&
                !item.isMergedRow
        }
    }

    SettingsSection(
        title = "Merged home rows",
        isTablet = isTablet,
    ) {
        SettingsGroup(isTablet = isTablet) {
            Text(
                "Combine catalogs from different add-ons into one home row. Select at least two sources. Reorder merged rows with the drag handles in the Home Layout catalog list.",
                modifier = Modifier.padding(16.dp),
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            )
            SettingsGroupDivider(isTablet = isTablet)
            SettingsSwitchRow(
                title = "Hide merged titles without posters",
                description = "Apply this filter to merged rows and their source tabs.",
                checked = state.hideMergedTitlesWithoutPosters,
                isTablet = isTablet,
                onCheckedChange = HomeCatalogSettingsRepository::setHideMergedTitlesWithoutPosters,
            )
            SettingsGroupDivider(isTablet = isTablet)
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                Button(
                    onClick = {
                        editingRow = null
                        showEditor = true
                    },
                    enabled = sourceRows.size >= 2,
                ) {
                    Text("Create merged row")
                }
            }
        }
    }

    state.mergedRows.sortedBy { it.order }.forEach { row ->
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = if (isTablet) 24.dp else 16.dp, vertical = 4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(row.title, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                    Text(
                        "${row.sourceKeys.size} source catalogs · ${row.resolvedDisplayMode().name.lowercase().replace('_', ' ')}",
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = HomeCatalogSettingsRepository.mergedRowEnabled(row.id),
                    onCheckedChange = { HomeCatalogSettingsRepository.setEnabled(mergedHomeRowKey(row.id), it) },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = {
                    HomeCatalogSettingsRepository.duplicateMergedRow(row.id)
                }) { Text("Duplicate") }
                TextButton(onClick = {
                    editingRow = row
                    showEditor = true
                }) { Text("Edit") }
                TextButton(onClick = { showDelete = row }) { Text("Delete") }
            }
        }
    }

    if (sourceRows.isEmpty()) {
        Text(
            "Enable at least two add-on catalog rows to create a merged row.",
            modifier = Modifier.padding(horizontal = if (isTablet) 24.dp else 16.dp, vertical = 8.dp),
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
        )
    }

    if (showEditor) {
        MergedHomeRowEditorDialog(
            row = editingRow,
            sourceRows = sourceRows,
            onDismiss = { showEditor = false },
            onSave = { title, keys, mode, type, blend, deduplicate, hideIncluded ->
                val saved = if (editingRow == null) {
                    HomeCatalogSettingsRepository.createMergedRow(
                        title = title,
                        sourceKeys = keys,
                        displayMode = mode,
                        deduplicate = deduplicate,
                        contentType = type,
                        blendMode = blend,
                        hideIncludedRows = hideIncluded,
                    ) != null
                } else {
                    HomeCatalogSettingsRepository.updateMergedRow(
                        id = editingRow!!.id,
                        title = title,
                        sourceKeys = keys,
                        displayMode = mode,
                        deduplicate = deduplicate,
                        contentType = type,
                        blendMode = blend,
                        hideIncludedRows = hideIncluded,
                    )
                }
                if (saved) showEditor = false
                saved
            },
        )
    }

    showDelete?.let { row ->
        AlertDialog(
            onDismissRequest = { showDelete = null },
            title = { Text("Delete merged row?") },
            text = { Text("This removes the merged row and its saved layout preference. The source catalogs remain unchanged.") },
            confirmButton = {
                TextButton(onClick = {
                    HomeCatalogSettingsRepository.deleteMergedRow(row.id)
                    showDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun MergedHomeRowEditorDialog(
    row: MergedHomeRowDefinition?,
    sourceRows: List<HomeCatalogSettingsItem>,
    onDismiss: () -> Unit,
    onSave: (
        String,
        List<String>,
        MergedHomeRowDisplayMode,
        MergedHomeRowContentType,
        MergedHomeRowBlendMode,
        Boolean,
        Boolean,
    ) -> Boolean,
) {
    var title by remember(row?.id) { mutableStateOf(row?.title.orEmpty()) }
    val availableSourceKeys = remember(sourceRows) { sourceRows.mapTo(mutableSetOf()) { it.key } }
    var selectedKeys by remember(row?.id, availableSourceKeys) {
        mutableStateOf(row?.sourceKeys?.filter { it in availableSourceKeys }?.toSet().orEmpty())
    }
    var displayMode by remember(row?.id) {
        mutableStateOf(row?.resolvedDisplayMode() ?: MergedHomeRowDisplayMode.SOURCE_TABS)
    }
    var contentType by remember(row?.id) {
        mutableStateOf(row?.resolvedContentType() ?: MergedHomeRowContentType.ALL)
    }
    var blendMode by remember(row?.id) {
        mutableStateOf(row?.resolvedBlendMode() ?: MergedHomeRowBlendMode.SMART_FRESHNESS_POPULARITY)
    }
    var deduplicate by remember(row?.id) { mutableStateOf(row?.deduplicate ?: true) }
    var hideIncluded by remember(row?.id) { mutableStateOf(row?.hideIncludedRows ?: false) }
    var validationError by remember(row?.id) { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (row == null) "Create merged row" else "Edit merged row") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Row title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Display mode", style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
                MergedChoiceRow("Unified carousel", displayMode == MergedHomeRowDisplayMode.UNIFIED_CAROUSEL) {
                    displayMode = MergedHomeRowDisplayMode.UNIFIED_CAROUSEL
                }
                MergedChoiceRow("Source tabs", displayMode == MergedHomeRowDisplayMode.SOURCE_TABS) {
                    displayMode = MergedHomeRowDisplayMode.SOURCE_TABS
                }
                MergedChoiceRow("Movie / series tabs", displayMode == MergedHomeRowDisplayMode.TYPE_TABS) {
                    displayMode = MergedHomeRowDisplayMode.TYPE_TABS
                }
                if (displayMode != MergedHomeRowDisplayMode.TYPE_TABS) {
                    Text("Content type", style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
                    MergedChoiceRow("All", contentType == MergedHomeRowContentType.ALL) { contentType = MergedHomeRowContentType.ALL }
                    MergedChoiceRow("Movies", contentType == MergedHomeRowContentType.MOVIES) { contentType = MergedHomeRowContentType.MOVIES }
                    MergedChoiceRow("Series", contentType == MergedHomeRowContentType.SERIES) { contentType = MergedHomeRowContentType.SERIES }
                }
                Text("Blend order", style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
                MergedChoiceRow("Interleave providers", blendMode == MergedHomeRowBlendMode.INTERLEAVE) {
                    blendMode = MergedHomeRowBlendMode.INTERLEAVE
                }
                MergedChoiceRow("Smart freshness and popularity", blendMode == MergedHomeRowBlendMode.SMART_FRESHNESS_POPULARITY) {
                    blendMode = MergedHomeRowBlendMode.SMART_FRESHNESS_POPULARITY
                }
                MergedChoiceRow("Provider priority", blendMode == MergedHomeRowBlendMode.PROVIDER_PRIORITY) {
                    blendMode = MergedHomeRowBlendMode.PROVIDER_PRIORITY
                }
                Text("Source catalogs (up to $MAX_MERGED_HOME_ROW_SOURCES)", style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
                sourceRows.forEach { item ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = item.key in selectedKeys,
                            enabled = item.key in selectedKeys || selectedKeys.size < MAX_MERGED_HOME_ROW_SOURCES,
                            onCheckedChange = { checked ->
                                selectedKeys = if (checked) selectedKeys + item.key else selectedKeys - item.key
                            },
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.displayTitle)
                            Text(item.addonName, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = deduplicate, onCheckedChange = { deduplicate = it })
                    Text("Remove duplicate titles")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = hideIncluded, onCheckedChange = { hideIncluded = it })
                    Text("Hide included source rows from Home")
                }
                validationError?.let { Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = {
                validationError = when {
                    title.isBlank() -> "Enter a title."
                    selectedKeys.size < 2 -> "Select at least two source catalogs."
                    else -> null
                }
                if (validationError == null) {
                    if (onSave(title, selectedKeys.toList(), displayMode, contentType, blendMode, deduplicate, hideIncluded)) {
                        onDismiss()
                    } else {
                        validationError = "The selected source catalogs are no longer available."
                    }
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun MergedChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label)
    }
}
