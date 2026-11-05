package com.nuvio.app.features.player

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayerTitleArtworkTest {

    @Test
    fun `shows artwork only when enabled, available, and successfully loaded`() {
        assertTrue(
            showPlayerTitleLogoArtwork(
                enabled = true,
                logoUrl = "https://image.example/logo.png",
                imageLoaded = true,
            ),
        )
        assertFalse(
            showPlayerTitleLogoArtwork(
                enabled = true,
                logoUrl = "https://image.example/logo.png",
                imageLoaded = false,
            ),
        )
        assertFalse(
            showPlayerTitleLogoArtwork(
                enabled = false,
                logoUrl = "https://image.example/logo.png",
                imageLoaded = true,
            ),
        )
        assertFalse(showPlayerTitleLogoArtwork(true, null, true))
        assertFalse(showPlayerTitleLogoArtwork(true, "   ", true))
    }
}
