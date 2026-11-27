package com.nuvio.app.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.DialogButton
import com.nuvio.app.core.ui.DialogButtonStyle
import com.nuvio.app.core.ui.DialogButtons
import com.nuvio.app.core.ui.DialogSurface
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.player.PlayerControlBarAlignment
import com.nuvio.app.features.player.PlayerToolbarButton
import com.nuvio.app.features.player.PlayerToolbarLayout
import com.nuvio.app.features.player.PlayerToolbarPlacement
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

internal val PlayerToolbarButton.labelRes: StringResource
    get() = when (this) {
        PlayerToolbarButton.NEXT_EPISODE -> Res.string.player_next_episode
        PlayerToolbarButton.SPEED -> Res.string.compose_player_speed
        PlayerToolbarButton.RESIZE -> Res.string.player_toolbar_button_resize
        PlayerToolbarButton.SUBTITLES -> Res.string.compose_player_subtitles
        PlayerToolbarButton.CHAPTERS -> Res.string.player_chapters
        PlayerToolbarButton.AUDIO -> Res.string.compose_player_audio
        PlayerToolbarButton.NIGHT_MODE -> Res.string.player_night_mode
        PlayerToolbarButton.QUALITY -> Res.string.player_toolbar_button_quality
        PlayerToolbarButton.SOURCES -> Res.string.compose_player_sources
        PlayerToolbarButton.EPISODES -> Res.string.compose_player_episodes
        PlayerToolbarButton.RATE -> Res.string.user_rating_action_rate
        PlayerToolbarButton.EXTERNAL_PLAYER -> Res.string.streams_open_external_player
        PlayerToolbarButton.PLAYBACK_INFO -> Res.string.compose_player_playback_info
        PlayerToolbarButton.SWITCH_ENGINE -> Res.string.cd_switch_player_engine
        PlayerToolbarButton.VIDEO_SETTINGS -> Res.string.player_action_video_settings
        PlayerToolbarButton.SUBMIT_INTRO -> Res.string.submit_intro_action
    }

internal val PlayerControlBarAlignment.labelRes: StringResource
    get() = when (this) {
        PlayerControlBarAlignment.LEFT -> Res.string.settings_playback_toolbar_align_left
        PlayerControlBarAlignment.CENTER -> Res.string.settings_playback_toolbar_align_center
        PlayerControlBarAlignment.RIGHT -> Res.string.settings_playback_toolbar_align_right
    }

internal val PlayerToolbarPlacement.labelRes: StringResource
    get() = when (this) {
        PlayerToolbarPlacement.BAR -> Res.string.settings_playback_toolbar_placement_bar
        PlayerToolbarPlacement.OVERFLOW -> Res.string.settings_playback_toolbar_placement_arrow
        PlayerToolbarPlacement.HIDDEN -> Res.string.settings_playback_toolbar_placement_off
    }

@Composable
internal fun PlayerToolbarSettingsDialog(
    layout: PlayerToolbarLayout,
    onLayoutChange: (PlayerToolbarLayout) -> Unit,
    onDismiss: () -> Unit,
) {
    DialogSurface(
        onDismissRequest = onDismiss,
        title = stringResource(Res.string.settings_playback_toolbar_layout),
        message = stringResource(Res.string.settings_playback_toolbar_note),
    ) {
        PlayerToolbarEditorContent(
            layout = layout,
            onLayoutChange = onLayoutChange,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .heightIn(max = 460.dp),
        )
        DialogButtons {
            DialogButton(
                text = stringResource(Res.string.action_reset),
                onClick = { onLayoutChange(PlayerToolbarLayout.Default) },
            )
            DialogButton(
                text = stringResource(Res.string.action_done),
                onClick = onDismiss,
                style = DialogButtonStyle.Primary,
            )
        }
    }
}

@Composable
internal fun PlayerToolbarEditorContent(
    layout: PlayerToolbarLayout,
    onLayoutChange: (PlayerToolbarLayout) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = MaterialTheme.nuvio
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(Res.string.settings_playback_toolbar_align),
                style = MaterialTheme.typography.labelLarge,
                color = tokens.colors.textSecondary,
                modifier = Modifier.padding(end = 8.dp),
            )
            SegmentedSelectorRow(
                options = PlayerControlBarAlignment.entries,
                selected = layout.alignment,
                labelOf = { stringResource(it.labelRes) },
                onSelect = { onLayoutChange(layout.withAlignment(it)) },
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            layout.entries.forEachIndexed { index, entry ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(tokens.shapes.compactCard)
                        .background(tokens.colors.textPrimary.copy(alpha = tokens.opacity.subtle))
                        .padding(start = 10.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = stringResource(entry.button.labelRes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (entry.placement == PlayerToolbarPlacement.HIDDEN) {
                            tokens.colors.textSecondary
                        } else {
                            tokens.colors.textPrimary
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    ToolbarRowAction(
                        icon = Icons.Rounded.KeyboardArrowUp,
                        description = stringResource(Res.string.settings_playback_toolbar_move_up),
                        enabled = index > 0,
                        onClick = { onLayoutChange(layout.moved(index, index - 1)) },
                    )
                    ToolbarRowAction(
                        icon = Icons.Rounded.KeyboardArrowDown,
                        description = stringResource(Res.string.settings_playback_toolbar_move_down),
                        enabled = index < layout.entries.lastIndex,
                        onClick = { onLayoutChange(layout.moved(index, index + 1)) },
                    )
                    SegmentedSelectorRow(
                        options = PlayerToolbarPlacement.entries,
                        selected = entry.placement,
                        labelOf = { stringResource(it.labelRes) },
                        onSelect = { placement ->
                            onLayoutChange(layout.withPlacement(entry.button, placement))
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun <T> SegmentedSelectorRow(
    options: List<T>,
    selected: T,
    labelOf: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val containerShape = RoundedCornerShape(8.dp)
    val pillShape = RoundedCornerShape(6.dp)
    Row(
        modifier = Modifier
            .clip(containerShape)
            .background(tokens.colors.surfaceCard.copy(alpha = 0.72f))
            .border(1.dp, tokens.colors.textPrimary.copy(alpha = 0.08f), containerShape)
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Box(
                modifier = Modifier
                    .clip(pillShape)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    )
                    .clickable { onSelect(option) }
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = labelOf(option),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else tokens.colors.textSecondary,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun ToolbarRowAction(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(32.dp)) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(18.dp))
    }
}
