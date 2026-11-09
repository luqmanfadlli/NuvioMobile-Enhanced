package com.nuvio.app.features.sports

/**
 * Which matched stream Play starts. Your own fix for the game always wins; otherwise only the
 * channels we are sure are showing it count, and among those the best picture (4K, FHD, HD, SD)
 * goes first, or the plainest one when you chose SD only.
 */
internal object SportsStreams {
    private const val YOUR_PICK = 150
    private const val SURE = 80

    /** Every matched copy, best choice first (all picture qualities of a channel are listed, not one per channel). */
    fun ordered(matches: List<ChannelMatch>, settings: SportsSettings): List<ChannelMatch> {
        if (matches.isEmpty()) return matches
        val anySure = matches.any { it.score >= SURE }
        fun quality(m: ChannelMatch): Int = SportsMatcher.qualityRank(m.quality)
        return matches.withIndex().sortedWith(
            compareByDescending<IndexedValue<ChannelMatch>> { it.value.score >= YOUR_PICK }
                .thenByDescending { !anySure || it.value.score >= SURE }
                .thenBy { if (settings.sdOnly) quality(it.value) else -quality(it.value) }
                .thenBy { it.index },
        ).map { it.value }
    }

    fun best(matches: List<ChannelMatch>, settings: SportsSettings): ChannelMatch? = ordered(matches, settings).firstOrNull()
}
