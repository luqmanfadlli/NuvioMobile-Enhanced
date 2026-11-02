package com.nuvio.app.core.ui.glass

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class LiquidGlassRadiusRefreshTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun liveRadiusChangeUpdatesResolvedGlassShapeWithoutResizingSurface() {
        val radiusPercent = mutableFloatStateOf(50f)
        val observedCornerRadii = mutableListOf<Float>()
        val measuredSizes = mutableListOf<IntSize>()

        compose.setContent {
            val previewSettings = LiquidGlassSurfacesSettings().withSurface(
                surface = LiquidGlassSurface.SEARCH,
                overrides = LiquidGlassSurfaceOverrides(
                    enabled = true,
                    cornerRadiusPercent = radiusPercent.floatValue,
                ),
            )
            val density = LocalDensity.current
            CompositionLocalProvider(LocalLiquidGlassPreviewSettings provides previewSettings) {
                val shape = liquidGlassShape(
                    surface = LiquidGlassSurface.SEARCH,
                    defaultShape = RoundedCornerShape(24.dp),
                    preview = true,
                )

                SideEffect {
                    val cornerBasedShape = shape as CornerBasedShape
                    val bounds = with(density) { Size(80.dp.toPx(), 80.dp.toPx()) }
                    observedCornerRadii += cornerBasedShape.topStart.toPx(bounds, density)
                }

                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(shape)
                        .onSizeChanged { measuredSizes += it },
                )
            }
        }

        compose.waitForIdle()
        val initialCornerRadius = observedCornerRadii.last()
        val initialBounds = measuredSizes.last()

        compose.runOnIdle {
            radiusPercent.floatValue = 8f
        }
        compose.waitForIdle()

        compose.runOnIdle {
            assertTrue(observedCornerRadii.last() < initialCornerRadius)
            assertEquals(initialBounds, measuredSizes.last())
        }
    }
}
