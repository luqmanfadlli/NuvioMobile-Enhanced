package com.nuvio.app.features.player

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SubtitleDevicePreferencesTest {
    @Test
    fun completeSubtitleStyleRoundTripsThroughVersionedRecord() {
        val expected = SubtitleStyleState(
            textColor = Color(0xCCF0E0D0),
            backgroundColor = Color(0x80112233),
            outlineColor = Color(0xFF123456),
            outlineEnabled = false,
            outlineWidth = 4,
            bold = true,
            fontSizeSp = 30,
            bottomOffset = 72,
            stripSdh = true,
            useForcedSubtitles = true,
            showOnlyPreferredLanguages = true,
            appearance = SubtitleAppearance(
                fontId = "serif",
                backgroundRadius = 12,
                shadowEnabled = true,
                shadowOpacity = 61,
                shadowSize = 5,
                outlineBlur = 7,
            ),
        )

        val restored = decodeSubtitleDevicePreferences(encodeSubtitleDevicePreferences(expected))

        assertEquals(expected, restored)
    }

    @Test
    fun unknownFieldsAreIgnoredAndInvalidFontIdsFallBackSafely() {
        val restored = decodeSubtitleDevicePreferences(
            """{"version":1,"fontSizeSp":30,"appearance":{"fontId":"../../font.ttf"},"futureField":true}""",
        )

        assertEquals(30, restored?.fontSizeSp)
        assertEquals("default", restored?.appearance?.fontId)
    }

    @Test
    fun invalidRecordsAreRejectedForLegacyMigration() {
        assertNull(decodeSubtitleDevicePreferences(null))
        assertNull(decodeSubtitleDevicePreferences(""))
        assertNull(decodeSubtitleDevicePreferences("{"))
        assertNull(decodeSubtitleDevicePreferences("""{"version":2,"fontSizeSp":30}"""))
    }

    @Test
    fun valuesAreClampedToSupportedPlatformRanges() {
        val restored = decodeSubtitleDevicePreferences(
            """{"version":1,"fontSizeSp":999,"bottomOffset":999,"outlineWidth":-2}""",
        )

        assertEquals(subtitleFontSizeRangeSp.last, restored?.fontSizeSp)
        assertEquals(200, restored?.bottomOffset)
        assertEquals(0, restored?.outlineWidth)
    }
}
