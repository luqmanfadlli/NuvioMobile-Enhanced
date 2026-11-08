package com.nuvio.app.core.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal const val PosterLandscapeAspectRatio = 1.77f
private const val PosterLandscapeWidthScale = 180f / 110f

internal fun landscapePosterWidth(basePosterWidthDp: Int): Dp =
    (basePosterWidthDp * PosterLandscapeWidthScale).dp

internal fun landscapePosterHeightForWidth(width: Dp): Dp =
    (width.value / PosterLandscapeAspectRatio).dp


private const val MinimumLandscapeLogoAspectRatio = 1.5f

/**
 * Reject portrait and square artwork from logo slots. Logos are intentionally wide;
 * validating decoded dimensions avoids rendering a portrait poster as a tiny logo.
 */
internal fun isLandscapeLogo(
    width: Int,
    height: Int,
    minimumAspectRatio: Float = MinimumLandscapeLogoAspectRatio,
): Boolean =
    width > 0 && height > 0 && width.toFloat() / height.toFloat() >= minimumAspectRatio
