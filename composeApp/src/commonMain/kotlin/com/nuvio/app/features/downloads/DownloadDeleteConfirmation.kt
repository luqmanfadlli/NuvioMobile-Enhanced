package com.nuvio.app.features.downloads

import androidx.compose.runtime.Composable
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_delete_confirm_message
import nuvio.composeapp.generated.resources.action_delete_confirm_title
import nuvio.composeapp.generated.resources.action_no
import nuvio.composeapp.generated.resources.action_yes
import nuvio.composeapp.generated.resources.downloads_delete_selected_confirm
import org.jetbrains.compose.resources.stringResource
import com.nuvio.app.core.ui.NuvioStatusModal
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.NuvioToastController
import nuvio.composeapp.generated.resources.downloads_open_directory
import nuvio.composeapp.generated.resources.downloads_open_directory_failed

@Composable
internal fun DownloadDeleteConfirmation(
    count: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    NuvioStatusModal(
        title = stringResource(Res.string.action_delete_confirm_title),
        message = if (count > 1) stringResource(Res.string.downloads_delete_selected_confirm, count)
        else stringResource(Res.string.action_delete_confirm_message),
        isVisible = true,
        destructive = true,
        confirmText = stringResource(Res.string.action_yes),
        dismissText = stringResource(Res.string.action_no),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

@Composable
internal fun OpenDownloadsFolderButton(
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    buttonSize: Dp = 40.dp,
    iconSize: Dp = 22.dp,
) {
    val failedText = stringResource(Res.string.downloads_open_directory_failed)
    IconButton(
        onClick = {
            if (!DownloadsPlatformDownloader.openDownloadsDirectory()) NuvioToastController.show(failedText)
        },
        modifier = modifier.size(buttonSize),
    ) {
        Icon(
            imageVector = Icons.Rounded.Folder,
            contentDescription = stringResource(Res.string.downloads_open_directory),
            modifier = Modifier.size(iconSize),
            tint = tint,
        )
    }
}
