package com.nuvio.app.features.details

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object OmdbSettingsRepository {
    private val _apiKey = MutableStateFlow("")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private var hasLoaded = false

    fun ensureLoaded() {
        if (hasLoaded) return
        hasLoaded = true
        _apiKey.value = OmdbSettingsStorage.loadApiKey().orEmpty()
    }

    fun setApiKey(value: String) {
        ensureLoaded()
        val trimmed = value.trim()
        if (_apiKey.value == trimmed) return
        _apiKey.value = trimmed
        OmdbSettingsStorage.saveApiKey(trimmed)
        MetaDetailsRepository.clear()
    }

    internal fun effectiveApiKey(): String {
        ensureLoaded()
        return _apiKey.value.ifBlank { ImdbEpisodeRatingsConfig.OMDB_API_KEY.trim() }
    }
}
