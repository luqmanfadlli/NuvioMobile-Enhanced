package com.nuvio.app.features.player

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayerTitleArtworkTest {

    @Test
    fun `title logo setting defaults off to preserve the existing title`() {
        assertFalse(PlayerSettingsUiState().showPlayerTitleLogo)
    }

    @Test
    fun `keeps the original title visible while the logo is loading`() {
        assertFalse(
            showPlayerTitleLogoArtwork(
                enabled = true,
                logoUrl = "https://image.example/logo.png",
                imageLoaded = false,
            ),
        )
    }

    @Test
    fun `uses title artwork only after a successful image request`() {
        assertTrue(
            showPlayerTitleLogoArtwork(
                enabled = true,
                logoUrl = "https://image.example/logo.png",
                imageLoaded = true,
            ),
        )
    }

    @Test
    fun `does not use artwork when disabled or when the URL is missing`() {
        assertFalse(showPlayerTitleLogoArtwork(false, "https://image.example/logo.png", true))
        assertFalse(showPlayerTitleLogoArtwork(true, null, true))
        assertFalse(showPlayerTitleLogoArtwork(true, "   ", true))
    }

    @Test
    fun `falls back to the title when the image request fails`() {
        assertFalse(
            showPlayerTitleLogoArtwork(
                enabled = true,
                logoUrl = "https://image.example/logo.png",
                imageLoaded = false,
            ),
        )
    }
}
