package com.nuvio.app.features.player.seekpreview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import android.opengl.GLES20
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.nuvio.app.core.logging.InAppLogger
import androidx.media3.common.ColorInfo
import androidx.media3.common.Effect
import androidx.media3.common.Format
import androidx.media3.common.GlObjectsProvider
import androidx.media3.common.GlTextureInfo
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import androidx.media3.effect.MatrixTransformation
import androidx.media3.effect.PassthroughShaderProgram
import androidx.media3.effect.Presentation
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.FormatHolder
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.mediacodec.MediaCodecAdapter
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer
import androidx.media3.exoplayer.video.VideoRendererEventListener
import androidx.media3.extractor.DefaultExtractorsFactory
import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

private const val FrameTimeoutSeconds = 20L
private const val NetworkTimeoutMs = 10_000
private const val SeekToleranceUs = 2_000_000L
private const val GrabberMinBufferMs = 1_000
private const val GrabberMaxBufferMs = 2_000

internal object SeekPreviewAndroid {
    @Volatile
    internal var appContext: Context? = null
        private set

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }
}

internal actual fun openSeekPreviewFrameSource(
    url: String,
    headers: Map<String, String>,
): SeekPreviewFrameSource? {
    val scheme = Uri.parse(url).scheme?.lowercase()
    if (scheme !in setOf("http", "https", "file", "content", null)) return null
    val context = SeekPreviewAndroid.appContext ?: return null
    return ExoSeekPreviewFrameSource(context, url, headers)
}

private class ExoSeekPreviewFrameSource(
    private val context: Context,
    private val url: String,
    private val headers: Map<String, String>,
) : SeekPreviewFrameSource {
    private var grabber: ExoFrameGrabber? = null

    override fun frameAt(positionMs: Long, maxWidthPx: Int): ImageBitmap? {
        val targetMs = positionMs.coerceAtLeast(0L)
        val activeGrabber = grabber
        if (activeGrabber == null) {
            val opened = openGrabber(
                targetHeight = (maxWidthPx * 9 / 16).coerceAtLeast(16),
                startPositionMs = targetMs,
            )
            grabber = opened
            return opened.awaitFirstFrame().asImageBitmap()
        }
        return activeGrabber.frameAt(targetMs).asImageBitmap()
    }

    private fun openGrabber(targetHeight: Int, startPositionMs: Long): ExoFrameGrabber = try {
        openAndAwait(targetHeight, mimeType = null, startPositionMs = startPositionMs)
    } catch (error: PlaybackException) {
        if (error.errorCode != PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED) throw error
        InAppLogger.info("Player/SeekPreview", "container not recognised, retrying as HLS")
        openAndAwait(targetHeight, mimeType = MimeTypes.APPLICATION_M3U8, startPositionMs = startPositionMs)
    }

    private fun openAndAwait(targetHeight: Int, mimeType: String?, startPositionMs: Long): ExoFrameGrabber {
        val created = onMainThread {
            ExoFrameGrabber(context, url, headers, targetHeight, mimeType, startPositionMs)
        }
        try {
            created.awaitFirstFrame()
        } catch (error: Throwable) {
            runCatching { onMainThread { created.release() } }
            throw error
        }
        return created
    }

    override fun close() {
        val activeGrabber = grabber ?: return
        grabber = null
        runCatching { onMainThread { activeGrabber.release() } }
    }
}

@OptIn(UnstableApi::class)
private class ExoFrameGrabber(
    context: Context,
    url: String,
    headers: Map<String, String>,
    targetHeight: Int,
    mimeType: String?,
    startPositionMs: Long,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pending = AtomicReference<CompletableFuture<Bitmap>?>(null)
    private val frameNeedsRendering = AtomicBoolean(false)

    @Volatile
    private var lastFrame: Bitmap? = null
    private val firstFrame = CompletableFuture<Bitmap>()
    private val player: ExoPlayer

    init {
        val userAgent = headers.entries.firstOrNull { it.key.equals("User-Agent", ignoreCase = true) }?.value
        val httpFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(NetworkTimeoutMs)
            .setReadTimeoutMs(NetworkTimeoutMs)
            .setDefaultRequestProperties(headers.filterKeys { !it.equals("User-Agent", ignoreCase = true) })
            .apply { if (userAgent != null) setUserAgent(userAgent) }
        val mediaSourceFactory = DefaultMediaSourceFactory(
            DefaultDataSource.Factory(context, httpFactory),
            DefaultExtractorsFactory(),
        )
        val renderersFactory = RenderersFactory { _, videoListener, _, _, _ ->
            arrayOf<Renderer>(GrabberVideoRenderer(context, videoListener))
        }
        player = ExoPlayer.Builder(context, renderersFactory, mediaSourceFactory)
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(GrabberMinBufferMs, GrabberMaxBufferMs, 0, 0)
                    .build(),
            )
            .setSeekParameters(SeekParameters(SeekToleranceUs, SeekToleranceUs))
            .build()
        player.addAnalyticsListener(Listener())
        player.setVideoEffects(
            listOf(
                Presentation.createForHeight(targetHeight),
                MatrixTransformation { Matrix().apply { setScale(1f, -1f) } },
                FrameReader(),
            ),
        )
        pending.set(firstFrame)
        player.setMediaItem(MediaItem.Builder().setUri(url).setMimeType(mimeType).build(), startPositionMs)
        player.playWhenReady = false
        player.prepare()
    }

    fun awaitFirstFrame(): Bitmap = await(firstFrame)

    fun frameAt(positionMs: Long): Bitmap {
        val request = CompletableFuture<Bitmap>()
        mainHandler.post {
            val error = player.playerError
            when {
                error != null -> request.completeExceptionally(error)
                !pending.compareAndSet(null, request) ->
                    request.completeExceptionally(IllegalStateException("frame request already pending"))
                else -> {
                    frameNeedsRendering.set(false)
                    player.seekTo(positionMs)
                }
            }
        }
        return try {
            await(request)
        } finally {
            pending.compareAndSet(request, null)
        }
    }

    fun release() {
        pending.getAndSet(null)?.cancel(true)
        player.release()
    }

    private fun await(future: CompletableFuture<Bitmap>): Bitmap = try {
        future.get(FrameTimeoutSeconds, TimeUnit.SECONDS)
    } catch (error: ExecutionException) {
        throw error.cause ?: error
    }

    private fun deliver(bitmap: Bitmap) {
        lastFrame = bitmap
        pending.getAndSet(null)?.complete(bitmap)
    }

    private inner class Listener : AnalyticsListener {
        override fun onPlayerError(eventTime: AnalyticsListener.EventTime, error: PlaybackException) {
            pending.getAndSet(null)?.completeExceptionally(error)
        }

        override fun onPlaybackStateChanged(eventTime: AnalyticsListener.EventTime, state: Int) {
            if (state == Player.STATE_READY && !frameNeedsRendering.get()) {
                val repeat = lastFrame
                val request = pending.getAndSet(null) ?: return
                if (repeat != null) request.complete(repeat)
                else request.completeExceptionally(IllegalStateException("no frame rendered"))
            }
        }
    }

    private inner class FrameReader : GlEffect {
        override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
            FrameReadingShaderProgram()
    }

    private inner class FrameReadingShaderProgram : PassthroughShaderProgram() {
        private var buffer: ByteBuffer = ByteBuffer.allocateDirect(0)

        override fun queueInputFrame(
            glObjectsProvider: GlObjectsProvider,
            inputTexture: GlTextureInfo,
            presentationTimeUs: Long,
        ) {
            val width = inputTexture.width
            val height = inputTexture.height
            val size = width * height * 4
            if (buffer.capacity() != size) buffer = ByteBuffer.allocateDirect(size)
            buffer.clear()
            try {
                GlUtil.focusFramebufferUsingCurrentContext(inputTexture.fboId, width, height)
                GlUtil.checkGlError()
                GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer)
                GlUtil.checkGlError()
            } catch (error: GlUtil.GlException) {
                onError(error)
                return
            }
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.copyPixelsFromBuffer(buffer)
            deliver(bitmap)
            inputListener.onInputFrameProcessed(inputTexture)
        }
    }

    private inner class GrabberVideoRenderer(
        context: Context,
        eventListener: VideoRendererEventListener,
    ) : MediaCodecVideoRenderer(
        MediaCodecVideoRenderer.Builder(context)
            .setMediaCodecSelector(MediaCodecSelector.DEFAULT)
            .setAllowedJoiningTimeMs(0)
            .setEventHandler(Util.createHandlerForCurrentOrMainLooper())
            .setEventListener(eventListener)
            .setMaxDroppedFramesToNotify(0),
    ) {
        private var frameRenderedSinceReset = false
        private var effectsFromPlayer: List<Effect> = emptyList()
        private var rotation: Effect? = null

        override fun onStreamChanged(
            formats: Array<Format>,
            startPositionUs: Long,
            offsetUs: Long,
            mediaPeriodId: MediaSource.MediaPeriodId,
        ) {
            super.onStreamChanged(formats, startPositionUs, offsetUs, mediaPeriodId)
            frameRenderedSinceReset = false
            setRotation(null)
        }

        override fun setVideoEffects(effects: List<Effect>) {
            effectsFromPlayer = effects
            applyEffects()
        }

        override fun maybeInitializeProcessingPipeline(format: Format): Boolean {
            val sdrFormat = if (ColorInfo.isTransferHdr(format.colorInfo)) {
                format.buildUpon().setColorInfo(ColorInfo.SDR_BT709_LIMITED).build()
            } else {
                format
            }
            return super.maybeInitializeProcessingPipeline(sdrFormat)
        }

        override fun onInputFormatChanged(formatHolder: FormatHolder): DecoderReuseEvaluation? {
            val format = formatHolder.format
            if (format != null && format.rotationDegrees != 0) {
                setRotation(
                    ScaleAndRotateTransformation.Builder()
                        .setRotationDegrees((360 - format.rotationDegrees).toFloat())
                        .build(),
                )
                formatHolder.format = format.buildUpon().setRotationDegrees(0).build()
            }
            return super.onInputFormatChanged(formatHolder)
        }

        override fun isReady(): Boolean = frameRenderedSinceReset

        override fun render(positionUs: Long, elapsedRealtimeUs: Long) {
            if (!frameRenderedSinceReset) super.render(positionUs, elapsedRealtimeUs)
        }

        override fun processOutputBuffer(
            positionUs: Long,
            elapsedRealtimeUs: Long,
            codec: MediaCodecAdapter?,
            buffer: ByteBuffer?,
            bufferIndex: Int,
            bufferFlags: Int,
            sampleCount: Int,
            bufferPresentationTimeUs: Long,
            isDecodeOnlyBuffer: Boolean,
            isLastBuffer: Boolean,
            format: Format,
        ): Boolean {
            if (frameRenderedSinceReset) return false
            return super.processOutputBuffer(
                positionUs,
                elapsedRealtimeUs,
                codec,
                buffer,
                bufferIndex,
                bufferFlags,
                sampleCount,
                bufferPresentationTimeUs,
                isDecodeOnlyBuffer,
                isLastBuffer,
                format,
            )
        }

        override fun renderOutputBufferV21(
            codec: MediaCodecAdapter,
            index: Int,
            presentationTimeUs: Long,
            releaseTimeNs: Long,
        ) {
            if (frameRenderedSinceReset) return
            frameRenderedSinceReset = true
            super.renderOutputBufferV21(codec, index, presentationTimeUs, releaseTimeNs)
        }

        override fun onPositionReset(positionUs: Long, joining: Boolean) {
            frameRenderedSinceReset = false
            frameNeedsRendering.set(true)
            super.onPositionReset(positionUs, joining)
        }

        private fun setRotation(value: Effect?) {
            rotation = value
            applyEffects()
        }

        private fun applyEffects() {
            super.setVideoEffects(listOfNotNull(rotation) + effectsFromPlayer)
        }
    }
}

private fun <T> onMainThread(block: () -> T): T {
    if (Looper.myLooper() == Looper.getMainLooper()) return block()
    val result = AtomicReference<Result<T>>()
    val done = CountDownLatch(1)
    Handler(Looper.getMainLooper()).post {
        result.set(runCatching(block))
        done.countDown()
    }
    check(done.await(FrameTimeoutSeconds, TimeUnit.SECONDS)) { "main thread busy" }
    return result.get().getOrThrow()
}
