@file:OptIn(ExperimentalForeignApi::class)

package com.nuvio.app.core.ui.background

import platform.darwin.DISPATCH_TIME_NOW
import platform.darwin.dispatch_time
import platform.darwin.dispatch_after
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import coil3.request.ImageRequest
import com.nuvio.app.core.storage.ProfileScopedKey
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDomainMask
import platform.Foundation.writeToFile
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIApplication
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIViewController
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

internal actual object AppBackgroundStorage {
    private const val configKey = "enhanced_local_app_background"
    private const val directoryName = "app_background"

    actual fun load(): String? =
        NSUserDefaults.standardUserDefaults.stringForKey(ProfileScopedKey.of(configKey))

    actual fun save(value: String) {
        NSUserDefaults.standardUserDefaults.setObject(value, forKey = ProfileScopedKey.of(configKey))
    }

    internal fun directory(): String {
        val base = NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true)
            .firstOrNull() as? String ?: ""
        val path = "$base/$directoryName"
        NSFileManager.defaultManager.createDirectoryAtPath(
            path,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )
        return path
    }

    actual fun localFilePath(fileName: String): String = "${directory()}/$fileName"

    actual fun deleteLocalFile(fileName: String) {
        NSFileManager.defaultManager.removeItemAtPath(localFilePath(fileName), error = null)
    }
}

internal actual fun ImageRequest.Builder.softwareBitmap(): ImageRequest.Builder = this

private const val GifTypeIdentifier = "com.compuserve.gif"
private const val ImageTypeIdentifier = "public.image"

@Composable
internal actual fun rememberAppBackgroundImagePicker(
    onPicked: (fileName: String) -> Unit,
    onError: (String) -> Unit,
): () -> Unit {
    val delegateHolder = remember { mutableStateOf<AppBackgroundPickerDelegate?>(null) }
    val currentOnPicked = rememberUpdatedState(onPicked)
    val currentOnError = rememberUpdatedState(onError)
    return remember {
        {
            presentWhenReady(attemptsLeft = 10) { presenter ->
                if (presenter == null) {
                    currentOnError.value("Unable to present the photo picker.")
                    return@presentWhenReady
                }
                val configuration = PHPickerConfiguration().apply {
                    filter = PHPickerFilter.imagesFilter
                    selectionLimit = 1
                }
                val picker = PHPickerViewController(configuration = configuration)
                val delegate = AppBackgroundPickerDelegate(
                    onPicked = { name ->
                        delegateHolder.value = null
                        currentOnPicked.value(name)
                    },
                    onError = { message ->
                        delegateHolder.value = null
                        currentOnError.value(message)
                    },
                    onCancelled = { delegateHolder.value = null },
                )
                delegateHolder.value = delegate
                picker.delegate = delegate
                presenter.presentViewController(picker, animated = true, completion = null)
            }
        }
    }
}

private fun presentWhenReady(attemptsLeft: Int, present: (UIViewController?) -> Unit) {
    val top = topViewController()
    val busy = top != null && (top.isBeingDismissed() || top.isBeingPresented())
    if (!busy || attemptsLeft <= 0) {
        present(top)
        return
    }
    dispatch_after(
        dispatch_time(DISPATCH_TIME_NOW, 200_000_000L),
        dispatch_get_main_queue(),
    ) {
        presentWhenReady(attemptsLeft - 1, present)
    }
}

private class AppBackgroundPickerDelegate(
    private val onPicked: (String) -> Unit,
    private val onError: (String) -> Unit,
    private val onCancelled: () -> Unit,
) : NSObject(), PHPickerViewControllerDelegateProtocol {
    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        picker.dismissViewControllerAnimated(true, completion = null)
        val provider = (didFinishPicking.firstOrNull() as? PHPickerResult)?.itemProvider
        if (provider == null) {
            onCancelled()
            return
        }
        val isGif = provider.hasItemConformingToTypeIdentifier(GifTypeIdentifier)
        val typeIdentifier = if (isGif) GifTypeIdentifier else ImageTypeIdentifier
        provider.loadDataRepresentationForTypeIdentifier(typeIdentifier) { data: NSData?, _ ->
            val stored = data?.let { storePickedImage(it, isGif) }
            dispatch_async(dispatch_get_main_queue()) {
                if (stored != null) onPicked(stored) else onError("Failed to load the selected image.")
            }
        }
    }
}

private fun storePickedImage(data: NSData, isGif: Boolean): String? {
    val stamp = NSUUID().UUIDString
    val payload = if (isGif) data else UIImage(data = data).let { UIImageJPEGRepresentation(it, 0.9) } ?: return null
    val name = if (isGif) "bg-$stamp.gif" else "bg-$stamp.jpg"
    return name.takeIf { payload.writeToFile(AppBackgroundStorage.localFilePath(it), atomically = true) }
}

private fun topViewController(): UIViewController? {
    var controller = UIApplication.sharedApplication.keyWindow?.rootViewController
    while (true) {
        val presented = controller?.presentedViewController ?: break
        if (presented.isBeingDismissed()) break
        controller = presented
    }
    return controller
}
