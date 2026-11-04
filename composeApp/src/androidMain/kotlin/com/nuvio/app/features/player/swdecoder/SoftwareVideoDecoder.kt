package com.nuvio.app.features.player.swdecoder

import android.util.Log
import android.view.Surface
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.decoder.DecoderException
import androidx.media3.decoder.DecoderInputBuffer
import androidx.media3.decoder.SimpleDecoder
import androidx.media3.decoder.VideoDecoderOutputBuffer
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

internal class SoftwareVideoDecoderException(
    message: String,
    cause: Throwable? = null,
) : DecoderException(message, cause)

@UnstableApi
internal class SoftwareVideoDecoder(
    format: Format,
    threads: Int,
) : SimpleDecoder<DecoderInputBuffer, VideoDecoderOutputBuffer, SoftwareVideoDecoderException>(
    emptyBuffers<DecoderInputBuffer>(INPUT_BUFFERS),
    emptyBuffers<VideoDecoderOutputBuffer>(OUTPUT_BUFFERS),
) {
    private val codecName: String
    private var handle = 0L
    private val frameInfo = LongArray(4)

    @Volatile
    var outputMode: Int = C.VIDEO_OUTPUT_MODE_NONE

    init {
        val name = codecNameFor(format.sampleMimeType)
        handle = if (name != null && isAvailable) nativeCreate(name, extraData(format), threads) else 0L
        if (handle == 0L) {
            release()
            throw SoftwareVideoDecoderException("Failed to initialize software decoder for ${format.sampleMimeType}")
        }
        codecName = name!!
        setInitialInputBufferSize(
            if (format.maxInputSize != Format.NO_VALUE) format.maxInputSize else DEFAULT_INPUT_BUFFER_SIZE,
        )
    }

    override fun getName(): String = "nuvio-sw-$codecName"

    override fun createInputBuffer(): DecoderInputBuffer =
        DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DIRECT)

    override fun createOutputBuffer(): VideoDecoderOutputBuffer =
        VideoDecoderOutputBuffer { releaseOutputBuffer(it) }

    override fun createUnexpectedDecodeException(error: Throwable): SoftwareVideoDecoderException =
        SoftwareVideoDecoderException("Unexpected software decode error", error)

    override fun decode(
        inputBuffer: DecoderInputBuffer,
        outputBuffer: VideoDecoderOutputBuffer,
        reset: Boolean,
    ): SoftwareVideoDecoderException? {
        if (reset) nativeFlush(handle)
        val data = inputBuffer.data ?: return null
        if (nativeSendPacket(handle, data, data.limit(), inputBuffer.timeUs) == RESULT_ERROR) {
            return SoftwareVideoDecoderException("Failed to decode $codecName sample")
        }
        when (nativeReceiveFrame(handle, frameInfo)) {
            RESULT_ERROR -> return SoftwareVideoDecoderException("Failed to receive $codecName frame")
            RESULT_SKIP -> {
                outputBuffer.shouldBeSkipped = true
                return null
            }
        }
        val timeUs = frameInfo[0]
        if (!isAtLeastOutputStartTimeUs(timeUs)) {
            outputBuffer.shouldBeSkipped = true
            return null
        }
        outputBuffer.shouldBeSkipped = false
        outputBuffer.init(timeUs, outputMode, null)
        val width = frameInfo[1].toInt()
        val height = frameInfo[2].toInt()
        val yStride = align(width)
        val uvStride = align((width + 1) / 2)
        if (!outputBuffer.initForYuvFrame(width, height, yStride, uvStride, frameInfo[3].toInt())) {
            return SoftwareVideoDecoderException("Invalid $codecName frame ${width}x$height")
        }
        val frameData = outputBuffer.data
            ?: return SoftwareVideoDecoderException("Missing $codecName frame buffer")
        if (!nativeConvertFrame(handle, frameData, yStride, uvStride)) {
            return SoftwareVideoDecoderException("Failed to convert $codecName frame")
        }
        outputBuffer.format = inputBuffer.format
        return null
    }

    fun renderToSurface(outputBuffer: VideoDecoderOutputBuffer, surface: Surface) {
        val data = outputBuffer.data
        if (outputBuffer.mode != C.VIDEO_OUTPUT_MODE_SURFACE_YUV || data == null) {
            throw SoftwareVideoDecoderException("Invalid output buffer")
        }
        val result = nativeRender(
            handle,
            surface,
            data,
            outputBuffer.width,
            outputBuffer.height,
            outputBuffer.yStride,
            outputBuffer.uvStride,
        )
        if (result == RESULT_ERROR) throw SoftwareVideoDecoderException("Failed to render $codecName frame")
    }

    override fun release() {
        super.release()
        if (handle != 0L) {
            nativeRelease(handle)
            handle = 0L
        }
    }

    companion object {
        private const val TAG = "SoftwareVideoDecoder"
        private const val INPUT_BUFFERS = 6
        private const val OUTPUT_BUFFERS = 6
        private const val DEFAULT_INPUT_BUFFER_SIZE = 768 * 1024
        private const val STRIDE_ALIGNMENT = 64
        private const val RESULT_SKIP = 1
        private const val RESULT_ERROR = -1

        private val HIGH_BIT_DEPTH_AVC_PROFILES = setOf(44, 110, 122, 244)
        private const val HEVC_PROFILE_RANGE_EXTENSIONS = 4

        val isAvailable: Boolean by lazy {
            runCatching {
                System.loadLibrary("avutil")
                System.loadLibrary("swscale")
                System.loadLibrary("avcodec")
                System.loadLibrary("nuvio_swvideo")
                nativeLoad()
            }.onFailure { Log.w(TAG, "Software video decoding is unavailable", it) }.getOrDefault(false)
        }

        fun codecNameFor(mimeType: String?): String? =
            when (mimeType) {
                MimeTypes.VIDEO_H264 -> "h264"
                MimeTypes.VIDEO_H265 -> "hevc"
                MimeTypes.VIDEO_VC1 -> "vc1"
                else -> null
            }

        fun needsSoftwareDecoding(format: Format): Boolean =
            when (format.sampleMimeType) {
                MimeTypes.VIDEO_H264 -> avcProfile(format) in HIGH_BIT_DEPTH_AVC_PROFILES
                MimeTypes.VIDEO_H265 -> hevcProfile(format) == HEVC_PROFILE_RANGE_EXTENSIONS
                MimeTypes.VIDEO_VC1 -> true
                else -> false
            }

        private fun avcProfile(format: Format): Int? {
            format.codecs
                ?.split(',')
                ?.map(String::trim)
                ?.firstOrNull { it.startsWith("avc1.") || it.startsWith("avc3.") }
                ?.substringAfter('.')
                ?.take(2)
                ?.toIntOrNull(16)
                ?.let { return it }
            val sps = format.initializationData.firstOrNull() ?: return null
            var index = 0
            while (index < sps.size && sps[index].toInt() == 0) index++
            if (index >= sps.size || sps[index].toInt() != 1) index = -1
            val header = index + 1
            if (header + 1 >= sps.size || (sps[header].toInt() and 0x1F) != 7) return null
            return sps[header + 1].toInt() and 0xFF
        }

        private fun hevcProfile(format: Format): Int? =
            format.codecs
                ?.split(',')
                ?.map(String::trim)
                ?.firstOrNull { it.startsWith("hvc1.") || it.startsWith("hev1.") }
                ?.split('.')
                ?.getOrNull(1)
                ?.trimStart('A', 'B', 'C')
                ?.toIntOrNull()

        private fun extraData(format: Format): ByteArray? {
            if (format.initializationData.isEmpty()) return null
            val output = ByteArrayOutputStream()
            format.initializationData.forEach { output.write(it) }
            return output.toByteArray()
        }

        @Suppress("UNCHECKED_CAST")
        private inline fun <reified T> emptyBuffers(size: Int): Array<T> = arrayOfNulls<T>(size) as Array<T>

        private fun align(value: Int): Int = (value + STRIDE_ALIGNMENT - 1) and (STRIDE_ALIGNMENT - 1).inv()

        @JvmStatic
        private external fun nativeLoad(): Boolean

        @JvmStatic
        private external fun nativeCreate(codecName: String, extraData: ByteArray?, threads: Int): Long

        @JvmStatic
        private external fun nativeFlush(handle: Long)

        @JvmStatic
        private external fun nativeRelease(handle: Long)

        @JvmStatic
        private external fun nativeSendPacket(handle: Long, data: ByteBuffer, size: Int, timeUs: Long): Int

        @JvmStatic
        private external fun nativeReceiveFrame(handle: Long, info: LongArray): Int

        @JvmStatic
        private external fun nativeConvertFrame(handle: Long, output: ByteBuffer, yStride: Int, uvStride: Int): Boolean

        @JvmStatic
        private external fun nativeRender(
            handle: Long,
            surface: Surface,
            source: ByteBuffer,
            width: Int,
            height: Int,
            yStride: Int,
            uvStride: Int,
        ): Int
    }
}
