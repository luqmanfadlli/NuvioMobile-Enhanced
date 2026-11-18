@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.nuvio.app.features.player.bluray

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.io.EOFException

internal class BlurayIsoDataSource(private val upstream: DataSource) : DataSource {
    private var isoSpec: DataSpec? = null
    private var file: BlurayIsoImage.IsoFile? = null
    private var image: BlurayIsoImage? = null
    private var passthrough = false
    private var upstreamOpen = false
    private var filePosition = 0L
    private var bytesRemaining = 0L
    private var extentRemaining = 0L
    private var openedUri: Uri? = null

    override fun addTransferListener(transferListener: TransferListener) {
        upstream.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        closeUpstream()
        if (!BlurayIsoImage.isIsoUri(dataSpec.uri)) {
            passthrough = true
            openedUri = null
            val length = upstream.open(dataSpec)
            upstreamOpen = true
            return length
        }
        passthrough = false
        openedUri = dataSpec.uri
        val image = BlurayIsoImage.forUri(dataSpec.uri)
            ?: throw DataSourceException(PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND)
        val file = image.file(dataSpec.uri.path.orEmpty())
            ?: throw DataSourceException(PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND)
        if (dataSpec.position > file.size) {
            throw DataSourceException(PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE)
        }
        this.image = image
        this.file = file
        isoSpec = dataSpec
        filePosition = dataSpec.position
        val available = file.size - dataSpec.position
        bytesRemaining = if (dataSpec.length == C.LENGTH_UNSET.toLong()) available else minOf(dataSpec.length, available)
        extentRemaining = 0L
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (passthrough) return upstream.read(buffer, offset, length)
        if (bytesRemaining <= 0L) return C.RESULT_END_OF_INPUT
        val file = file ?: return C.RESULT_END_OF_INPUT
        file.inline?.let { inline ->
            val count = minOf(length.toLong(), bytesRemaining).toInt()
            System.arraycopy(inline, filePosition.toInt(), buffer, offset, count)
            advance(count)
            return count
        }
        if (extentRemaining <= 0L) openExtentAt(file, filePosition)
        val extent = currentExtent
        val count = minOf(length.toLong(), bytesRemaining, extentRemaining).toInt()
        if (extent != null && extent.isoOffset < 0L) {
            buffer.fill(0, offset, offset + count)
            extentRemaining -= count
            advance(count)
            return count
        }
        val read = upstream.read(buffer, offset, count)
        if (read == C.RESULT_END_OF_INPUT) throw EOFException()
        extentRemaining -= read
        advance(read)
        if (extentRemaining <= 0L) closeUpstream()
        return read
    }

    private var currentExtent: BlurayIsoImage.Extent? = null

    private fun openExtentAt(file: BlurayIsoImage.IsoFile, position: Long) {
        closeUpstream()
        val extent = file.extents.lastOrNull { it.fileOffset <= position && position < it.fileOffset + it.length }
            ?: throw EOFException()
        currentExtent = extent
        val inExtent = position - extent.fileOffset
        extentRemaining = minOf(extent.length - inExtent, bytesRemaining)
        if (extent.isoOffset < 0L) return
        val image = image ?: throw EOFException()
        val spec = (isoSpec ?: throw EOFException()).buildUpon()
            .setUri(image.isoUri)
            .setPosition(extent.isoOffset + inExtent)
            .setLength(extentRemaining)
            .build()
        upstream.open(spec)
        upstreamOpen = true
    }

    private fun advance(count: Int) {
        filePosition += count
        bytesRemaining -= count
    }

    override fun getUri(): Uri? = openedUri ?: upstream.uri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    override fun close() {
        closeUpstream()
        file = null
        image = null
        isoSpec = null
        currentExtent = null
        passthrough = false
    }

    private fun closeUpstream() {
        if (upstreamOpen) {
            upstreamOpen = false
            upstream.close()
        }
    }
}

internal class BlurayIsoDataSourceFactory(private val upstreamFactory: DataSource.Factory) : DataSource.Factory {
    override fun createDataSource(): DataSource = BlurayIsoDataSource(upstreamFactory.createDataSource())
}
