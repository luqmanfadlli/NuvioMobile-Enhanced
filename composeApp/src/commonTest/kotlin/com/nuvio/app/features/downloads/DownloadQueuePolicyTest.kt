package com.nuvio.app.features.downloads

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DownloadQueuePolicyTest {
    private fun item(
        id: String,
        status: DownloadStatus = DownloadStatus.Queued,
        createdAt: Long = 0,
        show: String? = "show",
        episode: Int? = 1,
    ) = DownloadItem(
        id = id,
        contentType = if (episode == null) "movie" else "series",
        parentMetaId = show.orEmpty(),
        parentMetaType = if (episode == null) "movie" else "series",
        videoId = id,
        title = id,
        seasonNumber = episode?.let { 1 },
        episodeNumber = episode,
        streamTitle = "stream",
        providerName = "provider",
        sourceUrl = "https://example.test/video.mkv",
        fileName = "$id.mkv",
        status = status,
        createdAtEpochMs = createdAt,
        updatedAtEpochMs = createdAt,
    )

    @Test fun `network policy blocks starts`() {
        assertFalse(DownloadQueuePolicy(3, 2, 2, false).canStart("show", emptyList()))
    }

    @Test fun `queued items remain queued while network policy disallows downloads`() {
        val queued = listOf(item("first", createdAt = 1), item("second", createdAt = 2))
        assertEquals(null, DownloadQueuePolicy(3, 2, 2, false).nextToStart(queued))
        assertEquals(2, queued.count { it.status == DownloadStatus.Queued })
    }

    @Test fun `total limit blocks starts`() {
        val running = listOf(item("a", DownloadStatus.Downloading), item("b", DownloadStatus.Downloading, show = "other"))
        assertFalse(DownloadQueuePolicy(2, 3, 3, true).canStart("third", running))
    }

    @Test fun `per show limit blocks starts`() {
        assertFalse(DownloadQueuePolicy(4, 1, 3, true).canStart("show", listOf(item("a", DownloadStatus.Downloading))))
    }

    @Test fun `max shows blocks new show`() {
        assertFalse(DownloadQueuePolicy(4, 2, 1, true).canStart("other", listOf(item("a", DownloadStatus.Downloading))))
    }

    @Test fun `movie with null show key may start`() {
        assertTrue(DownloadQueuePolicy(1, 1, 1, true).canStart(null, emptyList()))
    }

    @Test fun `next queued is oldest eligible and skips blocked item`() {
        val items = listOf(
            item("blocked", createdAt = 1, show = "blocked"),
            item("newer", createdAt = 3, show = "active"),
            item("older", createdAt = 2, show = "active"),
            item("running", DownloadStatus.Downloading, show = "active"),
        )
        val policy = DownloadQueuePolicy(3, 2, 1, true)
        assertEquals("older", policy.nextToStart(items)?.id)
    }
}
