package com.nuvio.app.features.sportsfocus

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.features.sports.SportsRepository
import com.nuvio.app.features.sports.ui.SportsSearchTab
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.PlatformBackHandler
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.discover.DiscoverScreen
import com.nuvio.app.features.news.NewsArticle
import com.nuvio.app.features.podcasts.Podcast
import com.nuvio.app.features.sports.ui.SportsActions

private enum class BalancedSportsView(val title: String) { Home("Sports"), Following("Following"), Discover("Discover"), Teams("Pick Your Teams") }

/**
 * The Sports tab in Balanced mode: the Sports-focus home page (minus its greeting). Following and Discover,
 * which are tabs of their own in Sports focus, open from its links and come back with the back arrow.
 */
@Composable
fun BalancedSportsTab(
    actions: SportsActions,
    onOpenArticle: (NewsArticle) -> Unit,
    onOpenPodcast: (Podcast) -> Unit,
    onOpenPodcasts: () -> Unit,
    onOpenLive: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = MaterialTheme.nuvio
    var viewName by rememberSaveable { mutableStateOf(BalancedSportsView.Home.name) }
    val view = BalancedSportsView.entries.firstOrNull { it.name == viewName } ?: BalancedSportsView.Home
    val goHome = { viewName = BalancedSportsView.Home.name }
    PlatformBackHandler(enabled = view != BalancedSportsView.Home, onBack = goHome)

    when (view) {
        BalancedSportsView.Home -> SportsFocusHome(
            actions = actions,
            onOpenNews = { viewName = BalancedSportsView.Discover.name },
            onOpenArticle = onOpenArticle,
            onOpenFollowing = { viewName = BalancedSportsView.Following.name },
            onOpenLive = onOpenLive,
            modifier = modifier.fillMaxSize(),
            showGreeting = false,
            teamsHeader = { MyTeamsStrip(onChoose = { viewName = BalancedSportsView.Teams.name }) },
        )
        else -> Column(modifier.fillMaxSize().background(tokens.colors.background)) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().height(44.dp).clickable(onClick = goHome).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = tokens.colors.textPrimary)
                Text(
                    if (view == BalancedSportsView.Teams) view.title else "Sports",
                    color = tokens.colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
            // The bar above already clears the status bar, so the page under it must not add that gap again.
            Box(Modifier.weight(1f).consumeWindowInsets(WindowInsets.statusBars)) {
                when (view) {
                    BalancedSportsView.Following -> SportsFocusFollowing(actions, onOpenArticle, Modifier.fillMaxSize())
                    BalancedSportsView.Teams -> {
                        // Search for a team or league, or browse by sport; the star follows or unfollows.
                        val follows by SportsRepository.follows.collectAsStateWithLifecycle()
                        val known by SportsRepository.known.collectAsStateWithLifecycle()
                        SportsSearchTab(known, follows, actions)
                    }
                    else -> DiscoverScreen(
                        onOpenArticle = onOpenArticle,
                        onOpenPodcast = onOpenPodcast,
                        onOpenPodcasts = onOpenPodcasts,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}
