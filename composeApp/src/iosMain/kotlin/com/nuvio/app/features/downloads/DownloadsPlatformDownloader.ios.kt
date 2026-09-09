package com.nuvio.app.features.downloads

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.runBlocking
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.downloads_error_finalize_file_failed
import nuvio.composeapp.generated.resources.network_request_failed_http
import org.jetbrains.compose.resources.getString
import platform.Foundation.NSDate
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSHomeDirectory
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequestReloadIgnoringLocalCacheData
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLSessionDownloadDelegateProtocol
import platform.Foundation.NSURLSessionDownloadTask
import platform.Foundation.NSURLSessionTask
import platform.Foundation.setHTTPMethod
import platform.Foundation.setValue
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.valueForHTTPHeaderField
import platform.UIKit.UIApplication
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fwrite
import platform.darwin.NSObject

private const val BACKGROUND_SESSION_IDENTIFIER = "com.nuvio.app.downloads"
private const val DOWNLOAD_RESOURCE_TIMEOUT_SECONDS = 24.0 * 60.0 * 60.0
private const val PROGRESS_MIN_INTERVAL_SECONDS = 0.5
private const val PROGRESS_MIN_BYTE_DELTA = 512L * 1024L

private val backgroundSessionCompletionHandlers = mutableMapOf<String, () -> Unit>()

fun handleDownloadsBackgroundEvents(
    identifier: String,
    completionHandler: () -> Unit,
) {
    backgroundSessionCompletionHandlers[identifier] = completionHandler
    IosBackgroundDownloadCoordinator.ensureSessionCreated()
}

fun pauseDownloadsForAppBackground() = Unit

@OptIn(ExperimentalForeignApi::class)
internal actual object DownloadsPlatformDownloader {
    actual fun start(
        request: DownloadPlatformRequest,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
        onSuccess: (localFileUri: String, totalBytes: Long?) -> Unit,
        onFailure: (message: String) -> Unit,
        onPaused: () -> Unit,
    ): DownloadsTaskHandle {
        IosBackgroundDownloadCoordinator.startOrResume(
            downloadId = request.item.id,
            request = request,
            rangeStart = null,
            callbacks = DownloadCallbacks(onProgress, onSuccess, onFailure),
        )
        return IosDownloadsTaskHandle(request.item.id)
    }

    actual fun restoreItem(item: DownloadItem): DownloadItem =
        if (item.status == DownloadStatus.Downloading) {
            item.copy(status = DownloadStatus.Paused, errorMessage = null)
        } else {
            item
        }

    actual fun removeFile(localFileUri: String?): Boolean {
        if (localFileUri.isNullOrBlank()) return false
        return resolveDownloadsBaseDirectory().withAccess { downloadsDirectory ->
            val path = localFileUri.toLocalPath() ?: return@withAccess false
            if (NSFileManager.defaultManager.fileExistsAtPath(path)) {
                return@withAccess removePathIfExists(path)
            }

            val fileName = path.substringAfterLast('/').takeIf { it.isNotBlank() } ?: return@withAccess false
            removePathIfExists("$downloadsDirectory/$fileName")
        }
    }

    actual fun removePartialFile(destinationFileName: String): Boolean =
        resolveDownloadsBaseDirectory().withAccess { downloadsDirectory ->
            removePathIfExists("$downloadsDirectory/$destinationFileName.part")
        }

    actual fun resolveLocalFileUri(localFileUri: String?, destinationFileName: String): String? =
        resolveDownloadsBaseDirectory().withAccess { downloadsDirectory ->
            localFileUri?.toLocalPath()
                ?.takeIf { NSFileManager.defaultManager.fileExistsAtPath(it) }
                ?.let { path ->
                    return@withAccess NSURL.fileURLWithPath(path).absoluteString ?: "file://$path"
                }

            val fileName = destinationFileName.trim().takeIf { it.isNotBlank() }
                ?: localFileUri?.toLocalPath()?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
                ?: return@withAccess null
            val currentPath = "$downloadsDirectory/$fileName"
            if (NSFileManager.defaultManager.fileExistsAtPath(currentPath)) {
                NSURL.fileURLWithPath(currentPath).absoluteString ?: "file://$currentPath"
            } else {
                null
            }
        }

    actual fun openDownloadsDirectory(): Boolean =
        resolveDownloadsBaseDirectory().withAccess { downloadsDirectory ->
            val url = NSURL.fileURLWithPath(downloadsDirectory)
            UIApplication.sharedApplication.openURL(
                url = url,
                options = emptyMap<Any?, Any>(),
                completionHandler = null,
            )
            true
        }
}

private class IosDownloadsTaskHandle(
    private val downloadId: String,
) : DownloadsTaskHandle {
    override fun cancel() {
        IosBackgroundDownloadCoordinator.cancelDownload(downloadId)
    }
}

private class DownloadCallbacks(
    val onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
    val onSuccess: (localFileUri: String, totalBytes: Long?) -> Unit,
    val onFailure: (message: String) -> Unit,
)

/** Bookkeeping for one in-flight NSURLSessionDownloadTask, alive only as long as this process is. */
private class ActiveDownload(
    val request: DownloadPlatformRequest,
    var callbacks: DownloadCallbacks?,
    var retriedWithoutRange: Boolean = false,
    var deliberatelyCancelled: Boolean = false,
    var lastProgressBytes: Long = -1L,
    var lastProgressTimestampSeconds: Double = 0.0,
)

@OptIn(ExperimentalForeignApi::class)
private val IosBackgroundDownloadCoordinator: IosBackgroundDownloadCoordinatorImpl by lazy {
    IosBackgroundDownloadCoordinatorImpl()
}

@OptIn(ExperimentalForeignApi::class)
private class IosBackgroundDownloadCoordinatorImpl : NSObject(), NSURLSessionDownloadDelegateProtocol {
    private var session: NSURLSession? = null
    private val activeDownloads = mutableMapOf<String, ActiveDownload>()
    private val tasksByDownloadId = mutableMapOf<String, NSURLSessionDownloadTask>()

    fun ensureSessionCreated(): NSURLSession {
        session?.let { return it }
        val configuration = NSURLSessionConfiguration.backgroundSessionConfigurationWithIdentifier(
            BACKGROUND_SESSION_IDENTIFIER,
        ).apply {
            timeoutIntervalForResource = DOWNLOAD_RESOURCE_TIMEOUT_SECONDS
            allowsCellularAccess = true
            sessionSendsLaunchEvents = true
        }
        val created = NSURLSession.sessionWithConfiguration(
            configuration = configuration,
            delegate = this,
            delegateQueue = NSOperationQueue().apply { maxConcurrentOperationCount = 1 },
        )
        session = created
        return created
    }

    fun startOrResume(
        downloadId: String,
        request: DownloadPlatformRequest,
        rangeStart: Long?,
        callbacks: DownloadCallbacks?,
    ) {
        val existing = activeDownloads[downloadId]
        if (existing != null) {
            existing.callbacks = callbacks ?: existing.callbacks
            return
        }

        val base = resolveDownloadsBaseDirectory()
        val started = base.scopedUrl?.startAccessingSecurityScopedResource() ?: false
        try {
            val tempPath = "${base.path}/${request.destinationFileName}.part"
            val resumeFromBytes = rangeStart ?: fileSizeOrNull(tempPath)?.coerceAtLeast(0L) ?: 0L

            val nativeRequest = buildRequest(request, rangeStart = resumeFromBytes.takeIf { it > 0L })
            val task = ensureSessionCreated().downloadTaskWithRequest(nativeRequest)
            task.taskDescription = downloadId

            activeDownloads[downloadId] = ActiveDownload(request = request, callbacks = callbacks)
            tasksByDownloadId[downloadId] = task
            callbacks?.onProgress?.invoke(resumeFromBytes, null)
            task.resume()
        } finally {
            if (started) base.scopedUrl?.stopAccessingSecurityScopedResource()
        }
    }

    fun cancelDownload(downloadId: String) {
        activeDownloads[downloadId]?.deliberatelyCancelled = true
        tasksByDownloadId.remove(downloadId)?.cancel()
    }

    override fun URLSession(
        session: NSURLSession,
        downloadTask: NSURLSessionDownloadTask,
        didWriteData: Long,
        totalBytesWritten: Long,
        totalBytesExpectedToWrite: Long,
    ) {
        val downloadId = downloadTask.taskDescription ?: return
        reportProgress(
            downloadId = downloadId,
            downloadedBytes = resumeOffset(downloadTask) + totalBytesWritten,
            totalBytes = totalBytesExpectedToWrite.takeIf { it > 0L }
                ?.let { resumeOffset(downloadTask) + it },
        )
    }

    override fun URLSession(
        session: NSURLSession,
        downloadTask: NSURLSessionDownloadTask,
        didFinishDownloadingToURL: NSURL,
    ) {
        val downloadId = downloadTask.taskDescription ?: return
        val active = activeDownloads[downloadId]
        val request = active?.request
        if (request == null) {
            runCatching { NSFileManager.defaultManager.removeItemAtPath(didFinishDownloadingToURL.path.orEmpty(), null) }
            return
        }

        val statusCode = (downloadTask.response as? NSHTTPURLResponse)?.statusCode?.toInt() ?: 200
        val base = resolveDownloadsBaseDirectory()
        val scopedStarted = base.scopedUrl?.startAccessingSecurityScopedResource() ?: false
        try {
            val tempPath = "${base.path}/${request.destinationFileName}.part"
            val destinationPath = "${base.path}/${request.destinationFileName}"
            val requestedRange = resumeOffset(downloadTask)

            if (statusCode == 416 && !( active?.retriedWithoutRange ?: false)) {
                removePathIfExists(tempPath)
                activeDownloads.remove(downloadId)
                tasksByDownloadId.remove(downloadId)
                startOrResume(
                    downloadId = downloadId,
                    request = request,
                    rangeStart = 0L,
                    callbacks = active?.callbacks,
                ).also {
                    activeDownloads[downloadId]?.retriedWithoutRange = true
                }
                return
            }

            if (statusCode !in 200..299) {
                finishWithFailure(
                    downloadId,
                    runBlocking { getString(Res.string.network_request_failed_http, statusCode) },
                )
                return
            }

            val isPartialResume = statusCode == 206 && requestedRange > 0L
            if (!isPartialResume) {
                removePathIfExists(tempPath)
            }
            val appended = appendFile(
                fromPath = didFinishDownloadingToURL.path.orEmpty(),
                toPath = tempPath,
                append = isPartialResume,
            )
            if (!appended) {
                finishWithFailure(
                    downloadId,
                    runBlocking { getString(Res.string.downloads_error_finalize_file_failed) },
                )
                return
            }

            removePathIfExists(destinationPath)
            val moved = NSFileManager.defaultManager.moveItemAtPath(tempPath, destinationPath, null)
            if (!moved) {
                finishWithFailure(
                    downloadId,
                    runBlocking { getString(Res.string.downloads_error_finalize_file_failed) },
                )
                return
            }

            val localFileUri = NSURL.fileURLWithPath(destinationPath).absoluteString ?: "file://$destinationPath"
            val finalSize = fileSizeOrNull(destinationPath)
            val callbacks = activeDownloads.remove(downloadId)?.callbacks
            tasksByDownloadId.remove(downloadId)
            if (callbacks != null) {
                callbacks.onSuccess(localFileUri, finalSize)
            }
        } finally {
            if (scopedStarted) base.scopedUrl?.stopAccessingSecurityScopedResource()
        }
    }

    override fun URLSession(
        session: NSURLSession,
        task: NSURLSessionTask,
        didCompleteWithError: NSError?,
    ) {
        val downloadId = task.taskDescription ?: return
        val active = activeDownloads[downloadId] ?: return
        tasksByDownloadId.remove(downloadId)
        if (didCompleteWithError == null) return
        if (active.deliberatelyCancelled) {
            activeDownloads.remove(downloadId)
            return
        }
        finishWithFailure(downloadId, didCompleteWithError.localizedDescription)
    }

    override fun URLSessionDidFinishEventsForBackgroundURLSession(session: NSURLSession) {
        val identifier = session.configuration.identifier ?: return
        backgroundSessionCompletionHandlers.remove(identifier)?.invoke()
    }

    private fun finishWithFailure(downloadId: String, message: String) {
        val callbacks = activeDownloads.remove(downloadId)?.callbacks
        if (callbacks != null) {
            callbacks.onFailure(message)
        }
    }

    private fun reportProgress(downloadId: String, downloadedBytes: Long, totalBytes: Long?) {
        val active = activeDownloads[downloadId]
        val now = NSDate().timeIntervalSince1970
        val byteDelta = downloadedBytes - (active?.lastProgressBytes ?: -1L)
        val timeDelta = now - (active?.lastProgressTimestampSeconds ?: 0.0)
        val reachedEnd = totalBytes != null && downloadedBytes >= totalBytes
        if (
            active != null &&
            active.lastProgressBytes >= 0L &&
            !reachedEnd &&
            byteDelta < PROGRESS_MIN_BYTE_DELTA &&
            timeDelta < PROGRESS_MIN_INTERVAL_SECONDS
        ) {
            return
        }
        active?.lastProgressBytes = downloadedBytes
        active?.lastProgressTimestampSeconds = now

        val callbacks = active?.callbacks
        if (callbacks != null) {
            callbacks.onProgress(downloadedBytes, totalBytes)
        }
    }

    private fun resumeOffset(downloadTask: NSURLSessionDownloadTask): Long {
        val rangeHeader = downloadTask.originalRequest?.valueForHTTPHeaderField("Range") ?: return 0L
        return rangeHeader.removePrefix("bytes=").substringBefore('-').toLongOrNull() ?: 0L
    }

    private fun buildRequest(request: DownloadPlatformRequest, rangeStart: Long?): NSMutableURLRequest {
        val nativeRequest = NSMutableURLRequest(
            uRL = NSURL(string = request.sourceUrl),
            cachePolicy = NSURLRequestReloadIgnoringLocalCacheData,
            timeoutInterval = DOWNLOAD_RESOURCE_TIMEOUT_SECONDS,
        )
        nativeRequest.setHTTPMethod("GET")
        request.sourceHeaders.forEach { (key, value) -> nativeRequest.setValue(value, forHTTPHeaderField = key) }
        if (rangeStart != null && rangeStart > 0L) {
            nativeRequest.setValue("bytes=$rangeStart-", forHTTPHeaderField = "Range")
        }
        return nativeRequest
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun appendFile(fromPath: String, toPath: String, append: Boolean): Boolean {
    if (!append) {
        return NSFileManager.defaultManager.moveItemAtPath(fromPath, toPath, null)
    }
    return copyByAppending(fromPath, toPath)
}

@OptIn(ExperimentalForeignApi::class)
private fun copyByAppending(fromPath: String, toPath: String): Boolean {
    val input = fopen(fromPath, "rb") ?: return false
    val output = fopen(toPath, "ab")
    if (output == null) {
        fclose(input)
        return false
    }

    val ok = runCatching {
        val buffer = ByteArray(64 * 1024)
        buffer.usePinned { pinned ->
            while (true) {
                val read = fread(pinned.addressOf(0), 1uL, buffer.size.convert(), input).toInt()
                if (read <= 0) break
                fwrite(pinned.addressOf(0), 1uL, read.convert(), output)
            }
        }
        true
    }.getOrDefault(false)

    fclose(input)
    fclose(output)
    if (ok) {
        NSFileManager.defaultManager.removeItemAtPath(fromPath, null)
    }
    return ok
}

private data class DownloadsBaseDirectory(val path: String, val scopedUrl: NSURL?)

@OptIn(ExperimentalForeignApi::class)
private fun downloadsDirectoryPath(): String {
    val root = NSHomeDirectory().trimEnd('/')
    val path = "$root/Documents/nuvio_downloads"
    NSFileManager.defaultManager.createDirectoryAtPath(
        path = path,
        withIntermediateDirectories = true,
        attributes = null,
        error = null,
    )
    return path
}

@OptIn(ExperimentalForeignApi::class)
private fun resolveDownloadsBaseDirectory(): DownloadsBaseDirectory {
    val bookmarkBase64 = DownloadsSettingsRepository.downloadLocationUri.value
    val resolvedUrl = bookmarkBase64?.let(::resolveDownloadLocationBookmark)
    val resolvedPath = resolvedUrl?.path
    return if (resolvedUrl != null && resolvedPath != null) {
        DownloadsBaseDirectory(resolvedPath, resolvedUrl)
    } else {
        DownloadsBaseDirectory(downloadsDirectoryPath(), null)
    }
}

private fun <T> DownloadsBaseDirectory.withAccess(block: (String) -> T): T {
    val started = scopedUrl?.startAccessingSecurityScopedResource() ?: false
    try {
        return block(path)
    } finally {
        if (started) scopedUrl?.stopAccessingSecurityScopedResource()
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun removePathIfExists(path: String): Boolean {
    if (!NSFileManager.defaultManager.fileExistsAtPath(path)) return true
    return NSFileManager.defaultManager.removeItemAtPath(path, null)
}

@OptIn(ExperimentalForeignApi::class)
private fun fileSizeOrNull(path: String): Long? {
    val attrs = NSFileManager.defaultManager.attributesOfItemAtPath(path, error = null)
    val value = attrs?.get("NSFileSize")
    return when (value) {
        is Long -> value
        is Number -> value.toLong()
        else -> null
    }
}

private fun String.toLocalPath(): String? {
    val value = trim()
    if (value.startsWith("file:")) {
        return NSURL(string = value).path ?: value.removePrefix("file://")
    }
    return value.takeIf { it.isNotBlank() }
}
