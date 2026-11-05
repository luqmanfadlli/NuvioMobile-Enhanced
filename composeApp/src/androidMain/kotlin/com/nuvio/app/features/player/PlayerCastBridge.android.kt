package com.nuvio.app.features.player

import com.nuvio.app.features.autosync.AutoSyncPlayerController
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CastConnected
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.mediarouter.app.MediaRouteChooserDialog
import androidx.mediarouter.app.MediaRouteControllerDialog
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaSeekOptions
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.CastState
import com.google.android.gms.cast.framework.CastStateListener
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.images.WebImage
import kotlinx.coroutines.runBlocking
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

private const val CAST_PROGRESS_INTERVAL_MS = 500L
private const val CAST_METADATA_GRACE_MS = 1_500L
private const val CAST_STOP_DELAY_MS = 1_200L

internal data class PlayerCastSource(
    val url: String,
    val audioUrl: String?,
    val responseHeaders: Map<String, String>,
    val streamType: String?,
    val initialPositionMs: Long?,
)

internal class PlayerCastBridge(private val hostContext: Context) {

    private val appContext: Context = hostContext.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    private val castContext: CastContext? = runCatching {
        val availability = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(appContext)
        if (availability == ConnectionResult.SUCCESS) CastContext.getSharedInstance(appContext) else null
    }.getOrNull()

    private val mediaRouter: MediaRouter? = runCatching { MediaRouter.getInstance(appContext) }.getOrNull()
    private val routeListener = object : MediaRouter.Callback() {
        override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) = onRoutesChanged()
        override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) = onRoutesChanged()
        override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) = onRoutesChanged()
    }

    var isCasting by mutableStateOf(false)
        private set

    var deviceName by mutableStateOf<String?>(null)
        private set

    private var castState: Int = castContext?.castState ?: CastState.NO_DEVICES_AVAILABLE
    private var session: CastSession? = null
    private var client: RemoteMediaClient? = null
    private var delegate: PlayerEngineController? = null
    private var source: PlayerCastSource? = null
    private var nowPlaying: PlayerNowPlayingInfo? = null
    private var snapshotSink: (PlayerPlaybackSnapshot) -> Unit = {}
    private var controllerSink: (PlayerEngineController) -> Unit = {}
    private var latestLocal = PlayerPlaybackSnapshot()
    private var loadedUrl: String? = null
    private var pendingStartMs: Long = 0L
    private var metadataGraceOver = false
    private var metadataGraceScheduled = false
    private var warnedUrl: String? = null
    private var lastRemotePositionMs: Long = 0L
    private var lastRemotePlaying: Boolean = false
    private var lastRemoteEnded: Boolean = false
    private var started = false

    private val castStateListener = CastStateListener { state ->
        castState = state
        if (!isCasting) emitLocal()
    }

    private fun onRoutesChanged() {
        if (started && !isCasting) emitLocal()
    }

    private fun hasCastRoutes(): Boolean = runCatching {
        val selector = castContext?.mergedSelector ?: return@runCatching false
        mediaRouter?.routes?.any { it.isEnabled && it.matchesSelector(selector) } == true
    }.getOrDefault(false)

    private val sessionListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarting(session: CastSession) = Unit
        override fun onSessionStarted(session: CastSession, sessionId: String) = onSessionAvailable(session)
        override fun onSessionStartFailed(session: CastSession, error: Int) = Unit
        override fun onSessionEnding(session: CastSession) = Unit
        override fun onSessionEnded(session: CastSession, error: Int) = onSessionGone(session)
        override fun onSessionResuming(session: CastSession, sessionId: String) = Unit
        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) = onSessionAvailable(session)
        override fun onSessionResumeFailed(session: CastSession, error: Int) = onSessionGone(session)
        override fun onSessionSuspended(session: CastSession, reason: Int) = Unit
    }

    private val remoteCallback = object : RemoteMediaClient.Callback() {
        override fun onStatusUpdated() = onRemoteStatus()
        override fun onMetadataUpdated() = onRemoteStatus()
    }

    private val progressListener = RemoteMediaClient.ProgressListener { _, _ ->
        if (isCasting) emitRemote()
    }

    fun start() {
        val cast = castContext ?: return
        if (started) return
        started = true
        pendingStop?.let { stopHandler.removeCallbacks(it) }
        pendingStop = null
        castState = cast.castState
        cast.mergedSelector?.let { selector ->
            runCatching {
                mediaRouter?.addCallback(selector, routeCallback, MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY)
                mediaRouter?.addCallback(selector, routeListener, 0)
            }
        }
        cast.addCastStateListener(castStateListener)
        cast.sessionManager.addSessionManagerListener(sessionListener, CastSession::class.java)
        cast.sessionManager.currentCastSession
            ?.takeIf { it.isConnected }
            ?.let(::onSessionAvailable)
        emitLocal()
    }

    fun release() {
        val cast = castContext
        if (cast != null && started) {
            cast.removeCastStateListener(castStateListener)
            runCatching { mediaRouter?.removeCallback(routeListener) }
            cast.sessionManager.removeSessionManagerListener(sessionListener, CastSession::class.java)
        }
        started = false
        val hadMedia = isCasting && loadedUrl != null
        detachClient()
        mainHandler.removeCallbacksAndMessages(null)
        if (hadMedia) scheduleRemoteStop(appContext)
    }

    fun bind(
        source: PlayerCastSource,
        snapshotSink: (PlayerPlaybackSnapshot) -> Unit,
        controllerSink: (PlayerEngineController) -> Unit,
    ) {
        val changed = this.source?.url != source.url
        this.source = source
        this.snapshotSink = snapshotSink
        this.controllerSink = controllerSink
        if (changed && started) refreshMode()
    }

    fun onLocalControllerReady(controller: PlayerEngineController) {
        delegate = controller
        controllerSink(
            if (controller is AutoSyncPlayerController) {
                AutoSyncCastAwareController(controller, this, controller)
            } else {
                PlayerCastAwareController(controller, this)
            },
        )
        if (isCasting) controller.pause()
    }

    fun onLocalSnapshot(snapshot: PlayerPlaybackSnapshot) {
        latestLocal = snapshot
        if (isCasting) {
            if (snapshot.isPlaying) delegate?.pause()
        } else {
            emitLocal()
        }
    }

    fun onNowPlaying(info: PlayerNowPlayingInfo) {
        nowPlaying = info
        if (isCasting) ensureLoaded()
    }

    fun showDialog() {
        val cast = castContext ?: return
        val activity = hostContext.findHostActivity() ?: return
        if (activity.isFinishing) return
        runCatching {
            if (cast.castState == CastState.CONNECTED || cast.castState == CastState.CONNECTING) {
                MediaRouteControllerDialog(activity).show()
            } else {
                val selector = cast.mergedSelector ?: return
                MediaRouteChooserDialog(activity).also { it.routeSelector = selector }.show()
            }
        }
    }

    fun play(): Boolean {
        if (!isCasting) return false
        client?.takeIf { loadedUrl != null }?.play()
        return true
    }

    fun pause(): Boolean {
        if (!isCasting) return false
        client?.takeIf { loadedUrl != null }?.pause()
        return true
    }

    fun seekTo(positionMs: Long): Boolean {
        if (!isCasting) return false
        val target = positionMs.coerceAtLeast(0L)
        client?.takeIf { loadedUrl != null }?.seek(
            MediaSeekOptions.Builder().setPosition(target).build(),
        )
        return true
    }

    fun seekBy(offsetMs: Long): Boolean {
        if (!isCasting) return false
        val current = client?.approximateStreamPosition ?: lastRemotePositionMs
        val duration = client?.streamDuration?.takeIf { it > 0L } ?: Long.MAX_VALUE
        return seekTo((current + offsetMs).coerceIn(0L, duration))
    }

    fun setSpeed(speed: Float): Boolean {
        if (!isCasting) return false
        client?.takeIf { loadedUrl != null }?.setPlaybackRate(speed.toDouble().coerceIn(0.5, 2.0))
        return true
    }

    fun retry(): Boolean {
        if (!isCasting) return false
        loadedUrl = null
        ensureLoaded()
        return true
    }

    fun remoteVolume(): PlayerAudioLevel? {
        if (!isCasting) return null
        val current = session ?: return null
        return runCatching {
            PlayerAudioLevel(
                fraction = current.volume.toFloat().coerceIn(0f, 1f),
                isMuted = current.isMute,
            )
        }.getOrNull()
    }

    fun setRemoteVolume(level: Float): PlayerAudioLevel? {
        if (!isCasting) return null
        val current = session ?: return null
        val target = level.coerceIn(0f, 1f)
        return runCatching {
            current.volume = target.toDouble()
            PlayerAudioLevel(fraction = target, isMuted = target <= 0.001f)
        }.getOrNull()
    }

    private fun onSessionAvailable(newSession: CastSession) {
        detachClient()
        session = newSession
        deviceName = newSession.castDevice?.friendlyName
        val remote = newSession.remoteMediaClient
        client = remote
        remote?.registerCallback(remoteCallback)
        remote?.addProgressListener(progressListener, CAST_PROGRESS_INTERVAL_MS)
        loadedUrl = null
        refreshMode()
        if (!isCasting) emitLocal()
    }

    private fun onSessionGone(endedSession: CastSession) {
        if (session != null && session !== endedSession) return
        val wasCasting = isCasting
        val resumeAt = lastRemotePositionMs
        val resumePlaying = lastRemotePlaying
        val ended = lastRemoteEnded
        val hadMedia = loadedUrl != null
        detachClient()
        session = null
        loadedUrl = null
        isCasting = false
        deviceName = null
        if (wasCasting && hadMedia && !ended) {
            delegate?.seekTo(resumeAt)
            if (resumePlaying) delegate?.play()
        }
        emitLocal()
    }

    private fun detachClient() {
        client?.unregisterCallback(remoteCallback)
        client?.removeProgressListener(progressListener)
        client = null
    }

    private fun refreshMode() {
        val src = source
        val connected = session?.isConnected == true && client != null
        val castable = src != null && src.isCastable()
        val shouldCast = connected && castable
        if (connected && src != null && !castable && warnedUrl != src.url) {
            warnedUrl = src.url
            showMessage(Res.string.player_cast_stream_unsupported)
        }
        if (shouldCast && !isCasting) {
            isCasting = true
            delegate?.pause()
        } else if (!shouldCast && isCasting) {
            isCasting = false
            loadedUrl = null
            emitLocal()
            return
        }
        if (isCasting) {
            ensureLoaded()
            emitRemote()
        }
    }

    private fun ensureLoaded() {
        val remote = client ?: return
        val src = source ?: return
        if (loadedUrl == src.url) return
        if (nowPlaying == null && !metadataGraceOver) {
            if (!metadataGraceScheduled) {
                metadataGraceScheduled = true
                mainHandler.postDelayed(
                    {
                        metadataGraceOver = true
                        if (isCasting) ensureLoaded()
                    },
                    CAST_METADATA_GRACE_MS,
                )
            }
            return
        }
        val startMs = latestLocal.positionMs.takeIf { it > 0L }
            ?: src.initialPositionMs
            ?: 0L
        pendingStartMs = startMs
        loadedUrl = src.url
        lastRemoteEnded = false
        val request = MediaLoadRequestData.Builder()
            .setMediaInfo(buildMediaInfo(src))
            .setAutoplay(true)
            .setCurrentTime(startMs)
            .build()
        remote.load(request).setResultCallback { result ->
            if (loadedUrl != src.url) return@setResultCallback
            if (!result.status.isSuccess) onRemoteFailure()
        }
        emitRemote()
    }

    private fun buildMediaInfo(src: PlayerCastSource): MediaInfo {
        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE)
        nowPlaying?.let { info ->
            metadata.putString(MediaMetadata.KEY_TITLE, info.title)
            info.subtitle?.takeIf { it.isNotBlank() }
                ?.let { metadata.putString(MediaMetadata.KEY_SUBTITLE, it) }
            info.artworkUrl?.takeIf { it.startsWith("http", ignoreCase = true) }
                ?.let { metadata.addImage(WebImage(Uri.parse(it))) }
        }
        val mimeType = playbackMediaItemFromUrl(
            url = src.url,
            responseHeaders = src.responseHeaders,
            streamType = src.streamType,
        ).localConfiguration?.mimeType ?: "video/mp4"
        return MediaInfo.Builder(src.url)
            .setContentUrl(src.url)
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType(mimeType)
            .setMetadata(metadata)
            .build()
    }

    private fun onRemoteStatus() {
        val remote = client ?: return
        val status = remote.mediaStatus
        val matches = loadedUrl != null && status?.mediaInfo?.contentId == loadedUrl
        if (matches && status != null &&
            status.playerState == MediaStatus.PLAYER_STATE_IDLE &&
            status.idleReason == MediaStatus.IDLE_REASON_ERROR
        ) {
            onRemoteFailure()
            return
        }
        if (isCasting) emitRemote()
    }

    private fun onRemoteFailure() {
        showMessage(Res.string.player_cast_playback_failed)
        runCatching { castContext?.sessionManager?.endCurrentSession(true) }
    }

    private fun emitLocal() {
        snapshotSink(
            latestLocal.copy(
                castAvailable = castContext != null && (castState != CastState.NO_DEVICES_AVAILABLE || hasCastRoutes()),
                castDeviceName = deviceName,
            ),
        )
    }

    private fun emitRemote() {
        val remote = client
        val status = remote?.mediaStatus
        val matches = remote != null && loadedUrl != null && status?.mediaInfo?.contentId == loadedUrl
        val state = if (matches) status?.playerState else null
        val ended = matches && state == MediaStatus.PLAYER_STATE_IDLE &&
            status?.idleReason == MediaStatus.IDLE_REASON_FINISHED
        val positionMs = if (matches) {
            remote?.approximateStreamPosition?.coerceAtLeast(0L) ?: pendingStartMs
        } else {
            pendingStartMs
        }
        val durationMs = remote?.streamDuration?.takeIf { matches && it > 0L } ?: latestLocal.durationMs
        val playing = state == MediaStatus.PLAYER_STATE_PLAYING || state == MediaStatus.PLAYER_STATE_BUFFERING
        lastRemotePositionMs = if (ended && durationMs > 0L) durationMs else positionMs
        lastRemotePlaying = playing
        lastRemoteEnded = ended
        snapshotSink(
            latestLocal.copy(
                isLoading = !matches ||
                    state == MediaStatus.PLAYER_STATE_LOADING ||
                    state == MediaStatus.PLAYER_STATE_BUFFERING,
                isPlaying = playing,
                isEnded = ended,
                durationMs = durationMs,
                positionMs = lastRemotePositionMs,
                bufferedPositionMs = lastRemotePositionMs,
                playbackSpeed = status?.playbackRate?.toFloat() ?: 1f,
                castAvailable = true,
                castDeviceName = deviceName,
            ),
        )
    }

    private fun showMessage(resource: StringResource) {
        val text = runBlocking { getString(resource) }
        Toast.makeText(appContext, text, Toast.LENGTH_LONG).show()
    }

    private fun PlayerCastSource.isCastable(): Boolean {
        if (!audioUrl.isNullOrBlank()) return false
        val parsed = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        val scheme = parsed.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return false
        return when (parsed.host?.lowercase()) {
            null, "", "localhost", "127.0.0.1", "0.0.0.0", "::1", "[::1]" -> false
            else -> true
        }
    }

    private companion object {
        val stopHandler = Handler(Looper.getMainLooper())
        val routeCallback = object : MediaRouter.Callback() {}
        var pendingStop: Runnable? = null

        fun scheduleRemoteStop(context: Context) {
            pendingStop?.let { stopHandler.removeCallbacks(it) }
            val stop = Runnable {
                pendingStop = null
                runCatching {
                    CastContext.getSharedInstance(context)
                        .sessionManager
                        .currentCastSession
                        ?.remoteMediaClient
                        ?.stop()
                }
            }
            pendingStop = stop
            stopHandler.postDelayed(stop, CAST_STOP_DELAY_MS)
        }
    }
}

private open class PlayerCastAwareController(
    private val base: PlayerEngineController,
    private val bridge: PlayerCastBridge,
) : PlayerEngineController by base {

    override fun play() {
        if (!bridge.play()) base.play()
    }

    override fun pause() {
        if (!bridge.pause()) base.pause()
    }

    override fun seekTo(positionMs: Long) {
        if (!bridge.seekTo(positionMs)) base.seekTo(positionMs)
    }

    override fun seekBy(offsetMs: Long) {
        if (!bridge.seekBy(offsetMs)) base.seekBy(offsetMs)
    }

    override fun retry() {
        if (!bridge.retry()) base.retry()
    }

    override fun setPlaybackSpeed(speed: Float) {
        if (!bridge.setSpeed(speed)) base.setPlaybackSpeed(speed)
    }

    override fun currentPlayerVolume(): PlayerAudioLevel =
        bridge.remoteVolume() ?: base.currentPlayerVolume()

    override fun setPlayerVolume(level: Float): PlayerAudioLevel =
        bridge.setRemoteVolume(level) ?: base.setPlayerVolume(level)

    override fun updateNowPlayingMetadata(info: PlayerNowPlayingInfo) {
        bridge.onNowPlaying(info)
        base.updateNowPlayingMetadata(info)
    }

    override fun showCastDialog() {
        bridge.showDialog()
    }
}

@Composable
internal fun rememberPlayerCastBridge(enabled: Boolean): PlayerCastBridge? {
    if (!enabled) return null
    val context = LocalContext.current
    val bridge = remember(context) { PlayerCastBridge(context) }
    DisposableEffect(bridge) {
        bridge.start()
        onDispose { bridge.release() }
    }
    return bridge
}

@Composable
internal fun PlayerCastOverlay(bridge: PlayerCastBridge, modifier: Modifier = Modifier) {
    if (!bridge.isCasting) return
    Box(
        modifier = modifier.background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.CastConnected,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(56.dp),
            )
            Text(
                text = stringResource(Res.string.player_cast_casting_to, bridge.deviceName.orEmpty()),
                color = Color.White,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private tailrec fun Context.findHostActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findHostActivity()
        else -> null
    }

private class AutoSyncCastAwareController(
    base: PlayerEngineController,
    bridge: PlayerCastBridge,
    autoSync: AutoSyncPlayerController,
) : PlayerCastAwareController(base, bridge), AutoSyncPlayerController by autoSync
