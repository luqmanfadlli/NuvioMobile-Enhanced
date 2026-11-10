package com.nuvio.app.features.newhot

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.Chip
import com.nuvio.app.core.ui.NuvioScreenHeader
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.core.ui.nuvioSafeBottomPadding
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.library.LibraryClock
import com.nuvio.app.features.library.LibraryRepository
import com.nuvio.app.features.library.toLibraryItem
import kotlinx.coroutines.launch

private enum class NewHotSection(val label: String) {
    ComingSoon("Coming Soon"),
    Watching("Everyone's Watching"),
    TopMovies("Top 10 Movies"),
    TopShows("Top 10 Shows"),
}

/**
 * New & Hot, laid out the way Netflix does it: a row of chips across the top and, under the one you pick, big
 * landscape cards with what each title is about. Coming Soon leads with the release date.
 */
@Composable
fun NewAndHotScreen(
    onPosterClick: (MetaPreview) -> Unit,
    onPosterLongClick: ((MetaPreview) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val tokens = MaterialTheme.nuvio
    LaunchedEffect(Unit) { NewHotRepository.ensureLoaded() }
    val state by NewHotRepository.state.collectAsStateWithLifecycle()
    var sectionName by rememberSaveable { mutableStateOf(NewHotSection.ComingSoon.name) }
    val section = NewHotSection.entries.firstOrNull { it.name == sectionName } ?: NewHotSection.ComingSoon

    Column(modifier.fillMaxSize().background(tokens.colors.background).statusBarsPadding()) {
        NuvioScreenHeader(title = "New & Hot", includeStatusBarPadding = false, modifier = Modifier.padding(horizontal = NuvioTokens.Space.s8))
        LazyRow(
            contentPadding = PaddingValues(horizontal = NuvioTokens.Space.s16),
            horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8),
            modifier = Modifier.padding(vertical = NuvioTokens.Space.s8),
        ) {
            items(NewHotSection.entries.toList(), key = { it.name }) { s ->
                Chip(s.label, selected = s == section, onClick = { sectionName = s.name })
            }
        }
        val list = when (section) {
            NewHotSection.ComingSoon -> state.comingSoon
            NewHotSection.Watching -> state.everyoneWatching
            NewHotSection.TopMovies -> state.topMovies
            NewHotSection.TopShows -> state.topShows
        }
        if (list.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(top = 80.dp), contentAlignment = Alignment.TopCenter) {
                if (state.isLoading) CircularProgressIndicator() else Text("Nothing to show right now.", color = tokens.colors.textMuted)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = nuvioSafeBottomPadding(24.dp)),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                itemsIndexed(list, key = { _, item -> "${section.name}:${item.type}:${item.id}" }) { index, item ->
                    NewHotCard(
                        item = item,
                        rank = (index + 1).takeIf { section == NewHotSection.TopMovies || section == NewHotSection.TopShows },
                        showDate = section == NewHotSection.ComingSoon,
                        onClick = { onPosterClick(item) },
                        onLongClick = onPosterLongClick?.let { { it(item) } },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NewHotCard(
    item: MetaPreview,
    rank: Int?,
    showDate: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
) {
    val tokens = MaterialTheme.nuvio
    Row(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (showDate) {
            val (month, day) = dateParts(item.rawReleaseDate)
            Column(Modifier.width(44.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(month, style = MaterialTheme.typography.labelMedium, color = tokens.colors.textMuted, fontWeight = FontWeight.SemiBold)
                Text(day, fontSize = 26.sp, fontWeight = FontWeight.Black, color = tokens.colors.textPrimary)
            }
        }
        if (rank != null) {
            Text(
                rank.toString(),
                modifier = Modifier.width(if (rank >= 10) 56.dp else 40.dp),
                fontSize = 44.sp,
                fontWeight = FontWeight.Black,
                color = tokens.colors.textPrimary,
                textAlign = TextAlign.Center,
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val art = item.banner ?: item.poster
            AsyncImage(
                model = art,
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)).background(tokens.colors.surface),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = tokens.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                MyListToggle(item)
            }
            val subtitle = when {
                showDate -> item.rawReleaseDate?.let { "Coming ${dateParts(it).first.lowercase().replaceFirstChar(Char::uppercase)} ${dateParts(it).second}" }
                else -> item.releaseInfo
            }
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.labelMedium, color = tokens.colors.textMuted)
            item.description?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = tokens.colors.textMuted, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** "+ My List" in the corner of a card: saves the title (there is no push reminder yet), a check once saved. */
@Composable
private fun MyListToggle(item: MetaPreview) {
    val libraryState by LibraryRepository.uiState.collectAsStateWithLifecycle()
    val saved = remember(libraryState, item.id, item.type) { LibraryRepository.isSaved(item.id, item.type) }
    val scope = rememberCoroutineScope()
    Box(
        Modifier.size(34.dp).clip(CircleShape).background(MaterialTheme.nuvio.colors.surfaceElevated).clickable {
            scope.launch { runCatching { LibraryRepository.toggleSaved(item.toLibraryItem(LibraryClock.nowEpochMs())) } }
        },
        contentAlignment = Alignment.Center,
    ) {
        Icon(if (saved) Icons.Rounded.Check else Icons.Rounded.Add, contentDescription = if (saved) "In My List" else "Add to My List", modifier = Modifier.size(20.dp), tint = MaterialTheme.nuvio.colors.textPrimary)
    }
}

private val MONTHS = listOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")

/** "2026-11-14" -> ("NOV", "14"). */
private fun dateParts(iso: String?): Pair<String, String> {
    val parts = iso?.take(10)?.split("-")
    val month = parts?.getOrNull(1)?.toIntOrNull()?.let { MONTHS.getOrNull(it - 1) }
    val day = parts?.getOrNull(2)?.toIntOrNull()?.toString()
    return (month ?: "SOON") to (day ?: "")
}
