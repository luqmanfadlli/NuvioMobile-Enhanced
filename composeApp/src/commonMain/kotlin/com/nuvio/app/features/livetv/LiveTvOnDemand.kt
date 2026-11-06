package com.nuvio.app.features.livetv

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import co.touchlab.kermit.Logger
import com.nuvio.app.core.ui.NuvioModalBottomSheet
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.dismissNuvioBottomSheet
import com.nuvio.app.core.ui.nuvio
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.live_tv_on_demand_episode
import nuvio.composeapp.generated.resources.live_tv_on_demand_no_episodes
import nuvio.composeapp.generated.resources.live_tv_on_demand_season
import org.jetbrains.compose.resources.stringResource

// ---------------------------------------------------------------------------------------------
// Models
// ---------------------------------------------------------------------------------------------

enum class LiveTvVodKind { Movies, Series }

data class LiveTvVodCategory(
    val id: String,
    val name: String,
)

data class LiveTvVodItem(
    val id: String,
    val remoteId: String,
    val kind: LiveTvVodKind,
    val name: String,
    val posterUrl: String? = null,
    val categoryId: String? = null,
    val rating: Double? = null,
    val plot: String? = null,
    val year: String? = null,
    val extension: String? = null,
    val addedSeconds: Long? = null,
)

data class LiveTvVodEpisode(
    val id: String,
    val season: Int,
    val episodeNumber: Int,
    val title: String,
    val plot: String? = null,
    val imageUrl: String? = null,
    val durationText: String? = null,
    val streamUrl: String,
    val extension: String,
)

data class LiveTvVodCatalog(
    val categories: List<LiveTvVodCategory> = emptyList(),
    val items: List<LiveTvVodItem> = emptyList(),
)

/** What the app shell needs to open an On Demand title in the regular (seekable) player. */
data class LiveTvVodPlayback(
    val id: String,
    val title: String,
    val streamUrl: String,
    val headers: Map<String, String>,
    val streamType: String?,
    val posterUrl: String?,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val episodeTitle: String? = null,
    val episodeThumbnail: String? = null,
)

data class LiveTvVodCatalogState(
    val isLoading: Boolean = false,
    val hasLoaded: Boolean = false,
    val errorMessage: String? = null,
    val catalog: LiveTvVodCatalog = LiveTvVodCatalog(),
)

data class LiveTvVodEpisodesState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val episodes: List<LiveTvVodEpisode> = emptyList(),
)

data class LiveTvOnDemandUiState(
    val movies: LiveTvVodCatalogState = LiveTvVodCatalogState(),
    val series: LiveTvVodCatalogState = LiveTvVodCatalogState(),
    val episodes: Map<String, LiveTvVodEpisodesState> = emptyMap(),
) {
    fun catalog(kind: LiveTvVodKind): LiveTvVodCatalogState = when (kind) {
        LiveTvVodKind.Movies -> movies
        LiveTvVodKind.Series -> series
    }
}

// ---------------------------------------------------------------------------------------------
// Repository (Xtream Codes VOD + series)
// ---------------------------------------------------------------------------------------------

object LiveTvOnDemandRepository {
    private val log = Logger.withTag("LiveTvOnDemand")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _uiState = MutableStateFlow(LiveTvOnDemandUiState())
    val uiState: StateFlow<LiveTvOnDemandUiState> = _uiState.asStateFlow()

    private var settingsSignature: String? = null
    private val catalogJobs = mutableMapOf<LiveTvVodKind, Job>()

    /** Xtream settings that can serve VOD, or null when no Xtream provider is active. */
    fun activeSettings(): LiveTvXtreamSettings? =
        LiveTvRepository.uiState.value.xtreamSettings.takeIf { it.isEnabled && it.isConfigured }

    private fun resetIfSettingsChanged(settings: LiveTvXtreamSettings) {
        val signature = "${settings.serverUrl}|${settings.username}|${settings.password}"
        if (settingsSignature == signature) return
        settingsSignature = signature
        catalogJobs.values.forEach(Job::cancel)
        catalogJobs.clear()
        _uiState.value = LiveTvOnDemandUiState()
    }

    fun ensureLoaded(kind: LiveTvVodKind) {
        val settings = activeSettings() ?: return
        resetIfSettingsChanged(settings)
        val current = _uiState.value.catalog(kind)
        if (current.hasLoaded || current.isLoading) return
        load(kind, settings)
    }

    fun refresh(kind: LiveTvVodKind) {
        val settings = activeSettings() ?: return
        resetIfSettingsChanged(settings)
        load(kind, settings)
    }

    private fun load(kind: LiveTvVodKind, settings: LiveTvXtreamSettings) {
        catalogJobs[kind]?.cancel()
        updateCatalog(kind) { it.copy(isLoading = true, errorMessage = null) }
        catalogJobs[kind] = scope.launch {
            try {
                val catalog = kotlinx.coroutines.withContext(Dispatchers.Default) {
                    fetchXtreamVodCatalog(settings, kind)
                }
                updateCatalog(kind) {
                    LiveTvVodCatalogState(isLoading = false, hasLoaded = true, catalog = catalog)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                log.w(error) { "Failed to load Xtream ${kind.name}" }
                updateCatalog(kind) {
                    it.copy(isLoading = false, hasLoaded = true, errorMessage = error.message ?: error.toString())
                }
            }
        }
    }

    fun ensureEpisodes(seriesId: String) {
        val settings = activeSettings() ?: return
        val existing = _uiState.value.episodes[seriesId]
        if (existing != null && (existing.isLoading || existing.errorMessage == null)) return
        _uiState.value = _uiState.value.copy(
            episodes = _uiState.value.episodes + (seriesId to LiveTvVodEpisodesState(isLoading = true)),
        )
        scope.launch {
            val result = try {
                val episodes = kotlinx.coroutines.withContext(Dispatchers.Default) {
                    fetchXtreamSeriesEpisodes(settings, seriesId)
                }
                LiveTvVodEpisodesState(isLoading = false, episodes = episodes)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                log.w(error) { "Failed to load series $seriesId" }
                LiveTvVodEpisodesState(isLoading = false, errorMessage = error.message ?: error.toString())
            }
            _uiState.value = _uiState.value.copy(episodes = _uiState.value.episodes + (seriesId to result))
        }
    }

    fun moviePlayback(item: LiveTvVodItem): LiveTvVodPlayback? {
        val settings = activeSettings() ?: return null
        return LiveTvVodPlayback(
            id = item.id,
            title = item.name,
            streamUrl = xtreamMovieStreamUrl(settings, item),
            headers = liveTvVodRequestHeaders,
            streamType = item.extension ?: "mp4",
            posterUrl = item.posterUrl,
        )
    }

    fun episodePlayback(series: LiveTvVodItem, episode: LiveTvVodEpisode): LiveTvVodPlayback =
        LiveTvVodPlayback(
            id = "${series.id}:${episode.id}",
            title = series.name,
            streamUrl = episode.streamUrl,
            headers = liveTvVodRequestHeaders,
            streamType = episode.extension,
            posterUrl = series.posterUrl,
            seasonNumber = episode.season,
            episodeNumber = episode.episodeNumber,
            episodeTitle = episode.title,
            episodeThumbnail = episode.imageUrl,
        )

    private fun updateCatalog(kind: LiveTvVodKind, transform: (LiveTvVodCatalogState) -> LiveTvVodCatalogState) {
        val state = _uiState.value
        _uiState.value = when (kind) {
            LiveTvVodKind.Movies -> state.copy(movies = transform(state.movies))
            LiveTvVodKind.Series -> state.copy(series = transform(state.series))
        }
    }
}

internal fun filterLiveTvVodItems(
    items: List<LiveTvVodItem>,
    categoryId: String?,
    searchQuery: String,
): List<LiveTvVodItem> {
    val query = searchQuery.trim()
    return items.filter { item ->
        (categoryId == null || item.categoryId == categoryId) &&
            (query.isEmpty() || item.name.contains(query, ignoreCase = true))
    }
}

// ---------------------------------------------------------------------------------------------
// UI
// ---------------------------------------------------------------------------------------------

internal val LiveTvVodMinCellWidth = 104.dp

internal const val LIVE_TV_VOD_COUNT_ALL = " all"

@Composable
internal fun LiveTvOnDemandControls(
    kind: LiveTvVodKind,
    moviesLabel: String,
    seriesLabel: String,
    allLabel: String,
    categories: List<LiveTvVodCategory>,
    selectedCategoryId: String?,
    counts: Map<String, Int>,
    onKindSelected: (LiveTvVodKind) -> Unit,
    onCategorySelected: (String?) -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.listGap),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8)) {
            LiveTvPanelFilterChip(
                label = moviesLabel,
                selected = kind == LiveTvVodKind.Movies,
                onClick = { onKindSelected(LiveTvVodKind.Movies) },
            )
            LiveTvPanelFilterChip(
                label = seriesLabel,
                selected = kind == LiveTvVodKind.Series,
                onClick = { onKindSelected(LiveTvVodKind.Series) },
            )
        }
        if (categories.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8),
                ) {
                    LiveTvPanelFilterChip(
                        label = allLabel,
                        selected = selectedCategoryId == null,
                        onClick = { onCategorySelected(null) },
                        count = counts[LIVE_TV_VOD_COUNT_ALL],
                    )
                    categories.forEach { category ->
                        LiveTvPanelFilterChip(
                            label = category.name,
                            selected = selectedCategoryId == category.id,
                            onClick = { onCategorySelected(category.id) },
                            count = counts[category.id] ?: 0,
                        )
                    }
                }
            }
        }
    }
}

internal fun LazyListScope.liveTvVodGrid(
    items: List<LiveTvVodItem>,
    columns: Int,
    gap: Dp,
    onItemClick: (LiveTvVodItem) -> Unit,
) {
    val rows = items.chunked(columns)
    items(
        count = rows.size,
        key = { index -> "vod-grid:$columns:${rows[index].first().id}" },
    ) { index ->
        val row = rows[index]
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            row.forEach { item ->
                LiveTvVodPosterCell(
                    item = item,
                    onClick = { onItemClick(item) },
                    modifier = Modifier.weight(1f),
                )
            }
            repeat(columns - row.size) {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun LiveTvVodPosterCell(
    item: LiveTvVodItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = MaterialTheme.nuvio
    Column(
        modifier = modifier
            .clip(tokens.shapes.compactCard)
            .clickable(onClick = onClick)
            .padding(bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(tokens.shapes.compactCard)
                .background(tokens.colors.surfaceCard),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Movie,
                contentDescription = null,
                tint = tokens.colors.textMuted,
                modifier = Modifier.size(28.dp),
            )
            if (!item.posterUrl.isNullOrBlank()) {
                AsyncImage(
                    model = item.posterUrl,
                    contentDescription = item.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            item.rating?.let { rating ->
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Star,
                        contentDescription = null,
                        tint = tokens.colors.warning,
                        modifier = Modifier.size(11.dp),
                    )
                    Text(
                        text = formatVodRating(rating),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                    )
                }
            }
        }
        Text(
            text = item.name,
            style = MaterialTheme.typography.labelLarge,
            color = tokens.colors.textPrimary,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        item.year?.let { year ->
            Text(
                text = year,
                style = MaterialTheme.typography.bodySmall,
                color = tokens.colors.textMuted,
                maxLines = 1,
            )
        }
    }
}

private fun formatVodRating(rating: Double): String {
    val normalized = if (rating > 10.0) rating / 10.0 else rating
    val tenths = (normalized * 10).toInt()
    return "${tenths / 10}.${tenths % 10}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LiveTvSeriesSheet(
    series: LiveTvVodItem,
    onEpisodeClick: (LiveTvVodEpisode) -> Unit,
    onDismiss: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()
    val dismiss: () -> Unit = {
        coroutineScope.launch { dismissNuvioBottomSheet(sheetState = sheetState, onDismiss = onDismiss) }
    }
    LaunchedEffect(series.remoteId) {
        LiveTvOnDemandRepository.ensureEpisodes(series.remoteId)
    }
    val uiState by LiveTvOnDemandRepository.uiState.collectAsState()
    val episodesState = uiState.episodes[series.remoteId] ?: LiveTvVodEpisodesState()
    val seasons = remember(episodesState.episodes) { episodesState.episodes.map { it.season }.distinct() }
    var selectedSeason by rememberSaveable(series.id) { mutableStateOf<Int?>(null) }
    val activeSeason = selectedSeason?.takeIf { it in seasons } ?: seasons.firstOrNull()
    val seasonEpisodes = remember(episodesState.episodes, activeSeason) {
        episodesState.episodes.filter { it.season == activeSeason }
    }

    NuvioModalBottomSheet(
        onDismissRequest = dismiss,
        sheetState = sheetState,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .width(84.dp)
                            .aspectRatio(2f / 3f)
                            .clip(tokens.shapes.compactCard)
                            .background(tokens.colors.surfaceCard),
                    ) {
                        if (!series.posterUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = series.posterUrl,
                                contentDescription = series.name,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        }
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = series.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = tokens.colors.textPrimary,
                            fontWeight = FontWeight.SemiBold,
                        )
                        series.year?.let {
                            Text(text = it, style = MaterialTheme.typography.bodySmall, color = tokens.colors.textMuted)
                        }
                        series.plot?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.colors.textSecondary,
                                maxLines = 5,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            if (seasons.size > 1) {
                item(key = "seasons") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8),
                    ) {
                        seasons.forEach { season ->
                            LiveTvPanelFilterChip(
                                label = stringResource(Res.string.live_tv_on_demand_season, season),
                                selected = season == activeSeason,
                                onClick = { selectedSeason = season },
                            )
                        }
                    }
                }
            }
            when {
                episodesState.isLoading -> item(key = "loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = tokens.colors.accent, strokeWidth = 2.dp)
                    }
                }

                episodesState.errorMessage != null || seasonEpisodes.isEmpty() -> item(key = "empty") {
                    Text(
                        text = episodesState.errorMessage ?: stringResource(Res.string.live_tv_on_demand_no_episodes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = tokens.colors.textMuted,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
                    )
                }

                else -> items(seasonEpisodes, key = { "episode:${it.id}" }) { episode ->
                    LiveTvEpisodeRow(
                        episode = episode,
                        onClick = {
                            onEpisodeClick(episode)
                            dismiss()
                        },
                    )
                }
            }
            item(key = "bottom-space") { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun LiveTvEpisodeRow(
    episode: LiveTvVodEpisode,
    onClick: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        onClick = onClick,
        color = tokens.colors.surface,
        shape = tokens.shapes.compactCard,
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(112.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(tokens.colors.surfaceCard),
                contentAlignment = Alignment.Center,
            ) {
                if (!episode.imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = episode.imageUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(4.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(Res.string.live_tv_on_demand_episode, episode.episodeNumber, episode.title),
                    style = MaterialTheme.typography.labelLarge,
                    color = tokens.colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                episode.durationText?.let {
                    Text(text = it, style = MaterialTheme.typography.bodySmall, color = tokens.colors.textMuted)
                }
                episode.plot?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.colors.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
