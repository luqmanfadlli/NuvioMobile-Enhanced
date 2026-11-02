package com.nuvio.app.core.ui

import androidx.compose.runtime.Composable

/** A stable identifier and localized label for one choice in an iOS system action sheet. */
internal data class NativeLiquidGlassMenuOption(
    val id: String,
    val title: String,
)

/** Native Liquid Glass presentation APIs are available starting with iOS 26. */
internal fun shouldUseNativeLiquidGlassOptionMenu(
    operatingSystemMajorVersion: Long,
    surfaceGlassEnabled: Boolean,
): Boolean = operatingSystemMajorVersion >= 26L && surfaceGlassEnabled

internal expect fun isNativeLiquidGlassOptionMenuAvailable(): Boolean

internal fun shouldUseNativeLiquidGlassDropdown(
    nativeControlsAvailable: Boolean,
    controlEnabled: Boolean,
    previewGlass: Boolean,
    hasGlassSurface: Boolean,
    surfaceGlassEnabled: Boolean,
): Boolean = nativeControlsAvailable &&
    controlEnabled &&
    !previewGlass &&
    hasGlassSurface &&
    surfaceGlassEnabled

@Composable
internal expect fun NuvioNativeLiquidGlassOptionMenu(
    visible: Boolean,
    title: String?,
    cancelLabel: String,
    options: List<NativeLiquidGlassMenuOption>,
    selectedOptionId: String?,
    onOptionSelected: (String) -> Unit,
    onDismiss: () -> Unit,
)
