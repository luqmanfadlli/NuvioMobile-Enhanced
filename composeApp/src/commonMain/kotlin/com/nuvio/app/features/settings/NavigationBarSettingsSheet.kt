package com.nuvio.app.features.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.NuvioBottomSheetActionRow
import com.nuvio.app.core.ui.NuvioBottomSheetDivider
import com.nuvio.app.core.ui.NuvioModalBottomSheet
import com.nuvio.app.core.ui.NuvioPrimaryButton
import com.nuvio.app.core.ui.dismissNuvioBottomSheet
import com.nuvio.app.core.ui.floatingNavigationGlowSupported
import com.nuvio.app.core.ui.glass.LiquidGlassMaterial
import com.nuvio.app.core.ui.glass.LiquidGlassStyle
import com.nuvio.app.core.ui.glass.isLiquidGlassSupported
import com.nuvio.app.core.ui.glass.isLiquidRefractionSupported
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NavigationBarSettingsSheet(
    isTablet: Boolean,
    tabletLayout: Boolean = false,
    selectedStyle: NavBarStyle,
    onStyleSelected: (NavBarStyle) -> Unit,
    glowEnabled: Boolean,
    onGlowChanged: (Boolean) -> Unit,
    selectedPosition: NavBarPosition,
    onPositionSelected: (NavBarPosition) -> Unit,
    glassStyle: LiquidGlassStyle = LiquidGlassStyle.Default,
    onGlassStyleChanged: (LiquidGlassStyle) -> Unit = {},
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val dismiss: () -> Unit = {
        scope.launch { dismissNuvioBottomSheet(sheetState, onDismiss) }
    }
    NuvioModalBottomSheet(onDismissRequest = dismiss, sheetState = sheetState) {
        LazyColumn(Modifier.fillMaxWidth()) {
            item {
                Text(
                    text = stringResource(Res.string.settings_appearance_nav_bar_style),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
                NavigationBarPreview(selectedStyle, isTablet, glowEnabled, selectedPosition, tabletLayout, glassStyle)
            }
            if (!isTablet) {
                NavBarStyle.entries.forEach { style ->
                    item(key = style.key) {
                        NuvioBottomSheetActionRow(
                            title = stringResource(style.labelRes),
                            onClick = { onStyleSelected(style) },
                            selected = style == selectedStyle,
                            modifier = Modifier.semantics {
                                role = Role.RadioButton
                                selected = style == selectedStyle
                            },
                            trailingContent = {
                                if (style == selectedStyle) {
                                    Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                                }
                            },
                        )
                    }
                }
            }
            item {
                // Only the floating pill can move; CLASSIC keeps its fixed place.
                AnimatedVisibility(selectedStyle != NavBarStyle.CLASSIC) {
                    Column {
                        NuvioBottomSheetDivider()
                        Text(
                            text = stringResource(Res.string.settings_nav_bar_position),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp),
                        )
                        NavBarPosition.entries.forEach { position ->
                            NuvioBottomSheetActionRow(
                                title = stringResource(position.labelRes),
                                onClick = { onPositionSelected(position) },
                                modifier = Modifier.semantics {
                                    role = Role.RadioButton
                                    selected = position == selectedPosition
                                },
                                trailingContent = {
                                    if (position == selectedPosition) {
                                        Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                },
                            )
                        }
                    }
                }
            }
            item {
                AnimatedVisibility(floatingNavigationGlowSupported && selectedStyle != NavBarStyle.CLASSIC) {
                    Column {
                        NuvioBottomSheetDivider()
                        SettingsSwitchRow(
                            title = stringResource(Res.string.settings_nav_bar_glow),
                            description = stringResource(Res.string.settings_nav_bar_glow_description),
                            checked = glowEnabled,
                            isTablet = isTablet,
                            onCheckedChange = onGlowChanged,
                        )
                    }
                }
            }
            item {
                AnimatedVisibility(isLiquidGlassSupported() && selectedStyle != NavBarStyle.CLASSIC) {
                    NavigationBarGlassSettings(
                        style = glassStyle,
                        isTablet = isTablet,
                        onStyleChanged = onGlassStyleChanged,
                    )
                }
            }
            item {
                NuvioPrimaryButton(
                    text = stringResource(Res.string.action_done),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    onClick = dismiss,
                )
            }
        }
    }
}

private val LiquidGlassMaterial.labelRes: StringResource
    get() = when (this) {
        LiquidGlassMaterial.LIQUID -> Res.string.settings_nav_bar_glass_material_liquid
        LiquidGlassMaterial.FROSTED -> Res.string.settings_nav_bar_glass_material_frosted
        LiquidGlassMaterial.CLEAR -> Res.string.settings_nav_bar_glass_material_clear
        LiquidGlassMaterial.SOLID -> Res.string.settings_nav_bar_glass_material_solid
    }

@Composable
private fun NavigationBarGlassSettings(
    style: LiquidGlassStyle,
    isTablet: Boolean,
    onStyleChanged: (LiquidGlassStyle) -> Unit,
) {
    val refractionSupported = isLiquidRefractionSupported()
    val materials = LiquidGlassMaterial.entries.filter {
        refractionSupported || it != LiquidGlassMaterial.CLEAR
    }
    Column {
        NuvioBottomSheetDivider()
        Text(
            text = stringResource(Res.string.settings_nav_bar_glass),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp),
        )
        materials.forEach { material ->
            NuvioBottomSheetActionRow(
                title = stringResource(material.labelRes),
                onClick = { onStyleChanged(style.copy(material = material)) },
                modifier = Modifier.semantics {
                    role = Role.RadioButton
                    selected = material == style.material
                },
                trailingContent = {
                    if (material == style.material) {
                        Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                    }
                },
            )
        }
        AnimatedVisibility(style.usesBlur) {
            SettingsSliderRow(
                title = stringResource(Res.string.settings_nav_bar_glass_blur),
                value = style.blurRadius,
                valueText = stringResource(Res.string.settings_nav_bar_glass_value_dp, style.blurRadius),
                valueRange = LiquidGlassStyle.BlurRange,
                step = 1,
                isTablet = isTablet,
                onValueChange = { onStyleChanged(style.copy(blurRadius = it)) },
            )
        }
        AnimatedVisibility(style.usesLens && refractionSupported) {
            SettingsSliderRow(
                title = stringResource(Res.string.settings_nav_bar_glass_refraction),
                value = style.refraction,
                valueText = stringResource(Res.string.settings_nav_bar_glass_value_dp, style.refraction),
                valueRange = LiquidGlassStyle.RefractionRange,
                step = 2,
                isTablet = isTablet,
                onValueChange = { onStyleChanged(style.copy(refraction = it)) },
            )
        }
        AnimatedVisibility(style.usesBackdrop) {
            Column {
                SettingsSliderRow(
                    title = stringResource(Res.string.settings_nav_bar_glass_tint),
                    value = style.tintPercent,
                    valueText = stringResource(Res.string.settings_nav_bar_glass_value_percent, style.tintPercent),
                    valueRange = LiquidGlassStyle.TintRange,
                    step = 5,
                    isTablet = isTablet,
                    onValueChange = { onStyleChanged(style.copy(tintPercent = it)) },
                )
                SettingsSwitchRow(
                    title = stringResource(Res.string.settings_nav_bar_glass_vibrancy),
                    description = stringResource(Res.string.settings_nav_bar_glass_vibrancy_description),
                    checked = style.vibrancy,
                    isTablet = isTablet,
                    onCheckedChange = { onStyleChanged(style.copy(vibrancy = it)) },
                )
                SettingsSwitchRow(
                    title = stringResource(Res.string.settings_nav_bar_glass_shadow),
                    description = stringResource(Res.string.settings_nav_bar_glass_shadow_description),
                    checked = style.shadow,
                    isTablet = isTablet,
                    onCheckedChange = { onStyleChanged(style.copy(shadow = it)) },
                )
                if (refractionSupported) {
                    SettingsSwitchRow(
                        title = stringResource(Res.string.settings_nav_bar_glass_selection_lens),
                        description = stringResource(Res.string.settings_nav_bar_glass_selection_lens_description),
                        checked = style.selectionLens,
                        isTablet = isTablet,
                        onCheckedChange = { onStyleChanged(style.copy(selectionLens = it)) },
                    )
                }
            }
        }
        AnimatedVisibility(refractionSupported && style.usesBackdrop && (style.usesLens || style.selectionLens)) {
            SettingsSwitchRow(
                title = stringResource(Res.string.settings_nav_bar_glass_dispersion),
                description = stringResource(Res.string.settings_nav_bar_glass_dispersion_description),
                checked = style.chromaticAberration,
                isTablet = isTablet,
                onCheckedChange = { onStyleChanged(style.copy(chromaticAberration = it)) },
            )
        }
        SettingsSwitchRow(
            title = stringResource(Res.string.settings_nav_bar_glass_highlight),
            description = stringResource(Res.string.settings_nav_bar_glass_highlight_description),
            checked = style.edgeHighlight,
            isTablet = isTablet,
            onCheckedChange = { onStyleChanged(style.copy(edgeHighlight = it)) },
        )
        AnimatedVisibility(style != LiquidGlassStyle.Default) {
            NuvioBottomSheetActionRow(
                title = stringResource(Res.string.settings_nav_bar_glass_reset),
                onClick = { onStyleChanged(LiquidGlassStyle.Default) },
            )
        }
    }
}
