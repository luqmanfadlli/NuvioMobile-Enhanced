package com.nuvio.app.features.telegram

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class TelegramMediaIndexTest {
    private lateinit var index: TelegramMediaIndex

    @Before
    fun setUp() {
        index = TelegramMediaIndex(RuntimeEnvironment.getApplication<Application>())
        index.clear()
    }

    @Test
    fun persistsEntriesAndFiltersBySelectedChats() {
        index.upsert(listOf(media(10L, 100L), media(20L, 200L)))

        assertEquals(2, index.count())
        assertEquals(listOf(100L), index.entries(setOf(10L)).map { it.messageId })
        assertEquals(listOf(200L), index.entries(setOf(20L)).map { it.messageId })
        assertTrue(index.entries(setOf(30L)).isEmpty())
    }

    @Test
    fun upsertReplacesAnExistingMessageInsteadOfDuplicatingIt() {
        index.upsert(listOf(media(10L, 100L, "old.mkv")))
        index.upsert(listOf(media(10L, 100L, "new.mkv")))

        assertEquals(1, index.count())
        assertEquals("new.mkv", index.entries(setOf(10L)).single().fileName)
    }

    @Test
    fun persistsResumeCursorAndCompletionState() {
        index.setCursor(10L, 456L)
        assertEquals(456L, index.cursor(10L))
        assertFalse(index.isComplete(10L))

        index.setCursor(10L, -1L)
        assertEquals(-1L, index.cursor(10L))
        assertTrue(index.isComplete(10L))
    }

    @Test
    fun clearRemovesEntriesAndCursorState() {
        index.upsert(listOf(media(10L, 100L)))
        index.setCursor(10L, -1L)

        index.clear()

        assertEquals(0, index.count())
        assertEquals(0L, index.cursor(10L))
        assertFalse(index.isComplete(10L))
    }

    private fun media(chatId: Long, messageId: Long, fileName: String = "Example.Show.S01E02.mkv") =
        TelegramIndexedMedia(
            chatId = chatId,
            messageId = messageId,
            fileId = messageId.toInt(),
            fileName = fileName,
            fileSize = 1024L,
            mimeType = "video/x-matroska",
            caption = "",
            durationSeconds = 120,
            width = 1920,
            height = 1080,
        )
}
