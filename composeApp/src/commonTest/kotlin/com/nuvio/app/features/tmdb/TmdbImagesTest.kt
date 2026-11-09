package com.nuvio.app.features.tmdb

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TmdbImagesTest {
    @Test
    fun selectedLanguageBackdropTakesPriorityOverEnglishAndTextlessArtwork() {
        val images = Json { ignoreUnknownKeys = true }.decodeFromString<TmdbImagesResponse>(
            """{"backdrops":[
                {"file_path":"/textless.jpg","iso_639_1":null},
                {"file_path":"/english.jpg","iso_639_1":"en"},
                {"file_path":"/malayalam.jpg","iso_639_1":"ml"}
            ],"logos":[{"file_path":"/logo.png","iso_639_1":"ml"}]}""",
        )

        assertEquals("/malayalam.jpg", images.backdrops.selectBestLocalizedImagePath("ml"))
        assertEquals("/logo.png", images.logos.selectBestLocalizedImagePath("ml"))
    }

    @Test
    fun regionalArtworkTakesPriorityOverOtherRegions() {
        val images = listOf(
            TmdbImage("/brazil.jpg", "pt", "BR"),
            TmdbImage("/portuguese.jpg", "pt"),
            TmdbImage("/portugal.jpg", "pt", "PT"),
        )

        assertEquals("/portugal.jpg", images.selectBestLocalizedImagePath("pt-PT"))
        assertEquals("/brazil.jpg", images.selectBestLocalizedImagePath("pt-BR"))
        assertEquals("/portugal.jpg", images.selectBestLocalizedImagePath("pt"))
    }

    @Test
    fun missingRegionFallsBackToSameLanguageThenEnglishThenTextless() {
        val images = listOf(
            TmdbImage("/textless.jpg"),
            TmdbImage("/english.jpg", "en"),
            TmdbImage("/brazil.jpg", "pt", "BR"),
            TmdbImage("/portuguese.jpg", "pt"),
        )

        assertEquals("/portuguese.jpg", images.selectBestLocalizedImagePath("pt-PT"))
        assertEquals("/brazil.jpg", images.dropLast(1).selectBestLocalizedImagePath("pt-PT"))
        assertEquals("/english.jpg", images.take(2).selectBestLocalizedImagePath("pt-PT"))
        assertEquals("/textless.jpg", images.take(1).selectBestLocalizedImagePath("pt-PT"))
    }

    @Test
    fun unusableLocalizedImageDoesNotHideValidFallback() {
        val images = listOf(TmdbImage(" ", "ml"), TmdbImage(null, "ml"), TmdbImage("/english.jpg", "en"))
        assertEquals("/english.jpg", images.selectBestLocalizedImagePath("ml"))
        assertNull(emptyList<TmdbImage>().selectBestLocalizedImagePath("ml"))
        assertNull(images.take(2).selectBestLocalizedImagePath("ml"))
    }

    @Test
    fun imageLanguageQueryIncludesConfiguredLanguageAndFallbacks() {
        assertEquals("ml,en,null", tmdbImageLanguages("ml"))
        assertEquals("pt,pt-BR,en,null", tmdbImageLanguages("pt_BR"))
        assertEquals("es,es-MX,en,null", tmdbImageLanguages("es-419"))
        assertEquals("en,null", tmdbImageLanguages("en"))
    }

    private val localFilmLogos = listOf(
        TmdbImage("/indonesian.png", "id"),
        TmdbImage("/textless.png", null),
    )

    @Test
    fun titleLogoFallsBackToOriginalLanguageWhenNoAppLanguageOrEnglishLogo() {
        assertEquals(
            "/indonesian.png",
            localFilmLogos.selectTitleLogoPath("en-US", originalLanguage = "id", preferOriginal = false),
        )
    }

    @Test
    fun titleLogoPrefersAppLanguageThenEnglishOverOriginalByDefault() {
        val logos = localFilmLogos + TmdbImage("/english.png", "en")
        assertEquals("/english.png", logos.selectTitleLogoPath("en-US", "id", preferOriginal = false))
        assertEquals("/english.png", logos.selectTitleLogoPath("fr-FR", "id", preferOriginal = false))
        val withFrench = logos + TmdbImage("/french.png", "fr")
        assertEquals("/french.png", withFrench.selectTitleLogoPath("fr-FR", "id", preferOriginal = false))
    }

    @Test
    fun titleLogoPrefersOriginalLanguageAndFallsBackToEnglish() {
        val logos = localFilmLogos + TmdbImage("/english.png", "en")
        assertEquals("/indonesian.png", logos.selectTitleLogoPath("en-US", "id", preferOriginal = true))
        val noOriginal = listOf(TmdbImage("/english.png", "en"), TmdbImage("/textless.png", null))
        assertEquals("/english.png", noOriginal.selectTitleLogoPath("en-US", "id", preferOriginal = true))
    }

    @Test
    fun titleLogoIgnoresUnrelatedLanguages() {
        val logos = listOf(TmdbImage("/japanese.png", "ja"))
        assertNull(logos.selectTitleLogoPath("en-US", "id", preferOriginal = false))
        assertEquals(
            "/textless.png",
            (logos + TmdbImage("/textless.png", null)).selectTitleLogoPath("en-US", "id", preferOriginal = false),
        )
    }
}
