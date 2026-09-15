package com.nuvio.app.features.streams

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object PinnedStreamSourcesRepository {
    private const val SEPARATOR = "\n"

    private val _pinnedSourceIds = MutableStateFlow<List<String>>(emptyList())
    val pinnedSourceIds: StateFlow<List<String>> = _pinnedSourceIds.asStateFlow()

    private var hasLoaded = false

    fun ensureLoaded() {
        if (hasLoaded) return
        loadFromDisk()
    }

    fun onProfileChanged() {
        loadFromDisk()
    }

    fun isPinned(addonId: String): Boolean = addonId in _pinnedSourceIds.value

    fun setPinned(addonId: String, pinned: Boolean) {
        val normalized = addonId.trim()
        if (normalized.isEmpty()) return
        ensureLoaded()
        val current = _pinnedSourceIds.value
        val updated = when {
            pinned && normalized in current -> return
            pinned -> current + normalized
            else -> current.filterNot { it == normalized }
        }
        if (updated == current) return
        _pinnedSourceIds.value = updated
        persist(updated)
    }

    fun toggle(addonId: String) {
        setPinned(addonId, !isPinned(addonId))
    }

    private fun loadFromDisk() {
        hasLoaded = true
        _pinnedSourceIds.value = StreamBadgeSettingsStorage.loadPinnedStreamSources()
            ?.split(SEPARATOR)
            ?.map(String::trim)
            ?.filter { it.isNotEmpty() }
            ?.distinct()
            .orEmpty()
    }

    private fun persist(ids: List<String>) {
        StreamBadgeSettingsStorage.savePinnedStreamSources(ids.joinToString(SEPARATOR))
    }
}
