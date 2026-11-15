package com.nuvio.app.features.downloads

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

private const val PROGRESS_ANIMATION_MS = 400

/** [progress] animated towards each new value, so progress bars move smoothly. */
@Composable
internal fun animatedDownloadProgress(progress: Float): Float {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = PROGRESS_ANIMATION_MS),
        label = "downloadProgress",
    )
    return animated
}

/** A progress bar that shows [progress] when known and an indeterminate bar when it is null. */
@Composable
internal fun DownloadProgressBar(
    progress: Float?,
    modifier: Modifier = Modifier,
) {
    if (progress != null) {
        LinearProgressIndicator(progress = { progress }, modifier = modifier)
    } else {
        LinearProgressIndicator(modifier = modifier)
    }
}

/** A small spinner that shows [progress] when known and spins indefinitely when it is null. */
@Composable
internal fun DownloadProgressSpinner(
    progress: Float?,
    color: Color,
    size: Dp = 10.dp,
    strokeWidth: Dp = 2.dp,
) {
    if (progress != null) {
        CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier.size(size),
            strokeWidth = strokeWidth,
            color = color,
            trackColor = color.copy(alpha = 0.3f),
        )
    } else {
        CircularProgressIndicator(
            modifier = Modifier.size(size),
            strokeWidth = strokeWidth,
            color = color,
        )
    }
}

/** The download's progress when its total size is known, otherwise null. */
internal val DownloadItem.knownProgress: Float?
    get() = progressFraction.takeIf { totalBytes != null && totalBytes > 0L }

/**
 * The status action for a download (pause, resume, retry or play) followed by delete.
 * Pause and resume act on the download directly; play and delete are left to the caller.
 */
@Composable
internal fun DownloadItemActions(
    item: DownloadItem,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
    buttonSize: Dp = 36.dp,
    iconSize: Dp = 20.dp,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        val (icon, label, onClick) = when (item.status) {
            DownloadStatus.Downloading, DownloadStatus.Queued -> Triple(
                Icons.Rounded.Pause,
                Res.string.compose_action_pause,
                { DownloadsRepository.pauseDownload(item.id) },
            )
            DownloadStatus.Paused -> Triple(
                Icons.Rounded.PlayArrow,
                Res.string.action_resume,
                { DownloadsRepository.resumeDownload(item.id) },
            )
            DownloadStatus.Failed -> Triple(
                Icons.Rounded.Refresh,
                Res.string.action_retry,
                { DownloadsRepository.resumeDownload(item.id) },
            )
            DownloadStatus.Completed -> Triple(Icons.Rounded.PlayArrow, Res.string.action_play, onPlay)
        }
        IconButton(onClick = onClick, modifier = Modifier.size(buttonSize)) {
            Icon(
                imageVector = icon,
                contentDescription = stringResource(label),
                modifier = Modifier.size(iconSize),
            )
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(buttonSize)) {
            Icon(
                imageVector = Icons.Rounded.Delete,
                contentDescription = stringResource(Res.string.action_delete),
                modifier = Modifier.size(iconSize),
            )
        }
    }
}

/**
 * Artwork that fills in with colour as a download progresses. The image is split into one
 * horizontal band per entry of [segmentProgresses] (one per episode for a show), and each
 * band turns from greyscale to colour from the top down as its progress grows.
 *
 * @param edgeColor when set, draws a line at the moving edge of each unfinished band.
 * @param placeholder shown instead of the image when [imageUrl] is null.
 */
@Composable
internal fun DownloadRevealImage(
    imageUrl: String?,
    segmentProgresses: List<Float>,
    dimAlpha: Float,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    edgeColor: Color? = null,
    placeholder: @Composable BoxScope.() -> Unit = {},
) {
    val grayscale = remember { ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) }
    val fillColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)

    BoxWithConstraints(modifier = modifier) {
        val fullWidth = maxWidth
        val fullHeight = maxHeight
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                colorFilter = grayscale,
                modifier = Modifier.fillMaxSize(),
            )
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = dimAlpha)))
        } else {
            placeholder()
        }

        val segmentHeight = fullHeight / segmentProgresses.size.coerceAtLeast(1)
        segmentProgresses.forEachIndexed { index, progress ->
            key(index) {
                val revealed = animatedDownloadProgress(progress)
                val segmentTop = segmentHeight * index
                val revealedHeight = segmentHeight * revealed
                if (revealedHeight > 0.5.dp) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .fillMaxWidth()
                            .offset(y = segmentTop)
                            .height(revealedHeight)
                            .clipToBounds(),
                    ) {
                        if (imageUrl != null) {
                            AsyncImage(
                                model = imageUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .width(fullWidth)
                                    .height(fullHeight)
                                    .offset(y = -segmentTop),
                            )
                        } else {
                            Box(modifier = Modifier.fillMaxSize().background(fillColor))
                        }
                        if (edgeColor != null && revealed < 0.999f) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .height(2.5.dp)
                                    .background(edgeColor),
                            )
                        }
                    }
                }
            }
        }
    }
}
