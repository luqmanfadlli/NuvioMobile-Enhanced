package com.nuvio.app.features.telegram

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

internal data class TelegramIndexedMedia(
    val chatId: Long, val messageId: Long, val fileId: Int,
    val fileName: String, val fileSize: Long, val mimeType: String,
    val caption: String, val durationSeconds: Int, val width: Int, val height: Int,
    val hasAudioTag: Boolean = false, val hasSubtitleTag: Boolean = false,
)

/** Private metadata-only index persisted atomically; media bytes and Telegram credentials are never stored. */
internal class TelegramMediaIndex(context: Context) {
    companion object {
        private const val MAX_ENTRIES = 25_000
        private const val VERSION = 1
    }
    private val file = AtomicFile(File(context.applicationContext.filesDir, "telegram/media_index.json"))

    @Synchronized fun count(): Int = readRoot().optJSONArray("items")?.length() ?: 0
    @Synchronized fun cursor(chatId: Long): Long =
        readRoot().optJSONObject("cursors")?.optLong(chatId.toString(), 0L) ?: 0L

    @Synchronized fun isComplete(chatId: Long): Boolean = cursor(chatId) == -1L

    @Synchronized fun isFresh(maxAgeMs: Long): Boolean {
        val updatedAt = readRoot().optLong("updatedAt", 0L)
        return updatedAt > 0L && System.currentTimeMillis() - updatedAt <= maxAgeMs
    }

    @Synchronized fun touch() {
        val root = readRoot()
        root.put("updatedAt", System.currentTimeMillis())
        writeRoot(root)
    }

    @Synchronized fun setCursor(chatId: Long, value: Long) {
        val root = readRoot()
        val cursors = root.optJSONObject("cursors") ?: JSONObject()
        cursors.put(chatId.toString(), value)
        root.put("cursors", cursors)
        root.put("updatedAt", System.currentTimeMillis())
        writeRoot(root)
    }

    @Synchronized fun entries(chatIds: Set<Long>): List<TelegramIndexedMedia> {
        if (chatIds.isEmpty()) return emptyList()
        val array = readRoot().optJSONArray("items") ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val chatId = item.optLong("chatId")
                if (chatId !in chatIds) continue
                val fileId = item.optInt("fileId")
                val fileName = item.optString("fileName")
                if (fileId <= 0 || fileName.isBlank()) continue
                add(TelegramIndexedMedia(
                    chatId, item.optLong("messageId"), fileId, fileName,
                    item.optLong("fileSize"), item.optString("mimeType"), item.optString("caption"),
                    item.optInt("durationSeconds"), item.optInt("width"), item.optInt("height"),
                    item.optBoolean("hasAudioTag"), item.optBoolean("hasSubtitleTag"),
                ))
            }
        }
    }

    @Synchronized fun upsert(entries: List<TelegramIndexedMedia>, prepend: Boolean = false) {
        if (entries.isEmpty()) return
        val root = readRoot()
        val current = root.optJSONArray("items") ?: JSONArray()
        val values = LinkedHashMap<String, JSONObject>()
        fun toJson(entry: TelegramIndexedMedia) = JSONObject()
            .put("chatId", entry.chatId).put("messageId", entry.messageId)
            .put("fileId", entry.fileId).put("fileName", entry.fileName)
            .put("fileSize", entry.fileSize).put("mimeType", entry.mimeType)
            .put("caption", entry.caption).put("durationSeconds", entry.durationSeconds)
            .put("width", entry.width).put("height", entry.height)
            .put("hasAudioTag", entry.hasAudioTag).put("hasSubtitleTag", entry.hasSubtitleTag)
        if (prepend) {
            entries.forEach { entry ->
                val entryKey = entry.chatId.toString() + ":" + entry.messageId
                values[entryKey] = toJson(entry)
            }
        }
        for (i in 0 until current.length()) {
            val item = current.optJSONObject(i) ?: continue
            val itemKey = item.optLong("chatId").toString() + ":" + item.optLong("messageId")
            if (itemKey !in values) values[itemKey] = item
        }
        if (!prepend) {
            entries.forEach { entry ->
                values[entry.chatId.toString() + ":" + entry.messageId] = toJson(entry)
            }
        }
        while (values.size > MAX_ENTRIES) values.remove(values.keys.last())
        val output = JSONArray()
        values.values.forEach(output::put)
        root.put("version", VERSION).put("updatedAt", System.currentTimeMillis()).put("items", output)
        writeRoot(root)
    }

    @Synchronized fun clear() = file.delete()

    private fun readRoot(): JSONObject = try {
        if (!file.baseFile.exists()) JSONObject()
        else file.openRead().bufferedReader(Charsets.UTF_8).use { JSONObject(it.readText()) }
    } catch (_: Exception) { JSONObject() }

    private fun writeRoot(root: JSONObject) {
        val stream = file.startWrite()
        try {
            stream.write(root.toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
        } catch (error: Exception) {
            file.failWrite(stream)
            throw error
        }
    }
}
