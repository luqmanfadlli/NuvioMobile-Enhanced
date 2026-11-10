package com.nuvio.app.core.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val LocalVerticalBarPadding = compositionLocalOf { PaddingValues() }

/** Part of the vertical bar's end padding taken by the iPhone Duo tab rail (0 everywhere else). */
val LocalFoldRailPadding = compositionLocalOf { 0.dp }

@Composable
fun PaddingValues.withVerticalBar(): PaddingValues {
    val verticalBar = LocalVerticalBarPadding.current
    val layoutDirection = LocalLayoutDirection.current
    return PaddingValues(
        start = calculateStartPadding(layoutDirection) + verticalBar.calculateStartPadding(layoutDirection),
        top = calculateTopPadding(),
        end = calculateEndPadding(layoutDirection) + verticalBar.calculateEndPadding(layoutDirection),
        bottom = calculateBottomPadding(),
    )
}

@Composable
fun centeredVerticalBarPadding(minimum: Dp = 0.dp): PaddingValues {
    val verticalBar = LocalVerticalBarPadding.current
    val layoutDirection = LocalLayoutDirection.current
    val end = verticalBar.calculateEndPadding(layoutDirection)
    val rail = minOf(LocalFoldRailPadding.current, end)
    val start = verticalBar.calculateStartPadding(layoutDirection)
    // With the Duo tab rail up, centre the content in the space left of the rail (the rail already reserves the
    // trailing side). Otherwise the system's vertical bar stays mirrored on both sides, as before.
    if (rail > 0.dp) return PaddingValues(start = maxOf(minimum, start), end = maxOf(minimum, end))
    val side = maxOf(minimum, start, end)
    return PaddingValues(start = side, end = side)
}

@Composable
fun verticalBarWidth(): Dp {
    val verticalBar = LocalVerticalBarPadding.current
    val layoutDirection = LocalLayoutDirection.current
    return verticalBar.calculateStartPadding(layoutDirection) + verticalBar.calculateEndPadding(layoutDirection)
}
