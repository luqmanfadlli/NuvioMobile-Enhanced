package com.nuvio.app.features.sports.compat

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The Sports hub's own preferences: what you follow and your sports settings, kept in the app's files. General Live TV
 * settings (clock format, guide shift, hidden channels) are Nuvio's, and Nuvio has no hidden or locked channels, so the
 * hub reads defaults for those.
 */
internal object LiveTvPreferences {
    private const val SPORTS_FILE = "sports-settings.json"
    private const val MINE_FILE = "sports-mine.json"

    private val _settings = MutableStateFlow(LiveTvSettings())
    val settings: StateFlow<LiveTvSettings> = _settings.asStateFlow()
    fun currentSettings(): LiveTvSettings = _settings.value
    fun updateSettings(transform: (LiveTvSettings) -> LiveTvSettings) { _settings.value = transform(_settings.value) }

    private val _user = MutableStateFlow(LiveUserState())
    val userState: StateFlow<LiveUserState> = _user.asStateFlow()
    fun currentUserState(): LiveUserState = _user.value

    private val _sports = MutableStateFlow<String?>(null)
    private val _mine = MutableStateFlow<String?>(null)
    private var loaded = false

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        _sports.value = runCatching { LiveTvFiles.readSmall(LiveTvDirs.STATE, SPORTS_FILE) }.getOrNull()
        _mine.value = runCatching { LiveTvFiles.readSmall(LiveTvDirs.STATE, MINE_FILE) }.getOrNull()
    }

    val sportsSettingsJson: Flow<String?> get() { ensureLoaded(); return _sports }
    val sportsMineJson: Flow<String?> get() { ensureLoaded(); return _mine }

    fun editSportsSettings(transform: (String?) -> String) {
        ensureLoaded()
        val next = transform(_sports.value)
        _sports.value = next
        runCatching { LiveTvFiles.writeSmall(LiveTvDirs.STATE, SPORTS_FILE, next) }
    }

    fun editSportsMine(transform: (String?) -> String) {
        ensureLoaded()
        val next = transform(_mine.value)
        _mine.value = next
        runCatching { LiveTvFiles.writeSmall(LiveTvDirs.STATE, MINE_FILE, next) }
    }
}
