package com.nuvio.app.features.library

import androidx.compose.animation.core.tween
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.i18n.localizedMediaTypeLabel
import com.nuvio.app.core.poster.withCustomPosterUrl
import com.nuvio.app.core.ui.NuvioDropdownChip
import com.nuvio.app.core.ui.NuvioDropdownOption
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.components.PosterGridRow
import com.nuvio.app.features.home.components.PosterGridSkeletonRow
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.library_filter_all_types
import nuvio.composeapp.generated.resources.library_filter_sort
import nuvio.composeapp.generated.resources.library_sort_added_asc
import nuvio.composeapp.generated.resources.library_sort_added_desc
import nuvio.composeapp.generated.resources.library_sort_title_asc
import nuvio.composeapp.generated.resources.library_sort_title_desc
import nuvio.composeapp.generated.resources.library_sort_provider_order
import nuvio.composeapp.generated.resources.library_sort_released_asc
import nuvio.composeapp.generated.resources.library_sort_released_desc
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun LibrarySavedControls(
    layoutMode: LibraryLayoutMode,
    sourceMode: LibrarySourceMode,
    sortOption: LibrarySortOption,
    verticalProjection: LibraryVerticalProjection,
    onSectionSelected: (String) -> Unit,
    onTypeSelected: (String?) -> Unit,
    onSortSelected: (LibrarySortOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sortOptions = availableLibrarySortOptions(sourceMode)
    val allTypesLabel = stringResource(Res.string.library_filter_all_types)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (layoutMode == LibraryLayoutMode.VERTICAL) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LibraryChip(
                        label = allTypesLabel,
                        selected = verticalProjection.selectedType == null,
                        onClick = { onTypeSelected(null) },
                    )
                    verticalProjection.availableTypes.forEach { type ->
                        LibraryChip(
                            label = localizedMediaTypeLabel(type),
                            selected = verticalProjection.selectedType == type,
                            onClick = { onTypeSelected(type) },
                        )
                    }
                }
            } else {
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))
            }

            NuvioDropdownChip(
                title = stringResource(Res.string.library_filter_sort),
                label = librarySortOptionLabel(sortOption),
                selectedKey = sortOption.name,
                options = sortOptions.map { option ->
                    NuvioDropdownOption(key = option.name, label = librarySortOptionLabel(option))
                },
                enabled = sortOptions.size > 1,
                onSelected = { option ->
                    LibrarySortOption.entries
                        .firstOrNull { it.name == option.key }
                        ?.let(onSortSelected)
                },
            )
        }

        if (
            layoutMode == LibraryLayoutMode.VERTICAL &&
            sourceMode.isRemoteTrackingSource &&
            verticalProjection.availableSections.size > 1
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                verticalProjection.availableSections.forEach { section ->
                    LibraryChip(
                        label = localizedLibrarySectionTitle(section.type, section.displayTitle),
                        selected = section.type == verticalProjection.selectedSectionKey,
                        onClick = { onSectionSelected(section.type) },
                    )
                }
            }
        }
    }
}

internal fun LazyListScope.libraryVerticalContent(
    projection: LibraryVerticalProjection,
    columns: Int,
    releaseInfoFor: (LibraryItem) -> String,
    watchedKeys: Set<String>,
    fullyWatchedSeriesKeys: Set<String>,
    posterPattern: String,
    onPosterClick: ((LibraryItem) -> Unit)?,
    onPosterLongClick: ((LibraryItem, LibrarySection) -> Unit)?,
) {
    items(
        items = projection.entries.chunked(columns),
        key = { rowEntries ->
            val firstEntry = rowEntries.first()
            "library-vertical:${firstEntry.item.type}:${firstEntry.item.id}"
        },
        contentType = { "library_poster_row" },
    ) { rowEntries ->
        val resolvedItems = remember(rowEntries, posterPattern, releaseInfoFor) {
            rowEntries.map { entry ->
                entry.item.toMetaPreview()
                    .copy(releaseInfo = releaseInfoFor(entry.item))
                    .let { if (posterPattern.isNotBlank()) it.withCustomPosterUrl(posterPattern) else it }
            }
        }
        PosterGridRow(
            items = resolvedItems,
            columns = columns,
            modifier = libraryContentTransitionModifier()
                .padding(horizontal = 16.dp),
            watchedKeys = watchedKeys,
            fullyWatchedSeriesKeys = fullyWatchedSeriesKeys,
            onPosterClick = onPosterClick?.let { callback ->
                { preview -> rowEntries.findEntry(preview)?.item?.let(callback) }
            },
            onPosterLongClick = onPosterLongClick?.let { callback ->
                { preview ->
                    rowEntries.findEntry(preview)?.let { entry -> callback(entry.item, entry.section) }
                }
            },
        )
    }
}

internal fun LazyListScope.libraryVerticalSkeletonItems(columns: Int) {
    items(
        count = 2,
        key = { index -> "library-vertical-skeleton:$index" },
        contentType = { "library_skeleton_row" },
    ) {
        PosterGridSkeletonRow(
            columns = columns,
            modifier = libraryContentTransitionModifier()
                .padding(horizontal = 16.dp),
        )
    }
}

internal fun LazyItemScope.libraryContentTransitionModifier(): Modifier =
    Modifier.animateItem(
        fadeInSpec = tween(durationMillis = 160),
        placementSpec = tween(durationMillis = 180),
        fadeOutSpec = tween(durationMillis = 90),
    )

@Composable
private fun librarySortOptionLabel(option: LibrarySortOption): String =
    when (option) {
        LibrarySortOption.DEFAULT -> stringResource(Res.string.library_sort_provider_order)
        LibrarySortOption.ADDED_DESC -> stringResource(Res.string.library_sort_added_desc)
        LibrarySortOption.ADDED_ASC -> stringResource(Res.string.library_sort_added_asc)
        LibrarySortOption.RELEASED_DESC -> stringResource(Res.string.library_sort_released_desc)
        LibrarySortOption.RELEASED_ASC -> stringResource(Res.string.library_sort_released_asc)
        LibrarySortOption.TITLE_ASC -> stringResource(Res.string.library_sort_title_asc)
        LibrarySortOption.TITLE_DESC -> stringResource(Res.string.library_sort_title_desc)
    }

private fun List<LibraryVerticalEntry>.findEntry(preview: MetaPreview): LibraryVerticalEntry? =
    firstOrNull { entry -> entry.item.id == preview.id && entry.item.type == preview.type }
