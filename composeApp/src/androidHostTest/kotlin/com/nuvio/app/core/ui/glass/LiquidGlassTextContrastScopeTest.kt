package com.nuvio.app.core.ui.glass

import android.app.Application
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class LiquidGlassTextContrastScopeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun contrastScopePreservesInheritedTextStyleAndAddsShadow() {
        val inheritedStyle = TextStyle(
            fontSize = 23.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
        )
        var observedStyle: TextStyle? = null

        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalTextStyle provides inheritedStyle) {
                    LiquidGlassTextContrastScope(enabled = true) {
                        observedStyle = LocalTextStyle.current
                    }
                }
            }
        }

        compose.waitForIdle()

        assertEquals(inheritedStyle.fontSize, observedStyle?.fontSize)
        assertEquals(inheritedStyle.fontWeight, observedStyle?.fontWeight)
        assertEquals(inheritedStyle.letterSpacing, observedStyle?.letterSpacing)
        assertNotNull(observedStyle?.shadow)
    }
}
