package com.nuvio.app.features.music.webdav

import com.nuvio.app.features.music.data.MusicHttp
import com.nuvio.app.features.music.data.MusicStorage
import com.nuvio.app.features.music.data.lyrics.MiniXml
import com.nuvio.app.features.music.data.lyrics.XmlElement
import com.nuvio.app.features.music.data.lyrics.XmlNode
import io.ktor.http.decodeURLPart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

data class WebDavEntry(
    val name: String,
    val url: String,
    val isDirectory: Boolean,
    val size: Long = 0L,
)

data class WebDavConfig(
    val url: String = "",
    val username: String = "",
    val password: String = "",
) {
    val configured: Boolean get() = url.isNotBlank()
}

/** Browses a WebDAV collection (PROPFIND, Depth 1) and hands out the Basic-auth header for its files. */
@OptIn(ExperimentalEncodingApi::class)
object MusicWebDav {
    private const val STORE = "webdav"
    private const val KEY_URL = "url"
    private const val KEY_USER = "user"
    private const val KEY_PASSWORD = "password"

    private val audioExtensions = setOf("mp3", "m4a", "aac", "flac", "ogg", "oga", "opus", "wav", "wma", "m4b")

    private val _config = MutableStateFlow(load())
    val config: StateFlow<WebDavConfig> = _config.asStateFlow()

    private fun load() = WebDavConfig(
        url = MusicStorage.getString(STORE, KEY_URL).orEmpty(),
        username = MusicStorage.getString(STORE, KEY_USER).orEmpty(),
        password = MusicStorage.getString(STORE, KEY_PASSWORD).orEmpty(),
    )

    /** Re-reads storage (Android calls this once its preferences are ready). */
    fun refresh() {
        _config.value = load()
    }

    fun save(url: String, username: String, password: String) {
        val normalized = url.trim().let { if (it.endsWith("/")) it else "$it/" }
        _config.value = WebDavConfig(normalized, username.trim(), password)
        MusicStorage.putString(STORE, KEY_URL, normalized)
        MusicStorage.putString(STORE, KEY_USER, username.trim())
        MusicStorage.putString(STORE, KEY_PASSWORD, password)
    }

    fun clear() {
        _config.value = WebDavConfig()
        MusicStorage.clear(STORE)
    }

    private fun basic(user: String, password: String): String =
        "Basic " + Base64.encode("$user:$password".encodeToByteArray())

    /** Headers a media request to [url] needs, only for the configured server. */
    fun headersFor(url: String): Map<String, String> {
        val current = _config.value
        if (!current.configured || current.username.isBlank()) return emptyMap()
        if (hostOf(url) != hostOf(current.url)) return emptyMap()
        return mapOf("Authorization" to basic(current.username, current.password))
    }

    suspend fun connect(url: String, username: String, password: String): Result<Unit> {
        val previous = _config.value
        save(url, username, password)
        val result = list(_config.value.url)
        if (result.isFailure) {
            if (previous.configured) save(previous.url, previous.username, previous.password) else clear()
        }
        return result.map { }
    }

    fun isAudio(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in audioExtensions

    suspend fun list(url: String): Result<List<WebDavEntry>> = runCatching {
        val current = _config.value
        require(current.configured) { "Add a WebDAV server first." }
        val body = """<?xml version="1.0" encoding="utf-8" ?>
<d:propfind xmlns:d="DAV:"><d:prop><d:displayname /><d:resourcetype /><d:getcontentlength /></d:prop></d:propfind>"""
        val headers = buildMap {
            put("Depth", "1")
            put("Accept", "application/xml, text/xml")
            put("Content-Type", "application/xml; charset=utf-8")
            if (current.username.isNotBlank()) put("Authorization", basic(current.username, current.password))
        }
        val response = MusicHttp.request("PROPFIND", url, headers, body)
        require(response.isSuccessful) { "WebDAV returned HTTP ${response.status}." }
        parseEntries(response.body, url)
    }

    private fun parseEntries(xml: String, baseUrl: String): List<WebDavEntry> {
        val document = MiniXml.parse(xml)
        val responses = document.getElementsByTagName("D:response").takeIf { it.length > 0 }
            ?: document.getElementsByTagName("d:response").takeIf { it.length > 0 }
            ?: document.getElementsByTagName("response")
        val base = baseUrl.trimEnd('/')
        val entries = mutableListOf<WebDavEntry>()
        for (i in 0 until responses.length) {
            val element = responses.item(i) as? XmlElement ?: continue
            val href = element.firstText("href")
            if (href.isNullOrBlank()) continue
            val resolved = resolve(baseUrl, href.trim())
            if (resolved.trimEnd('/') == base || resolved.trimEnd('/') == baseUrl) continue
            val directory = element.hasDescendant("collection")
            val size = element.firstText("getcontentlength")?.trim()?.toLongOrNull() ?: 0L
            val display = element.firstText("displayname")?.trim()?.takeIf { it.isNotBlank() } ?: decodeName(resolved)
            if (directory || display.isNotBlank()) {
                entries += WebDavEntry(display.ifBlank { "Folder" }, resolved, directory, size)
            }
        }
        return entries.sortedWith(compareBy<WebDavEntry> { !it.isDirectory }.thenBy { it.name.lowercase() })
    }

    private fun XmlElement.firstText(local: String): String? {
        fun walk(node: XmlNode): String? {
            for (child in node.childList) {
                if (child is XmlElement) {
                    if (child.tagName.substringAfter(':') == local) return child.textContent
                    walk(child)?.let { return it }
                }
            }
            return null
        }
        return walk(this)
    }

    private fun XmlElement.hasDescendant(local: String): Boolean {
        fun walk(node: XmlNode): Boolean = node.childList.any {
            it is XmlElement && (it.tagName.substringAfter(':') == local || walk(it))
        }
        return walk(this)
    }

    private fun hostOf(url: String): String =
        url.substringAfter("://", "").substringBefore('/').substringAfter('@').lowercase()

    private fun resolve(base: String, href: String): String = when {
        href.startsWith("http://", ignoreCase = true) || href.startsWith("https://", ignoreCase = true) -> href
        href.startsWith("/") -> {
            val scheme = base.substringBefore("://")
            val authority = base.substringAfter("://").substringBefore('/')
            "$scheme://$authority$href"
        }
        else -> base.substringBeforeLast('/', base) + "/" + href
    }

    private fun decodeName(url: String): String {
        val part = url.trimEnd('/').substringAfterLast('/')
        return runCatching { part.decodeURLPart() }.getOrDefault(part)
    }
}
