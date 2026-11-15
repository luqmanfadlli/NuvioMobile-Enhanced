package com.nuvio.app.features.settings

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.features.downloads.DownloadConcurrencyLimit
import com.nuvio.app.features.downloads.DownloadPreferredResolution
import com.nuvio.app.features.downloads.DownloadsSettingsRepository
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.compose_settings_root_downloads_title
import nuvio.composeapp.generated.resources.settings_downloads_location_private
import nuvio.composeapp.generated.resources.settings_downloads_location_reset
import nuvio.composeapp.generated.resources.downloads_allow_mobile_data_description
import nuvio.composeapp.generated.resources.downloads_allow_mobile_data_title
import nuvio.composeapp.generated.resources.downloads_max_concurrent_shows_title
import nuvio.composeapp.generated.resources.downloads_preferred_resolution_title
import nuvio.composeapp.generated.resources.downloads_show_download_button_description
import nuvio.composeapp.generated.resources.downloads_show_download_button_title
import nuvio.composeapp.generated.resources.settings_downloads_location_title
import nuvio.composeapp.generated.resources.downloads_max_concurrent_title
import nuvio.composeapp.generated.resources.downloads_max_series_episode_concurrent_title
import org.jetbrains.compose.resources.pluralStringResource
import nuvio.composeapp.generated.resources.downloads_count
import nuvio.composeapp.generated.resources.downloads_episodes_count
import nuvio.composeapp.generated.resources.downloads_shows_count
import org.jetbrains.compose.resources.stringResource

internal fun LazyListScope.downloadsSettingsContent(
    isTablet: Boolean,
) {
    item {
        val downloadLocationUri by DownloadsSettingsRepository.downloadLocationUri.collectAsStateWithLifecycle()
        val allowMobileData by DownloadsSettingsRepository.allowMobileDataDownloads.collectAsStateWithLifecycle()
        val showDownloadButton by DownloadsSettingsRepository.showDownloadButton.collectAsStateWithLifecycle()
        val preferredResolution by DownloadsSettingsRepository.preferredResolution.collectAsStateWithLifecycle()
        val concurrencyLimits by DownloadsSettingsRepository.concurrencyLimits.collectAsStateWithLifecycle()
        var showPicker by remember { mutableStateOf(false) }
        var showResolutionDialog by remember { mutableStateOf(false) }

        SettingsSection(
            title = stringResource(Res.string.compose_settings_root_downloads_title),
            isTablet = isTablet,
        ) {
            SettingsGroup(isTablet = isTablet) {
                SettingsNavigationRow(
                    title = stringResource(Res.string.downloads_preferred_resolution_title),
                    description = stringResource(preferredResolution.label),
                    isTablet = isTablet,
                    onClick = { showResolutionDialog = true },
                )
                SettingsGroupDivider(isTablet = isTablet)
                listOf(
                    DownloadConcurrencyLimit.TotalDownloads,
                    DownloadConcurrencyLimit.ConcurrentShows,
                    DownloadConcurrencyLimit.EpisodesPerShow,
                ).forEach { limit ->
                    ConcurrencyLimitRow(
                        limit = limit,
                        value = concurrencyLimits.getValue(limit),
                        isTablet = isTablet,
                    )
                    SettingsGroupDivider(isTablet = isTablet)
                }
                SettingsSwitchRow(
                    title = stringResource(Res.string.downloads_allow_mobile_data_title),
                    description = stringResource(Res.string.downloads_allow_mobile_data_description),
                    checked = allowMobileData,
                    isTablet = isTablet,
                    onCheckedChange = DownloadsSettingsRepository::setAllowMobileDataDownloads,
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsSwitchRow(
                    title = stringResource(Res.string.downloads_show_download_button_title),
                    description = stringResource(Res.string.downloads_show_download_button_description),
                    checked = showDownloadButton,
                    isTablet = isTablet,
                    onCheckedChange = DownloadsSettingsRepository::setShowDownloadButton,
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.settings_downloads_location_title),
                    description = downloadLocationUri?.let { formatUriForDisplay(it) } ?: stringResource(Res.string.settings_downloads_location_private),
                    isTablet = isTablet,
                    onClick = { showPicker = true },
                )
                if (downloadLocationUri != null) {
                    SettingsGroupDivider(isTablet = isTablet)
                    SettingsClickableRow(
                        title = stringResource(Res.string.settings_downloads_location_reset),
                        isTablet = isTablet,
                        onClick = { DownloadsSettingsRepository.setDownloadLocationUri(null) },
                    )
                }
            }
        }

        if (showResolutionDialog) {
            SettingsChoiceDialog(
                title = stringResource(Res.string.downloads_preferred_resolution_title),
                options = DownloadPreferredResolution.entries,
                selected = preferredResolution,
                label = { stringResource(it.label) },
                onSelect = {
                    DownloadsSettingsRepository.setPreferredResolution(it)
                    showResolutionDialog = false
                },
                onDismiss = { showResolutionDialog = false },
            )
        }

        if (showPicker) {
            DownloadLocationPicker(
                onLocationSelected = { uri ->
                    DownloadsSettingsRepository.setDownloadLocationUri(uri)
                    showPicker = false
                },
                onDismiss = { showPicker = false },
            )
        }
    }
}

@Composable
internal expect fun DownloadLocationPicker(
    onLocationSelected: (uri: String) -> Unit,
    onDismiss: () -> Unit,
)

internal expect fun formatUriForDisplay(uri: String): String

/** A settings row for a download concurrency limit, with its own picker dialog. */
@Composable
private fun ConcurrencyLimitRow(
    limit: DownloadConcurrencyLimit,
    value: Int,
    isTablet: Boolean,
) {
    val (titleRes, countLabel) = when (limit) {
        DownloadConcurrencyLimit.TotalDownloads ->
            Res.string.downloads_max_concurrent_title to Res.plurals.downloads_count
        DownloadConcurrencyLimit.ConcurrentShows ->
            Res.string.downloads_max_concurrent_shows_title to Res.plurals.downloads_shows_count
        DownloadConcurrencyLimit.EpisodesPerShow ->
            Res.string.downloads_max_series_episode_concurrent_title to Res.plurals.downloads_episodes_count
    }
    val title = stringResource(titleRes)
    var showDialog by remember { mutableStateOf(false) }
    val formatCount: @Composable (Int) -> String = { count ->
        pluralStringResource(countLabel, count, count)
    }

    SettingsNavigationRow(
        title = title,
        description = formatCount(value),
        isTablet = isTablet,
        onClick = { showDialog = true },
    )

    if (showDialog) {
        SettingsChoiceDialog(
            title = title,
            options = (1..DownloadConcurrencyLimit.MAX_VALUE).toList(),
            selected = value,
            label = formatCount,
            onSelect = {
                DownloadsSettingsRepository.setConcurrencyLimit(limit, it)
                showDialog = false
            },
            onDismiss = { showDialog = false },
        )
    }
}
