package com.nuvio.app.features.library

import com.nuvio.app.features.music.data.MusicStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** A title whose trailer was played, shown in the Library's "Trailers You've Watched" row. */
@Serializable
data class TrailerHistoryItem(
    val id: String,
    val type: String,
    val name: String,
    val poster: String? = null,
    val background: String? = null,
    val watchedAtMs: Long,
)

/** The trailers you have played, newest first, one entry per title. */
object TrailerHistory {
    private const val STORE = "trailer_history"
    private const val KEY = "items"
    private const val MAX_ITEMS = 30

    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(TrailerHistoryItem.serializer())
    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<TrailerHistoryItem>> = _items.asStateFlow()

    fun record(id: String, type: String, name: String, poster: String?, background: String?, nowMs: Long) {
        if (id.isBlank() || name.isBlank()) return
        val entry = TrailerHistoryItem(id, type, name, poster, background, nowMs)
        val next = (listOf(entry) + _items.value.filterNot { it.id == id && it.type == type }).take(MAX_ITEMS)
        _items.value = next
        MusicStorage.putString(STORE, KEY, json.encodeToString(serializer, next))
    }

    private fun load(): List<TrailerHistoryItem> =
        MusicStorage.getString(STORE, KEY)
            ?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }
            .orEmpty()
}
