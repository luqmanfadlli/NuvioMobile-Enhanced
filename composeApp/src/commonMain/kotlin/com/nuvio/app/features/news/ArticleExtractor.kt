package com.nuvio.app.features.news

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

internal data class ArticleContent(
    val paragraphs: List<String>,
    val imageUrl: String?,
    val byline: String?,
)

/**
 * Pulls the readable text out of an article page so it can be shown in the app's own reader.
 * Tried in order: the structured article body most publishers embed for search engines (JSON-LD),
 * then the paragraphs inside the page's <article>. A page that gives neither returns no paragraphs,
 * and the reader falls back to the feed's summary and the full page.
 */
internal object ArticleExtractor {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val ENDS_LIKE_A_SENTENCE = Regex("[.!?\"\u201D\u2019)\\]]$")
    private val NOISE = Regex(
        "(?i)(to play this video|video can not be played|image source[,:]|image caption[,:]|skip navigation|getty images|promo code|all rights reserved|enable javascript|\u00A9|^(watch|listen|photo|video)\\b[:\\s]|^(sign up|subscribe|advertisement|read more|related|follow us|more:)|cookies? (policy|settings|consent))",
    )
    private val PARAGRAPH_BREAK = Regex("(?i)</p>\\s*<p[^>]*>|<br\\s*/?>|\\r?\\n")
    private val SENTENCE_END = Regex("(?<=[.!?])\\s+(?=[A-Z\"\u201C])")
    private val CONTENT_ATTR = Regex("(?i)content\\s*=\\s*[\"']([^\"']+)[\"']")
    private const val MAX_PARAGRAPHS = 150

    suspend fun load(url: String): ArticleContent? {
        val html = NewsHttp.get(url, "text/html,application/xhtml+xml", maxBytes = 8 * 1024 * 1024) ?: return null
        return runCatching { extract(html) }.getOrNull()
    }

    fun extract(html: String): ArticleContent {
        val structured = structuredBody(html)
        val fromJsonLd = structured?.first?.let { keep(splitStructured(it)) }.orEmpty()
        val paragraphs = if (fromJsonLd.size >= 2) fromJsonLd else keep(articleParagraphs(html))
        return ArticleContent(
            paragraphs = paragraphs.take(MAX_PARAGRAPHS),
            imageUrl = meta(html, "og:image"),
            byline = structured?.second ?: meta(html, "author"),
        )
    }

    // ---- structured data ------------------------------------------------------------------------

    private fun structuredBody(html: String): Pair<String, String?>? {
        var from = 0
        while (true) {
            val marker = html.indexOf("application/ld+json", from, ignoreCase = true)
            if (marker < 0) return null
            val open = html.indexOf('>', marker)
            val close = html.indexOf("</script>", open, ignoreCase = true)
            if (open < 0 || close < 0) return null
            val raw = html.substring(open + 1, close).trim()
            from = close
            val element = runCatching { json.parseToJsonElement(raw) }.getOrNull() ?: continue
            val body = findBody(element) ?: continue
            return body to findAuthor(element)
        }
    }

    private fun findBody(element: JsonElement): String? = when (element) {
        is JsonObject ->
            (element["articleBody"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.length > 200 }
                ?: element.values.firstNotNullOfOrNull { findBody(it) }
        is JsonArray -> element.firstNotNullOfOrNull { findBody(it) }
        else -> null
    }

    private fun findAuthor(element: JsonElement): String? = when (element) {
        is JsonObject -> {
            val author = element["author"]
            val named = when (author) {
                is JsonObject -> (author["name"] as? JsonPrimitive)?.contentOrNull
                is JsonArray -> author.mapNotNull { ((it as? JsonObject)?.get("name") as? JsonPrimitive)?.contentOrNull }.joinToString(", ").ifBlank { null }
                is JsonPrimitive -> author.contentOrNull
                else -> null
            }
            named?.let { NewsText.plain(it) }?.takeIf { it.isNotBlank() && it.length < 120 }
                ?: element.values.firstNotNullOfOrNull { findAuthor(it) }
        }
        is JsonArray -> element.firstNotNullOfOrNull { findAuthor(it) }
        else -> null
    }

    private fun splitStructured(body: String): List<String> {
        val parts = PARAGRAPH_BREAK.replace(body, "\n").split('\n').map { NewsText.plain(it) }.filter { it.isNotBlank() }
        return if (parts.size == 1 && parts[0].length > 600) chunkSentences(parts[0], 3) else parts
    }

    private fun chunkSentences(text: String, perParagraph: Int): List<String> =
        SENTENCE_END.split(text).chunked(perParagraph).map { it.joinToString(" ") }

    // ---- page paragraphs ------------------------------------------------------------------------

    private fun articleParagraphs(html: String): List<String> {
        val container = longestBlock(html, "article") ?: longestBlock(html, "main") ?: html
        val cleaned = NewsText.removeBlocks(container, listOf("script", "style", "noscript", "svg", "figure", "aside", "nav", "footer", "form", "button", "iframe"))
        val out = ArrayList<String>()
        var from = 0
        while (true) {
            val open = indexOfTag(cleaned, "p", from)
            if (open < 0) break
            val gt = cleaned.indexOf('>', open)
            if (gt < 0) break
            val close = cleaned.indexOf("</p>", gt, ignoreCase = true)
            if (close < 0) break
            out.add(NewsText.plain(cleaned.substring(gt + 1, close)))
            from = close + 4
        }
        return out
    }

    private fun longestBlock(html: String, tag: String): String? {
        var best: String? = null
        var from = 0
        while (true) {
            val open = indexOfTag(html, tag, from)
            if (open < 0) break
            val close = html.indexOf("</$tag>", open, ignoreCase = true)
            if (close < 0) break
            if (best == null || close - open > best.length) best = html.substring(open, close)
            from = close + tag.length + 3
        }
        return best
    }

    private fun indexOfTag(s: String, name: String, from: Int): Int {
        var i = from
        while (true) {
            val at = s.indexOf("<$name", i, ignoreCase = true)
            if (at < 0) return -1
            val next = s.getOrNull(at + name.length + 1)
            if (next == null || next == '>' || next.isWhitespace()) return at
            i = at + 1
        }
    }

    private fun keep(paragraphs: List<String>): List<String> {
        val seen = HashSet<String>()
        return paragraphs.filter { p ->
            p.length >= 40 && ENDS_LIKE_A_SENTENCE.containsMatchIn(p) && !NOISE.containsMatchIn(p) && seen.add(p)
        }
    }

    // ---- meta tags ------------------------------------------------------------------------------

    private fun meta(html: String, name: String): String? {
        var from = 0
        while (true) {
            val at = html.indexOf(name, from, ignoreCase = true)
            if (at < 0) return null
            val open = html.lastIndexOf('<', at)
            val close = html.indexOf('>', at)
            if (open < 0 || close < 0) return null
            val tag = html.substring(open, close)
            if (tag.startsWith("<meta", ignoreCase = true) && close - open < 2000) {
                CONTENT_ATTR.find(tag)?.groupValues?.get(1)?.let { return NewsText.decodeEntities(it).trim().ifBlank { null } }
            }
            from = at + name.length
            if (from >= html.length) return null
            if (at > 200_000) return null
        }
    }
}
