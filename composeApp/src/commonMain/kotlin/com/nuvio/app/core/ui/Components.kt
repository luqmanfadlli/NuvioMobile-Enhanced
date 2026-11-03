package com.nuvio.app.core.ui

import androidx.compose.material3.contentColorFor
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import com.nuvio.app.core.ui.glass.liquidGlassSurface
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_back
import nuvio.composeapp.generated.resources.action_ok
import org.jetbrains.compose.resources.stringResource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.nuvio.app.navigation.LocalNativeNavigationBarHidden
import com.nuvio.app.navigation.LocalUseNativeNavigation
import com.nuvio.app.core.ui.glass.LiquidGlassSurface
import com.nuvio.app.core.ui.glass.LocalLiquidGlassBackdrop
import com.nuvio.app.core.ui.glass.isLiquidGlassSurfaceEffectivelyEnabled
import com.nuvio.app.core.ui.glass.isLiquidGlassSupported
import com.nuvio.app.core.ui.glass.liquidGlassBackdropUnderlay
import com.nuvio.app.core.ui.glass.liquidGlassForSurface

@Composable
fun NuvioScreen(
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = MaterialTheme.nuvio.spacing.screenHorizontal,
    topPadding: Dp? = null,
    listState: LazyListState = rememberLazyListState(),
    autoHidesNativeTabBar: Boolean = false,
    transparentBackground: Boolean = false,
    content: LazyListScope.() -> Unit,
) {
    NativeTabBarScrollEffect(listState = listState, enabled = autoHidesNativeTabBar)
    val tokens = MaterialTheme.nuvio
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val screenBackground = if (transparentBackground || LocalDynamicArtworkBackgroundActive.current) {
        Color.Transparent
    } else {
        tokens.colors.background
    }
    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .background(screenBackground),
        contentPadding = PaddingValues(
            start = horizontalPadding,
            top = topPadding ?: tokens.spacing.screenTop + statusBarTop + nuvioPlatformExtraTopPadding,
            end = horizontalPadding,
            bottom = nuvioSafeBottomPadding(tokens.spacing.screenBottom),
        ).withVerticalBar(),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.listGap),
        content = content,
    )
}

internal fun Modifier.nuvioConsumePointerEvents(): Modifier =
    pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                awaitPointerEvent(PointerEventPass.Final).changes.forEach { change ->
                    change.consume()
                }
            }
        }
    }

@Composable
fun NuvioSurfaceCard(
    modifier: Modifier = Modifier,
    tonalElevation: Int = 0,
    content: @Composable ColumnScope.() -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val appGlass = LocalAppBackgroundActive.current
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(if (appGlass) Modifier.appBackgroundGlass(tokens.shapes.card) else Modifier),
        color = if (appGlass) Color.Transparent else tokens.colors.surface,
        contentColor = if (appGlass) MaterialTheme.colorScheme.onSurface else contentColorFor(tokens.colors.surface),
        shape = tokens.shapes.card,
        tonalElevation = if (appGlass) 0.dp else tonalElevation.dp,
        shadowElevation = if (appGlass) 0.dp else tokens.elevation.flat,
    ) {
        Column(
            modifier = Modifier.padding(tokens.spacing.cardPadding),
            content = content,
        )
    }
}

internal fun settingsHeaderShape(settingsGlassEnabled: Boolean, defaultShape: Shape): Shape =
    if (settingsGlassEnabled) RectangleShape else defaultShape

@Composable
fun NuvioScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    includeStatusBarPadding: Boolean = true,
    topPadding: Dp? = null,
    onBack: (() -> Unit)? = null,
    useSettingsGlass: Boolean = false,
    glassSurface: LiquidGlassSurface? = null,
    backgroundBrushOverride: Brush? = null,
    backgroundBrushBottomInset: Dp = 0.dp,
    contentHorizontalPadding: Dp = 0.dp,
    appBackgroundGlass: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val tokens = MaterialTheme.nuvio
    val appBackgroundActive = LocalAppBackgroundActive.current
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val headerGlassSurface = glassSurface ?: LiquidGlassSurface.SETTINGS.takeIf { useSettingsGlass }
    val settingsGlassEnabled = headerGlassSurface?.let { surface ->
        isLiquidGlassSurfaceEffectivelyEnabled(surface = surface, platformSupported = isLiquidGlassSupported())
    } == true
    val settingsBackdrop = if (settingsGlassEnabled) LocalLiquidGlassBackdrop.current else null
    val headerShape = settingsHeaderShape(
        settingsGlassEnabled = settingsGlassEnabled,
        defaultShape = tokens.shapes.compactCard,
    )
    val headerBackgroundModifier = backgroundBrushOverride?.let { brush ->
        Modifier.background(brush)
    } ?: Modifier.background(
        if ((glassSurface != null && settingsGlassEnabled) || appBackgroundActive) {
            Color.Transparent
        } else {
            tokens.colors.background
        },
    )
    val nativeDetailNavigation = LocalUseNativeNavigation.current &&
        !LocalNativeNavigationBarHidden.current &&
        onBack != null
    if (nativeDetailNavigation) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(bottom = NuvioTokens.Space.s4),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
            content = actions,
        )
        return
    }
    val resolvedTopPadding = topPadding ?: if (includeStatusBarPadding) statusBarTop else NuvioTokens.Space.none
    Box(
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .matchParentSize()
                .nuvioConsumePointerEvents()
                .padding(
                    bottom = if (backgroundBrushOverride != null) {
                        backgroundBrushBottomInset.coerceAtLeast(0.dp)
                    } else {
                        0.dp
                    },
                )
                .then(headerBackgroundModifier),
        ) {}
        if (!settingsGlassEnabled && appBackgroundGlass && appBackgroundActive) {
            Box(modifier = Modifier.matchParentSize().appBackgroundHeaderGlass())
        }
        if (settingsGlassEnabled) {
            var headerOriginInWindow by remember { mutableStateOf(Offset.Zero) }
            val windowWidthPx = LocalWindowInfo.current.containerSize.width
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .onGloballyPositioned { headerOriginInWindow = it.positionInWindow() },
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .extendToWindowEdges(headerOriginInWindow, windowWidthPx)
                    .liquidGlassBackdropUnderlay(settingsBackdrop),
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .extendToWindowEdges(headerOriginInWindow, windowWidthPx)
                    .liquidGlassForSurface(
                        surface = headerGlassSurface ?: LiquidGlassSurface.SETTINGS,
                        shape = headerShape,
                        tint = tokens.colors.surface.copy(alpha = 0.45f),
                        fallback = tokens.colors.surface,
                        blurRadius = 14.dp,
                        refractionHeight = 8.dp,
                        refractionAmount = 12.dp,
                        withHighlight = false,
                    )
                    .bottomHairline(),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = contentHorizontalPadding)
                .padding(top = resolvedTopPadding, bottom = NuvioTokens.Space.s4),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.controlGap),
            ) {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(Res.string.action_back),
                            tint = tokens.colors.textPrimary,
                        )
                    }
                }
                AnimatedContent(
                    targetState = title,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "screen_header_title",
                ) { currentTitle ->
                    Text(
                        text = currentTitle,
                        style = MaterialTheme.typography.displayLarge,
                        color = tokens.colors.textPrimary,
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s2),
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }
    }
}

@Composable
fun NuvioSectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.nuvio.colors.textMuted,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
fun NuvioActionLabel(
    text: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Text(
        text = text,
        modifier = modifier.then(
            if (onClick != null) {
                Modifier.clickable(onClick = onClick)
            } else {
                Modifier
            }
        ),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.nuvio.colors.accent,
    )
}

@Composable
fun NuvioIconActionButton(
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.nuvio.colors.textPrimary,
    onClick: () -> Unit = {},
) {
    val tokens = MaterialTheme.nuvio
    IconButton(
        modifier = modifier
            .background(
                color = tokens.colors.background.copy(alpha = 0.001f),
                shape = tokens.shapes.avatar,
            ),
        onClick = onClick,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
        )
    }
}

@Composable
fun NuvioBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.nuvio.shapes.avatar,
    containerColor: Color = MaterialTheme.nuvio.colors.surface,
    contentColor: Color = MaterialTheme.nuvio.colors.textPrimary,
    buttonSize: Dp = NuvioTokens.Space.s40,
    iconSize: Dp = NuvioTokens.Icon.md,
    contentDescription: String = stringResource(Res.string.action_back),
) {
    if (LocalUseNativeNavigation.current && !LocalNativeNavigationBarHidden.current) return

    Box(
        modifier = modifier
            .size(buttonSize)
            .clip(shape)
            .background(containerColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = contentDescription,
            tint = contentColor,
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
fun NuvioPrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit = {},
) {
    val tokens = MaterialTheme.nuvio
    val palette = MaterialTheme.themePalette
    val containerBrush = if (enabled) {
        palette.accentBrush()
    } else {
        palette.accentBrush(alpha = tokens.opacity.disabled)
    }
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(NuvioTokens.Space.s48 + NuvioTokens.Space.s4)
            .clip(tokens.shapes.button)
            .background(containerBrush),
        enabled = enabled,
        shape = tokens.shapes.button,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = tokens.colors.onAccent,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = tokens.colors.onAccent.copy(alpha = tokens.opacity.disabled),
        ),
    ) {
        AnimatedContent(
            targetState = text,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "buttonText",
        ) { animatedText ->
            Text(
                text = animatedText,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

internal fun shouldDrawNuvioInputFieldOutline(
    glassSurfaceEnabled: Boolean,
    glassRimCanRender: Boolean,
): Boolean = !glassSurfaceEnabled || !glassRimCanRender

@Composable
fun NuvioInputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.nuvio.shapes.compactCard,
    trailingContent: (@Composable (() -> Unit))? = null,
    containerColor: Color = MaterialTheme.nuvio.colors.surfaceCard,
    showOutline: Boolean = true,
) {
    val tokens = MaterialTheme.nuvio
    val appGlass = LocalAppBackgroundActive.current
    val borderColor = when {
        !showOutline -> Color.Transparent
        appGlass -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
        else -> tokens.colors.borderDefault
    }
    val fieldContainerColor = if (appGlass) Color.Transparent else containerColor
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .then(if (appGlass) Modifier.appBackgroundGlass(shape, outline = false) else Modifier),
        singleLine = true,
        shape = shape,
        placeholder = {
            Text(
                text = placeholder,
                color = tokens.colors.textMuted,
                style = MaterialTheme.typography.bodyLarge,
            )
        },
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = tokens.colors.textPrimary),
        trailingIcon = trailingContent,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = if (showOutline) tokens.colors.borderFocus else Color.Transparent,
            unfocusedBorderColor = borderColor,
            focusedContainerColor = fieldContainerColor,
            unfocusedContainerColor = fieldContainerColor,
            cursorColor = tokens.colors.accent,
        ),
    )
}

@Composable
fun NuvioInfoBadge(
    text: String,
    modifier: Modifier = Modifier,
) {
    val tokens = MaterialTheme.nuvio
    Box(
        modifier = modifier
            .background(
                color = nuvioFieldContainerColor(tokens.colors.surfaceCard),
                shape = tokens.shapes.chip,
            )
            .padding(horizontal = NuvioTokens.Space.s10, vertical = NuvioTokens.Space.s6),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = tokens.colors.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun NuvioInlineMetadata(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.nuvio.colors.textMuted,
        )
        Spacer(modifier = Modifier.width(NuvioTokens.Space.s6))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.nuvio.colors.textPrimary,
        )
    }
}

@Composable
fun NuvioStatusModal(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    isVisible: Boolean,
    isBusy: Boolean = false,
    destructive: Boolean = false,
    confirmText: String = stringResource(Res.string.action_ok),
    dismissText: String? = null,
    onConfirm: () -> Unit,
    onDismiss: (() -> Unit)? = null,
) {
    if (!isVisible) return

    DialogSurface(
        onDismissRequest = {
            if (!isBusy) {
                onDismiss?.invoke() ?: onConfirm()
            }
        },
        modifier = modifier,
        title = title,
        message = message,
    ) {
        DialogButtons {
            if (!isBusy && dismissText != null && onDismiss != null) {
                DialogButton(text = dismissText, onClick = onDismiss)
            }
            DialogButton(
                text = confirmText,
                onClick = onConfirm,
                style = if (destructive) DialogButtonStyle.Destructive else DialogButtonStyle.Primary,
                loading = isBusy,
            )
        }
    }
}

@Composable
fun NuvioToastHost(
    modifier: Modifier = Modifier,
) {
    val tokens = MaterialTheme.nuvio
    val toast by NuvioToastController.currentToast.collectAsState()
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val visibilityState = remember { MutableTransitionState(false) }
    var renderedToast by remember { mutableStateOf<NuvioToastMessage?>(null) }

    LaunchedEffect(toast?.id) {
        val currentToast = toast
        if (currentToast != null) {
            renderedToast = currentToast
            visibilityState.targetState = true
            delay(currentToast.durationMillis)
            NuvioToastController.dismiss(currentToast.id)
        } else {
            visibilityState.targetState = false
        }
    }

    LaunchedEffect(
        visibilityState.currentState,
        visibilityState.targetState,
        visibilityState.isIdle,
    ) {
        if (visibilityState.isIdle && !visibilityState.currentState && !visibilityState.targetState) {
            renderedToast = null
        }
    }

    AnimatedVisibility(
        visibleState = visibilityState,
        modifier = modifier,
        enter = fadeIn() + slideInVertically { -it },
        exit = fadeOut() + slideOutVertically { -it },
    ) {
        val currentToast = renderedToast ?: return@AnimatedVisibility
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = statusBarTop + tokens.spacing.listGap)
                .padding(horizontal = tokens.spacing.screenHorizontal),
            contentAlignment = Alignment.TopCenter,
        ) {
            Surface(
                shape = RoundedCornerShape(NuvioTokens.Radius.xl),
                color = tokens.colors.surfacePopover,
                tonalElevation = tokens.elevation.raised,
                shadowElevation = tokens.elevation.overlay,
            ) {
                Text(
                    text = currentToast.message,
                    modifier = Modifier.padding(horizontal = NuvioTokens.Space.s16, vertical = NuvioTokens.Space.s12),
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.colors.textPrimary,
                )
            }
        }
    }
}

data class NuvioToastMessage(
    val id: Long,
    val message: String,
    val durationMillis: Long,
)

object NuvioToastController {
    private val _currentToast = MutableStateFlow<NuvioToastMessage?>(null)
    val currentToast = _currentToast.asStateFlow()
    private var nextToastId = 0L

    fun show(
        message: String,
        durationMillis: Long = 2500L,
    ) {
        nextToastId += 1L
        _currentToast.value = NuvioToastMessage(
            id = nextToastId,
            message = message,
            durationMillis = durationMillis,
        )
    }

    fun dismiss(id: Long? = null) {
        val activeToast = _currentToast.value ?: return
        if (id == null || activeToast.id == id) {
            _currentToast.value = null
        }
    }
}

internal fun Modifier.extendToWindowEdges(
    originInWindow: Offset,
    windowWidthPx: Int,
    extendUp: Boolean = true,
): Modifier =
    layout { measurable, constraints ->
        if (!constraints.hasBoundedWidth || !constraints.hasBoundedHeight) {
            val placeable = measurable.measure(constraints)
            return@layout layout(placeable.width, placeable.height) { placeable.place(0, 0) }
        }
        val slotWidth = constraints.maxWidth
        val slotHeight = constraints.maxHeight
        val left = originInWindow.x.roundToInt().coerceAtLeast(0)
        val top = if (extendUp) originInWindow.y.roundToInt().coerceAtLeast(0) else 0
        val right = if (windowWidthPx > 0) (windowWidthPx - left - slotWidth).coerceAtLeast(0) else 0
        val placeable = measurable.measure(
            Constraints.fixed(width = slotWidth + left + right, height = slotHeight + top),
        )
        layout(slotWidth, slotHeight) {
            placeable.place(x = -left, y = -top)
        }
    }

@Composable
internal fun Modifier.bottomHairline(): Modifier {
    val color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)
    return drawWithContent {
        drawContent()
        val thickness = 1.dp.toPx().coerceAtLeast(1f)
        drawRect(
            color = color,
            topLeft = Offset(0f, size.height - thickness),
            size = Size(size.width, thickness),
        )
    }
}

@Composable
internal fun Modifier.appBackgroundHeaderGlass(
    extendToWindow: Boolean = true,
    hairline: Boolean = true,
    extendUp: Boolean = true,
): Modifier {
    var originInWindow by remember { mutableStateOf(Offset.Zero) }
    val windowWidthPx = LocalWindowInfo.current.containerSize.width
    val backdrop = LocalAppBackgroundBackdrop.current
    val colors = MaterialTheme.colorScheme
    return this
        .then(
            if (extendToWindow) {
                Modifier
                    .onGloballyPositioned { originInWindow = it.positionInWindow() }
                    .extendToWindowEdges(originInWindow, windowWidthPx, extendUp)
            } else {
                Modifier
            },
        )
        .liquidGlassSurface(
            backdrop = backdrop,
            shape = RectangleShape,
            tint = colors.surface.copy(alpha = appGlassStyledTint(0.45f)),
            fallback = colors.background.copy(alpha = 0.88f),
            blurRadius = appGlassStyledBlur(24.dp),
            withHighlight = false,
        )
        .then(if (hairline) Modifier.bottomHairline() else Modifier)
}

@Composable
internal fun Modifier.appBackgroundGlass(
    shape: Shape,
    tintAlpha: Float = 0.32f,
    outline: Boolean = true,
    blurRadius: Dp = 20.dp,
): Modifier {
    val colors = MaterialTheme.colorScheme
    return this
        .liquidGlassSurface(
            backdrop = LocalAppBackgroundBackdrop.current,
            shape = shape,
            tint = colors.surface.copy(alpha = appGlassStyledTint(tintAlpha)),
            fallback = colors.surface.copy(alpha = 0.72f),
            blurRadius = appGlassStyledBlur(blurRadius),
            refractionHeight = 8.dp,
            refractionAmount = 12.dp,
            withHighlight = true,
        )
        .then(
            if (outline) {
                Modifier.border(1.dp, colors.onSurface.copy(alpha = 0.12f), shape)
            } else {
                Modifier
            },
        )
}
