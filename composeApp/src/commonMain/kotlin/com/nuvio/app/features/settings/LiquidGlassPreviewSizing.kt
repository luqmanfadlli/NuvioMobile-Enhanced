package com.nuvio.app.features.settings

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.glass.LiquidGlassSurface

internal const val LIQUID_GLASS_PLAYER_PREVIEW_DESIGN_WIDTH_DP = 800f

/** Enough vertical room for the stream search field and resume banner together. */
internal const val LIQUID_GLASS_STREAM_SELECTION_PREVIEW_HEIGHT_DP = 152f

/** Keeps the miniature player in the same 16:9 landscape proportion as the real player. */
internal fun liquidGlassPlayerPreviewHeight(width: Dp): Dp =
    width.coerceAtMost(LIQUID_GLASS_PLAYER_PREVIEW_DESIGN_WIDTH_DP.dp) * 9f / 16f

/**
 * The real player is laid out at the same 800 dp logical width used by landscape tablet metrics.
 * The canvas is then scaled uniformly to the preview viewport instead of resizing its controls.
 */
internal fun liquidGlassPlayerPreviewScale(width: Dp): Float =
    (width.value / LIQUID_GLASS_PLAYER_PREVIEW_DESIGN_WIDTH_DP).coerceIn(0f, 1f)


/**
 * Surface targets present in the actual detail-action layout. The separate primary download
 * action is absent when the detail page uses the icon action row.
 */
internal fun liquidGlassDetailActionPreviewSelectableSurfaces(
    iconActionRow: Boolean,
    downloadActionAvailable: Boolean,
): Set<LiquidGlassSurface> = if (!iconActionRow && downloadActionAvailable) {
    setOf(LiquidGlassSurface.DETAILS, LiquidGlassSurface.DETAIL_PRIMARY_ACTION)
} else {
    setOf(LiquidGlassSurface.DETAILS)
}
