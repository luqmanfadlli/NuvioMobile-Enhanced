package com.nuvio.app.features.player

import androidx.compose.foundation.focusable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

/**
 * A hardware-keyboard action the player understands, independent of which layer recognised the
 * key. Compose maps key events to these directly; on iOS the mpv view controller maps its own
 * `UIPress` events to [wireCode] and hands them back, so both paths end up in the same runtime
 * actions instead of each driving the player on its own.
 */
enum class PlayerKeyboardShortcut(val wireCode: String) {
    TogglePlayback("toggle_playback"),
    SeekBackward("seek_backward"),
    SeekForward("seek_forward"),
    Exit("exit"),
    ;

    companion object {
        fun fromWireCode(code: String): PlayerKeyboardShortcut? =
            entries.firstOrNull { it.wireCode == code }
    }
}

/**
 * Handles hardware-keyboard shortcuts on the player surface.
 *
 * The modifier makes the node focusable and holds [focusRequester], because Compose only delivers
 * key events to the focused subtree and overlays take focus away while they are open.
 *
 * [enabledState] is read inside the handler rather than captured, so toggling it never rebuilds
 * the modifier chain — a rebuild would drop focus mid-playback. While it is false the handler
 * returns false, leaving the keys to whatever overlay currently owns them (a subtitle search
 * field, for instance).
 */
internal fun Modifier.playerKeyboardShortcuts(
    focusRequester: FocusRequester,
    enabledState: State<Boolean>,
    onShortcutState: State<(PlayerKeyboardShortcut) -> Unit>,
): Modifier =
    focusRequester(focusRequester)
        .focusable()
        .onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown || !enabledState.value) return@onPreviewKeyEvent false
            val shortcut = event.key.toPlayerKeyboardShortcut() ?: return@onPreviewKeyEvent false
            onShortcutState.value(shortcut)
            true
        }

private fun Key.toPlayerKeyboardShortcut(): PlayerKeyboardShortcut? = when (this) {
    Key.Spacebar -> PlayerKeyboardShortcut.TogglePlayback
    Key.DirectionLeft -> PlayerKeyboardShortcut.SeekBackward
    Key.DirectionRight -> PlayerKeyboardShortcut.SeekForward
    Key.Escape -> PlayerKeyboardShortcut.Exit
    else -> null
}
