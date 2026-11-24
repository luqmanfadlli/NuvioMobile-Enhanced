package com.nuvio.app.features.player.aiostreams

import android.app.ActivityManager
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.graphics.Rect
import android.media.MediaFormat
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Choreographer
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.ui.graphics.toArgb
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.Consumer
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.video.VideoFrameMetadataListener
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.mkv.MatroskaExtractor
import androidx.media3.extractor.text.CuesWithTiming
import androidx.media3.extractor.text.DefaultSubtitleParserFactory
import androidx.media3.extractor.text.SubtitleParser
import androidx.media3.ui.PlayerView
import com.nuvio.app.features.addons.httpGetTextWithHeaders
import com.nuvio.app.features.player.SubtitleStyleState
import com.nuvio.app.features.player.replaceInnermostExtractor
import `is`.xyz.mpv.Utils
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object Extractors {
    const val ANY_TRACK = "*"
}

internal object AiostreamsAss {
    val isAvailable: Boolean by lazy {
        runCatching {
            System.loadLibrary("mpv")
            System.loadLibrary("aiostreams_ass")
            true
        }.onFailure { Log.w(TAG, "libmpv's libass is unavailable", it) }.getOrDefault(false)
    }

    const val TAG = "AiostreamsAss"
}

@OptIn(UnstableApi::class)
internal class AiostreamsAssEngine(
    context: Context,
    private val tunneling: Boolean,
) : Player.Listener, VideoFrameMetadataListener, ComponentCallbacks2 {
    private val context = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val fontsDir = File(this.context.cacheDir, "attached-fonts")
    private val ass = AssSurface(context) { fallbackFont(this.context) }
    private val noText = object : TextSink {
        override fun track(track: String, parser: SampleParser) = Unit

        override fun sample(track: String, timeUs: Long, data: ByteArray) = Unit
    }
    private val events = object : MatroskaEvents {
        override fun skipping(uri: Uri, position: Long, length: Long) = Unit

        override fun attachments(uri: Uri, position: Long, length: Long?) {
            main.post { loadFonts(uri, position, length) }
        }
    }

    @Volatile
    private var readBack: String? = Extractors.ANY_TRACK

    @Volatile
    private var delayUs = 0L

    @Volatile
    private var lastFrameAtNs = 0L

    @Volatile
    private var shown = false

    private var fontSource: DataSource.Factory? = null
    private var fonts: AttachedFonts? = null
    private var player: ExoPlayer? = null
    private var stage: PlayerView? = null
    private var embeddedKey: String? = null
    private var externalKey: String? = null
    private var layout = AssLayout()
    private var sentLayout: AssLayout? = null
    private var rawVideoRect = Rect()
    private var style = SubtitleStyleState.DEFAULT
    private var controlsVisible = false
    private var released = false

    private val preDraw = ViewTreeObserver.OnPreDrawListener {
        stage?.let(::measureStage)
        true
    }

    private val clock = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            val player = player ?: return
            if (!tunneling || !player.isPlaying) return
            if (System.nanoTime() - lastFrameAtNs > FRAMES_STOPPED_NS) {
                ass.render(player.currentPosition * 1000 - delayUs, frameTimeNanos)
            }
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    init {
        fontsDir.deleteRecursively()
        if (smallDevice(this.context)) ass.setCacheLimit(SMALL_ASS_CACHE_MB)
        this.context.registerComponentCallbacks(this)
    }

    fun reset(fontSource: DataSource.Factory) {
        this.fontSource = fontSource
        closeFonts()
        readBack = Extractors.ANY_TRACK
        shown = false
        embeddedKey = null
        externalKey = null
        ass.reset()
    }

    fun extractors(base: ExtractorsFactory): ExtractorsFactory = object : ExtractorsFactory {
        override fun createExtractors(): Array<Extractor> = wrapped(base.createExtractors(), null)

        override fun createExtractors(uri: Uri, responseHeaders: Map<String, List<String>>): Array<Extractor> =
            wrapped(base.createExtractors(uri, responseHeaders), uri)

        private fun wrapped(extractors: Array<Extractor>, uri: Uri?): Array<Extractor> {
            extractors.forEachIndexed { index, extractor ->
                if (extractor.underlyingImplementation is MatroskaExtractor) {
                    extractors[index] = extractor.replaceInnermostExtractor(
                        MatroskaReader(SubtitleSink(ass.sink(), noText), { readBack }, uri, events),
                    )
                }
            }
            return extractors
        }
    }

    fun scripts(): SubtitleParser.Factory = object : SubtitleParser.Factory {
        private val parsers = DefaultSubtitleParserFactory()

        override fun supportsFormat(format: Format) = isAss(format) || parsers.supportsFormat(format)

        override fun getCueReplacementBehavior(format: Format) =
            if (isAss(format)) Format.CUE_REPLACEMENT_BEHAVIOR_REPLACE else parsers.getCueReplacementBehavior(format)

        override fun create(format: Format): SubtitleParser {
            if (!isAss(format)) return parsers.create(format)
            val key = configKey(format)
            return object : SubtitleParser {
                override fun parse(
                    data: ByteArray,
                    offset: Int,
                    length: Int,
                    outputOptions: SubtitleParser.OutputOptions,
                    output: Consumer<CuesWithTiming>,
                ) {
                    if (length > 0) ass.addScript(key, data.copyOfRange(offset, offset + length))
                }

                override fun getCueReplacementBehavior() = Format.CUE_REPLACEMENT_BEHAVIOR_REPLACE
            }
        }
    }

    fun bind(player: ExoPlayer) {
        if (released) return
        this.player?.let(::unbind)
        this.player = player
        player.setVideoFrameMetadataListener(this)
        player.addListener(this)
        onVideoSizeChanged(player.videoSize)
        onTracksChanged(player.currentTracks)
        updateClock()
    }

    fun unbind(player: ExoPlayer) {
        if (this.player !== player) return
        Choreographer.getInstance().removeFrameCallback(clock)
        player.clearVideoFrameMetadataListener(this)
        player.removeListener(this)
        this.player = null
    }

    fun attach(view: PlayerView) {
        if (released) return
        if (stage !== view) {
            stage?.viewTreeObserver?.removeOnPreDrawListener(preDraw)
            stage = view
            view.viewTreeObserver.addOnPreDrawListener(preDraw)
        }
        val overlay = ass.view
        if (overlay.parent !== view) {
            (overlay.parent as? ViewGroup)?.removeView(overlay)
            val index = view.subtitleView?.let(view::indexOfChild)?.takeIf { it >= 0 } ?: view.childCount
            view.addView(overlay, index, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        }
        measureStage(view)
    }

    fun setStyle(style: SubtitleStyleState) {
        if (this.style == style) return
        this.style = style
        updateLayout()
    }

    fun setControlsVisible(visible: Boolean) {
        if (this.controlsVisible == visible) return
        this.controlsVisible = visible
        updateLayout()
    }

    fun setSubtitleDelayMs(delayMs: Int) {
        val next = delayMs * 1000L
        if (next == delayUs) return
        delayUs = next
        ass.invalidate()
    }

    suspend fun showExternal(url: String, headers: Map<String, String>): Boolean {
        val body = runCatching {
            withContext(Dispatchers.IO) { httpGetTextWithHeaders(url = url, headers = headers) }
        }.onFailure { Log.w(AiostreamsAss.TAG, "couldn't add the subtitle", it) }.getOrNull() ?: return false
        val text = body.trimStart('﻿', ' ', '\r', '\n')
        if (!text.startsWith("[Script Info]", ignoreCase = true)) return false
        if (released) return false
        ass.addScript(EXTERNAL_KEY, text.toByteArray(Charsets.UTF_8))
        externalKey = EXTERNAL_KEY
        readBack = null
        show()
        return true
    }

    fun clearExternal() {
        if (externalKey == null) return
        externalKey = null
        ass.removeScript(EXTERNAL_KEY)
        show()
    }

    fun release() {
        if (released) return
        player?.let(::unbind)
        stage?.viewTreeObserver?.removeOnPreDrawListener(preDraw)
        stage = null
        (ass.view.parent as? ViewGroup)?.removeView(ass.view)
        released = true
        context.unregisterComponentCallbacks(this)
        closeFonts()
        ass.release()
    }

    override fun onVideoFrameAboutToBeRendered(
        presentationTimeUs: Long,
        releaseTimeNs: Long,
        format: Format,
        mediaFormat: MediaFormat?,
    ) {
        lastFrameAtNs = System.nanoTime()
        ass.render(presentationTimeUs - delayUs, releaseTimeNs)
    }

    override fun onRenderedFirstFrame() {
        shown = true
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        updateClock()
    }

    override fun onVideoSizeChanged(videoSize: VideoSize) {
        if (videoSize.width == 0 || videoSize.height == 0) return
        layout = layout.copy(videoWidth = videoSize.width, videoHeight = videoSize.height)
        updateLayout()
    }

    override fun onTracksChanged(tracks: Tracks) {
        val format = tracks.groups.firstNotNullOfOrNull { group ->
            if (group.type != C.TRACK_TYPE_TEXT || !group.isSelected) return@firstNotNullOfOrNull null
            (0 until group.length).firstOrNull(group::isTrackSelected)?.let(group::getTrackFormat)
        }
        val id = format?.id?.substringAfter(':')
        embeddedKey = when {
            format == null || !isAss(format) -> null
            id.isNullOrEmpty() -> configKey(format)
            else -> id
        }
        if (!tracks.isEmpty) readBack = id?.takeIf { it.isNotEmpty() && externalKey == null }
        show()
    }

    override fun onTrimMemory(level: Int) {
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) ass.setCacheLimit(SHORT_ASS_CACHE_MB)
    }

    override fun onConfigurationChanged(newConfig: Configuration) = Unit

    @Deprecated("Deprecated in Java")
    override fun onLowMemory() {
        ass.setCacheLimit(SHORT_ASS_CACHE_MB)
    }

    private fun show() {
        ass.select(externalKey ?: embeddedKey)
    }

    private fun updateClock() {
        Choreographer.getInstance().removeFrameCallback(clock)
        if (tunneling && player?.isPlaying == true) Choreographer.getInstance().postFrameCallback(clock)
    }

    private fun loadFonts(uri: Uri, position: Long, length: Long?) {
        if (released || fonts != null) return
        val source = fontSource ?: return
        val dir = File(fontsDir, System.nanoTime().toString())
        fonts = AttachedFonts(source, uri, position, length, ass.sink(), dir, ::fontsMayLoad).also { it.start() }
    }

    private fun closeFonts() {
        fonts?.close()
        fonts = null
        fontsDir.deleteRecursively()
    }

    private fun fontsMayLoad(bytes: Long) = bytes <= SMALL_FONTS_BYTES || shown

    private fun measureStage(view: PlayerView) {
        val frame = view.findViewById<View>(androidx.media3.ui.R.id.exo_content_frame) ?: return
        if (view.width <= 0 || view.height <= 0 || frame.width <= 0 || frame.height <= 0) return
        val left = frame.left + frame.translationX + frame.pivotX * (1 - frame.scaleX)
        val top = frame.top + frame.translationY + frame.pivotY * (1 - frame.scaleY)
        val video = Rect(
            left.roundToInt(),
            top.roundToInt(),
            (left + frame.width * frame.scaleX).roundToInt(),
            (top + frame.height * frame.scaleY).roundToInt(),
        )
        if (layout.stageWidth == view.width && layout.stageHeight == view.height && rawVideoRect == video) return
        rawVideoRect = video
        layout = layout.copy(stageWidth = view.width, stageHeight = view.height, video = video)
        updateLayout()
    }

    private fun updateLayout() {
        val rawVideo = rawVideoRect
        val sw = layout.stageWidth
        val sh = layout.stageHeight
        val isZoomed = sw > 0 && sh > 0 &&
            (rawVideo.left < 0 || rawVideo.top < 0 || rawVideo.right > sw || rawVideo.bottom > sh)
        val effectiveVideo = if (isZoomed) {
            Rect(
                rawVideo.left.coerceIn(0, sw),
                rawVideo.top.coerceIn(0, sh),
                rawVideo.right.coerceIn(0, sw),
                rawVideo.bottom.coerceIn(0, sh),
            )
        } else {
            rawVideo
        }
        val basePos = 100.0 - (style.bottomOffset - DEFAULT_BOTTOM_OFFSET) / 10.0
        val liftedPos = if (controlsVisible) basePos - 14.0 else basePos
        val next = layout.copy(
            video = effectiveVideo,
            scale = style.fontSizeSp / DEFAULT_FONT_SIZE_SP,
            position = liftedPos.coerceIn(0.0, 150.0),
            forceMargins = isZoomed,
            style = assStyle(style),
        )
        layout = next
        if (next == sentLayout) return
        sentLayout = next
        ass.setLayout(next)
    }

    private companion object {
        const val EXTERNAL_KEY = "+external"
        const val FRAMES_STOPPED_NS = 100_000_000L
        const val SMALL_FONTS_BYTES = 8L shl 20
        const val SMALL_ASS_CACHE_MB = 32
        const val SHORT_ASS_CACHE_MB = 8
        const val SMALL_DEVICE_BYTES = 3_400_000_000L
        const val DEFAULT_FONT_SIZE_SP = 18.0
        const val DEFAULT_BOTTOM_OFFSET = 20
        const val ASS_SHADOW_ARGB = 0xB3000000.toInt()

        fun configKey(format: Format) = "cfg:" + (format.label ?: format.language ?: "")

        fun fallbackFont(context: Context): File {
            runCatching { Utils.copyAssets(context) }
            return File(context.filesDir, "subfont.ttf").takeIf { it.isFile } ?: File("/system/fonts/Roboto-Regular.ttf")
        }

        fun smallDevice(context: Context): Boolean {
            val activities = context.getSystemService(ActivityManager::class.java) ?: return false
            val memory = ActivityManager.MemoryInfo().also(activities::getMemoryInfo)
            return activities.isLowRamDevice || memory.totalMem < SMALL_DEVICE_BYTES
        }

        fun assStyle(style: SubtitleStyleState): AssStyle {
            val box = style.backgroundColor.alpha > 0f
            return AssStyle(
                override = AssStyle.SCALE,
                color = assColor(style.textColor.toArgb()),
                outlineColor = assColor(style.outlineColor.toArgb()),
                backColor = assColor(
                    when {
                        box -> style.backgroundColor.toArgb()
                        style.appearance.shadowEnabled -> ASS_SHADOW_ARGB
                        else -> style.outlineColor.toArgb()
                    },
                ),
                outlineSize = if (style.outlineEnabled) style.outlineWidth.toDouble() else 0.0,
                borderStyle = if (box) 4 else 1,
                bold = style.bold,
                shadowSize = if (style.appearance.shadowEnabled) style.appearance.shadowSize.toDouble() else 0.0,
                fontFile = customSubtitleFontPath(style.appearance.fontId),
            )
        }

        fun assColor(argb: Int) =
            (android.graphics.Color.red(argb) shl 24) or
                (android.graphics.Color.green(argb) shl 16) or
                (android.graphics.Color.blue(argb) shl 8) or
                (255 - android.graphics.Color.alpha(argb))
    }
}

/** Path of an imported subtitle font (SubtitleFontStorage keeps them in filesDir/subtitle-fonts). */
private fun customSubtitleFontPath(fontId: String): String? {
    if (!fontId.startsWith("custom-")) return null
    val context = com.nuvio.app.features.player.PlayerSettingsStorage.applicationContext() ?: return null
    return java.io.File(context.filesDir, "subtitle-fonts").resolve(fontId).takeIf { it.isFile }?.path
}
