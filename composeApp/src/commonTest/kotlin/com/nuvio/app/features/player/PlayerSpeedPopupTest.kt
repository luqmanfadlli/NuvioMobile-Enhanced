package com.nuvio.app.features.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlayerSpeedPopupTest {
    @Test
    fun nativeMenuIdsRoundTripOnlySupportedPlaybackSpeeds() {
        PlayerPlaybackSpeedOptions.forEach { speed ->
            assertEquals(speed, parsePlayerPlaybackSpeedOption(speed.toString()))
        }
        assertNull(parsePlayerPlaybackSpeedOption("3.0"))
        assertNull(parsePlayerPlaybackSpeedOption("NaN"))
        assertNull(parsePlayerPlaybackSpeedOption("Infinity"))
        assertNull(parsePlayerPlaybackSpeedOption("not-a-speed"))
    }

    @Test
    fun currentSpeedSelectionKeepsTheExistingTolerance() {
        assertEquals(1f.toString(), playerPlaybackSpeedOptionId(1.005f))
        assertNull(playerPlaybackSpeedOptionId(1.02f))
    }
}
