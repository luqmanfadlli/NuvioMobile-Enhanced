package com.nuvio.app.features.downloads

import com.nuvio.app.features.details.seasonLabel
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.DialogButton
import com.nuvio.app.core.ui.DialogButtonStyle
import com.nuvio.app.core.ui.DialogButtons
import com.nuvio.app.core.ui.DialogSurface
import com.nuvio.app.core.ui.NuvioStatusModal
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

private enum class DownloadsDialogFilter {
    ActiveAndQueued,
    All,
}

private val statusDisplayOrder = listOf(
    DownloadStatus.Downloading,
    DownloadStatus.Queued,
    DownloadStatus.Paused,
    DownloadStatus.Failed,
    DownloadStatus.Completed,
)

/** Lists running, queued, paused and failed downloads, grouped by show and season, with bulk actions. */
@Composable
fun DownloadsQueueDialog(
    onDismissRequest: () -> Unit,
    onOpenDownload: ((DownloadItem) -> Unit)? = null,
) {
    val uiState by remember {
        DownloadsRepository.ensureLoaded()
        DownloadsRepository.uiState
    }.collectAsStateWithLifecycle()

    var filter by remember { mutableStateOf(DownloadsDialogFilter.ActiveAndQueued) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var pendingDeleteIds by remember { mutableStateOf<List<String>?>(null) }

    val displayedItems = remember(uiState.items, filter) {
        uiState.items
            .filter { filter == DownloadsDialogFilter.All || it.status != DownloadStatus.Completed }
            .sortedWith(compareBy<DownloadItem> { statusDisplayOrder.indexOf(it.status) }.thenBy { it.createdAtEpochMs })
    }
    val displayedIds = remember(displayedItems) { displayedItems.map { it.id }.toSet() }
    // Downloads can finish or be deleted while selected, so only count those still listed.
    val visibleSelectedIds = selectedIds intersect displayedIds
    val counts = displayedItems.statusCounts()

    DialogSurface(
        onDismissRequest = onDismissRequest,
        modifier = Modifier.padding(16.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            DialogHeader(
                subtitle = when {
                    displayedItems.isEmpty() -> stringResource(Res.string.downloads_view_empty)
                    else -> downloadActivitySummary(counts)
                        ?: stringResource(Res.string.downloads_summary_completed, displayedItems.size)
                },
                isDownloading = counts.downloading > 0,
                onClose = onDismissRequest,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterPill(
                    label = stringResource(Res.string.downloads_section_active_queued),
                    selected = filter == DownloadsDialogFilter.ActiveAndQueued,
                    onClick = { filter = DownloadsDialogFilter.ActiveAndQueued },
                )
                FilterPill(
                    label = stringResource(Res.string.downloads_filter_all),
                    selected = filter == DownloadsDialogFilter.All,
                    onClick = { filter = DownloadsDialogFilter.All },
                )
            }

            if (visibleSelectedIds.isNotEmpty()) {
                SelectionActionsBar(
                    selected = displayedItems.filter { it.id in visibleSelectedIds },
                    onDelete = { pendingDeleteIds = visibleSelectedIds.toList() },
                    onClearSelection = { selectedIds = emptySet() },
                )
            } else if (displayedItems.isNotEmpty()) {
                QuickActionsRow(
                    counts = counts,
                    onSelectAll = { selectedIds = displayedIds },
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            if (displayedItems.isEmpty()) {
                Text(
                    text = stringResource(
                        if (filter == DownloadsDialogFilter.ActiveAndQueued) {
                            Res.string.downloads_empty_active
                        } else {
                            Res.string.downloads_empty_title
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 32.dp),
                )
            } else {
                DownloadsList(
                    items = displayedItems,
                    selectedIds = visibleSelectedIds,
                    onSelectionChange = { ids, selected ->
                        selectedIds = if (selected) selectedIds + ids else selectedIds - ids
                    },
                    onOpenDownload = onOpenDownload,
                    onDelete = { pendingDeleteIds = listOf(it) },
                )
            }

            DialogButtons {
                DialogButton(
                    text = stringResource(Res.string.action_close),
                    onClick = onDismissRequest,
                    style = DialogButtonStyle.Secondary,
                )
            }
        }
    }

    val toDelete = pendingDeleteIds
    if (!toDelete.isNullOrEmpty()) {
        DownloadDeleteConfirmation(
            count = toDelete.size,
            onConfirm = {
                DownloadsRepository.cancelDownloads(toDelete)
                selectedIds = selectedIds - toDelete.toSet()
                pendingDeleteIds = null
            },
            onDismiss = { pendingDeleteIds = null },
        )
    }
}

@Composable
private fun DialogHeader(
    subtitle: String,
    isDownloading: Boolean,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(Res.string.downloads_show_active_queued),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (isDownloading) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(Res.string.action_close),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SelectionActionsBar(
    selected: List<DownloadItem>,
    onDelete: () -> Unit,
    onClearSelection: () -> Unit,
) {
    val toPause = selected.filter { it.isActive }
    val toResume = selected.filter { it.isResumable }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(Res.string.downloads_selected_count, selected.size),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (toPause.isNotEmpty()) {
                    ActionPill(
                        icon = Icons.Rounded.Pause,
                        label = stringResource(Res.string.downloads_action_pause_count, toPause.size),
                        onClick = { DownloadsRepository.pauseDownloads(toPause.map { it.id }) },
                    )
                }
                if (toResume.isNotEmpty()) {
                    ActionPill(
                        icon = Icons.Rounded.PlayArrow,
                        label = stringResource(Res.string.downloads_action_resume_count, toResume.size),
                        onClick = { DownloadsRepository.resumeDownloads(toResume.map { it.id }) },
                    )
                }
                ActionPill(
                    icon = Icons.Rounded.Delete,
                    label = stringResource(Res.string.action_delete),
                    isDestructive = true,
                    onClick = onDelete,
                )
                ActionPill(
                    icon = Icons.Rounded.Close,
                    label = stringResource(Res.string.downloads_action_deselect),
                    onClick = onClearSelection,
                )
            }
        }
    }
}

@Composable
private fun QuickActionsRow(
    counts: DownloadStatusCounts,
    onSelectAll: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ActionPill(
            icon = Icons.Rounded.Check,
            label = stringResource(Res.string.downloads_select_all),
            modifier = Modifier.weight(1f),
            onClick = onSelectAll,
        )
        if (counts.hasActive) {
            ActionPill(
                icon = Icons.Rounded.Pause,
                label = stringResource(Res.string.downloads_action_pause_all),
                modifier = Modifier.weight(1f),
                onClick = DownloadsRepository::pauseActiveDownloads,
            )
        }
        if (counts.paused > 0 || counts.failed > 0) {
            ActionPill(
                icon = Icons.Rounded.PlayArrow,
                label = stringResource(Res.string.downloads_action_resume_all),
                modifier = Modifier.weight(1f),
                onClick = DownloadsRepository::resumeOrRetryAllDownloads,
            )
        }
    }
}

/** Shows grouped into collapsible season folders, then movies. */
@Composable
private fun DownloadsList(
    items: List<DownloadItem>,
    selectedIds: Set<String>,
    onSelectionChange: (ids: Set<String>, selected: Boolean) -> Unit,
    onOpenDownload: ((DownloadItem) -> Unit)?,
    onDelete: (downloadId: String) -> Unit,
) {
    val collapsedFolders = remember { mutableStateMapOf<String, Boolean>() }
    val shows = remember(items) {
        items.filter { it.isEpisode }
            .groupBy { it.parentMetaId }
            .mapValues { (_, episodes) -> episodes.groupBy { it.seasonNumber ?: 0 }.toList().sortedBy { it.first }.toMap() }
    }
    val movies = remember(items) { items.filterNot { it.isEpisode } }
    val showFallbackTitle = stringResource(Res.string.downloads_show_fallback_title)
    val moviesTitle = stringResource(Res.string.downloads_section_movies)

    val queueRow: @Composable (DownloadItem) -> Unit = { item ->
        ActiveDownloadQueueRow(
            item = item,
            isSelected = item.id in selectedIds,
            onSelectedChange = { selected -> onSelectionChange(setOf(item.id), selected) },
            onPlay = { onOpenDownload?.invoke(item) },
            onDelete = { onDelete(item.id) },
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 420.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        shows.forEach { (showId, seasons) ->
            val showEpisodes = seasons.values.flatten()
            item(key = "show_$showId") {
                GroupHeader(
                    title = showEpisodes.first().title.ifBlank { showFallbackTitle },
                    trailing = episodeCountText(showEpisodes.size),
                    groupIds = showEpisodes.mapTo(mutableSetOf()) { it.id },
                    selectedIds = selectedIds,
                    onSelectionChange = onSelectionChange,
                )
            }
            seasons.forEach { (season, episodes) ->
                val folderKey = "folder_${showId}_$season"
                val isCollapsed = collapsedFolders[folderKey] == true
                item(key = folderKey) {
                    SeasonFolderHeader(
                        season = season,
                        episodes = episodes,
                        isCollapsed = isCollapsed,
                        onToggleCollapsed = { collapsedFolders[folderKey] = !isCollapsed },
                        selectedIds = selectedIds,
                        onSelectionChange = onSelectionChange,
                    )
                }
                if (!isCollapsed) {
                    items(items = episodes, key = { it.id }) { episode ->
                        Box(modifier = Modifier.padding(start = 24.dp)) { queueRow(episode) }
                    }
                }
            }
        }
        if (movies.isNotEmpty()) {
            item(key = "movies_header") {
                Text(
                    text = moviesTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
            items(items = movies, key = { it.id }) { movie -> queueRow(movie) }
        }
    }
}

/** A checkbox that selects or clears a whole group, showing a dash when only part is selected. */
@Composable
private fun GroupSelectionCheckbox(
    groupIds: Set<String>,
    selectedIds: Set<String>,
    onSelectionChange: (ids: Set<String>, selected: Boolean) -> Unit,
) {
    val selectedCount = groupIds.count { it in selectedIds }
    val state = when (selectedCount) {
        0 -> ToggleableState.Off
        groupIds.size -> ToggleableState.On
        else -> ToggleableState.Indeterminate
    }
    TriStateCheckbox(
        state = state,
        onClick = { onSelectionChange(groupIds, state != ToggleableState.On) },
    )
}

@Composable
private fun GroupHeader(
    title: String,
    trailing: String,
    groupIds: Set<String>,
    selectedIds: Set<String>,
    onSelectionChange: (ids: Set<String>, selected: Boolean) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            GroupSelectionCheckbox(groupIds, selectedIds, onSelectionChange)
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = trailing,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SeasonFolderHeader(
    season: Int,
    episodes: List<DownloadItem>,
    isCollapsed: Boolean,
    onToggleCollapsed: () -> Unit,
    selectedIds: Set<String>,
    onSelectionChange: (ids: Set<String>, selected: Boolean) -> Unit,
) {
    val counts = episodes.statusCounts()
    val status = downloadActivitySummary(counts)?.let { "$it • ${episodeCountText(episodes.size)}" }
        ?: stringResource(Res.string.downloads_summary_completed, episodes.size)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onToggleCollapsed),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            GroupSelectionCheckbox(episodes.mapTo(mutableSetOf()) { it.id }, selectedIds, onSelectionChange)
            Icon(
                imageVector = Icons.Rounded.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Text(
                    text = seasonLabel(season),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = status,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = if (counts.downloading > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = if (isCollapsed) Icons.Rounded.KeyboardArrowDown else Icons.Rounded.KeyboardArrowUp,
                contentDescription = stringResource(if (isCollapsed) Res.string.action_expand else Res.string.action_collapse),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun FilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun ActionPill(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDestructive: Boolean = false,
) {
    val contentColor = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (isDestructive) {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = if (isDestructive) contentColor else MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = contentColor,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ActiveDownloadQueueRow(
    item: DownloadItem,
    isSelected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
) {
    val progress = animatedDownloadProgress(item.progressFraction)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onSelectedChange(!isSelected) },
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = onSelectedChange,
                    modifier = Modifier.size(24.dp),
                )
                QueueRowThumbnail(item = item, progress = progress)
                QueueRowDetails(item = item, modifier = Modifier.weight(1f))
                DownloadItemActions(
                    item = item,
                    onPlay = onPlay,
                    onDelete = onDelete,
                    buttonSize = 32.dp,
                    iconSize = 18.dp,
                )
            }
            if (item.status == DownloadStatus.Downloading) {
                DownloadProgressBar(
                    progress = progress.takeIf { item.knownProgress != null },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp)),
                )
            }
        }
    }
}

@Composable
private fun QueueRowThumbnail(item: DownloadItem, progress: Float) {
    val showsProgress = item.status == DownloadStatus.Downloading || item.status == DownloadStatus.Paused
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center,
    ) {
        DownloadRevealImage(
            imageUrl = (item.episodeThumbnail ?: item.poster)?.takeIf { it.isNotBlank() },
            contentDescription = item.title,
            segmentProgresses = listOf(if (showsProgress) item.progressFraction else 0f),
            dimAlpha = if (item.status == DownloadStatus.Paused) 0.5f else 0.35f,
            modifier = Modifier.fillMaxSize(),
            placeholder = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (item.status == DownloadStatus.Queued) Icons.Rounded.Schedule else Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            },
        )
        if (showsProgress) {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = Color.Black.copy(alpha = 0.72f),
            ) {
                Text(
                    text = formatDownloadPercent(progress),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, fontWeight = FontWeight.Bold),
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp),
                )
            }
        }
    }
}

@Composable
private fun QueueRowDetails(item: DownloadItem, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(1.dp)) {
        val title = if (item.isEpisode) {
            localizedEpisodeLabel(item.seasonNumber ?: 0, item.episodeNumber ?: 0, item.displayTitle())
        } else {
            item.displayTitle()
        }
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val source = item.streamTitle.ifBlank { item.providerName }
        if (source.isNotBlank()) {
            Text(
                text = source,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (item.status == DownloadStatus.Queued) {
                Icon(
                    imageVector = Icons.Rounded.Schedule,
                    contentDescription = null,
                    modifier = Modifier.size(11.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = downloadStatusText(item),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = when (item.status) {
                    DownloadStatus.Downloading, DownloadStatus.Queued -> MaterialTheme.colorScheme.primary
                    DownloadStatus.Failed -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
