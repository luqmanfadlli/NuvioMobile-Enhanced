package com.nuvio.app.features.home.components

import com.nuvio.app.features.watchprogress.ContinueWatchingSectionStyle
import kotlin.test.Test
import com.nuvio.app.features.home.HomeHeroStyle
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeHeroSectionTest {

    @Test
    fun `first and last indicators select adjacent pages across the loop`() {
        assertEquals(79, heroPageForItem(currentPage = 80, itemIndex = 7, itemCount = 8))
        assertEquals(88, heroPageForItem(currentPage = 87, itemIndex = 0, itemCount = 8))
    }

    @Test
    fun `indicators keep the current page when selecting the active title`() {
        assertEquals(83, heroPageForItem(currentPage = 83, itemIndex = 3, itemCount = 8))
    }

    @Test
    fun `indicators select the closest occurrence of a title`() {
        assertEquals(85, heroPageForItem(currentPage = 83, itemIndex = 5, itemCount = 8))
        assertEquals(81, heroPageForItem(currentPage = 83, itemIndex = 1, itemCount = 8))
    }

    @Test
    fun `mobile hero height stays compact without continue watching`() {
        val layout = homeHeroLayout(
            maxWidthDp = 390f,
            viewportHeightDp = 844f,
        )

        assertEquals(false, layout.isTablet)
        assertEquals(452.4f, layout.heroHeight.value, 0.001f)
    }

    @Test
    fun `tablet hero height remains width driven even with viewport height`() {
        val layout = homeHeroLayout(
            maxWidthDp = 840f,
            viewportHeightDp = 1200f,
        )

        assertEquals(true, layout.isTablet)
        assertEquals(386.4f, layout.heroHeight.value, 0.001f)
    }

    @Test
    fun `mobile hero height leaves room for continue watching card section`() {
        val viewportHeight = 844f
        val continueWatchingLayout = rememberContinueWatchingLayout(maxWidthDp = 390f)
        val continueWatchingHeight = continueWatchingSectionHeightEstimate(
            style = ContinueWatchingSectionStyle.Card,
            layout = continueWatchingLayout,
            basePosterWidthDp = 110,
        )
        val reserveHeight = continueWatchingHeroViewportReserveHeight(
            style = ContinueWatchingSectionStyle.Card,
            layout = continueWatchingLayout,
            basePosterWidthDp = 110,
        )
        val layout = homeHeroLayout(
            maxWidthDp = 390f,
            viewportHeightDp = viewportHeight,
            mobileBelowSectionHeightHintDp = reserveHeight.value,
        )

        assertEquals(24f, viewportHeight - layout.heroHeight.value - continueWatchingHeight.value, 0.001f)
    }

    @Test
    fun `mobile hero can shrink below default minimum to fit short viewport`() {
        val layout = homeHeroLayout(
            maxWidthDp = 390f,
            viewportHeightDp = 568f,
            mobileBelowSectionHeightHintDp = 300f,
        )

        assertEquals(false, layout.isTablet)
        assertEquals(268f, layout.heroHeight.value, 0.001f)
    }

    @Test
    fun `netflix hero style round trips through storage and falls back to full bleed`() {
        assertEquals(HomeHeroStyle.NETFLIX, HomeHeroStyle.fromStorageValue("netflix"))
        assertEquals(HomeHeroStyle.NETFLIX, HomeHeroStyle.fromStorageValue(HomeHeroStyle.NETFLIX.storageValue))
        assertEquals(HomeHeroStyle.FULL_BLEED, HomeHeroStyle.fromStorageValue("something_else"))
        assertEquals(HomeHeroStyle.FULL_BLEED, HomeHeroStyle.fromStorageValue(null))
    }

    @Test
    fun `netflix phone portrait lays out two full width buttons side by side`() {
        val layout = netflixHeroLayout(maxWidthDp = 390f, viewportHeightDp = 844f)

        assertFalse(layout.isTablet)
        assertFalse(layout.isLandscape)
        assertFalse(layout.sideContent)
        assertTrue(layout.buttonsFillWidth)
        assertFalse(layout.buttonsStacked)
        assertTrue(layout.buttonCornerRadius.value < layout.buttonHeight.value / 2f)
        assertEquals(390f * 1.5f, layout.heroHeight.value, 0.001f)
    }

    @Test
    fun `netflix phone portrait keeps stacked pill buttons when requested`() {
        val layout = netflixHeroLayout(
            maxWidthDp = 390f,
            viewportHeightDp = 844f,
            stackPhonePortraitButtons = true,
        )

        assertTrue(layout.buttonsFillWidth)
        assertTrue(layout.buttonsStacked)
        assertEquals(layout.buttonHeight.value / 2f, layout.buttonCornerRadius.value, 0.001f)
        assertEquals(390f * 1.5f, layout.heroHeight.value, 0.001f)
    }

    @Test
    fun `netflix phone landscape uses a start aligned column`() {
        val layout = netflixHeroLayout(maxWidthDp = 844f, viewportHeightDp = 390f)

        // 844dp wide is "tablet" for homeHeroLayout, but a 390dp short side is still a phone.
        assertFalse(layout.isTablet)
        assertTrue(layout.isLandscape)
        assertTrue(layout.sideContent)
        assertFalse(layout.buttonsFillWidth)
        assertFalse(layout.buttonsStacked)
        assertTrue(layout.heroHeight.value <= 390f)
    }

    @Test
    fun `netflix phone landscape drops the synopsis when the hero is very short`() {
        // The continue watching hint squeezes the hero below the comfortable height.
        val squeezed = netflixHeroLayout(
            maxWidthDp = 844f,
            viewportHeightDp = 360f,
            mobileBelowSectionHeightHintDp = 100f,
        )
        val roomy = netflixHeroLayout(maxWidthDp = 844f, viewportHeightDp = 420f)

        assertTrue(squeezed.heroHeight.value < 290f)
        assertEquals(0, squeezed.synopsisMaxLines)
        assertTrue(roomy.synopsisMaxLines > 0)
    }

    @Test
    fun `netflix tablet portrait is taller than the default tablet hero but capped to the viewport`() {
        val layout = netflixHeroLayout(maxWidthDp = 800f, viewportHeightDp = 1280f)
        val base = homeHeroLayout(maxWidthDp = 800f, viewportHeightDp = 1280f)

        assertTrue(layout.isTablet)
        assertFalse(layout.isLandscape)
        assertTrue(layout.heroHeight.value >= base.heroHeight.value)
        assertTrue(layout.heroHeight.value <= 1280f * 0.62f + 0.001f)
    }

    @Test
    fun `netflix tablet landscape never fills more than most of the viewport`() {
        val layout = netflixHeroLayout(maxWidthDp = 1280f, viewportHeightDp = 800f)

        assertTrue(layout.isTablet)
        assertTrue(layout.isLandscape)
        assertTrue(layout.sideContent)
        assertTrue(layout.heroHeight.value <= 800f * 0.8f + 0.001f)
    }

    @Test
    fun `netflix logo never grows past its height budget`() {
        listOf(
            390f to 844f,
            844f to 390f,
            800f to 1280f,
            1280f to 800f,
        ).forEach { (width, height) ->
            val layout = netflixHeroLayout(maxWidthDp = width, viewportHeightDp = height)
            assertTrue(layout.logoWidth.value > 0f, "logo width for ${width}x$height")
            assertTrue(layout.logoWidth.value / 2.6f <= 130.001f, "logo height for ${width}x$height")
        }
    }
}
