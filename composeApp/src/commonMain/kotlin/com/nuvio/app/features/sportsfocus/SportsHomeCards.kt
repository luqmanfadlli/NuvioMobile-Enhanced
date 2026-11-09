package com.nuvio.app.features.sportsfocus

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.sports.ChannelMatch
import com.nuvio.app.features.sports.FollowedTeam
import com.nuvio.app.features.sports.Side
import com.nuvio.app.features.sports.Sport
import com.nuvio.app.features.sports.SportsCatalog
import com.nuvio.app.features.sports.SportsDates
import com.nuvio.app.features.sports.SportsEvent
import com.nuvio.app.features.sports.compat.LiveTvClock
import com.nuvio.app.features.sports.ui.LeagueMark
import com.nuvio.app.features.sports.ui.SportsLiveRed
import com.nuvio.app.features.sports.ui.SportsActions
import com.nuvio.app.features.sports.ui.StateBadge
import com.nuvio.app.features.sports.ui.TeamLogo
import com.nuvio.app.features.sports.ui.eventHeadline
import com.nuvio.app.features.sports.ui.startLabel

/** A tap on a game plays its first matched channel; with none matched it opens the game's page instead. */
internal fun SportsActions.watchOrOpen(ev: SportsEvent, @Suppress("UNUSED_PARAMETER") matches: Map<String, List<ChannelMatch>> = emptyMap()) {
    com.nuvio.app.features.sports.SportsWatch.watch(ev, this)
}

/** "Good evening" and a line under it, in place of a bare "Home" title. */
@Composable
internal fun HomeGreeting(modifier: Modifier = Modifier) {
    val tokens = MaterialTheme.nuvio
    val hour = remember { ((LiveTvClock.nowMs() - SportsDates.dayStart(0)) / 3_600_000L).toInt() }
    val greeting = when {
        hour in 5..11 -> "Good morning"
        hour in 12..17 -> "Good afternoon"
        else -> "Good evening"
    }
    Column(modifier.statusBarsPadding().padding(top = 8.dp)) {
        Text(
            greeting,
            color = tokens.colors.textPrimary,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, end = 72.dp, top = 8.dp, bottom = 12.dp),
        )
    }
}

/** The game to watch right now, large: both teams, the score, and how many of your channels have it. */
@Composable
internal fun FeaturedGameCard(
    ev: SportsEvent,
    finding: Boolean,
    hideScores: Boolean,
    use24h: Boolean,
    onClick: () -> Unit,
    onGameCentre: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = MaterialTheme.nuvio
    val league = SportsCatalog.league(ev.leagueId)
    val sides = ev.ordered(league?.sport ?: Sport.OTHER)
    val showScore = ev.hasScore && !hideScores
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(NuvioTokens.Radius.xl)).background(tokens.colors.surface)
            .clickable(onClick = onClick).padding(NuvioTokens.Space.s16),
        verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s12),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8)) {
                LeagueMark(league, 18.dp)
                Text(league?.name.orEmpty(), style = MaterialTheme.typography.labelLarge, color = tokens.colors.textMuted, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            StateBadge(ev, use24h)
        }
        if (sides.size == 2) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                FeaturedSide(sides[0], Modifier.weight(1f))
                Text(
                    if (showScore) "${sides[0].score ?: "–"} – ${sides[1].score ?: "–"}" else startLabel(ev, use24h),
                    style = if (showScore) MaterialTheme.typography.displayMedium else MaterialTheme.typography.titleLarge,
                    color = tokens.colors.textPrimary,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = NuvioTokens.Space.s8),
                )
                FeaturedSide(sides[1], Modifier.weight(1f))
            }
        } else {
            Text(eventHeadline(ev, league), style = MaterialTheme.typography.titleLarge, color = tokens.colors.textPrimary, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        val status = if (!hideScores) ev.status else null
        if (status != null) {
            Text(status, style = MaterialTheme.typography.bodyLarge, color = tokens.colors.textSecondary, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s12)) {
            Row(
                Modifier.clip(RoundedCornerShape(999.dp)).background(tokens.colors.accent).clickable(onClick = onClick)
                    .padding(start = 14.dp, end = 20.dp, top = 11.dp, bottom = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (finding) {
                    androidx.compose.material3.CircularProgressIndicator(color = tokens.colors.onAccent, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                } else {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = tokens.colors.onAccent, modifier = Modifier.size(22.dp))
                }
                Text(
                    if (ev.state == com.nuvio.app.features.sports.GameState.LIVE) "Watch Live" else "Watch",
                    style = MaterialTheme.typography.titleSmall,
                    color = tokens.colors.onAccent,
                    fontWeight = FontWeight.Bold,
                )
            }
            Box(Modifier.weight(1f))
            Icon(
                Icons.Rounded.Insights,
                contentDescription = "Game Centre",
                tint = tokens.colors.accent,
                modifier = Modifier.size(44.dp).clip(CircleShape).background(tokens.colors.accent.copy(alpha = 0.14f)).clickable(onClick = onGameCentre).padding(11.dp),
            )
        }
    }
}

@Composable
internal fun FeaturedSide(side: Side, modifier: Modifier = Modifier) {
    val tokens = MaterialTheme.nuvio
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6)) {
        TeamLogo(side.team, 52.dp)
        Text(
            side.team.fullName.ifBlank { side.team.name },
            style = MaterialTheme.typography.titleSmall,
            color = tokens.colors.textPrimary,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** What your teams are doing today, or a way to the schedule when they are not playing. */
@Composable
internal fun SportsDayCard(
    games: List<SportsEvent>,
    nextUp: List<Pair<FollowedTeam, SportsEvent>>,
    now: Long,
    use24h: Boolean,
    onOpenGame: (SportsEvent) -> Unit,
    onSeeSchedule: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = MaterialTheme.nuvio
    val live = com.nuvio.app.features.sports.GameState.LIVE
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(NuvioTokens.Radius.xl)).background(tokens.colors.surface).padding(NuvioTokens.Space.s16),
        verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s10),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s10)) {
            Icon(Icons.Rounded.CalendarMonth, contentDescription = null, tint = tokens.colors.textSecondary, modifier = Modifier.size(26.dp))
            Text("Your Sports Day", style = MaterialTheme.typography.titleLarge, color = tokens.colors.textPrimary, fontWeight = FontWeight.Bold)
        }
        if (games.isEmpty()) {
            Text("No games today", style = MaterialTheme.typography.titleMedium, color = tokens.colors.textMuted)
        } else {
            games.take(4).forEach { ev ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(NuvioTokens.Radius.md)).clickable { onOpenGame(ev) }.padding(vertical = NuvioTokens.Space.s4),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s12),
                ) {
                    if (ev.state == live) Box(Modifier.size(8.dp).clip(CircleShape).background(SportsLiveRed))
                    Text(
                        if (ev.state == live) "LIVE" else startLabel(ev, use24h),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (ev.state == live) SportsLiveRed else tokens.colors.accent,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(ev.title, style = MaterialTheme.typography.bodyLarge, color = tokens.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                }
            }
        }
        if (nextUp.isNotEmpty()) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(tokens.colors.borderSubtle))
            Text(if (nextUp.size == 1) "NEXT MATCH" else "NEXT MATCHES", style = MaterialTheme.typography.labelLarge, color = tokens.colors.textMuted, fontWeight = FontWeight.Bold)
            nextUp.forEach { (team, ev) ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(NuvioTokens.Radius.md)).clickable { onOpenGame(ev) }.padding(vertical = NuvioTokens.Space.s4),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s12),
                ) {
                    Box(Modifier.size(38.dp).clip(CircleShape).background(tokens.colors.surfaceElevated), contentAlignment = Alignment.Center) {
                        team.logo?.let { coil3.compose.AsyncImage(model = it, contentDescription = null, modifier = Modifier.size(26.dp)) }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(ev.title, style = MaterialTheme.typography.titleSmall, color = tokens.colors.textPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (ev.state == live) "Live now" else listOfNotNull(
                                com.nuvio.app.features.sports.ui.dayWord(ev.startMs, now),
                                if (ev.hasTime) startLabel(ev, use24h) else null,
                                if (ev.hasTime) com.nuvio.app.features.sports.ui.countdownLabel(ev.startMs, now) else null,
                            ).joinToString(" \u00B7 "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (ev.state == live) SportsLiveRed else tokens.colors.textMuted,
                        )
                    }
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = tokens.colors.textMuted, modifier = Modifier.size(20.dp))
                }
            }
        }
        Text(
            "See upcoming schedule",
            style = MaterialTheme.typography.titleMedium,
            color = tokens.colors.accent,
            modifier = Modifier.clickable(onClick = onSeeSchedule).padding(top = NuvioTokens.Space.s2),
        )
    }
}
