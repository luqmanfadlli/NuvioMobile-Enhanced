package com.nuvio.app.core.ui

import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.CoroutineScope

internal val LocalScreenActive = compositionLocalOf { true }

@Composable
internal fun ScreenActivityEffect(
    vararg keys: Any?,
    block: suspend CoroutineScope.(Boolean) -> Unit,
) {
    val active = LocalScreenActive.current
    LaunchedEffect(active, *keys) { block(active) }
}

@Composable
internal fun rememberScreenActiveInfiniteFloat(
    initialValue: Float,
    targetValue: Float,
    animationSpec: InfiniteRepeatableSpec<Float>,
    label: String = "ScreenActiveInfiniteFloat",
): State<Float> =
    if (LocalScreenActive.current) {
        rememberInfiniteTransition(label = label).animateFloat(
            initialValue = initialValue,
            targetValue = targetValue,
            animationSpec = animationSpec,
            label = label,
        )
    } else {
        remember(initialValue) { mutableFloatStateOf(initialValue) }
    }
