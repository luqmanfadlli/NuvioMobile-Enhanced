package com.nuvio.app.features.podcasts

import kotlinx.serialization.Serializable

/** A podcast. [id] is its feed address, which never changes. */
@Serializable
data class Podcast(
    val id: String,
    val title: String,
    val publisher: String = "",
    val imageUrl: String? = null,
    val sport: String? = null,
    val league: String? = null,
    val description: String = "",
    val tags: List<String> = emptyList(),
)

@Serializable
data class PodcastEpisode(
    val id: String,
    val podcastId: String,
    val podcastTitle: String,
    val podcastImage: String?,
    val title: String,
    val description: String,
    val audioUrl: String,
    val isVideo: Boolean = false,
    val durationSec: Int = 0,
    val publishedMs: Long = 0L,
) {
    /** "1:02:03" or "45:10"; empty when the feed gives no length. */
    val clock: String
        get() {
            if (durationSec <= 0) return ""
            val h = durationSec / 3600
            val m = (durationSec % 3600) / 60
            val s = durationSec % 60
            return if (h > 0) "$h:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}" else "$m:${s.toString().padStart(2, '0')}"
        }

    /** "1h 5m" or "45 min". */
    val lengthLabel: String
        get() {
            if (durationSec <= 0) return ""
            val h = durationSec / 3600
            val m = (durationSec % 3600) / 60
            return if (h > 0) "${h}h ${m}m" else "${maxOf(m, 1)} min"
        }
}

data class PodcastShow(val podcast: Podcast, val episodes: List<PodcastEpisode>)
