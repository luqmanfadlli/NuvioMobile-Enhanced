package com.nuvio.app.features.experience

import com.nuvio.app.AppScreenTab
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Music is parked while the app focuses on the Balanced and Sports layouts. The code stays (podcasts
 * share its player), but its mode, tab, and search results are hidden from the UI. Flip to bring it back.
 */
object ExperienceFeatures {
    const val MUSIC_ENABLED = false
}

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
            Balanced -> listOf(AppScreenTab.Home, AppScreenTab.NewHot, AppScreenTab.LiveTv, AppScreenTab.Search, AppScreenTab.Sports, AppScreenTab.Library)
            SportsFocus -> listOf(AppScreenTab.Home, AppScreenTab.Following, AppScreenTab.Search, AppScreenTab.LiveTv, AppScreenTab.News)
            Music -> listOf(AppScreenTab.Home, AppScreenTab.Search, AppScreenTab.Library)
        }
        return if (showLiveTv) ordered else ordered.filter { it != AppScreenTab.LiveTv && it != AppScreenTab.Sports }
    }

    companion object {
        /** The modes a user can pick between. Music is left out while [ExperienceFeatures.MUSIC_ENABLED] is off. */
        val selectable: List<ExperienceMode>
            get() = entries.filter { it != Music || ExperienceFeatures.MUSIC_ENABLED }

        /** A stored "music" choice from before the feature was parked opens Balanced instead. */
        fun fromKey(key: String?): ExperienceMode =
            entries.firstOrNull { it.storageKey == key }
                ?.takeIf { it in selectable }
                ?: Balanced
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
