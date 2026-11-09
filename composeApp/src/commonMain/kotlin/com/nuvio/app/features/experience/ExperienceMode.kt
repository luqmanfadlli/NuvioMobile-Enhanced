package com.nuvio.app.features.experience

import com.nuvio.app.AppScreenTab
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * How the app is arranged. Each mode picks which tabs exist and in what order; features that
 * are specific to a mode (the Stadia TV sports pages, the music library) live behind its tabs.
 */
enum class ExperienceMode(val storageKey: String) {
    /** Movies, shows, live TV and sports in equal measure. */
    Balanced("balanced"),

    /** Sports first: Home, Following, Search, Live TV and Discover, with a scoreboard-style home. Settings is the corner avatar, as in every layout. */
    SportsFocus("sports"),

    /** A Spotify-style music app: Home, Search and Your Library, with a mini player and Now Playing. */
    Music("music"),
    ;

    /** The bottom-bar tabs for this mode, in order. Settings sits last on Android; on iPhone it is the avatar (or, in Sports focus, a gear tab). */
    fun tabs(showLiveTv: Boolean): List<AppScreenTab> {
        val ordered = when (this) {
            Balanced -> listOf(AppScreenTab.Home, AppScreenTab.LiveTv, AppScreenTab.Search, AppScreenTab.Sports, AppScreenTab.Library)
            SportsFocus -> listOf(AppScreenTab.Home, AppScreenTab.Following, AppScreenTab.Search, AppScreenTab.LiveTv, AppScreenTab.News)
            Music -> listOf(AppScreenTab.Home, AppScreenTab.Search, AppScreenTab.Library)
        }
        return if (showLiveTv) ordered else ordered.filter { it != AppScreenTab.LiveTv && it != AppScreenTab.Sports }
    }

    companion object {
        fun fromKey(key: String?): ExperienceMode =
            entries.firstOrNull { it.storageKey == key } ?: Balanced
    }
}

/** Device-wide storage for the chosen mode (the native tab bar reads the same value). */
expect object ExperienceStorage {
    fun load(): String?
    fun save(key: String)
}

object ExperienceRepository {
    private val _mode = MutableStateFlow(ExperienceMode.fromKey(ExperienceStorage.load()))
    val mode: StateFlow<ExperienceMode> = _mode.asStateFlow()

    /** Re-reads storage (Android calls this once its preferences are ready). */
    fun refresh() {
        _mode.value = ExperienceMode.fromKey(ExperienceStorage.load())
    }

    fun setMode(mode: ExperienceMode) {
        if (_mode.value == mode) return
        ExperienceStorage.save(mode.storageKey)
        _mode.value = mode
    }
}
