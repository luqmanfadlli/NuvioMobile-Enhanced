package com.nuvio.app.features.downloads

import androidx.compose.runtime.Composable
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun downloadStatusText(item: DownloadItem): String {
    val size = if (item.totalBytes != null && item.totalBytes > 0L) {
        "${formatBytes(item.downloadedBytes)} / ${formatBytes(item.totalBytes)}"
    } else {
        formatBytes(item.downloadedBytes)
    }

    return when (item.status) {
        DownloadStatus.Downloading -> stringResource(Res.string.downloads_status_downloading, size)
        DownloadStatus.Queued -> stringResource(Res.string.downloads_status_queued)
        DownloadStatus.Paused -> stringResource(Res.string.downloads_status_paused, size)
        DownloadStatus.Completed -> stringResource(
            Res.string.downloads_status_completed,
            formatBytes(item.totalBytes?.takeIf { it > 0L } ?: item.downloadedBytes),
        )
        DownloadStatus.Failed -> item.errorMessage ?: stringResource(Res.string.downloads_status_failed)
    }
}

@Composable
internal fun downloadActivitySummary(counts: DownloadStatusCounts): String? = listOf(
    counts.downloading to Res.string.downloads_summary_downloading,
    counts.queued to Res.string.downloads_summary_queued,
    counts.paused to Res.string.downloads_summary_paused,
    counts.failed to Res.string.downloads_summary_failed,
).filter { (count, _) -> count > 0 }
    .map { (count, label) -> stringResource(label, count) }
    .joinToString(" • ")
    .ifEmpty { null }

@Composable
internal fun episodeCountText(count: Int): String =
    pluralStringResource(Res.plurals.downloads_episodes_count, count, count)
