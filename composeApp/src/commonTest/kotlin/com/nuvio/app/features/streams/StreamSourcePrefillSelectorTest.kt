package com.nuvio.app.features.streams

import com.nuvio.app.features.downloads.DownloadPreferredResolution
import kotlin.test.Test
import kotlin.test.assertEquals

class StreamSourcePrefillSelectorTest {
    private fun stream(name: String, size: Long? = null) = StreamItem(
        name = name,
        url = "https://example.test/${name.replace(' ', '-')}.mkv",
        addonName = "Test",
        addonId = "test",
        behaviorHints = StreamBehaviorHints(videoSize = size),
    )

    @Test fun `chooses smallest at or above preferred resolution`() {
        val streams = listOf(stream("2160p", 80_000_000), stream("1080p", 30_000_000), stream("720p", 15_000_000))
        assertEquals("1080p", StreamSourcePrefillSelector.selectForBatch(streams, DownloadPreferredResolution.P1080)?.stream?.name)
    }

    @Test fun `falls back to highest lower resolution`() {
        val streams = listOf(stream("720p", 30_000_000), stream("1080p", 60_000_000))
        assertEquals("1080p", StreamSourcePrefillSelector.selectForBatch(streams, DownloadPreferredResolution.P2160)?.stream?.name)
    }

    @Test fun `unknown resolution falls back to smallest stream`() {
        val streams = listOf(stream("WEB-DL", 35_000_000), stream("BluRay", 20_000_000))
        assertEquals("BluRay", StreamSourcePrefillSelector.selectForBatch(streams)?.stream?.name)
    }

    @Test fun `known size beats unknown size`() {
        val streams = listOf(stream("720p", null), stream("720p", 25_000_000))
        assertEquals(25_000_000L, StreamSourcePrefillSelector.selectForBatch(streams)?.sizeBytes)
    }

    @Test fun `skips a smaller torrent when debrid cannot resolve it`() {
        // No debrid is configured in tests, so the torrent has no way to become a download.
        val torrent = StreamItem(
            name = "1080p torrent",
            infoHash = "0123456789abcdef0123456789abcdef01234567",
            addonName = "Test",
            addonId = "addon:test",
            behaviorHints = StreamBehaviorHints(videoSize = 20_000_000),
        )
        val streams = listOf(torrent, stream("1080p direct", 40_000_000))
        assertEquals("1080p direct", StreamSourcePrefillSelector.selectForBatch(streams, DownloadPreferredResolution.P1080)?.stream?.name)
    }
}
