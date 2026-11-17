@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nuvio.app.features.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.stringWithContentsOfURL
import platform.Foundation.writeToFile
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerMode
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.darwin.NSObject

@Composable
internal actual fun rememberSettingsConfigFileLauncher(
    onSaved: () -> Unit,
    onSaveFailed: () -> Unit,
    onLoaded: (content: String) -> Unit,
    onLoadFailed: () -> Unit,
): SettingsConfigFileLauncher {
    val currentOnSaved by rememberUpdatedState(onSaved)
    val currentOnSaveFailed by rememberUpdatedState(onSaveFailed)
    val currentOnLoaded by rememberUpdatedState(onLoaded)
    val currentOnLoadFailed by rememberUpdatedState(onLoadFailed)
    val delegateHolder = remember { mutableStateOf<SettingsConfigDocumentPickerDelegate?>(null) }

    return remember {
        SettingsConfigFileLauncher(
            launchSaver = launch@{ fileName, content ->
                val presenter = settingsConfigTopViewController()
                val path = NSTemporaryDirectory().trimEnd('/') + "/" + fileName
                if (presenter == null || !writeUtf8File(path, content)) {
                    currentOnSaveFailed()
                    return@launch
                }
                val fileUrl = NSURL.fileURLWithPath(path)
                val picker = UIDocumentPickerViewController(
                    uRL = fileUrl,
                    inMode = UIDocumentPickerMode.UIDocumentPickerModeExportToService,
                )
                val delegate = SettingsConfigDocumentPickerDelegate(
                    onPicked = {
                        NSFileManager.defaultManager.removeItemAtPath(path, null)
                        delegateHolder.value = null
                        currentOnSaved()
                    },
                    onDismissed = {
                        NSFileManager.defaultManager.removeItemAtPath(path, null)
                        delegateHolder.value = null
                    },
                )
                delegateHolder.value = delegate
                picker.delegate = delegate
                presenter.presentViewController(picker, animated = true, completion = null)
            },
            launchPicker = launch@{
                val presenter = settingsConfigTopViewController()
                if (presenter == null) {
                    currentOnLoadFailed()
                    return@launch
                }
                val picker = UIDocumentPickerViewController(
                    documentTypes = listOf(
                        "public.json",
                        "public.plain-text",
                        "public.text",
                        "public.data",
                    ),
                    inMode = UIDocumentPickerMode.UIDocumentPickerModeImport,
                )
                val delegate = SettingsConfigDocumentPickerDelegate(
                    onPicked = { url ->
                        val didStartAccess = url.startAccessingSecurityScopedResource()
                        try {
                            val content = readUtf8File(url)
                            if (content.isNullOrBlank()) currentOnLoadFailed() else currentOnLoaded(content)
                        } catch (_: Throwable) {
                            currentOnLoadFailed()
                        } finally {
                            if (didStartAccess) url.stopAccessingSecurityScopedResource()
                            delegateHolder.value = null
                        }
                    },
                    onDismissed = { delegateHolder.value = null },
                )
                delegateHolder.value = delegate
                picker.delegate = delegate
                presenter.presentViewController(picker, animated = true, completion = null)
            },
        )
    }
}

private fun writeUtf8File(path: String, content: String): Boolean = runCatching {
    NSString.create(string = content).writeToFile(path, true, NSUTF8StringEncoding, null)
}.getOrDefault(false)

private fun readUtf8File(url: NSURL): String? =
    NSString.stringWithContentsOfURL(url, NSUTF8StringEncoding, null)?.removePrefix("\uFEFF")

private class SettingsConfigDocumentPickerDelegate(
    private val onPicked: (NSURL) -> Unit,
    private val onDismissed: () -> Unit,
) : NSObject(), UIDocumentPickerDelegateProtocol {
    override fun documentPicker(
        controller: UIDocumentPickerViewController,
        didPickDocumentsAtURLs: List<*>,
    ) {
        val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL
        if (url != null) {
            onPicked(url)
        } else {
            onDismissed()
        }
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        onDismissed()
    }
}

private fun settingsConfigTopViewController(): UIViewController? {
    var controller = UIApplication.sharedApplication.keyWindow?.rootViewController
    while (controller?.presentedViewController != null) {
        controller = controller.presentedViewController
    }
    return controller
}
