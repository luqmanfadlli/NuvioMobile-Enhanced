package com.nuvio.app.features.home

import com.nuvio.app.features.details.MetaDetailsRepository
import com.nuvio.app.features.library.LibraryRepository
import com.nuvio.app.features.sports.compat.LiveTvClock
import com.nuvio.app.features.tmdb.TmdbService
import com.nuvio.app.features.watched.WatchedItem
import com.nuvio.app.features.watched.watchedItemKeys
import com.nuvio.app.features.watching.application.WatchingState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What to watch next, based on a title you finished: its name and recommendations that fit it. */
data class BecauseYouWatched(val title: String, val items: List<MetaPreview>)

/**
 * The "Because you watched X" rows. They follow what you have finished, not what you have started: a movie
 * watched to the end, or a series with every episode watched. The two most recent finishes each get a row, built
 * from TMDB's recommendations for that title with anything you've already watched or saved removed and only
 * what can be watched at home kept. A title that leaves fewer than four picks is skipped for the next one.
 */
object BecauseYouWatchedRepository {
    private const val MAX_ROWS = 2
    private const val CANDIDATES = 6
    private const val PICKS = 14
    private const val MIN_PICKS = 4
    private const val REFRESH_MS = 3L * 60 * 60 * 1000

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow<List<BecauseYouWatched>>(emptyList())
    val state: StateFlow<List<BecauseYouWatched>> = _state.asStateFlow()
    private var builtFor: List<String> = emptyList()
    private var builtAtMs = 0L
    private var job: Job? = null

    private class Finished(val type: String, val id: String, val name: String, val finishedAtMs: Long)

    /** Rebuilds the rows when the titles you have finished change (or after a few hours). */
    fun refresh(watched: List<WatchedItem>, watchedKeys: Set<String>, fullyWatchedSeriesKeys: Set<String>) {
        val finished = finishedTitles(watched, fullyWatchedSeriesKeys).take(CANDIDATES)
        val signature = finished.map { "${it.type}:${it.id}" }
        if (signature.isEmpty()) { _state.value = emptyList(); builtFor = emptyList(); return }
        if (job?.isActive == true) return
        if (signature == builtFor && LiveTvClock.nowMs() - builtAtMs < REFRESH_MS) return
        job = scope.launch {
            val rows = ArrayList<BecauseYouWatched>()
            val used = HashSet<String>()
            for (title in finished) {
                if (rows.size >= MAX_ROWS) break
                val row = runCatching { build(title, watchedKeys, fullyWatchedSeriesKeys, used) }.getOrNull() ?: continue
                used += row.items.map { it.id }
                rows += row
            }
            builtFor = signature
            builtAtMs = LiveTvClock.nowMs()
            _state.value = rows
        }
    }

    /** Finished movies and fully watched series, most recently finished first. */
    private fun finishedTitles(watched: List<WatchedItem>, fullyWatchedSeriesKeys: Set<String>): List<Finished> {
        val byTitle = watched.groupBy { "${if (it.type.isSeriesType()) "series" else "movie"}:${it.id}" }
        return byTitle.values.mapNotNull { items ->
            val first = items.first()
            val isSeries = first.type.isSeriesType()
            if (isSeries && watchedItemKeys(first.type, first.id).none(fullyWatchedSeriesKeys::contains)) return@mapNotNull null
            // A movie counts through its own entry; individual episodes alone don't make a series finished.
            if (!isSeries && items.none { it.season == null && it.episode == null }) return@mapNotNull null
            Finished(first.type, first.id, first.name, items.maxOf { it.markedAtEpochMs })
        }.sortedByDescending { it.finishedAtMs }
    }

    private fun String.isSeriesType() = equals("series", true) || equals("tv", true) || equals("show", true)

    private suspend fun build(
        title: Finished,
        watchedKeys: Set<String>,
        fullyWatchedSeriesKeys: Set<String>,
        alreadyUsed: Set<String>,
    ): BecauseYouWatched? {
        val meta = MetaDetailsRepository.fetch(title.type, title.id) ?: return null
        val candidates = meta.moreLikeThis
            .filterNot { it.id == title.id || it.id in alreadyUsed }
            .filterNot { WatchingState.isPosterWatched(watchedKeys, it, fullyWatchedSeriesKeys) }
            .filterNot { LibraryRepository.isSaved(it.id, it.type) }
            .take(PICKS + 10)
        val watchable = coroutineScope {
            candidates.map { pick ->
                async {
                    val mediaType = TmdbService.normalizeMediaType(pick.type)
                    val tmdbId = TmdbService.ensureTmdbId(pick.id, mediaType)?.toIntOrNull() ?: return@async null
                    if (DigitalAvailability.check(mediaType, tmdbId) != null) pick else null
                }
            }.awaitAll().filterNotNull()
        }.take(PICKS)
        return if (watchable.size >= MIN_PICKS) BecauseYouWatched(meta.name.ifBlank { title.name }, watchable) else null
    }
}
