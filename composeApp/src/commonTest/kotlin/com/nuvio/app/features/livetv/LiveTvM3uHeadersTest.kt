package com.nuvio.app.features.livetv

import kotlin.test.Test
import kotlin.test.assertEquals

class LiveTvM3uHeadersTest {

    @Test
    fun channelsWithoutOptionsAreOpenedWithVlcUserAgent() {
        val channel = parseM3uPlaylist(
            """
            #EXTM3U
            #EXTINF:-1 tvg-id="news" group-title="News",News HD
            http://example.com/live/1.ts
            """.trimIndent(),
        ).single()

        assertEquals("http://example.com/live/1.ts", channel.streamUrl)
        assertEquals(mapOf("User-Agent" to LIVE_TV_STREAM_USER_AGENT), channel.headers)
    }

    @Test
    fun kodiOptionsAreHeadersNotPartOfTheLink() {
        val channel = parseM3uPlaylist(
            """
            #EXTM3U
            #EXTINF:-1,Sports
            https://example.com/hls/sports.m3u8|User-Agent=Mozilla%2F5.0%20(SMART-TV)&Referer=https://example.com/
            """.trimIndent(),
        ).single()

        assertEquals("https://example.com/hls/sports.m3u8", channel.streamUrl)
        assertEquals("Mozilla/5.0 (SMART-TV)", channel.headers["User-Agent"])
        assertEquals("https://example.com/", channel.headers["Referer"])
    }

    @Test
    fun vlcOptionsApplyToTheNextChannelOnly() {
        val channels = parseM3uPlaylist(
            """
            #EXTM3U
            #EXTINF:-1,First
            #EXTVLCOPT:http-user-agent=CustomAgent/1.0
            #EXTVLCOPT:http-referrer=https://ref.example/
            #EXTHTTP:{"Origin":"https://origin.example"}
            http://example.com/first.ts
            #EXTINF:-1,Second
            http://example.com/second.ts
            """.trimIndent(),
        )

        assertEquals(
            mapOf(
                "User-Agent" to "CustomAgent/1.0",
                "Referer" to "https://ref.example/",
                "Origin" to "https://origin.example",
            ),
            channels[0].headers,
        )
        assertEquals(mapOf("User-Agent" to LIVE_TV_STREAM_USER_AGENT), channels[1].headers)
    }
}
