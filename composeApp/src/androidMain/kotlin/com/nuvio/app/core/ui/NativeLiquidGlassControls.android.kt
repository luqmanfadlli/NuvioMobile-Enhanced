package com.nuvio.app.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

internal actual fun isNativeLiquidGlassControlsAvailable(): Boolean = false

internal actual fun isNativeLiquidGlassControlsSupportedByOs(): Boolean = false

@Composable
internal actual fun NuvioNativeLiquidGlassSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    accessibilityLabel: String,
    modifier: Modifier,
) = Unit

@Composable
internal actual fun NuvioNativeLiquidGlassSegmentedControl(
    items: List<String>,
    selectedIndex: Int,
    onSelectionChanged: (Int) -> Unit,
    modifier: Modifier,
) = Unit

@Composable
internal actual fun NuvioNativeLiquidGlassButton(
    title: String?,
    systemImage: String?,
    accessibilityLabel: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier,
) = Unit

@Composable
internal actual fun NuvioNativeLiquidGlassSearchField(
    value: String,
    placeholder: String,
    accessibilityLabel: String,
    focusRequestCount: Int,
    shouldBeFocused: Boolean,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    modifier: Modifier,
) = Unit
