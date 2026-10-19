package com.nuvio.app

import androidx.compose.runtime.collectAsState
import com.nuvio.app.core.ui.NativeTabBridge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.LocalNuvioBottomNavigationOverlayPadding
import com.nuvio.app.core.ui.FloatingNavigationBar
import com.nuvio.app.core.ui.FloatingNavigationItem
import com.nuvio.app.core.ui.LocalNuvioNavBarScrollState
import com.nuvio.app.core.ui.NuvioClassicNavigationBar
import com.nuvio.app.core.ui.PlatformBackHandler
import com.nuvio.app.core.ui.LocalNuvioTabletNavLayout
import com.nuvio.app.core.ui.floatingNavigationBarPadding
import com.nuvio.app.features.settings.NavBarPosition
import com.nuvio.app.core.ui.rememberNuvioNavBarScrollState
import com.nuvio.app.features.profiles.NuvioProfile
import com.nuvio.app.features.profiles.ProfileSwitcherTab
import com.nuvio.app.features.settings.NavBarStyle
import com.nuvio.app.features.settings.ThemeSettingsRepository
import com.kyant.backdrop.Backdrop
import com.nuvio.app.core.ui.glass.nuvioBackdropSource
import com.nuvio.app.core.ui.glass.rememberNuvioBackdrop
import com.nuvio.app.core.ui.glass.LiquidGlassEffectDefaults
import com.nuvio.app.core.ui.glass.LiquidGlassEffectSettings
import com.nuvio.app.core.ui.glass.LiquidGlassStyle
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.compose_nav_home
import nuvio.composeapp.generated.resources.compose_nav_library
import nuvio.composeapp.generated.resources.compose_nav_live_tv
import nuvio.composeapp.generated.resources.compose_nav_profile
import nuvio.composeapp.generated.resources.compose_nav_search
import nuvio.composeapp.generated.resources.sidebar_library
import nuvio.composeapp.generated.resources.sidebar_search
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp

@Composable
internal fun MainTabsDestination(
    selectedTab: AppScreenTab,
    initialHomeReady: Boolean,
    rootRouteActive: Boolean,
    useTabletFloatingTabBar: Boolean,
    useNativeNavigation: Boolean,
    useNativeTabBar: Boolean,
    liquidGlassNativeTabBarSupported: Boolean,
    liquidGlassNativeTabBarEnabled: Boolean,
    showLiveTvInNavigation: Boolean,
    requests: AppTabRequests,
    state: AppTabState,
    actions: (isTabletLayout: Boolean) -> AppTabActions,
    onBack: () -> Unit,
    onTabSelected: (AppScreenTab) -> Unit,
    onProfileSelected: (NuvioProfile) -> Unit,
    onAddProfileRequested: () -> Unit,
) {
    PlatformBackHandler(enabled = true, onBack = onBack)

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isTabletLayout = useTabletFloatingTabBar || maxWidth >= 768.dp
        val nativeActiveTab by NativeTabBridge.activeTab.collectAsState()
        val highlightedTab = if (useNativeNavigation) nativeActiveTab.toAppScreenTab() else selectedTab
        val tabActions = remember(actions, isTabletLayout) { actions(isTabletLayout) }
        val useNativeBottomTabs = if (useNativeNavigation) {
            useNativeTabBar
        } else {
            liquidGlassNativeTabBarSupported && liquidGlassNativeTabBarEnabled && initialHomeReady
        }
        val tabsRouteActive = rootRouteActive
        val navBarScrollState = rememberNuvioNavBarScrollState()
        val navBarStyleSetting by remember { ThemeSettingsRepository.navBarStyle }.collectAsStateWithLifecycle()
        val navBarGlowEnabled by ThemeSettingsRepository.navBarGlowEnabled.collectAsStateWithLifecycle()
        val navBarPosition by ThemeSettingsRepository.navBarPosition.collectAsStateWithLifecycle()
        val navBarGlass by ThemeSettingsRepository.navBarGlass.collectAsStateWithLifecycle()
        val navBarDefaultIconColorEnabled by ThemeSettingsRepository.navBarDefaultIconColorEnabled.collectAsStateWithLifecycle()
        val navBarDefaultIconColor by ThemeSettingsRepository.navBarDefaultIconColor.collectAsStateWithLifecycle()
        val navBarDefaultIconColorOverride = if (navBarDefaultIconColorEnabled) {
            Color(navBarDefaultIconColor or 0xFF000000.toInt())
        } else {
            null
        }
        val floatingBarOnTop = navBarStyleSetting != NavBarStyle.CLASSIC && navBarPosition == NavBarPosition.TOP
        // The backdrop source is only consumed by the custom floating bar, not native or CLASSIC navigation.
        val floatingBarVisible = tabsRouteActive && !useNativeBottomTabs && navBarStyleSetting != NavBarStyle.CLASSIC
        val navBarBackdrop = if (floatingBarVisible) rememberNuvioBackdrop() else null
        val floatingNavigationItems = mainFloatingNavigationItems(
            highlightedTab = highlightedTab,
            showLiveTv = showLiveTvInNavigation,
            onTabSelected = onTabSelected,
            onProfileSelected = onProfileSelected,
            onAddProfileRequested = onAddProfileRequested,
            backdrop = navBarBackdrop,
            defaultIconColorOverride = navBarDefaultIconColorOverride,
        )

        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .alpha(if (initialHomeReady) 1f else 0f),
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0),
            bottomBar = {
                if (tabsRouteActive && !isTabletLayout && !useNativeBottomTabs && navBarStyleSetting == NavBarStyle.CLASSIC) {
                    MainClassicNavigationBar(
                        highlightedTab = highlightedTab,
                        showLiveTv = showLiveTvInNavigation,
                        onTabSelected = onTabSelected,
                        onProfileSelected = onProfileSelected,
                        onAddProfileRequested = onAddProfileRequested,
                    )
                }
            },
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize()) {
                CompositionLocalProvider(
                    LocalNuvioBottomNavigationOverlayPadding provides when {
                        tabsRouteActive && useNativeBottomTabs -> 49.dp
                        // A top pill floats over the content like the tablet CLASSIC bar, so
                        // nothing needs to be kept clear at the bottom.
                        tabsRouteActive && navBarStyleSetting != NavBarStyle.CLASSIC && !floatingBarOnTop -> 72.dp
                        else -> 0.dp
                    },
                    LocalNuvioNavBarScrollState provides navBarScrollState,
                    LocalNuvioTabletNavLayout provides isTabletLayout,
                ) {
                    AppTabHost(
                        selectedTab = selectedTab,
                        requests = requests,
                        state = state,
                        actions = tabActions,
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                if (floatingBarVisible) {
                                    navBarBackdrop?.let { Modifier.nuvioBackdropSource(it) } ?: Modifier
                                } else {
                                    Modifier
                                },
                            )
                            .then(if (tabsRouteActive && navBarStyleSetting == NavBarStyle.ADAPTIVE) Modifier.nestedScroll(navBarScrollState.nestedScrollConnection) else Modifier)
                            .padding(innerPadding),
                    )
                }

                // CLASSIC keeps the previous per-form-factor chrome: a top rail on
                // tablet/landscape and the solid bottom bar on phones. Every other
                // style now uses the floating pill at the bottom on all sizes.
                if (isTabletLayout && !useNativeBottomTabs && navBarStyleSetting == NavBarStyle.CLASSIC) {
                    TabletFloatingTopBar(
                        selectedTab = highlightedTab,
                        showLiveTv = showLiveTvInNavigation,
                        onTabSelected = onTabSelected,
                        onProfileSelected = onProfileSelected,
                        onAddProfileRequested = onAddProfileRequested,
                    )
                }

                if (floatingBarVisible) {
                    when (navBarStyleSetting) {
                        NavBarStyle.EXPANDED -> navBarScrollState.expand()
                        NavBarStyle.COMPACT -> navBarScrollState.collapse()
                        else -> {}
                    }
                    MainFloatingNavigationBar(
                        modifier = Modifier.align(if (floatingBarOnTop) Alignment.TopCenter else Alignment.BottomCenter),
                        scrollState = navBarScrollState,
                        backdrop = navBarBackdrop,
                        contentPadding = if (floatingBarOnTop) {
                            PaddingValues(
                                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 10.dp,
                                bottom = 8.dp,
                            )
                        } else {
                            floatingNavigationBarPadding()
                        },
                        items = floatingNavigationItems,
                        glowEnabled = navBarGlowEnabled,
                        inlineLabels = isTabletLayout,
                        glassStyle = navBarGlass,
                        defaultIconColorOverride = navBarDefaultIconColorOverride,
                    )
                }
            }
        }
    }
}

/**
 * Keep the high-frequency glass-effect draft flows subscribed in the navigation-bar leaf.
 * Dragging effect sliders then updates this bar without invalidating MainTabsDestination and its tab host.
 */
@Composable
private fun MainFloatingNavigationBar(
    modifier: Modifier,
    scrollState: com.nuvio.app.core.ui.NuvioNavBarScrollState?,
    backdrop: Backdrop?,
    contentPadding: PaddingValues,
    items: List<FloatingNavigationItem>,
    glowEnabled: Boolean,
    inlineLabels: Boolean,
    glassStyle: LiquidGlassStyle,
    defaultIconColorOverride: Color?,
) {
    val liquidGlassBarEffects by ThemeSettingsRepository.navBarLiquidGlassBarEffects.collectAsStateWithLifecycle()
    val liquidGlassSelectionEffects by ThemeSettingsRepository.navBarLiquidGlassSelectionEffects.collectAsStateWithLifecycle()

    FloatingNavigationBar(
        items = items,
        modifier = modifier,
        scrollState = scrollState,
        backdrop = backdrop,
        glassStyle = glassStyle,
        contentPadding = contentPadding,
        glowEnabled = glowEnabled,
        liquidGlassBarEffects = liquidGlassBarEffects,
        liquidGlassSelectionEffects = liquidGlassSelectionEffects,
        inlineLabels = inlineLabels,
        defaultIconColorOverride = defaultIconColorOverride,
    )
}

@Composable
internal fun mainFloatingNavigationItems(
    highlightedTab: AppScreenTab,
    showLiveTv: Boolean,
    onTabSelected: (AppScreenTab) -> Unit,
    onProfileSelected: (NuvioProfile) -> Unit,
    onAddProfileRequested: () -> Unit,
    backdrop: Backdrop?,
    defaultIconColorOverride: Color? = null,
): List<FloatingNavigationItem> = buildList {
    add(
        FloatingNavigationItem(
            selected = highlightedTab == AppScreenTab.Home,
            onClick = { onTabSelected(AppScreenTab.Home) },
            icon = Icons.Filled.Home,
            label = stringResource(Res.string.compose_nav_home),
        ),
    )
    add(
        FloatingNavigationItem(
            selected = highlightedTab == AppScreenTab.Search,
            onClick = { onTabSelected(AppScreenTab.Search) },
            drawable = Res.drawable.sidebar_search,
            label = stringResource(Res.string.compose_nav_search),
        ),
    )
    add(
        FloatingNavigationItem(
            selected = highlightedTab == AppScreenTab.Library,
            onClick = { onTabSelected(AppScreenTab.Library) },
            drawable = Res.drawable.sidebar_library,
            label = stringResource(Res.string.compose_nav_library),
        ),
    )
    if (showLiveTv) {
        add(
            FloatingNavigationItem(
                selected = highlightedTab == AppScreenTab.LiveTv,
                onClick = { onTabSelected(AppScreenTab.LiveTv) },
                icon = Icons.Filled.Tv,
                label = stringResource(Res.string.compose_nav_live_tv),
            ),
        )
    }
    add(
        FloatingNavigationItem(
            selected = highlightedTab == AppScreenTab.Settings,
            onClick = { onTabSelected(AppScreenTab.Settings) },
            label = stringResource(Res.string.compose_nav_profile),
            content = { onClick, iconColorOverride ->
                ProfileSwitcherTab(
                    selected = highlightedTab == AppScreenTab.Settings,
                    onClick = onClick,
                    onProfileSelected = onProfileSelected,
                    onAddProfileRequested = onAddProfileRequested,
                    backdrop = backdrop,
                    popupBelowAnchor = false,
                    defaultIconColorOverride = iconColorOverride ?: defaultIconColorOverride,
                )
            },
        ),
    )
}

@Composable
internal fun MainClassicNavigationBar(
    highlightedTab: AppScreenTab,
    showLiveTv: Boolean,
    onTabSelected: (AppScreenTab) -> Unit,
    onProfileSelected: (NuvioProfile) -> Unit,
    onAddProfileRequested: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NuvioClassicNavigationBar(modifier) {
        NavItem(
            selected = highlightedTab == AppScreenTab.Home,
            onClick = { onTabSelected(AppScreenTab.Home) },
            icon = Icons.Filled.Home,
            contentDescription = stringResource(Res.string.compose_nav_home),
        )
        NavItem(
            selected = highlightedTab == AppScreenTab.Search,
            onClick = { onTabSelected(AppScreenTab.Search) },
            icon = Res.drawable.sidebar_search,
            contentDescription = stringResource(Res.string.compose_nav_search),
        )
        NavItem(
            selected = highlightedTab == AppScreenTab.Library,
            onClick = { onTabSelected(AppScreenTab.Library) },
            icon = Res.drawable.sidebar_library,
            contentDescription = stringResource(Res.string.compose_nav_library),
        )
        if (showLiveTv) {
            NavItem(
                selected = highlightedTab == AppScreenTab.LiveTv,
                onClick = { onTabSelected(AppScreenTab.LiveTv) },
                icon = Icons.Filled.Tv,
                contentDescription = stringResource(Res.string.compose_nav_live_tv),
            )
        }
        NavItem(
            selected = highlightedTab == AppScreenTab.Settings,
            onClick = { onTabSelected(AppScreenTab.Settings) },
        ) {
            ProfileSwitcherTab(
                selected = highlightedTab == AppScreenTab.Settings,
                onClick = { onTabSelected(AppScreenTab.Settings) },
                onProfileSelected = onProfileSelected,
                onAddProfileRequested = onAddProfileRequested,
            )
        }
    }
}

@Composable
internal fun BoxScope.SettingsRouteNavigationBar(
    isTabletLayout: Boolean,
    showLiveTv: Boolean,
    backdrop: Backdrop?,
    onTabSelected: (AppScreenTab) -> Unit,
    onProfileSelected: (NuvioProfile) -> Unit,
    onAddProfileRequested: () -> Unit,
    onBottomOverlayChanged: (Dp) -> Unit,
) {
    val style by remember { ThemeSettingsRepository.navBarStyle }.collectAsStateWithLifecycle()
    val glowEnabled by ThemeSettingsRepository.navBarGlowEnabled.collectAsStateWithLifecycle()
    val position by ThemeSettingsRepository.navBarPosition.collectAsStateWithLifecycle()
    val glass by ThemeSettingsRepository.navBarGlass.collectAsStateWithLifecycle()
    val liquidGlassBarEffects by ThemeSettingsRepository.navBarLiquidGlassBarEffects.collectAsStateWithLifecycle()
    val liquidGlassSelectionEffects by ThemeSettingsRepository.navBarLiquidGlassSelectionEffects.collectAsStateWithLifecycle()
    val defaultIconColorEnabled by ThemeSettingsRepository.navBarDefaultIconColorEnabled.collectAsStateWithLifecycle()
    val defaultIconColor by ThemeSettingsRepository.navBarDefaultIconColor.collectAsStateWithLifecycle()
    val defaultIconColorOverride = if (defaultIconColorEnabled) {
        Color(defaultIconColor or 0xFF000000.toInt())
    } else {
        null
    }
    val density = LocalDensity.current
    val highlightedTab = AppScreenTab.Settings

    when {
        style == NavBarStyle.CLASSIC && isTabletLayout -> {
            SideEffect { onBottomOverlayChanged(0.dp) }
            TabletFloatingTopBar(
                selectedTab = highlightedTab,
                showLiveTv = showLiveTv,
                onTabSelected = onTabSelected,
                onProfileSelected = onProfileSelected,
                onAddProfileRequested = onAddProfileRequested,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
        style == NavBarStyle.CLASSIC -> {
            Box(
                Modifier.align(Alignment.BottomCenter).onSizeChanged {
                    onBottomOverlayChanged(with(density) { it.height.toDp() })
                },
            ) {
                MainClassicNavigationBar(
                    highlightedTab = highlightedTab,
                    showLiveTv = showLiveTv,
                    onTabSelected = onTabSelected,
                    onProfileSelected = onProfileSelected,
                    onAddProfileRequested = onAddProfileRequested,
                )
            }
        }
        else -> {
            val onTop = position == NavBarPosition.TOP
            SideEffect { onBottomOverlayChanged(if (onTop) 0.dp else 72.dp) }
            FloatingNavigationBar(
                items = mainFloatingNavigationItems(
                    highlightedTab = highlightedTab,
                    showLiveTv = showLiveTv,
                    onTabSelected = onTabSelected,
                    onProfileSelected = onProfileSelected,
                    onAddProfileRequested = onAddProfileRequested,
                    backdrop = backdrop,
                    defaultIconColorOverride = defaultIconColorOverride,
                ),
                modifier = Modifier.align(if (onTop) Alignment.TopCenter else Alignment.BottomCenter),
                scrollState = if (style == NavBarStyle.COMPACT) {
                    remember { com.nuvio.app.core.ui.NuvioNavBarScrollState().apply { collapse() } }
                } else {
                    null
                },
                backdrop = backdrop,
                contentPadding = if (onTop) {
                    PaddingValues(
                        top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 10.dp,
                        bottom = 8.dp,
                    )
                } else {
                    floatingNavigationBarPadding()
                },
                glowEnabled = glowEnabled,
                inlineLabels = isTabletLayout,
                glassStyle = glass,
                liquidGlassBarEffects = liquidGlassBarEffects,
                liquidGlassSelectionEffects = liquidGlassSelectionEffects,
                defaultIconColorOverride = defaultIconColorOverride,
            )
        }
    }
}
