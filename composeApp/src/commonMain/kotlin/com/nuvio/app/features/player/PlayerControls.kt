package com.nuvio.app.features.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ListAlt
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Forward30
import androidx.compose.material.icons.rounded.Forward5
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.CastConnected
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Replay30
import androidx.compose.material.icons.rounded.Replay5
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.VideoLibrary
import com.nuvio.app.core.ui.NuvioLoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.LayoutDirection
import com.nuvio.app.core.ui.AppIconResource
import com.nuvio.app.core.ui.NuvioBackButton
import com.nuvio.app.core.ui.themePalette
import com.nuvio.app.core.ui.accentBrush
import com.nuvio.app.core.ui.appIconPainter
import com.nuvio.app.core.ui.gradientMask
import com.nuvio.app.core.ui.nuvioTypeScale
import com.nuvio.app.features.player.seekpreview.SeekPreviewController
import com.nuvio.app.features.player.seekpreview.SeekPreviewOverlay
import com.nuvio.app.features.streams.StreamBadge
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs
import kotlin.math.roundToLong
import com.nuvio.app.core.i18n.localizedEpisodeTitle
import com.nuvio.app.core.ui.glass.isAmbientLiquidGlassActive

@Composable
internal fun PlayerControlsShell(
    title: String,
    streamTitle: String,
    providerName: String,
    seasonNumber: Int?,
    episodeNumber: Int?,
    episodeTitle: String?,
    playbackSnapshot: PlayerPlaybackSnapshot,
    displayedPositionMs: Long,
    metrics: PlayerLayoutMetrics,
    resizeMode: PlayerResizeMode,
    isLocked: Boolean,
    useLegacyLayout: Boolean = false,
    showRemainingTime: Boolean = false,
    onRuntimeClick: () -> Unit = {},
    releaseInfo: String? = null,
    badges: List<StreamBadge> = emptyList(),
    hideDetails: Boolean = false,
    onNextEpisodeClick: (() -> Unit)? = null,
    onInteraction: () -> Unit = {},
    showPlaybackControls: Boolean = true,
    onLockToggle: () -> Unit,
    onBack: () -> Unit,
    onPartyClick: (() -> Unit)? = null,
    partyActive: Boolean = false,
    onTogglePlayback: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    skipBackwardSeconds: Int = PLAYER_SKIP_SECONDS_DEFAULT,
    skipForwardSeconds: Int = PLAYER_SKIP_SECONDS_DEFAULT,
    onResizeModeClick: () -> Unit,
    onSpeedClick: (() -> Unit)? = null,
    speedPickerVisible: Boolean = false,
    onSpeedSelected: (Float) -> Unit = {},
    onSpeedPickerDismiss: () -> Unit = {},
    onSubtitleClick: (() -> Unit)? = null,
    onAudioClick: (() -> Unit)? = null,
    onVideoSettingsClick: (() -> Unit)? = null,
    onCastClick: (() -> Unit)? = null,
    isCasting: Boolean = false,
    onPictureInPictureClick: (() -> Unit)? = null,
    onOrientationToggleClick: (() -> Unit)? = null,
    isPortraitPlayback: Boolean = false,
    onInfoClick: (() -> Unit)? = null,
    onRateClick: (() -> Unit)? = null,
    userRating: Int? = null,
    onSourcesClick: (() -> Unit)? = null,
    onEpisodesClick: (() -> Unit)? = null,
    onChaptersClick: (() -> Unit)? = null,
    onLiveChannelsClick: (() -> Unit)? = null,
    qualityLabel: String? = null,
    onQualityClick: (() -> Unit)? = null,
    onOpenInExternalPlayer: (() -> Unit)? = null,
    onSubmitIntroClick: (() -> Unit)? = null,
    onSwitchEngineClick: (() -> Unit)? = null,
    onStreamInfoClick: () -> Unit = {},
    parentalWarnings: List<ParentalWarning> = emptyList(),
    showParentalGuide: Boolean = false,
    onParentalGuideAnimationComplete: () -> Unit = {},
    onScrubChange: (Long) -> Unit,
    onScrubFinished: (Long) -> Unit,
    horizontalSafePadding: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    seekPreview: SeekPreviewController? = null,
    onTimelineTouchChange: (Boolean) -> Unit = {},
) {
    val density = LocalDensity.current
    var timelineHeight by remember { mutableStateOf(0.dp) }
    val glassActive = isAmbientLiquidGlassActive()
    var sourceLineHeight by remember { mutableStateOf(0.dp) }
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val centerControlHeight = metrics.playIconSize + maxOf(metrics.playButtonPadding, metrics.sideButtonPadding) * 2
        val centerBottomPadding = if (useLegacyLayout) metrics.centerLift else maxOf(
            metrics.centerLift,
            (timelineHeight - sourceLineHeight) * 2 + centerControlHeight + 16.dp - maxHeight,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = if (glassActive) 0.35f else 0.7f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (useLegacyLayout) 220.dp else 260.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(
                                alpha = when {
                                    useLegacyLayout && glassActive -> 0.35f
                                    useLegacyLayout -> 0.7f
                                    glassActive -> 0.55f
                                    else -> 0.8f
                                },
                            ),
                        ),
                    ),
                ),
        )

        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            if (useLegacyLayout) {
                PlayerHeader(
                    title = title,
                    streamTitle = streamTitle,
                    providerName = providerName,
                    seasonNumber = seasonNumber,
                    episodeNumber = episodeNumber,
                    episodeTitle = episodeTitle,
                    metrics = metrics,
                    isLocked = isLocked,
                    showActions = showPlaybackControls,
                    onSubmitIntroClick = onSubmitIntroClick,
                    parentalWarnings = parentalWarnings,
                    showParentalGuide = showParentalGuide,
                    onParentalGuideAnimationComplete = onParentalGuideAnimationComplete,
                    onLockToggle = onLockToggle,
                    onVideoSettingsClick = onVideoSettingsClick,
                    onCastClick = onCastClick,
                    isCasting = isCasting,
                    onPictureInPictureClick = onPictureInPictureClick,
                    onOrientationToggleClick = onOrientationToggleClick,
                    isPortraitPlayback = isPortraitPlayback,
                    onInfoClick = onInfoClick,
                    onRateClick = onRateClick,
                    userRating = userRating,
                    onOpenInExternalPlayer = onOpenInExternalPlayer,
                    onPartyClick = onPartyClick,
                    partyActive = partyActive,
                    onSwitchEngineClick = onSwitchEngineClick,
                    onBack = onBack,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .windowInsetsPadding(playerToolbarTopInsets(metrics))
                        .padding(horizontal = metrics.horizontalPadding),
                )
            } else {
                if (showPlaybackControls) {
                    PlayerToolbar(
                        isLocked = isLocked,
                        onLockToggle = onLockToggle,
                        onBack = onBack,
                        onCastClick = onCastClick,
                        isCasting = isCasting,
                        onPartyClick = onPartyClick,
                        partyActive = partyActive,
                        onOrientationToggleClick = onOrientationToggleClick,
                        isPortraitPlayback = isPortraitPlayback,
                        onPictureInPictureClick = onPictureInPictureClick,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .windowInsetsPadding(playerToolbarTopInsets(metrics))
                            .padding(horizontal = metrics.horizontalPadding),
                    )
                }
                ParentalGuideOverlay(
                    warnings = parentalWarnings,
                    isVisible = showParentalGuide,
                    onAnimationComplete = onParentalGuideAnimationComplete,
                    contentPadding = PaddingValues(horizontal = metrics.horizontalPadding, vertical = metrics.verticalPadding),
                    modifier = Modifier.align(Alignment.TopStart),
                )
            }

            if (showPlaybackControls) {
                CompositionLocalProvider(
                    LocalLayoutDirection provides if (useLegacyLayout) LocalLayoutDirection.current else LayoutDirection.Ltr,
                ) {
                    CenterControls(
                        snapshot = playbackSnapshot,
                        metrics = metrics,
                        skipBackwardSeconds = skipBackwardSeconds,
                        skipForwardSeconds = skipForwardSeconds,
                        onSeekBack = {
                            if (!useLegacyLayout) onInteraction()
                            onSeekBack()
                        },
                        onSeekForward = {
                            if (!useLegacyLayout) onInteraction()
                            onSeekForward()
                        },
                        onTogglePlayback = {
                            if (!useLegacyLayout) onInteraction()
                            onTogglePlayback()
                        },
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(bottom = centerBottomPadding),
                    )
                }
            }

            if (showPlaybackControls && useLegacyLayout) {
                ProgressControls(
                    playbackSnapshot = playbackSnapshot,
                    displayedPositionMs = displayedPositionMs,
                    metrics = metrics,
                    showRemainingTime = showRemainingTime,
                    onRuntimeClick = onRuntimeClick,
                    resizeMode = resizeMode,
                    onScrubChange = onScrubChange,
                    onScrubFinished = onScrubFinished,
                    onResizeModeClick = onResizeModeClick,
                    onSpeedClick = onSpeedClick,
                    speedPickerVisible = speedPickerVisible,
                    onSpeedSelected = onSpeedSelected,
                    onSpeedPickerDismiss = onSpeedPickerDismiss,
                    onSubtitleClick = onSubtitleClick,
                    onAudioClick = onAudioClick,
                    onSourcesClick = onSourcesClick,
                    onEpisodesClick = onEpisodesClick,
                    onChaptersClick = onChaptersClick,
                    onLiveChannelsClick = onLiveChannelsClick,
                    qualityLabel = qualityLabel,
                    onQualityClick = onQualityClick,
                    seekPreview = seekPreview,
                    onTimelineTouchChange = onTimelineTouchChange,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .windowInsetsPadding(playerTimelineBottomInsets(metrics))
                        .padding(horizontal = metrics.horizontalPadding),
                )
            }
            if (showPlaybackControls && !useLegacyLayout) {
                Column(
                    modifier = Modifier
                        .onSizeChanged { size -> timelineHeight = with(density) { size.height.toDp() } }
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .windowInsetsPadding(playerTimelineBottomInsets(metrics))
                        .padding(horizontal = metrics.horizontalPadding),
                ) {
                    if (!hideDetails) {
                        PlayerTimelineDetails(
                            title = title,
                            seasonNumber = seasonNumber,
                            episodeNumber = episodeNumber,
                            episodeTitle = episodeTitle,
                            releaseInfo = releaseInfo,
                            streamTitle = streamTitle,
                            providerName = providerName,
                            isPlaying = playbackSnapshot.isPlaying,
                            metrics = metrics,
                            badges = badges,
                            onSourceLineHeightChanged = { height ->
                                sourceLineHeight = with(density) { height.toDp() }
                            },
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            PlayerChapterLabel(
                                chapters = playbackSnapshot.chapters,
                                positionMs = displayedPositionMs,
                                metrics = metrics,
                                onClick = onChaptersClick?.let { openChapters ->
                                    {
                                        onInteraction()
                                        openChapters()
                                    }
                                },
                            )
                        }
                        PlayerEndTimeLabel(
                            positionMs = displayedPositionMs,
                            durationMs = playbackSnapshot.durationMs,
                            playbackSpeed = playbackSnapshot.playbackSpeed,
                            isPlaying = playbackSnapshot.isPlaying,
                            metrics = metrics,
                        )
                    }
                    PlayerTimeline(
                        snapshot = playbackSnapshot,
                        displayedPositionMs = displayedPositionMs,
                        onScrubChange = onScrubChange,
                        onScrubFinished = {
                            onInteraction()
                            onScrubFinished(it)
                        },
                        seekPreview = seekPreview,
                        onTouchChange = onTimelineTouchChange,
                    )
                    PlayerControlActions(
                        playbackSnapshot = playbackSnapshot,
                        displayedPositionMs = displayedPositionMs,
                        showRemainingTime = showRemainingTime,
                        onRuntimeClick = onRuntimeClick,
                        metrics = metrics,
                        resizeMode = resizeMode,
                        onSubtitleClick = { onSubtitleClick?.invoke() },
                        onAudioClick = { onAudioClick?.invoke() },
                        onSourcesClick = onSourcesClick,
                        onEpisodesClick = onEpisodesClick,
                        onChaptersClick = onChaptersClick,
                        onNextEpisodeClick = onNextEpisodeClick,
                        onSpeedClick = { onSpeedClick?.invoke() },
                        speedPickerVisible = speedPickerVisible,
                        onSpeedSelected = onSpeedSelected,
                        onSpeedPickerDismiss = onSpeedPickerDismiss,
                        onResizeModeClick = onResizeModeClick,
                        onVideoSettingsClick = onVideoSettingsClick,
                        onOpenInExternalPlayer = onOpenInExternalPlayer,
                        onStreamInfoClick = onStreamInfoClick,
                        onSubmitIntroClick = onSubmitIntroClick,
                        qualityLabel = qualityLabel,
                        onQualityClick = onQualityClick,
                        onSwitchEngineClick = onSwitchEngineClick,
                        onInfoClick = onInfoClick,
                        onRateClick = onRateClick,
                        userRating = userRating,
                        onInteraction = onInteraction,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerHeader(
    title: String,
    streamTitle: String,
    providerName: String,
    seasonNumber: Int?,
    episodeNumber: Int?,
    episodeTitle: String?,
    metrics: PlayerLayoutMetrics,
    isLocked: Boolean,
    showActions: Boolean,
    onSubmitIntroClick: (() -> Unit)?,
    parentalWarnings: List<ParentalWarning>,
    showParentalGuide: Boolean,
    onParentalGuideAnimationComplete: () -> Unit,
    onLockToggle: () -> Unit,
    onVideoSettingsClick: (() -> Unit)?,
    onCastClick: (() -> Unit)?,
    isCasting: Boolean,
    onPictureInPictureClick: (() -> Unit)?,
    onOrientationToggleClick: (() -> Unit)? = null,
    isPortraitPlayback: Boolean = false,
    onInfoClick: (() -> Unit)?,
    onRateClick: (() -> Unit)? = null,
    userRating: Int? = null,
    onOpenInExternalPlayer: (() -> Unit)?,
    onPartyClick: (() -> Unit)?,
    partyActive: Boolean,
    onSwitchEngineClick: (() -> Unit)? = null,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val typeScale = MaterialTheme.nuvioTypeScale
    val metadataAlpha by animateFloatAsState(
        targetValue = if (!showParentalGuide && showActions) 1f else 0f,
        animationSpec = tween(durationMillis = if (!showParentalGuide && showActions) 260 else 160),
        label = "playerHeaderMetadataAlpha",
    )
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier.weight(1f),
            ) {
                Column(
                    modifier = Modifier.graphicsLayer { alpha = metadataAlpha },
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = title,
                        style = typeScale.titleLg.copy(
                            fontSize = metrics.titleSize,
                            lineHeight = metrics.titleSize * 1.16f,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (seasonNumber != null && episodeNumber != null && !episodeTitle.isNullOrBlank()) {
                        val displayEpisodeTitle = localizedEpisodeTitle(episodeTitle, episodeNumber) ?: episodeTitle
                        Text(
                            text = stringResource(
                                Res.string.compose_player_episode_title_format,
                                seasonNumber,
                                episodeNumber,
                                displayEpisodeTitle,
                            ),
                            style = typeScale.bodyMd.copy(
                                fontSize = metrics.episodeInfoSize,
                                lineHeight = metrics.episodeInfoSize * 1.3f,
                            ),
                            color = Color.White.copy(alpha = 0.9f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = streamTitle,
                            style = typeScale.labelSm.copy(
                                fontSize = metrics.metadataSize,
                                lineHeight = metrics.metadataSize * 1.25f,
                            ),
                            color = Color.White.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = providerName,
                            style = typeScale.labelSm.copy(
                                fontSize = metrics.metadataSize,
                                lineHeight = metrics.metadataSize * 1.25f,
                                fontStyle = FontStyle.Italic,
                            ),
                            color = Color.White.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                ParentalGuideOverlay(
                    warnings = parentalWarnings,
                    isVisible = showParentalGuide,
                    onAnimationComplete = onParentalGuideAnimationComplete,
                    contentPadding = PaddingValues(0.dp),
                )
            }

            if (showActions) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (onSubmitIntroClick != null) {
                        PlayerHeaderIconButton(
                            icon = Icons.Rounded.Flag,
                            contentDescription = stringResource(Res.string.submit_intro_action),
                            buttonSize = metrics.headerIconSize + 16.dp,
                            iconSize = metrics.headerIconSize,
                            onClick = onSubmitIntroClick,
                        )
                    }
                    if (onOpenInExternalPlayer != null) {
                        PlayerHeaderIconButton(
                            icon = Icons.AutoMirrored.Rounded.OpenInNew,
                            contentDescription = stringResource(Res.string.streams_open_external_player),
                            buttonSize = metrics.headerIconSize + 16.dp,
                            iconSize = metrics.headerIconSize,
                            onClick = onOpenInExternalPlayer,
                        )
                    }
                    if (onSwitchEngineClick != null) {
                        PlayerHeaderIconButton(
                            icon = Icons.Rounded.SwapHoriz,
                            contentDescription = stringResource(Res.string.cd_switch_player_engine),
                            buttonSize = metrics.headerIconSize + 16.dp,
                            iconSize = metrics.headerIconSize,
                            onClick = onSwitchEngineClick,
                        )
                    }
                    if (onPartyClick != null) {
                        PlayerHeaderIconButton(
                            icon = Icons.Rounded.Groups,
                            contentDescription = stringResource(Res.string.party_title),
                            buttonSize = metrics.headerIconSize + 16.dp,
                            iconSize = metrics.headerIconSize,
                            onClick = onPartyClick,
                            tint = if (partyActive) MaterialTheme.colorScheme.primary else Color.White,
                        )
                    }
                    if (onCastClick != null) {
                        PlayerHeaderIconButton(
                            icon = if (isCasting) Icons.Rounded.CastConnected else Icons.Rounded.Cast,
                            contentDescription = stringResource(Res.string.player_cast),
                            buttonSize = metrics.headerIconSize + 16.dp,
                            iconSize = metrics.headerIconSize,
                            onClick = onCastClick,
                        )
                    }
                    if (onPictureInPictureClick != null) {
                        PlayerHeaderIconButton(
                            icon = Icons.Rounded.PictureInPictureAlt,
                            contentDescription = stringResource(Res.string.compose_player_picture_in_picture),
                            buttonSize = metrics.headerIconSize + 16.dp,
                            iconSize = metrics.headerIconSize,
                            onClick = onPictureInPictureClick,
                        )
                    }
                    if (onOrientationToggleClick != null) {
                        PlayerHeaderIconButton(
                            icon = playerOrientationToggleIcon(isPortraitPlayback),
                            contentDescription = stringResource(playerOrientationToggleLabel(isPortraitPlayback)),
                            buttonSize = metrics.headerIconSize + 16.dp,
                            iconSize = metrics.headerIconSize,
                            onClick = onOrientationToggleClick,
                        )
                    }
                    if (onRateClick != null) {
                        PlayerHeaderIconButton(
                            icon = if (userRating != null) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                            contentDescription = userRating?.let {
                                stringResource(Res.string.user_rating_action_rated, it)
                            } ?: stringResource(Res.string.user_rating_action_rate),
                            buttonSize = metrics.headerIconSize + 16.dp,
                            iconSize = metrics.headerIconSize,
                            onClick = onRateClick,
                        )
                    }
                    if (onInfoClick != null) {
                        PlayerHeaderIconButton(
                            icon = Icons.Rounded.Info,
                            contentDescription = stringResource(Res.string.compose_player_playback_info),
                            buttonSize = metrics.headerIconSize + 16.dp,
                            iconSize = metrics.headerIconSize,
                            onClick = onInfoClick,
                        )
                    }
                    if (onVideoSettingsClick != null) {
                        PlayerHeaderIconButton(
                            icon = Icons.Rounded.Build,
                            contentDescription = stringResource(Res.string.player_action_video_settings),
                            buttonSize = metrics.headerIconSize + 16.dp,
                            iconSize = metrics.headerIconSize,
                            onClick = onVideoSettingsClick,
                        )
                    }
                    PlayerHeaderIconButton(
                        icon = if (isLocked) Icons.Rounded.LockOpen else Icons.Rounded.Lock,
                        contentDescription = if (isLocked) {
                            stringResource(Res.string.compose_player_unlock_controls)
                        } else {
                            stringResource(Res.string.compose_player_lock_controls)
                        },
                        buttonSize = metrics.headerIconSize + 16.dp,
                        iconSize = metrics.headerIconSize,
                        onClick = onLockToggle,
                    )
                    NuvioBackButton(
                        onClick = onBack,
                        modifier = Modifier.playerLiquidGlass(CircleShape, refractionHeight = 6.dp, refractionAmount = 8.dp),
                        shape = CircleShape,
                        containerColor = Color.Transparent,
                        contentColor = Color.White,
                        buttonSize = metrics.headerIconSize + 16.dp,
                        iconSize = metrics.headerIconSize,
                        contentDescription = stringResource(Res.string.compose_player_close),
                    )
                }
            }
        }
    }
}


@Composable
internal fun PlayerHeaderIconButton(
    icon: ImageVector,
    contentDescription: String,
    buttonSize: androidx.compose.ui.unit.Dp,
    iconSize: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    tint: Color = Color.White,
) {
    Box(
        modifier = Modifier
            .size(buttonSize)
            .playerLiquidGlass(
                shape = CircleShape,
                refractionHeight = 6.dp,
                refractionAmount = 8.dp,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
private fun CenterControls(
    snapshot: PlayerPlaybackSnapshot,
    metrics: PlayerLayoutMetrics,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onTogglePlayback: () -> Unit,
    modifier: Modifier = Modifier,
    skipBackwardSeconds: Int = PLAYER_SKIP_SECONDS_DEFAULT,
    skipForwardSeconds: Int = PLAYER_SKIP_SECONDS_DEFAULT,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(metrics.centerGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SideControlButton(
            contentDescription = stringResource(Res.string.compose_player_seek_back_seconds, skipBackwardSeconds),
            metrics = metrics,
            onClick = onSeekBack,
        ) { SkipStepIcon(seconds = skipBackwardSeconds, forward = false, size = metrics.playIconSize) }
        PlayPauseControlButton(
            isPlaying = snapshot.isPlaying,
            isBuffering = snapshot.isLoading,
            metrics = metrics,
            onClick = onTogglePlayback,
        )
        SideControlButton(
            contentDescription = stringResource(Res.string.compose_player_seek_forward_seconds, skipForwardSeconds),
            metrics = metrics,
            onClick = onSeekForward,
        ) { SkipStepIcon(seconds = skipForwardSeconds, forward = true, size = metrics.playIconSize) }
    }
}

@Composable
private fun SideControlButton(
    contentDescription: String,
    metrics: PlayerLayoutMetrics,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .playerLiquidGlass(
                shape = CircleShape,
                refractionHeight = 8.dp,
                refractionAmount = 12.dp,
            )
            .clickable(onClick = onClick)
            .padding(metrics.sideButtonPadding)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        icon()
    }
}

@Composable
private fun SkipStepIcon(seconds: Int, forward: Boolean, size: Dp) {
    val numbered = when (seconds) {
        5 -> if (forward) Icons.Rounded.Forward5 else Icons.Rounded.Replay5
        10 -> if (forward) Icons.Rounded.Forward10 else Icons.Rounded.Replay10
        30 -> if (forward) Icons.Rounded.Forward30 else Icons.Rounded.Replay30
        else -> null
    }
    if (numbered != null) {
        Icon(numbered, contentDescription = null, tint = Color.White, modifier = Modifier.size(size))
        return
    }
    Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
        Icon(
            imageVector = Icons.Rounded.Replay,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { if (forward) scaleX = -1f },
        )
        val density = LocalDensity.current
        Text(
            text = seconds.toString(),
            color = Color.White,
            style = MaterialTheme.nuvioTypeScale.labelSm.copy(
                fontSize = with(density) { (size * 0.26f).toSp() },
                lineHeight = with(density) { (size * 0.26f).toSp() },
                fontWeight = FontWeight.Bold,
            ),
            modifier = Modifier.offset(y = size * 0.1f),
        )
    }
}

@Composable
internal fun PlayPauseControlButton(
    isPlaying: Boolean,
    isBuffering: Boolean,
    metrics: PlayerLayoutMetrics,
    onClick: () -> Unit,
) {
    val playPausePainter = appIconPainter(
        if (isPlaying) AppIconResource.PlayerPause else AppIconResource.PlayerPlay,
    )

    Box(
        modifier = Modifier
            .playerLiquidGlass(
                shape = CircleShape,
                refractionHeight = 10.dp,
                refractionAmount = 14.dp,
            )
            .clickable(onClick = onClick)
            .padding(metrics.playButtonPadding),
        contentAlignment = Alignment.Center,
    ) {
        if (isBuffering) {
            NuvioLoadingIndicator(
                modifier = Modifier.size(metrics.playIconSize),
            )
        } else {
            Icon(
                painter = playPausePainter,
                contentDescription = if (isPlaying) {
                    stringResource(Res.string.compose_action_pause)
                } else {
                    stringResource(Res.string.detail_btn_play)
                },
                tint = Color.White,
                modifier = Modifier.size(metrics.playIconSize),
            )
        }
    }
}

@Composable
private fun ProgressControls(
    playbackSnapshot: PlayerPlaybackSnapshot,
    displayedPositionMs: Long,
    metrics: PlayerLayoutMetrics,
    showRemainingTime: Boolean = false,
    onRuntimeClick: () -> Unit = {},
    resizeMode: PlayerResizeMode,
    onScrubChange: (Long) -> Unit,
    onScrubFinished: (Long) -> Unit,
    onResizeModeClick: () -> Unit,
    onSpeedClick: (() -> Unit)? = null,
    speedPickerVisible: Boolean = false,
    onSpeedSelected: (Float) -> Unit = {},
    onSpeedPickerDismiss: () -> Unit = {},
    onSubtitleClick: (() -> Unit)? = null,
    onAudioClick: (() -> Unit)? = null,
    onSourcesClick: (() -> Unit)? = null,
    onEpisodesClick: (() -> Unit)? = null,
    onChaptersClick: (() -> Unit)? = null,
    onLiveChannelsClick: (() -> Unit)? = null,
    qualityLabel: String? = null,
    onQualityClick: (() -> Unit)? = null,
    seekPreview: SeekPreviewController? = null,
    onTimelineTouchChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val aspectRatioPainter = appIconPainter(AppIconResource.PlayerAspectRatio)
    val subtitlesPainter = appIconPainter(AppIconResource.PlayerSubtitles)
    val audioPainter = appIconPainter(AppIconResource.PlayerAudioFilled)
    val sourcesPainter = appIconPainter(AppIconResource.PlayerSource)

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Box(modifier = Modifier.weight(1f)) {
                PlayerChapterLabel(
                    chapters = playbackSnapshot.chapters,
                    positionMs = displayedPositionMs,
                    metrics = metrics,
                    onClick = onChaptersClick,
                )
            }
            PlayerEndTimeLabel(
                positionMs = displayedPositionMs,
                durationMs = playbackSnapshot.durationMs,
                playbackSpeed = playbackSnapshot.playbackSpeed,
                isPlaying = playbackSnapshot.isPlaying,
                metrics = metrics,
            )
        }
        PlayerSeekBar(
            durationMs = playbackSnapshot.durationMs,
            displayedPositionMs = displayedPositionMs,
            bufferedPositionMs = playbackSnapshot.bufferedPositionMs,
            chapters = playbackSnapshot.chapters,
            metrics = metrics,
            onScrubChange = onScrubChange,
            onScrubFinished = onScrubFinished,
            seekPreview = seekPreview,
            onTouchChange = onTimelineTouchChange,
            showTimeRow = false,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        // Legacy bottom bar: time pills on the bar's outer edges (lined up with the seek bar's
        // ends and the pill's top edge), actions in the middle.
        Box(modifier = Modifier.fillMaxWidth()) {
            TimePill(
                text = formatPlaybackTime(displayedPositionMs),
                fontSize = metrics.timeSize,
                modifier = Modifier.align(Alignment.TopStart),
            )
            TimePill(
                text = if (showRemainingTime) {
                    formatPlaybackRemaining(displayedPositionMs, playbackSnapshot.durationMs)
                } else {
                    formatPlaybackTime(playbackSnapshot.durationMs)
                },
                fontSize = metrics.timeSize,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(
                        role = Role.Button,
                        onClickLabel = stringResource(
                            if (showRemainingTime) {
                                Res.string.compose_player_show_elapsed_time
                            } else {
                                Res.string.compose_player_show_remaining_time
                            },
                        ),
                        onClick = onRuntimeClick,
                    ),
            )
            // Legacy bottom bar: same compact pill as before, now liquid glass.
            Surface(
                color = Color.Transparent,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.align(Alignment.TopCenter).playerLiquidGlass(
                    shape = RoundedCornerShape(24.dp),
                    refractionHeight = 10.dp,
                    refractionAmount = 14.dp,
                ),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PlayerActionPillButton(
                        label = stringResource(resizeMode.labelRes),
                        painter = aspectRatioPainter,
                        onClick = onResizeModeClick,
                    )
                    if (onSpeedClick != null) {
                        Box {
                            PlayerActionPillButton(
                                label = formatPlaybackSpeedLabel(playbackSnapshot.playbackSpeed),
                                icon = Icons.Rounded.Speed,
                                onClick = onSpeedClick,
                            )
                            PlayerSpeedPopup(
                                visible = speedPickerVisible,
                                currentSpeed = playbackSnapshot.playbackSpeed,
                                onSpeedSelected = onSpeedSelected,
                                onDismiss = onSpeedPickerDismiss,
                            )
                        }
                    }
                    if (onSubtitleClick != null) {
                        PlayerActionPillButton(
                            label = stringResource(Res.string.compose_player_subs),
                            painter = subtitlesPainter,
                            onClick = onSubtitleClick,
                        )
                    }
                    if (onAudioClick != null) {
                        PlayerActionPillButton(
                            label = stringResource(Res.string.compose_player_audio),
                            painter = audioPainter,
                            onClick = onAudioClick,
                        )
                    }
                    if (onSourcesClick != null) {
                        PlayerActionPillButton(
                            label = stringResource(Res.string.compose_player_sources),
                            painter = sourcesPainter,
                            onClick = onSourcesClick,
                        )
                    }
                    if (onChaptersClick != null) {
                        PlayerActionPillButton(
                            label = stringResource(Res.string.player_chapters),
                            icon = Icons.AutoMirrored.Rounded.ListAlt,
                            onClick = onChaptersClick,
                        )
                    }
                    if (onEpisodesClick != null) {
                        PlayerActionPillButton(
                            label = stringResource(Res.string.compose_player_episodes),
                            icon = Icons.Rounded.VideoLibrary,
                            onClick = onEpisodesClick,
                        )
                    }
                    if (onLiveChannelsClick != null) {
                        PlayerActionPillButton(
                            label = stringResource(Res.string.compose_player_channels),
                            icon = Icons.Rounded.Tv,
                            onClick = onLiveChannelsClick,
                        )
                    }
                    if (onQualityClick != null) {
                        PlayerActionPillButton(
                            label = qualityLabel?.takeIf { it.isNotBlank() } ?: "Quality",
                            onClick = onQualityClick,
                        )
                    }
                }
            }
        }
    }
}

private val PlayerTimelineThumbInset = 2.dp

private const val PlayerTimelineTapDedupePx = 12f

private fun Modifier.tapToSeekOnTimeline(
    durationMs: Long,
    currentPositionMs: () -> Long,
    onSeek: (Long) -> Unit,
): Modifier {
    if (durationMs <= 0L) return this

    return pointerInput(durationMs) {
        val thumbInsetPx = PlayerTimelineThumbInset.toPx()
        awaitEachGesture {
            val trackWidth = (size.width.toFloat() - thumbInsetPx * 2f)
                .takeIf { it > 0f } ?: return@awaitEachGesture
            val down = awaitFirstDown(
                requireUnconsumed = false,
                pass = PointerEventPass.Initial,
            )
            val downPosition = down.position
            var lastPosition = downPosition
            var maxDistance = 0f

            while (true) {
                val event = awaitPointerEvent(pass = PointerEventPass.Final)
                val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                lastPosition = change.position
                maxDistance = maxOf(maxDistance, (lastPosition - downPosition).getDistance())
                if (!change.pressed) break
            }

            if (maxDistance > viewConfiguration.touchSlop) return@awaitEachGesture

            val targetFraction = ((lastPosition.x - thumbInsetPx) / trackWidth).coerceIn(0f, 1f)
            val targetPositionMs = (durationMs * targetFraction).roundToLong().coerceIn(0L, durationMs)

            val toleranceMs = (durationMs * (PlayerTimelineTapDedupePx / trackWidth)).roundToLong()
            if (abs(targetPositionMs - currentPositionMs()) <= toleranceMs) return@awaitEachGesture

            onSeek(targetPositionMs)
        }
    }
}

@Composable
internal fun PlayerSeekBar(
    durationMs: Long,
    displayedPositionMs: Long,
    metrics: PlayerLayoutMetrics,
    onScrubChange: (Long) -> Unit,
    onScrubFinished: (Long) -> Unit,
    modifier: Modifier = Modifier,
    bufferedPositionMs: Long = 0L,
    seekPreview: SeekPreviewController? = null,
    chapters: List<PlayerChapter> = emptyList(),
    onTouchChange: (Boolean) -> Unit = {},
    /** The legacy layout draws the time pills beside its action pill instead. */
    showTimeRow: Boolean = true,
) {
    val seekDurationMs = durationMs.coerceAtLeast(1L)
    val bufferedFraction = playerBufferedFraction(bufferedPositionMs, durationMs)
    val chapterMarks = remember(chapters, durationMs) { chapters.chapterMarkFractions(durationMs) }
    val seekDescription = stringResource(Res.string.player_seek_position)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isDragged by interactionSource.collectIsDraggedAsState()
    ReportTimelineTouch(durationMs > 0L && (isPressed || isDragged), onTouchChange)
    Column(modifier = modifier) {
        Box {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(metrics.sliderTouchHeight)
                    .graphicsLayer(scaleY = metrics.sliderScaleY)
                    .tapToSeekOnTimeline(
                        durationMs = durationMs,
                        currentPositionMs = { displayedPositionMs },
                        onSeek = { positionMs ->
                            val targetPositionMs = positionMs.coerceIn(0L, seekDurationMs)
                            onScrubChange(targetPositionMs)
                            onScrubFinished(targetPositionMs)
                        },
                    ),
            ) {
                Slider(
                    modifier = Modifier
                        .fillMaxSize()
                        .semantics { contentDescription = seekDescription },
                    value = displayedPositionMs.coerceIn(0L, seekDurationMs).toFloat(),
                    onValueChange = { value -> onScrubChange(value.toLong()) },
                    onValueChangeFinished = { onScrubFinished(displayedPositionMs.coerceIn(0L, seekDurationMs)) },
                    enabled = durationMs > 0L,
                    valueRange = 0f..seekDurationMs.toFloat(),
                    interactionSource = interactionSource,
                    track = { sliderState -> PlayerProgressTrack(sliderState, bufferedFraction, chapterMarks) },
                )
            }
            SeekPreviewOverlay(
                controller = seekPreview,
                active = durationMs > 0L && (isPressed || isDragged),
                positionMs = displayedPositionMs.coerceIn(0L, seekDurationMs),
                durationMs = durationMs,
                modifier = Modifier.matchParentSize(),
            )
        }
        if (showTimeRow) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .padding(top = 4.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TimePill(text = formatPlaybackTime(displayedPositionMs), fontSize = metrics.timeSize)
                TimePill(text = formatPlaybackTime(durationMs), fontSize = metrics.timeSize)
            }
        }
    }
}

internal fun playerBufferedFraction(bufferedPositionMs: Long, durationMs: Long): Float =
    if (durationMs > 0L) {
        (bufferedPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

internal val PlayerBaseTrackColor = Color.White.copy(alpha = 0.20f)

internal val PlayerBufferedTrackColor = Color.White.copy(alpha = 0.40f)

@Composable
private fun PlayerBufferedTrack(bufferedFraction: Float, enabled: Boolean = true) {
    if (bufferedFraction <= 0f) return
    val bufferedState = remember { SliderState(value = 0f, valueRange = 0f..1f) }
    bufferedState.value = bufferedFraction
    SliderDefaults.Track(
        sliderState = bufferedState,
        enabled = enabled,
        colors = SliderDefaults.colors(
            activeTrackColor = PlayerBufferedTrackColor,
            inactiveTrackColor = Color.Transparent,
            disabledActiveTrackColor = PlayerBufferedTrackColor,
            disabledInactiveTrackColor = Color.Transparent,
        ),
        drawStopIndicator = null,
        thumbTrackGapSize = 0.dp,
    )
}

@Composable
private fun PlayerProgressTrack(
    sliderState: SliderState,
    bufferedFraction: Float,
    chapterMarks: List<Float> = emptyList(),
) {
    val palette = MaterialTheme.themePalette
    val inactiveTrackColors = SliderDefaults.colors(
        activeTrackColor = Color.Transparent,
        inactiveTrackColor = PlayerBaseTrackColor,
        disabledActiveTrackColor = Color.Transparent,
        disabledInactiveTrackColor = PlayerBaseTrackColor,
    )
    val activeTrackColors = SliderDefaults.colors(
        activeTrackColor = Color.White,
        inactiveTrackColor = Color.Transparent,
        disabledActiveTrackColor = Color.White,
        disabledInactiveTrackColor = Color.Transparent,
    )

    Box {
        SliderDefaults.Track(
            sliderState = sliderState,
            colors = inactiveTrackColors,
        )
        PlayerBufferedTrack(bufferedFraction = bufferedFraction)
        SliderDefaults.Track(
            sliderState = sliderState,
            modifier = Modifier.gradientMask(palette.accentBrush()),
            colors = activeTrackColors,
        )
        if (chapterMarks.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .drawBehind {
                        val markWidth = PlayerChapterMarkWidth.toPx()
                        chapterMarks.forEach { fraction ->
                            drawRect(
                                color = PlayerChapterMarkColor,
                                topLeft = Offset(size.width * fraction - markWidth / 2f, 0f),
                                size = Size(markWidth, size.height),
                            )
                        }
                    },
            )
        }
    }
}

@Composable
internal fun LockedPlayerOverlay(
    playbackSnapshot: PlayerPlaybackSnapshot,
    displayedPositionMs: Long,
    metrics: PlayerLayoutMetrics,
    horizontalSafePadding: androidx.compose.ui.unit.Dp,
    onUnlock: () -> Unit,
    useLegacyLayout: Boolean = false,
    showRemainingTime: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val durationMs = playbackSnapshot.durationMs.coerceAtLeast(1L)
    val sliderColors = SliderDefaults.colors(
        thumbColor = Color.White,
        activeTrackColor = Color.White,
        inactiveTrackColor = Color.White.copy(alpha = 0.28f),
        disabledThumbColor = Color.White,
        disabledActiveTrackColor = Color.White,
        disabledInactiveTrackColor = Color.White.copy(alpha = 0.28f),
    )

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.72f),
                        ),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(78.dp)
                    .playerLiquidGlass(
                        shape = CircleShape,
                        refractionHeight = 10.dp,
                        refractionAmount = 14.dp,
                    )
                    .clickable(onClick = onUnlock),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = stringResource(Res.string.compose_player_unlock_controls),
                    tint = Color.White,
                    modifier = Modifier.size(34.dp),
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(Res.string.compose_player_tap_to_unlock),
                style = MaterialTheme.nuvioTypeScale.bodyMd.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White.copy(alpha = 0.92f),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = horizontalSafePadding + metrics.horizontalPadding)
                .padding(bottom = metrics.sliderBottomOffset),
        ) {
            if (useLegacyLayout) {
                Slider(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(metrics.sliderTouchHeight)
                        .graphicsLayer(scaleY = metrics.sliderScaleY),
                    value = displayedPositionMs.coerceIn(0L, durationMs).toFloat(),
                    onValueChange = {},
                    onValueChangeFinished = {},
                    valueRange = 0f..durationMs.toFloat(),
                    enabled = false,
                    colors = sliderColors,
                    track = { sliderState ->
                        Box {
                            SliderDefaults.Track(
                                sliderState = sliderState,
                                enabled = false,
                                colors = SliderDefaults.colors(
                                    disabledActiveTrackColor = Color.Transparent,
                                    disabledInactiveTrackColor = PlayerBaseTrackColor,
                                ),
                            )
                            PlayerBufferedTrack(
                                bufferedFraction = playerBufferedFraction(
                                    playbackSnapshot.bufferedPositionMs,
                                    playbackSnapshot.durationMs,
                                ),
                                enabled = false,
                            )
                            SliderDefaults.Track(
                                sliderState = sliderState,
                                enabled = false,
                                colors = SliderDefaults.colors(
                                    disabledActiveTrackColor = Color.White,
                                    disabledInactiveTrackColor = Color.Transparent,
                                ),
                            )
                        }
                    },
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp)
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TimePill(text = formatPlaybackTime(displayedPositionMs), fontSize = metrics.timeSize)
                    TimePill(text = formatPlaybackTime(durationMs), fontSize = metrics.timeSize)
                }
            } else {
                PlayerTimeline(
                    snapshot = playbackSnapshot,
                    displayedPositionMs = displayedPositionMs,
                    onScrubChange = {},
                    onScrubFinished = {},
                    enabled = false,
                )
                Text(
                    text = formatPlaybackRuntime(displayedPositionMs, playbackSnapshot.durationMs, showRemainingTime),
                    style = MaterialTheme.nuvioTypeScale.bodyMd.copy(fontSize = (metrics.timeSize.value + 2).sp),
                    color = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.align(Alignment.End),
                )
            }
        }
    }
}

@Composable
private fun TimePill(
    text: String,
    fontSize: androidx.compose.ui.unit.TextUnit,
    glass: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val glassModifier = if (glass) {
        Modifier.playerLiquidGlass(
            shape = RoundedCornerShape(12.dp),
            refractionHeight = 4.dp,
            refractionAmount = 6.dp,
        )
    } else {
        Modifier
    }
    Box(
        modifier = modifier.then(glassModifier).padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.nuvioTypeScale.labelSm.copy(
                fontSize = fontSize,
                lineHeight = fontSize * 1.25f,
                fontWeight = FontWeight.Medium,
            ),
            color = Color.White,
        )
    }
}

@Composable
private fun PlayerActionPillButton(
    label: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    painter: Painter? = null,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            painter != null -> Icon(
                painter = painter,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )

            icon != null -> Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.nuvioTypeScale.labelSm,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
        )
    }
}
