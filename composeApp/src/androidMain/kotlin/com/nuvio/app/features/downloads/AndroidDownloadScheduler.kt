package com.nuvio.app.features.downloads

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.net.Network
import android.os.Build
import android.os.PersistableBundle
import android.os.SystemClock
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool

internal class AndroidDownloadScheduler(val context: Context) {
    val store = AndroidDownloadStore(File(context.filesDir, "download-transfers"))
    val directory = File(context.filesDir, "downloads")
    private val locks = ConcurrentHashMap<String, Mutex>()
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        AndroidDownloadExport.initialize(context)
    }

    fun enqueue(item: DownloadItem): AndroidDownloadTransfer {
        val previous = store.get(item.fileName)
        val exportTreeUri = DownloadsSettingsRepository.run {
            ensureLoaded()
            downloadLocationUri.value
        }?.takeIf { it.isNotBlank() }
        val transfer = store.begin(item, exportTreeUri)
        previous?.takeIf { it.item.id == item.id }?.partialDocumentUri
            ?.takeIf { it != transfer.partialDocumentUri }
            ?.let { stale -> cleanupScope.launch { AndroidDownloadExport.delete(stale) } }
        val existing = previous?.generation == transfer.generation
        if (existing && Build.VERSION.SDK_INT >= 34) return transfer
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                check(jobScheduler().schedule(buildJob(transfer)) == JobScheduler.RESULT_SUCCESS) {
                    "Android could not start the download. Open the app and try again."
                }
            } else {
                val work = OneTimeWorkRequestBuilder<DownloadsTransferWorker>()
                    .setInputData(workDataOf(FILE_NAME to item.fileName, GENERATION to transfer.generation))
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(if (mobileDataAllowed()) NetworkType.CONNECTED else NetworkType.UNMETERED).build())
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                    .build()
                val operation = WorkManager.getInstance(context).enqueueUniqueWork(
                    workName(transfer), if (existing) ExistingWorkPolicy.KEEP else ExistingWorkPolicy.REPLACE, work,
                )
                operation.result.addListener({
                    try {
                        operation.result.get()
                    } catch (error: Exception) {
                        fail(transfer, error)
                        notifyCurrent(item.fileName)
                    }
                }, ContextCompat.getMainExecutor(context))
            }
        } catch (error: Exception) {
            if (Build.VERSION.SDK_INT >= 34) {
                store.update(item.fileName, transfer.generation) {
                    it.copy(item = it.item.copy(status = DownloadStatus.Queued, errorMessage = null))
                }
                cancelScheduled(transfer)
                Log.w(TAG, "Download is queued until the app returns to foreground", error)
            } else {
                fail(transfer, error)
            }
        }
        return store.get(item.fileName) ?: transfer
    }

    @RequiresApi(34)
    internal fun buildJob(transfer: AndroidDownloadTransfer): JobInfo =
        JobInfo.Builder(transfer.jobId, ComponentName(context, DownloadsTransferJobService::class.java))
            .setUserInitiated(true)
            // Matches DownloadNetworkGuard's unmetered rule, so the queue and the OS agree.
            .setRequiredNetworkType(if (mobileDataAllowed()) JobInfo.NETWORK_TYPE_ANY else JobInfo.NETWORK_TYPE_UNMETERED)
            .setPersisted(true)
            .setEstimatedNetworkBytes(transfer.item.totalBytes ?: JobInfo.NETWORK_BYTES_UNKNOWN.toLong(), 0L)
            .setBackoffCriteria(30_000L, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
            .setExtras(PersistableBundle().apply {
                putString(FILE_NAME, transfer.item.fileName)
                putString(GENERATION, transfer.generation)
            })
            .build()

    private fun mobileDataAllowed(): Boolean {
        DownloadsSettingsRepository.ensureLoaded()
        return DownloadsSettingsRepository.allowMobileDataDownloads.value
    }

    @RequiresApi(34)
    private fun jobScheduler(): JobScheduler =
        context.getSystemService(JobScheduler::class.java).forNamespace(JOB_NAMESPACE)

    fun restore(item: DownloadItem): DownloadItem {
        var transfer = store.get(item.fileName)?.takeIf { it.item.id == item.id }
            ?: return if (item.status == DownloadStatus.Downloading) {
                item.copy(status = DownloadStatus.Paused, errorMessage = null)
            } else item
        if (transfer.item.status == DownloadStatus.Downloading && Build.VERSION.SDK_INT >= 34 &&
            jobScheduler().getPendingJob(transfer.jobId) == null) {
            reschedule(item.fileName)
            transfer = store.get(item.fileName) ?: transfer
        }
        return if (item.status == DownloadStatus.Queued) item else transfer.item
    }

    fun reschedule(fileName: String) {
        val transfer = store.get(fileName) ?: return
        if (transfer.item.status != DownloadStatus.Downloading) return
        val scheduled = if (Build.VERSION.SDK_INT >= 34) {
            try {
                jobScheduler().schedule(buildJob(transfer)) == JobScheduler.RESULT_SUCCESS
            } catch (error: Exception) {
                Log.w(TAG, "Could not reschedule download $fileName", error)
                false
            }
        } else false
        if (!scheduled) pause(fileName)
    }

    fun pause(fileName: String) {
        val transfer = store.get(fileName) ?: return
        if (transfer.item.status != DownloadStatus.Downloading) return
        val paused = store.update(fileName, transfer.generation) {
            it.copy(item = it.item.copy(status = DownloadStatus.Paused, errorMessage = null))
        }
        cancelScheduled(transfer)
        paused?.let { DownloadsLiveStatusPlatform.notifyTransfer(it.item) }
    }

    fun remove(fileName: String) {
        val transfer = store.get(fileName)
        store.remove(fileName)
        transfer?.let(::cancelScheduled)
        transfer?.let { DownloadsLiveStatusPlatform.removeNotification(it.item.id) }
        cleanupScope.launch {
            lock(fileName).withLock {
                if (store.get(fileName) == null) {
                    File(directory, "$fileName.part").delete()
                    transfer?.partialDocumentUri?.let(AndroidDownloadExport::delete)
                    DownloadSubtitleStorage(File(directory, fileName).toURI().toString()).remove()
                }
            }
        }
    }

    fun clearUnfinishedTransfers() {
        store.transfers.value.values
            .filter { it.item.status != DownloadStatus.Completed }
            .map { it.item.fileName }
            .forEach(::remove)
    }

    private fun cancelScheduled(transfer: AndroidDownloadTransfer) {
        if (Build.VERSION.SDK_INT >= 34) jobScheduler().cancel(transfer.jobId)
        else WorkManager.getInstance(context).cancelUniqueWork(workName(transfer))
    }

    suspend fun execute(
        transfer: AndroidDownloadTransfer,
        network: Network? = null,
        onProgress: (AndroidDownloadTransfer) -> Unit,
    ): Boolean = lock(transfer.item.fileName).withLock {
        val fileName = transfer.item.fileName
        if (!isActive(transfer)) return@withLock false
        val destination = File(directory, fileName)
        val client = if (network != null) {
            downloadHttpClient.newBuilder()
                .socketFactory(network.socketFactory)
                .dns { network.getAllByName(it).toList() }
                .connectionPool(ConnectionPool())
                .build()
        } else downloadHttpClient
        val subtitles = CoroutineScope(currentCoroutineContext()).launch {
            try {
                DownloadSubtitles.prepare(transfer.item, destination.toURI().toString())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w(TAG, "Subtitle preparation failed for $fileName", error)
            }
        }
        try {
            var lastProgressAt = 0L
            val onHeaders: (Long?, String?) -> Unit = { total, validator ->
                updateActive(transfer) { it.copy(validator = validator, item = it.item.copy(totalBytes = total)) }
            }
            val reportProgress: (Long, Long?) -> Unit = { bytes, total ->
                val now = SystemClock.elapsedRealtime()
                if (now - lastProgressAt >= 1_000L || bytes == total) {
                    lastProgressAt = now
                    updateActive(transfer) {
                        it.copy(
                            retryCount = if (bytes > it.item.downloadedBytes) 0 else it.retryCount,
                            item = it.item.copy(downloadedBytes = bytes, totalBytes = total),
                        )
                    }?.let(onProgress)
                }
            }

            val exportFolder = transfer.exportTreeUri?.let { treeUri ->
                AndroidDownloadExport.writableFolder(treeUri).also {
                    if (it == null) Log.w(TAG, "Download location unavailable, keeping $fileName in app storage")
                }
            }
            val partialDocument = if (exportFolder != null && !destination.isFile) {
                directPartial(transfer, exportFolder)
            } else null
            if (!isActive(transfer)) return@withLock false

            if (partialDocument != null) {
                transferAndroidDownload(
                    item = transfer.item,
                    sink = AndroidDownloadExport.sink(partialDocument),
                    validator = transfer.validator,
                    client = client,
                    onHeaders = onHeaders,
                    onProgress = reportProgress,
                )
                currentCoroutineContext().ensureActive()
                subtitles.join()
                updateActive(transfer) { current ->
                    val finalUri = AndroidDownloadExport.finalizePartial(partialDocument, fileName).toString()
                    runCatching { AndroidDownloadExport.moveSubtitles(destination, finalUri) }
                    val bytes = runCatching { AndroidDownloadExport.sink(finalUri).length() }
                        .getOrDefault(current.item.downloadedBytes)
                    current.copy(
                        partialDocumentUri = null,
                        item = current.item.copy(
                            status = DownloadStatus.Completed,
                            localFileUri = finalUri,
                            downloadedBytes = bytes,
                            totalBytes = bytes,
                            errorMessage = null,
                        ),
                    )
                }
                return@withLock false
            }

            val partialFile = File(directory, "$fileName.part")
            if (!destination.isFile) {
                transferAndroidDownload(
                    item = transfer.item,
                    sink = FileDownloadSink(partialFile),
                    validator = transfer.validator,
                    client = client,
                    onHeaders = onHeaders,
                    onProgress = reportProgress,
                )
            }
            currentCoroutineContext().ensureActive()
            if (!isActive(transfer)) return@withLock false
            subtitles.join()
            if (!destination.isFile && !partialFile.renameTo(destination)) {
                throw IOException("Could not finalize the downloaded file")
            }
            val bytes = destination.length()
            val exportedUri = exportFolder?.let { AndroidDownloadExport.copyInto(it, destination).toString() }
            val completed = updateActive(transfer) { current ->
                if (exportedUri != null) {
                    AndroidDownloadExport.moveSubtitles(destination, exportedUri)
                    destination.delete()
                }
                current.copy(item = current.item.copy(
                    status = DownloadStatus.Completed,
                    localFileUri = exportedUri ?: destination.toURI().toString(),
                    downloadedBytes = bytes,
                    totalBytes = bytes,
                    errorMessage = null,
                ))
            }
            if (exportedUri != null && completed?.item?.localFileUri != exportedUri) {
                AndroidDownloadExport.delete(exportedUri)
            }
            false
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            currentCoroutineContext().ensureActive()
            if (!isActive(transfer)) return@withLock false
            val retry = shouldRetryAndroidDownload(error, transfer.retryCount)
            if (retry) updateActive(transfer) { it.copy(retryCount = it.retryCount + 1) }
            else fail(transfer, error)
            retry
        } finally {
            subtitles.cancel()
            if (network != null) withContext(NonCancellable + Dispatchers.IO) { client.connectionPool.evictAll() }
        }
    }

    private fun directPartial(
        transfer: AndroidDownloadTransfer,
        folder: androidx.documentfile.provider.DocumentFile,
    ): String? {
        val current = store.get(transfer.item.fileName)?.takeIf { it.generation == transfer.generation }
            ?: return null
        if (!current.directWrite) return null
        current.partialDocumentUri?.takeIf(AndroidDownloadExport::exists)?.let { return it }
        if (File(directory, "${transfer.item.fileName}.part").isFile) return null
        val created = AndroidDownloadExport.createPartial(folder, transfer.item.fileName)
        val usable = created?.takeIf(AndroidDownloadExport::probeDirectWrite)
        if (created != null && usable == null) AndroidDownloadExport.delete(created.toString())
        val updated = updateActive(transfer) {
            it.copy(partialDocumentUri = usable?.toString(), directWrite = usable != null)
        }
        if (updated?.partialDocumentUri != usable?.toString()) {
            usable?.let { AndroidDownloadExport.delete(it.toString()) }
            return null
        }
        if (usable == null) Log.i(TAG, "Download location can't be written in place, staging ${transfer.item.fileName}")
        return usable?.toString()
    }

    fun isActive(transfer: AndroidDownloadTransfer): Boolean = store.get(transfer.item.fileName)?.let {
        it.generation == transfer.generation && it.item.status == DownloadStatus.Downloading
    } == true

    fun fail(transfer: AndroidDownloadTransfer, error: Exception) {
        updateActive(transfer) {
            it.copy(item = it.item.copy(status = DownloadStatus.Failed, errorMessage = error.message ?: "Download failed"))
        }
    }

    fun notifyCurrent(fileName: String) {
        store.get(fileName)?.let { DownloadsLiveStatusPlatform.notifyTransfer(it.item) }
    }

    private fun updateActive(
        transfer: AndroidDownloadTransfer,
        update: (AndroidDownloadTransfer) -> AndroidDownloadTransfer,
    ): AndroidDownloadTransfer? = store.update(transfer.item.fileName, transfer.generation) {
        if (it.item.status == DownloadStatus.Downloading) {
            update(it).let { changed -> changed.copy(item = changed.item.copy(updatedAtEpochMs = System.currentTimeMillis())) }
        } else it
    }

    private fun lock(fileName: String): Mutex = locks.getOrPut(fileName) { Mutex() }
    private fun workName(transfer: AndroidDownloadTransfer) = "nuvio-download-${transfer.jobId}"

    companion object {
        const val FILE_NAME = "download_file_name"
        const val GENERATION = "download_generation"
        const val JOB_NAMESPACE = "nuvio-downloads"
        private const val TAG = "NuvioDownloads"
    }
}

internal fun shouldRetryAndroidDownload(error: Exception, retries: Int): Boolean =
    retries < 4 && when (error) {
        is DownloadHttpException -> error.statusCode == 408 || error.statusCode == 429 || error.statusCode in 500..599
        is IOException -> true
        else -> false
    }
