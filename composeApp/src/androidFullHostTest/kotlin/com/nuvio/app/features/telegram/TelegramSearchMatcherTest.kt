package com.nuvio.app.features.telegram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TelegramSearchMatcherTest {
    @Test fun primaryTitleMatchesWhenSubtitleIsOmitted() {
        assertTrue(TelegramSearchMatcher.score("Ramayana 2026 1080p.mkv", "", "Ramayana: Rise of a Legend", 2026, null, null, "movie") >= 75)
    }

    @Test fun punctuationAndSeparatorsAreNormalized() {
        assertTrue(TelegramSearchMatcher.score("The.Lord_of-the.Rings.2001.mkv", "", "The Lord of the Rings", 2001, null, null, "movie") >= 90)
    }

    @Test fun captionCanSupplyMissingTitleTokens() {
        assertTrue(
            TelegramSearchMatcher.score(
                "Ramayana.2026.1080p.mkv",
                "Rise of a Legend",
                "Ramayana: Rise of a Legend",
                2026, null, null, "movie",
            ) >= 75,
        )
    }

    @Test fun matchingEpisodeConventionIsAccepted() {
        assertTrue(
            TelegramSearchMatcher.score(
                "Example.Show.1x03.1080p.mkv", "", "Example Show",
                null, 1, 3, "tv",
            ) > 0,
        )
    }

    @Test fun conflictingMovieYearIsRejected() {
        assertEquals(0, TelegramSearchMatcher.score("Ramayana 2024 1080p.mkv", "", "Ramayana", 2026, null, null, "movie"))
    }

    @Test fun seriesStillRequiresCorrectEpisode() {
        assertEquals(0, TelegramSearchMatcher.score("Example.Show.S01E02.1080p.mkv", "", "Example Show", null, 1, 3, "tv"))
    }

    @Test fun queryBuilderIncludesShortPrimaryTitleFallback() {
        val queries = TelegramSearchMatcher.buildQueries("Ramayana: Rise of a Legend", 2026, null, null, "movie")
        assertTrue(queries.contains("Ramayana"))
        assertTrue(queries.contains("Ramayana Rise of a Legend"))
    }
}
