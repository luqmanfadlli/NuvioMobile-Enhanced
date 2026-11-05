package com.nuvio.app.features.livetv

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.nuvio

internal const val LIVE_TV_GUIDE_SLOT_MILLIS = 30L * 60L * 1000L
internal const val LIVE_TV_GUIDE_WINDOW_MILLIS = 12L * 60L * 60L * 1000L
internal val LiveTvGuideDpPerMinute = 4.dp
internal val LiveTvGuideChannelColumnWidth = 96.dp
private val LiveTvGuideRowHeight = 64.dp
private val LiveTvGuideBlockGap = 3.dp

internal fun liveTvGuideWindowStart(nowMillis: Long): Long =
    (nowMillis / LIVE_TV_GUIDE_SLOT_MILLIS) * LIVE_TV_GUIDE_SLOT_MILLIS - LIVE_TV_GUIDE_SLOT_MILLIS

private fun minutesToDp(millis: Long): Dp = LiveTvGuideDpPerMinute * (millis / 60_000f)

private val LiveTvGuideTimelineWidth: Dp get() = minutesToDp(LIVE_TV_GUIDE_WINDOW_MILLIS)

internal fun liveTvGuideHasPrograms(programs: List<LiveTvEpgProgram>?, windowStart: Long): Boolean {
    val windowEnd = windowStart + LIVE_TV_GUIDE_WINDOW_MILLIS
    return programs?.any { it.endMillis > windowStart && it.startMillis < windowEnd } == true
}

@Composable
internal fun LiveTvGuideTimeRuler(
    scrollState: ScrollState,
    windowStart: Long,
    nowMillis: Long,
) {
    val tokens = MaterialTheme.nuvio
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp),
    ) {
        Box(modifier = Modifier.width(LiveTvGuideChannelColumnWidth))
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .horizontalScroll(scrollState),
        ) {
            Box(
                modifier = Modifier
                    .width(LiveTvGuideTimelineWidth)
                    .fillMaxHeight(),
            ) {
                val slots = (LIVE_TV_GUIDE_WINDOW_MILLIS / LIVE_TV_GUIDE_SLOT_MILLIS).toInt()
                repeat(slots) { index ->
                    val slotStart = windowStart + index * LIVE_TV_GUIDE_SLOT_MILLIS
                    Text(
                        text = formatLiveTvEpgTime(slotStart),
                        style = MaterialTheme.typography.labelMedium,
                        color = tokens.colors.textMuted,
                        modifier = Modifier
                            .offset(x = minutesToDp(slotStart - windowStart))
                            .align(Alignment.CenterStart)
                            .padding(start = 4.dp),
                    )
                }
                LiveTvGuideNowLine(windowStart = windowStart, nowMillis = nowMillis)
            }
        }
    }
}

@Composable
private fun LiveTvGuideNowLine(windowStart: Long, nowMillis: Long) {
    if (nowMillis < windowStart || nowMillis > windowStart + LIVE_TV_GUIDE_WINDOW_MILLIS) return
    Box(
        modifier = Modifier
            .offset(x = minutesToDp(nowMillis - windowStart))
            .width(2.dp)
            .fillMaxHeight()
            .background(MaterialTheme.nuvio.colors.accent),
    )
}

internal fun LazyListScope.liveTvGuideRows(
    channels: List<LiveTvChannel>,
    epgPrograms: Map<String, List<LiveTvEpgProgram>>,
    scrollState: ScrollState,
    windowStart: Long,
    nowMillis: Long,
    onChannelClick: (LiveTvChannel) -> Unit,
    onProgramClick: (LiveTvChannel, LiveTvEpgProgram) -> Unit,
) {
    items(channels, key = { "guide:${it.id}" }) { channel ->
        LiveTvGuideRow(
            channel = channel,
            programs = epgPrograms[channel.id].orEmpty(),
            scrollState = scrollState,
            windowStart = windowStart,
            nowMillis = nowMillis,
            onChannelClick = { onChannelClick(channel) },
            onProgramClick = { program -> onProgramClick(channel, program) },
        )
    }
}

@Composable
private fun LiveTvGuideRow(
    channel: LiveTvChannel,
    programs: List<LiveTvEpgProgram>,
    scrollState: ScrollState,
    windowStart: Long,
    nowMillis: Long,
    onChannelClick: () -> Unit,
    onProgramClick: (LiveTvEpgProgram) -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val windowEnd = windowStart + LIVE_TV_GUIDE_WINDOW_MILLIS
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(LiveTvGuideRowHeight)
            .padding(vertical = 3.dp),
    ) {
        Column(
            modifier = Modifier
                .width(LiveTvGuideChannelColumnWidth)
                .fillMaxHeight()
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onChannelClick)
                .padding(end = 6.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 52.dp, height = 32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(tokens.colors.surfaceCard),
                contentAlignment = Alignment.Center,
            ) {
                if (!channel.logoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = channel.logoUrl,
                        contentDescription = channel.name,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(3.dp),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Tv,
                        contentDescription = null,
                        tint = tokens.colors.textMuted,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Text(
                text = channel.name,
                style = MaterialTheme.typography.labelSmall,
                color = tokens.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .horizontalScroll(scrollState),
        ) {
            Box(
                modifier = Modifier
                    .width(LiveTvGuideTimelineWidth)
                    .fillMaxHeight(),
            ) {
                programs.forEach { program ->
                    val start = maxOf(program.startMillis, windowStart)
                    val end = minOf(program.endMillis, windowEnd)
                    if (end <= start) return@forEach
                    val width = minutesToDp(end - start) - LiveTvGuideBlockGap
                    if (width <= 0.dp) return@forEach
                    val airing = program.isAiringAt(nowMillis)
                    val past = program.endMillis <= nowMillis
                    Column(
                        modifier = Modifier
                            .offset(x = minutesToDp(start - windowStart))
                            .width(width)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (airing) tokens.colors.overlaySelected else tokens.colors.surface)
                            .clickable { onProgramClick(program) }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = program.title,
                            style = MaterialTheme.typography.labelLarge,
                            color = when {
                                airing -> tokens.colors.accent
                                past -> tokens.colors.textMuted
                                else -> tokens.colors.textPrimary
                            },
                            fontWeight = if (airing) FontWeight.SemiBold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = liveTvEpgTimeRange(program),
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.colors.textMuted.copy(alpha = if (past) 0.6f else 1f),
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                        )
                    }
                }
                LiveTvGuideNowLine(windowStart = windowStart, nowMillis = nowMillis)
            }
        }
    }
}
