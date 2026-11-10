@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.nuvio.app.features.player.bluray

import android.app.Application
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BlurayIsoImageTest {
    private val sector = 2048
    private val partitionStart = 300
    private val hour = 45_000L * 3600

    private val clip1 = bdav(27, first = 1)
    private val clip2 = bdav(24, first = 7).copyOf(4596)

    private val image: ByteArray by lazy { buildImage() }
    private val factory by lazy { DataSource.Factory { ByteArrayDataSource(image) } }
    private val isoUrl = "https://debrid.example/d/abc123/Movie.2019.1080p.BluRay.iso"

    @Test fun `a udf image is recognised by its volume recognition sequence`() {
        assertTrue(BlurayIsoImage.looksLikeUdfImage(image.copyOf(256 * 1024)))
        assertFalse(BlurayIsoImage.looksLikeUdfImage(ByteArray(256 * 1024)))
        assertTrue(BlurayIsoImage.isLikelyIsoUrl(isoUrl))
        assertTrue(BlurayIsoImage.isLikelyIsoUrl("file:///storage/emulated/0/Movies/DISC.ISO"))
        assertFalse(BlurayIsoImage.isLikelyIsoUrl("https://host/movie.mkv?name=a.iso"))
    }

    @Test fun `an iso plays its feature playlist and offers its other titles`() {
        val disc = BlurayDiscResolver.resolveBeforePlayback(isoUrl, factory)
        assertNotNull(disc)
        disc!!
        assertEquals(listOf("00000.mpls", "00001.mpls"), disc.titles.map { it.name })
        assertEquals(0, disc.featureIndex)
        assertEquals(listOf(3_600_000L, 1_800_000L), disc.feature.durationsMs)
        val clipUris = disc.feature.clipUris.map(Uri::parse)
        assertTrue(clipUris.all { it.scheme == BlurayIsoImage.SCHEME })
        assertEquals(listOf("/BDMV/STREAM/00001.m2ts", "/BDMV/STREAM/00002.m2ts"), clipUris.map { it.path })
        assertTrue(disc.isBdavUri(clipUris.first()))
    }

    @Test fun `an unnamed iso is found by the probe after a failure`() {
        val disc = BlurayDiscResolver.probeAfterFailure("https://cdn.example/dl/7f3a9?token=x", factory)
        assertNotNull(disc)
        assertEquals("00000.mpls", disc!!.feature.name)
    }

    @Test fun `clips are read from their extents in the image`() {
        val disc = BlurayDiscResolver.resolveBeforePlayback(isoUrl, factory)!!
        val first = Uri.parse(disc.feature.clipUris[0])
        val second = Uri.parse(disc.feature.clipUris[1])
        val source = BlurayIsoDataSource(ByteArrayDataSource(image))

        assertEquals(clip1.size.toLong(), source.open(DataSpec(first)))
        assertArrayEquals(clip1, readAll(source))
        source.close()

        assertEquals(clip1.size - 4000L, source.open(DataSpec.Builder().setUri(first).setPosition(4000).build()))
        assertArrayEquals(clip1.copyOfRange(4000, clip1.size), readAll(source))
        source.close()

        source.open(DataSpec.Builder().setUri(first).setPosition(4090).setLength(20).build())
        assertArrayEquals(clip1.copyOfRange(4090, 4110), readAll(source))
        source.close()

        assertEquals(clip2.size.toLong(), source.open(DataSpec(second)))
        assertArrayEquals(clip2, readAll(source))
        source.close()
    }

    @Test fun `iso clips are stripped to transport stream packets`() {
        val disc = BlurayDiscResolver.resolveBeforePlayback(isoUrl, factory)!!
        val clip = Uri.parse(disc.feature.clipUris[0])
        val source = BdavM2tsDataSource(BlurayIsoDataSource(ByteArrayDataSource(image)), disc::isBdavUri)
        source.open(DataSpec(clip))
        val ts = readAll(source)
        source.close()
        assertEquals(27 * 188, ts.size)
        for (p in 0 until 27) assertEquals(0x47.toByte(), ts[p * 188])
    }

    @Test fun `a title's languages come from its clip info inside the image`() {
        val disc = BlurayDiscResolver.resolveBeforePlayback(isoUrl, factory)!!
        assertEquals(listOf("eng", "por") to listOf("por", "eng"), BlurayDiscResolver.titleLanguages(disc.feature, factory))
        assertEquals(emptyList<String>() to emptyList<String>(), BlurayDiscResolver.titleLanguages(disc.titles[1], factory))
    }

    @Test fun `an image without a disc structure is not a disc`() {
        val empty = ByteArray(image.size)
        assertNull(BlurayDiscResolver.probeAfterFailure("https://cdn.example/other", DataSource.Factory { ByteArrayDataSource(empty) }))
    }

    private fun readAll(source: DataSource): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(1000)
        while (true) {
            val read = source.read(buffer, 0, buffer.size)
            if (read == C.RESULT_END_OF_INPUT) return out.toByteArray()
            out.write(buffer, 0, read)
        }
    }

    private fun bdav(packets: Int, first: Int): ByteArray = ByteArray(packets * 192).also { out ->
        for (p in 0 until packets) {
            val base = p * 192
            out[base] = 0x12
            out[base + 4] = 0x47
            for (i in 1 until 188) out[base + 4 + i] = ((first + p * 188 + i) and 0xFF).toByte()
        }
    }

    private fun mpls(vararg items: Pair<String, Long>): ByteArray {
        var bytes = byteArrayOf()
        var at = 0L
        for ((clip, ticks) in items) {
            val body = ByteArray(20)
            (clip + "M2TS").toByteArray().copyInto(body, 0)
            for (i in 0..3) {
                body[12 + i] = (at shr (24 - 8 * i)).toByte()
                body[16 + i] = ((at + ticks) shr (24 - 8 * i)).toByte()
            }
            at += ticks
            bytes += byteArrayOf(0, 20) + body
        }
        val playlistStart = 16
        val data = ByteArray(playlistStart + 10 + bytes.size)
        "MPLS0200".toByteArray().copyInto(data, 0)
        data[11] = playlistStart.toByte()
        data[playlistStart + 4 + 2] = (items.size shr 8).toByte()
        data[playlistStart + 4 + 3] = items.size.toByte()
        bytes.copyInto(data, playlistStart + 10)
        return data
    }

    private val clipInfo: ByteArray by lazy {
        val streams = listOf(0x1011 to "", 0x1100 to "eng", 0x1101 to "por", 0x1200 to "por", 0x1800 to "eng")
            .map { (pid, language) ->
                val info = when {
                    language.isEmpty() -> byteArrayOf(0x24, 0x61, 0, 0, 0)
                    pid in 0x1100..0x111F -> byteArrayOf(0x81.toByte(), 0x61) + language.toByteArray() + 0
                    else -> byteArrayOf(0x90.toByte()) + language.toByteArray() + 0
                }
                byteArrayOf((pid shr 8).toByte(), pid.toByte(), info.size.toByte()) + info
            }
        "HDMV0300".toByteArray() + byteArrayOf(0, 0, 0, 0, 0, 0, 0, 40) + ByteArray(24) +
            byteArrayOf(0, 0, 0, 0, 0, 1) + byteArrayOf(0, 0, 0, 0, 0x01, 0x00, streams.size.toByte(), 0) +
            streams.reduce { a, b -> a + b }
    }

    private fun buildImage(): ByteArray {
        val img = ByteArray((partitionStart + 60) * sector)
        fun at(sectorIndex: Int) = sectorIndex * sector
        fun physical(block: Int) = at(partitionStart + block)
        fun meta(block: Int) = physical(1 + block)

        listOf("BEA01", "NSR03", "TEA01").forEachIndexed { i, id ->
            val o = at(16 + i)
            img[o + 6] = 1
            id.toByteArray().copyInto(img, o + 1)
        }

        tag(img, at(256), 2)
        u32(img, at(256) + 16, 16L * sector)
        u32(img, at(256) + 20, 32)

        tag(img, at(32), 5)
        u16(img, at(32) + 22, 0)
        u32(img, at(32) + 188, partitionStart.toLong())
        u32(img, at(32) + 192, 60)

        val lvd = at(33)
        tag(img, lvd, 6)
        u32(img, lvd + 212, sector.toLong())
        longAd(img, lvd + 248, sector.toLong(), 0, 1)
        u32(img, lvd + 264, 6 + 64)
        u32(img, lvd + 268, 2)
        img[lvd + 440] = 1
        img[lvd + 441] = 6
        u16(img, lvd + 444, 0)
        val map2 = lvd + 446
        img[map2] = 2
        img[map2 + 1] = 64
        "*UDF Metadata Partition".toByteArray().copyInto(img, map2 + 5)
        u16(img, map2 + 38, 0)
        u32(img, map2 + 40, 0)
        u32(img, map2 + 44, 0)

        tag(img, at(34), 8)

        fileEntry(img, physical(0), fileType = 250, size = 16L * sector, ads = shortAds(16L * sector to 1L))

        tag(img, meta(0), 256)
        longAd(img, meta(0) + 400, sector.toLong(), 1, 1)

        val rootDir = fids(parent = true, "BDMV" to (3 to true))
        fileEntry(img, meta(1), fileType = 4, size = rootDir.size.toLong(), ads = shortAds(rootDir.size.toLong() to 2L))
        rootDir.copyInto(img, meta(2))

        val bdmvDir = fids(parent = true, "PLAYLIST" to (5 to true), "STREAM" to (6 to true), "CLIPINF" to (7 to true))
        fileEntry(img, meta(3), fileType = 4, size = bdmvDir.size.toLong(), embedded = bdmvDir)
        val playlistDir = fids(parent = true, "00000.mpls" to (8 to false), "00001.mpls" to (9 to false))
        fileEntry(img, meta(5), fileType = 4, size = playlistDir.size.toLong(), embedded = playlistDir)
        val streamDir = fids(parent = true, "00001.m2ts" to (10 to false), "00002.m2ts" to (11 to false))
        fileEntry(img, meta(6), fileType = 4, size = streamDir.size.toLong(), embedded = streamDir)
        val clipinfDir = fids(parent = true, "00001.clpi" to (12 to false))
        fileEntry(img, meta(7), fileType = 4, size = clipinfDir.size.toLong(), embedded = clipinfDir)

        val feature = mpls("00001" to hour, "00002" to hour / 2)
        fileEntry(img, meta(8), fileType = 5, size = feature.size.toLong(), embedded = feature)

        val extra = mpls("00002" to hour / 2)
        extra.copyInto(img, physical(20))
        fileEntry(img, meta(9), fileType = 5, size = extra.size.toLong(), ads = longAds(Triple(extra.size.toLong(), 20L, 0)), extended = true)

        clip1.copyInto(img, physical(30), 0, 4096)
        clip1.copyInto(img, physical(40), 4096, clip1.size)
        fileEntry(
            img, meta(10), fileType = 5, size = clip1.size.toLong(),
            ads = longAds(Triple(4096L, 30L, 0), Triple((clip1.size - 4096).toLong(), 40L, 0)),
        )

        clip2.copyInto(img, physical(50))
        fileEntry(
            img, meta(11), fileType = 5, size = clip2.size.toLong(),
            ads = longAds(Triple(sector.toLong(), 50L, 0), Triple((clip2.size - sector).toLong(), 51L, 0)),
            extended = true,
        )

        fileEntry(img, meta(12), fileType = 5, size = clipInfo.size.toLong(), embedded = clipInfo)
        return img
    }

    private fun fids(parent: Boolean, vararg entries: Pair<String, Pair<Int, Boolean>>): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        fun fid(name: String?, block: Int, directory: Boolean, isParent: Boolean) {
            val nameBytes = name?.let { byteArrayOf(8) + it.toByteArray(Charsets.ISO_8859_1) } ?: ByteArray(0)
            val length = (38 + nameBytes.size + 3) and 3.inv()
            val b = ByteArray(length)
            tag(b, 0, 257)
            u16(b, 16, 1)
            var characteristics = 0
            if (directory) characteristics = characteristics or 0x02
            if (isParent) characteristics = characteristics or 0x08
            b[18] = characteristics.toByte()
            b[19] = nameBytes.size.toByte()
            longAd(b, 20, sector.toLong(), block.toLong(), 1)
            nameBytes.copyInto(b, 38)
            out.write(b)
        }
        if (parent) fid(null, 1, directory = true, isParent = true)
        entries.forEach { (name, target) -> fid(name, target.first, target.second, isParent = false) }
        return out.toByteArray()
    }

    private fun shortAds(vararg extents: Pair<Long, Long>): Pair<Int, ByteArray> {
        val b = ByteArray(extents.size * 8)
        extents.forEachIndexed { i, (length, block) ->
            u32(b, i * 8, length)
            u32(b, i * 8 + 4, block)
        }
        return 0 to b
    }

    private fun longAds(vararg extents: Triple<Long, Long, Int>): Pair<Int, ByteArray> {
        val b = ByteArray(extents.size * 16)
        extents.forEachIndexed { i, (length, block, partition) -> longAd(b, i * 16, length, block, partition) }
        return 1 to b
    }

    private fun fileEntry(
        img: ByteArray,
        offset: Int,
        fileType: Int,
        size: Long,
        ads: Pair<Int, ByteArray>? = null,
        embedded: ByteArray? = null,
        extended: Boolean = false,
    ) {
        tag(img, offset, if (extended) 266 else 261)
        u16(img, offset + 20, 4)
        img[offset + 27] = fileType.toByte()
        val adType = if (embedded != null) 3 else ads!!.first
        u16(img, offset + 34, adType)
        u32(img, offset + 56, size)
        val data = embedded ?: ads!!.second
        val (lengthAt, start) = if (extended) 212 to 216 else 172 to 176
        u32(img, offset + lengthAt, data.size.toLong())
        data.copyInto(img, offset + start)
    }

    private fun tag(b: ByteArray, offset: Int, id: Int) = u16(b, offset, id)

    private fun longAd(b: ByteArray, offset: Int, length: Long, block: Long, partition: Int) {
        u32(b, offset, length)
        u32(b, offset + 4, block)
        u16(b, offset + 8, partition)
    }

    private fun u16(b: ByteArray, offset: Int, value: Int) {
        b[offset] = value.toByte()
        b[offset + 1] = (value shr 8).toByte()
    }

    private fun u32(b: ByteArray, offset: Int, value: Long) {
        for (i in 0..3) b[offset + i] = (value shr (8 * i)).toByte()
    }
}
