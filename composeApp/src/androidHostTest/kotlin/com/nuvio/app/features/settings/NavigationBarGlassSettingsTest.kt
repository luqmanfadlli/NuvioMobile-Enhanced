package com.nuvio.app.features.settings

import android.app.Application
import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import com.nuvio.app.core.ui.glass.LiquidGlassMaterial
import com.nuvio.app.core.ui.glass.LiquidGlassStyle
import com.nuvio.app.features.membership.MemberAccessRepository
import com.nuvio.app.features.membership.MemberAssetStorage
import kotlin.math.roundToInt
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NavigationBarGlassSettingsTest {
    @get:Rule
    val compose = createComposeRule()

    @BeforeTest
    fun initialize() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("nuvio_theme_settings", Context.MODE_PRIVATE).edit().clear().commit()
        ThemeSettingsStorage.initialize(context)
        MemberAssetStorage.initialize(context)
        MemberAccessRepository.clearLocalState()
        ThemeSettingsRepository.clearLocalState()
        ThemeSettingsRepository.ensureLoaded()
    }

    @AfterTest
    fun clearState() {
        ThemeSettingsRepository.clearLocalState()
    }

    @Test
    fun draftSliderUpdatesRecomposeTheDraftHostWithoutRecomposingItsParent() {
        var parentCompositions = 0
        var draftHostCompositions = 0

        compose.setContent {
            parentCompositions += 1
            NavigationBarLiquidGlassEffectDrafts { bar, lens ->
                SideEffect { draftHostCompositions += 1 }
                Text(text = "${bar.blurRadiusDp}:${lens.blurRadiusDp}")
            }
        }
        compose.waitForIdle()

        var parentBefore = 0
        var draftBefore = 0
        var currentBlur = 0f
        compose.runOnIdle {
            parentBefore = parentCompositions
            draftBefore = draftHostCompositions
            currentBlur = ThemeSettingsRepository.navBarLiquidGlassBarEffects.value.blurRadiusDp
        }
        val nextBlur = if (currentBlur == 7f) 8f else 7f

        compose.runOnIdle {
            val current = ThemeSettingsRepository.navBarLiquidGlassBarEffects.value
            ThemeSettingsRepository.updateNavBarLiquidGlassBarEffects(current.copy(blurRadiusDp = nextBlur))
        }
        compose.waitForIdle()

        compose.runOnIdle {
            assertEquals(parentBefore, parentCompositions, "Draft changes should not invalidate the parent settings item")
            assertTrue(draftHostCompositions > draftBefore, "The draft host must observe the updated slider value")
        }
    }


    @Test
    fun draggingGlassEffectSliderUpdatesDraftValue() {
        val value = mutableFloatStateOf(10f)

        compose.setContent {
            MaterialTheme {
                LiquidGlassEffectSlider(
                    title = "Corner radius",
                    value = value.floatValue,
                    valueRange = 0f..50f,
                    valueLabel = "${value.floatValue.roundToInt()}%",
                    isTablet = false,
                    onValueChange = { value.floatValue = it },
                )
            }
        }

        compose.onNodeWithContentDescription("Corner radius").performTouchInput {
            swipe(
                start = Offset(size.width * 0.2f, size.height / 2f),
                end = Offset(size.width * 0.8f, size.height / 2f),
                durationMillis = 250,
            )
        }
        compose.waitForIdle()

        compose.runOnIdle {
            assertTrue(value.floatValue > 10f)
        }
    }

    @Test
    fun lensControlsFollowTheMaterialAndDeviceSupport() {
        assertTrue(navBarGlassShowsLensControls(LiquidGlassStyle(LiquidGlassMaterial.LIQUID), refractionSupported = true))
        assertTrue(navBarGlassShowsLensControls(LiquidGlassStyle(LiquidGlassMaterial.CLEAR), refractionSupported = true))
        assertFalse(navBarGlassShowsLensControls(LiquidGlassStyle(LiquidGlassMaterial.FROSTED), refractionSupported = true))
        assertFalse(navBarGlassShowsLensControls(LiquidGlassStyle(LiquidGlassMaterial.LIQUID), refractionSupported = false))
    }
}
