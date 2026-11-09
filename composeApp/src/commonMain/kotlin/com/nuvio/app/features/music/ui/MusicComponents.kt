package com.nuvio.app.features.music.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.nuvio.app.features.music.data.model.HomeShelf
import com.nuvio.app.features.music.data.model.ShelfItem
import com.nuvio.app.features.music.data.model.Song
import com.nuvio.app.features.music.playback.MusicPlayer
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** The palette the music section is drawn in: dark surfaces, white text, one green accent. */
internal object Sp {
    val bg = Color(0xFF121212)
    val surface = Color(0xFF181818)
    val elevated = Color(0xFF282828)
    val chip = Color(0xFF2A2A2A)
    val green = Color(0xFF1ED760)
    val text = Color.White
    val sub = Color(0xFFB3B3B3)
    val dim = Color(0xFF727272)
}

/** A catalogue card as a playable track (only meaningful when it has a video id). */
internal fun ShelfItem.toSong(): Song? {
    val id = videoId ?: return null
    return Song(
        videoId = id,
        title = title,
        artist = subtitle.substringBefore(" • ").ifBlank { subtitle },
        thumbnailUrl = thumbnailUrl,
        isVideo = isVideo,
    )
}

@Composable
internal fun Cover(
    url: String?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(4.dp),
) {
    Box(modifier.clip(shape).background(Sp.elevated), contentAlignment = Alignment.Center) {
        if (url.isNullOrBlank()) {
            Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = Sp.dim, modifier = Modifier.size(28.dp))
        } else {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
internal fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = Sp.text,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
internal fun Pill(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Sp.green else Sp.chip)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(label, color = if (selected) Color.Black else Sp.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

/** One track as a list row, with the playing one tinted green. */
@Composable
internal fun SongRow(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    index: Int? = null,
    showCover: Boolean = true,
    onMore: (() -> Unit)? = null,
) {
    val state by MusicPlayer.state.collectAsStateWithLifecycle()
    val isCurrent = state.song?.videoId == song.videoId
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (index != null) {
            Text(
                "${index + 1}",
                color = if (isCurrent) Sp.green else Sp.sub,
                fontSize = 14.sp,
                modifier = Modifier.width(24.dp),
            )
        }
        if (showCover) Cover(song.thumbnailUrl, Modifier.size(52.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                color = if (isCurrent) Sp.green else Sp.text,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(song.artist.takeIf { it.isNotBlank() }, song.durationText).joinToString(" • "),
                color = Sp.sub,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (onMore != null) {
            Icon(
                Icons.Rounded.MoreVert,
                contentDescription = "More",
                tint = Sp.sub,
                modifier = Modifier.size(24.dp).clip(CircleShape).clickable(onClick = onMore),
            )
        }
    }
}

/** A titled row of square cards, like Spotify's home shelves. */
@Composable
internal fun Shelf(
    shelf: HomeShelf,
    onItem: (ShelfItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (shelf.items.isEmpty()) return
    Column(modifier) {
        SectionTitle(shelf.title)
        LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(shelf.items) { item ->
                val wide = item.isVideo
                Column(
                    Modifier.width(if (wide) 240.dp else 150.dp).clickable { onItem(item) },
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Cover(
                        item.thumbnailUrl,
                        Modifier.width(if (wide) 240.dp else 150.dp).height(if (wide) 135.dp else 150.dp),
                        shape = RoundedCornerShape(if (item.browseId?.startsWith("UC") == true) 75.dp else 6.dp),
                    )
                    Text(item.title, color = Sp.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(item.subtitle, color = Sp.sub, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
