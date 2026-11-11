package com.nuvio.app.features.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ListAlt
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.CastConnected
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.nuvio.app.core.i18n.localizedEpisodeTitle
import com.nuvio.app.core.ui.AppIconResource
import com.nuvio.app.core.ui.appIconPainter
import com.nuvio.app.features.player.seekpreview.SeekPreviewController
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/** The red of the Netflix player's seek bar. */
internal val NetflixRed = Color(0xFFE50914)
private val NetflixMenuSurface = Color(0xF2191919)

/** One entry of the ⋮ menu: every control the Netflix player has no button for. */
internal class NetflixMenuItem(
    val label: String,
    val onClick: () -> Unit,
    val icon: ImageVector? = null,
    val painter: Painter? = null,
    val tint: Color = Color.White,
)

/**
 * Everything that is not on Netflix's player, in one list: sources, quality, chapters, aspect ratio, night mode,
 * rating, party, picture in picture, rotation, external player, playback info, video settings, engine switch and
 * intro submission. Each only appears when the player offers it.
 */
@Composable
internal fun netflixMenuItems(
    resizeMode: PlayerResizeMode,
    onResizeModeClick: () -> Unit,
    onSourcesClick: (() -> Unit)?,
    qualityLabel: String?,
    onQualityClick: (() -> Unit)?,
    onChaptersClick: (() -> Unit)?,
    nightModeEnabled: Boolean,
    onNightModeClick: (() -> Unit)?,
    onRateClick: (() -> Unit)?,
    userRating: Int?,
    onPartyClick: (() -> Unit)?,
    partyActive: Boolean,
    onPictureInPictureClick: (() -> Unit)?,
    onOrientationToggleClick: (() -> Unit)?,
    isPortraitPlayback: Boolean,
    onOpenInExternalPlayer: (() -> Unit)?,
    onInfoClick: (() -> Unit)?,
    onStreamInfoClick: () -> Unit,
    onVideoSettingsClick: (() -> Unit)?,
    onSwitchEngineClick: (() -> Unit)?,
    onSubmitIntroClick: (() -> Unit)?,
): List<NetflixMenuItem> {
    val accent = MaterialTheme.colorScheme.primary
    return listOfNotNull(
        onSourcesClick?.let {
            NetflixMenuItem(stringResource(Res.string.compose_player_sources), it, painter = appIconPainter(AppIconResource.PlayerSource))
        },
        onQualityClick?.let {
            NetflixMenuItem(
                qualityLabel?.takeIf(String::isNotBlank) ?: stringResource(Res.string.player_toolbar_button_quality),
                it,
                icon = Icons.Rounded.HighQuality,
            )
        },
        onChaptersClick?.let {
            NetflixMenuItem(stringResource(Res.string.player_chapters), it, icon = Icons.AutoMirrored.Rounded.ListAlt)
        },
        NetflixMenuItem(
            stringResource(resizeMode.labelRes),
            onResizeModeClick,
            painter = appIconPainter(AppIconResource.PlayerAspectRatio),
        ),
        onNightModeClick?.let {
            NetflixMenuItem(
                "${stringResource(Res.string.player_night_mode)} ${
                    stringResource(if (nightModeEnabled) Res.string.player_night_mode_on else Res.string.player_night_mode_off)
                }",
                it,
                icon = Icons.Rounded.NightsStay,
                tint = if (nightModeEnabled) accent else Color.White,
            )
        },
        onRateClick?.let {
            NetflixMenuItem(
                userRating?.let { rating -> stringResource(Res.string.user_rating_action_rated, rating) }
                    ?: stringResource(Res.string.user_rating_action_rate),
                it,
                icon = if (userRating != null) Icons.Rounded.Star else Icons.Rounded.StarBorder,
            )
        },
        onPartyClick?.let {
            NetflixMenuItem(
                stringResource(Res.string.party_title),
                it,
                icon = Icons.Rounded.Groups,
                tint = if (partyActive) accent else Color.White,
            )
        },
        onPictureInPictureClick?.let {
            NetflixMenuItem(stringResource(Res.string.compose_player_picture_in_picture), it, icon = Icons.Rounded.PictureInPictureAlt)
        },
        onOrientationToggleClick?.let {
            NetflixMenuItem(
                stringResource(playerOrientationToggleLabel(isPortraitPlayback)),
                it,
                icon = playerOrientationToggleIcon(isPortraitPlayback),
            )
        },
        onOpenInExternalPlayer?.let {
            NetflixMenuItem(stringResource(Res.string.streams_open_external_player), it, icon = Icons.AutoMirrored.Rounded.OpenInNew)
        },
        NetflixMenuItem(
            stringResource(Res.string.compose_player_playback_info),
            onInfoClick ?: onStreamInfoClick,
            icon = Icons.Rounded.Info,
        ),
        onVideoSettingsClick?.let {
            NetflixMenuItem(stringResource(Res.string.player_action_video_settings), it, icon = Icons.Rounded.Build)
        },
        onSwitchEngineClick?.let {
            NetflixMenuItem(stringResource(Res.string.cd_switch_player_engine), it, icon = Icons.Rounded.SwapHoriz)
        },
        onSubmitIntroClick?.let {
            NetflixMenuItem(stringResource(Res.string.submit_intro_action), it, icon = Icons.Rounded.Flag)
        },
    )
}

/**
 * The player controls laid out like Netflix's: a back arrow and the title across the top with Cast and a ⋮ menu
 * at the right, skip back / play-pause / skip forward in the middle with no backgrounds, a thin red seek bar with
 * the time left beside it, and a row of labelled buttons underneath (speed, lock, episodes, audio and subtitles,
 * next episode).
 */
@Composable
internal fun NetflixPlayerControls(
    title: String,
    seasonNumber: Int?,
    episodeNumber: Int?,
    episodeTitle: String?,
    playbackSnapshot: PlayerPlaybackSnapshot,
    displayedPositionMs: Long,
    metrics: PlayerLayoutMetrics,
    showPlaybackControls: Boolean,
    showRemainingTime: Boolean,
    onRuntimeClick: () -> Unit,
    parentalWarnings: List<ParentalWarning>,
    showParentalGuide: Boolean,
    onParentalGuideAnimationComplete: () -> Unit,
    onInteraction: () -> Unit,
    onMenuOpenChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onLockToggle: () -> Unit,
    onTogglePlayback: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    skipBackwardSeconds: Int,
    skipForwardSeconds: Int,
    showSkipButtons: Boolean,
    onNextEpisodeClick: (() -> Unit)?,
    onSpeedClick: (() -> Unit)?,
    speedPickerVisible: Boolean,
    onSpeedSelected: (Float) -> Unit,
    onSpeedPickerDismiss: () -> Unit,
    onSubtitleClick: (() -> Unit)?,
    onAudioClick: (() -> Unit)?,
    onEpisodesClick: (() -> Unit)?,
    onLiveChannelsClick: (() -> Unit)?,
    onCastClick: (() -> Unit)?,
    isCasting: Boolean,
    menuItems: List<NetflixMenuItem>,
    chapters: List<PlayerChapter>,
    onScrubChange: (Long) -> Unit,
    onScrubFinished: (Long) -> Unit,
    seekPreview: SeekPreviewController?,
    onTimelineTouchChange: (Boolean) -> Unit,
    horizontalSafePadding: Dp,
    modifier: Modifier = Modifier,
) {
    // While a menu is open the controls must not fade out from under it.
    var overflowOpen by remember { mutableStateOf(false) }
    var audioMenuOpen by remember { mutableStateOf(false) }
    val latestMenuOpenChange by rememberUpdatedState(onMenuOpenChange)
    LaunchedEffect(overflowOpen || audioMenuOpen) { latestMenuOpenChange(overflowOpen || audioMenuOpen) }
    DisposableEffect(Unit) { onDispose { latestMenuOpenChange(false) } }
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val compact = maxWidth < 600.dp
        val sidePadding = maxOf(horizontalSafePadding, metrics.horizontalPadding)

        // Netflix dims the whole picture while the controls are up, darker still at the top and bottom.
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.38f)))
        Box(
            Modifier
                .fillMaxWidth()
                .height(120.dp)
                .align(Alignment.TopCenter)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent))),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(200.dp)
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)))),
        )

        if (!showPlaybackControls) return@BoxWithConstraints

        // ---- Top bar
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .windowInsetsPadding(playerToolbarTopInsets(metrics))
                .padding(horizontal = sidePadding),
        ) {
            NetflixIconButton(
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                description = stringResource(Res.string.compose_player_close),
                onClick = onBack,
                modifier = Modifier.align(Alignment.CenterStart),
            )
            NetflixTitle(
                title = title,
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber,
                episodeTitle = episodeTitle,
                modifier = Modifier.align(Alignment.Center).fillMaxWidth(if (compact) 0.55f else 0.6f),
            )
            Row(
                modifier = Modifier.align(Alignment.CenterEnd),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                onCastClick?.let {
                    NetflixIconButton(
                        icon = if (isCasting) Icons.Rounded.CastConnected else Icons.Rounded.Cast,
                        description = stringResource(Res.string.player_cast),
                        onClick = { onInteraction(); it() },
                    )
                }
                NetflixOverflowMenu(items = menuItems, onInteraction = onInteraction, onOpenChange = { overflowOpen = it })
            }
            ParentalGuideOverlay(
                warnings = parentalWarnings,
                isVisible = showParentalGuide,
                onAnimationComplete = onParentalGuideAnimationComplete,
                contentPadding = PaddingValues(top = 56.dp),
                modifier = Modifier.align(Alignment.TopStart),
            )
        }

        // ---- Centre: skip back, play / pause, skip forward
        Row(
            modifier = Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 44.dp else 84.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showSkipButtons) {
                NetflixCenterButton(
                    description = stringResource(Res.string.compose_player_seek_back_seconds, skipBackwardSeconds),
                    onClick = { onInteraction(); onSeekBack() },
                ) { SkipStepIcon(seconds = skipBackwardSeconds, forward = false, size = if (compact) 40.dp else 52.dp) }
            }
            NetflixCenterButton(
                description = if (playbackSnapshot.isPlaying) {
                    stringResource(Res.string.compose_action_pause)
                } else {
                    stringResource(Res.string.detail_btn_play)
                },
                onClick = { onInteraction(); onTogglePlayback() },
            ) {
                val size = if (compact) 56.dp else 72.dp
                if (playbackSnapshot.isLoading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 4.dp,
                        modifier = Modifier.size(size * 0.8f),
                    )
                } else {
                    Icon(
                        imageVector = if (playbackSnapshot.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(size),
                    )
                }
            }
            if (showSkipButtons) {
                NetflixCenterButton(
                    description = stringResource(Res.string.compose_player_seek_forward_seconds, skipForwardSeconds),
                    onClick = { onInteraction(); onSeekForward() },
                ) { SkipStepIcon(seconds = skipForwardSeconds, forward = true, size = if (compact) 40.dp else 52.dp) }
            }
        }

        // ---- Bottom: seek bar with time left, then the labelled buttons
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(playerTimelineBottomInsets(metrics))
                .padding(horizontal = sidePadding),
        ) {
            val durationMs = playbackSnapshot.durationMs
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
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
                        chapters = chapters,
                        netflixStyle = true,
                    )
                }
                if (durationMs > 0L) {
                    // Netflix shows the time left; tapping swaps it for elapsed / total.
                    Text(
                        text = if (showRemainingTime) {
                            formatPlaybackRuntime(displayedPositionMs, durationMs, showRemainingTime = false)
                        } else {
                            formatNetflixRemaining(displayedPositionMs, durationMs)
                        },
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        modifier = Modifier
                            .padding(start = 14.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                role = Role.Button,
                            ) {
                                onRuntimeClick()
                                onInteraction()
                            },
                    )
                }
            }
            NetflixBottomButtons(
                playbackSnapshot = playbackSnapshot,
                compact = compact,
                onInteraction = onInteraction,
                onLockToggle = onLockToggle,
                onSpeedClick = onSpeedClick,
                speedPickerVisible = speedPickerVisible,
                onSpeedSelected = onSpeedSelected,
                onSpeedPickerDismiss = onSpeedPickerDismiss,
                onEpisodesClick = onEpisodesClick,
                onLiveChannelsClick = onLiveChannelsClick,
                onSubtitleClick = onSubtitleClick,
                onAudioClick = onAudioClick,
                onNextEpisodeClick = onNextEpisodeClick,
                onMenuOpenChange = { audioMenuOpen = it },
                modifier = Modifier.padding(top = 2.dp, bottom = 6.dp),
            )
        }
    }
}

@Composable
private fun NetflixTitle(
    title: String,
    seasonNumber: Int?,
    episodeNumber: Int?,
    episodeTitle: String?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        if (seasonNumber != null && episodeNumber != null && !episodeTitle.isNullOrBlank()) {
            val displayTitle = localizedEpisodeTitle(episodeTitle, episodeNumber) ?: episodeTitle
            Text(
                text = stringResource(Res.string.compose_player_episode_title_format, seasonNumber, episodeNumber, displayTitle),
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun NetflixIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(26.dp))
    }
}

@Composable
private fun NetflixCenterButton(
    description: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(8.dp)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** ⋮ at the top right: opens a list of the controls that Netflix's player doesn't show. */
@Composable
private fun NetflixOverflowMenu(items: List<NetflixMenuItem>, onInteraction: () -> Unit, onOpenChange: (Boolean) -> Unit) {
    var open by remember { mutableStateOf(false) }
    LaunchedEffect(open) { onOpenChange(open) }
    val gapPx = with(LocalDensity.current) { 4.dp.roundToPx() }
    Box {
        NetflixIconButton(
            icon = Icons.Rounded.MoreVert,
            description = stringResource(Res.string.compose_player_more_actions),
            onClick = {
                open = !open
                onInteraction()
            },
        )
        if (open) {
            Popup(
                popupPositionProvider = remember(gapPx) { NetflixPopupPosition(gapPx, above = false, alignEnd = true) },
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true),
            ) {
                NetflixMenuPanel(
                    items = items,
                    onSelected = { item ->
                        open = false
                        onInteraction()
                        item.onClick()
                    },
                )
            }
        }
    }
}

@Composable
private fun NetflixMenuPanel(items: List<NetflixMenuItem>, onSelected: (NetflixMenuItem) -> Unit) {
    Column(
        modifier = Modifier
            .widthIn(min = 220.dp, max = 300.dp)
            .heightIn(max = 300.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(NetflixMenuSurface)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 6.dp),
    ) {
        items.forEach { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) { onSelected(item) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (item.painter != null) {
                    Icon(item.painter, contentDescription = null, tint = item.tint, modifier = Modifier.size(22.dp))
                } else if (item.icon != null) {
                    Icon(item.icon, contentDescription = null, tint = item.tint, modifier = Modifier.size(22.dp))
                }
                Text(
                    text = item.label,
                    color = Color.White,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Speed, Lock, Episodes (or Channels on live TV), Audio & Subtitles and Next Episode, evenly spaced under the bar. */
@Composable
private fun NetflixBottomButtons(
    playbackSnapshot: PlayerPlaybackSnapshot,
    compact: Boolean,
    onInteraction: () -> Unit,
    onLockToggle: () -> Unit,
    onSpeedClick: (() -> Unit)?,
    speedPickerVisible: Boolean,
    onSpeedSelected: (Float) -> Unit,
    onSpeedPickerDismiss: () -> Unit,
    onEpisodesClick: (() -> Unit)?,
    onLiveChannelsClick: (() -> Unit)?,
    onSubtitleClick: (() -> Unit)?,
    onAudioClick: (() -> Unit)?,
    onNextEpisodeClick: (() -> Unit)?,
    onMenuOpenChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var audioSubtitleMenu by remember { mutableStateOf(false) }
    LaunchedEffect(audioSubtitleMenu) { onMenuOpenChange(audioSubtitleMenu) }
    val gapPx = with(LocalDensity.current) { 8.dp.roundToPx() }
    val audioLabel = stringResource(Res.string.compose_player_audio)
    val subtitleLabel = stringResource(Res.string.compose_player_subtitles)
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        onSpeedClick?.let { openSpeed ->
            Box {
                NetflixTextButton(
                    icon = Icons.Rounded.Speed,
                    label = "Speed (${formatPlaybackSpeedLabel(playbackSnapshot.playbackSpeed)})",
                    compact = compact,
                    onClick = { onInteraction(); openSpeed() },
                )
                PlayerSpeedPopup(
                    visible = speedPickerVisible,
                    currentSpeed = playbackSnapshot.playbackSpeed,
                    onSpeedSelected = onSpeedSelected,
                    onDismiss = onSpeedPickerDismiss,
                )
            }
        }
        NetflixTextButton(
            icon = Icons.Rounded.Lock,
            label = "Lock",
            compact = compact,
            onClick = { onInteraction(); onLockToggle() },
        )
        onEpisodesClick?.let {
            NetflixTextButton(
                painter = appIconPainter(AppIconResource.PlayerEpisodes),
                label = stringResource(Res.string.compose_player_episodes),
                compact = compact,
                onClick = { onInteraction(); it() },
            )
        }
        onLiveChannelsClick?.let {
            NetflixTextButton(
                icon = Icons.Rounded.LiveTv,
                label = "Channels",
                compact = compact,
                onClick = { onInteraction(); it() },
            )
        }
        if (onAudioClick != null || onSubtitleClick != null) {
            Box {
                NetflixTextButton(
                    painter = appIconPainter(AppIconResource.PlayerSubtitles),
                    label = if (onSubtitleClick != null) "Audio & Subtitles" else audioLabel,
                    compact = compact,
                    onClick = {
                        onInteraction()
                        when {
                            // Netflix opens one sheet for both; here the two panels sit behind one button.
                            onAudioClick != null && onSubtitleClick != null -> audioSubtitleMenu = !audioSubtitleMenu
                            onAudioClick != null -> onAudioClick()
                            else -> onSubtitleClick?.invoke()
                        }
                    },
                )
                if (audioSubtitleMenu && onAudioClick != null && onSubtitleClick != null) {
                    Popup(
                        popupPositionProvider = remember(gapPx) { NetflixPopupPosition(gapPx, above = true, alignEnd = false) },
                        onDismissRequest = { audioSubtitleMenu = false },
                        properties = PopupProperties(focusable = true),
                    ) {
                        NetflixMenuPanel(
                            items = listOf(
                                NetflixMenuItem(audioLabel, onAudioClick, painter = appIconPainter(AppIconResource.PlayerAudioFilled)),
                                NetflixMenuItem(subtitleLabel, onSubtitleClick, painter = appIconPainter(AppIconResource.PlayerSubtitles)),
                            ),
                            onSelected = { item ->
                                audioSubtitleMenu = false
                                onInteraction()
                                item.onClick()
                            },
                        )
                    }
                }
            }
        }
        onNextEpisodeClick?.let {
            NetflixTextButton(
                icon = Icons.Rounded.SkipNext,
                label = stringResource(Res.string.player_next_episode),
                compact = compact,
                onClick = { onInteraction(); it() },
            )
        }
    }
}

@Composable
private fun NetflixTextButton(
    label: String,
    compact: Boolean,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    painter: Painter? = null,
) {
    val content: @Composable () -> Unit = {
        if (painter != null) {
            Icon(painter, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
        } else if (icon != null) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
        }
    }
    val text: @Composable () -> Unit = {
        Text(
            text = label,
            color = Color.White,
            fontSize = if (compact) 11.sp else 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
    val shape = RoundedCornerShape(10.dp)
    if (compact) {
        Column(
            modifier = Modifier.clip(shape).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            content()
            text()
        }
    } else {
        Row(
            modifier = Modifier.clip(shape).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            content()
            text()
        }
    }
}

/** "1h 7m", "42m" or "35s": the time left, the way Netflix prints it. */
internal fun formatNetflixRemaining(positionMs: Long, durationMs: Long): String {
    val totalSeconds = ((durationMs - positionMs.coerceAtLeast(0L)).coerceAtLeast(0L)) / 1000L
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m"
        else -> "${totalSeconds}s"
    }
}

private class NetflixPopupPosition(
    private val gapPx: Int,
    private val above: Boolean,
    private val alignEnd: Boolean,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val maxX = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
        val x = (if (alignEnd) anchorBounds.right - popupContentSize.width else anchorBounds.center.x - popupContentSize.width / 2)
            .coerceIn(0, maxX)
        val aboveY = anchorBounds.top - gapPx - popupContentSize.height
        val belowY = anchorBounds.bottom + gapPx
        val maxY = (windowSize.height - popupContentSize.height).coerceAtLeast(0)
        val y = if (above) (if (aboveY >= 0) aboveY else belowY) else belowY
        return IntOffset(x, y.coerceIn(0, maxY))
    }
}
