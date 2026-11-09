package com.nuvio.app.features.news

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSURL
import platform.SafariServices.SFSafariViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController

@Composable
internal actual fun rememberInAppBrowser(): (url: String) -> Unit = remember {
    { url ->
        val target = NSURL.URLWithString(url)
        val presenter = topViewController()
        if (target != null && presenter != null) {
            presenter.presentViewController(SFSafariViewController(uRL = target), animated = true, completion = null)
        }
    }
}

private fun topViewController(): UIViewController? {
    var controller = UIApplication.sharedApplication.keyWindow?.rootViewController
    while (controller?.presentedViewController != null) {
        controller = controller.presentedViewController
    }
    return controller
}
