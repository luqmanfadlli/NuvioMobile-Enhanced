package com.nuvio.app.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.glass.NuvioBackdrop
import com.nuvio.app.core.ui.glass.isLiquidGlassSupported
import com.nuvio.app.core.ui.glass.liquidGlassSurface
import com.nuvio.app.core.ui.glass.nuvioBackdropSource

private val SheetShape = RoundedCornerShape(NuvioTokens.Space.s28)

@Stable
class NuvioSheetBackdrop(val backdrop: NuvioBackdrop) {
    var activeSheets by mutableIntStateOf(0)
}

val LocalNuvioSheetBackdrop = staticCompositionLocalOf<NuvioSheetBackdrop?> { null }

@Composable
fun NuvioSheetBackdropHost(
    sheetBackdrop: NuvioSheetBackdrop,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalNuvioSheetBackdrop provides sheetBackdrop) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (sheetBackdrop.activeSheets > 0) Modifier.nuvioBackdropSource(sheetBackdrop.backdrop) else Modifier),
        ) {
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NuvioModalBottomSheet(
    onDismissRequest: () -> Unit,
    sheetState: SheetState,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.nuvio.colors.surfaceSheet,
    contentColor: Color = MaterialTheme.nuvio.colors.textPrimary,
    showDragHandle: Boolean = true,
    fullHeight: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (usesNativeNuvioBottomSheet) {
        NuvioNativeModalBottomSheet(
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            containerColor = containerColor,
            contentColor = contentColor,
            showDragHandle = showDragHandle,
            fullHeight = fullHeight,
            content = content,
        )
    } else {
        val tokens = MaterialTheme.nuvio
        val sheetBackdrop = LocalNuvioSheetBackdrop.current
        if (sheetBackdrop != null) {
            DisposableEffect(sheetBackdrop) {
                sheetBackdrop.activeSheets += 1
                onDispose { sheetBackdrop.activeSheets -= 1 }
            }
        }
        val sheetGlassReady = sheetBackdrop != null && sheetBackdrop.backdrop.isReady && isLiquidGlassSupported()
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            sheetState = sheetState,
            shape = RectangleShape,
            containerColor = Color.Transparent,
            contentColor = contentColor,
            scrimColor = tokens.colors.overlayScrim,
            dragHandle = null,
        ) {
            CompositionLocalProvider(LocalBottomInsetsConsumed provides true) {
                Column(
                    modifier = modifier
                        .padding(NuvioTokens.Space.s10)
                        .clip(SheetShape)
                        .liquidGlassSurface(
                            backdrop = if (sheetGlassReady) sheetBackdrop?.backdrop else null,
                            shape = SheetShape,
                            tint = containerColor.copy(alpha = containerColor.alpha * 0.65f),
                            fallback = containerColor,
                            blurRadius = 24.dp,
                            refractionHeight = 16.dp,
                            refractionAmount = 32.dp,
                        )
                        .then(if (sheetGlassReady) Modifier else Modifier.border(tokens.borders.thin, SurfaceEdge, SheetShape)),
                ) {
                    if (showDragHandle) {
                        NuvioBottomSheetDragHandle(Modifier.align(Alignment.CenterHorizontally))
                    }
                    content()
                }
            }
        }
    }
}

@Composable
fun NuvioBottomSheetDivider(
    modifier: Modifier = Modifier,
) {
    val tokens = MaterialTheme.nuvio
    HorizontalDivider(
        modifier = modifier.padding(horizontal = tokens.spacing.screenHorizontal),
        thickness = tokens.borders.hairline,
        color = tokens.colors.borderDefault,
    )
}

@Composable
fun NuvioBottomSheetActionRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    selected: Boolean = false,
    trailingContent: (@Composable RowScope.() -> Unit)? = null,
) {
    val tokens = MaterialTheme.nuvio
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = NuvioTokens.Space.s8)
            .heightIn(min = NuvioTokens.Space.s56)
            .clip(tokens.shapes.compactCard)
            .background(if (selected) tokens.colors.accent.copy(alpha = tokens.opacity.hover) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(NuvioTokens.Space.s8),
        horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s14),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(NuvioTokens.Space.s36)
                    .clip(tokens.shapes.compactCard)
                    .background(tokens.colors.accent.copy(alpha = tokens.opacity.selected)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tokens.colors.accent,
                    modifier = Modifier.size(NuvioTokens.Icon.md),
                )
            }
        }
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.SemiBold else null,
            color = tokens.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        trailingContent?.invoke(this)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
suspend fun dismissNuvioBottomSheet(
    sheetState: SheetState,
    onDismiss: () -> Unit,
) {
    if (usesNativeNuvioBottomSheet) {
        dismissNativeNuvioBottomSheet()
    } else if (sheetState.isVisible) {
        sheetState.hide()
    }
    onDismiss()
}

@Composable
private fun NuvioBottomSheetDragHandle(modifier: Modifier = Modifier) {
    val tokens = MaterialTheme.nuvio
    Box(
        modifier = modifier
            .padding(top = NuvioTokens.Space.s10, bottom = NuvioTokens.Space.s4)
            .size(width = NuvioTokens.Space.s36, height = NuvioTokens.Space.s4)
            .clip(tokens.shapes.chip)
            .background(tokens.colors.borderStrong),
    )
}
