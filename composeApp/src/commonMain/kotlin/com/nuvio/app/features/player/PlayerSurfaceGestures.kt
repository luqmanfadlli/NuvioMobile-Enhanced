package com.nuvio.app.features.player

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import com.nuvio.app.core.logging.InAppLogger
import kotlin.math.abs
import kotlin.math.roundToLong

internal fun Modifier.playerSurfaceTapGestures(
    layoutSize: IntSize,
    playbackGesturesEnabled: Boolean,
    playerControlsLockedState: State<Boolean>,
    onSurfaceTap: State<(Offset) -> Unit>,
    onSurfaceDoubleTap: State<(Offset) -> Unit>,
    activateHoldToSpeedState: State<() -> Unit>,
    deactivateHoldToSpeedState: State<() -> Unit>,
    revealLockedOverlayState: State<() -> Unit>,
): Modifier =
    pointerInput(layoutSize, playbackGesturesEnabled) {
        detectTapGestures(
            onPress = {
                tryAwaitRelease()
                deactivateHoldToSpeedState.value()
            },
            onTap = { offset -> onSurfaceTap.value(offset) },
            onDoubleTap = if (playbackGesturesEnabled) {
                { offset -> onSurfaceDoubleTap.value(offset) }
            } else null,
            onLongPress = if (playbackGesturesEnabled) {
                {
                    if (playerControlsLockedState.value) {
                        revealLockedOverlayState.value()
                    } else {
                        activateHoldToSpeedState.value()
                    }
                }
            } else null,
        )
    }

internal fun Modifier.playerSurfaceDragGestures(
    gestureController: PlayerGestureController?,
    playerController: PlayerEngineController?,
    layoutSize: IntSize,
    playbackGesturesEnabled: Boolean,
    sideGestureSystemEdgeExclusionPx: Float,
    horizontalSeekSystemEdgeExclusionPx: Float = 0f,
    playerControlsLockedState: State<Boolean>,
    touchGesturesEnabledState: State<Boolean>,
    swipeToSeekEnabledState: State<Boolean>,
    isHoldToSpeedGestureActiveState: State<Boolean>,
    currentPositionMsState: State<Long>,
    currentDurationMsState: State<Long>,
    deactivateHoldToSpeedState: State<() -> Unit>,
    showHorizontalSeekPreviewState: State<(Long, Long) -> Unit>,
    showBrightnessFeedbackState: State<(Float) -> Unit>,
    showVolumeFeedbackState: State<(PlayerAudioLevel) -> Unit>,
    clearLiveGestureFeedbackState: State<() -> Unit>,
    revealLockedOverlayState: State<() -> Unit>,
    commitHorizontalSeekState: State<(Long) -> Unit>,
): Modifier =
    pointerInput(
        gestureController,
        playerController,
        layoutSize,
        sideGestureSystemEdgeExclusionPx,
        horizontalSeekSystemEdgeExclusionPx,
        playbackGesturesEnabled,
    ) {
        if (!playbackGesturesEnabled) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown()
            if (playerControlsLockedState.value) {
                return@awaitEachGesture
            }
            if (!touchGesturesEnabledState.value) {
                return@awaitEachGesture
            }
            val controller = gestureController
            val width = size.width.toFloat().takeIf { it > 0f } ?: return@awaitEachGesture
            val height = size.height.toFloat().takeIf { it > 0f } ?: return@awaitEachGesture
            val sideGestureEdgeExclusionPx = sideGestureSystemEdgeExclusionPx
                .coerceAtMost(height * 0.25f)
            val isInSideGestureSystemEdge =
                down.position.y <= sideGestureEdgeExclusionPx ||
                    down.position.y >= height - sideGestureEdgeExclusionPx
            // A swipe that starts inside the system back gesture zone belongs to the OS.
            // Starting a seek there would fight the back gesture and, when the system
            // cancels the touch stream, the seek would still be committed on "release".
            val backGestureEdgePx = horizontalSeekSystemEdgeExclusionPx.coerceAtMost(width * 0.25f)
            val isInBackGestureEdge = backGestureEdgePx > 0f &&
                (down.position.x <= backGestureEdgePx || down.position.x >= width - backGestureEdgePx)
            val region = when {
                isInSideGestureSystemEdge -> null
                down.position.x < width * PlayerLeftGestureBoundary -> PlayerSideGesture.Brightness
                down.position.x > width * PlayerRightGestureBoundary -> PlayerSideGesture.Volume
                else -> null
            }

            val initialBrightness = if (region == PlayerSideGesture.Brightness) {
                controller?.currentBrightness()
            } else {
                null
            }
            val initialVolume = if (region == PlayerSideGesture.Volume) {
                currentCombinedVolumeLevel(
                    systemVolume = controller?.currentVolume(),
                    softwareVolume = playerController?.currentPlayerVolume(),
                )
            } else {
                null
            }

            var totalDx = 0f
            var totalDy = 0f
            var gestureMode: PlayerGestureMode? = null
            var verticalGestureActivationDy = 0f
            val horizontalSeekBaselineMs = currentPositionMsState.value
            var horizontalSeekPreviewMs = horizontalSeekBaselineMs
            var lastVolumeTarget: Float? = null
            var interruptedByPinch = false

            while (true) {
                val event = awaitPointerEvent()
                if (event.changes.count { it.pressed } > 1) {
                    interruptedByPinch = true
                    break
                }
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break

                val delta = change.position - change.previousPosition
                totalDx += delta.x
                totalDy += delta.y

                if (gestureMode == null) {
                    val holdToSpeedActive = isHoldToSpeedGestureActiveState.value
                    val verticalGestureActivationSlop = maxOf(
                        viewConfiguration.touchSlop * PlayerVerticalGestureTouchSlopMultiplier,
                        height * PlayerVerticalGestureMinHeightFraction,
                    )
                    val horizontalGestureActivationSlop =
                        viewConfiguration.touchSlop * PlayerHorizontalGestureTouchSlopMultiplier
                    val horizontalDominant =
                        !holdToSpeedActive &&
                            swipeToSeekEnabledState.value &&
                            !isInBackGestureEdge &&
                            abs(totalDx) > horizontalGestureActivationSlop &&
                            abs(totalDx) > abs(totalDy) * PlayerHorizontalGestureDominanceRatio
                    val verticalDominant =
                        !holdToSpeedActive &&
                            abs(totalDy) > verticalGestureActivationSlop &&
                            abs(totalDy) > abs(totalDx) * PlayerVerticalGestureDominanceRatio

                    gestureMode = when {
                        horizontalDominant -> {
                            deactivateHoldToSpeedState.value()
                            PlayerGestureMode.HorizontalSeek
                        }

                        verticalDominant && region == PlayerSideGesture.Brightness && initialBrightness != null -> {
                            verticalGestureActivationDy = totalDy
                            PlayerGestureMode.Brightness
                        }

                        verticalDominant && region == PlayerSideGesture.Volume && initialVolume != null -> {
                            verticalGestureActivationDy = totalDy
                            PlayerGestureMode.Volume
                        }

                        else -> null
                    }

                    if (gestureMode == null) {
                        // A moving finger is not a press-and-hold. Consuming the movement
                        // cancels the pending long press in playerSurfaceTapGestures, so a
                        // slow system back swipe from the edge does not trigger 2x speed.
                        val touchSlop = viewConfiguration.touchSlop
                        if (!holdToSpeedActive &&
                            totalDx * totalDx + totalDy * totalDy > touchSlop * touchSlop
                        ) {
                            change.consume()
                        }
                        continue
                    }
                }

                when (gestureMode) {
                    PlayerGestureMode.HorizontalSeek -> {
                        val sensitivitySeconds = when {
                            currentDurationMsState.value >= 3_600_000L -> 120f
                            currentDurationMsState.value >= 1_800_000L -> 90f
                            else -> 60f
                        }
                        val previewOffsetMs =
                            ((totalDx / width) * sensitivitySeconds * 1000f).roundToLong()
                        val unclampedPreviewMs = horizontalSeekBaselineMs + previewOffsetMs
                        horizontalSeekPreviewMs = currentDurationMsState.value
                            .takeIf { it > 0L }
                            ?.let { durationMs ->
                                unclampedPreviewMs.coerceIn(0L, durationMs)
                            }
                            ?: unclampedPreviewMs.coerceAtLeast(0L)
                        showHorizontalSeekPreviewState.value(
                            horizontalSeekPreviewMs,
                            horizontalSeekBaselineMs,
                        )
                    }

                    PlayerGestureMode.Brightness -> {
                        val activeTotalDy = totalDy - verticalGestureActivationDy
                        val gestureDeltaFraction =
                            (-activeTotalDy / height) * PlayerVerticalGestureSensitivity
                        controller?.setBrightness((initialBrightness ?: 0f) + gestureDeltaFraction)
                            ?.let(showBrightnessFeedbackState.value)
                    }

                    PlayerGestureMode.Volume -> {
                        val activeTotalDy = totalDy - verticalGestureActivationDy
                        val gestureDeltaFraction =
                            (-activeTotalDy / height) * PlayerVerticalGestureSensitivity
                        val target = ((initialVolume?.fraction ?: 0f) + gestureDeltaFraction)
                            .coerceIn(0f, PlayerMaxVolumeBoost)
                        lastVolumeTarget = target
                        setCombinedVolumeLevel(
                            target = target,
                            systemController = controller,
                            engineController = playerController,
                        )?.let(showVolumeFeedbackState.value)
                    }
                }
                change.consume()
            }

            lastVolumeTarget?.let { target ->
                val system = target.coerceAtMost(PlayerNormalVolumeCeiling)
                val software = target.coerceAtLeast(PlayerNormalVolumeCeiling)
                InAppLogger.debug(
                    "Player/Gesture",
                    "volume gesture end target=${formatVolumeFraction(target)} " +
                        "system=${formatVolumeFraction(system)} software=${formatVolumeFraction(software)}",
                )
            }

            if (interruptedByPinch) {
                if (gestureMode == PlayerGestureMode.HorizontalSeek) clearLiveGestureFeedbackState.value()
                return@awaitEachGesture
            }

            if (gestureMode == PlayerGestureMode.HorizontalSeek && !isHoldToSpeedGestureActiveState.value) {
                commitHorizontalSeekState.value(horizontalSeekPreviewMs)
                clearLiveGestureFeedbackState.value()
            }
        }
    }

internal fun Modifier.playerSurfacePinchGestures(
    playbackGesturesEnabled: Boolean,
    playerControlsLockedState: State<Boolean>,
    touchGesturesEnabledState: State<Boolean>,
    onPinchZoomState: State<(zoomFactor: Float) -> Unit>,
): Modifier =
    pointerInput(playbackGesturesEnabled) {
        if (!playbackGesturesEnabled) return@pointerInput
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            do {
                val event = awaitPointerEvent()
                val multiTouch = event.changes.count { it.pressed } > 1
                if (multiTouch && !playerControlsLockedState.value && touchGesturesEnabledState.value) {
                    val zoom = event.calculateZoom()
                    if (zoom != 1f) onPinchZoomState.value(zoom)
                    event.changes.forEach { it.consume() }
                }
            } while (event.changes.any { it.pressed })
        }
    }

private fun formatVolumeFraction(value: Float): String {
    val hundredths = (value * 100f).roundToLong()
    return "${hundredths / 100}.${(hundredths % 100).toString().padStart(2, '0')}"
}
