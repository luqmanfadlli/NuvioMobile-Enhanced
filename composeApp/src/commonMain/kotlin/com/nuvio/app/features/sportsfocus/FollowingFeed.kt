package com.nuvio.app.features.sportsfocus

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PlayCircle
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.Chip
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.core.ui.nuvioSafeBottomPadding
import com.nuvio.app.core.ui.withDuplicateSafeLazyKeys
import com.nuvio.app.features.news.ArticleRow
import com.nuvio.app.features.news.NewsArticle
import com.nuvio.app.features.news.NewsRanking
import com.nuvio.app.features.sports.GameState
import com.nuvio.app.features.sports.Sport
import com.nuvio.app.features.sports.SportsCatalog
import com.nuvio.app.features.sports.SportsDates
import com.nuvio.app.features.sports.SportsEvent
import com.nuvio.app.features.sports.SportsFollows
import com.nuvio.app.features.sports.SportsRepository
import com.nuvio.app.features.sports.clockText
import com.nuvio.app.features.sports.ui.LeagueMark
import com.nuvio.app.features.sports.ui.SportsActions
import com.nuvio.app.features.sports.ui.isMine

private val WEEKDAYS = mapOf(
    "Mon" to "MONDAY", "Tue" to "TUESDAY", "Wed" to "WEDNESDAY", "Thu" to "THURSDAY",
    "Fri" to "FRIDAY", "Sat" to "SATURDAY", "Sun" to "SUNDAY",
)

/** "THURSDAY · NOV 12" */
private fun dayHeading(ms: Long): String {
    val (weekday, day, month) = SportsDates.dayLabel(ms).split(' ').let { Triple(it.getOrElse(0) { "" }, it.getOrElse(1) { "" }, it.getOrElse(2) { "" }) }
    return "${WEEKDAYS[weekday] ?: weekday.uppercase()} · ${month.uppercase()} $day"
}

/** "Tuesday, Nov 10 · 7:00 PM" */
private fun fullWhen(ms: Long, use24h: Boolean): String {
    val (weekday, day, month) = SportsDates.dayLabel(ms).split(' ').let { Triple(it.getOrElse(0) { "" }, it.getOrElse(1) { "" }, it.getOrElse(2) { "" }) }
    val name = WEEKDAYS[weekday]?.lowercase()?.replaceFirstChar { it.uppercase() } ?: weekday
    return "$name, $month $day · ${clockText(ms, use24h)}"
}

internal fun SportsEvent.involves(teamKeyLeague: String, teamId: String): Boolean =
    sides.any { it.team.id == teamId && SportsCatalog.teamFamily(leagueId) == SportsCatalog.teamFamily(teamKeyLeague) }

/**
 * Your teams' games as one feed: counts for live, today and this week; what is on next with a way to
 * be reminded; then everything coming up by day, and the stories about your teams.
 */
@Composable
internal fun FollowingMatches(
    known: List<SportsEvent>,
    follows: SportsFollows,
    hideScores: Boolean,
    now: Long,
    use24h: Boolean,
    channelsFor: (SportsEvent) -> Int?,
    stories: List<NewsArticle>,
    actions: SportsActions,
    onOpenArticle: (NewsArticle) -> Unit,
    onFind: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = MaterialTheme.nuvio
    val repo = SportsRepository
    var selectedTeam by rememberSaveable { mutableStateOf<String?>(null) }
    val reminders by repo.follows.collectAsStateWithLifecycle()

    val team = follows.teams.firstOrNull { it.key == selectedTeam }
    val mine = remember(known, follows, team, now / 60_000L) {
        known.filter { ev -> isMine(ev, follows) && (team == null || ev.involves(team.leagueId, team.teamId)) }
            .distinctBy { it.id }
    }
    val dayStart = remember(now / 3_600_000L) { SportsDates.dayStart(0) }
    val liveCount = mine.count { it.state == GameState.LIVE }
    val todayCount = mine.count { it.startMs in dayStart until (dayStart + 86_400_000L) && it.state != GameState.OFF }
    val weekCount = mine.count { it.startMs in dayStart until (dayStart + 7 * 86_400_000L) && it.state != GameState.OFF }
    val upNext = remember(mine, now / 60_000L) {
        mine.filter { it.state == GameState.LIVE || (it.state == GameState.PRE && it.startMs >= now - 30 * 60_000L) }
            .sortedWith(compareByDescending<SportsEvent> { it.state == GameState.LIVE }.thenBy { it.startMs })
            .firstOrNull()
    }
    val coming = remember(mine, upNext, now / 60_000L) {
        mine.filter { it.state == GameState.PRE && it.id != upNext?.id && it.startMs >= now }.sortedBy { it.startMs }.take(30)
            .groupBy { SportsDates.dayOf(it.startMs) }
            .toList()
    }
    val teamStories = remember(stories, follows.teams) { stories.filter { NewsRanking.mentions(it, follows.teams) }.take(4) }
    val keyedStories = remember(teamStories) { teamStories.withDuplicateSafeLazyKeys { it.id } }
    val edge = Modifier.padding(horizontal = NuvioTokens.Space.s16)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = nuvioSafeBottomPadding(NuvioTokens.Space.s16)),
        verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s12),
    ) {
        item(key = "chips") {
            LazyRow(contentPadding = PaddingValues(horizontal = NuvioTokens.Space.s16), horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8)) {
                item { Chip("All", selected = selectedTeam == null, onClick = { selectedTeam = null }) }
                items(follows.teams, key = { it.key }) { t -> Chip(t.abbr.ifBlank { t.name }, selected = selectedTeam == t.key, onClick = { selectedTeam = t.key }) }
                item { Chip("+", selected = false, onClick = onFind) }
            }
        }
        item(key = "stats") {
            Column(edge, verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8)) {
                Text("YOUR SPORTS", style = MaterialTheme.typography.labelLarge, color = tokens.colors.textMuted, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8)) {
                    StatCell(liveCount, "Live", Modifier.weight(1f), highlight = liveCount > 0)
                    StatCell(todayCount, "Today", Modifier.weight(1f))
                    StatCell(weekCount, "This Week", Modifier.weight(1f))
                }
            }
        }
        upNext?.let { ev ->
            item(key = "upnext") {
                UpNextCard(
                    ev = ev,
                    reminded = reminders.reminders.any { it.eventId == ev.id },
                    hideScores = hideScores,
                    use24h = use24h,
                    onOpen = { actions.onOpenGame(ev) },
                    onRemind = { repo.toggleReminder(ev) },
                    modifier = edge,
                )
            }
        }
        if (mine.isEmpty()) {
            item(key = "empty") {
                Text(
                    if (follows.isEmpty) "Follow teams and leagues to see their games here." else "No upcoming games for this yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.colors.textMuted,
                    modifier = edge,
                )
            }
        }
        if (coming.isNotEmpty()) {
            item(key = "coming-title") {
                Text("COMING UP", style = MaterialTheme.typography.labelLarge, color = tokens.colors.textMuted, fontWeight = FontWeight.Bold, modifier = edge)
            }
            coming.forEach { (day, games) ->
                item(key = "day-$day") {
                    Column(edge, verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6)) {
                        Text(dayHeading(day), style = MaterialTheme.typography.labelLarge, color = tokens.colors.textMuted, fontWeight = FontWeight.SemiBold)
                        games.forEach { ev ->
                            ComingUpRow(ev, channelsFor(ev), use24h, onClick = { actions.onOpenGame(ev) })
                        }
                    }
                }
            }
        }
        if (teamStories.isNotEmpty()) {
            item(key = "stories-title") {
                Text("FROM YOUR TEAMS", style = MaterialTheme.typography.labelLarge, color = tokens.colors.textMuted, fontWeight = FontWeight.Bold, modifier = edge)
            }
            items(keyedStories, key = { "story-" + it.lazyKey }) { entry ->
                ArticleRow(entry.value, onClick = { onOpenArticle(entry.value) }, modifier = edge)
            }
        }
    }
}

@Composable
private fun StatCell(count: Int, label: String, modifier: Modifier = Modifier, highlight: Boolean = false) {
    val tokens = MaterialTheme.nuvio
    Column(
        modifier.clip(RoundedCornerShape(NuvioTokens.Radius.lg)).background(tokens.colors.surface).padding(vertical = NuvioTokens.Space.s12),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text("$count", style = MaterialTheme.typography.headlineMedium, color = if (highlight) tokens.colors.danger else tokens.colors.textPrimary, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelMedium, color = tokens.colors.textMuted)
    }
}

@Composable
private fun UpNextCard(
    ev: SportsEvent,
    reminded: Boolean,
    hideScores: Boolean,
    use24h: Boolean,
    onOpen: () -> Unit,
    onRemind: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = MaterialTheme.nuvio
    val league = SportsCatalog.league(ev.leagueId)
    val sides = ev.ordered(league?.sport ?: Sport.OTHER)
    val live = ev.state == GameState.LIVE
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(NuvioTokens.Radius.xl)).background(tokens.colors.surface).padding(NuvioTokens.Space.s16),
        verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s12),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (live) "LIVE NOW" else "UP NEXT", style = MaterialTheme.typography.labelLarge, color = if (live) tokens.colors.danger else tokens.colors.accent, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(league?.short.orEmpty(), style = MaterialTheme.typography.labelLarge, color = tokens.colors.textMuted)
        }
        if (sides.size == 2) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                FeaturedSide(sides[0], Modifier.weight(1f))
                Text(
                    if (live && ev.hasScore && !hideScores) "${sides[0].score ?: "–"} – ${sides[1].score ?: "–"}" else "VS",
                    style = if (live && ev.hasScore && !hideScores) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.titleMedium,
                    color = tokens.colors.textMuted,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = NuvioTokens.Space.s8),
                )
                FeaturedSide(sides[1], Modifier.weight(1f))
            }
        } else {
            Text(ev.title, style = MaterialTheme.typography.titleLarge, color = tokens.colors.textPrimary, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (!live) Text(fullWhen(ev.startMs, use24h), style = MaterialTheme.typography.titleLarge, color = tokens.colors.textPrimary, fontWeight = FontWeight.SemiBold)
            ev.subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = tokens.colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s10)) {
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(NuvioTokens.Radius.lg)).background(tokens.colors.accent).clickable(onClick = onOpen).padding(vertical = NuvioTokens.Space.s12),
                contentAlignment = Alignment.Center,
            ) { Text("Game Centre", style = MaterialTheme.typography.titleSmall, color = tokens.colors.onAccent, fontWeight = FontWeight.Bold) }
            if (!live) {
                val shape = RoundedCornerShape(NuvioTokens.Radius.lg)
                Row(
                    Modifier.weight(1f).clip(shape).border(1.dp, tokens.colors.borderDefault, shape).clickable(onClick = onRemind).padding(vertical = NuvioTokens.Space.s12),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(if (reminded) Icons.Rounded.NotificationsActive else Icons.Rounded.Notifications, contentDescription = null, tint = tokens.colors.accent, modifier = Modifier.size(18.dp))
                    Text(if (reminded) "Reminded" else "Remind Me", style = MaterialTheme.typography.titleSmall, color = tokens.colors.accent, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun ComingUpRow(ev: SportsEvent, channels: Int?, use24h: Boolean, onClick: () -> Unit) {
    val tokens = MaterialTheme.nuvio
    val league = SportsCatalog.league(ev.leagueId)
    val sides = ev.ordered(league?.sport ?: Sport.OTHER)
    val title = if (sides.size == 2) "${sides[0].team.abbr.ifBlank { sides[0].team.name }} vs ${sides[1].team.abbr.ifBlank { sides[1].team.name }}" else ev.title
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(NuvioTokens.Radius.lg)).background(tokens.colors.surface).clickable(onClick = onClick)
            .padding(horizontal = NuvioTokens.Space.s12, vertical = NuvioTokens.Space.s12),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s10),
    ) {
        LeagueMark(league, 20.dp)
        Text(title, style = MaterialTheme.typography.titleSmall, color = tokens.colors.textPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(clockText(ev.startMs, use24h), style = MaterialTheme.typography.bodyMedium, color = tokens.colors.textSecondary)
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = tokens.colors.textMuted, modifier = Modifier.size(18.dp))
    }
}
