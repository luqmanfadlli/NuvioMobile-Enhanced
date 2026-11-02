package com.nuvio.app.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.uikit.LocalUIViewController
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.Foundation.NSProcessInfo
import platform.UIKit.UIAdaptivePresentationControllerDelegateProtocol
import platform.UIKit.UIAlertAction
import platform.UIKit.UIAlertActionStyleCancel
import platform.UIKit.UIAlertActionStyleDefault
import platform.UIKit.UIAlertController
import platform.UIKit.UIAlertControllerStyleActionSheet
import platform.UIKit.UIAlertControllerStyleAlert
import platform.UIKit.UIDevice
import platform.UIKit.UIPresentationController
import platform.UIKit.UIUserInterfaceIdiomPad
import platform.UIKit.presentationController
import platform.darwin.NSObject

@OptIn(ExperimentalForeignApi::class)
internal actual fun isNativeLiquidGlassOptionMenuAvailable(): Boolean {
    val majorVersion = NSProcessInfo.processInfo.operatingSystemVersion.useContents {
        majorVersion.toLong()
    }
    return shouldUseNativeLiquidGlassOptionMenu(
        operatingSystemMajorVersion = majorVersion,
        surfaceGlassEnabled = true,
    )
}

@Composable
internal actual fun NuvioNativeLiquidGlassOptionMenu(
    visible: Boolean,
    title: String?,
    cancelLabel: String,
    options: List<NativeLiquidGlassMenuOption>,
    selectedOptionId: String?,
    onOptionSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val presenter = LocalUIViewController.current
    val latestOnOptionSelected = rememberUpdatedState(onOptionSelected)
    val latestOnDismiss = rememberUpdatedState(onDismiss)

    DisposableEffect(presenter, visible, title, cancelLabel, options, selectedOptionId) {
        if (!visible || options.isEmpty()) {
            return@DisposableEffect onDispose { }
        }

        val style = if (UIDevice.currentDevice.userInterfaceIdiom == UIUserInterfaceIdiomPad) {
            UIAlertControllerStyleAlert
        } else {
            UIAlertControllerStyleActionSheet
        }
        val alert = UIAlertController.alertControllerWithTitle(
            title = title,
            message = null,
            preferredStyle = style,
        )
        var dismissalStarted = false
        val dismissalDelegate = NativeLiquidGlassOptionMenuDelegate {
            dismissalStarted = true
            latestOnDismiss.value()
        }

        options.forEach { option ->
            val actionTitle = if (option.id == selectedOptionId) {
                "✓ ${option.title}"
            } else {
                option.title
            }
            alert.addAction(
                UIAlertAction.actionWithTitle(
                    title = actionTitle,
                    style = UIAlertActionStyleDefault,
                    handler = { _ ->
                        latestOnOptionSelected.value(option.id)
                        dismissalDelegate.notifyDismissed()
                    },
                ),
            )
        }
        alert.addAction(
            UIAlertAction.actionWithTitle(
                title = cancelLabel,
                style = UIAlertActionStyleCancel,
                handler = { _ -> dismissalDelegate.notifyDismissed() },
            ),
        )
        alert.presentationController()?.delegate = dismissalDelegate
        presenter.presentViewController(
            viewControllerToPresent = alert,
            animated = true,
            completion = null,
        )

        onDispose {
            alert.presentationController()?.delegate = null
            if (!dismissalStarted && alert.presentingViewController != null) {
                alert.dismissViewControllerAnimated(
                    flag = false,
                    completion = null,
                )
            }
        }
    }
}

private class NativeLiquidGlassOptionMenuDelegate(
    private val onDismiss: () -> Unit,
) : NSObject(), UIAdaptivePresentationControllerDelegateProtocol {
    private var didNotifyDismissal = false

    fun notifyDismissed() {
        if (didNotifyDismissal) return
        didNotifyDismissal = true
        onDismiss()
    }

    override fun presentationControllerDidDismiss(presentationController: UIPresentationController) {
        notifyDismissed()
    }
}
