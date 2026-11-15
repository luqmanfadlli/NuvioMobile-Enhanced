package com.nuvio.app.features.downloads

import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.core.ui.nuvio
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val STORAGE_REFRESH_INTERVAL_MS = 10_000L

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DeviceStorageBar(
    appDownloadsBytes: Long,
    modifier: Modifier = Modifier,
) {
    val initialStats = DeviceStorageStats(0L, 0L)
    val stats by produceState(initialStats) {
        while (true) {
            value = withContext(Dispatchers.Default) { DownloadsStoragePlatform.getDeviceStorageStats() }
            delay(STORAGE_REFRESH_INTERVAL_MS)
        }
    }
    val totalBytes = stats.totalBytes
    val freeBytes = stats.freeBytes
    val otherUsedBytes = (stats.usedBytes - appDownloadsBytes).coerceAtLeast(0L)

    val tokens = MaterialTheme.nuvio
    val downloadsColor = tokens.colors.accent
    val otherAppsColor = MaterialTheme.colorScheme.tertiary
    val freeSpaceColor = tokens.colors.borderSubtle

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.downloads_storage_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (totalBytes > 0L) {
                    Text(
                        text = stringResource(
                            Res.string.downloads_storage_free_of_total,
                            formatBytes(freeBytes),
                            formatBytes(totalBytes),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (totalBytes > 0L) {
                val downloadsFraction = (appDownloadsBytes.toDouble() / totalBytes.toDouble()).coerceIn(0.0, 1.0).toFloat()
                val otherFraction = (otherUsedBytes.toDouble() / totalBytes.toDouble()).coerceIn(0.0, 1.0).toFloat()
                val freeFraction = (1f - downloadsFraction - otherFraction).coerceAtLeast(0.01f)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(freeSpaceColor),
                ) {
                    if (downloadsFraction > 0.001f) {
                        StorageBarSegment(downloadsFraction, downloadsColor)
                    }
                    if (otherFraction > 0.001f) {
                        StorageBarSegment(otherFraction, otherAppsColor)
                    }
                    if (freeFraction > 0.001f) {
                        StorageBarSegment(freeFraction)
                    }
                }
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                StorageLegendItem(
                    color = downloadsColor,
                    label = stringResource(Res.string.downloads_storage_app),
                    value = formatBytes(appDownloadsBytes),
                )
                if (totalBytes > 0L) {
                    StorageLegendItem(
                        color = otherAppsColor,
                        label = stringResource(Res.string.downloads_storage_other),
                        value = formatBytes(otherUsedBytes),
                    )
                    StorageLegendItem(
                        color = freeSpaceColor,
                        label = stringResource(Res.string.downloads_storage_free),
                        value = formatBytes(freeBytes),
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.StorageBarSegment(fraction: Float, color: Color? = null) {
    Box(
        modifier = Modifier
            .weight(fraction)
            .fillMaxHeight()
            .then(if (color != null) Modifier.background(color) else Modifier),
    )
}

@Composable
private fun StorageLegendItem(
    color: Color,
    label: String,
    value: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(
            text = stringResource(Res.string.downloads_storage_legend_value, label, value),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
