package com.nuvio.app.features.livetv

import com.nuvio.app.core.ui.nuvioScreenSideBleed
import androidx.compose.ui.input.pointer.pointerInput
import com.nuvio.app.core.ui.background.appBackgroundCover
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.LocalDynamicArtworkBackgroundActive
import com.nuvio.app.core.ui.NuvioIconActionButton
import com.nuvio.app.core.ui.NuvioInputField
import com.nuvio.app.core.ui.NuvioScreen
import com.nuvio.app.core.ui.NuvioScreenHeader
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.core.ui.nuvioPlatformExtraTopPadding
import com.nuvio.app.features.home.components.HomeEmptyStateCard
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_retry
import nuvio.composeapp.generated.resources.live_tv_button_continue
import nuvio.composeapp.generated.resources.live_tv_empty_message
import nuvio.composeapp.generated.resources.live_tv_empty_title
import nuvio.composeapp.generated.resources.live_tv_filter_choose_category
import nuvio.composeapp.generated.resources.live_tv_filter_favorites
import nuvio.composeapp.generated.resources.live_tv_group_all_channels
import nuvio.composeapp.generated.resources.live_tv_group_uncategorized
import nuvio.composeapp.generated.resources.live_tv_last_watched_title
import nuvio.composeapp.generated.resources.live_tv_load_failed
import nuvio.composeapp.generated.resources.live_tv_no_matching_channels_message
import nuvio.composeapp.generated.resources.live_tv_no_matching_channels_title
import nuvio.composeapp.generated.resources.live_tv_no_playlist_message
import nuvio.composeapp.generated.resources.live_tv_no_playlist_title
import nuvio.composeapp.generated.resources.live_tv_search_placeholder
import nuvio.composeapp.generated.resources.live_tv_title
import nuvio.composeapp.generated.resources.live_tv_action_clear_recents
import nuvio.composeapp.generated.resources.live_tv_action_search
import nuvio.composeapp.generated.resources.live_tv_action_grid_view
import nuvio.composeapp.generated.resources.live_tv_action_list_view
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import nuvio.composeapp.generated.resources.live_tv_filter_recents
import nuvio.composeapp.generated.resources.live_tv_section_channels
import nuvio.composeapp.generated.resources.live_tv_guide_empty_message
import nuvio.composeapp.generated.resources.live_tv_guide_empty_title
import nuvio.composeapp.generated.resources.live_tv_on_demand_empty_message
import nuvio.composeapp.generated.resources.live_tv_on_demand_empty_title
import nuvio.composeapp.generated.resources.live_tv_on_demand_load_failed
import nuvio.composeapp.generated.resources.live_tv_on_demand_movies
import nuvio.composeapp.generated.resources.live_tv_on_demand_search_placeholder
import nuvio.composeapp.generated.resources.live_tv_on_demand_series
import nuvio.composeapp.generated.resources.live_tv_on_demand_unavailable_message
import nuvio.composeapp.generated.resources.live_tv_on_demand_unavailable_title
import nuvio.composeapp.generated.resources.live_tv_sports_empty_message
import nuvio.composeapp.generated.resources.live_tv_sports_empty_title
import nuvio.composeapp.generated.resources.live_tv_sports_filter_live
import nuvio.composeapp.generated.resources.live_tv_sports_load_failed
import androidx.compose.runtime.produceState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import nuvio.composeapp.generated.resources.live_tv_section_guide
import nuvio.composeapp.generated.resources.live_tv_section_on_demand
import nuvio.composeapp.generated.resources.live_tv_section_sports
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LiveTvScreen(
    modifier: Modifier = Modifier,
    scrollToTopRequests: Flow<Unit> = emptyFlow(),
    onChannelClick: (LiveTvChannel) -> Unit,
    onVodClick: (LiveTvVodPlayback) -> Unit = {},
) {
    val uiState by remember {
        LiveTvRepository.ensureChannelsLoaded()
        LiveTvRepository.uiState
    }.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var isSearchVisible by rememberSaveable { mutableStateOf(false) }
    var section by rememberSaveable { mutableStateOf(LiveTvSection.Channels) }
    var filterMode by rememberSaveable { mutableStateOf(LiveTvChannelFilterMode.All) }
    var selectedCategoryName by rememberSaveable { mutableStateOf<String?>(null) }
    var guideChannelId by rememberSaveable { mutableStateOf<String?>(null) }
    val epgNowMillis = rememberLiveTvEpgNowMillis()
    val guideScrollState = rememberScrollState()
    val guideDensity = LocalDensity.current
    val guideWindowStart = remember(epgNowMillis / LIVE_TV_GUIDE_SLOT_MILLIS) { liveTvGuideWindowStart(epgNowMillis) }
    var sportsFilter by rememberSaveable { mutableStateOf(LIVE_TV_SPORTS_FILTER_ALL) }
    var vodKind by rememberSaveable { mutableStateOf(LiveTvVodKind.Movies) }
    var vodCategoryId by rememberSaveable { mutableStateOf<String?>(null) }
    var seriesSheetItemId by rememberSaveable { mutableStateOf<String?>(null) }
    val sportsState by LiveTvSportsRepository.uiState.collectAsStateWithLifecycle()
    val onDemandState by LiveTvOnDemandRepository.uiState.collectAsStateWithLifecycle()

    val allChannelsLabel = stringResource(Res.string.live_tv_group_all_channels)
    val favoritesLabel = stringResource(Res.string.live_tv_filter_favorites)
    val uncategorizedLabel = stringResource(Res.string.live_tv_group_uncategorized)
    val chooseCategoryLabel = stringResource(Res.string.live_tv_filter_choose_category)

    val categoryOptions = remember(
        uiState.channels,
        allChannelsLabel,
        favoritesLabel,
        uncategorizedLabel,
    ) {
        buildLiveTvCategoryFilterOptions(
            channels = uiState.channels,
            allChannelsLabel = allChannelsLabel,
            favoritesLabel = favoritesLabel,
            uncategorizedLabel = uncategorizedLabel,
        )
    }

    val visibleChannels = remember(
        uiState.channels,
        uiState.favoriteChannelIds,
        uiState.recentChannelIds,
        filterMode,
        selectedCategoryName,
        searchQuery,
        uncategorizedLabel,
    ) {
        filterLiveTvChannels(
            channels = uiState.channels,
            favoriteChannelIds = uiState.favoriteChannelIds,
            recentChannelIds = uiState.recentChannelIds,
            filterMode = filterMode,
            selectedCategoryName = selectedCategoryName,
            searchQuery = searchQuery,
            uncategorizedGroupName = uncategorizedLabel,
        )
    }

    val lastWatchedChannel = remember(uiState.channels, uiState.lastWatchedChannelId) {
        uiState.lastWatchedChannelId?.let { channelId ->
            uiState.channels.firstOrNull { it.id == channelId }
        }
    }

    val hasRecents = remember(uiState.channels, uiState.recentChannelIds) {
        val ids = uiState.recentChannelIds.toSet()
        ids.isNotEmpty() && uiState.channels.any { it.id in ids }
    }
    val hasFavorites = remember(uiState.channels, uiState.favoriteChannelIds) {
        uiState.favoriteChannelIds.isNotEmpty() &&
            uiState.channels.any { it.id in uiState.favoriteChannelIds }
    }
    val categoryChips = remember(categoryOptions, uiState.channels, uncategorizedLabel) {
        val hasUncategorized = uiState.channels.any { it.group.isNullOrBlank() }
        categoryOptions
            .filter { it.mode == LiveTvChannelFilterMode.Category }
            .filter { hasUncategorized || it.categoryName != uncategorizedLabel }
            .mapNotNull { it.categoryName }
    }

    LaunchedEffect(hasRecents, hasFavorites, filterMode) {
        if ((filterMode == LiveTvChannelFilterMode.Recents && !hasRecents) ||
            (filterMode == LiveTvChannelFilterMode.Favorites && !hasFavorites)
        ) {
            filterMode = LiveTvChannelFilterMode.All
            selectedCategoryName = null
        }
    }

    val channelChipCounts = remember(
        uiState.channels,
        uiState.favoriteChannelIds,
        uiState.recentChannelIds,
        searchQuery,
        uncategorizedLabel,
    ) {
        val searched = filterLiveTvChannels(
            channels = uiState.channels,
            favoriteChannelIds = uiState.favoriteChannelIds,
            recentChannelIds = uiState.recentChannelIds,
            filterMode = LiveTvChannelFilterMode.All,
            selectedCategoryName = null,
            searchQuery = searchQuery,
            uncategorizedGroupName = uncategorizedLabel,
        )
        val recentIds = uiState.recentChannelIds.toSet()
        LiveTvChannelChipCounts(
            all = searched.size,
            recents = searched.count { it.id in recentIds },
            favorites = searched.count { it.id in uiState.favoriteChannelIds },
            categories = searched
                .groupingBy { categoryNameForChannel(it, uncategorizedLabel).lowercase() }
                .eachCount(),
        )
    }

    val playChannel: (LiveTvChannel) -> Unit = { channel ->
        LiveTvRepository.markChannelWatched(channel)
        onChannelClick(channel)
    }

    // Guide ------------------------------------------------------------------------------------
    val guideChannels = remember(visibleChannels, uiState.epgPrograms, guideWindowStart, uiState.favoriteChannelIds) {
        visibleChannels
            .filter { liveTvGuideHasPrograms(uiState.epgPrograms[it.id], guideWindowStart) }
            .sortedByDescending { it.id in uiState.favoriteChannelIds }
    }
    LaunchedEffect(section, guideWindowStart, guideChannels.isNotEmpty()) {
        if (section != LiveTvSection.Guide || guideChannels.isEmpty()) return@LaunchedEffect
        withTimeoutOrNull(3_000L) {
            snapshotFlow { guideScrollState.maxValue }.first { it in 1 until Int.MAX_VALUE }
        }
        val minutesBeforeNow = ((epgNowMillis - guideWindowStart) / 60_000L - 15L).coerceAtLeast(0L)
        val target = with(guideDensity) { (LiveTvGuideDpPerMinute * minutesBeforeNow.toFloat()).roundToPx() }
        guideScrollState.scrollTo(target)
    }

    // Sports -----------------------------------------------------------------------------------
    LaunchedEffect(section) {
        if (section != LiveTvSection.Sports) return@LaunchedEffect
        while (true) {
            LiveTvSportsRepository.refreshIfStale()
            delay(30_000L)
        }
    }
    val sportsMatches by produceState(
        initialValue = emptyMap<String, List<LiveTvChannel>>(),
        sportsState.events,
        uiState.channels,
        section == LiveTvSection.Sports,
    ) {
        if (section != LiveTvSection.Sports) return@produceState
        value = withContext(Dispatchers.Default) {
            val normalized = uiState.channels.map { it to normalizeSportsName(it.name) }
            sportsState.events.associate { event ->
                "${event.league.id}:${event.id}" to matchSportsChannels(event, normalized)
            }
        }
    }
    val sportsLeagues = remember(sportsState.events) {
        val present = sportsState.events.map { it.league.id }.toSet()
        liveTvSportsLeagues.filter { it.id in present }
    }
    val sportsChipCounts = remember(sportsState.events) {
        buildMap {
            put(LIVE_TV_SPORTS_FILTER_ALL, sportsState.events.size)
            put(LIVE_TV_SPORTS_FILTER_LIVE, sportsState.events.count { it.state == LiveTvSportsState.Live })
            sportsState.events.groupingBy { it.league.id }.eachCount().forEach { (id, count) -> put(id, count) }
        }
    }
    val hasLiveSports = sportsState.events.any { it.state == LiveTvSportsState.Live }
    val visibleSports = remember(sportsState.events, sportsFilter) {
        sportsState.events.filter { event ->
            when (sportsFilter) {
                LIVE_TV_SPORTS_FILTER_ALL -> true
                LIVE_TV_SPORTS_FILTER_LIVE -> event.state == LiveTvSportsState.Live
                else -> event.league.id == sportsFilter
            }
        }
    }
    LaunchedEffect(sportsLeagues, hasLiveSports, sportsFilter) {
        val valid = sportsFilter == LIVE_TV_SPORTS_FILTER_ALL ||
            (sportsFilter == LIVE_TV_SPORTS_FILTER_LIVE && hasLiveSports) ||
            sportsLeagues.any { it.id == sportsFilter }
        if (!valid && sportsState.hasLoaded) sportsFilter = LIVE_TV_SPORTS_FILTER_ALL
    }

    // On Demand --------------------------------------------------------------------------------
    val hasVodProvider = uiState.xtreamSettings.isEnabled && uiState.xtreamSettings.isConfigured
    LaunchedEffect(section, vodKind, hasVodProvider) {
        if (section == LiveTvSection.OnDemand && hasVodProvider) LiveTvOnDemandRepository.ensureLoaded(vodKind)
    }
    val vodCatalog = onDemandState.catalog(vodKind)
    val vodChipCounts = remember(vodCatalog.catalog.items, searchQuery) {
        val searched = filterLiveTvVodItems(vodCatalog.catalog.items, null, searchQuery)
        searched.groupingBy { it.categoryId.orEmpty() }.eachCount() + (LIVE_TV_VOD_COUNT_ALL to searched.size)
    }
    val visibleVod = remember(vodCatalog.catalog.items, vodCategoryId, searchQuery) {
        filterLiveTvVodItems(vodCatalog.catalog.items, vodCategoryId, searchQuery)
    }

    LaunchedEffect(scrollToTopRequests) {
        scrollToTopRequests.collect {
            listState.animateScrollToItem(0)
        }
    }

    LaunchedEffect(searchQuery, filterMode, selectedCategoryName, section, vodKind, vodCategoryId, sportsFilter) {
        listState.scrollToItem(0)
    }

    LaunchedEffect(categoryOptions, filterMode, selectedCategoryName) {
        if (filterMode == LiveTvChannelFilterMode.Category && selectedCategoryName != null) {
            val selectedStillExists = categoryOptions.any { option ->
                option.mode == LiveTvChannelFilterMode.Category && option.categoryName == selectedCategoryName
            }
            if (!selectedStillExists) {
                filterMode = LiveTvChannelFilterMode.All
                selectedCategoryName = null
            }
        }
    }

    val tokens = MaterialTheme.nuvio
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val screenBackground = if (LocalDynamicArtworkBackgroundActive.current) {
        Color.Transparent
    } else {
        tokens.colors.background
    }
    val listTopPadding = tokens.spacing.screenTop + statusBarTop + nuvioPlatformExtraTopPadding

    val density = LocalDensity.current
    val controlsTopPadding by remember(listTopPadding) {
        derivedStateOf {
            val offset = listState.layoutInfo.visibleItemsInfo
                .firstOrNull { it.key == ChannelControlsKey }
                ?.offset
                ?: return@derivedStateOf listTopPadding
            (listTopPadding - with(density) { offset.toDp() }).coerceIn(0.dp, listTopPadding)
        }
    }

    val showChannelControls = !(uiState.isLoading && uiState.channels.isEmpty()) &&
        uiState.hasPlaylist &&
        !(!uiState.errorMessage.isNullOrBlank() && uiState.channels.isEmpty()) &&
        uiState.channels.isNotEmpty()

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
    val gridColumns = ((maxWidth - LiveTvScreenHorizontalPadding * 2) / LiveTvGridMinCellWidth)
        .toInt()
        .coerceIn(3, 8)
    val vodColumns = ((maxWidth - LiveTvScreenHorizontalPadding * 2) / LiveTvVodMinCellWidth)
        .toInt()
        .coerceIn(3, 8)
    val searchAvailable = when (section) {
        LiveTvSection.Channels, LiveTvSection.Guide -> showChannelControls
        LiveTvSection.OnDemand -> hasVodProvider
        LiveTvSection.Sports -> false
    }
        NuvioScreen(
            modifier = Modifier.fillMaxSize(),
            horizontalPadding = LiveTvScreenHorizontalPadding,
            topPadding = 0.dp,
            listState = listState,
            autoHidesNativeTabBar = true,
        ) {
            stickyHeader(key = ChannelControlsKey) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .nuvioScreenSideBleed()
                        .appBackgroundCover(screenBackground)
                        .liveTvBlockPointerPassThrough()
                        .padding(horizontal = LiveTvScreenHorizontalPadding)
                        .padding(top = listTopPadding),
                ) {
                    NuvioScreenHeader(
                        title = stringResource(Res.string.live_tv_title),
                        includeStatusBarPadding = false,
                        actions = {
                            if (searchAvailable) {
                                NuvioIconActionButton(
                                    icon = if (isSearchVisible) Icons.Rounded.Close else Icons.Rounded.Search,
                                    contentDescription = stringResource(Res.string.live_tv_action_search),
                                    onClick = {
                                        if (isSearchVisible) searchQuery = ""
                                        isSearchVisible = !isSearchVisible
                                    },
                                )
                            }
                            if (showChannelControls && section == LiveTvSection.Channels) {
                                NuvioIconActionButton(
                                    icon = if (uiState.isChannelGridEnabled) {
                                        Icons.AutoMirrored.Rounded.ViewList
                                    } else {
                                        Icons.Rounded.GridView
                                    },
                                    contentDescription = stringResource(
                                        if (uiState.isChannelGridEnabled) {
                                            Res.string.live_tv_action_list_view
                                        } else {
                                            Res.string.live_tv_action_grid_view
                                        },
                                    ),
                                    onClick = {
                                        LiveTvRepository.setChannelGridEnabled(!uiState.isChannelGridEnabled)
                                    },
                                )
                            }
                            if (uiState.hasPlaylist) {
                                NuvioIconActionButton(
                                    icon = Icons.Rounded.Refresh,
                                    contentDescription = stringResource(Res.string.action_retry),
                                    onClick = {
                                        when (section) {
                                            LiveTvSection.Channels -> LiveTvRepository.refresh()
                                            LiveTvSection.Guide -> LiveTvRepository.reloadEpg()
                                            LiveTvSection.Sports -> LiveTvSportsRepository.refresh()
                                            LiveTvSection.OnDemand -> LiveTvOnDemandRepository.refresh(vodKind)
                                        }
                                    },
                                )
                            }
                        },
                    )
                    if (uiState.hasPlaylist) {
                        LiveTvSectionTabs(
                            selected = section,
                            onSelected = { selected ->
                                if (selected != section) {
                                    searchQuery = ""
                                    isSearchVisible = false
                                    section = selected
                                }
                            },
                        )
                    }
                    if (showChannelControls &&
                        (section == LiveTvSection.Channels || section == LiveTvSection.Guide)
                    ) {
                        LiveTvChannelControls(
                            searchVisible = isSearchVisible,
                            searchQuery = searchQuery,
                            onSearchQueryChange = { searchQuery = it },
                            filterMode = filterMode,
                            selectedCategoryName = selectedCategoryName,
                            allLabel = allChannelsLabel,
                            favoritesLabel = favoritesLabel,
                            showRecents = hasRecents,
                            showFavorites = hasFavorites,
                            categories = categoryChips,
                            counts = channelChipCounts,
                            onAllSelected = {
                                filterMode = LiveTvChannelFilterMode.All
                                selectedCategoryName = null
                            },
                            onRecentsSelected = {
                                filterMode = LiveTvChannelFilterMode.Recents
                                selectedCategoryName = null
                            },
                            onClearRecents = LiveTvRepository::clearRecentChannels,
                            onFavoritesSelected = {
                                filterMode = LiveTvChannelFilterMode.Favorites
                                selectedCategoryName = null
                            },
                            onGroupSelected = { category ->
                                filterMode = LiveTvChannelFilterMode.Category
                                selectedCategoryName = category
                            },
                        )
                    }
                    if (section == LiveTvSection.Guide && guideChannels.isNotEmpty()) {
                        LiveTvGuideTimeRuler(
                            scrollState = guideScrollState,
                            windowStart = guideWindowStart,
                            nowMillis = epgNowMillis,
                        )
                    }
                    if (section == LiveTvSection.Sports && sportsState.events.isNotEmpty()) {
                        LiveTvSportsControls(
                            allLabel = allChannelsLabel,
                            liveLabel = stringResource(Res.string.live_tv_sports_filter_live),
                            leagues = sportsLeagues,
                            hasLive = hasLiveSports,
                            selectedFilter = sportsFilter,
                            counts = sportsChipCounts,
                            onFilterSelected = { sportsFilter = it },
                        )
                    }
                    if (section == LiveTvSection.OnDemand && hasVodProvider) {
                        AnimatedVisibility(visible = isSearchVisible) {
                            LiveTvSearchField(
                                query = searchQuery,
                                onQueryChange = { searchQuery = it },
                                placeholder = stringResource(Res.string.live_tv_on_demand_search_placeholder),
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                        }
                        LiveTvOnDemandControls(
                            kind = vodKind,
                            moviesLabel = stringResource(Res.string.live_tv_on_demand_movies),
                            seriesLabel = stringResource(Res.string.live_tv_on_demand_series),
                            allLabel = allChannelsLabel,
                            categories = vodCatalog.catalog.categories,
                            selectedCategoryId = vodCategoryId,
                            counts = vodChipCounts,
                            onKindSelected = { kind ->
                                if (kind != vodKind) {
                                    vodKind = kind
                                    vodCategoryId = null
                                }
                            },
                            onCategorySelected = { vodCategoryId = it },
                        )
                    }
                }
            }

            if (uiState.hasPlaylist && section == LiveTvSection.Guide && uiState.channels.isNotEmpty()) {
                when {
                    guideChannels.isNotEmpty() -> liveTvGuideRows(
                        channels = guideChannels,
                        epgPrograms = uiState.epgPrograms,
                        scrollState = guideScrollState,
                        windowStart = guideWindowStart,
                        nowMillis = epgNowMillis,
                        onChannelClick = playChannel,
                        onProgramClick = { channel, program ->
                            if (program.isAiringAt(epgNowMillis)) playChannel(channel) else guideChannelId = channel.id
                        },
                    )

                    uiState.isEpgLoading -> liveTvLoadingItem()

                    else -> item {
                        HomeEmptyStateCard(
                            title = stringResource(
                                if (visibleChannels.isEmpty()) Res.string.live_tv_no_matching_channels_title else Res.string.live_tv_guide_empty_title,
                            ),
                            message = stringResource(
                                if (visibleChannels.isEmpty()) Res.string.live_tv_no_matching_channels_message else Res.string.live_tv_guide_empty_message,
                            ),
                        )
                    }
                }
            } else if (uiState.hasPlaylist && section == LiveTvSection.Sports) {
                when {
                    sportsState.events.isEmpty() && (sportsState.isLoading || !sportsState.hasLoaded) -> liveTvLoadingItem()

                    sportsState.events.isEmpty() && sportsState.errorMessage != null -> item {
                        HomeEmptyStateCard(
                            title = stringResource(Res.string.live_tv_sports_load_failed),
                            message = sportsState.errorMessage.orEmpty(),
                            actionLabel = stringResource(Res.string.action_retry),
                            onActionClick = LiveTvSportsRepository::refresh,
                        )
                    }

                    visibleSports.isEmpty() -> item {
                        HomeEmptyStateCard(
                            title = stringResource(Res.string.live_tv_sports_empty_title),
                            message = stringResource(Res.string.live_tv_sports_empty_message),
                        )
                    }

                    else -> liveTvSportsList(
                        events = visibleSports,
                        matchedChannels = sportsMatches,
                        nowMillis = epgNowMillis,
                        onChannelClick = playChannel,
                    )
                }
            } else if (uiState.hasPlaylist && section == LiveTvSection.OnDemand) {
                when {
                    !hasVodProvider -> item {
                        HomeEmptyStateCard(
                            title = stringResource(Res.string.live_tv_on_demand_unavailable_title),
                            message = stringResource(Res.string.live_tv_on_demand_unavailable_message),
                        )
                    }

                    vodCatalog.catalog.items.isEmpty() && (vodCatalog.isLoading || !vodCatalog.hasLoaded) -> liveTvLoadingItem()

                    vodCatalog.catalog.items.isEmpty() && vodCatalog.errorMessage != null -> item {
                        HomeEmptyStateCard(
                            title = stringResource(Res.string.live_tv_on_demand_load_failed),
                            message = vodCatalog.errorMessage.orEmpty(),
                            actionLabel = stringResource(Res.string.action_retry),
                            onActionClick = { LiveTvOnDemandRepository.refresh(vodKind) },
                        )
                    }

                    visibleVod.isEmpty() -> item {
                        HomeEmptyStateCard(
                            title = stringResource(Res.string.live_tv_on_demand_empty_title),
                            message = stringResource(Res.string.live_tv_on_demand_empty_message),
                        )
                    }

                    else -> liveTvVodGrid(
                        items = visibleVod,
                        columns = vodColumns,
                        gap = LiveTvGridGap,
                        onItemClick = { item ->
                            when (item.kind) {
                                LiveTvVodKind.Movies -> LiveTvOnDemandRepository.moviePlayback(item)?.let(onVodClick)
                                LiveTvVodKind.Series -> seriesSheetItemId = item.id
                            }
                        },
                    )
                }
            } else when {
                uiState.isLoading && uiState.channels.isEmpty() -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 64.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                color = MaterialTheme.nuvio.colors.accent,
                                strokeWidth = 2.dp,
                            )
                        }
                    }
                }

                !uiState.hasPlaylist -> {
                    item {
                        HomeEmptyStateCard(
                            title = stringResource(Res.string.live_tv_no_playlist_title),
                            message = stringResource(Res.string.live_tv_no_playlist_message),
                        )
                    }
                }

                !uiState.errorMessage.isNullOrBlank() && uiState.channels.isEmpty() -> {
                    item {
                        HomeEmptyStateCard(
                            title = stringResource(Res.string.live_tv_load_failed),
                            message = uiState.errorMessage.orEmpty(),
                            actionLabel = stringResource(Res.string.action_retry),
                            onActionClick = LiveTvRepository::refresh,
                        )
                    }
                }

                uiState.channels.isEmpty() -> {
                    item {
                        HomeEmptyStateCard(
                            title = stringResource(Res.string.live_tv_empty_title),
                            message = stringResource(Res.string.live_tv_empty_message),
                            actionLabel = stringResource(Res.string.action_retry),
                            onActionClick = LiveTvRepository::refresh,
                        )
                    }
                }

                else -> {
                    if (visibleChannels.isEmpty()) {
                        item {
                            HomeEmptyStateCard(
                                title = stringResource(Res.string.live_tv_no_matching_channels_title),
                                message = stringResource(Res.string.live_tv_no_matching_channels_message),
                            )
                        }
                    } else if (uiState.isChannelGridEnabled) {
                        liveTvChannelGrid(
                            channels = visibleChannels,
                            columns = gridColumns,
                            favoriteChannelIds = uiState.favoriteChannelIds,
                            onFavoriteClick = { channel -> LiveTvRepository.toggleFavoriteChannel(channel.id) },
                            onPlayClick = playChannel,
                            epgPrograms = uiState.epgPrograms,
                            nowMillis = epgNowMillis,
                        )
                    } else {
                        liveTvChannelList(
                            channels = visibleChannels,
                            favoriteChannelIds = uiState.favoriteChannelIds,
                            uncategorizedGroupName = uncategorizedLabel,
                            onFavoriteClick = { channel -> LiveTvRepository.toggleFavoriteChannel(channel.id) },
                            onPlayClick = playChannel,
                            epgPrograms = uiState.epgPrograms,
                            nowMillis = epgNowMillis,
                            onGuideClick = { channel -> guideChannelId = channel.id },
                        )
                    }
                }
            }
        }

        val guideChannel = guideChannelId?.let { channelId -> uiState.channels.firstOrNull { it.id == channelId } }
        if (guideChannel != null) {
            LiveTvEpgGuideSheet(
                channel = guideChannel,
                programs = uiState.epgPrograms[guideChannel.id].orEmpty(),
                nowMillis = epgNowMillis,
                onPlayClick = {
                    guideChannelId = null
                    playChannel(guideChannel)
                },
                onDismiss = { guideChannelId = null },
            )
        }

        val seriesSheetItem = seriesSheetItemId?.let { id ->
            onDemandState.series.catalog.items.firstOrNull { it.id == id }
        }
        if (seriesSheetItem != null) {
            LiveTvSeriesSheet(
                series = seriesSheetItem,
                onEpisodeClick = { episode ->
                    onVodClick(LiveTvOnDemandRepository.episodePlayback(seriesSheetItem, episode))
                },
                onDismiss = { seriesSheetItemId = null },
            )
        }
    }
}

private fun LazyListScope.liveTvLoadingItem() {
    item(key = "live-tv-loading") {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 64.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(
                color = MaterialTheme.nuvio.colors.accent,
                strokeWidth = 2.dp,
            )
        }
    }
}

private const val ChannelControlsKey = "channel-controls"

private fun Modifier.liveTvBlockPointerPassThrough(): Modifier =
    pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                awaitPointerEvent()
            }
        }
    }

private val LiveTvScreenHorizontalPadding = 16.dp
private val LiveTvGridMinCellWidth = 108.dp
private val LiveTvGridGap = 10.dp

private fun LazyListScope.liveTvChannelGrid(
    channels: List<LiveTvChannel>,
    columns: Int,
    favoriteChannelIds: Set<String>,
    onFavoriteClick: (LiveTvChannel) -> Unit,
    onPlayClick: (LiveTvChannel) -> Unit,
    epgPrograms: Map<String, List<LiveTvEpgProgram>>,
    nowMillis: Long,
) {
    val rows = channels.chunked(columns)
    items(
        count = rows.size,
        key = { index -> "channel-grid:$columns:${rows[index].first().id}" },
    ) { index ->
        val row = rows[index]
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(LiveTvGridGap),
        ) {
            row.forEach { channel ->
                LiveTvChannelGridCell(
                    channel = channel,
                    isFavorite = channel.id in favoriteChannelIds,
                    currentProgramTitle = epgPrograms[channel.id]
                        ?.firstOrNull { it.isAiringAt(nowMillis) }
                        ?.title,
                    onFavoriteClick = { onFavoriteClick(channel) },
                    onPlayClick = { onPlayClick(channel) },
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
private fun LiveTvChannelGridCell(
    channel: LiveTvChannel,
    isFavorite: Boolean,
    currentProgramTitle: String?,
    onFavoriteClick: () -> Unit,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = MaterialTheme.nuvio
    Surface(
        modifier = modifier,
        onClick = onPlayClick,
        color = tokens.colors.surface,
        shape = tokens.shapes.compactCard,
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box {
                ChannelLogo(
                    channel = channel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(28.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(Color.Black.copy(alpha = 0.45f))
                        .clickable(onClick = onFavoriteClick),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (isFavorite) tokens.colors.warning else Color.White,
                    )
                }
            }
            Text(
                text = channel.name,
                style = MaterialTheme.typography.labelLarge,
                color = tokens.colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = currentProgramTitle.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = tokens.colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LiveTvSectionTabs(
    selected: LiveTvSection,
    onSelected: (LiveTvSection) -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(top = 4.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LiveTvSection.entries.forEach { item ->
            val isSelected = item == selected
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onSelected(item) }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(item.label),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (isSelected) tokens.colors.textPrimary else tokens.colors.textMuted,
                    maxLines = 1,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .width(24.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (isSelected) tokens.colors.accent else Color.Transparent),
                )
            }
        }
    }
}

@Composable
private fun LiveTvChannelControls(
    searchVisible: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    filterMode: LiveTvChannelFilterMode,
    selectedCategoryName: String?,
    allLabel: String,
    favoritesLabel: String,
    showRecents: Boolean,
    showFavorites: Boolean,
    categories: List<String>,
    counts: LiveTvChannelChipCounts,
    onAllSelected: () -> Unit,
    onRecentsSelected: () -> Unit,
    onClearRecents: () -> Unit,
    onFavoritesSelected: () -> Unit,
    onGroupSelected: (String) -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.listGap),
    ) {
        AnimatedVisibility(visible = searchVisible) {
            LiveTvSearchField(
                query = searchQuery,
                onQueryChange = onSearchQueryChange,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LiveTvPanelFilterChip(
                    label = allLabel,
                    selected = filterMode == LiveTvChannelFilterMode.All,
                    onClick = onAllSelected,
                    count = counts.all,
                )
                if (showRecents) {
                    LiveTvPanelFilterChip(
                        label = stringResource(Res.string.live_tv_filter_recents),
                        selected = filterMode == LiveTvChannelFilterMode.Recents,
                        onClick = onRecentsSelected,
                        count = counts.recents,
                    )
                    if (filterMode == LiveTvChannelFilterMode.Recents) {
                        LiveTvPanelFilterChip(
                            label = stringResource(Res.string.live_tv_action_clear_recents),
                            selected = false,
                            onClick = onClearRecents,
                        )
                    }
                }
                if (showFavorites) {
                    LiveTvPanelFilterChip(
                        label = favoritesLabel,
                        selected = filterMode == LiveTvChannelFilterMode.Favorites,
                        onClick = onFavoritesSelected,
                        count = counts.favorites,
                    )
                }
                categories.forEach { category ->
                    LiveTvPanelFilterChip(
                        label = category,
                        selected = filterMode == LiveTvChannelFilterMode.Category &&
                            selectedCategoryName.equals(category, ignoreCase = true),
                        onClick = { onGroupSelected(category) },
                        count = counts.categories[category.lowercase()] ?: 0,
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveTvSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = stringResource(Res.string.live_tv_search_placeholder),
) {
    Box(modifier = modifier.fillMaxWidth()) {
        NuvioInputField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = placeholder,
            trailingContent = {
                if (query.isBlank()) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = MaterialTheme.nuvio.colors.textMuted,
                    )
                } else {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = null,
                            tint = MaterialTheme.nuvio.colors.textMuted,
                        )
                    }
                }
            },
        )
    }
}

@Composable
private fun LiveTvFilterRow(
    filterMode: LiveTvChannelFilterMode,
    selectedCategoryName: String?,
    allChannelsLabel: String,
    favoritesLabel: String,
    chooseCategoryLabel: String,
    categoryOptions: List<LiveTvCategoryFilterOption>,
    onAllChannelsClick: () -> Unit,
    onFavoritesClick: () -> Unit,
    onCategoryOptionClick: (LiveTvCategoryFilterOption) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LiveTvFilterChip(
            label = allChannelsLabel,
            selected = filterMode == LiveTvChannelFilterMode.All,
            onClick = onAllChannelsClick,
        )
        LiveTvFilterChip(
            label = favoritesLabel,
            selected = filterMode == LiveTvChannelFilterMode.Favorites,
            onClick = onFavoritesClick,
        )
        LiveTvCategoryFilterChip(
            selected = filterMode == LiveTvChannelFilterMode.Category,
            label = selectedCategoryName ?: chooseCategoryLabel,
            options = categoryOptions,
            onOptionClick = onCategoryOptionClick,
        )
    }
}

@Composable
private fun LiveTvFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    Row(
        modifier = Modifier
            .clip(tokens.shapes.chip)
            .background(if (selected) tokens.colors.overlaySelected else tokens.colors.surface)
            .then(
                if (selected) Modifier else Modifier.border(
                    width = NuvioTokens.Border.thin,
                    color = tokens.colors.borderSubtle,
                    shape = tokens.shapes.chip,
                ),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) tokens.colors.accent else tokens.colors.textSecondary,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun LiveTvCategoryFilterChip(
    selected: Boolean,
    label: String,
    options: List<LiveTvCategoryFilterOption>,
    onOptionClick: (LiveTvCategoryFilterOption) -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    var expanded by remember { mutableStateOf(false) }
    val shape = tokens.shapes.chip

    Box(modifier = Modifier.wrapContentSize(Alignment.TopStart)) {
        Row(
            modifier = Modifier
                .clip(shape)
                .background(if (selected) tokens.colors.overlaySelected else tokens.colors.surface)
                .border(
                    width = if (selected) 0.dp else NuvioTokens.Border.thin,
                    color = if (selected) Color.Transparent else tokens.colors.borderSubtle,
                    shape = shape,
                )
                .clickable { expanded = true }
                .padding(horizontal = 16.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) tokens.colors.accent else tokens.colors.textSecondary,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(NuvioTokens.Icon.sm + NuvioTokens.Space.s2),
                tint = if (selected) tokens.colors.accent else tokens.colors.textMuted,
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.widthIn(min = 220.dp),
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option.label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    onClick = {
                        expanded = false
                        onOptionClick(option)
                    },
                )
            }
        }
    }
}

@Composable
private fun LiveTvRecentChannelCard(
    channel: LiveTvChannel,
    categoryName: String,
    onContinueClick: () -> Unit,
    programs: List<LiveTvEpgProgram> = emptyList(),
    nowMillis: Long = 0L,
    onGuideClick: () -> Unit = {},
) {
    val tokens = MaterialTheme.nuvio
    Surface(
        modifier = Modifier.fillMaxWidth(),
        onClick = onContinueClick,
        color = tokens.colors.surface,
        shape = tokens.shapes.card,
        border = BorderStroke(NuvioTokens.Border.thin, tokens.colors.borderSubtle),
    ) {
        Row(
            modifier = Modifier.padding(tokens.spacing.cardPadding),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChannelLogo(
                channel = channel,
                modifier = Modifier.size(58.dp),
                shape = RoundedCornerShape(16.dp),
                iconTint = tokens.colors.accent,
                borderColor = tokens.colors.borderSubtle,
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = stringResource(Res.string.live_tv_last_watched_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = tokens.colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = channel.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = tokens.colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                LiveTvCategoryLine(categoryName = categoryName)
                if (programs.isNotEmpty()) {
                    LiveTvEpgNowNext(
                        programs = programs,
                        nowMillis = nowMillis,
                        onClick = onGuideClick,
                    )
                }
            }

            Surface(
                color = tokens.colors.overlaySelected,
                shape = tokens.shapes.chip,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = tokens.colors.accent,
                    )
                    Text(
                        text = stringResource(Res.string.live_tv_button_continue),
                        style = MaterialTheme.typography.labelLarge,
                        color = tokens.colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

private fun LazyListScope.liveTvChannelList(
    channels: List<LiveTvChannel>,
    favoriteChannelIds: Set<String>,
    uncategorizedGroupName: String,
    onFavoriteClick: (LiveTvChannel) -> Unit,
    onPlayClick: (LiveTvChannel) -> Unit,
    epgPrograms: Map<String, List<LiveTvEpgProgram>> = emptyMap(),
    nowMillis: Long = 0L,
    onGuideClick: (LiveTvChannel) -> Unit = {},
) {
    items(
        items = channels,
        key = { channel -> "channel:${channel.id}" },
    ) { channel ->
        LiveTvChannelCard(
            channel = channel,
            categoryName = categoryNameForChannel(channel, uncategorizedGroupName),
            isFavorite = channel.id in favoriteChannelIds,
            onFavoriteClick = { onFavoriteClick(channel) },
            onPlayClick = { onPlayClick(channel) },
            programs = epgPrograms[channel.id].orEmpty(),
            nowMillis = nowMillis,
            onGuideClick = { onGuideClick(channel) },
        )
    }
}

@Composable
private fun LiveTvChannelCard(
    channel: LiveTvChannel,
    categoryName: String,
    isFavorite: Boolean,
    onFavoriteClick: () -> Unit,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier,
    programs: List<LiveTvEpgProgram> = emptyList(),
    nowMillis: Long = 0L,
    onGuideClick: () -> Unit = {},
) {
    val tokens = MaterialTheme.nuvio
    Surface(
        modifier = modifier.fillMaxWidth(),
        onClick = onPlayClick,
        color = tokens.colors.surface,
        shape = tokens.shapes.compactCard,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ChannelLogo(channel = channel)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = channel.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = tokens.colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                LiveTvCategoryLine(categoryName = categoryName)
                if (programs.isNotEmpty()) {
                    LiveTvEpgNowNext(
                        programs = programs,
                        nowMillis = nowMillis,
                        onClick = onGuideClick,
                    )
                }
            }
            IconButton(onClick = onFavoriteClick) {
                Icon(
                    imageVector = if (isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                    contentDescription = null,
                    tint = if (isFavorite) tokens.colors.warning else tokens.colors.textMuted,
                )
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(tokens.shapes.avatar)
                    .background(tokens.colors.overlaySelected)
                    .clickable(onClick = onPlayClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = tokens.colors.accent,
                )
            }
        }
    }
}

@Composable
private fun LiveTvCategoryLine(categoryName: String) {
    val tokens = MaterialTheme.nuvio
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(tokens.colors.accent),
        )
        Text(
            text = categoryName,
            style = MaterialTheme.typography.bodySmall,
            color = tokens.colors.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ChannelLogo(
    channel: LiveTvChannel,
    modifier: Modifier = Modifier.size(64.dp),
    shape: RoundedCornerShape = RoundedCornerShape(14.dp),
    iconTint: Color = MaterialTheme.nuvio.colors.textMuted,
    borderColor: Color? = null,
) {
    val tokens = MaterialTheme.nuvio
    val boxModifier = modifier
        .clip(shape)
        .background(tokens.colors.surfaceCard)
        .then(
            if (borderColor != null) {
                Modifier.border(
                    width = NuvioTokens.Border.thin,
                    color = borderColor,
                    shape = shape,
                )
            } else {
                Modifier
            },
        )

    Box(
        modifier = boxModifier,
        contentAlignment = Alignment.Center,
    ) {
        if (!channel.logoUrl.isNullOrBlank()) {
            AsyncImage(
                model = channel.logoUrl,
                contentDescription = channel.name,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                contentScale = ContentScale.Fit,
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.Tv,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

private enum class LiveTvSection(val label: StringResource) {
    Channels(Res.string.live_tv_section_channels),
    Guide(Res.string.live_tv_section_guide),
    Sports(Res.string.live_tv_section_sports),
    OnDemand(Res.string.live_tv_section_on_demand),
}

private data class LiveTvChannelChipCounts(
    val all: Int,
    val recents: Int,
    val favorites: Int,
    val categories: Map<String, Int>,
)

private enum class LiveTvChannelFilterMode {
    All,
    Recents,
    Favorites,
    Category,
}

private data class LiveTvCategoryFilterOption(
    val label: String,
    val mode: LiveTvChannelFilterMode,
    val categoryName: String? = null,
)

private fun buildLiveTvCategoryFilterOptions(
    channels: List<LiveTvChannel>,
    allChannelsLabel: String,
    favoritesLabel: String,
    uncategorizedLabel: String,
): List<LiveTvCategoryFilterOption> {
    val categories = channels
        .mapNotNull { channel -> channel.group?.trim()?.takeIf(String::isNotBlank) }
        .distinctBy { it.lowercase() }
        .filterNot { category ->
            category.equals(allChannelsLabel, ignoreCase = true) ||
                category.equals(favoritesLabel, ignoreCase = true) ||
                category.equals(uncategorizedLabel, ignoreCase = true)
        }
        .sortedBy { it.lowercase() }

    return buildList {
        add(LiveTvCategoryFilterOption(allChannelsLabel, LiveTvChannelFilterMode.All))
        add(LiveTvCategoryFilterOption(favoritesLabel, LiveTvChannelFilterMode.Favorites))
        add(LiveTvCategoryFilterOption(uncategorizedLabel, LiveTvChannelFilterMode.Category, uncategorizedLabel))
        categories.forEach { category ->
            add(LiveTvCategoryFilterOption(category, LiveTvChannelFilterMode.Category, category))
        }
    }
}

private fun filterLiveTvChannels(
    channels: List<LiveTvChannel>,
    favoriteChannelIds: Set<String>,
    recentChannelIds: List<String>,
    filterMode: LiveTvChannelFilterMode,
    selectedCategoryName: String?,
    searchQuery: String,
    uncategorizedGroupName: String,
): List<LiveTvChannel> {
    val normalizedQuery = searchQuery.trim().lowercase()
    val recentOrder = recentChannelIds.withIndex().associate { (index, id) -> id to index }
    val filtered = channels
        .asSequence()
        .filter { channel ->
            when (filterMode) {
                LiveTvChannelFilterMode.All -> true
                LiveTvChannelFilterMode.Recents -> channel.id in recentOrder
                LiveTvChannelFilterMode.Favorites -> channel.id in favoriteChannelIds
                LiveTvChannelFilterMode.Category -> selectedCategoryName == null ||
                    categoryNameForChannel(channel, uncategorizedGroupName).equals(selectedCategoryName, ignoreCase = true)
            }
        }
        .filter { channel ->
            normalizedQuery.isBlank() ||
                channel.name.lowercase().contains(normalizedQuery) ||
                categoryNameForChannel(channel, uncategorizedGroupName).lowercase().contains(normalizedQuery) ||
                channel.playlistName.orEmpty().lowercase().contains(normalizedQuery)
        }
    return if (filterMode == LiveTvChannelFilterMode.Recents) {
        filtered.sortedBy { recentOrder[it.id] ?: Int.MAX_VALUE }.toList()
    } else {
        filtered.sortedBy { it.name.lowercase() }.toList()
    }
}

private fun categoryNameForChannel(
    channel: LiveTvChannel,
    uncategorizedGroupName: String,
): String = channel.group?.trim()?.takeIf(String::isNotBlank) ?: uncategorizedGroupName
