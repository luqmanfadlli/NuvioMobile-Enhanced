package com.nuvio.app.features.player

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayerTitleArtworkTest {
    @Test
    fun `uses title artwork only when enabled and a URL exists`() {
        assertTrue(showPlayerTitleLogoArtwork(true, "https://image.example/logo.png", false))
        assertFalse(showPlayerTitleLogoArtwork(false, "https://image.example/logo.png", false))
        assertFalse(showPlayerTitleLogoArtwork(true, null, false))
        assertFalse(showPlayerTitleLogoArtwork(true, "   ", false))
    }

    @Test
    fun `falls back to text after the logo request fails`() {
        assertFalse(showPlayerTitleLogoArtwork(true, "https://image.example/logo.png", true))
    }
}
