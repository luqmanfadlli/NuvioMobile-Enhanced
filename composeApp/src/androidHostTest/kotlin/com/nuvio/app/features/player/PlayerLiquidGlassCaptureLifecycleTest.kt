package com.nuvio.app.features.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayerLiquidGlassCaptureLifecycleTest {
    @Test
    fun captureBacksOffOnlyAfterConsecutiveUnavailableFrames() {
        val policy = VideoFrameCapturePolicy(unavailableFrameLimit = 3)

        assertEquals(33L, policy.delayMs)
        repeat(2) {
            policy.recordUnavailableFrame()
        }
        assertFalse(policy.shouldUseSlowRetry)
        assertEquals(33L, policy.delayMs)

        policy.recordUnavailableFrame()
        assertTrue(policy.shouldUseSlowRetry)
        assertEquals(1_000L, policy.delayMs)
    }

    @Test
    fun usableFrameImmediatelyRestoresResponsiveCaptureRate() {
        val policy = VideoFrameCapturePolicy(unavailableFrameLimit = 3)
        repeat(3) {
            policy.recordUnavailableFrame()
        }
        assertTrue(policy.shouldUseSlowRetry)

        policy.recordUsableFrame()

        assertFalse(policy.shouldUseSlowRetry)
        assertEquals(33L, policy.delayMs)
    }

    @Test
    fun repeatedUnavailableFramesDoNotOverflowTheBackoffCounter() {
        val policy = VideoFrameCapturePolicy(unavailableFrameLimit = 2)
        repeat(100_000) {
            policy.recordUnavailableFrame()
        }

        assertTrue(policy.shouldUseSlowRetry)
        assertEquals(1_000L, policy.delayMs)
    }

    @Test
    fun backdropConsumerLeasesAreReferenceCountedAndNeverNegative() {
        val backdrop = VideoFrameBackdrop()

        assertEquals(0, backdrop.consumers)
        backdrop.acquire()
        backdrop.acquire()
        assertEquals(2, backdrop.consumers)

        backdrop.release()
        assertEquals(1, backdrop.consumers)
        backdrop.release()
        assertEquals(0, backdrop.consumers)
        backdrop.release()
        assertEquals(0, backdrop.consumers)
    }
}
