package com.nuvio.app.features.player

/**
 * Called when the user starts looking for something new to play (the streams screen opens).
 * On iOS a player parked in Picture in Picture is still a full mpv core; resolving streams
 * (addons and on-device plugins) next to a 4K HDR/DV core crossed the jetsam limit, so the
 * parked session is closed here instead of only when the new player starts.
 */
internal expect fun closeBackgroundPlayerForNewPlayback()
