package com.nuvio.app.features.player

import com.nuvio.app.features.tracking.TrackingScrobbleAction

internal fun PlayerScreenRuntime.openDiscTitlesPanel() {
    if (playbackSnapshot.discTitles.size < 2) return
    showDiscTitlesPanel = true
    showSourcesPanel = false
    showEpisodesPanel = false
    showChaptersPanel = false
    showQualityPanel = false
    showSpeedPanel = false
    showLiveChannelsPanel = false
    controlsVisible = false
    playerController?.loadDiscTitleDetails()
}

internal fun PlayerScreenRuntime.selectDiscTitle(index: Int) {
    showDiscTitlesPanel = false
    controlsVisible = true
    if (index == playbackSnapshot.activeDiscTitleIndex) return
    if (!playbackSnapshot.discExtraPlaying) {
        flushWatchProgress(TrackingScrobbleAction.PAUSE)
    }
    playerController?.selectDiscTitle(index)
}
