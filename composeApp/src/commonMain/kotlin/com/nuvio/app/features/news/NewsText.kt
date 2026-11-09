package com.nuvio.app.features.news

internal object NewsText {
    private val NAMED = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ",
        "rsquo" to "’", "lsquo" to "‘", "ldquo" to "“", "rdquo" to "”",
        "ndash" to "–", "mdash" to "—", "hellip" to "…", "middot" to "·",
        "eacute" to "é", "egrave" to "è", "aacute" to "á", "oacute" to "ó",
        "iacute" to "í", "uacute" to "ú", "ntilde" to "ñ", "uuml" to "ü",
        "ouml" to "ö", "auml" to "ä", "ccedil" to "ç", "copy" to "©",
    )
    private val ENTITY = Regex("&(#[xX][0-9a-fA-F]+|#[0-9]+|[a-zA-Z]+);")
    private val TAG = Regex("<[^>]+>")
    private val SPACES = Regex("\\s+")
    private val BREAKS = Regex("(?i)<br\\s*/?>|</p\\s*>|</div\\s*>|</li\\s*>")

    fun decodeEntities(s: String): String {
        if (s.indexOf('&') < 0) return s
        return ENTITY.replace(s) { m ->
            val body = m.groupValues[1]
            when {
                body.startsWith("#x") || body.startsWith("#X") -> body.substring(2).toIntOrNull(16)?.let(::codePoint)
                body.startsWith("#") -> body.substring(1).toIntOrNull()?.let(::codePoint)
                else -> NAMED[body.lowercase()]
            } ?: m.value
        }
    }

    private fun codePoint(code: Int): String? = when {
        code <= 0 || code > 0x10FFFF -> null
        code < 0x10000 -> code.toChar().toString()
        else -> {
            val c = code - 0x10000
            charArrayOf(((c shr 10) + 0xD800).toChar(), ((c and 0x3FF) + 0xDC00).toChar()).concatToString()
        }
    }

    fun unwrapCdata(s: String): String {
        val t = s.trim()
        if (!t.startsWith("<![CDATA[")) return t
        val end = t.lastIndexOf("]]>")
        return if (end > 8) t.substring(9, end).trim() else t.removePrefix("<![CDATA[")
    }

    /** HTML to one line of plain text: tags dropped, entities decoded, whitespace collapsed. */
    fun plain(html: String): String {
        val noBlocks = removeBlocks(html, listOf("script", "style", "noscript", "svg"))
        val text = TAG.replace(BREAKS.replace(noBlocks, " "), "")
        return SPACES.replace(decodeEntities(text), " ").trim()
    }

    fun removeBlocks(html: String, tags: List<String>): String {
        var out = html
        for (tag in tags) {
            var from = 0
            val sb = StringBuilder()
            while (true) {
                val open = out.indexOf("<$tag", from, ignoreCase = true)
                if (open < 0) { sb.appendRange(out, from, out.length); break }
                val after = out.getOrNull(open + tag.length + 1)
                if (after != null && !(after == '>' || after == ' ' || after == '/' || after == '\n' || after == '\t')) {
                    sb.appendRange(out, from, open + tag.length + 1)
                    from = open + tag.length + 1
                    continue
                }
                val close = out.indexOf("</$tag>", open, ignoreCase = true)
                sb.appendRange(out, from, open)
                if (close < 0) { from = out.length; break }
                from = close + tag.length + 3
            }
            out = sb.toString()
        }
        return out
    }

    // ---- dates ----------------------------------------------------------------------------------

    private val MONTHS = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
    private val RFC822 = Regex("(\\d{1,2})\\s+([A-Za-z]{3})[A-Za-z]*\\s+(\\d{2,4})\\s+(\\d{1,2}):(\\d{2})(?::(\\d{2}))?\\s*([+-]\\d{4}|[A-Za-z]{1,4})?")
    private val ISO = Regex("(\\d{4})-(\\d{2})-(\\d{2})[T ](\\d{2}):(\\d{2})(?::(\\d{2}))?(?:\\.\\d+)?\\s*(Z|[+-]\\d{2}:?\\d{2})?")
    private val ZONES = mapOf(
        "GMT" to 0, "UTC" to 0, "UT" to 0, "Z" to 0, "BST" to 60,
        "EST" to -300, "EDT" to -240, "CST" to -360, "CDT" to -300,
        "MST" to -420, "MDT" to -360, "PST" to -480, "PDT" to -420,
    )

    /** Parses an RSS (RFC 822) or Atom (ISO 8601) date to epoch milliseconds, or 0 when it can't. */
    fun parseDateMs(raw: String?): Long {
        val s = raw?.trim().orEmpty()
        if (s.isEmpty()) return 0L
        ISO.find(s)?.let { m ->
            val (y, mo, d, h, mi) = m.destructured
            val sec = m.groupValues[6].toIntOrNull() ?: 0
            val offset = offsetMinutes(m.groupValues[7])
            return epochMs(y.toInt(), mo.toInt(), d.toInt(), h.toInt(), mi.toInt(), sec, offset)
        }
        RFC822.find(s)?.let { m ->
            val month = MONTHS.indexOf(m.groupValues[2].lowercase()) + 1
            if (month == 0) return 0L
            var year = m.groupValues[3].toInt()
            if (year < 100) year += if (year < 70) 2000 else 1900
            val sec = m.groupValues[6].toIntOrNull() ?: 0
            val offset = offsetMinutes(m.groupValues[7])
            return epochMs(year, month, m.groupValues[1].toInt(), m.groupValues[4].toInt(), m.groupValues[5].toInt(), sec, offset)
        }
        return 0L
    }

    private fun offsetMinutes(zone: String): Int {
        if (zone.isBlank()) return 0
        ZONES[zone.uppercase()]?.let { return it }
        val sign = if (zone.startsWith("-")) -1 else 1
        val digits = zone.filter { it.isDigit() }
        if (digits.length < 4) return 0
        return sign * (digits.substring(0, 2).toInt() * 60 + digits.substring(2, 4).toInt())
    }

    private fun epochMs(y: Int, mo: Int, d: Int, h: Int, mi: Int, s: Int, offsetMin: Int): Long {
        val days = daysFromCivil(y, mo, d)
        return ((days * 24 + h) * 60 + mi - offsetMin) * 60_000L + s * 1000L
    }

    private fun daysFromCivil(year: Int, month: Int, day: Int): Long {
        val y = (if (month <= 2) year - 1 else year).toLong()
        val era = (if (y >= 0) y else y - 399) / 400
        val yoe = y - era * 400
        val doy = (153 * (month + (if (month > 2) -3 else 9)) + 2) / 5 + day - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146097 + doe - 719468
    }

    private fun civilFromDays(daysSinceEpoch: Long): Triple<Int, Int, Int> {
        val z = daysSinceEpoch + 719468
        val era = (if (z >= 0) z else z - 146096) / 146097
        val doe = z - era * 146097
        val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
        val y = yoe + era * 400
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val d = (doy - (153 * mp + 2) / 5 + 1).toInt()
        val m = (if (mp < 10) mp + 3 else mp - 9).toInt()
        return Triple((if (m <= 2) y + 1 else y).toInt(), m, d)
    }

    /** "5m ago", "3h ago", "Yesterday", "3d ago", then "Nov 5". Empty when the date is unknown or in the future. */
    fun relative(publishedMs: Long, nowMs: Long): String {
        if (publishedMs <= 0L) return ""
        val diff = nowMs - publishedMs
        if (diff < 0) return ""
        val minutes = diff / 60_000L
        val hours = minutes / 60
        val days = hours / 24
        return when {
            minutes < 1 -> "Just now"
            minutes < 60 -> "${minutes}m ago"
            hours < 24 -> "${hours}h ago"
            days == 1L -> "Yesterday"
            days < 7 -> "${days}d ago"
            else -> shortDate(publishedMs)
        }
    }

    /** "NOV 4, 2026". */
    fun longDate(ms: Long): String {
        if (ms <= 0L) return ""
        val (y, m, d) = civilFromDays(ms / 86_400_000L)
        return "${MONTHS[m - 1].uppercase()} $d, $y"
    }

    fun shortDate(ms: Long): String {
        if (ms <= 0L) return ""
        val (_, m, d) = civilFromDays(ms / 86_400_000L)
        return "${MONTHS[m - 1].replaceFirstChar { it.uppercase() }} $d"
    }
}
