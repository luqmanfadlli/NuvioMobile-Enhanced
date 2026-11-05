package com.nuvio.app.features.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Theaters
import androidx.compose.foundation.lazy.LazyListScope
import com.nuvio.app.features.discord.DiscordRpcPlatform
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.compose_settings_page_debrid
import nuvio.composeapp.generated.resources.compose_settings_page_discord_rpc
import nuvio.composeapp.generated.resources.compose_settings_page_live_tv
import nuvio.composeapp.generated.resources.compose_settings_page_mdblist_ratings
import nuvio.composeapp.generated.resources.compose_settings_page_omdb
import nuvio.composeapp.generated.resources.compose_settings_page_tmdb_enrichment
import nuvio.composeapp.generated.resources.settings_integrations_debrid_description
import nuvio.composeapp.generated.resources.settings_integrations_discord_description
import nuvio.composeapp.generated.resources.settings_integrations_live_tv_description
import nuvio.composeapp.generated.resources.settings_integrations_mdblist_description
import nuvio.composeapp.generated.resources.settings_integrations_omdb_description
import nuvio.composeapp.generated.resources.settings_integrations_section_title
import nuvio.composeapp.generated.resources.settings_telegram_title
import nuvio.composeapp.generated.resources.settings_telegram_description
import nuvio.composeapp.generated.resources.settings_integrations_tmdb_description
import org.jetbrains.compose.resources.stringResource

internal fun LazyListScope.integrationsContent(
    isTablet: Boolean,
    onDebridClick: () -> Unit,
    onTmdbClick: () -> Unit,
    onMdbListClick: () -> Unit,
    onOmdbClick: () -> Unit,
    onLiveTvClick: () -> Unit,
    onDiscordClick: () -> Unit,
    onTelegramClick: (() -> Unit)? = null,
) {
    item {
        SettingsSection(
            title = stringResource(Res.string.settings_integrations_section_title),
            isTablet = isTablet,
        ) {
            SettingsGroup(isTablet = isTablet) {
                SettingsNavigationRow(
                    title = stringResource(Res.string.compose_settings_page_debrid),
                    description = stringResource(Res.string.settings_integrations_debrid_description),
                    icon = Icons.Rounded.CloudQueue,
                    isTablet = isTablet,
                    onClick = onDebridClick,
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.compose_settings_page_tmdb_enrichment),
                    description = stringResource(Res.string.settings_integrations_tmdb_description),
                    iconPainter = integrationLogoPainter(IntegrationLogo.Tmdb),
                    isTablet = isTablet,
                    onClick = onTmdbClick,
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.compose_settings_page_mdblist_ratings),
                    description = stringResource(Res.string.settings_integrations_mdblist_description),
                    iconPainter = integrationLogoPainter(IntegrationLogo.MdbList),
                    isTablet = isTablet,
                    onClick = onMdbListClick,
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.compose_settings_page_omdb),
                    description = stringResource(Res.string.settings_integrations_omdb_description),
                    icon = Icons.Rounded.Theaters,
                    isTablet = isTablet,
                    onClick = onOmdbClick,
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.compose_settings_page_live_tv),
                    description = stringResource(Res.string.settings_integrations_live_tv_description),
                    icon = Icons.Rounded.LiveTv,
                    isTablet = isTablet,
                    onClick = onLiveTvClick,
                )
                if (DiscordRpcPlatform.isSupported) {
                    SettingsGroupDivider(isTablet = isTablet)
                    SettingsNavigationRow(
                        title = stringResource(Res.string.compose_settings_page_discord_rpc),
                        description = stringResource(Res.string.settings_integrations_discord_description),
                        icon = Icons.Rounded.Forum,
                        isTablet = isTablet,
                        onClick = onDiscordClick,
                    )
                }
                if (onTelegramClick != null) {
                    SettingsGroupDivider(isTablet = isTablet)
                    SettingsNavigationRow(
                        title = stringResource(Res.string.settings_telegram_title),
                        description = stringResource(Res.string.settings_telegram_description),
                        isTablet = isTablet,
                        onClick = onTelegramClick,
                    )
                }
            }
        }
    }
}
