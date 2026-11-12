package com.nuvio.app.features.player

import com.nuvio.app.core.sync.decodeSyncString
import com.nuvio.app.core.sync.encodeSyncInt
import com.nuvio.app.core.sync.encodeSyncString
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import nuvio.composeapp.generated.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SubtitleAppearancePlatformTest {
    @Test
    fun appearancePersistsLocallyAndIsExcludedFromSharedProfileSync() {
        val original = PlayerSettingsStorage.exportToSyncPayload()
        val testProfileId = Int.MAX_VALUE - 17
        val originalDevicePreferences = SubtitleDevicePreferencesStorage.load(testProfileId)
        try {
            val style = SubtitleStyleState(
                fontSizeSp = 30,
                appearance = SubtitleAppearance("mono", 18, true, 60, 4),
            )
            SubtitleDevicePreferencesRepository.save(testProfileId, style)
            PlayerSettingsStorage.savePreferredSubtitleLanguage("en")

            val payload = PlayerSettingsStorage.exportToSyncPayload()
            assertNull(payload["subtitle_appearance"])
            assertNull(payload["subtitle_font_size_sp"])
            assertEquals("en", payload.decodeSyncString("preferred_subtitle_language"))

            val legacyRemotePayload = buildJsonObject {
                put("subtitle_appearance", encodeSyncString(SubtitleAppearance("serif").encode()))
                put("subtitle_font_size_sp", encodeSyncInt(16))
            }
            PlayerSettingsStorage.replaceFromSyncPayload(legacyRemotePayload)

            assertEquals(
                style,
                SubtitleDevicePreferencesRepository.loadOrMigrate(testProfileId, SubtitleStyleState.DEFAULT),
            )
        } finally {
            PlayerSettingsStorage.replaceFromSyncPayload(original)
            if (originalDevicePreferences == null) {
                SubtitleDevicePreferencesStorage.remove(testProfileId)
            } else {
                SubtitleDevicePreferencesStorage.save(testProfileId, originalDevicePreferences)
            }
        }
    }

    @OptIn(ExperimentalResourceApi::class)
    @Test
    fun importedFontSurvivesReloadAndDuplicateImport() = runTest {
        val bytes = Res.readBytes("font/jetbrains_sans_regular.ttf")
        val original = SubtitleFontStorage.read("fonts.json")
        val existingIds = SubtitleFonts.installed().map { it.id }
        val font = SubtitleFonts.import("Custom.ttf", bytes)
        try {
            assertEquals(font, SubtitleFonts.installed().single { it.id == font.id })
            assertEquals(font, SubtitleFonts.import("Renamed.ttf", bytes))
            assertNotNull(SubtitleFontStorage.fontFamily(font.id))
            SubtitleFonts.remove(font.id)
            assertNull(SubtitleFontStorage.read(font.id))
        } finally {
            if (font.id in existingIds) SubtitleFontStorage.write(font.id, bytes) else SubtitleFontStorage.remove(font.id)
            if (original == null) SubtitleFontStorage.remove("fonts.json") else SubtitleFontStorage.write("fonts.json", original)
        }
    }
}
