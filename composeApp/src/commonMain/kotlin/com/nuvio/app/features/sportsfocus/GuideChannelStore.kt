package com.nuvio.app.features.sportsfocus

import com.nuvio.app.features.music.data.MusicStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Which channels make up "My Guide". The user builds the list once ("Build Your Guide") and edits it any
 * time; until they have, My Guide offers to set it up instead of guessing.
 */
object GuideChannelStore {
    private const val STORE = "guide_channels"
    private const val KEY_IDS = "ids"
    private const val KEY_CONFIGURED = "configured"

    private val serializer = ListSerializer(String.serializer())
    private val _selected = MutableStateFlow(loadIds())
    private val _configured = MutableStateFlow(MusicStorage.getString(STORE, KEY_CONFIGURED) == "1")

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
        MusicStorage.putString(STORE, KEY_IDS, Json.encodeToString(serializer, ids.toList()))
        markConfigured()
    }

    fun markConfigured() {
        if (_configured.value) return
        _configured.value = true
        MusicStorage.putString(STORE, KEY_CONFIGURED, "1")
    }

    private fun loadIds(): Set<String> =
        MusicStorage.getString(STORE, KEY_IDS)
            ?.let { runCatching { Json.decodeFromString(serializer, it) }.getOrNull() }
            ?.toSet()
            .orEmpty()
}
