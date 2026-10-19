package com.nuvio.app.core.ui.glass

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import java.util.concurrent.atomic.AtomicInteger
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
    fun shapeKeyChangeRemeasuresModifierSubtreeWithoutChangingBounds() {
        val radiusKey = mutableIntStateOf(30)
        val measuredSizes = mutableListOf<IntSize>()

        compose.setContent {
            Box(
                Modifier
                    .size(80.dp)
                    .remeasureOnKeyChange(radiusKey.intValue)
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        measuredSizes += IntSize(placeable.width, placeable.height)
                        layout(placeable.width, placeable.height) {
                            placeable.place(0, 0)
                        }
                    },
            )
        }

        compose.waitForIdle()
        val originalCount = measuredSizes.size
        val originalSize = measuredSizes.last()

        compose.runOnIdle {
            radiusKey.intValue = 50
        }
        compose.waitForIdle()

        compose.runOnIdle {
            assertTrue(measuredSizes.size > originalCount)
            assertEquals(originalSize, measuredSizes.last())
        }
    }


    @Test
    fun effectiveRadiusChangeRecreatesGlassSubtreeWithoutScrollOrResize() {
        val radius = mutableFloatStateOf(50f)
        val mounts = AtomicInteger()
        val disposals = AtomicInteger()

        compose.setContent {
            KeyedLiquidGlassRadius(radius.floatValue) {
                DisposableEffect(Unit) {
                    mounts.incrementAndGet()
                    onDispose { disposals.incrementAndGet() }
                }
                Box(Modifier.size(100.dp))
            }
        }

        compose.runOnIdle {
            assertEquals(1, mounts.get())
            radius.floatValue = 8f
        }
        compose.waitForIdle()

        compose.runOnIdle {
            assertEquals(2, mounts.get())
            assertEquals(1, disposals.get())
            radius.floatValue = 8.4f
        }
        compose.waitForIdle()

        compose.runOnIdle {
            assertEquals(2, mounts.get())
            assertEquals(1, disposals.get())
        }
    }
}
