package com.nuvio.app.features.home

import com.nuvio.app.features.details.MetaDetailsRepository
import com.nuvio.app.features.sports.compat.LiveTvClock
import com.nuvio.app.features.watched.WatchedItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What to watch next, based on the last title you finished: its name and recommendations that fit it. */
data class BecauseYouWatched(val title: String, val items: List<MetaPreview>)

object BecauseYouWatchedRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow<BecauseYouWatched?>(null)
    val state: StateFlow<BecauseYouWatched?> = _state.asStateFlow()
    private var builtFor: String? = null
    private var builtAtMs = 0L
    private var job: Job? = null

    /** Rebuilds the row when the most recently watched title changes (or is a few hours old). */
    fun refresh(watched: List<WatchedItem>, watchedKeys: Set<String>) {
        val latest = watched.sortedByDescending { it.markedAtEpochMs }.firstOrNull() ?: return
        val key = "${latest.type}:${latest.id}"
        if (job?.isActive == true) return
        if (builtFor == key && LiveTvClock.nowMs() - builtAtMs < 3L * 60 * 60 * 1000) return
        job = scope.launch {
            val meta = runCatching { MetaDetailsRepository.fetch(latest.type, latest.id) }.getOrNull() ?: return@launch
            val picks = meta.moreLikeThis
                .filterNot { it.id == latest.id }
                .filterNot { "${it.type}:${it.id}" in watchedKeys || it.id in watchedKeys }
                .take(14)
            builtFor = key
            builtAtMs = LiveTvClock.nowMs()
            _state.value = if (picks.size >= 4) BecauseYouWatched(meta.name.ifBlank { latest.name }, picks) else null
        }
    }
}
