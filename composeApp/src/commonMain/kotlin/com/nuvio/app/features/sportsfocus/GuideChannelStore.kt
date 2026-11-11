package com.nuvio.app.features.sportsfocus

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
 * Which channels make up "My Guide". The user builds the list once ("Build Your Guide") and edits it any
 * time; until they have, My Guide offers to set it up instead of guessing. Each profile has its own guide.
 */
object GuideChannelStore {
    private const val STORE = "guide_channels"
    private const val KEY_IDS = "ids"
    private const val KEY_CONFIGURED = "configured"

    private val serializer = ListSerializer(String.serializer())
    private var profileId = ProfileRepository.activeProfileId
    private val _selected = MutableStateFlow(loadIds())
    private val _configured = MutableStateFlow(loadConfigured())

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
            ProfileRepository.state
                .map { it.activeProfile?.profileIndex ?: ProfileRepository.activeProfileId }
                .distinctUntilChanged()
                .collect { id ->
                    if (id != profileId) {
                        profileId = id
                        _selected.value = loadIds()
                        _configured.value = loadConfigured()
                    }
                }
        }
    }

    private fun scoped(key: String) = "${key}_p$profileId"

    /** The channel ids in My Guide. */
    val selected: StateFlow<Set<String>> = _selected.asStateFlow()

    /** True once the user has built (or knowingly skipped building) their guide. */
    val configured: StateFlow<Boolean> = _configured.asStateFlow()

    fun isSelected(channelId: String): Boolean = channelId in _selected.value

    fun toggle(channelId: String) {
        val next = _selected.value.let { if (channelId in it) it - channelId else it + channelId }
        setSelected(next)
    }

    fun setSelected(ids: Set<String>) {
        _selected.value = ids
        MusicStorage.putString(STORE, scoped(KEY_IDS), Json.encodeToString(serializer, ids.toList()))
        markConfigured()
    }

    fun markConfigured() {
        if (_configured.value) return
        _configured.value = true
        MusicStorage.putString(STORE, scoped(KEY_CONFIGURED), "1")
    }

    /**
     * The guide used to be one list for the whole device. The profile that opens it first after the update keeps
     * that list; the others start with their own empty one.
     */
    private fun claimLegacy() {
        if (ProfileRepository.state.value.activeProfile == null) return
        if (MusicStorage.getString(STORE, scoped(KEY_CONFIGURED)) != null) return
        val ids = MusicStorage.getString(STORE, KEY_IDS)
        val configured = MusicStorage.getString(STORE, KEY_CONFIGURED)
        if (ids == null && configured == null) return
        ids?.let { MusicStorage.putString(STORE, scoped(KEY_IDS), it) }
        configured?.let { MusicStorage.putString(STORE, scoped(KEY_CONFIGURED), it) }
        MusicStorage.remove(STORE, KEY_IDS)
        MusicStorage.remove(STORE, KEY_CONFIGURED)
    }

    private fun loadConfigured(): Boolean {
        claimLegacy()
        return MusicStorage.getString(STORE, scoped(KEY_CONFIGURED)) == "1"
    }

    private fun loadIds(): Set<String> {
        claimLegacy()
        return MusicStorage.getString(STORE, scoped(KEY_IDS))
            ?.let { runCatching { Json.decodeFromString(serializer, it) }.getOrNull() }
            ?.toSet()
            .orEmpty()
    }
}
