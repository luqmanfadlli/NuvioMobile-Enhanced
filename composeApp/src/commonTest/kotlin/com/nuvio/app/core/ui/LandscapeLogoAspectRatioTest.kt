package com.nuvio.app.core.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LandscapeLogoAspectRatioTest {
    @Test
    fun wideArtworkIsAcceptedAsALogo() {
        assertTrue(isLandscapeLogo(width = 1200, height = 400))
    }

    @Test
    fun portraitAndSquareArtworkAreRejectedAsLogos() {
        assertFalse(isLandscapeLogo(width = 400, height = 1200))
        assertFalse(isLandscapeLogo(width = 800, height = 800))
    }

    @Test
    fun invalidDimensionsAreRejected() {
        assertFalse(isLandscapeLogo(width = 0, height = 400))
        assertFalse(isLandscapeLogo(width = 1200, height = 0))
    }
}
