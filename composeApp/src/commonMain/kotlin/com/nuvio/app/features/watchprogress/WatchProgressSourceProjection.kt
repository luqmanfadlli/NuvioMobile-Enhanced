package com.nuvio.app.features.watchprogress

import com.nuvio.app.features.tracking.WatchProgressSource

internal fun projectWatchProgressSourceEntries(
    source: WatchProgressSource,
    nuvioEntries: Collection<WatchProgressEntry>,
    providerEntries: Collection<WatchProgressEntry>,
): List<WatchProgressEntry> = when (source) {
    WatchProgressSource.ANILIST -> nuvioEntries.toList()
    else -> if (source.providerId == null) nuvioEntries.toList() else providerEntries.toList()
}
