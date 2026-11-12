package com.nuvio.app.features.livetv

/**
 * The user agent live channels are opened with, as Nuvio TV opens them. IPTV panels are made for
 * IPTV players: many refuse a browser's user agent, or answer it with a stream that never starts.
 */
internal const val LIVE_TV_STREAM_USER_AGENT = "VLC/3.0.0 LibVLC/3.0.0"

/** Headers a channel without its own gets: VLC's user agent for web links, none otherwise. */
internal fun defaultLiveTvStreamHeaders(url: String): Map<String, String> =
    if (url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)) {
        mapOf("User-Agent" to LIVE_TV_STREAM_USER_AGENT)
    } else {
        emptyMap()
    }
