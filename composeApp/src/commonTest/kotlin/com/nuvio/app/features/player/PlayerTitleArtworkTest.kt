package com.nuvio.app.features.player

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayerTitleArtworkTest {

    @Test
    fun `artwork shows only once the logo has loaded`() {
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

    @Test
    fun `dark logos are detected and bright or transparent ones are not`() {
        val black = 0xFF101010.toInt()
        val white = 0xFFF0F0F0.toInt()
        val transparent = 0x00000000
        assertTrue(isDarkTitleLogo(IntArray(400) { black }))
        assertFalse(isDarkTitleLogo(IntArray(400) { white }))
        assertFalse(isDarkTitleLogo(IntArray(400) { transparent }))
        assertFalse(isDarkTitleLogo(IntArray(400) { if (it % 2 == 0) black else white }))
    }
}
