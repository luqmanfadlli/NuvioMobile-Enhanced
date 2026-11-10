package com.nuvio.app.features.home.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.NuvioShelfSection
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.stableKey

/** Films about to reach streaming, rent or buy, each card carrying the date it lands. */
@Composable
internal fun HomeComingToDigitalRow(
    items: List<MetaPreview>,
    sectionPadding: Dp,
    modifier: Modifier = Modifier,
    onPosterClick: ((MetaPreview) -> Unit)? = null,
    onPosterLongClick: ((MetaPreview) -> Unit)? = null,
) {
    NuvioShelfSection(
        title = "Coming to Digital",
        entries = items,
        modifier = modifier,
        headerHorizontalPadding = sectionPadding,
        rowContentPadding = PaddingValues(horizontal = sectionPadding),
        key = { it.stableKey() },
    ) { item -> ComingToDigitalCard(item, onPosterClick, onPosterLongClick) }
}

private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/** "2026-11-14" -> "Nov 14". */
private fun digitalLabel(iso: String?): String {
    val parts = iso?.take(10)?.split("-") ?: return "Soon"
    val month = parts.getOrNull(1)?.toIntOrNull()?.let { MONTHS.getOrNull(it - 1) } ?: return "Soon"
    return "$month ${parts.getOrNull(2)?.toIntOrNull() ?: ""}".trim()
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ComingToDigitalCard(
    item: MetaPreview,
    onClick: ((MetaPreview) -> Unit)?,
    onLongClick: ((MetaPreview) -> Unit)?,
) {
    val tokens = MaterialTheme.nuvio
    Box(
        modifier = Modifier
            .width(270.dp)
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(12.dp))
            .background(tokens.colors.surface)
            .combinedClickable(onClick = { onClick?.invoke(item) }, onLongClick = onLongClick?.let { { it(item) } }),
    ) {
        AsyncImage(
            model = item.banner ?: item.poster,
            contentDescription = item.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.82f))))
        Text(
            text = "DIGITAL · ${digitalLabel(item.rawReleaseDate).uppercase()}",
            modifier = Modifier.align(Alignment.TopStart).padding(10.dp).clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.65f)).padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
        Text(
            text = item.name,
            modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
