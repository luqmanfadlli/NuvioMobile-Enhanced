package com.nuvio.app.features.library

import androidx.compose.ui.unit.dp
import androidx.compose.material3.OutlinedTextFieldDefaults
import com.nuvio.app.core.ui.glass.LiquidGlassSurface
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LibraryGlassLayoutTest {
    @Test
    fun calendarInnerSurfaceUsesTranslucencyOnlyWhenGlassAndBackdropAreReady() {
        assertTrue(shouldRenderLibraryCalendarGlass(surfaceEnabled = true, backdropReady = true))
        assertFalse(shouldRenderLibraryCalendarGlass(surfaceEnabled = true, backdropReady = false))
        assertFalse(shouldRenderLibraryCalendarGlass(surfaceEnabled = false, backdropReady = true))
        assertFalse(shouldRenderLibraryCalendarGlass(surfaceEnabled = false, backdropReady = false))
    }

    @Test
    fun calendarBackdropCaptureRequiresAnEnabledSurfaceAndSupportedRenderer() {
        assertTrue(
            libraryBackdropCaptureRequired(masterEnabled = true, platformSupported = true) {
                it == LiquidGlassSurface.LIBRARY_CALENDAR
            },
        )
        assertFalse(
            libraryBackdropCaptureRequired(masterEnabled = false, platformSupported = true) { true },
        )
        assertFalse(
            libraryBackdropCaptureRequired(masterEnabled = true, platformSupported = false) { true },
        )
        assertFalse(
            libraryBackdropCaptureRequired(masterEnabled = true, platformSupported = true) { false },
        )
    }

    @Test
    fun liveCalendarUsesLibraryContentBackdropOnlyWhenGlassIsEnabledInSavedMode() {
        assertTrue(
            shouldUseLibraryContentBackdropForCalendar(
                sourceMode = LibraryViewMode.Saved,
                calendarGlassEnabled = true,
            ),
        )
        assertFalse(
            shouldUseLibraryContentBackdropForCalendar(
                sourceMode = LibraryViewMode.Saved,
                calendarGlassEnabled = false,
            ),
        )
        assertFalse(
            shouldUseLibraryContentBackdropForCalendar(
                sourceMode = LibraryViewMode.Cloud,
                calendarGlassEnabled = true,
            ),
        )
    }

    @Test
    fun savedHeaderScrimIsLimitedToSavedModeAndEnabledLibraryGlass() {
        assertTrue(
            librarySavedHeaderUsesGlassScrim(
                sourceMode = LibraryViewMode.Saved,
                librarySurfaceGlassEnabled = true,
                savedTypeGlassEnabled = false,
                savedSortGlassEnabled = false,
            ),
        )
        assertTrue(
            librarySavedHeaderUsesGlassScrim(
                sourceMode = LibraryViewMode.Saved,
                librarySurfaceGlassEnabled = false,
                savedTypeGlassEnabled = true,
                savedSortGlassEnabled = false,
            ),
        )
        assertTrue(
            librarySavedHeaderUsesGlassScrim(
                sourceMode = LibraryViewMode.Saved,
                librarySurfaceGlassEnabled = false,
                savedTypeGlassEnabled = false,
                savedSortGlassEnabled = true,
            ),
        )
        assertFalse(
            librarySavedHeaderUsesGlassScrim(
                sourceMode = LibraryViewMode.Saved,
                librarySurfaceGlassEnabled = false,
                savedTypeGlassEnabled = false,
                savedSortGlassEnabled = false,
            ),
        )
        assertFalse(
            librarySavedHeaderUsesGlassScrim(
                sourceMode = LibraryViewMode.Cloud,
                librarySurfaceGlassEnabled = true,
                savedTypeGlassEnabled = true,
                savedSortGlassEnabled = true,
            ),
        )
    }

    @Test
    fun savedHeaderScrimStopsBeforeTheTitleHeadersBottomSpacing() {
        assertEquals(4.dp, librarySavedHeaderScrimBottomInset(scrimEnabled = true))
        assertEquals(0.dp, librarySavedHeaderScrimBottomInset(scrimEnabled = false))
    }

    @Test
    fun artworkHeaderFadeIsOnlyUsedForCloudLibraryWithAnActiveBackdrop() {
        assertTrue(
            libraryHeaderUsesArtworkBackdrop(
                sourceMode = LibraryViewMode.Cloud,
                artworkBackgroundActive = true,
            ),
        )
        assertFalse(
            libraryHeaderUsesArtworkBackdrop(
                sourceMode = LibraryViewMode.Saved,
                artworkBackgroundActive = true,
            ),
        )
        assertFalse(
            libraryHeaderUsesArtworkBackdrop(
                sourceMode = LibraryViewMode.Cloud,
                artworkBackgroundActive = false,
            ),
        )
    }

    @Test
    fun savedTitleHeaderKeepsItsOpaqueBackingScopedToTheTitle() {
        assertTrue(libraryTitleHeaderNeedsOpaqueBackground(LibraryViewMode.Saved))
        assertFalse(libraryTitleHeaderNeedsOpaqueBackground(LibraryViewMode.Cloud))
        assertFalse(libraryTitleHeaderNeedsOpaqueBackground(LibraryViewMode.Servers))
    }

    @Test
    fun savedPinnedGlassChromeUsesScrollingContentBackdropOnlyInSavedMode() {
        assertTrue(
            libraryPinnedChromeUsesContentBackdrop(
                sourceMode = LibraryViewMode.Saved,
                glassEnabled = true,
            ),
        )
        assertFalse(
            libraryPinnedChromeUsesContentBackdrop(
                sourceMode = LibraryViewMode.Saved,
                glassEnabled = false,
            ),
        )
        assertFalse(
            libraryPinnedChromeUsesContentBackdrop(
                sourceMode = LibraryViewMode.Cloud,
                glassEnabled = true,
            ),
        )
        assertFalse(
            libraryPinnedChromeUsesContentBackdrop(
                sourceMode = LibraryViewMode.Servers,
                glassEnabled = true,
            ),
        )
    }

    @Test
    fun savedFilterOverlayReservesInitialListSpaceWheneverControlsAreVisible() {
        assertFalse(
            librarySavedControlsNeedContentInset(
                hasSavedControls = false,
            ),
        )
        assertTrue(
            librarySavedControlsNeedContentInset(
                hasSavedControls = true,
            ),
        )
    }

    @Test
    fun librarySourceSwitchMatchesSearchControlHeightOnNativeAndComposePaths() {
        assertEquals(
            48.dp,
            librarySourceSwitchMinimumHeight(nativeSegmentedControlEnabled = true),
        )
        assertEquals(
            OutlinedTextFieldDefaults.MinHeight,
            librarySourceSwitchMinimumHeight(nativeSegmentedControlEnabled = false),
        )
    }

    @Test
    fun savedScrollBackdropReservesToolbarAndPinnedControlsBeforeFirstPoster() {
        assertEquals(
            120.dp,
            libraryContentTopPadding(
                headerHeight = 48.dp,
                hasSavedControls = true,
                savedControlsInset = 72.dp,
            ),
        )
        assertEquals(
            48.dp,
            libraryContentTopPadding(
                headerHeight = 48.dp,
                hasSavedControls = false,
                savedControlsInset = 72.dp,
            ),
        )
    }

}
