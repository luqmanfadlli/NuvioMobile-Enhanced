package com.nuvio.app.core.ui

import com.nuvio.app.features.settings.NuvioTabBarBehavior
import com.nuvio.app.features.profiles.AvatarRepository
import com.nuvio.app.features.profiles.AvatarCatalogItem
import com.nuvio.app.features.profiles.MAX_PROFILES
import com.nuvio.app.features.profiles.NuvioProfile
import com.nuvio.app.features.profiles.PinVerifyResult
import com.nuvio.app.features.profiles.ProfileRepository
import com.nuvio.app.features.profiles.profileAvatarImageUrl
import com.nuvio.app.features.profiles.showAlreadyActiveProfileToast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

internal enum class NativeNavigationTab {
    Home,
    Search,
    Library,
    LiveTv,
    Sports,
    Music,
    Following,
    News,
    Settings,
    ;

    companion object {
        fun fromName(name: String): NativeNavigationTab =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: Home
    }
}

internal object NativeTabBridge {
    private val _requestedTabs = MutableSharedFlow<NativeNavigationTab>(extraBufferCapacity = 1)
    val requestedTabs: SharedFlow<NativeNavigationTab> = _requestedTabs.asSharedFlow()

    private val _activeTab = MutableStateFlow(NativeNavigationTab.Home)
    val activeTab: StateFlow<NativeNavigationTab> = _activeTab.asStateFlow()

    fun markActiveTab(tab: NativeNavigationTab) {
        _activeTab.value = tab
    }

    fun requestPopToRoot(tabName: String) {
        swiftPopToRootListener?.invoke(tabName)
    }

    fun requestTab(tabName: String) {
        _requestedTabs.tryEmit(NativeNavigationTab.fromName(tabName))
    }

    fun publishSelectedTab(tab: NativeNavigationTab) {
        publishNativeSelectedTab(tab.name)
    }

    fun publishTabBarVisible(visible: Boolean) {
        val supported = isLiquidGlassNativeTabBarSupported()
        swiftTabBarVisibilityListener?.invoke(visible && supported)
        publishNativeTabBarVisible(visible && supported)
    }

    fun publishLiquidGlassEnabled(enabled: Boolean) {
        publishLiquidGlassNativeTabBarEnabled(enabled && isLiquidGlassNativeTabBarSupported())
    }

    fun publishTabBarBehavior(behavior: NuvioTabBarBehavior) {
        val supported = isLiquidGlassNativeTabBarSupported()
        publishLiquidGlassNativeTabBarEnabled(behavior.isEnabled && supported)
        publishNativeTabBarBehavior(if (supported) behavior.key else NuvioTabBarBehavior.OFF.key)
    }

    fun publishAccentColor(hexColor: String, gradientHexColors: List<String> = listOf(hexColor)) {
        publishNativeTabAccentColor(
            hexColor = hexColor,
            gradientHexColors = gradientHexColors.ifEmpty { listOf(hexColor) },
        )
    }

    fun publishTabTitles(
        home: String,
        search: String,
        library: String,
        profile: String,
    ) {
        publishNativeTabTitles(home, search, library, profile)
    }

    fun publishProfileTabIcon(
        name: String?,
        avatarColorHex: String?,
        avatarImageUrl: String?,
        avatarBackgroundColorHex: String?,
    ) {
        publishNativeProfileTabIcon(
            name = name,
            avatarColorHex = avatarColorHex,
            avatarImageUrl = avatarImageUrl,
            avatarBackgroundColorHex = avatarBackgroundColorHex,
        )
    }

    private val _profileTabIconFrame = MutableStateFlow<ProfileTabIconFrame?>(null)

    val profileTabIconFrame: StateFlow<ProfileTabIconFrame?> = _profileTabIconFrame.asStateFlow()

    fun receiveProfileTabIconFrame(xDp: Float, yDp: Float, widthDp: Float, heightDp: Float) {
        _profileTabIconFrame.value = ProfileTabIconFrame(xDp, yDp, widthDp, heightDp)
    }
}

internal data class ProfileTabIconFrame(
    val xDp: Float,
    val yDp: Float,
    val widthDp: Float,
    val heightDp: Float,
)

data class NativeProfileOption(
    val profileIndex: Int,
    val name: String,
    val avatarColorHex: String,
    val avatarImageUrl: String?,
    val avatarBackgroundColorHex: String?,
    val pinEnabled: Boolean,
    val active: Boolean,
)

data class NativeProfileSwitcherState(
    val profiles: List<NativeProfileOption>,
    val isLoaded: Boolean,
    val canAddProfile: Boolean,
)

class NativeProfileSwitcherController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val profileSelections = Channel<Int>(Channel.BUFFERED)
    private val manageProfileRequests = Channel<Unit>(Channel.BUFFERED)
    private var observationJob: Job? = null

    internal val selectedProfileIndices = profileSelections.receiveAsFlow()
    internal val requestedManageProfiles = manageProfileRequests.receiveAsFlow()

    fun currentState(): NativeProfileSwitcherState = nativeState(
        profilesLoaded = ProfileRepository.state.value.isLoaded,
        profiles = ProfileRepository.state.value.profiles,
        activeProfileIndex = ProfileRepository.state.value.activeProfile?.profileIndex,
        avatarsById = AvatarRepository.avatars.value.associateBy { it.id },
    )

    fun observeState(callback: (NativeProfileSwitcherState) -> Unit) {
        observationJob?.cancel()
        observationJob = scope.launch {
            combine(ProfileRepository.state, AvatarRepository.avatars) { state, avatars ->
                nativeState(
                    profilesLoaded = state.isLoaded,
                    profiles = state.profiles,
                    activeProfileIndex = state.activeProfile?.profileIndex,
                    avatarsById = avatars.associateBy { it.id },
                )
            }.collect { callback(it) }
        }
    }

    fun stopObserving() {
        observationJob?.cancel()
        observationJob = null
    }

    private fun nativeState(
        profilesLoaded: Boolean,
        profiles: List<NuvioProfile>,
        activeProfileIndex: Int?,
        avatarsById: Map<String, AvatarCatalogItem>,
    ): NativeProfileSwitcherState {
        val options = profiles.map { profile ->
            val avatar = profile.avatarId?.let(avatarsById::get)
            NativeProfileOption(
                profileIndex = profile.profileIndex,
                name = profile.name,
                avatarColorHex = profile.avatarColorHex,
                avatarImageUrl = profileAvatarImageUrl(profile, avatar),
                avatarBackgroundColorHex = avatar?.bgColor,
                pinEnabled = profile.pinEnabled,
                active = profile.profileIndex == activeProfileIndex,
            )
        }
        return NativeProfileSwitcherState(
            profiles = options,
            isLoaded = profilesLoaded,
            canAddProfile = profiles.size < MAX_PROFILES,
        )
    }

    fun chooseProfile(
        profileIndex: Int,
        pin: String?,
        completion: (PinVerifyResult) -> Unit,
    ) {
        scope.launch {
            val activeProfile = ProfileRepository.state.value.activeProfile
            if (activeProfile != null && profileIndex == activeProfile.profileIndex) {
                showAlreadyActiveProfileToast(activeProfile)
                completion(PinVerifyResult(unlocked = true))
                return@launch
            }
            val profile = ProfileRepository.state.value.profiles
                .firstOrNull { it.profileIndex == profileIndex }
            if (profile == null) {
                completion(PinVerifyResult(message = null))
                return@launch
            }
            val result = if (profile.pinEnabled) {
                ProfileRepository.verifyPin(profileIndex, pin.orEmpty())
            } else {
                PinVerifyResult(unlocked = true)
            }
            if (result.unlocked && profileIndex != ProfileRepository.state.value.activeProfile?.profileIndex) {
                profileSelections.trySend(profileIndex)
            }
            completion(result)
        }
    }

    fun requestManageProfiles() {
        manageProfileRequests.trySend(Unit)
    }
}

private var swiftTabBarVisibilityListener: ((Boolean) -> Unit)? = null

fun observeNativeTabBarVisible(listener: (Boolean) -> Unit) {
    swiftTabBarVisibilityListener = listener
}

private var swiftPopToRootListener: ((String) -> Unit)? = null

fun observeNativePopToRoot(listener: (String) -> Unit) {
    swiftPopToRootListener = listener
}

fun nativeTabSelect(tabName: String) {
    NativeTabBridge.requestTab(tabName)
}

/** The native iOS mode picker (long press on the profile icon) changes the layout through here. */
fun nativeSetExperienceMode(key: String) {
    com.nuvio.app.features.experience.ExperienceRepository.setMode(
        com.nuvio.app.features.experience.ExperienceMode.fromKey(key),
    )
}

fun publishProfileTabIconFrame(xDp: Float, yDp: Float, widthDp: Float, heightDp: Float) {
    NativeTabBridge.receiveProfileTabIconFrame(xDp, yDp, widthDp, heightDp)
}

fun nativeTabVisibilityChanged(tabName: String) {
    NativeTabBridge.markActiveTab(NativeNavigationTab.fromName(tabName))
    if (NativeNavigationTab.fromName(tabName) != NativeNavigationTab.Home) {
        com.nuvio.app.features.home.components.HomeHeroTrailerPlaybackController.forceStop()
    }
}

internal expect fun isLiquidGlassNativeTabBarSupported(): Boolean

internal expect fun publishLiquidGlassNativeTabBarEnabled(enabled: Boolean)

internal expect fun publishNativeTabBarBehavior(behaviorKey: String)

internal expect fun publishNativeTabBarVisible(visible: Boolean)

internal expect fun publishNativeSelectedTab(tabName: String)

internal expect fun publishNativeTabAccentColor(hexColor: String, gradientHexColors: List<String>)

internal expect fun publishNativeTabTitles(
    home: String,
    search: String,
    library: String,
    profile: String,
)

internal expect fun publishNativeProfileTabIcon(
    name: String?,
    avatarColorHex: String?,
    avatarImageUrl: String?,
    avatarBackgroundColorHex: String?,
)
