package com.nuvio.app.features.sportsfocus

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.Chip
import com.nuvio.app.core.ui.NuvioScreenHeader
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.core.ui.nuvioSafeBottomPadding
import com.nuvio.app.features.livetv.LiveTvChannel
import com.nuvio.app.features.livetv.LiveTvEpgProgram
import com.nuvio.app.features.livetv.LiveTvRepository
import com.nuvio.app.features.livetv.LiveTvScreen
import com.nuvio.app.features.livetv.LiveTvVodPlayback
import com.nuvio.app.features.livetv.rememberLiveTvEpgNowMillis
import com.nuvio.app.features.sports.ChannelMatch
import com.nuvio.app.features.sports.GameState
import com.nuvio.app.features.sports.Sport
import com.nuvio.app.features.sports.SportsCatalog
import com.nuvio.app.features.sports.SportsEvent
import com.nuvio.app.features.sports.SportsReminders
import com.nuvio.app.features.sports.SportsRepository
import com.nuvio.app.features.sports.clockText
import com.nuvio.app.features.sports.compat.LiveTvClock
import com.nuvio.app.features.sports.ui.LeagueMark
import com.nuvio.app.features.sports.ui.SportsActions
import com.nuvio.app.features.sports.ui.StateBadge
import com.nuvio.app.features.sports.ui.TeamLogo
import com.nuvio.app.features.sports.ui.cornerStatus
import com.nuvio.app.features.sports.ui.isMine
import com.nuvio.app.features.sports.ui.use24h
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

internal enum class LiveSegment(val label: String) { Channels("Channels"), Guide("Guide"), Sports("Sports") }

/** A three-way switch under the Live title, the way StadiaTV lays it out. */
@Composable
internal fun LiveSegmentedControl(selected: LiveSegment, onSelect: (LiveSegment) -> Unit, modifier: Modifier = Modifier) {
    val tokens = MaterialTheme.nuvio
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(NuvioTokens.Radius.lg)).background(tokens.colors.surface).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        LiveSegment.entries.forEach { segment ->
            val on = segment == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(NuvioTokens.Radius.md))
                    .background(if (on) tokens.colors.surfaceElevated else Color.Transparent)
                    .clickable { onSelect(segment) }.padding(vertical = NuvioTokens.Space.s8),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    segment.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (on) tokens.colors.textPrimary else tokens.colors.textMuted,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                )
            }
        }
    }
}

/** Live: your channels, the TV guide, and the games on now, one switch apart. */
@Composable
fun LiveHub(
    onChannelClick: (LiveTvChannel) -> Unit,
    sportsActions: SportsActions?,
    modifier: Modifier = Modifier,
    scrollToTopRequests: Flow<Unit> = emptyFlow(),
    onVodClick: (LiveTvVodPlayback) -> Unit = {},
) {
    var name by rememberSaveable { mutableStateOf(LiveSegment.Channels.name) }
    val segment = LiveSegment.entries.firstOrNull { it.name == name } ?: LiveSegment.Channels
    val select: (LiveSegment) -> Unit = { name = it.name }
    when (segment) {
        LiveSegment.Channels -> LiveTvScreen(
            modifier = modifier,
            scrollToTopRequests = scrollToTopRequests,
            onChannelClick = onChannelClick,
            onVodClick = onVodClick,
            segments = { LiveSegmentedControl(segment, select, Modifier.padding(bottom = NuvioTokens.Space.s8)) },
        )
        LiveSegment.Guide -> LiveGuideScreen(segment, select, onChannelClick, modifier)
        LiveSegment.Sports -> if (sportsActions != null) LiveSportsScreen(segment, select, sportsActions, modifier)
    }
}

// ------------------------------------------------------------------------------------------ Guide

private val GUIDE_CHANNEL_WIDTH = 96.dp
private val GUIDE_ROW_HEIGHT = 72.dp
private const val GUIDE_DP_PER_MINUTE = 4f
private const val GUIDE_WINDOW_HOURS = 6
private const val HALF_HOUR_MS = 30 * 60_000L

private data class GuideRow(val channel: LiveTvChannel, val programs: List<LiveTvEpgProgram>)

@Composable
private fun LiveGuideScreen(segment: LiveSegment, onSelect: (LiveSegment) -> Unit, onChannelClick: (LiveTvChannel) -> Unit, modifier: Modifier = Modifier) {
    val tokens = MaterialTheme.nuvio
    LaunchedEffect(Unit) { LiveTvRepository.ensureChannelsLoaded() }
    val ui by LiveTvRepository.uiState.collectAsStateWithLifecycle()
    val now = rememberLiveTvEpgNowMillis()
    val use24h = use24h()
    var mineOnly by rememberSaveable { mutableStateOf(true) }
    var query by rememberSaveable { mutableStateOf("") }
    val windowStart = remember(now / HALF_HOUR_MS) { (now / HALF_HOUR_MS) * HALF_HOUR_MS }
    val windowEnd = windowStart + GUIDE_WINDOW_HOURS * 60 * 60_000L
    val timelineWidth = (GUIDE_WINDOW_HOURS * 60 * GUIDE_DP_PER_MINUTE).dp
    val shared = rememberScrollState()

    val rows = remember(ui.channels, ui.epgPrograms, ui.favoriteChannelIds, ui.lastWatchedChannelId, mineOnly, query, windowStart) {
        val withPrograms = ui.channels.mapNotNull { channel ->
            val programs = ui.epgPrograms[channel.id].orEmpty()
                .filter { it.endMillis > windowStart && it.startMillis < windowEnd }
                .sortedBy { it.startMillis }
            if (programs.isEmpty()) null else GuideRow(channel, programs)
        }
        val mine = withPrograms.filter { it.channel.id in ui.favoriteChannelIds || it.channel.id == ui.lastWatchedChannelId }
        val scoped = if (mineOnly) mine.ifEmpty { withPrograms.take(30) } else withPrograms
        val q = query.trim().lowercase()
        (if (q.isEmpty()) scoped else scoped.filter { row -> row.channel.name.lowercase().contains(q) || row.programs.any { it.title.lowercase().contains(q) } }).take(200)
    }

    Column(modifier.fillMaxSize().background(tokens.colors.background).statusBarsPadding()) {
        NuvioScreenHeader(title = "Live", includeStatusBarPadding = false, modifier = Modifier.padding(horizontal = NuvioTokens.Space.s8))
        LiveSegmentedControl(segment, onSelect, Modifier.padding(horizontal = NuvioTokens.Space.s16))
        Row(
            Modifier.padding(horizontal = NuvioTokens.Space.s16, vertical = NuvioTokens.Space.s8),
            horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8),
        ) {
            Chip("My Guide", selected = mineOnly, onClick = { mineOnly = true })
            Chip("All Channels", selected = !mineOnly, onClick = { mineOnly = false })
        }
        GuideSearchField(query, { query = it }, Modifier.padding(horizontal = NuvioTokens.Space.s16))
        Spacer(Modifier.height(NuvioTokens.Space.s8))
        if (rows.isEmpty()) {
            val message = when {
                ui.isEpgLoading || ui.isLoading -> "Loading the guide…"
                !ui.hasPlaylist -> "Add a playlist in Settings to see its guide."
                ui.epgPrograms.isEmpty() -> "No guide data yet. Add or refresh an EPG source in Live TV settings."
                else -> "Nothing matches in the next $GUIDE_WINDOW_HOURS hours."
            }
            Text(message, style = MaterialTheme.typography.bodyMedium, color = tokens.colors.textMuted, modifier = Modifier.padding(NuvioTokens.Space.s16))
        } else {
            Row(Modifier.fillMaxWidth().height(32.dp)) {
                Text("CH", style = MaterialTheme.typography.labelLarge, color = tokens.colors.textMuted, textAlign = TextAlign.Center, modifier = Modifier.width(GUIDE_CHANNEL_WIDTH).padding(top = 6.dp))
                Box(Modifier.weight(1f).fillMaxHeight().horizontalScroll(shared)) {
                    Row(Modifier.width(timelineWidth).fillMaxHeight()) {
                        repeat(GUIDE_WINDOW_HOURS * 2) { slot ->
                            Text(
                                clockText(windowStart + slot * HALF_HOUR_MS, use24h),
                                style = MaterialTheme.typography.labelLarge,
                                color = tokens.colors.textMuted,
                                modifier = Modifier.width((30 * GUIDE_DP_PER_MINUTE).dp).padding(top = 6.dp, start = 4.dp),
                            )
                        }
                    }
                }
            }
            LazyColumn(contentPadding = PaddingValues(bottom = nuvioSafeBottomPadding(NuvioTokens.Space.s16))) {
                items(rows, key = { it.channel.id }) { row ->
                    Row(Modifier.fillMaxWidth().height(GUIDE_ROW_HEIGHT)) {
                        GuideChannelCell(row.channel, Modifier.width(GUIDE_CHANNEL_WIDTH).fillMaxHeight()) { onChannelClick(row.channel) }
                        Box(Modifier.weight(1f).fillMaxHeight().horizontalScroll(shared)) {
                            Box(Modifier.width(timelineWidth).fillMaxHeight()) {
                                row.programs.forEach { program ->
                                    GuideProgram(program, windowStart, windowEnd, now, use24h) { onChannelClick(row.channel) }
                                }
                                val nowX = ((now - windowStart) / 60_000f * GUIDE_DP_PER_MINUTE).dp
                                Box(Modifier.offset(x = nowX).width(2.dp).fillMaxHeight().background(com.nuvio.app.features.sports.ui.SportsLiveRed))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GuideSearchField(query: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val tokens = MaterialTheme.nuvio
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(NuvioTokens.Radius.lg)).background(tokens.colors.surface)
            .padding(horizontal = NuvioTokens.Space.s12, vertical = NuvioTokens.Space.s10),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8),
    ) {
        Icon(Icons.Rounded.Search, contentDescription = null, tint = tokens.colors.textMuted, modifier = Modifier.size(18.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) Text("Search channels or programmes…", style = MaterialTheme.typography.bodyMedium, color = tokens.colors.textMuted)
            BasicTextField(
                value = query,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(color = tokens.colors.textPrimary, fontSize = MaterialTheme.typography.bodyMedium.fontSize),
                cursorBrush = SolidColor(tokens.colors.accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun GuideChannelCell(channel: LiveTvChannel, modifier: Modifier, onClick: () -> Unit) {
    val tokens = MaterialTheme.nuvio
    Column(
        modifier.clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (channel.logoUrl != null) {
            AsyncImage(model = channel.logoUrl, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(width = 56.dp, height = 30.dp))
        }
        Text(channel.name, style = MaterialTheme.typography.labelSmall, color = tokens.colors.textPrimary, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun GuideProgram(program: LiveTvEpgProgram, windowStart: Long, windowEnd: Long, now: Long, use24h: Boolean, onClick: () -> Unit) {
    val tokens = MaterialTheme.nuvio
    val start = maxOf(program.startMillis, windowStart)
    val end = minOf(program.endMillis, windowEnd)
    val x: Dp = ((start - windowStart) / 60_000f * GUIDE_DP_PER_MINUTE).dp
    val w: Dp = ((end - start) / 60_000f * GUIDE_DP_PER_MINUTE).dp
    val airing = program.isAiringAt(now)
    val shape = RoundedCornerShape(NuvioTokens.Radius.md)
    Box(
        Modifier.offset(x = x).width((w - 3.dp).coerceAtLeast(10.dp)).fillMaxHeight().padding(vertical = 3.dp)
            .clip(shape)
            .background(if (airing) tokens.colors.surfaceElevated else tokens.colors.surface)
            .then(if (airing) Modifier.border(1.dp, tokens.colors.accent.copy(alpha = 0.6f), shape) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(program.title, style = MaterialTheme.typography.labelLarge, color = tokens.colors.textPrimary, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                "${clockText(program.startMillis, use24h)} – ${clockText(program.endMillis, use24h)}",
                style = MaterialTheme.typography.labelSmall,
                color = if (airing) tokens.colors.accent else tokens.colors.textMuted,
                maxLines = 1,
            )
        }
    }
}

// ------------------------------------------------------------------------------------------ Sports

@Composable
private fun LiveSportsScreen(segment: LiveSegment, onSelect: (LiveSegment) -> Unit, actions: SportsActions, modifier: Modifier = Modifier) {
    val tokens = MaterialTheme.nuvio
    val repo = SportsRepository
    LaunchedEffect(Unit) { SportsReminders.start(); LiveTvRepository.ensureLoaded() }
    DisposableEffect(Unit) {
        repo.setViewing(true)
        onDispose { repo.setViewing(false) }
    }
    val events by repo.events.collectAsStateWithLifecycle()
    val known by repo.known.collectAsStateWithLifecycle()
    val follows by repo.follows.collectAsStateWithLifecycle()
    val settings by repo.settings.collectAsStateWithLifecycle()
    val matches by repo.matches.collectAsStateWithLifecycle()
    val use24h = use24h()
    var forYou by rememberSaveable { mutableStateOf(false) }
    var sportName by rememberSaveable { mutableStateOf<String?>(null) }

    val live = remember(events, known) { (events + known).distinctBy { it.id }.filter { it.state == GameState.LIVE } }
    val scoped = remember(live, forYou, follows) { if (forYou) live.filter { isMine(it, follows) } else live }
    val sports = remember(scoped) { scoped.mapNotNull { SportsCatalog.league(it.leagueId)?.sport }.distinct() }
    val shown = remember(scoped, sportName) { if (sportName == null) scoped else scoped.filter { SportsCatalog.league(it.leagueId)?.sport?.name == sportName } }

    LaunchedEffect(shown.take(6).map { it.id }) { shown.take(6).forEach { com.nuvio.app.features.sports.SportsWatch.warm(it) } }
    LazyColumn(
        modifier = modifier.fillMaxSize().background(tokens.colors.background),
        contentPadding = PaddingValues(bottom = nuvioSafeBottomPadding(NuvioTokens.Space.s16)),
        verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s12),
    ) {
        item {
            Column(Modifier.statusBarsPadding()) {
                NuvioScreenHeader(title = "Live", includeStatusBarPadding = false, modifier = Modifier.padding(horizontal = NuvioTokens.Space.s8))
                LiveSegmentedControl(segment, onSelect, Modifier.padding(horizontal = NuvioTokens.Space.s16))
            }
        }
        item {
            Row(Modifier.padding(horizontal = NuvioTokens.Space.s16), horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8)) {
                Chip("For You", selected = forYou, onClick = { forYou = true })
                Chip("All Live", selected = !forYou, onClick = { forYou = false })
            }
        }
        item {
            Text(
                when (scoped.size) { 0 -> "No games live right now"; 1 -> "1 game currently live"; else -> "${scoped.size} games currently live" },
                style = MaterialTheme.typography.titleMedium,
                color = tokens.colors.textMuted,
                modifier = Modifier.padding(horizontal = NuvioTokens.Space.s16),
            )
        }
        if (sports.size > 1) {
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = NuvioTokens.Space.s16), horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8)) {
                    item { Chip("All", selected = sportName == null, onClick = { sportName = null }) }
                    items(sports, key = { it.name }) { s -> Chip(s.label, selected = sportName == s.name, onClick = { sportName = s.name }) }
                }
            }
        }
        items(shown, key = { "live-" + it.id }) { ev ->
            LiveGameCard(
                ev = ev,
                channels = emptyList(),
                hideScores = settings.hideScores,
                use24h = use24h,
                onOpen = { actions.onOpenGame(ev) },
                onWatch = { _ -> actions.watchOrOpen(ev, matches) },
                modifier = Modifier.padding(horizontal = NuvioTokens.Space.s16),
            )
        }
    }
}

@Composable
private fun LiveGameCard(
    ev: SportsEvent,
    channels: List<ChannelMatch>,
    hideScores: Boolean,
    use24h: Boolean,
    onOpen: () -> Unit,
    onWatch: (ChannelMatch?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = MaterialTheme.nuvio
    val league = SportsCatalog.league(ev.leagueId)
    val sides = ev.ordered(league?.sport ?: Sport.OTHER)
    val showScore = ev.hasScore && !hideScores
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(NuvioTokens.Radius.xl)).background(tokens.colors.surface)
            .clickable { onWatch(null) }.padding(NuvioTokens.Space.s16),
        verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s12),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8)) {
                LeagueMark(league, 18.dp)
                Text(league?.short.orEmpty(), style = MaterialTheme.typography.labelLarge, color = tokens.colors.textMuted, fontWeight = FontWeight.SemiBold)
            }
            StateBadge(ev, use24h)
            Icon(
                Icons.Rounded.Insights,
                contentDescription = "Game Centre",
                tint = tokens.colors.accent,
                modifier = Modifier.padding(start = NuvioTokens.Space.s8).size(32.dp).clip(CircleShape)
                    .background(tokens.colors.accent.copy(alpha = 0.14f)).clickable(onClick = onOpen).padding(6.dp),
            )
        }
        if (sides.size == 2) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TeamLogo(sides[0].team, 44.dp)
                    Text(sides[0].team.abbr.ifBlank { sides[0].team.name }, style = MaterialTheme.typography.titleMedium, color = tokens.colors.textPrimary, fontWeight = FontWeight.Bold)
                }
                Text(
                    if (showScore) "${sides[0].score ?: "–"} – ${sides[1].score ?: "–"}" else "vs",
                    style = MaterialTheme.typography.displaySmall,
                    color = tokens.colors.textPrimary,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(horizontal = NuvioTokens.Space.s8),
                )
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TeamLogo(sides[1].team, 44.dp)
                    Text(sides[1].team.abbr.ifBlank { sides[1].team.name }, style = MaterialTheme.typography.titleMedium, color = tokens.colors.textPrimary, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            Text(ev.title, style = MaterialTheme.typography.titleLarge, color = tokens.colors.textPrimary, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Text(
            "LIVE · " + cornerStatus(ev, use24h, hideScores),
            style = MaterialTheme.typography.bodyMedium,
            color = tokens.colors.textMuted,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        val finding by com.nuvio.app.features.sports.SportsWatch.finding.collectAsStateWithLifecycle()
        if (ev.state != GameState.POST) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(NuvioTokens.Radius.lg)).background(tokens.colors.accent).clickable { onWatch(channels.firstOrNull()) }
                    .padding(vertical = NuvioTokens.Space.s12),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (ev.id in finding) androidx.compose.material3.CircularProgressIndicator(color = tokens.colors.onAccent, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                else Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = tokens.colors.onAccent, modifier = Modifier.size(22.dp))
                Text("Watch Live", style = MaterialTheme.typography.titleSmall, color = tokens.colors.onAccent, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}
