package com.nuvio.app.features.player.anime

import android.app.Application
import android.content.Context
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.nuvio.app.core.ui.NuvioTheme
import com.nuvio.app.features.player.AnimeEnhancementBadge
import com.nuvio.app.features.player.AnimeEnhancementPanel
import com.nuvio.app.features.player.AnimeEnhancementStatus
import com.nuvio.app.features.player.PlayerSettingsRepository
import com.nuvio.app.features.player.PlayerSettingsStorage
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w891dp-h411dp")
class AnimeEnhancementControlsTest {
    @get:Rule
    val compose = createComposeRule()

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
    fun badgeShowsUpscaleAndSharpen() {
        compose.setContent {
            NuvioTheme {
                AnimeEnhancementBadge(status = AnimeEnhancementStatus(true, true, 1920, 1080, 2560, 1440))
            }
        }
        compose.onNodeWithText("Anime4K · 1080p → 1440p · Sharpen").assertIsDisplayed()
    }

    @Test
    fun badgeIsHiddenWhenNothingRuns() {
        compose.setContent {
            NuvioTheme {
                AnimeEnhancementBadge(status = null)
            }
        }
        compose.onNodeWithText("Anime4K", substring = true).assertDoesNotExist()
    }

    @Test
    fun panelTogglesUpdateSettingsAndShowStatus() {
        PlayerSettingsRepository.ensureLoaded()
        compose.setContent {
            NuvioTheme {
                val settings by PlayerSettingsRepository.uiState.collectAsState()
                AnimeEnhancementPanel(
                    visible = true,
                    settings = settings,
                    status = if (settings.androidAnimeUpscaleEnabled) {
                        AnimeEnhancementStatus(false, true, 1280, 720, 2560, 1440)
                    } else {
                        null
                    },
                    onDismiss = {},
                )
            }
        }
        compose.onNodeWithText("Off").assertIsDisplayed()
        val switches = compose.onAllNodes(isToggleable())
        switches[0].assertIsOff()
        switches[0].performClick()
        compose.waitForIdle()
        switches[0].assertIsOn()
        assertTrue(PlayerSettingsRepository.uiState.value.androidAnimeUpscaleEnabled)
        compose.onNodeWithText("Active: 720p → 1440p").assertIsDisplayed()
        switches[1].performClick()
        compose.waitForIdle()
        assertTrue(PlayerSettingsRepository.uiState.value.androidAnimeLineSharpenEnabled)
    }
}
