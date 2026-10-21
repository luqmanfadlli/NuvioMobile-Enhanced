package com.nuvio.app.features.ai

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Model ids change over time; this is only the default. The user can edit it in settings,
 * so a retired model name never requires an app update.
 */
const val AI_DEFAULT_GEMINI_MODEL = "gemini-2.5-flash"
const val AI_DEFAULT_ROW_COUNT = 5
const val AI_DEFAULT_ITEMS_PER_ROW = 25
val AI_ROW_COUNT_RANGE: IntRange = 1..8
val AI_ITEMS_PER_ROW_RANGE: IntRange = 10..30

/** The kinds of home rows the AI section can produce. [key] is what Gemini and the cache use. */
enum class AiRowKind(val key: String, val label: String) {
    ForYou("for_you", "For you"),
    Themed("theme", "Themed rows"),
    Mood("mood", "Mood & occasion rows"),
    HiddenGems("hidden_gem", "Hidden gems"),
    BecauseWatched("because", "Because you watched");

    companion object {
        fun fromKey(key: String): AiRowKind? = values().firstOrNull { it.key == key }
    }
}

data class AiRecommendationSettings(
    val enabled: Boolean = false,
    val apiKey: String = "",
    val model: String = AI_DEFAULT_GEMINI_MODEL,
    val rowCount: Int = AI_DEFAULT_ROW_COUNT,
    val itemsPerRow: Int = AI_DEFAULT_ITEMS_PER_ROW,
    val enabledRowKinds: Set<AiRowKind> = AiRowKind.values().toSet(),
) {
    val isUsable: Boolean
        get() = enabled && apiKey.isNotBlank() && model.isNotBlank()
}

object AiSettingsRepository {
    private const val KEY_ENABLED = "ai_enabled"
    private const val KEY_API_KEY = "ai_gemini_api_key"
    private const val KEY_MODEL = "ai_gemini_model"
    private const val KEY_ROW_COUNT = "ai_row_count"
    private const val KEY_ITEMS_PER_ROW = "ai_items_per_row"
    private const val KEY_ROW_KINDS = "ai_row_kinds"

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

    /** Applies on the next "Refresh now" (fetches more rows from Gemini). */
    fun setRowCount(value: Int) {
        ensureLoaded()
        val clamped = value.coerceIn(AI_ROW_COUNT_RANGE)
        if (_uiState.value.rowCount == clamped) return
        _uiState.value = _uiState.value.copy(rowCount = clamped)
        AiSettingsStorage.save(KEY_ROW_COUNT, clamped.toString())
        AiRecommendationsRepository.applySettings()
    }

    /** Applies on the next "Refresh now" (fetches more titles from Gemini). */
    fun setItemsPerRow(value: Int) {
        ensureLoaded()
        val clamped = value.coerceIn(AI_ITEMS_PER_ROW_RANGE)
        if (_uiState.value.itemsPerRow == clamped) return
        _uiState.value = _uiState.value.copy(itemsPerRow = clamped)
        AiSettingsStorage.save(KEY_ITEMS_PER_ROW, clamped.toString())
        AiRecommendationsRepository.applySettings()
    }

    /** Shows or hides a row kind immediately, without calling Gemini. */
    fun setRowKindEnabled(kind: AiRowKind, enabled: Boolean) {
        ensureLoaded()
        val current = _uiState.value.enabledRowKinds
        val updated = if (enabled) current + kind else current - kind
        if (updated == current) return
        _uiState.value = _uiState.value.copy(enabledRowKinds = updated)
        AiSettingsStorage.save(KEY_ROW_KINDS, updated.joinToString(",") { it.key })
        AiRecommendationsRepository.applySettings()
    }

    private fun loadFromDisk() {
        hasLoaded = true
        _uiState.value = AiRecommendationSettings(
            enabled = AiSettingsStorage.load(KEY_ENABLED)?.toBooleanStrictOrNull() ?: false,
            apiKey = AiSettingsStorage.load(KEY_API_KEY).orEmpty(),
            model = AiSettingsStorage.load(KEY_MODEL)?.takeIf { it.isNotBlank() } ?: AI_DEFAULT_GEMINI_MODEL,
            rowCount = AiSettingsStorage.load(KEY_ROW_COUNT)?.toIntOrNull()?.coerceIn(AI_ROW_COUNT_RANGE)
                ?: AI_DEFAULT_ROW_COUNT,
            itemsPerRow = AiSettingsStorage.load(KEY_ITEMS_PER_ROW)?.toIntOrNull()?.coerceIn(AI_ITEMS_PER_ROW_RANGE)
                ?: AI_DEFAULT_ITEMS_PER_ROW,
            enabledRowKinds = AiSettingsStorage.load(KEY_ROW_KINDS)
                ?.split(',')
                ?.mapNotNull(AiRowKind::fromKey)
                ?.toSet()
                ?: AiRowKind.values().toSet(),
        )
    }
}
