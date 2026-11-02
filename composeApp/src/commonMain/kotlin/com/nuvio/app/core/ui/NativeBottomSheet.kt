package com.nuvio.app.core.ui

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Native iOS page sheets use the system Liquid Glass surface only when both the OS and the
 * user's effective Bottom Sheets surface setting support it. Older iOS versions and disabled
 * surfaces retain the app's normal opaque sheet background.
 */
internal fun shouldUseNativeLiquidGlassSheetBackground(
    operatingSystemMajorVersion: Long,
    surfaceGlassEnabled: Boolean,
): Boolean = operatingSystemMajorVersion >= 26L && surfaceGlassEnabled

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal expect fun NuvioNativeModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier,
    containerColor: Color,
    contentColor: Color,
    showDragHandle: Boolean,
    fullHeight: Boolean,
    content: @Composable ColumnScope.() -> Unit,
)

internal expect val usesNativeNuvioBottomSheet: Boolean

internal expect fun dismissNativeNuvioBottomSheet()

@Composable
internal expect fun NuvioSystemBackHandler(enabled: Boolean, onBack: () -> Unit)
