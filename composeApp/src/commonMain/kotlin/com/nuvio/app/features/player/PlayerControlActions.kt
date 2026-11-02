package com.nuvio.app.features.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.ListAlt
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.Toc
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.CastConnected
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.StayCurrentLandscape
import androidx.compose.material.icons.rounded.StayCurrentPortrait
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.core.ui.AppIconResource
import com.nuvio.app.core.ui.NuvioBackButton
import com.nuvio.app.core.ui.appIconPainter
import com.nuvio.app.core.ui.nuvioTypeScale
import com.nuvio.app.core.ui.glass.LiquidGlassSurface
import com.nuvio.app.core.ui.glass.liquidGlassIconTint
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

internal fun playerOrientationToggleIcon(isPortraitPlayback: Boolean): ImageVector =
    if (isPortraitPlayback) Icons.Rounded.StayCurrentLandscape else Icons.Rounded.StayCurrentPortrait

internal fun playerOrientationToggleLabel(isPortraitPlayback: Boolean): StringResource =
    if (isPortraitPlayback) Res.string.player_action_rotate_landscape else Res.string.player_action_rotate_portrait

@Composable
internal fun PlayerToolbar(
    isLocked: Boolean,
    onLockToggle: () -> Unit,
    onBack: () -> Unit,
    onCastClick: (() -> Unit)? = null,
    isCasting: Boolean = false,
    modifier: Modifier = Modifier,
    onPartyClick: (() -> Unit)? = null,
    partyActive: Boolean = false,
    onOrientationToggleClick: (() -> Unit)? = null,
    isPortraitPlayback: Boolean = false,
    onPictureInPictureClick: (() -> Unit)? = null,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.weight(1f))
        // Toolbar actions share one liquid-glass capsule; close sits in its own glass circle.
        Row(
            modifier = Modifier.playerLiquidGlass(
                shape = PlayerGlassCapsule,
                refractionHeight = 8.dp,
                refractionAmount = 12.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            onPictureInPictureClick?.let {
                PlayerAction(
                    description = stringResource(Res.string.compose_player_picture_in_picture),
                    icon = Icons.Rounded.PictureInPictureAlt,
                    onClick = it,
                )
            }
            onOrientationToggleClick?.let {
                PlayerAction(
                    description = stringResource(playerOrientationToggleLabel(isPortraitPlayback)),
                    icon = playerOrientationToggleIcon(isPortraitPlayback),
                    onClick = it,
                )
            }
            if (onPartyClick != null) {
                PlayerAction(
                    description = stringResource(Res.string.party_title),
                    icon = Icons.Rounded.Groups,
                    onClick = onPartyClick,
                    tint = if (partyActive) MaterialTheme.colorScheme.primary else liquidGlassIconTint(LiquidGlassSurface.PLAYER, Color.White),
                )
            }
            onCastClick?.let {
                PlayerAction(
                    description = stringResource(Res.string.player_cast),
                    icon = if (isCasting) Icons.Rounded.CastConnected else Icons.Rounded.Cast,
                    onClick = it,
                )
            }
            PlayerAction(
                description = stringResource(
                    if (isLocked) Res.string.compose_player_unlock_controls else Res.string.compose_player_lock_controls,
                ),
                icon = if (isLocked) Icons.Rounded.LockOpen else Icons.Rounded.Lock,
                onClick = onLockToggle,
            )
        }
        Spacer(Modifier.width(8.dp))
        NuvioBackButton(
            onClick = onBack,
            modifier = Modifier.playerLiquidGlass(CircleShape, refractionHeight = 8.dp, refractionAmount = 12.dp),
            shape = CircleShape,
            containerColor = Color.Transparent,
            contentColor = liquidGlassIconTint(LiquidGlassSurface.PLAYER, Color.White),
            buttonSize = 48.dp,
            iconSize = 24.dp,
            contentDescription = stringResource(Res.string.compose_player_close),
        )
    }
}

@Composable
internal fun PlayerControlActions(
    playbackSnapshot: PlayerPlaybackSnapshot,
    displayedPositionMs: Long,
    showRemainingTime: Boolean,
    onRuntimeClick: () -> Unit,
    metrics: PlayerLayoutMetrics,
    resizeMode: PlayerResizeMode,
    onSubtitleClick: () -> Unit,
    onAudioClick: () -> Unit,
    onSourcesClick: (() -> Unit)?,
    onEpisodesClick: (() -> Unit)?,
    onChaptersClick: (() -> Unit)? = null,
    onNextEpisodeClick: (() -> Unit)?,
    onSpeedClick: () -> Unit,
    speedPickerVisible: Boolean = false,
    onSpeedSelected: (Float) -> Unit = {},
    onSpeedPickerDismiss: () -> Unit = {},
    nightModeEnabled: Boolean = false,
    onNightModeClick: (() -> Unit)? = null,
    onResizeModeClick: () -> Unit,
    onVideoSettingsClick: (() -> Unit)?,
    onOpenInExternalPlayer: (() -> Unit)?,
    onStreamInfoClick: () -> Unit,
    onSubmitIntroClick: (() -> Unit)?,
    qualityLabel: String? = null,
    onQualityClick: (() -> Unit)? = null,
    onSwitchEngineClick: (() -> Unit)? = null,
    onInfoClick: (() -> Unit)? = null,
    onRateClick: (() -> Unit)? = null,
    userRating: Int? = null,
    onInteraction: () -> Unit,
) {
    val availableActions = buildMap<PlayerToolbarButton, PlayerControlAction> {
        onNextEpisodeClick?.let {
            this[PlayerToolbarButton.NEXT_EPISODE] = PlayerControlAction(
                stringResource(Res.string.player_next_episode), it,
                icon = Icons.Rounded.SkipNext, iconSize = 40.dp,
            )
        }
        this[PlayerToolbarButton.SPEED] = PlayerControlAction(
            "${stringResource(Res.string.compose_player_speed)} ${formatPlaybackSpeedLabel(playbackSnapshot.playbackSpeed)}",
            onSpeedClick, icon = Icons.Rounded.Speed,
            popup = {
                PlayerSpeedPopup(
                    visible = speedPickerVisible,
                    currentSpeed = playbackSnapshot.playbackSpeed,
                    onSpeedSelected = onSpeedSelected,
                    onDismiss = onSpeedPickerDismiss,
                )
            },
        )
        this[PlayerToolbarButton.RESIZE] = PlayerControlAction(
            stringResource(resizeMode.labelRes), onResizeModeClick,
            painter = appIconPainter(AppIconResource.PlayerAspectRatio),
        )
        this[PlayerToolbarButton.SUBTITLES] = PlayerControlAction(
            stringResource(Res.string.compose_player_subtitles), onSubtitleClick,
            painter = appIconPainter(AppIconResource.PlayerSubtitles),
        )
        onChaptersClick?.let {
            this[PlayerToolbarButton.CHAPTERS] = PlayerControlAction(
                stringResource(Res.string.player_chapters), it,
                icon = Icons.AutoMirrored.Rounded.ListAlt,
            )
        }
        this[PlayerToolbarButton.AUDIO] = PlayerControlAction(
            stringResource(Res.string.compose_player_audio), onAudioClick,
            painter = appIconPainter(AppIconResource.PlayerAudioFilled),
        )
        onNightModeClick?.let {
            this[PlayerToolbarButton.NIGHT_MODE] = PlayerControlAction(
                "${stringResource(Res.string.player_night_mode)} ${
                    stringResource(if (nightModeEnabled) Res.string.player_night_mode_on else Res.string.player_night_mode_off)
                }",
                it,
                icon = Icons.Rounded.NightsStay,
                tint = if (nightModeEnabled) MaterialTheme.colorScheme.primary else Color.White,
            )
        }
        onQualityClick?.let {
            this[PlayerToolbarButton.QUALITY] = PlayerControlAction(
                qualityLabel?.takeIf { label -> label.isNotBlank() }
                    ?: stringResource(Res.string.player_toolbar_button_quality),
                it,
                icon = Icons.Rounded.HighQuality,
            )
        }
        onSourcesClick?.let {
            this[PlayerToolbarButton.SOURCES] = PlayerControlAction(
                stringResource(Res.string.compose_player_sources), it,
                painter = appIconPainter(AppIconResource.PlayerSource),
            )
        }
        onEpisodesClick?.let {
            this[PlayerToolbarButton.EPISODES] = PlayerControlAction(
                stringResource(Res.string.compose_player_episodes), it,
                painter = appIconPainter(AppIconResource.PlayerEpisodes),
            )
        }
        onRateClick?.let {
            this[PlayerToolbarButton.RATE] = PlayerControlAction(
                userRating?.let { rating -> stringResource(Res.string.user_rating_action_rated, rating) }
                    ?: stringResource(Res.string.user_rating_action_rate),
                it,
                icon = if (userRating != null) Icons.Rounded.Star else Icons.Rounded.StarBorder,
            )
        }
        onOpenInExternalPlayer?.let {
            this[PlayerToolbarButton.EXTERNAL_PLAYER] = PlayerControlAction(
                stringResource(Res.string.streams_open_external_player), it,
                icon = Icons.AutoMirrored.Rounded.OpenInNew,
            )
        }
        onInfoClick?.let {
            this[PlayerToolbarButton.PLAYBACK_INFO] = PlayerControlAction(
                stringResource(Res.string.compose_player_playback_info), it,
                icon = Icons.Rounded.Info,
            )
        }
        onSwitchEngineClick?.let {
            this[PlayerToolbarButton.SWITCH_ENGINE] = PlayerControlAction(
                stringResource(Res.string.cd_switch_player_engine), it,
                icon = Icons.Rounded.SwapHoriz,
            )
        }
        onVideoSettingsClick?.let {
            this[PlayerToolbarButton.VIDEO_SETTINGS] = PlayerControlAction(
                stringResource(Res.string.player_action_video_settings), it,
                icon = Icons.Rounded.Build,
            )
        }
        onSubmitIntroClick?.let {
            this[PlayerToolbarButton.SUBMIT_INTRO] = PlayerControlAction(
                stringResource(Res.string.submit_intro_action), it,
                icon = Icons.Rounded.Flag,
            )
        }
    }
    val playerSettings by PlayerSettingsRepository.uiState.collectAsState()
    val toolbarLayout = playerSettings.toolbarLayout
    val primaryActions = toolbarLayout.visibleButtons.mapNotNull(availableActions::get)
    val secondaryActions = toolbarLayout.hiddenButtons.mapNotNull(availableActions::get)
    val hasOverflow = secondaryActions.isNotEmpty()
    var expanded by remember(hasOverflow) { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    LaunchedEffect(expanded, scrollState.maxValue) {
        if (expanded) scrollState.animateScrollTo(scrollState.maxValue) else scrollState.scrollTo(0)
    }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = PlayerTimelineContentInset),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier
                        .playerLiquidGlass(
                            shape = PlayerGlassCapsule,
                            refractionHeight = 8.dp,
                            refractionAmount = 12.dp,
                        )
                        .horizontalScroll(scrollState),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    (if (expanded) primaryActions + secondaryActions else primaryActions).forEach { action ->
                        Box {
                            PlayerAction(
                                description = action.description,
                                onClick = {
                                    onInteraction()
                                    action.onClick()
                                },
                                icon = action.icon,
                                painter = action.painter,
                                iconSize = action.iconSize,
                                tint = action.tint,
                            )
                            action.popup?.invoke()
                        }
                    }
                    if (hasOverflow) {
                        PlayerAction(
                            description = stringResource(
                                if (expanded) Res.string.compose_player_fewer_actions else Res.string.compose_player_more_actions,
                            ),
                            onClick = {
                                expanded = !expanded
                                onInteraction()
                            },
                            icon = if (expanded) Icons.AutoMirrored.Rounded.KeyboardArrowLeft else Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .height(44.dp)
                    .widthIn(min = 48.dp)
                    .playerLiquidGlass(
                        shape = PlayerGlassCapsule,
                        refractionHeight = 6.dp,
                        refractionAmount = 8.dp,
                    )
                    .clickable(
                        role = Role.Button,
                        onClickLabel = stringResource(
                            if (showRemainingTime) Res.string.compose_player_show_elapsed_time else Res.string.compose_player_show_remaining_time,
                        ),
                        onClick = {
                            onRuntimeClick()
                            onInteraction()
                        },
                    )
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = formatPlaybackRuntime(displayedPositionMs, playbackSnapshot.durationMs, showRemainingTime),
                    style = MaterialTheme.nuvioTypeScale.bodyMd.copy(fontSize = (metrics.timeSize.value + 2).sp),
                    color = Color.White.copy(alpha = 0.9f),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun PlayerAction(
    description: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    painter: Painter? = null,
    iconSize: Dp = 24.dp,
    tint: Color = Color.White,
) {
    val resolvedTint = if (tint == Color.White) liquidGlassIconTint(LiquidGlassSurface.PLAYER, tint) else tint
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
        if (painter != null) {
            Icon(painter, description, tint = resolvedTint, modifier = Modifier.size(iconSize))
        } else if (icon != null) {
            Icon(icon, description, tint = resolvedTint, modifier = Modifier.size(iconSize))
        }
    }
}

internal val PlayerGlassCapsule = RoundedCornerShape(percent = 50)

// Upstream's flat action list, used by the fold layout (FoldControls). The regular toolbar
// builds its actions from the fork's customizable toolbar layout above.
@Composable
internal fun playerControlActions(
    playbackSnapshot: PlayerPlaybackSnapshot,
    resizeMode: PlayerResizeMode,
    onSubtitleClick: () -> Unit,
    onAudioClick: () -> Unit,
    onSourcesClick: (() -> Unit)?,
    onEpisodesClick: (() -> Unit)?,
    onChaptersClick: (() -> Unit)?,
    onNextEpisodeClick: (() -> Unit)?,
    onSpeedClick: () -> Unit,
    onResizeModeClick: () -> Unit,
    onVideoSettingsClick: (() -> Unit)?,
    onOpenInExternalPlayer: (() -> Unit)?,
    onStreamInfoClick: () -> Unit,
    onSubmitIntroClick: (() -> Unit)?,
): List<PlayerControlAction> =
    listOfNotNull(
        onNextEpisodeClick?.let {
            PlayerControlAction(
                stringResource(Res.string.player_next_episode), it,
                icon = Icons.Rounded.SkipNext, iconSize = 40.dp,
            )
        },
        PlayerControlAction(
            stringResource(Res.string.compose_player_subtitles), onSubtitleClick,
            painter = appIconPainter(AppIconResource.PlayerSubtitles),
        ),
        PlayerControlAction(
            stringResource(Res.string.compose_player_audio), onAudioClick,
            painter = appIconPainter(AppIconResource.PlayerAudioFilled),
        ),
        onChaptersClick?.let {
            PlayerControlAction(
                stringResource(Res.string.compose_player_chapters), it,
                icon = Icons.AutoMirrored.Rounded.Toc,
            )
        },
        onSourcesClick?.let {
            PlayerControlAction(
                stringResource(Res.string.compose_player_sources), it,
                painter = appIconPainter(AppIconResource.PlayerSource),
            )
        },
        onEpisodesClick?.let {
            PlayerControlAction(
                stringResource(Res.string.compose_player_episodes), it,
                painter = appIconPainter(AppIconResource.PlayerEpisodes),
            )
        },
        PlayerControlAction(
            "${stringResource(Res.string.compose_player_speed)} ${formatPlaybackSpeedLabel(playbackSnapshot.playbackSpeed)}",
            onSpeedClick, icon = Icons.Rounded.Speed,
        ),
        PlayerControlAction(
            stringResource(resizeMode.labelRes), onResizeModeClick,
            painter = appIconPainter(AppIconResource.PlayerAspectRatio),
        ),
        onOpenInExternalPlayer?.let {
            PlayerControlAction(
                stringResource(Res.string.streams_open_external_player), it,
                icon = Icons.AutoMirrored.Rounded.OpenInNew,
            )
        },
        PlayerControlAction(
            stringResource(Res.string.cd_stream_info), onStreamInfoClick,
            icon = Icons.Rounded.Info,
        ),
        onVideoSettingsClick?.let {
            PlayerControlAction(
                stringResource(Res.string.player_action_video_settings), it,
                icon = Icons.Rounded.Build,
            )
        },
        onSubmitIntroClick?.let {
            PlayerControlAction(
                stringResource(Res.string.submit_intro_action), it,
                icon = Icons.Rounded.Flag,
            )
        },
    )

internal data class PlayerControlAction(
    val description: String,
    val onClick: () -> Unit,
    val icon: ImageVector? = null,
    val painter: Painter? = null,
    val iconSize: Dp = 24.dp,
    val popup: (@Composable () -> Unit)? = null,
    val tint: Color = Color.White,
)
