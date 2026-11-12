package com.nuvio.app.features.livetv

import com.nuvio.app.features.music.data.MusicStorage
import com.nuvio.app.features.profiles.ProfileRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * What each profile wants from Live TV: the playlist categories they have hidden from the channel list, and the
 * guide categories they have chosen. Hidden categories are left out everywhere, including the guide data that is
 * kept in memory, so hiding what you don't watch also makes Live TV lighter.
 */
object LiveTvChannelPrefs {
    private const val STORE = "live_tv_prefs"
    private const val KEY_HIDDEN = "hidden_groups"
    private const val KEY_GUIDE = "guide_categories"

    private val serializer = ListSerializer(String.serializer())
    private var profileId = ProfileRepository.activeProfileId
    private val _hiddenGroups = MutableStateFlow(load(KEY_HIDDEN).orEmpty())
    private val _guideCategories = MutableStateFlow(load(KEY_GUIDE) ?: LIVE_TV_GUIDE_DEFAULT_CATEGORY_IDS)

    /** Keys ([groupKey]) of the playlist categories this profile has hidden. */
    val hiddenGroups: StateFlow<Set<String>> = _hiddenGroups.asStateFlow()

    /** Ids of the guide categories this profile wants to see. */
    val guideCategories: StateFlow<Set<String>> = _guideCategories.asStateFlow()

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
            ProfileRepository.state
                .map { it.activeProfile?.profileIndex ?: ProfileRepository.activeProfileId }
                .distinctUntilChanged()
                .collect { id ->
                    if (id != profileId) {
                        profileId = id
                        val previous = _hiddenGroups.value
                        _hiddenGroups.value = load(KEY_HIDDEN).orEmpty()
                        _guideCategories.value = load(KEY_GUIDE) ?: LIVE_TV_GUIDE_DEFAULT_CATEGORY_IDS
                        LiveTvRepository.onHiddenGroupsChanged(previous, _hiddenGroups.value)
                    }
                }
        }
    }

    /** The key a channel's playlist category is stored under; blank for channels without one. */
    fun groupKey(group: String?): String = group?.trim()?.lowercase().orEmpty()

    /** [channels] without the ones in categories this profile has hidden. */
    fun visibleChannels(channels: List<LiveTvChannel>): List<LiveTvChannel> {
        val hidden = _hiddenGroups.value
        return if (hidden.isEmpty()) channels else channels.filterNot { groupKey(it.group) in hidden }
    }

    fun isHidden(channel: LiveTvChannel): Boolean {
        val hidden = _hiddenGroups.value
        return hidden.isNotEmpty() && groupKey(channel.group) in hidden
    }

    fun setHiddenGroups(keys: Set<String>) {
        val previous = _hiddenGroups.value
        if (previous == keys) return
        _hiddenGroups.value = keys
        save(KEY_HIDDEN, keys)
        LiveTvRepository.onHiddenGroupsChanged(previous, keys)
    }

    fun setGuideCategories(ids: Set<String>) {
        if (_guideCategories.value == ids) return
        _guideCategories.value = ids
        save(KEY_GUIDE, ids)
    }

    private fun scoped(key: String) = "${key}_p$profileId"

    private fun save(key: String, values: Set<String>) {
        MusicStorage.putString(STORE, scoped(key), Json.encodeToString(serializer, values.toList()))
    }

    private fun load(key: String): Set<String>? =
        MusicStorage.getString(STORE, scoped(key))
            ?.let { runCatching { Json.decodeFromString(serializer, it) }.getOrNull() }
            ?.toSet()
}
