package com.nuvio.app.features.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Hearing
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.nuvio
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.compose_player_sync_by_ear
import nuvio.composeapp.generated.resources.compose_player_sync_by_ear_heard
import nuvio.composeapp.generated.resources.compose_player_sync_by_ear_hint
import nuvio.composeapp.generated.resources.compose_player_sync_by_ear_in_sync
import nuvio.composeapp.generated.resources.compose_player_sync_by_ear_offset
import nuvio.composeapp.generated.resources.compose_player_sync_by_ear_saw
import nuvio.composeapp.generated.resources.compose_player_sync_by_ear_waiting_heard
import nuvio.composeapp.generated.resources.compose_player_sync_by_ear_waiting_saw
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs
import com.nuvio.app.core.ui.glass.ambientLiquidGlass

@Composable
fun SubtitleSyncByEarCard(
    visible: Boolean,
    subtitleDelayMs: Int,
    heardCaptured: Boolean,
    sawCaptured: Boolean,
    onHeard: () -> Unit,
    onSaw: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = MaterialTheme.nuvio

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn() + slideInVertically { -it / 4 },
        exit = fadeOut() + slideOutVertically { -it / 4 },
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 340.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .ambientLiquidGlass(
                    shape = RoundedCornerShape(20.dp),
                    tint = Color(0xFF17171A).copy(alpha = 0.72f),
                    fallback = Color(0xFF17171A).copy(alpha = 0.94f),
                    blurRadius = 24.dp,
                    refractionHeight = 14.dp,
                    refractionAmount = 24.dp,
                )
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(20.dp),
                )
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(start = 18.dp, end = 12.dp, top = 12.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(Res.string.compose_player_sync_by_ear),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Text(
                modifier = Modifier.padding(end = 6.dp),
                text = stringResource(Res.string.compose_player_sync_by_ear_hint),
                color = Color.White.copy(alpha = 0.72f),
                style = MaterialTheme.typography.bodySmall,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SyncByEarButton(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.Hearing,
                    label = stringResource(Res.string.compose_player_sync_by_ear_heard),
                    captured = heardCaptured,
                    onClick = onHeard,
                )
                SyncByEarButton(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.Visibility,
                    label = stringResource(Res.string.compose_player_sync_by_ear_saw),
                    captured = sawCaptured,
                    onClick = onSaw,
                )
            }

            val statusText = when {
                heardCaptured -> stringResource(Res.string.compose_player_sync_by_ear_waiting_saw)
                sawCaptured -> stringResource(Res.string.compose_player_sync_by_ear_waiting_heard)
                subtitleDelayMs == 0 -> stringResource(Res.string.compose_player_sync_by_ear_in_sync)
                else -> stringResource(
                    Res.string.compose_player_sync_by_ear_offset,
                    formatSyncByEarOffset(subtitleDelayMs),
                )
            }
            Text(
                modifier = Modifier.padding(end = 6.dp),
                text = statusText,
                color = if (heardCaptured || sawCaptured) tokens.colors.accent else Color.White.copy(alpha = 0.6f),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun SyncByEarButton(
    icon: ImageVector,
    label: String,
    captured: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = MaterialTheme.nuvio
    val shape = RoundedCornerShape(14.dp)
    val contentColor = if (captured) tokens.colors.onAccent else Color.White

    Row(
        modifier = modifier
            .clip(shape)
            .background(if (captured) tokens.colors.accent else Color.White.copy(alpha = 0.1f))
            .border(
                width = 1.dp,
                color = if (captured) Color.Transparent else Color.White.copy(alpha = 0.08f),
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = label,
            color = contentColor,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun formatSyncByEarOffset(delayMs: Int): String {
    val sign = if (delayMs >= 0) "+" else "-"
    val absoluteMs = abs(delayMs)
    val seconds = absoluteMs / 1000
    val millis = absoluteMs % 1000
    return "$sign$seconds.${millis.toString().padStart(3, '0')}s"
}
