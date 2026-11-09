package com.nuvio.app.features.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.nuvio.app.core.network.NetworkStatusRepository
import com.nuvio.app.core.ui.Chip
import com.nuvio.app.core.ui.NuvioInputField
import com.nuvio.app.core.ui.NuvioScreen
import com.nuvio.app.core.ui.NuvioScreenHeader
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.background.appBackgroundCover
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.core.ui.withDuplicateSafeLazyKeys
import com.nuvio.app.features.addons.AddonRepository
import com.nuvio.app.features.addons.hasPendingEnabledManifests
import com.nuvio.app.features.addons.httpGetText
import com.nuvio.app.features.home.HomeCatalogSettingsRepository
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.buildAddonCatalogRefreshSignature
import com.nuvio.app.features.home.components.HomeCatalogRowSection
import com.nuvio.app.features.home.components.HomeSkeletonRow
import com.nuvio.app.features.home.components.homeSectionHorizontalPaddingForWidth
import com.nuvio.app.features.home.components.posterGridColumnCountForWidth
import com.nuvio.app.features.livetv.LiveTvChannel
import com.nuvio.app.features.livetv.LiveTvRepository
import com.nuvio.app.features.music.data.YtMusicRepository
import com.nuvio.app.features.music.data.model.BrowseType
import com.nuvio.app.features.music.data.model.SearchFilter
import com.nuvio.app.features.music.data.model.SearchResult
import com.nuvio.app.features.music.data.model.Song
import com.nuvio.app.features.music.playback.MusicPlayer
import com.nuvio.app.features.music.ui.MusicDest
import com.nuvio.app.features.music.ui.MusicHost
import com.nuvio.app.features.news.ArticleRow
import com.nuvio.app.features.news.NewsArticle
import com.nuvio.app.features.news.NewsRanking
import com.nuvio.app.features.news.rememberInAppBrowser
import com.nuvio.app.features.podcasts.Podcast
import com.nuvio.app.features.podcasts.PodcastRepository
import com.nuvio.app.features.podcasts.ShowRow
import com.nuvio.app.features.sports.GameState
import com.nuvio.app.features.sports.SportsCatalog
import com.nuvio.app.features.sports.SportsEvent
import com.nuvio.app.features.sports.SportsRepository
import com.nuvio.app.features.sports.TeamInfo
import com.nuvio.app.features.sports.ui.SportsActions
import com.nuvio.app.features.sports.ui.TeamSheet
import com.nuvio.app.features.sports.ui.use24h
import com.nuvio.app.features.sportsfocus.Chevron
import com.nuvio.app.features.sportsfocus.HitRow
import com.nuvio.app.features.sportsfocus.LeagueRow
import com.nuvio.app.features.sportsfocus.MatchRow
import com.nuvio.app.features.sportsfocus.RemoteSearch
import com.nuvio.app.features.sportsfocus.RowShell
import com.nuvio.app.features.sportsfocus.SportsNews
import com.nuvio.app.features.sportsfocus.SportsSearchData
import com.nuvio.app.features.sportsfocus.TeamRow
import com.nuvio.app.features.tmdb.TmdbSettingsRepository
import com.nuvio.app.features.tmdb.buildTmdbUrl
import com.nuvio.app.features.watched.WatchedRepository
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

/** An actor, director or other person from TMDB, with what they're known for. */
internal data class PersonHit(val id: Int, val name: String, val photo: String?, val subtitle: String?)

/** Sources the addon catalogs don't cover: people come from TMDB. */
internal object UniversalSearchData {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun people(query: String): List<PersonHit> {
        val key = TmdbSettingsRepository.effectiveApiKey()
        if (key.isBlank()) return emptyList()
        val url = buildTmdbUrl(
            endpoint = "search/person",
            apiKey = key,
            query = mapOf(
                "query" to query.encodeURLParameter(),
                "include_adult" to "false",
                "language" to TmdbSettingsRepository.snapshot().language,
            ),
        )
        val body = runCatching { httpGetText(url) }.getOrNull() ?: return emptyList()
        val results = runCatching { json.parseToJsonElement(body).jsonObject["results"] as? JsonArray }.getOrNull().orEmpty()
        return results.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val id = (o["id"] as? JsonPrimitive)?.intOrNull ?: return@mapNotNull null
            val name = o.str("name") ?: return@mapNotNull null
            val knownFor = (o["known_for"] as? JsonArray).orEmpty()
                .mapNotNull { (it as? JsonObject)?.let { k -> k.str("title") ?: k.str("name") } }
                .take(2)
            PersonHit(
                id = id,
                name = name,
                photo = o.str("profile_path")?.let { "https://image.tmdb.org/t/p/w185$it" },
                subtitle = listOfNotNull(o.str("known_for_department"), knownFor.takeIf { it.isNotEmpty() }?.joinToString(", "))
                    .joinToString(" · ").ifBlank { null },
            )
        }.sortedByDescending { it.photo != null }.take(12)
    }

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
}

internal enum class UniversalScope(val label: String) {
    All("All"), Media("Movies & Shows"), People("People"), Sports("Sports"), Music("Music"), Podcasts("Podcasts"), Channels("Live TV"),
}

/**
 * Everything the search box knows. It outlives the screen so a query and its results are still there
 * when you come back from an artist page.
 */
internal class UniversalSearchModel {
    var query by mutableStateOf("")
    var scope by mutableStateOf(UniversalScope.All)
    var people by mutableStateOf<List<PersonHit>>(emptyList())
    var sports by mutableStateOf(RemoteSearch())
    var music by mutableStateOf<List<SearchResult>>(emptyList())
    var podcasts by mutableStateOf<List<Podcast>>(emptyList())
    var searching by mutableStateOf(false)
    /** The query the lists above answer, so a screen coming back doesn't search again. */
    var resultsFor by mutableStateOf<String?>(null)
    /** The query and filter the list was last scrolled to the top for. */
    var scrolledFor: String? = null

    fun clearRemote() {
        people = emptyList(); sports = RemoteSearch(); music = emptyList(); podcasts = emptyList()
        searching = false; resultsFor = null
    }
}

/** The Search tab in Balanced mode: one box for movies, shows, people, sports, music, podcasts and channels. */
@Composable
internal fun UniversalSearchTab(
    sportsActions: SportsActions,
    onPosterClick: ((MetaPreview) -> Unit)?,
    onPosterLongClick: ((MetaPreview) -> Unit)?,
    onPersonClick: (id: Int, name: String, photo: String?) -> Unit,
    onChannelClick: (LiveTvChannel) -> Unit,
    onOpenPodcast: (Podcast) -> Unit,
    onOpenArticle: (NewsArticle) -> Unit,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val model = remember { UniversalSearchModel() }
    MusicHost(modifier) { openMusic ->
        UniversalSearchScreen(
            model = model,
            listState = listState,
            sportsActions = sportsActions,
            onPosterClick = onPosterClick,
            onPosterLongClick = onPosterLongClick,
            onPersonClick = onPersonClick,
            onChannelClick = onChannelClick,
            onOpenPodcast = onOpenPodcast,
            onOpenArticle = onOpenArticle,
            onOpenMusic = openMusic,
        )
    }
}

@Composable
private fun UniversalSearchScreen(
    model: UniversalSearchModel,
    listState: LazyListState,
    sportsActions: SportsActions,
    onPosterClick: ((MetaPreview) -> Unit)?,
    onPosterLongClick: ((MetaPreview) -> Unit)?,
    onPersonClick: (id: Int, name: String, photo: String?) -> Unit,
    onChannelClick: (LiveTvChannel) -> Unit,
    onOpenPodcast: (Podcast) -> Unit,
    onOpenArticle: (NewsArticle) -> Unit,
    onOpenMusic: (MusicDest) -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val query = model.query
    val q = query.trim()
    val active = q.length >= 2
    val scope = model.scope

    val addonsUiState by AddonRepository.uiState.collectAsStateWithLifecycle()
    val uiState by SearchRepository.uiState.collectAsStateWithLifecycle()
    val discoverUiState by SearchRepository.discoverUiState.collectAsStateWithLifecycle()
    val homeCatalogSettings by remember {
        HomeCatalogSettingsRepository.snapshot()
        HomeCatalogSettingsRepository.uiState
    }.collectAsStateWithLifecycle()
    val recentSearches by SearchHistoryRepository.uiState.collectAsStateWithLifecycle()
    val watchedUiState by WatchedRepository.uiState.collectAsStateWithLifecycle()
    val fullyWatchedSeriesKeys by WatchedRepository.fullyWatchedSeriesKeys.collectAsStateWithLifecycle()
    val networkStatus by NetworkStatusRepository.uiState.collectAsStateWithLifecycle()
    val liveTv by LiveTvRepository.uiState.collectAsStateWithLifecycle()
    val known by SportsRepository.known.collectAsStateWithLifecycle()
    val events by SportsRepository.events.collectAsStateWithLifecycle()
    val news by SportsNews.state.collectAsStateWithLifecycle()
    val catalog by PodcastRepository.catalog.collectAsStateWithLifecycle()
    val openInApp = rememberInAppBrowser()
    val use24h = use24h()
    var teamSheet by remember { mutableStateOf<TeamInfo?>(null) }
    var searchFocused by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        AddonRepository.initialize()
        WatchedRepository.ensureLoaded()
        SearchHistoryRepository.ensureLoaded()
        SportsNews.ensureLoaded()
        PodcastRepository.ensureCatalogLoaded()
        LiveTvRepository.ensureLoaded()
    }

    val addonRefreshKey = remember(addonsUiState.addons) { buildAddonCatalogRefreshSignature(addonsUiState.addons) }
    val addonManifestsLoading = addonsUiState.addons.hasPendingEnabledManifests()

    // Movies and shows: the same addon search the regular Search tab uses.
    LaunchedEffect(addonRefreshKey, homeCatalogSettings.hideUnreleasedContent) {
        SearchRepository.refreshDiscover(addonsUiState.addons)
    }
    LaunchedEffect(q, addonRefreshKey, homeCatalogSettings.hideUnreleasedContent) {
        if (q.isBlank()) {
            SearchRepository.clear()
        } else {
            delay(350)
            SearchRepository.search(query = q, addons = addonsUiState.addons)
        }
    }
    LaunchedEffect(listState, q, discoverUiState.canLoadMore, discoverUiState.isLoading) {
        if (q.isNotBlank()) return@LaunchedEffect
        snapshotFlow { listState.layoutInfo }
            .map { info -> (info.visibleItemsInfo.lastOrNull()?.index ?: -1) >= info.totalItemsCount - 4 }
            .distinctUntilChanged()
            .filter { it && discoverUiState.canLoadMore && !discoverUiState.isLoading }
            .collect { SearchRepository.loadMoreDiscover() }
    }

    // A new query or filter starts at the top, but coming back from a page keeps your place.
    LaunchedEffect(q, scope) {
        val key = "$q|${scope.name}"
        if (model.scrolledFor != key) {
            model.scrolledFor = key
            listState.scrollToItem(0)
        }
    }

    // Everything else: asked in parallel, each list fills in as soon as its source answers.
    LaunchedEffect(q) {
        if (!active) { model.clearRemote(); return@LaunchedEffect }
        if (model.resultsFor == q) return@LaunchedEffect
        model.people = emptyList(); model.sports = RemoteSearch(); model.music = emptyList(); model.podcasts = emptyList()
        delay(350)
        model.searching = true
        coroutineScope {
            launch { model.people = runCatching { UniversalSearchData.people(q) }.getOrDefault(emptyList()) }
            launch { model.sports = runCatching { SportsSearchData.remote(q) }.getOrDefault(RemoteSearch()) }
            launch { model.music = runCatching { YtMusicRepository.searchPage(q, SearchFilter.ALL).getOrNull()?.rows }.getOrNull().orEmpty() }
            launch { model.podcasts = runCatching { PodcastRepository.searchDirectory(q, 8) }.getOrDefault(emptyList()) }
        }
        model.resultsFor = q
        model.searching = false
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
    val remoteTeams = remember(teams, model.sports) { model.sports.teams.filter { r -> teams.none { it.fullName.equals(r.title, true) } }.take(6) }
    val stories = remember(q, news.articles, model.sports) {
        if (!active) emptyList() else {
            val own = news.articles.filter { it.headline.contains(q, true) || it.summary.contains(q, true) }.take(12)
            val ids = own.map { NewsRanking.urlKey(it.url) }.toSet()
            val more = model.sports.articles.filter { NewsRanking.urlKey(it.url) !in ids }.mapNotNull { h ->
                h.url?.let { NewsArticle(id = "search:" + it, headline = h.title, summary = h.subtitle.orEmpty(), imageUrl = h.imageUrl, url = it, publishedMs = h.publishedMs, leagueId = null, leagueShort = null, publisher = "ESPN") }
            }
            own + more
        }
    }
    val shows = remember(q, catalog, model.podcasts) {
        if (!active) emptyList() else (PodcastRepository.searchCatalog(q) + model.podcasts).distinctBy { it.id }.take(14)
    }
    val channels = remember(q, liveTv.channels) {
        if (!active) emptyList() else {
            val words = q.lowercase().split(' ').filter { it.isNotBlank() }
            liveTv.channels.filter { c -> val hay = (c.name + " " + c.group.orEmpty()).lowercase(); words.all { it in hay } }.distinctBy { it.id }.take(40)
        }
    }
    val songs: List<Song> = remember(model.music) { model.music.mapNotNull { (it as? SearchResult.Track)?.song ?: (it as? SearchResult.TopTrack)?.song } }

    val hasMedia = uiState.sections.isNotEmpty()
    val hasSports = teams.isNotEmpty() || remoteTeams.isNotEmpty() || model.sports.players.isNotEmpty() || matches.isNotEmpty() || leagues.isNotEmpty() || stories.isNotEmpty()
    val anything = hasMedia || model.people.isNotEmpty() || hasSports || model.music.isNotEmpty() || shows.isNotEmpty() || channels.isNotEmpty()
    val loadingMedia = (uiState.isLoading || addonManifestsLoading) && !hasMedia

    LaunchedEffect(q, hasMedia, uiState.isLoading) {
        if (active && hasMedia && !uiState.isLoading) SearchHistoryRepository.recordSearch(q)
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(tokens.colors.background)) {
        val discoverColumns = remember(maxWidth) { posterGridColumnCountForWidth(maxWidth) }
        val sectionPadding = remember(maxWidth) { homeSectionHorizontalPaddingForWidth(maxWidth.value) }
        val edge = Modifier.padding(horizontal = NuvioTokens.Space.s16)
        val show = { s: UniversalScope -> scope == UniversalScope.All || scope == s }
        val limit = { all: Int, one: Int -> if (scope == UniversalScope.All) all else one }
        val seeAll = { s: UniversalScope -> if (scope == UniversalScope.All) ({ model.scope = s }) else null }

        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().appBackgroundCover(MaterialTheme.colorScheme.background)) {
                Column(Modifier.fillMaxWidth()) {
                    NuvioScreenHeader(title = "Search", modifier = Modifier.padding(horizontal = 16.dp))
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.padding(horizontal = 16.dp)) {
                        NuvioInputField(
                            value = query,
                            onValueChange = { model.query = it },
                            placeholder = "Movies, shows, actors, teams, music…",
                            shape = RoundedCornerShape(NuvioTokens.Radius.xl),
                            modifier = Modifier.onFocusChanged { searchFocused = it.isFocused },
                            trailingContent = when {
                                model.searching -> ({ CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp)) })
                                query.isNotBlank() -> ({
                                    IconButton(onClick = { model.query = "" }) {
                                        Icon(Icons.Rounded.Close, contentDescription = "Clear", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                })
                                else -> null
                            },
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    if (q.isNotBlank()) {
                        LazyRow(contentPadding = PaddingValues(horizontal = NuvioTokens.Space.s16), horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8)) {
                            items(UniversalScope.entries.toList(), key = { it.name }) { s -> Chip(s.label, selected = scope == s, onClick = { model.scope = s }) }
                        }
                        Spacer(Modifier.height(10.dp))
                    } else {
                        DiscoverFilterRow(
                            state = discoverUiState,
                            modifier = Modifier.padding(horizontal = 16.dp).padding(top = 2.dp, bottom = 14.dp),
                            onTypeSelected = SearchRepository::selectDiscoverType,
                            onCatalogSelected = SearchRepository::selectDiscoverCatalog,
                            onGenreSelected = SearchRepository::selectDiscoverGenre,
                        )
                    }
                }
            }

            NuvioScreen(horizontalPadding = 0.dp, topPadding = 0.dp, listState = listState, autoHidesNativeTabBar = true, modifier = Modifier.weight(1f)) {
                if (q.isBlank()) {
                    if (searchFocused && recentSearches.isNotEmpty()) {
                        item(key = "recent_searches") {
                            Column(edge.padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Recent searches", style = MaterialTheme.typography.titleMedium, color = tokens.colors.textPrimary, fontWeight = FontWeight.Bold)
                                recentSearches.forEach { r ->
                                    Row(Modifier.fillMaxWidth().clickable { model.query = r }.padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text(r, style = MaterialTheme.typography.bodyLarge, color = tokens.colors.textPrimary, modifier = Modifier.weight(1f))
                                        Icon(Icons.Rounded.Close, contentDescription = "Remove", tint = tokens.colors.textMuted, modifier = Modifier.size(18.dp).clickable { SearchHistoryRepository.removeSearch(r) })
                                    }
                                }
                            }
                        }
                    }
                    discoverContent(
                        state = discoverUiState,
                        isSourceLoading = addonManifestsLoading,
                        columns = discoverColumns,
                        networkCondition = networkStatus.condition,
                        onTypeSelected = SearchRepository::selectDiscoverType,
                        onCatalogSelected = SearchRepository::selectDiscoverCatalog,
                        onGenreSelected = SearchRepository::selectDiscoverGenre,
                        onRetry = { NetworkStatusRepository.requestRefresh(force = true); SearchRepository.refreshDiscover(addonsUiState.addons, forceRefresh = true) },
                        watchedKeys = watchedUiState.watchedKeys,
                        fullyWatchedSeriesKeys = fullyWatchedSeriesKeys,
                        onPosterClick = onPosterClick,
                        onPosterLongClick = onPosterLongClick,
                    )
                    return@NuvioScreen
                }

                // ---- movies & shows
                if (show(UniversalScope.Media)) {
                    if (loadingMedia) {
                        items(if (scope == UniversalScope.All) 1 else 2) { HomeSkeletonRow(modifier = Modifier.padding(horizontal = sectionPadding)) }
                    } else {
                        items(items = uiState.sections.withDuplicateSafeLazyKeys { it.key }.let { if (scope == UniversalScope.All) it.take(2) else it }, key = { "media-" + it.lazyKey }) { keyed ->
                            HomeCatalogRowSection(
                                section = keyed.value,
                                modifier = Modifier.padding(bottom = 12.dp),
                                watchedKeys = watchedUiState.watchedKeys,
                                fullyWatchedSeriesKeys = fullyWatchedSeriesKeys,
                                onPosterClick = onPosterClick,
                                onPosterLongClick = onPosterLongClick,
                            )
                        }
                        if (scope == UniversalScope.All && uiState.sections.size > 2) {
                            item(key = "media-more") { SeeAllText("See all movies & shows") { model.scope = UniversalScope.Media } }
                        }
                    }
                }

                // ---- people
                if (show(UniversalScope.People) && model.people.isNotEmpty()) {
                    item(key = "h-people") { UniversalHeading("People", seeAll(UniversalScope.People)?.takeIf { model.people.size > 4 }) }
                    items(model.people.take(limit(4, 30)), key = { "pe-" + it.id }) { p -> PersonRow(p) { onPersonClick(p.id, p.name, p.photo) } }
                }

                // ---- sports
                if (show(UniversalScope.Sports)) {
                    if (teams.isNotEmpty() || remoteTeams.isNotEmpty()) {
                        item(key = "h-teams") { UniversalHeading("Teams", seeAll(UniversalScope.Sports)?.takeIf { teams.size + remoteTeams.size > 3 }) }
                        items(teams.take(limit(3, 12)), key = { "tm-" + it.key }) { t -> TeamRow(t) { teamSheet = t } }
                        items(remoteTeams.take(limit(if (teams.isEmpty()) 3 else 0, 6)), key = { "rt-" + it.title + it.subtitle }) { h -> HitRow(h, round = true) { h.url?.let(openInApp) } }
                    }
                    if (model.sports.players.isNotEmpty()) {
                        item(key = "h-players") { UniversalHeading("Players", seeAll(UniversalScope.Sports)?.takeIf { model.sports.players.size > 3 }) }
                        items(model.sports.players.take(limit(3, 12)), key = { "pl-" + it.title + it.subtitle }) { h -> HitRow(h, round = true, person = true) { h.url?.let(openInApp) } }
                    }
                    if (matches.isNotEmpty()) {
                        item(key = "h-matches") { UniversalHeading("Matches", seeAll(UniversalScope.Sports)?.takeIf { matches.size > 3 }) }
                        items(matches.take(limit(3, 14)), key = { "mt-" + it.id }) { ev -> MatchRow(ev, use24h) { sportsActions.onOpenGame(ev) } }
                    }
                    if (leagues.isNotEmpty()) {
                        item(key = "h-leagues") { UniversalHeading("Leagues") }
                        items(leagues.take(limit(2, 10)), key = { "lg-" + it.id }) { l -> LeagueRow(l) { sportsActions.onOpenLeague(l) } }
                    }
                    if (stories.isNotEmpty()) {
                        item(key = "h-news") { UniversalHeading("Sports news", seeAll(UniversalScope.Sports)?.takeIf { stories.size > 3 }) }
                        items(stories.take(limit(3, 30)), key = { "st-" + it.id }) { a -> ArticleRow(a, onClick = { onOpenArticle(a) }, modifier = edge) }
                    }
                }

                // ---- music
                if (show(UniversalScope.Music) && model.music.isNotEmpty()) {
                    item(key = "h-music") { UniversalHeading("Music", seeAll(UniversalScope.Music)?.takeIf { model.music.size > 5 }) }
                    items(model.music.take(limit(5, 40)).size, key = { "mu-$it" }) { i ->
                        when (val row = model.music[i]) {
                            is SearchResult.TopTrack -> SongHitRow(row.song) { MusicPlayer.play(songs, songs.indexOfFirst { it.videoId == row.song.videoId }.coerceAtLeast(0)) }
                            is SearchResult.Track -> SongHitRow(row.song) { MusicPlayer.play(songs, songs.indexOfFirst { it.videoId == row.song.videoId }.coerceAtLeast(0)) }
                            is SearchResult.Browse -> {
                                val b = row.item
                                ImageRow(b.thumbnailUrl, b.title, b.subtitle.ifBlank { b.type.name.lowercase().replaceFirstChar { it.uppercase() } }, round = b.type == BrowseType.ARTIST, fallback = { Icon(Icons.Rounded.MusicNote, null, tint = tokens.colors.textMuted) }) {
                                    onOpenMusic(MusicDest.Browse(b.browseId, b.title, b.thumbnailUrl))
                                }
                            }
                        }
                    }
                }

                // ---- podcasts
                if (show(UniversalScope.Podcasts) && shows.isNotEmpty()) {
                    item(key = "h-pod") { UniversalHeading("Podcasts", seeAll(UniversalScope.Podcasts)?.takeIf { shows.size > 3 }) }
                    items(shows.take(limit(3, 30)), key = { "pc-" + it.id }) { s -> ShowRow(s, onClick = { onOpenPodcast(s) }, chevron = true) }
                }

                // ---- live tv channels
                if (show(UniversalScope.Channels) && channels.isNotEmpty()) {
                    item(key = "h-ch") { UniversalHeading("Live TV", seeAll(UniversalScope.Channels)?.takeIf { channels.size > 4 }) }
                    items(channels.take(limit(4, 40)), key = { "ch-" + it.id }) { c ->
                        ImageRow(c.logoUrl, c.name, c.group ?: c.playlistName, round = false, fallback = { Icon(Icons.Rounded.LiveTv, null, tint = tokens.colors.textMuted) }) { onChannelClick(c) }
                    }
                }

                if (!anything && !model.searching && !loadingMedia) {
                    item(key = "empty") {
                        Text("Nothing found for “$q”. Try a title, an actor, a team or an artist.", style = MaterialTheme.typography.bodyMedium, color = tokens.colors.textMuted, modifier = edge.padding(top = NuvioTokens.Space.s8))
                    }
                }
            }
        }
    }
    teamSheet?.let { t -> TeamSheet(t, sportsActions, onDismiss = { teamSheet = null }) }
}

@Composable
private fun UniversalHeading(text: String, onSeeAll: (() -> Unit)? = null) {
    val tokens = MaterialTheme.nuvio
    Row(Modifier.fillMaxWidth().padding(horizontal = NuvioTokens.Space.s16).padding(top = NuvioTokens.Space.s8), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleLarge, color = tokens.colors.textPrimary, fontWeight = FontWeight.Bold)
        if (onSeeAll != null) Text("See all", style = MaterialTheme.typography.labelLarge, color = tokens.colors.accent, modifier = Modifier.clickable(onClick = onSeeAll).padding(vertical = 4.dp))
    }
}

@Composable
private fun SeeAllText(text: String, onClick: () -> Unit) {
    val tokens = MaterialTheme.nuvio
    Text(text, style = MaterialTheme.typography.labelLarge, color = tokens.colors.accent, modifier = Modifier.padding(horizontal = NuvioTokens.Space.s16, vertical = 4.dp).clickable(onClick = onClick))
}

@Composable
private fun PersonRow(p: PersonHit, onClick: () -> Unit) =
    ImageRow(p.photo, p.name, p.subtitle, round = true, crop = true, fallback = { Icon(Icons.Rounded.Person, null, tint = MaterialTheme.nuvio.colors.textMuted) }, onClick = onClick)

@Composable
private fun SongHitRow(song: Song, onClick: () -> Unit) =
    ImageRow(song.thumbnailUrl, song.title, song.artist, round = false, crop = true, fallback = { Icon(Icons.Rounded.MusicNote, null, tint = MaterialTheme.nuvio.colors.textMuted) }, onClick = onClick)

@Composable
private fun ImageRow(
    image: String?,
    title: String,
    subtitle: String?,
    round: Boolean,
    crop: Boolean = false,
    fallback: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    RowShell(onClick) {
        Box(Modifier.size(44.dp).clip(if (round) CircleShape else RoundedCornerShape(8.dp)).background(tokens.colors.surfaceElevated), contentAlignment = Alignment.Center) {
            if (image != null) AsyncImage(model = image, contentDescription = null, contentScale = if (crop) ContentScale.Crop else ContentScale.Fit, modifier = Modifier.size(if (crop) 44.dp else 32.dp))
            else fallback()
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = tokens.colors.textPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = tokens.colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        Chevron()
    }
}
