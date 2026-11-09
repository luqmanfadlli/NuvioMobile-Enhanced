package com.nuvio.app.features.settings

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.People
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.AppLoadingProfileAvatar
import com.nuvio.app.core.ui.accentBrush
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.core.ui.themePalette
import com.nuvio.app.features.experience.ExperienceMode
import com.nuvio.app.features.experience.ExperienceRepository
import com.nuvio.app.features.livetv.LiveTvRepository
import com.nuvio.app.features.sports.SportsRepository
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.Warning
import com.nuvio.app.features.profiles.ProfileRepository
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.runtime.Composable
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

internal fun LazyListScope.settingsRootContent(
    isTablet: Boolean,
    onPageClick: (SettingsPage) -> Unit,
    onSwitchProfileClick: (() -> Unit)? = null,
    onWatchPartyClick: (() -> Unit)? = null,
    category: SettingsCategory? = null,
) {
    // Fork: profile card at the top replaces upstream's "Switch profile" row.
    if (onSwitchProfileClick != null && (category == null || category == SettingsCategory.You)) {
        item(key = "profile_card") {
            SettingsProfileCard(isTablet = isTablet, onClick = onSwitchProfileClick)
        }
    }
    if (category == null || category == SettingsCategory.You) {
        item(key = "sports_dashboard") {
            val mode by ExperienceRepository.mode.collectAsStateWithLifecycle()
            if (mode == ExperienceMode.SportsFocus) SettingsSportsDashboard(isTablet = isTablet, onPageClick = onPageClick)
        }
        item(key = "experience_card") {
            val mode by ExperienceRepository.mode.collectAsStateWithLifecycle()
            SettingsGroup(
                isTablet = isTablet,
                modifier = Modifier.padding(top = if (isTablet) 12.dp else 8.dp),
            ) {
                SettingsNavigationRow(
                    title = stringResource(Res.string.settings_experience_title),
                    description = stringResource(mode.titleRes),
                    icon = SettingsPage.Experience.icon,
                    isTablet = isTablet,
                    onClick = { onPageClick(SettingsPage.Experience) },
                )
            }
        }
    }
    SettingsCategory.entries.filter { category == null || it == category }.forEach { group ->
        item(key = group.name) {
            SettingsGroup(isTablet = isTablet) {
                when (group) {
                    SettingsCategory.You -> {
                        SettingsPageRow(
                            page = SettingsPage.Account,
                            description = Res.string.compose_settings_root_account_description,
                            isTablet = isTablet,
                            onPageClick = onPageClick,
                        )
                        if (onWatchPartyClick != null) {
                            SettingsGroupDivider(isTablet = isTablet)
                            SettingsNavigationRow(
                                title = stringResource(Res.string.party_title),
                                description = stringResource(Res.string.party_settings_description),
                                icon = Icons.Rounded.Groups,
                                isTablet = isTablet,
                                onClick = onWatchPartyClick,
                            )
                        }
                    }
                    SettingsCategory.Look -> {
                        SettingsPageRow(
                            page = SettingsPage.Appearance,
                            description = Res.string.settings_appearance_description,
                            isTablet = isTablet,
                            onPageClick = onPageClick,
                        )
                        SettingsGroupDivider(isTablet = isTablet)
                        SettingsPageRow(
                            page = SettingsPage.Layout,
                            description = Res.string.compose_settings_root_layout_description,
                            isTablet = isTablet,
                            onPageClick = onPageClick,
                        )
                    }
                    SettingsCategory.Watch -> {
                        SettingsPageRow(
                            page = SettingsPage.ContentDiscovery,
                            description = Res.string.compose_settings_root_content_discovery_description,
                            isTablet = isTablet,
                            onPageClick = onPageClick,
                        )
                        SettingsGroupDivider(isTablet = isTablet)
                        SettingsPageRow(
                            page = SettingsPage.Playback,
                            description = Res.string.settings_playback_subtitle,
                            isTablet = isTablet,
                            onPageClick = onPageClick,
                        )
                    }
                    SettingsCategory.Services -> {
                        SettingsPageRow(
                            page = SettingsPage.Integrations,
                            description = Res.string.compose_settings_root_integrations_description,
                            isTablet = isTablet,
                            onPageClick = onPageClick,
                        )
                        SettingsGroupDivider(isTablet = isTablet)
                        SettingsPageRow(
                            page = SettingsPage.TraktAuthentication,
                            description = Res.string.compose_settings_root_tracking_description,
                            isTablet = isTablet,
                            onPageClick = onPageClick,
                        )
                    }
                    SettingsCategory.System -> {
                        SettingsPageRow(
                            page = SettingsPage.Notifications,
                            description = Res.string.compose_settings_root_notifications_description,
                            isTablet = isTablet,
                            onPageClick = onPageClick,
                        )
                        SettingsGroupDivider(isTablet = isTablet)
                        SettingsPageRow(
                            page = SettingsPage.Advanced,
                            description = Res.string.compose_settings_root_advanced_description,
                            isTablet = isTablet,
                            onPageClick = onPageClick,
                        )
                        SettingsGroupDivider(isTablet = isTablet)
                        SettingsPageRow(
                            page = SettingsPage.About,
                            description = Res.string.compose_settings_root_about_description,
                            isTablet = isTablet,
                            onPageClick = onPageClick,
                        )
                    }
                }
            }
        }
    }
    settingsFooterContent(isTablet = isTablet)
}

@Composable
private fun SettingsPageRow(
    page: SettingsPage,
    description: StringResource,
    isTablet: Boolean,
    onPageClick: (SettingsPage) -> Unit,
) {
    SettingsNavigationRow(
        title = stringResource(page.titleRes),
        description = stringResource(description),
        icon = page.icon,
        isTablet = isTablet,
        onClick = { onPageClick(page) },
    )
}

@Composable
private fun SettingsProfileCard(
    isTablet: Boolean,
    onClick: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val profileState by ProfileRepository.state.collectAsStateWithLifecycle()
    val profile = profileState.activeProfile
    val avatarSize = if (isTablet) 84.dp else 72.dp
    SettingsGroup(
        isTablet = isTablet,
        modifier = Modifier.padding(top = if (isTablet) 12.dp else 8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = if (isTablet) 20.dp else 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(avatarSize)
                    .border(2.5.dp, MaterialTheme.themePalette.accentBrush(), CircleShape)
                    .padding(4.dp),
            ) {
                if (profile != null) {
                    AppLoadingProfileAvatar(profile = profile, modifier = Modifier.fillMaxSize())
                } else {
                    Icon(
                        imageVector = Icons.Rounded.People,
                        contentDescription = null,
                        tint = tokens.colors.accent,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profile?.name?.takeIf { it.isNotBlank() }
                        ?: stringResource(Res.string.compose_settings_root_profile_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = tokens.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(Res.string.compose_settings_root_profile_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.colors.textMuted,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = tokens.colors.textMuted,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun SettingsSportsDashboard(isTablet: Boolean, onPageClick: (SettingsPage) -> Unit) {
    val tokens = MaterialTheme.nuvio
    val live by LiveTvRepository.uiState.collectAsStateWithLifecycle()
    val follows by SportsRepository.follows.collectAsStateWithLifecycle()
    val playlists = live.playlists.size + (if (live.stalkerSettings.isConfigured) 1 else 0) + (if (live.xtreamSettings.isConfigured) 1 else 0)
    val ready = live.hasPlaylist
    val teams = follows.teams.size
    val leagues = follows.leagues.size
    val guideLoaded = live.epgPrograms.isNotEmpty()
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = if (isTablet) 12.dp else 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(tokens.colors.surface).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                imageVector = if (ready) Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
                contentDescription = null,
                tint = if (ready) tokens.colors.success else tokens.colors.warning,
                modifier = Modifier.size(34.dp),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(if (ready) "Ready to watch" else "Set up Live TV", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = tokens.colors.textPrimary)
                Text(
                    listOf(
                        if (playlists == 1) "1 playlist" else "$playlists playlists",
                        if (teams == 1) "1 team followed" else "$teams teams followed",
                    ).joinToString(" \u00B7 "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.colors.textSecondary,
                )
                Text(if (guideLoaded) "Guide loaded" else "No guide data yet", style = MaterialTheme.typography.bodySmall, color = tokens.colors.textMuted)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DashboardTile(Icons.Rounded.VideoLibrary, "Playlists", if (playlists == 0) "None yet" else "$playlists connected", Modifier.weight(1f)) { onPageClick(SettingsPage.LiveTv) }
            DashboardTile(Icons.Rounded.Tv, "Guide", if (guideLoaded) "Loaded" else "Not loaded", Modifier.weight(1f)) { onPageClick(SettingsPage.LiveTv) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DashboardTile(Icons.Rounded.PlayArrow, "Playback", "Player & streams", Modifier.weight(1f)) { onPageClick(SettingsPage.Playback) }
            DashboardTile(
                Icons.Rounded.Notifications,
                "Notifications",
                if (leagues + teams == 0) "Nothing followed" else "$teams ${if (teams == 1) "team" else "teams"} \u00B7 $leagues ${if (leagues == 1) "league" else "leagues"}",
                Modifier.weight(1f),
            ) { onPageClick(SettingsPage.Notifications) }
        }
    }
}

@Composable
private fun DashboardTile(icon: ImageVector, title: String, subtitle: String, modifier: Modifier, onClick: () -> Unit) {
    val tokens = MaterialTheme.nuvio
    Column(
        modifier = modifier.clip(RoundedCornerShape(16.dp)).background(tokens.colors.surface).clickable(onClick = onClick).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tokens.colors.accent, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(4.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = tokens.colors.textPrimary, maxLines = 1)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = tokens.colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
