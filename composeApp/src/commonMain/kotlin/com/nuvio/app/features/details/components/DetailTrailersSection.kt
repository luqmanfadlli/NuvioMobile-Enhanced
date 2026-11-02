package com.nuvio.app.features.details.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.Menu
import com.nuvio.app.core.ui.MenuItem
import com.nuvio.app.core.ui.NativeLiquidGlassMenuOption
import com.nuvio.app.core.ui.NuvioNativeLiquidGlassButton
import com.nuvio.app.core.ui.NuvioNativeLiquidGlassOptionMenu
import com.nuvio.app.core.ui.isNativeLiquidGlassOptionMenuAvailable
import com.nuvio.app.core.ui.isNativeLiquidGlassControlsAvailable
import com.nuvio.app.core.ui.NuvioCardDepthSurface
import com.nuvio.app.core.ui.nuvioCardDepth
import com.nuvio.app.core.ui.nuvioHorizontalScrollBleed
import com.nuvio.app.core.ui.rememberPosterCardStyleUiState
import com.nuvio.app.core.ui.glass.LiquidGlassSurface
import com.nuvio.app.core.ui.glass.isLiquidGlassAvailable
import com.nuvio.app.core.ui.glass.isLiquidGlassSurfaceEffectivelyEnabled
import com.nuvio.app.core.ui.glass.liquidGlassForSurface
import com.nuvio.app.core.ui.glass.LiquidGlassTextContrastScope
import com.nuvio.app.core.ui.glass.liquidGlassIconTint
import com.nuvio.app.core.ui.glass.liquidGlassShape
import com.nuvio.app.features.details.MetaTrailer
import nuvio.composeapp.generated.resources.*
import nuvio.composeapp.generated.resources.detail_tab_trailer
import nuvio.composeapp.generated.resources.detail_trailer_category_count
import nuvio.composeapp.generated.resources.action_cancel
import nuvio.composeapp.generated.resources.detail_trailers_title
import org.jetbrains.compose.resources.stringResource

@Composable
fun DetailTrailersSection(
    trailers: List<MetaTrailer>,
    onTrailerClick: (MetaTrailer) -> Unit,
    modifier: Modifier = Modifier,
    showHeader: Boolean = true,
    horizontalScrollPadding: Dp = 0.dp,
    onPreviewSurfaceSelected: ((LiquidGlassSurface) -> Unit)? = null,
    selectedPreviewSurface: LiquidGlassSurface? = null,
    previewMenuInitiallyExpanded: Boolean = false,
) {
    if (trailers.isEmpty()) return

    val trailerLabel = stringResource(Res.string.detail_tab_trailer)
    val grouped = remember(trailers) {
        linkedMapOf<String, MutableList<MetaTrailer>>().apply {
            trailers.forEach { trailer ->
                val category = trailer.type.ifBlank { trailerLabel }
                getOrPut(category) { mutableListOf() }.add(trailer)
            }
        }
    }

    if (grouped.isEmpty()) return

    val initialCategory = remember(grouped) {
        grouped.keys.firstOrNull { it.equals(trailerLabel, ignoreCase = true) }
            ?: grouped.keys.first()
    }
    var selectedCategory by remember(grouped) { mutableStateOf(initialCategory) }
    var menuExpanded by remember(grouped, previewMenuInitiallyExpanded) {
        mutableStateOf(previewMenuInitiallyExpanded)
    }

    val selectedTrailers = grouped[selectedCategory].orEmpty()
    val useNativeTrailerCategoryMenu = !previewMenuInitiallyExpanded &&
        onPreviewSurfaceSelected == null &&
        selectedPreviewSurface == null &&
        isNativeLiquidGlassOptionMenuAvailable() &&
        isLiquidGlassSurfaceEffectivelyEnabled(LiquidGlassSurface.DETAIL_TRAILER_MENU)
    val posterCardStyle = rememberPosterCardStyleUiState()
    val userCornerRadius = posterCardStyle.cornerRadiusDp.dp

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val sizing = trailerSectionSizing(maxWidth.value, userCornerRadius)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showHeader) {
                    DetailSectionTitle(
                        title = stringResource(Res.string.detail_trailers_title),
                        fullWidth = false,
                    )
                }

                Box {
                    val selectorSurface = LiquidGlassSurface.DETAIL_TRAILER_SELECTOR
                    val selectorGlassEnabled = isLiquidGlassAvailable() && isLiquidGlassSurfaceEffectivelyEnabled(selectorSurface)
                    val selectorPreview = onPreviewSurfaceSelected != null || selectedPreviewSurface != null
                    val selectorNativeGlassEnabled = isNativeLiquidGlassControlsAvailable() &&
                        isLiquidGlassSurfaceEffectivelyEnabled(
                            selectorSurface,
                            preview = selectorPreview,
                            platformSupported = true,
                        ) &&
                        (!selectorPreview || selectedPreviewSurface == null || selectedPreviewSurface == selectorSurface) &&
                        !previewMenuInitiallyExpanded
                    val selectorShape = liquidGlassShape(
                        surface = selectorSurface,
                        defaultShape = RoundedCornerShape(sizing.selectorRadius),
                    )
                    val selectorTint = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f)
                    val selectorFallback = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    val selectorModifier = if (selectorGlassEnabled) {
                        Modifier.liquidGlassForSurface(
                            surface = selectorSurface,
                            shape = selectorShape,
                            tint = selectorTint,
                            fallback = selectorFallback,
                            blurRadius = 12.dp,
                            refractionHeight = 6.dp,
                            refractionAmount = 9.dp,
                        )
                    } else {
                        Modifier
                    }
                    val selectorSelectionOutline = selectedPreviewSurface == selectorSurface
                    val selectorBorder = if (selectorSelectionOutline) {
                        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, selectorShape)
                    } else {
                        Modifier
                    }

                    if (selectorNativeGlassEnabled) {
                        NuvioNativeLiquidGlassButton(
                            title = selectedCategory,
                            systemImage = "chevron.down",
                            accessibilityLabel = selectedCategory,
                            onClick = {
                                if (selectorPreview) {
                                    onPreviewSurfaceSelected?.invoke(selectorSurface)
                                } else {
                                    menuExpanded = true
                                }
                            },
                            modifier = Modifier.height(36.dp),
                        )
                    } else {
                        Surface(
                            shape = selectorShape,
                            color = if (selectorGlassEnabled) Color.Transparent else selectorFallback,
                            tonalElevation = 0.dp,
                            modifier = selectorModifier
                                .clip(selectorShape)
                                .then(selectorBorder)
                                .clickable {
                                    onPreviewSurfaceSelected?.invoke(selectorSurface)
                                    menuExpanded = true
                                },
                        ) {
                            LiquidGlassTextContrastScope(enabled = selectorGlassEnabled) {
                                Row(
                                    modifier = Modifier.padding(
                                        horizontal = sizing.selectorHorizontalPadding,
                                        vertical = sizing.selectorVerticalPadding,
                                    ),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(
                                        text = selectedCategory,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            fontWeight = FontWeight.SemiBold,
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Icon(
                                        imageVector = Icons.Filled.ExpandMore,
                                        contentDescription = null,
                                        tint = liquidGlassIconTint(
                                            surface = selectorSurface,
                                            default = MaterialTheme.colorScheme.onSurfaceVariant,
                                        ),
                                        modifier = Modifier.size(sizing.selectorIconSize),
                                    )
                                }
                            }
                        }
                    }

                    if (useNativeTrailerCategoryMenu) {
                        NuvioNativeLiquidGlassOptionMenu(
                            visible = menuExpanded,
                            title = null,
                            cancelLabel = stringResource(Res.string.action_cancel),
                            options = grouped.map { (category, categoryTrailers) ->
                                NativeLiquidGlassMenuOption(
                                    id = category,
                                    title = stringResource(
                                        Res.string.detail_trailer_category_count,
                                        category,
                                        categoryTrailers.size,
                                    ),
                                )
                            },
                            selectedOptionId = selectedCategory,
                            onOptionSelected = { category ->
                                if (category in grouped) selectedCategory = category
                            },
                            onDismiss = { menuExpanded = false },
                        )
                    } else {
                        Menu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                            glassSurface = LiquidGlassSurface.DETAIL_TRAILER_MENU,
                            onGlassSurfaceInteracted = onPreviewSurfaceSelected?.let { onSelected ->
                                { onSelected(LiquidGlassSurface.DETAIL_TRAILER_MENU) }
                            },
                            selectionHighlight = selectedPreviewSurface == LiquidGlassSurface.DETAIL_TRAILER_MENU,
                        ) {
                            grouped.keys.forEach { category ->
                                val count = grouped[category]?.size ?: 0
                                MenuItem(
                                    text = stringResource(Res.string.detail_trailer_category_count, category, count),
                                    selected = category == selectedCategory,
                                    onClick = {
                                        selectedCategory = category
                                        menuExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val sizing = trailerSectionSizing(maxWidth.value, userCornerRadius)
            LazyRow(
                modifier = Modifier
                    .nuvioHorizontalScrollBleed(horizontalScrollPadding)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = horizontalScrollPadding),
                horizontalArrangement = Arrangement.spacedBy(sizing.cardSpacing),
            ) {
                itemsIndexed(
                    items = selectedTrailers,
                    key = { index, trailer -> "${trailer.type}-${trailer.id}-${trailer.seasonNumber ?: 0}#$index" },
                ) { _, trailer ->
                    TrailerCard(
                        trailer = trailer,
                        cardWidth = sizing.cardWidth,
                        cornerRadius = sizing.cardRadius,
                        titleFontSize = sizing.titleFontSize,
                        metaFontSize = sizing.metaFontSize,
                        onClick = { onTrailerClick(trailer) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TrailerCard(
    trailer: MetaTrailer,
    cardWidth: androidx.compose.ui.unit.Dp,
    cornerRadius: androidx.compose.ui.unit.Dp,
    titleFontSize: androidx.compose.ui.unit.TextUnit,
    metaFontSize: androidx.compose.ui.unit.TextUnit,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier.width(cardWidth),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(cornerRadius))
                .nuvioCardDepth(
                    shape = RoundedCornerShape(cornerRadius),
                    surface = NuvioCardDepthSurface.Trailers,
                )
                .clickable(onClick = onClick),
        ) {
            AsyncImage(
                model = "https://img.youtube.com/vi/${trailer.key}/hqdefault.jpg",
                contentDescription = trailer.displayName ?: trailer.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clip(RoundedCornerShape(cornerRadius)),
                contentScale = ContentScale.Crop,
            )

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.2f))
            )
        }

        Text(
            text = trailer.displayName ?: trailer.name,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = titleFontSize,
            ),
            color = MaterialTheme.colorScheme.onSurface,
        )

        val year = trailer.publishedAt?.take(4).orEmpty()
        if (year.isNotBlank()) {
            Text(
                text = year,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = metaFontSize),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private data class TrailerSectionSizing(
    val cardWidth: androidx.compose.ui.unit.Dp,
    val cardSpacing: androidx.compose.ui.unit.Dp,
    val cardRadius: androidx.compose.ui.unit.Dp,
    val selectorRadius: androidx.compose.ui.unit.Dp,
    val selectorHorizontalPadding: androidx.compose.ui.unit.Dp,
    val selectorVerticalPadding: androidx.compose.ui.unit.Dp,
    val selectorIconSize: androidx.compose.ui.unit.Dp,
    val titleFontSize: androidx.compose.ui.unit.TextUnit,
    val metaFontSize: androidx.compose.ui.unit.TextUnit,
)

private fun trailerSectionSizing(maxWidthDp: Float, userCornerRadius: androidx.compose.ui.unit.Dp = 16.dp): TrailerSectionSizing =
    when {
        maxWidthDp >= 1200f -> TrailerSectionSizing(
            cardWidth = 280.dp,
            cardSpacing = 16.dp,
            cardRadius = userCornerRadius,
            selectorRadius = 20.dp,
            selectorHorizontalPadding = 14.dp,
            selectorVerticalPadding = 8.dp,
            selectorIconSize = 22.dp,
            titleFontSize = 16.sp,
            metaFontSize = 14.sp,
        )

        maxWidthDp >= 1024f -> TrailerSectionSizing(
            cardWidth = 260.dp,
            cardSpacing = 14.dp,
            cardRadius = userCornerRadius,
            selectorRadius = 18.dp,
            selectorHorizontalPadding = 12.dp,
            selectorVerticalPadding = 6.dp,
            selectorIconSize = 20.dp,
            titleFontSize = 15.sp,
            metaFontSize = 13.sp,
        )

        maxWidthDp >= 768f -> TrailerSectionSizing(
            cardWidth = 240.dp,
            cardSpacing = 12.dp,
            cardRadius = userCornerRadius,
            selectorRadius = 16.dp,
            selectorHorizontalPadding = 10.dp,
            selectorVerticalPadding = 5.dp,
            selectorIconSize = 18.dp,
            titleFontSize = 14.sp,
            metaFontSize = 12.sp,
        )

        else -> TrailerSectionSizing(
            cardWidth = 200.dp,
            cardSpacing = 12.dp,
            cardRadius = userCornerRadius,
            selectorRadius = 16.dp,
            selectorHorizontalPadding = 10.dp,
            selectorVerticalPadding = 5.dp,
            selectorIconSize = 18.dp,
            titleFontSize = 12.sp,
            metaFontSize = 10.sp,
        )
    }
