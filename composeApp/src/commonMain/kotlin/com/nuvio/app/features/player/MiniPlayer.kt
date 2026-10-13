package com.nuvio.app.features.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.PlatformBackHandler
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.profiles.ProfileRepository
import com.nuvio.app.features.servers.ServerPlayback
import com.nuvio.app.features.tracking.TrackingMediaReference
import com.nuvio.app.features.tracking.TrackingScrobbleAction
import com.nuvio.app.features.tracking.TrackingScrobbleCoordinator
import com.nuvio.app.features.tracking.TrackingScrobbleEvent
import com.nuvio.app.features.watching.domain.isShortPlaceholderDuration
import com.nuvio.app.features.watchprogress.WatchProgressClock
import com.nuvio.app.features.watchprogress.WatchProgressPlaybackSession
import com.nuvio.app.features.watchprogress.WatchProgressRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.mini_player_close
import nuvio.composeapp.generated.resources.mini_player_expand
import nuvio.composeapp.generated.resources.mini_player_pause
import nuvio.composeapp.generated.resources.mini_player_play
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

data class MiniPlayerSession(
    val id: Long,
    val launch: PlayerLaunch,
    val playbackUrl: String,
    val startPositionMs: Long,
    val startPlaying: Boolean,
    val progressSession: WatchProgressPlaybackSession?,
    val trackingMedia: TrackingMediaReference?,
)

object MiniPlayerController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var nextSessionId = 1L
    private val _session = MutableStateFlow<MiniPlayerSession?>(null)
    val session: StateFlow<MiniPlayerSession?> = _session.asStateFlow()
    private val _activeFullPlayers = MutableStateFlow(0)
    val activeFullPlayers: StateFlow<Int> = _activeFullPlayers.asStateFlow()
    var isActive by mutableStateOf(false)
        private set
    private var lastSnapshot: PlayerPlaybackSnapshot? = null
    private var lastPersistEpochMs = 0L

    internal fun start(
        launch: PlayerLaunch,
        playbackUrl: String,
        startPositionMs: Long,
        startPlaying: Boolean,
        progressSession: WatchProgressPlaybackSession?,
        trackingMedia: TrackingMediaReference?,
    ) {
        close()
        lastSnapshot = null
        lastPersistEpochMs = WatchProgressClock.nowEpochMs()
        _session.value = MiniPlayerSession(
            id = nextSessionId++,
            launch = launch,
            playbackUrl = playbackUrl,
            startPositionMs = startPositionMs,
            startPlaying = startPlaying,
            progressSession = progressSession,
            trackingMedia = trackingMedia,
        )
        isActive = true
    }

    internal fun onSnapshot(sessionId: Long, snapshot: PlayerPlaybackSnapshot) {
        val current = _session.value ?: return
        if (current.id != sessionId) return
        lastSnapshot = snapshot
        if (snapshot.isEnded) {
            close()
            return
        }
        if (!snapshot.isPlaying) return
        val progressSession = current.progressSession ?: return
        if (isShortPlaceholderDuration(snapshot.durationMs)) return
        val now = WatchProgressClock.nowEpochMs()
        if (now - lastPersistEpochMs < PlaybackProgressPersistIntervalMs) return
        lastPersistEpochMs = now
        WatchProgressRepository.upsertPlaybackProgress(
            session = progressSession,
            snapshot = snapshot,
            syncRemote = false,
        )
    }

    internal fun resumePositionMs(session: MiniPlayerSession): Long {
        if (_session.value?.id != session.id) return session.startPositionMs
        return lastSnapshot?.positionMs?.takeIf { it > 0L } ?: session.startPositionMs
    }

    fun expand(): PlayerLaunch? {
        val current = _session.value ?: return null
        val positionMs = lastSnapshot?.positionMs?.takeIf { it > 0L } ?: current.startPositionMs
        clearSession()
        return current.launch.copy(
            initialPositionMs = positionMs,
            initialProgressFraction = null,
        )
    }

    fun close() {
        val current = _session.value ?: return
        val snapshot = lastSnapshot
        clearSession()
        recordProgress(current, snapshot)
    }

    fun discard() {
        clearSession()
    }

    internal fun onFullPlayerEntered() {
        _activeFullPlayers.update { it + 1 }
        close()
    }

    internal fun onFullPlayerExited() {
        _activeFullPlayers.update { (it - 1).coerceAtLeast(0) }
    }

    private fun clearSession() {
        _session.value = null
        lastSnapshot = null
        isActive = false
    }

    private fun recordProgress(session: MiniPlayerSession, snapshot: PlayerPlaybackSnapshot?) {
        val progressSession = session.progressSession ?: return
        val finalSnapshot = snapshot ?: return
        if (finalSnapshot.durationMs <= 0L || isShortPlaceholderDuration(finalSnapshot.durationMs)) return
        WatchProgressRepository.flushPlaybackProgress(
            session = progressSession,
            snapshot = finalSnapshot.copy(isPlaying = false, isLoading = false),
        )
        val media = session.trackingMedia ?: return
        if (!media.hasResolvableIdentity) return
        val progressPercent = (finalSnapshot.positionMs.toDouble() / finalSnapshot.durationMs.toDouble() * 100.0)
            .coerceIn(0.0, 100.0)
        if (progressPercent < 1.0) return
        scope.launch {
            runCatching {
                TrackingScrobbleCoordinator.scrobble(
                    profileId = progressSession.profileId,
                    action = TrackingScrobbleAction.STOP,
                    event = TrackingScrobbleEvent(
                        media = media,
                        progressPercent = progressPercent,
                    ),
                )
            }
        }
    }
}

internal fun PlayerScreenRuntime.handOffToMiniPlayer() {
    if (!playerSettingsUiState.miniPlayerEnabled) return
    if (errorMessage != null || !initialLoadCompleted || playbackSnapshot.isEnded) return
    if (activeTorrentInfoHash != null || p2pResolvedSourceUrl != null) return
    if (playbackSnapshot.castDeviceName != null) return
    if (ServerPlayback.session(activeSourceUrl) != null) return
    val launchId = args.launchId ?: return
    val baseLaunch = PlayerLaunchStore.get(launchId) ?: return
    val playbackUrl = activePlaybackSourceUrl ?: activeSourceUrl
    if (playbackUrl.isBlank()) return
    val positionMs = if (isLiveTvPlayback) 0L else playbackSnapshot.positionMs.coerceAtLeast(0L)
    val trackProgress = shouldTrackWatchProgress
    MiniPlayerController.start(
        launch = currentLaunch(baseLaunch).copy(
            initialPositionMs = positionMs,
            initialProgressFraction = null,
        ),
        playbackUrl = playbackUrl,
        startPositionMs = positionMs,
        startPlaying = shouldPlay,
        progressSession = if (trackProgress) playbackSession else null,
        trackingMedia = if (trackProgress) currentTrackingMedia() else null,
    )
}

@Composable
internal fun PlayerScreenRuntime.BindMiniPlayerEffects() {
    val settings by PlayerSettingsRepository.uiState.collectAsStateWithLifecycle()
    DisposableEffect(Unit) {
        MiniPlayerController.onFullPlayerEntered()
        onDispose { MiniPlayerController.onFullPlayerExited() }
    }
    val canMinimize by remember(this) {
        derivedStateOf { errorMessage == null && initialLoadCompleted }
    }
    PlatformBackHandler(enabled = settings.miniPlayerEnabled && canMinimize) {
        flushWatchProgress()
        handOffToMiniPlayer()
        args.onBack()
    }
}

private val MiniPlayerShape = RoundedCornerShape(14.dp)
private val MiniPlayerInfoHeight = 52.dp
private val MiniPlayerProgressHeight = 2.dp
private val MiniPlayerEdgeMargin = 12.dp
private val MiniPlayerBottomClearance = 96.dp

@Composable
fun MiniPlayerHost(
    hidden: Boolean,
    onExpand: (PlayerLaunch) -> Unit,
    modifier: Modifier = Modifier,
) {
    val session by MiniPlayerController.session.collectAsStateWithLifecycle()
    val activeFullPlayers by MiniPlayerController.activeFullPlayers.collectAsStateWithLifecycle()
    val profileState by ProfileRepository.state.collectAsStateWithLifecycle()
    val current = session
    LaunchedEffect(current?.id, profileState) {
        if (current != null && current.launch.profileId != ProfileRepository.activeProfileId) {
            MiniPlayerController.discard()
        }
    }
    if (current == null || hidden) return
    key(current.id) {
        MiniPlayerWindow(
            session = current,
            surfaceEnabled = activeFullPlayers == 0,
            onExpand = onExpand,
            modifier = modifier,
        )
    }
}

@Composable
private fun MiniPlayerWindow(
    session: MiniPlayerSession,
    surfaceEnabled: Boolean,
    onExpand: (PlayerLaunch) -> Unit,
    modifier: Modifier,
) {
    val colors = MaterialTheme.nuvio.colors
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val startPositionMs = remember { MiniPlayerController.resumePositionMs(session) }
    var controller by remember { mutableStateOf<PlayerEngineController?>(null) }
    var snapshot by remember { mutableStateOf(PlayerPlaybackSnapshot(positionMs = startPositionMs)) }
    var playing by remember { mutableStateOf(session.startPlaying) }
    var initialSeekDone by remember { mutableStateOf(startPositionMs <= 0L) }
    var failed by remember { mutableStateOf(false) }
    val requestKey = remember { "mini-player:${session.id}" }
    val expand: () -> Unit = {
        MiniPlayerController.expand()?.let(onExpand)
    }
    val launch = session.launch
    val isLive = launch.contentType == "live"
    val title = if (isLive) launch.streamTitle else launch.title
    val subtitle = when {
        launch.seasonNumber != null && launch.episodeNumber != null -> listOfNotNull(
            "S${launch.seasonNumber} E${launch.episodeNumber}",
            launch.episodeTitle?.takeIf { it.isNotBlank() },
        ).joinToString(" · ")
        isLive -> launch.streamSubtitle?.takeIf { it.isNotBlank() }
        else -> launch.streamTitle.takeIf { it.isNotBlank() } ?: launch.providerName.takeIf { it.isNotBlank() }
    }
    val artwork = launch.background ?: launch.poster ?: launch.logo

    LaunchedEffect(controller, snapshot.isLoading, snapshot.durationMs, initialSeekDone) {
        val player = controller ?: return@LaunchedEffect
        if (initialSeekDone || snapshot.isLoading || snapshot.durationMs <= 0L) return@LaunchedEffect
        player.seekTo(startPositionMs.coerceAtMost(snapshot.durationMs))
        initialSeekDone = true
    }

    BoxWithConstraints(modifier = modifier) {
        val containerWidthPx = constraints.maxWidth.toFloat()
        val containerHeightPx = constraints.maxHeight.toFloat()
        val cardWidth = if (maxWidth >= 600.dp) 320.dp else maxWidth * 0.58f
        val videoHeight = cardWidth * 9f / 16f
        val cardWidthPx = with(density) { cardWidth.toPx() }
        val cardHeightPx = with(density) { (videoHeight + MiniPlayerInfoHeight + MiniPlayerProgressHeight).toPx() }
        val marginPx = with(density) { MiniPlayerEdgeMargin.toPx() }
        val topInsetPx = WindowInsets.safeDrawing.getTop(density).toFloat()
        val bottomInsetPx = WindowInsets.safeDrawing.getBottom(density).toFloat()
        val bottomClearancePx = with(density) { MiniPlayerBottomClearance.toPx() }
        val minX = marginPx
        val maxX = (containerWidthPx - cardWidthPx - marginPx).coerceAtLeast(minX)
        val minY = topInsetPx + marginPx
        val maxY = (containerHeightPx - cardHeightPx - bottomInsetPx - bottomClearancePx).coerceAtLeast(minY)
        val offsetX = remember { Animatable(maxX) }
        val offsetY = remember { Animatable(maxY) }
        var dockedToEnd by remember { mutableStateOf(true) }

        LaunchedEffect(minX, maxX, minY, maxY) {
            offsetX.updateBounds(minX, maxX)
            offsetY.updateBounds(minY, maxY)
            offsetX.snapTo(if (dockedToEnd) maxX else minX)
            offsetY.snapTo(offsetY.value.coerceIn(minY, maxY))
        }

        Column(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), offsetY.value.roundToInt()) }
                .width(cardWidth)
                .shadow(16.dp, MiniPlayerShape)
                .clip(MiniPlayerShape)
                .background(colors.surfaceElevated)
                .pointerInput(minX, maxX, minY, maxY) {
                    val dock: () -> Unit = {
                        dockedToEnd = offsetX.value + cardWidthPx / 2f > containerWidthPx / 2f
                        scope.launch {
                            offsetX.animateTo(if (dockedToEnd) maxX else minX, tween(durationMillis = 220))
                        }
                    }
                    detectDragGestures(
                        onDragEnd = dock,
                        onDragCancel = dock,
                    ) { change, dragAmount ->
                        change.consume()
                        scope.launch {
                            offsetX.snapTo(offsetX.value + dragAmount.x)
                            offsetY.snapTo(offsetY.value + dragAmount.y)
                        }
                    }
                },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(videoHeight)
                    .background(Color.Black)
                    .clickable(onClick = expand),
            ) {
                if (!surfaceEnabled && artwork != null) {
                    AsyncImage(
                        model = artwork,
                        contentDescription = stringResource(Res.string.mini_player_expand),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize(),
                    )
                }
                if (surfaceEnabled) {
                    PlatformPlayerSurface(
                        sourceUrl = session.playbackUrl,
                        sourceAudioUrl = launch.sourceAudioUrl,
                        sourceHeaders = launch.sourceHeaders,
                        sourceResponseHeaders = launch.sourceResponseHeaders,
                        streamType = launch.streamType,
                        modifier = Modifier.matchParentSize(),
                        playWhenReady = playing,
                        initialPositionMs = startPositionMs.takeIf { it > 0L },
                        initialPositionRequestKey = requestKey,
                        resizeMode = PlayerResizeMode.Fit,
                        onInitialPositionHandled = { key, handled ->
                            if (key == requestKey && handled) initialSeekDone = true
                        },
                        onControllerReady = { controller = it },
                        onSnapshot = { update ->
                            snapshot = update
                            if (initialSeekDone) MiniPlayerController.onSnapshot(session.id, update)
                        },
                        onError = { message -> failed = message != null },
                    )
                }
                if (!failed && (!surfaceEnabled || snapshot.isLoading)) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(26.dp),
                        color = Color.White,
                        strokeWidth = 2.dp,
                    )
                }
            }
            val progressFraction = if (snapshot.durationMs > 0L) {
                (snapshot.positionMs.toFloat() / snapshot.durationMs.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(MiniPlayerProgressHeight)
                    .background(Color.White.copy(alpha = 0.12f)),
            ) {
                if (snapshot.durationMs > 0L) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progressFraction)
                            .fillMaxHeight()
                            .background(colors.accent),
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(MiniPlayerInfoHeight)
                    .padding(start = 12.dp, end = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = expand),
                ) {
                    Text(
                        text = title,
                        color = colors.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            color = colors.textMuted,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                IconButton(
                    onClick = {
                        playing = !playing
                        if (playing) controller?.play() else controller?.pause()
                    },
                ) {
                    Icon(
                        imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(
                            if (playing) Res.string.mini_player_pause else Res.string.mini_player_play,
                        ),
                        tint = colors.textPrimary,
                    )
                }
                IconButton(onClick = { MiniPlayerController.close() }) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(Res.string.mini_player_close),
                        tint = colors.textPrimary,
                    )
                }
            }
        }
    }
}
