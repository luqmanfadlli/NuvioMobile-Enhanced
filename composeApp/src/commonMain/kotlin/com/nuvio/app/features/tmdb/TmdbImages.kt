package com.nuvio.app.features.tmdb

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

internal fun tmdbImageLanguages(language: String): String {
    val normalizedLanguage = normalizeTmdbLanguage(language)
    return listOf(normalizedLanguage.substringBefore("-"), normalizedLanguage, "en", "null")
        .distinct()
        .joinToString(",")
}

internal fun List<TmdbImage>.selectBestLocalizedImagePath(normalizedLanguage: String): String? {
    val languageCode = normalizedLanguage.substringBefore("-")
    val regionCode = normalizedLanguage.substringAfter("-", "").uppercase().takeIf { it.length == 2 }
        ?: defaultLanguageRegions[languageCode]
    return filter { !it.filePath.isNullOrBlank() }.sortedWith(
        compareByDescending<TmdbImage> { it.iso6391 == languageCode && it.iso31661 == regionCode }
            .thenByDescending { it.iso6391 == languageCode && it.iso31661 == null }
            .thenByDescending { it.iso6391 == languageCode }
            .thenByDescending { it.iso6391 == "en" }
            .thenByDescending { it.iso6391 == null },
    ).firstOrNull()?.filePath?.trim()
}

internal fun List<TmdbImage>.selectTitleLogoPath(
    normalizedLanguage: String,
    originalLanguage: String?,
    preferOriginal: Boolean,
): String? {
    val languageCode = normalizedLanguage.substringBefore("-").lowercase()
    val regionCode = normalizedLanguage.substringAfter("-", "").uppercase().takeIf { it.length == 2 }
        ?: defaultLanguageRegions[languageCode]
    val original = originalLanguage?.substringBefore("-")?.lowercase()?.takeIf(String::isNotBlank)
    fun rank(image: TmdbImage): Int? {
        val iso = image.iso6391?.lowercase()
        return when {
            preferOriginal && original != null && iso == original -> 0
            iso == languageCode && image.iso31661 == regionCode -> 1
            iso == languageCode && image.iso31661 == null -> 2
            iso == languageCode -> 3
            iso == "en" -> 4
            original != null && iso == original -> 5
            iso == null -> 6
            else -> null
        }
    }
    return asSequence()
        .filter { !it.filePath.isNullOrBlank() }
        .mapNotNull { image -> rank(image)?.let { image to it } }
        .minByOrNull { it.second }
        ?.first
        ?.filePath
        ?.trim()
}

private val defaultLanguageRegions = mapOf(
    "pt" to "PT",
    "es" to "ES",
)

@Serializable
internal data class TmdbImagesResponse(
    val logos: List<TmdbImage> = emptyList(),
    val backdrops: List<TmdbImage> = emptyList(),
    val posters: List<TmdbImage> = emptyList(),
)

@Serializable
internal data class TmdbImage(
    @SerialName("file_path") val filePath: String? = null,
    @SerialName("iso_639_1") val iso6391: String? = null,
    @SerialName("iso_3166_1") val iso31661: String? = null,
)
