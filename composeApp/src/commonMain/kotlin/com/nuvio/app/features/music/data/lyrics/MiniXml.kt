package com.nuvio.app.features.music.data.lyrics

/**
 * A small, forgiving XML reader with the handful of DOM-style members the TTML lyric parser uses.
 * It never fetches anything (no DTDs, no external entities); DOCTYPE and processing instructions
 * are skipped, and only the five predefined entities plus numeric references are expanded.
 */
internal open class XmlNode(val nodeType: Int) {
    var textContentValue: String = ""
    val childList = ArrayList<XmlNode>()

    val childNodes: XmlNodeList get() = XmlNodeList(childList)

    open val textContent: String?
        get() = if (nodeType == TEXT_NODE) textContentValue else buildString { collect(this@XmlNode, this) }

    private fun collect(node: XmlNode, out: StringBuilder) {
        if (node.nodeType == TEXT_NODE) out.append(node.textContentValue) else node.childList.forEach { collect(it, out) }
    }

    companion object {
        const val ELEMENT_NODE = 1
        const val TEXT_NODE = 3
    }
}

internal class XmlAttr(val nodeName: String, val nodeValue: String?) {
    val localName: String get() = nodeName.substringAfter(':')
}

internal class XmlAttributes(private val items: List<XmlAttr>) {
    val length: Int get() = items.size
    fun item(index: Int): XmlAttr? = items.getOrNull(index)
}

internal class XmlNodeList(private val nodes: List<XmlNode>) {
    val length: Int get() = nodes.size
    fun item(index: Int): XmlNode? = nodes.getOrNull(index)
}

internal class XmlElement(val tagName: String, private val attrs: List<XmlAttr>) : XmlNode(ELEMENT_NODE) {
    val attributes: XmlAttributes? get() = XmlAttributes(attrs)

    /** The attribute's value, or "" when absent (DOM behaviour). */
    fun getAttribute(name: String): String = attrs.firstOrNull { it.nodeName == name }?.nodeValue.orEmpty()
}

internal class XmlDocument(val root: XmlElement?) {
    fun getElementsByTagName(name: String): XmlNodeList {
        val found = ArrayList<XmlNode>()
        fun walk(node: XmlNode) {
            if (node is XmlElement && node.tagName == name) found += node
            node.childList.forEach { walk(it) }
        }
        root?.let { walk(it) }
        return XmlNodeList(found)
    }
}

internal object MiniXml {
    fun parse(text: String): XmlDocument {
        val reader = Reader(text)
        return XmlDocument(reader.readDocument())
    }

    private class Reader(private val s: String) {
        private var i = 0

        fun readDocument(): XmlElement? {
            var root: XmlElement? = null
            while (i < s.length) {
                skipMisc()
                if (i >= s.length) break
                if (s[i] == '<') {
                    val el = readElement() ?: break
                    if (root == null) root = el
                } else i++
            }
            return root
        }

        private fun skipMisc() {
            while (i < s.length) {
                when {
                    s.startsWith("<?", i) -> skipTo("?>")
                    s.startsWith("<!--", i) -> skipTo("-->")
                    s.startsWith("<!DOCTYPE", i) || s.startsWith("<!doctype", i) -> skipDoctype()
                    s[i].isWhitespace() -> i++
                    else -> return
                }
            }
        }

        private fun skipTo(end: String) {
            val at = s.indexOf(end, i)
            i = if (at < 0) s.length else at + end.length
        }

        private fun skipDoctype() {
            var depth = 0
            while (i < s.length) {
                val c = s[i++]
                if (c == '[') depth++ else if (c == ']') depth-- else if (c == '>' && depth <= 0) return
            }
        }

        private fun readElement(): XmlElement? {
            if (s[i] != '<') return null
            i++
            val name = readName()
            if (name.isEmpty()) return null
            val attrs = ArrayList<XmlAttr>()
            while (i < s.length) {
                skipSpaces()
                if (i >= s.length) return null
                val c = s[i]
                if (c == '/') { // self-closing
                    skipTo(">")
                    return XmlElement(name, attrs)
                }
                if (c == '>') { i++; break }
                val attrName = readName()
                if (attrName.isEmpty()) { i++; continue }
                skipSpaces()
                var value = ""
                if (i < s.length && s[i] == '=') {
                    i++; skipSpaces()
                    val quote = s.getOrNull(i)
                    if (quote == '"' || quote == '\'') {
                        i++
                        val end = s.indexOf(quote, i).let { if (it < 0) s.length else it }
                        value = decode(s.substring(i, end))
                        i = minOf(end + 1, s.length)
                    }
                }
                attrs += XmlAttr(attrName, value)
            }
            val element = XmlElement(name, attrs)
            readChildren(element)
            return element
        }

        private fun readChildren(parent: XmlElement) {
            val text = StringBuilder()
            fun flush() {
                if (text.isNotEmpty()) {
                    parent.childList += XmlNode(XmlNode.TEXT_NODE).also { it.textContentValue = decode(text.toString()) }
                    text.clear()
                }
            }
            while (i < s.length) {
                if (s[i] != '<') { text.append(s[i++]); continue }
                when {
                    s.startsWith("</", i) -> { flush(); skipTo(">"); return }
                    s.startsWith("<!--", i) -> skipTo("-->")
                    s.startsWith("<![CDATA[", i) -> {
                        val end = s.indexOf("]]>", i).let { if (it < 0) s.length else it }
                        flush()
                        parent.childList += XmlNode(XmlNode.TEXT_NODE).also { it.textContentValue = s.substring(i + 9, end) }
                        i = minOf(end + 3, s.length)
                    }
                    s.startsWith("<?", i) -> skipTo("?>")
                    else -> { flush(); readElement()?.let { parent.childList += it } ?: run { i++ } }
                }
            }
            flush()
        }

        private fun readName(): String {
            val start = i
            while (i < s.length && !s[i].isWhitespace() && s[i] != '=' && s[i] != '>' && s[i] != '/' ) i++
            return s.substring(start, i)
        }

        private fun skipSpaces() { while (i < s.length && s[i].isWhitespace()) i++ }

        private fun decode(raw: String): String {
            if ('&' !in raw) return raw
            val out = StringBuilder(raw.length)
            var k = 0
            while (k < raw.length) {
                val c = raw[k]
                if (c != '&') { out.append(c); k++; continue }
                val semi = raw.indexOf(';', k)
                if (semi < 0 || semi - k > 10) { out.append(c); k++; continue }
                val entity = raw.substring(k + 1, semi)
                val replacement = when {
                    entity == "amp" -> "&"
                    entity == "lt" -> "<"
                    entity == "gt" -> ">"
                    entity == "quot" -> "\""
                    entity == "apos" -> "'"
                    entity.startsWith("#x") || entity.startsWith("#X") -> entity.drop(2).toIntOrNull(16)?.let { codePoint(it) }
                    entity.startsWith("#") -> entity.drop(1).toIntOrNull()?.let { codePoint(it) }
                    else -> null
                }
                if (replacement == null) { out.append(c); k++ } else { out.append(replacement); k = semi + 1 }
            }
            return out.toString()
        }

        private fun codePoint(cp: Int): String =
            if (cp < 0x10000) cp.toChar().toString() else {
                val v = cp - 0x10000
                charArrayOf((0xD800 + (v shr 10)).toChar(), (0xDC00 + (v and 0x3FF)).toChar()).concatToString()
            }
    }
}
