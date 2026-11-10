package com.nuvio.app

import com.nuvio.app.core.ui.NativeNavigationTab

enum class AppScreenTab {
    Home,
    NewHot,
    Search,
    Library,
    LiveTv,
    Sports,
    Music,
    Following,
    News,
    Settings,
    ;

    companion object {
        fun fromName(name: String): AppScreenTab =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: Home
    }
}

internal fun AppScreenTab.toNativeNavigationTab(): NativeNavigationTab = when (this) {
    AppScreenTab.Home -> NativeNavigationTab.Home
    AppScreenTab.NewHot -> NativeNavigationTab.NewHot
    AppScreenTab.Search -> NativeNavigationTab.Search
    AppScreenTab.Library -> NativeNavigationTab.Library
    AppScreenTab.LiveTv -> NativeNavigationTab.LiveTv
    AppScreenTab.Sports -> NativeNavigationTab.Sports
    AppScreenTab.Music -> NativeNavigationTab.Music
    AppScreenTab.Following -> NativeNavigationTab.Following
    AppScreenTab.News -> NativeNavigationTab.News
    AppScreenTab.Settings -> NativeNavigationTab.Settings
}

internal fun NativeNavigationTab.toAppScreenTab(): AppScreenTab = when (this) {
    NativeNavigationTab.Home -> AppScreenTab.Home
    NativeNavigationTab.NewHot -> AppScreenTab.NewHot
    NativeNavigationTab.Search -> AppScreenTab.Search
    NativeNavigationTab.Library -> AppScreenTab.Library
    NativeNavigationTab.LiveTv -> AppScreenTab.LiveTv
    NativeNavigationTab.Sports -> AppScreenTab.Sports
    NativeNavigationTab.Music -> AppScreenTab.Music
    NativeNavigationTab.Following -> AppScreenTab.Following
    NativeNavigationTab.News -> AppScreenTab.News
    NativeNavigationTab.Settings -> AppScreenTab.Settings
}
