package com.nuvio.app.core.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import org.jetbrains.compose.resources.painterResource

private val RailCollapsedWidth = 80.dp
private val RailExpandedWidth = 248.dp

/**
 * The TV navigation rail: a column of icons down the left edge that widens to show labels while the
 * remote has focus in it, drawing over the content so the screen doesn't shift. Takes the same items
 * as the floating bar.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
internal fun TvSideRail(items: List<FloatingNavigationItem>, modifier: Modifier = Modifier) {
    val tokens = MaterialTheme.nuvio
    var expanded by remember { mutableStateOf(false) }
    // Coming back into the rail lands on the tab you are on, not always the first one.
    val requesters = remember(items.size) { List(items.size) { FocusRequester() } }
    val selectedIndex = items.indexOfFirst { it.selected }
    val initialFocus = LocalTvInitialFocus.current
    val width by animateDpAsState(if (expanded) RailExpandedWidth else RailCollapsedWidth)
    val labelAlpha by animateFloatAsState(if (expanded) 1f else 0f)
    val background = tokens.colors.background
    val panel = if (expanded) {
        Modifier.background(
            Brush.horizontalGradient(
                0f to background,
                0.82f to background.copy(alpha = 0.96f),
                1f to Color.Transparent,
            ),
        )
    } else {
        Modifier.background(background)
    }
    Box(modifier.width(RailCollapsedWidth).fillMaxHeight().zIndex(2f)) {
        Column(
            modifier = Modifier
                // Wider than its slot: grow to the right over the content, never centred outwards.
                .wrapContentWidth(Alignment.Start, unbounded = true)
                .width(width)
                .fillMaxHeight()
                .then(panel)
                .onFocusChanged { expanded = it.hasFocus }
                .focusProperties { enter = { if (selectedIndex >= 0) requesters[selectedIndex] else FocusRequester.Default } }
                .focusGroup()
                .padding(start = 10.dp, end = if (expanded) 36.dp else 10.dp, top = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        ) {
            items.forEachIndexed { index, item -> TvRailItem(
                item,
                labelAlpha,
                Modifier.focusRequester(requesters[index]).then(if (item.selected) Modifier.focusRequester(initialFocus) else Modifier),
            ) }
        }
    }
}

@Composable
private fun TvRailItem(item: FloatingNavigationItem, labelAlpha: Float, modifier: Modifier = Modifier) {
    val tokens = MaterialTheme.nuvio
    val tint = if (item.selected) Color.White else tokens.colors.textMuted
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(shape)
            .then(if (item.selected) Modifier.background(Color.White.copy(alpha = 0.14f)) else Modifier)
            // The profile item brings its own clickable avatar, so it must not be made a second stop.
            .then(
                if (item.content == null) {
                    Modifier.selectable(selected = item.selected, role = Role.Tab, onClick = item.onClick)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
            when {
                item.icon != null -> Icon(item.icon, contentDescription = item.label, modifier = Modifier.size(28.dp), tint = tint)
                item.drawable != null -> Icon(painterResource(item.drawable), contentDescription = item.label, modifier = Modifier.size(28.dp), tint = tint)
                item.content != null -> item.content.invoke({ item.onClick() }, null)
            }
        }
        Text(
            text = item.label,
            modifier = Modifier.alpha(labelAlpha),
            color = tint,
            fontWeight = if (item.selected) FontWeight.Bold else FontWeight.Medium,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
        )
    }
}
