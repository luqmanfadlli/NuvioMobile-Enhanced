package com.nuvio.app.features.player

import com.nuvio.app.features.watching.application.WatchingState
import com.nuvio.app.features.home.MetaPreview
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import kotlinx.coroutines.delay
import androidx.compose.runtime.produceState
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.onSizeChanged
import com.nuvio.app.features.player.seekpreview.rememberSeekPreviewController
import com.nuvio.app.features.player.skip.PlayerNextEpisodeRules
import com.nuvio.app.core.logging.InAppLogger
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.NuvioStatusModal
import com.nuvio.app.features.details.MetaDetailsRepository
import com.nuvio.app.core.ui.LocalFold
import com.nuvio.app.features.p2p.P2pStreamingState
import com.nuvio.app.features.party.PartyRepository
import com.nuvio.app.features.p2p.formatP2pMegabytes
import com.nuvio.app.features.p2p.formatP2pSpeed
import com.nuvio.app.features.player.skip.internalSkipAction
import com.nuvio.app.features.servers.ServerStreams
import com.nuvio.app.features.streams.streamAddonInstanceId
import com.nuvio.app.isIos
import kotlinx.coroutines.launch
import com.nuvio.app.core.i18n.localizedSeasonEpisodeCode
import com.nuvio.app.features.ratings.UserRatingPlayerOverlay
import com.nuvio.app.features.ratings.rememberCanRate
import com.nuvio.app.features.ratings.rememberUserRating
import com.nuvio.app.features.ratings.toUserRatingTarget
import com.nuvio.app.features.tracking.TrackingRatingTarget
import com.nuvio.app.features.livetv.LiveTvRepository
import com.nuvio.app.features.livetv.currentProgram
import com.nuvio.app.features.livetv.rememberLiveTvEpgNowMillis
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun PlayerScreenRuntime.RenderPlayerRuntimeUi(
    partyPanelWidth: Dp,
    partyPanelInset: Dp,
    dockPartyPanel: Boolean,
) {
    val runtime = this
    val isEpisode = activeSeasonNumber != null && activeEpisodeNumber != null
    val currentGestureFeedback = liveGestureFeedback ?: gestureFeedback
    val isP2pPlaybackActive = activeTorrentInfoHash != null
    val p2pConnecting = p2pStreamingState as? P2pStreamingState.Connecting
    val p2pStats = p2pStreamingState as? P2pStreamingState.Streaming
    val p2pPeerInfo = p2pStats?.let { stats ->
        org.jetbrains.compose.resources.stringResource(
            nuvio.composeapp.generated.resources.Res.string.player_torrent_peer_info,
            stats.seeds,
            stats.peers,
        )
    }
    val p2pDownloadSpeed = p2pStats?.let { formatP2pSpeed(it.downloadSpeed) }
    val p2pLoadingBytes = p2pStats?.let { maxOf(it.downloadedBytes, it.deliveredBytes) } ?: 0L
    val connectingPeerInfo = p2pConnecting?.let { state ->
        org.jetbrains.compose.resources.stringResource(
            nuvio.composeapp.generated.resources.Res.string.player_torrent_peer_info,
            state.seeds,
            state.peers,
        )
    }
    val p2pInitialLoadingMessage = when {
        !isP2pPlaybackActive || initialLoadCompleted -> null
        p2pConnecting != null -> {
            if (p2pSettingsUiState.hideTorrentStats) {
                p2pConnectingPhaseLabel(p2pConnecting.phase)
            } else {
                org.jetbrains.compose.resources.stringResource(
                    nuvio.composeapp.generated.resources.Res.string.player_torrent_connecting_status,
                    p2pConnectingPhaseLabel(p2pConnecting.phase),
                    connectingPeerInfo.orEmpty(),
                    formatP2pSpeed(p2pConnecting.downloadSpeed),
                )
            }
        }
        p2pStats != null -> {
            if (p2pSettingsUiState.hideTorrentStats) {
                null
            } else {
                org.jetbrains.compose.resources.stringResource(
                    nuvio.composeapp.generated.resources.Res.string.player_torrent_loading_status,
                    formatP2pMegabytes(p2pLoadingBytes),
                    p2pPeerInfo.orEmpty(),
                    p2pDownloadSpeed.orEmpty(),
                )
            }
        }
        else -> org.jetbrains.compose.resources.stringResource(
            nuvio.composeapp.generated.resources.Res.string.player_torrent_starting_engine,
        )
    }
    val p2pInitialLoadingProgress = when {
        !isP2pPlaybackActive || initialLoadCompleted || p2pStats == null -> null
        else -> {
            val bufferedAheadMs = (playbackSnapshot.bufferedPositionMs - playbackSnapshot.positionMs)
                .coerceAtLeast(0L)
            p2pInitialLoadingProgress(
                bufferedAheadMs = bufferedAheadMs,
                downloadedBytes = p2pStats.downloadedBytes,
                deliveredBytes = p2pStats.deliveredBytes,
            )
        }
    }
    val showP2pRebufferStats = isP2pPlaybackActive &&
        initialLoadCompleted &&
        playbackSnapshot.isLoading &&
        p2pStats != null &&
        !p2pSettingsUiState.hideTorrentStats
    val p2pRebufferMessage = when {
        !showP2pRebufferStats -> null
        else -> {
            val bufferedSeconds = ((playbackSnapshot.bufferedPositionMs - playbackSnapshot.positionMs) / 1000L)
                .coerceAtLeast(0L)
            "${bufferedSeconds}s buffered · ${p2pPeerInfo.orEmpty()} · ${p2pDownloadSpeed.orEmpty()}"
        }
    }
    val p2pRebufferProgress = when {
        !showP2pRebufferStats -> null
        else -> {
            val bufferedSeconds = ((playbackSnapshot.bufferedPositionMs - playbackSnapshot.positionMs) / 1000f)
                .coerceAtLeast(0f)
            (bufferedSeconds / 10f).coerceIn(0f, 1f)
        }
    }
    val gestureCallbacks = rememberSurfaceGestureCallbacks()
    val onPinchZoom = rememberUpdatedState<(Float) -> Unit> { factor -> pinchVideoZoom(factor) }
    LaunchedEffect(playerController) {
        if (videoZoom != 1f) playerController?.setVideoZoom(videoZoom)
    }
    val isInPip = rememberIsInPictureInPicture()
    val fold = LocalFold.current?.takeIf { !isInPip }
    val videoModifier = if (fold != null) Modifier.fillMaxWidth().height(fold.top) else Modifier.fillMaxSize()
    val panelModifier = if (fold != null) Modifier.fillMaxSize().padding(top = fold.bottom) else Modifier.fillMaxSize()
    val playbackGesturesEnabled = initialLoadCompleted && errorMessage == null

    LaunchedEffect(activeSourceUrl, activeSourceAudioUrl, activeSourceHeaders, activeTorrentInfoHash) {
        val resolvingSourceUrl = activeSourceUrl
        val resolvingAudioUrl = activeSourceAudioUrl
        val resolvingHeaders = activeSourceHeaders
        val resolvingTorrentInfoHash = activeTorrentInfoHash

        selectedPlayerQualityId = null
        val shouldInspectHls = activeTorrentInfoHash == null &&
            activeSourceAudioUrl == null &&
            activeSourceUrl.contains(".m3u8", ignoreCase = true)
        activePlaybackSourceUrl = if (shouldInspectHls) null else activeSourceUrl
        playerQualityState = PlayerQualitySelectionState(
            isLoading = shouldInspectHls,
            sourceUrl = activeSourceUrl,
        )
        InAppLogger.debug(
            "Player/Quality",
            "source changed inspectHls=$shouldInspectHls url=${InAppLogger.redactUrl(resolvingSourceUrl)} " +
                "audio=${!resolvingAudioUrl.isNullOrBlank()} torrent=${!resolvingTorrentInfoHash.isNullOrBlank()} " +
                "requestHeaders=${InAppLogger.headerKeys(resolvingHeaders)}",
        )

        if (shouldInspectHls) {
            val resolved = runCatching {
                PlayerQualityResolver.resolve(
                    sourceUrl = resolvingSourceUrl,
                    requestHeaders = resolvingHeaders,
                )
            }.getOrElse { error ->
                InAppLogger.warn(
                    "Player/Quality",
                    "resolve quality failed url=${InAppLogger.redactUrl(resolvingSourceUrl)} " +
                        "error=${InAppLogger.throwableSummary(error)}",
                )
                PlayerQualitySelectionState(
                    sourceUrl = resolvingSourceUrl,
                    errorMessage = error.message ?: "Unable to inspect HLS quality variants.",
                )
            }

            if (activeSourceUrl != resolvingSourceUrl ||
                activeSourceAudioUrl != resolvingAudioUrl ||
                activeTorrentInfoHash != resolvingTorrentInfoHash
            ) {
                InAppLogger.debug(
                    "Player/Quality",
                    "discard stale quality result url=${InAppLogger.redactUrl(resolvingSourceUrl)}",
                )
                return@LaunchedEffect
            }

            playerQualityState = resolved
            activePlaybackSourceUrl = resolved.playbackUrlFor(null) ?: resolvingSourceUrl
            InAppLogger.info(
                "Player/Quality",
                "quality state ready variants=${resolved.variants.size} " +
                    "recommended=${resolved.labelFor(null).orEmpty()} " +
                    "playbackUrl=${InAppLogger.redactUrl(activePlaybackSourceUrl)}",
            )
        } else {
            playerQualityState = PlayerQualitySelectionState(sourceUrl = resolvingSourceUrl)
        }
    }

    val keyboardFocusRequester = remember { FocusRequester() }
    val keyboardShortcutsEnabled = remember {
        derivedStateOf { !isAnyOverlayVisible && !playerControlsLocked && !isMiniPresentation }
    }
    val onKeyboardShortcut = rememberUpdatedState(::handleKeyboardShortcut)
    LaunchedEffect(keyboardShortcutsEnabled.value) {
        if (keyboardShortcutsEnabled.value) {
            runCatching { keyboardFocusRequester.requestFocus() }
        }
    }

    BindAutoSyncRuntimeEffects() // AutoSync hook

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(end = partyPanelInset)
            .clipToBounds()
            .onSizeChanged { layoutSize = it }
            .playerKeyboardShortcuts(
                focusRequester = keyboardFocusRequester,
                enabledState = keyboardShortcutsEnabled,
                onShortcutState = onKeyboardShortcut,
            )
            .playerSurfaceTapGestures(
                layoutSize = layoutSize,
                playbackGesturesEnabled = playbackGesturesEnabled,
                playerControlsLockedState = gestureCallbacks.playerControlsLocked,
                onSurfaceTap = gestureCallbacks.onSurfaceTap,
                onSurfaceDoubleTap = gestureCallbacks.onSurfaceDoubleTap,
                activateHoldToSpeedState = gestureCallbacks.activateHoldToSpeed,
                deactivateHoldToSpeedState = gestureCallbacks.deactivateHoldToSpeed,
                revealLockedOverlayState = gestureCallbacks.revealLockedOverlay,
            )
            .playerSurfaceDragGestures(
                gestureController = gestureController,
                playerController = playerController,
                layoutSize = layoutSize,
                playbackGesturesEnabled = playbackGesturesEnabled,
                sideGestureSystemEdgeExclusionPx = sideGestureSystemEdgeExclusionPx,
                horizontalSeekSystemEdgeExclusionPx = horizontalSeekSystemEdgeExclusionPx,
                playerControlsLockedState = gestureCallbacks.playerControlsLocked,
                touchGesturesEnabledState = gestureCallbacks.touchGesturesEnabled,
                swipeToSeekEnabledState = gestureCallbacks.swipeToSeekEnabled,
                isHoldToSpeedGestureActiveState = gestureCallbacks.isHoldToSpeedGestureActive,
                currentPositionMsState = gestureCallbacks.currentPositionMs,
                currentDurationMsState = gestureCallbacks.currentDurationMs,
                deactivateHoldToSpeedState = gestureCallbacks.deactivateHoldToSpeed,
                showHorizontalSeekPreviewState = gestureCallbacks.showHorizontalSeekPreview,
                showBrightnessFeedbackState = gestureCallbacks.showBrightnessFeedback,
                showVolumeFeedbackState = gestureCallbacks.showVolumeFeedback,
                clearLiveGestureFeedbackState = gestureCallbacks.clearLiveGestureFeedback,
                revealLockedOverlayState = gestureCallbacks.revealLockedOverlay,
                commitHorizontalSeekState = gestureCallbacks.commitHorizontalSeek,
            )
            .playerSurfacePinchGestures(
                playbackGesturesEnabled = playbackGesturesEnabled,
                playerControlsLockedState = gestureCallbacks.playerControlsLocked,
                touchGesturesEnabledState = gestureCallbacks.touchGesturesEnabled,
                onPinchZoomState = onPinchZoom,
            ),
    ) {
        val playerSurfaceSourceUrl = if (isP2pPlaybackActive) p2pResolvedSourceUrl else activePlaybackSourceUrl
        val playbackKey = activePlaybackKey
        val initialPositionRequestKey = currentInitialPositionRequestKey()

        val resolvedAutoEngineState = remember(sourcePlaybackKey) {
            mutableStateOf<AndroidPlaybackEngine?>(null)
        }
        if (!initialLoadCompleted || resolvedAutoEngineState.value == null) {
            resolvedAutoEngineState.value = resolveAutoPlaybackEngine(
                playbackEngineSetting = playerSettingsUiState.androidPlaybackEngine,
                videoId = activeVideoId,
                streamFilename = activeTorrentFilename ?: activeStreamFilename,
                streamName = activeStreamTitle,
                streamDescription = activeStreamSubtitle,
                contentTitle = title,
                genres = playerMeta?.genres.orEmpty(),
                country = playerMeta?.country,
            )
        }
        val resolvedAutoEngine = resolvedAutoEngineState.value

        if (playerSurfaceSourceUrl != null) {
            key(playbackKey) {
                val active = remember { mutableStateOf(true) }
                DisposableEffect(Unit) {
                    onDispose { active.value = false }
                }
                val animeContentState = remember { mutableStateOf(false) }
                if (isAnimePlayback && !animeContentState.value) animeContentState.value = true
                PlatformPlayerSurface(
                    sourceUrl = playerSurfaceSourceUrl,
                    sourceAudioUrl = activeSourceAudioUrl,
                    sourceHeaders = activeSourceHeaders,
                    sourceResponseHeaders = activeSourceResponseHeaders,
                    externalSubtitles = externalSubtitles,
                    streamType = activeStreamType,
                    modifier = videoModifier,
                    playWhenReady = shouldPlay,
                    initialPositionMs = activeInitialPositionMs.takeIf { it > 0L },
                    initialPositionRequestKey = initialPositionRequestKey,
                    resizeMode = if (partyPanelInset > 0.dp) PlayerResizeMode.Fit else resizeMode,
                    playbackEngine = playbackEngineOverride ?: resolvedAutoEngine,
                    animeContent = animeContentState.value,
                    onInitialPositionHandled = { key, handled ->
                        if (active.value && playbackKey == activePlaybackKey && key == currentInitialPositionRequestKey()) {
                            initialSeekApplied = handled
                        }
                    },
                    onControllerReady = { controller ->
                        if (active.value && playbackKey == activePlaybackKey) {
                            playerController = controller
                            playerControllerSourceUrl = playerSurfaceSourceUrl
                        }
                    },
                    onSnapshot = { snapshot ->
                        if (!active.value || !updatePlaybackSnapshot(snapshot, playbackKey)) return@PlatformPlayerSurface
                        checkAutoSubtitleRewindWatermark(snapshot.positionMs)
                        refreshAudioTracksIfChanged()
                        if (!snapshot.isLoading) initialLoadCompleted = true
                        if (snapshot.isEnded) {
                            shouldPlay = false
                            controlsVisible = !playerControlsLocked
                        }
                    },
                    onError = { message ->
                        if (!active.value || playbackKey != activePlaybackKey) return@PlatformPlayerSurface
                        if (message != null && tryRefreshCredentialedSourceAfterError(message)) {
                            return@PlatformPlayerSurface
                        }
                        // Engine failover is decided here, driven by its own player setting;
                        // the engine's own decoder/MIME retries have already been tried.
                        if (message != null && maybeFailoverPlaybackEngine(resolvedAutoEngine)) {
                            return@PlatformPlayerSurface
                        }
                        errorMessage = message
                        if (message != null) {
                            scrubbingPositionMs = null
                            controlsVisible = !playerControlsLocked
                            removeFailedStreamFromCache()
                        }
                    },
                )
            }
        }

        if (!isMiniPresentation) {
        AnimatedVisibility(
            visible = playerSettingsUiState.pauseOverlayEnabled && pausedOverlayVisible && !controlsVisible && !playerControlsLocked && fold == null,
            enter = fadeIn(animationSpec = tween(durationMillis = 220)),
            exit = fadeOut(animationSpec = tween(durationMillis = 180)),
        ) {
            PauseMetadataOverlay(
                title = if (isLiveTvPlayback) activeStreamTitle else title,
                logo = if (isLiveTvPlayback) activeLogo else logo,
                isEpisode = isEpisode,
                seasonNumber = activeSeasonNumber,
                episodeNumber = activeEpisodeNumber,
                episodeTitle = activeEpisodeTitle,
                pauseDescription = activePauseDescription ?: activeStreamSubtitle,
                providerName = activeProviderName,
                metrics = metrics,
                horizontalSafePadding = horizontalSafePadding,
                modifier = Modifier.fillMaxSize(),
            )
        }

        RenderPlayerControls(
            isEpisode = isEpisode,
            folded = fold != null,
            modifier = panelModifier,
        )
        Box(modifier = videoModifier) {
            RenderPlaybackOverlays(
                runtime = runtime,
                currentGestureFeedback = currentGestureFeedback,
                p2pInitialLoadingMessage = p2pInitialLoadingMessage,
                p2pInitialLoadingProgress = p2pInitialLoadingProgress,
                showP2pRebufferStats = showP2pRebufferStats,
                p2pRebufferMessage = p2pRebufferMessage,
                p2pRebufferProgress = p2pRebufferProgress,
                folded = fold != null,
            )
            SubtitleSyncByEarCard(
                visible = showSubtitleSyncByEar,
                subtitleDelayMs = subtitleDelayMs,
                heardCaptured = subtitleSyncHeardPositionMs != null,
                sawCaptured = subtitleSyncSawPositionMs != null,
                onHeard = { captureSubtitleSyncHeard() },
                onSaw = { captureSubtitleSyncSaw() },
                onClose = { closeSubtitleSyncByEar() },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp, start = horizontalSafePadding, end = horizontalSafePadding),
            )
        }
        }
    }
    // Panels sit outside the clipped video box so the docked party panel can take the freed space.
    if (!isMiniPresentation) {
        Box(modifier = panelModifier) {
            RenderParty(
                runtime = runtime,
                panelWidth = partyPanelWidth,
                modal = !dockPartyPanel,
                panelVisible = showPartyPanel && !isInPip,
            )
            RenderPlayerModals()
        }
    }
}

@Composable
private fun p2pConnectingPhaseLabel(phase: String): String = when (phase) {
    "add_magnet" -> org.jetbrains.compose.resources.stringResource(
        nuvio.composeapp.generated.resources.Res.string.player_torrent_fetching_metadata,
    )
    "prepare_stream", "attach_route" -> org.jetbrains.compose.resources.stringResource(
        nuvio.composeapp.generated.resources.Res.string.player_torrent_preparing_stream,
    )
    else -> org.jetbrains.compose.resources.stringResource(
        nuvio.composeapp.generated.resources.Res.string.player_torrent_starting_engine,
    )
}

private val PlayerScreenRuntime.activeAddonLogo: String?
    get() = addonsUiState.addons.firstNotNullOfOrNull { addon ->
        addon.manifest?.takeIf { addon.streamAddonInstanceId(it.id) == activeProviderAddonId }?.logoUrl
    }

private fun PlayerScreenRuntime.currentInitialPositionRequestKey(): String? {
    val positionMs = activeInitialPositionMs.takeIf { it > 0L } ?: return null
    return "$activePlaybackIdentity:${activeVideoId.orEmpty()}:$positionMs"
}

@Composable
private fun PlayerScreenRuntime.RenderPlayerControls(
    isEpisode: Boolean,
    folded: Boolean,
    modifier: Modifier,
) {
    val isInPip = rememberIsInPictureInPicture()
    val seekPreview = rememberSeekPreviewController(
        url = activePlaybackSourceUrl?.takeIf {
            playerSettingsUiState.seekPreviewEnabled &&
                !isLiveTvPlayback &&
                activeTorrentInfoHash == null
        },
        headers = activeSourceHeaders,
    )
    val userRatingTarget = currentUserRatingTarget()
    val canRate = rememberCanRate(userRatingTarget)
    val userRating = rememberUserRating(userRatingTarget.takeIf { canRate })
    val liveTvEpgNowMillis = rememberLiveTvEpgNowMillis()
    LaunchedEffect(isLiveTvPlayback) {
        if (isLiveTvPlayback) LiveTvRepository.ensureChannelsLoaded()
    }
    val liveTvEpgState by LiveTvRepository.uiState.collectAsStateWithLifecycle()
    val liveTvNowProgramTitle = if (isLiveTvPlayback) {
        activeVideoId?.let { channelId -> liveTvEpgState.epgPrograms[channelId] }
            ?.currentProgram(liveTvEpgNowMillis)
            ?.title
    } else {
        null
    }
    val partyState by PartyRepository.state.collectAsStateWithLifecycle()
    val partyActive = partyState.party != null
    val playerBadges = rememberPlayerBadges()
    // Live TV channel artwork is not a title logo; retain the program title text instead.
    val playerTitleLogo = if (isLiveTvPlayback) null else logo
    val showPlayerTitleLogo = playerSettingsUiState.showPlayerTitleLogo && !isLiveTvPlayback
    LaunchedEffect(folded) {
        if (folded) {
            controlsVisible = true
            showParentalGuide = false
        }
    }
    AnimatedVisibility(
        visible = (controlsVisible || folded || showParentalGuide) && !playerControlsLocked && !isInPip &&
            !(folded && isPanelOpen),
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        @OptIn(ExperimentalAnimationApi::class)
        val controlsFadingOut = transition.targetState == EnterExitState.PostExit
        val shownBeforeExit = remember { booleanArrayOf(controlsVisible) }
        if (!controlsFadingOut) shownBeforeExit[0] = controlsVisible
        val displayedPositionMs = scrubbingPositionMs ?: playbackSnapshot.positionMs
        val orientationToggleSupported = rememberPlayerOrientationToggleSupported()
        val portraitPlayback by PlayerOrientationPreference.portrait.collectAsStateWithLifecycle()
        val pipAvailable = isIos && playerSettingsUiState.miniPlayerEnabled &&
            playerController?.isPictureInPictureSupported() == true
        PlayerControlsShell(
            title = title,
            titleLogo = playerTitleLogo,
            showTitleLogo = showPlayerTitleLogo,
            streamTitle = activeStreamTitle,
            providerName = liveTvNowProgramTitle?.let { programTitle -> "$activeProviderName · $programTitle" } ?: activeProviderName,
            seasonNumber = activeSeasonNumber,
            episodeNumber = activeEpisodeNumber,
            episodeTitle = activeEpisodeTitle,
            playbackSnapshot = playbackSnapshot,
            displayedPositionMs = displayedPositionMs,
            metrics = metrics,
            resizeMode = resizeMode,
            isLocked = playerControlsLocked,
            useLegacyLayout = playerSettingsUiState.useLegacyPlayerLayout,
            showRemainingTime = showRemainingTime,
            onRuntimeClick = { showRemainingTime = !showRemainingTime },
            releaseInfo = metaUiState.meta?.takeIf { it.id == parentMetaId }?.releaseInfo,
            badges = playerBadges,
            hideDetails = activeSkipInterval != null && !skipIntervalDismissed,
            onNextEpisodeClick = if (nextEpisodeInfo?.hasAired == true && !nextEpisodeAutoPlaySearching && nextEpisodeAutoPlayCountdown == null) {
                {
                    if (!partyBlocksControl()) playNextEpisode()
                }
            } else null,
            onInteraction = { controlsActivityTick += 1 },
            showPlaybackControls = if (controlsFadingOut) shownBeforeExit[0] else controlsVisible,
            onLockToggle = {
                if (playerControlsLocked) unlockPlayerControls() else lockPlayerControls()
            },
            onBack = {
                flushWatchProgress()
                args.onBack()
            },
            onPartyClick = if (PartyRepository.available.collectAsStateWithLifecycle().value) {
                { openPartyPanel() }
            } else {
                null
            },
            partyActive = partyActive,
            onTogglePlayback = { togglePlayback() },
            onSeekBack = { seekBy(-skipStepMs(PlayerSeekDirection.Backward)) },
            onSeekForward = { seekBy(skipStepMs(PlayerSeekDirection.Forward)) },
            skipBackwardSeconds = playerSettingsUiState.skipBackwardSeconds,
            skipForwardSeconds = playerSettingsUiState.skipForwardSeconds,
            showSkipButtons = playerSettingsUiState.showSkipButtons,
            onResizeModeClick = { cycleResizeMode() },
            onSpeedClick = if (!isLiveTvPlayback) {
                {
                    showSpeedPanel = !showSpeedPanel
                    controlsActivityTick += 1
                }
            } else null,
            speedPickerVisible = showSpeedPanel,
            onSpeedSelected = { speed ->
                setPlaybackSpeedFromPicker(speed)
                showSpeedPanel = false
                controlsActivityTick += 1
            },
            onSpeedPickerDismiss = {
                showSpeedPanel = false
                controlsActivityTick += 1
            },
            nightModeEnabled = playerSettingsUiState.nightModeEnabled,
            onNightModeClick = if (
                playerSettingsUiState.nightModeButtonEnabled && playerController?.supportsNightMode == true
            ) {
                {
                    PlayerSettingsRepository.setNightModeEnabled(!PlayerSettingsRepository.uiState.value.nightModeEnabled)
                    controlsActivityTick += 1
                }
            } else null,
            onSubtitleClick = if (!isLiveTvPlayback) {
                {
                    refreshTracks()
                    showSubtitleModal = true
                }
            } else null,
            onAudioClick = {
                refreshTracks()
                showAudioModal = true
            },
            onCastClick = if (playbackSnapshot.castAvailable || playbackSnapshot.castDeviceName != null) {
                { playerController?.showCastDialog() }
            } else null,
            isCasting = playbackSnapshot.castDeviceName != null,
            onPictureInPictureClick = when {
                pipAvailable -> {
                    {
                        InAppLogger.info("Player/PiP", "start requested")
                        playerController?.startPictureInPicture()
                        controlsVisible = false
                    }
                }
                !isIos && canHandOffToMiniPlayer() -> {
                    {
                        InAppLogger.info("Player/MiniPlayer", "minimize requested")
                        if (handOffToMiniPlayer()) args.onBack()
                    }
                }
                else -> null
            },
            onOrientationToggleClick = if (orientationToggleSupported) {
                {
                    PlayerOrientationPreference.toggle()
                    controlsActivityTick += 1
                }
            } else null,
            isPortraitPlayback = portraitPlayback,
            onInfoClick = {
                val selectedQuality = playerQualityState.selectedVariantFor(selectedPlayerQualityId)
                InAppLogger.info(
                    "Player/Info",
                    "open playback info selectedQuality=${selectedQuality?.qualityName ?: "source"} " +
                        "actual=${playbackSnapshot.videoWidth ?: 0}x${playbackSnapshot.videoHeight ?: 0} " +
                        "mediaInfoChars=${playbackSnapshot.mediaInfoJson.length} " +
                        "source=${InAppLogger.redactUrl(activePlaybackSourceUrl ?: activeSourceUrl)}",
                )
                showStreamInfoModal = true
            },
            onRateClick = if (canRate) {
                { showUserRatingSheet = true }
            } else null,
            userRating = userRating,
            onVideoSettingsClick = if (isIos || isAnimePlayback) {
                {
                    showVideoSettingsModal = true
                    controlsVisible = true
                }
            } else {
                null
            },
            onSourcesClick = if (!isLiveTvPlayback && activeVideoId != null) {
                {
                    openSourcesPanel()
                }
            } else null,
            onEpisodesClick = if (isSeries) {
                {
                    openEpisodesPanel()
                }
            } else null,
            chapters = chapters,
            onChaptersClick = if (chapters.isNotEmpty()) {
                {
                    openChaptersPanel()
                }
            } else null,
            onLiveChannelsClick = if (isLiveTvPlayback) {
                {
                    showLiveChannelsPanel = true
                }
            } else null,
            qualityLabel = playerQualityControlLabel(),
            onQualityClick = {
                openQualityPanel()
            },
            onOpenInExternalPlayer = args.onOpenInExternalPlayer
                ?.takeUnless { ServerStreams.isServerSourceId(activeProviderAddonId) }
                ?.let { openExternal ->
                {
                    val loadedSubtitles = addonSubtitles
                        .takeIf { it.isNotEmpty() }
                        ?.map { sub ->
                            SubtitleInput(
                                url = sub.url,
                                name = buildString {
                                    if (!sub.addonName.isNullOrBlank()) append("[${sub.addonName}] ")
                                    append(sub.display)
                                },
                                lang = sub.language,
                            )
                        }
                    PlayerStreamsRepository.pauseSearchForPlayback()
                    openExternal(
                        ExternalPlayerPlaybackRequest(
                            sourceUrl = activeSourceUrl,
                            title = title,
                            streamTitle = activeStreamTitle,
                            sourceHeaders = activeSourceHeaders,
                            resumePositionMs = playbackSnapshot.positionMs,
                            durationMs = playbackSnapshot.durationMs.takeIf { it > 0L },
                            playbackSession = playbackSession,
                            subtitles = loadedSubtitles,
                            season = activeSeasonNumber,
                            episode = activeEpisodeNumber,
                            episodeTitle = activeEpisodeTitle,
                        ),
                    )
                }
            },
            onSubmitIntroClick = if (
                isSeries &&
                playerSettingsUiState.introSubmitEnabled &&
                playerSettingsUiState.introDbApiKey.isNotBlank()
            ) {
                {
                    InAppLogger.info(
                        "Player/SkipIntro",
                        "open submit dialog videoId=${activeVideoId.orEmpty()} s=${activeSeasonNumber ?: 0} " +
                            "e=${activeEpisodeNumber ?: 0} positionMs=$displayedPositionMs",
                    )
                    showSubmitIntroModal = true
                }
            } else {
                null
            },
            onSwitchEngineClick = if (playerController?.playbackEngine != null) {
                { showSwitchEngineConfirm = true }
            } else {
                null
            },
            onStreamInfoClick = { openStreamInfo() },
            parentalWarnings = parentalWarnings,
            showParentalGuide = showParentalGuide,
            onParentalGuideAnimationComplete = { showParentalGuide = false },
            onScrubChange = { positionMs ->
                isScrubbingTimeline = true
                scrubbingPositionMs = positionMs
            },
            onScrubFinished = { positionMs ->
                val targetMs = if (isPartyControlLocked) ownSeekTarget(positionMs) else positionMs
                if (targetMs == null) {
                    isScrubbingTimeline = false
                    scrubbingPositionMs = null
                } else {
                    finishTimelineScrub(targetMs)
                    playerController?.seekTo(targetMs)
                    scheduleProgressSyncAfterSeek()
                    publishPartyPlayback(positionMs = targetMs)
                }
            },
            horizontalSafePadding = horizontalSafePadding,
            modifier = modifier,
            seekPreview = seekPreview,
            onTimelineTouchChange = { touching -> isTouchingTimeline = touching },
            foldLayout = folded,
            gestureController = gestureController,
            onWake = {
                controlsVisible = true
                controlsActivityTick += 1
            },
        )
    }
    AnimatedVisibility(
        visible = folded && playerControlsLocked && !isInPip,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        FoldLockedControls(
            onUnlock = { unlockPlayerControls() },
            modifier = modifier,
        )
    }
}

@Composable
private fun BoxScope.RenderPlaybackOverlays(
    runtime: PlayerScreenRuntime,
    currentGestureFeedback: GestureFeedbackState?,
    p2pInitialLoadingMessage: String?,
    p2pInitialLoadingProgress: Float?,
    showP2pRebufferStats: Boolean,
    p2pRebufferMessage: String?,
    p2pRebufferProgress: Float?,
    folded: Boolean,
) {
    runtime.run {
        val overlaySnapshot = if (playerControlsLocked && lockedOverlayVisible) {
            playbackSnapshot
        } else {
            Snapshot.withoutReadObservation { playbackSnapshot }
        }
        val snapshotIsLoading by remember(runtime) { derivedStateOf { playbackSnapshot.isLoading } }
        val snapshotDurationMs by remember(runtime) { derivedStateOf { playbackSnapshot.durationMs } }
        val displayedPositionMs = scrubbingPositionMs ?: overlaySnapshot.positionMs
        val openingOverlayVisible = playerSettingsUiState.showLoadingOverlay && !initialLoadCompleted && errorMessage == null
        val showNetworkSpeed = openingOverlayVisible &&
            playerSettingsUiState.showPlayerLoadingStatus &&
            p2pInitialLoadingMessage == null
        val networkSpeedText by produceState<String?>(null, playerController, showNetworkSpeed) {
            value = null
            if (!showNetworkSpeed) return@produceState
            var seenTraffic = false
            while (true) {
                val bytesPerSecond = playerController?.networkSpeedBytesPerSecond()
                if (bytesPerSecond != null && bytesPerSecond > 0L) seenTraffic = true
                value = if (bytesPerSecond != null && seenTraffic) formatNetworkSpeed(bytesPerSecond) else null
                delay(1_000L)
            }
        }
        PlayerPlaybackOverlays(
            playerControlsLocked = playerControlsLocked,
            useLegacyLayout = playerSettingsUiState.useLegacyPlayerLayout,
            lockedOverlayVisible = lockedOverlayVisible && !folded,
            showRemainingTime = showRemainingTime,
            playbackSnapshot = overlaySnapshot,
        displayedPositionMs = displayedPositionMs,
        metrics = metrics,
        horizontalSafePadding = horizontalSafePadding,
        onUnlock = { unlockPlayerControls() },
        showOpeningOverlay = openingOverlayVisible,
        backdropArtwork = if (isLiveTvPlayback) activeLogo?.takeIf(String::isNotBlank) ?: background ?: poster else background ?: poster,
        logo = if (isLiveTvPlayback) activeLogo else logo,
        title = if (isLiveTvPlayback) activeStreamTitle else title,
        onBackWithProgress = {
            flushWatchProgress()
            args.onBack()
        },
        openingLoadingMessage = if (playerSettingsUiState.showPlayerLoadingStatus) {
            p2pInitialLoadingMessage ?: playerLoadingStatusMessage(
                showStatus = true,
                controllerReady = playerController != null,
                buffering = snapshotIsLoading,
            )
        } else null,
        openingNetworkSpeed = networkSpeedText,
        p2pInitialLoadingProgress = p2pInitialLoadingProgress,
        showP2pRebufferStats = showP2pRebufferStats,
        p2pRebufferMessage = p2pRebufferMessage,
        p2pRebufferProgress = p2pRebufferProgress,
        currentGestureFeedback = currentGestureFeedback,
        renderedGestureFeedback = renderedGestureFeedback,
        initialLoadCompleted = initialLoadCompleted,
        pausedOverlayVisible = pausedOverlayVisible,
        activeSkipInterval = activeSkipInterval,
        skipsToPostCredits = activeSkipInterval?.internalSkipAction(skipIntervals, snapshotDurationMs)?.skipsToPostCredits == true,
        skipIntervalDismissed = skipIntervalDismissed,
        controlsVisible = controlsVisible,
        onSkipInterval = { interval ->
            interval.internalSkipAction(skipIntervals, playbackSnapshot.durationMs)?.let { action ->
                val durationMs = playbackSnapshot.durationMs
                val requestedMs = if (durationMs > 0L) action.targetMs.coerceAtMost(durationMs - 1) else action.targetMs
                val seekMs = if (isPartyControlLocked) ownSeekTarget(requestedMs) else requestedMs
                if (seekMs != null) {
                    InAppLogger.info(
                        "Player/SkipIntro",
                        "skip type=${interval.type} provider=${interval.provider} " +
                            "fromMs=${playbackSnapshot.positionMs} targetMs=$seekMs rawMs=${action.targetMs} " +
                            "postCredits=${action.skipsToPostCredits} " +
                            "startSec=${interval.startTime} endSec=${interval.endTime}",
                    )
                    playerController?.seekTo(seekMs)
                    scheduleProgressSyncAfterSeek()
                    publishPartyPlayback(positionMs = seekMs)
                    skipIntervalDismissed = true
                }
            }
        },
        onDismissSkipInterval = {
            activeSkipInterval?.let { interval ->
                InAppLogger.debug(
                    "Player/SkipIntro",
                    "dismiss type=${interval.type} provider=${interval.provider} " +
                        "positionMs=${playbackSnapshot.positionMs}",
                )
            }
            skipIntervalDismissed = true
        },
        sliderEdgePadding = sliderEdgePadding,
        overlayBottomPadding = overlayBottomPadding,
        isSeries = isSeries,
        nextEpisodeInfo = nextEpisodeInfo,
        showNextEpisodeCard = showNextEpisodeCard,
        nextEpisodeAutoPlaySearching = nextEpisodeAutoPlaySearching,
        nextEpisodeAutoPlaySourceName = nextEpisodeAutoPlaySourceName,
        nextEpisodeAutoPlayCountdown = nextEpisodeAutoPlayCountdown,
        blurUnwatchedEpisodes = metaScreenSettingsUiState.blurUnwatchedEpisodes,
        onPlayNextEpisode = {
            if (!partyBlocksControl()) playNextEpisode()
        },
        onDismissNextEpisode = {
            cancelNextEpisodeAutoPlay()
            nextEpisodeCardDismissed = true
            showNextEpisodeCard = false
        },
        movieRecommendations = if (isMoviePlayback && args.onOpenMetaDetails != null) {
            movieRecommendationCandidates
        } else {
            emptyList()
        },
        showMovieRecommendationCard = showMovieRecommendationCard,
        movieRecommendationStage = movieRecommendationStage,
        onOpenMovieRecommendation = { preview ->
            flushWatchProgress()
            args.onOpenMetaDetails?.invoke(preview)
        },
        onDismissMovieRecommendations = {
            movieRecommendationDismissedStage = PlayerNextEpisodeRules.movieRecommendationStage(
                positionMs = playbackSnapshot.positionMs,
                durationMs = playbackSnapshot.durationMs,
                isEnded = playbackSnapshot.isEnded,
            )
            showMovieRecommendationCard = false
        },
        errorMessage = errorMessage,
            onDismissError = {
                flushWatchProgress()
                args.onBack()
            },
        )
    }
}

private fun PlayerScreenRuntime.openQualityPanel() {
    InAppLogger.info(
        "Player/Quality",
        "open quality panel selected=${selectedPlayerQualityId ?: "auto"} " +
            "variants=${playerQualityState.variants.size} loading=${playerQualityState.isLoading} " +
            "actual=${playbackSnapshot.videoWidth ?: 0}x${playbackSnapshot.videoHeight ?: 0}",
    )
    showQualityPanel = true
    showSourcesPanel = false
    showEpisodesPanel = false
    controlsVisible = false
}

private fun PlayerScreenRuntime.playerQualityControlLabel(): String {
    if (playerQualityState.isLoading) return playbackResolutionLabel(forButton = true) ?: "Quality"
    val label = playerQualityState.labelFor(selectedPlayerQualityId, forButton = true)
    if (!label.isNullOrBlank()) {
        return if (selectedPlayerQualityId == null && playerQualityState.hasSelectableQualities) {
            "Auto $label"
        } else {
            label
        }
    }
    return playbackResolutionLabel(forButton = true) ?: "Quality"
}

private fun PlayerScreenRuntime.currentQualityPanelResolutionLabel(): String? {
    playbackResolutionLabel(forButton = false)?.let { return it }
    return playerQualityState.labelFor(selectedPlayerQualityId, forButton = false)
}

private fun PlayerScreenRuntime.playbackResolutionLabel(forButton: Boolean): String? =
    playerQualityNameForResolution(
        width = playbackSnapshot.videoWidth,
        height = playbackSnapshot.videoHeight,
        forButton = forButton,
    )

@Composable
private fun PlayerScreenRuntime.RenderPlayerModals() {
    val displayedPositionMs = if (showChaptersPanel || showSubmitIntroModal) {
        scrubbingPositionMs ?: playbackSnapshot.positionMs
    } else {
        Snapshot.withoutReadObservation { scrubbingPositionMs ?: playbackSnapshot.positionMs }
    }
    val snapshotMediaInfoJson by remember(this) { derivedStateOf { playbackSnapshot.mediaInfoJson } }
    PlayerChaptersPanel(
        visible = showChaptersPanel && chapters.isNotEmpty(),
        chapters = chapters,
        positionMs = displayedPositionMs,
        durationMs = playbackSnapshot.durationMs,
        onChapterSelected = { selectChapter(it) },
        onDismiss = {
            showChaptersPanel = false
            controlsVisible = true
        },
    )
    PlayerScreenModalHosts(
        pendingP2pSwitch = pendingP2pSwitch,
        onPendingP2pSwitchChanged = { pendingP2pSwitch = it },
        onP2pEpisodeStreamSelected = { stream, episode, isAutoPlay ->
            switchToP2pEpisodeStream(stream, episode, isAutoPlay)
        },
        onP2pSourceStreamSelected = { stream -> switchToP2pSourceStream(stream) },
        onNextEpisodeAutoPlaySearchingChanged = { nextEpisodeAutoPlaySearching = it },
        onNextEpisodeAutoPlayCountdownChanged = { nextEpisodeAutoPlayCountdown = it },
        onNextEpisodeAutoPlaySourceNameChanged = { nextEpisodeAutoPlaySourceName = it },
        showAudioModal = showAudioModal,
        audioTracks = serverAudioTracks.ifEmpty { audioTracks },
        selectedAudioIndex = serverAudioTracks.firstOrNull { it.isSelected }?.index ?: selectedAudioIndex,
        onAudioTrackSelected = { index ->
            if (serverAudioTracks.isNotEmpty()) {
                selectServerAudioTrack(index)
            } else {
                selectedAudioIndex = index
                persistAudioPreference(audioTracks.firstOrNull { it.index == index })
                playerController?.selectAudioTrack(index)
            }
            scope.launch {
                kotlinx.coroutines.delay(200)
                showAudioModal = false
            }
        },
        onAudioModalDismissed = { showAudioModal = false },
        showAudioDelay = playerController?.supportsAudioDelay == true,
        audioDelayMs = audioDelayMs,
        onAudioDelayChanged = { delayMs -> setAudioDelay(delayMs) },
        showAudioAmplification = playerController?.supportsAudioAmplification == true,
        audioAmplificationDb = audioAmplificationDb,
        keepAudioAmplification = playerSettingsUiState.persistAudioAmplification,
        onAudioAmplificationChanged = { db -> setAudioAmplification(db) },
        onKeepAudioAmplificationChanged = { keep -> setKeepAudioAmplification(keep) },
        showCenterMix = centerMixAvailable,
        centerMixLevelDb = centerMixLevelDb,
        onCenterMixLevelChanged = { db -> setCenterMixLevel(db) },
        showSubtitleModal = showSubtitleModal,
        subtitleTracks = serverSubtitleTracks.ifEmpty { subtitleTracks },
        selectedSubtitleIndex = if (serverSubtitleTracks.isEmpty()) {
            selectedSubtitleIndex
        } else {
            serverSubtitleTracks.firstOrNull { it.isSelected }?.index ?: -1
        },
        addonSubtitles = visibleAddonSubtitles,
        selectedAddonSubtitleId = selectedAddonSubtitleId,
        isLoadingAddonSubtitles = isLoadingAddonSubtitles,
        subtitleStyle = subtitleStyle,
        subtitleDelayMs = subtitleDelayMs,
        selectedAddonSubtitle = selectedAddonSubtitle,
        subtitleAutoSyncState = subtitleAutoSyncState,
        onBuiltInSubtitleTrackSelected = { index ->
            clearAutoSubtitleState()
            if (serverSubtitleTracks.isNotEmpty() && index >= 0) {
                selectServerSubtitleTrack(index)
            } else {
                val wasCustom = useCustomSubtitles
                isUserExplicitSubtitleSelection = true
                preferredSubtitleSelectionApplied = true
                selectedSubtitleIndex = index
                selectedAddonSubtitleId = null
                useCustomSubtitles = false
                persistInternalSubtitlePreference(subtitleTracks.firstOrNull { it.index == index })
                if (wasCustom) {
                    playerController?.clearExternalSubtitleAndSelect(index)
                } else {
                    playerController?.selectSubtitleTrack(index)
                }
                if (hasBurnedInServerSubtitle) clearServerSubtitleTrack()
            }
        },
        onAddonSubtitleSelected = { addon ->
            clearAutoSubtitleState()
            isUserExplicitSubtitleSelection = true
            selectedAddonSubtitleId = addon.selectionKey
            selectedSubtitleIndex = -1
            useCustomSubtitles = true
            preferredSubtitleSelectionApplied = true
            persistAddonSubtitlePreference(addon)
            attachSelectedAddonSubtitleWithAutoSync(addon.url) // AutoSync hook
            if (hasBurnedInServerSubtitle) clearServerSubtitleTrack()
        },
        onFetchAddonSubtitles = { fetchAddonSubtitlesForActiveItem() },
        onSubtitleStyleChanged = PlayerSettingsRepository::setSubtitleStyle,
        onSubtitleDelayChanged = { delayMs -> setSubtitleDelay(delayMs) },
        onSubtitleDelayReset = { setSubtitleDelay(0) },
        onSyncByEarClick = { openSubtitleSyncByEar() },
        onAutoSyncCapture = { captureSubtitleAutoSyncTime() },
        onAutoSyncCueSelected = { cue -> applySubtitleAutoSyncCue(cue) },
        onAutoSyncReload = { loadSubtitleAutoSyncCues(force = true) },
        onSubtitleModalDismissed = { showSubtitleModal = false },
        showVideoSettingsModal = showVideoSettingsModal,
        playerSettings = playerSettingsUiState,
        onVideoSettingsChanged = {
            playerController?.configureIosVideoOutput(PlayerSettingsRepository.uiState.value)
        },
        onVideoSettingsModalDismissed = { showVideoSettingsModal = false },
        animeEnhancementStatus = playbackSnapshot.animeEnhancement,
        showSourcesPanel = showSourcesPanel,
        sourceStreamsState = sourceStreamsState,
        contentTitle = title,
        activeEpisodeTitle = activeEpisodeTitle,
        currentStream = PlayerCurrentStream(
            url = activeSourceUrl,
            name = activeStreamTitle,
            description = activeStreamSubtitle,
            addonId = activeProviderAddonId,
            infoHash = activeStream?.p2pInfoHash ?: activeTorrentInfoHash,
            fileIdx = activeStream?.p2pFileIdx ?: activeTorrentFileIdx,
            filename = activeSourceFilename,
        ),
        showQualityPanel = showQualityPanel,
        playerQualityState = playerQualityState,
        selectedPlayerQualityId = selectedPlayerQualityId,
        currentQualityLabel = currentQualityPanelResolutionLabel(),
        selectedQualityVariant = playerQualityState.selectedVariantFor(selectedPlayerQualityId),
        selectedQualityIsAuto = selectedPlayerQualityId == null && playerQualityState.hasSelectableQualities,
        onPlayerQualitySelected = { qualityId -> selectPlayerQuality(qualityId) },
        onQualityPanelDismissed = {
            showQualityPanel = false
            controlsVisible = true
        },
        onSourceFilterSelected = PlayerStreamsRepository::selectSourceFilter,
        onSourceStreamSelected = { stream -> switchToSource(stream) },
        onReloadSources = {
            val vid = activeVideoId
            if (vid != null) {
                PlayerStreamsRepository.loadSources(
                    type = contentType ?: parentMetaType,
                    videoId = vid,
                    season = activeSeasonNumber,
                    episode = activeEpisodeNumber,
                    forceRefresh = true,
                )
            }
        },
        onSourcesPanelDismissed = {
            showSourcesPanel = false
            PlayerStreamsRepository.stopSourcesLoading()
            controlsVisible = true
        },
        showLiveChannelsPanel = showLiveChannelsPanel,
        liveTvChannels = liveTvUiState.channels,
        activeLiveChannelId = activeVideoId,
        onLiveChannelSelected = { channel -> switchToLiveChannel(channel) },
        onLiveChannelsPanelDismissed = {
            showLiveChannelsPanel = false
            controlsVisible = true
        },
        isSeries = isSeries,
        showEpisodesPanel = showEpisodesPanel,
        allEpisodes = playerMetaVideos,
        parentMetaType = parentMetaType,
        parentMetaId = parentMetaId,
        activeSeasonNumber = activeSeasonNumber,
        activeEpisodeNumber = activeEpisodeNumber,
        watchProgressByVideoId = watchProgressUiState.byVideoIdForContent(parentMetaId),
        watchedKeys = watchedUiState.watchedKeys,
        blurUnwatchedEpisodes = metaScreenSettingsUiState.blurUnwatchedEpisodes,
        episodeStreamsPanelState = episodeStreamsPanelState,
        episodeStreamsRepoState = episodeStreamsRepoState,
        onEpisodeSelectedForDownload = { episode ->
            selectDownloadedEpisodeForPlayback(
                parentMetaId = parentMetaId,
                episode = episode,
                onDownloadedEpisodeSelected = { item, video -> switchToDownloadedEpisode(item, video) },
            )
        },
        onEpisodeStreamsRequested = { episode ->
            PlayerStreamsRepository.loadEpisodeStreams(
                type = contentType ?: parentMetaType,
                videoId = episode.id,
                season = episode.season,
                episode = episode.episode,
            )
            episodeStreamsPanelState = EpisodeStreamsPanelState(showStreams = true, selectedEpisode = episode)
        },
        onEpisodeStreamFilterSelected = PlayerStreamsRepository::selectEpisodeStreamsFilter,
        onEpisodeStreamSelected = { stream, episode ->
            switchToEpisodeStream(
                stream,
                episode,
                saveForReuse = stream != PartyRepository.sharedStream.value?.toStreamItem(),
            )
        },
        onBackToEpisodes = {
            episodeStreamsPanelState = EpisodeStreamsPanelState()
            PlayerStreamsRepository.clearEpisodeStreams()
        },
        onReloadEpisodeStreams = {
            val episode = episodeStreamsPanelState.selectedEpisode
            if (episode != null) {
                PlayerStreamsRepository.loadEpisodeStreams(
                    type = contentType ?: parentMetaType,
                    videoId = episode.id,
                    season = episode.season,
                    episode = episode.episode,
                    forceRefresh = true,
                )
            }
        },
        onEpisodesPanelDismissed = {
            showEpisodesPanel = false
            episodeStreamsPanelState = EpisodeStreamsPanelState()
            PlayerStreamsRepository.clearEpisodeStreams()
            controlsVisible = true
        },
        showSubmitIntroModal = showSubmitIntroModal,
        activeVideoId = activeVideoId,
        metaUiState = metaUiState,
        displayedPositionMs = displayedPositionMs,
        submitIntroSegmentType = submitIntroSegmentType,
        onSubmitIntroSegmentTypeChanged = { submitIntroSegmentType = it },
        submitIntroStartTimeStr = submitIntroStartTimeStr,
        onSubmitIntroStartTimeChanged = { submitIntroStartTimeStr = it },
        submitIntroEndTimeStr = submitIntroEndTimeStr,
        onSubmitIntroEndTimeChanged = { submitIntroEndTimeStr = it },
        onSubmitIntroDismissed = { showSubmitIntroModal = false },
        onSubmitIntroSuccess = {
            InAppLogger.info(
                "Player/SkipIntro",
                "submit success videoId=${activeVideoId.orEmpty()} s=${activeSeasonNumber ?: 0} " +
                    "e=${activeEpisodeNumber ?: 0} type=$submitIntroSegmentType " +
                    "start=$submitIntroStartTimeStr end=$submitIntroEndTimeStr",
            )
            submitIntroStartTimeStr = "00:00"
            submitIntroEndTimeStr = "00:00"
            submitIntroSegmentType = "intro"
            showSubmitIntroModal = false
        },
        showStreamInfoModal = showStreamInfoModal,
        mediaInfoJson = snapshotMediaInfoJson,
        onStreamInfoModalDismissed = { showStreamInfoModal = false },
    )

    if (showUserRatingSheet) {
        currentUserRatingTarget()?.let { target ->
            UserRatingPlayerOverlay(
                target = target,
                title = activeEpisodeTitle?.takeIf { target.episode != null && it.isNotBlank() } ?: title,
                subtitle = if (target.episode != null) {
                    listOfNotNull(
                        title,
                        localizedSeasonEpisodeCode(target.season, target.episode),
                    ).joinToString(" · ")
                } else {
                    null
                },
                onDismiss = {
                    showUserRatingSheet = false
                    controlsVisible = true
                },
            )
        }
    }
    StreamInfoOverlay(
        visible = showStreamInfo,
        addonName = activeProviderName,
        addonLogo = activeAddonLogo,
        streamName = activeStreamTitle,
        streamDescription = activeStreamSubtitle,
        playbackEngine = playerController?.playbackEngine,
        serverPlayback = serverPlaybackSummary(),
        mediaInfo = streamMediaInfo,
        audioTrack = audioTracks.firstOrNull { it.index == selectedAudioIndex },
        subtitleTrack = subtitleTracks.firstOrNull { it.index == selectedSubtitleIndex }.takeIf { !useCustomSubtitles },
        addonSubtitle = selectedAddonSubtitle.takeIf { useCustomSubtitles },
        onDismiss = { showStreamInfo = false },
    )
    val switchEngineTarget = playerController?.playbackEngine?.switchTarget
    if (switchEngineTarget != null) {
        NuvioStatusModal(
            isVisible = showSwitchEngineConfirm,
            title = stringResource(Res.string.player_engine_switch_title, switchEngineTarget.label),
            message = stringResource(Res.string.player_engine_switch_message),
            confirmText = stringResource(Res.string.action_switch),
            dismissText = stringResource(Res.string.action_cancel),
            onConfirm = {
                showSwitchEngineConfirm = false
                switchPlaybackEngine()
            },
            onDismiss = { showSwitchEngineConfirm = false },
        )
    }
}

/** The movie or episode currently playing, for in-player rating. */
@Composable
private fun PlayerScreenRuntime.currentUserRatingTarget(): TrackingRatingTarget? =
    remember(parentMetaId, contentType, parentMetaType, activeVideoId, activeSeasonNumber, activeEpisodeNumber) {
        if (isLiveTvPlayback) null else currentTrackingMedia().toUserRatingTarget()
    }

private const val MOVIE_RECOMMENDATION_LIMIT = 10

internal val PlayerScreenRuntime.movieRecommendationCandidates: List<MetaPreview>
    get() {
        val watchedKeys = watchedUiState.watchedKeys
        return playerMeta?.moreLikeThis.orEmpty()
            .asSequence()
            .filterNot { it.id == parentMetaId }
            .filterNot { WatchingState.isPosterWatched(watchedKeys = watchedKeys, item = it) }
            .take(MOVIE_RECOMMENDATION_LIMIT)
            .toList()
    }

