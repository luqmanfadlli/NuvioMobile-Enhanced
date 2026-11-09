package com.nuvio.app.features.music

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.NuvioScreenHeader
import com.nuvio.app.core.ui.nuvio
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.compose_nav_music
import org.jetbrains.compose.resources.stringResource

/** The Music tab. The library, search, playlists and lyrics are added on top of this shell. */
@Composable
fun MusicScreen(modifier: Modifier = Modifier) {
    val tokens = MaterialTheme.nuvio
    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
        NuvioScreenHeader(
            title = stringResource(Res.string.compose_nav_music),
            includeStatusBarPadding = false,
        )
        Text(
            text = "Music",
            style = MaterialTheme.typography.bodyMedium,
            color = tokens.colors.textMuted,
        )
    }
}
