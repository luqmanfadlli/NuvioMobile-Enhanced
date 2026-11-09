package com.nuvio.app.features.podcasts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.nuvio

@Composable
internal fun ShowArtwork(imageUrl: String?, size: Dp, modifier: Modifier = Modifier, radius: Dp = NuvioTokens.Radius.lg) {
    val tokens = MaterialTheme.nuvio
    Box(
        modifier.size(size).clip(RoundedCornerShape(radius)).background(tokens.colors.surfaceElevated),
        contentAlignment = Alignment.Center,
    ) {
        if (imageUrl != null) {
            AsyncImage(model = imageUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(size))
        } else {
            Icon(Icons.Rounded.Mic, contentDescription = null, tint = tokens.colors.textMuted, modifier = Modifier.size(size / 3))
        }
    }
}

/** A "Sports Talk" shelf: podcast artwork you can scroll through, with a link to everything. */
@Composable
fun PodcastCarousel(
    shows: List<Podcast>,
    onOpenShow: (Podcast) -> Unit,
    onSeeAll: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Sports Talk",
) {
    if (shows.isEmpty()) return
    val tokens = MaterialTheme.nuvio
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s10)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = NuvioTokens.Space.s16),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = tokens.colors.textMuted,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Text(
                "See All",
                style = MaterialTheme.typography.labelLarge,
                color = tokens.colors.accent,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(onClick = onSeeAll),
            )
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = NuvioTokens.Space.s16),
            horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s12),
        ) {
            items(shows, key = { it.id }) { show ->
                Column(
                    Modifier.width(140.dp).clickable { onOpenShow(show) },
                    verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6),
                ) {
                    ShowArtwork(show.imageUrl, 140.dp)
                    Text(
                        show.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = tokens.colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
internal fun ShowRow(show: Podcast, onClick: () -> Unit, modifier: Modifier = Modifier, trailing: String? = null, chevron: Boolean = false) {
    val tokens = MaterialTheme.nuvio
    Row(
        modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = NuvioTokens.Space.s16, vertical = NuvioTokens.Space.s8),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s12),
    ) {
        ShowArtwork(show.imageUrl, 76.dp, radius = NuvioTokens.Radius.lg)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(show.title, style = MaterialTheme.typography.titleSmall, color = tokens.colors.textPrimary, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val sub = listOfNotNull(show.publisher.ifBlank { null }, trailing ?: show.sport).joinToString(" · ")
            if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodySmall, color = tokens.colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (chevron) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = tokens.colors.textMuted, modifier = Modifier.size(22.dp))
    }
}
