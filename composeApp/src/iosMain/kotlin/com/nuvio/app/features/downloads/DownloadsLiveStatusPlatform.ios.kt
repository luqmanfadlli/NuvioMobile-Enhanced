package com.nuvio.app.features.downloads

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSUserDefaults

internal actual object DownloadsLiveStatusPlatform {
    private const val notificationName = "NuvioDownloadsLiveStatusUpdated"
    private const val userDefaultsPayloadKey = "nuvio.downloads.live_status.payload"

    private val json = Json {
        encodeDefaults = true
    }

    private var lastPayload: String? = null

    actual fun onItemsChanged(items: List<DownloadItem>) {
        val primary = items
            .filter { item ->
                item.status == DownloadStatus.Downloading ||
                    item.status == DownloadStatus.Queued ||
                    item.status == DownloadStatus.Paused ||
                    item.status == DownloadStatus.Failed
            }
            .sortedWith(
                compareBy<DownloadItem> { statusPriority(it.status) }
                    .thenByDescending { it.updatedAtEpochMs },
            )
            .firstOrNull()

        val payload = primary?.let { item ->
            json.encodeToString(
                DownloadsLiveStatusPayload(
                    id = item.id,
                    title = item.title,
                    subtitle = item.displaySubtitle,
                    status = item.status.name,
                    downloadedBytes = item.downloadedBytes,
                    totalBytes = item.totalBytes,
                    progressPercent = if (item.totalBytes != null && item.totalBytes > 0L) {
                        ((item.downloadedBytes.toDouble() / item.totalBytes.toDouble()) * 100.0)
                            .toInt()
                            .coerceIn(0, 100)
                    } else {
                        -1
                    },
                ),
            )
        }

        if (payload == lastPayload) return
        lastPayload = payload

        val defaults = NSUserDefaults.standardUserDefaults
        if (payload == null) {
            defaults.removeObjectForKey(userDefaultsPayloadKey)
        } else {
            defaults.setObject(payload, forKey = userDefaultsPayloadKey)
        }

        NSNotificationCenter.defaultCenter.postNotificationName(notificationName, null)
    }

    private fun statusPriority(status: DownloadStatus): Int = when (status) {
        DownloadStatus.Downloading -> 0
        DownloadStatus.Queued -> 1
        DownloadStatus.Paused -> 2
        DownloadStatus.Failed -> 3
        DownloadStatus.Completed -> 4
    }
}

@Serializable
private data class DownloadsLiveStatusPayload(
    val id: String,
    val title: String,
    val subtitle: String,
    val status: String,
    val downloadedBytes: Long,
    val totalBytes: Long? = null,
    val progressPercent: Int,
)
