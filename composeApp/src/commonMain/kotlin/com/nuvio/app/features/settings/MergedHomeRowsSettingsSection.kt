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
    var rowsExpanded by remember { mutableStateOf(false) }
    val eligibleSourceKeys = remember(state.items) {
        HomeCatalogSettingsRepository.mergedRowSourceKeys()
    }
    val sourceRows = remember(state.items, eligibleSourceKeys) {
        state.items.filter { item -> item.key in eligibleSourceKeys }
    }
    val mergedRows = state.mergedRows.sortedBy { it.order }

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
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Your merged rows",
                        style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "${mergedRows.size} configured",
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { rowsExpanded = !rowsExpanded }) {
                    Text(if (rowsExpanded) "Collapse" else "Open")
                }
            }
            if (rowsExpanded) {
                SettingsGroupDivider(isTablet = isTablet)
                if (mergedRows.isEmpty()) {
                    Text(
                        "No merged rows yet. Create one to combine two or more catalogs.",
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    mergedRows.forEachIndexed { index, row ->
                        if (index > 0) SettingsGroupDivider(isTablet = isTablet)
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(row.title, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                                Text(
                                    "${row.sourceKeys.size} source catalogs · ${row.resolvedDisplayMode().name.lowercase().replace('_', ' ')}",
                                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                )
                                val sourceLabels = row.sourceKeys.map { key ->
                                    sourceRows.firstOrNull { it.key == key }?.displayTitle ?: "Unavailable: $key"
                                }
                                Text(
                                    sourceLabels.joinToString(" · "),
                                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = HomeCatalogSettingsRepository.mergedRowEnabled(row.id),
                                onCheckedChange = { HomeCatalogSettingsRepository.setEnabled(mergedHomeRowKey(row.id), it) },
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            TextButton(onClick = { HomeCatalogSettingsRepository.duplicateMergedRow(row.id) }) {
                                Text("Duplicate")
                            }
                            TextButton(onClick = {
                                editingRow = row
                                showEditor = true
                            }) { Text("Edit") }
                            TextButton(onClick = { showDelete = row }) { Text("Delete") }
                        }
                    }
                }
                SettingsGroupDivider(isTablet = isTablet)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
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
    }

    if (sourceRows.isEmpty() && rowsExpanded) {
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
            onSave = { title, keys, mode, type, blend, deduplicate, hideIncluded, hidePosterless ->
                val saved = if (editingRow == null) {
                    HomeCatalogSettingsRepository.createMergedRow(
                        title = title,
                        sourceKeys = keys,
                        displayMode = mode,
                        deduplicate = deduplicate,
                        contentType = type,
                        blendMode = blend,
                        hideIncludedRows = hideIncluded,
                        hideTitlesWithoutPosters = hidePosterless,
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
                        hideTitlesWithoutPosters = hidePosterless,
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
        Boolean,
    ) -> Boolean,
) {
    var title by remember(row?.id) { mutableStateOf(row?.title.orEmpty()) }
    val availableSourceKeys = remember(sourceRows) { sourceRows.mapTo(mutableSetOf()) { it.key } }
    // Keep unavailable keys in the draft so editing a row never silently drops them.
    var selectedKeys by remember(row?.id) {
        mutableStateOf(row?.sourceKeys?.toSet().orEmpty())
    }
    val unavailableSourceKeys = selectedKeys.filterNot { it in availableSourceKeys }.sorted()
    val validSelectedCount = selectedKeys.count { it in availableSourceKeys }
    var displayMode by remember(row?.id) {
        mutableStateOf(row?.resolvedDisplayMode() ?: MergedHomeRowDisplayMode.SOURCE_TABS)
    }
    var contentType by remember(row?.id) {
        mutableStateOf(row?.resolvedContentType() ?: MergedHomeRowContentType.ALL)
    }
    var blendMode by remember(row?.id) {
        mutableStateOf(
            row?.resolvedBlendMode()
                ?.takeIf {
                    it == MergedHomeRowBlendMode.INTERLEAVE ||
                        it == MergedHomeRowBlendMode.SMART_FRESHNESS_POPULARITY ||
                        it == MergedHomeRowBlendMode.FRESHNESS_WEIGHTED
                }
                ?: MergedHomeRowBlendMode.SMART_FRESHNESS_POPULARITY,
        )
    }
    var deduplicate by remember(row?.id) { mutableStateOf(row?.deduplicate ?: true) }
    var hideIncluded by remember(row?.id) { mutableStateOf(row?.hideIncludedRows ?: false) }
    var hidePosterless by remember(row?.id) { mutableStateOf(row?.hideTitlesWithoutPosters ?: false) }
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
                MergedChoiceRow("Freshness weighted", blendMode == MergedHomeRowBlendMode.FRESHNESS_WEIGHTED) {
                    blendMode = MergedHomeRowBlendMode.FRESHNESS_WEIGHTED
                }
                Text(
                    "Source catalogs ($validSelectedCount/$MAX_MERGED_HOME_ROW_SOURCES selected)",
                    style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
                )
                unavailableSourceKeys.forEach { key ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Unavailable source",
                                color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                            )
                            Text(key, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = { selectedKeys = selectedKeys - key }) {
                            Text("Remove")
                        }
                    }
                }
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
                    validSelectedCount < 2 -> "Select at least two available source catalogs."
                    validSelectedCount > MAX_MERGED_HOME_ROW_SOURCES -> "Select no more than $MAX_MERGED_HOME_ROW_SOURCES source catalogs."
                    unavailableSourceKeys.isNotEmpty() -> "Remove unavailable sources before saving."
                    else -> null
                }
                if (validationError == null) {
                    if (onSave(title, selectedKeys.filter { it in availableSourceKeys }, displayMode, contentType, blendMode, deduplicate, hideIncluded, hidePosterless)) {
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
