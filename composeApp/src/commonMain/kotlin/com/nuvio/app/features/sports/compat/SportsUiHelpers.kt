package com.nuvio.app.features.sports.compat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.nuvio

/** Keeps content out from under the camera cutout and navigation bar when the phone is held sideways. */
@Composable
internal fun Modifier.liveTvSideInsets(): Modifier =
    windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))

@Composable
internal fun ChannelLogo(channel: LiveChannel, size: Dp, show: Boolean = true) {
    ChannelLogo(channel, width = size, height = size, show = show)
}

/** The channel's logo in a rounded box (or its first letters when it has none). */
@Composable
internal fun ChannelLogo(
    channel: LiveChannel,
    width: Dp,
    height: Dp,
    show: Boolean = true,
    inset: Dp = NuvioTokens.Space.s4,
    radius: Dp = NuvioTokens.Radius.md,
) {
    val tokens = MaterialTheme.nuvio
    Box(
        modifier = Modifier.width(width).height(height).clip(RoundedCornerShape(radius)).background(tokens.colors.surfaceCard),
        contentAlignment = Alignment.Center,
    ) {
        val logo = channel.logo?.takeIf { show && it.isNotBlank() }
        if (logo != null) {
            AsyncImage(model = logo, contentDescription = null, modifier = Modifier.fillMaxSize().padding(inset), contentScale = ContentScale.Fit)
        } else {
            Text(
                text = channel.name.trim().take(2).uppercase(),
                style = if (minOf(width, height) < 28.dp) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelLarge,
                color = tokens.colors.textMuted,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}

/** "20:30" or "8:30 PM", following the 24-hour setting when it's on. */
@Composable
internal fun formatClock(epochMs: Long): String {
    val settings by LiveTvPreferences.settings.collectAsStateWithLifecycle()
    return if (settings.use24HourClock) clock24(epochMs) else com.nuvio.app.core.format.formatLocalTime(epochMs)
}

internal fun clock24(epochMs: Long): String {
    val t = CivilTime.at(epochMs, LiveTvTimeZones.SYSTEM)
    return t.hour.toString().padStart(2, '0') + ":" + t.minute.toString().padStart(2, '0')
}
