package com.nuvio.app.features.livetv

import com.nuvio.app.core.network.createApiHttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.readAvailable

internal const val LIVE_TV_EPG_PAST_WINDOW_MILLIS = 3L * 60L * 60L * 1000L
// The guide screens show 6-12 hours; a day ahead keeps the in-memory guide about half the size of two days.
internal const val LIVE_TV_EPG_FUTURE_WINDOW_MILLIS = 24L * 60L * 60L * 1000L
private const val LIVE_TV_EPG_DEFAULT_DURATION_MILLIS = 60L * 60L * 1000L
private const val LIVE_TV_EPG_MAX_DOWNLOAD_BYTES = 64 * 1024 * 1024
private const val LIVE_TV_EPG_MAX_XML_BYTES = 160 * 1024 * 1024
/** Uncompressed guides are parsed as they download, so only the read is bounded, not memory. */
private const val LIVE_TV_EPG_MAX_STREAM_BYTES = 1024L * 1024L * 1024L
private const val LIVE_TV_EPG_MAX_DESCRIPTION_CHARS = 160
private const val LIVE_TV_EPG_MAX_PROGRAMS_PER_CHANNEL = 300
private const val LIVE_TV_EPG_MAX_ELEMENT_BYTES = 1024 * 1024

private val epgRequestHeaders = mapOf(
    "Accept" to "application/xml, text/xml, application/gzip, application/octet-stream, */*",
    "User-Agent" to "Nuvio/1.0",
)

private val epgHttpClient by lazy { createApiHttpClient().config { followRedirects = true } }

internal data class LiveTvEpgGuide(
    val programsByEpgKey: Map<String, List<LiveTvEpgProgram>> = emptyMap(),
    val epgKeysByName: Map<String, String> = emptyMap(),
    val programsByChannelId: Map<String, List<LiveTvEpgProgram>> = emptyMap(),
)

/**
 * Downloads and parses an XMLTV guide without ever holding the whole document: a provider's guide is often
 * hundreds of megabytes, and reading it into one string got the app killed for memory on iPhone.
 */
internal suspend fun fetchXmltvGuide(url: String, nowMillis: Long): LiveTvEpgGuide {
    val parser = XmltvStreamParser(
        windowStartMillis = nowMillis - LIVE_TV_EPG_PAST_WINDOW_MILLIS,
        windowEndMillis = nowMillis + LIVE_TV_EPG_FUTURE_WINDOW_MILLIS,
    )
    val sink = GuideSink(parser)
    if (!liveTvStreamGuideFromDisk(url, epgRequestHeaders, sink::accept)) {
        epgHttpClient.prepareGet(url) {
            epgRequestHeaders.forEach { (name, value) -> header(name, value) }
            timeout {
                requestTimeoutMillis = 10L * 60L * 1000L
                socketTimeoutMillis = 60L * 1000L
            }
        }.execute { response ->
            require(response.status.value in 200..299) { "Guide request failed with HTTP ${response.status.value}" }
            val channel = response.bodyAsChannel()
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = channel.readAvailable(buffer, 0, buffer.size)
                if (count == -1) break
                if (count == 0) continue
                if (!sink.accept(buffer, count)) break
            }
        }
    }
    sink.finish()
    return parser.finish()
}

/**
 * Takes the guide's bytes as they arrive. Plain XML goes straight to the parser; a gzipped guide is collected
 * (it is small) and unpacked at the end.
 */
private class GuideSink(private val parser: XmltvStreamParser) {
    private var gzipChunks: MutableList<ByteArray>? = null
    private var sniffed = false
    private var total = 0L

    /** Returns false once enough has been read. */
    fun accept(bytes: ByteArray, count: Int): Boolean {
        total += count
        if (!sniffed) {
            sniffed = true
            if (count >= 2 && bytes[0] == 0x1F.toByte() && bytes[1] == 0x8B.toByte()) gzipChunks = mutableListOf()
        }
        val compressed = gzipChunks
        if (compressed != null) {
            require(total <= LIVE_TV_EPG_MAX_DOWNLOAD_BYTES) { "Guide is too large to load." }
            compressed += bytes.copyOf(count)
            return true
        }
        parser.feed(bytes, 0, count)
        return total < LIVE_TV_EPG_MAX_STREAM_BYTES
    }

    fun finish() {
        val compressed = gzipChunks ?: return
        val joined = ByteArray(total.toInt())
        var offset = 0
        compressed.forEach { chunk ->
            chunk.copyInto(joined, offset)
            offset += chunk.size
        }
        compressed.clear()
        val xml = liveTvGunzip(joined, LIVE_TV_EPG_MAX_XML_BYTES)
        var position = 0
        while (position < xml.size) {
            val length = minOf(64 * 1024, xml.size - position)
            parser.feed(xml, position, length)
            position += length
        }
    }
}

internal fun parseXmltvGuide(
    xml: String,
    windowStartMillis: Long,
    windowEndMillis: Long,
): LiveTvEpgGuide {
    val parser = XmltvStreamParser(windowStartMillis, windowEndMillis)
    val bytes = xml.encodeToByteArray()
    var position = 0
    while (position < bytes.size) {
        val length = minOf(64 * 1024, bytes.size - position)
        parser.feed(bytes, position, length)
        position += length
    }
    return parser.finish()
}

/**
 * Incremental XMLTV reader. Bytes are fed in as they arrive; each complete `<channel>` or `<programme>` element
 * is decoded on its own and everything else is dropped, so memory stays at one network chunk plus the kept guide.
 */
internal class XmltvStreamParser(
    private val windowStartMillis: Long,
    private val windowEndMillis: Long,
) {
    private var buffer = ByteArray(256 * 1024)
    private var start = 0
    private var end = 0
    private val keysByName = HashMap<String, String>()
    private val idKeysByName = HashMap<String, String>()
    private val programs = HashMap<String, MutableList<LiveTvEpgProgram>>()

    fun feed(bytes: ByteArray, offset: Int, length: Int) {
        if (length <= 0) return
        if (buffer.size - end < length) {
            val pending = end - start
            val target = if (pending + length > buffer.size) ByteArray(maxOf(buffer.size * 2, pending + length)) else buffer
            buffer.copyInto(target, 0, start, end)
            buffer = target
            start = 0
            end = pending
        }
        bytes.copyInto(buffer, end, offset, offset + length)
        end += length
        drain()
    }

    fun finish(): LiveTvEpgGuide {
        val finalized = programs
            .mapValues { (_, list) -> finalizeEpgPrograms(list, windowStartMillis) }
            .filterValues { it.isNotEmpty() }
        idKeysByName.forEach { (name, key) -> keysByName.getOrPut(name) { key } }
        return LiveTvEpgGuide(
            programsByEpgKey = finalized,
            epgKeysByName = keysByName.filterValues { it in finalized },
        )
    }

    private fun drain() {
        var position = start
        while (true) {
            val open = indexOfByte(LESS_THAN, position)
            if (open < 0) {
                start = end
                return
            }
            // Not enough bytes yet to tell which element this is.
            if (end - open < PROGRAMME_OPEN.size + 1) {
                start = open
                return
            }
            val isChannel = matchesTag(open, CHANNEL_OPEN)
            val isProgramme = !isChannel && matchesTag(open, PROGRAMME_OPEN)
            if (!isChannel && !isProgramme) {
                position = open + 1
                continue
            }
            val tagEnd = indexOfByte(GREATER_THAN, open)
            if (tagEnd < 0) {
                if (!waitFor(open)) position = open + 1 else return
                continue
            }
            val selfClosing = buffer[tagEnd - 1] == SLASH
            val closeTag = if (isChannel) CHANNEL_CLOSE else PROGRAMME_CLOSE
            val close = if (selfClosing) tagEnd else indexOfBytes(closeTag, tagEnd)
            if (close < 0) {
                if (!waitFor(open)) position = open + 1 else return
                continue
            }
            if (isChannel) readChannel(open, tagEnd, close, selfClosing) else if (!selfClosing) readProgramme(open, tagEnd, close)
            position = if (selfClosing) tagEnd + 1 else close + closeTag.size
            start = position
        }
    }

    /** Keeps an unfinished element for the next chunk; gives up on one that is implausibly large. */
    private fun waitFor(open: Int): Boolean {
        if (end - open > LIVE_TV_EPG_MAX_ELEMENT_BYTES) return false
        start = open
        return true
    }

    private fun readChannel(open: Int, tagEnd: Int, close: Int, selfClosing: Boolean) {
        val openTag = buffer.decodeToString(open, tagEnd)
        val id = readXmlAttribute(openTag, "id")?.let(::decodeXmlEntities)?.trim()?.takeIf(String::isNotBlank) ?: return
        val key = liveTvEpgKey(id)
        if (!selfClosing) {
            readXmlElementTexts(buffer.decodeToString(tagEnd + 1, close), "display-name").forEach { name ->
                normalizeEpgChannelName(name).takeIf(String::isNotBlank)?.let { keysByName.getOrPut(it) { key } }
            }
        }
        epgIdNameCandidates(id).forEach { candidate -> idKeysByName.getOrPut(candidate) { key } }
    }

    private fun readProgramme(open: Int, tagEnd: Int, close: Int) {
        // Decide from the open tag alone, so programmes outside the window never have their text decoded.
        val openTag = buffer.decodeToString(open, tagEnd)
        val startMillis = readXmlAttribute(openTag, "start")?.let(::parseXmltvTime) ?: return
        if (startMillis > windowEndMillis) return
        val stopMillis = readXmlAttribute(openTag, "stop")?.let(::parseXmltvTime)
        if (stopMillis != null && stopMillis < windowStartMillis) return
        val channel = readXmlAttribute(openTag, "channel")?.let(::decodeXmlEntities)?.trim()?.takeIf(String::isNotBlank) ?: return
        val list = programs.getOrPut(liveTvEpgKey(channel)) { mutableListOf() }
        if (list.size >= LIVE_TV_EPG_MAX_PROGRAMS_PER_CHANNEL) return
        val body = buffer.decodeToString(tagEnd + 1, close)
        val title = readXmlElementTexts(body, "title", limit = 1).firstOrNull() ?: return
        list += LiveTvEpgProgram(
            title = title,
            startMillis = startMillis,
            endMillis = stopMillis ?: -1L,
            description = readXmlElementTexts(body, "desc", limit = 1).firstOrNull()?.let(::shortenEpgDescription),
            category = readXmlElementTexts(body, "category", limit = 1).firstOrNull(),
        )
    }

    private fun matchesTag(at: Int, tag: ByteArray): Boolean {
        if (end - at < tag.size + 1) return false
        for (i in tag.indices) if (buffer[at + i] != tag[i]) return false
        val next = buffer[at + tag.size]
        return next == GREATER_THAN || next == SLASH || next == SPACE || next == TAB || next == NEW_LINE || next == CARRIAGE_RETURN
    }

    private fun indexOfByte(value: Byte, from: Int): Int {
        for (i in from until end) if (buffer[i] == value) return i
        return -1
    }

    private fun indexOfBytes(pattern: ByteArray, from: Int): Int {
        val last = end - pattern.size
        var i = from
        while (i <= last) {
            if (buffer[i] == pattern[0]) {
                var j = 1
                while (j < pattern.size && buffer[i + j] == pattern[j]) j++
                if (j == pattern.size) return i
            }
            i++
        }
        return -1
    }

    private companion object {
        const val LESS_THAN: Byte = 0x3C
        const val GREATER_THAN: Byte = 0x3E
        const val SLASH: Byte = 0x2F
        const val SPACE: Byte = 0x20
        const val TAB: Byte = 0x09
        const val NEW_LINE: Byte = 0x0A
        const val CARRIAGE_RETURN: Byte = 0x0D
        val CHANNEL_OPEN = "<channel".encodeToByteArray()
        val CHANNEL_CLOSE = "</channel>".encodeToByteArray()
        val PROGRAMME_OPEN = "<programme".encodeToByteArray()
        val PROGRAMME_CLOSE = "</programme>".encodeToByteArray()
    }
}

internal fun shortenEpgDescription(value: String): String =
    if (value.length <= LIVE_TV_EPG_MAX_DESCRIPTION_CHARS) value
    else value.take(LIVE_TV_EPG_MAX_DESCRIPTION_CHARS - 1).trimEnd() + "\u2026"

internal fun finalizeEpgPrograms(
    programs: List<LiveTvEpgProgram>,
    windowStartMillis: Long,
): List<LiveTvEpgProgram> {
    val sorted = programs.sortedBy { it.startMillis }.distinctBy { it.startMillis }
    return sorted.mapIndexed { index, program ->
        if (program.endMillis > program.startMillis) {
            program
        } else {
            val nextStart = sorted.getOrNull(index + 1)?.startMillis
            program.copy(endMillis = nextStart ?: (program.startMillis + LIVE_TV_EPG_DEFAULT_DURATION_MILLIS))
        }
    }.filter { it.endMillis >= windowStartMillis }
}

internal fun matchEpgToChannels(
    channels: List<LiveTvChannel>,
    guides: List<LiveTvEpgGuide>,
): Map<String, List<LiveTvEpgProgram>> {
    if (guides.isEmpty() || channels.isEmpty()) return emptyMap()
    val result = HashMap<String, List<LiveTvEpgProgram>>()
    channels.forEach { channel ->
        val epgKey = channel.epgId?.trim()?.takeIf(String::isNotBlank)?.let(::liveTvEpgKey)
        val nameKey = normalizeEpgChannelName(channel.name)
        val programs = guides.firstNotNullOfOrNull { guide ->
            guide.programsByChannelId[channel.id]?.takeIf { it.isNotEmpty() }
        } ?: epgKey?.let { key ->
            guides.firstNotNullOfOrNull { guide -> guide.programsByEpgKey[key] }
        } ?: epgKey?.let { key ->
            epgIdNameCandidates(key).firstNotNullOfOrNull { candidate ->
                guides.firstNotNullOfOrNull { guide -> guide.epgKeysByName[candidate]?.let(guide.programsByEpgKey::get) }
            }
        } ?: nameKey.takeIf(String::isNotBlank)?.let { name ->
            guides.firstNotNullOfOrNull { guide -> guide.epgKeysByName[name]?.let(guide.programsByEpgKey::get) }
        }
        if (!programs.isNullOrEmpty()) {
            result[channel.id] = programs
        }
    }
    return result
}

internal fun List<LiveTvEpgProgram>.currentProgram(nowMillis: Long): LiveTvEpgProgram? =
    firstOrNull { it.isAiringAt(nowMillis) }

internal fun List<LiveTvEpgProgram>.nextProgram(nowMillis: Long): LiveTvEpgProgram? =
    firstOrNull { it.startMillis >= nowMillis }

internal fun List<LiveTvEpgProgram>.upcomingPrograms(nowMillis: Long): List<LiveTvEpgProgram> =
    filter { it.endMillis > nowMillis }

internal fun liveTvEpgKey(value: String): String = value.trim().lowercase()

internal fun normalizeEpgChannelName(value: String): String {
    val withoutBrackets = StringBuilder(value.length)
    var depth = 0
    value.lowercase().forEach { char ->
        when (char) {
            '(', '[', '{' -> depth++
            ')', ']', '}' -> if (depth > 0) depth--
            else -> if (depth == 0) withoutBrackets.append(char)
        }
    }
    var name = withoutBrackets.toString().trim()
    val prefixSeparator = name.indexOfFirst { it == ':' || it == '|' }
    if (prefixSeparator in 1..5 && prefixSeparator < name.length - 1) {
        name = name.substring(prefixSeparator + 1)
    }
    val tokens = mutableListOf<String>()
    val token = StringBuilder()
    name.forEach { char ->
        if (char.isLetterOrDigit()) {
            token.append(char)
        } else if (token.isNotEmpty()) {
            tokens += token.toString()
            token.clear()
        }
    }
    if (token.isNotEmpty()) tokens += token.toString()
    val filtered = tokens.filterNot { it in epgQualityTokens }
    return (filtered.ifEmpty { tokens }).joinToString("")
}

private val epgQualityTokens = setOf(
    "hd", "fhd", "uhd", "sd", "hq", "lq", "4k", "8k", "hevc", "h264", "h265",
    "2160p", "1080p", "1080i", "720p", "576p", "480p", "50fps", "60fps", "backup", "raw",
)

private fun epgIdNameCandidates(id: String): List<String> {
    val trimmed = id.trim()
    val suffix = trimmed.substringAfterLast('.', missingDelimiterValue = "")
    val withoutCountry = if (suffix.length in 2..3 && suffix.all(Char::isLetter)) {
        trimmed.substringBeforeLast('.')
    } else {
        trimmed
    }
    return listOf(normalizeEpgChannelName(withoutCountry), normalizeEpgChannelName(trimmed))
        .filter(String::isNotBlank)
        .distinct()
}

internal fun parseXmltvTime(value: String): Long? {
    val trimmed = value.trim()
    val digits = trimmed.takeWhile(Char::isDigit)
    if (digits.length < 12) return null
    val year = digits.substring(0, 4).toIntOrNull() ?: return null
    val month = digits.substring(4, 6).toIntOrNull()?.takeIf { it in 1..12 } ?: return null
    val day = digits.substring(6, 8).toIntOrNull()?.takeIf { it in 1..31 } ?: return null
    val hour = digits.substring(8, 10).toIntOrNull()?.takeIf { it in 0..23 } ?: return null
    val minute = digits.substring(10, 12).toIntOrNull()?.takeIf { it in 0..59 } ?: return null
    val second = if (digits.length >= 14) digits.substring(12, 14).toIntOrNull()?.coerceIn(0, 59) ?: 0 else 0
    val rest = trimmed.substring(digits.length).trim()
    var offsetMinutes = 0
    if (rest.length >= 5 && (rest[0] == '+' || rest[0] == '-')) {
        val offsetDigits = rest.substring(1).filter(Char::isDigit)
        val offsetHours = offsetDigits.take(2).toIntOrNull()
        val offsetMins = offsetDigits.drop(2).take(2).toIntOrNull() ?: 0
        if (offsetHours != null) {
            offsetMinutes = (offsetHours * 60 + offsetMins) * if (rest[0] == '-') -1 else 1
        }
    }
    val epochDays = epgDaysFromCivil(year, month, day)
    val epochSeconds = epochDays * 86_400L + hour * 3_600L + minute * 60L + second - offsetMinutes * 60L
    return epochSeconds * 1000L
}

private fun epgDaysFromCivil(year: Int, month: Int, day: Int): Long {
    val y = if (month <= 2) year - 1 else year
    val era = (if (y >= 0) y else y - 399) / 400
    val yearOfEra = y - era * 400
    val dayOfYear = (153 * (if (month > 2) month - 3 else month + 9) + 2) / 5 + day - 1
    val dayOfEra = yearOfEra * 365 + yearOfEra / 4 - yearOfEra / 100 + dayOfYear
    return era.toLong() * 146_097L + dayOfEra - 719_468L
}

private fun isXmlTagBoundary(char: Char?): Boolean =
    char == null || char == '>' || char == '/' || char.isWhitespace()

private fun readXmlAttribute(tag: String, name: String): String? {
    var from = 0
    while (true) {
        val index = tag.indexOf(name, from)
        if (index < 0) return null
        var cursor = index + name.length
        val before = tag.getOrNull(index - 1)
        if (before == null || !before.isWhitespace()) {
            from = cursor
            continue
        }
        while (cursor < tag.length && tag[cursor].isWhitespace()) cursor++
        if (tag.getOrNull(cursor) != '=') {
            from = index + name.length
            continue
        }
        cursor++
        while (cursor < tag.length && tag[cursor].isWhitespace()) cursor++
        val quote = tag.getOrNull(cursor)
        if (quote != '"' && quote != '\'') {
            from = cursor
            continue
        }
        val end = tag.indexOf(quote, cursor + 1)
        if (end < 0) return null
        return tag.substring(cursor + 1, end)
    }
}

private fun readXmlElementTexts(
    body: String,
    name: String,
    limit: Int = Int.MAX_VALUE,
): List<String> {
    val results = mutableListOf<String>()
    val open = "<$name"
    val close = "</$name>"
    var index = 0
    while (results.size < limit) {
        val start = body.indexOf(open, index)
        if (start < 0) break
        if (!isXmlTagBoundary(body.getOrNull(start + open.length))) {
            index = start + open.length
            continue
        }
        val tagEnd = body.indexOf('>', start)
        if (tagEnd < 0) break
        if (body[tagEnd - 1] == '/') {
            index = tagEnd + 1
            continue
        }
        val end = body.indexOf(close, tagEnd)
        if (end < 0) break
        val text = decodeXmlText(body.substring(tagEnd + 1, end)).trim()
        if (text.isNotEmpty()) results += text
        index = end + close.length
    }
    return results
}

private fun decodeXmlText(raw: String): String {
    if (!raw.contains("<![CDATA[")) return decodeXmlEntities(raw)
    val builder = StringBuilder(raw.length)
    var index = 0
    while (index < raw.length) {
        val cdataStart = raw.indexOf("<![CDATA[", index)
        if (cdataStart < 0) {
            builder.append(decodeXmlEntities(raw.substring(index)))
            break
        }
        builder.append(decodeXmlEntities(raw.substring(index, cdataStart)))
        val contentStart = cdataStart + 9
        val cdataEnd = raw.indexOf("]]>", contentStart)
        if (cdataEnd < 0) {
            builder.append(raw.substring(contentStart))
            break
        }
        builder.appendRange(raw, contentStart, cdataEnd)
        index = cdataEnd + 3
    }
    return builder.toString()
}

internal fun decodeXmlEntities(value: String): String {
    if ('&' !in value) return value
    val builder = StringBuilder(value.length)
    var index = 0
    while (index < value.length) {
        val char = value[index]
        if (char == '&') {
            val semicolon = value.indexOf(';', index + 1)
            if (semicolon > index && semicolon - index <= 10) {
                val entity = value.substring(index + 1, semicolon)
                val decoded = when {
                    entity == "amp" -> "&"
                    entity == "lt" -> "<"
                    entity == "gt" -> ">"
                    entity == "quot" -> "\""
                    entity == "apos" -> "'"
                    entity.startsWith("#x") || entity.startsWith("#X") ->
                        entity.substring(2).toIntOrNull(16)?.let(::xmlCodePointToString)
                    entity.startsWith("#") -> entity.substring(1).toIntOrNull()?.let(::xmlCodePointToString)
                    else -> null
                }
                if (decoded != null) {
                    builder.append(decoded)
                    index = semicolon + 1
                    continue
                }
            }
        }
        builder.append(char)
        index++
    }
    return builder.toString()
}

private fun xmlCodePointToString(codePoint: Int): String? = when (codePoint) {
    in 0..0xFFFF -> codePoint.toChar().toString()
    in 0x10000..0x10FFFF -> {
        val value = codePoint - 0x10000
        charArrayOf(((value shr 10) + 0xD800).toChar(), ((value and 0x3FF) + 0xDC00).toChar()).concatToString()
    }
    else -> null
}
