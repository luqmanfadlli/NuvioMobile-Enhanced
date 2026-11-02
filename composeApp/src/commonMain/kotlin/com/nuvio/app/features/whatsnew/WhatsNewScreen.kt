package com.nuvio.app.features.whatsnew

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.format.formatReleaseDateForDisplay
import com.nuvio.app.core.build.AppVersionConfig
import com.nuvio.app.core.ui.NuvioLoadingIndicator
import com.nuvio.app.core.ui.NuvioPrimaryButton
import com.nuvio.app.core.ui.NuvioSectionLabel
import com.nuvio.app.core.ui.NuvioScreen
import com.nuvio.app.core.ui.NuvioScreenHeader
import com.nuvio.app.features.settings.SettingsGlassPageBackground
import com.nuvio.app.features.settings.SettingsSurfaceCard
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.nuvio
import kotlin.time.Clock
import com.nuvio.app.features.updater.AppUpdaterPlatform
import com.nuvio.app.features.updater.UpdateChannel
import com.nuvio.app.features.updater.UpdatePreferences
import com.nuvio.app.features.updater.VersionUtils
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_collapse
import nuvio.composeapp.generated.resources.action_expand
import nuvio.composeapp.generated.resources.compose_settings_page_whats_new
import nuvio.composeapp.generated.resources.updates_channel_beta
import nuvio.composeapp.generated.resources.updates_channel_stable
import nuvio.composeapp.generated.resources.whats_new_category_features
import nuvio.composeapp.generated.resources.whats_new_category_fixes
import nuvio.composeapp.generated.resources.whats_new_category_localization
import nuvio.composeapp.generated.resources.whats_new_category_other
import nuvio.composeapp.generated.resources.whats_new_category_performance
import nuvio.composeapp.generated.resources.whats_new_channel_format
import nuvio.composeapp.generated.resources.whats_new_hide_changes
import nuvio.composeapp.generated.resources.whats_new_last_checked_days
import nuvio.composeapp.generated.resources.whats_new_last_checked_day
import nuvio.composeapp.generated.resources.whats_new_last_checked_hour
import nuvio.composeapp.generated.resources.whats_new_last_checked_minute
import nuvio.composeapp.generated.resources.whats_new_last_checked_hours
import nuvio.composeapp.generated.resources.whats_new_last_checked_just_now
import nuvio.composeapp.generated.resources.whats_new_last_checked_minutes
import nuvio.composeapp.generated.resources.whats_new_last_checked_week
import nuvio.composeapp.generated.resources.whats_new_last_checked_weeks
import nuvio.composeapp.generated.resources.whats_new_last_checked_month
import nuvio.composeapp.generated.resources.whats_new_last_checked_months
import nuvio.composeapp.generated.resources.whats_new_last_checked_year
import nuvio.composeapp.generated.resources.whats_new_last_checked_years
import nuvio.composeapp.generated.resources.whats_new_latest_release
import nuvio.composeapp.generated.resources.whats_new_load_failed
import nuvio.composeapp.generated.resources.whats_new_no_release_notes
import nuvio.composeapp.generated.resources.whats_new_contributor_credit
import nuvio.composeapp.generated.resources.whats_new_no_releases
import nuvio.composeapp.generated.resources.whats_new_open_github
import nuvio.composeapp.generated.resources.whats_new_refresh
import nuvio.composeapp.generated.resources.whats_new_release_format
import nuvio.composeapp.generated.resources.whats_new_show_all_changes
import nuvio.composeapp.generated.resources.whats_new_status_current_ahead
import nuvio.composeapp.generated.resources.whats_new_status_current_ahead_all
import nuvio.composeapp.generated.resources.whats_new_status_newer_releases_available
import nuvio.composeapp.generated.resources.whats_new_status_update_available
import nuvio.composeapp.generated.resources.whats_new_status_unknown
import nuvio.composeapp.generated.resources.whats_new_status_updates_available
import nuvio.composeapp.generated.resources.whats_new_status_up_to_date
import nuvio.composeapp.generated.resources.whats_new_status_up_to_date_all
import nuvio.composeapp.generated.resources.whats_new_unavailable
import nuvio.composeapp.generated.resources.whats_new_refresh_failed_cached
import nuvio.composeapp.generated.resources.whats_new_since_one_release
import nuvio.composeapp.generated.resources.whats_new_since_releases
import nuvio.composeapp.generated.resources.whats_new_since_unavailable
import nuvio.composeapp.generated.resources.whats_new_recent_releases
import org.jetbrains.compose.resources.stringResource

private const val PREVIEW_NOTE_CHAR_LIMIT = 420
private const val PREVIEW_MIN_NOTES = 2
private const val PREVIEW_MAX_NOTES = 6
private val NOTE_CATEGORY_ORDER = listOf(
    WhatsNewNoteCategory.FEATURES,
    WhatsNewNoteCategory.FIXES,
    WhatsNewNoteCategory.PERFORMANCE,
    WhatsNewNoteCategory.LOCALIZATION,
    WhatsNewNoteCategory.OTHER,
)

@Composable
fun WhatsNewSettingsScreen(onBack: () -> Unit) {
    val releaseChannelsEnabled = AppUpdaterPlatform.isSupported
    val selectedChannel by UpdatePreferences.shared.channel.collectAsStateWithLifecycle()
    val channel = if (releaseChannelsEnabled) selectedChannel else UpdateChannel.BETA
    var state by remember { mutableStateOf<LoadState>(LoadState.Loading) }
    var refreshKey by rememberSaveable { mutableIntStateOf(0) }
    var lastHandledRefreshKey by rememberSaveable { mutableIntStateOf(0) }
    var isRefreshing by remember { mutableStateOf(false) }
    var loadedChannel by remember { mutableStateOf<UpdateChannel?>(null) }

    LaunchedEffect(channel, refreshKey) {
        val forceRefresh = refreshKey != 0 && refreshKey != lastHandledRefreshKey
        lastHandledRefreshKey = refreshKey
        val preserveContent = state is LoadState.Content && loadedChannel == channel
        if (!preserveContent) state = LoadState.Loading
        isRefreshing = true
        try {
            val result = WhatsNewRepository.load(
                channel = channel,
                currentVersion = AppVersionConfig.VERSION_NAME,
                forceRefresh = forceRefresh,
                matchBaseVersion = !releaseChannelsEnabled,
            )
            state = result.fold(
                onSuccess = LoadState::Content,
                onFailure = { LoadState.Error },
            )
            loadedChannel = channel
        } finally {
            isRefreshing = false
        }
    }

    val channelLabel = stringResource(
        when (channel) {
            UpdateChannel.STABLE -> Res.string.updates_channel_stable
            UpdateChannel.BETA -> Res.string.updates_channel_beta
        },
    )
    val buildChannel = if (
        !releaseChannelsEnabled || VersionUtils.isPrerelease(AppVersionConfig.VERSION_NAME)
    ) {
        UpdateChannel.BETA
    } else {
        UpdateChannel.STABLE
    }
    val buildChannelLabel = if (releaseChannelsEnabled) {
        stringResource(
            when (buildChannel) {
                UpdateChannel.STABLE -> Res.string.updates_channel_stable
                UpdateChannel.BETA -> Res.string.updates_channel_beta
            },
        )
    } else {
        null
    }

    SettingsGlassPageBackground {
        NuvioScreen {
        stickyHeader {
            NuvioScreenHeader(
                title = stringResource(Res.string.compose_settings_page_whats_new),
                onBack = onBack,
                useSettingsGlass = true,
                actions = {
                    if (isRefreshing) {
                        Box(
                            modifier = Modifier.size(48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(NuvioTokens.Icon.md),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.nuvio.colors.textPrimary,
                            )
                        }
                    } else {
                        IconButton(onClick = { refreshKey++ }) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = stringResource(Res.string.whats_new_refresh),
                                tint = MaterialTheme.nuvio.colors.textPrimary,
                            )
                        }
                    }
                },
            )
        }

        when (val value = state) {
            LoadState.Loading -> item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    NuvioLoadingIndicator()
                }
            }

            LoadState.Error -> item {
                SettingsSurfaceCard(isTablet = false) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s10),
                    ) {
                        Text(
                            text = stringResource(Res.string.whats_new_unavailable),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.nuvio.colors.textPrimary,
                        )
                        Text(
                            text = stringResource(Res.string.whats_new_load_failed),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.nuvio.colors.textMuted,
                        )
                        NuvioPrimaryButton(
                            text = stringResource(Res.string.whats_new_refresh),
                            onClick = { refreshKey++ },
                        )
                    }
                }
            }

            is LoadState.Content -> {
                val snapshot = value.content.snapshot

                item {
                    CurrentVersionCard(
                        version = snapshot.currentVersion,
                        buildChannelLabel = buildChannelLabel,
                        viewingChannelLabel = channelLabel,
                        showViewingChannel = releaseChannelsEnabled && channel != buildChannel,
                        lastCheckedLabel = lastCheckedLabel(value.content.fetchedAtMillis),
                        status = versionStatus(
                    snapshot = snapshot,
                    channelLabel = channelLabel.takeIf { releaseChannelsEnabled },
                    buildChannel = buildChannel,
                ),
                    )
                }

                if (!snapshot.hasCompleteSinceVersion &&
                    snapshot.releases.any {
                        VersionUtils.isRemoteNewer(it.version, snapshot.currentVersion)
                    }
                ) {
                    item {
                        Text(
                            text = stringResource(Res.string.whats_new_since_unavailable),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.nuvio.colors.textMuted,
                            modifier = Modifier.padding(horizontal = NuvioTokens.Space.s4),
                        )
                    }
                }

                if (value.content.isStale && !isRefreshing) {
                    item {
                        Text(
                            text = stringResource(Res.string.whats_new_refresh_failed_cached),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.nuvio.colors.textMuted,
                            modifier = Modifier.padding(horizontal = NuvioTokens.Space.s4),
                        )
                    }
                }

                val sinceReleases = snapshot.sinceYourVersion.takeIf {
                    snapshot.hasCompleteSinceVersion && it.isNotEmpty()
                }.orEmpty()
                val sinceVersions = sinceReleases.mapTo(mutableSetOf(), WhatsNewRelease::version)

                if (sinceReleases.isNotEmpty()) {
                    item {
                        NuvioSectionLabel(
                            text = if (sinceReleases.size == 1) {
                                stringResource(Res.string.whats_new_since_one_release)
                            } else {
                                stringResource(
                                    Res.string.whats_new_since_releases,
                                    sinceReleases.size,
                                )
                            },
                        )
                    }
                    item(key = "latest:" + sinceReleases.first().version) {
                        ReleaseCard(
                            release = sinceReleases.first(),
                            showLatestLabel = false,
                        )
                    }
                    if (sinceReleases.size > 1) {
                        items(
                            items = sinceReleases.drop(1),
                            key = { release -> "since:" + release.version },
                        ) { release ->
                            ReleaseHistoryRow(
                                release = release,
                            )
                        }
                    }
                } else {
                    snapshot.releases.firstOrNull()?.let { latest ->
                        item(key = "latest:" + latest.version) {
                            ReleaseCard(
                                release = latest,
                            )
                        }
                    }
                }

                val earlierReleases = snapshot.releases
                    .drop(1)
                    .filterNot { it.version in sinceVersions }

                if (earlierReleases.isNotEmpty()) {
                    item {
                        NuvioSectionLabel(
                            text = stringResource(Res.string.whats_new_recent_releases),
                        )
                    }
                    items(
                        items = earlierReleases,
                        key = { release -> release.version },
                    ) { release ->
                        ReleaseHistoryRow(
                            release = release,
                        )
                    }
                }

                if (snapshot.releases.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(Res.string.whats_new_no_releases),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.nuvio.colors.textMuted,
                        )
                    }
                }
            }
        }

        item(key = "whats-new-bottom-spacer") {
            Spacer(modifier = Modifier.size(80.dp))
        }
    }
    }
}

private sealed interface LoadState {
    data object Loading : LoadState
    data object Error : LoadState
    data class Content(val content: WhatsNewContent) : LoadState
}

@Composable
private fun CurrentVersionCard(
    version: String,
    buildChannelLabel: String?,
    viewingChannelLabel: String,
    showViewingChannel: Boolean,
    lastCheckedLabel: String,
    status: String,
) {
    SettingsSurfaceCard(isTablet = false) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8),
            ) {
                Icon(
                    imageVector = Icons.Rounded.NewReleases,
                    contentDescription = null,
                    modifier = Modifier.size(NuvioTokens.Icon.md),
                    tint = MaterialTheme.nuvio.colors.accent,
                )
                Text(
                    text = stringResource(Res.string.whats_new_release_format, version),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.nuvio.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (buildChannelLabel != null) {
                    Text(
                        text = buildChannelLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.nuvio.colors.textSecondary,
                    )
                }
            }
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s2),
            ) {
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.nuvio.colors.textSecondary,
                )
                if (showViewingChannel) {
                    Text(
                        text = stringResource(
                            Res.string.whats_new_channel_format,
                            viewingChannelLabel,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.nuvio.colors.textMuted,
                    )
                }
                Text(
                    text = lastCheckedLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.nuvio.colors.textMuted,
                )
            }
        }
    }
}

@Composable
private fun versionStatus(
    snapshot: WhatsNewSnapshot,
    channelLabel: String?,
    buildChannel: UpdateChannel,
): String {
    val latest = snapshot.releases.firstOrNull()
        ?: return stringResource(Res.string.whats_new_status_unknown)

    val latestIsNewer = VersionUtils.isRemoteNewer(latest.version, snapshot.currentVersion)
    val currentIsNewer = VersionUtils.isRemoteNewer(snapshot.currentVersion, latest.version)
    val betaBuildMatchesDisplayedRelease =
        buildChannel == UpdateChannel.BETA &&
            VersionUtils.isPrerelease(latest.version) &&
            VersionUtils.isSameBaseVersion(latest.version, snapshot.currentVersion)

    return when {
        betaBuildMatchesDisplayedRelease -> if (channelLabel != null) {
            stringResource(Res.string.whats_new_status_up_to_date, channelLabel)
        } else {
            stringResource(Res.string.whats_new_status_up_to_date_all)
        }
        latestIsNewer && snapshot.hasCompleteSinceVersion -> {
            val count = snapshot.sinceYourVersion.size.coerceAtLeast(1)
            if (count == 1) {
                stringResource(Res.string.whats_new_status_update_available)
            } else {
                stringResource(Res.string.whats_new_status_updates_available, count)
            }
        }
        latestIsNewer -> stringResource(Res.string.whats_new_status_newer_releases_available)
        currentIsNewer -> if (channelLabel != null) {
            stringResource(Res.string.whats_new_status_current_ahead, channelLabel)
        } else {
            stringResource(Res.string.whats_new_status_current_ahead_all)
        }
        VersionUtils.parse(snapshot.currentVersion) != null &&
            VersionUtils.parse(latest.version) != null -> if (channelLabel != null) {
                stringResource(Res.string.whats_new_status_up_to_date, channelLabel)
            } else {
                stringResource(Res.string.whats_new_status_up_to_date_all)
            }
        else -> stringResource(Res.string.whats_new_status_unknown)
    }
}

@Composable
private fun lastCheckedLabel(fetchedAtMillis: Long): String {
    val age = calculateLastCheckedAge(
        fetchedAtMillis = fetchedAtMillis,
        nowMillis = Clock.System.now().toEpochMilliseconds(),
    )

    return when (age.unit) {
        LastCheckedUnit.JUST_NOW ->
            stringResource(Res.string.whats_new_last_checked_just_now)
        LastCheckedUnit.MINUTE ->
            stringResource(
                if (age.amount == 1L) {
                    Res.string.whats_new_last_checked_minute
                } else {
                    Res.string.whats_new_last_checked_minutes
                },
                age.amount,
            )
        LastCheckedUnit.HOUR ->
            stringResource(
                if (age.amount == 1L) {
                    Res.string.whats_new_last_checked_hour
                } else {
                    Res.string.whats_new_last_checked_hours
                },
                age.amount,
            )
        LastCheckedUnit.DAY ->
            stringResource(
                if (age.amount == 1L) {
                    Res.string.whats_new_last_checked_day
                } else {
                    Res.string.whats_new_last_checked_days
                },
                age.amount,
            )
        LastCheckedUnit.WEEK ->
            stringResource(
                if (age.amount == 1L) {
                    Res.string.whats_new_last_checked_week
                } else {
                    Res.string.whats_new_last_checked_weeks
                },
                age.amount,
            )
        LastCheckedUnit.MONTH ->
            stringResource(
                if (age.amount == 1L) {
                    Res.string.whats_new_last_checked_month
                } else {
                    Res.string.whats_new_last_checked_months
                },
                age.amount,
            )
        LastCheckedUnit.YEAR ->
            stringResource(
                if (age.amount == 1L) {
                    Res.string.whats_new_last_checked_year
                } else {
                    Res.string.whats_new_last_checked_years
                },
                age.amount,
            )
    }
}
@Composable
private fun ReleaseCard(
    release: WhatsNewRelease,
    showLatestLabel: Boolean = true,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable(release.version) { mutableStateOf(false) }
    val previewNoteCount = previewNotes(release.notes).size

    SettingsSurfaceCard(
        isTablet = false,
        modifier = modifier.animateContentSize(
            animationSpec = tween(
                durationMillis = NuvioTokens.Motion.normalMillis,
                easing = NuvioTokens.Motion.standard,
            ),
        ),
        tonalElevation = 1,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s10),
        ) {
            if (showLatestLabel) {
                NuvioSectionLabel(
                    text = stringResource(Res.string.whats_new_latest_release),
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8),
            ) {
                Icon(
                    imageVector = Icons.Rounded.NewReleases,
                    contentDescription = null,
                    modifier = Modifier.size(NuvioTokens.Icon.md),
                    tint = MaterialTheme.nuvio.colors.accent,
                )
                Text(
                    text = release.version,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.nuvio.colors.textPrimary,
                )
            }
            release.title.takeUnless { it == release.version }?.let { title ->
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.nuvio.colors.textPrimary,
                )
            }
            releaseDate(release)?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.nuvio.colors.textMuted,
                )
            }
            ReleaseNotes(
                release = release,
                expanded = expanded,
                onToggle = { expanded = !expanded },
                allowToggle = release.notes.size > previewNoteCount,
                showGithub = true,
            )
        }
    }
}

@Composable
private fun ReleaseHistoryRow(
    release: WhatsNewRelease,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable(release.version) { mutableStateOf(false) }
    val expansionLabel = stringResource(
        if (expanded) Res.string.action_collapse else Res.string.action_expand,
    )
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = tween(
            durationMillis = NuvioTokens.Motion.fastMillis,
            easing = NuvioTokens.Motion.standard,
        ),
        label = "release_chevron_rotation",
    )
    SettingsSurfaceCard(
        isTablet = false,
        modifier = modifier.animateContentSize(
            animationSpec = tween(
                durationMillis = NuvioTokens.Motion.normalMillis,
                easing = NuvioTokens.Motion.standard,
            ),
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s10),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClickLabel = expansionLabel) { expanded = !expanded }
                    .semantics {
                        role = Role.Button
                        stateDescription = expansionLabel
                        if (expanded) {
                            collapse(label = expansionLabel) {
                                expanded = false
                                true
                            }
                        } else {
                            expand(label = expansionLabel) {
                                expanded = true
                                true
                            }
                        }
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s12),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = release.version,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.nuvio.colors.textPrimary,
                    )
                    release.title.takeUnless { it == release.version }?.let { title ->
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.nuvio.colors.textMuted,
                        )
                    }
                    releaseDate(release)?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.nuvio.colors.textMuted,
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier
                        .size(NuvioTokens.Icon.md)
                        .rotate(chevronRotation),
                    tint = MaterialTheme.nuvio.colors.textMuted,
                )
            }
            if (expanded) {
                ReleaseNotes(
                    release = release,
                    expanded = true,
                    onToggle = {},
                    allowToggle = false,
                    showGithub = true,
                )
            }
        }
    }
}

@Composable
private fun ReleaseNotes(
    release: WhatsNewRelease,
    expanded: Boolean,
    onToggle: () -> Unit,
    allowToggle: Boolean,
    showGithub: Boolean,
) {
    val tokens = MaterialTheme.nuvio
    val visibleNotes = if (expanded) release.notes else previewNotes(release.notes)
    val contributors = release.notes
        .mapNotNull { it.authorLogin?.takeIf(String::isNotBlank) }
        .distinct()
    val showContributors = contributors.isNotEmpty() && (expanded || !allowToggle)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8),
    ) {
        if (visibleNotes.isEmpty()) {
            Text(
                text = stringResource(Res.string.whats_new_no_release_notes),
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.colors.textPrimary,
            )
        } else {
            NOTE_CATEGORY_ORDER.forEach { category ->
                val notes = visibleNotes.filter { it.category == category }
                if (notes.isEmpty()) return@forEach

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s2),
                ) {
                    Text(
                        text = stringResource(noteCategoryResource(category)),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = tokens.colors.textMuted,
                    )
                    notes.forEach { note ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8),
                        ) {
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.bodyMedium,
                                color = tokens.colors.textPrimary,
                            )
                            Text(
                                text = WhatsNewSnapshotBuilder.formatReleaseNoteForDisplay(note.text),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                                color = tokens.colors.textPrimary,
                            )
                        }
                    }
                    }
                }
            }
        }

        if (showContributors) {
            Text(
                text = stringResource(
                    Res.string.whats_new_contributor_credit,
                    contributors.joinToString(" · @"),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = tokens.colors.textSecondary,
            )
        }

        val releaseUrl = release.releaseUrl?.takeIf { showGithub }
        if (allowToggle || releaseUrl != null) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                if (maxWidth < 360.dp) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.End,
                    ) {
                        if (allowToggle) {
                            TextButton(
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = tokens.colors.textPrimary,
                                ),
                                onClick = onToggle,
                            ) {
                                Text(
                                    text = if (expanded) {
                                        stringResource(Res.string.whats_new_hide_changes)
                                    } else {
                                        stringResource(
                                            Res.string.whats_new_show_all_changes,
                                            release.notes.size,
                                        )
                                    },
                                )
                            }
                        }
                        releaseUrl?.let { url ->
                            val uriHandler = LocalUriHandler.current
                            TextButton(
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = tokens.colors.textSecondary,
                                ),
                                onClick = { uriHandler.openUri(url) },
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.OpenInNew,
                                    contentDescription = null,
                                    modifier = Modifier.size(NuvioTokens.Icon.sm),
                                )
                                Spacer(modifier = Modifier.size(NuvioTokens.Space.s4))
                                Text(stringResource(Res.string.whats_new_open_github))
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (allowToggle) {
                            TextButton(
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = tokens.colors.textPrimary,
                                ),
                                onClick = onToggle,
                            ) {
                                Text(
                                    text = if (expanded) {
                                        stringResource(Res.string.whats_new_hide_changes)
                                    } else {
                                        stringResource(
                                            Res.string.whats_new_show_all_changes,
                                            release.notes.size,
                                        )
                                    },
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }

                        releaseUrl?.let { url ->
                            val uriHandler = LocalUriHandler.current
                            TextButton(
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = tokens.colors.textSecondary,
                                ),
                                onClick = { uriHandler.openUri(url) },
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.OpenInNew,
                                    contentDescription = null,
                                    modifier = Modifier.size(NuvioTokens.Icon.sm),
                                )
                                Spacer(modifier = Modifier.size(NuvioTokens.Space.s4))
                                Text(stringResource(Res.string.whats_new_open_github))
                            }
                        }
                    }
                }
            }
        }
    }

private fun previewNotes(notes: List<WhatsNewNote>): List<WhatsNewNote> {
    if (notes.size <= PREVIEW_MIN_NOTES) return notes

    val preview = ArrayList<WhatsNewNote>(minOf(notes.size, PREVIEW_MAX_NOTES))
    var visibleCharacters = 0

    for (note in notes) {
        if (preview.size >= PREVIEW_MAX_NOTES) break

        val noteCharacters = note.text.trim().length
        if (preview.size >= PREVIEW_MIN_NOTES &&
            visibleCharacters + noteCharacters > PREVIEW_NOTE_CHAR_LIMIT
        ) {
            break
        }

        preview += note
        visibleCharacters += noteCharacters
    }

    return preview
}

private fun noteCategoryResource(category: WhatsNewNoteCategory) = when (category) {
    WhatsNewNoteCategory.FEATURES -> Res.string.whats_new_category_features
    WhatsNewNoteCategory.FIXES -> Res.string.whats_new_category_fixes
    WhatsNewNoteCategory.PERFORMANCE -> Res.string.whats_new_category_performance
    WhatsNewNoteCategory.LOCALIZATION -> Res.string.whats_new_category_localization
    WhatsNewNoteCategory.OTHER -> Res.string.whats_new_category_other
}

private fun releaseDate(release: WhatsNewRelease): String? =
    release.publishedAt
        ?.takeIf { it.isNotBlank() }
        ?.let(::formatReleaseDateForDisplay)