package com.nuvio.app.core.ui

import androidx.compose.runtime.Composable

internal actual fun isNativeLiquidGlassOptionMenuAvailable(): Boolean = false

@Composable
internal actual fun NuvioNativeLiquidGlassOptionMenu(
    visible: Boolean,
    title: String?,
    cancelLabel: String,
    options: List<NativeLiquidGlassMenuOption>,
    selectedOptionId: String?,
    onOptionSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) = Unit
