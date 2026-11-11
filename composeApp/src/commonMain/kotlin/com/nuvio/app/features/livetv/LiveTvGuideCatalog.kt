package com.nuvio.app.features.livetv

/** A group of channels the user can turn on or off for their guide. */
internal class LiveTvGuideCategory(val id: String, val title: String)

internal val LIVE_TV_GUIDE_CATEGORIES = listOf(
    LiveTvGuideCategory("ca_broadcast", "Canadian Broadcast"),
    LiveTvGuideCategory("us_broadcast", "U.S. Broadcast"),
    LiveTvGuideCategory("ca_sports", "Canadian Sports"),
    LiveTvGuideCategory("us_sports", "U.S. Sports"),
    LiveTvGuideCategory("news", "News & Weather"),
    LiveTvGuideCategory("us_ent", "U.S. Entertainment"),
    LiveTvGuideCategory("ca_specialty", "Canadian Specialty"),
    LiveTvGuideCategory("french", "French-Language"),
    LiveTvGuideCategory("docs", "Documentaries & Lifestyle"),
    LiveTvGuideCategory("kids", "Kids & Family"),
    LiveTvGuideCategory("premium", "Premium Movies"),
)

/** Premium channels are in the catalogue but not in a guide nobody has customised: most people don't subscribe. */
internal val LIVE_TV_GUIDE_DEFAULT_CATEGORY_IDS: Set<String> =
    LIVE_TV_GUIDE_CATEGORIES.map { it.id }.filterNot { it == "premium" }.toSet()

private enum class GuideCountry { Canada, UnitedStates, Other }

private class CatalogChannel(
    val categoryId: String,
    val name: String,
    val country: GuideCountry,
    val acceptsOtherCountry: Boolean,
    /** Normalised names, most preferred first. */
    val keys: List<String>,
)

private val catalogChannels: List<CatalogChannel> by lazy {
    LIVE_TV_GUIDE_CATALOG_TEXT.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .mapNotNull { line ->
            val parts = line.split('|')
            if (parts.size < 3) return@mapNotNull null
            val names = listOf(parts[2]) + parts.getOrNull(3).orEmpty().split(';').filter { it.isNotBlank() }
            CatalogChannel(
                categoryId = parts[0],
                name = parts[2],
                country = if (parts[1].startsWith("C")) GuideCountry.Canada else GuideCountry.UnitedStates,
                acceptsOtherCountry = parts[1].endsWith("+"),
                keys = names.map { normalizeStreamName(it, stripPrefix = false).full }.filter { it.isNotEmpty() }.distinct(),
            )
        }
        .toList()
}

/** How many channels the catalogue lists in each category. */
internal val liveTvGuideCatalogSizes: Map<String, Int> by lazy {
    catalogChannels.groupingBy { it.categoryId }.eachCount()
}

private class NormalizedStream(
    /** The name with the country prefix, quality tags and punctuation removed, e.g. "SPORTSNETEAST". */
    val full: String,
    /** [full] without a trailing East/West feed word, e.g. "TBS" for "TBS HD EAST". */
    val base: String,
    /** 3 when the name names no feed or the East one in brackets, 2 for a trailing East, 1 for West or Pacific. */
    val feedScore: Int,
    /** 2 for HD, 1 for 4K (heavier to stream), 0 otherwise. */
    val quality: Int,
    val prefix: String?,
)

private val QUALITY_WORDS = setOf("HD", "FHD", "UHD", "SD", "4K", "HEVC", "H264", "H265", "RAW", "BK", "BACKUP", "ALT", "50FPS", "60FPS")
private const val PREFIX_SEPARATORS = "★|:•❖"

private fun foldChar(char: Char): Char = when (char) {
    'À', 'Á', 'Â', 'Ã', 'Ä', 'Å' -> 'A'
    'Ç' -> 'C'
    'È', 'É', 'Ê', 'Ë' -> 'E'
    'Ì', 'Í', 'Î', 'Ï' -> 'I'
    'Ñ' -> 'N'
    'Ò', 'Ó', 'Ô', 'Õ', 'Ö' -> 'O'
    'Ù', 'Ú', 'Û', 'Ü' -> 'U'
    else -> char
}

/** The 2-3 letter tag providers put in front of a name ("CA ★ TSN 1", "US | ESPN", "US - ESPN"), and where it ends. */
private fun leadingTag(upper: String): Pair<String, Int>? {
    var index = 0
    while (index < upper.length && upper[index].isWhitespace()) index++
    val start = index
    while (index < upper.length && upper[index] in 'A'..'Z') index++
    val length = index - start
    if (length !in 2..3) return null
    val tag = upper.substring(start, index)
    val hadSpace = index < upper.length && upper[index].isWhitespace()
    while (index < upper.length && upper[index].isWhitespace()) index++
    if (index >= upper.length) return null
    val separator = upper[index]
    val isDash = separator == '-' || separator == '–'
    val followedBySpace = index + 1 < upper.length && upper[index + 1].isWhitespace()
    return when {
        separator in PREFIX_SEPARATORS -> tag to index + 1
        // A dash only counts when it stands alone, so "AMI-TELE" is a name and "US - ESPN" is a tagged one.
        isDash && hadSpace && followedBySpace -> tag to index + 1
        else -> null
    }
}

private fun countryOfTag(tag: String): GuideCountry = when (tag) {
    "CA", "CAF", "CAN" -> GuideCountry.Canada
    "US", "USA" -> GuideCountry.UnitedStates
    else -> GuideCountry.Other
}

private fun normalizeStreamName(name: String, stripPrefix: Boolean): NormalizedStream {
    val upper = buildString(name.length) { name.uppercase().forEach { append(foldChar(it)) } }
    var prefix: String? = null
    var bodyStart = 0
    if (stripPrefix) {
        leadingTag(upper)?.let { (tag, end) ->
            prefix = tag
            bodyStart = end
        }
    }
    val tokens = ArrayList<String>()
    val bracketTokens = ArrayList<String>()
    val token = StringBuilder()
    var depth = 0
    fun flush() {
        if (token.isEmpty()) return
        (if (depth > 0) bracketTokens else tokens) += token.toString()
        token.clear()
    }
    for (index in bodyStart until upper.length) {
        val char = upper[index]
        when {
            char == '(' || char == '[' || char == '{' -> { flush(); depth++ }
            char == ')' || char == ']' || char == '}' -> { flush(); if (depth > 0) depth-- }
            char in 'A'..'Z' || char in '0'..'9' -> token.append(char)
            char == '&' -> { flush(); if (depth == 0) tokens += "AND" }
            char == '+' -> { flush(); if (depth == 0) tokens += "PLUS" }
            else -> flush()
        }
    }
    flush()
    val quality = when {
        tokens.any { it == "HD" || it == "FHD" } -> 2
        tokens.any { it == "UHD" || it == "4K" } -> 1
        else -> 0
    }
    val kept = tokens.filterNot { it in QUALITY_WORDS }
    val full = kept.joinToString("")
    val bracketFeed = bracketTokens.lastOrNull { it == "EAST" || it == "WEST" || it == "PACIFIC" }
    val trailingFeed = kept.lastOrNull()?.takeIf { kept.size > 1 && (it == "EAST" || it == "WEST" || it == "PACIFIC") }
    return if (trailingFeed != null) {
        NormalizedStream(full, kept.dropLast(1).joinToString(""), if (trailingFeed == "EAST") 2 else 1, quality, prefix)
    } else {
        NormalizedStream(full, full, if (bracketFeed == null || bracketFeed == "EAST") 3 else 1, quality, prefix)
    }
}

private fun countryOf(channel: LiveTvChannel, normalized: NormalizedStream): GuideCountry? {
    normalized.prefix?.let { return countryOfTag(it) }
    channel.group?.let { group -> leadingTag(group.uppercase())?.let { return countryOfTag(it.first) } }
    val suffix = channel.epgId?.substringAfterLast('.', missingDelimiterValue = "")?.lowercase()
    return when (suffix) {
        "ca" -> GuideCountry.Canada
        "us" -> GuideCountry.UnitedStates
        else -> null
    }
}

private class StreamCandidate(
    val channel: LiveTvChannel,
    val country: GuideCountry?,
    val feedScore: Int,
    val quality: Int,
    val featured: Boolean,
)

/**
 * Finds, for every catalogue channel, the best stream in the user's playlist: the right country, the main (East)
 * feed, one that has guide listings, in HD. Returns the matched streams per category, in catalogue order.
 * Catalogue channels the playlist doesn't carry are simply absent.
 */
internal fun matchLiveTvGuideCatalog(
    channels: List<LiveTvChannel>,
    hasGuide: (LiveTvChannel) -> Boolean,
): Map<String, List<LiveTvChannel>> {
    if (channels.isEmpty()) return emptyMap()
    val wanted = HashSet<String>()
    catalogChannels.forEach { wanted += it.keys }
    val byKey = HashMap<String, MutableList<StreamCandidate>>()
    channels.forEach { channel ->
        val normalized = normalizeStreamName(channel.name, stripPrefix = true)
        val fullWanted = normalized.full in wanted
        val baseWanted = normalized.base != normalized.full && normalized.base in wanted
        if (!fullWanted && !baseWanted) return@forEach
        val country = countryOf(channel, normalized)
        if (country == GuideCountry.Other) return@forEach
        val featured = channel.name.contains('◉')
        if (fullWanted) {
            // A name that spells out its feed ("Sportsnet East") is an exact match, not a lesser feed of something else.
            val score = if (normalized.base != normalized.full) 3 else normalized.feedScore
            byKey.getOrPut(normalized.full) { ArrayList() } += StreamCandidate(channel, country, score, normalized.quality, featured)
        }
        if (baseWanted) {
            byKey.getOrPut(normalized.base) { ArrayList() } += StreamCandidate(channel, country, normalized.feedScore, normalized.quality, featured)
        }
    }
    val result = LinkedHashMap<String, MutableList<LiveTvChannel>>()
    val used = HashSet<String>()
    catalogChannels.forEach { entry ->
        var best: LiveTvChannel? = null
        var bestScore = -1
        entry.keys.forEachIndexed { keyIndex, key ->
            byKey[key]?.forEach { candidate ->
                val countryScore = when {
                    candidate.country == null -> 1
                    candidate.country == entry.country -> 2
                    entry.acceptsOtherCountry -> 0
                    else -> return@forEach
                }
                val score = countryScore * 1_000_000 +
                    (if (candidate.feedScore >= 2) 100_000 else 0) +
                    (50 - keyIndex.coerceAtMost(50)) * 1_000 +
                    candidate.feedScore * 100 +
                    (if (hasGuide(candidate.channel)) 40 else 0) +
                    (if (candidate.featured) 20 else 0) +
                    candidate.quality * 5
                if (score > bestScore) {
                    bestScore = score
                    best = candidate.channel
                }
            }
        }
        val match = best ?: return@forEach
        // One stream stands for one catalogue channel within a category.
        if (used.add(entry.categoryId + "|" + match.id)) {
            result.getOrPut(entry.categoryId) { ArrayList() } += match
        }
    }
    return result
}

/**
 * The channels of the user's guide: their chosen categories in catalogue order, then the channels they added by
 * hand. [categoryId] narrows it to one category, or to the hand-picked ones with [LIVE_TV_GUIDE_MINE].
 */
internal fun liveTvGuideChannels(
    matches: Map<String, List<LiveTvChannel>>,
    selectedCategoryIds: Set<String>,
    addedChannels: List<LiveTvChannel>,
    categoryId: String? = null,
): List<LiveTvChannel> {
    val seen = HashSet<String>()
    val result = ArrayList<LiveTvChannel>()
    if (categoryId != LIVE_TV_GUIDE_MINE) {
        LIVE_TV_GUIDE_CATEGORIES.forEach { category ->
            if (category.id !in selectedCategoryIds) return@forEach
            if (categoryId != null && categoryId != category.id) return@forEach
            matches[category.id]?.forEach { if (seen.add(it.id)) result += it }
        }
    }
    if (categoryId == null || categoryId == LIVE_TV_GUIDE_MINE) {
        addedChannels.forEach { if (seen.add(it.id)) result += it }
    }
    return result
}

internal const val LIVE_TV_GUIDE_MINE = "mine"
