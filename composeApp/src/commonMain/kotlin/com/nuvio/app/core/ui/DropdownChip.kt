package com.nuvio.app.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.glass.LiquidGlassSurface
import com.nuvio.app.core.ui.glass.isLiquidGlassAvailable
import com.nuvio.app.core.ui.glass.isLiquidGlassSupported
import com.nuvio.app.core.ui.glass.isLiquidGlassSurfaceEffectivelyEnabled
import com.nuvio.app.core.ui.glass.liquidGlassIconTint
import com.nuvio.app.core.ui.glass.liquidGlassForSurface
import com.nuvio.app.core.ui.glass.liquidGlassShape
import com.nuvio.app.core.ui.glass.LiquidGlassTextContrastScope
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_cancel
import org.jetbrains.compose.resources.stringResource

data class NuvioDropdownOption(
    val key: String,
    val label: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NuvioDropdownChip(
    title: String,
    label: String,
    selectedKey: String?,
    options: List<NuvioDropdownOption>,
    enabled: Boolean = true,
    onSelected: (NuvioDropdownOption) -> Unit,
    modifier: Modifier = Modifier,
    glassSurface: LiquidGlassSurface? = null,
    previewGlass: Boolean = false,
    selectedPreviewSurface: LiquidGlassSurface? = null,
    onGlassSurfaceInteracted: (() -> Unit)? = null,
) {
    val tokens = MaterialTheme.nuvio
    val glassEnabled = glassSurface != null && isLiquidGlassSurfaceEffectivelyEnabled(
        surface = glassSurface,
        preview = previewGlass,
        platformSupported = if (previewGlass) isLiquidGlassAvailable() else isLiquidGlassSupported(),
    )
    val nativeMenuEnabled = shouldUseNativeLiquidGlassDropdown(
        nativeControlsAvailable = isNativeLiquidGlassControlsAvailable(),
        controlEnabled = enabled,
        previewGlass = previewGlass,
        hasGlassSurface = glassSurface != null,
        surfaceGlassEnabled = glassSurface?.let { surface ->
            isLiquidGlassSurfaceEffectivelyEnabled(surface, preview = false, platformSupported = true)
        } == true,
    )
    val nativeTriggerEnabled = enabled &&
        glassSurface != null &&
        isNativeLiquidGlassControlsAvailable() &&
        isLiquidGlassSurfaceEffectivelyEnabled(
            surface = glassSurface,
            preview = previewGlass,
            platformSupported = true,
        )
    val defaultShape = tokens.shapes.compactCard
    val chipShape = glassSurface?.let { surface ->
        liquidGlassShape(surface, defaultShape, preview = previewGlass)
    } ?: defaultShape
    val containerModifier = if (glassSurface != null && glassEnabled) {
        Modifier.liquidGlassForSurface(
            surface = glassSurface,
            shape = chipShape,
            tint = tokens.colors.surface.copy(alpha = 0.25f),
            fallback = tokens.colors.surface,
            blurRadius = 12.dp,
            refractionHeight = 6.dp,
            refractionAmount = 9.dp,
            preview = previewGlass,
        ).clip(chipShape)
    } else {
        Modifier.clip(chipShape).background(tokens.colors.surface)
    }
    var isSheetVisible by remember { mutableStateOf(false) }
    var isNativeMenuVisible by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()

    val selectionFrameModifier = if (glassSurface != null && selectedPreviewSurface == glassSurface) {
        Modifier
            .border(2.dp, MaterialTheme.colorScheme.primary, chipShape)
            .padding(2.dp)
    } else {
        Modifier
    }

    val openOptions: () -> Unit = {
        onGlassSurfaceInteracted?.invoke()
        if (nativeMenuEnabled) {
            isNativeMenuVisible = true
        } else {
            isSheetVisible = true
        }
    }

    if (nativeTriggerEnabled) {
        val nativeWidth = (label.length * 8 + 44).coerceIn(92, 224).dp
        NuvioNativeLiquidGlassButton(
            title = label,
            systemImage = "chevron.down",
            accessibilityLabel = "$title, $label",
            enabled = enabled,
            onClick = openOptions,
            modifier = modifier
                .then(selectionFrameModifier)
                .size(width = nativeWidth, height = 40.dp),
        )
    } else {
        LiquidGlassTextContrastScope(enabled = glassEnabled) {
            Row(
                modifier = modifier
                    .then(selectionFrameModifier)
                    .then(containerModifier)
                    .then(if (enabled) Modifier.clickable(onClick = openOptions) else Modifier)
                    .padding(horizontal = NuvioTokens.Space.s12, vertical = tokens.components.chipVerticalPadding),
                horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (enabled) tokens.colors.textPrimary else tokens.colors.textDisabled,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(NuvioTokens.Icon.sm + NuvioTokens.Space.s2),
                    tint = if (enabled) {
                        glassSurface?.let { surface -> liquidGlassIconTint(surface, tokens.colors.textMuted) }
                            ?: tokens.colors.textMuted
                    } else {
                        tokens.colors.borderDefault
                    },
                )
            }
        }
    }

    NuvioNativeLiquidGlassOptionMenu(
        visible = isNativeMenuVisible && nativeMenuEnabled,
        title = title,
        cancelLabel = stringResource(Res.string.action_cancel),
        options = options.map { option -> NativeLiquidGlassMenuOption(id = option.key, title = option.label) },
        selectedOptionId = selectedKey,
        onOptionSelected = { selectedId ->
            options.firstOrNull { option -> option.key == selectedId }?.let(onSelected)
            isNativeMenuVisible = false
        },
        onDismiss = { isNativeMenuVisible = false },
    )

    if (isSheetVisible) {
        NuvioDropdownOptionsSheet(
            title = title,
            options = options,
            selectedKey = selectedKey,
            sheetState = sheetState,
            onDismiss = {
                coroutineScope.launch {
                    dismissNuvioBottomSheet(
                        sheetState = sheetState,
                        onDismiss = { isSheetVisible = false },
                    )
                }
            },
            onSelected = { option ->
                onSelected(option)
                coroutineScope.launch {
                    dismissNuvioBottomSheet(
                        sheetState = sheetState,
                        onDismiss = { isSheetVisible = false },
                    )
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NuvioDropdownOptionsSheet(
    title: String,
    options: List<NuvioDropdownOption>,
    selectedKey: String?,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onSelected: (NuvioDropdownOption) -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    NuvioModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = nuvioSafeBottomPadding(tokens.spacing.screenHorizontal)),
        ) {
            Text(
                text = title,
                modifier = Modifier.padding(horizontal = tokens.spacing.screenHorizontal, vertical = NuvioTokens.Space.s14),
                style = MaterialTheme.typography.titleLarge,
                color = tokens.colors.textPrimary,
            )
            NuvioBottomSheetDivider()
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = tokens.breakpoints.largePhone),
            ) {
                items(options) { option ->
                    NuvioBottomSheetActionRow(
                        title = option.label,
                        onClick = { onSelected(option) },
                        selected = option.key == selectedKey,
                        trailingContent = {
                            if (option.key == selectedKey) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = tokens.colors.accent,
                                    modifier = Modifier.size(tokens.icons.md),
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}
