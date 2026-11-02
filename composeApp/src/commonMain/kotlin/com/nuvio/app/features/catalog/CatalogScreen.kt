package com.nuvio.app.features.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import com.nuvio.app.core.ui.NuvioBackButton
import com.nuvio.app.core.ui.NuvioNativeLiquidGlassButton
import com.nuvio.app.core.ui.isNativeLiquidGlassControlsAvailable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.network.NetworkCondition
import com.nuvio.app.core.network.NetworkStatusRepository
import com.nuvio.app.core.ui.NuvioCardDepthSurface
import coil3.compose.AsyncImage
import com.nuvio.app.core.format.formatReleaseDateForDisplay
import com.nuvio.app.core.ui.NuvioLoadingIndicator
import com.nuvio.app.core.ui.NuvioNetworkOfflineCard
import com.nuvio.app.core.ui.NuvioPosterWatchedOverlay
import com.nuvio.app.core.ui.SkeletonPoster
import com.nuvio.app.core.ui.nuvioCardDepth
import com.nuvio.app.core.ui.nuvioSafeBottomPadding
import com.nuvio.app.core.ui.posterCardClickable
import com.nuvio.app.core.ui.rememberPosterCardStyleUiState
import com.nuvio.app.core.ui.withDuplicateSafeLazyKeys
import com.nuvio.app.core.ui.glass.LiquidGlassSurface
import com.nuvio.app.core.ui.glass.LocalLiquidGlassBackdrop
import com.nuvio.app.core.ui.glass.isLiquidGlassAvailable
import com.nuvio.app.core.ui.glass.isLiquidGlassSurfaceEffectivelyEnabled
import com.nuvio.app.core.ui.glass.liquidGlassForSurface
import com.nuvio.app.core.ui.glass.LiquidGlassTextContrastScope
import com.nuvio.app.core.ui.glass.liquidGlassIconTint
import com.nuvio.app.core.ui.glass.liquidGlassShape
import com.nuvio.app.core.ui.glass.nuvioBackdropSource
import com.nuvio.app.core.ui.glass.rememberNuvioBackdrop
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.HomeCatalogSettingsRepository
import com.nuvio.app.features.home.PosterShape
import com.nuvio.app.features.home.components.HomeEmptyStateCard
import com.nuvio.app.features.home.stableKey
import com.nuvio.app.features.watched.WatchedRepository
import com.nuvio.app.features.watching.application.WatchingState
import com.nuvio.app.navigation.LocalUseNativeNavigation
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun CatalogScreen(
    title: String,
    subtitle: String,
    target: CatalogTarget,
    onBack: () -> Unit,
    onPosterClick: ((MetaPreview) -> Unit)? = null,
    onPosterLongClick: ((MetaPreview) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val uiState by CatalogRepository.uiState.collectAsStateWithLifecycle()
    val homeCatalogSettingsUiState by HomeCatalogSettingsRepository.uiState.collectAsStateWithLifecycle()
    val posterCardStyle = rememberPosterCardStyleUiState()
    val networkStatusUiState by NetworkStatusRepository.uiState.collectAsStateWithLifecycle()
    val watchedUiState by remember {
        WatchedRepository.ensureLoaded()
        WatchedRepository.uiState
    }.collectAsStateWithLifecycle()
    val fullyWatchedSeriesKeys by WatchedRepository.fullyWatchedSeriesKeys.collectAsStateWithLifecycle()
    val initialScrollPosition = remember(
        target,
        homeCatalogSettingsUiState.hideUnreleasedContent,
    ) {
        CatalogRepository.scrollPosition(
            target = target,
        )
    }
    val gridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = initialScrollPosition.firstVisibleItemIndex,
        initialFirstVisibleItemScrollOffset = initialScrollPosition.firstVisibleItemScrollOffset,
    )
    var headerHeightPx by remember { mutableIntStateOf(0) }
    var observedOfflineState by remember { mutableStateOf(false) }
    val categoryHeaderTitleSurface = LiquidGlassSurface.CATEGORY_HEADER_TITLE
    val categoryHeaderBackSurface = LiquidGlassSurface.CATEGORY_HEADER_BACK_BUTTON
    val catalogHeaderGlassEnabled = !LocalUseNativeNavigation.current &&
        isLiquidGlassAvailable() &&
        (
            isLiquidGlassSurfaceEffectivelyEnabled(categoryHeaderTitleSurface) ||
                isLiquidGlassSurfaceEffectivelyEnabled(categoryHeaderBackSurface)
            )
    val catalogBackdrop = rememberNuvioBackdrop()

    LaunchedEffect(target, homeCatalogSettingsUiState.hideUnreleasedContent) {
        CatalogRepository.load(
            target = target,
        )
    }

    LaunchedEffect(gridState, target, homeCatalogSettingsUiState.hideUnreleasedContent) {
        snapshotFlow { gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .collect { (index, offset) ->
                CatalogRepository.saveScrollPosition(
                    target = target,
                    firstVisibleItemIndex = index,
                    firstVisibleItemScrollOffset = offset,
                )
            }
    }

    LaunchedEffect(gridState, uiState.canLoadMore, uiState.isLoading) {
        snapshotFlow { gridState.layoutInfo }
            .map { layoutInfo ->
                val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                lastVisible >= layoutInfo.totalItemsCount - 6
            }
            .distinctUntilChanged()
            .filter { it && uiState.canLoadMore && !uiState.isLoading }
            .collect {
                CatalogRepository.loadMore()
            }
    }

    LaunchedEffect(networkStatusUiState.condition, target) {
        when (networkStatusUiState.condition) {
            NetworkCondition.NoInternet,
            NetworkCondition.ServersUnreachable,
            -> {
                observedOfflineState = true
            }

            NetworkCondition.Online -> {
                if (!observedOfflineState) return@LaunchedEffect
                observedOfflineState = false
                CatalogRepository.load(
                    target = target,
                    force = true,
                )
            }

            NetworkCondition.Unknown,
            NetworkCondition.Checking,
            -> Unit
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        val columns = remember(maxWidth) { catalogGridColumnsForWidth(maxWidth) }

        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (catalogHeaderGlassEnabled) Modifier.nuvioBackdropSource(catalogBackdrop) else Modifier)
                    .background(MaterialTheme.colorScheme.background),
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        top = with(androidx.compose.ui.platform.LocalDensity.current) { headerHeightPx.toDp() } + 12.dp,
                        end = 16.dp,
                        bottom = nuvioSafeBottomPadding(28.dp),
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    if (uiState.items.isEmpty() && uiState.isLoading) {
                        items(columns * 3) {
                            SkeletonPoster(
                                modifier = Modifier.fillMaxWidth(),
                                cornerRadius = posterCardStyle.cornerRadiusDp.dp,
                                showLabels = !posterCardStyle.hideLabelsEnabled,
                            )
                        }
                    } else if (uiState.items.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            CatalogEmptyState(
                                errorMessage = uiState.errorMessage,
                                networkCondition = networkStatusUiState.condition,
                                onRetry = {
                                    NetworkStatusRepository.requestRefresh(force = true)
                                    CatalogRepository.load(
                                        target = target,
                                        force = true,
                                    )
                                },
                            )
                        }
                    } else {
                        items(
                            items = uiState.items.withDuplicateSafeLazyKeys { item -> item.stableKey() },
                            key = { item -> item.lazyKey },
                        ) { keyedItem ->
                            val item = keyedItem.value
                            CatalogPosterTile(
                                item = item,
                                cornerRadiusDp = posterCardStyle.cornerRadiusDp,
                                hideLabels = posterCardStyle.hideLabelsEnabled,
                                isWatched = WatchingState.isPosterWatched(
                                    watchedKeys = watchedUiState.watchedKeys,
                                    item = item,
                                    fullyWatchedSeriesKeys = fullyWatchedSeriesKeys,
                                ),
                                onClick = onPosterClick?.let { { it(item) } },
                                onLongClick = onPosterLongClick?.let { { it(item) } },
                            )
                        }
                        if (uiState.isLoading) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                CatalogLoadingFooter()
                            }
                        }
                    }
                }
            }

            CompositionLocalProvider(
                LocalLiquidGlassBackdrop provides catalogBackdrop.takeIf { catalogHeaderGlassEnabled },
            ) {
                CatalogHeader(
                    title = title,
                    subtitle = subtitle,
                    modifier = Modifier.onSizeChanged { headerHeightPx = it.height },
                    onBack = onBack,
                )
            }
        }
    }
}

internal fun categoryHeaderUsesPosterBackdrop(surface: LiquidGlassSurface): Boolean =
    surface == LiquidGlassSurface.CATEGORY_HEADER_TITLE ||
        surface == LiquidGlassSurface.CATEGORY_HEADER_BACK_BUTTON

internal const val CATEGORY_HEADER_PREVIEW_TITLE = "Popular This Week - Movies"
internal const val CATEGORY_HEADER_PREVIEW_SUBTITLE = "Letterboxd"

/** Fixed, local preview data keeps Studio deterministic while using the production catalog tile UI. */
internal fun categoryHeaderPreviewItems(): List<MetaPreview> = listOf(
    MetaPreview(
        id = "category-preview-dune-part-two",
        type = "movie",
        name = "Dune: Part Two",
        poster = "https://image.tmdb.org/t/p/w342/1pdfLvkbY9ohJlCjQH2CZjjYVvJ.jpg",
        releaseInfo = "2024",
    ),
    MetaPreview(
        id = "category-preview-the-batman",
        type = "movie",
        name = "The Batman",
        poster = "https://image.tmdb.org/t/p/w342/74xTEgt7R36Fpooo50r9T25onhq.jpg",
        releaseInfo = "2022",
    ),
    MetaPreview(
        id = "category-preview-spider-verse",
        type = "movie",
        name = "Spider-Man: Across the Spider-Verse",
        poster = "https://image.tmdb.org/t/p/w342/8Vt6mWEReuy4Of61Lnj5Xj704m8.jpg",
        releaseInfo = "2023",
    ),
    MetaPreview(
        id = "category-preview-oppenheimer",
        type = "movie",
        name = "Oppenheimer",
        poster = "https://image.tmdb.org/t/p/w342/ptpr0kGAckfQkJeJIt8st5dglvd.jpg",
        releaseInfo = "2023",
    ),
    MetaPreview(
        id = "category-preview-avatar-way-of-water",
        type = "movie",
        name = "Avatar: The Way of Water",
        poster = "https://image.tmdb.org/t/p/w342/t6HIqrRAclMCA60NsSmeqe9RmNV.jpg",
        releaseInfo = "2022",
    ),
    MetaPreview(
        id = "category-preview-wonka",
        type = "movie",
        name = "Wonka",
        poster = "https://image.tmdb.org/t/p/w342/qhb1qOilapbapxWQn9jtRCMwXJF.jpg",
        releaseInfo = "2023",
    ),
    MetaPreview(
        id = "category-preview-barbie",
        type = "movie",
        name = "Barbie",
        poster = "https://image.tmdb.org/t/p/w342/iuFNMS8U5cb6xfzi51Dbkovj7vM.jpg",
        releaseInfo = "2023",
    ),
    MetaPreview(
        id = "category-preview-guardians-three",
        type = "movie",
        name = "Guardians of the Galaxy Vol. 3",
        poster = "https://image.tmdb.org/t/p/w342/r2J02Z2OpNTctfOSN1Ydgii51I3.jpg",
        releaseInfo = "2023",
    ),
    MetaPreview(
        id = "category-preview-the-creator",
        type = "movie",
        name = "The Creator",
        poster = "https://image.tmdb.org/t/p/w342/vBZ0qvaRxqEhZwl6LWmruJqWE8Z.jpg",
        releaseInfo = "2023",
    ),
)

/** Uses the same poster tile, sizing, labels and corner-radius preference as CatalogScreen. */
@Composable
internal fun CatalogHeaderPreviewGrid(modifier: Modifier = Modifier) {
    val previewItems = remember { categoryHeaderPreviewItems() }
    val posterCardStyle = rememberPosterCardStyleUiState()

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val columns = remember(maxWidth) { catalogGridColumnsForWidth(maxWidth) }
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = rememberLazyGridState(initialFirstVisibleItemIndex = columns),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            items(
                items = previewItems,
                key = { item -> item.stableKey() },
            ) { item ->
                CatalogPosterTile(
                    item = item,
                    cornerRadiusDp = posterCardStyle.cornerRadiusDp,
                    hideLabels = posterCardStyle.hideLabelsEnabled,
                    isWatched = false,
                )
            }
        }
    }
}

/** Apply the rounded title clip only while that title's glass material is rendered. */
internal fun categoryHeaderTitleClipRequired(titleGlassEnabled: Boolean): Boolean = titleGlassEnabled

/** In the Studio preview, render the real glass effect only on the component being edited. */
internal fun categoryHeaderPreviewUsesGlass(
    surface: LiquidGlassSurface,
    previewMode: Boolean,
    selectedPreviewSurface: LiquidGlassSurface?,
): Boolean = !previewMode || selectedPreviewSurface == null || selectedPreviewSurface == surface

@Composable
internal fun CatalogHeader(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    previewMode: Boolean = false,
    onPreviewSurfaceSelected: ((LiquidGlassSurface) -> Unit)? = null,
    selectedPreviewSurface: LiquidGlassSurface? = null,
) {
    if (LocalUseNativeNavigation.current && !previewMode) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .height(44.dp),
        )
        return
    }

    val titleSurface = LiquidGlassSurface.CATEGORY_HEADER_TITLE
    val backSurface = LiquidGlassSurface.CATEGORY_HEADER_BACK_BUTTON
    val posterBackdrop = LocalLiquidGlassBackdrop.current
    val titleGlassConfigured = isLiquidGlassAvailable() && isLiquidGlassSurfaceEffectivelyEnabled(titleSurface)
    val backGlassConfigured = isLiquidGlassAvailable() && isLiquidGlassSurfaceEffectivelyEnabled(backSurface)
    val nativeBackGlassEnabled = isNativeLiquidGlassControlsAvailable() &&
        isLiquidGlassSurfaceEffectivelyEnabled(
            backSurface,
            preview = previewMode,
            platformSupported = true,
        ) &&
        (!previewMode || selectedPreviewSurface == null || selectedPreviewSurface == backSurface)
    val titleGlassEnabled = titleGlassConfigured &&
        categoryHeaderPreviewUsesGlass(titleSurface, previewMode, selectedPreviewSurface)
    val backGlassEnabled = backGlassConfigured &&
        categoryHeaderPreviewUsesGlass(backSurface, previewMode, selectedPreviewSurface)
    val titleShape = liquidGlassShape(titleSurface, RoundedCornerShape(16.dp))
    val backShape = liquidGlassShape(backSurface, RoundedCornerShape(20.dp))
    val titleGlassModifier = if (titleGlassEnabled) {
        Modifier.liquidGlassForSurface(
            surface = titleSurface,
            shape = titleShape,
            tint = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
            fallback = MaterialTheme.colorScheme.surface.copy(alpha = 0.45f),
            blurRadius = 12.dp,
            refractionHeight = 6.dp,
            refractionAmount = 9.dp,
            explicitBackdrop = posterBackdrop.takeIf { categoryHeaderUsesPosterBackdrop(titleSurface) },
            useExplicitBackdrop = true,
        )
    } else {
        Modifier
    }
    val backGlassModifier = if (backGlassEnabled) {
        Modifier.liquidGlassForSurface(
            surface = backSurface,
            shape = backShape,
            tint = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
            fallback = MaterialTheme.colorScheme.surface.copy(alpha = 0.45f),
            blurRadius = 12.dp,
            refractionHeight = 6.dp,
            refractionAmount = 9.dp,
            explicitBackdrop = posterBackdrop.takeIf { categoryHeaderUsesPosterBackdrop(backSurface) },
            useExplicitBackdrop = true,
        )
    } else {
        Modifier
    }
    val titleSelectionBorder = if (previewMode && selectedPreviewSurface == titleSurface) {
        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, titleShape)
    } else {
        Modifier
    }
    val backSelectionBorder = if (previewMode && selectedPreviewSurface == backSurface) {
        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, backShape)
    } else {
        Modifier
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            // The poster grid scrolls beneath this fixed header. Keep those tiles masked outside
            // the back button's dedicated poster-aware glass surface.
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp)
            .padding(top = 52.dp, bottom = 12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .then(backGlassModifier)
                .clip(backShape)
                .then(backSelectionBorder),
        ) {
            if (nativeBackGlassEnabled) {
                NuvioNativeLiquidGlassButton(
                    systemImage = "chevron.left",
                    accessibilityLabel = stringResource(Res.string.action_back),
                    onClick = {
                        if (previewMode && onPreviewSurfaceSelected != null) {
                            onPreviewSurfaceSelected(backSurface)
                        } else {
                            onBack()
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                CompositionLocalProvider(
                    LocalUseNativeNavigation provides (if (previewMode) false else LocalUseNativeNavigation.current),
                ) {
                    NuvioBackButton(
                        onClick = {
                            if (previewMode && onPreviewSurfaceSelected != null) {
                                onPreviewSurfaceSelected(backSurface)
                            } else {
                                onBack()
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        shape = backShape,
                        containerColor = if (backGlassEnabled) Color.Transparent else MaterialTheme.colorScheme.surface,
                        contentColor = liquidGlassIconTint(backSurface, MaterialTheme.colorScheme.onSurface),
                        iconSize = 24.dp,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(titleGlassModifier)
                .then(
                    if (categoryHeaderTitleClipRequired(titleGlassEnabled)) {
                        Modifier.clip(titleShape)
                    } else {
                        Modifier
                    },
                )
                .then(titleSelectionBorder)
                .then(
                    if (previewMode && onPreviewSurfaceSelected != null) {
                        Modifier.clickable { onPreviewSurfaceSelected(titleSurface) }
                    } else {
                        Modifier
                    },
                )
                .padding(
                    horizontal = if (titleGlassConfigured || (previewMode && selectedPreviewSurface == titleSurface)) 12.dp else 0.dp,
                    vertical = if (titleGlassConfigured || (previewMode && selectedPreviewSurface == titleSurface)) 8.dp else 0.dp,
                ),
        ) {
            LiquidGlassTextContrastScope(enabled = titleGlassEnabled) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            }
        }
    }
}

@Composable
private fun CatalogPosterTile(
    item: MetaPreview,
    cornerRadiusDp: Int,
    hideLabels: Boolean,
    isWatched: Boolean,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(item.posterShape.catalogAspectRatio())
                .clip(RoundedCornerShape(cornerRadiusDp.dp))
                .background(MaterialTheme.colorScheme.surface)
                .nuvioCardDepth(
                    shape = RoundedCornerShape(cornerRadiusDp.dp),
                    surface = NuvioCardDepthSurface.Posters,
                )
                .posterCardClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                    zoomImageUrl = item.poster,
                    zoomCornerRadius = cornerRadiusDp.dp,
                ),
        ) {
            if (item.poster != null) {
                val platformContext = coil3.compose.LocalPlatformContext.current
                val hasFallback = !item.rawPosterUrl.isNullOrBlank() && item.rawPosterUrl != item.poster
                val imageModel = remember(item.poster, item.rawPosterUrl, platformContext) {
                    if (hasFallback) {
                        coil3.request.ImageRequest.Builder(platformContext)
                            .data(item.poster)
                            .memoryCacheKeyExtras(
                                mapOf(com.nuvio.app.core.poster.CustomPosterFallbackInterceptor.FALLBACK_URL_KEY to item.rawPosterUrl!!)
                            )
                            .build()
                    } else {
                        item.poster
                    }
                }
                AsyncImage(
                    model = imageModel,
                    contentDescription = item.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            NuvioPosterWatchedOverlay(isWatched = isWatched)
        }
        if (!hideLabels) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val detail = item.releaseInfo?.let { formatReleaseDateForDisplay(it) }
            if (detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun CatalogEmptyState(
    errorMessage: String?,
    networkCondition: NetworkCondition,
    onRetry: (() -> Unit)? = null,
) {
    if (
        !errorMessage.isNullOrBlank() &&
        (networkCondition == NetworkCondition.NoInternet || networkCondition == NetworkCondition.ServersUnreachable)
    ) {
        NuvioNetworkOfflineCard(
            condition = networkCondition,
            onRetry = onRetry,
        )
        return
    }

    val loadFailed = !errorMessage.isNullOrBlank()
    HomeEmptyStateCard(
        title = stringResource(
            if (loadFailed) Res.string.catalog_load_failed_title else Res.string.catalog_empty_title,
        ),
        message = errorMessage ?: stringResource(Res.string.catalog_empty_message),
        actionLabel = if (loadFailed) stringResource(Res.string.action_retry) else null,
        onActionClick = if (loadFailed) onRetry else null,
    )
}

@Composable
private fun CatalogLoadingFooter() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        NuvioLoadingIndicator(
            modifier = Modifier.size(22.dp),
        )
    }
}

private fun PosterShape.catalogAspectRatio(): Float =
    when (this) {
        PosterShape.Poster -> 0.68f
        PosterShape.Square -> 1f
        PosterShape.Landscape -> 1.78f
    }

private fun catalogGridColumnsForWidth(screenWidth: Dp): Int =
    when {
        screenWidth >= 1400.dp -> 7
        screenWidth >= 1200.dp -> 6
        screenWidth >= 1000.dp -> 5
        screenWidth >= 840.dp -> 4
        else -> 3
    }
