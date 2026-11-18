package com.nuvio.app.features.telegram

import java.util.Locale

/** Conservative matching: subtitles are optional when the primary title is present. */
internal object TelegramSearchMatcher {
    private val separators = Regex("[^\\p{L}\\p{N}]+")
    private val spaces = Regex("\\s+")
    private val yearPattern = Regex("\\b(19\\d{2}|20\\d{2})\\b")

    fun normalize(value: String): String = spaces.replace(
        separators.replace(value.lowercase(Locale.ROOT), " "),
        " ",
    ).trim()

    fun buildQueries(title: String, year: Int?, season: Int?, episode: Int?, type: String): List<String> {
        val fullTitle = cleanTitle(title)
        if (fullTitle.isBlank()) return emptyList()
        val primaryTitle = cleanTitle(title.substringBefore(':')).ifBlank { fullTitle }
        val forms = listOf(fullTitle, primaryTitle).distinctBy(::normalize)
        val queries = linkedSetOf<String>()
        if (type == "tv" && season != null && episode != null) {
            forms.forEach { base ->
                queries += "$base S${season.toString().padStart(2, '0')}E${episode.toString().padStart(2, '0')}"
                queries += "$base ${season}x$episode"
            }
            forms.forEach { queries += it }
        } else {
            if (year != null) forms.forEach { queries += "$it $year" }
            forms.forEach { queries += it }
        }
        return queries.filter(String::isNotBlank).take(6)
    }

    fun score(
        fileName: String, caption: String, title: String, year: Int?,
        season: Int?, episode: Int?, type: String,
    ): Int {
        val searchable = normalize("$fileName $caption")
        val words = normalize(title).split(' ').filter { it.length > 1 }.distinct()
        if (searchable.isBlank() || words.isEmpty()) return 0
        val available = searchable.split(' ').toSet()
        val matched = words.filter { it in available }.toSet()
        val primaryWords = normalize(title.substringBefore(':')).split(' ').filter { it.length > 1 }.distinct()
        val primaryMatched = primaryWords.isNotEmpty() && primaryWords.all { it in available }
        val ratio = matched.size.toDouble() / words.size.toDouble()
        if (matched.isEmpty() || (!primaryMatched && ratio < 0.60)) return 0

        if (type == "tv" && season != null && episode != null) {
            val se = Regex("(?i)(?:s0*$season\\s*e0*$episode|0*$season\\s*x0*$episode)(?!\\d)")
            if (!se.containsMatchIn(searchable)) return 0
        }

        var score = when {
            ratio >= 1.0 -> 90
            primaryMatched -> 78 + (ratio * 10).toInt()
            else -> 60 + (ratio * 25).toInt()
        }
        if (type == "movie" && year != null) {
            val years = yearPattern.findAll(searchable).mapNotNull { it.value.toIntOrNull() }.toList()
            when {
                year in years -> score += 8
                years.isEmpty() -> score += 2
                years.any { kotlin.math.abs(it - year) == 1 } -> score -= 5
                else -> return 0
            }
        }
        if (type == "tv" && season != null && episode != null) score += 8
        return score.coerceIn(0, 100)
    }

    private fun cleanTitle(value: String): String =
        spaces.replace(separators.replace(value, " "), " ").trim()
}
