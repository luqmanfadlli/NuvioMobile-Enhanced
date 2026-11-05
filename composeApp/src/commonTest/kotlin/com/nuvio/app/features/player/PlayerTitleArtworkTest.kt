package com.nuvio.app.features.player

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayerTitleArtworkTest {

    @Test
    fun `artwork visibility and reserved slot sizing account for fallback text`() {
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

        assertEquals(64.dp, playerTitleArtworkSlotHeight(64.dp, 32.dp, 2))
        assertEquals(66.dp, playerTitleArtworkSlotHeight(64.dp, 33.dp, 2))
        assertEquals(26.dp, playerTitleArtworkSlotHeight(26.dp, 18.dp, 1))
    }
}
