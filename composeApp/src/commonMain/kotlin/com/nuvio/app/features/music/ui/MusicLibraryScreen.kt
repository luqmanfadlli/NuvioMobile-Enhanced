package com.nuvio.app.features.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.features.music.MusicLibrary
import com.nuvio.app.features.music.navidrome.MusicNavidrome
import com.nuvio.app.features.music.webdav.MusicWebDav

private enum class LibraryChip(val label: String) { All("All"), Playlists("Playlists"), Servers("Servers") }

@Composable
internal fun MusicLibraryScreen(onOpen: (MusicDest) -> Unit) {
    val liked by MusicLibrary.liked.collectAsStateWithLifecycle()
    val recent by MusicLibrary.recent.collectAsStateWithLifecycle()
    val playlists by MusicLibrary.playlists.collectAsStateWithLifecycle()
    val navidrome by MusicNavidrome.config.collectAsStateWithLifecycle()
    val webdav by MusicWebDav.config.collectAsStateWithLifecycle()
    var chip by remember { mutableStateOf(LibraryChip.All) }
    var creating by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Sp.bg),
        contentPadding = PaddingValues(bottom = LocalMusicBottomPad.current),
    ) {
        item {
            Column(Modifier.statusBarsPadding()) {
                Row(Modifier.padding(start = 16.dp, end = 72.dp, top = 16.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Your Library", color = Sp.text, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Icon(Icons.Rounded.Settings, "Music services", tint = Sp.text, modifier = Modifier.size(26.dp).clip(CircleShape).clickable { onOpen(MusicDest.Settings) })
                    Icon(Icons.Rounded.Add, "New playlist", tint = Sp.text, modifier = Modifier.padding(start = 14.dp).size(28.dp).clip(CircleShape).clickable { creating = true })
                }
                Row(Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LibraryChip.entries.forEach { entry -> Pill(entry.label, chip == entry) { chip = entry } }
                }
            }
        }
        if (chip != LibraryChip.Servers) {
            item {
                LibraryRow(
                    title = "Liked Songs",
                    subtitle = "Playlist • ${liked.size} songs",
                    leading = {
                        Box(
                            Modifier.size(60.dp).clip(RoundedCornerShape(4.dp)).background(Brush.linearGradient(listOf(Color(0xFF450AF5), Color(0xFFC4EFD9)))),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Rounded.Favorite, null, tint = Color.White, modifier = Modifier.size(26.dp)) }
                    },
                    onClick = { onOpen(MusicDest.Liked) },
                )
            }
            if (chip == LibraryChip.All && recent.isNotEmpty()) {
                item {
                    LibraryRow(
                        title = "Recently played",
                        subtitle = "Playlist • ${recent.size} songs",
                        leading = { Cover(recent.first().thumbnailUrl, Modifier.size(60.dp)) },
                        onClick = { onOpen(MusicDest.Recent) },
                    )
                }
            }
            items(playlists.size) { i ->
                val p = playlists[i]
                LibraryRow(
                    title = p.name,
                    subtitle = "Playlist • ${p.songs.size} songs",
                    leading = { Cover(p.songs.firstOrNull()?.thumbnailUrl, Modifier.size(60.dp)) },
                    onClick = { onOpen(MusicDest.Local(p.id)) },
                )
            }
        }
        if (chip != LibraryChip.Playlists) {
            if (navidrome.configured) {
                item {
                    LibraryRow(
                        title = "Navidrome",
                        subtitle = "Your server • albums and playlists",
                        leading = { ServerBadge() },
                        onClick = { onOpen(MusicDest.NavidromeHome) },
                    )
                }
            }
            if (webdav.configured) {
                item {
                    LibraryRow(
                        title = "WebDAV",
                        subtitle = "Browse folders",
                        leading = { ServerBadge() },
                        onClick = { onOpen(MusicDest.WebDav(webdav.url, "WebDAV")) },
                    )
                }
            }
            if (!navidrome.configured && !webdav.configured) {
                item {
                    LibraryRow(
                        title = "Add a music server",
                        subtitle = "Navidrome, WebDAV or Last.fm",
                        leading = { ServerBadge() },
                        onClick = { onOpen(MusicDest.Settings) },
                    )
                }
            }
        }
    }

    if (creating) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { creating = false },
            containerColor = Sp.elevated,
            title = { Text("Give your playlist a name", color = Sp.text, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
            text = {
                BasicTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Sp.text, fontSize = 20.sp, fontWeight = FontWeight.Bold),
                    cursorBrush = SolidColor(Sp.green),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner -> Box { if (name.isEmpty()) Text("My playlist #${playlists.size + 1}", color = Sp.dim, fontSize = 20.sp, fontWeight = FontWeight.Bold); inner() } },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val created = MusicLibrary.createPlaylist(name)
                    creating = false
                    onOpen(MusicDest.Local(created.id))
                }) { Text("Create", color = Sp.green, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { creating = false }) { Text("Cancel", color = Sp.sub) } },
        )
    }
}

@Composable
private fun ServerBadge() {
    Box(Modifier.size(60.dp).clip(RoundedCornerShape(4.dp)).background(Sp.elevated), contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.Dns, null, tint = Sp.sub, modifier = Modifier.size(26.dp))
    }
}

@Composable
internal fun LibraryRow(title: String, subtitle: String, leading: @Composable () -> Unit, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        leading()
        Column(Modifier.weight(1f)) {
            Text(title, color = Sp.text, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            Text(subtitle, color = Sp.sub, fontSize = 13.sp, maxLines = 1)
        }
    }
}
