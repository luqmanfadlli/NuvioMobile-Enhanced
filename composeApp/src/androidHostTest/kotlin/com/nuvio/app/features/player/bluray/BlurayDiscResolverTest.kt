@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.nuvio.app.features.player.bluray

import android.app.Application
import androidx.media3.datasource.DefaultHttpDataSource
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BlurayDiscResolverTest {
    private val server = MockWebServer()
    private val factory = DefaultHttpDataSource.Factory()
    private val hour = 45_000L * 3600

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

    private val playlists by lazy {
        mapOf(
            "00000.mpls" to mpls("00001" to hour, "00002" to hour / 2),
            "00001.mpls" to mpls(*Array(40) { "00009" to hour / 5 }),
            "00002.mpls" to mpls("00003" to hour / 40),
            "00003.mpls" to mpls("00001" to hour),
        )
    }

    private val clipInfo: ByteArray by lazy {
        val streams = listOf(0x1011 to "", 0x1100 to "eng", 0x1101 to "por", 0x1200 to "por", 0x1800 to "eng", 0x1A00 to "deu")
            .map { (pid, language) ->
                val info = when {
                    language.isEmpty() -> byteArrayOf(0x24, 0x61, 0, 0, 0)
                    pid in 0x1100..0x111F || pid >= 0x1A00 -> byteArrayOf(0x81.toByte(), 0x61) + language.toByteArray() + 0
                    else -> byteArrayOf(0x90.toByte()) + language.toByteArray() + 0
                }
                byteArrayOf((pid shr 8).toByte(), pid.toByte(), info.size.toByte()) + info
            }
        "HDMV0300".toByteArray() + byteArrayOf(0, 0, 0, 0, 0, 0, 0, 40) + ByteArray(24) +
            byteArrayOf(0, 0, 0, 0, 0, 1) + byteArrayOf(0, 0, 0, 0, 0x01, 0x00, streams.size.toByte(), 0) +
            streams.reduce { a, b -> a + b }
    }

    private val movieObjects: ByteArray by lazy {
        fun command(head: Int, options: Int, destination: Int): ByteArray =
            byteArrayOf(head.toByte(), options.toByte(), 0, 0) +
                byteArrayOf((destination shr 24).toByte(), (destination shr 16).toByte(), (destination shr 8).toByte(), destination.toByte()) +
                ByteArray(4)
        val first = byteArrayOf(0, 0, 0, 3) + command(0x22, 0x80, 0) + command(0x50, 0x80, 7) + command(0x22, 0x00, 2)
        val second = byteArrayOf(0, 0, 0, 2) + command(0x22, 0x81, 3) + command(0x22, 0x80, 0)
        val body = byteArrayOf(0, 2) + first + second
        "MOBJ0200".toByteArray() + ByteArray(32) + byteArrayOf(0, 0, 0, body.size.toByte()) + ByteArray(4) + body
    }

    @Before fun start() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                if (path == "/disc/BDMV/CLIPINF/00001.clpi") return MockResponse().setBody(Buffer().write(clipInfo))
                if (path == "/disc/BDMV/PLAYLIST/") {
                    return MockResponse().setBody(playlists.keys.joinToString("") { "<a href=\"$it\">$it</a>\n" })
                }
                if (path == "/folder/") {
                    return MockResponse().setBody("<html><a href=\"BDMV/\">BDMV/</a><a href=\"CERTIFICATE/\">CERTIFICATE/</a></html>")
                }
                if (path == "/folder/BDMV/PLAYLIST/") {
                    return MockResponse().setBody(playlists.keys.joinToString("") { "<a href=\"$it\">$it</a>\n" })
                }
                if (path.startsWith("/nolist/")) {
                    if (!path.endsWith("?token=abc")) return MockResponse().setResponseCode(403)
                    val bare = path.removeSuffix("?token=abc")
                    if (bare == "/nolist/BDMV/MovieObject.bdmv") return MockResponse().setBody(Buffer().write(movieObjects))
                    val listed = bare.removePrefix("/nolist/BDMV/PLAYLIST/")
                    if (listed in setOf("00000.mpls", "00003.mpls")) {
                        return MockResponse().setBody(Buffer().write(playlists.getValue(listed)))
                    }
                    return MockResponse().setResponseCode(404)
                }
                val name = path.removePrefix("/disc/BDMV/PLAYLIST/").removePrefix("/folder/BDMV/PLAYLIST/")
                return playlists[name]?.let { MockResponse().setBody(Buffer().write(it)) } ?: MockResponse().setResponseCode(404)
            }
        }
        server.start()
    }

    @After fun stop() = server.shutdown()

    private fun clip(path: String) = server.url(path).toString()

    private fun resolve(clipName: String) = BlurayDiscResolver.resolveBeforePlayback(clip("/disc/BDMV/STREAM/$clipName"), factory)

    @Test fun `a clip that is part of its feature plays the feature playlist`() {
        val disc = resolve("00001.m2ts")!!
        assertEquals("00000.mpls", disc.feature.name)
        assertEquals(listOf(clip("/disc/BDMV/STREAM/00001.m2ts"), clip("/disc/BDMV/STREAM/00002.m2ts")), disc.feature.clipUris)
        assertEquals(listOf(3_600_000L, 1_800_000L), disc.feature.durationsMs)
    }

    @Test fun `a disc offers its titles, longest first, without loops`() {
        val disc = resolve("00001.m2ts")!!
        assertEquals(listOf("00000.mpls", "00003.mpls", "00002.mpls"), disc.titles.map { it.name })
        assertEquals(listOf(5_400_000L, 3_600_000L, 90_000L), disc.titles.map { it.durationMs })
    }

    @Test fun `a clip that is a whole title plays as that title`() {
        val disc = resolve("00003.m2ts")!!
        assertEquals("00002.mpls", disc.feature.name)
        assertEquals(listOf(clip("/disc/BDMV/STREAM/00003.m2ts")), disc.feature.clipUris)
    }

    @Test fun `a clip outside every playlist or folder plays alone`() {
        val outside = resolve("00004.m2ts")!!
        assertEquals(listOf(clip("/disc/BDMV/STREAM/00004.m2ts")), outside.feature.clipUris)
        assertEquals(1, outside.titles.size)
        val other = BlurayDiscResolver.resolveBeforePlayback(clip("/other/BDMV/STREAM/00001.m2ts"), factory)!!
        assertEquals(listOf(clip("/other/BDMV/STREAM/00001.m2ts")), other.feature.clipUris)
    }

    @Test fun `a listing of a disc folder plays its feature after a failure`() {
        val disc = BlurayDiscResolver.probeAfterFailure(clip("/folder/"), factory)
        assertNotNull(disc)
        assertEquals("00000.mpls", disc!!.feature.name)
        assertEquals(listOf(clip("/folder/BDMV/STREAM/00001.m2ts"), clip("/folder/BDMV/STREAM/00002.m2ts")), disc.feature.clipUris)
    }

    @Test fun `a title's languages come from its first clip`() {
        val disc = resolve("00001.m2ts")!!
        assertEquals(listOf("eng", "por") to listOf("por", "eng"), BlurayDiscResolver.titleLanguages(disc.feature, factory))
        assertEquals(emptyList<String>() to emptyList<String>(), BlurayDiscResolver.titleLanguages(disc.titles.last(), factory))
    }

    @Test fun `an index bdmv url plays the disc it belongs to`() {
        val disc = BlurayDiscResolver.resolveBeforePlayback(clip("/disc/BDMV/index.bdmv"), factory)!!
        assertEquals("00000.mpls", disc.feature.name)
        assertEquals(listOf(clip("/disc/BDMV/STREAM/00001.m2ts"), clip("/disc/BDMV/STREAM/00002.m2ts")), disc.feature.clipUris)
        assertEquals(listOf("00000.mpls", "00003.mpls", "00002.mpls"), disc.titles.map { it.name })
    }

    @Test fun `an mpls url plays that playlist and offers the disc's other titles`() {
        val disc = BlurayDiscResolver.resolveBeforePlayback(clip("/disc/BDMV/PLAYLIST/00003.mpls"), factory)!!
        assertEquals("00003.mpls", disc.feature.name)
        assertEquals(listOf(clip("/disc/BDMV/STREAM/00001.m2ts")), disc.feature.clipUris)
        assertEquals(listOf("00000.mpls", "00003.mpls", "00002.mpls"), disc.titles.map { it.name })
    }

    @Test fun `without a listing the playlists come from the movie objects, keeping the url's query`() {
        val disc = BlurayDiscResolver.resolveBeforePlayback(clip("/nolist/BDMV/index.bdmv?token=abc"), factory)!!
        assertEquals("00000.mpls", disc.feature.name)
        assertEquals(
            listOf(clip("/nolist/BDMV/STREAM/00001.m2ts?token=abc"), clip("/nolist/BDMV/STREAM/00002.m2ts?token=abc")),
            disc.feature.clipUris,
        )
        assertEquals(listOf("00000.mpls", "00003.mpls"), disc.titles.map { it.name })
    }

    @Test fun `movie objects name the playlists their play commands start`() {
        assertEquals(listOf(0, 3), BlurayDiscResolver.parseMovieObjectPlaylists(movieObjects))
        assertEquals(emptyList<Int>(), BlurayDiscResolver.parseMovieObjectPlaylists(ByteArray(64)))
    }

    @Test fun `titles shorter than a minute or repeating another are left out`() {
        val titles = BlurayDiscResolver.selectTitles(
            listOf(
                BlurayDiscResolver.parseMpls("00010.mpls", mpls("00001" to hour))!!,
                BlurayDiscResolver.parseMpls("00011.mpls", mpls("00001" to hour))!!,
                BlurayDiscResolver.parseMpls("00012.mpls", mpls("00004" to 45_000L * 30))!!,
            ),
        )
        assertEquals(listOf("00010.mpls"), titles.map { it.name })
    }

    @Test fun `the feature is the longest playlist that is not a loop`() {
        val parsed = playlists.map { (name, data) -> BlurayDiscResolver.parseMpls(name, data)!! }
        assertEquals("00000.mpls", BlurayDiscResolver.selectMainPlaylist(parsed)!!.name)
    }
}
