package com.nuvio.app.features.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.nuvio.app.features.downloads.createDownloadLocationBookmarkBase64
import com.nuvio.app.features.downloads.resolveDownloadLocationBookmark
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerMode
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.darwin.NSObject

@OptIn(ExperimentalForeignApi::class)
@Composable
internal actual fun DownloadLocationPicker(
    onLocationSelected: (uri: String) -> Unit,
    onDismiss: () -> Unit,
) {
    // UIDocumentPickerViewController's delegate property is weak — hold a strong reference for
    // as long as the picker can still call back, or it may be deallocated before it does.
    val delegateHolder = remember { mutableStateOf<DownloadLocationDocumentPickerDelegate?>(null) }

    LaunchedEffect(Unit) {
        val presenter = topmostViewController()
        if (presenter == null) {
            onDismiss()
            return@LaunchedEffect
        }

        val picker = UIDocumentPickerViewController(
            documentTypes = listOf("public.folder"),
            inMode = UIDocumentPickerMode.UIDocumentPickerModeOpen,
        )
        val delegate = DownloadLocationDocumentPickerDelegate(
            onPicked = { url ->
                delegateHolder.value = null
                val bookmark = createDownloadLocationBookmarkBase64(url)
                if (bookmark != null) onLocationSelected(bookmark) else onDismiss()
            },
            onDismissed = {
                delegateHolder.value = null
                onDismiss()
            },
        )
        delegateHolder.value = delegate
        picker.delegate = delegate
        presenter.presentViewController(picker, animated = true, completion = null)
    }
}

internal actual fun formatUriForDisplay(uri: String): String {
    val url = resolveDownloadLocationBookmark(uri) ?: return uri
    return url.lastPathComponent ?: uri
}

private class DownloadLocationDocumentPickerDelegate(
    private val onPicked: (NSURL) -> Unit,
    private val onDismissed: () -> Unit,
) : NSObject(), UIDocumentPickerDelegateProtocol {
    override fun documentPicker(
        controller: UIDocumentPickerViewController,
        didPickDocumentsAtURLs: List<*>,
    ) {
        val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL
        if (url != null) onPicked(url) else onDismissed()
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        onDismissed()
    }
}

private fun topmostViewController(): UIViewController? {
    var controller = UIApplication.sharedApplication.keyWindow?.rootViewController
    while (controller?.presentedViewController != null) {
        controller = controller.presentedViewController
    }
    return controller
}
