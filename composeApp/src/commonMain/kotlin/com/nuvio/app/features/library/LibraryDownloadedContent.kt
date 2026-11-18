package com.nuvio.app.features.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.core.poster.withCustomPosterUrl
import com.nuvio.app.core.ui.NuvioCardDepthSurface
import com.nuvio.app.core.ui.NuvioPosterShape
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.aspectRatio
import com.nuvio.app.core.ui.cardWidth
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.core.ui.nuvioCardDepth
import com.nuvio.app.core.ui.rememberPosterCardStyleUiState
import com.nuvio.app.features.downloads.DownloadItem
import com.nuvio.app.features.downloads.DownloadProgressBar
import com.nuvio.app.features.downloads.DownloadProgressSpinner
import com.nuvio.app.features.downloads.DownloadRevealImage
import com.nuvio.app.features.downloads.DownloadStatus
import com.nuvio.app.features.downloads.StoredOfflineMeta
import com.nuvio.app.features.downloads.animatedDownloadProgress
import com.nuvio.app.features.downloads.formatDownloadBadgeProgress
import com.nuvio.app.features.downloads.formatDownloadPercent
import com.nuvio.app.features.downloads.sortedForSeriesDownloads
import com.nuvio.app.features.downloads.statusCounts
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.PosterShape
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/** A library title with downloads still in progress, shown as a filling poster. */
internal data class ActiveDownloadCard(
    val title: String,
    val poster: String?,
    val isShow: Boolean,
    /** The movie's id, or the show's id for a show. */
    val contentId: String,
    /** The show's overall status: downloading wins over queued, which wins over paused. */
    val status: DownloadStatus,
    /** Overall progress, counting finished episodes as complete. */
    val progressFraction: Float,
    /** One entry per episode (a single entry for a movie). */
    val segmentProgresses: List<Float>,
    /** The movie's download; null for a show. */
    val movieDownload: DownloadItem? = null,
    val completedEpisodeCount: Int = 0,
    val totalEpisodeCount: Int = 0,
)

internal val LibraryItem.isSeries: Boolean
    get() = type == "series" || type == "tv"

/**
 * The Downloaded tab's sections: downloaded titles keep the sections they have in the Saved
 * tab, and titles that aren't saved go into one section per type.
 */
internal fun buildDownloadedSections(
    savedSections: List<LibrarySection>,
    savedItems: List<LibraryItem>,
    downloadItems: List<DownloadItem>,
    offlineMetadataFor: (showId: String) -> StoredOfflineMeta?,
): List<LibrarySection> {
    if (downloadItems.isEmpty()) return emptyList()

    val savedItemsById = savedItems.associateBy { it.id }
    val downloadedTitles = downloadItems
        .groupBy { it.contentId }
        .map { (contentId, downloads) ->
            val offlineMeta = offlineMetadataFor(contentId)
            val saved = savedItemsById[contentId]
            saved?.withReleaseInfoFallback(offlineMeta) ?: downloads.toLibraryItem(contentId, offlineMeta)
        }
        .distinctBy { it.id }
    val downloadedById = downloadedTitles.associateBy { it.id }

    val placedIds = mutableSetOf<String>()
    val sections = savedSections.mapNotNull { section ->
        val matching = section.items.mapNotNull { downloadedById[it.id] }
        placedIds += matching.map { it.id }
        matching.takeIf { it.isNotEmpty() }?.let { section.copy(items = it) }
    }.toMutableList()

    downloadedTitles
        .filter { it.id !in placedIds }
        .groupBy { it.type }
        .forEach { (type, items) ->
            val index = sections.indexOfFirst { it.type == type }
            if (index >= 0) {
                sections[index] = sections[index].copy(items = sections[index].items + items)
            } else {
                sections += LibrarySection(
                    type = type,
                    displayTitle = type.toLibraryDisplayTitle(),
                    items = items.sortedByDescending { it.savedAtEpochMs },
                )
            }
        }
    return sections
}

private fun LibraryItem.withReleaseInfoFallback(offlineMeta: StoredOfflineMeta?): LibraryItem {
    val fallback = offlineMeta?.releaseInfo?.takeIf { it.isNotBlank() }
    return if (releaseInfo.isNullOrBlank() && fallback != null) copy(releaseInfo = fallback) else this
}

/** A library entry for a downloaded title that isn't saved, built from what the downloads know. */
private fun List<DownloadItem>.toLibraryItem(
    contentId: String,
    offlineMeta: StoredOfflineMeta?,
): LibraryItem {
    val first = first()
    val defaultType = if (first.isEpisode) "series" else "movie"
    return LibraryItem(
        id = contentId,
        type = offlineMeta?.type?.ifBlank { null } ?: first.parentMetaType.ifBlank { defaultType },
        name = offlineMeta?.title?.ifBlank { null } ?: first.title,
        poster = offlineMeta?.poster ?: first.poster,
        banner = offlineMeta?.background ?: first.background,
        logo = offlineMeta?.logo ?: first.logo,
        description = offlineMeta?.description,
        releaseInfo = offlineMeta?.releaseInfo?.takeIf { it.isNotBlank() },
        imdbRating = offlineMeta?.imdbRating,
        genres = offlineMeta?.genres.orEmpty(),
        posterShape = PosterShape.Poster,
        imdbId = contentId.takeIf { it.startsWith("tt") },
        savedAtEpochMs = maxOf { it.updatedAtEpochMs },
    )
}

/** The in-progress state of [item]'s downloads, or null when nothing is left to download. */
internal fun activeDownloadShelfItem(
    item: LibraryItem,
    allDownloads: List<DownloadItem>,
): ActiveDownloadCard? {
    if (!item.isSeries) {
        val movie = allDownloads.firstOrNull { !it.isEpisode && it.contentId == item.id }
        if (movie == null || movie.status == DownloadStatus.Completed) return null
        return ActiveDownloadCard(
            title = item.name.ifBlank { movie.title },
            poster = item.poster ?: movie.poster,
            isShow = false,
            contentId = movie.parentMetaId,
            status = movie.status,
            progressFraction = movie.progressFraction,
            segmentProgresses = listOf(movie.progressFraction),
            movieDownload = movie,
            totalEpisodeCount = 1,
        )
    }

    val episodes = allDownloads
        .filter { it.isEpisode && it.parentMetaId == item.id }
        .sortedForSeriesDownloads()
    val counts = episodes.statusCounts()
    if (!counts.hasActive && counts.paused == 0) return null

    val segmentProgresses = episodes.map { episode ->
        when (episode.status) {
            DownloadStatus.Completed -> 1f
            DownloadStatus.Downloading, DownloadStatus.Paused -> episode.progressFraction
            else -> 0f
        }
    }
    return ActiveDownloadCard(
        title = item.name,
        poster = item.poster,
        isShow = true,
        contentId = item.id,
        status = when {
            counts.downloading > 0 -> DownloadStatus.Downloading
            counts.queued > 0 -> DownloadStatus.Queued
            else -> DownloadStatus.Paused
        },
        progressFraction = segmentProgresses.average().toFloat(),
        segmentProgresses = segmentProgresses,
        completedEpisodeCount = episodes.count { it.status == DownloadStatus.Completed },
        totalEpisodeCount = episodes.size,
    )
}

@Composable
internal fun ActiveDownloadPosterCard(
    item: ActiveDownloadCard,
    posterPattern: String,
    onClick: () -> Unit,
    onTogglePause: (() -> Unit)?,
    modifier: Modifier = Modifier,
    fillMaxWidth: Boolean = false,
) {
    val posterCardStyle = rememberPosterCardStyleUiState()
    val cardShape = RoundedCornerShape(posterCardStyle.cornerRadiusDp.dp)
    val cardWidth = NuvioPosterShape.Poster.cardWidth(basePosterWidthDp = posterCardStyle.widthDp)
    val posterUrl = if (posterPattern.isNotBlank() && item.poster != null) {
        MetaPreview(id = item.contentId, type = if (item.isShow) "series" else "movie", name = item.title, poster = item.poster)
            .withCustomPosterUrl(posterPattern).poster
    } else {
        item.poster
    }
    val progress = animatedDownloadProgress(item.progressFraction)
    val isPaused = item.status == DownloadStatus.Paused

    Column(
        modifier = if (fillMaxWidth) modifier.fillMaxWidth() else modifier.width(cardWidth),
        verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(NuvioPosterShape.Poster.aspectRatio)
                .clip(cardShape)
                .background(MaterialTheme.nuvio.colors.surface)
                .nuvioCardDepth(shape = cardShape, surface = NuvioCardDepthSurface.Posters)
                .clickable(onClick = onClick),
        ) {
            DownloadRevealImage(
                imageUrl = posterUrl,
                contentDescription = item.title,
                segmentProgresses = item.segmentProgresses,
                dimAlpha = if (isPaused) 0.5f else 0.35f,
                edgeColor = if (isPaused) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxSize(),
                placeholder = { PosterPlaceholder(isQueued = item.status == DownloadStatus.Queued) },
            )
            Box(modifier = Modifier.fillMaxSize().padding(6.dp)) {
                StatusBadge(item = item, progress = progress, modifier = Modifier.align(Alignment.TopStart))
                if (onTogglePause != null && item.status != DownloadStatus.Failed) {
                    PauseResumeButton(
                        showsPause = item.status != DownloadStatus.Paused,
                        onClick = onTogglePause,
                        modifier = Modifier.align(Alignment.TopEnd),
                    )
                }
            }
            if (item.status == DownloadStatus.Downloading && !item.isShow) {
                MovieProgressOverlay(progress = progress.takeIf { item.progressFraction > 0f })
            }
        }
        PosterCaption(item = item, progress = progress)
    }
}

@Composable
private fun BoxScope.PosterPlaceholder(isQueued: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (isQueued) Icons.Rounded.Schedule else Icons.Rounded.ArrowDownward,
            contentDescription = null,
            modifier = Modifier.size(36.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        )
    }
}

@Composable
private fun StatusBadge(
    item: ActiveDownloadCard,
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val (containerColor, contentColor) = when (item.status) {
        DownloadStatus.Downloading -> colors.primary to colors.onPrimary
        DownloadStatus.Queued -> colors.surfaceContainerHigh.copy(alpha = 0.92f) to colors.primary
        DownloadStatus.Paused -> colors.errorContainer.copy(alpha = 0.92f) to colors.onErrorContainer
        else -> colors.surfaceContainer.copy(alpha = 0.92f) to colors.onSurface
    }
    val percentText = formatDownloadPercent(progress)
    val episodesText = "${item.completedEpisodeCount}/${item.totalEpisodeCount}"
    val text = when (item.status) {
        DownloadStatus.Downloading -> when {
            item.isShow -> stringResource(Res.string.downloads_progress_episodes, item.completedEpisodeCount, item.totalEpisodeCount)
            item.progressFraction <= 0f -> stringResource(Res.string.downloads_connecting)
            else -> percentText
        }
        DownloadStatus.Queued -> {
            val queued = stringResource(Res.string.downloads_status_queued)
            if (item.isShow) stringResource(Res.string.downloads_episodes_queued, episodesText, queued) else queued
        }
        DownloadStatus.Paused -> {
            val paused = stringResource(Res.string.downloads_badge_paused)
            when {
                item.isShow -> "$episodesText $paused"
                item.progressFraction > 0f -> percentText
                else -> paused
            }
        }
        DownloadStatus.Failed -> stringResource(Res.string.downloads_status_failed)
        DownloadStatus.Completed -> ""
    }

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = containerColor,
        border = if (item.status == DownloadStatus.Queued) BorderStroke(1.dp, colors.primary.copy(alpha = 0.5f)) else null,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            when (item.status) {
                DownloadStatus.Downloading -> DownloadProgressSpinner(
                    progress = progress.takeIf { it > 0f },
                    color = contentColor,
                    strokeWidth = 1.75.dp,
                )
                DownloadStatus.Queued -> BadgeIcon(Icons.Rounded.Schedule, contentColor)
                DownloadStatus.Paused -> BadgeIcon(Icons.Rounded.Pause, contentColor)
                else -> Unit
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                color = contentColor,
            )
        }
    }
}

@Composable
private fun BadgeIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color) {
    Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(10.dp), tint = tint)
}

@Composable
private fun PauseResumeButton(
    showsPause: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .size(26.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.65f),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (showsPause) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = stringResource(if (showsPause) Res.string.downloads_action_pause else Res.string.action_resume),
                modifier = Modifier.size(14.dp),
                tint = Color.White,
            )
        }
    }
}

@Composable
private fun BoxScope.MovieProgressOverlay(progress: Float?) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .align(Alignment.BottomCenter)
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))))
            .padding(6.dp),
    ) {
        DownloadProgressBar(
            progress = progress,
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
        )
    }
}

@Composable
private fun PosterCaption(item: ActiveDownloadCard, progress: Float) {
    val captionColor = if (item.status == DownloadStatus.Queued) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = item.title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            if (item.isShow) {
                DownloadProgressSpinner(
                    progress = progress,
                    color = MaterialTheme.colorScheme.primary,
                    size = 11.dp,
                    strokeWidth = 1.8.dp,
                )
            }
            Text(
                text = item.captionText(),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = captionColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ActiveDownloadCard.captionText(): String {
    if (isShow) {
        return stringResource(Res.string.downloads_episodes_out_of_downloaded, completedEpisodeCount, totalEpisodeCount)
    }
    val movie = movieDownload ?: return ""
    return when (status) {
        DownloadStatus.Downloading -> formatDownloadBadgeProgress(movie.downloadedBytes, movie.totalBytes)
        DownloadStatus.Queued -> stringResource(Res.string.downloads_status_queued)
        DownloadStatus.Paused -> stringResource(Res.string.downloads_badge_paused)
        DownloadStatus.Failed -> stringResource(Res.string.downloads_status_failed)
        DownloadStatus.Completed -> ""
    }
}
