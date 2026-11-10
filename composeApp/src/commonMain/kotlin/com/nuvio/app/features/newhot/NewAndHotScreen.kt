package com.nuvio.app.features.newhot

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.NuvioScreenHeader
import com.nuvio.app.core.ui.NuvioShelfSection
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.core.ui.nuvioSafeBottomPadding
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.components.HomePosterCard
import com.nuvio.app.features.home.components.HomeSkeletonRow
import com.nuvio.app.features.home.components.HomeTop10Row
import com.nuvio.app.features.home.stableKey

/**
 * New & Hot as carousels, the same shape as Home's Top 10 Today: one swipeable row per list. Everything here
 * is something you can watch at home. Coming Soon is films about to reach digital (streaming, rent or buy),
 * and every other row is limited to titles that are already available.
 */
@Composable
fun NewAndHotScreen(
    onPosterClick: (MetaPreview) -> Unit,
    onPosterLongClick: ((MetaPreview) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val tokens = MaterialTheme.nuvio
    LaunchedEffect(Unit) { NewHotRepository.ensureLoaded() }
    val state by NewHotRepository.state.collectAsStateWithLifecycle()
    val edge = 16.dp
    val empty = state.comingSoon.isEmpty() && state.everyoneWatching.isEmpty() && state.topMovies.isEmpty() && state.topShows.isEmpty()

    Column(modifier.fillMaxSize().background(tokens.colors.background).statusBarsPadding()) {
        NuvioScreenHeader(title = "New & Hot", includeStatusBarPadding = false, modifier = Modifier.padding(horizontal = NuvioTokens.Space.s8))
        if (empty) {
            // Loading shows the shape of the page rather than a spinner, so rows don't pop in.
            Column(Modifier.padding(horizontal = edge, vertical = 8.dp)) {
                repeat(3) { HomeSkeletonRow(modifier = Modifier.padding(bottom = 20.dp)) }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(top = 4.dp, bottom = nuvioSafeBottomPadding(24.dp)),
            ) {
                if (state.comingSoon.isNotEmpty()) item(key = "coming-soon") {
                    NuvioShelfSection(
                        title = "Coming to Digital",
                        entries = state.comingSoon,
                        modifier = Modifier.padding(bottom = 16.dp),
                        headerHorizontalPadding = edge,
                        rowContentPadding = PaddingValues(horizontal = edge),
                        key = { it.stableKey() },
                    ) { item -> ComingSoonCard(item, onPosterClick, onPosterLongClick) }
                }
                if (state.topMovies.size >= 3) item(key = "top-movies") {
                    HomeTop10Row(
                        items = state.topMovies,
                        sectionPadding = edge,
                        modifier = Modifier.padding(bottom = 16.dp),
                        title = "Top 10 Movies Today",
                        onPosterClick = onPosterClick,
                        onPosterLongClick = onPosterLongClick,
                    )
                }
                if (state.topShows.size >= 3) item(key = "top-shows") {
                    HomeTop10Row(
                        items = state.topShows,
                        sectionPadding = edge,
                        modifier = Modifier.padding(bottom = 16.dp),
                        title = "Top 10 Shows Today",
                        onPosterClick = onPosterClick,
                        onPosterLongClick = onPosterLongClick,
                    )
                }
                if (state.everyoneWatching.isNotEmpty()) item(key = "everyone-watching") {
                    NuvioShelfSection(
                        title = "Everyone's Watching",
                        entries = state.everyoneWatching,
                        modifier = Modifier.padding(bottom = 16.dp),
                        headerHorizontalPadding = edge,
                        rowContentPadding = PaddingValues(horizontal = edge),
                        key = { it.stableKey() },
                    ) { item ->
                        HomePosterCard(
                            item = item,
                            isWatched = false,
                            onClick = { onPosterClick(item) },
                            onLongClick = onPosterLongClick?.let { { it(item) } },
                        )
                    }
                }
            }
        }
    }
}

private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/** "2026-11-14" -> "Nov 14". */
private fun digitalLabel(iso: String?): String {
    val parts = iso?.take(10)?.split("-") ?: return "Soon"
    val month = parts.getOrNull(1)?.toIntOrNull()?.let { MONTHS.getOrNull(it - 1) } ?: return "Soon"
    return "$month ${parts.getOrNull(2)?.toIntOrNull() ?: ""}".trim()
}

/** A landscape card with the film's digital release date on it, so the row reads as a countdown. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ComingSoonCard(
    item: MetaPreview,
    onClick: (MetaPreview) -> Unit,
    onLongClick: ((MetaPreview) -> Unit)?,
) {
    val tokens = MaterialTheme.nuvio
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .width(270.dp)
            .aspectRatio(16f / 9f)
            .clip(shape)
            .background(tokens.colors.surface)
            .combinedClickable(onClick = { onClick(item) }, onLongClick = onLongClick?.let { { it(item) } }),
    ) {
        AsyncImage(
            model = item.banner ?: item.poster,
            contentDescription = item.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.82f)),
            ),
        )
        Text(
            text = "DIGITAL · ${digitalLabel(item.rawReleaseDate).uppercase()}",
            modifier = Modifier.align(Alignment.TopStart).padding(10.dp).clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.65f)).padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
        Text(
            text = item.name,
            modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
