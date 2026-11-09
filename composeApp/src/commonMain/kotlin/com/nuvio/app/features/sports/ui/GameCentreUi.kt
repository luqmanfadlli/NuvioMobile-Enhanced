package com.nuvio.app.features.sports.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.Chip
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.sports.BoxGroup
import com.nuvio.app.features.sports.BoxTeam
import com.nuvio.app.features.sports.CentreEvent
import com.nuvio.app.features.sports.CentreLeader
import com.nuvio.app.features.sports.ChannelMatch
import com.nuvio.app.features.sports.GameCentre
import com.nuvio.app.features.sports.GameExtras
import com.nuvio.app.features.sports.GameState
import com.nuvio.app.features.sports.Side
import com.nuvio.app.features.sports.SportsEvent
import com.nuvio.app.features.sports.StatLine

internal enum class CentreTab(val label: String) { Overview("Overview"), PlayByPlay("Play-by-Play"), BoxScore("Box Score"), Table("Table") }

// ------------------------------------------------------------------------------------------ hero

/** League, game clock, both teams with their shots, and the score in the middle. */
@Composable
internal fun GameCentreHero(
    ev: SportsEvent,
    sides: List<Side>,
    centre: GameCentre?,
    showScore: Boolean,
    statusText: String,
    modifier: Modifier = Modifier,
) {
    val tokens = MaterialTheme.nuvio
    val live = ev.state == GameState.LIVE
    Column(modifier.fillMaxWidth().padding(vertical = NuvioTokens.Space.s12), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (live) Box(Modifier.size(7.dp).clip(CircleShape).background(SportsLiveRed))
            Text(statusText, style = MaterialTheme.typography.titleMedium, color = if (live) SportsLiveRed else tokens.colors.textSecondary, fontWeight = FontWeight.Bold)
        }
        if (sides.size == 2) {
            Row(Modifier.fillMaxWidth().padding(top = NuvioTokens.Space.s6), verticalAlignment = Alignment.CenterVertically) {
                HeroTeam(sides[0], centre?.shots?.get(sides[0].team.id), Modifier.weight(1f))
                Text(
                    if (showScore) "${sides[0].score ?: "–"}–${sides[1].score ?: "–"}" else "vs",
                    style = MaterialTheme.typography.displayMedium,
                    color = tokens.colors.textPrimary,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = NuvioTokens.Space.s8),
                )
                HeroTeam(sides[1], centre?.shots?.get(sides[1].team.id), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun HeroTeam(side: Side, shots: String?, modifier: Modifier = Modifier) {
    val tokens = MaterialTheme.nuvio
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        TeamLogo(side.team, 64.dp)
        Text(side.team.abbr.ifBlank { side.team.name }, style = MaterialTheme.typography.titleLarge, color = tokens.colors.textSecondary, fontWeight = FontWeight.Bold, maxLines = 1)
        (shots ?: side.record)?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = tokens.colors.textMuted, fontWeight = FontWeight.SemiBold, maxLines = 1) }
    }
}

// ------------------------------------------------------------------------------------------ play

/** One big Play button: it starts the best matched stream (or lists them, as you set it up in settings). */
@Composable
internal fun PlayButton(match: ChannelMatch?, finding: Boolean, choosing: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val tokens = MaterialTheme.nuvio
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6)) {
        Row(
            Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(NuvioTokens.Radius.xl)).background(tokens.colors.accent).clickable(onClick = onClick),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (finding) {
                androidx.compose.material3.CircularProgressIndicator(color = tokens.colors.onAccent, strokeWidth = 3.dp, modifier = Modifier.size(30.dp))
            } else {
                Icon(Icons.Rounded.PlayArrow, contentDescription = "Play", tint = tokens.colors.onAccent, modifier = Modifier.size(40.dp))
            }
            Text("Play", style = MaterialTheme.typography.titleLarge, color = tokens.colors.onAccent, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, end = 8.dp))
        }
        val caption = when {
            choosing -> "Choose from your matched streams"
            match != null -> listOfNotNull(match.channel.name, match.quality).joinToString(" \u00B7 ")
            else -> null
        }
        if (caption != null) Text(caption, style = MaterialTheme.typography.bodySmall, color = tokens.colors.textMuted, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
    }
}

// ------------------------------------------------------------------------------------------ tabs

@Composable
internal fun CentreTabBar(selected: CentreTab, onSelect: (CentreTab) -> Unit, modifier: Modifier = Modifier, tabs: List<CentreTab> = CentreTab.entries) {
    val tokens = MaterialTheme.nuvio
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s24)) {
            tabs.forEach { tab ->
                val on = tab == selected
                Column(Modifier.clickable { onSelect(tab) }.padding(top = NuvioTokens.Space.s8), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(tab.label, style = MaterialTheme.typography.titleMedium, color = if (on) tokens.colors.textPrimary else tokens.colors.textMuted, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium)
                    Box(Modifier.padding(top = 8.dp).height(3.dp).width(if (on) 36.dp else 0.dp).clip(RoundedCornerShape(2.dp)).background(tokens.colors.accent))
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(tokens.colors.borderSubtle))
    }
}

// ------------------------------------------------------------------------------------------ overview

internal fun LazyListScope.overviewItems(ev: SportsEvent, centre: GameCentre?, extras: GameExtras?, sides: List<Side>, loading: Boolean, sport: com.nuvio.app.features.sports.Sport, hide: Boolean) {
    if (centre == null) {
        item(key = "ov-loading") { CentreNote(if (loading) "Loading the game…" else "No game details yet.") }
        return
    }
    if (sides.size == 2 && sides.any { it.periods.isNotEmpty() } && !hide) item(key = "ov-line") { LineScore(sides, sport) }
    if (centre.scoring.isNotEmpty() && !hide) {
        item(key = "ov-scoring-h") { CentreSection("SCORING") }
        items(centre.scoring, key = { "ov-s-" + it.id }) { e -> TimelineRow(e) }
    } else if (ev.state != GameState.PRE) {
        item(key = "ov-no-scoring") { CentreNote("No scoring yet.") }
    }
    if (centre.leaders.isNotEmpty() && !hide) {
        item(key = "ov-leaders-h") { CentreSection("GAME LEADERS") }
        item(key = "ov-leaders") { LeadersRow(centre.leaders) }
    }
    val stats = extras?.stats.orEmpty()
    if (stats.isNotEmpty() && !hide && sides.size == 2) {
        item(key = "ov-stats-h") { CentreSection("TEAM STATS") }
        item(key = "ov-stats") { StatComparison(stats.take(8), sides) }
    }
    if (centre.venue != null || centre.attendance != null || centre.officials.isNotEmpty()) {
        item(key = "ov-info-h") { CentreSection("GAME INFO") }
        item(key = "ov-info") { GameInfoCard(centre) }
    }
}

@Composable
internal fun CentreSection(title: String, modifier: Modifier = Modifier) {
    val tokens = MaterialTheme.nuvio
    Text(title, style = MaterialTheme.typography.labelLarge, color = tokens.colors.textMuted, fontWeight = FontWeight.Bold, modifier = modifier.padding(top = NuvioTokens.Space.s8))
}

@Composable
internal fun CentreNote(text: String) {
    val tokens = MaterialTheme.nuvio
    Text(text, style = MaterialTheme.typography.bodyMedium, color = tokens.colors.textMuted, modifier = Modifier.padding(vertical = NuvioTokens.Space.s16))
}

@Composable
private fun LineScore(sides: List<Side>, sport: com.nuvio.app.features.sports.Sport) {
    val tokens = MaterialTheme.nuvio
    val n = sides.maxOf { it.periods.size }
    val heading = when (sport) {
        com.nuvio.app.features.sports.Sport.BASEBALL -> "Innings"
        com.nuvio.app.features.sports.Sport.HOCKEY -> "Periods"
        com.nuvio.app.features.sports.Sport.FOOTBALL, com.nuvio.app.features.sports.Sport.RUGBY -> "Halves"
        else -> "Quarters"
    }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(NuvioTokens.Radius.lg)).background(tokens.colors.surface).padding(NuvioTokens.Space.s12), verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6)) {
        Row {
            Text(heading, style = MaterialTheme.typography.labelMedium, color = tokens.colors.textMuted, modifier = Modifier.weight(1f))
            (1..n).forEach { Text("$it", style = MaterialTheme.typography.labelMedium, color = tokens.colors.textMuted, modifier = Modifier.width(30.dp), textAlign = TextAlign.Center) }
            Text("T", style = MaterialTheme.typography.labelMedium, color = tokens.colors.textMuted, modifier = Modifier.width(36.dp), textAlign = TextAlign.Center)
        }
        sides.forEach { s ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.team.abbr.ifBlank { s.team.name }, style = MaterialTheme.typography.titleSmall, color = tokens.colors.textPrimary, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1)
                (0 until n).forEach { i -> Text(s.periods.getOrNull(i) ?: "-", style = MaterialTheme.typography.bodyMedium, color = tokens.colors.textSecondary, modifier = Modifier.width(30.dp), textAlign = TextAlign.Center) }
                Text(s.score ?: "-", style = MaterialTheme.typography.titleSmall, color = tokens.colors.textPrimary, fontWeight = FontWeight.Black, modifier = Modifier.width(36.dp), textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun Headshot(url: String?, size: androidx.compose.ui.unit.Dp) {
    val tokens = MaterialTheme.nuvio
    Box(Modifier.size(size).clip(CircleShape).background(tokens.colors.surfaceElevated)) {
        if (url != null) AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(size))
    }
}

/** A goal or a basket on the timeline: when, who, how, and the score after it. */
@Composable
internal fun TimelineRow(e: CentreEvent) {
    val tokens = MaterialTheme.nuvio
    Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s12)) {
        Column(Modifier.width(10.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.padding(top = 6.dp).size(9.dp).clip(CircleShape).background(tokens.colors.accent))
            Box(Modifier.padding(top = 4.dp).width(1.dp).weight(1f).background(tokens.colors.borderSubtle))
        }
        Column(Modifier.weight(1f).padding(bottom = NuvioTokens.Space.s16), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    listOfNotNull(e.periodHeading.take(3).let { h -> if (h.isBlank()) null else h }, e.clock).joinToString(" · "),
                    style = MaterialTheme.typography.titleSmall, color = tokens.colors.textSecondary, fontWeight = FontWeight.SemiBold,
                )
                e.badges.forEach { b -> Text(b, style = MaterialTheme.typography.labelMedium, color = tokens.colors.textSecondary, fontWeight = FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(5.dp)).background(tokens.colors.surfaceElevated).padding(horizontal = 6.dp, vertical = 2.dp)) }
                Box(Modifier.weight(1f))
                e.teamLogo?.let { AsyncImage(model = it, contentDescription = null, modifier = Modifier.size(18.dp)) }
                e.scoreAfter?.let { Text(it, style = MaterialTheme.typography.headlineSmall, color = tokens.colors.textPrimary, fontWeight = FontWeight.Black) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s12), verticalAlignment = Alignment.Top) {
                if (e.headshot != null) Headshot(e.headshot, 52.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(e.headline, style = MaterialTheme.typography.titleLarge, color = tokens.colors.textPrimary, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    e.detail?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = tokens.colors.textSecondary, maxLines = 2) }
                    e.secondary?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = tokens.colors.textMuted, maxLines = 2) }
                }
            }
        }
    }
}

@Composable
private fun LeadersRow(leaders: List<CentreLeader>) {
    val tokens = MaterialTheme.nuvio
    LazyRow(horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s10)) {
        items(leaders.take(10), key = { it.category + it.name + it.teamAbbr }) { l ->
            Column(
                Modifier.width(168.dp).clip(RoundedCornerShape(NuvioTokens.Radius.lg)).background(tokens.colors.surface).padding(NuvioTokens.Space.s12),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(l.category.uppercase(), style = MaterialTheme.typography.labelSmall, color = tokens.colors.textMuted, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Headshot(l.headshot, 44.dp)
                    Text(l.value, style = MaterialTheme.typography.titleLarge, color = tokens.colors.textPrimary, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Text(l.name, style = MaterialTheme.typography.titleSmall, color = tokens.colors.textPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(l.teamAbbr, l.position).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = tokens.colors.textMuted, maxLines = 1)
            }
        }
    }
}

/** Two values with a bar under each label showing how the two sides compare. */
@Composable
internal fun StatComparison(stats: List<StatLine>, sides: List<Side>) {
    val tokens = MaterialTheme.nuvio
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(NuvioTokens.Radius.lg)).background(tokens.colors.surface).padding(NuvioTokens.Space.s16), verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s14)) {
        Row {
            Text(sides[0].team.abbr, style = MaterialTheme.typography.titleSmall, color = tokens.colors.textPrimary, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
            Text(sides[1].team.abbr, style = MaterialTheme.typography.titleSmall, color = tokens.colors.textPrimary, fontWeight = FontWeight.Black)
        }
        stats.forEach { s ->
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(s.away, style = MaterialTheme.typography.titleSmall, color = tokens.colors.textPrimary, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(s.label, style = MaterialTheme.typography.bodySmall, color = tokens.colors.textMuted, textAlign = TextAlign.Center)
                    Text(s.home, style = MaterialTheme.typography.titleSmall, color = tokens.colors.textPrimary, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                }
                val a = s.awayValue
                val h = s.homeValue
                if (a != null && h != null && a + h > 0) {
                    val share = (a / (a + h)).toFloat().coerceIn(0.04f, 0.96f)
                    Row(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        Box(Modifier.weight(share).fillMaxHeight().background(if (a >= h) tokens.colors.accent else tokens.colors.textMuted.copy(alpha = 0.45f)))
                        Box(Modifier.weight(1f - share).fillMaxHeight().background(if (h >= a) tokens.colors.accent else tokens.colors.textMuted.copy(alpha = 0.45f)))
                    }
                }
            }
        }
    }
}

@Composable
private fun GameInfoCard(c: GameCentre) {
    val tokens = MaterialTheme.nuvio
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(NuvioTokens.Radius.lg)).background(tokens.colors.surface).padding(NuvioTokens.Space.s16), verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s10)) {
        c.venue?.let { InfoLine("Venue", listOfNotNull(it, c.city).joinToString(", ")) }
        c.attendance?.let { InfoLine("Attendance", groupThousands(it)) }
        c.officials.take(5).forEach { InfoLine(it.role, it.name) }
    }
}

private fun groupThousands(n: Int): String = n.toString().reversed().chunked(3).joinToString(",").reversed()

@Composable
private fun InfoLine(label: String, value: String) {
    val tokens = MaterialTheme.nuvio
    Row(verticalAlignment = Alignment.Top) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = tokens.colors.textMuted, modifier = Modifier.width(110.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = tokens.colors.textPrimary, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
    }
}

// ------------------------------------------------------------------------------------------ play by play

internal fun LazyListScope.playItems(centre: GameCentre?, filter: String, onFilter: (String) -> Unit, loading: Boolean, hide: Boolean) {
    if (centre == null || centre.plays.isEmpty() || hide) {
        item(key = "pb-empty") { CentreNote(if (hide) "Scores are hidden." else if (loading) "Loading the play-by-play…" else "The play-by-play starts with the game.") }
        return
    }
    if (centre.filters.size > 1) {
        item(key = "pb-filters") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8)) {
                items(centre.filters, key = { it }) { f -> Chip(f, selected = filter == f, onClick = { onFilter(f) }) }
            }
        }
    }
    val shown = centre.plays.asReversed().let { all -> if (filter == "All") all else all.filter { it.category == filter } }
    if (shown.isEmpty()) {
        item(key = "pb-none") { CentreNote("Nothing here yet.") }
        return
    }
    var last: String? = null
    shown.forEachIndexed { i, e ->
        if (e.periodHeading != last) {
            last = e.periodHeading
            item(key = "pb-h-$i-${e.periodHeading}") { PeriodHeading(e.periodHeading) }
        }
        item(key = "pb-" + i + "-" + e.id) { PlayCard(e) }
    }
}

@Composable
private fun PeriodHeading(text: String) {
    val tokens = MaterialTheme.nuvio
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(NuvioTokens.Radius.md)).background(tokens.colors.surfaceElevated).padding(vertical = NuvioTokens.Space.s10), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.titleSmall, color = tokens.colors.textPrimary, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PlayCard(e: CentreEvent) {
    val tokens = MaterialTheme.nuvio
    val accent = if (e.scoring) tokens.colors.accent else tokens.colors.borderSubtle
    Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s10)) {
        Column(Modifier.width(14.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.padding(top = 14.dp).size(if (e.scoring) 12.dp else 8.dp).clip(CircleShape).background(if (e.scoring) tokens.colors.accent else tokens.colors.textMuted.copy(alpha = 0.5f)))
            Box(Modifier.width(1.dp).weight(1f).background(tokens.colors.borderSubtle))
        }
        Column(
            Modifier.weight(1f).padding(bottom = NuvioTokens.Space.s8).clip(RoundedCornerShape(NuvioTokens.Radius.lg)).background(tokens.colors.surface)
                .then(if (e.scoring) Modifier.border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(NuvioTokens.Radius.lg)) else Modifier)
                .padding(NuvioTokens.Space.s12),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(e.category.uppercase().removeSuffix("S").ifBlank { "PLAY" }.let { if (e.category == "Other") "PLAY" else it }, style = MaterialTheme.typography.labelMedium, color = if (e.scoring) tokens.colors.accent else tokens.colors.textMuted, fontWeight = FontWeight.Bold)
                e.teamLogo?.let { AsyncImage(model = it, contentDescription = null, modifier = Modifier.size(16.dp)) }
                e.teamAbbr?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = tokens.colors.textSecondary, fontWeight = FontWeight.Bold) }
                Box(Modifier.weight(1f))
                e.clock?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = tokens.colors.textSecondary) }
                e.scoreAfter?.let { Text(it, style = MaterialTheme.typography.titleSmall, color = tokens.colors.textPrimary, fontWeight = FontWeight.Black, modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(tokens.colors.surfaceElevated).padding(horizontal = 8.dp, vertical = 3.dp)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s10), verticalAlignment = Alignment.Top) {
                if (e.headshot != null && e.scoring) Headshot(e.headshot, 44.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(e.headline, style = if (e.scoring) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium, color = tokens.colors.textPrimary, fontWeight = if (e.scoring) FontWeight.Bold else FontWeight.Normal, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    e.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = tokens.colors.textSecondary, maxLines = 2) }
                    e.secondary?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = tokens.colors.textMuted, maxLines = 2) }
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------ box score

internal fun LazyListScope.boxItems(centre: GameCentre?, extras: GameExtras?, sides: List<Side>, team: Int, onTeam: (Int) -> Unit, loading: Boolean, hide: Boolean) {
    if (hide) { item(key = "bx-hide") { CentreNote("Scores are hidden.") }; return }
    val stats = extras?.stats.orEmpty()
    if (stats.isNotEmpty() && sides.size == 2) {
        item(key = "bx-stats") { TeamStatsTable(stats, sides) }
    }
    val box = centre?.box.orEmpty()
    if (box.isEmpty()) {
        if (stats.isEmpty()) item(key = "bx-empty") { CentreNote(if (loading) "Loading the box score…" else "The box score fills in as the game goes.") }
        return
    }
    item(key = "bx-teams") {
        Row(horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8)) {
            box.forEachIndexed { i, t -> Chip(t.abbr, selected = team == i, onClick = { onTeam(i) }) }
        }
    }
    val chosen = box.getOrNull(team.coerceIn(0, box.lastIndex)) ?: return
    chosen.groups.forEachIndexed { gi, g -> item(key = "bx-g-${chosen.abbr}-$gi") { PlayerTable(g) } }
}

/** "TBL · Team stats · WPG": the numbers side by side, as the Box Score tab opens. */
@Composable
private fun TeamStatsTable(stats: List<StatLine>, sides: List<Side>) {
    val tokens = MaterialTheme.nuvio
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(NuvioTokens.Radius.lg)).background(tokens.colors.surface).padding(NuvioTokens.Space.s16), verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s12)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(sides[0].team.abbr, style = MaterialTheme.typography.titleLarge, color = tokens.colors.textPrimary, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
            Text("Team stats", style = MaterialTheme.typography.titleMedium, color = tokens.colors.textSecondary, fontWeight = FontWeight.SemiBold)
            Text(sides[1].team.abbr, style = MaterialTheme.typography.titleLarge, color = tokens.colors.textPrimary, fontWeight = FontWeight.Black, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        }
        stats.forEach { s ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.away, style = MaterialTheme.typography.titleMedium, color = tokens.colors.textPrimary, modifier = Modifier.weight(1f))
                Text(s.label, style = MaterialTheme.typography.bodyMedium, color = tokens.colors.textMuted, textAlign = TextAlign.Center, modifier = Modifier.weight(1.4f))
                Text(s.home, style = MaterialTheme.typography.titleMedium, color = tokens.colors.textPrimary, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
            }
        }
    }
}

private val ROW_HEIGHT = 40.dp
private val STAT_WIDTH = 46.dp

/** One group of players (forwards, goalies, starters…) with the names held still while the numbers scroll. */
@Composable
private fun PlayerTable(group: BoxGroup) {
    val tokens = MaterialTheme.nuvio
    val scroll = rememberScrollState()
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(NuvioTokens.Radius.lg)).background(tokens.colors.surface).padding(vertical = NuvioTokens.Space.s8)) {
        Text(group.title.uppercase(), style = MaterialTheme.typography.labelLarge, color = tokens.colors.textMuted, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = NuvioTokens.Space.s12, vertical = NuvioTokens.Space.s4))
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.width(132.dp)) {
                Box(Modifier.height(ROW_HEIGHT))
                group.rows.forEach { r ->
                    Row(Modifier.height(ROW_HEIGHT).padding(start = NuvioTokens.Space.s12), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column {
                            Text(r.name, style = MaterialTheme.typography.bodyMedium, color = tokens.colors.textPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val sub = listOfNotNull(r.position, r.jersey?.let { "#$it" }).joinToString(" ")
                            if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.labelSmall, color = tokens.colors.textMuted, maxLines = 1)
                        }
                    }
                }
            }
            Column(Modifier.weight(1f).horizontalScroll(scroll)) {
                Row(Modifier.height(ROW_HEIGHT), verticalAlignment = Alignment.CenterVertically) {
                    group.labels.forEach { l -> Text(l, style = MaterialTheme.typography.labelMedium, color = tokens.colors.textMuted, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.width(widthFor(l))) }
                }
                group.rows.forEach { r ->
                    Row(Modifier.height(ROW_HEIGHT), verticalAlignment = Alignment.CenterVertically) {
                        group.labels.forEachIndexed { i, l -> Text(r.stats.getOrNull(i).orEmpty(), style = MaterialTheme.typography.bodyMedium, color = tokens.colors.textPrimary, textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.width(widthFor(l))) }
                    }
                }
            }
        }
    }
}

private fun widthFor(label: String) = when {
    label.length >= 5 || label == "FO%" || label == "PC-ST" -> 64.dp
    label in setOf("3PT", "FG", "FT", "H-AB", "TOI", "PPTOI", "SHTOI", "ESTOI", "C/ATT", "SV%", "MIN") -> 58.dp
    else -> STAT_WIDTH
}

// ------------------------------------------------------------------------------------------ table

/** The league table as it stands, with this game's teams picked out, and a way to the full table. */
internal fun LazyListScope.tableItems(extras: GameExtras?, onOpenFull: (() -> Unit)?, loading: Boolean) {
    val rows = extras?.table.orEmpty()
    if (rows.isEmpty()) {
        item(key = "tb-empty") { CentreNote(if (loading) "Loading the table\u2026" else "No table for this competition yet.") }
    } else {
        item(key = "tb-title") { CentreSection((extras?.tableTitle ?: "Standings").uppercase()) }
        item(key = "tb-table") { LeagueTableCard(extras!!) }
    }
    if (onOpenFull != null) {
        item(key = "tb-full") {
            val tokens = MaterialTheme.nuvio
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(NuvioTokens.Radius.lg)).background(tokens.colors.surface).clickable(onClick = onOpenFull).padding(NuvioTokens.Space.s16),
                contentAlignment = Alignment.Center,
            ) { Text("View full table", style = MaterialTheme.typography.titleSmall, color = tokens.colors.accent, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun LeagueTableCard(x: GameExtras) {
    val tokens = MaterialTheme.nuvio
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(NuvioTokens.Radius.lg)).background(tokens.colors.surface).padding(NuvioTokens.Space.s12), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row {
            Text("", modifier = Modifier.width(30.dp))
            Text("", modifier = Modifier.weight(1f))
            x.tableHead.forEach { Text(it, style = MaterialTheme.typography.labelMedium, color = tokens.colors.textMuted, fontWeight = FontWeight.Bold, modifier = Modifier.width(46.dp), textAlign = TextAlign.End) }
        }
        x.table.forEach { r ->
            Row(
                Modifier.fillMaxWidth().then(if (r.mine) Modifier.clip(RoundedCornerShape(8.dp)).background(tokens.colors.accent.copy(alpha = 0.14f)) else Modifier).padding(vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(r.pos, style = MaterialTheme.typography.labelLarge, color = tokens.colors.textMuted, modifier = Modifier.width(30.dp).padding(start = 6.dp))
                Text(r.team, style = MaterialTheme.typography.bodyMedium, color = if (r.mine) tokens.colors.accent else tokens.colors.textPrimary, fontWeight = if (r.mine) FontWeight.Bold else FontWeight.Medium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                listOf(r.a, r.b, r.c).forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = tokens.colors.textSecondary, modifier = Modifier.width(46.dp), textAlign = TextAlign.End) }
            }
        }
    }
}
