package com.nuvio.app.features.details.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.Dp
import com.nuvio.app.features.details.EpisodeRatingsVisibility
import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.details.groupedEpisodesForDisplay
import com.nuvio.app.features.details.seasonSortKey
import com.nuvio.app.features.watchprogress.WatchProgressEntry
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.NuvioBottomSheetActionRow
import com.nuvio.app.core.ui.NuvioNativeLiquidGlassButton
import com.nuvio.app.core.ui.NuvioNativeLiquidGlassSearchField
import com.nuvio.app.core.ui.isNativeLiquidGlassControlsAvailable
import com.nuvio.app.core.ui.NuvioModalBottomSheet
import com.nuvio.app.core.ui.dismissNuvioBottomSheet
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.core.ui.nuvioSafeBottomPadding
import com.nuvio.app.core.ui.glass.LiquidGlassSurface
import com.nuvio.app.core.ui.glass.isLiquidGlassAvailable
import com.nuvio.app.core.ui.glass.isLiquidGlassSurfaceEffectivelyEnabled
import com.nuvio.app.core.ui.glass.liquidGlassForSurface
import com.nuvio.app.core.ui.glass.LiquidGlassTextContrastScope
import com.nuvio.app.core.ui.glass.liquidGlassIconTint
import com.nuvio.app.core.ui.glass.liquidGlassShape
import com.nuvio.app.features.details.MetaVideo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

internal enum class EpisodeSortOrder {
    FirstToLast,
    LastToFirst,
    HighestRated,
    NewestAired,
    UnwatchedFirst,
}

@Stable
internal class EpisodeNavigatorState {
    var selectedSeason by mutableStateOf<Int?>(null)
    var query by mutableStateOf("")
    var sort by mutableStateOf(EpisodeSortOrder.FirstToLast)
    var highlightedEpisodeKey by mutableStateOf<String?>(null)
    var goToSheetVisible by mutableStateOf(false)
    var sortSheetVisible by mutableStateOf(false)

    private var groupedSource: List<MetaVideo>? = null
    private var groupedCache: Map<Int, List<MetaVideo>> = emptyMap()

    fun grouped(videos: List<MetaVideo>, group: () -> Map<Int, List<MetaVideo>>): Map<Int, List<MetaVideo>> {
        if (groupedSource !== videos) {
            groupedSource = videos
            groupedCache = group()
        }
        return groupedCache
    }

    fun selectSeason(season: Int) {
        if (selectedSeason == season) return
        selectedSeason = season
        query = ""
        highlightedEpisodeKey = null
    }
}

@Composable
internal fun rememberEpisodeNavigatorState(metaId: String): EpisodeNavigatorState =
    remember(metaId) { EpisodeNavigatorState() }

internal fun MetaVideo.navigatorKey(): String = "$season:$episode:$id"

internal fun List<MetaVideo>.filteredAndSortedForNavigator(
    query: String,
    sort: EpisodeSortOrder,
    isWatched: (MetaVideo) -> Boolean,
): List<MetaVideo> {
    val trimmed = query.trim()
    val filtered = if (trimmed.isEmpty()) this else filter { it.matchesEpisodeQuery(trimmed) }
    return when (sort) {
        EpisodeSortOrder.FirstToLast -> filtered
        EpisodeSortOrder.LastToFirst -> filtered.asReversed()
        EpisodeSortOrder.HighestRated -> filtered.sortedByDescending { it.imdbRating ?: it.tmdbRating ?: it.rating ?: -1.0 }
        EpisodeSortOrder.NewestAired -> filtered.sortedByDescending { it.released.orEmpty() }
        EpisodeSortOrder.UnwatchedFirst -> filtered.sortedBy { isWatched(it) }
    }
}

private fun MetaVideo.matchesEpisodeQuery(query: String): Boolean {
    query.toIntOrNull()?.let { number -> if (episode == number) return true }
    return title.contains(query, ignoreCase = true) ||
        overview?.contains(query, ignoreCase = true) == true
}

internal fun highlightEpisodeMatches(text: String, query: String?, highlight: SpanStyle): AnnotatedString {
    val needle = query?.trim().orEmpty()
    if (needle.isEmpty() || needle.toIntOrNull() != null) return AnnotatedString(text)
    return buildAnnotatedString {
        var start = 0
        while (start < text.length) {
            val match = text.indexOf(needle, startIndex = start, ignoreCase = true)
            if (match < 0) {
                append(text.substring(start))
                break
            }
            append(text.substring(start, match))
            pushStyle(highlight)
            append(text.substring(match, match + needle.length))
            pop()
            start = match + needle.length
        }
    }
}

internal fun EpisodeSortOrder.labelRes() = when (this) {
    EpisodeSortOrder.FirstToLast -> Res.string.episode_navigator_sort_first_to_last
    EpisodeSortOrder.LastToFirst -> Res.string.episode_navigator_sort_last_to_first
    EpisodeSortOrder.HighestRated -> Res.string.episode_navigator_sort_highest_rated
    EpisodeSortOrder.NewestAired -> Res.string.episode_navigator_sort_newest_aired
    EpisodeSortOrder.UnwatchedFirst -> Res.string.episode_navigator_sort_unwatched_first
}

internal suspend fun EpisodeNavigatorState.flashHighlight(key: String) {
    highlightedEpisodeKey = key
    delay(EPISODE_HIGHLIGHT_MS)
    if (highlightedEpisodeKey == key) highlightedEpisodeKey = null
}

private const val EPISODE_HIGHLIGHT_MS = 1_600L

/**
 * Lets the Studio preview select an entire glass surface without intercepting its real child
 * controls. Runtime callers pass no callback and keep their existing input behavior.
 */
internal fun Modifier.onLiquidGlassPreviewSurfaceTap(
    surface: LiquidGlassSurface,
    onSurfaceSelected: ((LiquidGlassSurface) -> Unit)?,
): Modifier {
    val callback = onSurfaceSelected ?: return this
    return pointerInput(surface, callback) {
        awaitEachGesture {
            awaitFirstDown(pass = PointerEventPass.Initial, requireUnconsumed = false)
            if (waitForUpOrCancellation(pass = PointerEventPass.Initial) != null) {
                callback(surface)
            }
        }
    }
}

@Composable
internal fun EpisodeNavigatorBar(
    state: EpisodeNavigatorState,
    shownCount: Int,
    totalCount: Int,
    onSubmitNumber: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onPreviewSurfaceSelected: ((LiquidGlassSurface) -> Unit)? = null,
    selectedPreviewSurface: LiquidGlassSurface? = null,
) {
    val tokens = MaterialTheme.nuvio
    val searchSurface = LiquidGlassSurface.DETAIL_EPISODE_SEARCH
    val searchGlassEnabled = isLiquidGlassAvailable() && isLiquidGlassSurfaceEffectivelyEnabled(searchSurface)
    val nativeSearchPreview = onPreviewSurfaceSelected != null || selectedPreviewSurface != null
    val nativeSearchEnabled = isNativeLiquidGlassControlsAvailable() &&
        isLiquidGlassSurfaceEffectivelyEnabled(
            searchSurface,
            preview = nativeSearchPreview,
            platformSupported = true,
        ) &&
        (!nativeSearchPreview || selectedPreviewSurface == null || selectedPreviewSurface == searchSurface)
    val fieldShape = liquidGlassShape(searchSurface, RoundedCornerShape(14.dp))
    val searchSelected = selectedPreviewSurface == searchSurface
    val searchSurfaceModifier = if (searchGlassEnabled) {
        Modifier.liquidGlassForSurface(
            surface = searchSurface,
            shape = fieldShape,
            tint = tokens.colors.surfaceSheet.copy(alpha = 0.24f),
            fallback = tokens.colors.surfaceSheet.copy(alpha = 0.6f),
            blurRadius = 12.dp,
            refractionHeight = 6.dp,
            refractionAmount = 9.dp,
        )
    } else {
        Modifier
            .clip(fieldShape)
            .background(tokens.colors.surfaceSheet.copy(alpha = 0.6f))
            .border(
                width = if (searchSelected) 2.dp else 1.dp,
                color = if (searchSelected) MaterialTheme.colorScheme.primary else tokens.colors.borderDefault,
                shape = fieldShape,
            )
    }
    val searchSelectionBorder = if (searchGlassEnabled && searchSelected) {
        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, fieldShape)
    } else {
        Modifier
    }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LiquidGlassTextContrastScope(enabled = searchGlassEnabled) {
                if (nativeSearchEnabled) {
                    NuvioNativeLiquidGlassSearchField(
                        value = state.query,
                        placeholder = stringResource(Res.string.episode_navigator_search_hint),
                        accessibilityLabel = stringResource(Res.string.episode_navigator_search_hint),
                        onValueChange = { state.query = it },
                        onSearch = { state.query.trim().toIntOrNull()?.let(onSubmitNumber) },
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                            .then(searchSurfaceModifier)
                            .then(searchSelectionBorder)
                            .onLiquidGlassPreviewSurfaceTap(
                                surface = searchSurface,
                                onSurfaceSelected = onPreviewSurfaceSelected,
                            )
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = liquidGlassIconTint(searchSurface, tokens.colors.textMuted),
                            modifier = Modifier.size(18.dp),
                        )
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                            if (state.query.isEmpty()) {
                                Text(
                                    text = stringResource(Res.string.episode_navigator_search_hint),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = tokens.colors.textMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            BasicTextField(
                                value = state.query,
                                onValueChange = { state.query = it },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = tokens.colors.textPrimary),
                                cursorBrush = SolidColor(tokens.colors.accent),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(
                                    onSearch = { state.query.trim().toIntOrNull()?.let(onSubmitNumber) },
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onFocusChanged { focus ->
                                        if (focus.isFocused) onPreviewSurfaceSelected?.invoke(searchSurface)
                                    },
                            )
                        }
                        if (state.query.isNotEmpty()) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(Res.string.episode_navigator_clear_search),
                                tint = tokens.colors.textMuted,
                                modifier = Modifier
                                    .size(18.dp)
                                    .clickable { state.query = "" },
                            )
                        }
                    }
                }
            }
            EpisodeNavigatorChip(
                text = stringResource(Res.string.episode_navigator_go_to),
                glassSurface = LiquidGlassSurface.DETAIL_EPISODE_GO_TO,
                onPreviewSurfaceSelected = onPreviewSurfaceSelected,
                selectedPreviewSurface = selectedPreviewSurface,
                onClick = { state.goToSheetVisible = true },
            )
            EpisodeNavigatorChip(
                text = stringResource(Res.string.episode_navigator_sort),
                glassSurface = LiquidGlassSurface.DETAIL_EPISODE_SORT,
                onPreviewSurfaceSelected = onPreviewSurfaceSelected,
                selectedPreviewSurface = selectedPreviewSurface,
                onClick = { state.sortSheetVisible = true },
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(Res.string.episode_navigator_count, shownCount, totalCount),
                style = MaterialTheme.typography.labelMedium,
                color = tokens.colors.textMuted,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(state.sort.labelRes()),
                style = MaterialTheme.typography.labelMedium,
                color = tokens.colors.textMuted,
            )
        }
    }
}

@Composable
private fun EpisodeNavigatorChip(
    text: String,
    onClick: () -> Unit,
    glassSurface: LiquidGlassSurface? = null,
    onPreviewSurfaceSelected: ((LiquidGlassSurface) -> Unit)? = null,
    selectedPreviewSurface: LiquidGlassSurface? = null,
) {
    val tokens = MaterialTheme.nuvio
    val nativeShape = RoundedCornerShape(14.dp)
    val surface = glassSurface
    val isPreview = onPreviewSurfaceSelected != null || selectedPreviewSurface != null
    val nativeGlassEnabled = surface != null &&
        isNativeLiquidGlassControlsAvailable() &&
        isLiquidGlassSurfaceEffectivelyEnabled(
            surface,
            preview = isPreview,
            platformSupported = true,
        ) &&
        (!isPreview || selectedPreviewSurface == null || selectedPreviewSurface == surface)
    if (nativeGlassEnabled) {
        NuvioNativeLiquidGlassButton(
            title = text,
            accessibilityLabel = text,
            onClick = {
                if (isPreview && surface != null) {
                    onPreviewSurfaceSelected?.invoke(surface)
                } else {
                    onClick()
                }
            },
            modifier = Modifier.heightIn(min = 44.dp),
        )
        return
    }
    val glassEnabled = surface != null && isLiquidGlassAvailable() && isLiquidGlassSurfaceEffectivelyEnabled(surface)
    val shape = surface?.let { liquidGlassShape(it, nativeShape) } ?: nativeShape
    val isSelected = surface != null && selectedPreviewSurface == surface
    val containerModifier = if (glassEnabled && surface != null) {
        Modifier.liquidGlassForSurface(
            surface = surface,
            shape = shape,
            tint = tokens.colors.surfaceSheet.copy(alpha = 0.24f),
            fallback = tokens.colors.surfaceSheet.copy(alpha = 0.6f),
            blurRadius = 12.dp,
            refractionHeight = 6.dp,
            refractionAmount = 9.dp,
        )
    } else {
        Modifier
            .clip(shape)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else tokens.colors.borderDefault,
                shape = shape,
            )
    }
    val selectedOutlineModifier = if (glassEnabled && isSelected) {
        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape)
    } else {
        Modifier
    }
    LiquidGlassTextContrastScope(enabled = glassEnabled) {
    Box(
        modifier = Modifier
            .heightIn(min = 44.dp)
            .then(containerModifier)
            .then(selectedOutlineModifier)
            .clickable {
                surface?.let { onPreviewSurfaceSelected?.invoke(it) }
                onClick()
            }
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = tokens.colors.textPrimary,
            maxLines = 1,
        )
    }
    }
}

@Composable
internal fun EpisodeNavigatorEmptyState(query: String, onClear: () -> Unit, modifier: Modifier = Modifier) {
    val tokens = MaterialTheme.nuvio
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(Res.string.episode_navigator_no_match, query.trim()),
            style = MaterialTheme.typography.bodyMedium,
            color = tokens.colors.textMuted,
            textAlign = TextAlign.Center,
        )
        EpisodeNavigatorChip(text = stringResource(Res.string.episode_navigator_clear_search), onClick = onClear)
    }
}

@Composable
internal fun Modifier.episodeJumpHighlight(active: Boolean): Modifier {
    if (!active) return this
    val accent = MaterialTheme.nuvio.colors.accent
    return this.border(2.dp, accent, RoundedCornerShape(16.dp))
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun EpisodeGoToSheet(
    seasonLabel: String,
    episodes: List<MetaVideo>,
    isWatched: (MetaVideo) -> Boolean,
    onPick: (MetaVideo) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val tokens = MaterialTheme.nuvio
    var input by remember { mutableStateOf("") }
    var outOfRange by remember { mutableStateOf(false) }
    val numbered = remember(episodes) { episodes.filter { it.episode != null } }
    val firstNumber = numbered.minOfOrNull { it.episode ?: 0 } ?: 0
    val lastNumber = numbered.maxOfOrNull { it.episode ?: 0 } ?: 0
    val upNextKey = remember(numbered, isWatched) {
        val lastWatched = numbered.indexOfLast(isWatched)
        numbered.drop(lastWatched + 1).firstOrNull { !isWatched(it) }?.navigatorKey()
    }

    fun close(then: () -> Unit = {}) {
        scope.launch {
            dismissNuvioBottomSheet(sheetState = sheetState, onDismiss = onDismiss)
            then()
        }
    }

    fun submit() {
        val number = input.trim().toIntOrNull() ?: return
        val target = numbered.firstOrNull { it.episode == number }
        if (target == null) {
            outOfRange = true
        } else {
            close { onPick(target) }
        }
    }

    NuvioModalBottomSheet(onDismissRequest = { close() }, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = nuvioSafeBottomPadding(16.dp)),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(Res.string.episode_navigator_go_to_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = tokens.colors.textPrimary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                val fieldShape = RoundedCornerShape(14.dp)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .clip(fieldShape)
                        .border(1.dp, tokens.colors.accent, fieldShape)
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (input.isEmpty()) {
                        Text(
                            text = stringResource(Res.string.episode_navigator_episode_number),
                            style = MaterialTheme.typography.bodyLarge,
                            color = tokens.colors.textMuted,
                        )
                    }
                    BasicTextField(
                        value = input,
                        onValueChange = { value ->
                            input = value.filter(Char::isDigit).take(5)
                            outOfRange = false
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = tokens.colors.textPrimary),
                        cursorBrush = SolidColor(tokens.colors.accent),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { submit() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Box(
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(tokens.colors.accent)
                        .clickable { submit() }
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(Res.string.episode_navigator_go),
                        style = MaterialTheme.typography.labelLarge,
                        color = tokens.colors.onAccent,
                    )
                }
            }
            Text(
                text = if (outOfRange) {
                    stringResource(Res.string.episode_navigator_out_of_range, seasonLabel, firstNumber, lastNumber)
                } else {
                    stringResource(Res.string.episode_navigator_go_to_help)
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (outOfRange) tokens.colors.danger else tokens.colors.textMuted,
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                numbered.forEach { episode ->
                    val watched = isWatched(episode)
                    val upNext = episode.navigatorKey() == upNextKey
                    val shape = RoundedCornerShape(12.dp)
                    Box(
                        modifier = Modifier
                            .width(52.dp)
                            .height(44.dp)
                            .clip(shape)
                            .border(
                                width = if (upNext) 2.dp else 1.dp,
                                color = if (upNext) tokens.colors.accent else tokens.colors.borderDefault,
                                shape = shape,
                            )
                            .clickable { close { onPick(episode) } }
                            .drawBehind {
                                if (watched) {
                                    val y = size.height - 8.dp.toPx()
                                    drawLine(
                                        color = tokens.colors.accent,
                                        start = Offset(size.width * 0.3f, y),
                                        end = Offset(size.width * 0.7f, y),
                                        strokeWidth = 2.dp.toPx(),
                                    )
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = episode.episode.toString(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (watched) tokens.colors.textMuted else tokens.colors.textPrimary,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EpisodeSortSheet(
    current: EpisodeSortOrder,
    onSelect: (EpisodeSortOrder) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val tokens = MaterialTheme.nuvio
    NuvioModalBottomSheet(
        onDismissRequest = {
            scope.launch { dismissNuvioBottomSheet(sheetState = sheetState, onDismiss = onDismiss) }
        },
        sheetState = sheetState,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = nuvioSafeBottomPadding(16.dp))) {
            Text(
                text = stringResource(Res.string.episode_navigator_sort_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = tokens.colors.textPrimary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            EpisodeSortOrder.entries.forEach { order ->
                NuvioBottomSheetActionRow(
                    title = stringResource(order.labelRes()),
                    selected = order == current,
                    onClick = {
                        onSelect(order)
                        scope.launch { dismissNuvioBottomSheet(sheetState = sheetState, onDismiss = onDismiss) }
                    },
                    trailingContent = if (order == current) {
                        {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = tokens.colors.accent,
                            )
                        }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

@Composable
internal fun episodeMatchStyle(): SpanStyle =
    SpanStyle(color = MaterialTheme.nuvio.colors.accent, fontWeight = FontWeight.SemiBold, background = Color.Transparent)


@OptIn(ExperimentalFoundationApi::class)
internal fun LazyListScope.detailEpisodeListItems(
    meta: MetaDetails,
    navigator: EpisodeNavigatorState,
    listState: LazyListState,
    firstItemIndex: Int,
    horizontalPadding: Dp,
    contentMaxWidth: Dp,
    stickyBackground: Color,
    preferredSeasonNumber: Int?,
    progressByVideoId: Map<String, WatchProgressEntry>,
    watchedKeys: Set<String>,
    episodeRatingsVisibility: EpisodeRatingsVisibility,
    blurUnwatchedEpisodes: Boolean,
    onEpisodeClick: (MetaVideo) -> Unit,
    onEpisodeLongPress: (MetaVideo) -> Unit,
    onSeasonLongPress: (Int) -> Unit,
): Int {
    val grouped = navigator.grouped(meta.videos) { meta.groupedEpisodesForDisplay() }
    if (grouped.isEmpty()) return 0
    val seasons = grouped.keys.sortedBy(::seasonSortKey)
    val season = navigator.selectedSeason?.takeIf { it in grouped }
        ?: preferredSeasonNumber?.takeIf { it in grouped }
        ?: seasons.first()
    val seasonEpisodes = grouped.getValue(season)
    val isWatched: (MetaVideo) -> Boolean = { episode ->
        meta.isEpisodeWatchedForNavigator(episode, progressByVideoId, watchedKeys)
    }
    val query = navigator.query
    val shown = seasonEpisodes.filteredAndSortedForNavigator(query, navigator.sort, isWatched)
    var emitted = 0

    item(key = "detail-episodes-header") {
        EpisodeSectionContainer(horizontalPadding, contentMaxWidth, bottom = 16.dp) {
            DetailSeriesListHeader(
                meta = meta,
                groupedEpisodes = grouped,
                seasons = seasons,
                currentSeason = season,
                horizontalScrollPadding = horizontalPadding,
                onSeasonSelect = navigator::selectSeason,
                onSeasonLongPress = onSeasonLongPress,
            )
        }
    }
    emitted += 1

    val firstEpisodeIndex = firstItemIndex + emitted + 1
    run {
        stickyHeader(key = STICKY_BAR_KEY) {
            EpisodeNavigatorStickyBar(
                meta = meta,
                season = season,
                navigator = navigator,
                listState = listState,
                seasonEpisodes = seasonEpisodes,
                shownCount = shown.size,
                firstEpisodeIndex = firstEpisodeIndex,
                isWatched = isWatched,
                background = stickyBackground,
                horizontalPadding = horizontalPadding,
                contentMaxWidth = contentMaxWidth,
            )
        }
        emitted += 1
    }

    if (shown.isEmpty()) {
        item(key = "detail-episodes-empty") {
            EpisodeSectionContainer(horizontalPadding, contentMaxWidth, bottom = 20.dp) {
                EpisodeNavigatorEmptyState(query = query, onClear = { navigator.query = "" })
            }
        }
        emitted += 1
    } else {
        items(shown, key = { episode -> "detail-episode-${episode.navigatorKey()}" }) { episode ->
            EpisodeSectionContainer(horizontalPadding, contentMaxWidth, bottom = 12.dp) {
                DetailSeriesListEpisode(
                    meta = meta,
                    episode = episode,
                    progressByVideoId = progressByVideoId,
                    watchedKeys = watchedKeys,
                    episodeRatings = emptyMap(),
                    episodeRatingsVisibility = episodeRatingsVisibility,
                    blurUnwatchedEpisodes = blurUnwatchedEpisodes,
                    onEpisodeClick = onEpisodeClick,
                    onEpisodeLongPress = onEpisodeLongPress,
                    highlightQuery = query,
                    jumpHighlighted = navigator.highlightedEpisodeKey == episode.navigatorKey(),
                )
            }
        }
        emitted += shown.size
    }

    item(key = "detail-episodes-end") { Spacer(modifier = Modifier.height(8.dp)) }
    emitted += 1
    return emitted
}

private const val STICKY_BAR_KEY = "detail-episodes-navigator-bar"

@Composable
private fun EpisodeSectionContainer(
    horizontalPadding: Dp,
    contentMaxWidth: Dp,
    bottom: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding)
            .padding(bottom = bottom),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (contentMaxWidth == Dp.Unspecified) Modifier else Modifier.widthIn(max = contentMaxWidth)),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

@Composable
private fun EpisodeNavigatorStickyBar(
    meta: MetaDetails,
    season: Int,
    navigator: EpisodeNavigatorState,
    listState: LazyListState,
    seasonEpisodes: List<MetaVideo>,
    shownCount: Int,
    firstEpisodeIndex: Int,
    isWatched: (MetaVideo) -> Boolean,
    background: Color,
    horizontalPadding: Dp,
    contentMaxWidth: Dp,
) {
    val density = LocalDensity.current
    val floatingHeaderPx = with(density) { detailFloatingHeaderHeight().toPx() }
    var barHeightPx by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    var orderInitialized by remember { mutableStateOf(false) }
    LaunchedEffect(navigator.sort, navigator.query, navigator.selectedSeason) {
        if (!orderInitialized) {
            orderInitialized = true
            return@LaunchedEffect
        }
        val barIndex = firstEpisodeIndex - 1
        if (listState.firstVisibleItemIndex >= barIndex) {
            listState.scrollToItem(barIndex)
            listState.scrollBy(-floatingHeaderPx)
        }
    }

    val jumpTo: (MetaVideo) -> Unit = { target ->
        scope.launch {
            val key = target.navigatorKey()
            if (navigator.query.isNotEmpty() &&
                seasonEpisodes.filteredAndSortedForNavigator(navigator.query, navigator.sort, isWatched)
                    .none { it.navigatorKey() == key }
            ) {
                navigator.query = ""
                withFrameNanos { }
                withFrameNanos { }
            }
            val position = seasonEpisodes
                .filteredAndSortedForNavigator(navigator.query, navigator.sort, isWatched)
                .indexOfFirst { it.navigatorKey() == key }
            if (position < 0) return@launch
            listState.animateScrollToItem(firstEpisodeIndex + position)
            listState.animateScrollBy(-(floatingHeaderPx + barHeightPx))
            navigator.flashHighlight(key)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                val offset = listState.layoutInfo.visibleItemsInfo
                    .firstOrNull { it.key == STICKY_BAR_KEY }
                    ?.offset
                    ?: Int.MAX_VALUE
                translationY = (floatingHeaderPx - offset).coerceIn(0f, floatingHeaderPx)
            }
            .onSizeChanged { barHeightPx = it.height },
    ) {
        EpisodeSectionContainer(horizontalPadding, contentMaxWidth, bottom = 0.dp, modifier = Modifier.padding(vertical = 10.dp)) {
            EpisodeNavigatorBar(
                state = navigator,
                shownCount = shownCount,
                totalCount = seasonEpisodes.size,
                onSubmitNumber = { number ->
                    seasonEpisodes.firstOrNull { it.episode == number }?.let(jumpTo)
                },
            )
        }
    }

    if (navigator.goToSheetVisible) {
        EpisodeGoToSheet(
            seasonLabel = seasonDisplayLabel(meta, season),
            episodes = seasonEpisodes,
            isWatched = isWatched,
            onPick = jumpTo,
            onDismiss = { navigator.goToSheetVisible = false },
        )
    }
    if (navigator.sortSheetVisible) {
        EpisodeSortSheet(
            current = navigator.sort,
            onSelect = { navigator.sort = it },
            onDismiss = { navigator.sortSheetVisible = false },
        )
    }
}
