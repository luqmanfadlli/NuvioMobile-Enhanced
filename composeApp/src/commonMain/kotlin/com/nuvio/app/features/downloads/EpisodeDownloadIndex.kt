package com.nuvio.app.features.downloads

/** One title's downloads, indexed for per-episode lookup. */
class EpisodeDownloadIndex private constructor(
    private val byVideoId: Map<String, List<DownloadItem>>,
    private val byEpisode: Map<Pair<Int, Int>, List<DownloadItem>>,
    private val movieItems: List<DownloadItem>,
) {
    /**
     * The download for an episode (or the movie when both numbers are null), matched by video id
     * or by season and episode. Prefers running, queued, completed, then any remaining match.
     */
    fun find(seasonNumber: Int?, episodeNumber: Int?, videoId: String? = null): DownloadItem? {
        val byId = videoId?.trim()?.takeIf { it.isNotEmpty() }?.let { byVideoId[it] }.orEmpty()
        val byNumber = when {
            seasonNumber != null && episodeNumber != null -> byEpisode[seasonNumber to episodeNumber].orEmpty()
            seasonNumber == null && episodeNumber == null -> movieItems
            else -> emptyList()
        }
        return preferred((byId + byNumber).distinctBy { it.id })
    }

    companion object {
        fun build(items: List<DownloadItem>, parentMetaId: String): EpisodeDownloadIndex {
            val normalizedParentMetaId = parentMetaId.trim()
            val titleItems = items.filter { it.parentMetaId.trim() == normalizedParentMetaId }
            return EpisodeDownloadIndex(
                byVideoId = titleItems.filter { it.videoId.isNotBlank() }.groupBy { it.videoId.trim() },
                byEpisode = titleItems
                    .filter { it.isEpisode }
                    .groupBy { checkNotNull(it.seasonNumber) to checkNotNull(it.episodeNumber) },
                movieItems = titleItems.filter { it.seasonNumber == null && it.episodeNumber == null },
            )
        }

        private fun preferred(matches: List<DownloadItem>): DownloadItem? =
            matches.firstOrNull { it.status == DownloadStatus.Downloading }
                ?: matches.firstOrNull { it.status == DownloadStatus.Queued }
                ?: matches.firstOrNull { it.status == DownloadStatus.Completed }
                ?: matches.firstOrNull()
    }
}
