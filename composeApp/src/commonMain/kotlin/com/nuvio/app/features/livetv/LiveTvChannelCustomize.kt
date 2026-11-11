package com.nuvio.app.features.livetv

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nuvio.app.core.ui.PlatformBackHandler
import com.nuvio.app.core.ui.nuvio

private class PlaylistCategory(val key: String, val name: String, val count: Int)

/**
 * "Customize Channels": turn the playlist's categories on or off. A category that is off disappears from the
 * channel list, search and the guide. Nothing is saved until Done.
 */
@Composable
internal fun LiveTvCategoryManagerDialog(
    channels: List<LiveTvChannel>,
    uncategorizedLabel: String,
    onDismiss: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    var hidden by remember { mutableStateOf(LiveTvChannelPrefs.hiddenGroups.value) }
    var query by rememberSaveable { mutableStateOf("") }
    val categories = remember(channels, uncategorizedLabel) {
        channels.groupBy { LiveTvChannelPrefs.groupKey(it.group) }
            .map { (key, list) ->
                PlaylistCategory(key, list.first().group?.trim()?.takeIf(String::isNotBlank) ?: uncategorizedLabel, list.size)
            }
            .sortedBy { it.name.lowercase() }
    }
    val q = query.trim().lowercase()
    val shown = remember(categories, q) { if (q.isEmpty()) categories else categories.filter { it.name.lowercase().contains(q) } }
    val visibleCount = categories.count { it.key !in hidden }
    val visibleChannels = categories.sumOf { if (it.key in hidden) 0 else it.count }

    PlatformBackHandler(enabled = true) { onDismiss() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(tokens.colors.background).statusBarsPadding()) {
            CustomizeHeader("Customize Channels", onDismiss)
            Text(
                "Turn off the categories you don't watch. They are left out of All Channels, search and the guide, " +
                    "and Live TV loads less.",
                style = MaterialTheme.typography.bodyMedium, color = tokens.colors.textMuted,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            )
            CustomizeSearch(query, { query = it }, "Search categories", Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                val keys = shown.map { it.key }.toSet()
                CustomizeLink(if (q.isEmpty()) "Show all" else "Show these") { hidden = hidden - keys }
                CustomizeLink(if (q.isEmpty()) "Hide all" else "Hide these") { hidden = hidden + keys }
            }
            LazyColumn(Modifier.weight(1f)) {
                if (shown.isEmpty()) item(key = "empty") {
                    Text("No categories match.", color = tokens.colors.textMuted, modifier = Modifier.padding(16.dp))
                }
                items(shown, key = { "category:" + it.key }) { category ->
                    val on = category.key !in hidden
                    CustomizeRow(
                        title = category.name,
                        subtitle = "${category.count} channel${if (category.count == 1) "" else "s"}",
                        checked = on,
                    ) { hidden = if (on) hidden + category.key else hidden - category.key }
                }
                item(key = "bottom-space") { Spacer(Modifier.height(24.dp)) }
            }
            CustomizeFooter(
                summary = "$visibleCount of ${categories.size} categories · $visibleChannels channels",
                enabled = visibleCount > 0,
            ) {
                // Only keys that still exist are kept, so a category the provider removed doesn't linger.
                LiveTvChannelPrefs.setHiddenGroups(hidden.intersect(categories.map { it.key }.toSet()))
                onDismiss()
            }
        }
    }
}

/** "Guide Categories": choose which groups of channels make up the guide. */
@Composable
internal fun LiveTvGuideCategoriesDialog(
    matches: Map<String, List<LiveTvChannel>>,
    onDismiss: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    var selected by remember { mutableStateOf(LiveTvChannelPrefs.guideCategories.value) }

    PlatformBackHandler(enabled = true) { onDismiss() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(tokens.colors.background).statusBarsPadding()) {
            CustomizeHeader("Guide Categories", onDismiss)
            Text(
                "Choose what your guide shows. Each category lists the main channels of its kind that your " +
                    "playlist carries. Use + in the guide to add any other channel.",
                style = MaterialTheme.typography.bodyMedium, color = tokens.colors.textMuted,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.weight(1f)) {
                items(LIVE_TV_GUIDE_CATEGORIES, key = { it.id }) { category ->
                    val found = matches[category.id]?.size ?: 0
                    val total = liveTvGuideCatalogSizes[category.id] ?: 0
                    val on = category.id in selected
                    CustomizeRow(
                        title = category.title,
                        subtitle = if (found == 0) "None of its $total channels are in your playlist" else "$found of $total channels in your playlist",
                        checked = on,
                    ) { selected = if (on) selected - category.id else selected + category.id }
                }
                item(key = "bottom-space") { Spacer(Modifier.height(24.dp)) }
            }
            CustomizeFooter(
                summary = "${selected.size} of ${LIVE_TV_GUIDE_CATEGORIES.size} categories",
                enabled = true,
            ) {
                LiveTvChannelPrefs.setGuideCategories(selected)
                onDismiss()
            }
        }
    }
}

@Composable
private fun CustomizeHeader(title: String, onBack: () -> Unit) {
    val tokens = MaterialTheme.nuvio
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = tokens.colors.textPrimary,
            modifier = Modifier.size(26.dp).clickable(onClick = onBack),
        )
        Text(
            title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
            color = tokens.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun CustomizeLink(label: String, onClick: () -> Unit) {
    Text(
        label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.nuvio.colors.accent,
        modifier = Modifier.clickable(onClick = onClick).padding(vertical = 8.dp),
    )
}

@Composable
private fun CustomizeRow(title: String, subtitle: String, checked: Boolean, onToggle: () -> Unit) {
    val tokens = MaterialTheme.nuvio
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                color = if (checked) tokens.colors.textPrimary else tokens.colors.textMuted,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = tokens.colors.textMuted)
        }
        Box(
            Modifier.size(24.dp).clip(CircleShape).background(if (checked) tokens.colors.accent else tokens.colors.surfaceElevated),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) Icon(Icons.Rounded.Check, contentDescription = null, tint = tokens.colors.background, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun CustomizeFooter(summary: String, enabled: Boolean, onDone: () -> Unit) {
    val tokens = MaterialTheme.nuvio
    Row(
        Modifier.fillMaxWidth().background(tokens.colors.surface).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (enabled) summary else "Keep at least one category on",
            style = MaterialTheme.typography.bodyMedium, color = tokens.colors.textMuted, modifier = Modifier.weight(1f),
        )
        Box(
            Modifier.clip(RoundedCornerShape(24.dp))
                .background(if (enabled) tokens.colors.textPrimary else tokens.colors.surfaceElevated)
                .clickable(enabled = enabled, onClick = onDone)
                .padding(horizontal = 20.dp, vertical = 10.dp),
        ) {
            Text("Done", color = if (enabled) tokens.colors.background else tokens.colors.textMuted, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CustomizeSearch(query: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    val tokens = MaterialTheme.nuvio
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(tokens.colors.surface).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Rounded.Search, contentDescription = null, tint = tokens.colors.textMuted, modifier = Modifier.size(18.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = tokens.colors.textMuted)
            BasicTextField(
                value = query, onValueChange = onChange, singleLine = true,
                textStyle = TextStyle(color = tokens.colors.textPrimary, fontSize = MaterialTheme.typography.bodyMedium.fontSize),
                cursorBrush = SolidColor(tokens.colors.accent), modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
