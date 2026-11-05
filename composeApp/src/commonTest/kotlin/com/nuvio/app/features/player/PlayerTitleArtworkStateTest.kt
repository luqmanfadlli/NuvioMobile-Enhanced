package com.nuvio.app.features.player

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composition
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayerTitleArtworkStateTest {

    @Test
    fun `loaded artwork state survives controls leaving and re-entering composition`() = runTest {
        val frameClock = BroadcastFrameClock()
        val recomposer = Recomposer(coroutineContext + frameClock)
        val composition = Composition(EmptyApplier(), recomposer)
        val runner = launch(frameClock) { recomposer.runRecomposeAndApplyChanges() }
        val controlsVisible = mutableStateOf(true)
        lateinit var loadedState: MutableState<Boolean>

        suspend fun frame(millis: Long) {
            Snapshot.sendApplyNotifications()
            yield()
            frameClock.sendFrame(millis * 1_000_000L)
            yield()
            Snapshot.sendApplyNotifications()
            yield()
        }

        try {
            composition.setContent {
                val state = rememberPlayerTitleArtworkLoadedState(
                    logoUrl = "https://image.example/logo.png",
                    enabled = true,
                )
                if (controlsVisible.value) {
                    SideEffect { loadedState = state }
                }
            }
            frame(0)
            loadedState.value = true
            frame(16)

            controlsVisible.value = false
            frame(32)
            controlsVisible.value = true
            frame(48)

            assertTrue(loadedState.value)
        } finally {
            composition.dispose()
            recomposer.cancel()
            runner.join()
        }
    }

    @Test
    fun `loaded artwork state resets when its source or setting changes`() = runTest {
        val frameClock = BroadcastFrameClock()
        val recomposer = Recomposer(coroutineContext + frameClock)
        val composition = Composition(EmptyApplier(), recomposer)
        val runner = launch(frameClock) { recomposer.runRecomposeAndApplyChanges() }
        val logoUrl = mutableStateOf("https://image.example/first.png")
        val enabled = mutableStateOf(true)
        lateinit var loadedState: MutableState<Boolean>

        suspend fun frame(millis: Long) {
            Snapshot.sendApplyNotifications()
            yield()
            frameClock.sendFrame(millis * 1_000_000L)
            yield()
            Snapshot.sendApplyNotifications()
            yield()
        }

        try {
            composition.setContent {
                val state = rememberPlayerTitleArtworkLoadedState(logoUrl.value, enabled.value)
                SideEffect { loadedState = state }
            }
            frame(0)
            loadedState.value = true
            frame(16)

            logoUrl.value = "https://image.example/second.png"
            frame(32)
            assertFalse(loadedState.value)

            loadedState.value = true
            frame(48)
            enabled.value = false
            frame(64)
            assertFalse(loadedState.value)
        } finally {
            composition.dispose()
            recomposer.cancel()
            runner.join()
        }
    }

    private class EmptyApplier : AbstractApplier<Unit>(Unit) {
        override fun insertBottomUp(index: Int, instance: Unit) = Unit
        override fun insertTopDown(index: Int, instance: Unit) = Unit
        override fun move(from: Int, to: Int, count: Int) = Unit
        override fun remove(index: Int, count: Int) = Unit
        override fun onClear() = Unit
    }
}
