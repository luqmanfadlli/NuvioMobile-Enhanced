package com.nuvio.app.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.glass.LiquidGlassSurface
import com.nuvio.app.core.ui.glass.isLiquidGlassAvailable
import com.nuvio.app.core.ui.glass.isLiquidGlassSurfaceEffectivelyEnabled
import com.nuvio.app.core.ui.glass.liquidGlassForSurface
import com.nuvio.app.core.ui.glass.LiquidGlassTextContrastScope
import com.nuvio.app.core.ui.glass.liquidGlassShape

private val MenuShape = RoundedCornerShape(NuvioTokens.Space.s20)

private val MenuMinWidth = 180.dp

@Composable
fun Menu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(NuvioTokens.Space.none, NuvioTokens.Space.s6),
    glassSurface: LiquidGlassSurface? = null,
    onGlassSurfaceInteracted: (() -> Unit)? = null,
    selectionHighlight: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val useGlass = glassSurface != null &&
        isLiquidGlassAvailable() && isLiquidGlassSurfaceEffectivelyEnabled(glassSurface)
    val shape = glassSurface?.let { liquidGlassShape(it, MenuShape) } ?: MenuShape
    val interactionObserver = if (onGlassSurfaceInteracted == null) {
        Modifier
    } else {
        Modifier.pointerInput(onGlassSurfaceInteracted) {
            awaitEachGesture {
                awaitFirstDown(pass = PointerEventPass.Initial, requireUnconsumed = false)
                if (waitForUpOrCancellation(pass = PointerEventPass.Initial) != null) {
                    onGlassSurfaceInteracted()
                }
            }
        }
    }
    val baseModifier = modifier.then(interactionObserver)
    val glassModifier = if (useGlass && glassSurface != null) {
        baseModifier.liquidGlassForSurface(
            surface = glassSurface,
            shape = shape,
            tint = tokens.colors.surfacePopover.copy(alpha = 0.28f),
            fallback = tokens.colors.surfacePopover.copy(alpha = 0.72f),
            blurRadius = 14.dp,
            refractionHeight = 6.dp,
            refractionAmount = 9.dp,
        )
    } else {
        baseModifier
    }
    val menuModifier = glassModifier.then(
        if (selectionHighlight && useGlass) {
            Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape)
        } else {
            Modifier
        },
    )

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = menuModifier.widthIn(min = MenuMinWidth),
        offset = offset,
        shape = shape,
        containerColor = if (useGlass) Color.Transparent else tokens.colors.surfacePopover,
        tonalElevation = if (useGlass) 0.dp else tokens.elevation.flat,
        shadowElevation = if (useGlass) 0.dp else tokens.elevation.overlay,
        border = when {
            useGlass -> null
            selectionHighlight -> BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
            else -> BorderStroke(tokens.borders.thin, SurfaceEdge)
        },
        content = {
            val menuScope = this
            LiquidGlassTextContrastScope(enabled = useGlass) {
                content.invoke(menuScope)
            }
        },
    )
}

@Composable
fun MenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
) {
    val tokens = MaterialTheme.nuvio
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = NuvioTokens.Space.s8)
            .clip(tokens.shapes.compactCard)
            .background(if (selected) tokens.colors.accent.copy(alpha = tokens.opacity.hover) else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(NuvioTokens.Space.s12),
        horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.SemiBold else null,
            color = if (enabled) tokens.colors.textPrimary else tokens.colors.textDisabled,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (selected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = tokens.colors.accent,
                modifier = Modifier.size(tokens.icons.md),
            )
        }
    }
}
