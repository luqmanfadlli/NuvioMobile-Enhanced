package com.nuvio.app.features.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nuvio.app.features.settings.PlayerToolbarEditorContent
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_done
import nuvio.composeapp.generated.resources.action_reset
import nuvio.composeapp.generated.resources.settings_playback_toolbar_layout
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun PlayerToolbarSidePanel(
    visible: Boolean,
    layout: PlayerToolbarLayout,
    onLayoutChange: (PlayerToolbarLayout) -> Unit,
    onDismiss: () -> Unit,
) {
    PlayerSidePanel(
        visible = visible,
        onDismiss = onDismiss,
        width = 460.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PlayerPanelHeader(
                title = stringResource(Res.string.settings_playback_toolbar_layout),
                actions = {
                    PlayerDialogButton(
                        label = stringResource(Res.string.action_reset),
                        onClick = { onLayoutChange(PlayerToolbarLayout.Default) },
                    )
                    PlayerDialogButton(
                        label = stringResource(Res.string.action_done),
                        onClick = onDismiss,
                    )
                },
            )
            PlayerToolbarEditorContent(
                layout = layout,
                onLayoutChange = onLayoutChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        }
    }
}
