@file:androidx.annotation.OptIn(UnstableApi::class)

package com.nuvio.app.features.player

import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.TrackGroupArray
import androidx.media3.exoplayer.trackselection.ExoTrackSelection
import androidx.media3.exoplayer.upstream.Allocator
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory

/*
 * Live TV playback, as Nuvio TV plays its channels (reshaped/livetv in NuvioTV): a live channel is
 * an endless stream from an IPTV server, not a file, so it skips the file buffering extras and gets
 * the start, retry and live edge handling IPTV players use.
 */

/**
 * MPEG-TS reader flags added for a live channel. Many broadcast restreams send H.264 with IDR
 * frames only every several seconds, or none at all (just recovery point I-frames). Media3 drops
 * every video sample until it sees an IDR frame, so the channel sits in buffering while data keeps
 * arriving; with this flag it starts on the first I-frame, as IPTV players and Media3's own HLS
 * reader do. Files keep the normal flags.
 */
internal fun liveTsExtractorFlags(liveStream: Boolean): Int =
    if (liveStream) DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES else 0

/**
 * Load retries for a live channel. Media3 fails a stream at once on HTTP 400/401/403/404/410,
 * which suits files. An IPTV server often refuses a channel only for a moment: the channel just
 * left still counts against a one-connection account for a second or two, or a load balancer is
 * busy. IPTV players simply try again, so a channel gets a few quick retries (0.7 s, 1.4 s, 2.1 s)
 * before its error shows. Other errors keep Media3's live defaults.
 */
internal object LiveLoadErrorPolicy : DefaultLoadErrorHandlingPolicy(
    DefaultLoadErrorHandlingPolicy.DEFAULT_MIN_LOADABLE_RETRY_COUNT_PROGRESSIVE_LIVE,
) {
    private const val HTTP_RETRIES = 3
    private const val HTTP_RETRY_STEP_MS = 700L

    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
        if (loadErrorInfo.exception.httpResponseCode() != null) {
            return if (loadErrorInfo.errorCount <= HTTP_RETRIES) {
                HTTP_RETRY_STEP_MS * loadErrorInfo.errorCount
            } else {
                C.TIME_UNSET
            }
        }
        return super.getRetryDelayMsFor(loadErrorInfo)
    }

    private fun Throwable.httpResponseCode(): Int? =
        generateSequence(this) { it.cause }
            .filterIsInstance<HttpDataSource.InvalidResponseCodeException>()
            .firstOrNull()
            ?.responseCode
}

/** The media source for a live channel: the playback data source, with [LiveLoadErrorPolicy]. */
internal fun createLiveMediaSource(
    dataSourceFactory: DataSource.Factory,
    extractorsFactory: ExtractorsFactory,
    mediaItem: MediaItem,
): MediaSource =
    DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory)
        .setLoadErrorHandlingPolicy(LiveLoadErrorPolicy)
        .createMediaSource(mediaItem)

/**
 * The load control for a live channel. The playback buffer settings are made for files: playback
 * waits for several seconds of video before it starts (and again after a stall), which a channel
 * arriving in real time turns into the same wait on every open and every zap, or a stall that
 * never ends on a connection only just fast enough for the channel.
 *
 * This keeps [delegate] (its memory budget, allocator, back buffer and how far it reads ahead) and
 * only lets playback start once [START_MS] of media is buffered, or [RESTART_MS] after a stall.
 * It never waits longer than [delegate] would.
 */
internal class LiveStartLoadControl(private val delegate: LoadControl) : LoadControl {

    override fun shouldStartPlayback(parameters: LoadControl.Parameters): Boolean {
        if (delegate.shouldStartPlayback(parameters)) return true
        val bufferedUs = Util.getPlayoutDurationForMediaDuration(parameters.bufferedDurationUs, parameters.playbackSpeed)
        val neededUs = (if (parameters.rebuffering) RESTART_MS else START_MS) * 1_000L
        return bufferedUs >= neededUs
    }

    override fun onPrepared(playerId: PlayerId) = delegate.onPrepared(playerId)

    override fun onTracksSelected(
        parameters: LoadControl.Parameters,
        trackGroups: TrackGroupArray,
        trackSelections: Array<out ExoTrackSelection?>,
    ) = delegate.onTracksSelected(parameters, trackGroups, trackSelections)

    override fun onStopped(playerId: PlayerId) = delegate.onStopped(playerId)

    override fun onReleased(playerId: PlayerId) = delegate.onReleased(playerId)

    override fun getAllocator(): Allocator = delegate.allocator

    override fun getBackBufferDurationUs(playerId: PlayerId): Long = delegate.getBackBufferDurationUs(playerId)

    override fun retainBackBufferFromKeyframe(playerId: PlayerId): Boolean =
        delegate.retainBackBufferFromKeyframe(playerId)

    override fun shouldContinueLoading(parameters: LoadControl.Parameters): Boolean =
        delegate.shouldContinueLoading(parameters)

    override fun shouldContinuePreloading(
        timeline: Timeline,
        mediaPeriodId: MediaSource.MediaPeriodId,
        bufferedDurationUs: Long,
    ): Boolean = delegate.shouldContinuePreloading(timeline, mediaPeriodId, bufferedDurationUs)

    private companion object {
        /** Media buffered before a channel starts. */
        const val START_MS = 1_000L

        /** Media buffered before a channel resumes after a stall, so it does not stall again at once. */
        const val RESTART_MS = 2_000L
    }
}

/**
 * A paused or stalled live stream falls out of its playlist window (BEHIND_LIVE_WINDOW); the
 * player then only needs to jump back to the live edge. A few rejoins per minute at most, so a
 * stream that keeps failing still reaches the normal error handling.
 */
internal class LiveEdgeRejoin {
    private var windowStartMs = 0L
    private var rejoins = 0

    /** True when [error] was handled by rejoining the live edge of [player]. */
    fun tryRejoin(error: PlaybackException, player: Player): Boolean {
        if (error.errorCode != PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) return false
        val now = SystemClock.elapsedRealtime()
        if (now - windowStartMs > WINDOW_MS) {
            windowStartMs = now
            rejoins = 0
        }
        if (rejoins >= MAX_REJOINS) return false
        rejoins++
        player.seekToDefaultPosition()
        player.prepare()
        return true
    }

    private companion object {
        const val MAX_REJOINS = 3
        const val WINDOW_MS = 60_000L
    }
}
