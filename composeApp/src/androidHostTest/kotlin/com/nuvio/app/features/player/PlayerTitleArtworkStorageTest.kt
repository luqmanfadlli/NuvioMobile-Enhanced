package com.nuvio.app.features.player

import android.app.Application
import android.content.Context
import com.nuvio.app.core.storage.ProfileScopedKey
import com.nuvio.app.core.sync.decodeSyncBoolean
import com.nuvio.app.features.profiles.ProfileRepository
import kotlinx.serialization.json.buildJsonObject
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PlayerTitleArtworkStorageTest {

    private lateinit var preferences: android.content.SharedPreferences

    @BeforeTest
    fun initialize() {
        val context = RuntimeEnvironment.getApplication()
        preferences = context.getSharedPreferences("nuvio_player_settings", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        ProfileRepository.clearInMemory()
        PlayerSettingsStorage.initialize(context)
        PlayerSettingsRepository.clearLocalState()
    }

    @AfterTest
    fun clear() {
        PlayerSettingsRepository.clearLocalState()
        ProfileRepository.clearInMemory()
    }

    @Test
    fun `title logo preference survives repository reload`() {
        PlayerSettingsRepository.setShowPlayerTitleLogo(true)

        PlayerSettingsRepository.clearLocalState()
        PlayerSettingsRepository.ensureLoaded()

        assertTrue(PlayerSettingsRepository.uiState.value.showPlayerTitleLogo)
    }

    @Test
    fun `title logo preference round trips through profile settings sync`() {
        PlayerSettingsStorage.saveShowPlayerTitleLogo(true)
        val payload = PlayerSettingsStorage.exportToSyncPayload()

        assertEquals(true, payload.decodeSyncBoolean("show_player_title_logo"))

        PlayerSettingsStorage.saveShowPlayerTitleLogo(false)
        PlayerSettingsStorage.replaceFromSyncPayload(payload)

        assertEquals(true, PlayerSettingsStorage.loadShowPlayerTitleLogo())
    }

    @Test
    fun `older sync clears only the active profile title logo preference`() {
        val otherProfileKey = ProfileScopedKey.of("show_player_title_logo", 99)
        preferences.edit().putBoolean(otherProfileKey, true).commit()
        PlayerSettingsStorage.saveShowPlayerTitleLogo(false)

        PlayerSettingsStorage.replaceFromSyncPayload(buildJsonObject {})

        assertNull(PlayerSettingsStorage.loadShowPlayerTitleLogo())
        assertEquals(true, preferences.getBoolean(otherProfileKey, false))
    }
}
