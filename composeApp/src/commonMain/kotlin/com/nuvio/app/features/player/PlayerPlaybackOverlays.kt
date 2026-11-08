package com.nuvio.app.features.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nuvio.app.features.p2p.P2pLoadingStatus
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.player.skip.MovieRecommendationCard
import com.nuvio.app.features.player.skip.NextEpisodeCard
import com.nuvio.app.features.player.skip.NextEpisodeInfo
import com.nuvio.app.features.player.skip.SkipIntroButton
import com.nuvio.app.features.player.skip.SkipInterval
import com.nuvio.app.core.ui.PlatformBackHandler

@Composable
internal fun BoxScope.PlayerPlaybackOverlays(
    playerControlsLocked: Boolean,
    useLegacyLayout: Boolean,
    lockedOverlayVisible: Boolean,
    showRemainingTime: Boolean = false,
    playbackSnapshot: PlayerPlaybackSnapshot,
    displayedPositionMs: Long,
    metrics: PlayerLayoutMetrics,
    horizontalSafePadding: Dp,
    onUnlock: () -> Unit,
    showOpeningOverlay: Boolean,
    backdropArtwork: String?,
    logo: String?,
    title: String,
    onBackWithProgress: () -> Unit,
    openingLoadingMessage: String?,
    openingNetworkSpeed: String? = null,
    p2pInitialLoadingProgress: Float?,
    showP2pRebufferStats: Boolean,
    p2pRebufferMessage: String?,
    p2pRebufferProgress: Float?,
    currentGestureFeedback: GestureFeedbackState?,
    renderedGestureFeedback: GestureFeedbackState?,
    initialLoadCompleted: Boolean,
    pausedOverlayVisible: Boolean,
    activeSkipInterval: SkipInterval?,
    skipsToPostCredits: Boolean,
    skipIntervalDismissed: Boolean,
    controlsVisible: Boolean,
    onSkipInterval: (SkipInterval) -> Unit,
    onDismissSkipInterval: () -> Unit,
    sliderEdgePadding: Dp,
    overlayBottomPadding: Dp,
    isSeries: Boolean,
    nextEpisodeInfo: NextEpisodeInfo?,
    showNextEpisodeCard: Boolean,
    nextEpisodeAutoPlaySearching: Boolean,
    nextEpisodeAutoPlaySourceName: String?,
    nextEpisodeAutoPlayCountdown: Int?,
    blurUnwatchedEpisodes: Boolean,
    onPlayNextEpisode: () -> Unit,
    onDismissNextEpisode: () -> Unit,
    movieRecommendations: List<MetaPreview> = emptyList(),
    showMovieRecommendationCard: Boolean = false,
    movieRecommendationStage: Int = 0,
    onOpenMovieRecommendation: (MetaPreview) -> Unit = {},
    onDismissMovieRecommendations: () -> Unit = {},
    moreLikeThisItems: List<MetaPreview> = emptyList(),
    showMoreLikeThis: Boolean = false,
    onOpenMoreLikeThis: (MetaPreview) -> Unit = {},
    onDismissMoreLikeThis: () -> Unit = {},
    errorMessage: String?,
    onDismissError: () -> Unit,
) {
    AnimatedVisibility(
        visible = playerControlsLocked && lockedOverlayVisible,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        LockedPlayerOverlay(
            playbackSnapshot = playbackSnapshot,
            displayedPositionMs = displayedPositionMs,
            metrics = metrics,
            horizontalSafePadding = horizontalSafePadding,
            onUnlock = onUnlock,
            useLegacyLayout = useLegacyLayout,
            showRemainingTime = showRemainingTime,
            modifier = Modifier.fillMaxSize(),
        )
    }

    AnimatedVisibility(
        visible = showOpeningOverlay,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        OpeningOverlay(
            artwork = backdropArtwork,
            logo = logo,
            title = title,
            onBack = onBackWithProgress,
            horizontalSafePadding = horizontalSafePadding,
            modifier = Modifier.fillMaxSize(),
            message = openingLoadingMessage,
            networkSpeed = openingNetworkSpeed,
            progress = p2pInitialLoadingProgress,
        )
    }

    P2pLoadingStatus(
        visible = showP2pRebufferStats && errorMessage == null,
        message = p2pRebufferMessage,
        progress = p2pRebufferProgress,
        modifier = Modifier
            .align(Alignment.Center)
            .padding(top = 58.dp),
    )

    PlayerGestureOverlay(
        currentFeedback = currentGestureFeedback,
        renderedFeedback = renderedGestureFeedback,
        useLegacyLayout = useLegacyLayout,
        horizontalSafePadding = horizontalSafePadding,
        horizontalPadding = metrics.horizontalPadding,
    )

    if (!playerControlsLocked) {
        SkipIntroButton(
            interval = if (!initialLoadCompleted || pausedOverlayVisible) null else activeSkipInterval,
            skipsToPostCredits = skipsToPostCredits,
            dismissed = skipIntervalDismissed,
            controlsVisible = controlsVisible,
            onSkip = {
                activeSkipInterval?.let(onSkipInterval)
            },
            onDismiss = onDismissSkipInterval,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = sliderEdgePadding, bottom = overlayBottomPadding),
        )
    }

    if (isSeries && !playerControlsLocked) {
        NextEpisodeCard(
            nextEpisode = nextEpisodeInfo,
            visible = showNextEpisodeCard || nextEpisodeAutoPlaySearching || nextEpisodeAutoPlayCountdown != null,
            isAutoPlaySearching = nextEpisodeAutoPlaySearching,
            autoPlaySourceName = nextEpisodeAutoPlaySourceName,
            autoPlayCountdownSec = nextEpisodeAutoPlayCountdown,
            blurred = blurUnwatchedEpisodes && nextEpisodeInfo?.isWatched == false,
            onPlayNext = onPlayNextEpisode,
            onDismiss = onDismissNextEpisode,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = sliderEdgePadding, bottom = overlayBottomPadding),
        )
    }

    if (!isSeries && !playerControlsLocked) {
        MovieRecommendationCard(
            recommendations = movieRecommendations,
            stage = movieRecommendationStage,
            dismissed = !showMovieRecommendationCard,
            controlsVisible = controlsVisible,
            onOpen = onOpenMovieRecommendation,
            onDismiss = onDismissMovieRecommendations,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = overlayBottomPadding),
        )
    }

    if (showMoreLikeThis && moreLikeThisItems.isNotEmpty()) {
        MoreLikeThisOverlay(
            items = moreLikeThisItems,
            onItemClick = onOpenMoreLikeThis,
            onDismiss = onDismissMoreLikeThis,
        )
    }

    if (errorMessage != null) {
        ErrorModal(
            message = errorMessage,
            onDismiss = onDismissError,
        )
    }
}


internal fun shouldShowMoreLikeThisOverlay(
    isPlaybackEnded: Boolean,
    hasRecommendations: Boolean,
    isSeries: Boolean,
    hasNextEpisode: Boolean,
    controlsLocked: Boolean,
    dismissed: Boolean,
    hasPlaybackError: Boolean,
    isInPip: Boolean,
    completionPromptVisible: Boolean,
): Boolean =
    isPlaybackEnded &&
        hasRecommendations &&
        !controlsLocked &&
        !dismissed &&
        !hasPlaybackError &&
        !isInPip &&
        !completionPromptVisible &&
        (!isSeries || !hasNextEpisode)

@Composable
private fun BoxScope.MoreLikeThisOverlay(
    items: List<MetaPreview>,
    onItemClick: (MetaPreview) -> Unit,
    onDismiss: () -> Unit,
) {
    PlatformBackHandler(
        enabled = items.isNotEmpty(),
        onBack = onDismiss,
    )
    Column(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items.forEach { item ->
                Column(
                    modifier = Modifier
                        .width(184.dp)
                        .clickable { onItemClick(item) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AsyncImage(
                        model = item.banner ?: item.landscapePoster ?: item.poster,
                        contentDescription = item.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(104.dp)
                            .clip(RoundedCornerShape(4.dp)),
                    )
                    Text(
                        text = item.name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.94f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
