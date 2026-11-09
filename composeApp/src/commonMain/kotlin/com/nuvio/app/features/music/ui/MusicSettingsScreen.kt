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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.features.music.lastfm.MusicLastFm
import com.nuvio.app.features.music.navidrome.MusicNavidrome
import com.nuvio.app.features.music.playback.StreamSource
import com.nuvio.app.features.music.playback.TorBoxStreamResolver
import com.nuvio.app.features.music.webdav.MusicWebDav
import kotlinx.coroutines.launch

@Composable
internal fun MusicSettingsScreen(onBack: () -> Unit) {
    val navidrome by MusicNavidrome.config.collectAsStateWithLifecycle()
    val webdav by MusicWebDav.config.collectAsStateWithLifecycle()
    val lastfm by MusicLastFm.config.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize().background(Sp.bg), contentPadding = PaddingValues(bottom = LocalMusicBottomPad.current)) {
        item {
            Row(Modifier.statusBarsPadding().padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Sp.text, modifier = Modifier.size(32.dp).clip(CircleShape).clickable(onClick = onBack).padding(2.dp))
                Text("Music services", color = Sp.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
        }
        item { StreamSourceCard() }
        item { TorBoxCard() }
        item { NavidromeCard(navidrome.configured, navidrome.url, navidrome.username) }
        item { WebDavCard(webdav.configured, webdav.url, webdav.username) }
        item { LastFmCard(lastfm.configured, lastfm.enabled) }
    }
}

@Composable
private fun Card(title: String, subtitle: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clip(RoundedCornerShape(10.dp)).background(Sp.surface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, color = Sp.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = Sp.sub, fontSize = 13.sp)
        content()
    }
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit, secret: Boolean = false, keyboard: KeyboardType = KeyboardType.Text) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = Sp.sub, fontSize = 12.sp)
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(Sp.chip).padding(horizontal = 12.dp, vertical = 12.dp)) {
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(color = Sp.text, fontSize = 15.sp),
                cursorBrush = SolidColor(Sp.green),
                visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(keyboardType = if (secret) KeyboardType.Password else keyboard),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ActionButton(label: String, enabled: Boolean = true, primary: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(if (primary) Sp.green else Sp.chip).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp),
    ) { Text(label, color = if (primary) androidx.compose.ui.graphics.Color.Black else Sp.text, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
}

@Composable
private fun NavidromeCard(connected: Boolean, savedUrl: String, savedUser: String) {
    val scope = rememberCoroutineScope()
    var url by remember(savedUrl) { mutableStateOf(savedUrl) }
    var user by remember(savedUser) { mutableStateOf(savedUser) }
    var password by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    Card("Navidrome", if (connected) "Connected as $savedUser" else "Stream your own library from a Navidrome or Subsonic server. Your password is checked once, then only a token is kept.") {
        Field("Server URL", url, { url = it }, keyboard = KeyboardType.Uri)
        Field("Username", user, { user = it })
        Field("Password", password, { password = it }, secret = true)
        status?.let { Text(it, color = Sp.sub, fontSize = 13.sp) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionButton(if (busy) "Connecting…" else if (connected) "Reconnect" else "Connect", enabled = !busy) {
                busy = true; status = null
                scope.launch {
                    MusicNavidrome.connect(url, user, password).fold(
                        onSuccess = { status = "Connected."; password = "" },
                        onFailure = { status = it.message ?: "Couldn't connect." },
                    )
                    busy = false
                }
            }
            if (connected) ActionButton("Disconnect", primary = false) { MusicNavidrome.disconnect(); status = "Disconnected." }
        }
    }
}

@Composable
private fun WebDavCard(connected: Boolean, savedUrl: String, savedUser: String) {
    val scope = rememberCoroutineScope()
    var url by remember(savedUrl) { mutableStateOf(savedUrl) }
    var user by remember(savedUser) { mutableStateOf(savedUser) }
    var password by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    Card("WebDAV", if (connected) "Connected to $savedUrl" else "Browse and play music from a WebDAV folder (Nextcloud, a NAS, and so on).") {
        Field("Folder URL", url, { url = it }, keyboard = KeyboardType.Uri)
        Field("Username (optional)", user, { user = it })
        Field("Password", password, { password = it }, secret = true)
        status?.let { Text(it, color = Sp.sub, fontSize = 13.sp) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionButton(if (busy) "Connecting…" else if (connected) "Reconnect" else "Connect", enabled = !busy) {
                busy = true; status = null
                scope.launch {
                    MusicWebDav.connect(url, user, password).fold(
                        onSuccess = { status = "Connected."; password = "" },
                        onFailure = { status = it.message ?: "Couldn't connect." },
                    )
                    busy = false
                }
            }
            if (connected) ActionButton("Remove", primary = false) { MusicWebDav.clear(); status = "Removed." }
        }
    }
}

@Composable
private fun StreamSourceCard() {
    var selected by remember { mutableStateOf(StreamSource.current()) }
    Card("Stream source", "Choose where songs play from. \"Both\" tries YouTube first and falls back to TorBox if blocked.") {
        StreamSource.entries.forEach { mode ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                    .background(if (mode == selected) Sp.green.copy(alpha = 0.15f) else Sp.chip)
                    .clickable { selected = mode; StreamSource.set(mode) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(20.dp).clip(CircleShape)
                        .background(if (mode == selected) Sp.green else Sp.sub.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (mode == selected) Box(Modifier.size(8.dp).clip(CircleShape).background(androidx.compose.ui.graphics.Color.Black))
                }
                Text(mode.label, color = Sp.text, fontSize = 15.sp, modifier = Modifier.padding(start = 12.dp))
            }
        }
    }
}

@Composable
private fun TorBoxCard() {
    var source by remember { mutableStateOf(TorBoxStreamResolver.source()) }
    var status by remember { mutableStateOf<String?>(null) }
    val connected = source != TorBoxStreamResolver.Source.None
    Card(
        "TorBox",
        if (connected) "Connected through ${source.label}. Songs play from your TorBox account; there is nothing to sign in to here."
        else "Music uses the TorBox you already connected in Nuvio. Add it under Settings \u203A Debrid, or install the TorBox addon, and it works here too.",
    ) {
        status?.let { Text(it, color = Sp.sub, fontSize = 13.sp) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionButton("Check again", primary = !connected) {
                TorBoxStreamResolver.clearCache()
                source = TorBoxStreamResolver.source()
                status = if (source == TorBoxStreamResolver.Source.None) "No TorBox connection found yet." else "Connected."
            }
            if (source == TorBoxStreamResolver.Source.Saved) ActionButton("Forget saved key", primary = false) {
                TorBoxStreamResolver.setApiKey("")
                TorBoxStreamResolver.clearCache()
                source = TorBoxStreamResolver.source()
                status = "Removed."
            }
        }
    }
}

@Composable
private fun LastFmCard(configured: Boolean, enabled: Boolean) {
    var key by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    var session by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    Card("Last.fm", if (configured) "Scrobbling ${if (enabled) "is on" else "is off"}." else "Scrobble what you play. Paste your API key, shared secret and session key.") {
        if (!configured) {
            Field("API key", key, { key = it })
            Field("Shared secret", secret, { secret = it }, secret = true)
            Field("Session key", session, { session = it }, secret = true)
        }
        status?.let { Text(it, color = Sp.sub, fontSize = 13.sp) }
        if (configured) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Scrobble plays", color = Sp.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                Switch(checked = enabled, onCheckedChange = { MusicLastFm.setEnabled(it) }, colors = SwitchDefaults.colors(checkedTrackColor = Sp.green, checkedThumbColor = androidx.compose.ui.graphics.Color.Black))
            }
            ActionButton("Disconnect", primary = false) { MusicLastFm.clear(); status = "Disconnected." }
        } else {
            ActionButton("Save", enabled = key.isNotBlank() && secret.isNotBlank() && session.isNotBlank()) {
                MusicLastFm.save(key, secret, session); MusicLastFm.setEnabled(true); status = "Saved."
            }
        }
    }
}
