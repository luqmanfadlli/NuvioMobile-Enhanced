package com.nuvio.app.features.news

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.sports.compat.LiveTvClock

/** "CBS Sports · NFL · 3h ago": where it came from, what it's about, how old it is. */
internal fun NewsArticle.metaLine(includePublisher: Boolean = true): String =
    listOfNotNull(
        publisher.takeIf { includePublisher && it.isNotBlank() },
        leagueShort,
        NewsText.relative(publishedMs, LiveTvClock.nowMs()).ifBlank { null },
    ).joinToString(" · ")

/** The lead story: its picture full-width with the headline set over a dark fade. */
@Composable
internal fun ArticleHero(article: NewsArticle, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val tokens = MaterialTheme.nuvio
    Box(
        modifier.fillMaxWidth().aspectRatio(16f / 11f).clip(RoundedCornerShape(NuvioTokens.Radius.xl))
            .background(tokens.colors.surface).clickable(onClick = onClick),
    ) {
        if (article.imageUrl != null) {
            AsyncImage(model = article.imageUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color(0xE6000000))))
        Column(Modifier.align(Alignment.BottomStart).padding(NuvioTokens.Space.s16), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                (article.leagueShort ?: "Sports").uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.85f),
                fontWeight = FontWeight.Bold,
            )
            Text(
                article.headline,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                article.metaLine(),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A story in a list: source line, headline, and a thumbnail when it has one. */
@Composable
internal fun ArticleRow(article: NewsArticle, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val tokens = MaterialTheme.nuvio
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(NuvioTokens.Radius.lg)).background(tokens.colors.surface).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(NuvioTokens.Space.s12), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                article.metaLine(),
                style = MaterialTheme.typography.labelSmall,
                color = tokens.colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                article.headline,
                style = MaterialTheme.typography.titleSmall,
                color = tokens.colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (article.imageUrl != null) {
            AsyncImage(
                model = article.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.padding(end = NuvioTokens.Space.s12).size(width = 96.dp, height = 72.dp).clip(RoundedCornerShape(NuvioTokens.Radius.md)),
            )
        }
    }
}
