package com.nuvio.app.features.library

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import com.nuvio.app.core.ui.NativeNavigationTab
import com.nuvio.app.core.ui.canOpenNativeTab
import com.nuvio.app.core.ui.openNativeModeSwitcher
import com.nuvio.app.core.ui.openNativeTab
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.NuvioPosterCard
import com.nuvio.app.core.ui.NuvioPosterShape
import com.nuvio.app.core.ui.NuvioShelfSection
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.details.MetaDetailsRepository
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.components.HomePosterCard
import com.nuvio.app.features.profiles.ActiveProfileMiniAvatar
import com.nuvio.app.features.profiles.AvatarRepository
import com.nuvio.app.features.profiles.ProfileRepository
import com.nuvio.app.features.watched.WatchedRepository

/** The top of the Library, the way "My Netflix" opens: who is watching, centred, before anything else. */
@Composable
internal fun LibraryProfileHeader(modifier: Modifier = Modifier) {
    val tokens = MaterialTheme.nuvio
    val profileState by ProfileRepository.state.collectAsStateWithLifecycle()
    val avatars by AvatarRepository.avatars.collectAsStateWithLifecycle()
    val profile = profileState.activeProfile
    Column(
        modifier = modifier.fillMaxWidth().padding(top = 0.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // On iOS this avatar stands in for the corner profile button: a tap opens Settings, holding it picks a mode.
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .combinedClickable(
                    enabled = canOpenNativeTab,
                    onLongClick = { openNativeModeSwitcher() },
                    onClick = { openNativeTab(NativeNavigationTab.Settings) },
                ),
        ) {
            ActiveProfileMiniAvatar(profile = profile, avatars = avatars, selected = false, size = 64)
        }
        Text(
            text = profile?.name?.takeIf { it.isNotBlank() } ?: "My Library",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = tokens.colors.textPrimary,
        )
    }
}

/** "Watch Again" and "Trailers You've Watched", the history rows under your list. */
internal fun LazyListScope.libraryHistoryRows(
    onPreviewClick: ((MetaPreview) -> Unit)?,
    onPreviewLongClick: ((MetaPreview) -> Unit)?,
) {
    item(key = "library-watch-again") {
        val watched by remember { WatchedRepository.ensureLoaded(); WatchedRepository.uiState }.collectAsStateWithLifecycle()
        val posters = remember { mutableStateMapOf<String, String>() }
        val base = remember(watched.items) {
            watched.items.sortedByDescending { it.markedAtEpochMs }.distinctBy { it.id }.take(20)
        }
        // A watched episode often has no poster saved with it; borrow the title's own.
        LaunchedEffect(base) {
            base.filter { it.poster.isNullOrBlank() && it.id !in posters }.forEach { item ->
                val poster = MetaDetailsRepository.peek(item.type, item.id)?.poster
                    ?: runCatching { MetaDetailsRepository.fetch(item.type, item.id) }.getOrNull()?.poster
                if (!poster.isNullOrBlank()) posters[item.id] = poster
            }
        }
        val recent = base.map { MetaPreview(id = it.id, type = it.type, name = it.name, poster = it.poster ?: posters[it.id], releaseInfo = it.releaseInfo) }
        if (recent.isNotEmpty()) {
            NuvioShelfSection(
                title = "Watch Again",
                entries = recent,
                modifier = Modifier.padding(top = 8.dp),
                headerHorizontalPadding = 16.dp,
                rowContentPadding = PaddingValues(horizontal = 16.dp),
                key = { "again:${it.type}:${it.id}" },
            ) { item ->
                HomePosterCard(
                    item = item,
                    isWatched = false,
                    onClick = onPreviewClick?.let { { it(item) } },
                    onLongClick = onPreviewLongClick?.let { { it(item) } },
                )
            }
        }
    }
    item(key = "library-trailers") {
        val trailers by TrailerHistory.items.collectAsStateWithLifecycle()
        if (trailers.isNotEmpty()) {
            NuvioShelfSection(
                title = "Trailers You've Watched",
                entries = trailers,
                modifier = Modifier.padding(top = 8.dp),
                headerHorizontalPadding = 16.dp,
                rowContentPadding = PaddingValues(horizontal = 16.dp),
                key = { "trailer:${it.type}:${it.id}" },
            ) { trailer ->
                NuvioPosterCard(
                    title = trailer.name,
                    imageUrl = trailer.background ?: trailer.poster,
                    fallbackImageUrl = trailer.poster,
                    shape = NuvioPosterShape.Landscape,
                    showTitleBelow = true,
                    onClick = onPreviewClick?.let {
                        { it(MetaPreview(id = trailer.id, type = trailer.type, name = trailer.name, poster = trailer.poster)) }
                    },
                )
            }
        }
    }
}
