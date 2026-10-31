package com.nuvio.app.features.player

import com.nuvio.app.core.ui.glass.LiquidGlassSurface
import com.nuvio.app.core.ui.glass.LiquidGlassSurfaceOverrides
import com.nuvio.app.core.ui.glass.liquidGlassSurfaceRecipe
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlayerLiquidGlassResponsivenessTest {
    @Test
    fun blurSliderChangesFallbackWhenNoVideoBackdropCanBeCaptured() {
        val baseAlpha = PlayerGlassFallback.alpha
        val noBlur = playerGlassFallbackAlpha(baseAlpha, blurRadiusDp = 0f)
        val maximumBlur = playerGlassFallbackAlpha(baseAlpha, blurRadiusDp = 32f)

        assertEquals(baseAlpha, noBlur)
        assertTrue(maximumBlur > noBlur)
        assertTrue(maximumBlur <= 0.22f)
    }

    @Test
    fun highlightToggleControlsPlayerRimWhenGlassIsEnabled() {
        assertEquals(
            0f,
            playerGlassRimAlpha(
                rimAlpha = PlayerGlassRim.alpha,
                surfaceEnabled = true,
                highlightEnabled = false,
                backdropReady = true,
                lensEnabled = true,
                refractionHeightDp = 24f,
                refractionAmountDp = 24f,
            ),
        )
        assertEquals(
            PlayerGlassRim.alpha,
            playerGlassRimAlpha(
                rimAlpha = PlayerGlassRim.alpha,
                surfaceEnabled = false,
                highlightEnabled = false,
                backdropReady = true,
                lensEnabled = true,
                refractionHeightDp = 0f,
                refractionAmountDp = 0f,
            ),
        )
    }

    @Test
    fun refractionControlsChangeFallbackRimWhenBackdropIsUnavailable() {
        val baseAlpha = PlayerGlassRim.alpha
        val weakRim = playerGlassRimAlpha(
            rimAlpha = baseAlpha,
            surfaceEnabled = true,
            highlightEnabled = true,
            backdropReady = false,
            lensEnabled = true,
            refractionHeightDp = 0f,
            refractionAmountDp = 0f,
        )
        val strongRim = playerGlassRimAlpha(
            rimAlpha = baseAlpha,
            surfaceEnabled = true,
            highlightEnabled = true,
            backdropReady = false,
            lensEnabled = true,
            refractionHeightDp = 48f,
            refractionAmountDp = 48f,
        )

        assertTrue(strongRim > weakRim)
    }

    @Test
    fun globalLiquidGlassOffAlsoHidesThePlayerGlassRim() {
        assertEquals(
            0f,
            playerGlassRimAlpha(
                rimAlpha = PlayerGlassRim.alpha,
                surfaceEnabled = true,
                highlightEnabled = true,
                backdropReady = false,
                lensEnabled = true,
                refractionHeightDp = 24f,
                refractionAmountDp = 24f,
                masterEnabled = false,
            ),
        )
    }

    @Test
    fun previewUsesTheSameDefaultBlurAsTheLivePlayer() {
        assertEquals(
            PlayerGlassBlur.value,
            liquidGlassSurfaceRecipe(LiquidGlassSurface.PLAYER, LiquidGlassSurfaceOverrides()).blurRadiusDp,
        )
        assertTrue(PlayerGlassBackdropOpacity >= 0.85f)
    }
}
