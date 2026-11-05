package com.nuvio.app.features.player

import com.nuvio.app.core.storage.ProfileScopedKey
import com.nuvio.app.core.sync.decodeSyncBoolean
import com.nuvio.app.features.profiles.ProfileRepository
import kotlinx.serialization.json.buildJsonObject
import platform.Foundation.NSUserDefaults
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlayerTitleArtworkStoragePlatformTest {

    @Test
    fun `title logo preference reloads syncs and remains profile scoped`() {
        val defaults = NSUserDefaults.standardUserDefaults
        val otherProfileKey = ProfileScopedKey.of("show_player_title_logo", 99)
        val hadOtherProfileValue = defaults.objectForKey(otherProfileKey) != null
        val previousOtherProfileValue = defaults.boolForKey(otherProfileKey)
        ProfileRepository.clearInMemory()
        PlayerSettingsRepository.clearLocalState()
        val originalPayload = PlayerSettingsStorage.exportToSyncPayload()

        try {
            PlayerSettingsRepository.setShowPlayerTitleLogo(true)
            PlayerSettingsRepository.clearLocalState()
            PlayerSettingsRepository.ensureLoaded()
            assertTrue(PlayerSettingsRepository.uiState.value.showPlayerTitleLogo)

            val payload = PlayerSettingsStorage.exportToSyncPayload()
            assertEquals(true, payload.decodeSyncBoolean("show_player_title_logo"))

            PlayerSettingsStorage.saveShowPlayerTitleLogo(false)
            PlayerSettingsStorage.replaceFromSyncPayload(payload)
            assertEquals(true, PlayerSettingsStorage.loadShowPlayerTitleLogo())

            defaults.setBool(true, forKey = otherProfileKey)
            PlayerSettingsStorage.replaceFromSyncPayload(buildJsonObject {})
            assertNull(PlayerSettingsStorage.loadShowPlayerTitleLogo())
            assertTrue(defaults.boolForKey(otherProfileKey))
        } finally {
            PlayerSettingsStorage.replaceFromSyncPayload(originalPayload)
            if (hadOtherProfileValue) {
                defaults.setBool(previousOtherProfileValue, forKey = otherProfileKey)
            } else {
                defaults.removeObjectForKey(otherProfileKey)
            }
            PlayerSettingsRepository.clearLocalState()
            ProfileRepository.clearInMemory()
        }
    }
}
