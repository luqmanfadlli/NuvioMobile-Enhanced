package com.nuvio.app.features.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.nuvioTypeScale
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.player_chapter_number
import org.jetbrains.compose.resources.stringResource

/** A chapter of the playing file, as its container lists it. */
@Immutable
data class PlayerChapter(
    val startMs: Long,
    val title: String?,
)

internal object PlayerChapters {

    /** Going back within this much of a chapter's start goes to the chapter before, as in mpv. */
    const val PREVIOUS_RESTART_THRESHOLD_MS = 3_000L

    /**
     * Sorted, one per start time, and only when there are at least two: a lone chapter (often a
     * single "Chapter 1" at 0) has nothing to skip to.
     */
    fun normalize(chapters: List<PlayerChapter>): List<PlayerChapter> {
        val sorted = chapters
            .filter { it.startMs >= 0L }
            .sortedBy { it.startMs }
            .distinctBy { it.startMs }
        return if (sorted.size < 2) emptyList() else sorted
    }

    /** Index of the chapter playing at [positionMs], or -1 before the first one starts. */
    fun indexAt(chapters: List<PlayerChapter>, positionMs: Long): Int =
        chapters.indexOfLast { it.startMs <= positionMs }

    fun nextStartMs(chapters: List<PlayerChapter>, positionMs: Long): Long? =
        chapters.firstOrNull { it.startMs > positionMs }?.startMs

    /** The current chapter's start, or the one before when [positionMs] is right after that start. */
    fun previousStartMs(chapters: List<PlayerChapter>, positionMs: Long): Long? {
        val index = indexAt(chapters, positionMs)
        if (index < 0) return null
        val current = chapters[index].startMs
        if (positionMs - current > PREVIOUS_RESTART_THRESHOLD_MS || index == 0) return current
        return chapters[index - 1].startMs
    }
}

// --- Fork additions -------------------------------------------------------------------------
// Upstream reads chapters through PlayerEngineController.getChapters() (libmpv on Android,
// MKV-only ExoPlayer). The fork also fills PlayerPlaybackSnapshot.chapters from iOS mpv and the
// fork ExoPlayer reader (MKV + MP4); the runtime falls back to those (see
// PlayerScreenRuntimeChapters.kt). The fork also shows the current chapter above the timeline.

private const val MaxMpvChapters = 500

internal fun mpvChapters(property: (String) -> String?): List<PlayerChapter> {
    val count = property("chapter-list/count")?.trim()?.toIntOrNull() ?: return emptyList()
    return (0 until count.coerceIn(0, MaxMpvChapters))
        .mapNotNull { index ->
            val seconds = property("chapter-list/$index/time")?.trim()?.toDoubleOrNull()
                ?: return@mapNotNull null
            PlayerChapter(
                startMs = (seconds * 1000.0).toLong().coerceAtLeast(0L),
                title = property("chapter-list/$index/title")?.trim()?.takeIf { it.isNotBlank() },
            )
        }
}

internal fun List<PlayerChapter>.chapterMarkFractions(durationMs: Long): List<Float> {
    if (durationMs <= 0L) return emptyList()
    return asSequence()
        .map { it.startMs }
        .filter { it > 0L && it < durationMs }
        .map { (it.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) }
        .toList()
}

@Composable
internal fun PlayerChapterLabel(
    chapters: List<PlayerChapter>,
    positionMs: Long,
    metrics: PlayerLayoutMetrics,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val settings by PlayerSettingsRepository.uiState.collectAsState()
    if (!settings.chapterTitleEnabled) return
    val index = PlayerChapters.indexAt(chapters, positionMs)
    if (index < 0) return
    val title = chapters[index].title?.takeIf { it.isNotBlank() }
        ?: stringResource(Res.string.player_chapter_number, index + 1)
    Text(
        text = title,
        style = MaterialTheme.nuvioTypeScale.labelSm.copy(
            fontSize = metrics.metadataSize,
            fontWeight = FontWeight.Medium,
        ),
        color = Color.White.copy(alpha = 0.9f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = PlayerTimelineContentInset, vertical = 2.dp),
    )
}
