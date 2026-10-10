package com.nuvio.app.features.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.format.formatLocalDateTime
import com.nuvio.app.features.anilist.AniListConfig
import com.nuvio.app.features.anilist.AniListError
import com.nuvio.app.features.anilist.AniListTracker
import com.nuvio.app.features.profiles.ProfileRepository
import com.nuvio.app.features.tracking.TrackingProviderId
import com.nuvio.app.features.tracking.TrackingRefreshIntent
import com.nuvio.app.features.watchprogress.WatchProgressSourceCoordinator
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun AniListProviderCard(modifier: Modifier = Modifier) {
    remember { AniListTracker.ensureLoaded() }
    val auth by AniListTracker.authState.collectAsStateWithLifecycle()
    val sync by AniListTracker.syncState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val error = auth.error ?: sync.error
    val syncedAt = sync.syncedAtEpochMs
    TrackingProviderCard(
        brand = TrackingBrand.ANILIST,
        mode = when {
            auth.isAuthenticated -> TrackingConnectionCardMode.CONNECTED
            auth.isAwaitingAuthorization -> TrackingConnectionCardMode.AWAITING_APPROVAL
            else -> TrackingConnectionCardMode.DISCONNECTED
        },
        credentialsConfigured = AniListConfig.CLIENT_ID.isNotBlank(),
        isLoading = auth.isWorking,
        connectedLabel = stringResource(
            Res.string.settings_anilist_connected_as,
            auth.userName ?: stringResource(Res.string.settings_anilist_account_fallback),
        ),
        connectedDescription = stringResource(Res.string.settings_anilist_connected_description),
        signInDescription = stringResource(Res.string.settings_anilist_sign_in_description),
        finishSignInLabel = stringResource(Res.string.settings_anilist_finish_sign_in),
        approvalDescription = stringResource(Res.string.settings_anilist_approval_description),
        connectLabel = stringResource(Res.string.settings_anilist_connect),
        openLoginLabel = stringResource(Res.string.settings_anilist_open_login),
        disconnectLabel = stringResource(Res.string.settings_anilist_disconnect),
        missingCredentialsMessage = stringResource(Res.string.settings_anilist_missing_credentials),
        syncLabel = stringResource(Res.string.settings_anilist_sync_now),
        isSyncing = sync.isLoading,
        statusMessage = when {
            sync.isLoading -> stringResource(Res.string.settings_anilist_syncing)
            auth.isAuthenticated && syncedAt != null ->
                stringResource(Res.string.settings_anilist_last_synced, formatLocalDateTime(syncedAt))
            else -> null
        },
        errorMessage = aniListErrorMessage(error),
        websiteLabel = stringResource(Res.string.settings_anilist_visit),
        websiteUrl = AniListConfig.WEBSITE_URL,
        onConnectRequested = { AniListTracker.beginAuthorization() },
        onResumeAuthorization = { AniListTracker.beginAuthorization() },
        onCancelAuthorization = { AniListTracker.cancelAuthorization() },
        onDisconnect = { AniListTracker.disconnect() },
        onSyncRequested = {
            scope.launch {
                WatchProgressSourceCoordinator.refreshProviderAndActiveSource(
                    profileId = ProfileRepository.activeProfileId,
                    providerId = TrackingProviderId.ANILIST,
                    refreshProvider = {
                        AniListTracker.refresh(TrackingRefreshIntent.USER_INITIATED)
                        AniListTracker.syncState.value.let { it.hasLoaded && it.error == null }
                    },
                )
            }
        },
        modifier = modifier,
    )
}

@Composable
private fun aniListErrorMessage(error: AniListError?): String? = when (error) {
    null, AniListError.MISSING_CLIENT_ID -> null
    AniListError.INVALID_CALLBACK -> stringResource(Res.string.settings_anilist_invalid_callback)
    AniListError.AUTHORIZATION_EXPIRED -> stringResource(Res.string.settings_anilist_authorization_expired)
    AniListError.SIGN_IN_FAILED -> stringResource(Res.string.settings_anilist_sign_in_failed)
    AniListError.SYNC_FAILED -> stringResource(Res.string.settings_anilist_sync_failed)
    AniListError.RATE_LIMITED -> stringResource(Res.string.settings_anilist_rate_limited)
}
