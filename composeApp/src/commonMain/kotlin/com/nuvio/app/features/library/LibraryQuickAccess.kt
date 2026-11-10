package com.nuvio.app.features.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.accentBrush
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.core.ui.themePalette
import com.nuvio.app.features.downloads.DownloadStatus
import com.nuvio.app.features.downloads.DownloadsRepository

/**
 * The two shortcuts at the top of the Library, laid out the way Netflix does its Downloads and Coming Soon
 * rows: a big tappable card each, with a fan of artwork so you can see what is inside before you open it.
 */
@Composable
internal fun LibraryQuickAccess(
    onDownloadsClick: (() -> Unit)?,
    onCalendarClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (onDownloadsClick != null) LibraryDownloadsCard(onClick = onDownloadsClick)
        LibraryCalendarCard(onClick = onCalendarClick)
    }
}

@Composable
private fun LibraryDownloadsCard(onClick: () -> Unit) {
    val downloads by remember {
        DownloadsRepository.ensureLoaded()
        DownloadsRepository.uiState
    }.collectAsStateWithLifecycle()
    val downloading = downloads.items.count { it.status == DownloadStatus.Downloading }
    val ready = downloads.completedItems.size
    val subtitle = when {
        downloading > 0 && ready > 0 -> "$downloading downloading · $ready ready to watch offline"
        downloading > 0 -> "$downloading downloading"
        ready > 0 -> "$ready ready to watch offline"
        else -> "Save movies and episodes to watch offline"
    }
    val artwork = remember(downloads.items) {
        downloads.items
            .sortedByDescending { it.updatedAtEpochMs }
            .distinctBy { it.parentMetaId }
            .mapNotNull { it.poster ?: it.episodeThumbnail ?: it.background }
            .take(3)
    }
    QuickAccessCard(
        icon = Icons.Rounded.Download,
        title = "Downloads",
        subtitle = subtitle,
        artwork = artwork,
        onClick = onClick,
    )
}

@Composable
private fun LibraryCalendarCard(onClick: () -> Unit) {
    val upcoming by remember { libraryUpcomingEpisodesFlow(7) }.collectAsStateWithLifecycle(emptyList())
    val subtitle = when (upcoming.size) {
        0 -> "See what's coming out from your list"
        1 -> "1 new episode this week"
        else -> "${upcoming.size} new episodes this week"
    }
    val artwork = remember(upcoming) {
        upcoming.mapNotNull { it.imageUrl ?: it.item.poster }.distinct().take(3)
    }
    QuickAccessCard(
        icon = Icons.Rounded.CalendarMonth,
        title = "Release Calendar",
        subtitle = subtitle,
        artwork = artwork,
        onClick = onClick,
    )
}

@Composable
private fun QuickAccessCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    artwork: List<String>,
    onClick: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val palette = MaterialTheme.themePalette
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(tokens.colors.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(palette.accentBrush()),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = palette.onSecondary,
                modifier = Modifier.size(26.dp),
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = tokens.colors.textPrimary,
                maxLines = 1,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = tokens.colors.textMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (artwork.isNotEmpty()) ArtworkFan(artwork)
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = tokens.colors.textMuted,
        )
    }
}

/** Up to three posters stacked like a hand of cards, the newest on top. */
@Composable
private fun ArtworkFan(urls: List<String>) {
    val tokens = MaterialTheme.nuvio
    val tilts = listOf(-7f, 0f, 7f)
    Box(modifier = Modifier.size(width = (34 + 22 * (urls.size - 1)).dp, height = 52.dp)) {
        urls.asReversed().forEachIndexed { index, url ->
            val slot = urls.size - 1 - index
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .offset(x = (22 * slot).dp)
                    .size(width = 34.dp, height = 50.dp)
                    .rotate(tilts[slot.coerceIn(0, 2)])
                    .clip(RoundedCornerShape(6.dp))
                    .background(tokens.colors.surfaceElevated),
            )
        }
    }
}
