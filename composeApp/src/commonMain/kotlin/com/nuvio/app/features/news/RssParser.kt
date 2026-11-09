package com.nuvio.app.features.news

internal data class RssItem(
    val title: String,
    val link: String?,
    val guid: String?,
    /** Plain-text summary, tags stripped. */
    val summary: String,
    /** The full HTML body when the feed carries it (content:encoded, Atom content). */
    val contentHtml: String?,
    val publishedMs: Long,
    val author: String?,
    val imageUrl: String?,
    val categories: List<String>,
    val enclosureUrl: String?,
    val enclosureType: String?,
    /** itunes:duration as written ("1:02:03", "3723", "45:10"). */
    val duration: String?,
)

internal data class RssFeed(
    val title: String,
    val author: String?,
    val description: String,
    val imageUrl: String?,
    val items: List<RssItem>,
)

/** A forgiving RSS 2.0 / Atom reader built on string scanning, so it copes with the odd feed and stays fast on big ones. */
internal object RssParser {
    private val IMG_SRC = Regex("(?i)<img[^>]+src=[\"']([^\"']+)[\"']")
    private val IMAGE_EXT = Regex("(?i)\\.(jpe?g|png|webp|gif)(\\?|$)")

    fun parse(xml: String, maxItems: Int = 300): RssFeed {
        val isAtom = xml.contains("<feed", ignoreCase = true) && !xml.contains("<rss", ignoreCase = true)
        val itemTag = if (isAtom) "entry" else "item"
        val blocks = blocks(xml, itemTag, maxItems)
        val headerEnd = xml.indexOf("<$itemTag", ignoreCase = true).let { if (it < 0) xml.length else it }
        val header = xml.substring(0, headerEnd)
        val feedImage = attr(header, "itunes:image", "href")
            ?: tagText(header, "url")
            ?: attr(header, "media:thumbnail", "url")
        return RssFeed(
            title = NewsText.decodeEntities(tagText(header, "title").orEmpty()),
            author = (tagText(header, "itunes:author") ?: tagText(header, "author") ?: tagText(header, "managingEditor"))
                ?.let { NewsText.plain(it) }?.takeIf { it.isNotBlank() },
            description = NewsText.plain(tagText(header, "description") ?: tagText(header, "itunes:summary") ?: tagText(header, "subtitle").orEmpty()),
            imageUrl = feedImage?.trim()?.takeIf { it.startsWith("http") },
            items = blocks.mapNotNull { item(it, isAtom) },
        )
    }

    private fun item(block: String, atom: Boolean): RssItem? {
        val title = NewsText.plain(tagText(block, "title").orEmpty())
        if (title.isBlank()) return null
        val link = if (atom) atomLink(block) else tagText(block, "link")?.trim()?.takeIf { it.startsWith("http") }
        val guid = tagText(block, if (atom) "id" else "guid")?.trim()
        val description = tagText(block, "description") ?: tagText(block, "summary") ?: tagText(block, "itunes:summary").orEmpty()
        val content = tagText(block, "content:encoded") ?: if (atom) tagText(block, "content") else null
        val enclosureUrl = attr(block, "enclosure", "url")
        val enclosureType = attr(block, "enclosure", "type")
        val published = tagText(block, "pubDate") ?: tagText(block, "published") ?: tagText(block, "updated") ?: tagText(block, "dc:date")
        val author = (tagText(block, "dc:creator") ?: tagText(block, "author") ?: tagText(block, "itunes:author"))
            ?.let { NewsText.plain(it) }?.takeIf { it.isNotBlank() }
        return RssItem(
            title = title,
            link = link,
            guid = guid,
            summary = NewsText.plain(description).take(600),
            contentHtml = content,
            publishedMs = NewsText.parseDateMs(published),
            author = author,
            imageUrl = imageOf(block, description, content, enclosureUrl, enclosureType),
            categories = allTags(block, "category").map { NewsText.plain(it) }.filter { it.isNotBlank() },
            enclosureUrl = enclosureUrl?.let { NewsText.decodeEntities(it).trim() },
            enclosureType = enclosureType,
            duration = tagText(block, "itunes:duration")?.trim(),
        )
    }

    private fun imageOf(block: String, description: String, content: String?, enclosureUrl: String?, enclosureType: String?): String? {
        val candidates = buildList {
            allAttrs(block, "media:content", "url").forEach { add(it) }
            allAttrs(block, "media:thumbnail", "url").forEach { add(it) }
            if (enclosureUrl != null && (enclosureType?.startsWith("image") == true || IMAGE_EXT.containsMatchIn(enclosureUrl))) add(enclosureUrl)
            attr(block, "itunes:image", "href")?.let { add(it) }
            IMG_SRC.find(content.orEmpty())?.groupValues?.get(1)?.let { add(it) }
            IMG_SRC.find(NewsText.unwrapCdata(description))?.groupValues?.get(1)?.let { add(it) }
        }
        return candidates.map { NewsText.decodeEntities(it).trim() }
            .firstOrNull { it.startsWith("http") && IMAGE_EXT.containsMatchIn(it) }
            ?: candidates.map { NewsText.decodeEntities(it).trim() }.firstOrNull { it.startsWith("http") && !it.endsWith(".mp4") && !it.endsWith(".mp3") }
    }

    private fun atomLink(block: String): String? {
        var from = 0
        var fallback: String? = null
        while (true) {
            val open = indexOfTag(block, "link", from)
            if (open < 0) return fallback
            val end = block.indexOf('>', open)
            if (end < 0) return fallback
            val tag = block.substring(open, end)
            val href = Regex("href=[\"']([^\"']+)[\"']").find(tag)?.groupValues?.get(1)
            val rel = Regex("rel=[\"']([^\"']+)[\"']").find(tag)?.groupValues?.get(1)
            if (href != null && (rel == null || rel == "alternate")) return NewsText.decodeEntities(href)
            if (href != null && fallback == null) fallback = NewsText.decodeEntities(href)
            from = end
        }
    }

    // ---- scanning helpers -----------------------------------------------------------------------

    /** The index of "<name" when it is a whole tag name (not a prefix of a longer one), else -1. */
    private fun indexOfTag(s: String, name: String, from: Int): Int {
        var i = from
        while (true) {
            val at = s.indexOf("<$name", i, ignoreCase = true)
            if (at < 0) return -1
            val next = s.getOrNull(at + name.length + 1)
            if (next == null || next == '>' || next == '/' || next.isWhitespace()) return at
            i = at + 1
        }
    }

    private fun blocks(xml: String, tag: String, max: Int): List<String> {
        val out = ArrayList<String>()
        var from = 0
        while (out.size < max) {
            val open = indexOfTag(xml, tag, from)
            if (open < 0) break
            val close = xml.indexOf("</$tag>", open, ignoreCase = true)
            if (close < 0) break
            out.add(xml.substring(open, close))
            from = close + tag.length + 3
        }
        return out
    }

    private fun tagText(s: String, name: String): String? {
        val open = indexOfTag(s, name, 0)
        if (open < 0) return null
        val gt = s.indexOf('>', open)
        if (gt < 0 || s[gt - 1] == '/') return null
        val close = s.indexOf("</$name>", gt, ignoreCase = true)
        if (close < 0) return null
        return NewsText.unwrapCdata(s.substring(gt + 1, close)).ifEmpty { null }
    }

    private fun allTags(s: String, name: String): List<String> {
        val out = ArrayList<String>()
        var from = 0
        while (out.size < 12) {
            val open = indexOfTag(s, name, from)
            if (open < 0) break
            val gt = s.indexOf('>', open)
            if (gt < 0) break
            val close = s.indexOf("</$name>", gt, ignoreCase = true)
            if (close < 0) break
            out.add(NewsText.unwrapCdata(s.substring(gt + 1, close)))
            from = close
        }
        return out
    }

    private fun attr(s: String, tag: String, attribute: String): String? = allAttrs(s, tag, attribute).firstOrNull()

    private fun allAttrs(s: String, tag: String, attribute: String): List<String> {
        val out = ArrayList<String>()
        var from = 0
        val pattern = Regex("(?i)(?<![A-Za-z:-])$attribute\\s*=\\s*[\"']([^\"']*)[\"']")
        while (out.size < 8) {
            val open = indexOfTag(s, tag, from)
            if (open < 0) break
            val gt = s.indexOf('>', open)
            if (gt < 0) break
            pattern.find(s.substring(open, gt))?.groupValues?.get(1)?.takeIf { it.isNotBlank() }?.let { out.add(it) }
            from = gt
        }
        return out
    }
}
