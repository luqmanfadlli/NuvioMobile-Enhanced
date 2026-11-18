package com.nuvio.app.core.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.uikit.LocalUIViewController
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.UIKit.UIViewController
import platform.UIKit.navigationController

internal actual val nuvioPlatformExtraTopPadding: Dp = 0.dp
internal actual val nuvioPlatformExtraBottomPadding: Dp = 0.dp
internal actual val nuvioBottomNavigationExtraVerticalPadding: Dp = 0.dp

@Composable
internal actual fun nuvioBottomNavigationBarInsets(): WindowInsets =
	WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)

@OptIn(ExperimentalForeignApi::class)
@Composable
internal actual fun platformPhysicalTopInset(): Dp {
    val physicalTop = LocalUIViewController.current.view.window
        ?.safeAreaInsets
        ?.useContents { top.toFloat() }

    return physicalTop?.dp
        ?: WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
}

@OptIn(ExperimentalForeignApi::class)
@Composable
internal actual fun platformNativeBarButtonAnchor(): NativeBarButtonAnchor? {
    val controller = LocalUIViewController.current
    val windowSize = LocalWindowInfo.current.containerSize
    val anchor by produceState(controller.nativeBarButtonAnchor(), controller, windowSize) {
        withFrameNanos { }
        value = controller.nativeBarButtonAnchor()
    }
    return anchor
}

@OptIn(ExperimentalForeignApi::class)
private fun UIViewController.nativeBarButtonAnchor(): NativeBarButtonAnchor? {
    val bar = navigationController?.navigationBar ?: return null
    if (bar.hidden) return null
    val centerY = bar.frame.useContents { origin.y + size.height / 2.0 }
    val trailing = bar.directionalLayoutMargins.useContents { trailing }
    return NativeBarButtonAnchor(
        centerY = centerY.toFloat().dp,
        trailingCenterX = (trailing + NativeBarButtonSize / 2.0).toFloat().dp,
    )
}

private const val NativeBarButtonSize = 44.0

@Composable
internal actual fun ResizeForKeyboard() = Unit
