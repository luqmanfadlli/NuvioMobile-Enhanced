package com.nuvio.app.features.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayerToolbarLayoutTest {
    @Test
    fun toolbarLayoutRoundTripsThreeStatePlacementAlignmentAndOrder() {
        assertTrue(PlayerToolbarLayout.Default.showOverflowArrow)
        assertTrue(PlayerToolbarButton.EPISODES in PlayerToolbarLayout.Default.overflowButtons)

        val custom = PlayerToolbarLayout.Default
            .withPlacement(PlayerToolbarButton.EPISODES, PlayerToolbarPlacement.BAR)
            .withPlacement(PlayerToolbarButton.NIGHT_MODE, PlayerToolbarPlacement.HIDDEN)
            .withAlignment(PlayerControlBarAlignment.CENTER)
            .moved(0, 2)

        val encoded = custom.encode()
        val decoded = PlayerToolbarLayout.decode(encoded)

        assertEquals(PlayerControlBarAlignment.CENTER, decoded.alignment)
        assertTrue(decoded.showOverflowArrow)
        assertTrue(PlayerToolbarButton.EPISODES in decoded.visibleButtons)
        assertTrue(PlayerToolbarButton.NIGHT_MODE in decoded.hiddenButtons)
        assertFalse(PlayerToolbarButton.NIGHT_MODE in decoded.visibleButtons)
        assertFalse(PlayerToolbarButton.NIGHT_MODE in decoded.overflowButtons)
        assertEquals(custom.entries, decoded.entries)

        val allOff = decoded.entries.fold(decoded) { acc, entry ->
            if (entry.placement == PlayerToolbarPlacement.OVERFLOW) {
                acc.withPlacement(entry.button, PlayerToolbarPlacement.HIDDEN)
            } else {
                acc
            }
        }
        val decodedAllOff = PlayerToolbarLayout.decode(allOff.encode())
        assertFalse(decodedAllOff.showOverflowArrow)

        val legacyOverflowDecoded = PlayerToolbarLayout.decode("speed:v,subtitles:v,resize:h")
        assertEquals(PlayerControlBarAlignment.LEFT, legacyOverflowDecoded.alignment)
        assertTrue(legacyOverflowDecoded.showOverflowArrow)
        assertTrue(PlayerToolbarButton.RESIZE in legacyOverflowDecoded.overflowButtons)
        assertEquals(PlayerToolbarButton.SPEED, legacyOverflowDecoded.visibleButtons.first())

        val legacyHiddenDecoded = PlayerToolbarLayout.decode("speed:v,subtitles:v,resize:h;align=right;overflow=0")
        assertEquals(PlayerControlBarAlignment.RIGHT, legacyHiddenDecoded.alignment)
        assertTrue(PlayerToolbarButton.RESIZE in legacyHiddenDecoded.hiddenButtons)
    }
}
