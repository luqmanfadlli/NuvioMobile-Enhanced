package com.nuvio.app.features.home.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.core.ui.NuvioShelfSection
import com.nuvio.app.core.ui.NuvioViewAllPillSize
import com.nuvio.app.core.ui.PosterRowImagePrefetchEffect
import com.nuvio.app.core.ui.rememberPosterCardStyleUiState
import com.nuvio.app.features.home.HomeCatalogSection
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.stableKey
import com.nuvio.app.features.watching.application.WatchingState

@Composable
fun HomeCatalogRowSection(
    section: HomeCatalogSection,
    modifier: Modifier = Modifier,
    entries: List<MetaPreview> = section.items,
    watchedKeys: Set<String> = emptySet(),
    fullyWatchedSeriesKeys: Set<String> = emptySet(),
    sectionPadding: Dp? = null,
    onViewAllClick: (() -> Unit)? = null,
    onPosterClick: ((MetaPreview) -> Unit)? = null,
    onPosterLongClick: ((MetaPreview) -> Unit)? = null,
) {
    if (sectionPadding != null) {
        HomeCatalogRowSectionContent(
            section = section,
            entries = entries,
            watchedKeys = watchedKeys,
            fullyWatchedSeriesKeys = fullyWatchedSeriesKeys,
            modifier = modifier.fillMaxWidth(),
            sectionPadding = sectionPadding,
            onViewAllClick = onViewAllClick,
            onPosterClick = onPosterClick,
            onPosterLongClick = onPosterLongClick,
        )
    } else {
        BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
            HomeCatalogRowSectionContent(
                section = section,
                entries = entries,
                watchedKeys = watchedKeys,
                fullyWatchedSeriesKeys = fullyWatchedSeriesKeys,
                modifier = Modifier.fillMaxWidth(),
                sectionPadding = homeSectionHorizontalPaddingForWidth(maxWidth.value),
                onViewAllClick = onViewAllClick,
                onPosterClick = onPosterClick,
                onPosterLongClick = onPosterLongClick,
            )
        }
    }
}

@Composable
private fun HomeCatalogRowSectionContent(
    section: HomeCatalogSection,
    entries: List<MetaPreview>,
    watchedKeys: Set<String>,
    fullyWatchedSeriesKeys: Set<String>,
    modifier: Modifier,
    sectionPadding: Dp,
    onViewAllClick: (() -> Unit)?,
    onPosterClick: ((MetaPreview) -> Unit)?,
    onPosterLongClick: ((MetaPreview) -> Unit)?,
) {
    val posterCardStyle = rememberPosterCardStyleUiState()
    val netflixStyleEnabled = posterCardStyle.netflixStyleEnabled
    val catalogPosterStyle = remember(posterCardStyle, netflixStyleEnabled) {
        if (!netflixStyleEnabled) {
            posterCardStyle
        } else {
            val width = maxOf(posterCardStyle.widthDp, 142)
            posterCardStyle.copy(
                widthDp = width,
                heightDp = (width * 3) / 2,
                cornerRadiusDp = minOf(posterCardStyle.cornerRadiusDp, 8),
                catalogLandscapeModeEnabled = false,
            )
        }
    }
    val isTopTenShelf = remember(section.title) {
        section.title.contains("top 10", ignoreCase = true) ||
            section.title.contains("top ten", ignoreCase = true)
    }
    var selectedTabKey by remember(section.key) { mutableStateOf("all") }
    val selectedTab = section.sourceTabs.firstOrNull { it.key == selectedTabKey }
        ?: section.sourceTabs.firstOrNull()
    val previewEntryKeys = remember(entries) { entries.mapTo(hashSetOf()) { it.stableKey() } }
    val visibleEntries = remember(selectedTab, previewEntryKeys, entries) {
        (selectedTab?.items ?: entries).filter { item -> item.stableKey() in previewEntryKeys }
    }
    val rowState = rememberLazyListState()
    LaunchedEffect(selectedTabKey) {
        rowState.scrollToItem(0)
    }
    val prefetchTargets = remember(visibleEntries, catalogPosterStyle) {
        visibleEntries.map { item ->
            item.homePosterImages(
                catalogPosterStyle,
                useLandscapeBackdropMode = catalogPosterStyle.catalogLandscapeModeEnabled,
            ).toPrefetchTarget()
        }
    }
    PosterRowImagePrefetchEffect(state = rowState, targets = prefetchTargets)

    Column(modifier = modifier.fillMaxWidth()) {
        if (section.sourceTabs.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = sectionPadding),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                section.sourceTabs.forEach { tab ->
                    FilterChip(
                        selected = selectedTab?.key == tab.key,
                        onClick = { selectedTabKey = tab.key },
                        label = { Text(tab.title) },
                    )
                }
            }
        }

        NuvioShelfSection(
            title = section.title,
            entries = visibleEntries,
            modifier = Modifier.fillMaxWidth(),
            headerHorizontalPadding = sectionPadding,
            rowContentPadding = PaddingValues(horizontal = sectionPadding),
            onViewAllClick = onViewAllClick,
            viewAllPillSize = NuvioViewAllPillSize.Compact,
            key = { item -> item.stableKey() },
            state = rowState,
        ) { item ->
            val rank = visibleEntries.indexOf(item) + 1
            val poster = @Composable {
                HomePosterCard(
                    item = item,
                    useLandscapeBackdropMode = catalogPosterStyle.catalogLandscapeModeEnabled,
                    isWatched = WatchingState.isPosterWatched(
                        watchedKeys = watchedKeys,
                        item = item,
                        fullyWatchedSeriesKeys = fullyWatchedSeriesKeys,
                    ),
                    posterCardStyleOverride = catalogPosterStyle,
                    onClick = onPosterClick?.let { { it(item) } },
                    onLongClick = onPosterLongClick?.let { { it(item) } },
                )
            }
            if (netflixStyleEnabled && isTopTenShelf && rank in 1..10) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Box(
                        modifier = Modifier
                            .width(58.dp)
                            .height(catalogPosterStyle.heightDp.dp),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        Text(
                            text = rank.toString(),
                            color = Color(0xFF777777),
                            fontSize = if (rank == 10) 76.sp else 100.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                        )
                    }
                    poster()
                }
            } else {
                poster()
            }
        }
    }
}
