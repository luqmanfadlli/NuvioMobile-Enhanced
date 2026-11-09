package com.nuvio.app.features.music

import com.nuvio.app.features.music.data.md5Hex
import com.nuvio.app.features.music.data.sha1Hex
import com.nuvio.app.features.music.data.lyrics.LrcLib
import com.nuvio.app.features.music.data.lyrics.MiniXml
import com.nuvio.app.features.music.data.lyrics.TtmlLyrics
import com.nuvio.app.features.music.data.toHttpUrl
import com.nuvio.app.features.music.lastfm.MusicLastFm
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MusicDataTest {
    @Test
    fun md5MatchesKnownVectors() {
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", md5Hex(""))
        assertEquals("900150983cd24fb0d6963f7d28e17f72", md5Hex("abc"))
        assertEquals("9e107d9d372bb6826bd81d3542a419d6", md5Hex("The quick brown fox jumps over the lazy dog"))
        // Longer than one 64-byte block, to exercise padding across blocks.
        assertEquals("57edf4a22be3c955ac49da2e2107b67a", md5Hex("12345678901234567890123456789012345678901234567890123456789012345678901234567890"))
    }

    @Test
    fun sha1MatchesKnownVectors() {
        assertEquals("da39a3ee5e6b4b0d3255bfef95601890afd80709", sha1Hex(""))
        assertEquals("a9993e364706816aba3e25717850c26c9cd0d89d", sha1Hex("abc"))
        assertEquals("2fd4e1c67a2d28fced849ee1bb76e7391b93eb12", sha1Hex("The quick brown fox jumps over the lazy dog"))
        assertEquals("84983e441c3bd26ebaae4aa1f95129e5e54670f1", sha1Hex("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq"))
    }

    @Test
    fun subsonicTokenIsMd5OfPasswordAndSalt() {
        // The Subsonic docs' own example: password "sesame", salt "c19b2d" -> 26719a1196d2a940705a59634eb18eab.
        assertEquals("26719a1196d2a940705a59634eb18eab", md5Hex("sesame" + "c19b2d"))
    }

    @Test
    fun lastFmSignatureSortsParametersAndAppendsSecret() {
        val sig = MusicLastFm.signature(mapOf("b" to "2", "a" to "1"), "s3cret")
        assertEquals(md5Hex("a1b2s3cret"), sig)
    }

    @Test
    fun urlBuilderEncodesAndAppendsQuery() {
        val url = "https://example.com/search".toHttpUrl()
            .newBuilder()
            .addQueryParameter("track_name", "Don't Stop")
            .addQueryParameter("artist_name", "A & B")
            .build()
            .toString()
        assertEquals("https://example.com/search?track_name=Don%27t%20Stop&artist_name=A%20%26%20B", url)
    }

    @Test
    fun lrcParsesTimestampsAndSkipsMetadata() {
        val lines = LrcLib.parseLrc("[ar:Someone]\n[00:12.50] First line\n[01:02.345] Second line\n")
        assertEquals(2, lines.count { it.text.isNotBlank() })
        val first = lines.first { it.text == "First line" }
        val second = lines.first { it.text == "Second line" }
        assertEquals(12_500L, first.timeMs)
        assertEquals(62_345L, second.timeMs)
    }

    @Test
    fun miniXmlReadsNestedElementsAttributesAndEntities() {
        val doc = MiniXml.parse("""<?xml version="1.0"?><!-- c --><a x="1 &amp; 2"><b>hi &lt;there&gt;</b><c/></a>""")
        val a = doc.root!!
        assertEquals("a", a.tagName)
        assertEquals("1 & 2", a.getAttribute("x"))
        assertEquals("hi <there>", doc.getElementsByTagName("b").item(0)!!.textContent)
        assertEquals(1, doc.getElementsByTagName("c").length)
    }

    @Test
    fun ttmlGroupsSyllablesIntoWordsAndKeepsLineTimes() {
        val ttml = """
            <tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata">
              <body><div>
                <p begin="1.000" end="3.000" ttm:agent="v1"><span begin="1.000" end="1.500">e</span><span begin="1.500" end="2.000">nough</span> <span begin="2.200" end="3.000">time</span></p>
              </div></body>
            </tt>
        """.trimIndent()
        val lines = TtmlLyrics.parse(ttml).filter { it.text.isNotBlank() }
        assertEquals(1, lines.size)
        val line = lines.single()
        assertEquals(1_000L, line.timeMs)
        assertEquals("enough time", line.text)
        assertTrue(line.isWordSynced)
        assertEquals(listOf("enough", "time"), line.words.map { it.text.trim() })
    }
}
