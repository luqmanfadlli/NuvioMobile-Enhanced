package com.nuvio.app.core.ui

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalWindowInfo
import kotlinx.coroutines.delay
import androidx.compose.foundation.Indication
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** True on Android TV and Fire TV, where the app is driven with a remote's D-pad instead of touch. */
val LocalIsTv = staticCompositionLocalOf { false }

/** The focus-ring indication on a TV, for controls that otherwise turn the touch ripple off. */
@Composable
internal fun tvFocusIndication(): Indication? = if (LocalIsTv.current) LocalIndication.current else null

/** Held by the control that should get the remote's first focus when the app opens. */
internal val LocalTvInitialFocus = staticCompositionLocalOf { FocusRequester() }

/** Whether this device is a television. Always false off Android. */
@Composable
internal expect fun rememberIsTelevision(): Boolean

/** Gives the app's own view the platform's input focus, which key presses need before Compose can move focus. */
@Composable
internal expect fun rememberRequestPlatformFocus(): () -> Unit

/**
 * On a TV, draws a bright ring around whatever the remote has focused and dims what is being
 * pressed, in place of the touch ripple. Provided as the app-wide indication, so every ordinary
 * clickable picks it up.
 */
private object TvFocusIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = TvFocusNode(interactionSource)
    override fun equals(other: Any?): Boolean = other === TvFocusIndication
    override fun hashCode(): Int = -1
}

private class TvFocusNode(private val source: InteractionSource) : Modifier.Node(), DrawModifierNode {
    private var focused = false
    private var pressed = false

    override fun onAttach() {
        coroutineScope.launch {
            source.interactions.collect { interaction ->
                when (interaction) {
                    is FocusInteraction.Focus -> focused = true
                    is FocusInteraction.Unfocus -> focused = false
                    is PressInteraction.Press -> pressed = true
                    is PressInteraction.Release, is PressInteraction.Cancel -> pressed = false
                    else -> Unit
                }
                invalidateDraw()
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        if (pressed) drawRect(Color.White.copy(alpha = 0.14f))
        if (focused) {
            val stroke = 3.dp.toPx()
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(stroke / 2f, stroke / 2f),
                size = Size(size.width - stroke, size.height - stroke),
                cornerRadius = CornerRadius(12.dp.toPx()),
                style = Stroke(width = stroke),
            )
        }
    }
}

/**
 * Re-applies the TV focus ring beneath a nested [androidx.compose.material3.MaterialTheme], which
 * otherwise puts the touch ripple back for everything inside it.
 */
@Composable
internal fun TvIndicationScope(content: @Composable () -> Unit) {
    if (LocalIsTv.current) {
        CompositionLocalProvider(LocalIndication provides TvFocusIndication, content = content)
    } else {
        content()
    }
}

/**
 * Wraps the app so that, on a TV, [LocalIsTv] is true, focus is drawn for the remote and something
 * always holds focus: the first D-pad press would otherwise do nothing, and so would any press after
 * a screen change takes the focused control away.
 */
@Composable
internal fun ProvideTvSupport(content: @Composable () -> Unit) {
    if (!rememberIsTelevision()) {
        content()
        return
    }
    val focusManager = LocalFocusManager.current
    var hasFocus by remember { mutableStateOf(false) }
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    val requestPlatformFocus = rememberRequestPlatformFocus()
    val initialFocus = remember { FocusRequester() }
    LaunchedEffect(hasFocus, windowFocused) {
        if (hasFocus || !windowFocused) return@LaunchedEffect
        // Keep trying until it takes: early attempts can land before the window or the screen is ready.
        var first = true
        while (true) {
            delay(if (first) 400 else 700)
            first = false
            requestPlatformFocus()
            // Prefer the control that asked to hold the first focus (the selected tab in the rail).
            if (runCatching { initialFocus.requestFocus() }.getOrDefault(false)) break
            if (focusManager.moveFocus(FocusDirection.Next)) break
        }
    }
    CompositionLocalProvider(
        LocalIsTv provides true,
        LocalIndication provides TvFocusIndication,
        LocalTvInitialFocus provides initialFocus,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .onFocusChanged { hasFocus = it.hasFocus }
                .focusGroup(),
        ) {
            content()
        }
    }
}
