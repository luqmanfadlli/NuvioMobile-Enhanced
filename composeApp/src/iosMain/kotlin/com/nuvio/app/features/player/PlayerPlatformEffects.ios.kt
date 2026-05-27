package com.nuvio.app.features.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Foundation.NSNotificationCenter
import platform.MediaPlayer.MPVolumeView
import platform.UIKit.UIApplication
import platform.UIKit.UIControlEventValueChanged
import platform.UIKit.UIScreen
import platform.UIKit.UISlider

private const val lockPlayerToLandscapeNotification = "NuvioPlayerLockLandscape"
private const val unlockPlayerOrientationNotification = "NuvioPlayerUnlockOrientation"

/**
 * Shared registry — the active mpv bridge publishes itself here so the Compose-side
 * [ManagePlayerPictureInPicture] hook can drive PiP without holding a direct reference to
 * the bridge.
 */
internal object IosPictureInPictureSession {
    private val activeBridgeState = MutableStateFlow<NuvioPlayerBridge?>(null)
    private val isActiveState = MutableStateFlow(false)

    val isActive: StateFlow<Boolean> = isActiveState.asStateFlow()

    private val listener = object : PictureInPictureStateListener {
        override fun onPictureInPictureActiveChanged(active: Boolean) {
            isActiveState.value = active
        }
    }

    fun registerBridge(bridge: NuvioPlayerBridge) {
        activeBridgeState.value?.setPictureInPictureStateListener(null)
        activeBridgeState.value = bridge
        bridge.setPictureInPictureStateListener(listener)
        isActiveState.value = bridge.isPictureInPictureActive()
    }

    fun unregisterBridge(bridge: NuvioPlayerBridge) {
        if (activeBridgeState.value === bridge) {
            bridge.setPictureInPictureStateListener(null)
            activeBridgeState.value = null
            isActiveState.value = false
        }
    }

    fun start() {
        activeBridgeState.value?.startPictureInPicture()
    }
}

@Composable
actual fun LockPlayerToLandscape() {
    DisposableEffect(Unit) {
        NSNotificationCenter.defaultCenter.postNotificationName(
            lockPlayerToLandscapeNotification,
            null,
        )

        onDispose {
            NSNotificationCenter.defaultCenter.postNotificationName(
                unlockPlayerOrientationNotification,
                null,
            )
        }
    }
}

@Composable
actual fun EnterImmersivePlayerMode(keepScreenAwake: Boolean) {
    SideEffect {
        UIApplication.sharedApplication.setIdleTimerDisabled(keepScreenAwake)
    }

    DisposableEffect(Unit) {
        onDispose {
            UIApplication.sharedApplication.setIdleTimerDisabled(false)
        }
    }
}

@Composable
actual fun ManagePlayerPictureInPicture(
    isPlaying: Boolean,
    playerSize: IntSize,
): PlayerPictureInPictureController {
    var active by remember { mutableStateOf(IosPictureInPictureSession.isActive.value) }

    LaunchedEffect(Unit) {
        IosPictureInPictureSession.isActive.collect { value -> active = value }
    }

    // iOS apps cannot programmatically background themselves, so a manual PiP button
    // can't deliver the Android-style "tap to minimize + float" UX. AVKit's inline
    // auto-start (canStartPictureInPictureAutomaticallyFromInline = true) handles PiP
    // when the user swipes home, which is the native iOS pattern. Report unsupported
    // so the player header hides the button; isActive still tracks system PiP state
    // so the rest of the UI reacts when the floating window appears.
    return remember(active) {
        object : PlayerPictureInPictureController {
            override val isSupported: Boolean = false
            override val isActive: Boolean = active
            override fun enter() {
                IosPictureInPictureSession.start()
            }
        }
    }
}

@Composable
actual fun rememberPlayerGestureController(): PlayerGestureController? {
    val controller = remember { IOSPlayerGestureController() }

    DisposableEffect(controller) {
        onDispose {
            controller.restoreBrightness()
        }
    }

    return controller
}

private class IOSPlayerGestureController : PlayerGestureController {
    private val volumeView = MPVolumeView().apply {
        hidden = true
        alpha = 0.01
    }
    private val originalBrightness = UIScreen.mainScreen.brightness
    private var brightnessRestored = false

    override fun currentBrightness(): Float =
        UIScreen.mainScreen.brightness.toFloat().coerceIn(0.02f, 1f)

    override fun setBrightness(level: Float): Float {
        val target = level.coerceIn(0.02f, 1f)
        UIScreen.mainScreen.brightness = target.toDouble()
        return target
    }

    override fun currentVolume(): PlayerAudioLevel {
        val current = (volumeView.subviews.filterIsInstance<UISlider>().firstOrNull()?.value ?: 0f)
            .coerceIn(0f, 1f)
        return PlayerAudioLevel(
            fraction = current,
            isMuted = current <= 0.001f,
        )
    }

    override fun setVolume(level: Float): PlayerAudioLevel {
        val target = level.coerceIn(0f, 1f)
        val slider = volumeView.subviews.filterIsInstance<UISlider>().firstOrNull()
            ?: return currentVolume()
        slider.value = target
        slider.sendActionsForControlEvents(UIControlEventValueChanged)
        return PlayerAudioLevel(
            fraction = target,
            isMuted = target <= 0.001f,
        )
    }

    fun restoreBrightness() {
        if (brightnessRestored) return
        brightnessRestored = true
        UIScreen.mainScreen.brightness = originalBrightness
    }
}
