package com.nuvio.app.features.library

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

/** The trailers you have played, newest first, one entry per title. Each profile has its own list. */
object TrailerHistory {
    private const val STORE = "trailer_history"
    private const val KEY = "items"
    private const val MAX_ITEMS = 30

    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(TrailerHistoryItem.serializer())
    private var profileId = ProfileRepository.activeProfileId
    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<TrailerHistoryItem>> = _items.asStateFlow()

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
            ProfileRepository.state
                .map { it.activeProfile?.profileIndex ?: ProfileRepository.activeProfileId }
                .distinctUntilChanged()
                .collect { id ->
                    if (id != profileId) {
                        profileId = id
                        _items.value = load()
                    }
                }
        }
    }

    private fun scopedKey() = "${KEY}_p$profileId"

    fun record(id: String, type: String, name: String, poster: String?, background: String?, nowMs: Long) {
        if (id.isBlank() || name.isBlank()) return
        val entry = TrailerHistoryItem(id, type, name, poster, background, nowMs)
        val next = (listOf(entry) + _items.value.filterNot { it.id == id && it.type == type }).take(MAX_ITEMS)
        _items.value = next
        MusicStorage.putString(STORE, scopedKey(), json.encodeToString(serializer, next))
    }

    private fun load(): List<TrailerHistoryItem> {
        // The list used to be one for the whole device: the profile that opens it first after the update keeps it.
        if (ProfileRepository.state.value.activeProfile != null && MusicStorage.getString(STORE, scopedKey()) == null) {
            MusicStorage.getString(STORE, KEY)?.let { legacy ->
                MusicStorage.putString(STORE, scopedKey(), legacy)
                MusicStorage.remove(STORE, KEY)
            }
        }
        return MusicStorage.getString(STORE, scopedKey())
            ?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }
            .orEmpty()
    }
}
