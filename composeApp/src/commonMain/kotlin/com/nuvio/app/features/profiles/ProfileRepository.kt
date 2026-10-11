package com.nuvio.app.features.profiles

import co.touchlab.kermit.Logger
import com.nuvio.app.core.auth.AuthRepository
import com.nuvio.app.core.auth.AuthState
import com.nuvio.app.core.auth.isAnonymous
import com.nuvio.app.core.network.SupabaseProvider
import com.nuvio.app.core.poster.CustomPosterUrlRepository
import com.nuvio.app.core.sync.ProfileSettingsSync
import com.nuvio.app.core.sync.putSyncOriginClientId
import com.nuvio.app.core.tracking.ensureTrackingProvidersRegistered
import com.nuvio.app.features.addons.AddonRepository
import com.nuvio.app.features.collection.CollectionMobileSettingsRepository
import com.nuvio.app.features.collection.CollectionRepository
import com.nuvio.app.features.downloads.DownloadsRepository
import com.nuvio.app.features.details.MetaScreenSettingsRepository
import com.nuvio.app.features.home.HomeCatalogSettingsRepository
import com.nuvio.app.features.home.HomeRepository
import com.nuvio.app.core.ui.CardDepthStyleRepository
import com.nuvio.app.core.ui.PosterCardStyleRepository
import com.nuvio.app.features.library.LibraryRepository
import com.nuvio.app.features.library.LibraryDisplaySettingsRepository
import com.nuvio.app.features.mdblist.MdbListSettingsRepository
import com.nuvio.app.features.notifications.EpisodeReleaseNotificationsRepository
import com.nuvio.app.features.p2p.P2pSettingsRepository
import com.nuvio.app.features.player.PlayerSettingsRepository
import com.nuvio.app.features.plugins.PluginRepository
import com.nuvio.app.features.search.SearchHistoryRepository
import com.nuvio.app.features.search.SearchRepository
import com.nuvio.app.features.servers.ServerRepository
import com.nuvio.app.features.settings.ThemeSettingsRepository
import com.nuvio.app.features.streams.StreamBadgeSettingsRepository
import com.nuvio.app.features.tracking.TrackingProviderRegistry
import com.nuvio.app.features.tracking.TrackingSettingsRepository
import com.nuvio.app.features.tmdb.TmdbSettingsRepository
import com.nuvio.app.features.watched.WatchedRepository
import com.nuvio.app.features.watchprogress.ContinueWatchingPreferencesRepository
import com.nuvio.app.features.watchprogress.WatchProgressRepository
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString

@Serializable
private data class StoredProfilePayload(
    val userId: String,
    val activeProfileIndex: Int = 1,
    val hasEverSelectedProfile: Boolean = false,
    val rememberLastProfileEnabled: Boolean = false,
    val profiles: List<NuvioProfile> = emptyList(),
)

object ProfileRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val log = Logger.withTag("ProfileRepository")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private fun localizedString(resource: StringResource): String = runBlocking { getString(resource) }

    private val _state = MutableStateFlow(ProfileState())
    val state: StateFlow<ProfileState> = _state.asStateFlow()

    private var activeProfileIndex: Int = 1
    private var loadedCacheForUserId: String? = null

    val activeProfileId: Int get() = activeProfileIndex

    fun setRememberLastProfileEnabled(enabled: Boolean) {
        if (_state.value.rememberLastProfileEnabled == enabled) return

        _state.value = _state.value.copy(rememberLastProfileEnabled = enabled)
        persist()
    }

    suspend fun loadCachedProfiles(): Boolean {
        val stored = decodeStoredPayload() ?: return false
        loadedCacheForUserId = stored.userId
        applyStoredPayload(stored)
        ThemeSettingsRepository.onProfileChanged()
        return _state.value.profiles.isNotEmpty()
    }

    suspend fun ensureLoaded(userId: String) {
        if (loadedCacheForUserId == userId && _state.value.isLoaded) return

        val stored = decodeStoredPayload()
        loadedCacheForUserId = userId
        if (stored == null) {
            _state.value = ProfileState()
            activeProfileIndex = 1
            return
        }

        if (stored.userId != userId) {
            // A persisted profile snapshot can belong to a previous account. Clear its
            // in-memory state and local PIN payloads before suspending so cleanup cannot
            // overwrite new-account state or remove newly populated cache entries.
            _state.value = ProfileState()
            activeProfileIndex = 1
            (1..MAX_PROFILES).forEach { ProfilePinCacheStorage.removePayload(it) }
            withContext(Dispatchers.IO) {
                ProfileBiometricAuth.disable(1, stored.userId)
            }
            return
        }

        applyStoredPayload(stored)
    }

    fun clearInMemory() {
        loadedCacheForUserId = null
        activeProfileIndex = 1
        _state.value = ProfileState()
    }

    suspend fun pullProfiles() {
        if (AuthRepository.state.value.isAnonymous) {
            if (!_state.value.isLoaded) {
                _state.value = _state.value.copy(isLoaded = true)
            }
            return
        }
        try {
            val result = SupabaseProvider.client.postgrest.rpc("sync_pull_profiles")
            val profiles = result.decodeList<NuvioProfile>()
            _state.value = _state.value.copy(
                profiles = profiles.sortedBy { it.profileIndex },
                isLoaded = true,
                activeProfile = profiles.find { it.profileIndex == activeProfileIndex }
                    ?: profiles.firstOrNull(),
            )
            if (_state.value.activeProfile != null) {
                activeProfileIndex = _state.value.activeProfile!!.profileIndex
            }
            withContext(Dispatchers.IO) {
                syncPinCache(profiles.sortedBy { it.profileIndex })
            }
            persist()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            if (AuthRepository.signOutIfSessionInvalid(e, "Profile pull")) return
            log.e(e) { "Failed to pull profiles" }
            if (!_state.value.isLoaded) {
                _state.value = _state.value.copy(isLoaded = true)
            }
        }
    }

    fun selectProfile(profileIndex: Int) {
        activeProfileIndex = profileIndex
        CustomPosterUrlRepository.onProfileChanged()
        val selectedProfile = _state.value.profiles.find { it.profileIndex == profileIndex }
        _state.value = _state.value.copy(
            activeProfile = selectedProfile,
            hasEverSelectedProfile = selectedProfile != null || _state.value.hasEverSelectedProfile,
        )
        persist()
        WatchedRepository.onProfileChanged(profileIndex)
        TrackingSettingsRepository.onProfileChanged()
        ensureTrackingProvidersRegistered()
        TrackingProviderRegistry.onProfileChanged()
        LibraryRepository.onProfileChanged(profileIndex)
        LibraryDisplaySettingsRepository.onProfileChanged()
        WatchProgressRepository.onProfileChanged(profileIndex)
        AddonRepository.onProfileChanged(profileIndex)
        if (com.nuvio.app.core.build.AppFeaturePolicy.pluginsEnabled) {
            PluginRepository.onProfileChanged(profileIndex)
        }
        ThemeSettingsRepository.onProfileChanged()
        PosterCardStyleRepository.onProfileChanged()
        CardDepthStyleRepository.onProfileChanged()
        PlayerSettingsRepository.onProfileChanged()
        StreamBadgeSettingsRepository.onProfileChanged()
        P2pSettingsRepository.onProfileChanged()
        HomeCatalogSettingsRepository.onProfileChanged()
        HomeRepository.clear()
        MetaScreenSettingsRepository.onProfileChanged()
        com.nuvio.app.features.shuffle.EpisodeShuffleRepository.onProfileChanged()
        ContinueWatchingPreferencesRepository.onProfileChanged()
        com.nuvio.app.features.watchprogress.ContinueWatchingEnrichmentCache.onProfileChanged()
        EpisodeReleaseNotificationsRepository.onProfileChanged()
        TmdbSettingsRepository.onProfileChanged()
        MdbListSettingsRepository.onProfileChanged()
        SearchHistoryRepository.onProfileChanged()
        SearchRepository.reset()
        CollectionRepository.onProfileChanged()
        CollectionMobileSettingsRepository.onProfileChanged()
        DownloadsRepository.onProfileChanged()
        ServerRepository.onProfileChanged()
        ProfileSettingsSync.onProfileChanged()
    }

    suspend fun pushProfiles(profiles: List<ProfilePushPayload>) {
        if (AuthRepository.state.value.isAnonymous) {
            applyPayloadsLocally(profiles)
            return
        }
        try {
            val params = buildJsonObject {
                put("p_client_max_profiles", MAX_PROFILES)
                put("p_profiles", json.encodeToJsonElement(profiles))
                putSyncOriginClientId()
            }
            SupabaseProvider.client.postgrest.rpc("sync_push_profiles", params)
            pullProfiles()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            if (AuthRepository.signOutIfSessionInvalid(e, "Profile push")) return
            log.e(e) { "Failed to push profiles" }
        }
    }

    suspend fun createProfile(
        name: String,
        avatarColorHex: String,
        avatarId: String? = null,
        avatarUrl: String? = null,
        usesPrimaryAddons: Boolean = false,
    ) {
        val existing = _state.value.profiles
        val nextIndex = ((1..MAX_PROFILES).toSet() - existing.map { it.profileIndex }.toSet()).minOrNull() ?: return

        val allPayloads = existing.map { profile ->
            ProfilePushPayload(
                profileIndex = profile.profileIndex,
                name = profile.name,
                avatarColorHex = profile.avatarColorHex,
                usesPrimaryAddons = profile.usesPrimaryAddons,
                usesPrimaryPlugins = profile.usesPrimaryPlugins,
                avatarId = profile.avatarId,
                avatarUrl = profile.avatarUrl,
                profileBackgroundId = profile.profileBackgroundId,
                profileBackgroundUrl = profile.profileBackgroundUrl,
            )
        } + ProfilePushPayload(
            profileIndex = nextIndex,
            name = name,
            avatarColorHex = avatarColorHex,
            usesPrimaryAddons = usesPrimaryAddons,
            avatarId = avatarId,
            avatarUrl = avatarUrl,
        )

        pushProfiles(allPayloads)
    }

    suspend fun updateProfile(
        profileIndex: Int,
        name: String,
        avatarColorHex: String,
        avatarId: String? = null,
        avatarUrl: String? = null,
        profileBackgroundId: String? = null,
        profileBackgroundUrl: String? = null,
        usesPrimaryAddons: Boolean = false,
    ) {
        val allPayloads = _state.value.profiles.map { profile ->
            if (profile.profileIndex == profileIndex) {
                ProfilePushPayload(
                    profileIndex = profileIndex,
                    name = name,
                    avatarColorHex = avatarColorHex,
                    usesPrimaryAddons = usesPrimaryAddons,
                    avatarId = avatarId,
                    avatarUrl = avatarUrl,
                    profileBackgroundId = profileBackgroundId,
                    profileBackgroundUrl = profileBackgroundUrl,
                )
            } else {
                ProfilePushPayload(
                    profileIndex = profile.profileIndex,
                    name = profile.name,
                    avatarColorHex = profile.avatarColorHex,
                    usesPrimaryAddons = profile.usesPrimaryAddons,
                    usesPrimaryPlugins = profile.usesPrimaryPlugins,
                    avatarId = profile.avatarId,
                    avatarUrl = profile.avatarUrl,
                    profileBackgroundId = profile.profileBackgroundId,
                    profileBackgroundUrl = profile.profileBackgroundUrl,
                )
            }
        }

        pushProfiles(allPayloads)
    }

    suspend fun deleteProfile(profileIndex: Int) {
        val deletedProfile = _state.value.profiles.firstOrNull { it.profileIndex == profileIndex }
        val userId = deletedProfile?.userId.orEmpty()

        if (AuthRepository.state.value.isAnonymous) {
            val remaining = _state.value.profiles.filter { it.profileIndex != profileIndex }
            val localLockCleanupSucceeded = withContext(Dispatchers.IO) {
                val pinCacheRemoved = ProfilePinCacheStorage.removePayload(profileIndex)
                val biometricRemoved = ProfileBiometricAuth.disable(profileIndex, userId)
                pinCacheRemoved && biometricRemoved
            }
            if (!localLockCleanupSucceeded) {
                log.w { "Profile $profileIndex was deleted, but local profile-lock cleanup could not be confirmed" }
            }
            ServerRepository.removeProfile(profileIndex)
            _state.value = _state.value.copy(
                profiles = remaining,
                activeProfile = if (_state.value.activeProfile?.profileIndex == profileIndex) {
                    remaining.firstOrNull()
                } else {
                    _state.value.activeProfile
                },
            )
            if (_state.value.activeProfile != null) {
                activeProfileIndex = _state.value.activeProfile!!.profileIndex
            }
            persist()
            return
        }
        try {
            val params = buildJsonObject {
                put("p_profile_id", profileIndex)
                putSyncOriginClientId()
            }
            SupabaseProvider.client.postgrest.rpc("sync_delete_profile_data", params)
            ServerRepository.removeProfile(profileIndex)
            // Remote deletion succeeded; remove device-local authentication artifacts even if
            // the subsequent profile refresh is interrupted or unavailable.
            val localLockCleanupSucceeded = withContext(Dispatchers.IO) {
                val pinCacheRemoved = ProfilePinCacheStorage.removePayload(profileIndex)
                val biometricRemoved = ProfileBiometricAuth.disable(profileIndex, userId)
                pinCacheRemoved && biometricRemoved
            }
            if (!localLockCleanupSucceeded) {
                log.w { "Profile $profileIndex was deleted, but local profile-lock cleanup could not be confirmed" }
            }
            pullProfiles()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            if (AuthRepository.signOutIfSessionInvalid(e, "Profile delete")) return
            log.e(e) { "Failed to delete profile $profileIndex" }
        }
    }

    suspend fun verifyPin(profileIndex: Int, pin: String): PinVerifyResult {
        if (AuthRepository.state.value !is AuthState.Authenticated) {
            return withContext(Dispatchers.IO) {
                verifyPinLocally(profileIndex, pin)
            }
        }

        return runCatching {
            val params = buildJsonObject {
                put("p_profile_id", profileIndex)
                put("p_pin", pin)
            }
            val result = SupabaseProvider.client.postgrest.rpc("verify_profile_pin", params)
            result.decodeSingle<PinVerifyResult>().also { verifyResult ->
                if (verifyResult.unlocked) {
                    pullProfiles()
                    withContext(Dispatchers.IO) {
                        rememberVerifiedPin(profileIndex = profileIndex, pin = pin)
                    }
                }
            }
        }.getOrElse { error ->
            if (error is CancellationException) throw error
            // Never log the authentication exception: request/transport exceptions are not useful
            // enough to justify risking credential-adjacent data in crash/log pipelines.
            log.w { "PIN verification request failed; attempting local verification" }
            withContext(Dispatchers.IO) {
                verifyPinLocally(profileIndex, pin)
            }
        }
    }

    suspend fun setPin(profileIndex: Int, pin: String, currentPin: String? = null): PinVerifyResult {
        if (AuthRepository.state.value !is AuthState.Authenticated) {
            return PinVerifyResult(unlocked = false, message = getString(Res.string.profile_pin_set_requires_internet))
        }

        return runCatching {
            val params = buildJsonObject {
                put("p_profile_id", profileIndex)
                put("p_pin", pin)
                currentPin?.let { put("p_current_pin", it) }
            }
            SupabaseProvider.client.postgrest.rpc("set_profile_pin", params)
            pullProfiles()
            withContext(Dispatchers.IO) {
                rememberVerifiedPin(profileIndex = profileIndex, pin = pin)
            }
            PinVerifyResult(unlocked = true)
        }.onFailure { e ->
            if (e is CancellationException) throw e
            log.e(e) { "Failed to set pin" }
        }.getOrElse {
            PinVerifyResult(unlocked = false, message = getString(Res.string.profile_pin_set_failed))
        }
    }

    suspend fun clearPin(profileIndex: Int, currentPin: String? = null): PinVerifyResult {
        val accountUserId =
            (AuthRepository.state.value as? AuthState.Authenticated)?.userId.orEmpty()
        if (accountUserId.isBlank()) {
            return PinVerifyResult(unlocked = false, message = getString(Res.string.profile_pin_clear_requires_internet))
        }

        val profileUserId = _state.value.profiles
            .firstOrNull { it.profileIndex == profileIndex }
            ?.userId
            .orEmpty()
            .ifBlank { accountUserId }

        val params = buildJsonObject {
            put("p_profile_id", profileIndex)
            currentPin?.let { put("p_current_pin", it) }
        }
        try {
            SupabaseProvider.client.postgrest.rpc("clear_profile_pin", params)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.e(e) { "Failed to clear pin" }
            return PinVerifyResult(unlocked = false, message = getString(Res.string.profile_pin_clear_failed))
        }

        // Once the server confirms PIN removal, local credential cleanup must not be
        // reported as a failed PIN operation. Avoid touching index-scoped storage if the
        // authenticated account changed while the request was in flight.
        var pinCacheRemoved = false
        var biometricRemoved = false
        val sameAccount = {
            (AuthRepository.state.value as? AuthState.Authenticated)?.userId == accountUserId
        }
        if (sameAccount()) {
            withContext(NonCancellable) {
                withContext(Dispatchers.IO) {
                    // Re-check after the dispatcher switch so an account change while queued
                    // cannot clear the new account's profile-indexed PIN cache.
                    if (sameAccount()) {
                        pinCacheRemoved = try {
                            ProfilePinCacheStorage.removePayload(profileIndex)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            log.w { "Unable to remove local PIN cache after server PIN removal" }
                            false
                        }

                        biometricRemoved = try {
                            ProfileBiometricAuth.disable(profileIndex, profileUserId)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            log.w { "Unable to remove biometric credential after server PIN removal" }
                            false
                        }
                    }
                }
            }
        }

        if (sameAccount()) {
            pullProfiles()
        }

        val cleanupSucceeded = sameAccount() && pinCacheRemoved && biometricRemoved
        return PinVerifyResult(
            unlocked = true,
            message = if (cleanupSucceeded) null
                else getString(Res.string.profile_pin_clear_cleanup_failed),
        )
    }

    suspend fun clearPinWithPassword(profileIndex: Int, accountPassword: String) {
        runCatching {
            val params = buildJsonObject {
                put("p_account_password", accountPassword)
                put("p_profile_id", profileIndex)
            }
            SupabaseProvider.client.postgrest.rpc("clear_profile_pin_with_account_password", params)
            pullProfiles()
            val localLockCleanup = withContext(Dispatchers.IO) {
                val pinCacheRemoved = ProfilePinCacheStorage.removePayload(profileIndex)
                // PIN removal must not depend on biometric cleanup succeeding; check both local
                // credential removals separately after the server confirms PIN removal.
                val biometricRemoved = ProfileBiometricAuth.disable(
                    profileIndex,
                    _state.value.profiles.firstOrNull { it.profileIndex == profileIndex }?.userId.orEmpty(),
                )
                pinCacheRemoved to biometricRemoved
            }
            if (!localLockCleanup.first || !localLockCleanup.second) {
                log.w { "PIN was cleared but local profile-lock cleanup could not be confirmed" }
            }
        }.onFailure { e ->
            if (e is CancellationException) throw e
            log.e(e) { "Failed to clear pin with password" }
        }
    }

    suspend fun pullProfileLocks(): List<ProfileLockState> {
        return runCatching {
            val result = SupabaseProvider.client.postgrest.rpc("sync_pull_profile_locks")
            result.decodeList<ProfileLockState>()
        }.getOrElse { e ->
            if (e is CancellationException) throw e
            log.e(e) { "Failed to pull profile locks" }
            emptyList()
        }
    }

    private suspend fun applyPayloadsLocally(payloads: List<ProfilePushPayload>) {
        val authState = AuthRepository.state.value as? AuthState.Authenticated ?: return
        val profiles = payloads.map { p ->
            NuvioProfile(
                id = "",
                userId = authState.userId,
                profileIndex = p.profileIndex,
                name = p.name,
                avatarColorHex = p.avatarColorHex,
                avatarId = p.avatarId,
                avatarUrl = p.avatarUrl,
                profileBackgroundId = p.profileBackgroundId,
                profileBackgroundUrl = p.profileBackgroundUrl,
                usesPrimaryAddons = p.usesPrimaryAddons,
                usesPrimaryPlugins = p.usesPrimaryPlugins,
            )
        }.sortedBy { it.profileIndex }
        _state.value = _state.value.copy(
            profiles = profiles,
            isLoaded = true,
            activeProfile = profiles.find { it.profileIndex == activeProfileIndex } ?: profiles.firstOrNull(),
        )
        if (_state.value.activeProfile != null) {
            activeProfileIndex = _state.value.activeProfile!!.profileIndex
        }
        withContext(Dispatchers.IO) {
            syncPinCache(profiles)
        }
        persist()
    }

    private fun decodeStoredPayload(): StoredProfilePayload? {
        val payload = ProfileStorage.loadPayload().orEmpty().trim()
        if (payload.isEmpty()) return null

        return runCatching {
            json.decodeFromString<StoredProfilePayload>(payload)
        }.getOrNull()
    }

    private suspend fun applyStoredPayload(stored: StoredProfilePayload) {
        val profiles = stored.profiles.sortedBy { it.profileIndex }
        activeProfileIndex = stored.activeProfileIndex
        _state.value = ProfileState(
            profiles = profiles,
            activeProfile = profiles.find { it.profileIndex == activeProfileIndex } ?: profiles.firstOrNull(),
            isLoaded = profiles.isNotEmpty(),
            hasEverSelectedProfile = stored.hasEverSelectedProfile,
            rememberLastProfileEnabled = stored.rememberLastProfileEnabled,
        )
        _state.value.activeProfile?.let { activeProfileIndex = it.profileIndex }
        withContext(Dispatchers.IO) {
            syncPinCache(profiles)
        }
    }

    private fun rememberVerifiedPin(profileIndex: Int, pin: String) {
        val profile = _state.value.profiles.find { it.profileIndex == profileIndex }
        val salt = generateProfilePinSalt()
        val payload = CachedProfilePinPayload(
            salt = salt,
            digest = hashProfilePin(profileIndex = profileIndex, salt = salt, pin = pin),
            profileUserId = profile?.userId.orEmpty(),
            profileUpdatedAt = profile?.updatedAt.orEmpty(),
        )
        ProfilePinCacheStorage.savePayload(profileIndex, json.encodeToString(payload))
    }

    private fun verifyPinLocally(profileIndex: Int, pin: String): PinVerifyResult {
        val profile = _state.value.profiles.find { it.profileIndex == profileIndex }
        if (profile?.pinEnabled != true) {
            return PinVerifyResult(unlocked = true)
        }

        val payload = ProfilePinCacheStorage.loadPayload(profileIndex).orEmpty().trim()
        if (payload.isEmpty()) {
            return PinVerifyResult(
                unlocked = false,
                message = localizedString(Res.string.profile_pin_offline_verification_requires_online),
            )
        }

        val cached = runCatching {
            json.decodeFromString<CachedProfilePinPayload>(payload)
        }.getOrNull() ?: return PinVerifyResult(
            unlocked = false,
            message = localizedString(Res.string.profile_pin_offline_verification_requires_online),
        )

        if (cached.profileUserId != profile.userId) {
            // The verifier is account-bound. Never allow a cache from another account
            // to authorize this profile, even if profile indexes overlap.
            ProfilePinCacheStorage.removePayload(profileIndex)
            return PinVerifyResult(
                unlocked = false,
                message = localizedString(Res.string.profile_pin_offline_verification_requires_online),
            )
        }

        if (
            cached.profileUpdatedAt.isNotBlank() &&
            profile.updatedAt.isNotBlank() &&
            cached.profileUpdatedAt != profile.updatedAt
        ) {
            ProfilePinCacheStorage.removePayload(profileIndex)
            return PinVerifyResult(
                unlocked = false,
                message = localizedString(Res.string.profile_pin_changed_requires_refresh),
            )
        }

        val digest = hashProfilePin(profileIndex = profileIndex, salt = cached.salt, pin = pin)
        return if (digest == cached.digest) {
            PinVerifyResult(unlocked = true)
        } else {
            PinVerifyResult(unlocked = false, message = localizedString(Res.string.pin_incorrect))
        }
    }

    private fun syncPinCache(profiles: List<NuvioProfile>) {
        val profilesByIndex = profiles.associateBy { it.profileIndex }
        val accountUserId =
            (AuthRepository.state.value as? AuthState.Authenticated)?.userId.orEmpty()
        for (profileIndex in 1..MAX_PROFILES) {
            val profile = profilesByIndex[profileIndex]
            if (profile == null || !profile.pinEnabled) {
                val pinCacheRemoved = ProfilePinCacheStorage.removePayload(profileIndex)
                if (!pinCacheRemoved) {
                    log.w { "Unable to confirm local PIN-cache removal for profile $profileIndex" }
                }
                if (profileIndex == 1 && accountUserId.isNotBlank()) {
                    // PIN state can change remotely. A primary profile whose PIN is gone
                    // must not retain a stale biometric credential on this device.
                    val biometricRemoved = ProfileBiometricAuth.disable(
                        1,
                        profile?.userId.orEmpty().ifBlank { accountUserId },
                    )
                    if (!biometricRemoved) {
                        log.w { "Unable to confirm biometric-credential removal for the primary profile" }
                    }
                }
                continue
            }

            val raw = ProfilePinCacheStorage.loadPayload(profileIndex).orEmpty().trim()
            if (raw.isEmpty()) continue

            val cached = runCatching {
                json.decodeFromString<CachedProfilePinPayload>(raw)
            }.getOrNull() ?: run {
                ProfilePinCacheStorage.removePayload(profileIndex)
                continue
            }

            if (
                cached.profileUpdatedAt.isNotBlank() &&
                profile.updatedAt.isNotBlank() &&
                cached.profileUpdatedAt != profile.updatedAt
            ) {
                ProfilePinCacheStorage.removePayload(profileIndex)
            }
        }
    }

    private fun persist() {
        val authState = AuthRepository.state.value as? AuthState.Authenticated ?: return
        val state = _state.value
        ProfileStorage.savePayload(
            json.encodeToString(
                StoredProfilePayload(
                    userId = authState.userId,
                    activeProfileIndex = activeProfileIndex,
                    hasEverSelectedProfile = state.hasEverSelectedProfile,
                    rememberLastProfileEnabled = state.rememberLastProfileEnabled,
                    profiles = state.profiles,
                ),
            ),
        )
    }
}

@kotlinx.serialization.Serializable
data class ProfileLockState(
    @kotlinx.serialization.SerialName("profile_index") val profileIndex: Int,
    @kotlinx.serialization.SerialName("pin_enabled") val pinEnabled: Boolean = false,
    @kotlinx.serialization.SerialName("pin_locked_until") val pinLockedUntil: String? = null,
)
