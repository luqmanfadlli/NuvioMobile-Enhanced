package com.nuvio.app.features.details.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import com.nuvio.app.core.ui.glass.LiquidGlassSurface
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EpisodeNavigatorPreviewSurfaceTapTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun selectingSearchSurfaceDoesNotConsumeTheTextFieldTap() {
        var selectedSurface: LiquidGlassSurface? = null
        var childClickCount = 0

        compose.setContent {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onLiquidGlassPreviewSurfaceTap(LiquidGlassSurface.DETAIL_EPISODE_SEARCH) {
                        selectedSurface = it
                    }
                    .clickable { childClickCount++ },
            )
        }

        compose.onRoot().performTouchInput {
            click(Offset(4f, 4f))
        }

        compose.runOnIdle {
            assertEquals(LiquidGlassSurface.DETAIL_EPISODE_SEARCH, selectedSurface)
            assertEquals(1, childClickCount)
        }
    }

    @Test
    fun missingPreviewCallbackLeavesTheSurfaceUnobserved() {
        var childClickCount = 0

        compose.setContent {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onLiquidGlassPreviewSurfaceTap(LiquidGlassSurface.DETAIL_EPISODE_SEARCH, null)
                    .clickable { childClickCount++ },
            )
        }

        compose.onRoot().performTouchInput {
            click(Offset(4f, 4f))
        }

        compose.runOnIdle {
            assertEquals(1, childClickCount)
        }
    }
}
