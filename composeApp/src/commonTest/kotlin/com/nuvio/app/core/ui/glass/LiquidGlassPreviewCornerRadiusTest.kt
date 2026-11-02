package com.nuvio.app.core.ui.glass

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class LiquidGlassPreviewCornerRadiusTest {
    @Test
    fun activeSurfaceUsesItsLatestOverrideWhileDisabledSurfaceKeepsOriginalShape() {
        val original = RoundedCornerShape(20.dp)
        val active = effectiveLiquidGlassSurfaceShape(
            shape = original,
            cornerRadiusPercent = 10f,
            enabled = true,
        )
        val disabled = effectiveLiquidGlassSurfaceShape(
            shape = original,
            cornerRadiusPercent = 10f,
            enabled = false,
        )
        val inherited = effectiveLiquidGlassSurfaceShape(
            shape = original,
            cornerRadiusPercent = null,
            enabled = true,
        )

        assertEquals(4f, topStartRadiusPx(active))
        assertEquals(original, disabled)
        assertEquals(original, inherited)
    }

    @Test
    fun applyingTheSameRadiusTwiceDoesNotScaleTheShapeTwice() {
        val original = RoundedCornerShape(20.dp)
        val once = scaleLiquidGlassCornerShape(original, 25f)
        val twice = scaleLiquidGlassCornerShape(once, 25f)

        assertEquals(10f, topStartRadiusPx(once))
        assertEquals(10f, topStartRadiusPx(twice))
    }

    @Test
    fun changingRadiusReplacesThePreviousPreviewRadius() {
        val original = RoundedCornerShape(20.dp)
        val previous = scaleLiquidGlassCornerShape(original, 25f)
        val updated = scaleLiquidGlassCornerShape(previous, 10f)
        val expected = scaleLiquidGlassCornerShape(original, 10f)

        assertEquals(4f, topStartRadiusPx(updated))
        assertEquals(topStartRadiusPx(expected), topStartRadiusPx(updated))
    }

    @Test
    fun increasingRadiusAfterSquareCornersRestoresTheOriginalShapeScale() {
        val original = RoundedCornerShape(20.dp)
        val square = scaleLiquidGlassCornerShape(original, 0f)
        val roundedAgain = scaleLiquidGlassCornerShape(square, 25f)

        assertEquals(0f, topStartRadiusPx(square))
        assertEquals(10f, topStartRadiusPx(roundedAgain))
    }

    @Test
    fun fullRadiusRestoresTheOriginalCornerSizeAfterPreviousEdits() {
        val original = RoundedCornerShape(20.dp)
        val edited = scaleLiquidGlassCornerShape(original, 10f)
        val restored = scaleLiquidGlassCornerShape(edited, 50f)

        assertEquals(20f, topStartRadiusPx(restored))
    }

    @Test
    fun categoryTitleAndBackButtonUseTheSameLiquidGlassMaterialAndRecipe() {
        assertEquals(
            LiquidGlassMaterial.LIQUID,
            LiquidGlassSurface.CATEGORY_HEADER_TITLE.defaultMaterial,
        )
        assertEquals(
            LiquidGlassSurface.CATEGORY_HEADER_BACK_BUTTON.defaultMaterial,
            LiquidGlassSurface.CATEGORY_HEADER_TITLE.defaultMaterial,
        )
        assertEquals(
            liquidGlassSurfaceRecipe(LiquidGlassSurface.CATEGORY_HEADER_BACK_BUTTON),
            liquidGlassSurfaceRecipe(LiquidGlassSurface.CATEGORY_HEADER_TITLE),
        )
        assertEquals(
            LiquidGlassSurface.CATEGORY_HEADER_BACK_BUTTON.supportsLens,
            LiquidGlassSurface.CATEGORY_HEADER_TITLE.supportsLens,
        )
    }

    private fun topStartRadiusPx(shape: androidx.compose.ui.graphics.Shape): Float {
        val corners = shape as CornerBasedShape
        return corners.topStart.toPx(Size(100f, 100f), Density(1f))
    }
}
