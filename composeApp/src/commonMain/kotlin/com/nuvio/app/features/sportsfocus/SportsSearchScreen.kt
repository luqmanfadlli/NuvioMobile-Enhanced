package com.nuvio.app.features.sportsfocus

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.Chip
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.core.ui.nuvioSafeBottomPadding
import com.nuvio.app.features.news.ArticleRow
import com.nuvio.app.features.news.NewsArticle
import com.nuvio.app.features.news.NewsRanking
import com.nuvio.app.features.news.NewsText
import com.nuvio.app.features.news.rememberInAppBrowser
import com.nuvio.app.features.podcasts.Podcast
import com.nuvio.app.features.podcasts.PodcastCarousel
import com.nuvio.app.features.podcasts.PodcastRepository
import com.nuvio.app.features.podcasts.ShowRow
import com.nuvio.app.features.sports.GameState
import com.nuvio.app.features.sports.League
import com.nuvio.app.features.sports.SportsCatalog
import com.nuvio.app.features.sports.SportsEvent
import com.nuvio.app.features.sports.SportsRepository
import com.nuvio.app.features.sports.TeamInfo
import com.nuvio.app.features.sports.ui.LeagueMark
import com.nuvio.app.features.sports.ui.SportsActions
import com.nuvio.app.features.sports.ui.SportsLiveRed
import com.nuvio.app.features.sports.ui.TeamLogo
import com.nuvio.app.features.sports.ui.TeamSheet
import com.nuvio.app.features.sports.ui.dayWord
import com.nuvio.app.features.sports.ui.startLabel
import com.nuvio.app.features.sports.ui.use24h
import com.nuvio.app.features.sports.compat.LiveTvClock
import kotlinx.coroutines.delay

private enum class SearchScope(val label: String) { All("All"), Teams("Teams"), Players("Players"), Matches("Matches"), Standings("Standings"), News("News"), Podcasts("Podcasts") }

private val TOP_LEAGUES = listOf("football/nfl", "basketball/nba", "hockey/nhl", "baseball/mlb", "soccer/eng.1", "soccer/esp.1", "soccer/ger.1", "soccer/ita.1", "soccer/usa.1", "soccer/uefa.champions", "racing/f1", "basketball/wnba")

/**
 * Search the whole sports world from one box: teams, players, matches, standings, stories and
 * podcasts. Matches, leagues and your own news come from what the app already has, so they show as
 * you type; players, other teams and more stories come from ESPN's search a moment later.
 */
@Composable
fun SportsSearchScreen(
    actions: SportsActions,
    onOpenArticle: (NewsArticle) -> Unit,
    onOpenPodcast: (Podcast) -> Unit,
    onOpenPodcasts: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = MaterialTheme.nuvio
    val repo = SportsRepository
    val known by repo.known.collectAsStateWithLifecycle()
    val events by repo.events.collectAsStateWithLifecycle()
    val follows by repo.follows.collectAsStateWithLifecycle()
    val news by SportsNews.state.collectAsStateWithLifecycle()
    val catalog by PodcastRepository.catalog.collectAsStateWithLifecycle()
    val openInApp = rememberInAppBrowser()
    val use24h = use24h()
    var query by rememberSaveable { mutableStateOf("") }
    var scopeName by rememberSaveable { mutableStateOf(SearchScope.All.name) }
    val scope = SearchScope.entries.firstOrNull { it.name == scopeName } ?: SearchScope.All
    var remote by remember { mutableStateOf(RemoteSearch()) }
    var directory by remember { mutableStateOf<List<Podcast>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var teamSheet by remember { mutableStateOf<TeamInfo?>(null) }
    val q = query.trim()
    val active = q.length >= 2

    LaunchedEffect(Unit) { SportsNews.ensureLoaded(); PodcastRepository.ensureCatalogLoaded() }
    LaunchedEffect(q) {
        remote = RemoteSearch(); directory = emptyList()
        if (!active) { searching = false; return@LaunchedEffect }
        searching = true
        delay(350)
        remote = SportsSearchData.remote(q)
        if (scope == SearchScope.All || scope == SearchScope.Podcasts) directory = runCatching { PodcastRepository.searchDirectory(q, 8) }.getOrDefault(emptyList())
        searching = false
    }

    val all = remember(events, known) { (events + known).distinctBy { it.id } }
    val matches = remember(q, all) {
        if (!active) emptyList() else {
            val words = q.lowercase().split(' ').filter { it.isNotBlank() }
            all.filter { ev ->
                val hay = (ev.title + " " + ev.sides.joinToString(" ") { it.team.fullName + " " + it.team.name + " " + it.team.abbr } + " " + (SportsCatalog.league(ev.leagueId)?.name ?: "")).lowercase()
                words.all { it in hay }
            }.sortedWith(compareBy<SportsEvent> { when (it.state) { GameState.LIVE -> 0; GameState.PRE -> 1; else -> 2 } }.thenBy { if (it.state == GameState.POST) -it.startMs else it.startMs }).take(14)
        }
    }
    val teams = remember(q, all) {
        if (!active) emptyList() else all.asSequence().flatMap { it.sides.asSequence() }.map { it.team }
            .filter { it.fullName.contains(q, true) || it.name.contains(q, true) || it.abbr.equals(q, true) }
            .distinctBy { SportsCatalog.teamFamily(it.leagueId) + "|" + it.id }.take(12).toList()
    }
    val leagues = remember(q) {
        if (!active) emptyList() else SportsCatalog.all.filter { l -> l.name.contains(q, true) || l.short.equals(q, true) || l.guideWords.any { it.equals(q, true) } }.take(10)
    }
    val stories = remember(q, news.articles, remote) {
        if (!active) emptyList() else {
            val own = news.articles.filter { it.headline.contains(q, true) || it.summary.contains(q, true) }.take(12)
            val ids = own.map { NewsRanking.urlKey(it.url) }.toSet()
            val more = remote.articles.filter { NewsRanking.urlKey(it.url) !in ids }.mapNotNull { h ->
                h.url?.let { NewsArticle(id = "search:" + it, headline = h.title, summary = h.subtitle.orEmpty(), imageUrl = h.imageUrl, url = it, publishedMs = h.publishedMs, leagueId = null, leagueShort = null, publisher = "ESPN") }
            }
            own + more
        }
    }
    val shows = remember(q, catalog, directory) {
        if (!active) emptyList() else (PodcastRepository.searchCatalog(q) + directory).distinctBy { it.id }.take(14)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().background(tokens.colors.background),
        contentPadding = PaddingValues(bottom = nuvioSafeBottomPadding(NuvioTokens.Space.s16)),
        verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s12),
    ) {
        item {
            Column(Modifier.statusBarsPadding().padding(top = 8.dp)) {
                Text("Search", color = tokens.colors.textPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, end = 72.dp, top = 8.dp, bottom = 12.dp))
                SearchField(query, { query = it }, searching, Modifier.padding(horizontal = NuvioTokens.Space.s16))
            }
        }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = NuvioTokens.Space.s16), horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8)) {
                items(SearchScope.entries, key = { it.name }) { s -> Chip(s.label, selected = scope == s, onClick = { scopeName = s.name }) }
            }
        }
        val show = { s: SearchScope -> scope == SearchScope.All || scope == s }
        val edge = Modifier.padding(horizontal = NuvioTokens.Space.s16)

        if (!active) {
            // ---- nothing typed yet: ways in
            if (follows.teams.isNotEmpty() && show(SearchScope.Teams)) {
                item { Heading("Your teams") }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = NuvioTokens.Space.s16), horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s12)) {
                        items(follows.teams, key = { it.key }) { t ->
                            Column(Modifier.width(76.dp).clickable { teamSheet = com.nuvio.app.features.sports.TeamInfo(t.leagueId, t.teamId, t.name, com.nuvio.app.features.news.TeamNames.full(t), t.abbr, null, t.color, null, t.logo) }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(Modifier.size(56.dp).clip(CircleShape).background(tokens.colors.surfaceElevated), contentAlignment = Alignment.Center) {
                                    t.logo?.let { AsyncImage(model = it, contentDescription = null, modifier = Modifier.size(38.dp)) }
                                }
                                Text(t.abbr.ifBlank { t.name }, style = MaterialTheme.typography.labelMedium, color = tokens.colors.textPrimary, maxLines = 1)
                            }
                        }
                    }
                }
            }
            if (show(SearchScope.Standings)) {
                item { Heading("Standings") }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = NuvioTokens.Space.s16), horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s10)) {
                        items(TOP_LEAGUES.mapNotNull { SportsCatalog.league(it) }, key = { it.id }) { l -> LeagueTile(l) { actions.onOpenLeague(l) } }
                    }
                }
            }
            if (show(SearchScope.News)) {
                val top = news.articles.take(4)
                if (top.isNotEmpty()) {
                    item { Heading("Trending stories") }
                    items(top, key = { "t-" + it.id }) { a -> ArticleRow(a, onClick = { onOpenArticle(a) }, modifier = edge) }
                }
            }
            if (show(SearchScope.Podcasts)) {
                val featured = catalog.take(10)
                if (featured.isNotEmpty()) item { PodcastCarousel(featured, onOpenShow = onOpenPodcast, onSeeAll = onOpenPodcasts) }
            }
            return@LazyColumn
        }

        // ---- results
        var any = false
        if (show(SearchScope.Teams) && (teams.isNotEmpty() || remote.teams.isNotEmpty())) {
            any = true
            item { Heading("Teams") }
            items(teams, key = { "tm-" + it.key }) { t -> TeamRow(t) { teamSheet = t } }
            items(remote.teams.filter { r -> teams.none { it.fullName.equals(r.title, true) } }.take(6), key = { "rt-" + it.title + it.subtitle }) { h ->
                HitRow(h, round = true) { h.url?.let(openInApp) }
            }
        }
        if (show(SearchScope.Players) && remote.players.isNotEmpty()) {
            any = true
            item { Heading("Players") }
            items(remote.players, key = { "pl-" + it.title + it.subtitle }) { h -> HitRow(h, round = true, person = true) { h.url?.let(openInApp) } }
        }
        if (show(SearchScope.Matches) && matches.isNotEmpty()) {
            any = true
            item { Heading("Matches") }
            items(matches, key = { "mt-" + it.id }) { ev -> MatchRow(ev, use24h) { actions.onOpenGame(ev) } }
        }
        if (show(SearchScope.Standings) && leagues.isNotEmpty()) {
            any = true
            item { Heading("Standings") }
            items(leagues, key = { "lg-" + it.id }) { l -> LeagueRow(l) { actions.onOpenLeague(l) } }
        }
        if (show(SearchScope.News) && stories.isNotEmpty()) {
            any = true
            item { Heading("News") }
            items(stories.take(if (scope == SearchScope.All) 5 else 30), key = { "st-" + it.id }) { a -> ArticleRow(a, onClick = { onOpenArticle(a) }, modifier = edge) }
        }
        if (show(SearchScope.Podcasts) && shows.isNotEmpty()) {
            any = true
            item { Heading("Podcasts") }
            items(shows.take(if (scope == SearchScope.All) 4 else 30), key = { "pc-" + it.id }) { show -> ShowRow(show, onClick = { onOpenPodcast(show) }, chevron = true) }
        }
        if (!any && !searching) {
            item { Text("Nothing found for “$q”. Try a team, a player or a league.", style = MaterialTheme.typography.bodyMedium, color = tokens.colors.textMuted, modifier = edge.padding(top = NuvioTokens.Space.s8)) }
        }
    }
    teamSheet?.let { t -> TeamSheet(t, actions, onDismiss = { teamSheet = null }) }
}

@Composable
internal fun Heading(text: String) {
    val tokens = MaterialTheme.nuvio
    Text(text, style = MaterialTheme.typography.titleLarge, color = tokens.colors.textPrimary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = NuvioTokens.Space.s16))
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit, busy: Boolean, modifier: Modifier = Modifier) {
    val tokens = MaterialTheme.nuvio
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(NuvioTokens.Radius.xl)).background(tokens.colors.surface).padding(horizontal = NuvioTokens.Space.s14, vertical = NuvioTokens.Space.s14),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s10),
    ) {
        Icon(Icons.Rounded.Search, contentDescription = null, tint = tokens.colors.textMuted, modifier = Modifier.size(22.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) Text("Teams, players, matches, standings…", style = MaterialTheme.typography.bodyLarge, color = tokens.colors.textMuted)
            BasicTextField(
                value = query,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(color = tokens.colors.textPrimary, fontSize = MaterialTheme.typography.bodyLarge.fontSize),
                cursorBrush = SolidColor(tokens.colors.accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (busy) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
        else if (query.isNotEmpty()) Icon(Icons.Rounded.Close, contentDescription = "Clear", tint = tokens.colors.textMuted, modifier = Modifier.size(20.dp).clip(CircleShape).clickable { onChange("") })
    }
}

@Composable
internal fun RowShell(onClick: () -> Unit, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    val tokens = MaterialTheme.nuvio
    Row(
        Modifier.padding(horizontal = NuvioTokens.Space.s16).fillMaxWidth().clip(RoundedCornerShape(NuvioTokens.Radius.lg)).background(tokens.colors.surface).clickable(onClick = onClick).padding(NuvioTokens.Space.s12),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s12),
    ) { content() }
}

@Composable
internal fun Chevron() {
    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.nuvio.colors.textMuted, modifier = Modifier.size(20.dp))
}

@Composable
internal fun TeamRow(team: TeamInfo, onClick: () -> Unit) {
    val tokens = MaterialTheme.nuvio
    RowShell(onClick) {
        TeamLogo(team, 40.dp)
        Column(Modifier.weight(1f)) {
            Text(team.fullName.ifBlank { team.name }, style = MaterialTheme.typography.titleSmall, color = tokens.colors.textPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(SportsCatalog.league(team.leagueId)?.name.orEmpty(), style = MaterialTheme.typography.bodySmall, color = tokens.colors.textMuted, maxLines = 1)
        }
        Chevron()
    }
}

@Composable
internal fun HitRow(hit: SearchHit, round: Boolean, person: Boolean = false, onClick: () -> Unit) {
    val tokens = MaterialTheme.nuvio
    RowShell(onClick) {
        Box(Modifier.size(44.dp).clip(if (round) CircleShape else RoundedCornerShape(8.dp)).background(tokens.colors.surfaceElevated), contentAlignment = Alignment.Center) {
            if (hit.imageUrl != null) AsyncImage(model = hit.imageUrl, contentDescription = null, contentScale = if (person) ContentScale.Crop else ContentScale.Fit, modifier = Modifier.size(if (person) 44.dp else 30.dp))
            else Icon(Icons.Rounded.Person, contentDescription = null, tint = tokens.colors.textMuted)
        }
        Column(Modifier.weight(1f)) {
            Text(hit.title, style = MaterialTheme.typography.titleSmall, color = tokens.colors.textPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            hit.subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = tokens.colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        Chevron()
    }
}

@Composable
internal fun MatchRow(ev: SportsEvent, use24h: Boolean, onClick: () -> Unit) {
    val tokens = MaterialTheme.nuvio
    val league = SportsCatalog.league(ev.leagueId)
    val sides = ev.sides
    val live = ev.state == GameState.LIVE
    RowShell(onClick) {
        LeagueMark(league, 26.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(ev.title, style = MaterialTheme.typography.titleSmall, color = tokens.colors.textPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val line = when (ev.state) {
                GameState.LIVE -> "Live" + (ev.status?.let { " · $it" } ?: "")
                GameState.POST -> (ev.status ?: "Final") + " · " + dayWord(ev.startMs, LiveTvClock.nowMs())
                else -> dayWord(ev.startMs, LiveTvClock.nowMs()) + " " + startLabel(ev, use24h)
            }
            Text(listOfNotNull(league?.short, line).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = if (live) SportsLiveRed else tokens.colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (ev.hasScore && sides.size == 2) Text("${sides[0].score}–${sides[1].score}", style = MaterialTheme.typography.titleSmall, color = tokens.colors.textPrimary, fontWeight = FontWeight.Black)
        Chevron()
    }
}

@Composable
internal fun LeagueRow(league: League, onClick: () -> Unit) {
    val tokens = MaterialTheme.nuvio
    RowShell(onClick) {
        LeagueMark(league, 30.dp)
        Column(Modifier.weight(1f)) {
            Text(league.name, style = MaterialTheme.typography.titleSmall, color = tokens.colors.textPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Standings and fixtures", style = MaterialTheme.typography.bodySmall, color = tokens.colors.textMuted)
        }
        Chevron()
    }
}

@Composable
private fun LeagueTile(league: League, onClick: () -> Unit) {
    val tokens = MaterialTheme.nuvio
    Column(
        Modifier.width(96.dp).clip(RoundedCornerShape(NuvioTokens.Radius.lg)).background(tokens.colors.surface).clickable(onClick = onClick).padding(NuvioTokens.Space.s12),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LeagueMark(league, 36.dp)
        Text(league.short, style = MaterialTheme.typography.labelLarge, color = tokens.colors.textPrimary, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}
