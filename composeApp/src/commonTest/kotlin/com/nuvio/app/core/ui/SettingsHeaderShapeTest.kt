package com.nuvio.app.core.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SettingsHeaderShapeTest {
    @Test
    fun settingsGlassHeaderUsesRectangleRegardlessOfConfiguredCornerShape() {
        val configuredRoundedShape: Shape = RoundedCornerShape(32.dp)

        val resolved = settingsHeaderShape(
            settingsGlassEnabled = true,
            defaultShape = configuredRoundedShape,
        )

        assertSame(RectangleShape, resolved)
        assertTrue(resolved !== configuredRoundedShape)
    }

    @Test
    fun nonGlassHeaderKeepsItsExistingDefaultShape() {
        val defaultShape: Shape = RoundedCornerShape(12.dp)

        assertSame(
            defaultShape,
            settingsHeaderShape(settingsGlassEnabled = false, defaultShape = defaultShape),
        )
    }
}
