package com.nuvio.app.features.music.data.lyrics


/**
 * Apple Music's word-timed lyric format.
 *
 * A document is `<p>` per sung line, each holding one `<span>` per syllable
 * with its own `begin`/`end`:
 *
 * ```xml
 * <p begin="27.395" end="28.960" ttm:agent="v1">
 *   <span begin="27.395" end="27.549">I</span>
 *   <span begin="27.549" end="27.740">been</span>
 * </p>
 * ```
 *
 * Syllables of one word are written as adjacent spans with no whitespace
 * between them ("e" + "nough"), so whitespace — not the span boundary — is
 * what separates words. That is the whole trick to reading this format.
 *
 * Parsed with DOM rather than a pull parser so this stays plain JVM code and
 * can be unit tested off-device.
 */
object TtmlLyrics {

    /**
     * Roles that are not this line at all: translations and romanisations are
     * alternate renderings of the same words and would double the line up.
     */
    private val SKIPPED_ROLES = setOf("x-translation", "x-roman")

    /**
     * The answering vocal. It is this line, sung by a second voice over the
     * lead and often past the *next* line's stamp, so it is collected apart
     * and carried as [LyricLine.background] — run into the lead's own words it
     * dragged the sweep along and the tail of the line was skipped.
     */
    private const val BACKGROUND_ROLE = "x-bg"

    fun parse(ttml: String): List<LyricLine> = runCatching {
        val document = MiniXml.parse(ttml)
        val paragraphs = document.getElementsByTagName("p")

        // The line and the voice that sang it, kept together: which side of the
        // panel a line belongs on can only be worked out once they all are.
        val sung = ArrayList<Pair<LyricLine, String?>>(paragraphs.length)
        for (i in 0 until paragraphs.length) {
            val paragraph = paragraphs.item(i) as? XmlElement ?: continue
            val line = lineFrom(paragraph) ?: continue
            sung += line to paragraph.qualified("ttm:agent").takeIf { it.isNotEmpty() }
        }
        sung.sortBy { it.first.timeMs }

        val sides = lineAlignments(sung.map { it.second }, agentTypes(document))
        sung.mapIndexed { index, (line, _) -> line.copy(alignment = sides[index]) }
            .withInstrumentalGaps()
    }.getOrDefault(emptyList())

    /**
     * The `<ttm:agent>` declarations in the head, as id to type — `person`,
     * `group`, `other`. A document that names its voices without describing
     * them is common enough that [lineAlignments] guesses rather than gives up.
     */
    private fun agentTypes(document: XmlDocument): Map<String, String> {
        val agents = document.getElementsByTagName("ttm:agent")
            .takeIf { it.length > 0 }
            ?: document.getElementsByTagName("agent")
        val types = HashMap<String, String>(agents.length)
        for (i in 0 until agents.length) {
            val agent = agents.item(i) as? XmlElement ?: continue
            val id = agent.qualified("xml:id")
            val type = agent.getAttribute("type")
            if (id.isNotEmpty() && type.isNotEmpty()) types[id] = type
        }
        return types
    }

    /**
     * An attribute named with a prefix, read the same way on both DOM
     * implementations this runs on.
     *
     * The parser above is deliberately not namespace-aware, and the two
     * implementations disagree about what that makes an attribute called. The
     * JVM's keeps `ttm:agent` whole, so [XmlElement.getAttribute] finds it.
     * Android's, built on Expat, files it under its local name — so the same
     * call returns `""` on a phone.
     *
     * That difference is invisible from a unit test, which runs on the JVM: the
     * duet layout passed every test and shipped with every line drawn down the
     * left, because on the device no paragraph appeared to name a voice at all.
     * The same applies to `ttm:role`, which is how the answering vocal is told
     * apart from the lead, and to `xml:id`.
     *
     * So: ask for the whole name, and if nothing comes back, look through the
     * attributes for one that ends in it.
     */
    private fun XmlElement.qualified(name: String): String {
        getAttribute(name).takeIf { it.isNotEmpty() }?.let { return it }
        val local = name.substringAfter(':')
        val found = attributes ?: return ""
        for (i in 0 until found.length) {
            val attribute = found.item(i) ?: continue
            if (attribute.nodeName == name ||
                attribute.nodeName == local ||
                attribute.localName == local
            ) {
                return attribute.nodeValue.orEmpty()
            }
        }
        return ""
    }

    private fun lineFrom(paragraph: XmlElement): LyricLine? {
        val pieces = mutableListOf<Piece>()
        val backingPieces = mutableListOf<Piece>()
        collect(paragraph, pieces, backingPieces)
        val words = mergeIntoWords(pieces)
        val backing = mergeIntoWords(backingPieces).takeIf { it.isNotEmpty() }?.let {
            LyricLine(
                timeMs = it.first().startMs,
                text = it.joinToString(" ") { word -> word.text },
                words = it,
            )
        }

        if (words.isEmpty()) {
            // Line-synced TTML: a <p> with a stamp and bare text, no spans.
            // textContent is the whole paragraph, backing vocal included, so
            // there is nothing here to hang underneath — the bracket in the
            // text is all the separation the document gave.
            val text = paragraph.textContent?.trim().orEmpty()
            val begin = time(paragraph.getAttribute("begin")) ?: return null
            if (text.isEmpty()) return null
            // The paragraph's own end is the only thing that says when the
            // singing stops, so carry it — a break can't be found without it.
            val end = time(paragraph.getAttribute("end"))?.takeIf { it > begin }
            return LyricLine(timeMs = begin, text = text, sungUntilMs = end)
        }

        // Prefer the paragraph's own stamp: Apple sets it a hair before the
        // first syllable on lines that open with a soft consonant, and that
        // lead-in is when the line should appear.
        val begin = time(paragraph.getAttribute("begin")) ?: words.first().startMs
        return LyricLine(
            timeMs = minOf(begin, words.first().startMs),
            text = words.joinToString(" ") { it.text },
            words = words,
            background = backing,
        )
    }

    /**
     * Flattens a paragraph into timed spans and the whitespace between them.
     * Nested spans (Apple wraps background vocals, and occasionally whole
     * phrases, in an outer timed span) recurse to their leaves, so only the
     * innermost timings — the ones actually per-syllable — survive.
     *
     * Spans marked [BACKGROUND_ROLE] and everything under them go to
     * [backing] instead of [out], which is what keeps the two voices apart.
     */
    private fun collect(node: XmlNode, out: MutableList<Piece>, backing: MutableList<Piece>) {
        val children = node.childNodes
        for (i in 0 until children.length) {
            when (val child = children.item(i)) {
                is XmlElement -> {
                    val role = child.qualified("ttm:role")
                    if (role in SKIPPED_ROLES) continue
                    // Inside a backing span every leaf is backing, so the sink
                    // switches for the whole of that subtree — whether the
                    // span holds its own syllables or is a single timed leaf.
                    val sink = if (role == BACKGROUND_ROLE) backing else out
                    val begin = time(child.getAttribute("begin"))
                    val end = time(child.getAttribute("end"))
                    if (begin != null && end != null && !hasTimedChild(child)) {
                        sink += Piece.Timed(child.textContent.orEmpty(), begin, end)
                    } else {
                        collect(child, sink, backing)
                    }
                }
                else -> if (child != null && child.nodeType == XmlNode.TEXT_NODE) {
                    val text = child.textContent.orEmpty()
                    if (text.isNotEmpty()) out += Piece.Text(text)
                }
            }
        }
    }

    private fun hasTimedChild(element: XmlElement): Boolean {
        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i) as? XmlElement ?: continue
            if (child.getAttribute("begin").isNotEmpty() || hasTimedChild(child)) return true
        }
        return false
    }

    /**
     * Glues syllables back into words. A word ends at the first whitespace
     * after it — whether that whitespace is a text node between two spans or
     * part of a span's own text — and its span runs from the first syllable's
     * start to the last one's end. Each span's own timing is kept on the word
     * as a [LyricSyllable], which is what the sweep follows.
     */
    private fun mergeIntoWords(pieces: List<Piece>): List<LyricWord> {
        val words = mutableListOf<LyricWord>()
        val current = StringBuilder()
        val syllables = mutableListOf<LyricSyllable>()
        var start = 0L
        var end = 0L
        // Untimed text is punctuation hanging off a span, or a line that was
        // never word-timed at all. Either way it can't carry a word of its
        // own — a word needs a span to get its timing from.
        var timed = false

        fun flush() {
            val text = current.toString().trim()
            current.setLength(0)
            if (text.isNotEmpty() && timed) {
                words += LyricWord(start, end, text, LyricSyllable.normalized(syllables, text.length))
            }
            syllables.clear()
            timed = false
        }

        pieces.forEach { piece ->
            when (piece) {
                is Piece.Text -> when {
                    piece.text.isBlank() -> flush()
                    // Trailing punctuation belongs to the word it follows;
                    // anything before the first span has no timing to join.
                    timed -> current.append(piece.text)
                    else -> Unit
                }
                is Piece.Timed -> {
                    if (piece.text.isBlank()) return@forEach
                    // Leading whitespace closes off whatever came before it.
                    if (piece.text.first().isWhitespace()) flush()
                    if (current.isEmpty()) start = piece.start
                    val from = current.length
                    current.append(piece.text.trim())
                    syllables += LyricSyllable(piece.start, piece.end, from, current.length)
                    end = piece.end
                    timed = true
                    if (piece.text.last().isWhitespace()) flush()
                }
            }
        }
        flush()
        return words
    }

    /**
     * TTML clock values: `27.395`, `1:05.20`, `1:02:03.4`, or a plain number
     * with a `s`/`ms` unit. Returned in milliseconds.
     */
    fun time(value: String?): Long? {
        val raw = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (raw.endsWith("ms")) return raw.dropLast(2).toDoubleOrNull()?.toLong()
        val stripped = raw.removeSuffix("s")
        val parts = stripped.split(':')
        val seconds = when (parts.size) {
            1 -> parts[0].toDoubleOrNull()
            2 -> parts[0].toDoubleOrNull()?.let { m -> parts[1].toDoubleOrNull()?.let { m * 60 + it } }
            3 -> parts[0].toDoubleOrNull()?.let { h ->
                parts[1].toDoubleOrNull()?.let { m ->
                    parts[2].toDoubleOrNull()?.let { h * 3600 + m * 60 + it }
                }
            }
            else -> null
        } ?: return null
        return (seconds * 1000).toLong()
    }

    private sealed interface Piece {
        data class Text(val text: String) : Piece
        data class Timed(val text: String, val start: Long, val end: Long) : Piece
    }
}
