package com.nuvio.app.features.podcasts

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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.Chip
import com.nuvio.app.core.ui.NuvioScreenHeader
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.core.ui.nuvioSafeBottomPadding
import com.nuvio.app.features.music.playback.MusicPlayer
import com.nuvio.app.features.news.NewsText
import com.nuvio.app.features.news.TeamNames
import com.nuvio.app.features.sports.SportsCatalog
import com.nuvio.app.features.sports.SportsRepository
import kotlinx.coroutines.delay

private val POPULAR_SPORTS = setOf("Hockey", "American Football", "Basketball", "Baseball", "Soccer")

/**
 * Sports Talk: every sports podcast in one place. Pick For You or the shows you follow, narrow it to a
 * team or a sport, search anything, and open a show for its episodes.
 */
@Composable
fun PodcastBrowserScreen(onBack: () -> Unit, onOpenShow: (Podcast) -> Unit, modifier: Modifier = Modifier) {
    val tokens = MaterialTheme.nuvio
    val catalog by PodcastRepository.catalog.collectAsStateWithLifecycle()
    val subscriptions by PodcastRepository.subscriptions.collectAsStateWithLifecycle()
    val follows by SportsRepository.follows.collectAsStateWithLifecycle()
    var following by rememberSaveable { mutableStateOf(false) }
    var selection by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var sortAz by rememberSaveable { mutableStateOf(false) }
    var directory by remember { mutableStateOf<List<Podcast>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { PodcastRepository.ensureCatalogLoaded() }
    LaunchedEffect(query) {
        directory = emptyList()
        if (query.trim().length < 3) { searching = false; return@LaunchedEffect }
        searching = true
        delay(450)
        directory = PodcastRepository.searchDirectory(query)
        searching = false
    }

    val team = follows.teams.firstOrNull { "team:" + it.key == selection }
    val sportName = selection?.takeIf { it.startsWith("sport:") }?.removePrefix("sport:")
    val teamShows by produceState(emptyList<Podcast>(), team?.key) {
        value = team?.let { runCatching { PodcastRepository.teamShows(it) }.getOrDefault(emptyList()) }.orEmpty()
    }
    val followedSports = remember(follows) {
        SportsRepository.followedLeagueIds(follows).mapNotNull { SportsCatalog.league(it)?.sport }.mapNotNull(PodcastRepository::catalogSport).toSet()
    }
    val sports = remember(catalog) { PodcastRepository.sports() }

    val searchingNow = query.isNotBlank()
    val local = remember(query, catalog) { if (searchingNow) PodcastRepository.searchCatalog(query) else emptyList() }
    val remote = remember(directory, local) { directory.filter { d -> local.none { it.id == d.id } } }

    val base = remember(catalog, subscriptions, following, selection, teamShows, follows, followedSports) {
        when {
            following -> subscriptions
            team != null -> (teamShows + PodcastRepository.aboutTeam(team)).distinctBy { it.id }
            sportName != null -> catalog.filter { it.sport == sportName }
            else -> (PodcastRepository.forSports(followedSports.ifEmpty { POPULAR_SPORTS }, follows.teams, 1000) + catalog).distinctBy { it.id }
        }
    }
    val shown = remember(base, sortAz) { if (sortAz) base.sortedBy { it.title.lowercase() } else base }
    val heading = when {
        following -> "Your shows"
        team != null -> TeamNames.full(team)
        sportName != null -> sportName
        else -> "All podcasts"
    }

    Box(modifier.fillMaxSize().background(tokens.colors.background)) {
        LazyColumn(
            contentPadding = PaddingValues(bottom = nuvioSafeBottomPadding(96.dp)),
            verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s12),
        ) {
            item {
                Column(Modifier.statusBarsPadding()) {
                    NuvioScreenHeader(title = "", includeStatusBarPadding = false, onBack = onBack, modifier = Modifier.padding(horizontal = NuvioTokens.Space.s8))
                    Text("Sports Talk", style = MaterialTheme.typography.displayMedium, color = tokens.colors.textPrimary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = NuvioTokens.Space.s16))
                    Text(
                        "Podcasts, analysis and conversations from across the sports world.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = tokens.colors.textMuted,
                        modifier = Modifier.padding(horizontal = NuvioTokens.Space.s16, vertical = NuvioTokens.Space.s6),
                    )
                }
            }
            item {
                Row(
                    Modifier.padding(horizontal = NuvioTokens.Space.s16).fillMaxWidth().clip(RoundedCornerShape(NuvioTokens.Radius.xl))
                        .background(tokens.colors.surface).padding(horizontal = NuvioTokens.Space.s14, vertical = NuvioTokens.Space.s14),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s10),
                ) {
                    Icon(Icons.Rounded.Search, contentDescription = null, tint = tokens.colors.textMuted, modifier = Modifier.size(22.dp))
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) Text("Search podcasts, teams, hosts", style = MaterialTheme.typography.bodyLarge, color = tokens.colors.textMuted)
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            textStyle = TextStyle(color = tokens.colors.textPrimary, fontSize = MaterialTheme.typography.bodyLarge.fontSize),
                            cursorBrush = SolidColor(tokens.colors.accent),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (searching) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                }
            }
            if (!searchingNow) {
                item {
                    Row(Modifier.padding(horizontal = NuvioTokens.Space.s16), horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8)) {
                        Chip("For You", selected = !following, onClick = { following = false })
                        Chip("Following", selected = following, onClick = { following = true; selection = null })
                    }
                }
                if (!following) {
                    item {
                        LazyRow(contentPadding = PaddingValues(horizontal = NuvioTokens.Space.s16), horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8)) {
                            items(follows.teams, key = { "team:" + it.key }) { t ->
                                val key = "team:" + t.key
                                Chip(TeamNames.full(t), selected = selection == key, onClick = { selection = if (selection == key) null else key })
                            }
                            items(sports, key = { "sport:$it" }) { s ->
                                val key = "sport:$s"
                                Chip(s, selected = selection == key, onClick = { selection = if (selection == key) null else key })
                            }
                        }
                    }
                    if (shown.isNotEmpty()) {
                        item { SectionTitle("Featured for You") }
                        item {
                            LazyRow(contentPadding = PaddingValues(horizontal = NuvioTokens.Space.s16), horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s12)) {
                                items(shown.take(10), key = { "f-" + it.id }) { show ->
                                    Column(Modifier.width(150.dp).clickable { onOpenShow(show) }, verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6)) {
                                        ShowArtwork(show.imageUrl, 150.dp)
                                        Text(show.title, style = MaterialTheme.typography.titleSmall, color = tokens.colors.textPrimary, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            listOfNotNull(show.publisher.ifBlank { null }, show.sport).joinToString(" · "),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = tokens.colors.textMuted,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = NuvioTokens.Space.s16), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(heading, style = MaterialTheme.typography.headlineSmall, color = tokens.colors.textPrimary, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${shown.size} podcasts", style = MaterialTheme.typography.bodyMedium, color = tokens.colors.textMuted)
                        }
                        SortToggle(if (sortAz) "A–Z" else "Popular") { sortAz = !sortAz }
                    }
                }
                if (shown.isEmpty()) {
                    item {
                        Text(
                            if (following) "Follow a show and it will be here." else if (team != null && teamShows.isEmpty()) "Looking for shows about this team…" else "Nothing here yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = tokens.colors.textMuted,
                            modifier = Modifier.padding(horizontal = NuvioTokens.Space.s16),
                        )
                    }
                }
                items(shown, key = { "b-" + it.id }) { show -> ShowRow(show, onClick = { onOpenShow(show) }, chevron = true) }
            } else {
                if (local.isNotEmpty()) {
                    item { SectionTitle("In our catalogue", local.size) }
                    items(local, key = { "l-" + it.id }) { show -> ShowRow(show, onClick = { onOpenShow(show) }, chevron = true) }
                }
                if (remote.isNotEmpty()) {
                    item { SectionTitle("More podcasts", remote.size) }
                    items(remote, key = { "r-" + it.id }) { show -> ShowRow(show, onClick = { onOpenShow(show) }, trailing = show.league, chevron = true) }
                }
                if (!searching && local.isEmpty() && remote.isEmpty()) {
                    item { Text("No shows found for “${query.trim()}”.", style = MaterialTheme.typography.bodyMedium, color = tokens.colors.textMuted, modifier = Modifier.padding(NuvioTokens.Space.s16)) }
                }
            }
        }
        PodcastMiniPlayer(Modifier.align(Alignment.BottomCenter).padding(bottom = nuvioSafeBottomPadding(8.dp)))
    }
}

@Composable
private fun SectionTitle(title: String, count: Int? = null) {
    val tokens = MaterialTheme.nuvio
    Row(
        Modifier.fillMaxWidth().padding(horizontal = NuvioTokens.Space.s16),
        horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = tokens.colors.textPrimary, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f, fill = false))
        count?.let { Text("$it", style = MaterialTheme.typography.labelLarge, color = tokens.colors.textMuted) }
    }
}

@Composable
private fun SortToggle(label: String, onClick: () -> Unit) {
    val tokens = MaterialTheme.nuvio
    Row(Modifier.clip(RoundedCornerShape(NuvioTokens.Radius.md)).clickable(onClick = onClick).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.titleSmall, color = tokens.colors.accent, fontWeight = FontWeight.SemiBold)
        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Sort", tint = tokens.colors.accent, modifier = Modifier.size(20.dp))
    }
}

private sealed interface ShowLoad {
    data object Loading : ShowLoad
    data object Failed : ShowLoad
    data class Ready(val show: PodcastShow) : ShowLoad
}

/** One podcast: artwork, follow and play-latest, the latest episode up top, then every episode. */
@Composable
fun PodcastShowScreen(podcast: Podcast, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val tokens = MaterialTheme.nuvio
    val subscriptions by PodcastRepository.subscriptions.collectAsStateWithLifecycle()
    val played by PodcastRepository.played.collectAsStateWithLifecycle()
    val player by MusicPlayer.state.collectAsStateWithLifecycle()
    val load by produceState<ShowLoad>(ShowLoad.Loading, podcast.id) {
        value = PodcastRepository.loadShow(podcast)?.let { ShowLoad.Ready(it) } ?: ShowLoad.Failed
    }
    val shown = (load as? ShowLoad.Ready)?.show?.podcast ?: podcast
    val following = subscriptions.any { it.id == podcast.id }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var unplayedOnly by rememberSaveable { mutableStateOf(false) }
    var oldestFirst by rememberSaveable { mutableStateOf(false) }
    val allEpisodes = (load as? ShowLoad.Ready)?.show?.episodes.orEmpty()
    val list = remember(allEpisodes, unplayedOnly, oldestFirst, played) {
        val filtered = if (unplayedOnly) allEpisodes.filter { it.id !in played } else allEpisodes
        if (oldestFirst) filtered.reversed() else filtered
    }

    Box(modifier.fillMaxSize().background(tokens.colors.background)) {
        LazyColumn(
            contentPadding = PaddingValues(bottom = nuvioSafeBottomPadding(96.dp)),
            verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s12),
        ) {
            item {
                Column(Modifier.statusBarsPadding()) {
                    NuvioScreenHeader(title = "", includeStatusBarPadding = false, onBack = onBack, modifier = Modifier.padding(horizontal = NuvioTokens.Space.s8))
                }
            }
            item {
                Row(Modifier.padding(horizontal = NuvioTokens.Space.s16).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s16)) {
                    ShowArtwork(shown.imageUrl, 130.dp, radius = NuvioTokens.Radius.xl)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6)) {
                        Text(shown.title, style = MaterialTheme.typography.headlineMedium, color = tokens.colors.textPrimary, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        val sub = listOfNotNull(shown.publisher.ifBlank { null }, shown.sport).joinToString(" · ")
                        if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodyMedium, color = tokens.colors.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            if (shown.description.isNotBlank()) {
                item {
                    Column(Modifier.padding(horizontal = NuvioTokens.Space.s16)) {
                        Text(
                            shown.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = tokens.colors.textSecondary,
                            maxLines = if (expanded) Int.MAX_VALUE else 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            if (expanded) "less" else "more",
                            style = MaterialTheme.typography.titleSmall,
                            color = tokens.colors.accent,
                            modifier = Modifier.clickable { expanded = !expanded }.padding(top = 4.dp),
                        )
                    }
                }
            }
            val ready = load as? ShowLoad.Ready
            item {
                Row(Modifier.padding(horizontal = NuvioTokens.Space.s16), horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s10)) {
                    FollowPill(following) { PodcastRepository.toggleSubscription(shown) }
                    ready?.show?.episodes?.firstOrNull()?.let { latest ->
                        Row(
                            Modifier.clip(RoundedCornerShape(999.dp)).background(tokens.colors.accent).clickable { PodcastPlayback.play(latest) }
                                .padding(horizontal = 18.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = tokens.colors.onAccent, modifier = Modifier.size(20.dp))
                            Text("Play Latest Episode", style = MaterialTheme.typography.titleSmall, color = tokens.colors.onAccent, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            when (load) {
                ShowLoad.Loading -> item {
                    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        repeat(4) { com.nuvio.app.core.ui.SkeletonBlock(modifier = Modifier.fillMaxWidth(), height = 64.dp, cornerRadius = 12.dp) }
                    }
                }
                ShowLoad.Failed -> item { Text("This show's episodes couldn't be loaded right now.", style = MaterialTheme.typography.bodyMedium, color = tokens.colors.textMuted, modifier = Modifier.padding(NuvioTokens.Space.s16)) }
                is ShowLoad.Ready -> {
                    val episodes = (load as ShowLoad.Ready).show.episodes
                    val latest = episodes.first()
                    item {
                        LatestEpisodeCard(
                            episode = latest,
                            playing = PodcastPlayback.isCurrent(latest, player.song?.videoId) && player.isPlaying,
                            onPlay = {
                                if (PodcastPlayback.isCurrent(latest, player.song?.videoId)) MusicPlayer.togglePlay() else PodcastPlayback.play(latest)
                            },
                            modifier = Modifier.padding(horizontal = NuvioTokens.Space.s16),
                        )
                    }
                    item {
                        Row(Modifier.fillMaxWidth().padding(horizontal = NuvioTokens.Space.s16), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8)) {
                            Text("All Episodes", style = MaterialTheme.typography.headlineSmall, color = tokens.colors.textPrimary, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Chip("Unplayed", selected = unplayedOnly, onClick = { unplayedOnly = !unplayedOnly })
                            SortToggle(if (oldestFirst) "Oldest" else "Newest") { oldestFirst = !oldestFirst }
                        }
                    }
                    if (list.isEmpty()) {
                        item { Text("You're all caught up.", style = MaterialTheme.typography.bodyMedium, color = tokens.colors.textMuted, modifier = Modifier.padding(horizontal = NuvioTokens.Space.s16)) }
                    }
                    items(list, key = { it.id }) { episode ->
                        val current = PodcastPlayback.isCurrent(episode, player.song?.videoId)
                        EpisodeRow(
                            episode = episode,
                            artwork = shown.imageUrl,
                            current = current,
                            playing = current && player.isPlaying,
                            played = episode.id in played,
                            onClick = { if (current) MusicPlayer.togglePlay() else PodcastPlayback.play(episode) },
                        )
                    }
                }
            }
        }
        PodcastMiniPlayer(Modifier.align(Alignment.BottomCenter).padding(bottom = nuvioSafeBottomPadding(8.dp)))
    }
}

@Composable
private fun FollowPill(following: Boolean, onClick: () -> Unit) {
    val tokens = MaterialTheme.nuvio
    val shape = RoundedCornerShape(999.dp)
    Row(
        Modifier.clip(shape).background(if (following) Color.Transparent else tokens.colors.surface)
            .border(1.dp, tokens.colors.borderDefault, shape).clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(if (following) Icons.Rounded.Check else Icons.Rounded.Add, contentDescription = null, tint = tokens.colors.textPrimary, modifier = Modifier.size(18.dp))
        Text(if (following) "Following" else "Follow", style = MaterialTheme.typography.titleSmall, color = tokens.colors.textPrimary, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun LatestEpisodeCard(episode: PodcastEpisode, playing: Boolean, onPlay: () -> Unit, modifier: Modifier = Modifier) {
    val tokens = MaterialTheme.nuvio
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(NuvioTokens.Radius.xxl)).background(tokens.colors.surface).clickable(onClick = onPlay).padding(NuvioTokens.Space.s20),
        verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8),
    ) {
        Text("LATEST EPISODE", style = MaterialTheme.typography.labelLarge, color = tokens.colors.accent, fontWeight = FontWeight.Bold)
        Text(episode.title, style = MaterialTheme.typography.titleLarge, color = tokens.colors.textPrimary, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (episode.description.isNotBlank()) Text(episode.description, style = MaterialTheme.typography.bodyMedium, color = tokens.colors.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Row(Modifier.fillMaxWidth().padding(top = NuvioTokens.Space.s6), verticalAlignment = Alignment.CenterVertically) {
            Text(
                listOf(PodcastRepository.relativeDate(episode.publishedMs), episode.lengthLabel).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            Box(Modifier.size(56.dp).clip(CircleShape).background(tokens.colors.accent), contentAlignment = Alignment.Center) {
                Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = if (playing) "Pause" else "Play", tint = tokens.colors.onAccent, modifier = Modifier.size(30.dp))
            }
        }
    }
}

@Composable
private fun EpisodeRow(episode: PodcastEpisode, artwork: String?, current: Boolean, playing: Boolean, played: Boolean, onClick: () -> Unit) {
    val tokens = MaterialTheme.nuvio
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = NuvioTokens.Space.s16, vertical = NuvioTokens.Space.s8),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s12),
    ) {
        ShowArtwork(episode.podcastImage ?: artwork, 66.dp, radius = NuvioTokens.Radius.md)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(NewsText.longDate(episode.publishedMs), style = MaterialTheme.typography.labelMedium, color = if (current) tokens.colors.accent else tokens.colors.textMuted, fontWeight = FontWeight.SemiBold)
            Text(
                episode.title,
                style = MaterialTheme.typography.titleSmall,
                color = if (played && !current) tokens.colors.textSecondary else tokens.colors.textPrimary,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (episode.description.isNotBlank()) Text(episode.description, style = MaterialTheme.typography.bodySmall, color = tokens.colors.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (episode.lengthLabel.isNotBlank()) Text(episode.lengthLabel, style = MaterialTheme.typography.bodySmall, color = tokens.colors.textSecondary)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(if (current) tokens.colors.accent else tokens.colors.surfaceElevated), contentAlignment = Alignment.Center) {
                Icon(
                    if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (playing) "Pause" else "Play",
                    tint = if (current) tokens.colors.onAccent else tokens.colors.textPrimary,
                    modifier = Modifier.size(22.dp),
                )
            }
            Box {
                Icon(Icons.Rounded.MoreHoriz, contentDescription = "More", tint = tokens.colors.textMuted, modifier = Modifier.size(36.dp).clip(CircleShape).clickable { menu = true }.padding(6.dp))
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Play") }, onClick = { menu = false; if (!current) PodcastPlayback.play(episode) })
                    DropdownMenuItem(text = { Text(if (played) "Already played" else "Mark as played") }, onClick = { menu = false; PodcastRepository.markPlayed(episode.id) })
                }
            }
        }
    }
}
