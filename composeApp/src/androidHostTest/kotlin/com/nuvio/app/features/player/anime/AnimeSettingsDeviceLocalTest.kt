package com.nuvio.app.features.player.anime

import android.app.Application
import android.content.Context
import com.nuvio.app.core.sync.encodeSyncBoolean
import com.nuvio.app.features.player.PlayerSettingsRepository
import com.nuvio.app.features.player.PlayerSettingsStorage
import kotlinx.serialization.json.buildJsonObject
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AnimeSettingsDeviceLocalTest {
    @BeforeTest
    fun initialize() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("nuvio_player_settings", Context.MODE_PRIVATE).edit().clear().commit()
        PlayerSettingsStorage.initialize(context)
        PlayerSettingsRepository.clearLocalState()
    }

    @AfterTest
    fun clearState() {
        PlayerSettingsRepository.clearLocalState()
    }

    @Test
    fun animeSettingsAreNotExportedToProfileSync() {
        PlayerSettingsStorage.saveAndroidAnimeUpscaleEnabled(true)
        PlayerSettingsStorage.saveAndroidAnimeLineSharpenEnabled(true)
        val payload = PlayerSettingsStorage.exportToSyncPayload()
        assertFalse(payload.containsKey("android_anime_upscale_enabled"))
        assertFalse(payload.containsKey("android_anime_line_sharpen_enabled"))
    }

    @Test
    fun profileSyncPullKeepsTheLocalAnimeSettings() {
        PlayerSettingsStorage.saveAndroidAnimeUpscaleEnabled(true)
        PlayerSettingsStorage.saveAndroidAnimeLineSharpenEnabled(false)
        PlayerSettingsStorage.saveAndroidLibmpvYuv420pEnabled(false)
        PlayerSettingsStorage.replaceFromSyncPayload(
            buildJsonObject {
                put("android_anime_upscale_enabled", encodeSyncBoolean(false))
                put("android_anime_line_sharpen_enabled", encodeSyncBoolean(true))
                put("android_libmpv_yuv420p_enabled", encodeSyncBoolean(true))
            },
        )
        assertEquals(true, PlayerSettingsStorage.loadAndroidLibmpvYuv420pEnabled())
        assertEquals(true, PlayerSettingsStorage.loadAndroidAnimeUpscaleEnabled())
        assertEquals(false, PlayerSettingsStorage.loadAndroidAnimeLineSharpenEnabled())

        PlayerSettingsStorage.replaceFromSyncPayload(buildJsonObject {})
        assertEquals(true, PlayerSettingsStorage.loadAndroidAnimeUpscaleEnabled())
        assertEquals(false, PlayerSettingsStorage.loadAndroidAnimeLineSharpenEnabled())
    }
}
