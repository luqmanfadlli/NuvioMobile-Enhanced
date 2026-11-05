package com.nuvio.app.features.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.*
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

internal data class PlayerSurfaceGestureCallbacks(
    val onSurfaceTap: State<(Offset) -> Unit>,
    val onSurfaceDoubleTap: State<(Offset) -> Unit>,
    val activateHoldToSpeed: State<() -> Unit>,
    val deactivateHoldToSpeed: State<() -> Unit>,
    val showHorizontalSeekPreview: State<(Long, Long) -> Unit>,
    val showBrightnessFeedback: State<(Float) -> Unit>,
    val showVolumeFeedback: State<(PlayerAudioLevel) -> Unit>,
    val clearLiveGestureFeedback: State<() -> Unit>,
    val revealLockedOverlay: State<() -> Unit>,
    val isHoldToSpeedGestureActive: State<Boolean>,
    val touchGesturesEnabled: State<Boolean>,
    val swipeToSeekEnabled: State<Boolean>,
    val playerControlsLocked: State<Boolean>,
    val currentPositionMs: State<Long>,
    val currentDurationMs: State<Long>,
    val commitHorizontalSeek: State<(Long) -> Unit>,
)

internal fun PlayerScreenRuntime.showGestureFeedback(feedback: GestureFeedbackState) {
    gestureMessageJob?.cancel()
    gestureFeedback = feedback
    gestureMessageJob = scope.launch {
        delay(900)
        gestureFeedback = null
    }
}

internal fun PlayerScreenRuntime.showGestureMessage(message: String) {
    showGestureFeedback(GestureFeedbackState(message = message))
}

internal fun PlayerScreenRuntime.clearLiveGestureFeedback() {
    liveGestureFeedback = null
}

internal fun PlayerScreenRuntime.revealLockedOverlay() {
    controlsVisible = false
    lockedOverlayVisible = true
}

internal fun PlayerScreenRuntime.lockPlayerControls() {
    playerControlsLocked = true
    controlsVisible = false
    lockedOverlayVisible = false
    pausedOverlayVisible = false
    isScrubbingTimeline = false
    scrubbingPositionMs = null
    gestureMessageJob?.cancel()
    gestureFeedback = null
    liveGestureFeedback = null
    renderedGestureFeedback = null
    showAudioModal = false
    showSubtitleModal = false
    showVideoSettingsModal = false
    showStreamInfoModal = false
    showUserRatingSheet = false
    showStreamInfo = false
    showPartyPanel = false
    showSourcesPanel = false
    showEpisodesPanel = false
    showChaptersPanel = false
    episodeStreamsPanelState = EpisodeStreamsPanelState()
    PlayerStreamsRepository.clearEpisodeStreams()
}

internal fun PlayerScreenRuntime.unlockPlayerControls() {
    playerControlsLocked = false
    lockedOverlayVisible = false
    controlsVisible = true
}

internal fun PlayerScreenRuntime.showSeekFeedback(direction: PlayerSeekDirection, amountMs: Long) {
    val seconds = amountMs / 1000L
    if (seconds <= 0L) return
    showGestureFeedback(
        GestureFeedbackState(
            messageRes = if (direction == PlayerSeekDirection.Forward) {
                Res.string.compose_player_seek_feedback_forward
            } else {
                Res.string.compose_player_seek_feedback_backward
            },
            messageArgs = listOf(seconds),
            icon = if (direction == PlayerSeekDirection.Forward) {
                GestureFeedbackIcon.SeekForward
            } else {
                GestureFeedbackIcon.SeekBackward
            },
        ),
    )
}

internal fun PlayerScreenRuntime.showHorizontalSeekPreview(previewPositionMs: Long, baselinePositionMs: Long) {
    val deltaMs = previewPositionMs - baselinePositionMs
    val direction = if (deltaMs < 0L) PlayerSeekDirection.Backward else PlayerSeekDirection.Forward
    liveGestureFeedback = GestureFeedbackState(
        message = formatPlaybackTime(previewPositionMs),
        icon = if (direction == PlayerSeekDirection.Forward) {
            GestureFeedbackIcon.SeekForward
        } else {
            GestureFeedbackIcon.SeekBackward
        },
        secondaryMessageRes = if (deltaMs >= 0L) {
            Res.string.compose_player_seek_delta_forward
        } else {
            Res.string.compose_player_seek_delta_backward
        },
        secondaryMessageArgs = listOf((abs(deltaMs) / 1000f).roundToInt()),
        secondaryMessageColor = if (direction == PlayerSeekDirection.Forward) {
            Color(0xFF6EE7A8)
        } else {
            Color(0xFFFF9A76)
        },
    )
}

internal fun PlayerScreenRuntime.showBrightnessFeedback(level: Float) {
    val percentage = (level.coerceIn(0f, 1f) * 100f).roundToInt()
    showGestureFeedback(
        GestureFeedbackState(
            messageRes = Res.string.compose_player_brightness_level,
            messageArgs = listOf("$percentage%"),
            icon = GestureFeedbackIcon.Brightness,
            level = level.coerceIn(0f, 1f),
        ),
    )
}

internal fun PlayerScreenRuntime.showVolumeFeedback(level: PlayerAudioLevel) {
    notifyVolumeLevelForAutoSubtitle(level)
    val normalized = level.fraction.coerceIn(0f, PlayerMaxVolumeBoost)
    val percentage = (normalized * 100f).roundToInt()
    val isBoosted = normalized > PlayerNormalVolumeCeiling
    showGestureFeedback(
        GestureFeedbackState(
            messageRes = if (level.isMuted) {
                Res.string.compose_player_muted
            } else {
                Res.string.compose_player_volume_level
            },
            messageArgs = if (level.isMuted) emptyList() else listOf("$percentage%"),
            icon = if (level.isMuted) GestureFeedbackIcon.VolumeMuted else GestureFeedbackIcon.Volume,
            // Reuse the existing alternate feedback color path to distinguish boosted volume.
            isDanger = level.isMuted || isBoosted,
            level = if (level.isMuted) 0f else level.fraction.coerceIn(0f, 1f),
        ),
    )
}

internal fun PlayerScreenRuntime.togglePlayback() {
    if (isPartyControlLocked) {
        toggleOwnPlayback()
        return
    }
    if (playbackSnapshot.isPlaying) {
        shouldPlay = false
        playerController?.pause()
    } else {
        if (playbackSnapshot.isEnded) {
            playerController?.seekTo(0L)
        }
        shouldPlay = true
        playerController?.play()
    }
    controlsVisible = true
    publishPartyPlayback(positionMs = if (playbackSnapshot.isEnded) 0L else currentPlaybackPositionMs())
}

/** Sets the requested state; system controls may send pause, skip and play in a row. */
internal fun PlayerScreenRuntime.setPlaybackPlaying(playing: Boolean) {
    if (playing == shouldPlay) return
    if (isPartyControlLocked) {
        toggleOwnPlayback()
        return
    }
    val restart = playing && playbackSnapshot.isEnded
    if (restart) playerController?.seekTo(0L)
    shouldPlay = playing
    if (playing) playerController?.play() else playerController?.pause()
    // A seek issued just before may not be reflected in the playback snapshot yet.
    val positionMs = pendingSeekTarget
        ?.takeIf { (_, mark) -> mark.elapsedNow() < PendingSeekWindow }
        ?.first
        ?: currentPlaybackPositionMs()
    publishPartyPlayback(positionMs = if (restart) 0L else positionMs)
}

internal fun PlayerScreenRuntime.seekBy(offsetMs: Long) {
    val fromMs = pendingSeekTarget
        ?.takeIf { (_, mark) -> mark.elapsedNow() < PendingSeekWindow }
        ?.first
        ?: currentPlaybackPositionMs()
    val fromPositionMs = fromMs.coerceAtLeast(0L)
    val requestedMs = (fromMs + offsetMs).coerceAtLeast(0L)
        .let { if (playbackSnapshot.durationMs > 0L) it.coerceAtMost(playbackSnapshot.durationMs) else it }
    val targetMs = if (isPartyControlLocked) ownSeekTarget(requestedMs) ?: return else requestedMs
    lastManualSkipSeekPositions = fromMs to targetMs
    pendingSeekTarget = targetMs to TimeSource.Monotonic.markNow()
    if (isPartySynced) playerController?.seekTo(targetMs) else playerController?.seekBy(offsetMs)
    scheduleProgressSyncAfterSeek()
    publishPartyPlayback(positionMs = targetMs)
    controlsVisible = true
    when {
        offsetMs > 0L -> showSeekFeedback(PlayerSeekDirection.Forward, offsetMs)
        offsetMs < 0L -> {
            showSeekFeedback(PlayerSeekDirection.Backward, abs(offsetMs))
            notifyRewindOccurred(fromPositionMs)
        }
    }
}

/**
 * Single entry point for hardware-keyboard shortcuts, whichever layer recognised the key. Routing
 * through the ordinary runtime actions is what keeps a key press equivalent to the same action
 * from a tap: seek feedback, control reveal and the debounced watch-progress sync all still run.
 */
internal fun PlayerScreenRuntime.handleKeyboardShortcut(shortcut: PlayerKeyboardShortcut) {
    if (playerControlsLocked || isAnyOverlayVisible) return
    when (shortcut) {
        PlayerKeyboardShortcut.TogglePlayback -> togglePlayback()
        PlayerKeyboardShortcut.SeekBackward -> seekBy(-skipStepMs(PlayerSeekDirection.Backward))
        PlayerKeyboardShortcut.SeekForward -> seekBy(skipStepMs(PlayerSeekDirection.Forward))
        PlayerKeyboardShortcut.Exit -> {
            flushWatchProgress()
            args.onBack()
        }
    }
}

internal fun PlayerScreenRuntime.skipStepMs(direction: PlayerSeekDirection): Long =
    when (direction) {
        PlayerSeekDirection.Backward -> playerSettingsUiState.skipBackwardSeconds
        PlayerSeekDirection.Forward -> playerSettingsUiState.skipForwardSeconds
    } * 1_000L

internal fun PlayerScreenRuntime.handleDoubleTapSeek(direction: PlayerSeekDirection) {
    val stepMs = skipStepMs(direction)
    val currentPositionMs = playbackSnapshot.positionMs.coerceAtLeast(0L)
    val currentSeekState = accumulatedSeekState
    val nextState = if (currentSeekState?.direction == direction) {
        currentSeekState.copy(amountMs = currentSeekState.amountMs + stepMs)
    } else {
        PlayerAccumulatedSeekState(
            direction = direction,
            baselinePositionMs = currentPositionMs,
            amountMs = stepMs,
        )
    }
    val maxDurationMs = playbackSnapshot.durationMs.takeIf { it > 0L }
    val requestedPositionMs = when (direction) {
        PlayerSeekDirection.Backward -> {
            (nextState.baselinePositionMs - nextState.amountMs).coerceAtLeast(0L)
        }
        PlayerSeekDirection.Forward -> {
            val unclamped = nextState.baselinePositionMs + nextState.amountMs
            maxDurationMs?.let { unclamped.coerceAtMost(it) } ?: unclamped
        }
    }
    val targetPositionMs = if (isPartyControlLocked) ownSeekTarget(requestedPositionMs) ?: return else requestedPositionMs
    accumulatedSeekState = nextState
    lastManualSkipSeekPositions = currentPositionMs to targetPositionMs
    playerController?.seekTo(targetPositionMs)
    scheduleProgressSyncAfterSeek()
    publishPartyPlayback(positionMs = targetPositionMs)
    showSeekFeedback(direction, nextState.amountMs)
    if (direction == PlayerSeekDirection.Backward) {
        notifyRewindOccurred(nextState.baselinePositionMs)
    }

    accumulatedSeekResetJob?.cancel()
    accumulatedSeekResetJob = scope.launch {
        delay(PlayerDoubleTapSeekResetDelayMs)
        accumulatedSeekState = null
    }
}

internal fun PlayerScreenRuntime.cycleResizeMode() {
    val nextMode = resizeMode.next()
    resizeMode = nextMode
    lastSyncedSettingsResizeMode = nextMode
    PlayerSettingsRepository.setResizeMode(nextMode)
    rawVideoZoom = 1f
    if (videoZoom != 1f) {
        videoZoom = 1f
        playerController?.setVideoZoom(1f)
    }
    showGestureMessage(
        when (nextMode) {
            PlayerResizeMode.Fit -> resizeModeFitLabel
            PlayerResizeMode.Fill -> resizeModeFillLabel
            PlayerResizeMode.Zoom -> resizeModeZoomLabel
        },
    )
    controlsVisible = true
}

internal const val PlayerMinVideoZoom = 0.8f
internal const val PlayerMaxVideoZoom = 2.0f
private const val PlayerVideoZoomSnapRange = 0.04f

internal fun PlayerScreenRuntime.pinchVideoZoom(zoomFactor: Float) {
    if (zoomFactor <= 0f || zoomFactor == 1f) return
    rawVideoZoom = (rawVideoZoom * zoomFactor).coerceIn(PlayerMinVideoZoom, PlayerMaxVideoZoom)
    val next = if (abs(rawVideoZoom - 1f) < PlayerVideoZoomSnapRange) 1f else rawVideoZoom
    if (next == videoZoom) return
    videoZoom = next
    playerController?.setVideoZoom(next)
    showGestureFeedback(
        GestureFeedbackState(
            message = "${(next * 100f).roundToInt()}%",
            icon = GestureFeedbackIcon.VideoZoom,
        ),
    )
}

internal fun PlayerScreenRuntime.setPlaybackSpeedFromPicker(speed: Float) {
    if (partyBlocksControl()) return
    partyRateNudge = null
    playerController?.setPlaybackSpeed(speed)
    publishPartyPlayback(rate = speed)
    showGestureMessage(formatPlaybackSpeedLabel(speed))
}

internal fun PlayerScreenRuntime.cyclePlaybackSpeed() {
    if (partyBlocksControl()) return
    val speeds = listOf(1f, 1.25f, 1.5f, 2f)
    val current = playbackSnapshot.playbackSpeed
    val next = speeds.firstOrNull { it > current + 0.01f } ?: speeds.first()
    partyRateNudge = null
    playerController?.setPlaybackSpeed(next)
    publishPartyPlayback(rate = next)
    showGestureMessage(formatPlaybackSpeedLabel(next))
    controlsVisible = true
}

internal fun PlayerScreenRuntime.activateHoldToSpeed() {
    if (!playerSettingsUiState.holdToSpeedEnabled) return
    if (isPartySynced) return
    val controller = playerController ?: return
    if (speedBoostRestoreSpeed != null) return

    val targetSpeed = playerSettingsUiState.holdToSpeedValue
    val currentSpeed = playbackSnapshot.playbackSpeed
    if (abs(currentSpeed - targetSpeed) < 0.01f) return

    isHoldToSpeedGestureActive = true
    speedBoostRestoreSpeed = currentSpeed
    controller.setPlaybackSpeed(targetSpeed)
    liveGestureFeedback = GestureFeedbackState(
        message = formatPlaybackSpeedLabel(targetSpeed),
        icon = GestureFeedbackIcon.Speed,
    )
    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
}

internal fun PlayerScreenRuntime.deactivateHoldToSpeed() {
    isHoldToSpeedGestureActive = false
    val restoreSpeed = speedBoostRestoreSpeed ?: return
    playerController?.setPlaybackSpeed(restoreSpeed)
    speedBoostRestoreSpeed = null
    liveGestureFeedback = null
}

@Composable
internal fun PlayerScreenRuntime.rememberSurfaceGestureCallbacks(): PlayerSurfaceGestureCallbacks {
    val onSurfaceTap = rememberUpdatedState { offset: Offset ->
        if (playerControlsLocked) {
            revealLockedOverlay()
            return@rememberUpdatedState
        }
        val centerStart = layoutSize.width * PlayerLeftGestureBoundary
        val centerEnd = layoutSize.width * PlayerRightGestureBoundary
        if (controlsVisible && offset.x in centerStart..centerEnd) {
            controlsVisible = false
        } else {
            controlsVisible = !controlsVisible
        }
    }
    val onSurfaceDoubleTap = rememberUpdatedState { offset: Offset ->
        if (playerControlsLocked) {
            revealLockedOverlay()
            return@rememberUpdatedState
        }
        if (!playerSettingsUiState.touchGesturesEnabled) {
            controlsVisible = !controlsVisible
            return@rememberUpdatedState
        }
        when {
            offset.x < layoutSize.width * PlayerLeftGestureBoundary -> {
                handleDoubleTapSeek(PlayerSeekDirection.Backward)
            }
            offset.x > layoutSize.width * PlayerRightGestureBoundary -> {
                handleDoubleTapSeek(PlayerSeekDirection.Forward)
            }
            else -> controlsVisible = !controlsVisible
        }
    }
    return PlayerSurfaceGestureCallbacks(
        onSurfaceTap = onSurfaceTap,
        onSurfaceDoubleTap = onSurfaceDoubleTap,
        activateHoldToSpeed = rememberUpdatedState(::activateHoldToSpeed),
        deactivateHoldToSpeed = rememberUpdatedState(::deactivateHoldToSpeed),
        showHorizontalSeekPreview = rememberUpdatedState(::showHorizontalSeekPreview),
        showBrightnessFeedback = rememberUpdatedState(::showBrightnessFeedback),
        showVolumeFeedback = rememberUpdatedState(::showVolumeFeedback),
        clearLiveGestureFeedback = rememberUpdatedState(::clearLiveGestureFeedback),
        revealLockedOverlay = rememberUpdatedState(::revealLockedOverlay),
        isHoldToSpeedGestureActive = rememberUpdatedState(isHoldToSpeedGestureActive),
        touchGesturesEnabled = rememberUpdatedState(playerSettingsUiState.touchGesturesEnabled),
        swipeToSeekEnabled = rememberUpdatedState(playerSettingsUiState.swipeToSeekEnabled),
        playerControlsLocked = rememberUpdatedState(playerControlsLocked),
        currentPositionMs = remember(this) { derivedStateOf { playbackSnapshot.positionMs.coerceAtLeast(0L) } },
        currentDurationMs = remember(this) { derivedStateOf { playbackSnapshot.durationMs } },
        commitHorizontalSeek = rememberUpdatedState { requestedPositionMs: Long ->
            val targetPositionMs = if (isPartyControlLocked) {
                ownSeekTarget(requestedPositionMs) ?: return@rememberUpdatedState
            } else {
                requestedPositionMs
            }
            val fromPositionMs = playbackSnapshot.positionMs.coerceAtLeast(0L)
            lastManualSkipSeekPositions = playbackSnapshot.positionMs to targetPositionMs
            playerController?.seekTo(targetPositionMs)
            scheduleProgressSyncAfterSeek()
            publishPartyPlayback(positionMs = targetPositionMs)
            if (targetPositionMs < fromPositionMs) {
                notifyRewindOccurred(fromPositionMs)
            }
        },
    )
}

private val PendingSeekWindow = 1_500.milliseconds
