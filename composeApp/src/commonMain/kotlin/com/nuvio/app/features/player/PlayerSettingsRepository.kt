package com.nuvio.app.features.player

import com.nuvio.app.core.build.AppFeaturePolicy
import com.nuvio.app.features.player.skip.AutoSkipSegmentType
import com.nuvio.app.features.player.skip.NextEpisodeThresholdMode
import com.nuvio.app.features.streams.StreamAutoPlayMode
import com.nuvio.app.features.streams.StreamAutoPlaySource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

val STREAM_AUTO_PLAY_TIMEOUT_VALUES: List<Int> = listOf(
    0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 15, 20, 25, 30, Int.MAX_VALUE
)

/**
 * Snaps [value] to the nearest allowed timeout value in [STREAM_AUTO_PLAY_TIMEOUT_VALUES].
 * Ties break to the lower value. Negative values snap to 0.
 */
fun snapToAllowedTimeout(value: Int): Int {
    if (value <= 0) return 0
    var bestValue = STREAM_AUTO_PLAY_TIMEOUT_VALUES[0]
    var bestDistance = Long.MAX_VALUE
    for (allowed in STREAM_AUTO_PLAY_TIMEOUT_VALUES) {
        val distance = abs(value.toLong() - allowed.toLong())
        if (distance < bestDistance || (distance == bestDistance && allowed < bestValue)) {
            bestDistance = distance
            bestValue = allowed
        }
    }
    return bestValue
}

data class PlayerSettingsUiState(
    val useLegacyPlayerLayout: Boolean = false,
    val showPlayerTitleLogo: Boolean = false,
    val showLoadingOverlay: Boolean = true,
    val showPlayerLoadingStatus: Boolean = true,
    val pauseOverlayEnabled: Boolean = true,
    val showParentalGuide: Boolean = true,
    val resizeMode: PlayerResizeMode = PlayerResizeMode.Fit,
    val holdToSpeedEnabled: Boolean = true,
    val holdToSpeedValue: Float = 2f,
    val skipBackwardSeconds: Int = PLAYER_SKIP_SECONDS_DEFAULT,
    val skipForwardSeconds: Int = PLAYER_SKIP_SECONDS_DEFAULT,
    val toolbarLayout: PlayerToolbarLayout = PlayerToolbarLayout.Default,
    val legacyBottomButtons: LegacyBottomButtonLayout = LegacyPlayerButtonLayouts.DefaultBottom,
    val legacyTopButtons: LegacyTopButtonLayout = LegacyPlayerButtonLayouts.DefaultTop,
    val touchGesturesEnabled: Boolean = true,
    val swipeToSeekEnabled: Boolean = true,
    /** Round/pill glass backings behind player buttons, time pills and the legacy bottom bar. */
    val playerControlGlassEnabled: Boolean = true,
    val showSkipButtons: Boolean = true,
    val seekPreviewEnabled: Boolean = true,
    val movieRecommendationsEnabled: Boolean = true,
    val autoShowSubtitlesOnRewindEnabled: Boolean = true,
    val autoShowSubtitlesOnMuteEnabled: Boolean = true,
    val externalPlayerEnabled: Boolean = false,
    val externalPlayerForwardSubtitles: Boolean = false,
    val externalPlayerSendSkipSegments: Boolean = false,
    val externalPlayerId: String? = ExternalPlayerPlatform.defaultPlayerId(),
    val preferredAudioLanguage: String = AudioLanguageOption.DEVICE,
    val secondaryPreferredAudioLanguage: String? = null,
    val preferredSubtitleLanguage: String = SubtitleLanguageOption.NONE,
    val secondaryPreferredSubtitleLanguage: String? = null,
    val subtitleStyle: SubtitleStyleState = SubtitleStyleState.DEFAULT,
    val streamReuseLastLinkEnabled: Boolean = false,
    val streamReuseLastLinkCacheHours: Int = 24,
    val androidPlaybackEngine: AndroidPlaybackEngine = AndroidPlaybackEngine.ExoPlayer,
    val autoSwitchPlaybackEngineOnError: Boolean = true,
    val androidLibmpvVideoOutput: AndroidLibmpvVideoOutput = AndroidLibmpvVideoOutput.GpuNext,
    val androidLibmpvHardwareDecodingEnabled: Boolean = true,
    val androidLibmpvYuv420pEnabled: Boolean = false,
    val androidAnimeUpscaleEnabled: Boolean = false,
    val androidAnimeLineSharpenEnabled: Boolean = false,
    val androidExoChaptersEnabled: Boolean = false,
    val rememberAudioDelayPerDevice: Boolean = true,
    val persistAudioAmplification: Boolean = false,
    val audioAmplificationDb: Int = 0,
    val downmixEnabled: Boolean = false,
    val audioOutputChannels: AudioOutputChannels = AudioOutputChannels.default,
    val maintainOriginalAudioOnDownmix: Boolean = true,
    val centerMixLevelDb: Int = 0,
    val decoderPriority: Int = 1,
    val mapDV7ToHevc: Boolean = false,
    val tunnelingEnabled: Boolean = false,
    val androidAudioPassthroughEnabled: Boolean = false,
    val nightModeButtonEnabled: Boolean = true,
    val nightModeEnabled: Boolean = false,
    val exoNativeMemoryEnabled: Boolean = false,
    val customPlaybackBuffersEnabled: Boolean = false,
    val vodDiskCacheEnabled: Boolean = false,
    val vodDiskCacheAutoSize: Boolean = true,
    val playbackMinBufferSeconds: Int = 50,
    val playbackMaxBufferSeconds: Int = 50,
    val playbackStartBufferSeconds: Int = 3,
    val playbackRebufferSeconds: Int = 5,
    val playbackBackBufferSeconds: Int = 10,
    val playbackTargetBufferMb: Int = 256,
    val vodDiskCacheSizeMb: Int = 2048,
    val streamAutoPlayMode: StreamAutoPlayMode = StreamAutoPlayMode.MANUAL,
    val streamAutoPlaySource: StreamAutoPlaySource = StreamAutoPlaySource.ALL_SOURCES,
    val streamAutoPlaySelectedAddons: Set<String> = emptySet(),
    val streamAutoPlaySelectedPlugins: Set<String> = emptySet(),
    val streamAutoPlayRegex: String = "",
    val streamAutoPlayTimeoutSeconds: Int = 3,
    val skipIntroEnabled: Boolean = true,
    val skipIntroOffsetSeconds: Int = 0,
    val autoSkipSegmentTypes: Set<AutoSkipSegmentType> = emptySet(),
    val animeSkipEnabled: Boolean = false,
    val animeSkipClientId: String = "",
    val introDbApiKey: String = "",
    val introSubmitEnabled: Boolean = false,
    val streamAutoPlayNextEpisodeEnabled: Boolean = false,
    val streamAutoPlayNextEpisodeFallbackEnabled: Boolean = true,
    val streamAutoPlayPreferBingeGroup: Boolean = true,
    val streamAutoPlayReuseBingeGroup: Boolean = false,
    val nextEpisodeThresholdMode: NextEpisodeThresholdMode = NextEpisodeThresholdMode.PERCENTAGE,
    val nextEpisodeThresholdPercent: Float = 99f,
    val nextEpisodeThresholdMinutesBeforeEnd: Float = 2f,
    val preloadNextEpisodeSources: Boolean = false,
    val miniPlayerEnabled: Boolean = true,
    val privateViewingEnabled: Boolean = false,
    val useLibmpvAss: Boolean = false,
    val useLibass: Boolean = true,
    val libassRenderType: String = "CUES",
    val libassRenderScale: String = "FULL",
    val libassRenderMode: String = "OVERLAY",
    val iosVideoOutputPreset: IosVideoOutputPreset = IosVideoOutputPreset.NativeEdr,
    val iosToneMappingMode: IosToneMappingMode = IosToneMappingMode.Auto,
    val iosTargetPrimaries: IosTargetPrimaries = IosTargetPrimaries.Auto,
    val iosTargetTransfer: IosTargetTransfer = IosTargetTransfer.Auto,
    val iosHardwareDecoderMode: IosHardwareDecoderMode = IosHardwareDecoderMode.VideoToolbox,
    val iosAudioOutputMode: IosAudioOutputMode = IosAudioOutputMode.Auto,
    val iosExtendedDynamicRangeEnabled: Boolean = true,
    val iosTargetColorspaceHintEnabled: Boolean = true,
    val iosHdrComputePeakEnabled: Boolean = true,
    val iosDebandEnabled: Boolean = false,
    val iosInterpolationEnabled: Boolean = false,
    val iosBrightness: Int = 0,
    val iosContrast: Int = 0,
    val iosSaturation: Int = 0,
    val iosGamma: Int = 0,
)

object PlayerSettingsRepository {
    private val _uiState = MutableStateFlow(PlayerSettingsUiState())
    val uiState: StateFlow<PlayerSettingsUiState> = _uiState.asStateFlow()

    private var hasLoaded = false
    private var useLegacyPlayerLayout = false
    private var showPlayerTitleLogo = false
    private var showLoadingOverlay = true
    private var showPlayerLoadingStatus = true
    private var pauseOverlayEnabled = true
    private var showParentalGuide = true
    private var resizeMode = PlayerResizeMode.Fit
    private var holdToSpeedEnabled = true
    private var holdToSpeedValue = 2f
    private var skipBackwardSeconds = PLAYER_SKIP_SECONDS_DEFAULT
    private var skipForwardSeconds = PLAYER_SKIP_SECONDS_DEFAULT
    private var toolbarLayout = PlayerToolbarLayout.Default
    private var legacyBottomButtons = LegacyPlayerButtonLayouts.DefaultBottom
    private var legacyTopButtons = LegacyPlayerButtonLayouts.DefaultTop
    private var touchGesturesEnabled = true
    private var swipeToSeekEnabled = true
    private var playerControlGlassEnabled = true
    private var showSkipButtons = true
    private var seekPreviewEnabled = true
    private var movieRecommendationsEnabled = true
    private var autoShowSubtitlesOnRewindEnabled = true
    private var autoShowSubtitlesOnMuteEnabled = true
    private var externalPlayerEnabled = false
    private var externalPlayerForwardSubtitles = false
    private var externalPlayerSendSkipSegments = false
    private var externalPlayerId: String? = ExternalPlayerPlatform.defaultPlayerId()
    private var preferredAudioLanguage = AudioLanguageOption.DEVICE
    private var secondaryPreferredAudioLanguage: String? = null
    private var preferredSubtitleLanguage = SubtitleLanguageOption.NONE
    private var secondaryPreferredSubtitleLanguage: String? = null
    private var subtitleStyle = SubtitleStyleState.DEFAULT
    private var streamReuseLastLinkEnabled = false
    private var streamReuseLastLinkCacheHours = 24
    private var androidPlaybackEngine = AndroidPlaybackEngine.ExoPlayer
    private var autoSwitchPlaybackEngineOnError = true
    private var androidLibmpvVideoOutput = AndroidLibmpvVideoOutput.GpuNext
    private var androidLibmpvHardwareDecodingEnabled = true
    private var androidLibmpvYuv420pEnabled = false
    private var androidAnimeUpscaleEnabled = false
    private var androidAnimeLineSharpenEnabled = false
    private var androidExoChaptersEnabled = false
    private var rememberAudioDelayPerDevice = true
    private var persistAudioAmplification = false
    private var audioAmplificationDb = 0
    private var downmixEnabled = false
    private var audioOutputChannels = AudioOutputChannels.default
    private var maintainOriginalAudioOnDownmix = true
    private var centerMixLevelDb = 0
    private var decoderPriority = 1
    private var mapDV7ToHevc = false
    private var tunnelingEnabled = false
    private var androidAudioPassthroughEnabled = false
    private var nightModeButtonEnabled = true
    private var nightModeEnabled = false
    private var exoNativeMemoryEnabled = false
    private var customPlaybackBuffersEnabled = false
    private var vodDiskCacheEnabled = false
    private var vodDiskCacheAutoSize = true
    private var playbackMinBufferSeconds = 50
    private var playbackMaxBufferSeconds = 50
    private var playbackStartBufferSeconds = 3
    private var playbackRebufferSeconds = 5
    private var playbackBackBufferSeconds = 10
    private var playbackTargetBufferMb = 256
    private var vodDiskCacheSizeMb = 2048
    private var streamAutoPlayMode = StreamAutoPlayMode.MANUAL
    private var streamAutoPlaySource = StreamAutoPlaySource.ALL_SOURCES
    private var streamAutoPlaySelectedAddons: Set<String> = emptySet()
    private var streamAutoPlaySelectedPlugins: Set<String> = emptySet()
    private var streamAutoPlayRegex = ""
    private var streamAutoPlayTimeoutSeconds = 3
    private var skipIntroEnabled = true
    private var skipIntroOffsetSeconds = 0
    private var autoSkipSegmentTypes: Set<AutoSkipSegmentType> = emptySet()
    private var animeSkipEnabled = false
    private var animeSkipClientId = ""
    private var introDbApiKey = ""
    private var introSubmitEnabled = false
    private var streamAutoPlayNextEpisodeEnabled = false
    private var streamAutoPlayNextEpisodeFallbackEnabled = true
    private var streamAutoPlayPreferBingeGroup = true
    private var streamAutoPlayReuseBingeGroup = false
    private var nextEpisodeThresholdMode = NextEpisodeThresholdMode.PERCENTAGE
    private var nextEpisodeThresholdPercent = 99f
    private var nextEpisodeThresholdMinutesBeforeEnd = 2f
    private var preloadNextEpisodeSources = false
    private var miniPlayerEnabled = true
    private var privateViewingEnabled = false
    private var useLibmpvAss = false
    private var useLibass = true
    private var libassRenderType = "OVERLAY_OPEN_GL"
    private var libassRenderScale = "FULL"
    private var libassRenderMode = "OVERLAY"
    private var iosVideoOutputPreset = IosVideoOutputPreset.NativeEdr
    private var iosToneMappingMode = IosToneMappingMode.Auto
    private var iosTargetPrimaries = IosTargetPrimaries.Auto
    private var iosTargetTransfer = IosTargetTransfer.Auto
    private var iosHardwareDecoderMode = IosHardwareDecoderMode.VideoToolbox
    private var iosAudioOutputMode = IosAudioOutputMode.Auto
    private var iosExtendedDynamicRangeEnabled = true
    private var iosTargetColorspaceHintEnabled = true
    private var iosHdrComputePeakEnabled = true
    private var iosDebandEnabled = false
    private var iosInterpolationEnabled = false
    private var iosBrightness = 0
    private var iosContrast = 0
    private var iosSaturation = 0
    private var iosGamma = 0

    fun ensureLoaded() {
        if (hasLoaded) return
        loadFromDisk()
    }

    fun onProfileChanged() {
        loadFromDisk()
    }

    fun clearLocalState() {
        hasLoaded = false
        useLegacyPlayerLayout = false
        showPlayerTitleLogo = false
        showLoadingOverlay = true
        showPlayerLoadingStatus = true
        pauseOverlayEnabled = true
        showParentalGuide = true
        resizeMode = PlayerResizeMode.Fit
        holdToSpeedEnabled = true
        holdToSpeedValue = 2f
        skipBackwardSeconds = PLAYER_SKIP_SECONDS_DEFAULT
        skipForwardSeconds = PLAYER_SKIP_SECONDS_DEFAULT
        toolbarLayout = PlayerToolbarLayout.Default
        legacyBottomButtons = LegacyPlayerButtonLayouts.DefaultBottom
        legacyTopButtons = LegacyPlayerButtonLayouts.DefaultTop
        touchGesturesEnabled = true
        swipeToSeekEnabled = true
        playerControlGlassEnabled = true
        showSkipButtons = true
        seekPreviewEnabled = true
        movieRecommendationsEnabled = true
        autoShowSubtitlesOnRewindEnabled = true
        autoShowSubtitlesOnMuteEnabled = true
        externalPlayerEnabled = false
        externalPlayerForwardSubtitles = false
        externalPlayerSendSkipSegments = false
        externalPlayerId = ExternalPlayerPlatform.defaultPlayerId()
        preferredAudioLanguage = AudioLanguageOption.DEVICE
        secondaryPreferredAudioLanguage = null
        preferredSubtitleLanguage = SubtitleLanguageOption.NONE
        secondaryPreferredSubtitleLanguage = null
        subtitleStyle = SubtitleStyleState.DEFAULT
        streamReuseLastLinkEnabled = false
        streamReuseLastLinkCacheHours = 24
        androidPlaybackEngine = AndroidPlaybackEngine.ExoPlayer
        autoSwitchPlaybackEngineOnError = true
        androidLibmpvVideoOutput = AndroidLibmpvVideoOutput.GpuNext
        androidLibmpvHardwareDecodingEnabled = true
        androidLibmpvYuv420pEnabled = false
        androidAnimeUpscaleEnabled = false
        androidAnimeLineSharpenEnabled = false
        androidExoChaptersEnabled = false
        rememberAudioDelayPerDevice = true
        persistAudioAmplification = false
        audioAmplificationDb = 0
        downmixEnabled = false
        audioOutputChannels = AudioOutputChannels.default
        maintainOriginalAudioOnDownmix = true
        centerMixLevelDb = 0
        decoderPriority = 1
        mapDV7ToHevc = false
        tunnelingEnabled = false
        androidAudioPassthroughEnabled = false
        nightModeButtonEnabled = true
        nightModeEnabled = false
        exoNativeMemoryEnabled = false
        customPlaybackBuffersEnabled = false
        vodDiskCacheEnabled = false
        vodDiskCacheAutoSize = true
        playbackMinBufferSeconds = 50
        playbackMaxBufferSeconds = 50
        playbackStartBufferSeconds = 3
        playbackRebufferSeconds = 5
        playbackBackBufferSeconds = 10
        playbackTargetBufferMb = 256
        vodDiskCacheSizeMb = 2048
        streamAutoPlayMode = StreamAutoPlayMode.MANUAL
        streamAutoPlaySource = StreamAutoPlaySource.ALL_SOURCES
        streamAutoPlaySelectedAddons = emptySet()
        streamAutoPlaySelectedPlugins = emptySet()
        streamAutoPlayRegex = ""
        streamAutoPlayTimeoutSeconds = 3
        skipIntroEnabled = true
        skipIntroOffsetSeconds = 0
        autoSkipSegmentTypes = emptySet()
        animeSkipEnabled = false
        animeSkipClientId = ""
        introDbApiKey = ""
        introSubmitEnabled = false
        streamAutoPlayNextEpisodeEnabled = false
        streamAutoPlayNextEpisodeFallbackEnabled = true
        streamAutoPlayPreferBingeGroup = true
        streamAutoPlayReuseBingeGroup = false
        nextEpisodeThresholdMode = NextEpisodeThresholdMode.PERCENTAGE
        nextEpisodeThresholdPercent = 99f
        nextEpisodeThresholdMinutesBeforeEnd = 2f
        preloadNextEpisodeSources = false
        miniPlayerEnabled = true
        privateViewingEnabled = false
        useLibmpvAss = false
        useLibass = true
        libassRenderType = "OVERLAY_OPEN_GL"
        libassRenderScale = "FULL"
        libassRenderMode = "OVERLAY"
        iosVideoOutputPreset = IosVideoOutputPreset.NativeEdr
        iosToneMappingMode = IosToneMappingMode.Auto
        iosTargetPrimaries = IosTargetPrimaries.Auto
        iosTargetTransfer = IosTargetTransfer.Auto
        iosHardwareDecoderMode = IosHardwareDecoderMode.VideoToolbox
        iosAudioOutputMode = IosAudioOutputMode.Auto
        iosExtendedDynamicRangeEnabled = true
        iosTargetColorspaceHintEnabled = true
        iosHdrComputePeakEnabled = true
        iosDebandEnabled = false
        iosInterpolationEnabled = false
        iosBrightness = 0
        iosContrast = 0
        iosSaturation = 0
        iosGamma = 0
        publish()
    }

    private fun loadFromDisk() {
        hasLoaded = true
        useLegacyPlayerLayout = PlayerSettingsStorage.loadUseLegacyPlayerLayout() ?: false
        showPlayerTitleLogo = PlayerSettingsStorage.loadShowPlayerTitleLogo() ?: false
        showLoadingOverlay = PlayerSettingsStorage.loadShowLoadingOverlay() ?: true
        showPlayerLoadingStatus = PlayerSettingsStorage.loadShowPlayerLoadingStatus() ?: true
        pauseOverlayEnabled = PlayerSettingsStorage.loadPauseOverlayEnabled() ?: true
        showParentalGuide = PlayerSettingsStorage.loadShowParentalGuide() ?: true
        resizeMode = PlayerSettingsStorage.loadResizeMode()
            ?.let { runCatching { PlayerResizeMode.valueOf(it) }.getOrNull() }
            ?: PlayerResizeMode.Fit
        holdToSpeedEnabled = PlayerSettingsStorage.loadHoldToSpeedEnabled() ?: true
        holdToSpeedValue = PlayerSettingsStorage.loadHoldToSpeedValue() ?: 2f
        skipBackwardSeconds = normalizePlayerSkipSeconds(PlayerSettingsStorage.loadSkipBackwardSeconds())
        skipForwardSeconds = normalizePlayerSkipSeconds(PlayerSettingsStorage.loadSkipForwardSeconds())
        toolbarLayout = PlayerToolbarLayout.decode(PlayerSettingsStorage.loadToolbarLayout())
        legacyBottomButtons = LegacyPlayerButtonLayouts.decodeBottom(PlayerSettingsStorage.loadLegacyBottomButtons())
        legacyTopButtons = LegacyPlayerButtonLayouts.decodeTop(PlayerSettingsStorage.loadLegacyTopButtons())
        touchGesturesEnabled = PlayerSettingsStorage.loadTouchGesturesEnabled() ?: true
        swipeToSeekEnabled = PlayerSettingsStorage.loadSwipeToSeekEnabled() ?: true
        playerControlGlassEnabled = PlayerSettingsStorage.loadPlayerControlGlassEnabled() ?: true
        showSkipButtons = PlayerSettingsStorage.loadShowSkipButtons() ?: true
        seekPreviewEnabled = PlayerSettingsStorage.loadSeekPreviewEnabled() ?: true
        movieRecommendationsEnabled = PlayerSettingsStorage.loadMovieRecommendationsEnabled() ?: true
        autoShowSubtitlesOnRewindEnabled = PlayerSettingsStorage.loadAutoShowSubtitlesOnRewindEnabled() ?: true
        autoShowSubtitlesOnMuteEnabled = PlayerSettingsStorage.loadAutoShowSubtitlesOnMuteEnabled() ?: true
        externalPlayerEnabled = PlayerSettingsStorage.loadExternalPlayerEnabled() ?: false
        externalPlayerForwardSubtitles = PlayerSettingsStorage.loadExternalPlayerForwardSubtitles() ?: false
        externalPlayerSendSkipSegments = PlayerSettingsStorage.loadExternalPlayerSendSkipSegments() ?: false
        externalPlayerId = PlayerSettingsStorage.loadExternalPlayerId()
            ?: ExternalPlayerPlatform.defaultPlayerId()
        preferredAudioLanguage =
            normalizeLanguageCode(PlayerSettingsStorage.loadPreferredAudioLanguage())
                ?: AudioLanguageOption.DEVICE
        secondaryPreferredAudioLanguage =
            normalizeLanguageCode(PlayerSettingsStorage.loadSecondaryPreferredAudioLanguage())
        preferredSubtitleLanguage =
            normalizeLanguageCode(PlayerSettingsStorage.loadPreferredSubtitleLanguage())
                ?: SubtitleLanguageOption.NONE
        secondaryPreferredSubtitleLanguage =
            normalizeLanguageCode(PlayerSettingsStorage.loadSecondaryPreferredSubtitleLanguage())
        subtitleStyle = SubtitleStyleState(
            textColor = subtitleColorFromStorage(PlayerSettingsStorage.loadSubtitleTextColor())
                ?: SubtitleStyleState.DEFAULT.textColor,
            backgroundColor = subtitleColorFromStorage(PlayerSettingsStorage.loadSubtitleBackgroundColor())
                ?: SubtitleStyleState.DEFAULT.backgroundColor,
            outlineColor = subtitleColorFromStorage(PlayerSettingsStorage.loadSubtitleOutlineColor())
                ?: SubtitleStyleState.DEFAULT.outlineColor,
            outlineEnabled = PlayerSettingsStorage.loadSubtitleOutlineEnabled()
                ?: SubtitleStyleState.DEFAULT.outlineEnabled,
            outlineWidth = PlayerSettingsStorage.loadSubtitleOutlineWidth()
                ?: SubtitleStyleState.DEFAULT.outlineWidth,
            bold = PlayerSettingsStorage.loadSubtitleBold()
                ?: SubtitleStyleState.DEFAULT.bold,
            fontSizeSp = (PlayerSettingsStorage.loadSubtitleFontSizeSp()
                ?: SubtitleStyleState.DEFAULT.fontSizeSp).coerceIn(subtitleFontSizeRangeSp),
            bottomOffset = PlayerSettingsStorage.loadSubtitleBottomOffset()
                ?: SubtitleStyleState.DEFAULT.bottomOffset,
            stripSdh = PlayerSettingsStorage.loadSubtitleStripSdh()
                ?: SubtitleStyleState.DEFAULT.stripSdh,
            useForcedSubtitles = PlayerSettingsStorage.loadSubtitleUseForcedSubtitles()
                ?: SubtitleStyleState.DEFAULT.useForcedSubtitles,
            showOnlyPreferredLanguages = PlayerSettingsStorage.loadSubtitleShowOnlyPreferredLanguages()
                ?: SubtitleStyleState.DEFAULT.showOnlyPreferredLanguages,
            appearance = decodeSubtitleAppearance(PlayerSettingsStorage.loadSubtitleAppearance()),
        )
        streamReuseLastLinkEnabled = PlayerSettingsStorage.loadStreamReuseLastLinkEnabled() ?: false
        streamReuseLastLinkCacheHours = PlayerSettingsStorage.loadStreamReuseLastLinkCacheHours() ?: 24
        androidPlaybackEngine = PlayerSettingsStorage.loadAndroidPlaybackEngine()
            ?.let { runCatching { AndroidPlaybackEngine.valueOf(it) }.getOrNull() }
            ?: AndroidPlaybackEngine.ExoPlayer
        autoSwitchPlaybackEngineOnError = PlayerSettingsStorage.loadAutoSwitchPlaybackEngineOnError() ?: true
        androidLibmpvVideoOutput = PlayerSettingsStorage.loadAndroidLibmpvVideoOutput()
            ?.let { runCatching { AndroidLibmpvVideoOutput.valueOf(it) }.getOrNull() }
            ?: AndroidLibmpvVideoOutput.GpuNext
        androidLibmpvHardwareDecodingEnabled = PlayerSettingsStorage.loadAndroidLibmpvHardwareDecodingEnabled() ?: true
        androidLibmpvYuv420pEnabled = PlayerSettingsStorage.loadAndroidLibmpvYuv420pEnabled() ?: false
        androidAnimeUpscaleEnabled = PlayerSettingsStorage.loadAndroidAnimeUpscaleEnabled() ?: false
        androidAnimeLineSharpenEnabled = PlayerSettingsStorage.loadAndroidAnimeLineSharpenEnabled() ?: false
        androidExoChaptersEnabled = PlayerSettingsStorage.loadAndroidExoChaptersEnabled() ?: false
        rememberAudioDelayPerDevice = PlayerSettingsStorage.loadRememberAudioDelayPerDevice() ?: true
        persistAudioAmplification = PlayerSettingsStorage.loadPersistAudioAmplification() ?: false
        audioAmplificationDb = (PlayerSettingsStorage.loadAudioAmplificationDb() ?: 0)
            .coerceIn(AUDIO_AMPLIFICATION_MIN_DB, AUDIO_AMPLIFICATION_MAX_DB)
        downmixEnabled = PlayerSettingsStorage.loadDownmixEnabled() ?: false
        audioOutputChannels = AudioOutputChannels.fromSettingValue(PlayerSettingsStorage.loadAudioOutputChannels())
        maintainOriginalAudioOnDownmix = PlayerSettingsStorage.loadMaintainOriginalAudioOnDownmix() ?: true
        centerMixLevelDb = (PlayerSettingsStorage.loadCenterMixLevelDb() ?: 0)
            .coerceIn(CENTER_MIX_LEVEL_MIN_DB, CENTER_MIX_LEVEL_MAX_DB)
        decoderPriority = PlayerSettingsStorage.loadDecoderPriority() ?: 1
        mapDV7ToHevc = PlayerSettingsStorage.loadMapDV7ToHevc() ?: false
        tunnelingEnabled = PlayerSettingsStorage.loadTunnelingEnabled() ?: false
        androidAudioPassthroughEnabled = PlayerSettingsStorage.loadAndroidAudioPassthroughEnabled() ?: false
        nightModeButtonEnabled = PlayerSettingsStorage.loadNightModeButtonEnabled() ?: true
        nightModeEnabled = PlayerSettingsStorage.loadNightModeEnabled() ?: false
        exoNativeMemoryEnabled = PlayerSettingsStorage.loadExoNativeMemoryEnabled() ?: false
        customPlaybackBuffersEnabled = PlayerSettingsStorage.loadCustomPlaybackBuffersEnabled() ?: false
        vodDiskCacheEnabled = PlayerSettingsStorage.loadVodDiskCacheEnabled() ?: false
        vodDiskCacheAutoSize = PlayerSettingsStorage.loadVodDiskCacheAutoSize() ?: true
        playbackMinBufferSeconds = PlayerSettingsStorage.loadPlaybackMinBufferSeconds() ?: 50
        playbackMaxBufferSeconds = PlayerSettingsStorage.loadPlaybackMaxBufferSeconds() ?: 50
        playbackStartBufferSeconds = PlayerSettingsStorage.loadPlaybackStartBufferSeconds() ?: 3
        playbackRebufferSeconds = PlayerSettingsStorage.loadPlaybackRebufferSeconds() ?: 5
        playbackBackBufferSeconds = PlayerSettingsStorage.loadPlaybackBackBufferSeconds() ?: 10
        playbackTargetBufferMb = PlayerSettingsStorage.loadPlaybackTargetBufferMb() ?: 256
        vodDiskCacheSizeMb = PlayerSettingsStorage.loadVodDiskCacheSizeMb() ?: 2048
        streamAutoPlayMode = PlayerSettingsStorage.loadStreamAutoPlayMode()
            ?.let { runCatching { StreamAutoPlayMode.valueOf(it) }.getOrNull() }
            ?: StreamAutoPlayMode.MANUAL
        streamAutoPlaySource = PlayerSettingsStorage.loadStreamAutoPlaySource()
            ?.let { runCatching { StreamAutoPlaySource.valueOf(it) }.getOrNull() }
            ?: StreamAutoPlaySource.ALL_SOURCES
        streamAutoPlaySelectedAddons = PlayerSettingsStorage.loadStreamAutoPlaySelectedAddons() ?: emptySet()
        streamAutoPlaySelectedPlugins = PlayerSettingsStorage.loadStreamAutoPlaySelectedPlugins() ?: emptySet()
        if (!AppFeaturePolicy.pluginsEnabled) {
            val normalizedSource = normalizeStreamAutoPlaySource(streamAutoPlaySource)
            if (normalizedSource != streamAutoPlaySource) {
                streamAutoPlaySource = normalizedSource
                PlayerSettingsStorage.saveStreamAutoPlaySource(normalizedSource.name)
            }
            if (streamAutoPlaySelectedPlugins.isNotEmpty()) {
                streamAutoPlaySelectedPlugins = emptySet()
                PlayerSettingsStorage.saveStreamAutoPlaySelectedPlugins(emptySet())
            }
        }
        streamAutoPlayRegex = PlayerSettingsStorage.loadStreamAutoPlayRegex() ?: ""
        streamAutoPlayTimeoutSeconds = PlayerSettingsStorage.loadStreamAutoPlayTimeoutSeconds() ?: 3
        // Legacy migration: 11 was the old sentinel for "unlimited"
        if (streamAutoPlayTimeoutSeconds == 11) {
            streamAutoPlayTimeoutSeconds = Int.MAX_VALUE
            PlayerSettingsStorage.saveStreamAutoPlayTimeoutSeconds(streamAutoPlayTimeoutSeconds)
        } else if (streamAutoPlayTimeoutSeconds !in STREAM_AUTO_PLAY_TIMEOUT_VALUES) {
            streamAutoPlayTimeoutSeconds = snapToAllowedTimeout(streamAutoPlayTimeoutSeconds)
            PlayerSettingsStorage.saveStreamAutoPlayTimeoutSeconds(streamAutoPlayTimeoutSeconds)
        }
        skipIntroEnabled = PlayerSettingsStorage.loadSkipIntroEnabled() ?: true
        skipIntroOffsetSeconds = normalizeSkipIntroOffsetSeconds(PlayerSettingsStorage.loadSkipIntroOffsetSeconds())
        autoSkipSegmentTypes = PlayerSettingsStorage.loadAutoSkipSegmentTypes()
            ?.mapNotNull(AutoSkipSegmentType::fromStoredValue)?.toSet() ?: buildSet {
                if (PlayerSettingsStorage.loadAutoSkipMovieCredits() == true) add(AutoSkipSegmentType.MOVIE_CREDITS)
            }
        animeSkipEnabled = PlayerSettingsStorage.loadAnimeSkipEnabled() ?: false
        animeSkipClientId = PlayerSettingsStorage.loadAnimeSkipClientId() ?: ""
        introDbApiKey = PlayerSettingsStorage.loadIntroDbApiKey() ?: ""
        introSubmitEnabled = PlayerSettingsStorage.loadIntroSubmitEnabled() ?: false
        streamAutoPlayNextEpisodeEnabled = PlayerSettingsStorage.loadStreamAutoPlayNextEpisodeEnabled() ?: false
        streamAutoPlayNextEpisodeFallbackEnabled = PlayerSettingsStorage.loadStreamAutoPlayNextEpisodeFallbackEnabled() ?: true
        streamAutoPlayPreferBingeGroup = PlayerSettingsStorage.loadStreamAutoPlayPreferBingeGroup() ?: true
        streamAutoPlayReuseBingeGroup = PlayerSettingsStorage.loadStreamAutoPlayReuseBingeGroup() ?: false
        nextEpisodeThresholdMode = PlayerSettingsStorage.loadNextEpisodeThresholdMode()
            ?.let { runCatching { NextEpisodeThresholdMode.valueOf(it) }.getOrNull() }
            ?: NextEpisodeThresholdMode.PERCENTAGE
        nextEpisodeThresholdPercent = PlayerSettingsStorage.loadNextEpisodeThresholdPercent() ?: 99f
        nextEpisodeThresholdMinutesBeforeEnd = PlayerSettingsStorage.loadNextEpisodeThresholdMinutesBeforeEnd() ?: 2f
        preloadNextEpisodeSources = PlayerSettingsStorage.loadPreloadNextEpisodeSources() ?: false
        miniPlayerEnabled = PlayerSettingsStorage.loadMiniPlayerEnabled() ?: true
        privateViewingEnabled = PlayerSettingsStorage.loadPrivateViewingEnabled() ?: false
        useLibmpvAss = PlayerSettingsStorage.loadUseLibmpvAss() ?: false
        useLibass = PlayerSettingsStorage.loadUseLibass() ?: true
        libassRenderType = PlayerSettingsStorage.loadLibassRenderType() ?: "CUES"
        libassRenderScale = PlayerSettingsStorage.loadLibassRenderScale() ?: "FULL"
        libassRenderMode = PlayerSettingsStorage.loadLibassRenderMode() ?: "OVERLAY"
        iosVideoOutputPreset = PlayerSettingsStorage.loadIosVideoOutputPreset()
            ?.let { runCatching { IosVideoOutputPreset.valueOf(it) }.getOrNull() }
            ?: IosVideoOutputPreset.NativeEdr
        iosToneMappingMode = PlayerSettingsStorage.loadIosToneMappingMode()
            ?.let { runCatching { IosToneMappingMode.valueOf(it) }.getOrNull() }
            ?: IosToneMappingMode.Auto
        iosTargetPrimaries = PlayerSettingsStorage.loadIosTargetPrimaries()
            ?.let { runCatching { IosTargetPrimaries.valueOf(it) }.getOrNull() }
            ?: IosTargetPrimaries.Auto
        iosTargetTransfer = PlayerSettingsStorage.loadIosTargetTransfer()
            ?.let { runCatching { IosTargetTransfer.valueOf(it) }.getOrNull() }
            ?: IosTargetTransfer.Auto
        iosHardwareDecoderMode = PlayerSettingsStorage.loadIosHardwareDecoderMode()
            ?.let { runCatching { IosHardwareDecoderMode.valueOf(it) }.getOrNull() }
            ?: IosHardwareDecoderMode.VideoToolbox
        iosAudioOutputMode = IosAudioOutputMode.fromStoredName(PlayerSettingsStorage.loadIosAudioOutputMode())
        iosExtendedDynamicRangeEnabled = PlayerSettingsStorage.loadIosExtendedDynamicRangeEnabled() ?: true
        iosTargetColorspaceHintEnabled = PlayerSettingsStorage.loadIosTargetColorspaceHintEnabled() ?: true
        iosHdrComputePeakEnabled = PlayerSettingsStorage.loadIosHdrComputePeakEnabled() ?: true
        iosDebandEnabled = PlayerSettingsStorage.loadIosDebandEnabled() ?: false
        iosInterpolationEnabled = PlayerSettingsStorage.loadIosInterpolationEnabled() ?: false
        iosBrightness = PlayerSettingsStorage.loadIosBrightness() ?: 0
        iosContrast = PlayerSettingsStorage.loadIosContrast() ?: 0
        iosSaturation = PlayerSettingsStorage.loadIosSaturation() ?: 0
        iosGamma = PlayerSettingsStorage.loadIosGamma() ?: 0
        publish()
    }

    fun setUseLegacyPlayerLayout(enabled: Boolean) {
        ensureLoaded()
        if (useLegacyPlayerLayout == enabled) return
        useLegacyPlayerLayout = enabled
        publish()
        PlayerSettingsStorage.saveUseLegacyPlayerLayout(enabled)
    }

    fun setShowPlayerTitleLogo(enabled: Boolean) {
        ensureLoaded()
        if (showPlayerTitleLogo == enabled) return
        showPlayerTitleLogo = enabled
        publish()
        PlayerSettingsStorage.saveShowPlayerTitleLogo(enabled)
    }

    fun setShowLoadingOverlay(enabled: Boolean) {
        ensureLoaded()
        if (showLoadingOverlay == enabled) return
        showLoadingOverlay = enabled
        publish()
        PlayerSettingsStorage.saveShowLoadingOverlay(enabled)
    }

    fun setShowPlayerLoadingStatus(enabled: Boolean) {
        ensureLoaded()
        if (showPlayerLoadingStatus == enabled) return
        showPlayerLoadingStatus = enabled
        publish()
        PlayerSettingsStorage.saveShowPlayerLoadingStatus(enabled)
    }

    fun setPauseOverlayEnabled(enabled: Boolean) {
        ensureLoaded()
        if (pauseOverlayEnabled == enabled) return
        pauseOverlayEnabled = enabled
        publish()
        PlayerSettingsStorage.savePauseOverlayEnabled(enabled)
    }

    fun setShowParentalGuide(enabled: Boolean) {
        ensureLoaded()
        if (showParentalGuide == enabled) return
        showParentalGuide = enabled
        publish()
        PlayerSettingsStorage.saveShowParentalGuide(enabled)
    }

    fun setResizeMode(mode: PlayerResizeMode) {
        ensureLoaded()
        if (resizeMode == mode) return
        resizeMode = mode
        publish()
        PlayerSettingsStorage.saveResizeMode(mode.name)
    }

    fun setHoldToSpeedEnabled(enabled: Boolean) {
        ensureLoaded()
        if (holdToSpeedEnabled == enabled) return
        holdToSpeedEnabled = enabled
        publish()
        PlayerSettingsStorage.saveHoldToSpeedEnabled(enabled)
    }

    fun setHoldToSpeedValue(speed: Float) {
        ensureLoaded()
        val normalized = speed.coerceIn(1f, 4f)
        if (holdToSpeedValue == normalized) return
        holdToSpeedValue = normalized
        publish()
        PlayerSettingsStorage.saveHoldToSpeedValue(normalized)
    }

    fun setSkipBackwardSeconds(seconds: Int) {
        ensureLoaded()
        val normalized = normalizePlayerSkipSeconds(seconds)
        if (skipBackwardSeconds == normalized) return
        skipBackwardSeconds = normalized
        publish()
        PlayerSettingsStorage.saveSkipBackwardSeconds(normalized)
    }

    fun setSkipForwardSeconds(seconds: Int) {
        ensureLoaded()
        val normalized = normalizePlayerSkipSeconds(seconds)
        if (skipForwardSeconds == normalized) return
        skipForwardSeconds = normalized
        publish()
        PlayerSettingsStorage.saveSkipForwardSeconds(normalized)
    }

    fun setToolbarLayout(layout: PlayerToolbarLayout) {
        ensureLoaded()
        if (toolbarLayout == layout) return
        toolbarLayout = layout
        publish()
        PlayerSettingsStorage.saveToolbarLayout(layout.encode())
    }

    fun setLegacyBottomButtons(layout: LegacyBottomButtonLayout) {
        ensureLoaded()
        if (legacyBottomButtons == layout) return
        legacyBottomButtons = layout
        publish()
        PlayerSettingsStorage.saveLegacyBottomButtons(LegacyPlayerButtonLayouts.encodeBottom(layout))
    }

    fun setLegacyTopButtons(layout: LegacyTopButtonLayout) {
        ensureLoaded()
        if (legacyTopButtons == layout) return
        legacyTopButtons = layout
        publish()
        PlayerSettingsStorage.saveLegacyTopButtons(LegacyPlayerButtonLayouts.encodeTop(layout))
    }

    fun setTouchGesturesEnabled(enabled: Boolean) {
        ensureLoaded()
        if (touchGesturesEnabled == enabled) return
        touchGesturesEnabled = enabled
        publish()
        PlayerSettingsStorage.saveTouchGesturesEnabled(enabled)
    }

    fun setSwipeToSeekEnabled(enabled: Boolean) {
        ensureLoaded()
        if (swipeToSeekEnabled == enabled) return
        swipeToSeekEnabled = enabled
        publish()
        PlayerSettingsStorage.saveSwipeToSeekEnabled(enabled)
    }

    fun setPlayerControlGlassEnabled(enabled: Boolean) {
        ensureLoaded()
        if (playerControlGlassEnabled == enabled) return
        playerControlGlassEnabled = enabled
        publish()
        PlayerSettingsStorage.savePlayerControlGlassEnabled(enabled)
    }

    fun setShowSkipButtons(enabled: Boolean) {
        ensureLoaded()
        if (showSkipButtons == enabled) return
        showSkipButtons = enabled
        publish()
        PlayerSettingsStorage.saveShowSkipButtons(enabled)
    }

    fun setSeekPreviewEnabled(enabled: Boolean) {
        ensureLoaded()
        if (seekPreviewEnabled == enabled) return
        seekPreviewEnabled = enabled
        publish()
        PlayerSettingsStorage.saveSeekPreviewEnabled(enabled)
    }

    fun setMovieRecommendationsEnabled(enabled: Boolean) {
        ensureLoaded()
        if (movieRecommendationsEnabled == enabled) return
        movieRecommendationsEnabled = enabled
        publish()
        PlayerSettingsStorage.saveMovieRecommendationsEnabled(enabled)
    }

    fun setAutoShowSubtitlesOnRewindEnabled(enabled: Boolean) {
        ensureLoaded()
        if (autoShowSubtitlesOnRewindEnabled == enabled) return
        autoShowSubtitlesOnRewindEnabled = enabled
        publish()
        PlayerSettingsStorage.saveAutoShowSubtitlesOnRewindEnabled(enabled)
    }

    fun setAutoShowSubtitlesOnMuteEnabled(enabled: Boolean) {
        ensureLoaded()
        if (autoShowSubtitlesOnMuteEnabled == enabled) return
        autoShowSubtitlesOnMuteEnabled = enabled
        publish()
        PlayerSettingsStorage.saveAutoShowSubtitlesOnMuteEnabled(enabled)
    }

    fun setExternalPlayerEnabled(enabled: Boolean) {
        ensureLoaded()
        if (enabled && externalPlayerId.isNullOrBlank()) {
            externalPlayerId = ExternalPlayerPlatform.defaultPlayerId()
                ?: ExternalPlayerPlatform.availablePlayers().firstOrNull()?.id
            PlayerSettingsStorage.saveExternalPlayerId(externalPlayerId)
        }
        if (externalPlayerEnabled == enabled) {
            publish()
            return
        }
        externalPlayerEnabled = enabled
        publish()
        PlayerSettingsStorage.saveExternalPlayerEnabled(enabled)
    }

    fun setExternalPlayerId(playerId: String?) {
        ensureLoaded()
        val normalized = playerId?.takeIf { it.isNotBlank() }
        if (externalPlayerId == normalized) return
        externalPlayerId = normalized
        publish()
        PlayerSettingsStorage.saveExternalPlayerId(normalized)
    }

    fun setExternalPlayerForwardSubtitles(enabled: Boolean) {
        ensureLoaded()
        if (externalPlayerForwardSubtitles == enabled) return
        externalPlayerForwardSubtitles = enabled
        publish()
        PlayerSettingsStorage.saveExternalPlayerForwardSubtitles(enabled)
    }

    fun setExternalPlayerSendSkipSegments(enabled: Boolean) {
        ensureLoaded()
        if (externalPlayerSendSkipSegments == enabled) return
        externalPlayerSendSkipSegments = enabled
        publish()
        PlayerSettingsStorage.saveExternalPlayerSendSkipSegments(enabled)
    }

    fun setPreferredAudioLanguage(language: String) {
        ensureLoaded()
        val normalized = normalizeLanguageCode(language) ?: AudioLanguageOption.DEVICE
        if (preferredAudioLanguage == normalized) return
        preferredAudioLanguage = normalized
        publish()
        PlayerSettingsStorage.savePreferredAudioLanguage(normalized)
    }

    fun setSecondaryPreferredAudioLanguage(language: String?) {
        ensureLoaded()
        val normalized = normalizeLanguageCode(language)
        if (secondaryPreferredAudioLanguage == normalized) return
        secondaryPreferredAudioLanguage = normalized
        publish()
        PlayerSettingsStorage.saveSecondaryPreferredAudioLanguage(normalized)
    }

    fun setPreferredSubtitleLanguage(language: String) {
        ensureLoaded()
        val normalized = normalizeLanguageCode(language) ?: SubtitleLanguageOption.NONE
        if (preferredSubtitleLanguage == normalized) return
        preferredSubtitleLanguage = normalized
        publish()
        PlayerSettingsStorage.savePreferredSubtitleLanguage(normalized)
    }

    fun setSecondaryPreferredSubtitleLanguage(language: String?) {
        ensureLoaded()
        val normalized = normalizeLanguageCode(language)
        if (secondaryPreferredSubtitleLanguage == normalized) return
        secondaryPreferredSubtitleLanguage = normalized
        publish()
        PlayerSettingsStorage.saveSecondaryPreferredSubtitleLanguage(normalized)
    }

    fun setSubtitleStyle(style: SubtitleStyleState) {
        ensureLoaded()
        val normalized = style.copy(
            fontSizeSp = style.fontSizeSp.coerceIn(subtitleFontSizeRangeSp),
            bottomOffset = style.bottomOffset.coerceIn(0, 200),
            appearance = style.appearance.normalized(),
        )
        if (subtitleStyle == normalized) return
        subtitleStyle = normalized
        publish()
        PlayerSettingsStorage.saveSubtitleTextColor(normalized.textColor.toStorageHexString())
        PlayerSettingsStorage.saveSubtitleBackgroundColor(normalized.backgroundColor.toStorageHexString())
        PlayerSettingsStorage.saveSubtitleAppearance(normalized.appearance.encode())
        PlayerSettingsStorage.saveSubtitleOutlineColor(normalized.outlineColor.toStorageHexString())
        PlayerSettingsStorage.saveSubtitleOutlineEnabled(normalized.outlineEnabled)
        PlayerSettingsStorage.saveSubtitleOutlineWidth(normalized.outlineWidth)
        PlayerSettingsStorage.saveSubtitleBold(normalized.bold)
        PlayerSettingsStorage.saveSubtitleFontSizeSp(normalized.fontSizeSp)
        PlayerSettingsStorage.saveSubtitleBottomOffset(normalized.bottomOffset)
        PlayerSettingsStorage.saveSubtitleStripSdh(normalized.stripSdh)
        PlayerSettingsStorage.saveSubtitleUseForcedSubtitles(normalized.useForcedSubtitles)
        PlayerSettingsStorage.saveSubtitleShowOnlyPreferredLanguages(normalized.showOnlyPreferredLanguages)
    }

    fun setStreamReuseLastLinkEnabled(enabled: Boolean) {
        ensureLoaded()
        if (streamReuseLastLinkEnabled == enabled) return
        streamReuseLastLinkEnabled = enabled
        publish()
        PlayerSettingsStorage.saveStreamReuseLastLinkEnabled(enabled)
    }

    fun setStreamReuseLastLinkCacheHours(hours: Int) {
        ensureLoaded()
        if (streamReuseLastLinkCacheHours == hours) return
        streamReuseLastLinkCacheHours = hours
        publish()
        PlayerSettingsStorage.saveStreamReuseLastLinkCacheHours(hours)
    }

    fun setAndroidPlaybackEngine(engine: AndroidPlaybackEngine) {
        ensureLoaded()
        if (androidPlaybackEngine == engine) return
        androidPlaybackEngine = engine
        publish()
        PlayerSettingsStorage.saveAndroidPlaybackEngine(engine.name)
    }

    fun setAutoSwitchPlaybackEngineOnError(enabled: Boolean) {
        ensureLoaded()
        if (autoSwitchPlaybackEngineOnError == enabled) return
        autoSwitchPlaybackEngineOnError = enabled
        publish()
        PlayerSettingsStorage.saveAutoSwitchPlaybackEngineOnError(enabled)
    }

    fun setAndroidLibmpvVideoOutput(output: AndroidLibmpvVideoOutput) {
        ensureLoaded()
        if (androidLibmpvVideoOutput == output) return
        androidLibmpvVideoOutput = output
        publish()
        PlayerSettingsStorage.saveAndroidLibmpvVideoOutput(output.name)
    }

    fun setAndroidLibmpvHardwareDecodingEnabled(enabled: Boolean) {
        ensureLoaded()
        if (androidLibmpvHardwareDecodingEnabled == enabled) return
        androidLibmpvHardwareDecodingEnabled = enabled
        publish()
        PlayerSettingsStorage.saveAndroidLibmpvHardwareDecodingEnabled(enabled)
    }

    fun setAndroidLibmpvYuv420pEnabled(enabled: Boolean) {
        ensureLoaded()
        if (androidLibmpvYuv420pEnabled == enabled) return
        androidLibmpvYuv420pEnabled = enabled
        publish()
        PlayerSettingsStorage.saveAndroidLibmpvYuv420pEnabled(enabled)
    }

    fun setAndroidAnimeUpscaleEnabled(enabled: Boolean) {
        ensureLoaded()
        if (androidAnimeUpscaleEnabled == enabled) return
        androidAnimeUpscaleEnabled = enabled
        publish()
        PlayerSettingsStorage.saveAndroidAnimeUpscaleEnabled(enabled)
    }

    fun setAndroidAnimeLineSharpenEnabled(enabled: Boolean) {
        ensureLoaded()
        if (androidAnimeLineSharpenEnabled == enabled) return
        androidAnimeLineSharpenEnabled = enabled
        publish()
        PlayerSettingsStorage.saveAndroidAnimeLineSharpenEnabled(enabled)
    }

    fun setAndroidExoChaptersEnabled(enabled: Boolean) {
        ensureLoaded()
        if (androidExoChaptersEnabled == enabled) return
        androidExoChaptersEnabled = enabled
        publish()
        PlayerSettingsStorage.saveAndroidExoChaptersEnabled(enabled)
    }

    fun setRememberAudioDelayPerDevice(enabled: Boolean) {
        ensureLoaded()
        if (rememberAudioDelayPerDevice == enabled) return
        rememberAudioDelayPerDevice = enabled
        publish()
        PlayerSettingsStorage.saveRememberAudioDelayPerDevice(enabled)
    }

    fun setPersistAudioAmplification(enabled: Boolean) {
        ensureLoaded()
        if (persistAudioAmplification == enabled) return
        persistAudioAmplification = enabled
        publish()
        PlayerSettingsStorage.savePersistAudioAmplification(enabled)
    }

    fun setAudioAmplificationDb(db: Int) {
        ensureLoaded()
        val clamped = db.coerceIn(AUDIO_AMPLIFICATION_MIN_DB, AUDIO_AMPLIFICATION_MAX_DB)
        if (audioAmplificationDb == clamped) return
        audioAmplificationDb = clamped
        publish()
        PlayerSettingsStorage.saveAudioAmplificationDb(clamped)
    }

    fun setDownmixEnabled(enabled: Boolean) {
        ensureLoaded()
        if (downmixEnabled == enabled) return
        downmixEnabled = enabled
        publish()
        PlayerSettingsStorage.saveDownmixEnabled(enabled)
    }

    fun setAudioOutputChannels(channels: AudioOutputChannels) {
        ensureLoaded()
        if (audioOutputChannels == channels) return
        audioOutputChannels = channels
        publish()
        PlayerSettingsStorage.saveAudioOutputChannels(channels.settingValue)
    }

    fun setMaintainOriginalAudioOnDownmix(enabled: Boolean) {
        ensureLoaded()
        if (maintainOriginalAudioOnDownmix == enabled) return
        maintainOriginalAudioOnDownmix = enabled
        publish()
        PlayerSettingsStorage.saveMaintainOriginalAudioOnDownmix(enabled)
    }

    fun setCenterMixLevelDb(db: Int) {
        ensureLoaded()
        val clamped = db.coerceIn(CENTER_MIX_LEVEL_MIN_DB, CENTER_MIX_LEVEL_MAX_DB)
        if (centerMixLevelDb == clamped) return
        centerMixLevelDb = clamped
        publish()
        PlayerSettingsStorage.saveCenterMixLevelDb(clamped)
    }

    fun setDecoderPriority(priority: Int) {
        ensureLoaded()
        if (decoderPriority == priority) return
        decoderPriority = priority
        publish()
        PlayerSettingsStorage.saveDecoderPriority(priority)
    }

    fun setMapDV7ToHevc(enabled: Boolean) {
        ensureLoaded()
        if (mapDV7ToHevc == enabled) return
        mapDV7ToHevc = enabled
        publish()
        PlayerSettingsStorage.saveMapDV7ToHevc(enabled)
    }

    fun setTunnelingEnabled(enabled: Boolean) {
        ensureLoaded()
        if (tunnelingEnabled == enabled) return
        tunnelingEnabled = enabled
        publish()
        PlayerSettingsStorage.saveTunnelingEnabled(enabled)
    }

    fun setAndroidAudioPassthroughEnabled(enabled: Boolean) {
        ensureLoaded()
        if (androidAudioPassthroughEnabled == enabled) return
        androidAudioPassthroughEnabled = enabled
        publish()
        PlayerSettingsStorage.saveAndroidAudioPassthroughEnabled(enabled)
    }

    fun setNightModeButtonEnabled(enabled: Boolean) {
        ensureLoaded()
        if (nightModeButtonEnabled == enabled) return
        nightModeButtonEnabled = enabled
        publish()
        PlayerSettingsStorage.saveNightModeButtonEnabled(enabled)
    }

    fun setNightModeEnabled(enabled: Boolean) {
        ensureLoaded()
        if (nightModeEnabled == enabled) return
        nightModeEnabled = enabled
        publish()
        PlayerSettingsStorage.saveNightModeEnabled(enabled)
    }

    fun setExoNativeMemoryEnabled(value: Boolean) {
        ensureLoaded()
        if (exoNativeMemoryEnabled == value) return
        exoNativeMemoryEnabled = value
        publish()
        PlayerSettingsStorage.saveExoNativeMemoryEnabled(value)
    }

    fun setCustomPlaybackBuffersEnabled(value: Boolean) {
        ensureLoaded()
        if (customPlaybackBuffersEnabled == value) return
        customPlaybackBuffersEnabled = value
        publish()
        PlayerSettingsStorage.saveCustomPlaybackBuffersEnabled(value)
    }

    fun setVodDiskCacheEnabled(value: Boolean) {
        ensureLoaded()
        if (vodDiskCacheEnabled == value) return
        vodDiskCacheEnabled = value
        publish()
        PlayerSettingsStorage.saveVodDiskCacheEnabled(value)
    }

    fun setVodDiskCacheAutoSize(value: Boolean) {
        ensureLoaded()
        if (vodDiskCacheAutoSize == value) return
        vodDiskCacheAutoSize = value
        publish()
        PlayerSettingsStorage.saveVodDiskCacheAutoSize(value)
    }

    fun setPlaybackMinBufferSeconds(value: Int) {
        ensureLoaded()
        if (playbackMinBufferSeconds == value) return
        playbackMinBufferSeconds = value
        publish()
        PlayerSettingsStorage.savePlaybackMinBufferSeconds(value)
    }

    fun setPlaybackMaxBufferSeconds(value: Int) {
        ensureLoaded()
        if (playbackMaxBufferSeconds == value) return
        playbackMaxBufferSeconds = value
        publish()
        PlayerSettingsStorage.savePlaybackMaxBufferSeconds(value)
    }

    fun setPlaybackStartBufferSeconds(value: Int) {
        ensureLoaded()
        if (playbackStartBufferSeconds == value) return
        playbackStartBufferSeconds = value
        publish()
        PlayerSettingsStorage.savePlaybackStartBufferSeconds(value)
    }

    fun setPlaybackRebufferSeconds(value: Int) {
        ensureLoaded()
        if (playbackRebufferSeconds == value) return
        playbackRebufferSeconds = value
        publish()
        PlayerSettingsStorage.savePlaybackRebufferSeconds(value)
    }

    fun setPlaybackBackBufferSeconds(value: Int) {
        ensureLoaded()
        if (playbackBackBufferSeconds == value) return
        playbackBackBufferSeconds = value
        publish()
        PlayerSettingsStorage.savePlaybackBackBufferSeconds(value)
    }

    fun setPlaybackTargetBufferMb(value: Int) {
        ensureLoaded()
        if (playbackTargetBufferMb == value) return
        playbackTargetBufferMb = value
        publish()
        PlayerSettingsStorage.savePlaybackTargetBufferMb(value)
    }

    fun setVodDiskCacheSizeMb(value: Int) {
        ensureLoaded()
        if (vodDiskCacheSizeMb == value) return
        vodDiskCacheSizeMb = value
        publish()
        PlayerSettingsStorage.saveVodDiskCacheSizeMb(value)
    }

    fun setStreamAutoPlayMode(mode: StreamAutoPlayMode) {
        ensureLoaded()
        if (streamAutoPlayMode == mode) return
        streamAutoPlayMode = mode
        publish()
        PlayerSettingsStorage.saveStreamAutoPlayMode(mode.name)
    }

    fun setStreamAutoPlaySource(source: StreamAutoPlaySource) {
        ensureLoaded()
        val normalizedSource = normalizeStreamAutoPlaySource(source)
        if (streamAutoPlaySource == normalizedSource) return
        streamAutoPlaySource = normalizedSource
        publish()
        PlayerSettingsStorage.saveStreamAutoPlaySource(normalizedSource.name)
    }

    fun setStreamAutoPlaySelectedAddons(addons: Set<String>) {
        ensureLoaded()
        if (streamAutoPlaySelectedAddons == addons) return
        streamAutoPlaySelectedAddons = addons
        publish()
        PlayerSettingsStorage.saveStreamAutoPlaySelectedAddons(addons)
    }

    fun setStreamAutoPlaySelectedPlugins(plugins: Set<String>) {
        ensureLoaded()
        val normalizedPlugins = if (AppFeaturePolicy.pluginsEnabled) plugins else emptySet()
        if (streamAutoPlaySelectedPlugins == normalizedPlugins) return
        streamAutoPlaySelectedPlugins = normalizedPlugins
        publish()
        PlayerSettingsStorage.saveStreamAutoPlaySelectedPlugins(normalizedPlugins)
    }

    fun setStreamAutoPlayRegex(regex: String) {
        ensureLoaded()
        if (streamAutoPlayRegex == regex) return
        streamAutoPlayRegex = regex
        publish()
        PlayerSettingsStorage.saveStreamAutoPlayRegex(regex)
    }

    fun setStreamAutoPlayTimeoutSeconds(seconds: Int) {
        ensureLoaded()
        if (streamAutoPlayTimeoutSeconds == seconds) return
        streamAutoPlayTimeoutSeconds = seconds
        publish()
        PlayerSettingsStorage.saveStreamAutoPlayTimeoutSeconds(seconds)
    }

    fun setSkipIntroOffsetSeconds(seconds: Int) {
        ensureLoaded()
        val normalized = normalizeSkipIntroOffsetSeconds(seconds)
        if (skipIntroOffsetSeconds == normalized) return
        skipIntroOffsetSeconds = normalized
        publish()
        PlayerSettingsStorage.saveSkipIntroOffsetSeconds(normalized)
    }

    fun setSkipIntroEnabled(enabled: Boolean) {
        ensureLoaded()
        if (skipIntroEnabled == enabled) return
        skipIntroEnabled = enabled
        publish()
        PlayerSettingsStorage.saveSkipIntroEnabled(enabled)
    }

    fun setAutoSkipSegmentTypeEnabled(segmentType: AutoSkipSegmentType, enabled: Boolean) {
        ensureLoaded()
        val updated = if (enabled) autoSkipSegmentTypes + segmentType else autoSkipSegmentTypes - segmentType
        if (autoSkipSegmentTypes == updated) return
        autoSkipSegmentTypes = updated
        publish()
        PlayerSettingsStorage.saveAutoSkipSegmentTypes(updated.mapTo(linkedSetOf()) { it.storedValue })
    }

    fun setAnimeSkipEnabled(enabled: Boolean) {
        ensureLoaded()
        if (animeSkipEnabled == enabled) return
        animeSkipEnabled = enabled
        publish()
        PlayerSettingsStorage.saveAnimeSkipEnabled(enabled)
    }

    fun setAnimeSkipClientId(clientId: String) {
        ensureLoaded()
        if (animeSkipClientId == clientId) return
        animeSkipClientId = clientId
        publish()
        PlayerSettingsStorage.saveAnimeSkipClientId(clientId)
    }

    fun setIntroDbApiKey(apiKey: String) {
        ensureLoaded()
        if (introDbApiKey == apiKey) return
        introDbApiKey = apiKey
        publish()
        PlayerSettingsStorage.saveIntroDbApiKey(apiKey)
    }

    fun setIntroSubmitEnabled(enabled: Boolean) {
        ensureLoaded()
        if (introSubmitEnabled == enabled) return
        introSubmitEnabled = enabled
        publish()
        PlayerSettingsStorage.saveIntroSubmitEnabled(enabled)
    }

    fun setStreamAutoPlayNextEpisodeEnabled(enabled: Boolean) {
        ensureLoaded()
        if (streamAutoPlayNextEpisodeEnabled == enabled) return
        streamAutoPlayNextEpisodeEnabled = enabled
        publish()
        PlayerSettingsStorage.saveStreamAutoPlayNextEpisodeEnabled(enabled)
    }

    fun setStreamAutoPlayNextEpisodeFallbackEnabled(enabled: Boolean) {
        ensureLoaded()
        if (streamAutoPlayNextEpisodeFallbackEnabled == enabled) return
        streamAutoPlayNextEpisodeFallbackEnabled = enabled
        publish()
        PlayerSettingsStorage.saveStreamAutoPlayNextEpisodeFallbackEnabled(enabled)
    }

    fun setStreamAutoPlayPreferBingeGroup(enabled: Boolean) {
        ensureLoaded()
        if (streamAutoPlayPreferBingeGroup == enabled) return
        streamAutoPlayPreferBingeGroup = enabled
        publish()
        PlayerSettingsStorage.saveStreamAutoPlayPreferBingeGroup(enabled)
    }

    fun setStreamAutoPlayReuseBingeGroup(enabled: Boolean) {
        ensureLoaded()
        if (streamAutoPlayReuseBingeGroup == enabled) return
        streamAutoPlayReuseBingeGroup = enabled
        publish()
        PlayerSettingsStorage.saveStreamAutoPlayReuseBingeGroup(enabled)
    }

    fun setNextEpisodeThresholdMode(mode: NextEpisodeThresholdMode) {
        ensureLoaded()
        if (nextEpisodeThresholdMode == mode) return
        nextEpisodeThresholdMode = mode
        publish()
        PlayerSettingsStorage.saveNextEpisodeThresholdMode(mode.name)
    }

    fun setNextEpisodeThresholdPercent(percent: Float) {
        ensureLoaded()
        if (nextEpisodeThresholdPercent == percent) return
        nextEpisodeThresholdPercent = percent
        publish()
        PlayerSettingsStorage.saveNextEpisodeThresholdPercent(percent)
    }

    fun setNextEpisodeThresholdMinutesBeforeEnd(minutes: Float) {
        ensureLoaded()
        if (nextEpisodeThresholdMinutesBeforeEnd == minutes) return
        nextEpisodeThresholdMinutesBeforeEnd = minutes
        publish()
        PlayerSettingsStorage.saveNextEpisodeThresholdMinutesBeforeEnd(minutes)
    }

    fun setPreloadNextEpisodeSources(enabled: Boolean) {
        ensureLoaded()
        if (preloadNextEpisodeSources == enabled) return
        preloadNextEpisodeSources = enabled
        publish()
        PlayerSettingsStorage.savePreloadNextEpisodeSources(enabled)
    }

    fun setMiniPlayerEnabled(enabled: Boolean) {
        ensureLoaded()
        if (miniPlayerEnabled == enabled) return
        miniPlayerEnabled = enabled
        publish()
        PlayerSettingsStorage.saveMiniPlayerEnabled(enabled)
    }

    fun setPrivateViewingEnabled(enabled: Boolean) {
        ensureLoaded()
        if (privateViewingEnabled == enabled) return
        privateViewingEnabled = enabled
        publish()
        PlayerSettingsStorage.savePrivateViewingEnabled(enabled)
    }

    fun setUseLibass(enabled: Boolean) {
        ensureLoaded()
        if (useLibass == enabled) return
        useLibass = enabled
        publish()
        PlayerSettingsStorage.saveUseLibass(enabled)
    }

    fun setLibassRenderType(renderType: String) {
        ensureLoaded()
        if (libassRenderType == renderType) return
        libassRenderType = renderType
        publish()
        PlayerSettingsStorage.saveLibassRenderType(renderType)
    }

    fun setUseLibmpvAss(enabled: Boolean) {
        ensureLoaded()
        if (useLibmpvAss == enabled) return
        useLibmpvAss = enabled
        publish()
        PlayerSettingsStorage.saveUseLibmpvAss(enabled)
    }

    fun setLibassRenderScale(scale: String) {
        ensureLoaded()
        if (libassRenderScale == scale) return
        libassRenderScale = scale
        publish()
        PlayerSettingsStorage.saveLibassRenderScale(scale)
    }

    fun setLibassRenderMode(mode: String) {
        ensureLoaded()
        if (libassRenderMode == mode) return
        libassRenderMode = mode
        publish()
        PlayerSettingsStorage.saveLibassRenderMode(mode)
    }

    fun setIosVideoOutputPreset(preset: IosVideoOutputPreset) {
        ensureLoaded()
        iosVideoOutputPreset = preset
        when (preset) {
            IosVideoOutputPreset.NativeEdr -> {
                iosExtendedDynamicRangeEnabled = true
                iosTargetColorspaceHintEnabled = true
                iosHdrComputePeakEnabled = true
                iosToneMappingMode = IosToneMappingMode.Auto
                iosTargetPrimaries = IosTargetPrimaries.Auto
                iosTargetTransfer = IosTargetTransfer.Auto
            }
            IosVideoOutputPreset.SdrToneMapped -> {
                iosExtendedDynamicRangeEnabled = false
                iosTargetColorspaceHintEnabled = false
                iosHdrComputePeakEnabled = true
                iosToneMappingMode = IosToneMappingMode.Bt2390
                iosTargetPrimaries = IosTargetPrimaries.Bt709
                iosTargetTransfer = IosTargetTransfer.Srgb
            }
            IosVideoOutputPreset.Compatibility -> {
                iosExtendedDynamicRangeEnabled = false
                iosTargetColorspaceHintEnabled = true
                iosHdrComputePeakEnabled = false
                iosToneMappingMode = IosToneMappingMode.Auto
                iosTargetPrimaries = IosTargetPrimaries.Auto
                iosTargetTransfer = IosTargetTransfer.Auto
            }
            IosVideoOutputPreset.Custom -> Unit
        }
        publish()
        saveIosVideoOutputSettings()
    }

    fun setIosToneMappingMode(mode: IosToneMappingMode) {
        ensureLoaded()
        iosVideoOutputPreset = IosVideoOutputPreset.Custom
        iosToneMappingMode = mode
        publish()
        saveIosVideoOutputSettings()
    }

    fun setIosTargetPrimaries(primaries: IosTargetPrimaries) {
        ensureLoaded()
        iosVideoOutputPreset = IosVideoOutputPreset.Custom
        iosTargetPrimaries = primaries
        publish()
        saveIosVideoOutputSettings()
    }

    fun setIosTargetTransfer(transfer: IosTargetTransfer) {
        ensureLoaded()
        iosVideoOutputPreset = IosVideoOutputPreset.Custom
        iosTargetTransfer = transfer
        publish()
        saveIosVideoOutputSettings()
    }

    fun setIosHardwareDecoderMode(mode: IosHardwareDecoderMode) {
        ensureLoaded()
        iosHardwareDecoderMode = mode
        publish()
        PlayerSettingsStorage.saveIosHardwareDecoderMode(mode.name)
    }

    fun setIosAudioOutputMode(mode: IosAudioOutputMode) {
        ensureLoaded()
        iosAudioOutputMode = mode.takeUnless { it == IosAudioOutputMode.AvFoundation } ?: IosAudioOutputMode.Auto
        publish()
        PlayerSettingsStorage.saveIosAudioOutputMode(iosAudioOutputMode.name)
    }

    fun setIosExtendedDynamicRangeEnabled(enabled: Boolean) {
        ensureLoaded()
        iosVideoOutputPreset = IosVideoOutputPreset.Custom
        iosExtendedDynamicRangeEnabled = enabled
        publish()
        saveIosVideoOutputSettings()
    }

    fun setIosTargetColorspaceHintEnabled(enabled: Boolean) {
        ensureLoaded()
        iosVideoOutputPreset = IosVideoOutputPreset.Custom
        iosTargetColorspaceHintEnabled = enabled
        publish()
        saveIosVideoOutputSettings()
    }

    fun setIosHdrComputePeakEnabled(enabled: Boolean) {
        ensureLoaded()
        iosVideoOutputPreset = IosVideoOutputPreset.Custom
        iosHdrComputePeakEnabled = enabled
        publish()
        saveIosVideoOutputSettings()
    }

    fun setIosDebandEnabled(enabled: Boolean) {
        ensureLoaded()
        iosDebandEnabled = enabled
        publish()
        PlayerSettingsStorage.saveIosDebandEnabled(enabled)
    }

    fun setIosInterpolationEnabled(enabled: Boolean) {
        ensureLoaded()
        iosInterpolationEnabled = enabled
        publish()
        PlayerSettingsStorage.saveIosInterpolationEnabled(enabled)
    }

    fun setIosBrightness(value: Int) {
        ensureLoaded()
        iosBrightness = value.coerceIn(-50, 50)
        publish()
        PlayerSettingsStorage.saveIosBrightness(iosBrightness)
    }

    fun setIosContrast(value: Int) {
        ensureLoaded()
        iosContrast = value.coerceIn(-50, 50)
        publish()
        PlayerSettingsStorage.saveIosContrast(iosContrast)
    }

    fun setIosSaturation(value: Int) {
        ensureLoaded()
        iosSaturation = value.coerceIn(-50, 50)
        publish()
        PlayerSettingsStorage.saveIosSaturation(iosSaturation)
    }

    fun setIosGamma(value: Int) {
        ensureLoaded()
        iosGamma = value.coerceIn(-50, 50)
        publish()
        PlayerSettingsStorage.saveIosGamma(iosGamma)
    }

    fun resetIosVideoOutputTuning() {
        ensureLoaded()
        iosBrightness = 0
        iosContrast = 0
        iosSaturation = 0
        iosGamma = 0
        iosDebandEnabled = false
        iosInterpolationEnabled = false
        publish()
        PlayerSettingsStorage.saveIosBrightness(0)
        PlayerSettingsStorage.saveIosContrast(0)
        PlayerSettingsStorage.saveIosSaturation(0)
        PlayerSettingsStorage.saveIosGamma(0)
        PlayerSettingsStorage.saveIosDebandEnabled(false)
        PlayerSettingsStorage.saveIosInterpolationEnabled(false)
    }

    private fun saveIosVideoOutputSettings() {
        PlayerSettingsStorage.saveIosVideoOutputPreset(iosVideoOutputPreset.name)
        PlayerSettingsStorage.saveIosToneMappingMode(iosToneMappingMode.name)
        PlayerSettingsStorage.saveIosTargetPrimaries(iosTargetPrimaries.name)
        PlayerSettingsStorage.saveIosTargetTransfer(iosTargetTransfer.name)
        PlayerSettingsStorage.saveIosExtendedDynamicRangeEnabled(iosExtendedDynamicRangeEnabled)
        PlayerSettingsStorage.saveIosTargetColorspaceHintEnabled(iosTargetColorspaceHintEnabled)
        PlayerSettingsStorage.saveIosHdrComputePeakEnabled(iosHdrComputePeakEnabled)
    }

    private fun publish() {
        _uiState.value = PlayerSettingsUiState(
            useLegacyPlayerLayout = useLegacyPlayerLayout,
            showPlayerTitleLogo = showPlayerTitleLogo,
            showLoadingOverlay = showLoadingOverlay,
            showPlayerLoadingStatus = showPlayerLoadingStatus,
            pauseOverlayEnabled = pauseOverlayEnabled,
            showParentalGuide = showParentalGuide,
            resizeMode = resizeMode,
            holdToSpeedEnabled = holdToSpeedEnabled,
            holdToSpeedValue = holdToSpeedValue,
            skipBackwardSeconds = skipBackwardSeconds,
            skipForwardSeconds = skipForwardSeconds,
            toolbarLayout = toolbarLayout,
            legacyBottomButtons = legacyBottomButtons,
            legacyTopButtons = legacyTopButtons,
            touchGesturesEnabled = touchGesturesEnabled,
            swipeToSeekEnabled = swipeToSeekEnabled,
            playerControlGlassEnabled = playerControlGlassEnabled,
            showSkipButtons = showSkipButtons,
            seekPreviewEnabled = seekPreviewEnabled,
            movieRecommendationsEnabled = movieRecommendationsEnabled,
            autoShowSubtitlesOnRewindEnabled = autoShowSubtitlesOnRewindEnabled,
            autoShowSubtitlesOnMuteEnabled = autoShowSubtitlesOnMuteEnabled,
            externalPlayerEnabled = externalPlayerEnabled,
            externalPlayerForwardSubtitles = externalPlayerForwardSubtitles,
            externalPlayerSendSkipSegments = externalPlayerSendSkipSegments,
            externalPlayerId = externalPlayerId,
            preferredAudioLanguage = preferredAudioLanguage,
            secondaryPreferredAudioLanguage = secondaryPreferredAudioLanguage,
            preferredSubtitleLanguage = preferredSubtitleLanguage,
            secondaryPreferredSubtitleLanguage = secondaryPreferredSubtitleLanguage,
            subtitleStyle = subtitleStyle,
            streamReuseLastLinkEnabled = streamReuseLastLinkEnabled,
            streamReuseLastLinkCacheHours = streamReuseLastLinkCacheHours,
            androidPlaybackEngine = androidPlaybackEngine,
            autoSwitchPlaybackEngineOnError = autoSwitchPlaybackEngineOnError,
            androidLibmpvVideoOutput = androidLibmpvVideoOutput,
            androidLibmpvHardwareDecodingEnabled = androidLibmpvHardwareDecodingEnabled,
            androidLibmpvYuv420pEnabled = androidLibmpvYuv420pEnabled,
            androidAnimeUpscaleEnabled = androidAnimeUpscaleEnabled,
            androidAnimeLineSharpenEnabled = androidAnimeLineSharpenEnabled,
            androidExoChaptersEnabled = androidExoChaptersEnabled,
            rememberAudioDelayPerDevice = rememberAudioDelayPerDevice,
            persistAudioAmplification = persistAudioAmplification,
            audioAmplificationDb = audioAmplificationDb,
            downmixEnabled = downmixEnabled,
            audioOutputChannels = audioOutputChannels,
            maintainOriginalAudioOnDownmix = maintainOriginalAudioOnDownmix,
            centerMixLevelDb = centerMixLevelDb,
            decoderPriority = decoderPriority,
            mapDV7ToHevc = mapDV7ToHevc,
            tunnelingEnabled = tunnelingEnabled,
            androidAudioPassthroughEnabled = androidAudioPassthroughEnabled,
            nightModeButtonEnabled = nightModeButtonEnabled,
            nightModeEnabled = nightModeEnabled,
            exoNativeMemoryEnabled = exoNativeMemoryEnabled,
            customPlaybackBuffersEnabled = customPlaybackBuffersEnabled,
            vodDiskCacheEnabled = vodDiskCacheEnabled,
            vodDiskCacheAutoSize = vodDiskCacheAutoSize,
            playbackMinBufferSeconds = playbackMinBufferSeconds,
            playbackMaxBufferSeconds = playbackMaxBufferSeconds,
            playbackStartBufferSeconds = playbackStartBufferSeconds,
            playbackRebufferSeconds = playbackRebufferSeconds,
            playbackBackBufferSeconds = playbackBackBufferSeconds,
            playbackTargetBufferMb = playbackTargetBufferMb,
            vodDiskCacheSizeMb = vodDiskCacheSizeMb,
            streamAutoPlayMode = streamAutoPlayMode,
            streamAutoPlaySource = streamAutoPlaySource,
            streamAutoPlaySelectedAddons = streamAutoPlaySelectedAddons,
            streamAutoPlaySelectedPlugins = streamAutoPlaySelectedPlugins,
            streamAutoPlayRegex = streamAutoPlayRegex,
            streamAutoPlayTimeoutSeconds = streamAutoPlayTimeoutSeconds,
            skipIntroEnabled = skipIntroEnabled,
            skipIntroOffsetSeconds = skipIntroOffsetSeconds,
            autoSkipSegmentTypes = autoSkipSegmentTypes,
            animeSkipEnabled = animeSkipEnabled,
            animeSkipClientId = animeSkipClientId,
            introDbApiKey = introDbApiKey,
            introSubmitEnabled = introSubmitEnabled,
            streamAutoPlayNextEpisodeEnabled = streamAutoPlayNextEpisodeEnabled,
            streamAutoPlayNextEpisodeFallbackEnabled = streamAutoPlayNextEpisodeFallbackEnabled,
            streamAutoPlayPreferBingeGroup = streamAutoPlayPreferBingeGroup,
            streamAutoPlayReuseBingeGroup = streamAutoPlayReuseBingeGroup,
            nextEpisodeThresholdMode = nextEpisodeThresholdMode,
            nextEpisodeThresholdPercent = nextEpisodeThresholdPercent,
            nextEpisodeThresholdMinutesBeforeEnd = nextEpisodeThresholdMinutesBeforeEnd,
            preloadNextEpisodeSources = preloadNextEpisodeSources,
            miniPlayerEnabled = miniPlayerEnabled,
            privateViewingEnabled = privateViewingEnabled,
            useLibass = useLibass,
            libassRenderType = libassRenderType,
            useLibmpvAss = useLibmpvAss,
            libassRenderScale = libassRenderScale,
            libassRenderMode = libassRenderMode,
            iosVideoOutputPreset = iosVideoOutputPreset,
            iosToneMappingMode = iosToneMappingMode,
            iosTargetPrimaries = iosTargetPrimaries,
            iosTargetTransfer = iosTargetTransfer,
            iosHardwareDecoderMode = iosHardwareDecoderMode,
            iosAudioOutputMode = iosAudioOutputMode,
            iosExtendedDynamicRangeEnabled = iosExtendedDynamicRangeEnabled,
            iosTargetColorspaceHintEnabled = iosTargetColorspaceHintEnabled,
            iosHdrComputePeakEnabled = iosHdrComputePeakEnabled,
            iosDebandEnabled = iosDebandEnabled,
            iosInterpolationEnabled = iosInterpolationEnabled,
            iosBrightness = iosBrightness,
            iosContrast = iosContrast,
            iosSaturation = iosSaturation,
            iosGamma = iosGamma,
        )
    }

    private fun normalizeStreamAutoPlaySource(source: StreamAutoPlaySource): StreamAutoPlaySource {
        return if (!AppFeaturePolicy.pluginsEnabled && source == StreamAutoPlaySource.ENABLED_PLUGINS_ONLY) {
            StreamAutoPlaySource.ALL_SOURCES
        } else {
            source
        }
    }
}

val PlayerSkipSecondsOptions = listOf(5, 10, 15, 30)
const val PLAYER_SKIP_SECONDS_DEFAULT = 10

const val SKIP_INTRO_OFFSET_SLIDER_SECONDS = 30
const val SKIP_INTRO_OFFSET_MAX_SECONDS = 60

internal fun normalizeSkipIntroOffsetSeconds(seconds: Int?): Int =
    (seconds ?: 0).coerceIn(-SKIP_INTRO_OFFSET_MAX_SECONDS, SKIP_INTRO_OFFSET_MAX_SECONDS)

internal fun normalizePlayerSkipSeconds(seconds: Int?): Int =
    seconds?.takeIf { it in PlayerSkipSecondsOptions } ?: PLAYER_SKIP_SECONDS_DEFAULT
