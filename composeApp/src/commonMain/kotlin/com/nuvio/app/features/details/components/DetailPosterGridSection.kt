package com.nuvio.app.features.details.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.rememberPosterCardStyleUiState
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.components.HomePosterCard
import com.nuvio.app.features.watching.application.WatchingState
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DetailPosterGridSection(
    title: String,
    items: List<MetaPreview>,
    watchedKeys: Set<String>,
    modifier: Modifier = Modifier,
    fullyWatchedSeriesKeys: Set<String> = emptySet(),
    onPosterClick: ((MetaPreview) -> Unit)? = null,
    sourceLabel: String? = null,
    onViewAllClick: (() -> Unit)? = null,
    onPosterLongClick: ((MetaPreview) -> Unit)? = null,
) {
    if (items.isEmpty()) return
    val baseStyle = rememberPosterCardStyleUiState()

    androidx.compose.foundation.layout.Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (onViewAllClick != null) {
                Text(
                    text = "View all",
                    modifier = Modifier.clickable(onClick = onViewAllClick),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val spacing = 12.dp
            val cardWidth = (maxWidth - spacing * 2) / 3
            val cardWidthDp = cardWidth.value.roundToInt().coerceAtLeast(1)
            val cardStyle = baseStyle.copy(
                widthDp = cardWidthDp,
                heightDp = (cardWidthDp * 3) / 2,
                cornerRadiusDp = minOf(baseStyle.cornerRadiusDp, 8),
                catalogLandscapeModeEnabled = false,
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items.forEach { item ->
                    HomePosterCard(
                        item = item,
                        modifier = Modifier.width(cardWidth),
                        posterCardStyleOverride = cardStyle,
                        isWatched = WatchingState.isPosterWatched(
                            watchedKeys = watchedKeys,
                            item = item,
                            fullyWatchedSeriesKeys = fullyWatchedSeriesKeys,
                        ),
                        onClick = onPosterClick?.let { { it(item) } },
                        onLongClick = onPosterLongClick?.let { { it(item) } },
                    )
                }
            }
        }
        sourceLabel?.takeIf { it.isNotBlank() }?.let { label ->
            Text(
                text = label,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
