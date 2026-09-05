package com.nuvio.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.DisintegrationRequest
import com.nuvio.app.core.ui.NativeTabBridge
import com.nuvio.app.core.ui.NuvioAsyncImage
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.cloud.CloudLibraryContentType
import com.nuvio.app.features.cloud.CloudLibraryFile
import com.nuvio.app.features.cloud.CloudLibraryItem
import com.nuvio.app.features.home.HomeCatalogSection
import com.nuvio.app.features.home.HomeScreen
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.library.LibraryItem
import com.nuvio.app.features.library.LibraryScreen
import com.nuvio.app.features.library.LibrarySection
import com.nuvio.app.features.library.LibrarySortOption
import com.nuvio.app.features.livetv.LiveTvChannel
import com.nuvio.app.features.livetv.LiveTvScreen
import com.nuvio.app.features.profiles.AvatarRepository
import com.nuvio.app.features.profiles.NuvioProfile
import com.nuvio.app.features.profiles.ProfileBackgroundBackdrop
import com.nuvio.app.features.profiles.ProfileSwitcherTab
import com.nuvio.app.features.profiles.parseHexColor
import com.nuvio.app.features.profiles.profileAvatarImageUrl
import com.nuvio.app.features.search.SearchScreen
import com.nuvio.app.features.settings.AppBrandWordmark
import com.nuvio.app.features.settings.SettingsScreen
import com.nuvio.app.features.watchprogress.ContinueWatchingItem
import com.nuvio.app.navigation.AppRoute
import com.nuvio.app.navigation.NuvioNavigator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.app_brand_name
import nuvio.composeapp.generated.resources.compose_nav_home
import nuvio.composeapp.generated.resources.compose_nav_library
import nuvio.composeapp.generated.resources.compose_nav_live_tv
import nuvio.composeapp.generated.resources.compose_nav_profile
import nuvio.composeapp.generated.resources.compose_nav_search
import nuvio.composeapp.generated.resources.sidebar_library
import nuvio.composeapp.generated.resources.sidebar_search
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun rememberGuardedPopBackStack(
    navController: NuvioNavigator,
    route: AppRoute,
    beforePop: () -> Unit = {},
): () -> Unit {
    var popHandled by remember(route) { mutableStateOf(false) }

    return remember(navController, route, popHandled, beforePop) {
        {
            if (!popHandled && navController.currentRoute == route) {
                popHandled = true
                beforePop()
                navController.popBackStack(expectedRoute = route)
            }
        }
    }
}

internal data class AppTabState(
    val searchListState: LazyListState,
    val homeContentGeneration: Int = 0,
    val searchFocusRequestCount: Int = 0,
    val rootActionsEnabled: Boolean = true,
    val animateHomeCollectionGifs: Boolean = true,
    val libraryDisintegrationRequest: DisintegrationRequest<String>? = null,
    val continueWatchingDisintegrationRequest: DisintegrationRequest<String>? = null,
    val requestedSettingsPageName: String? = null,
)

internal data class AppTabRequests(
    val homeScrollToTopRequests: Flow<Unit>,
    val searchScrollToTopRequests: Flow<Unit>,
    val libraryScrollToTopRequests: Flow<Unit>,
    val liveTvScrollToTopRequests: Flow<Unit>,
    val settingsRootActionRequests: Flow<Unit>,
)

internal data class AppTabActions(
    val onCatalogClick: ((HomeCatalogSection) -> Unit)? = null,
    val onPosterClick: ((MetaPreview) -> Unit)? = null,
    val onPosterLongClick: ((MetaPreview) -> Unit)? = null,
    val onLibraryPosterClick: ((LibraryItem) -> Unit)? = null,
    val onLibraryPosterLongClick: ((LibraryItem, LibrarySection) -> Unit)? = null,
    val onLibrarySectionViewAllClick: ((LibrarySection, LibrarySortOption) -> Unit)? = null,
    val onCloudFilePlay: ((CloudLibraryItem, CloudLibraryFile) -> Unit)? = null,
    val onConnectCloudClick: (() -> Unit)? = null,
    val onContinueWatchingClick: ((ContinueWatchingItem) -> Unit)? = null,
    val onContinueWatchingLongPress: ((ContinueWatchingItem) -> Unit)? = null,
    val onLiveTvChannelClick: (LiveTvChannel) -> Unit = {},
    val onSwitchProfile: (() -> Unit)? = null,
    val onEditProfile: (() -> Unit)? = null,
    val onSettingsPageClick: ((pageName: String, title: String) -> Unit)? = null,
    val onHomescreenSettingsClick: () -> Unit = {},
    val onMetaScreenSettingsClick: () -> Unit = {},
    val onContinueWatchingSettingsClick: () -> Unit = {},
    val onDownloadsSettingsClick: () -> Unit = {},
    val onAddonsSettingsClick: () -> Unit = {},
    val onPluginsSettingsClick: () -> Unit = {},
    val onAccountSettingsClick: () -> Unit = {},
    val onSupportersContributorsSettingsClick: () -> Unit = {},
    val onLicensesAttributionsSettingsClick: () -> Unit = {},
    val onCheckForUpdatesClick: (() -> Unit)? = null,
    val onTestUpdateBannerClick: (() -> Unit)? = null,
    val onCollectionsSettingsClick: () -> Unit = {},
    val onFolderClick: ((collectionId: String, folderId: String) -> Unit)? = null,
    val onRequestedSettingsPageConsumed: () -> Unit = {},
    val onInitialHomeContentRendered: () -> Unit = {},
)

@Composable
internal fun AppTabHost(
    selectedTab: AppScreenTab,
    requests: AppTabRequests,
    state: AppTabState,
    actions: AppTabActions,
    modifier: Modifier = Modifier,
) {
    val tabStateHolder = rememberSaveableStateHolder()

    Box(modifier = modifier.fillMaxSize()) {
        tabStateHolder.SaveableStateProvider(selectedTab.name) {
            when (selectedTab) {
                AppScreenTab.Home -> {
                    key(state.homeContentGeneration) {
                        HomeScreen(
                            modifier = Modifier.fillMaxSize(),
                            animateCollectionGifs = state.animateHomeCollectionGifs,
                            scrollToTopRequests = requests.homeScrollToTopRequests,
                            onCatalogClick = actions.onCatalogClick,
                            onPosterClick = actions.onPosterClick,
                            onPosterLongClick = actions.onPosterLongClick,
                            onContinueWatchingClick = actions.onContinueWatchingClick,
                            onContinueWatchingLongPress = actions.onContinueWatchingLongPress,
                            continueWatchingDisintegrationRequest = state.continueWatchingDisintegrationRequest,
                            onFolderClick = actions.onFolderClick,
                            onFirstCatalogRendered = actions.onInitialHomeContentRendered,
                        )
                    }
                }

                AppScreenTab.Search -> {
                    SearchScreen(
                        modifier = Modifier.fillMaxSize(),
                        listState = state.searchListState,
                        onPosterClick = actions.onPosterClick,
                        onPosterLongClick = actions.onPosterLongClick,
                        searchFocusRequestCount = state.searchFocusRequestCount,
                        scrollToTopRequests = requests.searchScrollToTopRequests,
                    )
                }

                AppScreenTab.Library -> {
                    LibraryScreen(
                        modifier = Modifier.fillMaxSize(),
                        scrollToTopRequests = requests.libraryScrollToTopRequests,
                        onPosterClick = actions.onLibraryPosterClick,
                        onPosterLongClick = actions.onLibraryPosterLongClick,
                        onSectionViewAllClick = actions.onLibrarySectionViewAllClick,
                        onCloudFilePlay = actions.onCloudFilePlay,
                        onConnectCloudClick = actions.onConnectCloudClick,
                        disintegrationRequest = state.libraryDisintegrationRequest,
                    )
                }

                AppScreenTab.LiveTv -> {
                    LiveTvScreen(
                        scrollToTopRequests = requests.liveTvScrollToTopRequests,
                        onChannelClick = actions.onLiveTvChannelClick,
                    )
                }

                AppScreenTab.Settings -> {
                    SettingsScreen(
                        modifier = Modifier.fillMaxSize(),
                        rootActionRequests = requests.settingsRootActionRequests,
                        requestedPageName = state.requestedSettingsPageName,
                        onRequestedPageConsumed = actions.onRequestedSettingsPageConsumed,
                        rootActionsEnabled = state.rootActionsEnabled,
                        onNavigatePage = actions.onSettingsPageClick,
                        onSwitchProfile = actions.onSwitchProfile,
                        onEditProfile = actions.onEditProfile,
                        onPosterClick = actions.onPosterClick,
                        onHomescreenClick = actions.onHomescreenSettingsClick,
                        onMetaScreenClick = actions.onMetaScreenSettingsClick,
                        onContinueWatchingClick = actions.onContinueWatchingSettingsClick,
                        onDownloadsClick = actions.onDownloadsSettingsClick,
                        onAddonsClick = actions.onAddonsSettingsClick,
                        onPluginsClick = actions.onPluginsSettingsClick,
                        onAccountClick = actions.onAccountSettingsClick,
                        onSupportersContributorsClick = actions.onSupportersContributorsSettingsClick,
                        onLicensesAttributionsClick = actions.onLicensesAttributionsSettingsClick,
                        onCheckForUpdatesClick = actions.onCheckForUpdatesClick,
                        onTestUpdateBannerClick = actions.onTestUpdateBannerClick,
                        onCollectionsClick = actions.onCollectionsSettingsClick,
                    )
                }
            }
        }
    }
}

@Composable
internal fun TabletFloatingTopBar(
    selectedTab: AppScreenTab,
    showLiveTv: Boolean,
    onTabSelected: (AppScreenTab) -> Unit,
    onProfileSelected: (NuvioProfile) -> Unit,
    onAddProfileRequested: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = MaterialTheme.nuvio
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = statusBarPadding + NuvioTokens.Space.s10, bottom = tokens.spacing.controlGap),
        contentAlignment = Alignment.TopCenter,
    ) {
        Surface(
            color = tokens.colors.surface.copy(alpha = tokens.opacity.visible - tokens.opacity.subtle),
            shape = tokens.shapes.chip,
            tonalElevation = tokens.elevation.playerControls,
            shadowElevation = tokens.elevation.overlay,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = NuvioTokens.Space.s10, vertical = tokens.spacing.controlGap),
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.controlGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TabletTopPillItem(
                    label = stringResource(Res.string.compose_nav_home),
                    selected = selectedTab == AppScreenTab.Home,
                    onClick = { onTabSelected(AppScreenTab.Home) },
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Home,
                            contentDescription = stringResource(Res.string.compose_nav_home),
                            modifier = Modifier.size(NuvioTokens.Space.s18),
                            tint = if (selectedTab == AppScreenTab.Home) {
                                tokens.colors.textPrimary
                            } else {
                                tokens.colors.textMuted
                            },
                        )
                    },
                )
                TabletTopPillItem(
                    label = stringResource(Res.string.compose_nav_search),
                    selected = selectedTab == AppScreenTab.Search,
                    onClick = { onTabSelected(AppScreenTab.Search) },
                    icon = {
                        Icon(
                            painter = painterResource(Res.drawable.sidebar_search),
                            contentDescription = stringResource(Res.string.compose_nav_search),
                            modifier = Modifier.size(NuvioTokens.Space.s18),
                            tint = if (selectedTab == AppScreenTab.Search) {
                                tokens.colors.textPrimary
                            } else {
                                tokens.colors.textMuted
                            },
                        )
                    },
                )
                TabletTopPillItem(
                    label = stringResource(Res.string.compose_nav_library),
                    selected = selectedTab == AppScreenTab.Library,
                    onClick = { onTabSelected(AppScreenTab.Library) },
                    icon = {
                        Icon(
                            painter = painterResource(Res.drawable.sidebar_library),
                            contentDescription = stringResource(Res.string.compose_nav_library),
                            modifier = Modifier.size(NuvioTokens.Space.s18),
                            tint = if (selectedTab == AppScreenTab.Library) {
                                tokens.colors.textPrimary
                            } else {
                                tokens.colors.textMuted
                            },
                        )
                    },
                )
                if (showLiveTv) {
                    TabletTopPillItem(
                        label = stringResource(Res.string.compose_nav_live_tv),
                        selected = selectedTab == AppScreenTab.LiveTv,
                        onClick = { onTabSelected(AppScreenTab.LiveTv) },
                        icon = {
                            Icon(
                                imageVector = Icons.Filled.Tv,
                                contentDescription = stringResource(Res.string.compose_nav_live_tv),
                                modifier = Modifier.size(18.dp),
                                tint = if (selectedTab == AppScreenTab.LiveTv) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        },
                    )
                }
                Surface(
                    color = if (selectedTab == AppScreenTab.Settings) {
                        tokens.colors.overlaySelected
                    } else {
                        tokens.colors.surface
                    },
                    shape = tokens.shapes.chip,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = tokens.spacing.listGap, vertical = tokens.spacing.controlGap),
                        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.controlGap),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ProfileSwitcherTab(
                            selected = selectedTab == AppScreenTab.Settings,
                            onClick = { onTabSelected(AppScreenTab.Settings) },
                            onProfileSelected = onProfileSelected,
                            onAddProfileRequested = onAddProfileRequested,
                        )
                        Text(
                            text = stringResource(Res.string.compose_nav_profile),
                            modifier = Modifier.clickable { onTabSelected(AppScreenTab.Settings) },
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selectedTab == AppScreenTab.Settings) {
                                tokens.colors.textPrimary
                            } else {
                                tokens.colors.textMuted
                            },
                        )
                    }
                }
            }
        }
    }
}

internal fun ContinueWatchingItem.isCloudLibraryContinueWatchingItem(): Boolean =
    parentMetaType.equals(CloudLibraryContentType, ignoreCase = true)

@Composable
private fun TabletTopPillItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    Surface(
        color = if (selected) tokens.colors.overlaySelected else tokens.colors.surface,
        shape = tokens.shapes.chip,
        tonalElevation = if (selected) tokens.elevation.raised else tokens.elevation.flat,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = tokens.components.chipHorizontalPadding, vertical = NuvioTokens.Space.s10),
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.controlGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon()
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) {
                    tokens.colors.textPrimary
                } else {
                    tokens.colors.textMuted
                },
            )
        }
    }
}

@Composable
internal fun AppLoadingContent(
    modifier: Modifier = Modifier,
    profile: NuvioProfile? = null,
    entryOrigin: Offset? = null,
    exitTowardProfileTab: Boolean = false,
    onExitFinished: () -> Unit = {},
) {
    val tokens = MaterialTheme.nuvio
    // Netflix-style profile-select entrance, paced over a couple of seconds rather than a quick
    // snap: the emblem drifts in from oversized-and-lifted, settles with a slow, pronounced
    // bounce, and a soft "impact" ring pulses outward the moment it lands.
    val emblemScale = remember { Animatable(1.7f) }
    val emblemAlpha = remember { Animatable(0f) }
    val emblemOffsetX = remember { Animatable(0f) }
    val emblemOffsetY = remember { Animatable(-36f) }
    val haloAlpha = remember { Animatable(0f) }
    val ringScale = remember { Animatable(0.7f) }
    val ringAlpha = remember { Animatable(0f) }
    val density = LocalDensity.current
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    val profileTabIconFrame by NativeTabBridge.profileTabIconFrame.collectAsStateWithLifecycle()

    suspend fun CoroutineScope.pulseRing() {
        ringScale.snapTo(0.7f)
        ringAlpha.snapTo(0.45f)
        launch { ringScale.animateTo(1.9f, animationSpec = tween(900, easing = LinearOutSlowInEasing)) }
        ringAlpha.animateTo(0f, animationSpec = tween(900))
    }

    LaunchedEffect(entryOrigin) {
        if (entryOrigin != null) {
            // Tap-to-center transition: this emblem *is* the avatar the user just tapped —
            // already fully on-screen at that spot — so it glides straight into the center
            // instead of fading in out of nowhere the way the no-origin case below does. Waits
            // for the container's real size before computing how far that glide needs to travel
            // (mirrors the math `exitTowardProfileTab` below uses, just in reverse).
            val size = snapshotFlow { containerSize }.first { it != IntSize.Zero }
            val startX = entryOrigin.x - size.width / 2f
            val startY = entryOrigin.y - size.height / 2f
            emblemAlpha.snapTo(1f)
            emblemScale.snapTo(1f)
            emblemOffsetX.snapTo(startX)
            emblemOffsetY.snapTo(startY)
            val travelDuration = 550
            launch { emblemOffsetX.animateTo(0f, animationSpec = tween(travelDuration, easing = FastOutSlowInEasing)) }
            launch { haloAlpha.animateTo(1f, animationSpec = tween(260, delayMillis = travelDuration - 160, easing = FastOutSlowInEasing)) }
            emblemOffsetY.animateTo(0f, animationSpec = tween(travelDuration, easing = FastOutSlowInEasing))
            pulseRing()
        } else {
            launch { emblemAlpha.animateTo(1f, animationSpec = tween(220, easing = FastOutSlowInEasing)) }
            launch { haloAlpha.animateTo(1f, animationSpec = tween(420, easing = FastOutSlowInEasing)) }
            // A snappier spring than before (higher stiffness) so the bounce actually settles
            // inside the much shorter ~1s minimum display window instead of getting cut off
            // mid-motion.
            launch {
                emblemOffsetY.animateTo(
                    0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium,
                    ),
                )
            }
            launch {
                emblemScale.animateTo(
                    1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium,
                    ),
                )
            }
            pulseRing()
        }
    }

    LaunchedEffect(exitTowardProfileTab) {
        if (!exitTowardProfileTab) return@LaunchedEffect
        // Prefer the real Profile tab icon's on-screen frame, pushed live from Swift (see
        // NativeTabBridge.publishProfileTabIconFrame) — lands pixel-perfect on the actual icon.
        // Falls back to an approximated bottom-right corner if that frame isn't known yet (e.g.
        // this ran before Swift ever attached the tab bar coordinator).
        val frame = profileTabIconFrame
        val targetX: Float
        val targetY: Float
        if (frame != null) {
            val iconCenterXPx = with(density) { (frame.xDp + frame.widthDp / 2f).dp.toPx() }
            val iconCenterYPx = with(density) { (frame.yDp + frame.heightDp / 2f).dp.toPx() }
            targetX = iconCenterXPx - containerSize.width / 2f
            targetY = iconCenterYPx - containerSize.height / 2f
        } else {
            val marginPx = with(density) { 24.dp.toPx() }
            targetX = (containerSize.width / 2f - marginPx).coerceAtLeast(0f)
            targetY = (containerSize.height / 2f - marginPx).coerceAtLeast(0f)
        }
        // The tab bar is already visible underneath by this point, so the emblem stays fully
        // opaque for almost the whole trip, only dissolving right at the very end — reading as the
        // icon shrinking down and merging into the real one, not just fading away. Kept brisk
        // (rather than the ~900ms this used to run) so the overlay doesn't overstay once content
        // is ready — still long enough at 550ms for that shrink-and-merge motion to read clearly.
        val travelDuration = 550
        launch { haloAlpha.animateTo(0f, animationSpec = tween(220)) }
        launch { ringAlpha.animateTo(0f, animationSpec = tween(200)) }
        launch {
            emblemOffsetX.animateTo(targetX, animationSpec = tween(travelDuration, easing = FastOutSlowInEasing))
        }
        launch {
            emblemScale.animateTo(0.22f, animationSpec = tween(travelDuration, easing = FastOutSlowInEasing))
        }
        launch {
            emblemAlpha.animateTo(0f, animationSpec = tween(180, delayMillis = travelDuration - 180))
        }
        emblemOffsetY.animateTo(targetY, animationSpec = tween(travelDuration, easing = FastOutSlowInEasing))
        onExitFinished()
    }

    Box(
        modifier = modifier.onSizeChanged { containerSize = it },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(150.dp)
                        .graphicsLayer { alpha = haloAlpha.value }
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    tokens.colors.accent.copy(alpha = 0.28f),
                                    tokens.colors.accent.copy(alpha = 0f),
                                ),
                            ),
                            CircleShape,
                        ),
                )
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .graphicsLayer {
                            scaleX = ringScale.value
                            scaleY = ringScale.value
                            alpha = ringAlpha.value
                        }
                        .border(1.5.dp, tokens.colors.accent, CircleShape),
                )
                val emblemModifier = Modifier
                    .graphicsLayer {
                        scaleX = emblemScale.value
                        scaleY = emblemScale.value
                        alpha = emblemAlpha.value
                        translationX = emblemOffsetX.value
                        translationY = emblemOffsetY.value
                    }
                if (profile != null) {
                    AppLoadingProfileAvatar(
                        profile = profile,
                        modifier = emblemModifier.size(96.dp),
                    )
                } else {
                    AppBrandWordmark(
                        contentDescription = stringResource(Res.string.app_brand_name),
                        modifier = emblemModifier
                            .width(160.dp)
                            .height(44.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AppLoadingProfileAvatar(
    profile: NuvioProfile,
    modifier: Modifier = Modifier,
) {
    val avatarColor = remember(profile.avatarColorHex) { parseHexColor(profile.avatarColorHex) }
    val avatars by remember { AvatarRepository.avatars }.collectAsStateWithLifecycle()
    val avatarItem = remember(profile.avatarId, avatars) {
        profile.avatarId?.let { id -> avatars.find { it.id == id } }
    }
    val avatarImageUrl = remember(profile.avatarUrl, avatarItem) {
        profileAvatarImageUrl(profile, avatarItem)
    }
    val backgroundColor = avatarItem?.bgColor?.let(::parseHexColor) ?: avatarColor

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(if (avatarImageUrl != null) backgroundColor else backgroundColor.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        when {
            avatarImageUrl != null -> NuvioAsyncImage(
                imageUrl = avatarImageUrl,
                contentDescription = avatarItem?.displayName ?: profile.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                // Keep an animated avatar (GIF) playing through the loading transition instead of
                // freezing on its first frame.
                animateIfPossible = true,
            )
            profile.name.isNotBlank() -> Text(
                text = profile.name.take(1).uppercase(),
                style = MaterialTheme.typography.headlineLarge,
                color = avatarColor,
                fontWeight = FontWeight.Bold,
            )
            else -> Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = null,
                tint = avatarColor,
                modifier = Modifier.size(40.dp),
            )
        }
    }
}

@Composable
internal fun AppLaunchOverlay(
    profile: NuvioProfile?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.zIndex(NuvioTokens.Z.dialog),
    ) {
        ProfileBackgroundBackdrop(
            profile = profile,
            modifier = Modifier.fillMaxSize(),
        )
        AppLoadingContent(modifier = Modifier.fillMaxSize(), profile = profile)
    }
}
