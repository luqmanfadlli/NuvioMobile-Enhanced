package com.nuvio.app.features.home.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import com.nuvio.app.core.build.AppFeaturePolicy
import com.nuvio.app.isIos
import com.nuvio.app.core.build.TrailerPlaybackMode
import com.nuvio.app.core.format.formatReleaseDateForDisplay
import com.nuvio.app.core.ui.LocalDynamicArtworkBackgroundColor
import com.nuvio.app.core.ui.rememberDynamicArtworkBackgroundEnabled
import com.nuvio.app.core.ui.PrefetchArtworkDominantColors
import com.nuvio.app.core.ui.heroStretchHeight
import com.nuvio.app.core.ui.ScreenActivityEffect
import com.nuvio.app.core.ui.heroStretchZoom
import com.nuvio.app.features.details.HeroTrailerAudioState
import com.nuvio.app.features.details.HeroTrailerSurface
import com.nuvio.app.features.details.MetaDetailsRepository
import com.nuvio.app.features.details.components.DetailHeroMetaBadge
import com.nuvio.app.features.details.components.HeroTrailerPlayerSurface
import com.nuvio.app.features.details.selectHeroTrailer
import com.nuvio.app.features.details.youtubePlaybackUrl
import com.nuvio.app.features.home.HomeHeroArtworkSource
import com.nuvio.app.features.home.HomeHeroStyle
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.stableKey
import com.nuvio.app.features.player.MiniPlayerController
import com.nuvio.app.features.trailer.TrailerPlaybackResolver
import com.nuvio.app.features.trailer.TrailerPlaybackSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs

private const val HERO_BACKGROUND_PARALLAX = 0.055f
private const val HERO_BACKGROUND_SCALE = 1.14f
private const val HERO_CONTENT_PARALLAX = 0.18f
private const val HERO_SCROLL_PARALLAX = 0.3f
private const val HERO_SCROLL_DOWN_SCALE_MULTIPLIER = 0.0001f
private const val HERO_SCROLL_UP_SCALE_MULTIPLIER = 0.002f
private const val HERO_SCROLL_MAX_SCALE = 1.3f
private const val HERO_SWIPE_THRESHOLD_FRACTION = 0.16f
private const val HERO_SWIPE_VELOCITY_THRESHOLD = 300f
private const val HERO_AUTO_SCROLL_INTERVAL_MS = 8_000L
private const val MOBILE_HERO_VIEWPORT_RATIO = 0.82f
private const val MOBILE_PORTRAIT_HERO_WIDTH_RATIO = 1.5f
private const val MOBILE_HERO_MIN_HEIGHT_DP = 360f
private const val MOBILE_HERO_MAX_HEIGHT_DP = 760f
private const val TABLET_LANDSCAPE_HERO_HEIGHT_MULTIPLIER = 1.5f

internal object HomeHeroTrailerPlaybackController {
    private var stopCallback: (() -> Unit)? = null

    fun register(stop: () -> Unit) {
        stopCallback = stop
    }

    fun unregister(stop: () -> Unit) {
        if (stopCallback === stop) stopCallback = null
    }

    fun forceStop() {
        stopCallback?.invoke()
    }
}

private object HomeHeroTrailerPlaybackPositionStore {
    private val positions = mutableMapOf<String, Long>()

    fun get(key: String): Long = positions[key] ?: 0L

    fun set(key: String, positionMs: Long) {
        if (positionMs > 0L) positions[key] = positionMs
    }

    fun clear(key: String) {
        positions.remove(key)
    }
}

internal const val TMDB_POSTER_HEIGHT_RATIO = 1.5f

internal const val BACKDROP_HEIGHT_RATIO = 9f / 16f

private const val CARD_WIDE_MAX_VIEWPORT_FRACTION = 0.72f
internal val HERO_CARD_CORNER_RADIUS = 28.dp
internal val HERO_CARD_HORIZONTAL_PADDING = 12.dp
internal val HERO_CARD_TOP_PADDING = 8.dp

internal fun heroUsesWideArtwork(
    maxWidth: Dp,
    viewportHeight: Dp?,
    isTablet: Boolean,
): Boolean = isTablet || (viewportHeight != null && maxWidth > viewportHeight)

internal fun cardHeroHeight(
    maxWidth: Dp,
    viewportHeight: Dp?,
    isTablet: Boolean,
): Dp = if (heroUsesWideArtwork(maxWidth, viewportHeight, isTablet)) {
    val wideHeight = maxWidth * BACKDROP_HEIGHT_RATIO
    if (viewportHeight != null) {
        minOf(wideHeight, viewportHeight * CARD_WIDE_MAX_VIEWPORT_FRACTION)
    } else {
        wideHeight
    }
} else {
    maxWidth * TMDB_POSTER_HEIGHT_RATIO
}

internal data class HomeHeroLayout(
    val isTablet: Boolean,
    val heroHeight: Dp,
    val contentMaxWidth: Dp,
    val contentWidthFraction: Float,
    val contentHorizontalPadding: Dp,
    val contentVerticalPadding: Dp,
    val bottomFadeHeight: Dp,
    val logoWidthFraction: Float,
)

/**
 * Layout for [HomeHeroStyle.NETFLIX]. Derived purely from the available width and viewport height so the
 * four Android form factors (phone / tablet x portrait / landscape) are covered by one tested function.
 *
 * "Tablet" means the shortest viewport side is at least 600dp, so a 844x390 phone in landscape is a phone.
 *
 * Artwork is always the backdrop (posters carry the title, which would double up with the logo).
 *
 * - phone portrait : text + full-width buttons pinned to the bottom (side by side on Android, stacked pills on iOS)
 * - phone landscape: text column on the start side, compact buttons
 * - tablet portrait: start-aligned column, taller hero
 * - tablet landscape: start-aligned column, hero capped to the viewport
 */
internal data class NetflixHeroLayout(
    val isTablet: Boolean,
    val isLandscape: Boolean,
    val heroHeight: Dp,
    /** True when the text column sits on the start side and the scrim is drawn left-to-right. */
    val sideContent: Boolean,
    val contentWidthFraction: Float,
    val contentMaxWidth: Dp,
    val contentHorizontalPadding: Dp,
    val contentVerticalPadding: Dp,
    val logoWidth: Dp,
    /** 0 hides the synopsis (very short landscape heroes). */
    val synopsisMaxLines: Int,
    val buttonHeight: Dp,
    val buttonCornerRadius: Dp,
    val buttonsFillWidth: Boolean,
    /** Buttons are laid out one above the other instead of side by side (phone portrait). */
    val buttonsStacked: Boolean,
    val bottomFadeHeight: Dp,
)

private const val NETFLIX_LOGO_ASPECT_RATIO = 2.6f

internal fun netflixHeroLayout(
    maxWidthDp: Float,
    viewportHeightDp: Float? = null,
    mobileBelowSectionHeightHintDp: Float? = null,
    /** iOS keeps the stacked full-width pill buttons in phone portrait; Android uses side-by-side buttons. */
    stackPhonePortraitButtons: Boolean = false,
): NetflixHeroLayout {
    val base = homeHeroLayout(
        maxWidthDp = maxWidthDp,
        viewportHeightDp = viewportHeightDp,
        mobileBelowSectionHeightHintDp = mobileBelowSectionHeightHintDp,
    )
    val isLandscape = viewportHeightDp?.let { maxWidthDp > it } == true
    // homeHeroLayout keys "tablet" off width alone, which would make every phone in landscape a tablet.
    // Use the shortest side (the standard 600dp sw threshold) so phones stay phones when rotated.
    val shortestSideDp = viewportHeightDp?.let { minOf(maxWidthDp, it) } ?: maxWidthDp
    val isTablet = shortestSideDp >= 600f
    val phoneHeroHeight = mobileHeroHeight(
        maxWidthDp = maxWidthDp,
        viewportHeightDp = viewportHeightDp,
        mobileBelowSectionHeightHintDp = mobileBelowSectionHeightHintDp,
    )

    fun columnWidth(horizontalPaddingDp: Float, fraction: Float, maxDp: Float): Float =
        minOf((maxWidthDp - horizontalPaddingDp * 2f).coerceAtLeast(0f) * fraction, maxDp)

    fun logoWidth(columnWidthDp: Float, logoFraction: Float, logoMaxHeightDp: Float): Dp =
        minOf(columnWidthDp * logoFraction, logoMaxHeightDp * NETFLIX_LOGO_ASPECT_RATIO).dp

    return when {
        // Phone, portrait.
        !isTablet && !isLandscape -> {
            val hPad = 20f
            val column = columnWidth(hPad, 1f, 480f)
            NetflixHeroLayout(
                isTablet = false,
                isLandscape = false,
                heroHeight = phoneHeroHeight,
                sideContent = false,
                contentWidthFraction = 1f,
                contentMaxWidth = 480.dp,
                contentHorizontalPadding = hPad.dp,
                contentVerticalPadding = 20.dp,
                logoWidth = logoWidth(column, 0.7f, 84f),
                synopsisMaxLines = 2,
                buttonHeight = if (stackPhonePortraitButtons) 48.dp else 44.dp,
                // Stacked: fully rounded pill. Side by side: slightly rounded rectangles.
                buttonCornerRadius = if (stackPhonePortraitButtons) 24.dp else 6.dp,
                buttonsFillWidth = true,
                buttonsStacked = stackPhonePortraitButtons,
                bottomFadeHeight = 120.dp,
            )
        }

        // Phone, landscape: height is the scarce resource, so shrink the logo and drop the synopsis if needed.
        !isTablet -> {
            val hPad = 32f
            val column = columnWidth(hPad, 0.52f, 480f)
            val heroHeight = phoneHeroHeight
            NetflixHeroLayout(
                isTablet = false,
                isLandscape = true,
                heroHeight = heroHeight,
                sideContent = true,
                contentWidthFraction = 0.52f,
                contentMaxWidth = 480.dp,
                contentHorizontalPadding = hPad.dp,
                contentVerticalPadding = 16.dp,
                logoWidth = logoWidth(column, 0.7f, 60f),
                synopsisMaxLines = when {
                    heroHeight.value >= 340f -> 3
                    heroHeight.value >= 290f -> 2
                    else -> 0
                },
                buttonHeight = 40.dp,
                buttonCornerRadius = 8.dp,
                buttonsFillWidth = false,
                buttonsStacked = false,
                bottomFadeHeight = 90.dp,
            )
        }

        // Tablet, portrait.
        !isLandscape -> {
            val hPad = 40f
            val column = columnWidth(hPad, 0.66f, 560f)
            val wanted = (maxWidthDp * 0.62f).coerceIn(420f, 640f)
            val heroHeight = maxOf(base.heroHeight.value, wanted).let { height ->
                if (viewportHeightDp != null) minOf(height, viewportHeightDp * 0.62f) else height
            }
            NetflixHeroLayout(
                isTablet = true,
                isLandscape = false,
                heroHeight = heroHeight.dp,
                sideContent = true,
                contentWidthFraction = 0.66f,
                contentMaxWidth = 560.dp,
                contentHorizontalPadding = hPad.dp,
                contentVerticalPadding = 28.dp,
                logoWidth = logoWidth(column, 0.6f, 110f),
                synopsisMaxLines = 3,
                buttonHeight = 48.dp,
                buttonCornerRadius = 8.dp,
                buttonsFillWidth = false,
                buttonsStacked = false,
                bottomFadeHeight = 140.dp,
            )
        }

        // Tablet, landscape.
        else -> {
            val hPad = if (maxWidthDp >= 1200f) 56f else 44f
            val column = columnWidth(hPad, 0.48f, 600f)
            val heroHeight = if (viewportHeightDp != null) {
                minOf(base.heroHeight.value, viewportHeightDp * 0.8f)
            } else {
                base.heroHeight.value
            }
            NetflixHeroLayout(
                isTablet = true,
                isLandscape = true,
                heroHeight = heroHeight.dp,
                sideContent = true,
                contentWidthFraction = 0.48f,
                contentMaxWidth = 600.dp,
                contentHorizontalPadding = hPad.dp,
                contentVerticalPadding = 32.dp,
                logoWidth = logoWidth(column, 0.6f, 130f),
                synopsisMaxLines = if (heroHeight >= 420f) 3 else 2,
                buttonHeight = 48.dp,
                buttonCornerRadius = 8.dp,
                buttonsFillWidth = false,
                buttonsStacked = false,
                bottomFadeHeight = 160.dp,
            )
        }
    }
}

@Composable
internal fun HomeHeroSection(
    items: List<MetaPreview>,
    modifier: Modifier = Modifier,
    viewportHeight: Dp? = null,
    mobileBelowSectionHeightHint: Dp? = null,
    heroStyle: HomeHeroStyle = HomeHeroStyle.FULL_BLEED,
    listState: LazyListState? = null,
    stretchPx: () -> Float = { 0f },
    trailerPlaybackEnabled: Boolean = false,
    trailerStartDelaySeconds: Int = 0,
    trailerStartUnmuted: Boolean = false,
    onItemClick: ((MetaPreview) -> Unit)? = null,
    onActiveArtworkChange: ((String?) -> Unit)? = null,
    /** [MetaPreview.stableKey]s of hero items that are in the library. Only used by the Netflix style. */
    savedItemKeys: Set<String> = emptySet(),
    /** Toggles library membership. When null the Netflix style hides its "Save to Library" button. */
    onToggleSaved: ((MetaPreview) -> Unit)? = null,
) {
    if (items.isEmpty()) return

    val pagerState = key(items.size) {
        rememberPagerState(
            initialPage = if (items.size > 1) {
                Int.MAX_VALUE / 2 - (Int.MAX_VALUE / 2) % items.size
            } else {
                0
            },
            pageCount = { if (items.size > 1) Int.MAX_VALUE else 1 },
        )
    }
    val coroutineScope = rememberCoroutineScope()
    val autoScrollPage = pagerState.settledPage
    val effectiveTrailerPlaybackEnabled = trailerPlaybackEnabled &&
        AppFeaturePolicy.heroTrailerPlaybackSupported &&
        AppFeaturePolicy.trailerPlaybackMode == TrailerPlaybackMode.IN_APP &&
        !MiniPlayerController.isActive

    LaunchedEffect(pagerState) {
        pagerState.scrollToPage(pagerState.currentPage)
    }

    ScreenActivityEffect(pagerState) { active ->
        if (!active) {
            pagerState.stopScroll(MutatePriority.PreventUserInput)
            pagerState.scrollToPage(pagerState.currentPage)
        }
    }

    ScreenActivityEffect(autoScrollPage, items.size, effectiveTrailerPlaybackEnabled) { active ->
        if (!active || items.size <= 1 || effectiveTrailerPlaybackEnabled) return@ScreenActivityEffect
        delay(HERO_AUTO_SCROLL_INTERVAL_MS)
        while (pagerState.isScrollInProgress) {
            delay(100L)
        }

        val nextPage = pagerState.currentPage + 1
        pagerState.animateScrollToPage(nextPage)
    }

    val isCardStyle = heroStyle == HomeHeroStyle.CARD
    val isNetflixStyle = heroStyle == HomeHeroStyle.NETFLIX
    val statusBarTopPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val cardInsetModifier = if (isCardStyle) {
        Modifier.padding(
            top = statusBarTopPadding + HERO_CARD_TOP_PADDING,
            start = HERO_CARD_HORIZONTAL_PADDING,
            end = HERO_CARD_HORIZONTAL_PADDING,
        )
    } else {
        Modifier
    }
    val heroShape = when {
        isCardStyle -> RoundedCornerShape(HERO_CARD_CORNER_RADIUS)
        isNetflixStyle -> RectangleShape
        else -> RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .then(cardInsetModifier)
                .homeHeroPagerGesture(
                    pagerState = pagerState,
                    itemCount = items.size,
                    coroutineScope = coroutineScope,
                )
                .clip(heroShape),
        ) {
            val baseLayout = homeHeroLayout(
                maxWidthDp = maxWidth.value,
                viewportHeightDp = viewportHeight?.value,
                mobileBelowSectionHeightHintDp = mobileBelowSectionHeightHint?.value,
            )
            val netflixLayout = if (isNetflixStyle) {
                netflixHeroLayout(
                    maxWidthDp = maxWidth.value,
                    viewportHeightDp = viewportHeight?.value,
                    mobileBelowSectionHeightHintDp = mobileBelowSectionHeightHint?.value,
                    stackPhonePortraitButtons = isIos,
                )
            } else {
                null
            }
            val layout = when {
                isCardStyle -> baseLayout.copy(
                    heroHeight = cardHeroHeight(
                        maxWidth = maxWidth,
                        viewportHeight = viewportHeight,
                        isTablet = baseLayout.isTablet,
                    ),
                )
                netflixLayout != null -> baseLayout.copy(
                    heroHeight = netflixLayout.heroHeight,
                    bottomFadeHeight = netflixLayout.bottomFadeHeight,
                )
                else -> baseLayout
            }
            val effectiveArtworkSource = when {
                // Always the backdrop: posters have the title printed on them, which doubles up with our logo.
                netflixLayout != null -> HomeHeroArtworkSource.BACKDROP
                !isCardStyle -> HomeHeroArtworkSource.BACKDROP
                heroUsesWideArtwork(maxWidth, viewportHeight, baseLayout.isTablet) ->
                    HomeHeroArtworkSource.BACKDROP
                else -> HomeHeroArtworkSource.POSTER
            }
            val artworkParallax = if (isCardStyle) 0f else HERO_BACKGROUND_PARALLAX
            val artworkBaseScale = if (isCardStyle) 1f else HERO_BACKGROUND_SCALE
            val heroWidthPx = with(LocalDensity.current) { maxWidth.toPx() }
            val heroHeightPx = with(LocalDensity.current) { layout.heroHeight.toPx() }
            val scrollOffsetPx by remember(listState, heroHeightPx) {
                derivedStateOf {
                    when {
                        listState == null -> 0f
                        listState.firstVisibleItemIndex > 0 -> heroHeightPx
                        else -> listState.firstVisibleItemScrollOffset.toFloat()
                    }
                }
            }
            val isHeroScrolledAway by remember(listState, heroHeightPx) {
                derivedStateOf { scrollOffsetPx >= heroHeightPx * 0.6f }
            }
            val currentPage = pagerState.currentPage
            val visiblePages = remember(currentPage, pagerState.pageCount, items.size) {
                listOf(
                    (currentPage - 1).coerceAtLeast(0),
                    (currentPage + 1).coerceAtMost(pagerState.pageCount - 1),
                    currentPage,
                ).distinct()
                    .map { page -> HeroPageLayer(page = page, itemIndex = page % items.size) }
            }
            val currentItem = items[currentPage % items.size]

            val activeArtworkUrl = heroArtworkUrl(items[pagerState.targetPage % items.size], effectiveArtworkSource)
            LaunchedEffect(onActiveArtworkChange, activeArtworkUrl) {
                onActiveArtworkChange?.invoke(activeArtworkUrl)
            }
            if (onActiveArtworkChange != null && rememberDynamicArtworkBackgroundEnabled()) {
                val heroArtworkUrls = remember(items, effectiveArtworkSource) {
                    items.mapNotNull { item -> heroArtworkUrl(item, effectiveArtworkSource) }
                }
                PrefetchArtworkDominantColors(heroArtworkUrls)
            }

            val platformContext = LocalPlatformContext.current
            LaunchedEffect(pagerState.settledPage, items, effectiveArtworkSource) {
                val settled = pagerState.settledPage
                val neighbours = listOf(settled + 1, settled - 1)
                    .filter { page -> page >= 0 && items.size > 1 }
                    .map { page -> items[page % items.size] }
                    .distinct()
                val imageLoader = SingletonImageLoader.get(platformContext)
                neighbours.forEach { item ->
                    listOfNotNull(heroArtworkUrl(item, effectiveArtworkSource), item.logo?.takeIf { it.isNotBlank() })
                        .forEach { url ->
                            imageLoader.enqueue(ImageRequest.Builder(platformContext).data(url).build())
                        }
                }
                neighbours.forEach { item ->
                    if (MetaDetailsRepository.peek(item.type, item.id) == null) {
                        runCatching { MetaDetailsRepository.fetch(item.type, item.id) }
                    }
                }
            }

            val heroTrailerItemKey = "${currentItem.type}:${currentItem.id}"
            var heroTrailerPlaybackSource by remember(currentItem.type, currentItem.id) {
                mutableStateOf<TrailerPlaybackSource?>(null)
            }
            var heroTrailerReady by remember(currentItem.type, currentItem.id) { mutableStateOf(false) }
            var heroTrailerFinished by remember(currentItem.type, currentItem.id) { mutableStateOf(false) }
            val heroTrailerMuted by HeroTrailerAudioState
                .muted(HeroTrailerSurface.Home)
                .collectAsStateWithLifecycle()

            LaunchedEffect(trailerStartUnmuted) {
                HeroTrailerAudioState.applyStartMuted(HeroTrailerSurface.Home, !trailerStartUnmuted)
            }

            val latestForceStopTrailer = rememberUpdatedState {
                if (heroTrailerPlaybackSource != null || !heroTrailerFinished) {
                    heroTrailerPlaybackSource = null
                    heroTrailerReady = false
                    heroTrailerFinished = true
                }
            }
            DisposableEffect(Unit) {
                val callback: () -> Unit = { latestForceStopTrailer.value.invoke() }
                HomeHeroTrailerPlaybackController.register(callback)
                onDispose { HomeHeroTrailerPlaybackController.unregister(callback) }
            }

            LaunchedEffect(
                effectiveTrailerPlaybackEnabled,
                currentItem.type,
                currentItem.id,
                trailerStartDelaySeconds,
            ) {
                heroTrailerPlaybackSource = null
                heroTrailerReady = false
                heroTrailerFinished = false
                if (!effectiveTrailerPlaybackEnabled) return@LaunchedEffect
                val resolvedSource = async {
                    val meta = runCatching {
                        MetaDetailsRepository.fetch(currentItem.type, currentItem.id)
                    }.getOrNull()
                    val trailer = meta?.trailers?.let(::selectHeroTrailer) ?: return@async null
                    runCatching {
                        TrailerPlaybackResolver.resolveFromYouTubeUrl(trailer.youtubePlaybackUrl())
                    }.getOrNull()
                }
                if (trailerStartDelaySeconds > 0) {
                    delay(trailerStartDelaySeconds.toLong() * 1000L)
                }
                val source = resolvedSource.await()
                if (source == null) {
                    heroTrailerFinished = true
                } else {
                    heroTrailerPlaybackSource = source
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heroStretchHeight(layout.heroHeight, stretchPx),
            ) {
                HorizontalPager(
                    state = pagerState,
                    userScrollEnabled = false,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = 0.01f },
                ) {
                    Box(modifier = Modifier.fillMaxSize())
                }

                Box(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(layout.heroHeight)
                            .heroStretchZoom(stretchPx),
                    ) {
                        visiblePages.forEach { layer ->
                            val item = items[layer.itemIndex]
                            val artworkUrl = heroArtworkUrl(item, effectiveArtworkSource)
                            val isCurrentLayer = layer.page == currentPage
                            key(effectiveArtworkSource, item.type, item.id, artworkUrl) {
                                AsyncImage(
                                    model = artworkUrl,
                                    contentDescription = item.name,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer {
                                            val offset = heroPageOffset(pagerState, layer.page)
                                            val visibility = (1f - abs(offset)).coerceIn(0f, 1f)
                                            alpha = when {
                                                isCurrentLayer -> visibility
                                                visibility > 0f -> 1f
                                                else -> 0f
                                            }
                                            translationX = -offset * heroWidthPx * artworkParallax
                                            translationY = if (isCardStyle) 0f else heroBackgroundScrollTranslationY(scrollOffsetPx)
                                            scaleX = artworkBaseScale *
                                                if (isCardStyle) 1f else heroBackgroundScrollScale(scrollOffsetPx)
                                            scaleY = artworkBaseScale *
                                                if (isCardStyle) 1f else heroBackgroundScrollScale(scrollOffsetPx)
                                        },
                                    alignment = if (layout.isTablet) Alignment.TopCenter else Alignment.Center,
                                    contentScale = ContentScale.Crop,
                                )
                            }
                        }

                        val heroTrailerSourceUrl = heroTrailerPlaybackSource
                            ?.videoUrl
                            ?.takeIf { it.isNotBlank() && effectiveTrailerPlaybackEnabled && !heroTrailerFinished }
                        if (heroTrailerSourceUrl != null) {
                            val trailerReadyAlpha by animateFloatAsState(
                                targetValue = if (heroTrailerReady) 1f else 0f,
                                animationSpec = tween(durationMillis = 300),
                                label = "home_hero_trailer_alpha",
                            )
                            HeroTrailerPlayerSurface(
                                sourceUrl = heroTrailerSourceUrl,
                                sourceAudioUrl = heroTrailerPlaybackSource?.audioUrl,
                                playWhenReady = !heroTrailerFinished && !isHeroScrolledAway,
                                muted = heroTrailerMuted,
                                startPositionMs = remember(heroTrailerItemKey) {
                                    HomeHeroTrailerPlaybackPositionStore.get(heroTrailerItemKey)
                                },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        val currentPageOffset = pagerState.currentPageOffsetFraction
                                        val currentPageVisibility = (1f - abs(currentPageOffset)).coerceIn(0f, 1f)
                                        alpha = trailerReadyAlpha * currentPageVisibility
                                        translationX = -currentPageOffset * heroWidthPx * artworkParallax
                                        translationY = if (isCardStyle) 0f else heroBackgroundScrollTranslationY(scrollOffsetPx)
                                        scaleX = artworkBaseScale * if (isCardStyle) 1f else heroBackgroundScrollScale(scrollOffsetPx)
                                        scaleY = artworkBaseScale * if (isCardStyle) 1f else heroBackgroundScrollScale(scrollOffsetPx)
                                    },
                                onReady = {
                                    if (!heroTrailerFinished) heroTrailerReady = true
                                },
                                onEnded = {
                                    heroTrailerReady = false
                                    heroTrailerFinished = true
                                    HomeHeroTrailerPlaybackPositionStore.clear(heroTrailerItemKey)
                                },
                                onError = {
                                    heroTrailerReady = false
                                    heroTrailerFinished = true
                                    HomeHeroTrailerPlaybackPositionStore.clear(heroTrailerItemKey)
                                },
                                onPositionUpdate = { positionMs ->
                                    HomeHeroTrailerPlaybackPositionStore.set(heroTrailerItemKey, positionMs)
                                },
                            )
                        }
                    }

                    if (netflixLayout != null) {
                        val heroFadeColorState = LocalDynamicArtworkBackgroundColor.current
                        val themeBackground = MaterialTheme.colorScheme.background
                        // Legibility scrim: left-to-right when the text sits on the start side, otherwise bottom-heavy.
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .drawBehind {
                                    if (netflixLayout.sideContent) {
                                        drawRect(
                                            Brush.horizontalGradient(
                                                0f to Color.Black.copy(alpha = 0.84f),
                                                0.45f to Color.Black.copy(alpha = 0.55f),
                                                0.8f to Color.Black.copy(alpha = 0.10f),
                                                1f to Color.Transparent,
                                            ),
                                        )
                                        drawRect(
                                            Brush.verticalGradient(
                                                0f to Color.Transparent,
                                                0.55f to Color.Transparent,
                                                1f to Color.Black.copy(alpha = 0.55f),
                                            ),
                                        )
                                    } else {
                                        drawRect(
                                            Brush.verticalGradient(
                                                0f to Color.Black.copy(alpha = 0.18f),
                                                0.4f to Color.Transparent,
                                                0.72f to Color.Black.copy(alpha = 0.55f),
                                                1f to Color.Black.copy(alpha = 0.88f),
                                            ),
                                        )
                                    }
                                },
                        )
                        // Blend the bottom edge into the screen background so catalog rows sit flush under the hero.
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(netflixLayout.bottomFadeHeight)
                                .align(Alignment.BottomCenter)
                                .drawBehind {
                                    val fade = heroFadeColorState?.value ?: themeBackground
                                    drawRect(Brush.verticalGradient(colors = listOf(fade.copy(alpha = 0f), fade)))
                                },
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable(enabled = onItemClick != null) {
                                    onItemClick?.invoke(currentItem)
                                },
                        )
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .then(
                                    if (netflixLayout.isLandscape) {
                                        // Keep text clear of camera cut-outs / side navigation bars when rotated.
                                        Modifier.windowInsetsPadding(
                                            WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
                                        )
                                    } else {
                                        Modifier
                                    },
                                )
                                .padding(
                                    horizontal = netflixLayout.contentHorizontalPadding,
                                    vertical = netflixLayout.contentVerticalPadding,
                                ),
                            horizontalAlignment = if (netflixLayout.sideContent) {
                                Alignment.Start
                            } else {
                                Alignment.CenterHorizontally
                            },
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(netflixLayout.contentWidthFraction)
                                    .widthIn(max = netflixLayout.contentMaxWidth),
                                contentAlignment = Alignment.BottomStart,
                            ) {
                                visiblePages.forEach { layer ->
                                    val layerItem = items[layer.itemIndex]
                                    val isCurrentLayer = layer.page == currentPage
                                    key(layerItem.type, layerItem.id) {
                                        Box(
                                            modifier = Modifier.graphicsLayer {
                                                val offset = heroPageOffset(pagerState, layer.page)
                                                val visibility = (1f - abs(offset)).coerceIn(0f, 1f)
                                                alpha = ((visibility - 0.5f) * 2f).coerceIn(0f, 1f)
                                                translationX = -offset * heroWidthPx * HERO_CONTENT_PARALLAX
                                            },
                                        ) {
                                            HeroContentBlock(
                                                item = layerItem,
                                                layout = layout,
                                                onItemClick = onItemClick,
                                                startAligned = true,
                                                contentColor = Color.White,
                                                logoWidth = netflixLayout.logoWidth,
                                                synopsisMaxLines = netflixLayout.synopsisMaxLines,
                                                actions = {
                                                    HeroActionButtons(
                                                        item = layerItem,
                                                        isSaved = layerItem.stableKey() in savedItemKeys,
                                                        // Neighbour layers sit invisibly underneath during a swipe;
                                                        // they must never swallow taps meant for the visible title.
                                                        enabled = isCurrentLayer,
                                                        fillWidth = netflixLayout.buttonsFillWidth,
                                                        stacked = netflixLayout.buttonsStacked,
                                                        buttonHeight = netflixLayout.buttonHeight,
                                                        cornerRadius = netflixLayout.buttonCornerRadius,
                                                        onViewDetails = onItemClick,
                                                        onToggleSaved = onToggleSaved,
                                                    )
                                                },
                                            )
                                        }
                                    }
                                }
                            }

                            if (items.size > 1) {
                                Spacer(modifier = Modifier.height(if (netflixLayout.isTablet) 18.dp else 14.dp))
                                HeroPageIndicator(
                                    itemCount = items.size,
                                    pagerState = pagerState,
                                    coroutineScope = coroutineScope,
                                    color = Color.White,
                                )
                            }
                        }
                    } else if (!isCardStyle) {
                        val heroFadeColorState = LocalDynamicArtworkBackgroundColor.current
                        val themeBackground = MaterialTheme.colorScheme.background
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .drawBehind {
                                    val fade = heroFadeColorState?.value ?: themeBackground
                                    drawRect(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                fade.copy(alpha = 0.02f),
                                                fade.copy(alpha = 0.12f),
                                                fade.copy(alpha = 0.34f),
                                                fade.copy(alpha = 0.78f),
                                            ),
                                        ),
                                    )
                                },
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(layout.bottomFadeHeight)
                                .align(Alignment.BottomCenter)
                                .drawBehind {
                                    val fade = heroFadeColorState?.value ?: themeBackground
                                    drawRect(Brush.verticalGradient(colors = listOf(fade.copy(alpha = 0f), fade)))
                                },
                        )
                    }

                    if (isCardStyle) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable(enabled = onItemClick != null) {
                                    onItemClick?.invoke(currentItem)
                                },
                        )
                    } else if (netflixLayout == null) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable(enabled = onItemClick != null) {
                                    onItemClick?.invoke(currentItem)
                                },
                        )
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(
                                    horizontal = layout.contentHorizontalPadding,
                                    vertical = layout.contentVerticalPadding,
                                ),
                            horizontalAlignment = if (layout.isTablet) Alignment.Start else Alignment.CenterHorizontally,
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(layout.contentWidthFraction)
                                    .widthIn(max = layout.contentMaxWidth),
                                contentAlignment = Alignment.BottomStart,
                            ) {
                                visiblePages.forEach { layer ->
                                    val layerItem = items[layer.itemIndex]
                                    key(layerItem.type, layerItem.id) {
                                    Box(
                                        modifier = Modifier.graphicsLayer {
                                            val offset = heroPageOffset(pagerState, layer.page)
                                            val visibility = (1f - abs(offset)).coerceIn(0f, 1f)
                                            alpha = ((visibility - 0.5f) * 2f).coerceIn(0f, 1f)
                                            translationX = -offset * heroWidthPx * HERO_CONTENT_PARALLAX
                                        },
                                    ) {
                                        HeroContentBlock(
                                            item = layerItem,
                                            layout = layout,
                                            onItemClick = onItemClick,
                                        )
                                    }
                                    }
                                }
                            }

                            if (items.size > 1) {
                                Spacer(modifier = Modifier.height(if (layout.isTablet) 16.dp else 18.dp))
                                HeroPageIndicator(
                                    itemCount = items.size,
                                    pagerState = pagerState,
                                    coroutineScope = coroutineScope,
                                )
                            }
                        }
                    }

                    if (heroTrailerReady && heroTrailerPlaybackSource != null) {
                        val muteIconSize = 20.dp
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(
                                    top = statusBarTopPadding + 12.dp,
                                    end = if (layout.isTablet) 32.dp else 18.dp,
                                )
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.35f))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) {
                                    HeroTrailerAudioState.toggleMuted(HeroTrailerSurface.Home)
                                }
                                .padding(8.dp),
                        ) {
                            Icon(
                                imageVector = if (heroTrailerMuted) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(muteIconSize),
                            )
                        }
                    }
                }
            }
        }

        if (isCardStyle && items.size > 1) {
            Spacer(modifier = Modifier.height(12.dp))
            HeroPageIndicator(
                itemCount = items.size,
                pagerState = pagerState,
                coroutineScope = coroutineScope,
            )
        }
    }
}

@Composable
private fun HeroPageIndicator(
    itemCount: Int,
    pagerState: PagerState,
    coroutineScope: CoroutineScope,
    color: Color = MaterialTheme.colorScheme.onBackground,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(itemCount) { index ->
            val activeFraction = heroItemVisibility(pagerState, index, itemCount)
            Box(
                modifier = Modifier
                    .clickable {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(
                                heroPageForItem(pagerState.currentPage, index, itemCount),
                            )
                        }
                    }
                    .clip(CircleShape)
                    .background(color)
                    .graphicsLayer {
                        alpha = 0.35f + (0.57f * activeFraction)
                    }
                    .width(8.dp + (24.dp * activeFraction))
                    .height(8.dp),
            )
        }
    }
}

private data class HeroPageLayer(
    val page: Int,
    val itemIndex: Int,
)

private fun heroArtworkUrl(item: MetaPreview, source: HomeHeroArtworkSource): String? = when (source) {
    HomeHeroArtworkSource.POSTER -> item.poster ?: item.banner
    HomeHeroArtworkSource.BACKDROP -> item.banner ?: item.poster
}

private fun heroPageOffset(
    pagerState: PagerState,
    page: Int,
): Float = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction

private fun heroItemVisibility(pagerState: PagerState, itemIndex: Int, itemCount: Int): Float {
    val page = heroPageForItem(pagerState.currentPage, itemIndex, itemCount)
    return (1f - abs(heroPageOffset(pagerState, page))).coerceIn(0f, 1f)
}

internal fun heroPageForItem(currentPage: Int, itemIndex: Int, itemCount: Int): Int {
    val page = currentPage.toLong() - currentPage % itemCount + itemIndex
    return listOf(page - itemCount, page, page + itemCount)
        .filter { it in 0L until Int.MAX_VALUE.toLong() }
        .minBy { abs(it - currentPage) }
        .toInt()
}

@Composable
internal fun HomeHeroReservedSpace(
    modifier: Modifier = Modifier,
    viewportHeight: Dp? = null,
    mobileBelowSectionHeightHint: Dp? = null,
    heroStyle: HomeHeroStyle = HomeHeroStyle.FULL_BLEED,
) {
    val isCardStyle = heroStyle == HomeHeroStyle.CARD
    val isNetflixStyle = heroStyle == HomeHeroStyle.NETFLIX
    val statusBarTopPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isCardStyle) {
                    Modifier.padding(
                        top = statusBarTopPadding + HERO_CARD_TOP_PADDING,
                        start = HERO_CARD_HORIZONTAL_PADDING,
                        end = HERO_CARD_HORIZONTAL_PADDING,
                    )
                } else {
                    Modifier
                },
            )
            .clip(
                when {
                    isCardStyle -> RoundedCornerShape(HERO_CARD_CORNER_RADIUS)
                    isNetflixStyle -> RectangleShape
                    else -> RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
                },
            ),
    ) {
        val baseLayout = homeHeroLayout(
            maxWidthDp = maxWidth.value,
            viewportHeightDp = viewportHeight?.value,
            mobileBelowSectionHeightHintDp = mobileBelowSectionHeightHint?.value,
        )
        val heroHeight = when {
            isCardStyle -> cardHeroHeight(
                maxWidth = maxWidth,
                viewportHeight = viewportHeight,
                isTablet = baseLayout.isTablet,
            )
            isNetflixStyle -> netflixHeroLayout(
                maxWidthDp = maxWidth.value,
                viewportHeightDp = viewportHeight?.value,
                mobileBelowSectionHeightHintDp = mobileBelowSectionHeightHint?.value,
                stackPhonePortraitButtons = isIos,
            ).heroHeight
            else -> baseLayout.heroHeight
        }

        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(heroHeight),
        )
    }
}

@Composable
private fun HeroContentBlock(
    item: MetaPreview,
    layout: HomeHeroLayout,
    onItemClick: ((MetaPreview) -> Unit)?,
    startAligned: Boolean = layout.isTablet,
    contentColor: Color = MaterialTheme.colorScheme.onBackground,
    /** Fixed logo width (Netflix style). Null keeps the original fraction-of-width sizing. */
    logoWidth: Dp? = null,
    synopsisMaxLines: Int = 2,
    /** Rendered under the synopsis (Netflix style action buttons). */
    actions: (@Composable () -> Unit)? = null,
) {
    var logoLoadError by remember(item.type, item.id, item.logo) {
        mutableStateOf(false)
    }
    val logoUrl = item.logo?.takeIf { it.isNotBlank() }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (startAligned) Alignment.Start else Alignment.CenterHorizontally,
    ) {
        if (logoUrl != null && !logoLoadError) {
            AsyncImage(
                model = logoUrl,
                contentDescription = item.name,
                modifier = (
                    if (logoWidth != null) {
                        Modifier.width(logoWidth)
                    } else {
                        Modifier.fillMaxWidth(layout.logoWidthFraction)
                    }
                    )
                    .aspectRatio(2.6f)
                    .clickable(enabled = onItemClick != null) {
                        onItemClick?.invoke(item)
                    },
                alignment = if (startAligned) Alignment.CenterStart else Alignment.Center,
                contentScale = ContentScale.Fit,
                onError = { logoLoadError = true },
            )
        } else {
            Text(
                text = item.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = onItemClick != null) {
                        onItemClick?.invoke(item)
                    },
                style = if (layout.isTablet) {
                    MaterialTheme.typography.displaySmall
                } else {
                    MaterialTheme.typography.displaySmall
                },
                color = contentColor,
                fontWeight = FontWeight.Black,
                textAlign = if (startAligned) TextAlign.Start else TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        val detailsState = remember(item.type, item.id) {
            mutableStateOf(MetaDetailsRepository.peek(item.type, item.id))
        }
        LaunchedEffect(item.type, item.id) {
            if (detailsState.value == null) {
                detailsState.value = runCatching {
                    MetaDetailsRepository.fetch(item.type, item.id)
                }.getOrNull()
            }
        }
        val details = detailsState.value
        val rating = (item.imdbRating ?: details?.imdbRating)
            ?.takeIf { raw -> raw.toDoubleOrNull()?.let { it > 0.0 } == true }
        val year = (item.releaseInfo ?: details?.releaseInfo)
            ?.takeIf { it.isNotBlank() }
            ?.let(::formatReleaseDateForDisplay)
        val genre = (item.genres.firstOrNull() ?: details?.genres?.firstOrNull())?.takeIf { it.isNotBlank() }
        val ageRating = details?.ageRating?.trim()?.takeIf { it.isNotBlank() }
        val synopsis = (item.description ?: details?.description)?.trim()?.takeIf { it.isNotBlank() }
        val typeLabel = when (item.type.lowercase()) {
            "movie" -> stringResource(Res.string.media_movie)
            "series", "tv", "show" -> stringResource(Res.string.media_series)
            else -> item.type.replaceFirstChar(Char::uppercase)
        }
        val textAlign = if (startAligned) TextAlign.Start else TextAlign.Center

        Spacer(modifier = Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (startAligned) {
                Arrangement.spacedBy(8.dp, Alignment.Start)
            } else {
                Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
            },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            var needsDot = false
            if (rating != null) {
                Icon(
                    imageVector = Icons.Rounded.Star,
                    contentDescription = null,
                    tint = HeroRatingStar,
                    modifier = Modifier.size(18.dp),
                )
                HeroMetaText(text = rating, color = contentColor)
                needsDot = true
            }
            year?.let {
                if (needsDot) HeroMetaDot(color = contentColor)
                HeroMetaText(text = it, color = contentColor)
                needsDot = true
            }
            genre?.let {
                if (needsDot) HeroMetaDot(color = contentColor)
                HeroMetaText(text = it, color = contentColor, modifier = Modifier.weight(1f, fill = false))
                needsDot = true
            }
            ageRating?.let {
                if (needsDot) HeroMetaDot(color = contentColor)
                DetailHeroMetaBadge(text = it, contentColor = contentColor)
                needsDot = true
            }
            if (needsDot) HeroMetaDot(color = contentColor)
            HeroTypePill(text = typeLabel, color = contentColor)
        }

        if (synopsis != null && synopsisMaxLines > 0) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = synopsis,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor.copy(alpha = 0.72f),
                textAlign = textAlign,
                maxLines = synopsisMaxLines,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (actions != null) {
            Spacer(modifier = Modifier.height(16.dp))
            actions()
        }
    }
}

/**
 * Netflix-style hero buttons: a white primary "View Details" button and a translucent grey
 * "Save to Library" toggle. There is intentionally no play button.
 */
@Composable
private fun HeroActionButtons(
    item: MetaPreview,
    isSaved: Boolean,
    enabled: Boolean,
    fillWidth: Boolean,
    stacked: Boolean,
    buttonHeight: Dp,
    cornerRadius: Dp,
    onViewDetails: ((MetaPreview) -> Unit)?,
    onToggleSaved: ((MetaPreview) -> Unit)?,
) {
    val viewDetailsLabel = stringResource(Res.string.home_view_details)
    val saveLabel = stringResource(
        if (isSaved) Res.string.home_hero_saved_to_library else Res.string.home_hero_save_to_library,
    )
    val saveDescription = stringResource(
        if (isSaved) Res.string.hero_remove_from_library else Res.string.hero_add_to_library,
    )

    @Composable
    fun ViewDetailsButton(modifier: Modifier) {
        HeroActionButton(
            modifier = modifier,
            label = viewDetailsLabel,
            icon = Icons.Rounded.Info,
            containerColor = Color.White,
            contentColor = Color.Black,
            height = buttonHeight,
            cornerRadius = cornerRadius,
            enabled = enabled && onViewDetails != null,
            onClick = { onViewDetails?.invoke(item) },
        )
    }

    @Composable
    fun SaveButton(modifier: Modifier) {
        if (onToggleSaved == null) return
        HeroActionButton(
            modifier = modifier,
            label = saveLabel,
            icon = if (isSaved) Icons.Rounded.Check else Icons.Rounded.Add,
            containerColor = HeroSecondaryButtonColor,
            contentColor = Color.White,
            height = buttonHeight,
            cornerRadius = cornerRadius,
            enabled = enabled,
            semanticsDescription = saveDescription,
            onClick = { onToggleSaved(item) },
        )
    }

    if (stacked) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ViewDetailsButton(Modifier.fillMaxWidth())
            SaveButton(Modifier.fillMaxWidth())
        }
    } else {
        Row(
            modifier = if (fillWidth) Modifier.fillMaxWidth() else Modifier,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val buttonModifier = if (fillWidth) Modifier.weight(1f) else Modifier
            ViewDetailsButton(buttonModifier)
            SaveButton(buttonModifier)
        }
    }
}

private val HeroSecondaryButtonColor = Color(0xFF6D6D6E).copy(alpha = 0.7f)

@Composable
private fun HeroActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    containerColor: Color,
    contentColor: Color,
    height: Dp,
    cornerRadius: Dp,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    semanticsDescription: String? = null,
) {
    Row(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(cornerRadius))
            .background(containerColor)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .then(
                if (semanticsDescription != null) {
                    Modifier.semantics(mergeDescendants = true) { contentDescription = semanticsDescription }
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun HeroTypePill(
    text: String,
    color: Color = MaterialTheme.colorScheme.onBackground,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = color,
            maxLines = 1,
        )
    }
}

private val HeroRatingStar = Color(0xFFF5C518)

@Composable
private fun HeroMetaText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onBackground,
) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.labelLarge,
        color = color,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

internal fun homeHeroLayout(
    maxWidthDp: Float,
    viewportHeightDp: Float? = null,
    mobileBelowSectionHeightHintDp: Float? = null,
): HomeHeroLayout {
    val isLandscapeViewport = viewportHeightDp?.let { maxWidthDp > it } == true
    val shortestViewportSideDp = viewportHeightDp?.let { minOf(maxWidthDp, it) } ?: maxWidthDp
    val shouldBoostTabletLandscapeHero = isLandscapeViewport && shortestViewportSideDp >= 600f

    return when {
        maxWidthDp >= 1200f -> HomeHeroLayout(
            isTablet = true,
            heroHeight = tabletHeroHeight(
                baseHeight = (maxWidthDp * 0.42f).dp.coerceIn(360.dp, 440.dp),
                boostForLandscape = shouldBoostTabletLandscapeHero,
            ),
            contentMaxWidth = 640.dp,
            contentWidthFraction = 0.56f,
            contentHorizontalPadding = 56.dp,
            contentVerticalPadding = 22.dp,
            bottomFadeHeight = 190.dp,
            logoWidthFraction = 0.58f,
        )
        maxWidthDp >= 840f -> HomeHeroLayout(
            isTablet = true,
            heroHeight = tabletHeroHeight(
                baseHeight = (maxWidthDp * 0.46f).dp.coerceIn(340.dp, 420.dp),
                boostForLandscape = shouldBoostTabletLandscapeHero,
            ),
            contentMaxWidth = 560.dp,
            contentWidthFraction = 0.62f,
            contentHorizontalPadding = 40.dp,
            contentVerticalPadding = 20.dp,
            bottomFadeHeight = 180.dp,
            logoWidthFraction = 0.56f,
        )
        maxWidthDp >= 600f -> HomeHeroLayout(
            isTablet = true,
            heroHeight = tabletHeroHeight(
                baseHeight = (maxWidthDp * 0.58f).dp.coerceIn(320.dp, 380.dp),
                boostForLandscape = shouldBoostTabletLandscapeHero,
            ),
            contentMaxWidth = 520.dp,
            contentWidthFraction = 0.72f,
            contentHorizontalPadding = 32.dp,
            contentVerticalPadding = 18.dp,
            bottomFadeHeight = 170.dp,
            logoWidthFraction = 0.54f,
        )
        else -> HomeHeroLayout(
            isTablet = false,
            heroHeight = mobileHeroHeight(
                maxWidthDp = maxWidthDp,
                viewportHeightDp = viewportHeightDp,
                mobileBelowSectionHeightHintDp = mobileBelowSectionHeightHintDp,
            ),
            contentMaxWidth = 480.dp,
            contentWidthFraction = 1f,
            contentHorizontalPadding = 24.dp,
            contentVerticalPadding = 16.dp,
            bottomFadeHeight = 220.dp,
            logoWidthFraction = 0.62f,
        )
    }
}

private fun tabletHeroHeight(
    baseHeight: Dp,
    boostForLandscape: Boolean,
): Dp {
    if (!boostForLandscape) return baseHeight

    return (baseHeight.value * TABLET_LANDSCAPE_HERO_HEIGHT_MULTIPLIER).dp
}

private fun mobileHeroHeight(
    maxWidthDp: Float,
    viewportHeightDp: Float?,
    mobileBelowSectionHeightHintDp: Float?,
): Dp {
    val isPortraitViewport = viewportHeightDp?.let { it >= maxWidthDp } != false

    if (isPortraitViewport) {
        return (maxWidthDp * MOBILE_PORTRAIT_HERO_WIDTH_RATIO).dp
    }

    val viewportDrivenHeight = viewportHeightDp?.let { (it * MOBILE_HERO_VIEWPORT_RATIO).dp }
    val widthFallbackHeight = (maxWidthDp * 1.16f).dp
    val baseHeight = viewportDrivenHeight ?: widthFallbackHeight

    val maxAllowedFromViewportDp = if (viewportHeightDp != null && mobileBelowSectionHeightHintDp != null) {
        viewportHeightDp - mobileBelowSectionHeightHintDp
    } else {
        null
    }
    val cappedHeight = if (maxAllowedFromViewportDp != null) {
        val maxAllowedFromViewport = maxAllowedFromViewportDp.dp
        baseHeight.coerceAtMost(maxAllowedFromViewport)
    } else {
        baseHeight
    }
    val minHeight = if (maxAllowedFromViewportDp != null) {
        minOf(MOBILE_HERO_MIN_HEIGHT_DP, maxAllowedFromViewportDp.coerceAtLeast(0f)).dp
    } else {
        MOBILE_HERO_MIN_HEIGHT_DP.dp
    }

    return cappedHeight.coerceIn(minHeight, MOBILE_HERO_MAX_HEIGHT_DP.dp)
}

@Composable
private fun HeroMetaDot(color: Color = MaterialTheme.colorScheme.onBackground) {
    Box(
        modifier = Modifier
            .size(4.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.7f)),
    )
}

private fun heroBackgroundScrollScale(scrollOffsetPx: Float): Float {
    val scaleIncrease = if (scrollOffsetPx < 0f) {
        abs(scrollOffsetPx) * HERO_SCROLL_UP_SCALE_MULTIPLIER
    } else {
        scrollOffsetPx * HERO_SCROLL_DOWN_SCALE_MULTIPLIER
    }
    return (1f + scaleIncrease).coerceAtMost(HERO_SCROLL_MAX_SCALE)
}

private fun heroBackgroundScrollTranslationY(scrollOffsetPx: Float): Float {
    return scrollOffsetPx * HERO_SCROLL_PARALLAX
}

private fun Modifier.homeHeroPagerGesture(
    pagerState: PagerState,
    itemCount: Int,
    coroutineScope: CoroutineScope,
): Modifier {
    if (itemCount <= 1) return this

    return pointerInput(pagerState, itemCount) {
        awaitEachGesture {
            val down = awaitFirstDown(pass = PointerEventPass.Initial)
            val widthPx = size.width.toFloat().takeIf { it > 0f } ?: return@awaitEachGesture
            val velocityTracker = VelocityTracker().apply {
                addPosition(down.uptimeMillis, down.position)
            }
            val startPage = pagerState.currentPage
            var totalDx = 0f
            var totalDy = 0f
            var dragging = false

            while (true) {
                val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                velocityTracker.addPosition(change.uptimeMillis, change.position)

                if (!change.pressed) {
                    if (dragging) {
                        val targetPage = resolveHeroTargetPage(
                            startPage = startPage,
                            pageCount = pagerState.pageCount,
                            totalDx = totalDx,
                            velocityX = velocityTracker.calculateVelocity().x,
                            widthPx = widthPx,
                        )
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(targetPage)
                        }
                    }
                    break
                }

                val delta = change.position - change.previousPosition
                totalDx += delta.x
                totalDy += delta.y

                if (!dragging) {
                    val horizontalDrag =
                        abs(totalDx) > viewConfiguration.touchSlop && abs(totalDx) > abs(totalDy)
                    val verticalDrag =
                        abs(totalDy) > viewConfiguration.touchSlop && abs(totalDy) > abs(totalDx)

                    when {
                        verticalDrag -> break
                        horizontalDrag -> dragging = true
                        else -> continue
                    }
                }

                pagerState.dispatchRawDelta(-delta.x)
                change.consume()
            }
        }
    }
}

private fun resolveHeroTargetPage(
    startPage: Int,
    pageCount: Int,
    totalDx: Float,
    velocityX: Float,
    widthPx: Float,
): Int {
    val thresholdPassed = abs(totalDx) > widthPx * HERO_SWIPE_THRESHOLD_FRACTION ||
        abs(velocityX) > HERO_SWIPE_VELOCITY_THRESHOLD
    if (!thresholdPassed) return startPage

    val currentPage = startPage.coerceIn(0, pageCount - 1)
    return when {
        totalDx > 0f -> (currentPage - 1).coerceAtLeast(0)
        totalDx < 0f -> (currentPage + 1).coerceAtMost(pageCount - 1)
        else -> currentPage
    }
}
