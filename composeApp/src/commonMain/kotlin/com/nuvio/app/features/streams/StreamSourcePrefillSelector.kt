package com.nuvio.app.features.streams

import com.nuvio.app.features.debrid.DebridStreamFacts
import com.nuvio.app.features.debrid.DebridStreamMetadata
import com.nuvio.app.features.debrid.DebridStreamPreferences
import com.nuvio.app.features.debrid.DirectDebridPlaybackResolver
import com.nuvio.app.features.downloads.DownloadPreferredResolution
import com.nuvio.app.features.downloads.isSupportedDownloadUrl
import com.nuvio.app.features.downloads.isDownloadableDirectUrl

internal data class StreamSelectionResult(
    val stream: StreamItem,
    val facts: DebridStreamFacts,
    val sizeBytes: Long?,
)

/**
 * Picks the source to download for each episode of a batch download.
 *
 * Only download-capable streams are considered. Among them it prefers, in order:
 * 1. the smallest stream at or above the preferred resolution;
 * 2. otherwise the highest lower resolution, smallest first;
 * 3. otherwise, when no resolution is known, the smallest stream.
 * A known size beats an unknown one, and ties keep the input order.
 */
internal object StreamSourcePrefillSelector {

    private const val MIN_VALID_FILE_SIZE_BYTES = 10_000_000L
    private const val MAX_VALID_FILE_SIZE_BYTES = 250_000_000_000L

    private val smallestFirst = compareBy<Candidate> { it.size == null }
        .thenBy { it.size ?: Long.MAX_VALUE }
        .thenBy { it.index }

    /** Debrid links count as eligible because they are resolved when the download is queued. */
    fun selectForBatch(
        streams: List<StreamItem>,
        preferredResolution: DownloadPreferredResolution = DownloadPreferredResolution.DEFAULT,
    ): StreamSelectionResult? {
        val candidates = streams
            .filter(::isDownloadCapable)
            .mapIndexed { index, stream ->
                Candidate(
                    index = index,
                    stream = stream,
                    facts = DebridStreamMetadata.facts(stream, DebridStreamPreferences()),
                    size = sizeBytes(stream),
                )
            }

        val preferred = preferredResolution.resolution
        val selected = candidates.filter { it.resolution >= preferred }.minWithOrNull(smallestFirst)
            ?: candidates.filter { it.resolution in 1 until preferred }
                .minWithOrNull(compareByDescending<Candidate> { it.resolution }.then(smallestFirst))
            ?: candidates.minWithOrNull(smallestFirst)
            ?: return null
        return StreamSelectionResult(stream = selected.stream, facts = selected.facts, sizeBytes = selected.size)
    }

    private fun isDownloadCapable(stream: StreamItem): Boolean {
        return isDownloadableDirectUrl(stream.playableDirectUrl) ||
            DirectDebridPlaybackResolver.shouldResolveToPlayableStream(stream)
    }

    private data class Candidate(
        val index: Int,
        val stream: StreamItem,
        val facts: DebridStreamFacts,
        val size: Long?,
    ) {
        val resolution: Int get() = facts.resolution.value
    }

    private fun isPlausibleFileSize(size: Long): Boolean =
        size in MIN_VALID_FILE_SIZE_BYTES..MAX_VALID_FILE_SIZE_BYTES

    fun sizeBytes(stream: StreamItem): Long? =
        stream.clientResolve?.stream?.raw?.size?.takeIf(::isPlausibleFileSize)
            ?: stream.behaviorHints.videoSize?.takeIf(::isPlausibleFileSize)
}

fun StreamItem.isResolverDependent(): Boolean =
    isDirectDebridStream || needsLocalDebridResolve
