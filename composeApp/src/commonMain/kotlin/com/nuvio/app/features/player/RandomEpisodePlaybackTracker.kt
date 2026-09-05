package com.nuvio.app.features.player

/** Tracks series started through the detail screen's random-episode action. */
object RandomEpisodePlaybackTracker {
    private val parentIds = mutableSetOf<String>()

    fun mark(parentMetaId: String) {
        if (parentMetaId.isNotBlank()) parentIds += parentMetaId
    }

    fun isMarked(parentMetaId: String): Boolean = parentMetaId in parentIds
    fun consume(parentMetaId: String): Boolean = parentIds.remove(parentMetaId)
}
