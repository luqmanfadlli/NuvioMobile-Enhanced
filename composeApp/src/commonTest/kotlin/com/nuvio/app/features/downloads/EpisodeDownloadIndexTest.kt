package com.nuvio.app.features.downloads

import kotlin.test.Test
import kotlin.test.assertEquals

class EpisodeDownloadIndexTest {
    private fun item(
        id: String,
        status: DownloadStatus,
        season: Int? = 1,
        episode: Int? = 1,
        videoId: String = "video",
    ) = DownloadItem(
        id = id,
        contentType = if (season == null) "movie" else "series",
        parentMetaId = "show",
        parentMetaType = if (season == null) "movie" else "series",
        videoId = videoId,
        title = id,
        seasonNumber = season,
        episodeNumber = episode,
        streamTitle = "stream",
        providerName = "provider",
        sourceUrl = "https://example.test/video.mkv",
        fileName = "$id.mkv",
        status = status,
        createdAtEpochMs = 0,
        updatedAtEpochMs = 0,
    )

    @Test fun `prefers downloading over queued`() {
        val queued = item("queued", DownloadStatus.Queued)
        val downloading = item("downloading", DownloadStatus.Downloading)
        val index = EpisodeDownloadIndex.build(listOf(queued, downloading), "show")
        assertEquals("downloading", index.find(1, 1)?.id)
    }

    @Test fun `matches by video id`() {
        val download = item("by-id", DownloadStatus.Queued, season = 3, episode = 7, videoId = "target")
        val index = EpisodeDownloadIndex.build(listOf(download), "show")
        assertEquals("by-id", index.find(1, 2, "target")?.id)
    }

    @Test fun `matches by season and episode`() {
        val download = item("by-number", DownloadStatus.Queued, season = 2, episode = 5)
        val index = EpisodeDownloadIndex.build(listOf(download), "show")
        assertEquals("by-number", index.find(2, 5)?.id)
    }

    @Test fun `movie lookup uses two null episode numbers`() {
        val movie = item("movie", DownloadStatus.Queued, season = null, episode = null)
        val index = EpisodeDownloadIndex.build(listOf(movie), "show")
        assertEquals("movie", index.find(null, null)?.id)
    }
}
