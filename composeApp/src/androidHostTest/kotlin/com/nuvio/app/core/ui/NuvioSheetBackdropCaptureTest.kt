package com.nuvio.app.core.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NuvioSheetBackdropCaptureTest {
    @Test
    fun studioPreviewCapturesLocalBackdropWhenMasterSwitchIsOff() {
        assertTrue(
            shouldCaptureNuvioSheetBackdrop(
                platformSupported = true,
                masterEnabled = false,
                needsPreviewBackdrop = true,
                needsSheetGlassBackdrop = false,
            ),
        )
    }

    @Test
    fun runtimeSheetStillRequiresMasterSwitch() {
        assertFalse(
            shouldCaptureNuvioSheetBackdrop(
                platformSupported = true,
                masterEnabled = false,
                needsPreviewBackdrop = false,
                needsSheetGlassBackdrop = true,
            ),
        )
        assertTrue(
            shouldCaptureNuvioSheetBackdrop(
                platformSupported = true,
                masterEnabled = true,
                needsPreviewBackdrop = false,
                needsSheetGlassBackdrop = true,
            ),
        )
    }

    @Test
    fun unsupportedPlatformNeverCapturesBackdropEvenForPreview() {
        assertFalse(
            shouldCaptureNuvioSheetBackdrop(
                platformSupported = false,
                masterEnabled = false,
                needsPreviewBackdrop = true,
                needsSheetGlassBackdrop = false,
            ),
        )
        assertFalse(
            shouldCaptureNuvioSheetBackdrop(
                platformSupported = false,
                masterEnabled = true,
                needsPreviewBackdrop = false,
                needsSheetGlassBackdrop = true,
            ),
        )
    }

    @Test
    fun inactiveSheetWithoutPreviewDoesNotCaptureBackdrop() {
        assertFalse(
            shouldCaptureNuvioSheetBackdrop(
                platformSupported = true,
                masterEnabled = true,
                needsPreviewBackdrop = false,
                needsSheetGlassBackdrop = false,
            ),
        )
    }
}
