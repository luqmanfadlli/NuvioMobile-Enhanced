package com.nuvio.app.features.settings

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.WindowInsets
import com.nuvio.app.navigation.LocalUseNativeNavigation
import co.touchlab.kermit.Logger
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CollectionsBookmark
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.animation.animateContentSize
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.format.resolveReleaseInfoForDisplay
import com.nuvio.app.core.ui.platformPhysicalTopInset
import com.nuvio.app.core.ui.NuvioPrimaryButton
import com.nuvio.app.core.ui.NuvioModalBottomSheet
import com.nuvio.app.core.ui.accentBrush
import com.nuvio.app.core.ui.gradientMask
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.core.ui.themePalette
import com.nuvio.app.core.ui.NuvioAsyncImage
import com.nuvio.app.core.ui.glass.LiquidGlassSurface
import com.nuvio.app.core.ui.glass.isLiquidGlassSurfaceEffectivelyEnabled
import com.nuvio.app.core.ui.glass.isLiquidGlassSupported
import com.nuvio.app.core.ui.glass.liquidGlassForSurface
import com.nuvio.app.core.ui.glass.liquidGlassShape
import com.nuvio.app.features.details.MetaDetailsRepository
import com.nuvio.app.features.details.MetaLookupOutcome
import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.library.LibraryItem
import com.nuvio.app.features.library.LibraryRepository
import com.nuvio.app.features.library.LibraryUiState
import com.nuvio.app.features.library.LibraryUpcomingEpisode
import com.nuvio.app.features.library.libraryUpcomingEpisodesFlow
import com.nuvio.app.features.library.forceRefreshLibraryReleaseSchedule
import com.nuvio.app.features.library.warmLibraryReleaseSchedule
import com.nuvio.app.features.profiles.AvatarCatalogItem
import com.nuvio.app.features.profiles.AvatarRepository
import com.nuvio.app.features.profiles.NuvioProfile
import com.nuvio.app.features.profiles.ProfileBackgroundBackdrop
import com.nuvio.app.features.profiles.ProfileRepository
import com.nuvio.app.features.profiles.parseHexColor
import com.nuvio.app.features.profiles.profileAvatarImageUrl
import com.nuvio.app.features.watched.WatchedClock
import com.nuvio.app.features.watched.WatchedItem
import com.nuvio.app.features.watched.WatchedRepository
import com.nuvio.app.features.watched.WatchedUiState
import com.nuvio.app.features.watched.watchedItemKey
import com.nuvio.app.features.watchprogress.CurrentDateProvider
import com.nuvio.app.features.watchprogress.WatchProgressEntry
import com.nuvio.app.features.watchprogress.WatchProgressRepository
import com.nuvio.app.features.watchprogress.WatchProgressUiState
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

private val profileInsightsLog = Logger.withTag("ProfileInsights")

internal fun LazyListScope.profileInsightsContent(
    isTablet: Boolean,
    onSwitchProfile: (() -> Unit)?,
    onEditProfile: (() -> Unit)?,
    onPosterClick: ((MetaPreview) -> Unit)?,
    onBack: (() -> Unit)? = null,
) {
    item {
        ProfileInsightsBody(
            isTablet = isTablet,
            onSwitchProfile = onSwitchProfile,
            onEditProfile = onEditProfile,
            onPosterClick = onPosterClick,
            onBack = onBack,
        )
    }
}

@Composable
private fun ProfileInsightsBody(
    isTablet: Boolean,
    onSwitchProfile: (() -> Unit)?,
    onEditProfile: (() -> Unit)?,
    onPosterClick: ((MetaPreview) -> Unit)?,
    onBack: (() -> Unit)? = null,
) {
    val profileState by ProfileRepository.state.collectAsStateWithLifecycle()
    val avatars by AvatarRepository.avatars.collectAsStateWithLifecycle()
    val watchProgressState by remember {
        WatchProgressRepository.ensureLoaded()
        WatchProgressRepository.uiState
    }.collectAsStateWithLifecycle()
    val watchedState by remember {
        WatchedRepository.ensureLoaded()
        WatchedRepository.uiState
    }.collectAsStateWithLifecycle()
    val fullyWatchedSeriesKeys by WatchedRepository.fullyWatchedSeriesKeys.collectAsStateWithLifecycle()
    val libraryState by remember {
        LibraryRepository.ensureLoaded()
        LibraryRepository.uiState
    }.collectAsStateWithLifecycle()
    val todayIsoDate = remember { CurrentDateProvider.todayIsoDate() }
    val upcomingEpisodes by remember {
        libraryUpcomingEpisodesFlow(days = PROFILE_UPCOMING_EPISODE_DAYS)
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    LaunchedEffect(libraryState.items) {
        warmLibraryReleaseSchedule(libraryState.items)
    }

    LaunchedEffect(Unit) {
        AvatarRepository.fetchAvatars()
    }

    val activeProfile = profileState.activeProfile
    val activeProfileIndex = activeProfile?.profileIndex ?: ProfileRepository.activeProfileId
    val avatarItem = remember(activeProfile?.avatarId, avatars) {
        activeProfile
            ?.avatarId
            ?.let { avatarId -> avatars.firstOrNull { avatar -> avatar.id == avatarId } }
    }
    val profileNameFallback = stringResource(Res.string.compose_nav_profile)
    val profileName = activeProfile
        ?.name
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: profileNameFallback
    val continueTitle = stringResource(Res.string.profile_insights_stat_continue)
    val watchedTitle = stringResource(Res.string.profile_insights_stat_watched)
    val completedTitle = stringResource(Res.string.profile_insights_stat_completed)
    val ongoingTitle = stringResource(Res.string.profile_insights_stat_ongoing)
    val libraryTitle = stringResource(Res.string.profile_insights_stat_library)
    val upcomingTitle = stringResource(Res.string.profile_insights_stat_upcoming)
    val core by produceState(
        ProfileInsightsSnapshotCache.coreFor(activeProfileIndex),
        activeProfileIndex,
        watchProgressState,
        watchedState,
        fullyWatchedSeriesKeys,
        libraryState,
        todayIsoDate,
        continueTitle,
        watchedTitle,
        completedTitle,
        ongoingTitle,
        libraryTitle,
        upcomingTitle,
    ) {
        val profileIndex = activeProfileIndex
        val progressSnapshot = watchProgressState
        val watchedSnapshot = watchedState
        val fullyWatchedSnapshot = fullyWatchedSeriesKeys
        val librarySnapshot = libraryState
        val computed = withContext(Dispatchers.Default) {
            try {
                buildProfileInsightsCore(
                    profileIndex = profileIndex,
                    watchProgressState = progressSnapshot,
                    watchedState = watchedSnapshot,
                    fullyWatchedSeriesKeys = fullyWatchedSnapshot,
                    libraryState = librarySnapshot,
                    todayIsoDate = todayIsoDate,
                    continueTitle = continueTitle,
                    watchedTitle = watchedTitle,
                    completedTitle = completedTitle,
                    ongoingTitle = ongoingTitle,
                    libraryTitle = libraryTitle,
                    upcomingTitle = upcomingTitle,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                profileInsightsLog.e(error) { "Failed to build profile insights profile=$profileIndex" }
                null
            }
        }
        if (computed != null) {
            ProfileInsightsSnapshotCache.rememberCore(computed)
            value = computed
        }
    }
    val activeCore = core?.takeIf { snapshot -> snapshot.profileIndex == activeProfileIndex }
    val titleFacts by ProfileTitleFactsStore.facts.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        ProfileTitleFactsStore.ensureLoaded()
    }
    val resolvedStats by produceState(
        ProfileInsightsSnapshotCache.statsFor(activeCore),
        activeCore,
        titleFacts,
    ) {
        val source = activeCore
        if (source == null) {
            value = null
            return@produceState
        }
        val factsSnapshot = titleFacts
        val computed = withContext(Dispatchers.Default) { source.resolveStats(factsSnapshot) }
        ProfileInsightsSnapshotCache.rememberStats(source, computed)
        value = computed
    }
    val stats = remember(resolvedStats, upcomingEpisodes) {
        (resolvedStats ?: emptyProfileInsightsStats()).copy(upcomingCount = upcomingEpisodes.size)
    }
    val hydrationRequest = activeCore?.hydrationRequest
    LaunchedEffect(hydrationRequest) {
        hydrationRequest?.let { request -> ProfileTitleFactsStore.hydrate(request) }
    }
    val refreshContext = ProfileInsightsRefreshContext(
        profileId = activeProfileIndex,
        libraryItems = libraryState.items,
        hydrationRequest = hydrationRequest,
    )
    SideEffect {
        ProfileInsightsRefresher.bind(refreshContext)
    }
    DisposableEffect(Unit) {
        onDispose { ProfileInsightsRefresher.unbind() }
    }
    val isRefreshing by ProfileInsightsRefresher.isRefreshing.collectAsStateWithLifecycle()
    val refreshedAtByProfile by ProfileTitleFactsStore.refreshedAtByProfile.collectAsStateWithLifecycle()
    val failedFactKeys by ProfileTitleFactsStore.failedKeys.collectAsStateWithLifecycle()
    val failedGenreTitleCount = remember(hydrationRequest, failedFactKeys, titleFacts) {
        hydrationRequest?.genreTargetKeys?.count { key ->
            key in failedFactKeys && titleFacts[key]?.genres.isNullOrEmpty()
        } ?: 0
    }
    val factsProgress = remember(hydrationRequest, failedFactKeys, titleFacts) {
        hydrationRequest?.let { request -> profileTitleFactsProgress(request, titleFacts, failedFactKeys) }
    }
    val emptyCollections = remember(
        continueTitle,
        watchedTitle,
        completedTitle,
        ongoingTitle,
        libraryTitle,
        upcomingTitle,
    ) {
        emptyProfileInsightCollections(
            continueTitle = continueTitle,
            watchedTitle = watchedTitle,
            completedTitle = completedTitle,
            ongoingTitle = ongoingTitle,
            libraryTitle = libraryTitle,
            upcomingTitle = upcomingTitle,
        )
    }
    val baseInsightCollections = activeCore?.collections ?: emptyCollections
    val insightCollections = remember(baseInsightCollections, upcomingEpisodes, upcomingTitle) {
        baseInsightCollections + (
            ProfileInsightCollectionKind.Upcoming to ProfileInsightCollection(
                title = upcomingTitle,
                subtitle = "",
                items = upcomingEpisodes.map(LibraryUpcomingEpisode::toProfileInsightPosterItem),
            )
        )
    }
    var selectedInsightCollection by remember { mutableStateOf<ProfileInsightCollection?>(null) }
    LaunchedEffect(activeProfileIndex) {
        selectedInsightCollection = null
    }
    val isCollectionAvailable: (ProfileInsightCollectionKind) -> Boolean = { kind ->
        insightCollections[kind]?.items?.isNotEmpty() == true
    }
    val onCollectionClick: (ProfileInsightCollectionKind) -> Unit = { kind ->
        selectedInsightCollection = insightCollections[kind]
            ?.takeIf { collection -> collection.items.isNotEmpty() }
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        ProfileInsightsHero(
            profile = activeProfile,
            avatarItem = avatarItem,
            profileName = profileName,
            isTablet = isTablet,
            stats = stats,
            isCollectionAvailable = isCollectionAvailable,
            onCollectionClick = onCollectionClick,
            onEditProfile = onEditProfile,
            onSwitchProfile = onSwitchProfile,
            onBack = onBack.takeUnless { isTablet },
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = if (isTablet) 18.dp else 14.dp),
            verticalArrangement = Arrangement.spacedBy(if (isTablet) 18.dp else 14.dp),
        ) {
            ProfileInsightsRefreshStatusRow(
                refreshedAtEpochMs = refreshedAtByProfile[activeProfileIndex],
                failedGenreTitleCount = failedGenreTitleCount,
                factsProgress = factsProgress,
                isRefreshing = isRefreshing,
                onRefresh = ProfileInsightsRefresher::refresh,
            )
            ProfileWatchTimeRow(stats = stats)
            SettingsSection(
                title = stringResource(Res.string.profile_insights_section_taste),
                isTablet = isTablet,
            ) {
                ProfileTasteCard(isTablet = isTablet, stats = stats, factsProgress = factsProgress)
            }
        }
    }

    selectedInsightCollection?.let { collection ->
        ProfileInsightCollectionSheet(
            collection = collection,
            isTablet = isTablet,
            onDismiss = { selectedInsightCollection = null },
            onPosterClick = onPosterClick,
        )
    }

}

@Composable
private fun ProfileHeaderIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.14f))
            .border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun ProfileInsightsHero(
    profile: NuvioProfile?,
    avatarItem: AvatarCatalogItem?,
    profileName: String,
    isTablet: Boolean,
    stats: ProfileInsightsStats,
    isCollectionAvailable: (ProfileInsightCollectionKind) -> Boolean,
    onCollectionClick: (ProfileInsightCollectionKind) -> Unit,
    onEditProfile: (() -> Unit)?,
    onSwitchProfile: (() -> Unit)?,
    onBack: (() -> Unit)? = null,
) {
    if (isTablet) {
        ProfileInsightsHeroBounded(
            profile = profile,
            avatarItem = avatarItem,
            profileName = profileName,
            stats = stats,
            isCollectionAvailable = isCollectionAvailable,
            onCollectionClick = onCollectionClick,
            onEditProfile = onEditProfile,
            onSwitchProfile = onSwitchProfile,
        )
    } else {
        ProfileInsightsHeroCinematic(
            profile = profile,
            avatarItem = avatarItem,
            profileName = profileName,
            stats = stats,
            isCollectionAvailable = isCollectionAvailable,
            onCollectionClick = onCollectionClick,
            onEditProfile = onEditProfile,
            onSwitchProfile = onSwitchProfile,
            onBack = onBack,
        )
    }
}

@Composable
private fun ProfileInsightsHeroBounded(
    profile: NuvioProfile?,
    avatarItem: AvatarCatalogItem?,
    profileName: String,
    stats: ProfileInsightsStats,
    isCollectionAvailable: (ProfileInsightCollectionKind) -> Boolean,
    onCollectionClick: (ProfileInsightCollectionKind) -> Unit,
    onEditProfile: (() -> Unit)?,
    onSwitchProfile: (() -> Unit)?,
) {
    val tokens = MaterialTheme.nuvio
    val accent = profile?.avatarColorHex?.let(::parseHexColor) ?: tokens.colors.accent
    val avatarImageUrl = remember(profile, avatarItem) {
        profile?.let { profileAvatarImageUrl(it, avatarItem) }
    }
    val shape = RoundedCornerShape(34.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(320.dp)
            .clip(shape)
            .border(1.dp, Color.White.copy(alpha = 0.12f), shape),
    ) {
        if (profile != null) {
            ProfileBackgroundBackdrop(profile = profile, modifier = Modifier.matchParentSize())
        } else {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF0E1727), accent.copy(alpha = 0.42f), tokens.colors.surface),
                        ),
                    ),
            )
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.20f), Color.Black.copy(alpha = 0.88f)),
                        startY = 0f,
                        endY = Float.POSITIVE_INFINITY,
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomStart)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ProfileHeroAvatar(
                profileName = profileName,
                avatarImageUrl = avatarImageUrl,
                avatarColor = accent,
                avatarBackgroundColor = avatarItem?.bgColor?.let(::parseHexColor) ?: accent,
                isTablet = true,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onSwitchProfile != null) {
                    ProfileHeaderIconButton(
                        icon = Icons.Rounded.People,
                        contentDescription = stringResource(Res.string.profile_insights_switch_profile),
                        onClick = onSwitchProfile,
                    )
                }
                Text(
                    text = stringResource(Res.string.profile_insights_title, profileName),
                    style = MaterialTheme.typography.displaySmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (onEditProfile != null) {
                    ProfileHeaderIconButton(
                        icon = Icons.Rounded.Edit,
                        contentDescription = stringResource(Res.string.profile_insights_edit_profile),
                        onClick = onEditProfile,
                    )
                }
            }
            ProfileMetricPillRow(
                stats = stats,
                isCollectionAvailable = isCollectionAvailable,
                onCollectionClick = onCollectionClick,
                centerWhenFits = true,
            )
        }
    }
}

@Composable
private fun ProfileInsightsHeroCinematic(
    profile: NuvioProfile?,
    avatarItem: AvatarCatalogItem?,
    profileName: String,
    stats: ProfileInsightsStats,
    isCollectionAvailable: (ProfileInsightCollectionKind) -> Boolean,
    onCollectionClick: (ProfileInsightCollectionKind) -> Unit,
    onEditProfile: (() -> Unit)?,
    onSwitchProfile: (() -> Unit)?,
    onBack: (() -> Unit)? = null,
) {
    val tokens = MaterialTheme.nuvio
    val accent = profile?.avatarColorHex?.let(::parseHexColor) ?: tokens.colors.accent
    val avatarImageUrl = remember(profile, avatarItem) {
        profile?.let { profileAvatarImageUrl(it, avatarItem) }
    }

    val bleedsUnderNativeNavBar = LocalUseNativeNavigation.current
    val floatingChromeTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f),
    ) {
        BoxWithConstraints(modifier = Modifier.matchParentSize()) {
            val leftInset = tokens.spacing.screenHorizontal + 40.dp
            val rightInset = tokens.spacing.screenHorizontal + 160.dp
            val bleedWidth = maxWidth + leftInset + rightInset
            val topExtension = if (bleedsUnderNativeNavBar) 56.dp else 0.dp
            val extendedHeight = maxHeight + topExtension

            Box(
                modifier = Modifier
                    .requiredWidth(bleedWidth)
                    .requiredHeight(extendedHeight)
                    .offset(x = -leftInset, y = -topExtension),
            ) {
                if (profile != null) {
                    ProfileBackgroundBackdrop(
                        profile = profile,
                        modifier = Modifier.matchParentSize(),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        Color(0xFF0E1727),
                                        accent.copy(alpha = 0.42f),
                                        tokens.colors.surface,
                                    ),
                                ),
                            ),
                    )
                }
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.20f),
                                    Color.Black.copy(alpha = 0.88f),
                                ),
                                startY = 0f,
                                endY = Float.POSITIVE_INFINITY,
                            ),
                        ),
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 18.dp)
                    .padding(bottom = 140.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ProfileHeroAvatar(
                    profileName = profileName,
                    avatarImageUrl = avatarImageUrl,
                    avatarColor = accent,
                    avatarBackgroundColor = avatarItem?.bgColor?.let(::parseHexColor) ?: accent,
                    isTablet = false,
                )
                Text(
                    text = stringResource(Res.string.profile_insights_title, profileName),
                    style = MaterialTheme.typography.headlineLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            ProfileMetricPillRow(
                stats = stats,
                isCollectionAvailable = isCollectionAvailable,
                onCollectionClick = onCollectionClick,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(bottom = 18.dp),
                edgeInset = 18.dp,
            )

            if (onBack != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = floatingChromeTop, start = 18.dp),
                ) {
                    ProfileHeaderIconButton(
                        icon = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(Res.string.action_back),
                        onClick = onBack,
                    )
                }
                Text(
                    text = stringResource(Res.string.compose_settings_page_profile),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = floatingChromeTop + 9.dp),
                )
            }

            if (onEditProfile != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 140.dp, end = 18.dp),
                ) {
                    ProfileHeaderIconButton(
                        icon = Icons.Rounded.Edit,
                        contentDescription = stringResource(Res.string.profile_insights_edit_profile),
                        onClick = onEditProfile,
                    )
                }
            }

            if (onSwitchProfile != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(bottom = 140.dp, start = 18.dp),
                ) {
                    ProfileHeaderIconButton(
                        icon = Icons.Rounded.People,
                        contentDescription = stringResource(Res.string.profile_insights_switch_profile),
                        onClick = onSwitchProfile,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileHeroAvatar(
    profileName: String,
    avatarImageUrl: String?,
    avatarColor: Color,
    avatarBackgroundColor: Color,
    isTablet: Boolean,
) {
    val size = if (isTablet) 92.dp else 78.dp
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(
                if (avatarImageUrl.isNullOrBlank()) {
                    avatarColor.copy(alpha = 0.18f)
                } else {
                    avatarBackgroundColor
                },
            )
            .border(1.5.dp, Color.White.copy(alpha = 0.28f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (!avatarImageUrl.isNullOrBlank()) {
            NuvioAsyncImage(
                imageUrl = avatarImageUrl,
                contentDescription = profileName,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
                animateIfPossible = true,
            )
        } else {
            Text(
                text = profileName.take(1).uppercase(),
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun ProfileMetricPillRow(
    stats: ProfileInsightsStats,
    isCollectionAvailable: (ProfileInsightCollectionKind) -> Boolean,
    onCollectionClick: (ProfileInsightCollectionKind) -> Unit,
    modifier: Modifier = Modifier,
    edgeInset: Dp = 18.dp,
    centerWhenFits: Boolean = false,
) {
    val pills = listOf(
        ProfileMetricPillSpec(
            icon = Icons.Rounded.PlayArrow,
            value = stats.continueCount.toString(),
            label = stringResource(Res.string.profile_insights_hero_continue),
            collectionKind = ProfileInsightCollectionKind.Continue,
        ),
        ProfileMetricPillSpec(
            icon = Icons.Rounded.CollectionsBookmark,
            value = stats.libraryCount.toString(),
            label = stringResource(Res.string.profile_insights_hero_library),
            collectionKind = ProfileInsightCollectionKind.Library,
        ),
        ProfileMetricPillSpec(
            icon = Icons.Rounded.CalendarMonth,
            value = stats.upcomingCount.toString(),
            label = stringResource(Res.string.profile_insights_hero_upcoming),
            collectionKind = ProfileInsightCollectionKind.Upcoming,
        ),
        ProfileMetricPillSpec(
            icon = Icons.Rounded.Movie,
            value = stats.watchedMovieCount.toString(),
            label = stringResource(Res.string.profile_insights_hero_watched),
            collectionKind = ProfileInsightCollectionKind.Watched,
        ),
        ProfileMetricPillSpec(
            icon = Icons.Rounded.Favorite,
            value = stats.completedCount.toString(),
            label = stringResource(Res.string.profile_insights_stat_completed),
            collectionKind = ProfileInsightCollectionKind.Completed,
        ),
        ProfileMetricPillSpec(
            icon = Icons.Rounded.Sync,
            value = stats.ongoingSeriesCount.toString(),
            label = stringResource(Res.string.profile_insights_stat_ongoing),
            collectionKind = ProfileInsightCollectionKind.Ongoing,
        ),
        ProfileMetricPillSpec(
            icon = Icons.Rounded.Tv,
            value = stats.episodesWatchedCount.toString(),
            label = stringResource(Res.string.profile_insights_hero_episodes),
            collectionKind = null,
        ),
    )

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val pillCount = pills.size
        val contentWidth = ProfileMetricPillWidth * pillCount + ProfileMetricPillGap * (pillCount - 1)
        val fits = contentWidth + edgeInset * 2 <= maxWidth
        val pillContent: @Composable () -> Unit = {
            pills.forEach { pill ->
                ProfileMetricPill(
                    spec = pill,
                    onClick = pill.collectionKind
                        ?.takeIf(isCollectionAvailable)
                        ?.let { kind -> { onCollectionClick(kind) } },
                )
            }
        }
        if (fits && centerWhenFits) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ProfileMetricPillGap, Alignment.CenterHorizontally),
            ) { pillContent() }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .profileHorizontalEdgeFade(edgeInset)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(ProfileMetricPillGap),
            ) {
                Spacer(modifier = Modifier.width((edgeInset - ProfileMetricPillGap).coerceAtLeast(0.dp)))
                pillContent()
                Spacer(modifier = Modifier.width((edgeInset - ProfileMetricPillGap).coerceAtLeast(0.dp)))
            }
        }
    }
}

private val ProfileMetricPillWidth = 96.dp
private val ProfileMetricPillGap = 10.dp

private fun Modifier.profileHorizontalEdgeFade(fadeWidth: Dp): Modifier =
    if (fadeWidth <= 0.dp) this else this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val fade = fadeWidth.toPx().coerceAtMost(size.width / 2f)
            drawRect(
                brush = Brush.horizontalGradient(
                    0f to Color.Transparent,
                    (fade / size.width) to Color.Black,
                    (1f - fade / size.width) to Color.Black,
                    1f to Color.Transparent,
                ),
                blendMode = BlendMode.DstIn,
            )
        }

private data class ProfileMetricPillSpec(
    val icon: ImageVector,
    val value: String,
    val label: String,
    val collectionKind: ProfileInsightCollectionKind?,
)

@Composable
private fun ProfileMetricPill(
    spec: ProfileMetricPillSpec,
    onClick: (() -> Unit)?,
) {
    Column(
        modifier = Modifier
            .width(ProfileMetricPillWidth)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.14f))
            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(18.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = spec.icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = Color.White,
        )
        Text(
            text = spec.value,
            autoSize = TextAutoSize.StepBased(
                minFontSize = 14.sp,
                maxFontSize = MaterialTheme.typography.titleLarge.fontSize,
            ),
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
        Text(
            text = spec.label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.72f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ProfileWatchTimeRow(stats: ProfileInsightsStats) {
    val tokens = MaterialTheme.nuvio
    val surface = LiquidGlassSurface.SETTINGS
    val glassEnabled = isLiquidGlassSurfaceEffectivelyEnabled(
        surface = surface,
        platformSupported = isLiquidGlassSupported(),
    )
    val shape = liquidGlassShape(surface, tokens.shapes.card)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (glassEnabled) {
                    Modifier.liquidGlassForSurface(
                        surface = surface,
                        shape = shape,
                        tint = tokens.colors.surface.copy(alpha = 0.18f),
                        fallback = tokens.colors.surface,
                        blurRadius = 14.dp,
                        refractionHeight = 8.dp,
                        refractionAmount = 12.dp,
                    )
                } else {
                    Modifier
                },
            ),
        color = if (glassEnabled) Color.Transparent else tokens.colors.surface,
        shape = shape,
        border = BorderStroke(1.dp, tokens.colors.borderSubtle),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(Res.string.profile_insights_stat_time),
                style = MaterialTheme.typography.titleMedium,
                color = tokens.colors.textPrimary,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            )
            Text(
                text = profileInsightDurationLabel(stats.trackedDurationMs),
                style = MaterialTheme.typography.displayMedium,
                color = tokens.colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            Text(
                text = stringResource(Res.string.profile_insights_stat_time_caption),
                style = MaterialTheme.typography.bodySmall,
                color = tokens.colors.textMuted,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileInsightCollectionSheet(
    collection: ProfileInsightCollection,
    isTablet: Boolean,
    onDismiss: () -> Unit,
    onPosterClick: ((MetaPreview) -> Unit)?,
) {
    val tokens = MaterialTheme.nuvio
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    NuvioModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (isTablet) 24.dp else 18.dp)
                .padding(bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = collection.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = tokens.colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(Res.string.profile_insights_collection_count, collection.items.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(if (isTablet) 132.dp else 104.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(
                    items = collection.items,
                    key = { item -> item.id },
                ) { item ->
                    ProfileInsightPosterTile(
                        item = item,
                        onClick = onPosterClick?.let { callback ->
                            { preview ->
                                onDismiss()
                                callback(preview)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileInsightPosterTile(
    item: ProfileInsightPosterItem,
    onClick: ((MetaPreview) -> Unit)?,
) {
    val tokens = MaterialTheme.nuvio
    val unknownLabel = stringResource(Res.string.generic_unknown)
    val initialImageUrl = remember(item.id, item.imageUrl) {
        item.imageUrl?.trim()?.takeIf { it.isNotBlank() }
    }
    val initialReleaseInfo = remember(item.id, item.releaseInfo) {
        item.releaseInfo?.trim()?.takeIf { it.isNotBlank() }
    }
    val cachedMeta = remember(item.id, item.lookupType, item.lookupId) {
        profileCachedMeta(item.lookupType, item.lookupId)
    }
    val cachedImageUrl = remember(item.id, initialImageUrl, cachedMeta) {
        initialImageUrl ?: cachedMeta.profileMetaArtworkUrl()
    }
    val cachedReleaseInfo = remember(item.id, initialReleaseInfo, cachedMeta) {
        initialReleaseInfo ?: cachedMeta?.releaseInfo?.trim()?.takeIf { it.isNotBlank() }
    }
    var resolvedImageUrl by remember(item.id, cachedImageUrl) {
        mutableStateOf(cachedImageUrl)
    }
    var resolvedReleaseInfo by remember(item.id, cachedReleaseInfo) {
        mutableStateOf(cachedReleaseInfo)
    }

    LaunchedEffect(item.id, item.lookupType, item.lookupId, cachedImageUrl, cachedReleaseInfo) {
        resolvedImageUrl = cachedImageUrl
        resolvedReleaseInfo = cachedReleaseInfo
        if (cachedImageUrl != null && cachedReleaseInfo != null) return@LaunchedEffect
        val (artwork, releaseInfo) = profileFetchPosterMetadata(item.lookupType, item.lookupId)
        resolvedImageUrl = cachedImageUrl ?: artwork
        resolvedReleaseInfo = cachedReleaseInfo ?: releaseInfo
    }

    val cleanType = item.lookupType?.trim()?.takeIf { it.isNotBlank() }
    val cleanId = item.lookupId?.trim()?.takeIf { it.isNotBlank() }
    val displayReleaseInfo = resolveReleaseInfoForDisplay(
        stored = initialReleaseInfo,
        hydrated = resolvedReleaseInfo,
        fallback = unknownLabel,
    )
    val detailLine = listOfNotNull(
        displayReleaseInfo,
        item.secondaryText?.trim()?.takeIf { it.isNotBlank() },
    ).joinToString(" • ")

    Column(
        modifier = if (onClick != null && cleanType != null && cleanId != null) {
            Modifier.clickable {
                onClick(
                    MetaPreview(
                        id = cleanId,
                        type = cleanType,
                        name = item.title,
                        poster = resolvedImageUrl,
                        releaseInfo = resolvedReleaseInfo,
                    ),
                )
            }
        } else {
            Modifier
        },
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(15.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, tokens.colors.borderSubtle, RoundedCornerShape(15.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (resolvedImageUrl != null) {
                NuvioAsyncImage(
                    imageUrl = resolvedImageUrl.orEmpty(),
                    contentDescription = item.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    animateIfPossible = true,
                )
            } else {
                Text(
                    text = item.title.take(1).uppercase(),
                    style = MaterialTheme.typography.headlineMedium,
                    color = tokens.colors.textMuted,
                    fontWeight = FontWeight.Black,
                )
            }
        }
        Text(
            text = item.title,
            style = MaterialTheme.typography.labelLarge,
            color = tokens.colors.textPrimary,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = detailLine,
            style = MaterialTheme.typography.labelSmall,
            color = tokens.colors.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ProfileTasteCard(
    isTablet: Boolean,
    stats: ProfileInsightsStats,
    factsProgress: ProfileTitleFactsProgress? = null,
) {
    val tokens = MaterialTheme.nuvio
    val fallbackType = when (stats.topType) {
        "movie" -> stringResource(Res.string.profile_insights_type_movie)
        "series" -> stringResource(Res.string.profile_insights_type_series)
        null -> null
        else -> stats.topType.fallbackDisplayLabel()
    }
    val topSignal = stats.topGenre ?: fallbackType ?: stringResource(Res.string.profile_insights_taste_empty)

    SettingsSurfaceCard(isTablet = isTablet) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier
                        .size(52.dp)
                        .background(
                            brush = MaterialTheme.themePalette.accentBrush(alpha = tokens.opacity.pressed),
                            shape = RoundedCornerShape(18.dp),
                        ),
                    color = Color.Transparent,
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.gradientMask(MaterialTheme.themePalette.accentBrush()),
                            tint = tokens.colors.accent,
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.profile_insights_taste_dna_title),
                        style = MaterialTheme.typography.labelMedium,
                        color = tokens.colors.textMuted,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = topSignal,
                        style = MaterialTheme.typography.titleLarge,
                        color = tokens.colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(Res.string.profile_insights_taste_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = tokens.colors.textMuted,
                    )
                }
            }
            if (stats.tasteSegments.isNotEmpty()) {
                ProfileTasteGenreDonut(segments = stats.tasteSegments)
                if (factsProgress != null && factsProgress.genresPending) {
                    Text(
                        text = stringResource(
                            Res.string.profile_insights_genre_partial,
                            factsProgress.genreResolved,
                            factsProgress.genreTotal,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.nuvio.colors.textMuted,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }
            }
            ProfileTasteBalanceBar(stats = stats)
            if (stats.dnaChips.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    stats.dnaChips.chunked(2).forEach { rowChips ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            rowChips.forEach { chip ->
                                ProfileTasteDnaChip(
                                    text = chip.localizedLabel(),
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            repeat(2 - rowChips.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val PROFILE_TASTE_DONUT_MAX_GENRES = 7

private val ProfileTasteDonutPalette = listOf(
    Color(0xFFFF6384), // rose
    Color(0xFFFF9F40), // orange
    Color(0xFFFFCD56), // yellow
    Color(0xFF4BC0C0), // teal
    Color(0xFF36A2EB), // blue
    Color(0xFF9966FF), // violet
    Color(0xFFE879F9), // pink
)
private val ProfileTasteDonutOthersColor = Color(0xFF8A8A96)

private class ProfileTasteDonutSlice(
    val label: String,
    val share: Float,
    val color: Color,
    val isOthers: Boolean = false,
)

private fun Float.profilePercentLabel(): String = "${(this * 100f).roundToInt().coerceIn(1, 100)}%"

@Composable
private fun ProfileTasteGenreDonut(segments: List<ProfileTasteSegment>) {
    val othersLabel = stringResource(Res.string.profile_insights_genre_others)
    val topSegments = segments.take(PROFILE_TASTE_DONUT_MAX_GENRES)
    val otherSegments = segments.drop(PROFILE_TASTE_DONUT_MAX_GENRES)
    val slices = remember(segments, othersLabel) {
        buildList {
            topSegments.forEachIndexed { index, segment ->
                add(
                    ProfileTasteDonutSlice(
                        label = segment.label,
                        share = segment.share,
                        color = ProfileTasteDonutPalette[index % ProfileTasteDonutPalette.size],
                    ),
                )
            }
            val othersShare = otherSegments.sumOf { it.share.toDouble() }.toFloat()
            if (othersShare > 0f) {
                add(
                    ProfileTasteDonutSlice(
                        label = othersLabel,
                        share = othersShare,
                        color = ProfileTasteDonutOthersColor,
                        isOthers = true,
                    ),
                )
            }
        }
    }
    var othersExpanded by remember { mutableStateOf(false) }
    val chartDescription = stringResource(
        Res.string.profile_insights_genre_chart_description,
        slices.joinToString(", ") { "${it.label} ${it.share.profilePercentLabel()}" },
    )

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val wide = maxWidth >= 560.dp
        val donutSize = if (wide) 220.dp else 188.dp
        val donut: @Composable () -> Unit = {
            ProfileTasteDonutCanvas(
                slices = slices,
                topLabel = topSegments.firstOrNull()?.label,
                topShare = topSegments.firstOrNull()?.share,
                modifier = Modifier
                    .size(donutSize)
                    .semantics { contentDescription = chartDescription },
            )
        }
        val legend: @Composable (Modifier) -> Unit = { modifier ->
            ProfileTasteDonutLegend(
                slices = slices,
                otherSegments = otherSegments,
                othersExpanded = othersExpanded,
                onToggleOthers = { othersExpanded = !othersExpanded },
                columns = 2,
                modifier = modifier,
            )
        }
        if (wide) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                donut()
                legend(Modifier.weight(1f))
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                donut()
                legend(Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun ProfileTasteDonutCanvas(
    slices: List<ProfileTasteDonutSlice>,
    topLabel: String?,
    topShare: Float?,
    modifier: Modifier = Modifier,
) {
    val tokens = MaterialTheme.nuvio
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = size.minDimension * 0.18f
            val inset = strokeWidth / 2f
            val arcSize = androidx.compose.ui.geometry.Size(
                size.minDimension - strokeWidth,
                size.minDimension - strokeWidth,
            )
            val total = slices.sumOf { it.share.toDouble() }.toFloat().takeIf { it > 0f } ?: return@Canvas
            val gapDegrees = if (slices.size > 1) 1.6f else 0f
            var startAngle = -90f
            slices.forEach { slice ->
                val sweep = slice.share / total * 360f
                val drawnSweep = (sweep - gapDegrees).coerceAtLeast(0.5f)
                drawArc(
                    color = slice.color,
                    startAngle = startAngle + gapDegrees / 2f,
                    sweepAngle = drawnSweep,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
                )
                startAngle += sweep
            }
        }
        if (topLabel != null && topShare != null) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 40.dp),
            ) {
                Text(
                    text = topShare.profilePercentLabel(),
                    style = MaterialTheme.typography.titleLarge,
                    color = tokens.colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Text(
                    text = topLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = tokens.colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun ProfileTasteDonutLegend(
    slices: List<ProfileTasteDonutSlice>,
    otherSegments: List<ProfileTasteSegment>,
    othersExpanded: Boolean,
    onToggleOthers: () -> Unit,
    columns: Int,
    modifier: Modifier = Modifier,
) {
    val tokens = MaterialTheme.nuvio
    Column(
        modifier = modifier.animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        slices.chunked(columns).forEach { rowSlices ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                rowSlices.forEach { slice ->
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .then(
                                if (slice.isOthers) Modifier.clickable(onClick = onToggleOthers) else Modifier,
                            )
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(slice.color),
                        )
                        Text(
                            text = if (slice.isOthers) {
                                "${slice.label} ${if (othersExpanded) "▴" else "▾"}"
                            } else {
                                slice.label
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = tokens.colors.textPrimary,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = slice.share.profilePercentLabel(),
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.colors.textMuted,
                            maxLines = 1,
                        )
                    }
                }
                repeat(columns - rowSlices.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
        if (othersExpanded && otherSegments.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(tokens.colors.borderSubtle.copy(alpha = 0.35f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                otherSegments.forEach { segment ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = segment.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.colors.textMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = segment.share.profilePercentLabel(),
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.colors.textMuted,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileTasteBalanceBar(stats: ProfileInsightsStats) {
    val tokens = MaterialTheme.nuvio
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.profile_insights_taste_balance_title),
                style = MaterialTheme.typography.labelMedium,
                color = tokens.colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stats.typeBalanceLabel.localizedLabel(),
                style = MaterialTheme.typography.labelSmall,
                color = tokens.colors.textMuted,
                maxLines = 1,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(tokens.colors.borderSubtle),
        ) {
            val movieLeaning = stats.movieShare >= 0.5f
            val accentBrush = MaterialTheme.themePalette.accentBrush()
            val mutedBrush = SolidColor(tokens.colors.textMuted.copy(alpha = 0.42f))
            Box(
                modifier = Modifier
                    .weight(stats.movieShare.coerceIn(0.05f, 0.95f))
                    .fillMaxSize()
                    .background(if (movieLeaning) accentBrush else mutedBrush),
            )
            Box(
                modifier = Modifier
                    .weight((1f - stats.movieShare).coerceIn(0.05f, 0.95f))
                    .fillMaxSize()
                    .background(if (movieLeaning) mutedBrush else accentBrush),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(Res.string.profile_insights_type_movie),
                style = MaterialTheme.typography.labelSmall,
                color = tokens.colors.textMuted,
            )
            Text(
                text = stringResource(Res.string.profile_insights_type_series),
                style = MaterialTheme.typography.labelSmall,
                color = tokens.colors.textMuted,
            )
        }
        stats.movieWatchTimeShare?.let { movieTimeShare ->
            val moviePercent = (movieTimeShare * 100f).roundToInt().coerceIn(0, 100)
            Text(
                text = stringResource(
                    Res.string.profile_insights_type_watch_time_split,
                    moviePercent,
                    100 - moviePercent,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = tokens.colors.textMuted,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ProfileTasteDnaChip(
    text: String,
    modifier: Modifier = Modifier,
) {
    val tokens = MaterialTheme.nuvio
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.themePalette.accentBrush(alpha = tokens.opacity.pressed))
            .border(1.dp, MaterialTheme.themePalette.accentBrush(alpha = 0.22f), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.AutoAwesome,
            contentDescription = null,
            modifier = Modifier
                .size(14.dp)
                .gradientMask(MaterialTheme.themePalette.accentBrush()),
            tint = tokens.colors.accent,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = tokens.colors.textPrimary,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun profileInsightDurationLabel(durationMs: Long): String {
    val totalMinutes = (durationMs / ProfileInsightsMinuteMs).coerceAtLeast(0L)
    if (totalMinutes <= 0L) return stringResource(Res.string.profile_insights_minutes, 0)

    val formatted = if (totalMinutes < 60L) {
        stringResource(Res.string.profile_insights_minutes, totalMinutes.toInt())
    } else {
        val totalHours = (totalMinutes + 30L) / 60L
        val days = totalHours / 24L
        val hours = totalHours % 24L
        when {
            days > 0L && hours > 0L ->
                stringResource(Res.string.profile_insights_days_hours, days.toInt(), hours.toInt())
            days > 0L -> stringResource(Res.string.profile_insights_days, days.toInt())
            else -> stringResource(Res.string.profile_insights_hours, totalHours.toInt())
        }
    }
    return "~$formatted"
}

private class ProfileInsightsCore(
    val profileIndex: Int,
    private val baseStats: ProfileInsightsStats,
    val collections: Map<ProfileInsightCollectionKind, ProfileInsightCollection>,
    private val typeBalance: ProfileTypeBalance,
    private val libraryGenresByKey: Map<String, List<String>>,
    private val progressDurationByKey: Map<String, Long>,
    private val watchedDurationRefs: List<ProfileWatchedDurationRef>,
    val hydrationRequest: ProfileTitleHydrationRequest,
) {
    fun resolveStats(facts: Map<String, ProfileTitleFacts>): ProfileInsightsStats {
        val watchTime = profileWatchTimeSplit(
            progressDurationByKey = progressDurationByKey,
            watchedDurationRefs = watchedDurationRefs,
            facts = facts,
        )
        val segments = buildProfileWatchedTitleGenreSegments(
            titleKeys = typeBalance.titleKeys,
            libraryGenresByKey = libraryGenresByKey,
            facts = facts,
        )
        return baseStats.copy(
            trackedDurationMs = watchTime.totalMs,
            movieWatchTimeShare = watchTime.movieShare,
            tasteSegments = segments,
            topGenre = segments.firstOrNull()?.label,
        )
    }
}

private data class ProfileTitleHydrationRequest(
    val seedKeys: List<String>,
    val genreTitleKeys: List<String>,
    val genreTargetKeys: List<String>,
    val fetchTargetKeys: List<String>,
)

private object ProfileInsightsSnapshotCache {
    private var core: ProfileInsightsCore? = null
    private var statsSource: ProfileInsightsCore? = null
    private var stats: ProfileInsightsStats? = null

    fun coreFor(profileIndex: Int): ProfileInsightsCore? =
        core?.takeIf { snapshot -> snapshot.profileIndex == profileIndex }

    fun rememberCore(snapshot: ProfileInsightsCore) {
        core = snapshot
    }

    fun statsFor(source: ProfileInsightsCore?): ProfileInsightsStats? =
        stats?.takeIf { source != null && statsSource === source }

    fun rememberStats(source: ProfileInsightsCore, resolved: ProfileInsightsStats) {
        statsSource = source
        stats = resolved
    }
}

private fun buildProfileInsightsCore(
    profileIndex: Int,
    watchProgressState: WatchProgressUiState,
    watchedState: WatchedUiState,
    fullyWatchedSeriesKeys: Set<String>,
    libraryState: LibraryUiState,
    todayIsoDate: String,
    continueTitle: String,
    watchedTitle: String,
    completedTitle: String,
    ongoingTitle: String,
    libraryTitle: String,
    upcomingTitle: String,
): ProfileInsightsCore {
    val now = WatchedClock.nowEpochMs()
    val recentCutoff = now - ProfileInsightsRecentWindowMs
    val progressEntries = watchProgressState.entries
        .filter(WatchProgressEntry::isProfileInsightProgressEntry)
        .map(WatchProgressEntry::normalizedCompletion)
        .distinctBy { entry ->
            listOf(
                entry.parentMetaType,
                entry.parentMetaId,
                entry.videoId,
                entry.seasonNumber,
                entry.episodeNumber,
            ).joinToString("|")
        }
    val continueEntries = progressEntries.profileInsightContinueWatchingEntries()
    val libraryItems = libraryState.items.filter(LibraryItem::isProfileInsightContent)
    val watchedItems = watchedState.items.filter(WatchedItem::isProfileInsightContent)
    val watchedBuckets = buildProfileWatchedContentBuckets(
        watchedItems = watchedItems,
        fullyWatchedSeriesKeys = fullyWatchedSeriesKeys,
        libraryItems = libraryItems,
        progressEntries = progressEntries,
    )
    val completedContentItems = watchedBuckets.completedItems
    val watchedMovieItems = watchedBuckets.watchedMovieItems
    val ongoingSeriesItems = watchedBuckets.ongoingSeriesItems
    val normalizedTypes = libraryItems.map { item -> item.type } +
        progressEntries.map { entry -> entry.parentMetaType } +
        watchedItems.map { item -> item.type }
    val typeBalance = buildProfileTypeBalance(
        watchedItems = watchedItems,
        progressEntries = progressEntries,
        fullyWatchedSeriesKeys = fullyWatchedSeriesKeys,
    )
    val movieSeriesTotal = typeBalance.titleTotal
    val movieShare = typeBalance.movieShare
    val libraryGenresByKey = profileLibraryGenresByKey(libraryItems)
    val lastActivityByTitleKey = profileLastActivityByTitleKey(watchedItems, progressEntries)
    val watchedDurationRefs = profileWatchedDurationRefs(watchedItems)
    val trackedTitleKeys = (typeBalance.titleKeys + watchedDurationRefs.map { ref -> ref.titleKey }).distinct()
    val recentActivityCount = profileRecentActivityCount(
        watchedItems = watchedItems,
        progressEntries = progressEntries,
        recentCutoff = recentCutoff,
    )
    val upcomingLibraryCount = libraryItems.count { item ->
        item.profileReleaseIsoDate()?.let { releaseDate -> releaseDate >= todayIsoDate } == true
    }

    val baseStats = ProfileInsightsStats(
        continueCount = continueEntries.size,
        watchedMovieCount = watchedMovieItems.size,
        completedCount = completedContentItems.size,
        episodesWatchedCount = watchedItems.profileWatchedEpisodeCount(),
        ongoingSeriesCount = ongoingSeriesItems.size,
        libraryCount = libraryItems.size,
        // Resolved against title facts in ProfileInsightsCore.resolveStats().
        trackedDurationMs = 0L,
        recentActivityCount = recentActivityCount,
        upcomingCount = upcomingLibraryCount,
        topGenre = null,
        topType = normalizedTypes
            .mapNotNull(String::profileNormalizedType)
            .profileMostCommonValue(),
        tasteSegments = emptyList(),
        movieShare = movieShare,
        typeBalanceLabel = when {
            movieSeriesTotal == 0 -> ProfileTasteBalanceLabel.Learning
            movieShare >= 0.62f -> ProfileTasteBalanceLabel.MovieLeaning
            movieShare <= 0.38f -> ProfileTasteBalanceLabel.SeriesLeaning
            else -> ProfileTasteBalanceLabel.Balanced
        },
        dnaChips = buildProfileTasteDnaChips(
            libraryCount = libraryItems.size,
            continueCount = continueEntries.size,
            completedCount = watchedMovieItems.size + completedContentItems.size,
            recentActivityCount = recentActivityCount,
            upcomingCount = upcomingLibraryCount,
            movieShare = movieShare,
            movieSeriesTotal = movieSeriesTotal,
        ),
    )

    return ProfileInsightsCore(
        profileIndex = profileIndex,
        baseStats = baseStats,
        collections = buildProfileInsightCollections(
            watchProgressState = watchProgressState,
            libraryState = libraryState,
            watchedBuckets = watchedBuckets,
            continueTitle = continueTitle,
            watchedTitle = watchedTitle,
            completedTitle = completedTitle,
            ongoingTitle = ongoingTitle,
            libraryTitle = libraryTitle,
            upcomingTitle = upcomingTitle,
        ),
        typeBalance = typeBalance,
        libraryGenresByKey = libraryGenresByKey,
        progressDurationByKey = profileProgressDurationByActivityKey(progressEntries),
        watchedDurationRefs = watchedDurationRefs,
        hydrationRequest = ProfileTitleHydrationRequest(
            seedKeys = trackedTitleKeys,
            genreTitleKeys = typeBalance.titleKeys,
            genreTargetKeys = typeBalance.titleKeys
                .filter { key -> key !in libraryGenresByKey }
                .sortedByDescending { key -> lastActivityByTitleKey[key] ?: 0L },
            fetchTargetKeys = trackedTitleKeys.sortedByDescending { key -> lastActivityByTitleKey[key] ?: 0L },
        ),
    )
}

private fun buildProfileInsightCollections(
    watchProgressState: WatchProgressUiState,
    libraryState: LibraryUiState,
    watchedBuckets: ProfileWatchedContentBuckets,
    continueTitle: String,
    watchedTitle: String,
    completedTitle: String,
    ongoingTitle: String,
    libraryTitle: String,
    upcomingTitle: String,
): Map<ProfileInsightCollectionKind, ProfileInsightCollection> {
    val continueItems = watchProgressState.entries
        .profileInsightContinueWatchingEntries()
        .asSequence()
        .map { entry ->
            ProfileInsightPosterItem(
                id = "continue:${entry.parentMetaId}:${entry.videoId}",
                title = entry.title.trim().takeIf { it.isNotBlank() } ?: entry.parentMetaId,
                secondaryText = entry.profileEpisodeLine(),
                imageUrl = entry.poster ?: entry.episodeThumbnail ?: entry.background,
                lookupType = entry.parentMetaType,
                lookupId = entry.parentMetaId,
            )
        }
        .toList()

    val watchedMovieItems = watchedBuckets.watchedMovieItems
        .asSequence()
        .map { item ->
            ProfileInsightPosterItem(
                id = "watched:${item.kind}:${item.id}",
                title = item.title,
                releaseInfo = item.releaseInfo,
                imageUrl = item.imageUrl,
                lookupType = item.kind,
                lookupId = item.id,
            )
        }
        .toList()

    val completedItems = watchedBuckets.completedItems
        .asSequence()
        .map { item ->
            ProfileInsightPosterItem(
                id = "completed:${item.kind}:${item.id}",
                title = item.title,
                releaseInfo = item.releaseInfo,
                imageUrl = item.imageUrl,
                lookupType = item.kind,
                lookupId = item.id,
            )
        }
        .toList()

    val ongoingItems = watchedBuckets.ongoingSeriesItems
        .asSequence()
        .map { item ->
            ProfileInsightPosterItem(
                id = "ongoing:${item.kind}:${item.id}",
                title = item.title,
                releaseInfo = item.releaseInfo,
                imageUrl = item.imageUrl,
                lookupType = item.kind,
                lookupId = item.id,
            )
        }
        .toList()

    val libraryItems = libraryState.items
        .asSequence()
        .filter(LibraryItem::isProfileInsightContent)
        .sortedByDescending { item -> item.savedAtEpochMs }
        .map { item ->
            ProfileInsightPosterItem(
                id = "library:${item.id}:${item.type}",
                title = item.name.trim().takeIf { it.isNotBlank() } ?: item.id,
                releaseInfo = item.releaseInfo?.trim()?.takeIf { it.isNotBlank() },
                imageUrl = item.poster ?: item.banner,
                lookupType = item.type,
                lookupId = item.id,
            )
        }
        .toList()


    return mapOf(
        ProfileInsightCollectionKind.Continue to ProfileInsightCollection(
            title = continueTitle,
            subtitle = "",
            items = continueItems,
        ),
        ProfileInsightCollectionKind.Watched to ProfileInsightCollection(
            title = watchedTitle,
            subtitle = "",
            items = watchedMovieItems,
        ),
        ProfileInsightCollectionKind.Completed to ProfileInsightCollection(
            title = completedTitle,
            subtitle = "",
            items = completedItems,
        ),
        ProfileInsightCollectionKind.Ongoing to ProfileInsightCollection(
            title = ongoingTitle,
            subtitle = "",
            items = ongoingItems,
        ),
        ProfileInsightCollectionKind.Library to ProfileInsightCollection(
            title = libraryTitle,
            subtitle = "",
            items = libraryItems,
        ),
        ProfileInsightCollectionKind.Upcoming to ProfileInsightCollection(
            title = upcomingTitle,
            subtitle = "",
            items = emptyList(),
        ),
    )
}

private fun emptyProfileInsightsStats(): ProfileInsightsStats =
    ProfileInsightsStats(
        continueCount = 0,
        watchedMovieCount = 0,
        completedCount = 0,
        episodesWatchedCount = 0,
        ongoingSeriesCount = 0,
        libraryCount = 0,
        trackedDurationMs = 0L,
        recentActivityCount = 0,
        upcomingCount = 0,
        topGenre = null,
        topType = null,
        tasteSegments = emptyList(),
        movieShare = 0.5f,
        typeBalanceLabel = ProfileTasteBalanceLabel.Learning,
        dnaChips = listOf(ProfileTasteDnaChip.Learning),
    )

private fun emptyProfileInsightCollections(
    continueTitle: String,
    watchedTitle: String,
    completedTitle: String,
    ongoingTitle: String,
    libraryTitle: String,
    upcomingTitle: String,
): Map<ProfileInsightCollectionKind, ProfileInsightCollection> =
    mapOf(
        ProfileInsightCollectionKind.Continue to ProfileInsightCollection(
            title = continueTitle,
            subtitle = "",
            items = emptyList(),
        ),
        ProfileInsightCollectionKind.Watched to ProfileInsightCollection(
            title = watchedTitle,
            subtitle = "",
            items = emptyList(),
        ),
        ProfileInsightCollectionKind.Completed to ProfileInsightCollection(
            title = completedTitle,
            subtitle = "",
            items = emptyList(),
        ),
        ProfileInsightCollectionKind.Ongoing to ProfileInsightCollection(
            title = ongoingTitle,
            subtitle = "",
            items = emptyList(),
        ),
        ProfileInsightCollectionKind.Library to ProfileInsightCollection(
            title = libraryTitle,
            subtitle = "",
            items = emptyList(),
        ),
        ProfileInsightCollectionKind.Upcoming to ProfileInsightCollection(
            title = upcomingTitle,
            subtitle = "",
            items = emptyList(),
        ),
    )

private data class ProfileWatchedContentBuckets(
    val watchedMovieItems: List<ProfileCompletedContentItem>,
    val completedItems: List<ProfileCompletedContentItem>,
    val ongoingSeriesItems: List<ProfileCompletedContentItem>,
)

private fun buildProfileWatchedContentBuckets(
    watchedItems: List<WatchedItem>,
    fullyWatchedSeriesKeys: Set<String>,
    libraryItems: List<LibraryItem>,
    progressEntries: List<WatchProgressEntry> = emptyList(),
): ProfileWatchedContentBuckets {
    val libraryByContentKey = libraryItems
        .mapNotNull { item ->
            val kind = item.type.profileCompletedContentKind() ?: return@mapNotNull null
            if (!item.isProfileInsightContent()) return@mapNotNull null
            "${kind}:${item.id}" to item
        }
        .toMap()
    val progressByContentKey = progressEntries
        .filter(WatchProgressEntry::isProfileInsightProgressEntry)
        .map(WatchProgressEntry::normalizedCompletion)
        .mapNotNull { entry ->
            val kind = entry.parentMetaType.profileCompletedContentKind() ?: return@mapNotNull null
            if (entry.parentMetaId.isBlank()) return@mapNotNull null
            "${kind}:${entry.parentMetaId}" to entry
        }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, entries) -> entries.maxByOrNull(WatchProgressEntry::lastUpdatedEpochMs) }

    val eligibleItems = watchedItems
        .asSequence()
        .filter(WatchedItem::isProfileInsightContent)
        .toList()

    val movieItems = eligibleItems
        .asSequence()
        .filter { item -> item.type.profileCompletedContentKind() == "movie" }
        .filterNot { item -> item.season != null || item.episode != null }
        .groupBy { item -> "movie:${item.id}" }
        .mapNotNull { (key, group) ->
            val item = group.maxByOrNull(WatchedItem::markedAtEpochMs) ?: return@mapNotNull null
            val libraryItem = libraryByContentKey[key]
            val progressItem = progressByContentKey[key]
            ProfileCompletedContentItem(
                id = item.id,
                kind = "movie",
                title = item.name.trim().takeIf { it.isNotBlank() }
                    ?: libraryItem?.name?.trim()?.takeIf { it.isNotBlank() }
                    ?: progressItem?.title?.trim()?.takeIf { it.isNotBlank() }
                    ?: item.id,
                releaseInfo = item.releaseInfo?.trim()?.takeIf { it.isNotBlank() }
                    ?: libraryItem?.releaseInfo?.trim()?.takeIf { it.isNotBlank() },
                imageUrl = item.poster
                    ?: libraryItem?.poster
                    ?: libraryItem?.banner
                    ?: progressItem?.profileArtworkUrl(),
                markedAtEpochMs = item.markedAtEpochMs,
            )
        }

    val completedSeriesItems = mutableListOf<ProfileCompletedContentItem>()
    val ongoingSeriesItems = mutableListOf<ProfileCompletedContentItem>()

    eligibleItems
        .asSequence()
        .filter { item -> item.type.profileCompletedContentKind() == "series" }
        .groupBy { item -> "series:${item.id}" }
        .forEach { (key, group) ->
            val topLevelMarker = group
                .filterNot { item -> item.season != null || item.episode != null }
                .maxByOrNull(WatchedItem::markedAtEpochMs)
            val hasTopLevelSeriesMarker = topLevelMarker != null &&
                !topLevelMarker.type.equals("tv", ignoreCase = true)
            val hasFullyWatchedMarker = hasTopLevelSeriesMarker ||
                group.any { item -> watchedItemKey(item.type, item.id) in fullyWatchedSeriesKeys }
            val libraryItem = libraryByContentKey[key]
            val progressItem = progressByContentKey[key]

            if (hasFullyWatchedMarker) {
                val representative = topLevelMarker ?: group.maxByOrNull(WatchedItem::markedAtEpochMs)
                    ?: return@forEach
                completedSeriesItems += ProfileCompletedContentItem(
                    id = representative.id,
                    kind = "series",
                    title = topLevelMarker?.name?.trim()?.takeIf { it.isNotBlank() }
                        ?: libraryItem?.name?.trim()?.takeIf { it.isNotBlank() }
                        ?: progressItem?.title?.trim()?.takeIf { it.isNotBlank() }
                        ?: representative.name.trim().takeIf { it.isNotBlank() }
                        ?: representative.id,
                    releaseInfo = topLevelMarker?.releaseInfo?.trim()?.takeIf { it.isNotBlank() }
                        ?: libraryItem?.releaseInfo?.trim()?.takeIf { it.isNotBlank() }
                        ?: representative.releaseInfo?.trim()?.takeIf { it.isNotBlank() },
                    imageUrl = topLevelMarker?.poster
                        ?: libraryItem?.poster
                        ?: libraryItem?.banner
                        ?: progressItem?.profileArtworkUrl()
                        ?: representative.poster,
                    markedAtEpochMs = group.maxOf { item -> item.markedAtEpochMs },
                )
            } else {
                val hasEpisodeActivity = group.any { item -> item.season != null && item.episode != null }
                if (!hasEpisodeActivity) return@forEach
                val representative = group.maxByOrNull(WatchedItem::markedAtEpochMs) ?: return@forEach
                ongoingSeriesItems += ProfileCompletedContentItem(
                    id = representative.id,
                    kind = "series",
                    title = libraryItem?.name?.trim()?.takeIf { it.isNotBlank() }
                        ?: progressItem?.title?.trim()?.takeIf { it.isNotBlank() }
                        ?: representative.name.trim().takeIf { it.isNotBlank() }
                        ?: representative.id,
                    releaseInfo = libraryItem?.releaseInfo?.trim()?.takeIf { it.isNotBlank() }
                        ?: representative.releaseInfo?.trim()?.takeIf { it.isNotBlank() },
                    imageUrl = libraryItem?.poster
                        ?: libraryItem?.banner
                        ?: progressItem?.profileArtworkUrl()
                        ?: representative.poster,
                    markedAtEpochMs = group.maxOf { item -> item.markedAtEpochMs },
                )
            }
        }

    return ProfileWatchedContentBuckets(
        watchedMovieItems = movieItems.sortedByDescending(ProfileCompletedContentItem::markedAtEpochMs),
        completedItems = completedSeriesItems.sortedByDescending(ProfileCompletedContentItem::markedAtEpochMs),
        ongoingSeriesItems = ongoingSeriesItems.sortedByDescending(ProfileCompletedContentItem::markedAtEpochMs),
    )
}
private fun List<WatchedItem>.profileWatchedEpisodeCount(): Int =
    asSequence()
        .filter { item -> item.type.profileCompletedContentKind() == "series" }
        .filter { item -> item.season != null && item.episode != null }
        .map { item -> Triple(item.id, item.season, item.episode) }
        .distinct()
        .count()

private fun WatchProgressEntry.isProfileInsightProgressEntry(): Boolean =
    parentMetaType.profileCompletedContentKind() != null &&
        !parentMetaId.isLikelyProfileLiveTvValue() &&
        !title.isLikelyProfileLiveTvValue()

private fun List<WatchProgressEntry>.profileInsightContinueWatchingEntries(): List<WatchProgressEntry> =
    asSequence()
        .filter(WatchProgressEntry::isProfileInsightProgressEntry)
        .map(WatchProgressEntry::normalizedCompletion)
        .filterNot(WatchProgressEntry::isEffectivelyCompleted)
        .filter { entry ->
            entry.lastPositionMs > 0L || (entry.normalizedProgressPercent ?: 0f) > 0f
        }
        .distinctBy { entry ->
            listOf(
                entry.parentMetaType,
                entry.parentMetaId,
                entry.videoId,
                entry.seasonNumber,
                entry.episodeNumber,
            ).joinToString("|")
        }
        .sortedByDescending(WatchProgressEntry::lastUpdatedEpochMs)
        .toList()

private fun WatchProgressEntry.profileEpisodeLine(): String? {
    val episodeCode = if (seasonNumber != null && episodeNumber != null) {
        "S${seasonNumber}E${episodeNumber}"
    } else {
        null
    }
    val cleanTitle = episodeTitle?.trim()?.takeIf { it.isNotBlank() }
    return when {
        episodeCode != null && cleanTitle != null -> "$episodeCode - $cleanTitle"
        episodeCode != null -> episodeCode
        cleanTitle != null -> cleanTitle
        else -> null
    }
}

private fun WatchProgressEntry.profileTrackedDurationMs(): Long {
    val effectiveDurationMs = if (durationMs > 0L) {
        durationMs
    } else {
        profileFallbackDurationMs(
            kind = parentMetaType.profileCompletedContentKind(),
            isEpisode = isEpisode,
        )
    }
    if (effectiveDurationMs <= 0L) return lastPositionMs.coerceAtLeast(0L)
    if (isEffectivelyCompleted) return effectiveDurationMs
    if (lastPositionMs > 0L) return lastPositionMs.coerceIn(0L, effectiveDurationMs)
    val explicitPercent = normalizedProgressPercent ?: return 0L
    return (effectiveDurationMs * (explicitPercent / 100f)).toLong().coerceIn(0L, effectiveDurationMs)
}

private fun profileProgressDurationByActivityKey(
    progressEntries: List<WatchProgressEntry>,
): Map<String, Long> {
    val durationByKey = HashMap<String, Long>()
    progressEntries.forEach { entry ->
        val key = entry.profileActivityKey() ?: return@forEach
        val durationMs = entry.profileTrackedDurationMs()
        if (durationMs > (durationByKey[key] ?: 0L)) durationByKey[key] = durationMs
    }
    return durationByKey
}

private class ProfileWatchedDurationRef(
    val activityKey: String,
    val titleKey: String,
    val isMovie: Boolean,
    val season: Int?,
    val episode: Int?,
)

private fun profileWatchedDurationRefs(watchedItems: List<WatchedItem>): List<ProfileWatchedDurationRef> {
    val seenKeys = HashSet<String>()
    return watchedItems.mapNotNull { item ->
        val activityKey = item.profileActivityKey() ?: return@mapNotNull null
        if (!seenKeys.add(activityKey)) return@mapNotNull null
        val kind = item.type.profileCompletedContentKind() ?: return@mapNotNull null
        ProfileWatchedDurationRef(
            activityKey = activityKey,
            titleKey = "$kind:${item.id.trim()}",
            isMovie = kind == "movie",
            season = item.season,
            episode = item.episode,
        )
    }
}

private fun ProfileWatchedDurationRef.estimatedDurationMs(facts: ProfileTitleFacts?): Long {
    val minutes = when {
        isMovie && season == null && episode == null ->
            facts?.runtimeMinutes?.toLong() ?: ProfileInsightsFallbackMovieMinutes
        !isMovie && season != null && episode != null ->
            facts?.episodeRuntimeMinutes?.get(profileEpisodeKey(season, episode))?.toLong()
                ?: ProfileInsightsFallbackEpisodeMinutes
        else -> return 0L
    }
    return minutes * ProfileInsightsMinuteMs
}

private class ProfileWatchTimeSplit(
    val movieMs: Long,
    val seriesMs: Long,
) {
    val totalMs: Long get() = movieMs + seriesMs

    val movieShare: Float?
        get() = if (totalMs > 0L) movieMs.toFloat() / totalMs.toFloat() else null
}

private fun profileWatchTimeSplit(
    progressDurationByKey: Map<String, Long>,
    watchedDurationRefs: List<ProfileWatchedDurationRef>,
    facts: Map<String, ProfileTitleFacts>,
): ProfileWatchTimeSplit {
    val durationByKey = HashMap(progressDurationByKey)
    watchedDurationRefs.forEach { ref ->
        val durationMs = ref.estimatedDurationMs(facts[ref.titleKey])
        if (durationMs > (durationByKey[ref.activityKey] ?: 0L)) durationByKey[ref.activityKey] = durationMs
    }
    var movieMs = 0L
    var seriesMs = 0L
    durationByKey.forEach { (key, durationMs) ->
        when (key.substringBefore(':')) {
            "movie" -> movieMs += durationMs
            "series" -> seriesMs += durationMs
        }
    }
    return ProfileWatchTimeSplit(movieMs = movieMs, seriesMs = seriesMs)
}

private fun buildProfileTypeBalance(
    watchedItems: List<WatchedItem>,
    progressEntries: List<WatchProgressEntry>,
    fullyWatchedSeriesKeys: Set<String>,
): ProfileTypeBalance {
    val watchedMovieIds = mutableSetOf<String>()
    val completedSeriesIds = mutableSetOf<String>()
    val engagedEpisodesBySeries = mutableMapOf<String, MutableSet<String>>()

    watchedItems.forEach { item ->
        val id = item.id.trim().takeIf { it.isNotBlank() } ?: return@forEach
        when (item.type.profileCompletedContentKind()) {
            "movie" -> if (item.season == null && item.episode == null) watchedMovieIds += id
            "series" -> {
                if (item.season != null && item.episode != null) {
                    engagedEpisodesBySeries.getOrPut(id) { mutableSetOf() } += "${item.season}:${item.episode}"
                } else if (!item.type.equals("tv", ignoreCase = true)) {
                    completedSeriesIds += id
                }
                if (watchedItemKey(item.type, item.id) in fullyWatchedSeriesKeys) {
                    completedSeriesIds += id
                }
            }
        }
    }
    progressEntries.forEach { entry ->
        val id = entry.parentMetaId.trim().takeIf { it.isNotBlank() } ?: return@forEach
        when (entry.parentMetaType.profileCompletedContentKind()) {
            "movie" -> if (entry.isEffectivelyCompleted) watchedMovieIds += id
            "series" -> {
                val season = entry.seasonNumber
                val episode = entry.episodeNumber
                if (season != null && episode != null && entry.profileTrackedDurationMs() > 0L) {
                    engagedEpisodesBySeries.getOrPut(id) { mutableSetOf() } += "$season:$episode"
                }
            }
        }
    }

    val engagedSeriesIds = completedSeriesIds + engagedEpisodesBySeries
        .filterValues { episodes -> episodes.size >= PROFILE_SERIES_MIN_ENGAGED_EPISODES }
        .keys

    return ProfileTypeBalance(
        movieTitleIds = watchedMovieIds.toSet(),
        seriesTitleIds = engagedSeriesIds.toSet(),
    )
}

private const val PROFILE_SERIES_MIN_ENGAGED_EPISODES = 2

private data class ProfileTypeBalance(
    val movieTitleIds: Set<String>,
    val seriesTitleIds: Set<String>,
) {
    val movieTitleCount: Int get() = movieTitleIds.size
    val seriesTitleCount: Int get() = seriesTitleIds.size

    val titleTotal: Int get() = movieTitleCount + seriesTitleCount

    val movieShare: Float
        get() = if (titleTotal > 0) movieTitleCount.toFloat() / titleTotal.toFloat() else 0.5f

    val titleKeys: List<String> =
        movieTitleIds.map { id -> "movie:$id" } + seriesTitleIds.map { id -> "series:$id" }
}

private fun WatchProgressEntry.profileArtworkUrl(): String? =
    poster?.takeIf { it.isNotBlank() }
        ?: background?.takeIf { it.isNotBlank() }
        ?: episodeThumbnail?.takeIf { it.isNotBlank() }

private suspend fun profileFetchPosterMetadata(type: String?, id: String?): Pair<String?, String?> {
    var artwork: String? = null
    var releaseInfo: String? = null

    for ((lookupType, lookupId) in profileMetaLookupCandidates(type, id)) {
        val meta = MetaDetailsRepository.peek(type = lookupType, id = lookupId)
            ?: runCatching {
                MetaDetailsRepository.fetch(type = lookupType, id = lookupId)
            }.onFailure { error ->
                profileInsightsLog.w(error) {
                    "Failed to hydrate profile metadata for $lookupType/$lookupId"
                }
            }.getOrNull()

        if (meta != null) {
            artwork = artwork ?: meta.profileMetaArtworkUrl()
            releaseInfo = releaseInfo ?: meta.releaseInfo?.trim()?.takeIf { it.isNotBlank() }
            if (artwork != null && releaseInfo != null) {
                return artwork to releaseInfo
            }
        }
    }

    return artwork to releaseInfo
}

private fun MetaDetails?.profileMetaArtworkUrl(): String? =
    this?.poster?.trim()?.takeIf { it.isNotBlank() }
        ?: this?.background?.trim()?.takeIf { it.isNotBlank() }

private fun profileMetaLookupCandidates(type: String?, id: String?): List<Pair<String, String>> {
    val cleanId = id?.trim()?.takeIf { it.isNotBlank() } ?: return emptyList()
    val cleanType = type?.trim()?.takeIf { it.isNotBlank() } ?: return emptyList()
    val normalizedKind = cleanType.profileCompletedContentKind()

    val typeCandidates = buildList {
        add(cleanType)
        normalizedKind?.let(::add)
        when (normalizedKind) {
            "movie" -> add("film")
            "series" -> {
                add("tv")
                add("show")
                add("tvshow")
            }
        }
    }
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinctBy { it.lowercase() }

    return typeCandidates.map { candidateType -> candidateType to cleanId }
}

private fun profileRecentActivityCount(
    watchedItems: List<WatchedItem>,
    progressEntries: List<WatchProgressEntry>,
    recentCutoff: Long,
): Int = buildSet {
    watchedItems
        .asSequence()
        .filter { item -> item.markedAtEpochMs >= recentCutoff }
        .mapNotNull(WatchedItem::profileActivityKey)
        .forEach(::add)
    progressEntries
        .asSequence()
        .filter { entry -> entry.lastUpdatedEpochMs >= recentCutoff }
        .mapNotNull(WatchProgressEntry::profileActivityKey)
        .forEach(::add)
}.size

private fun WatchedItem.profileActivityKey(): String? {
    if (!isProfileTrackableActivity()) return null
    val kind = type.profileCompletedContentKind() ?: return null
    val contentId = id.trim().takeIf { it.isNotBlank() } ?: return null
    return "$kind:$contentId:${season ?: -1}:${episode ?: -1}"
}

private fun WatchProgressEntry.profileActivityKey(): String? {
    if (!isProfileTrackableActivity()) return null
    val kind = parentMetaType.profileCompletedContentKind() ?: return null
    val contentId = parentMetaId.trim().takeIf { it.isNotBlank() } ?: return null
    return "$kind:$contentId:${seasonNumber ?: -1}:${episodeNumber ?: -1}"
}

private fun WatchedItem.isProfileTrackableActivity(): Boolean {
    val kind = type.profileCompletedContentKind() ?: return false
    return kind == "movie" || (kind == "series" && season != null && episode != null)
}

private fun WatchProgressEntry.isProfileTrackableActivity(): Boolean {
    val kind = parentMetaType.profileCompletedContentKind() ?: return false
    return kind == "movie" || (kind == "series" && seasonNumber != null && episodeNumber != null)
}

private fun profileFallbackDurationMs(kind: String?, isEpisode: Boolean): Long = when {
    kind == null -> 0L
    kind == "movie" && !isEpisode -> ProfileInsightsFallbackMovieMinutes * ProfileInsightsMinuteMs
    kind == "series" && isEpisode -> ProfileInsightsFallbackEpisodeMinutes * ProfileInsightsMinuteMs
    else -> 0L
}

private fun profileCachedMeta(type: String?, id: String?): MetaDetails? {
    for ((lookupType, lookupId) in profileMetaLookupCandidates(type, id)) {
        MetaDetailsRepository.peek(type = lookupType, id = lookupId)?.let { return it }
    }
    return null
}

private fun profileParseRuntimeMinutes(value: String?): Int? {
    val runtime = value?.trim()?.takeIf { it.isNotBlank() } ?: return null

    profileHourMinuteColonRegex.matchEntire(runtime)?.let { match ->
        val hours = match.groupValues[1].toIntOrNull() ?: return null
        val minutes = match.groupValues[2].toIntOrNull() ?: return null
        return ((hours * 60) + minutes).coerceAtLeast(0)
    }

    val hoursToken = profileHourTokenRegex.find(runtime)?.groupValues?.getOrNull(1)?.toIntOrNull()
    val minutesToken = profileMinuteTokenRegex.find(runtime)?.groupValues?.getOrNull(1)?.toIntOrNull()
    if (hoursToken != null || minutesToken != null) {
        return (((hoursToken ?: 0).coerceAtLeast(0) * 60) + (minutesToken ?: 0).coerceAtLeast(0))
    }

    return profileDigitsOnlyRegex.matchEntire(runtime)
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()
        ?.coerceAtLeast(0)
}

private fun profileLastActivityByTitleKey(
    watchedItems: List<WatchedItem>,
    progressEntries: List<WatchProgressEntry>,
): Map<String, Long> {
    val lastActivity = HashMap<String, Long>()
    fun record(kind: String?, id: String, epochMs: Long) {
        val cleanKind = kind ?: return
        val key = "$cleanKind:${id.trim()}"
        if (epochMs > (lastActivity[key] ?: Long.MIN_VALUE)) lastActivity[key] = epochMs
    }
    watchedItems.forEach { item -> record(item.type.profileCompletedContentKind(), item.id, item.markedAtEpochMs) }
    progressEntries.forEach { entry ->
        record(entry.parentMetaType.profileCompletedContentKind(), entry.parentMetaId, entry.lastUpdatedEpochMs)
    }
    return lastActivity
}

private fun profileLibraryGenresByKey(libraryItems: List<LibraryItem>): Map<String, List<String>> =
    libraryItems
        .mapNotNull { item ->
            val kind = item.type.profileCompletedContentKind() ?: return@mapNotNull null
            val genres = item.genres.profileCleanGenres()
            if (genres.isEmpty()) null else "$kind:${item.id.trim()}" to genres
        }
        .toMap()

private fun buildProfileWatchedTitleGenreSegments(
    titleKeys: List<String>,
    libraryGenresByKey: Map<String, List<String>>,
    facts: Map<String, ProfileTitleFacts>,
): List<ProfileTasteSegment> {
    val counts = HashMap<String, Int>()
    titleKeys.forEach { key ->
        val genres = libraryGenresByKey[key]
            ?: facts[key]?.genres?.takeIf { it.isNotEmpty() }
            ?: return@forEach
        genres.forEach { genre -> counts[genre] = (counts[genre] ?: 0) + 1 }
    }

    val total = counts.values.sum().coerceAtLeast(1)
    return counts.toList()
        .sortedByDescending { (_, count) -> count }
        .map { (genre, count) ->
            ProfileTasteSegment(label = genre, share = count.toFloat() / total.toFloat())
        }
}

// Addons and TMDB name genres differently ("Sci-Fi" vs "Science Fiction", TV-only combined
// genres such as "Action & Adventure"). Normalize so every device and source counts alike.
private val profileGenreAliases = mapOf(
    "sci-fi" to "Science Fiction",
    "sci fi" to "Science Fiction",
    "scifi" to "Science Fiction",
    "science-fiction" to "Science Fiction",
    "science fiction" to "Science Fiction",
)

private fun List<String>.profileCleanGenres(): List<String> =
    flatMap { genre -> genre.split('&') }
        .map { genre -> genre.trim() }
        .filter { genre -> genre.isNotBlank() }
        .map { genre -> profileGenreAliases[genre.lowercase()] ?: genre }
        .distinctBy { genre -> genre.lowercase() }

private class ProfileTitleFacts(
    val genres: List<String>,
    val runtimeMinutes: Int?,
    val episodeRuntimeMinutes: Map<Long, Int>,
    val resolvedAtEpochMs: Long,
    // Seeded from the shared meta cache: runtimes only. Its genres may be TMDB-enriched or
    // localized, so it is still fetched from the addon to keep genres consistent across devices.
    val partial: Boolean = false,
)

private fun profileEpisodeKey(season: Int, episode: Int): Long =
    (season.toLong() shl 32) or (episode.toLong() and 0xFFFFFFFFL)

private fun profileEpisodeKeySeason(key: Long): Int = (key shr 32).toInt()

private fun profileEpisodeKeyEpisode(key: Long): Int = key.toInt()

private fun MetaDetails.toProfileTitleFacts(
    resolvedAtEpochMs: Long,
    partial: Boolean = false,
): ProfileTitleFacts =
    ProfileTitleFacts(
        genres = if (partial) emptyList() else genres.profileCleanGenres(),
        runtimeMinutes = profileParseRuntimeMinutes(runtime)?.takeIf { minutes -> minutes > 0 },
        episodeRuntimeMinutes = buildMap {
            videos.forEach { video ->
                val season = video.season ?: return@forEach
                val episode = video.episode ?: return@forEach
                val minutes = video.runtime?.takeIf { it > 0 } ?: return@forEach
                val key = profileEpisodeKey(season, episode)
                if (key !in this) put(key, minutes)
            }
        },
        resolvedAtEpochMs = resolvedAtEpochMs,
        partial = partial,
    )

private fun String.profileSplitTitleKey(): Pair<String, String>? {
    val kind = substringBefore(':', missingDelimiterValue = "").takeIf { it.isNotBlank() } ?: return null
    val id = substringAfter(':', missingDelimiterValue = "").takeIf { it.isNotBlank() } ?: return null
    return kind to id
}

@Serializable
private data class StoredProfileTitleFacts(
    @SerialName("v") val version: Int = PROFILE_TITLE_FACTS_STORAGE_VERSION,
    @SerialName("t") val titles: Map<String, StoredProfileTitle> = emptyMap(),
    @SerialName("f") val failures: Map<String, Long> = emptyMap(),
    @SerialName("u") val refreshedAtByProfile: Map<String, Long> = emptyMap(),
    @SerialName("s") val fullSnapshotAtByProfile: Map<String, Long> = emptyMap(),
)

@Serializable
private data class StoredProfileTitle(
    @SerialName("g") val genres: List<String> = emptyList(),
    @SerialName("r") val runtimeMinutes: Int? = null,
    @SerialName("e") val episodeRuntimes: List<Int> = emptyList(),
    @SerialName("a") val resolvedAtEpochMs: Long = 0L,
    @SerialName("p") val partial: Boolean = false,
)

private const val PROFILE_TITLE_FACTS_STORAGE_VERSION = 1

private fun ProfileTitleFacts.toStored(): StoredProfileTitle =
    StoredProfileTitle(
        genres = genres,
        runtimeMinutes = runtimeMinutes,
        episodeRuntimes = buildList(episodeRuntimeMinutes.size * 3) {
            episodeRuntimeMinutes.forEach { (key, minutes) ->
                add(profileEpisodeKeySeason(key))
                add(profileEpisodeKeyEpisode(key))
                add(minutes)
            }
        },
        resolvedAtEpochMs = resolvedAtEpochMs,
        partial = partial,
    )

private fun StoredProfileTitle.toFacts(): ProfileTitleFacts =
    ProfileTitleFacts(
        genres = genres.profileCleanGenres(),
        runtimeMinutes = runtimeMinutes,
        episodeRuntimeMinutes = buildMap {
            var index = 0
            while (index + 2 < episodeRuntimes.size) {
                put(profileEpisodeKey(episodeRuntimes[index], episodeRuntimes[index + 1]), episodeRuntimes[index + 2])
                index += 3
            }
        },
        resolvedAtEpochMs = resolvedAtEpochMs,
        partial = partial,
    )

private object ProfileTitleFactsStore {
    private const val FETCH_CONCURRENCY = 4
    private const val PUBLISH_BATCH_SIZE = 8
    private const val SEED_YIELD_INTERVAL = 48
    private const val SAVE_DEBOUNCE_MS = 1_500L

    private const val FACTS_MAX_AGE_MS = 30L * 24L * 60L * 60L * 1000L

    private const val FAILURE_RETRY_AFTER_MS = 3L * 24L * 60L * 60L * 1000L

    private val json = Json { ignoreUnknownKeys = true }
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private const val FETCH_BUDGET_PER_WINDOW = 150
    private const val FETCH_BUDGET_WINDOW_MS = 60L * 60_000L
    private var budgetWindowStartedAtEpochMs = 0L
    private var fetchesInWindow = 0
    private var pausedUntilEpochMs = 0L
    private var lastRequest: ProfileTitleHydrationRequest? = null
    private var resumeJob: Job? = null
    private val fullSnapshotAtByProfile = mutableMapOf<Int, Long>()

    private val _facts = MutableStateFlow<Map<String, ProfileTitleFacts>>(emptyMap())
    val facts: StateFlow<Map<String, ProfileTitleFacts>> = _facts.asStateFlow()

    private val failures = mutableMapOf<String, Long>()
    private val _failedKeys = MutableStateFlow<Set<String>>(emptySet())
    val failedKeys: StateFlow<Set<String>> = _failedKeys.asStateFlow()

    private val _refreshedAtByProfile = MutableStateFlow<Map<Int, Long>>(emptyMap())
    val refreshedAtByProfile: StateFlow<Map<Int, Long>> = _refreshedAtByProfile.asStateFlow()

    private val attemptedFetchKeys = mutableSetOf<String>()

    private var loaded = false
    private var loading: Deferred<StoredProfileTitleFacts?>? = null
    private var saveJob: Job? = null

    suspend fun ensureLoaded() {
        if (loaded) return
        val job = loading ?: ioScope.async { readFromDisk() }.also { loading = it }
        val stored = job.await()
        if (loaded) return
        loaded = true
        loading = null
        if (stored == null) return
        val restored = stored.titles.mapValues { (_, title) -> title.toFacts() }
        _facts.value = restored + _facts.value
        stored.failures.forEach { (key, failedAt) ->
            if (key !in failures) failures[key] = failedAt
        }
        syncFailedKeys()
        val restoredRefreshes = stored.refreshedAtByProfile.mapNotNull { (profileId, refreshedAt) ->
            profileId.toIntOrNull()?.let { it to refreshedAt }
        }.toMap()
        _refreshedAtByProfile.value = restoredRefreshes + _refreshedAtByProfile.value
        stored.fullSnapshotAtByProfile.forEach { (profileId, snapshotAt) ->
            val index = profileId.toIntOrNull() ?: return@forEach
            if (index !in fullSnapshotAtByProfile) fullSnapshotAtByProfile[index] = snapshotAt
        }
    }

    fun fullSnapshotAt(profileId: Int): Long? = fullSnapshotAtByProfile[profileId]

    fun markFullSnapshot(profileId: Int, snapshotAtEpochMs: Long) {
        fullSnapshotAtByProfile[profileId] = snapshotAtEpochMs
        scheduleSave()
    }

    suspend fun clearAll() {
        ensureLoaded()
        resumeJob?.cancel()
        resumeJob = null
        _facts.value = emptyMap()
        failures.clear()
        attemptedFetchKeys.clear()
        // A clear should visibly start over; the clear itself is limited to once an hour.
        budgetWindowStartedAtEpochMs = WatchedClock.nowEpochMs()
        fetchesInWindow = 0
        syncFailedKeys()
        scheduleSave()
        lastRequest?.let { request -> mainScope.launch { hydrate(request) } }
    }

    fun clearFailures() {
        if (failures.isEmpty()) return
        failures.keys.forEach(attemptedFetchKeys::remove)
        failures.clear()
        syncFailedKeys()
        scheduleSave()
    }

    fun markRefreshed(profileId: Int, refreshedAtEpochMs: Long) {
        _refreshedAtByProfile.value = _refreshedAtByProfile.value + (profileId to refreshedAtEpochMs)
        scheduleSave()
    }

    private fun syncFailedKeys() {
        _failedKeys.value = failures.keys.toSet()
    }

    suspend fun hydrate(request: ProfileTitleHydrationRequest) {
        lastRequest = request
        ensureLoaded()
        seedFromMetaCache(request.seedKeys)
        fetchMissingFacts(request.fetchTargetKeys)
    }

    private fun ProfileTitleFacts?.isStale(now: Long): Boolean =
        this == null || now - resolvedAtEpochMs > FACTS_MAX_AGE_MS

    private fun ProfileTitleFacts?.needsFetch(now: Long): Boolean =
        this == null || partial || isStale(now)

    private suspend fun seedFromMetaCache(keys: List<String>) {
        val now = WatchedClock.nowEpochMs()
        val pending = mutableMapOf<String, ProfileTitleFacts>()
        keys.forEachIndexed { index, key ->
            if (index > 0 && index % SEED_YIELD_INTERVAL == 0) yield()
            if (!_facts.value[key].isStale(now)) return@forEachIndexed
            val (kind, id) = key.profileSplitTitleKey() ?: return@forEachIndexed
            profileCachedMeta(kind, id)?.let { meta -> pending[key] = meta.toProfileTitleFacts(now, partial = true) }
        }
        publish(pending)
    }

    private suspend fun fetchMissingFacts(keys: List<String>) {
        val now = WatchedClock.nowEpochMs()
        if (now < pausedUntilEpochMs) {
            scheduleResume(pausedUntilEpochMs)
            return
        }
        if (now - budgetWindowStartedAtEpochMs >= FETCH_BUDGET_WINDOW_MS) {
            budgetWindowStartedAtEpochMs = now
            fetchesInWindow = 0
        }
        val candidates = keys.filter { key ->
            key !in attemptedFetchKeys &&
                _facts.value[key].needsFetch(now) &&
                failures[key]?.let { failedAt -> now - failedAt < FAILURE_RETRY_AFTER_MS } != true
        }
        if (candidates.isEmpty()) return
        val remainingBudget = FETCH_BUDGET_PER_WINDOW - fetchesInWindow
        if (remainingBudget <= 0) {
            scheduleResume(budgetWindowStartedAtEpochMs + FETCH_BUDGET_WINDOW_MS)
            return
        }
        val targets = candidates.take(remainingBudget)
        val pending = mutableMapOf<String, ProfileTitleFacts>()
        val permits = Semaphore(FETCH_CONCURRENCY)
        try {
            coroutineScope {
                targets.forEach { key ->
                    launch {
                        permits.withPermit {
                            // An addon started rate-limiting while we waited for a permit.
                            if (WatchedClock.nowEpochMs() < pausedUntilEpochMs) return@withPermit
                            val (kind, id) = key.profileSplitTitleKey() ?: return@withPermit
                            if (!attemptedFetchKeys.add(key)) return@withPermit
                            fetchesInWindow += 1
                            var settled = false
                            try {
                                val outcome = MetaDetailsRepository.fetchLightweight(type = kind, id = id)
                                settled = true
                                when (outcome) {
                                    is MetaLookupOutcome.Loaded -> {
                                        if (failures.remove(key) != null) syncFailedKeys()
                                        pending[key] = outcome.meta.toProfileTitleFacts(WatchedClock.nowEpochMs())
                                        if (pending.size >= PUBLISH_BATCH_SIZE) publish(pending)
                                    }
                                    is MetaLookupOutcome.Throttled -> {
                                        // Not the title's fault: retry it once the addon allows.
                                        attemptedFetchKeys.remove(key)
                                        pausedUntilEpochMs = maxOf(pausedUntilEpochMs, outcome.retryAtEpochMs)
                                    }
                                    MetaLookupOutcome.Unavailable -> attemptedFetchKeys.remove(key)
                                    MetaLookupOutcome.Failed -> recordFailure(key)
                                }
                            } catch (error: CancellationException) {
                                throw error
                            } catch (error: Throwable) {
                                settled = true
                                recordFailure(key)
                                profileInsightsLog.w(error) { "Failed to hydrate title facts for $kind/$id" }
                            } finally {
                                // Cancelled mid-flight (e.g. the tab was left): allow a retry next time.
                                if (!settled) attemptedFetchKeys.remove(key)
                            }
                        }
                    }
                }
            }
        } finally {
            publish(pending)
        }
        val afterRun = WatchedClock.nowEpochMs()
        when {
            pausedUntilEpochMs > afterRun -> scheduleResume(pausedUntilEpochMs)
            candidates.size > targets.size -> scheduleResume(budgetWindowStartedAtEpochMs + FETCH_BUDGET_WINDOW_MS)
        }
    }

    private fun scheduleResume(atEpochMs: Long) {
        if (resumeJob?.isActive == true) return
        resumeJob = mainScope.launch {
            delay((atEpochMs - WatchedClock.nowEpochMs()).coerceAtLeast(1_000L))
            lastRequest?.let { request -> hydrate(request) }
        }
    }

    private fun recordFailure(key: String) {
        failures[key] = WatchedClock.nowEpochMs()
        syncFailedKeys()
        scheduleSave()
    }

    private fun publish(pending: MutableMap<String, ProfileTitleFacts>) {
        if (pending.isEmpty()) return
        _facts.value = _facts.value + pending
        pending.clear()
        scheduleSave()
    }

    private fun scheduleSave() {
        if (!loaded) return
        val factsSnapshot = _facts.value
        val failuresSnapshot = failures.toMap()
        val refreshedSnapshot = _refreshedAtByProfile.value
        val fullSnapshotTimes = fullSnapshotAtByProfile.toMap()
        saveJob?.cancel()
        saveJob = ioScope.launch {
            delay(SAVE_DEBOUNCE_MS)
            writeToDisk(factsSnapshot, failuresSnapshot, refreshedSnapshot, fullSnapshotTimes)
        }
    }

    private fun readFromDisk(): StoredProfileTitleFacts? =
        try {
            ProfileTitleFactsStorage.load()
                ?.takeIf { it.isNotBlank() }
                ?.let { payload -> json.decodeFromString(StoredProfileTitleFacts.serializer(), payload) }
                ?.takeIf { stored -> stored.version == PROFILE_TITLE_FACTS_STORAGE_VERSION }
        } catch (error: Throwable) {
            profileInsightsLog.w(error) { "Discarding unreadable profile title facts cache" }
            null
        }

    private fun writeToDisk(
        factsSnapshot: Map<String, ProfileTitleFacts>,
        failuresSnapshot: Map<String, Long>,
        refreshedSnapshot: Map<Int, Long>,
        fullSnapshotTimes: Map<Int, Long>,
    ) {
        val now = WatchedClock.nowEpochMs()
        val stored = StoredProfileTitleFacts(
            titles = factsSnapshot.mapValues { (_, facts) -> facts.toStored() },
            failures = failuresSnapshot.filterValues { failedAt -> now - failedAt < FAILURE_RETRY_AFTER_MS },
            refreshedAtByProfile = refreshedSnapshot.mapKeys { (profileId, _) -> profileId.toString() },
            fullSnapshotAtByProfile = fullSnapshotTimes.mapKeys { (profileId, _) -> profileId.toString() },
        )
        try {
            ProfileTitleFactsStorage.save(json.encodeToString(StoredProfileTitleFacts.serializer(), stored))
        } catch (error: Throwable) {
            profileInsightsLog.w(error) { "Failed to persist profile title facts cache" }
        }
    }
}

// How far genre/runtime hydration has got, so partial numbers are not mistaken for final ones.
private class ProfileTitleFactsProgress(
    val detailsResolved: Int,
    val detailsTotal: Int,
    val detailsPending: Boolean,
    val genreResolved: Int,
    val genreTotal: Int,
    val genresPending: Boolean,
)

private fun profileTitleFactsProgress(
    request: ProfileTitleHydrationRequest,
    facts: Map<String, ProfileTitleFacts>,
    failedKeys: Set<String>,
): ProfileTitleFactsProgress {
    fun isFetched(key: String) = facts[key]?.partial == false
    val detailsResolved = request.fetchTargetKeys.count(::isFetched)
    val detailsFailed = request.fetchTargetKeys.count { key -> !isFetched(key) && key in failedKeys }
    val genreTargets = request.genreTargetKeys.toHashSet()
    val genreResolved = request.genreTitleKeys.count { key -> key !in genreTargets || isFetched(key) }
    val genreFailed = request.genreTitleKeys.count { key -> key in genreTargets && !isFetched(key) && key in failedKeys }
    return ProfileTitleFactsProgress(
        detailsResolved = detailsResolved,
        detailsTotal = request.fetchTargetKeys.size,
        detailsPending = detailsResolved + detailsFailed < request.fetchTargetKeys.size,
        genreResolved = genreResolved,
        genreTotal = request.genreTitleKeys.size,
        genresPending = genreResolved + genreFailed < request.genreTitleKeys.size,
    )
}

internal suspend fun clearProfileInsightTitleFacts() {
    ProfileTitleFactsStore.clearAll()
}

private class ProfileInsightsRefreshContext(
    val profileId: Int,
    val libraryItems: List<LibraryItem>,
    val hydrationRequest: ProfileTitleHydrationRequest?,
)

private object ProfileInsightsRefresher {
    private const val COOLDOWN_MS = 30_000L
    private const val FULL_SNAPSHOT_INTERVAL_MS = 24L * 60L * 60_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private var context: ProfileInsightsRefreshContext? = null
    private var job: Job? = null
    private var lastStartedAtEpochMs = 0L

    fun bind(context: ProfileInsightsRefreshContext) {
        this.context = context
    }

    fun unbind() {
        context = null
    }

    fun refresh() {
        val target = context ?: return
        if (job?.isActive == true) return
        val now = WatchedClock.nowEpochMs()
        if (now - lastStartedAtEpochMs < COOLDOWN_MS) return
        lastStartedAtEpochMs = now
        job = scope.launch {
            _isRefreshing.value = true
            try {
                ProfileTitleFactsStore.ensureLoaded()
                ProfileTitleFactsStore.clearFailures()
                val lastFullSnapshotAt = ProfileTitleFactsStore.fullSnapshotAt(target.profileId)
                val fullSnapshot = lastFullSnapshotAt == null || now - lastFullSnapshotAt >= FULL_SNAPSHOT_INTERVAL_MS
                coroutineScope {
                    launch {
                        runRefreshStep("watched") {
                            if (fullSnapshot) {
                                WatchedRepository.forceSnapshotRefreshFromServer(target.profileId)
                            } else {
                                WatchedRepository.pullFromServer(target.profileId)
                            }
                        }
                    }
                    launch {
                        runRefreshStep("progress") {
                            if (fullSnapshot) {
                                WatchProgressRepository.forceSnapshotRefreshFromServer(target.profileId)
                            } else {
                                WatchProgressRepository.pullFromServer(target.profileId)
                            }
                        }
                    }
                    launch {
                        runRefreshStep("calendar") { forceRefreshLibraryReleaseSchedule(target.libraryItems) }
                    }
                    launch {
                        runRefreshStep("titles") { target.hydrationRequest?.let { ProfileTitleFactsStore.hydrate(it) } }
                    }
                }
                if (fullSnapshot) ProfileTitleFactsStore.markFullSnapshot(target.profileId, now)
                ProfileTitleFactsStore.markRefreshed(target.profileId, WatchedClock.nowEpochMs())
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    private suspend fun runRefreshStep(name: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            profileInsightsLog.w(error) { "Profile insights refresh step failed: $name" }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfileInsightsPullToRefresh(
    enabled: Boolean,
    content: @Composable () -> Unit,
) {
    if (enabled) {
        val isRefreshing by ProfileInsightsRefresher.isRefreshing.collectAsStateWithLifecycle()
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = ProfileInsightsRefresher::refresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            content()
        }
    } else {
        content()
    }
}

@Composable
private fun ProfileInsightsRefreshStatusRow(
    refreshedAtEpochMs: Long?,
    failedGenreTitleCount: Int,
    factsProgress: ProfileTitleFactsProgress?,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val now by produceState(WatchedClock.nowEpochMs()) {
        while (true) {
            delay(60_000L)
            value = WatchedClock.nowEpochMs()
        }
    }
    val statusText = when {
        isRefreshing -> stringResource(Res.string.profile_insights_refreshing)
        refreshedAtEpochMs == null -> stringResource(Res.string.profile_insights_pull_to_refresh)
        else -> {
            val elapsedMinutes = ((now - refreshedAtEpochMs).coerceAtLeast(0L) / ProfileInsightsMinuteMs).toInt()
            when {
                elapsedMinutes < 1 -> stringResource(Res.string.profile_insights_updated_just_now)
                elapsedMinutes < 60 -> stringResource(Res.string.profile_insights_updated_minutes, elapsedMinutes)
                elapsedMinutes < 24 * 60 -> stringResource(Res.string.profile_insights_updated_hours, elapsedMinutes / 60)
                else -> stringResource(Res.string.profile_insights_updated_days, elapsedMinutes / (24 * 60))
            }
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelSmall,
                color = tokens.colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (factsProgress != null && factsProgress.detailsPending) {
                Text(
                    text = stringResource(
                        Res.string.profile_insights_details_progress,
                        factsProgress.detailsResolved,
                        factsProgress.detailsTotal,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (failedGenreTitleCount > 0 && !isRefreshing) {
                Text(
                    text = if (failedGenreTitleCount == 1) {
                        stringResource(Res.string.profile_insights_genre_failures_single)
                    } else {
                        stringResource(Res.string.profile_insights_genre_failures, failedGenreTitleCount)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(
            onClick = onRefresh,
            enabled = !isRefreshing,
            modifier = Modifier.size(36.dp),
        ) {
            if (isRefreshing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = tokens.colors.textMuted,
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = stringResource(Res.string.profile_insights_refresh),
                    modifier = Modifier.size(20.dp),
                    tint = tokens.colors.textMuted,
                )
            }
        }
    }
}

private fun buildProfileTasteDnaChips(
    libraryCount: Int,
    continueCount: Int,
    completedCount: Int,
    recentActivityCount: Int,
    upcomingCount: Int,
    movieShare: Float,
    movieSeriesTotal: Int,
): List<ProfileTasteDnaChip> = buildList {
    when {
        movieSeriesTotal == 0 -> add(ProfileTasteDnaChip.Learning)
        movieShare >= 0.62f -> add(ProfileTasteDnaChip.MovieLeaning)
        movieShare <= 0.38f -> add(ProfileTasteDnaChip.SeriesLeaning)
        else -> add(ProfileTasteDnaChip.Balanced)
    }
    if (continueCount >= 3) add(ProfileTasteDnaChip.BingeReady)
    if (recentActivityCount >= 5) add(ProfileTasteDnaChip.HighActivity)
    if (libraryCount >= 12) add(ProfileTasteDnaChip.Collector)
    if (upcomingCount > 0) add(ProfileTasteDnaChip.RadarWatcher)
    if (completedCount >= 10) add(ProfileTasteDnaChip.Completionist)
}.distinct().take(4)

private fun LibraryItem.profileReleaseIsoDate(): String? =
    releaseInfo.profileExtractIsoDate()

private fun String?.profileExtractIsoDate(): String? {
    val value = this?.trim().orEmpty()
    if (value.isBlank()) return null

    if (value.length >= 10) {
        for (start in 0..(value.length - 10)) {
            val candidate = value.substring(start, start + 10)
            if (candidate.isIsoDateCandidate()) return candidate
        }
    }

    val normalized = value
        .replace(',', ' ')
        .replace('.', ' ')
        .replace('/', ' ')
        .replace('-', ' ')
        .replace(Regex("\\s+"), " ")
        .trim()
    val tokens = normalized.split(' ').filter(String::isNotBlank)
    val yearIndex = tokens.indexOfFirst { token ->
        token.length == 4 && token.all(Char::isDigit) && token.toIntOrNull() in 1000..9999
    }
    if (yearIndex < 0) return null

    val year = tokens[yearIndex].toInt()
    val monthBefore = tokens.getOrNull(yearIndex - 1)?.profileMonthNumber()
    val monthAfter = tokens.getOrNull(yearIndex + 1)?.profileMonthNumber()
    val month = monthBefore ?: monthAfter ?: 12
    val dayBefore = tokens.getOrNull(yearIndex - 1)?.toIntOrNull()?.takeIf { it in 1..31 }
    val dayAfterOne = tokens.getOrNull(yearIndex + 1)?.toIntOrNull()?.takeIf { it in 1..31 }
    val dayAfterTwo = tokens.getOrNull(yearIndex + 2)?.toIntOrNull()?.takeIf { it in 1..31 }
    val day = when {
        monthBefore != null -> dayBefore ?: 1
        monthAfter != null -> dayAfterTwo ?: 1
        else -> dayAfterOne ?: 31
    }.coerceAtMost(profileDaysInMonth(year, month))

    return "${year.toString().padStart(4, '0')}-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
}

private fun String.isIsoDateCandidate(): Boolean =
    length == 10 &&
        this[4] == '-' &&
        this[7] == '-' &&
        take(4).all(Char::isDigit) &&
        substring(5, 7).all(Char::isDigit) &&
        substring(8, 10).all(Char::isDigit)

private fun String.profileMonthNumber(): Int? =
    when (trim().lowercase().take(3)) {
        "jan", "oca" -> 1
        "feb", "şub", "sub" -> 2
        "mar" -> 3
        "apr", "nis" -> 4
        "may", "mai" -> 5
        "jun", "haz" -> 6
        "jul", "tem" -> 7
        "aug", "ağu", "agu" -> 8
        "sep", "eyl" -> 9
        "oct", "eki" -> 10
        "nov", "kas" -> 11
        "dec", "ara" -> 12
        else -> null
    }

private fun profileDaysInMonth(year: Int, month: Int): Int =
    when (month) {
        1, 3, 5, 7, 8, 10, 12 -> 31
        4, 6, 9, 11 -> 30
        2 -> if ((year % 4 == 0 && year % 100 != 0) || year % 400 == 0) 29 else 28
        else -> 31
    }

private fun String.profileNormalizedType(): String? =
    when (trim().lowercase()) {
        "movie", "film" -> "movie"
        "series", "show", "tv", "tvshow", "anime" -> "series"
        "" -> null
        else -> trim().lowercase()
    }

private fun String.profileCompletedContentKind(): String? =
    when (trim().lowercase()) {
        "live-tv", "livetv", "live_tv", "channel", "tv-channel", "tv_channel", "iptv", "m3u", "stalker" -> null
        "movie", "film" -> "movie"
        "series", "show", "tv", "tvshow", "anime" -> "series"
        else -> null
    }

private fun LibraryItem.isProfileInsightContent(): Boolean =
    type.profileCompletedContentKind() != null &&
        !id.isLikelyProfileLiveTvValue() &&
        !name.isLikelyProfileLiveTvValue()

private fun WatchedItem.isProfileInsightContent(): Boolean =
    type.profileCompletedContentKind() != null &&
        !id.isLikelyProfileLiveTvValue() &&
        !name.isLikelyProfileLiveTvValue()

private fun String.isLikelyProfileLiveTvValue(): Boolean {
    val value = trim().lowercase()
    return value.startsWith("http://") ||
        value.startsWith("https://") ||
        value.startsWith("rtmp://") ||
        value.startsWith("rtsp://") ||
        value.endsWith(".m3u") ||
        value.endsWith(".m3u8")
}

private fun List<String>.profileMostCommonValue(): String? =
    filter { value -> value.isNotBlank() }
        .groupingBy { value -> value }
        .eachCount()
        .maxByOrNull { (_, count) -> count }
        ?.key

private fun String.fallbackDisplayLabel(): String {
    val clean = trim()
    if (clean.isBlank()) return clean
    return clean.replaceFirstChar { char ->
        if (char.isLowerCase()) char.titlecase() else char.toString()
    }
}

private data class ProfileInsightsStats(
    val continueCount: Int,
    val completedCount: Int,
    val ongoingSeriesCount: Int,
    val libraryCount: Int,
    val trackedDurationMs: Long,
    val recentActivityCount: Int,
    val upcomingCount: Int,
    val watchedMovieCount: Int = 0,
    val episodesWatchedCount: Int = 0,
    val topGenre: String?,
    val topType: String?,
    val tasteSegments: List<ProfileTasteSegment>,
    val movieShare: Float,
    val movieWatchTimeShare: Float? = null,
    val typeBalanceLabel: ProfileTasteBalanceLabel,
    val dnaChips: List<ProfileTasteDnaChip>,
)


private const val PROFILE_UPCOMING_EPISODE_DAYS = 7

private fun LibraryUpcomingEpisode.toProfileInsightPosterItem(): ProfileInsightPosterItem =
    ProfileInsightPosterItem(
        id = "upcoming:$key",
        title = item.name.trim().takeIf { it.isNotBlank() } ?: item.id,
        secondaryText = subtitle,
        releaseInfo = dateIso,
        imageUrl = item.poster ?: imageUrl ?: item.banner,
        lookupType = item.type,
        lookupId = item.id,
    )

private enum class ProfileInsightCollectionKind {
    Continue,
    Watched,
    Completed,
    Ongoing,
    Library,
    Upcoming,
}

private data class ProfileInsightCollection(
    val title: String,
    val subtitle: String,
    val items: List<ProfileInsightPosterItem>,
)

private data class ProfileInsightPosterItem(
    val id: String,
    val title: String,
    val secondaryText: String? = null,
    val releaseInfo: String? = null,
    val imageUrl: String?,
    val lookupType: String? = null,
    val lookupId: String? = null,
)

private data class ProfileCompletedContentItem(
    val id: String,
    val kind: String,
    val title: String,
    val releaseInfo: String?,
    val imageUrl: String?,
    val markedAtEpochMs: Long,
)

private data class ProfileTasteSegment(
    val label: String,
    val share: Float,
)

private enum class ProfileTasteBalanceLabel {
    Learning,
    MovieLeaning,
    SeriesLeaning,
    Balanced,
}

private enum class ProfileTasteDnaChip {
    Learning,
    MovieLeaning,
    SeriesLeaning,
    Balanced,
    BingeReady,
    HighActivity,
    Collector,
    RadarWatcher,
    Completionist,
}

@Composable
private fun ProfileTasteBalanceLabel.localizedLabel(): String =
    when (this) {
        ProfileTasteBalanceLabel.Learning -> stringResource(Res.string.profile_insights_taste_balance_learning)
        ProfileTasteBalanceLabel.MovieLeaning -> stringResource(Res.string.profile_insights_taste_balance_movie)
        ProfileTasteBalanceLabel.SeriesLeaning -> stringResource(Res.string.profile_insights_taste_balance_series)
        ProfileTasteBalanceLabel.Balanced -> stringResource(Res.string.profile_insights_taste_balance_balanced)
    }

@Composable
private fun ProfileTasteDnaChip.localizedLabel(): String =
    when (this) {
        ProfileTasteDnaChip.Learning -> stringResource(Res.string.profile_insights_taste_chip_learning)
        ProfileTasteDnaChip.MovieLeaning -> stringResource(Res.string.profile_insights_taste_chip_movie)
        ProfileTasteDnaChip.SeriesLeaning -> stringResource(Res.string.profile_insights_taste_chip_series)
        ProfileTasteDnaChip.Balanced -> stringResource(Res.string.profile_insights_taste_chip_balanced)
        ProfileTasteDnaChip.BingeReady -> stringResource(Res.string.profile_insights_taste_chip_binge)
        ProfileTasteDnaChip.HighActivity -> stringResource(Res.string.profile_insights_taste_chip_active)
        ProfileTasteDnaChip.Collector -> stringResource(Res.string.profile_insights_taste_chip_collector)
        ProfileTasteDnaChip.RadarWatcher -> stringResource(Res.string.profile_insights_taste_chip_radar)
        ProfileTasteDnaChip.Completionist -> stringResource(Res.string.profile_insights_taste_chip_completionist)
    }

private const val ProfileInsightsMinuteMs = 60_000L
private const val ProfileInsightsRecentWindowMs = 7L * 24L * 60L * 60L * 1000L

private const val ProfileInsightsFallbackMovieMinutes = 115L
private const val ProfileInsightsFallbackEpisodeMinutes = 42L
private val profileHourTokenRegex = Regex("""(?i)(\d+)\s*h(?:ours?)?""")
private val profileMinuteTokenRegex = Regex("""(?i)(\d+)\s*m(?:in(?:ute)?s?)?""")
private val profileHourMinuteColonRegex = Regex("""^\s*(\d+)\s*:\s*(\d{1,2})\s*$""")
private val profileDigitsOnlyRegex = Regex("""^\s*(\d+)\s*$""")
