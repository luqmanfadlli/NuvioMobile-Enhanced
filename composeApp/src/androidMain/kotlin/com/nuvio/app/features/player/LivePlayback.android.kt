@file:androidx.annotation.OptIn(UnstableApi::class)

package com.nuvio.app.features.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Tracks
import androidx.media3.common.util.TimestampAdjuster
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.hls.DefaultHlsExtractorFactory
import androidx.media3.exoplayer.hls.HlsExtractorFactory
import androidx.media3.exoplayer.hls.HlsMediaChunkExtractor
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.text.SubtitleParser
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import com.nuvio.app.core.logging.InAppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal val LIVE_TS_PAYLOAD_READER_FLAGS: Int =
    DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or
        DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS

private class LiveHlsExtractorFactory : HlsExtractorFactory {
    private val delegate = DefaultHlsExtractorFactory(LIVE_TS_PAYLOAD_READER_FLAGS, true)

    override fun createExtractor(
        uri: Uri,
        format: Format,
        muxedCaptionFormats: List<Format>?,
        timestampAdjuster: TimestampAdjuster,
        responseHeaders: Map<String, List<String>>,
        sniffingExtractorInput: ExtractorInput,
        playerId: PlayerId,
    ): HlsMediaChunkExtractor = delegate.createExtractor(
        uri,
        format.buildUpon().setCodecs(null).build(),
        muxedCaptionFormats,
        timestampAdjuster,
        responseHeaders,
        sniffingExtractorInput,
        playerId,
    )

    override fun setSubtitleParserFactory(subtitleParserFactory: SubtitleParser.Factory): HlsExtractorFactory {
        delegate.setSubtitleParserFactory(subtitleParserFactory)
        return this
    }

    override fun experimentalParseSubtitlesDuringExtraction(parseSubtitlesDuringExtraction: Boolean): HlsExtractorFactory {
        delegate.experimentalParseSubtitlesDuringExtraction(parseSubtitlesDuringExtraction)
        return this
    }

    override fun experimentalSetCodecsToParseWithinGopSampleDependencies(
        codecsToParseWithinGopSampleDependencies: Int,
    ): HlsExtractorFactory {
        delegate.experimentalSetCodecsToParseWithinGopSampleDependencies(codecsToParseWithinGopSampleDependencies)
        return this
    }

    override fun getOutputTextFormat(sourceFormat: Format): Format = delegate.getOutputTextFormat(sourceFormat)
}

internal fun createLiveTvMediaSource(
    dataSourceFactory: DataSource.Factory,
    extractorsFactory: ExtractorsFactory,
    mediaItem: MediaItem,
): MediaSource {
    val localConfiguration = mediaItem.localConfiguration
    val contentType = localConfiguration?.let {
        Util.inferContentTypeForUriAndMimeType(it.uri, it.mimeType)
    }
    return if (contentType == C.CONTENT_TYPE_HLS) {
        HlsMediaSource.Factory(dataSourceFactory)
            .setExtractorFactory(LiveHlsExtractorFactory())
            .setAllowChunklessPreparation(false)
            .createMediaSource(mediaItem)
    } else {
        DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory).createMediaSource(mediaItem)
    }
}

internal class LivePlaybackRecovery(
    private val player: ExoPlayer,
    private val scope: CoroutineScope,
    private val onFallback: (() -> Unit)?,
) {
    private var reconnectAttempts = 0
    private var firstFrameRendered = false
    private var fallbackRequested = false
    private var reconnectJob: Job? = null
    private var blackScreenJob: Job? = null

    val canFallback: Boolean
        get() = onFallback != null && !fallbackRequested

    fun onError(error: PlaybackException): Boolean {
        if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
            InAppLogger.warn("ExoPlayer/Live", "behind live window; jumping to live edge")
            player.seekToDefaultPosition()
            player.prepare()
            return true
        }
        if (!error.isTransientLiveFailure()) return false
        return scheduleReconnect("error ${error.errorCodeName}")
    }

    fun onEnded(): Boolean = scheduleReconnect("stream ended")

    fun onFirstFrameRendered() {
        firstFrameRendered = true
        reconnectAttempts = 0
        blackScreenJob?.cancel()
    }

    fun onPlayingChanged(isPlaying: Boolean) {
        if (!isPlaying || firstFrameRendered || !canFallback) {
            if (!isPlaying) blackScreenJob?.cancel()
            return
        }
        blackScreenJob?.cancel()
        blackScreenJob = scope.launch {
            delay(BLACK_SCREEN_TIMEOUT_MS)
            if (!firstFrameRendered && player.isPlaying && player.currentTracks.hasSelectedVideo()) {
                fallback("no video frame rendered")
            }
        }
    }

    fun onTracksChanged(tracks: Tracks) {
        if (!canFallback) return
        val videoGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }
        if (videoGroups.isEmpty() || videoGroups.any { it.isSelected }) return
        val playableVideo = videoGroups.any { group ->
            (0 until group.length).any { index -> group.isTrackSupported(index, true) }
        }
        val encrypted = videoGroups.any { group ->
            (0 until group.length).any { index -> group.getTrackFormat(index).drmInitData != null }
        }
        if (!playableVideo && !encrypted) fallback("video track not supported")
    }

    fun fallback(reason: String): Boolean {
        val callback = onFallback ?: return false
        if (fallbackRequested) return true
        fallbackRequested = true
        reconnectJob?.cancel()
        blackScreenJob?.cancel()
        InAppLogger.warn("ExoPlayer/Live", "switching live stream to libmpv reason=$reason")
        callback()
        return true
    }

    fun release() {
        reconnectJob?.cancel()
        blackScreenJob?.cancel()
    }

    private fun scheduleReconnect(reason: String): Boolean {
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) return false
        reconnectAttempts += 1
        val attempt = reconnectAttempts
        InAppLogger.warn("ExoPlayer/Live", "reconnecting attempt=$attempt reason=$reason")
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(RECONNECT_BASE_DELAY_MS * attempt)
            player.seekToDefaultPosition()
            player.prepare()
            player.playWhenReady = true
        }
        return true
    }

    private fun Tracks.hasSelectedVideo(): Boolean =
        groups.any { it.type == C.TRACK_TYPE_VIDEO && it.isSelected }

    private fun PlaybackException.isTransientLiveFailure(): Boolean = when (errorCode) {
        PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE,
        PlaybackException.ERROR_CODE_TIMEOUT,
        -> firstFrameRendered || reconnectAttempts == 0
        else -> false
    }

    private companion object {
        const val MAX_RECONNECT_ATTEMPTS = 3
        const val RECONNECT_BASE_DELAY_MS = 1_000L
        const val BLACK_SCREEN_TIMEOUT_MS = 10_000L
    }
}
