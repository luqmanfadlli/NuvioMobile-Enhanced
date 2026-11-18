package com.nuvio.app.features.details.components

import com.nuvio.app.features.details.seasonLabel
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.DialogButton
import com.nuvio.app.core.ui.DialogButtonStyle
import com.nuvio.app.core.ui.DialogButtons
import com.nuvio.app.core.ui.DialogSurface
import com.nuvio.app.features.debrid.DebridStreamMetadata
import com.nuvio.app.features.debrid.DebridStreamPreferences
import com.nuvio.app.features.details.MetaVideo
import com.nuvio.app.features.downloads.BatchDialogStep
import com.nuvio.app.features.downloads.DownloadsRepository
import com.nuvio.app.features.downloads.EpisodePlanStatus
import com.nuvio.app.features.downloads.PlannedEpisodeItem
import com.nuvio.app.features.downloads.BatchDownloadCoordinator
import com.nuvio.app.features.downloads.BatchDownloadPlan
import com.nuvio.app.features.downloads.batchEpisodeKey
import com.nuvio.app.features.downloads.batchKey
import com.nuvio.app.features.downloads.localizedEpisodeLabel
import com.nuvio.app.features.downloads.formatBytes
import com.nuvio.app.features.downloads.qualityBadge
import com.nuvio.app.features.downloads.isBatchEpisodeUnavailable
import com.nuvio.app.features.downloads.isBatchEpisodeDownloaded
import com.nuvio.app.features.streams.StreamSourcePrefillSelector
import com.nuvio.app.features.streams.StreamItem
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun BatchDownloadDialog(
    plan: BatchDownloadPlan,
    isEpisodeWatched: (MetaVideo) -> Boolean = { false },
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    var overrideTargetItem by remember { mutableStateOf<PlannedEpisodeItem?>(null) }

    when (val step = plan.step) {
        is BatchDialogStep.EpisodeSelection -> {
            EpisodeSelectionDialog(
                plan = plan,
                isEpisodeWatched = isEpisodeWatched,
                onDismissRequest = onDismissRequest,
            )
        }
        is BatchDialogStep.SourceSelection -> {
            SourceSelectionDialog(
                plan = plan,
                seasonIndex = step.seasonIndex,
                onDismissRequest = onDismissRequest,
                onConfirm = onConfirm,
                onOverrideClicked = { overrideTargetItem = it },
            )
        }
    }

    val overrideItem = overrideTargetItem
    if (overrideItem != null) {
        val currentSeason = when (val step = plan.step) {
            is BatchDialogStep.SourceSelection -> plan.seasonsToSource[step.seasonIndex]
            else -> plan.visibleSeason
        }
        StreamOverrideDialog(
            target = overrideItem,
            onSelect = { stream ->
                BatchDownloadCoordinator.overrideEpisodeStream(
                    season = currentSeason,
                    episode = overrideItem.episode,
                    stream = stream,
                )
                overrideTargetItem = null
            },
            onDismiss = { overrideTargetItem = null },
        )
    }
}

@Composable
private fun EpisodeSelectionDialog(
    plan: BatchDownloadPlan,
    isEpisodeWatched: (MetaVideo) -> Boolean,
    onDismissRequest: () -> Unit,
) {
    DialogSurface(
        onDismissRequest = onDismissRequest,
        title = plan.meta.name,
    ) {
        val downloadsUiState by remember {
            DownloadsRepository.ensureLoaded()
            DownloadsRepository.uiState
        }.collectAsStateWithLifecycle()
        val currentDownloads = downloadsUiState.items

        fun isUnavailable(episode: MetaVideo, season: Int): Boolean =
            isBatchEpisodeUnavailable(currentDownloads, plan.meta.id, episode, season)

        fun isDownloaded(episode: MetaVideo, season: Int): Boolean =
            isBatchEpisodeDownloaded(currentDownloads, plan.meta.id, episode, season)

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (plan.availableSeasons.size > 1) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(plan.availableSeasons) { seasonNumber ->
                        val seasonLabel = seasonLabel(seasonNumber)
                        val countInSeason = plan.episodesBySeason[seasonNumber].orEmpty().count { episode ->
                            episode.batchKey(seasonNumber) in plan.selectedEpisodeKeys
                        }
                        val chipText = if (countInSeason > 0) {
                            stringResource(Res.string.season_download_season_count, seasonLabel, countInSeason)
                        } else seasonLabel
                        val isSelectedSeason = plan.visibleSeason == seasonNumber

                        FilterChip(
                            selected = isSelectedSeason,
                            onClick = { BatchDownloadCoordinator.showSeason(seasonNumber) },
                            label = { Text(chipText, style = MaterialTheme.typography.labelMedium) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        )
                    }
                }
            }
            val currentSeasonEpisodes = plan.episodesBySeason[plan.visibleSeason].orEmpty()
            val currentSeasonLabel = seasonLabel(plan.visibleSeason)

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = currentSeasonLabel,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f, fill = false),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SelectionTextButton(Res.string.downloads_select_unwatched) {
                        BatchDownloadCoordinator.selectUnwatchedForSeason(
                            season = plan.visibleSeason,
                            isEpisodeWatched = isEpisodeWatched,
                        )
                    }
                    SelectionTextButton(Res.string.downloads_select_all) {
                        BatchDownloadCoordinator.selectAllForSeason(plan.visibleSeason)
                    }
                    SelectionTextButton(Res.string.downloads_deselect_all) {
                        BatchDownloadCoordinator.deselectAllForSeason(plan.visibleSeason)
                    }
                }
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 260.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(
                    items = currentSeasonEpisodes,
                    key = { it.id.ifBlank { "${it.season}_${it.episode}" } },
                ) { episode ->
                    val episodeNumber = episode.episode ?: 0
                    val key = episode.batchKey(plan.visibleSeason)
                    val isChecked = key in plan.selectedEpisodeKeys
                    val unavailable = isUnavailable(episode, plan.visibleSeason)
                    val completed = isDownloaded(episode, plan.visibleSeason)
                    val watched = isEpisodeWatched(episode)

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !unavailable) {
                                BatchDownloadCoordinator.toggleEpisodeSelection(plan.visibleSeason, episodeNumber)
                            },
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isChecked) 0.5f else 0.25f),
                        border = if (isChecked) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)) else null,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = if (unavailable) true else isChecked,
                                enabled = !unavailable,
                                onCheckedChange = {
                                    BatchDownloadCoordinator.toggleEpisodeSelection(plan.visibleSeason, episodeNumber)
                                },
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                val episodeTitle = episode.title?.takeIf { it.isNotBlank() } ?: stringResource(Res.string.downloads_episode_fallback_title, episodeNumber)
                                Text(
                                    text = localizedEpisodeLabel(plan.visibleSeason, episodeNumber, episodeTitle),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = if (unavailable) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                val tags = mutableListOf<String>()
                                if (completed) tags.add(stringResource(Res.string.season_download_already_downloaded))
                                else if (unavailable) tags.add(stringResource(Res.string.downloads_status_already_in_progress))
                                if (watched) tags.add(stringResource(Res.string.episodes_cd_watched))
                                if (tags.isNotEmpty()) {
                                    Text(
                                        text = tags.joinToString(" • "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (completed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            val selectedCount = plan.totalSelectedCount
            val seasonsCount = plan.availableSeasons.count(plan::isSeasonSelected)
            val summaryText = if (seasonsCount > 1) {
                stringResource(Res.string.downloads_episodes_selected_multi_season, selectedCount, seasonsCount)
            } else {
                stringResource(Res.string.downloads_episodes_selected, selectedCount)
            }

            Text(
                text = summaryText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        DialogButtons {
            DialogButton(
                text = stringResource(Res.string.action_cancel),
                onClick = onDismissRequest,
                style = DialogButtonStyle.Secondary,
            )
            DialogButton(
                text = stringResource(Res.string.downloads_action_continue_source_selection),
                onClick = { BatchDownloadCoordinator.continueToSourceSelection() },
                style = DialogButtonStyle.Primary,
                enabled = plan.totalSelectedCount > 0,
            )
        }
    }
}

@Composable
private fun SourceSelectionDialog(
    plan: BatchDownloadPlan,
    seasonIndex: Int,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    onOverrideClicked: (PlannedEpisodeItem) -> Unit,
) {
    val totalSeasons = plan.seasonsToSource.size
    val currentSeason = plan.seasonsToSource[seasonIndex]
    val currentSeasonPlan = plan.seasonPlans[currentSeason]
    val isPlanning = currentSeasonPlan?.isPlanning == true

    val dialogTitle = if (plan.isSingleTitle) {
        plan.meta.name
    } else {
        stringResource(
            Res.string.downloads_season_step_indicator,
            seasonIndex + 1,
            totalSeasons,
        )
    }

    val currentSeasonLabel = if (plan.isSingleTitle) {
        plan.meta.releaseInfo.orEmpty()
    } else {
        seasonLabel(currentSeason)
    }

    DialogSurface(
        onDismissRequest = onDismissRequest,
        title = dialogTitle,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (currentSeasonLabel.isNotBlank()) {
                    Text(
                        text = currentSeasonLabel,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (isPlanning) {
                    FindingSourceIndicator(compact = false)
                }
            }

            val episodes = currentSeasonPlan?.episodes.orEmpty()
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(
                    items = episodes,
                    key = { it.episode.id.ifBlank { "${it.episode.season}_${it.episode.episode}" } },
                ) { item ->
                    EpisodeSourcePlanRow(
                        item = item,
                        season = currentSeason,
                        defaultTitle = plan.meta.name,
                        onChangeSource = { onOverrideClicked(item) },
                    )
                }
            }
        }

        val isLastSeason = seasonIndex >= totalSeasons - 1

        DialogButtons {
            DialogButton(
                text = if (plan.isSingleTitle) {
                    stringResource(Res.string.action_cancel)
                } else {
                    stringResource(Res.string.action_back)
                },
                onClick = {
                    if (plan.isSingleTitle) {
                        onDismissRequest()
                    } else if (seasonIndex > 0) {
                        BatchDownloadCoordinator.goToSeasonIndex(seasonIndex - 1)
                    } else {
                        BatchDownloadCoordinator.goToEpisodeSelection()
                    }
                },
                style = DialogButtonStyle.Secondary,
            )

            if (!isLastSeason && !plan.isSingleTitle) {
                DialogButton(
                    text = stringResource(Res.string.downloads_next_season),
                    onClick = { BatchDownloadCoordinator.goToSeasonIndex(seasonIndex + 1) },
                    style = DialogButtonStyle.Primary,
                    enabled = !isPlanning,
                    loading = isPlanning,
                )
            } else {
                DialogButton(
                    text = stringResource(Res.string.downloads_action_queue),
                    onClick = onConfirm,
                    style = DialogButtonStyle.Primary,
                    enabled = !isPlanning && plan.seasonPlans.values.any { it.readyToDownloadCount > 0 },
                    loading = isPlanning,
                )
            }
        }
    }
}

@Composable
private fun EpisodeSourcePlanRow(
    item: PlannedEpisodeItem,
    season: Int,
    defaultTitle: String,
    onChangeSource: () -> Unit,
) {
    val episodeTitle = item.episode.title?.takeIf { it.isNotBlank() } ?: defaultTitle
    val displayText = item.episode.episode?.let { episodeNumber ->
        localizedEpisodeLabel(season, episodeNumber, episodeTitle)
    } ?: episodeTitle

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayText,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                when {
                    item.status == EpisodePlanStatus.Loading -> {
                        FindingSourceIndicator(compact = true)
                    }
                    item.status == EpisodePlanStatus.AlreadyDownloaded -> {
                        Text(
                            text = stringResource(Res.string.season_download_already_downloaded),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    item.status == EpisodePlanStatus.AlreadyInProgress -> {
                        Text(
                            text = stringResource(Res.string.downloads_status_queued),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    item.status == EpisodePlanStatus.Ready && item.streamCandidate != null -> {
                        val badge = item.resolutionLabel
                        val addon = item.streamCandidate.addonName.takeUnless { it.isNullOrBlank() }
                        val size = item.sizeBytes?.takeIf { it > 0 }?.let { formatBytes(it) }
                        val details = streamSourceSummary(badge, addon, size)
                        Text(
                            text = details.ifBlank { item.streamCandidate.name ?: stringResource(Res.string.downloads_stream_selected) },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    else -> {
                        Text(
                            text = stringResource(Res.string.season_download_no_source_found),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            if (item.status == EpisodePlanStatus.Ready || item.allAvailableStreams.isNotEmpty()) {
                OutlinedButton(
                    onClick = onChangeSource,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.padding(start = 8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(Res.string.downloads_override_source),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun StreamOverrideDialog(
    target: PlannedEpisodeItem,
    onSelect: (StreamItem) -> Unit,
    onDismiss: () -> Unit,
) {
    val episodeNumber = target.episode.episode
    val dialogTitle = if (episodeNumber != null) {
        stringResource(Res.string.downloads_select_source_dialog_title, episodeNumber)
    } else {
        stringResource(Res.string.downloads_select_source_dialog_title_single)
    }
    DialogSurface(
        onDismissRequest = onDismiss,
        title = dialogTitle,
    ) {
        val streams = target.allAvailableStreams
        if (streams.isEmpty()) {
            Text(
                text = stringResource(Res.string.season_download_no_source_found),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(streams) { stream ->
                    val facts = remember(stream) {
                        DebridStreamMetadata.facts(stream, DebridStreamPreferences())
                    }
                    val isCurrentSelection = stream == target.streamCandidate

                    val badge = facts.qualityBadge()
                    val size = StreamSourcePrefillSelector.sizeBytes(stream)?.let { formatBytes(it) }
                    val addon = stream.addonName

                    val titleText = stream.streamLabel.ifBlank { stream.name ?: stringResource(Res.string.downloads_stream_fallback_title) }
                    val subtitleText = streamSourceSummary(badge, addon, size)

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(stream) },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isCurrentSelection) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        },
                        border = if (isCurrentSelection) {
                            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                        } else null,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = titleText,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (subtitleText.isNotBlank()) {
                                    Text(
                                        text = subtitleText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isCurrentSelection) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            if (isCurrentSelection) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
        }

        DialogButtons {
            DialogButton(
                text = stringResource(Res.string.action_cancel),
                onClick = onDismiss,
                style = DialogButtonStyle.Secondary,
            )
        }
    }
}

@Composable
private fun SelectionTextButton(label: StringResource, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(text = stringResource(label), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun FindingSourceIndicator(compact: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 6.dp),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(if (compact) 10.dp else 14.dp),
            strokeWidth = if (compact) 1.5.dp else 2.dp,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(Res.string.season_download_finding_source),
            style = MaterialTheme.typography.bodySmall,
            color = if (compact) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
        )
    }
}

private fun streamSourceSummary(badge: String?, addon: String?, size: String?): String =
    listOfNotNull(badge, addon, size).joinToString(" • ")
