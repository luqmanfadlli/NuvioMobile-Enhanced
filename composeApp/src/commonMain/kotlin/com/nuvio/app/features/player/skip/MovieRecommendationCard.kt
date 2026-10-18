package com.nuvio.app.features.player.skip

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
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
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_close
import nuvio.composeapp.generated.resources.details_more_like_this
import org.jetbrains.compose.resources.stringResource
import com.nuvio.app.core.ui.glass.ambientLiquidGlass

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
        val shape = RoundedCornerShape(16.dp)
        Column(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(shape)
                .ambientLiquidGlass(
                    shape = shape,
                    tint = Color(0xFF191919).copy(alpha = 0.64f),
                    fallback = Color(0xFF191919).copy(alpha = 0.89f),
                    blurRadius = 20.dp,
                    refractionHeight = 10.dp,
                    refractionAmount = 14.dp,
                )
                .border(1.dp, Color.White.copy(alpha = 0.12f), shape)
                .padding(vertical = 8.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 11.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.details_more_like_this),
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .clickable(enabled = visible, onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(Res.string.action_close),
                        tint = Color.White.copy(alpha = 0.75f),
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 9.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(recommendations, key = { "${it.type}:${it.id}" }) { preview ->
                    Column(
                        modifier = Modifier
                            .width(72.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .clickable(enabled = visible) { onOpen(preview) },
                    ) {
                        AsyncImage(
                            model = preview.poster ?: preview.banner,
                            contentDescription = preview.name,
                            modifier = Modifier
                                .size(width = 72.dp, height = 108.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(Color.White.copy(alpha = 0.08f)),
                            contentScale = ContentScale.Crop,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = preview.name,
                            color = Color.White,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}

private const val MOVIE_RECOMMENDATION_AUTO_HIDE_MS = 10_000L
