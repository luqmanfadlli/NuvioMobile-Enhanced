package com.nuvio.app.features.player.anime

import com.nuvio.app.features.player.isAnimeContent
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AnimeContentDetectionTest {
    @Test
    fun animeIdsAreAnime() {
        assertTrue(isAnimeContent("kitsu:1376:5", emptyList(), null))
        assertTrue(isAnimeContent("mal:5114", emptyList(), null))
        assertTrue(isAnimeContent("anilist:21", emptyList(), null))
    }

    @Test
    fun animeGenreIsAnime() {
        assertTrue(isAnimeContent("tt0388629", listOf("Action", "ANIME"), null))
    }

    @Test
    fun japaneseAnimationIsAnime() {
        assertTrue(isAnimeContent("tt5311514", listOf("Animation", "Drama"), "JP"))
        assertTrue(isAnimeContent("tt5311514", listOf("Animation"), "US, Japan"))
    }

    @Test
    fun otherContentIsNotAnime() {
        assertFalse(isAnimeContent("tt0114709", listOf("Animation", "Family"), "US"))
        assertFalse(isAnimeContent("tt0133093", listOf("Action", "Sci-Fi"), "US"))
        assertFalse(isAnimeContent(null, emptyList(), "Japan"))
    }
}
