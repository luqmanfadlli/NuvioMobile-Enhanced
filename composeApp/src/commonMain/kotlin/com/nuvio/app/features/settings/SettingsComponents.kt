package com.nuvio.app.features.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.NuvioThemeTokens
import com.nuvio.app.core.ui.NuvioNativeLiquidGlassSwitch
import com.nuvio.app.core.ui.isNativeLiquidGlassControlsAvailable
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.NuvioSurfaceCard
import com.nuvio.app.core.ui.LocalDynamicArtworkBackgroundActive
import com.nuvio.app.core.ui.NuvioBackButton
import com.nuvio.app.core.ui.NuvioSectionLabel
import com.nuvio.app.core.ui.accentBrush
import com.nuvio.app.core.ui.gradientMask
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.core.ui.themePalette
import com.nuvio.app.core.ui.nuvioConsumePointerEvents
import com.nuvio.app.core.ui.glass.LiquidGlassSettings
import com.nuvio.app.core.ui.glass.LiquidGlassSurface
import com.nuvio.app.core.ui.glass.LiquidGlassTextContrastScope
import com.nuvio.app.core.ui.glass.withLiquidGlassTextContrast
import com.nuvio.app.core.ui.glass.withLiquidGlassTextShadow
import com.nuvio.app.core.ui.glass.LocalLiquidGlassBackdrop
import com.nuvio.app.core.ui.glass.isLiquidGlassSurfacePreferenceEnabled
import com.nuvio.app.core.ui.glass.nuvioBackdropSource
import com.nuvio.app.core.ui.glass.rememberNuvioBackdrop
import com.nuvio.app.core.ui.glass.liquidGlassBackdropUnderlay
import com.nuvio.app.core.ui.glass.isLiquidGlassSurfaceEffectivelyEnabled
import com.nuvio.app.core.ui.glass.isLiquidGlassAvailable
import com.nuvio.app.core.ui.glass.isLiquidGlassSupported
import com.nuvio.app.core.ui.glass.liquidGlassForSurface
import com.nuvio.app.core.ui.glass.liquidGlassIconTint
import com.nuvio.app.core.ui.glass.liquidGlassShape
import com.nuvio.app.features.home.HomeCatalogSettingsItem
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.settings_homescreen_collection_with_addon
import nuvio.composeapp.generated.resources.settings_homescreen_display_name
import nuvio.composeapp.generated.resources.settings_homescreen_hero_source
import nuvio.composeapp.generated.resources.settings_homescreen_hidden
import nuvio.composeapp.generated.resources.settings_homescreen_not_in_hero
import nuvio.composeapp.generated.resources.settings_homescreen_pinned
import nuvio.composeapp.generated.resources.settings_homescreen_pinned_to_top
import nuvio.composeapp.generated.resources.settings_homescreen_reorder
import nuvio.composeapp.generated.resources.settings_homescreen_visible
import org.jetbrains.compose.resources.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import sh.calvin.reorderable.ReorderableCollectionItemScope

/**
 * Backward-compatible names for Settings call sites; the implementation is shared by every
 * Liquid Glass surface so text treatment stays consistent across the app.
 */
internal fun NuvioThemeTokens.withSettingsGlassTextContrast(enabled: Boolean): NuvioThemeTokens =
    withLiquidGlassTextContrast(enabled)

internal fun TextStyle.withSettingsGlassTextShadow(enabled: Boolean): TextStyle =
    withLiquidGlassTextShadow(enabled)

/**
 * Gives standalone Settings destinations the same private background and backdrop source as the
 * Settings root. Only the background/image is captured, so a glass header can never sample
 * scrolling controls underneath it.
 */
@Composable
internal fun SettingsGlassPageBackground(content: @Composable () -> Unit) {
    val backdrop = rememberNuvioBackdrop()
    val customImageUri by remember {
        ThemeSettingsRepository.ensureLoaded()
        ThemeSettingsRepository.customSettingsBackgroundImageUri
    }.collectAsStateWithLifecycle()
    val imageRevision by remember {
        ThemeSettingsRepository.customSettingsBackgroundImageRevision
    }.collectAsStateWithLifecycle()
    val platformContext = LocalPlatformContext.current
    val imageRequest = remember(customImageUri, imageRevision, platformContext) {
        customImageUri?.let { imageUrl ->
            ImageRequest.Builder(platformContext)
                .data(imageUrl)
                .memoryCacheKey("$imageUrl#settings-background-$imageRevision")
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.DISABLED)
                .build()
        }
    }
    var imageLoaded by remember(customImageUri, imageRevision) { mutableStateOf(false) }
    val settingsGlassEnabled = LiquidGlassSettings.enabled &&
        isLiquidGlassSurfacePreferenceEnabled(LiquidGlassSurface.SETTINGS) &&
        isLiquidGlassSupported()

    Box(modifier = Modifier.fillMaxSize()) {
        if (settingsGlassEnabled || customImageUri != null) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .then(if (settingsGlassEnabled) Modifier.nuvioBackdropSource(backdrop) else Modifier),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                )
                imageRequest?.let { request ->
                    AsyncImage(
                        model = request,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        onSuccess = { imageLoaded = true },
                        onError = { imageLoaded = false },
                    )
                    if (imageLoaded) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.28f)),
                        )
                    }
                }
            }
        }
        CompositionLocalProvider(
            LocalLiquidGlassBackdrop provides if (settingsGlassEnabled) backdrop else null,
            LocalDynamicArtworkBackgroundActive provides (
                LocalDynamicArtworkBackgroundActive.current || customImageUri != null
            ),
        ) {
            LiquidGlassTextContrastScope(enabled = settingsGlassEnabled) {
                content()
            }
        }
    }
}

@Composable
private fun SettingsCard(
    isTablet: Boolean,
    modifier: Modifier = Modifier,
    previewGlass: Boolean = false,
    contentPadding: Boolean = false,
    tonalElevation: Int = 0,
    content: @Composable ColumnScope.() -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val defaultCardShape = if (isTablet) RoundedCornerShape(NuvioTokens.Radius.xl) else tokens.shapes.compactCard
    val glassEnabled = isLiquidGlassSurfaceEffectivelyEnabled(
        surface = LiquidGlassSurface.SETTINGS,
        preview = previewGlass,
        platformSupported = if (previewGlass) isLiquidGlassAvailable() else isLiquidGlassSupported(),
    )
    val cardShape = if (glassEnabled) {
        liquidGlassShape(LiquidGlassSurface.SETTINGS, defaultCardShape, preview = previewGlass)
    } else {
        defaultCardShape
    }
    Surface(
        modifier = modifier.fillMaxWidth().then(
            if (glassEnabled) {
                Modifier.liquidGlassForSurface(
                    surface = LiquidGlassSurface.SETTINGS,
                    shape = cardShape,
                    tint = tokens.colors.surface.copy(alpha = 0.18f),
                    fallback = tokens.colors.surface,
                    blurRadius = 12.dp,
                    refractionHeight = 6.dp,
                    refractionAmount = 9.dp,
                    preview = previewGlass,
                )
            } else {
                Modifier
            },
        ),
        color = if (glassEnabled) Color.Transparent else tokens.colors.surface,
        shape = cardShape,
        border = BorderStroke(
            tokens.borders.hairline,
            tokens.colors.borderSubtle,
        ),
        tonalElevation = tonalElevation.dp,
        shadowElevation = if (contentPadding) tokens.elevation.flat else 0.dp,
    ) {
        LiquidGlassTextContrastScope(enabled = glassEnabled) {
            Column(
                modifier = if (contentPadding) Modifier.padding(tokens.spacing.cardPadding) else Modifier,
                content = content,
            )
        }
    }
}

@Composable
internal fun SettingsSurfaceCard(
    isTablet: Boolean = false,
    modifier: Modifier = Modifier,
    tonalElevation: Int = 0,
    content: @Composable ColumnScope.() -> Unit,
) {
    val glassEnabled = isLiquidGlassSurfaceEffectivelyEnabled(
        surface = LiquidGlassSurface.SETTINGS,
        platformSupported = isLiquidGlassSupported(),
    )
    if (glassEnabled) {
        SettingsCard(
            isTablet = isTablet,
            modifier = modifier,
            contentPadding = true,
            tonalElevation = tonalElevation,
            content = content,
        )
    } else {
        NuvioSurfaceCard(
            modifier = modifier,
            tonalElevation = tonalElevation,
            content = content,
        )
    }
}

@Composable
internal fun SettingsGroup(
    isTablet: Boolean,
    modifier: Modifier = Modifier,
    previewGlass: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    SettingsCard(
        isTablet = isTablet,
        modifier = modifier,
        previewGlass = previewGlass,
    ) {
        Column(content = content)
    }
}

@Composable
internal fun SettingsGroupDivider(isTablet: Boolean) {
    val tokens = MaterialTheme.nuvio
    HorizontalDivider(
        modifier = Modifier.padding(start = if (isTablet) NuvioTokens.Space.s80 - NuvioTokens.Space.s2 else NuvioTokens.Space.s64 + NuvioTokens.Space.s2),
        thickness = tokens.borders.hairline,
        color = tokens.colors.borderSubtle,
    )
}

@Composable
internal fun TabletPageHeader(
    title: String,
    showBack: Boolean,
    onBack: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val glassEnabled = isLiquidGlassSurfaceEffectivelyEnabled(
        surface = LiquidGlassSurface.SETTINGS,
        platformSupported = isLiquidGlassSupported(),
    )
    val shape = if (glassEnabled) {
        liquidGlassShape(LiquidGlassSurface.SETTINGS, RoundedCornerShape(NuvioTokens.Radius.xl))
    } else {
        RoundedCornerShape(NuvioTokens.Radius.xl)
    }
    val backdrop = if (glassEnabled) LocalLiquidGlassBackdrop.current else null
    Box(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(tokens.colors.background)
                .nuvioConsumePointerEvents(),
        )
        if (glassEnabled) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .liquidGlassBackdropUnderlay(backdrop),
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .liquidGlassForSurface(
                        surface = LiquidGlassSurface.SETTINGS,
                        shape = shape,
                        tint = tokens.colors.surface.copy(alpha = 0.18f),
                        fallback = tokens.colors.surface,
                        blurRadius = 12.dp,
                        refractionHeight = 6.dp,
                        refractionAmount = 9.dp,
                    )
                    .border(
                        BorderStroke(tokens.borders.hairline, tokens.colors.borderSubtle),
                        shape,
                    ),
            )
        }
        LiquidGlassTextContrastScope(enabled = glassEnabled) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.listGap),
        ) {
            if (showBack) {
                NuvioBackButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(36.dp),
                    shape = tokens.shapes.compactCard,
                    containerColor = tokens.colors.surface,
                    contentColor = tokens.colors.textPrimary,
                    buttonSize = NuvioTokens.Space.s36,
                    iconSize = tokens.icons.md,
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge,
                color = tokens.colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
            )
        }
        }
    }
}

@Composable
internal fun SettingsSidebarItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val primary = tokens.colors.accent
    val background = if (selected) primary.copy(alpha = tokens.opacity.hover) else Color.Transparent
    val iconChip = if (selected) primary.copy(alpha = tokens.opacity.selected) else Color.Transparent
    val contentColor = if (selected) tokens.colors.textPrimary else tokens.colors.textMuted

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = tokens.spacing.listGap, vertical = NuvioTokens.Space.s2)
            .background(background, RoundedCornerShape(NuvioTokens.Space.s10))
            .clickable(onClick = onClick)
            .padding(horizontal = tokens.spacing.screenHorizontal, vertical = tokens.spacing.listGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(tokens.icons.xl),
            color = iconChip,
            shape = RoundedCornerShape(NuvioTokens.Radius.md),
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = if (selected) {
                        Modifier.gradientMask(MaterialTheme.themePalette.accentBrush())
                    } else {
                        Modifier
                    },
                    tint = if (selected) primary else liquidGlassIconTint(LiquidGlassSurface.SETTINGS, contentColor),
                )
            }
        }
        Spacer(modifier = Modifier.width(tokens.spacing.listGap))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = contentColor,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}

@Composable
internal fun SettingsSection(
    title: String,
    isTablet: Boolean,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = NuvioTokens.Radius.card),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NuvioSectionLabel(text = title)
            Row(
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }
        Spacer(modifier = Modifier.height(if (isTablet) tokens.spacing.listGap else NuvioTokens.Space.s10))
        content()
    }
}

@Composable
internal fun SettingsClickableRow(
    title: String,
    enabled: Boolean = true,
    isTablet: Boolean,
    onClick: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val verticalPadding = if (isTablet) 16.dp else 14.dp
    val horizontalPadding = if (isTablet) 20.dp else 16.dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = horizontalPadding, vertical = verticalPadding)
            .alpha(if (enabled) NuvioTokens.Opacity.visible else tokens.opacity.medium),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = tokens.colors.accent,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
internal fun SettingsNavigationRow(
    title: String,
    description: String?,
    icon: ImageVector? = null,
    iconPainter: Painter? = null,
    enabled: Boolean = true,
    isTablet: Boolean,
    trailingContent: (@Composable RowScope.() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val iconSize = if (isTablet) 42.dp else 36.dp
    val verticalPadding = if (isTablet) 16.dp else 14.dp
    val horizontalPadding = if (isTablet) 20.dp else 16.dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = horizontalPadding, vertical = verticalPadding)
            .alpha(if (enabled) NuvioTokens.Opacity.visible else tokens.opacity.medium),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
                .widthIn(max = if (isTablet) 560.dp else Dp.Unspecified),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null || iconPainter != null) {
                Surface(
                    modifier = Modifier.size(iconSize),
                    color = tokens.colors.accent.copy(alpha = tokens.opacity.pressed),
                    shape = tokens.shapes.compactCard,
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (iconPainter != null) {
                            androidx.compose.foundation.Image(
                                painter = iconPainter,
                                contentDescription = null,
                                modifier = Modifier.size(if (isTablet) 28.dp else 24.dp),
                                contentScale = ContentScale.Fit,
                            )
                        } else if (icon != null) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                modifier = Modifier.gradientMask(MaterialTheme.themePalette.accentBrush()),
                                tint = tokens.colors.accent,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(if (isTablet) 16.dp else 14.dp))
            }
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = tokens.colors.textPrimary,
                    fontWeight = FontWeight.Medium,
                )
                if (!description.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = tokens.colors.textMuted,
                        modifier = Modifier.alpha(0.92f),
                    )
                }
            }
        }
        trailingContent?.invoke(this)
    }
}

@Composable
internal fun SettingsSwitchRow(
    title: String,
    description: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    isTablet: Boolean,
    horizontalPadding: Dp = if (isTablet) 20.dp else 16.dp,
    onCheckedChange: (Boolean) -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val verticalPadding = if (isTablet) 16.dp else 14.dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
                .widthIn(max = if (isTablet) 560.dp else Dp.Unspecified)
                .alpha(if (enabled) NuvioTokens.Opacity.visible else tokens.opacity.medium),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = tokens.colors.textPrimary,
                fontWeight = FontWeight.Medium,
            )
            if (!description.isNullOrBlank()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.colors.textMuted,
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            modifier = Modifier.padding(start = 4.dp),
            colors = SwitchDefaults.colors(
                checkedThumbColor = tokens.colors.onAccent,
                checkedTrackColor = tokens.colors.accent,
                uncheckedThumbColor = tokens.colors.textMuted,
                uncheckedTrackColor = tokens.colors.borderDefault,
            ),
        )
    }
}

@Composable
internal fun LiquidGlassSettingsSwitchRow(
    title: String,
    description: String?,
    checked: Boolean,
    isTablet: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    if (!isNativeLiquidGlassControlsAvailable()) {
        SettingsSwitchRow(
            title = title,
            description = description,
            checked = checked,
            isTablet = isTablet,
            onCheckedChange = onCheckedChange,
        )
        return
    }

    val tokens = MaterialTheme.nuvio
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(
                horizontal = if (isTablet) 20.dp else 16.dp,
                vertical = if (isTablet) 16.dp else 14.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f).padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = tokens.colors.textPrimary,
                fontWeight = FontWeight.Medium,
            )
            if (!description.isNullOrBlank()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.colors.textMuted,
                )
            }
        }
        NuvioNativeLiquidGlassSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            accessibilityLabel = title,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SettingsChipRow(
    title: String,
    description: String,
    isTablet: Boolean,
    footer: String? = null,
    chips: @Composable FlowRowScope.() -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = if (isTablet) 20.dp else 16.dp,
                vertical = if (isTablet) 16.dp else 14.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = tokens.colors.textPrimary,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.colors.textMuted,
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = chips,
        )
        if (footer != null) {
            Text(
                text = footer,
                style = MaterialTheme.typography.bodySmall,
                color = tokens.colors.textMuted,
            )
        }
    }
}

@Composable
internal fun HomescreenCatalogRow(
    item: HomeCatalogSettingsItem,
    isTablet: Boolean,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onTitleChange: (String) -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    dragHandleScope: ReorderableCollectionItemScope,
    onPinnedDragAttempt: () -> Unit = {},
) {
    val tokens = MaterialTheme.nuvio
    val horizontalPadding = if (isTablet) 20.dp else 16.dp
    val verticalPadding = if (isTablet) 18.dp else 16.dp
    val hapticFeedback = LocalHapticFeedback.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onExpandedChange(!expanded) }
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp)
                    .then(if (isTablet) Modifier.widthIn(max = 560.dp) else Modifier),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = item.displayTitle,
                    style = MaterialTheme.typography.bodyLarge,
                    color = tokens.colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (item.isCollection) {
                        stringResource(Res.string.settings_homescreen_collection_with_addon, item.addonName)
                    } else {
                        item.addonName
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.colors.textMuted,
                )
                Text(
                    text = buildString {
                        append(
                            if (item.enabled) {
                                stringResource(Res.string.settings_homescreen_visible)
                            } else {
                                stringResource(Res.string.settings_homescreen_hidden)
                            },
                        )
                        if (item.isCollection) {
                            if (item.isPinnedToTop) {
                                append(" • ")
                                append(stringResource(Res.string.settings_homescreen_pinned_to_top))
                            }
                        } else {
                            append(" • ")
                            append(
                                if (item.heroSourceEnabled) {
                                    stringResource(Res.string.settings_homescreen_hero_source)
                                } else {
                                    stringResource(Res.string.settings_homescreen_not_in_hero)
                                },
                            )
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.colors.textMuted,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Switch(
                    checked = item.enabled,
                    onCheckedChange = onEnabledChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = tokens.colors.onAccent,
                        checkedTrackColor = tokens.colors.accent,
                        uncheckedThumbColor = tokens.colors.textMuted,
                        uncheckedTrackColor = tokens.colors.borderDefault,
                    ),
                )
                if (item.isPinnedToTop) {
                    IconButton(
                        onClick = onPinnedDragAttempt,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = stringResource(Res.string.settings_homescreen_pinned),
                            tint = liquidGlassIconTint(LiquidGlassSurface.SETTINGS, tokens.colors.textMuted.copy(alpha = tokens.opacity.medium)),
                        )
                    }
                } else {
                    IconButton(
                        modifier = with(dragHandleScope) {
                            Modifier.draggableHandle(
                                onDragStarted = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDragStopped = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                },
                            )
                        },
                        onClick = {},
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Menu,
                            contentDescription = stringResource(Res.string.settings_homescreen_reorder),
                            tint = liquidGlassIconTint(LiquidGlassSurface.SETTINGS, tokens.colors.textMuted),
                        )
                    }
                }
            }
        }

        AnimatedVisibility(visible = expanded && !item.isCollection) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = item.customTitle,
                    onValueChange = onTitleChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(Res.string.settings_homescreen_display_name)) },
                    placeholder = { Text(item.defaultTitle) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = tokens.colors.borderFocus.copy(alpha = tokens.opacity.strong),
                        unfocusedBorderColor = tokens.colors.borderDefault.copy(alpha = tokens.opacity.medium),
                        focusedContainerColor = tokens.colors.surface,
                        unfocusedContainerColor = tokens.colors.surface,
                        disabledContainerColor = tokens.colors.surface,
                    ),
                )
            }
        }
    }
}

