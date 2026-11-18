package com.nuvio.app.features.downloads

import androidx.compose.runtime.Composable
import com.nuvio.app.core.i18n.localizedByteUnit
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.downloads_progress_percent
import org.jetbrains.compose.resources.stringResource

internal fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 ${localizedByteUnit("B")}"
    val unit = ByteUnit.forBytes(bytes)
    return if (unit == ByteUnit.B) {
        "$bytes ${localizedByteUnit(unit.name)}"
    } else {
        "${unit.format(bytes)} ${localizedByteUnit(unit.name)}"
    }
}

/** "1.2/3.4 GB" when both sizes share a unit, otherwise "512.0 MB/1.2 GB". */
internal fun formatDownloadBadgeProgress(downloadedBytes: Long, totalBytes: Long?): String {
    if (totalBytes == null || totalBytes <= 0L) return formatBytes(downloadedBytes)
    val unit = ByteUnit.forBytes(totalBytes)
    return if (unit == ByteUnit.forBytes(downloadedBytes)) {
        "${unit.format(downloadedBytes)}/${unit.format(totalBytes)} ${localizedByteUnit(unit.name)}"
    } else {
        "${formatBytes(downloadedBytes)}/${formatBytes(totalBytes)}"
    }
}

data class SeriesDownloadStats(
    val totalBytes: Long,
    val avgBytes: Long,
    val medianBytes: Long,
)

internal fun calculateSeriesDownloadStats(episodes: List<DownloadItem>): SeriesDownloadStats {
    if (episodes.isEmpty()) return SeriesDownloadStats(0L, 0L, 0L)
    val sizes = episodes.map { it.totalBytes?.takeIf { b -> b > 0L } ?: it.downloadedBytes }
    val total = sizes.sum()
    val avg = total / sizes.size
    val sorted = sizes.sorted()
    val median = if (sorted.size % 2 == 1) {
        sorted[sorted.size / 2]
    } else {
        (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2L
    }
    return SeriesDownloadStats(totalBytes = total, avgBytes = avg, medianBytes = median)
}

@Composable
internal fun formatDownloadPercent(progress: Float): String =
    stringResource(Res.string.downloads_progress_percent, (progress * 100f).toInt())

private enum class ByteUnit(val bytes: Double) {
    B(1.0),
    KB(1024.0),
    MB(1024.0 * 1024.0),
    GB(1024.0 * 1024.0 * 1024.0);

    fun format(value: Long): Double = ((value / bytes) * 10.0).toInt() / 10.0

    companion object {
        fun forBytes(value: Long): ByteUnit = entries.last { value >= it.bytes || it == B }
    }
}
