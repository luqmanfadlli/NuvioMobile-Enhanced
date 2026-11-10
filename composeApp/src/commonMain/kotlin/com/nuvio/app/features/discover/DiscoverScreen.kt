package com.nuvio.app.features.discover

import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.Chip
import com.nuvio.app.core.ui.NuvioScreenHeader
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.core.ui.nuvioSafeBottomPadding
import com.nuvio.app.core.ui.withDuplicateSafeLazyKeys
import com.nuvio.app.features.music.playback.MusicPlayer
import com.nuvio.app.features.news.ArticleHero
import com.nuvio.app.features.news.ArticleRow
import com.nuvio.app.features.news.NewsArticle
import com.nuvio.app.features.news.NewsRanking
import com.nuvio.app.features.news.NewsTopics
import com.nuvio.app.features.podcasts.Podcast
import com.nuvio.app.features.podcasts.PodcastCarousel
import com.nuvio.app.features.podcasts.PodcastRepository
import com.nuvio.app.features.sports.SportsCatalog
import com.nuvio.app.features.sports.SportsRepository
import com.nuvio.app.features.sportsfocus.SportsNews

private const val FOR_YOU = "foryou"
private const val ALL = "all"
private val POPULAR_SPORTS = setOf("Hockey", "American Football", "Basketball", "Baseball", "Soccer")

private fun podcastSportsFor(topic: String): Set<String> = when (topic) {
    "nhl" -> setOf("Hockey")
    "nfl" -> setOf("American Football")
    "nba" -> setOf("Basketball")
    "mlb" -> setOf("Baseball")
    "soccer" -> setOf("Soccer")
    "college" -> setOf("American Football", "Basketball")
    "racing" -> setOf("Motorsport", "Motorcycling")
    "golf" -> setOf("Golf")
    "tennis" -> setOf("Tennis")
    "combat" -> setOf("Combat Sports", "Pro Wrestling")
    else -> emptySet()
}

/**
 * Discover: sports news from several publishers and the podcasts around them. Stories open in the
 * app's own reader; shows open to their episodes and play in the background.
 */
@Composable
fun DiscoverScreen(
    onOpenArticle: (NewsArticle) -> Unit,
    onOpenPodcast: (Podcast) -> Unit,
    onOpenPodcasts: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = MaterialTheme.nuvio
    val news by SportsNews.state.collectAsStateWithLifecycle()
    val follows by SportsRepository.follows.collectAsStateWithLifecycle()
    val catalog by PodcastRepository.catalog.collectAsStateWithLifecycle()
    val player by MusicPlayer.state.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableStateOf(FOR_YOU) }
    LaunchedEffect(Unit) {
        SportsNews.ensureLoaded()
        PodcastRepository.ensureCatalogLoaded()
    }

    val topics = remember(news.articles) { NewsTopics.available(news.articles) }
    val articles = remember(news.articles, selected, follows) {
        when (selected) {
            FOR_YOU -> NewsRanking.forYou(news.articles, follows)
            ALL -> news.articles
            else -> topics.firstOrNull { it.key == selected }?.let { topic -> news.articles.filter(topic.matches) } ?: news.articles
        }
    }
    val heroIndex = remember(articles) { articles.take(8).indexOfFirst { it.imageUrl != null } }
    val hero = articles.getOrNull(heroIndex.coerceAtLeast(0))
    val rest = remember(articles, heroIndex) { articles.filterIndexed { i, _ -> i != heroIndex.coerceAtLeast(0) }.take(60) }

    val keyedRest = remember(rest) { rest.withDuplicateSafeLazyKeys { it.id } }
    val followedSports = remember(follows) {
        SportsRepository.followedLeagueIds(follows).mapNotNull { SportsCatalog.league(it)?.sport }.mapNotNull(PodcastRepository::catalogSport).toSet()
    }
    val teamShows by produceState(emptyList<Podcast>(), follows.teams) {
        value = follows.teams.take(2).flatMap { team -> runCatching { PodcastRepository.teamShows(team) }.getOrDefault(emptyList()) }
    }
    val shows = remember(catalog, selected, followedSports, teamShows, follows.teams) {
        when (selected) {
            FOR_YOU -> (teamShows + PodcastRepository.forSports(followedSports.ifEmpty { POPULAR_SPORTS }, follows.teams)).distinctBy { it.id }.take(16)
            ALL -> PodcastRepository.forSports(POPULAR_SPORTS, follows.teams, 16)
            else -> PodcastRepository.forSports(podcastSportsFor(selected), follows.teams, 16)
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().background(tokens.colors.background),
        contentPadding = PaddingValues(bottom = nuvioSafeBottomPadding(NuvioTokens.Space.s16) + if (player.isPodcast) 76.dp else 0.dp),
        verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s12),
    ) {
        item {
            Column(Modifier.statusBarsPadding()) {
                NuvioScreenHeader(title = "Discover", includeStatusBarPadding = false, modifier = Modifier.padding(horizontal = NuvioTokens.Space.s8))
                Text(
                    "Sports news and stories",
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.colors.textMuted,
                    modifier = Modifier.padding(horizontal = NuvioTokens.Space.s16),
                )
            }
        }
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = NuvioTokens.Space.s16),
                horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8),
            ) {
                item { Chip("For You", selected = selected == FOR_YOU, onClick = { selected = FOR_YOU }) }
                item { Chip("All", selected = selected == ALL, onClick = { selected = ALL }) }
                items(topics, key = { it.key }) { topic -> Chip(topic.label, selected = selected == topic.key, onClick = { selected = topic.key }) }
            }
        }
        if (news.loading && articles.isEmpty()) {
            item {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    com.nuvio.app.core.ui.SkeletonBlock(modifier = Modifier.fillMaxWidth(), height = 200.dp, cornerRadius = 16.dp)
                    repeat(3) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            com.nuvio.app.core.ui.SkeletonBlock(modifier = Modifier.width(96.dp), height = 72.dp, cornerRadius = 10.dp)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                com.nuvio.app.core.ui.SkeletonBlock(modifier = Modifier.fillMaxWidth(), height = 16.dp, cornerRadius = 6.dp)
                                com.nuvio.app.core.ui.SkeletonBlock(modifier = Modifier.fillMaxWidth(0.6f), height = 16.dp, cornerRadius = 6.dp)
                            }
                        }
                    }
                }
            }
        }
        news.error?.let { message ->
            if (articles.isEmpty()) item { Text(message, style = MaterialTheme.typography.bodyMedium, color = tokens.colors.textMuted, modifier = Modifier.padding(NuvioTokens.Space.s16)) }
        }
        hero?.let { lead ->
            item(key = "hero") { ArticleHero(lead, onClick = { onOpenArticle(lead) }, modifier = Modifier.padding(horizontal = NuvioTokens.Space.s16)) }
        }
        if (shows.isNotEmpty()) {
            item(key = "podcasts") { PodcastCarousel(shows, onOpenShow = onOpenPodcast, onSeeAll = onOpenPodcasts) }
        }
        if (!news.loading && articles.isEmpty() && news.error == null) {
            item { Text("No stories for this yet.", style = MaterialTheme.typography.bodyMedium, color = tokens.colors.textMuted, modifier = Modifier.padding(NuvioTokens.Space.s16)) }
        }
        items(keyedRest, key = { it.lazyKey }) { entry ->
            ArticleRow(entry.value, onClick = { onOpenArticle(entry.value) }, modifier = Modifier.padding(horizontal = NuvioTokens.Space.s16))
        }
    }
}
