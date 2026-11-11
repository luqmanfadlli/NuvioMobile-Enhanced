package com.nuvio.app.features.sportsfocus

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.Chip
import com.nuvio.app.core.ui.NuvioScreenHeader
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.core.ui.nuvioSafeBottomPadding
import com.nuvio.app.core.ui.withDuplicateSafeLazyKeys
import com.nuvio.app.features.livetv.LiveTvRepository
import com.nuvio.app.features.news.ArticleRow
import com.nuvio.app.features.news.NewsArticle
import com.nuvio.app.features.sports.GameState
import com.nuvio.app.features.sports.SportsCatalog
import com.nuvio.app.features.sports.SportsDates
import com.nuvio.app.features.sports.SportsEvent
import com.nuvio.app.features.sports.SportsReminders
import com.nuvio.app.features.sports.SportsRepository
import com.nuvio.app.features.sports.compat.LiveTvClock
import com.nuvio.app.features.sports.ui.GameCard
import com.nuvio.app.features.sports.ui.SportsActions
import com.nuvio.app.features.sports.ui.MySportsTab
import com.nuvio.app.features.sports.ui.SportsFollowingTab
import com.nuvio.app.features.sports.ui.SportsSearchTab
import com.nuvio.app.features.sports.ui.SportsLiveRed
import com.nuvio.app.features.sports.ui.isMine
import com.nuvio.app.features.sports.ui.use24h
import kotlinx.coroutines.delay

private const val TWELVE_HOURS = 12 * 60 * 60 * 1000L

@Composable
private fun rememberNow(): Long {
    val now by produceState(LiveTvClock.nowMs()) {
        while (true) { value = LiveTvClock.nowMs(); delay(15_000) }
    }
    return now
}

@Composable
private fun SectionHeader(title: String, count: Int? = null) {
    val tokens = MaterialTheme.nuvio
    Row(
        Modifier.fillMaxWidth().padding(horizontal = NuvioTokens.Space.s16, vertical = NuvioTokens.Space.s6),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = tokens.colors.textPrimary, fontWeight = FontWeight.Bold)
        count?.let { Text("$it", style = MaterialTheme.typography.labelLarge, color = tokens.colors.textMuted) }
    }
}

/**
 * The Sports home: a scoreboard rather than a catalogue. What is on right now, what your teams are
 * doing, the rest of today's slate, and the day's top stories.
 */
@Composable
fun SportsFocusHome(
    actions: SportsActions,
    onOpenNews: () -> Unit,
    onOpenArticle: (NewsArticle) -> Unit,
    onOpenFollowing: () -> Unit,
    onOpenLive: () -> Unit,
    modifier: Modifier = Modifier,
    /** The Balanced Sports tab is this same page without the "Good evening" line. */
    showGreeting: Boolean = true,
    /** Shown right under the greeting: the Balanced Sports tab puts "My Teams" here. */
    teamsHeader: (@Composable () -> Unit)? = null,
) {
    val tokens = MaterialTheme.nuvio
    val repo = SportsRepository
    LaunchedEffect(Unit) { SportsReminders.start(); LiveTvRepository.ensureLoaded(); SportsNews.ensureLoaded() }
    androidx.compose.runtime.DisposableEffect(Unit) {
        repo.setViewing(true)
        onDispose { repo.setViewing(false) }
    }
    val events by repo.events.collectAsStateWithLifecycle()
    val known by repo.known.collectAsStateWithLifecycle()
    val follows by repo.follows.collectAsStateWithLifecycle()
    val settings by repo.settings.collectAsStateWithLifecycle()
    val matches by repo.matches.collectAsStateWithLifecycle()
    val loading by repo.loading.collectAsStateWithLifecycle()
    val news by SportsNews.state.collectAsStateWithLifecycle()
    val finding by com.nuvio.app.features.sports.SportsWatch.finding.collectAsStateWithLifecycle()
    val now = rememberNow()
    val use24h = use24h()
    var liveSport by rememberSaveable { mutableStateOf<String?>(null) }

    val all = remember(events, known) { (events + known).distinctBy { it.id } }
    val live = remember(all, now / 60_000L) { all.filter { it.state == GameState.LIVE }.sortedByDescending { isMine(it, follows) } }
    val featured = live.firstOrNull()
    val liveRest = remember(live, featured) { live.filter { it.id != featured?.id } }
    val liveSports = remember(liveRest) { liveRest.mapNotNull { SportsCatalog.league(it.leagueId)?.sport }.distinct() }
    val liveShown = remember(liveRest, liveSport) {
        if (liveSport == null) liveRest else liveRest.filter { SportsCatalog.league(it.leagueId)?.sport?.name == liveSport }
    }
    val sportsDay = remember(all, follows, now / 60_000L) {
        val start = SportsDates.dayStart(0)
        val end = start + 24 * 60 * 60 * 1000L
        all.filter { isMine(it, follows) && it.startMs in start until end }.sortedBy { it.startMs }
    }
    val nextUp = remember(all, follows, now / 60_000L) {
        follows.teams.mapNotNull { t ->
            all.filter { (it.state == GameState.LIVE || (it.state == GameState.PRE && it.startMs >= now - 30 * 60_000L)) && it.involves(t.leagueId, t.teamId) }
                .minByOrNull { it.startMs }?.let { t to it }
        }.distinctBy { it.second.id }.sortedBy { it.second.startMs }.take(3)
    }
    val mine = remember(all, follows, now / 60_000L) {
        all.filter { isMine(it, follows) && it.state != GameState.LIVE && it.startMs in (now - TWELVE_HOURS)..(now + 36 * 60 * 60 * 1000L) }
            .sortedBy { it.startMs }
    }
    val today = remember(all, now / 60_000L) {
        val start = SportsDates.dayStart(0)
        val end = start + 24 * 60 * 60 * 1000L
        all.filter { it.state != GameState.LIVE && it.startMs in start until end }.sortedBy { it.startMs }
    }
    val stories = remember(news) { SportsNews.merged(news).take(6) }
    val keyedStories = remember(stories) { stories.withDuplicateSafeLazyKeys { it.id } }

    fun channelsFor(ev: SportsEvent): Int? = matches[ev.id]?.size
    // Look up the streams of what is on screen first, so Watch is instant.
    LaunchedEffect(featured?.id, liveRest.take(3).map { it.id }, nextUp.map { it.second.id }) {
        listOfNotNull(featured).plus(liveRest.take(3)).plus(nextUp.map { it.second }.filter { it.state == GameState.LIVE })
            .forEach { com.nuvio.app.features.sports.SportsWatch.warm(it) }
    }
    val edge = Modifier.padding(horizontal = NuvioTokens.Space.s16)

    LazyColumn(
        modifier = modifier.fillMaxSize().background(tokens.colors.background),
        contentPadding = PaddingValues(bottom = nuvioSafeBottomPadding(NuvioTokens.Space.s16)),
        verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s12),
    ) {
        // Without the greeting, keep the same clearance for the status bar and the corner profile icon.
        item { if (showGreeting) HomeGreeting() else Spacer(Modifier.statusBarsPadding().height(56.dp)) }
        teamsHeader?.let { header -> item(key = "my-teams") { header() } }
        if (loading && all.isEmpty()) {
            item {
                Column(Modifier.padding(horizontal = NuvioTokens.Space.s16), verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s12)) {
                    com.nuvio.app.core.ui.SkeletonBlock(modifier = Modifier.fillMaxWidth(), height = 210.dp, cornerRadius = 20.dp)
                    com.nuvio.app.core.ui.SkeletonBlock(modifier = Modifier.fillMaxWidth(), height = 110.dp, cornerRadius = 20.dp)
                    Row(horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8)) {
                        repeat(3) { com.nuvio.app.core.ui.SkeletonBlock(modifier = Modifier.weight(1f), height = 84.dp, cornerRadius = 16.dp) }
                    }
                }
            }
        }
        featured?.let { ev ->
            item(key = "featured") {
                FeaturedGameCard(ev, ev.id in finding, settings.hideScores, use24h, onClick = { actions.watchOrOpen(ev, matches) }, onGameCentre = { actions.onOpenGame(ev) }, modifier = edge)
            }
        }
        item(key = "sports-day") {
            SportsDayCard(sportsDay, nextUp, now, use24h, onOpenGame = { actions.onOpenGame(it) }, onSeeSchedule = onOpenFollowing, modifier = edge)
        }
        if (liveRest.isNotEmpty()) {
            item(key = "live-header") {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = NuvioTokens.Space.s16),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8),
                ) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(SportsLiveRed))
                    Text("LIVE NOW", style = MaterialTheme.typography.titleMedium, color = SportsLiveRed, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("See All", style = MaterialTheme.typography.titleSmall, color = tokens.colors.accent, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable(onClick = onOpenLive))
                }
            }
            if (liveSports.size > 1) {
                item(key = "live-chips") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = NuvioTokens.Space.s16),
                        horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8),
                    ) {
                        item { Chip("All", selected = liveSport == null, onClick = { liveSport = null }) }
                        items(liveSports, key = { it.name }) { sport -> Chip(sport.label, selected = liveSport == sport.name, onClick = { liveSport = sport.name }) }
                    }
                }
            }
            items(liveShown.take(12), key = { "live-" + it.id }) { ev ->
                GameCard(ev, follows, now, use24h, settings.hideScores, channelsFor(ev), edge, onGameCentre = { actions.onOpenGame(ev) }) { actions.watchOrOpen(ev, matches) }
            }
        }
        if (mine.isNotEmpty()) {
            item { SectionHeader("Your teams") }
            items(mine.take(6), key = { "mine-" + it.id }) { ev ->
                GameCard(ev, follows, now, use24h, settings.hideScores, channelsFor(ev), edge, onGameCentre = { actions.onOpenGame(ev) }) { actions.watchOrOpen(ev, matches) }
            }
        } else if (follows.isEmpty) {
            item {
                Column(
                    edge.fillMaxWidth().clip(RoundedCornerShape(NuvioTokens.Radius.lg)).background(tokens.colors.surface).padding(NuvioTokens.Space.s16),
                ) {
                    Text("Make it yours", style = MaterialTheme.typography.titleMedium, color = tokens.colors.textPrimary, fontWeight = FontWeight.Bold)
                    Text("Follow teams and leagues on the Following tab and their games show up here first.", style = MaterialTheme.typography.bodyMedium, color = tokens.colors.textMuted)
                }
            }
        }
        if (today.isNotEmpty()) {
            item { SectionHeader("Today") }
            items(today.take(12), key = { "today-" + it.id }) { ev ->
                GameCard(ev, follows, now, use24h, settings.hideScores, channelsFor(ev), edge, onGameCentre = { actions.onOpenGame(ev) }) { actions.watchOrOpen(ev, matches) }
            }
        }
        if (stories.isNotEmpty()) {
            item {
                Row(Modifier.fillMaxWidth().padding(end = NuvioTokens.Space.s16), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { SectionHeader("Top stories") }
                    Text("See all", style = MaterialTheme.typography.labelLarge, color = tokens.colors.accent, modifier = Modifier.clickable(onClick = onOpenNews))
                }
            }
            items(keyedStories, key = { "story-" + it.lazyKey }) { entry ->
                ArticleRow(entry.value, onClick = { onOpenArticle(entry.value) }, modifier = edge)
            }
        }
    }
}

private enum class FollowingMode(val label: String) { Games("Matches"), Find("Find"), Manage("Following") }

/** The Following tab: games for what you follow, and the place to change what you follow. */
@Composable
fun SportsFocusFollowing(actions: SportsActions, onOpenArticle: (NewsArticle) -> Unit, modifier: Modifier = Modifier) {
    val tokens = MaterialTheme.nuvio
    val repo = SportsRepository
    LaunchedEffect(Unit) { SportsReminders.start(); LiveTvRepository.ensureLoaded(); SportsNews.ensureLoaded() }
    androidx.compose.runtime.DisposableEffect(Unit) {
        repo.setViewing(true)
        onDispose { repo.setViewing(false) }
    }
    val follows by repo.follows.collectAsStateWithLifecycle()
    val settings by repo.settings.collectAsStateWithLifecycle()
    val known by repo.known.collectAsStateWithLifecycle()
    val matches by repo.matches.collectAsStateWithLifecycle()
    val news by SportsNews.state.collectAsStateWithLifecycle()
    val now = rememberNow()
    val use24h = use24h()
    var mode by rememberSaveable { mutableIntStateOf(if (follows.isEmpty) FollowingMode.Find.ordinal else FollowingMode.Games.ordinal) }
    Column(modifier.fillMaxSize().background(tokens.colors.background).statusBarsPadding()) {
        NuvioScreenHeader(
            title = "Following",
            includeStatusBarPadding = false,
            modifier = Modifier.padding(horizontal = NuvioTokens.Space.s8),
            actions = {
                val editing = mode == FollowingMode.Manage.ordinal
                Text(
                    if (editing) "Done" else "Edit",
                    style = MaterialTheme.typography.titleSmall,
                    color = tokens.colors.accent,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(end = NuvioTokens.Space.s8).clip(RoundedCornerShape(NuvioTokens.Radius.full))
                        .background(tokens.colors.surface)
                        .clickable { mode = if (editing) FollowingMode.Games.ordinal else FollowingMode.Manage.ordinal }
                        .padding(horizontal = NuvioTokens.Space.s16, vertical = NuvioTokens.Space.s8),
                )
            },
        )
        Row(
            Modifier.padding(horizontal = NuvioTokens.Space.s16, vertical = NuvioTokens.Space.s6),
            horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8),
        ) {
            FollowingMode.entries.forEach { m -> Chip(m.label, selected = mode == m.ordinal, onClick = { mode = m.ordinal }) }
        }
        Spacer(Modifier.height(4.dp))
        if (mode == FollowingMode.Games.ordinal) {
            FollowingMatches(
                known = known,
                follows = follows,
                hideScores = settings.hideScores,
                now = now,
                use24h = use24h,
                channelsFor = { ev -> matches[ev.id]?.size },
                stories = news.articles,
                actions = actions,
                onOpenArticle = onOpenArticle,
                onFind = { mode = FollowingMode.Find.ordinal },
            )
        } else if (mode == FollowingMode.Find.ordinal) {
            SportsSearchTab(known, follows, actions)
        } else {
            SportsFollowingTab(follows, settings, actions)
        }
    }
}
