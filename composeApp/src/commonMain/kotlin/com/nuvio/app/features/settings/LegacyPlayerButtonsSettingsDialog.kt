package com.nuvio.app.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.DialogButton
import com.nuvio.app.core.ui.DialogButtonStyle
import com.nuvio.app.core.ui.DialogButtons
import com.nuvio.app.core.ui.DialogSurface
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.player.LegacyBottomButton
import com.nuvio.app.features.player.LegacyTopButton
import com.nuvio.app.features.player.PlayerButtonLayout
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

internal val LegacyBottomButton.labelRes: StringResource
    get() = when (this) {
        LegacyBottomButton.RESIZE -> Res.string.player_toolbar_button_resize
        LegacyBottomButton.SPEED -> Res.string.compose_player_speed
        LegacyBottomButton.SUBTITLES -> Res.string.compose_player_subtitles
        LegacyBottomButton.AUDIO -> Res.string.compose_player_audio
        LegacyBottomButton.NIGHT_MODE -> Res.string.player_night_mode
        LegacyBottomButton.SOURCES -> Res.string.compose_player_sources
        LegacyBottomButton.CHAPTERS -> Res.string.player_chapters
        LegacyBottomButton.EPISODES -> Res.string.compose_player_episodes
        LegacyBottomButton.LIVE_CHANNELS -> Res.string.compose_player_channels
        LegacyBottomButton.QUALITY -> Res.string.player_toolbar_button_quality
    }

internal val LegacyTopButton.labelRes: StringResource
    get() = when (this) {
        LegacyTopButton.SUBMIT_INTRO -> Res.string.submit_intro_action
        LegacyTopButton.EXTERNAL_PLAYER -> Res.string.streams_open_external_player
        LegacyTopButton.SWITCH_ENGINE -> Res.string.cd_switch_player_engine
        LegacyTopButton.PARTY -> Res.string.party_title
        LegacyTopButton.CAST -> Res.string.player_cast
        LegacyTopButton.PICTURE_IN_PICTURE -> Res.string.compose_player_picture_in_picture
        LegacyTopButton.ORIENTATION -> Res.string.player_toolbar_button_orientation
        LegacyTopButton.RATE -> Res.string.user_rating_action_rate
        LegacyTopButton.PLAYBACK_INFO -> Res.string.compose_player_playback_info
        LegacyTopButton.VIDEO_SETTINGS -> Res.string.player_action_video_settings
        LegacyTopButton.CUSTOMIZE_BUTTONS -> Res.string.settings_playback_toolbar_layout
        LegacyTopButton.LOCK -> Res.string.compose_player_lock_controls
    }

@Composable
internal fun <B : Enum<B>> LegacyPlayerButtonsSettingsDialog(
    title: String,
    layout: PlayerButtonLayout<B>,
    defaultLayout: PlayerButtonLayout<B>,
    label: (B) -> StringResource,
    onLayoutChange: (PlayerButtonLayout<B>) -> Unit,
    onDismiss: () -> Unit,
) {
    DialogSurface(
        onDismissRequest = onDismiss,
        title = title,
        message = stringResource(Res.string.settings_playback_toolbar_note),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 460.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            LegacyPlayerButtonsEditorContent(
                layout = layout,
                label = label,
                onLayoutChange = onLayoutChange,
            )
        }
        DialogButtons {
            DialogButton(
                text = stringResource(Res.string.action_reset),
                onClick = { onLayoutChange(defaultLayout) },
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
internal fun <B : Enum<B>> LegacyPlayerButtonsEditorContent(
    layout: PlayerButtonLayout<B>,
    label: (B) -> StringResource,
    onLayoutChange: (PlayerButtonLayout<B>) -> Unit,
) {
    LegacyButtonGroup(
        title = stringResource(Res.string.settings_playback_legacy_buttons_shown),
        visible = true,
        layout = layout,
        label = label,
        onLayoutChange = onLayoutChange,
    )
    LegacyButtonGroup(
        title = stringResource(Res.string.settings_playback_legacy_buttons_hidden),
        visible = false,
        layout = layout,
        label = label,
        onLayoutChange = onLayoutChange,
    )
}

@Composable
private fun <B : Enum<B>> LegacyButtonGroup(
    title: String,
    visible: Boolean,
    layout: PlayerButtonLayout<B>,
    label: (B) -> StringResource,
    onLayoutChange: (PlayerButtonLayout<B>) -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = tokens.colors.textSecondary,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
    val groupIndices = layout.entries.indices.filter { layout.entries[it].visible == visible }
    if (groupIndices.isEmpty()) {
        Text(
            text = stringResource(Res.string.settings_playback_toolbar_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = tokens.colors.textSecondary,
            modifier = Modifier.padding(vertical = 6.dp),
        )
        return
    }
    groupIndices.forEachIndexed { position, entryIndex ->
        val entry = layout.entries[entryIndex]
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(tokens.shapes.compactCard)
                .background(tokens.colors.textPrimary.copy(alpha = tokens.opacity.subtle))
                .padding(start = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(label(entry.button)),
                style = MaterialTheme.typography.bodyLarge,
                color = tokens.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            LegacyButtonRowAction(
                icon = Icons.Rounded.KeyboardArrowUp,
                description = stringResource(Res.string.settings_playback_toolbar_move_up),
                enabled = position > 0,
                onClick = { onLayoutChange(layout.moved(entryIndex, groupIndices[position - 1])) },
            )
            LegacyButtonRowAction(
                icon = Icons.Rounded.KeyboardArrowDown,
                description = stringResource(Res.string.settings_playback_toolbar_move_down),
                enabled = position < groupIndices.lastIndex,
                onClick = { onLayoutChange(layout.moved(entryIndex, groupIndices[position + 1])) },
            )
            LegacyButtonRowAction(
                icon = if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                description = stringResource(
                    if (visible) Res.string.settings_playback_legacy_buttons_hide else Res.string.settings_playback_legacy_buttons_show,
                ),
                enabled = true,
                onClick = {
                    val toggled = layout.withVisibility(entry.button, !visible)
                    onLayoutChange(toggled.moved(entryIndex, toggled.entries.lastIndex))
                },
            )
        }
    }
}

@Composable
private fun LegacyButtonRowAction(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(40.dp)) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(22.dp))
    }
}
