package com.nuvio.app.features.ai

import com.nuvio.app.features.library.LibraryRepository
import com.nuvio.app.features.watched.WatchedRepository
import com.nuvio.app.features.watched.isEpisode
import com.nuvio.app.features.watchprogress.WatchProgressRepository

internal data class TasteTitle(
    val name: String,
    val year: String?,
    val type: String,
)

/**
 * A deliberately small summary of what the user watches. Only titles/years/genres are ever sent
 * to the model - no ids, tokens, profile names or timestamps.
 */
internal data class TasteProfile(
    val watched: List<TasteTitle>,
    val saved: List<TasteTitle>,
    val topGenres: List<String>,
) {
    val count: Int get() = watched.size
    val isMeaningful: Boolean get() = watched.size >= AI_MIN_WATCHED_TITLES

    /** Normalized names the model must not suggest again. */
    val excludedNames: Set<String> =
        (watched + saved).mapTo(HashSet()) { normalizeAiTitle(it.name) }

    fun toPrompt(rowCount: Int, itemsPerRow: Int): String = buildString {
        appendLine("Recently watched (most recent first):")
        watched.forEach { appendLine("- ${it.label()}") }
        if (saved.isNotEmpty()) {
            appendLine()
            appendLine("Saved to watch later:")
            saved.forEach { appendLine("- ${it.label()}") }
        }
        if (topGenres.isNotEmpty()) {
            appendLine()
            appendLine("Favourite genres: ${topGenres.joinToString()}")
        }
        appendLine()
        append("Create $rowCount themed recommendation rows with $itemsPerRow titles each. ")
        append("Do not include any title listed above.")
    }

    private fun TasteTitle.label(): String =
        buildString {
            append(name)
            if (year != null) append(" ($year)")
            append(" [").append(type).append(']')
        }
}

internal const val AI_MIN_WATCHED_TITLES = 5
private const val MAX_WATCHED = 40
private const val MAX_SAVED = 20
private const val MAX_GENRES = 6

internal fun normalizeAiTitle(value: String): String =
    value.lowercase().filter { it.isLetterOrDigit() }

private fun normalizeAiType(type: String): String =
    when (type.trim().lowercase()) {
        "series", "show", "tv", "tvshow", "anime" -> "series"
        else -> "movie"
    }

private fun String?.firstYear(): String? =
    this?.let { Regex("""\d{4}""").find(it)?.value }

internal object AiTasteProfileBuilder {
    fun build(): TasteProfile {
        WatchedRepository.ensureLoaded()
        WatchProgressRepository.ensureLoaded()
        LibraryRepository.ensureLoaded()

        data class Candidate(val title: TasteTitle, val at: Long)

        val candidates = mutableListOf<Candidate>()

        // Whole movies / series explicitly marked watched. Episode rows carry the episode title,
        // not the show name, so they are skipped here and covered by watch progress below.
        WatchedRepository.uiState.value.items
            .filterNot { it.isEpisode }
            .forEach { item ->
                if (item.name.isBlank()) return@forEach
                candidates += Candidate(
                    TasteTitle(item.name.trim(), item.releaseInfo.firstYear(), normalizeAiType(item.type)),
                    item.markedAtEpochMs,
                )
            }

        // Anything meaningfully played. title is the show/movie name for these entries.
        WatchProgressRepository.uiState.value.entries
            .filter { entry ->
                entry.isCompleted ||
                    (entry.durationMs > 0 && entry.lastPositionMs.toDouble() / entry.durationMs >= 0.2)
            }
            .forEach { entry ->
                if (entry.title.isBlank()) return@forEach
                candidates += Candidate(
                    TasteTitle(entry.title.trim(), null, normalizeAiType(entry.parentMetaType)),
                    entry.lastUpdatedEpochMs,
                )
            }

        val watched = candidates
            .sortedByDescending { it.at }
            .distinctBy { normalizeAiTitle(it.title.name) }
            .take(MAX_WATCHED)
            .map { it.title }

        val library = LibraryRepository.uiState.value.items
        val saved = library
            .sortedByDescending { it.savedAtEpochMs }
            .take(MAX_SAVED)
            .map { TasteTitle(it.name, it.releaseInfo.firstYear(), normalizeAiType(it.type)) }

        val topGenres = library
            .flatMap { it.genres }
            .filter { it.isNotBlank() }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(MAX_GENRES)
            .map { it.key }

        return TasteProfile(watched = watched, saved = saved, topGenres = topGenres)
    }
}
