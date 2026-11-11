package com.nuvio.app.features.livetv

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSFileManager
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.NSUUID
import platform.Foundation.downloadTaskWithRequest
import platform.Foundation.setValue
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import platform.posix.remove
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@OptIn(ExperimentalForeignApi::class)
internal actual suspend fun liveTvStreamGuideFromDisk(
    url: String,
    headers: Map<String, String>,
    onChunk: (bytes: ByteArray, count: Int) -> Boolean,
): Boolean {
    val path = downloadToTemporaryFile(url, headers)
    try {
        val file = fopen(path, "rb") ?: error("Unable to read the downloaded guide.")
        try {
            val buffer = ByteArray(64 * 1024)
            buffer.usePinned { pinned ->
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = fread(pinned.addressOf(0), 1.convert(), buffer.size.convert(), file).toInt()
                    if (count <= 0 || !onChunk(buffer, count)) break
                }
            }
        } finally {
            fclose(file)
        }
    } finally {
        remove(path)
    }
    return true
}

@OptIn(ExperimentalForeignApi::class)
private suspend fun downloadToTemporaryFile(url: String, headers: Map<String, String>): String =
    suspendCancellableCoroutine { continuation ->
        val address = NSURL.URLWithString(url)
        if (address == null) {
            continuation.resumeWithException(IllegalArgumentException("The guide address is not valid."))
            return@suspendCancellableCoroutine
        }
        val request = NSMutableURLRequest.requestWithURL(address)
        request.setTimeoutInterval(60.0)
        headers.forEach { (name, value) -> request.setValue(value, forHTTPHeaderField = name) }
        val task = NSURLSession.sharedSession.downloadTaskWithRequest(request) { location, response, error ->
            val status = (response as? NSHTTPURLResponse)?.statusCode?.toInt()
            when {
                error != null ->
                    continuation.resumeWithException(IllegalStateException(error.localizedDescription))
                location == null ->
                    continuation.resumeWithException(IllegalStateException("The guide could not be downloaded."))
                status != null && status !in 200..299 ->
                    continuation.resumeWithException(IllegalStateException("Guide request failed with HTTP $status"))
                else -> {
                    // The system deletes its own file when this handler returns, so it is moved first.
                    val target = NSTemporaryDirectory() + "guide-" + NSUUID().UUIDString + ".tmp"
                    val moved = NSFileManager.defaultManager.moveItemAtURL(location, NSURL.fileURLWithPath(target), null)
                    when {
                        !moved -> continuation.resumeWithException(IllegalStateException("The guide could not be saved."))
                        continuation.isActive -> continuation.resume(target)
                        else -> remove(target)
                    }
                }
            }
        }
        continuation.invokeOnCancellation { task.cancel() }
        task.resume()
    }
