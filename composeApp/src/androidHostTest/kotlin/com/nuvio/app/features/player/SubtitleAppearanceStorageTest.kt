package com.nuvio.app.features.player

import android.app.Application
import android.content.Context
import androidx.compose.ui.graphics.Color
import com.nuvio.app.core.storage.LocalAccountDataCleaner
import com.nuvio.app.core.storage.ProfileScopedKey
import com.nuvio.app.core.sync.decodeSyncString
import com.nuvio.app.core.sync.encodeSyncInt
import com.nuvio.app.core.sync.encodeSyncString
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SubtitleAppearanceStorageTest {
    @BeforeTest
    fun initialize() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("nuvio_player_settings", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("nuvio_subtitle_device_preferences", Context.MODE_PRIVATE).edit().clear().commit()
        PlayerSettingsStorage.initialize(context)
        PlayerSettingsRepository.clearLocalState()
    }

    @AfterTest
    fun clear() { PlayerSettingsRepository.clearLocalState() }

    @Test
    fun appearanceSurvivesRestartAndIsExcludedFromSharedProfileSync() {
        val style = SubtitleStyleState(
            backgroundColor = Color.Black.copy(alpha = 0.6f),
            appearance = SubtitleAppearance("serif", 12, true, 65, 4, outlineBlur = 6),
        )
        PlayerSettingsRepository.setSubtitleStyle(style)
        PlayerSettingsRepository.clearLocalState()
        PlayerSettingsRepository.ensureLoaded()
        assertEquals(style, PlayerSettingsRepository.uiState.value.subtitleStyle)

        PlayerSettingsStorage.savePreferredSubtitleLanguage("en")
        val payload = PlayerSettingsStorage.exportToSyncPayload()
        assertNull(payload.decodeSyncString("subtitle_appearance"))
        assertNull(payload.decodeSyncString("subtitle_text_color"))
        assertNull(payload.decodeSyncString("subtitle_font_size_sp"))
        assertEquals("en", payload.decodeSyncString("preferred_subtitle_language"))

        // A legacy remote blob may still contain globally synchronized subtitle fields.
        // Applying it must no longer replace the new installation-local preference record.
        val legacyPayload = buildJsonObject {
            put("subtitle_appearance", encodeSyncString(SubtitleAppearance(fontId = "mono").encode()))
            put("subtitle_font_size_sp", encodeSyncInt(16))
        }
        PlayerSettingsStorage.replaceFromSyncPayload(legacyPayload)
        PlayerSettingsRepository.onProfileChanged()
        assertEquals(style, PlayerSettingsRepository.uiState.value.subtitleStyle)
    }

    @Test
    fun subtitlePreferencesAreProfileScopedAndSurviveAccountStorageWipe() {
        val first = SubtitleStyleState(fontSizeSp = 14)
        val second = SubtitleStyleState(fontSizeSp = 30)
        SubtitleDevicePreferencesRepository.save(1, first)
        SubtitleDevicePreferencesRepository.save(2, second)

        assertEquals(first, SubtitleDevicePreferencesRepository.loadOrMigrate(1, SubtitleStyleState.DEFAULT))
        assertEquals(second, SubtitleDevicePreferencesRepository.loadOrMigrate(2, SubtitleStyleState.DEFAULT))

        LocalAccountDataCleaner.wipePlatformStorage()

        assertEquals(first, SubtitleDevicePreferencesRepository.loadOrMigrate(1, SubtitleStyleState.DEFAULT))
        assertEquals(second, SubtitleDevicePreferencesRepository.loadOrMigrate(2, SubtitleStyleState.DEFAULT))
    }

    @Test
    fun legacySubtitlePreferencesForEveryProfileAreMigratedBeforeLegacyStorageIsWiped() {
        PlayerSettingsStorage.saveSubtitleFontSizeSp(26)
        PlayerSettingsStorage.saveSubtitleAppearance(SubtitleAppearance(fontId = "serif").encode())

        // Profile 2 is inactive. Its legacy values must also be preserved before
        // the platform cleaner removes the shared player-settings namespace.
        val preferences = RuntimeEnvironment.getApplication()
            .getSharedPreferences("nuvio_player_settings", Context.MODE_PRIVATE)
        preferences.edit()
            .putInt(ProfileScopedKey.of("subtitle_font_size_sp", 2), 34)
            .putString(ProfileScopedKey.of("subtitle_appearance", 2), SubtitleAppearance(fontId = "mono").encode())
            .commit()

        LocalAccountDataCleaner.wipePlatformStorage()

        PlayerSettingsRepository.clearLocalState()
        PlayerSettingsRepository.ensureLoaded()
        assertEquals(26, PlayerSettingsRepository.uiState.value.subtitleStyle.fontSizeSp)
        assertEquals("serif", PlayerSettingsRepository.uiState.value.subtitleStyle.appearance.fontId)

        val inactiveProfile = SubtitleDevicePreferencesRepository.loadOrMigrate(2, SubtitleStyleState.DEFAULT)
        assertEquals(34, inactiveProfile.fontSizeSp)
        assertEquals("mono", inactiveProfile.appearance.fontId)
    }
}
