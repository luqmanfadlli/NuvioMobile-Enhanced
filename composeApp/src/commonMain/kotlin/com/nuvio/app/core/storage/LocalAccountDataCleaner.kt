package com.nuvio.app.core.storage

import com.nuvio.app.core.auth.AuthRepository
import com.nuvio.app.core.auth.AuthState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

import com.nuvio.app.core.build.AppFeaturePolicy
import com.nuvio.app.core.sync.SyncManager
import com.nuvio.app.core.sync.ProfileSettingsSync
import com.nuvio.app.core.tracking.ensureTrackingProvidersRegistered
import com.nuvio.app.features.addons.AddonRepository
import com.nuvio.app.features.catalog.CatalogRepository
import com.nuvio.app.features.collection.CollectionMobileSettingsRepository
import com.nuvio.app.features.collection.CollectionRepository
import com.nuvio.app.features.details.MetaDetailsRepository
import com.nuvio.app.features.details.MetaScreenSettingsRepository
import com.nuvio.app.features.home.HomeCatalogSettingsRepository
import com.nuvio.app.features.home.HomeRepository
import com.nuvio.app.features.library.LibraryRepository
import com.nuvio.app.features.membership.MemberAccessRepository
import com.nuvio.app.features.library.LibraryDisplaySettingsRepository
import com.nuvio.app.features.notifications.EpisodeReleaseNotificationsRepository
import com.nuvio.app.features.player.PlayerLaunchStore
import com.nuvio.app.features.player.PlayerSettingsRepository
import com.nuvio.app.features.p2p.P2pSettingsRepository
import com.nuvio.app.features.plugins.PluginRepository
import com.nuvio.app.features.player.SubtitleRepository
import com.nuvio.app.features.profiles.ProfileBiometricAuth
import com.nuvio.app.features.profiles.ProfilePinCacheStorage
import com.nuvio.app.features.profiles.ProfileRepository
import com.nuvio.app.features.profiles.MAX_PROFILES
import com.nuvio.app.features.search.SearchRepository
import com.nuvio.app.features.servers.ServerRepository
import com.nuvio.app.features.settings.ThemeSettingsRepository
import com.nuvio.app.features.settings.ThemeSettingsStorage
import com.nuvio.app.features.streams.StreamContextStore
import com.nuvio.app.features.streams.StreamBadgeSettingsRepository
import com.nuvio.app.features.streams.StreamLaunchStore
import com.nuvio.app.features.streams.StreamsRepository
import com.nuvio.app.features.tracking.TrackingProviderRegistry
import com.nuvio.app.features.tracking.TrackingSettingsRepository
import com.nuvio.app.core.ui.CardDepthStyleRepository
import com.nuvio.app.core.ui.PosterCardStyleRepository
import com.nuvio.app.features.watchprogress.ContinueWatchingPreferencesRepository
import com.nuvio.app.features.watchprogress.ContinueWatchingEnrichmentCache
import com.nuvio.app.features.watchprogress.WatchProgressRepository
import com.nuvio.app.features.watchprogress.WatchProgressSourceCoordinator
import com.nuvio.app.features.watched.WatchedRepository

/**
 * Removes every profile-scoped local Liquid Glass background even if one file cannot be deleted.
 * Attempts both image types for every profile and reports incomplete cleanup to the caller.
 */
internal fun clearProfileBackgroundImages(
    profileIndices: IntRange,
    removeSettingsImage: (Int) -> Boolean,
    removeCloudImage: (Int) -> Boolean,
): Boolean {
    var allRemoved = true
    for (profileIndex in profileIndices) {
        val settingsRemoved = try {
            removeSettingsImage(profileIndex)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            false
        }
        val cloudRemoved = try {
            removeCloudImage(profileIndex)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            false
        }
        if (!settingsRemoved || !cloudRemoved) allRemoved = false
    }
    return allRemoved
}

internal object LocalAccountDataCleaner {
    suspend fun wipe(accountUserId: String? = null) {
        val cleanupUserId = accountUserId
            ?.takeIf { it.isNotBlank() }
            ?: (AuthRepository.state.value as? AuthState.Authenticated)?.userId
        val profileBackgroundImagesRemoved = withContext(Dispatchers.IO) {
            clearProfileBackgroundImages(
                profileIndices = 1..MAX_PROFILES,
                removeSettingsImage = { profileIndex ->
                    ThemeSettingsStorage.clearCustomSettingsBackgroundImage(profileIndex)
                },
                removeCloudImage = { profileIndex ->
                    ThemeSettingsStorage.clearCustomLibraryCloudBackgroundImage(profileIndex)
                },
            )
        }

        var profileAuthenticationCleanupFailed = false
        (1..MAX_PROFILES).forEach { profileIndex ->
            val removed = try {
                ProfilePinCacheStorage.removePayload(profileIndex)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                false
            }
            if (!removed) profileAuthenticationCleanupFailed = true
        }

        if (!cleanupUserId.isNullOrBlank()) {
            val biometricRemoved = try {
                withContext(Dispatchers.IO) {
                    ProfileBiometricAuth.disable(1, cleanupUserId)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                false
            }
            if (!biometricRemoved) profileAuthenticationCleanupFailed = true
        }

        ensureTrackingProvidersRegistered()
        TrackingProviderRegistry.removeStoredProfiles(1..MAX_PROFILES)
        SyncManager.cancelAccountSync()
        WatchProgressSourceCoordinator.clearLocalState()
        ProfileSettingsSync.clearAccountState()
        ContinueWatchingEnrichmentCache.clearLocalState()
        WatchProgressRepository.clearLocalState()
        WatchedRepository.clearLocalState()
        LibraryRepository.runAccountStorageWipe {
            wipePlatformStorage()
        }

        ProfileRepository.clearInMemory()
        MemberAccessRepository.clearLocalState()
        AddonRepository.clearLocalState()
        ServerRepository.clearLocalState()
        if (AppFeaturePolicy.pluginsEnabled) {
            PluginRepository.clearLocalState()
        }
        HomeRepository.clear()
        HomeCatalogSettingsRepository.clearLocalState()
        MetaScreenSettingsRepository.clearLocalState()
        com.nuvio.app.features.shuffle.EpisodeShuffleRepository.clearLocalState()
        LibraryRepository.clearLocalState()
        LibraryDisplaySettingsRepository.clearLocalState()
        ContinueWatchingPreferencesRepository.clearLocalState()
        EpisodeReleaseNotificationsRepository.clearLocalState()
        CollectionMobileSettingsRepository.clearLocalState()
        CollectionRepository.clearLocalState()
        ThemeSettingsRepository.clearLocalState()
        PosterCardStyleRepository.clearLocalState()
        CardDepthStyleRepository.clearLocalState()
        TrackingProviderRegistry.clearLocalState()
        TrackingSettingsRepository.clearLocalState()
        PlayerSettingsRepository.clearLocalState()
        StreamBadgeSettingsRepository.clearLocalState()
        P2pSettingsRepository.clearLocalState()
        CatalogRepository.clear()
        StreamsRepository.clear()
        MetaDetailsRepository.clear()
        SearchRepository.reset()
        SubtitleRepository.clear()
        PlayerLaunchStore.clear()
        StreamLaunchStore.clear()
        StreamContextStore.clear()

        if (!profileBackgroundImagesRemoved || profileAuthenticationCleanupFailed) {
            throw IllegalStateException("Local profile data cleanup could not be confirmed")
        }
    }

    internal fun wipePlatformStorage(wipeStorage: () -> Unit = PlatformLocalAccountDataCleaner::wipe) {
        try {
            wipeStorage()
        } finally {
            ContinueWatchingEnrichmentCache.clearLocalState()
        }
    }
}

internal expect object PlatformLocalAccountDataCleaner {
    fun wipe()
}
