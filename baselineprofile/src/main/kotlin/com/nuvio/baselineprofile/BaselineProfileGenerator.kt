package com.nuvio.baselineprofile

import android.graphics.Rect
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun startupAndHomeScroll() {
        rule.collect(
            packageName = targetPackageName(),
            includeInStartupProfile = true,
        ) {
            pressHome()
            startActivityAndWait()
            device.waitForIdle()
            scrollHome()
        }
    }

    private fun MacrobenchmarkScope.scrollHome() {
        val list = device.wait(Until.findObject(By.scrollable(true)), UI_TIMEOUT_MS) ?: return
        repeat(VERTICAL_FLINGS) {
            flingSafely(list, Direction.DOWN)
            flingHorizontalRows()
        }
        repeat(VERTICAL_FLINGS) { flingSafely(list, Direction.UP) }
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.flingHorizontalRows() {
        val rows = try {
            device.findObjects(By.scrollable(true)).filter { it.visibleBounds.isHorizontalRow() }
        } catch (_: StaleObjectException) {
            emptyList()
        }
        rows.take(MAX_ROWS_PER_SCREEN).forEach { row ->
            flingSafely(row, Direction.RIGHT)
            flingSafely(row, Direction.LEFT)
        }
    }

    private fun MacrobenchmarkScope.flingSafely(target: UiObject2, direction: Direction) {
        try {
            target.setGestureMargin(device.displayWidth / 5)
            target.fling(direction)
        } catch (_: StaleObjectException) {
        }
        device.waitForIdle()
    }

    private fun Rect.isHorizontalRow(): Boolean = width() > height() * 2

    private fun targetPackageName(): String =
        InstrumentationRegistry.getArguments().getString("targetAppId") ?: DEFAULT_PACKAGE_NAME

    private companion object {
        const val DEFAULT_PACKAGE_NAME = "com.nuvio.media"
        const val UI_TIMEOUT_MS = 10_000L
        const val VERTICAL_FLINGS = 3
        const val MAX_ROWS_PER_SCREEN = 2
    }
}
