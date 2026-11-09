package com.nuvio.app.core.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalDensity
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.size.Size
import com.nuvio.app.core.poster.CustomPosterFallbackInterceptor
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlin.math.min
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
internal val ShelfRowCacheWindow = LazyLayoutCacheWindow(aheadFraction = 0.5f, behindFraction = 0.25f)

private const val ShelfImagePrefetchAheadCount = 6

private const val ShelfImagePrefetchSkipCount = 2

internal fun posterImageModel(
    platformContext: PlatformContext,
    imageUrl: String,
    fallbackImageUrl: String?,
): Any =
    if (fallbackImageUrl != null) {
        ImageRequest.Builder(platformContext)
            .data(imageUrl)
            .memoryCacheKeyExtras(
                mapOf(CustomPosterFallbackInterceptor.FALLBACK_URL_KEY to fallbackImageUrl),
            )
            .build()
    } else {
        imageUrl
    }

internal data class PosterPrefetchTarget(
    val imageUrl: String,
    val fallbackImageUrl: String?,
    val shape: NuvioPosterShape,
)

@Composable
internal fun PosterRowImagePrefetchEffect(
    state: LazyListState,
    targets: List<PosterPrefetchTarget?>,
) {
    if (targets.isEmpty()) return
    val platformContext = LocalPlatformContext.current
    val density = LocalDensity.current
    val basePosterWidthDp = rememberPosterCardStyleUiState().widthDp

    LaunchedEffect(state, platformContext, density, basePosterWidthDp, targets) {
        val imageLoader = SingletonImageLoader.get(platformContext)
        val enqueued = HashSet<Int>()
        snapshotFlow { state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .filter { it >= 0 }
            .distinctUntilChanged()
            .collect { lastVisible ->
                val items = targets
                val from = lastVisible + 1 + ShelfImagePrefetchSkipCount
                val to = min(items.lastIndex, lastVisible + ShelfImagePrefetchSkipCount + ShelfImagePrefetchAheadCount)
                for (index in from..to) {
                    if (!enqueued.add(index)) continue
                    val target = items[index] ?: continue
                    val widthPx = with(density) { target.shape.cardWidth(basePosterWidthDp).toPx() }
                    val heightPx = widthPx / target.shape.aspectRatio
                    if (widthPx <= 0f || heightPx <= 0f) continue
                    val model = posterImageModel(
                        platformContext = platformContext,
                        imageUrl = target.imageUrl,
                        fallbackImageUrl = target.fallbackImageUrl,
                    )
                    val builder = when (model) {
                        is ImageRequest -> model.newBuilder()
                        else -> ImageRequest.Builder(platformContext).data(model)
                    }
                    imageLoader.enqueue(
                        builder
                            .size(Size(widthPx.roundToInt(), heightPx.roundToInt()))
                            .build(),
                    )
                }
            }
    }
}
