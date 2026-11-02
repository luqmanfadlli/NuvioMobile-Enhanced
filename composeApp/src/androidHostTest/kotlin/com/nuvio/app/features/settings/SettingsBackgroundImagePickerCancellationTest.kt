package com.nuvio.app.features.settings

import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class SettingsBackgroundImagePickerCancellationTest {
    @Test
    fun cancelledImageReadIsRethrownInsteadOfReportedAsAnImageError() {
        val cancellation = CancellationException("screen disposed")

        val thrown = assertFailsWith<CancellationException> {
            runBlocking {
                readSettingsBackgroundImageResult<String> {
                    throw cancellation
                }
            }
        }

        assertSame(cancellation, thrown)
    }

    @Test
    fun actualImageReadFailureIsReturnedForErrorUi() {
        val result = runBlocking {
            readSettingsBackgroundImageResult<String> {
                error("decode failed")
            }
        }

        assertTrue(result.isFailure)
        assertEquals("decode failed", result.exceptionOrNull()?.message)
    }

    @Test
    fun successfulImageReadPassesThroughUnchanged() {
        val result = runBlocking {
            readSettingsBackgroundImageResult { "normalized-image" }
        }

        assertEquals("normalized-image", result.getOrThrow())
    }
}
