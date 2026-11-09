@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nuvio.app.features.player.seekpreview

import kotlinx.cinterop.autoreleasepool

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import org.jetbrains.skia.Image
import platform.Foundation.NSData
import platform.posix.memcpy

interface NuvioSeekPreviewGenerator {
    fun frameJpeg(positionMs: Long, maxWidth: Int): NSData?

    fun lastError(): String?

    fun cancel()

    fun close()
}

interface NuvioSeekPreviewGeneratorCreator {
    fun createGenerator(url: String, headers: Map<String, String>): NuvioSeekPreviewGenerator?
}

object NuvioSeekPreviewRegistry {
    private var creator: NuvioSeekPreviewGeneratorCreator? = null

    fun registerCreator(creator: NuvioSeekPreviewGeneratorCreator) {
        this.creator = creator
    }

    internal fun create(url: String, headers: Map<String, String>): NuvioSeekPreviewGenerator? =
        creator?.createGenerator(url, headers)
}

internal actual fun openSeekPreviewFrameSource(
    url: String,
    headers: Map<String, String>,
): SeekPreviewFrameSource? =
    NuvioSeekPreviewRegistry.create(url, headers)?.let(::BridgedSeekPreviewFrameSource)

private class BridgedSeekPreviewFrameSource(
    private val generator: NuvioSeekPreviewGenerator,
) : SeekPreviewFrameSource {
    override fun frameAt(positionMs: Long, maxWidthPx: Int): ImageBitmap? {
        val bytes = autoreleasepool {
            generator.frameJpeg(positionMs, maxWidthPx)?.toByteArray()
        }
        if (bytes == null || bytes.isEmpty()) {
            throw IllegalStateException(generator.lastError() ?: "no frame")
        }
        return Image.makeFromEncoded(bytes).toComposeImageBitmap()
    }

    override fun cancel() = generator.cancel()

    override fun close() = generator.close()
}

private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size <= 0) return ByteArray(0)
    val result = ByteArray(size)
    result.usePinned { pinned -> memcpy(pinned.addressOf(0), bytes, length) }
    return result
}

internal actual fun openSeekPreviewFeed(
    url: String,
    headers: Map<String, String>,
    onThumbnail: (positionMs: Long, bitmap: ImageBitmap?, error: String?) -> Unit,
): SeekPreviewFeed? = null
