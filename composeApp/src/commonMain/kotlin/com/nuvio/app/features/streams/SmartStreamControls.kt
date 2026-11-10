package com.nuvio.app.features.streams

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.build.AppFeaturePolicy
import com.nuvio.app.core.ui.NuvioModalBottomSheet
import com.nuvio.app.core.ui.NuvioToastController
import com.nuvio.app.core.ui.dismissNuvioBottomSheet
import com.nuvio.app.core.ui.nuvio
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class SmartStreamsState(
    /** Null for the first moment after the screen opens, while the background sort finishes. */
    val result: SmartStreamsResult?,
    val settings: SmartStreamSettings,
    val device: SmartDeviceProfile,
)

@Composable
internal fun rememberSmartStreams(
    listState: StreamsUiState,
    allState: StreamsUiState,
    debridEnabled: Boolean,
    isEpisode: Boolean,
): SmartStreamsState {
    val settings by SmartStreamSettingsRepository.state.collectAsStateWithLifecycle()
    val device = remember { currentSmartDeviceProfile() }
    DisposableEffect(Unit) {
        onDispose { SmartStreamSettingsRepository.onStreamsScreenClosed() }
    }
    // Sorting runs in the background; the screen keeps showing the last result until the new one is ready.
    val result by produceState<SmartStreamsResult?>(
        initialValue = null,
        listState, allState, settings, device, debridEnabled, isEpisode,
    ) {
        value = withContext(SmartStreamRanking.dispatcher) {
            SmartStreamRanking.compute(
                listState = listState,
                allState = allState,
                settings = settings,
                device = device,
                debridEnabled = debridEnabled,
                isEpisode = isEpisode,
            )
        }
    }
    return SmartStreamsState(result, settings, device)
}

private fun updateFilters(transform: (SmartStreamFilters) -> SmartStreamFilters) {
    SmartStreamSettingsRepository.update { it.copy(filters = transform(it.filters)) }
}

private fun <T> Set<T>.toggle(value: T): Set<T> = if (value in this) this - value else this + value

// ---------------------------------------------------------------------------------------------
// The bar above the list: Play best, Sort, Filters and the active filter chips.
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SmartStreamControls(
    state: SmartStreamsState,
    onPlay: (StreamItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.nuvio.colors
    var showSort by remember { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }
    var showBest by remember { mutableStateOf(false) }
    val result = state.result
    val best = result?.best
    val filters = state.settings.filters

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (best != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 60.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.accent)
                    .combinedClickable(
                        onClick = { onPlay(best.stream) },
                        onLongClick = { showBest = true },
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(colors.onAccent.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = colors.onAccent)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Play best for this device",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = colors.onAccent,
                        maxLines = 1,
                    )
                    val summary = best.info.summary().ifBlank { best.stream.addonName }
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onAccent.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmartBarButton(
                icon = { Icon(Icons.Rounded.SwapVert, contentDescription = null, modifier = Modifier.size(18.dp)) },
                label = state.settings.sortMode.label,
                highlighted = false,
                trailing = {
                    Icon(
                        Icons.Rounded.KeyboardArrowDown,
                        contentDescription = null,
                        tint = colors.textMuted,
                        modifier = Modifier.size(18.dp),
                    )
                },
                onClick = { showSort = true },
                modifier = Modifier.weight(1f),
            )
            SmartBarButton(
                icon = { Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(18.dp)) },
                label = "Filters",
                highlighted = filters.activeCount > 0,
                trailing = if (filters.activeCount > 0) {
                    {
                        Box(
                            modifier = Modifier
                                .heightIn(min = 20.dp)
                                .clip(CircleShape)
                                .background(colors.accent)
                                .padding(horizontal = 7.dp, vertical = 2.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = filters.activeCount.toString(),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = colors.onAccent,
                            )
                        }
                    }
                } else {
                    null
                },
                onClick = { showFilters = true },
                modifier = Modifier.weight(1f),
            )
        }

        val chips = activeFilterChips(filters)
        if (chips.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    chips.forEach { chip ->
                        Row(
                            modifier = Modifier
                                .height(32.dp)
                                .clip(CircleShape)
                                .background(colors.accent.copy(alpha = 0.14f))
                                .border(1.dp, colors.accent.copy(alpha = 0.55f), CircleShape)
                                .clickable(onClick = chip.remove)
                                .padding(start = 12.dp, end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = chip.label,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = colors.textPrimary,
                                maxLines = 1,
                            )
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = "Remove ${chip.label} filter",
                                tint = colors.textPrimary,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
                Text(
                    text = "Clear all",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.textSecondary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { updateFilters { SmartStreamFilters() } }
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                )
            }
        }

        if (result != null && result.totalCount > 0) {
            val order = when (state.settings.sortMode) {
                SmartSortMode.BEST_MATCH -> "BEST MATCH FIRST"
                SmartSortMode.HIGHEST_QUALITY -> "HIGHEST QUALITY FIRST"
                SmartSortMode.SMALLEST_FILE -> "SMALLEST FIRST"
                SmartSortMode.LARGEST_FILE -> "LARGEST FIRST"
                SmartSortMode.ADDON_ORDER -> "BY ADDON"
            }
            Text(
                text = "${result.shownCount} OF ${result.totalCount} STREAMS · $order",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp),
                color = colors.textMuted,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }

    if (showSort) SmartSortSheet(state = state, onDismiss = { showSort = false })
    if (showFilters) SmartFiltersSheet(state = state, onDismiss = { showFilters = false })
    if (showBest && best != null) {
        SmartBestSheet(
            state = state,
            best = best,
            onPlay = { onPlay(best.stream) },
            onDismiss = { showBest = false },
        )
    }
}

@Composable
private fun SmartBarButton(
    icon: @Composable () -> Unit,
    label: String,
    highlighted: Boolean,
    trailing: (@Composable () -> Unit)?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.nuvio.colors
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .height(44.dp)
            .clip(shape)
            .background(if (highlighted) colors.accent.copy(alpha = 0.14f) else colors.surfaceCard)
            .border(1.dp, if (highlighted) colors.accent.copy(alpha = 0.55f) else colors.borderDefault, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        Box(contentAlignment = Alignment.Center) {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.material3.LocalContentColor provides colors.textPrimary,
            ) { icon() }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        trailing?.invoke()
    }
}

private data class SmartFilterChip(val label: String, val remove: () -> Unit)

private fun activeFilterChips(filters: SmartStreamFilters): List<SmartFilterChip> = buildList {
    SmartResolution.entries.reversed().filter { it in filters.resolutions }.forEach { value ->
        add(SmartFilterChip(value.label) { updateFilters { it.copy(resolutions = it.resolutions - value) } })
    }
    SmartHdrFilter.entries.filter { it in filters.hdr }.forEach { value ->
        add(SmartFilterChip(value.label) { updateFilters { it.copy(hdr = it.hdr - value) } })
    }
    SmartCodec.entries.filter { it in filters.codecs }.forEach { value ->
        add(SmartFilterChip(value.label) { updateFilters { it.copy(codecs = it.codecs - value) } })
    }
    SmartAudio.entries.filter { it in filters.audio }.forEach { value ->
        add(SmartFilterChip(value.label) { updateFilters { it.copy(audio = it.audio - value) } })
    }
    SmartSource.entries.reversed().filter { it in filters.sources }.forEach { value ->
        add(SmartFilterChip(value.label) { updateFilters { it.copy(sources = it.sources - value) } })
    }
    filters.maxSizeGb?.let { gb ->
        add(SmartFilterChip("Under $gb GB") { updateFilters { it.copy(maxSizeGb = null) } })
    }
    if (filters.instantOnly) add(SmartFilterChip("Instant only") { updateFilters { it.copy(instantOnly = false) } })
    if (!filters.hideLowQuality) {
        add(SmartFilterChip("Showing low quality") { updateFilters { it.copy(hideLowQuality = true) } })
    }
}

// ---------------------------------------------------------------------------------------------
// Sheets
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SmartSheet(
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.(close: () -> Unit) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val close: () -> Unit = {
        scope.launch { dismissNuvioBottomSheet(sheetState = sheetState, onDismiss = onDismiss) }
    }
    NuvioModalBottomSheet(onDismissRequest = close, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            content(close)
        }
    }
}

@Composable
private fun SheetTitle(text: String, subtitle: String? = null) {
    Column(modifier = Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.nuvio.colors.textPrimary,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.nuvio.colors.textMuted,
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp),
        color = MaterialTheme.nuvio.colors.textMuted,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

@Composable
private fun GroupCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.nuvio.colors.surfaceCard),
        content = content,
    )
}

@Composable
private fun GroupDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp)
            .height(1.dp)
            .background(MaterialTheme.nuvio.colors.borderSubtle),
    )
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val colors = MaterialTheme.nuvio.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = colors.textPrimary,
            )
            if (subtitle != null) {
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.onAccent,
                checkedTrackColor = colors.accent,
                uncheckedThumbColor = colors.textMuted,
                uncheckedTrackColor = colors.borderDefault,
            ),
        )
    }
}

@Composable
private fun <T> Segmented(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.nuvio.colors
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(11.dp))
            .background(colors.surfaceCard)
            .border(1.dp, colors.borderDefault, RoundedCornerShape(11.dp))
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (isSelected) colors.accent else Color.Transparent)
                    .clickable { onSelect(option) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label(option),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    ),
                    color = if (isSelected) colors.onAccent else colors.textSecondary,
                    maxLines = 1,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChipSection(
    title: String,
    options: List<T>,
    selected: Set<T>,
    label: (T) -> String,
    onToggle: (T) -> Unit,
) {
    val colors = MaterialTheme.nuvio.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(title)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            options.forEach { option ->
                val isSelected = option in selected
                Box(
                    modifier = Modifier
                        .height(36.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) colors.accent.copy(alpha = 0.14f) else colors.surfaceCard)
                        .border(
                            1.dp,
                            if (isSelected) colors.accent.copy(alpha = 0.6f) else colors.borderDefault,
                            CircleShape,
                        )
                        .clickable { onToggle(option) }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label(option),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        ),
                        color = if (isSelected) colors.textPrimary else colors.textSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: Boolean = false) {
    val colors = MaterialTheme.nuvio.colors
    Row(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.accent)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = colors.onAccent)
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = colors.onAccent,
        )
    }
}

@Composable
private fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.nuvio.colors
    Box(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, colors.borderDefault, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = colors.textPrimary,
        )
    }
}

@Composable
private fun SmartSortSheet(state: SmartStreamsState, onDismiss: () -> Unit) {
    val colors = MaterialTheme.nuvio.colors
    SmartSheet(onDismiss = onDismiss) { close ->
        SheetTitle("Sort streams")
        GroupCard {
            SmartSortMode.entries.forEachIndexed { index, mode ->
                if (index > 0) GroupDivider()
                val isSelected = mode == state.settings.sortMode
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 62.dp)
                        .background(if (isSelected) colors.accent.copy(alpha = 0.12f) else Color.Transparent)
                        .clickable {
                            SmartStreamSettingsRepository.update { it.copy(sortMode = mode) }
                            close()
                        }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = mode.label,
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.textPrimary,
                        )
                        Text(text = mode.description, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                    }
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .border(2.dp, if (isSelected) colors.accent else colors.borderStrong, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isSelected) {
                            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(colors.accent))
                        }
                    }
                }
            }
        }
        GroupCard {
            SwitchRow(
                title = "Use for every title",
                subtitle = "Remember this sort and your filters next time",
                checked = state.settings.rememberChoices,
                onCheckedChange = { checked -> SmartStreamSettingsRepository.update { it.copy(rememberChoices = checked) } },
            )
        }
    }
}

@Composable
private fun SmartFiltersSheet(state: SmartStreamsState, onDismiss: () -> Unit) {
    val filters = state.settings.filters
    SmartSheet(onDismiss = onDismiss) { close ->
        SheetTitle("Filters")
        ChipSection(
            title = "RESOLUTION",
            options = SmartResolution.entries.reversed(),
            selected = filters.resolutions,
            label = { it.label },
            onToggle = { value -> updateFilters { it.copy(resolutions = it.resolutions.toggle(value)) } },
        )
        ChipSection(
            title = "HDR",
            options = SmartHdrFilter.entries,
            selected = filters.hdr,
            label = { it.label },
            onToggle = { value -> updateFilters { it.copy(hdr = it.hdr.toggle(value)) } },
        )
        ChipSection(
            title = "VIDEO",
            options = listOf(SmartCodec.HEVC, SmartCodec.H264, SmartCodec.AV1),
            selected = filters.codecs,
            label = { it.label },
            onToggle = { value -> updateFilters { it.copy(codecs = it.codecs.toggle(value)) } },
        )
        ChipSection(
            title = "AUDIO",
            options = SmartAudio.entries,
            selected = filters.audio,
            label = { it.label },
            onToggle = { value -> updateFilters { it.copy(audio = it.audio.toggle(value)) } },
        )
        ChipSection(
            title = "SOURCE",
            options = listOf(SmartSource.REMUX, SmartSource.BLURAY, SmartSource.WEBDL, SmartSource.WEBRIP),
            selected = filters.sources,
            label = { it.label },
            onToggle = { value -> updateFilters { it.copy(sources = it.sources.toggle(value)) } },
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("FILE SIZE")
            Segmented(
                options = listOf<Int?>(null, 2, 5, 10, 20),
                selected = filters.maxSizeGb,
                label = { gb -> if (gb == null) "Any" else "< $gb GB" },
                onSelect = { gb -> updateFilters { it.copy(maxSizeGb = gb) } },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        GroupCard {
            SwitchRow(
                title = "Instant only",
                subtitle = "Streams that start with no waiting",
                checked = filters.instantOnly,
                onCheckedChange = { checked -> updateFilters { it.copy(instantOnly = checked) } },
            )
            GroupDivider()
            SwitchRow(
                title = "Hide low quality",
                subtitle = "CAM, telesync and screener copies",
                checked = filters.hideLowQuality,
                onCheckedChange = { checked -> updateFilters { it.copy(hideLowQuality = checked) } },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton(
                text = "Reset",
                onClick = { updateFilters { SmartStreamFilters() } },
                modifier = Modifier.weight(1f),
            )
            PrimaryButton(
                text = "Show ${state.result?.shownCount ?: 0} streams",
                onClick = close,
                modifier = Modifier.weight(2f),
            )
        }
    }
}

@Composable
private fun SmartBestSheet(
    state: SmartStreamsState,
    best: SmartPick,
    onPlay: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.nuvio.colors
    val device = state.device
    SmartSheet(onDismiss = onDismiss) { close ->
        val streamName = listOfNotNull(best.stream.addonName, best.stream.streamLabel.takeIf { it.isNotBlank() })
            .joinToString(" · ")
        SheetTitle("Best for this device", streamName)

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("THIS DEVICE")
            GroupCard {
                InfoRow("Screen", device.screenLabel)
                GroupDivider()
                InfoRow("Smooth video", device.videoLabel)
                GroupDivider()
                InfoRow("Connection", device.connectionLabel)
            }
        }

        if (best.reasons.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("WHY THIS STREAM")
                GroupCard {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        best.reasons.forEach { reason ->
                            Row(
                                modifier = Modifier.heightIn(min = 40.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(
                                    Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = colors.accent,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(text = reason, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
                            }
                        }
                    }
                }
            }
        }

        GroupCard {
            SettingSegmentRow(title = "Highest resolution") {
                Segmented(
                    options = SmartResolutionLimit.entries,
                    selected = state.settings.resolutionLimit,
                    label = { it.label },
                    onSelect = { value -> SmartStreamSettingsRepository.update { it.copy(resolutionLimit = value) } },
                    modifier = Modifier.width(180.dp),
                )
            }
            GroupDivider()
            SettingSegmentRow(title = "On mobile data") {
                Segmented(
                    options = SmartMobileData.entries,
                    selected = state.settings.mobileData,
                    label = { it.label },
                    onSelect = { value -> SmartStreamSettingsRepository.update { it.copy(mobileData = value) } },
                    modifier = Modifier.width(180.dp),
                )
            }
        }

        PrimaryButton(
            text = "Play this stream",
            icon = true,
            onClick = {
                close()
                onPlay()
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val colors = MaterialTheme.nuvio.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary, modifier = Modifier.weight(1f))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = colors.textPrimary,
        )
    }
}

@Composable
private fun SettingSegmentRow(title: String, control: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.nuvio.colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        control()
    }
}

// ---------------------------------------------------------------------------------------------
// Empty state and the merged, sorted list
// ---------------------------------------------------------------------------------------------

@Composable
internal fun SmartFiltersEmptyBlock(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.nuvio.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "No streams match your filters",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = colors.textPrimary,
        )
        Text(
            text = "Some addons don't say the quality in the stream name, so those can't be filtered.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textMuted,
        )
        SecondaryButton(
            text = "Clear filters",
            onClick = { updateFilters { SmartStreamFilters() } },
            modifier = Modifier.width(200.dp),
        )
    }
}

internal fun LazyListScope.smartSortedStreamSection(
    streams: List<StreamItem>,
    bestStream: StreamItem?,
    debridEnabled: Boolean,
    appendInstantServiceToDefaultName: Boolean,
    showFileSizeBadges: Boolean,
    badgePlacement: StreamBadgePlacement,
    torrentNotSupportedText: String,
    onStreamSelected: (stream: StreamItem, resumePositionMs: Long?, resumeProgressFraction: Float?) -> Unit,
    onStreamLongPress: (StreamItem) -> Unit,
    resumePositionMs: Long?,
    resumeProgressFraction: Float?,
) {
    itemsIndexed(
        items = streams,
        key = { index, stream ->
            streamCardRenderKey(sectionKey = "smart", sourceIndex = 0, itemIndex = index, stream = stream)
        },
    ) { _, stream ->
        val isSelectable = stream.isSelectableForPlayback(debridEnabled)
        val isUnsupportedTorrentStream =
            stream.needsLocalDebridResolve &&
                !AppFeaturePolicy.p2pEnabled &&
                !(debridEnabled && stream.isAddonDebridCandidate)
        StreamCard(
            stream = stream,
            enabled = isSelectable || isUnsupportedTorrentStream,
            appendInstantServiceToDefaultName = appendInstantServiceToDefaultName,
            showFileSizeBadges = showFileSizeBadges,
            // One merged list mixes addons, so each card says which addon it comes from.
            showAddonLogo = true,
            badgePlacement = badgePlacement,
            isCurrent = stream == bestStream,
            currentLabel = if (stream == bestStream) "Best match" else null,
            onClick = {
                if (isSelectable) {
                    onStreamSelected(stream, resumePositionMs, resumeProgressFraction)
                } else if (isUnsupportedTorrentStream) {
                    NuvioToastController.show(torrentNotSupportedText)
                }
            },
            onLongClick = {
                if (stream.playableDirectUrl != null || stream.shouldOpenExternally || stream.isAddonDebridCandidate) {
                    onStreamLongPress(stream)
                }
            },
        )
        Spacer(modifier = Modifier.height(10.dp))
    }
}
