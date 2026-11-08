package com.nuvio.app.features.settings

import androidx.compose.foundation.lazy.LazyListScope
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.settings_nuvio_reshaped_autosync
import nuvio.composeapp.generated.resources.settings_nuvio_reshaped_autosync_description
import nuvio.composeapp.generated.resources.settings_nuvio_reshaped_fonts
import nuvio.composeapp.generated.resources.settings_nuvio_reshaped_fonts_description
import nuvio.composeapp.generated.resources.settings_nuvio_reshaped_seek_buffer
import nuvio.composeapp.generated.resources.settings_nuvio_reshaped_seek_buffer_description
import nuvio.composeapp.generated.resources.settings_nuvio_reshaped_seek_previews
import nuvio.composeapp.generated.resources.settings_nuvio_reshaped_seek_previews_description
import nuvio.composeapp.generated.resources.settings_nuvio_reshaped_pill_menu
import nuvio.composeapp.generated.resources.settings_nuvio_reshaped_pill_menu_description
import nuvio.composeapp.generated.resources.settings_nuvio_reshaped_streams
import nuvio.composeapp.generated.resources.settings_nuvio_reshaped_streams_description
import nuvio.composeapp.generated.resources.settings_nuvio_reshaped_section_subtitles
import nuvio.composeapp.generated.resources.settings_nuvio_reshaped_section_playback
import nuvio.composeapp.generated.resources.settings_nuvio_reshaped_section_streams
import org.jetbrains.compose.resources.stringResource

internal fun LazyListScope.nuvioReshapedSettingsContent(
    isTablet: Boolean,
    onPageClick: (SettingsPage) -> Unit,
) {
    item(key = "nuvio_reshaped_subtitles") {
        SettingsSection(
            title = stringResource(Res.string.settings_nuvio_reshaped_section_subtitles),
            isTablet = isTablet,
        ) {
            SettingsGroup(isTablet = isTablet) {
                SettingsNavigationRow(
                    title = stringResource(Res.string.settings_nuvio_reshaped_autosync),
                    description = stringResource(Res.string.settings_nuvio_reshaped_autosync_description),
                    icon = SettingsPage.PlaybackSubtitles.icon,
                    isTablet = isTablet,
                    onClick = { onPageClick(SettingsPage.PlaybackSubtitles) },
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.settings_nuvio_reshaped_fonts),
                    description = stringResource(Res.string.settings_nuvio_reshaped_fonts_description),
                    icon = SettingsPage.PlaybackSubtitles.icon,
                    isTablet = isTablet,
                    onClick = { onPageClick(SettingsPage.PlaybackSubtitles) },
                )
            }
        }
    }
    item(key = "nuvio_reshaped_playback") {
        SettingsSection(
            title = stringResource(Res.string.settings_nuvio_reshaped_section_playback),
            isTablet = isTablet,
        ) {
            SettingsGroup(isTablet = isTablet) {
                SettingsNavigationRow(
                    title = stringResource(Res.string.settings_nuvio_reshaped_seek_buffer),
                    description = stringResource(Res.string.settings_nuvio_reshaped_seek_buffer_description),
                    icon = SettingsPage.PlaybackBuffer.icon,
                    isTablet = isTablet,
                    onClick = { onPageClick(SettingsPage.PlaybackBuffer) },
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.settings_nuvio_reshaped_seek_previews),
                    description = stringResource(Res.string.settings_nuvio_reshaped_seek_previews_description),
                    icon = SettingsPage.PlaybackInterface.icon,
                    isTablet = isTablet,
                    onClick = { onPageClick(SettingsPage.PlaybackInterface) },
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.settings_nuvio_reshaped_pill_menu),
                    description = stringResource(Res.string.settings_nuvio_reshaped_pill_menu_description),
                    icon = SettingsPage.PlaybackInterface.icon,
                    isTablet = isTablet,
                    onClick = { onPageClick(SettingsPage.PlaybackInterface) },
                )
            }
        }
    }
    item(key = "nuvio_reshaped_streams") {
        SettingsSection(
            title = stringResource(Res.string.settings_nuvio_reshaped_section_streams),
            isTablet = isTablet,
        ) {
            SettingsGroup(isTablet = isTablet) {
                SettingsNavigationRow(
                    title = stringResource(Res.string.settings_nuvio_reshaped_streams),
                    description = stringResource(Res.string.settings_nuvio_reshaped_streams_description),
                    icon = SettingsPage.Streams.icon,
                    isTablet = isTablet,
                    onClick = { onPageClick(SettingsPage.Streams) },
                )
            }
        }
    }
}
