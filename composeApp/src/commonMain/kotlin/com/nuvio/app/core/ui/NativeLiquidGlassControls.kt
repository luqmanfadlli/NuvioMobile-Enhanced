package com.nuvio.app.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** iOS 26 system controls automatically adopt Apple's Liquid Glass interaction treatment. */
internal fun shouldUseNativeLiquidGlassControls(operatingSystemMajorVersion: Long): Boolean =
    operatingSystemMajorVersion >= 26L

internal expect fun isNativeLiquidGlassControlsAvailable(): Boolean

@Composable
internal expect fun NuvioNativeLiquidGlassSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    accessibilityLabel: String,
    modifier: Modifier = Modifier,
)

@Composable
internal expect fun NuvioNativeLiquidGlassSegmentedControl(
    items: List<String>,
    selectedIndex: Int,
    onSelectionChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
)

@Composable
internal expect fun NuvioNativeLiquidGlassButton(
    title: String? = null,
    systemImage: String? = null,
    accessibilityLabel: String,
    selected: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
)

@Composable
internal expect fun NuvioNativeLiquidGlassSearchField(
    value: String,
    placeholder: String,
    accessibilityLabel: String,
    focusRequestCount: Int = 0,
    shouldBeFocused: Boolean = false,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit = {},
    onFocusChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
)

/** Keep Liquid Glass settings visible if either the Compose renderer or native iOS exists. */
internal fun shouldExposeLiquidGlassSettings(
    composeRendererAvailable: Boolean,
    nativeControlsAvailable: Boolean,
): Boolean = composeRendererAvailable || nativeControlsAvailable
