package com.nuvio.app.features.music.data.lyrics

import com.nuvio.app.features.music.data.MusicHttp
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json

/**
 * Shared plumbing for the lyric providers.
 *
 * They are all raced against each other by [LyricsRepository], so a provider
 * that hangs holds up the whole lookup. The deadline is deliberately short: a
 * lyric that arrives after the second chorus is of no use to anyone, and the
 * fallbacks behind it are the better answer.
 */
private const val LYRICS_TIMEOUT_MS = 6_000L
private const val LYRICS_AUTH_TIMEOUT_MS = 15_000L

internal const val LYRICS_AGENT = "BitChord (https://github.com/bitchord)"

val lyricsJson = Json { ignoreUnknownKeys = true; isLenient = true }

private suspend fun fetch(url: String, headers: Map<String, String>, timeoutMs: Long): String? =
    runCatching {
        withTimeoutOrNull(timeoutMs) {
            val response = MusicHttp.get(url, headers)
            if (response.isSuccessful) response.body else null
        }
    }.getOrNull()

/** Body of a successful GET, or null for any failure at all. */
internal suspend fun lyricsGet(url: String): String? =
    fetch(url, mapOf("User-Agent" to LYRICS_AGENT, "Accept" to "application/json"), LYRICS_TIMEOUT_MS)

/** Body of an authenticated provider GET without service-specific browser headers. */
internal suspend fun lyricsGetBearer(url: String, bearer: String): String? {
    if (bearer.isBlank()) return null
    return fetch(
        url,
        mapOf(
            "User-Agent" to LYRICS_AGENT,
            "Accept" to "application/json, text/plain, */*",
            "Authorization" to "Bearer $bearer",
        ),
        LYRICS_AUTH_TIMEOUT_MS,
    )
}

/**
 * [lyricsGet], with a bearer token and the headers Apple's own web player
 * sends alongside one — `amp-api.music.apple.com` answers a token with no
 * `Origin` at all the same way it answers a wrong one, with a 403.
 */
internal suspend fun lyricsGetAuthorized(url: String, bearer: String): String? =
    fetch(
        url,
        mapOf(
            "User-Agent" to LYRICS_AGENT,
            "Accept" to "application/json",
            "Authorization" to "Bearer $bearer",
            "Origin" to "https://music.apple.com",
            "Referer" to "https://music.apple.com/",
        ),
        LYRICS_TIMEOUT_MS,
    )
