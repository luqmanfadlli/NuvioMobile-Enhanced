package com.nuvio.app.features.ai

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Model ids change over time; this is only the default. The user can edit it in settings,
 * so a retired model name never requires an app update.
 */
const val AI_DEFAULT_GEMINI_MODEL = "gemini-2.5-flash"

data class AiRecommendationSettings(
    val enabled: Boolean = false,
    val apiKey: String = "",
    val model: String = AI_DEFAULT_GEMINI_MODEL,
) {
    val isUsable: Boolean
        get() = enabled && apiKey.isNotBlank() && model.isNotBlank()
}

object AiSettingsRepository {
    private const val KEY_ENABLED = "ai_enabled"
    private const val KEY_API_KEY = "ai_gemini_api_key"
    private const val KEY_MODEL = "ai_gemini_model"

    private val _uiState = MutableStateFlow(AiRecommendationSettings())
    val uiState: StateFlow<AiRecommendationSettings> = _uiState.asStateFlow()

    private var hasLoaded = false

    fun ensureLoaded() {
        if (hasLoaded) return
        loadFromDisk()
    }

    fun onProfileChanged() {
        loadFromDisk()
        AiRecommendationsRepository.onProfileChanged()
    }

    fun snapshot(): AiRecommendationSettings {
        ensureLoaded()
        return _uiState.value
    }

    fun setEnabled(value: Boolean) {
        ensureLoaded()
        if (_uiState.value.enabled == value) return
        _uiState.value = _uiState.value.copy(enabled = value)
        AiSettingsStorage.save(KEY_ENABLED, value.toString())
        AiRecommendationsRepository.refresh(force = false)
    }

    /** Persists credentials/model and forces a fresh generation. */
    fun saveCredentials(apiKey: String, model: String) {
        ensureLoaded()
        val normalizedKey = apiKey.trim()
        val normalizedModel = model.trim().ifBlank { AI_DEFAULT_GEMINI_MODEL }
        _uiState.value = _uiState.value.copy(apiKey = normalizedKey, model = normalizedModel)
        AiSettingsStorage.save(KEY_API_KEY, normalizedKey)
        AiSettingsStorage.save(KEY_MODEL, normalizedModel)
        AiRecommendationsRepository.refresh(force = true)
    }

    private fun loadFromDisk() {
        hasLoaded = true
        _uiState.value = AiRecommendationSettings(
            enabled = AiSettingsStorage.load(KEY_ENABLED)?.toBooleanStrictOrNull() ?: false,
            apiKey = AiSettingsStorage.load(KEY_API_KEY).orEmpty(),
            model = AiSettingsStorage.load(KEY_MODEL)?.takeIf { it.isNotBlank() } ?: AI_DEFAULT_GEMINI_MODEL,
        )
    }
}
