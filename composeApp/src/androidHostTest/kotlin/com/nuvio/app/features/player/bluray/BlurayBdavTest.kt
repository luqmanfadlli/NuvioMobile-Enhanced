@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.nuvio.app.features.player.bluray

import android.app.Application
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BlurayBdavTest {

    private fun bdav(packets: Int, first: Int = 0): ByteArray = ByteArray(packets * 192).also { out ->
        for (p in 0 until packets) {
            val base = p * 192
            out[base] = 0x12
            out[base + 4] = 0x47
            for (i in 1 until 188) out[base + 4 + i] = ((first + p * 188 + i) and 0xFF).toByte()
        }
    }

    private fun ts(packets: Int): ByteArray = ByteArray(packets * 188).also { out ->
        for (p in 0 until packets) {
            out[p * 188] = 0x47
            for (i in 1 until 188) out[p * 188 + i] = ((p * 188 + i) and 0xFF).toByte()
        }
    }

    @Test fun `bdav packets are recognised and plain transport streams are not`() {
        assertTrue(BlurayDiscResolver.isBdavPacketPattern(bdav(3)))
        assertFalse(BlurayDiscResolver.isBdavPacketPattern(ts(4)))
        assertFalse(BlurayDiscResolver.isBdavPacketPattern(bdav(1)))
        assertFalse(BlurayDiscResolver.isBdavPacketPattern("<html><a href=\"BDMV/\">".toByteArray()))
    }

    @Test fun `only blu-ray and avchd extensions take the bdav path`() {
        assertTrue(BlurayDiscResolver.isLikelyBdavM2tsUrl("http://127.0.0.1:1/stream/a/00065.m2ts"))
        assertTrue(BlurayDiscResolver.isLikelyBdavM2tsUrl("https://host/clips/00001.MTS?token=1"))
        assertFalse(BlurayDiscResolver.isLikelyBdavM2tsUrl("https://host/hdv/tape.m2t"))
        assertFalse(BlurayDiscResolver.isLikelyBdavM2tsUrl("https://host/movie.mkv?f=a.m2ts"))
        assertFalse(BlurayDiscResolver.isLikelyBdavM2tsUrl("https://host/live/channel.ts"))
    }

    @Test fun `live and ordinary streams are never disc candidates`() {
        assertFalse(BlurayDiscResolver.isCandidate("https://host/live/channel.ts"))
        assertFalse(BlurayDiscResolver.isCandidate("https://host/live/index.m3u8"))
        assertFalse(BlurayDiscResolver.isCandidate("https://host/movie.mkv"))
        assertTrue(BlurayDiscResolver.isCandidate("https://host/d/abc/Movie.2019.BluRay.iso"))
        assertTrue(BlurayDiscResolver.isCandidate("https://host/disc/BDMV/STREAM/00001.m2ts"))
    }

    @Test fun `mpls play items give clip ids and duration`() {
        fun item(clip: String, inTime: Long, outTime: Long): ByteArray {
            val body = ByteArray(20)
            clip.toByteArray().copyInto(body, 0)
            "M2TS".toByteArray().copyInto(body, 5)
            for (i in 0..3) {
                body[12 + i] = (inTime shr (24 - 8 * i)).toByte()
                body[16 + i] = (outTime shr (24 - 8 * i)).toByte()
            }
            return byteArrayOf(0, 20) + body
        }
        val items = item("00800", 0, 90_000) + item("00801", 90_000, 270_000)
        val playlistStart = 16
        val data = ByteArray(playlistStart + 10 + items.size)
        "MPLS0200".toByteArray().copyInto(data, 0)
        data[11] = playlistStart.toByte()
        data[playlistStart + 4 + 3] = 2
        items.copyInto(data, playlistStart + 10)

        val parsed = BlurayDiscResolver.parseMpls("00001.mpls", data)!!
        assertEquals(listOf("00800", "00801"), parsed.clipIds)
        assertEquals(270_000L, parsed.duration90kHz)
        assertNull(BlurayDiscResolver.parseMpls("x.mpls", ByteArray(40)))
    }

    @Test fun `directory listings of a disc folder are recognised`() {
        val listing = """<html><a href="BDMV/">BDMV/</a><a href="CERTIFICATE/">CERTIFICATE/</a></html>"""
        assertTrue(BlurayDiscResolver.looksLikeBlurayDirectoryHtml(listing))
        assertFalse(BlurayDiscResolver.looksLikeBlurayDirectoryHtml("<html><a href=\"movie.mkv\">"))
        assertEquals(
            listOf("00001.mpls", "00800.MPLS"),
            BlurayDiscResolver.extractPlaylistNames("""<a href="00001.mpls">00001.mpls</a> 00800.MPLS"""),
        )
    }

    @Test fun `bdav data source yields 188-byte packets from any offset`() {
        val source = bdav(6)
        val expected = ByteArray(6 * 188).also { out ->
            for (p in 0 until 6) source.copyInto(out, p * 188, p * 192 + 4, p * 192 + 192)
        }
        val upstream = ByteArrayUpstream(source)
        val bdavSource = BdavM2tsDataSource(upstream) { true }

        assertEquals(expected.size.toLong(), bdavSource.open(spec(0)))
        assertArrayEquals(expected, readAll(bdavSource))
        bdavSource.close()

        val from = 2 * 188 + 100
        bdavSource.open(spec(from.toLong()))
        assertEquals(2 * 192 + 4 + 100L, upstream.openedAt)
        assertArrayEquals(expected.copyOfRange(from, expected.size), readAll(bdavSource))
        bdavSource.close()
    }

    @Test fun `bdav data source passes other files through untouched`() {
        val subtitle = "1\n00:00:01,000 --> 00:00:02,000\nHello\n".toByteArray()
        val bdavSource = BdavM2tsDataSource(ByteArrayUpstream(subtitle)) { false }
        assertEquals(subtitle.size.toLong(), bdavSource.open(spec(0)))
        assertArrayEquals(subtitle, readAll(bdavSource))
        bdavSource.close()
    }

    private val uri: Uri = Uri.parse("https://host/disc/BDMV/STREAM/00001.m2ts")

    private fun spec(position: Long) = DataSpec.Builder().setUri(uri).setPosition(position).build()

    private fun readAll(source: DataSource): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(1000)
        while (true) {
            val read = source.read(buffer, 0, buffer.size)
            if (read == C.RESULT_END_OF_INPUT) return out.toByteArray()
            out.write(buffer, 0, read)
        }
    }

    private class ByteArrayUpstream(private val data: ByteArray) : DataSource {
        var openedAt = -1L
        private var position = 0
        override fun addTransferListener(transferListener: TransferListener) = Unit
        override fun open(dataSpec: DataSpec): Long {
            openedAt = dataSpec.position
            position = dataSpec.position.toInt()
            return (data.size - position).toLong()
        }
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (position >= data.size) return C.RESULT_END_OF_INPUT
            val count = minOf(length, data.size - position)
            data.copyInto(buffer, offset, position, position + count)
            position += count
            return count
        }
        override fun getUri(): Uri? = null
        override fun close() = Unit
    }
}
