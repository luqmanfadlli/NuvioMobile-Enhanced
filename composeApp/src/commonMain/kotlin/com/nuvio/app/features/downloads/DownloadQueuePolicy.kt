package com.nuvio.app.features.downloads

/** Decides which downloads may run, given the user's concurrency limits and network policy. */
internal data class DownloadQueuePolicy(
    val maxDownloads: Int,
    val maxEpisodesPerShow: Int,
    val maxShows: Int,
    val isNetworkAllowed: Boolean,
) {
    /** Whether a download for [showKey] can start next to [downloading] without exceeding the limits. */
    fun canStart(showKey: String?, downloading: List<DownloadItem>): Boolean {
        if (!isNetworkAllowed) return false
        if (downloading.size >= maxDownloads) return false
        if (showKey == null) return true
        val activeShows = downloading.mapNotNullTo(mutableSetOf()) { it.showKey }
        if (showKey !in activeShows && activeShows.size >= maxShows) return false
        return downloading.count { it.showKey == showKey } < maxEpisodesPerShow
    }

    /** The oldest queued download that may start now, or null when none can. */
    fun nextToStart(items: List<DownloadItem>): DownloadItem? {
        val downloading = items.filter { it.status == DownloadStatus.Downloading }
        return items
            .filter { it.status == DownloadStatus.Queued }
            .sortedWith(queueOrder)
            .firstOrNull { canStart(it.showKey, downloading) }
    }

    companion object {
        private val queueOrder = compareBy<DownloadItem> { it.createdAtEpochMs }
            .thenBy { it.seasonNumber ?: 0 }
            .thenBy { it.episodeNumber ?: 0 }
            .thenBy { it.id }

        fun current(): DownloadQueuePolicy {
            DownloadsSettingsRepository.ensureLoaded()
            val limits = DownloadsSettingsRepository.concurrencyLimits.value
            return DownloadQueuePolicy(
                maxDownloads = limits.getValue(DownloadConcurrencyLimit.TotalDownloads),
                maxEpisodesPerShow = limits.getValue(DownloadConcurrencyLimit.EpisodesPerShow),
                maxShows = limits.getValue(DownloadConcurrencyLimit.ConcurrentShows),
                isNetworkAllowed = isDownloadNetworkAllowed(),
            )
        }
    }
}

/** False when the user only allows downloads on Wi-Fi and the device is on mobile data. */
internal fun isDownloadNetworkAllowed(): Boolean {
    DownloadsSettingsRepository.ensureLoaded()
    return DownloadsSettingsRepository.allowMobileDataDownloads.value || DownloadNetworkGuard.transportState.value
}

/** The show an episode download counts against for per-show limits; null for movies. */
internal val DownloadItem.showKey: String?
    get() = parentMetaId.takeIf { isEpisode && it.isNotBlank() }
