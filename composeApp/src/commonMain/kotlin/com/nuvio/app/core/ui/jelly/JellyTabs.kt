package com.nuvio.app.core.ui.jelly

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.core.ui.FloatingNavigationItem
import com.nuvio.app.core.ui.accentBrush
import com.nuvio.app.core.ui.gradientMask
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.core.ui.themePalette
import com.nuvio.app.core.ui.visualNavIndex
import org.jetbrains.compose.resources.painterResource
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
internal fun JellyTabRow(
    items: List<FloatingNavigationItem>,
    labelFraction: Float,
    motion: JellyMotion,
    active: Boolean,
    compactSize: Boolean,
    modifier: Modifier,
    inlineLabels: Boolean = false,
    inactiveColorOverride: Color? = null,
) {
    val tokens = MaterialTheme.nuvio
    val palette = MaterialTheme.themePalette
    // The override applies to both inactive icon glyphs and their labels; selected styling stays themed.
    val color = if (active) tokens.colors.accent else inactiveColorOverride ?: tokens.colors.textMuted
    val iconSize = if (compactSize) 24.dp else 28.dp
    val labelHeight = if (compactSize) 14.dp else 16.dp
    val iconModifier = Modifier.size(iconSize)
        .then(if (active) Modifier.gradientMask(palette.accentBrush()) else Modifier)
    val iconTint = if (active) Color.White else color
    Row(
        modifier = modifier.padding(4.dp).clearAndSetSemantics {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { item ->
            Box(
                Modifier.weight(1f).fillMaxHeight().graphicsLayer {
                    val scale = if (active) motion.frame.contentScale else 1f
                    scaleX = scale
                    scaleY = scale
                },
                contentAlignment = Alignment.Center,
            ) {
                if (inlineLabels) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(iconSize)) {
                            when {
                                item.icon != null -> Icon(item.icon, null, iconModifier, tint = iconTint)
                                item.drawable != null -> Icon(painterResource(item.drawable), null, iconModifier, tint = iconTint)
                            }
                        }
                        InlineJellyLabel(item.label, labelFraction, color, active)
                    }
                    return@Box
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(iconSize).graphicsLayer { translationY = 2.dp.toPx() * labelFraction }) {
                        when {
                            item.icon != null -> Icon(item.icon, null, iconModifier, tint = iconTint)
                            item.drawable != null -> Icon(painterResource(item.drawable), null, iconModifier, tint = iconTint)
                        }
                    }
                    Box(Modifier.height(labelHeight * labelFraction).fillMaxWidth().clipToBounds().alpha(labelFraction)) {
                        Text(
                            text = item.label,
                            color = color,
                            style = TextStyle(
                                fontSize = if (compactSize) 12.sp else 13.sp,
                                lineHeight = if (compactSize) 14.sp else 16.sp,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                textAlign = TextAlign.Center,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun JellyTabTargets(
    items: List<FloatingNavigationItem>,
    labelFraction: Float,
    motion: JellyMotion,
    compactSize: Boolean,
    modifier: Modifier,
    inlineLabels: Boolean = false,
    defaultIconColorOverride: Color? = null,
) {
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Row(modifier.padding(horizontal = 4.dp).selectableGroup()) {
        items.forEachIndexed { index, item ->
            val visualIndex = visualNavIndex(index, items.size, isRtl)
            val onClick = {
                JellySelectionSource.lastDragCommit = null
                motion.select(visualIndex)
                item.onClick()
            }
            Box(
                modifier = Modifier.weight(1f).fillMaxHeight()
                    .selectable(
                        selected = item.selected,
                        role = Role.Tab,
                        interactionSource = null,
                        indication = null,
                        onClick = onClick,
                    )
                    .clearAndSetSemantics {
                        role = Role.Tab
                        selected = item.selected
                        contentDescription = item.label
                        onClick { onClick(); true }
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (item.content != null && inlineLabels) {
                    Row(
                        modifier = Modifier.graphicsLayer {
                            val frame = motion.frame
                            val coverage = (1f - abs(frame.position - visualIndex)).coerceIn(0f, 1f)
                            val scale = 1f + (frame.contentScale - 1f) * coverage
                            scaleX = scale
                            scaleY = scale
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(if (compactSize) Modifier.size(24.dp) else Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                            item.content(onClick, defaultIconColorOverride)
                        }
                        Box(Modifier.alpha(0f)) {
                            InlineJellyLabel(item.label, labelFraction, Color.Transparent, active = false)
                        }
                    }
                } else if (item.content != null) {
                    Column(
                        modifier = Modifier.graphicsLayer {
                            val frame = motion.frame
                            val coverage = (1f - abs(frame.position - visualIndex)).coerceIn(0f, 1f)
                            val scale = 1f + (frame.contentScale - 1f) * coverage
                            scaleX = scale
                            scaleY = scale
                        },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier
                                .then(if (compactSize) Modifier.size(24.dp) else Modifier)
                                .graphicsLayer { translationY = 2.dp.toPx() * labelFraction },
                        ) {
                            item.content(onClick, defaultIconColorOverride)
                        }
                        Spacer(Modifier.height((if (compactSize) 14.dp else 16.dp) * labelFraction))
                    }
                }
            }
        }
    }
}

@Composable
private fun InlineJellyLabel(label: String, labelFraction: Float, color: Color, active: Boolean) {
    Text(
        text = label,
        color = color,
        style = TextStyle(
            fontSize = 14.sp,
            lineHeight = 16.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
        ),
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clipToBounds()
            .alpha(labelFraction)
            .layout { measurable, constraints ->
                val gap = (6.dp.toPx() * labelFraction).roundToInt()
                val maxText = (constraints.maxWidth - gap).coerceAtLeast(0)
                val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = maxText))
                val width = gap + (placeable.width * labelFraction).roundToInt()
                layout(width, placeable.height) { placeable.place(gap, 0) }
            },
    )
}
