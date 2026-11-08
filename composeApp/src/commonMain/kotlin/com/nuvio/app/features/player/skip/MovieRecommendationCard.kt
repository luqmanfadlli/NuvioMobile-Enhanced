package com.nuvio.app.features.player.skip

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.PlatformBackHandler
import com.nuvio.app.features.home.MetaPreview

@Composable
fun MovieRecommendationCard(
    recommendations: List<MetaPreview>,
    stage: Int,
    dismissed: Boolean,
    controlsVisible: Boolean,
    onOpen: (MetaPreview) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (recommendations.isEmpty()) return

    val ended = stage >= PlayerNextEpisodeRules.MOVIE_RECOMMENDATION_STAGE_ENDED
    val shouldShow = stage > 0 && (!dismissed || controlsVisible)
    var autoHidden by remember { mutableStateOf(false) }

    LaunchedEffect(stage) {
        autoHidden = false
    }

    LaunchedEffect(shouldShow, autoHidden, controlsVisible, ended) {
        if (shouldShow && !autoHidden && !controlsVisible && !ended) {
            delay(MOVIE_RECOMMENDATION_AUTO_HIDE_MS)
            autoHidden = true
        }
    }

    val visible = shouldShow && (ended || !autoHidden || controlsVisible)

    PlatformBackHandler(enabled = visible && !dismissed, onBack = onDismiss)

    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(animationSpec = tween(260), initialOffsetX = { it / 2 }) +
            fadeIn(animationSpec = tween(220)),
        exit = slideOutHorizontally(animationSpec = tween(200), targetOffsetX = { it / 2 }) +
            fadeOut(animationSpec = tween(160)),
        modifier = modifier,
    ) {
        // NuvioEx recommendation tray: a transparent, full-width bottom strip.
        // Artwork is deliberately rendered in a landscape frame; titles sit directly below.
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            contentPadding = PaddingValues(horizontal = 0.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(recommendations, key = { "${it.type}:${it.id}" }) { preview ->
                Column(
                    modifier = Modifier
                        .width(184.dp)
                        .clickable(enabled = visible) { onOpen(preview) },
                ) {
                    AsyncImage(
                        model = preview.banner ?: preview.poster,
                        contentDescription = preview.name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(104.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        contentScale = ContentScale.Crop,
                    )
                    Text(
                        text = preview.name,
                        color = Color.White,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

private const val MOVIE_RECOMMENDATION_AUTO_HIDE_MS = 10_000L
