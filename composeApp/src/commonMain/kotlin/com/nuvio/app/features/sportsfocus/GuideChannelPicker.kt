package com.nuvio.app.features.sportsfocus

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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.PlatformBackHandler
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.livetv.LiveTvChannel

private const val NO_GROUP = "Other"
private const val SEARCH_RESULT_LIMIT = 250

/**
 * "Build Your Guide": pick the channels that make up My Guide. Channels are listed by their group (a
 * playlist can hold tens of thousands, so nothing is shown flat), and a search looks across all of them.
 * Nothing is saved until the user taps the button at the bottom.
 */
@Composable
internal fun GuideChannelPicker(
    channels: List<LiveTvChannel>,
    hasGuide: (LiveTvChannel) -> Boolean,
    title: String = "Build Your Guide",
    subtitle: String = "Choose what you actually watch. You can change this anytime.",
    confirmLabel: String = "View My Guide",
    onDismiss: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    var chosen by remember { mutableStateOf(GuideChannelStore.selected.value) }
    var openGroup by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }

    val groups = remember(channels) {
        channels.groupBy { it.group?.takeIf(String::isNotBlank) ?: NO_GROUP }
            .toList().sortedBy { it.first.lowercase() }
    }
    val q = query.trim().lowercase()
    val results = remember(q, channels) {
        if (q.isEmpty()) emptyList() else channels.asSequence().filter { it.name.lowercase().contains(q) }.take(SEARCH_RESULT_LIMIT).toList()
    }

    PlatformBackHandler(enabled = true) { if (openGroup != null) openGroup = null else onDismiss() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(tokens.colors.background).statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = tokens.colors.textPrimary,
                    modifier = Modifier.size(26.dp).clickable { if (openGroup != null) openGroup = null else onDismiss() },
                )
                Text(
                    openGroup ?: title,
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                    color = tokens.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
            }
            if (openGroup == null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium, color = tokens.colors.textMuted,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                )
            }
            PickerSearch(query, { query = it }, Modifier.padding(horizontal = 16.dp, vertical = 10.dp))

            val shownGroup = openGroup?.let { name -> groups.firstOrNull { it.first == name }?.second.orEmpty() }
            Box(Modifier.weight(1f)) {
                when {
                    q.isNotEmpty() -> ChannelList(results, chosen, hasGuide, header = null) { id -> chosen = chosen.toggled(id) }
                    shownGroup != null -> ChannelList(
                        shownGroup, chosen, hasGuide,
                        header = {
                            val ids = shownGroup.map { it.id }
                            val all = ids.all { it in chosen }
                            Text(
                                if (all) "Remove all ${ids.size}" else "Add all ${ids.size}",
                                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = tokens.colors.accent,
                                modifier = Modifier.clickable { chosen = if (all) chosen - ids.toSet() else chosen + ids }.padding(horizontal = 16.dp, vertical = 10.dp),
                            )
                        },
                    ) { id -> chosen = chosen.toggled(id) }
                    else -> LazyColumn(Modifier.fillMaxSize()) {
                        items(groups, key = { it.first }) { (name, list) ->
                            val inGuide = list.count { it.id in chosen }
                            Row(
                                Modifier.fillMaxWidth().clickable { openGroup = name }.padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = tokens.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        if (inGuide > 0) "${list.size} channels · $inGuide in your guide" else "${list.size} channels",
                                        style = MaterialTheme.typography.bodySmall, color = if (inGuide > 0) tokens.colors.accent else tokens.colors.textMuted,
                                    )
                                }
                                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = tokens.colors.textMuted)
                            }
                        }
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().background(tokens.colors.surface).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${chosen.size} channel${if (chosen.size == 1) "" else "s"} selected",
                    style = MaterialTheme.typography.bodyMedium, color = tokens.colors.textMuted, modifier = Modifier.weight(1f),
                )
                Box(
                    Modifier.clip(RoundedCornerShape(24.dp)).background(tokens.colors.textPrimary)
                        .clickable { GuideChannelStore.setSelected(chosen); onDismiss() }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                ) {
                    Text(confirmLabel, color = tokens.colors.background, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun Set<String>.toggled(id: String): Set<String> = if (id in this) this - id else this + id

@Composable
private fun ChannelList(
    list: List<LiveTvChannel>,
    chosen: Set<String>,
    hasGuide: (LiveTvChannel) -> Boolean,
    header: (@Composable () -> Unit)?,
    onToggle: (String) -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    LazyColumn(Modifier.fillMaxSize()) {
        if (header != null) item(key = "header") { header() }
        if (list.isEmpty()) item(key = "empty") {
            Text("No channels match.", color = tokens.colors.textMuted, modifier = Modifier.padding(16.dp))
        }
        items(list, key = { it.id }) { ch ->
            val on = ch.id in chosen
            Row(
                Modifier.fillMaxWidth().clickable { onToggle(ch.id) }.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.size(width = 52.dp, height = 36.dp), contentAlignment = Alignment.Center) {
                    if (ch.logoUrl != null) AsyncImage(model = ch.logoUrl, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                }
                Column(Modifier.weight(1f)) {
                    Text(ch.name, style = MaterialTheme.typography.bodyLarge, color = tokens.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (hasGuide(ch)) Text("Has TV guide", style = MaterialTheme.typography.labelSmall, color = tokens.colors.textMuted)
                }
                Box(
                    Modifier.size(24.dp).clip(CircleShape).background(if (on) tokens.colors.accent else tokens.colors.surfaceElevated),
                    contentAlignment = Alignment.Center,
                ) {
                    if (on) Icon(Icons.Rounded.Check, contentDescription = null, tint = tokens.colors.background, modifier = Modifier.size(16.dp))
                }
            }
        }
        item(key = "bottom-space") { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun PickerSearch(query: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val tokens = MaterialTheme.nuvio
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(tokens.colors.surface).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Rounded.Search, contentDescription = null, tint = tokens.colors.textMuted, modifier = Modifier.size(18.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) Text("Search all channels", style = MaterialTheme.typography.bodyMedium, color = tokens.colors.textMuted)
            BasicTextField(
                value = query, onValueChange = onChange, singleLine = true,
                textStyle = TextStyle(color = tokens.colors.textPrimary, fontSize = MaterialTheme.typography.bodyMedium.fontSize),
                cursorBrush = SolidColor(tokens.colors.accent), modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
