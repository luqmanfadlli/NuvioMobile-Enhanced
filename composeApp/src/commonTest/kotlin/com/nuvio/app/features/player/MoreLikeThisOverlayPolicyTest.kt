package com.nuvio.app.features.player

import kotlin.test.Test
import kotlin.test.assertEquals

class MoreLikeThisOverlayPolicyTest {
    @Test
    fun `movie recommendations appear only after playback ends`() {
        assertEquals(true, shouldShowMoreLikeThisOverlay(true, true, false, false, false, false, false, false, false))
        assertEquals(false, shouldShowMoreLikeThisOverlay(false, true, false, false, false, false, false, false, false))
    }

    @Test
    fun `series recommendations wait until no next episode is available`() {
        assertEquals(false, shouldShowMoreLikeThisOverlay(true, true, true, true, false, false, false, false, false))
        assertEquals(true, shouldShowMoreLikeThisOverlay(true, true, true, false, false, false, false, false, false))
    }

    @Test
    fun `recommendations are suppressed when unavailable dismissed locked errored in pip or rating prompt is visible`() {
        assertEquals(false, shouldShowMoreLikeThisOverlay(true, false, false, false, false, false, false, false, false))
        assertEquals(false, shouldShowMoreLikeThisOverlay(true, true, false, false, true, false, false, false, false))
        assertEquals(false, shouldShowMoreLikeThisOverlay(true, true, false, false, false, true, false, false, false))
        assertEquals(false, shouldShowMoreLikeThisOverlay(true, true, false, false, false, false, true, false, false))
        assertEquals(false, shouldShowMoreLikeThisOverlay(true, true, false, false, false, false, false, true, false))
        assertEquals(false, shouldShowMoreLikeThisOverlay(true, true, false, false, false, false, false, false, true))
    }
}
