package com.nuvio.app.features.streams

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.NuvioBottomSheetActionRow
import com.nuvio.app.core.ui.NuvioBottomSheetDivider
import com.nuvio.app.core.ui.NuvioModalBottomSheet
import com.nuvio.app.core.ui.dismissNuvioBottomSheet
import com.nuvio.app.core.ui.nuvioSafeBottomPadding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.collections_tab_all
import nuvio.composeapp.generated.resources.streams_pin_source
import nuvio.composeapp.generated.resources.streams_pinned_source
import nuvio.composeapp.generated.resources.streams_refresh
import nuvio.composeapp.generated.resources.streams_unpin_source
import org.jetbrains.compose.resources.stringResource
import com.nuvio.app.core.ui.glass.ambientLiquidGlass

@Composable
internal fun ProviderFilterRow(
    groups: List<AddonStreamGroup>,
    selectedFilter: String?,
    onFilterSelected: (String?) -> Unit,
    onRefresh: (() -> Unit)? = null,
    isRefreshing: Boolean = groups.any { it.isLoading },
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    spacing: Dp = 8.dp,
    refreshChip: @Composable (Boolean, () -> Unit) -> Unit = { isLoading, onClick ->
        RefreshChip(isLoading = isLoading, onClick = onClick)
    },
    filterChip: (@Composable (AddonStreamGroup?, Boolean, () -> Unit) -> Unit)? = null,
) {
    val addonGroups = groups.filter { it.streams.isNotEmpty() || it.isLoading }
    val pinnedSourceIds by rememberPinnedStreamSourceIds()
    var pinSheetTarget by remember { mutableStateOf<PinTarget?>(null) }
    val chip: @Composable (AddonStreamGroup?, Boolean, () -> Unit) -> Unit =
        filterChip ?: { group, isSelected, onClick ->
            FilterChip(
                label = group?.addonName ?: stringResource(Res.string.collections_tab_all),
                isSelected = isSelected,
                isPinned = group != null && group.soleSourcePin()?.key in pinnedSourceIds,
                onClick = onClick,
                onLongClick = group?.let { pinnedGroup -> { pinSheetTarget = pinnedGroup.soleSourcePin() } },
            )
        }
    LaunchedEffect(addonGroups, selectedFilter) {
        if (selectedFilter != null && addonGroups.none { it.addonId == selectedFilter }) {
            onFilterSelected(null)
        }
    }
    if (addonGroups.isEmpty() && onRefresh == null) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(spacing),
    ) {
        if (onRefresh != null) {
            refreshChip(isRefreshing, onRefresh)
        }
        chip(null, selectedFilter == null) { onFilterSelected(null) }
        addonGroups.forEach { group ->
            chip(group, selectedFilter == group.addonId) { onFilterSelected(group.addonId) }
        }
    }

    StreamSourcePinSheet(
        target = pinSheetTarget,
        onDismiss = { pinSheetTarget = null },
    )
}

@Composable
internal fun rememberPinnedStreamSourceIds(): State<List<String>> {
    LaunchedEffect(Unit) { PinnedStreamSourcesRepository.ensureLoaded() }
    return PinnedStreamSourcesRepository.pinnedSourceIds.collectAsStateWithLifecycle()
}

internal fun AddonStreamGroup.soleSourcePin(): PinTarget? {
    if (addonId.startsWith("debrid:")) return null
    val playableStreams = streams.filterNot { it.isAddonDebridCandidate && it.isDirectDebridStream }
    val sourceNames = playableStreams
        .map { it.sourceName?.trim()?.takeIf(String::isNotEmpty) }
        .distinct()
    if (sourceNames.size > 1) return null
    val sourceName = sourceNames.firstOrNull()
    return PinTarget(
        key = PinnedStreamSourcesRepository.sourceKeyFor(addonId = addonId, sourceName = sourceName),
        label = sourceName ?: addonName,
    )
}

internal data class PinTarget(val key: String, val label: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StreamSourcePinSheet(
    target: PinTarget?,
    onDismiss: () -> Unit,
) {
    if (target == null) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()
    val pinnedSourceIds by rememberPinnedStreamSourceIds()
    val isPinned = target.key in pinnedSourceIds

    NuvioModalBottomSheet(
        onDismissRequest = {
            coroutineScope.launch {
                dismissNuvioBottomSheet(sheetState = sheetState, onDismiss = onDismiss)
            }
        },
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = nuvioSafeBottomPadding(16.dp)),
        ) {
            Text(
                text = target.label,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            NuvioBottomSheetDivider()
            NuvioBottomSheetActionRow(
                icon = Icons.Rounded.PushPin,
                title = stringResource(
                    if (isPinned) Res.string.streams_unpin_source else Res.string.streams_pin_source,
                ),
                onClick = {
                    PinnedStreamSourcesRepository.setPinned(target.key, !isPinned)
                    coroutineScope.launch {
                        dismissNuvioBottomSheet(sheetState = sheetState, onDismiss = onDismiss)
                    }
                },
            )
        }
    }
}

@Composable
internal fun rememberRefreshRotation(isLoading: Boolean): Animatable<Float, AnimationVector1D> {
    val rotation = remember { Animatable(0f) }
    var hasCompletedFullRotation by remember { mutableStateOf(false) }
    LaunchedEffect(isLoading) {
        if (isLoading) {
            delay(100)
            hasCompletedFullRotation = false
            rotation.animateTo(360f, remainingTurn(rotation.value))
            hasCompletedFullRotation = true
            rotation.snapTo(0f)
            rotation.animateTo(360f, infiniteRepeatable(tween(durationMillis = 1000, easing = LinearEasing)))
        } else {
            if (hasCompletedFullRotation && rotation.value > 0f) {
                rotation.animateTo(360f, remainingTurn(rotation.value))
            }
            rotation.snapTo(0f)
            hasCompletedFullRotation = false
        }
    }
    return rotation
}

private fun remainingTurn(rotation: Float) =
    tween<Float>(durationMillis = ((360f - rotation) / 360f * 1000).toInt(), easing = LinearEasing)

@Composable
private fun RefreshChip(
    isLoading: Boolean,
    onClick: () -> Unit,
) {
    val rotation = rememberRefreshRotation(isLoading)
    FilterChip(
        icon = Icons.Rounded.Refresh,
        contentDescription = stringResource(Res.string.streams_refresh),
        iconModifier = Modifier.graphicsLayer { rotationZ = rotation.value },
        isSelected = false,
        onClick = onClick,
    )
}

@Composable
private fun FilterChip(
    label: String? = null,
    icon: ImageVector? = null,
    contentDescription: String? = null,
    iconModifier: Modifier = Modifier,
    isSelected: Boolean,
    isPinned: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = tween(durationMillis = 140),
        label = "filter_chip_scale",
    )
    val containerColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        },
        animationSpec = tween(durationMillis = 180),
        label = "filter_chip_container",
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        animationSpec = tween(durationMillis = 180),
        label = "filter_chip_content",
    )
    Box(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .height(36.dp)
            .clip(RoundedCornerShape(16.dp))
            .ambientLiquidGlass(
                shape = RoundedCornerShape(16.dp),
                tint = containerColor,
                fallback = containerColor,
                blurRadius = 14.dp,
                refractionHeight = 8.dp,
            )
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = contentColor,
                    modifier = Modifier.size(20.dp).then(iconModifier),
                )
            }
            if (isPinned) {
                Icon(
                    imageVector = Icons.Rounded.PushPin,
                    contentDescription = stringResource(Res.string.streams_pinned_source),
                    tint = contentColor,
                    modifier = Modifier.size(14.dp),
                )
            }
            if (label != null) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 14.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                        letterSpacing = 0.1.sp,
                    ),
                    color = contentColor,
                    maxLines = 1,
                )
            }
        }
    }
}
