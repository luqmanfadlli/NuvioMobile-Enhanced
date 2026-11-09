package com.nuvio.app.features.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_close
import nuvio.composeapp.generated.resources.player_anime4k_badge_sharpen
import nuvio.composeapp.generated.resources.player_anime4k_badge_upscale
import nuvio.composeapp.generated.resources.player_anime4k_status_active
import nuvio.composeapp.generated.resources.player_anime4k_status_inactive
import nuvio.composeapp.generated.resources.player_anime4k_status_off
import nuvio.composeapp.generated.resources.player_anime4k_title
import nuvio.composeapp.generated.resources.settings_playback_anime_line_sharpen
import nuvio.composeapp.generated.resources.settings_playback_anime_line_sharpen_description
import nuvio.composeapp.generated.resources.settings_playback_anime_upscale
import nuvio.composeapp.generated.resources.settings_playback_anime_upscale_description
import org.jetbrains.compose.resources.stringResource
import kotlin.math.min

@Composable
internal fun animeEnhancementSummary(status: AnimeEnhancementStatus): String {
    val parts = buildList {
        if (status.upscale) {
            add(
                stringResource(
                    Res.string.player_anime4k_badge_upscale,
                    min(status.sourceWidth, status.sourceHeight),
                    min(status.outputWidth, status.outputHeight),
                ),
            )
        }
        if (status.lineSharpen) add(stringResource(Res.string.player_anime4k_badge_sharpen))
    }
    return parts.joinToString(" · ")
}

@Composable
internal fun AnimeEnhancementBadge(
    status: AnimeEnhancementStatus?,
    modifier: Modifier = Modifier,
) {
    if (status == null) return
    Text(
        text = "${stringResource(Res.string.player_anime4k_title)} · ${animeEnhancementSummary(status)}",
        color = Color.White.copy(alpha = 0.92f),
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .background(Color.White.copy(alpha = 0.16f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
internal fun AnimeEnhancementPanel(
    visible: Boolean,
    settings: PlayerSettingsUiState,
    status: AnimeEnhancementStatus?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlayerSidePanel(
        visible = visible,
        onDismiss = onDismiss,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
        ) {
            PlayerPanelHeader(
                title = stringResource(Res.string.player_anime4k_title),
            ) {
                PlayerDialogButton(
                    label = stringResource(Res.string.action_close),
                    onClick = onDismiss,
                )
            }

            Spacer(Modifier.height(8.dp))

            val enabled = settings.androidAnimeUpscaleEnabled || settings.androidAnimeLineSharpenEnabled
            Text(
                text = when {
                    status != null -> stringResource(Res.string.player_anime4k_status_active, animeEnhancementSummary(status))
                    enabled -> stringResource(Res.string.player_anime4k_status_inactive)
                    else -> stringResource(Res.string.player_anime4k_status_off)
                },
                color = if (status != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
            )

            Spacer(Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                AnimeEnhancementToggleRow(
                    title = stringResource(Res.string.settings_playback_anime_upscale),
                    description = stringResource(Res.string.settings_playback_anime_upscale_description),
                    checked = settings.androidAnimeUpscaleEnabled,
                    onCheckedChange = PlayerSettingsRepository::setAndroidAnimeUpscaleEnabled,
                )
                AnimeEnhancementToggleRow(
                    title = stringResource(Res.string.settings_playback_anime_line_sharpen),
                    description = stringResource(Res.string.settings_playback_anime_line_sharpen_description),
                    checked = settings.androidAnimeLineSharpenEnabled,
                    onCheckedChange = PlayerSettingsRepository::setAndroidAnimeLineSharpenEnabled,
                )
            }
        }
    }
}

@Composable
private fun AnimeEnhancementToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
            Text(text = description, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
