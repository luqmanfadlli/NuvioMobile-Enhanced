package com.nuvio.app.features.library

import androidx.compose.animation.core.tween
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
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
import nuvio.composeapp.generated.resources.library_filter_genre
import nuvio.composeapp.generated.resources.library_filter_list
import nuvio.composeapp.generated.resources.library_filter_sort
import nuvio.composeapp.generated.resources.library_filter_type
import nuvio.composeapp.generated.resources.library_filter_watched
import nuvio.composeapp.generated.resources.library_filter_year
import nuvio.composeapp.generated.resources.library_type_all
import nuvio.composeapp.generated.resources.library_watched_filter_all
import nuvio.composeapp.generated.resources.library_watched_filter_unwatched
import nuvio.composeapp.generated.resources.library_watched_filter_watched
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
    watchedFilter: LibraryWatchedFilter,
    onSectionSelected: (String) -> Unit,
    onTypeSelected: (String?) -> Unit,
    onGenreSelected: (String?) -> Unit,
    onYearSelected: (String?) -> Unit,
    onWatchedFilterSelected: (LibraryWatchedFilter) -> Unit,
    onSortSelected: (LibrarySortOption) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val sortOptions = availableLibrarySortOptions(sourceMode)
    val allTypesLabel = stringResource(Res.string.library_filter_all_types)
    val allLabel = stringResource(Res.string.library_type_all)
    val facetChips: @Composable (Boolean) -> Unit = { fillWidth ->
        if (verticalProjection.availableGenres.isNotEmpty()) {
            val genreTitle = stringResource(Res.string.library_filter_genre)
            FacetSlot(fillWidth) {
                NuvioDropdownChip(
                    title = genreTitle,
                    label = "$genreTitle: ${verticalProjection.selectedGenre ?: allLabel}",
                    selectedKey = verticalProjection.selectedGenre.orEmpty(),
                    options = libraryFacetOptions(allLabel, verticalProjection.availableGenres),
                    onSelected = { option -> onGenreSelected(option.key.ifBlank { null }) },
                    fillWidth = fillWidth,
                )
            }
        }
        if (verticalProjection.availableYears.isNotEmpty()) {
            val yearTitle = stringResource(Res.string.library_filter_year)
            FacetSlot(fillWidth) {
                NuvioDropdownChip(
                    title = yearTitle,
                    label = "$yearTitle: ${verticalProjection.selectedYear ?: allLabel}",
                    selectedKey = verticalProjection.selectedYear.orEmpty(),
                    options = libraryFacetOptions(allLabel, verticalProjection.availableYears),
                    onSelected = { option -> onYearSelected(option.key.ifBlank { null }) },
                    fillWidth = fillWidth,
                )
            }
        }
        val watchedTitle = stringResource(Res.string.library_filter_watched)
        FacetSlot(fillWidth) {
            NuvioDropdownChip(
                title = watchedTitle,
                label = "$watchedTitle: ${libraryWatchedFilterLabel(watchedFilter)}",
                selectedKey = watchedFilter.name,
                options = LibraryWatchedFilter.entries.map { filter ->
                    NuvioDropdownOption(key = filter.name, label = libraryWatchedFilterLabel(filter))
                },
                onSelected = { option ->
                    LibraryWatchedFilter.entries
                        .firstOrNull { it.name == option.key }
                        ?.let(onWatchedFilterSelected)
                },
                fillWidth = fillWidth,
            )
        }
    }

    val leadingChips: @Composable (Boolean) -> Unit = { fillWidth ->
        if (layoutMode == LibraryLayoutMode.VERTICAL && sourceMode.isRemoteTrackingSource) {
            val selectedSection = verticalProjection.availableSections
                .firstOrNull { section -> section.type == verticalProjection.selectedSectionKey }
            FacetSlot(fillWidth) {
                NuvioDropdownChip(
                    title = stringResource(Res.string.library_filter_list),
                    label = selectedSection?.let { section -> localizedLibrarySectionTitle(section.type, section.displayTitle) }.orEmpty(),
                    selectedKey = verticalProjection.selectedSectionKey,
                    options = verticalProjection.availableSections.map { section ->
                        NuvioDropdownOption(key = section.type, label = localizedLibrarySectionTitle(section.type, section.displayTitle))
                    },
                    enabled = verticalProjection.availableSections.size > 1,
                    onSelected = { option -> onSectionSelected(option.key) },
                    fillWidth = fillWidth,
                )
            }
        }

        if (layoutMode == LibraryLayoutMode.VERTICAL) {
            val typeOptions = buildList {
                add(NuvioDropdownOption(key = "", label = "$allTypesLabel (${verticalProjection.allTypesCount})"))
                addAll(
                    verticalProjection.availableTypes.map { type ->
                        NuvioDropdownOption(
                            key = type,
                            label = "${localizedMediaTypeLabel(type)} (${verticalProjection.typeCounts[type] ?: 0})",
                        )
                    },
                )
            }
            FacetSlot(fillWidth) {
                NuvioDropdownChip(
                    title = stringResource(Res.string.library_filter_type),
                    label = verticalProjection.selectedType
                        ?.let(::localizedMediaTypeLabel)
                        ?: allTypesLabel,
                    selectedKey = verticalProjection.selectedType.orEmpty(),
                    options = typeOptions,
                    enabled = typeOptions.size > 1,
                    onSelected = { option -> onTypeSelected(option.key.ifBlank { null }) },
                    fillWidth = fillWidth,
                )
            }
        }

        FacetSlot(fillWidth) {
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
                fillWidth = fillWidth,
            )
        }
    }

    val chipCount = (if (sourceMode.isRemoteTrackingSource) 1 else 0) + 2 +
        (if (verticalProjection.availableGenres.isNotEmpty()) 1 else 0) +
        (if (verticalProjection.availableYears.isNotEmpty()) 1 else 0) + 1
    val singleRow = layoutMode == LibraryLayoutMode.VERTICAL && (compact || chipCount <= SingleRowMaxChips)

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (singleRow) {
            LibraryFilterRow(modifier = Modifier.widthIn(max = CompactFilterRowMaxWidth), shrinkToFit = true) {
                leadingChips(true)
                facetChips(true)
            }
        } else {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                leadingChips(false)
            }
        }

        if (layoutMode == LibraryLayoutMode.VERTICAL && !singleRow) {
            LibraryFilterRow {
                facetChips(true)
            }
        }
    }
}

private val CompactFilterRowMaxWidth = 960.dp
private val CompactFilterMinChipWidth = 112.dp
private const val SingleRowMaxChips = 3

@Composable
private fun FacetSlot(fillWidth: Boolean, content: @Composable () -> Unit) {
    if (fillWidth) {
        Box(propagateMinConstraints = true) { content() }
    } else {
        content()
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
    activeDownloadItemFor: ((LibraryItem) -> ActiveDownloadCard?)? = null,
    onActiveDownloadTogglePause: ((ActiveDownloadCard) -> Unit)? = null,
) {
    items(
        items = projection.entries.chunked(columns),
        key = { rowEntries ->
            val firstEntry = rowEntries.first()
            "library-vertical:${firstEntry.item.type}:${firstEntry.item.id}"
        },
        contentType = { "library_poster_row" },
    ) { rowEntries ->
        // Not remembered: whether a row has an active download changes as downloads progress,
        // while the row's entries stay the same.
        val activeDownloads = rowEntries.map { activeDownloadItemFor?.invoke(it.item) }
        val resolvedItems = remember(rowEntries, posterPattern, releaseInfoFor) {
            rowEntries.map { entry ->
                entry.item.toPosterPreview(releaseInfoFor, posterPattern)
            }
        }
        PosterGridRow(
            items = resolvedItems,
            columns = columns,
            modifier = libraryContentTransitionModifier().padding(horizontal = 16.dp),
            watchedKeys = watchedKeys,
            fullyWatchedSeriesKeys = fullyWatchedSeriesKeys,
            onPosterClick = onPosterClick?.let { callback ->
                { preview -> rowEntries.findEntry(preview)?.item?.let(callback) }
            },
            onPosterLongClick = onPosterLongClick?.let { callback ->
                { preview -> rowEntries.findEntry(preview)?.let { entry -> callback(entry.item, entry.section) } }
            },
            cellOverride = { index, cellModifier ->
                val activeDownload = activeDownloads[index]
                activeDownload?.let { download ->
                    @Composable {
                        ActiveDownloadPosterCard(
                            item = download,
                            posterPattern = posterPattern,
                            onClick = { onPosterClick?.invoke(rowEntries[index].item) },
                            onTogglePause = onActiveDownloadTogglePause?.let { callback -> { callback(download) } },
                            modifier = cellModifier,
                            fillMaxWidth = true,
                        )
                    }
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

private fun libraryFacetOptions(
    allLabel: String,
    options: List<LibraryFilterOption>,
): List<NuvioDropdownOption> =
    listOf(NuvioDropdownOption(key = "", label = allLabel)) +
        options.map { option -> NuvioDropdownOption(key = option.key, label = "${option.key} (${option.count})") }

@Composable
private fun LibraryFilterRow(
    modifier: Modifier = Modifier,
    shrinkToFit: Boolean = false,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val viewportWidth = constraints.maxWidth
        Layout(
            content = content,
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) { measurables, _ ->
            val count = measurables.size
            val spacing = 8.dp.roundToPx()
            val totalSpacing = spacing * (count - 1).coerceAtLeast(0)
            val naturalWidths = measurables.map { it.maxIntrinsicWidth(Constraints.Infinity) }
            val naturalTotal = naturalWidths.sum() + totalSpacing
            val equalShare = if (count > 0) (viewportWidth - totalSpacing) / count else 0
            val minChip = CompactFilterMinChipWidth.roundToPx()
            val available = viewportWidth - totalSpacing
            val widths = when {
                count == 0 -> emptyList()
                naturalWidths.all { it <= equalShare } -> List(count) { index ->
                    if (index == count - 1) viewportWidth - totalSpacing - equalShare * (count - 1) else equalShare
                }
                naturalTotal <= viewportWidth -> {
                    val extra = (viewportWidth - naturalTotal) / count
                    naturalWidths.mapIndexed { index, width ->
                        width + extra + if (index == count - 1) (viewportWidth - naturalTotal) - extra * count else 0
                    }
                }
                shrinkToFit && minChip * count <= available -> {
                    val shrunk = naturalWidths.map { width ->
                        (width.toLong() * available / (naturalTotal - totalSpacing)).toInt().coerceAtLeast(minChip)
                    }
                    val overflow = shrunk.sum() - available
                    if (overflow <= 0) {
                        shrunk.mapIndexed { index, width -> if (index == count - 1) width - overflow else width }
                    } else {
                        val shrinkable = shrunk.sumOf { (it - minChip).coerceAtLeast(0) }.coerceAtLeast(1)
                        val adjusted = shrunk.map { width ->
                            width - ((width - minChip).coerceAtLeast(0).toLong() * overflow / shrinkable).toInt()
                        }
                        adjusted.mapIndexed { index, width ->
                            if (index == count - 1) width + (available - adjusted.sum()) else width
                        }
                    }
                }
                else -> naturalWidths
            }
            val placeables = measurables.mapIndexed { index, measurable ->
                measurable.measure(Constraints.fixedWidth(widths[index]))
            }
            val height = placeables.maxOfOrNull { it.height } ?: 0
            val width = placeables.sumOf { it.width } + totalSpacing
            layout(width, height) {
                var x = 0
                placeables.forEach { placeable ->
                    placeable.placeRelative(x, (height - placeable.height) / 2)
                    x += placeable.width + spacing
                }
            }
        }
    }
}

@Composable
private fun libraryWatchedFilterLabel(filter: LibraryWatchedFilter): String =
    when (filter) {
        LibraryWatchedFilter.ALL -> stringResource(Res.string.library_watched_filter_all)
        LibraryWatchedFilter.WATCHED -> stringResource(Res.string.library_watched_filter_watched)
        LibraryWatchedFilter.UNWATCHED -> stringResource(Res.string.library_watched_filter_unwatched)
    }

private fun List<LibraryVerticalEntry>.findEntry(preview: MetaPreview): LibraryVerticalEntry? =
    firstOrNull { entry -> entry.item.id == preview.id && entry.item.type == preview.type }

/** The poster preview for [this] item, with its release info and the user's custom poster pattern. */
internal fun LibraryItem.toPosterPreview(
    releaseInfoFor: (LibraryItem) -> String,
    posterPattern: String,
): MetaPreview = toMetaPreview()
    .copy(releaseInfo = releaseInfoFor(this))
    .let { if (posterPattern.isNotBlank()) it.withCustomPosterUrl(posterPattern) else it }
