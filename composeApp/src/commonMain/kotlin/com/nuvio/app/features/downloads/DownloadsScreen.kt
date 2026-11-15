package com.nuvio.app.features.downloads

import com.nuvio.app.features.details.seasonLabel
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.i18n.localizedByteUnit
import com.nuvio.app.core.ui.NuvioScreen
import com.nuvio.app.core.ui.NuvioScreenHeader
import com.nuvio.app.core.ui.NuvioStatusModal
import com.nuvio.app.core.ui.NuvioToastController
import com.nuvio.app.core.ui.nuvio
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun DownloadsScreen(
    onBack: () -> Unit,
    onOpenDownload: (DownloadItem) -> Unit,
    initialShowId: String? = null,
    onNavigateToShow: ((showId: String, title: String) -> Unit)? = null,
    onNavigateToMovie: ((type: String, movieId: String, title: String) -> Unit)? = null,
    onBackFromShow: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
) {
    val uiState by remember {
        DownloadsRepository.ensureLoaded()
        DownloadsRepository.uiState
    }.collectAsStateWithLifecycle()
    LaunchedEffect(uiState.completedItems.size) {
        DownloadsRepository.markCompletedSeen()
    }

    var selectedShowId by rememberSaveable(initialShowId) { mutableStateOf(initialShowId) }
    var downloadPendingDeletionId by rememberSaveable { mutableStateOf<String?>(null) }

    val allSeriesEpisodes = remember(uiState.items) {
        uiState.items
            .filter { it.isEpisode }
            .sortedForSeriesDownloads()
    }

    val selectedShowTitle = remember(selectedShowId, allSeriesEpisodes) {
        selectedShowId?.let { showId ->
            allSeriesEpisodes.firstOrNull { it.parentMetaId == showId }?.title
        }
    }

    val tokens = MaterialTheme.nuvio

    NuvioScreen {
        stickyHeader {
            NuvioScreenHeader(
                title = if (selectedShowId == null) {
                    stringResource(Res.string.compose_settings_root_downloads_title)
                } else {
                    selectedShowTitle ?: stringResource(Res.string.downloads_show_downloads)
                },
                onBack = {
                    if (selectedShowId != null) {
                        onBackFromShow?.invoke() ?: run { selectedShowId = null }
                    } else {
                        onBack()
                    }
                },
                actions = {
                    OpenDownloadsFolderButton(tint = tokens.colors.textPrimary)
                    if (selectedShowId == null && onOpenSettings != null) {
                        IconButton(onClick = onOpenSettings) {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = stringResource(Res.string.compose_settings_page_root),
                                tint = tokens.colors.textPrimary,
                            )
                        }
                    }
                },
            )
        }

        if (selectedShowId == null) {
            downloadsRootContent(
                uiState = uiState,
                onOpenDownload = onOpenDownload,
                onOpenShow = { showId, title ->
                    if (onNavigateToShow != null) {
                        onNavigateToShow(showId, title)
                    } else {
                        selectedShowId = showId
                    }
                },
                onOpenMovie = onNavigateToMovie,
                onDeleteDownload = { downloadPendingDeletionId = it },
            )
        } else {
            downloadsShowContent(
                showId = selectedShowId.orEmpty(),
                episodes = allSeriesEpisodes,
                onOpenDownload = onOpenDownload,
                onDeleteDownload = { downloadPendingDeletionId = it },
            )
        }
    }

    val pendingDeletionId = downloadPendingDeletionId
    if (pendingDeletionId != null) {
        DownloadDeleteConfirmation(
            count = 1,
            onConfirm = {
                DownloadsRepository.cancelDownload(pendingDeletionId)
                downloadPendingDeletionId = null
            },
            onDismiss = { downloadPendingDeletionId = null },
        )
    }
}

private fun LazyListScope.downloadsRootContent(
    uiState: DownloadsUiState,
    onOpenDownload: (DownloadItem) -> Unit,
    onOpenShow: (showId: String, title: String) -> Unit,
    onOpenMovie: ((type: String, movieId: String, title: String) -> Unit)?,
    onDeleteDownload: (String) -> Unit,
) {
    val activeItems = uiState.activeItems
    val activeMovies = activeItems.filterNot(DownloadItem::isEpisode)
    val activeShows = activeItems.groupedByShow()
    val completedMovies = uiState.completedItems.filterNot(DownloadItem::isEpisode)
    val completedShows = uiState.completedItems.groupedByShow()

    val movieRow: @Composable (DownloadItem) -> Unit = { item ->
        DownloadRow(
            item = item,
            title = item.title,
            subtitle = null,
            onPlay = { onOpenDownload(item) },
            onDelete = { onDeleteDownload(item.id) },
            onClick = onOpenMovie?.let { open -> { open(item.parentMetaType, item.contentId, item.title) } },
        )
    }

    if (activeItems.isNotEmpty()) {
        item {
            val hasActive = activeItems.statusCounts().hasActive
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionTitle(stringResource(Res.string.downloads_section_active), padded = false)
                Text(
                    text = stringResource(
                        if (hasActive) Res.string.downloads_action_pause_all else Res.string.downloads_action_resume_all,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clickable {
                            if (hasActive) {
                                DownloadsRepository.pauseActiveDownloads()
                            } else {
                                DownloadsRepository.resumeOrRetryAllDownloads()
                            }
                        }
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
        }
        items(items = activeMovies, key = { it.id }) { item -> movieRow(item) }
        items(
            items = activeShows,
            key = { (showId, _) -> "active_$showId" },
        ) { (showId, episodes) ->
            val title = episodes.first().title
            val allShowEpisodes = remember(uiState.items, showId) {
                uiState.items.filter { it.isEpisode && it.parentMetaId == showId }
            }
            val counts = episodes.statusCounts()
            val seriesStats = remember(allShowEpisodes) { calculateSeriesDownloadStats(allShowEpisodes) }
            val activeStatusText = downloadActivitySummary(counts.copy(paused = 0, failed = 0))
                ?: stringResource(Res.string.downloads_episode_count, episodes.size)
            val subtitle = if (seriesStats.totalBytes > 0L) {
                "$activeStatusText • ${seriesStats.sizeStatsText()}"
            } else {
                activeStatusText
            }

            ShowDownloadRow(
                title = title,
                subtitle = subtitle,
                onClick = { onOpenShow(showId, title) },
                action = {
                    IconButton(
                        onClick = { DownloadsRepository.toggleShowPause(showId) },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = if (counts.hasActive) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = stringResource(
                                if (counts.hasActive) Res.string.downloads_action_pause_series else Res.string.downloads_action_resume_series,
                            ),
                            tint = if (counts.hasActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                },
            )
        }
    }

    if (completedMovies.isNotEmpty()) {
        item {
            SectionTitle(stringResource(Res.string.downloads_section_movies))
        }
        items(items = completedMovies, key = { it.id }) { item -> movieRow(item) }
    }

    if (completedShows.isNotEmpty()) {
        item {
            SectionTitle(stringResource(Res.string.downloads_section_shows))
        }
        items(
            items = completedShows,
            key = { (showId, _) -> showId },
        ) { (showId, episodes) ->
            val title = episodes.first().title
            val seriesStats = remember(episodes) { calculateSeriesDownloadStats(episodes) }
            val episodeCountText = stringResource(Res.string.downloads_episode_count, episodes.size)
            val sizeText = if (episodes.size > 1) {
                seriesStats.sizeStatsText()
            } else {
                stringResource(Res.string.downloads_series_size_total, formatBytes(seriesStats.totalBytes))
            }

            ShowDownloadRow(
                title = title,
                subtitle = "$episodeCountText • $sizeText",
                onClick = { onOpenShow(showId, title) },
                action = {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp),
                    )
                },
            )
        }
    }

    if (uiState.items.isEmpty()) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 40.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(Res.string.downloads_empty_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    item(key = "downloads_device_storage_bar") {
        DeviceStorageBar(
            appDownloadsBytes = uiState.items.sumOf { it.downloadedBytes },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
        )
    }
}

/** Episode downloads grouped by show and sorted by show title, each group in episode order. */
private fun List<DownloadItem>.groupedByShow(): List<Pair<String, List<DownloadItem>>> =
    filter(DownloadItem::isEpisode)
        .groupBy { it.parentMetaId }
        .map { (showId, episodes) -> showId to episodes.sortedForSeriesDownloads() }
        .sortedBy { (_, episodes) -> episodes.first().title.lowercase() }

private fun LazyListScope.downloadsShowContent(
    showId: String,
    episodes: List<DownloadItem>,
    onOpenDownload: (DownloadItem) -> Unit,
    onDeleteDownload: (String) -> Unit,
) {
    val showEpisodes = episodes
        .filter { it.parentMetaId == showId }
        .sortedForSeriesDownloads()

    val seasons = showEpisodes
        .groupBy { it.seasonNumber ?: 0 }
        .toList()
        .sortedWith(
            compareBy<Pair<Int, List<DownloadItem>>> { (season, _) ->
                if (season == 0) 0 else 1
            }.thenBy { (season, _) -> if (season == 0) 0 else season },
        )

    if (seasons.isEmpty()) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 40.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(Res.string.downloads_empty_episodes),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }

    seasons.forEach { (seasonNumber, entries) ->
        item {
            SectionTitle(seasonLabel(seasonNumber))
        }

        val sortedEpisodes = entries.sortedForSeriesDownloads()

        items(
            items = sortedEpisodes,
            key = { it.id },
        ) { item ->
            val title = item.displayTitle()
            DownloadRow(
                item = item,
                title = title,
                subtitle = downloadDisplaySubtitle(item = item, displayTitle = title),
                onPlay = { onOpenDownload(item) },
                onDelete = { onDeleteDownload(item.id) },
            )
        }
    }
}

/**
 * One download (a movie or an episode) with its status, progress and actions.
 *
 * @param onClick opens the item; when null the row is only clickable once the file is playable.
 */
@Composable
private fun DownloadRow(
    item: DownloadItem,
    title: String,
    subtitle: String?,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
    onClick: (() -> Unit)? = null,
) {
    val isQueued = item.status == DownloadStatus.Queued
    val progress = animatedDownloadProgress(item.progressFraction)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .defaultMinSize(minHeight = 72.dp)
            .clickable(enabled = onClick != null || item.isPlayable, onClick = onClick ?: onPlay),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (isQueued) {
                            Icon(
                                imageVector = Icons.Rounded.Schedule,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Text(
                            text = downloadStatusText(item),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isQueued) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                DownloadItemActions(item = item, onPlay = onPlay, onDelete = onDelete)
            }

            if (item.status == DownloadStatus.Downloading) {
                Spacer(modifier = Modifier.height(8.dp))
                DownloadProgressBar(
                    progress = progress.takeIf { item.knownProgress != null },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun ShowDownloadRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    action: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .defaultMinSize(minHeight = 72.dp)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            action()
        }
    }
}

@Composable
private fun downloadDisplaySubtitle(
    item: DownloadItem,
    displayTitle: String,
): String {
    val seasonNumber = item.seasonNumber
    val episodeNumber = item.episodeNumber
    if (seasonNumber == null || episodeNumber == null) {
        return item.displaySubtitle
    }

    val episodeCode = stringResource(
        Res.string.compose_player_episode_code_full,
        seasonNumber,
        episodeNumber,
    )
    return listOf(
        episodeCode,
        item.episodeTitle?.trim().orEmpty().takeIf { it.isNotBlank() && it != displayTitle },
        item.title.trim().takeIf { it.isNotBlank() && it != displayTitle },
    ).filterNotNull().joinToString(" • ")
}

@Composable
private fun SectionTitle(title: String, padded: Boolean = true) {
    Text(
        text = title,
        modifier = if (padded) Modifier.padding(horizontal = 14.dp, vertical = 8.dp) else Modifier,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
internal fun downloadStatusText(item: DownloadItem): String {
    val size = if (item.totalBytes != null && item.totalBytes > 0L) {
        "${formatBytes(item.downloadedBytes)} / ${formatBytes(item.totalBytes)}"
    } else {
        formatBytes(item.downloadedBytes)
    }

    return when (item.status) {
        DownloadStatus.Downloading -> stringResource(Res.string.downloads_status_downloading, size)
        DownloadStatus.Queued -> stringResource(Res.string.downloads_status_queued)
        DownloadStatus.Paused -> stringResource(Res.string.downloads_status_paused, size)
        DownloadStatus.Completed -> stringResource(
            Res.string.downloads_status_completed,
            formatBytes(item.totalBytes?.takeIf { it > 0L } ?: item.downloadedBytes),
        )
        DownloadStatus.Failed -> item.errorMessage ?: stringResource(Res.string.downloads_status_failed)
    }
}

/** "2 downloading • 1 queued"-style summary of the non-zero counts, or null when all are zero. */
@Composable
internal fun downloadActivitySummary(counts: DownloadStatusCounts): String? = listOf(
    counts.downloading to Res.string.downloads_summary_downloading,
    counts.queued to Res.string.downloads_summary_queued,
    counts.paused to Res.string.downloads_summary_paused,
    counts.failed to Res.string.downloads_summary_failed,
).filter { (count, _) -> count > 0 }
    .map { (count, label) -> stringResource(label, count) }
    .joinToString(" • ")
    .ifEmpty { null }

@Composable
internal fun episodeCountText(count: Int): String =
    pluralStringResource(Res.plurals.downloads_episodes_count, count, count)

@Composable
private fun SeriesDownloadStats.sizeStatsText(): String =
    stringResource(
        Res.string.downloads_series_size_stats,
        formatBytes(totalBytes),
        formatBytes(avgBytes),
        formatBytes(medianBytes),
    )
