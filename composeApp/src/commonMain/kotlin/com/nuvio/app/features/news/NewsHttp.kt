package com.nuvio.app.features.news

import com.nuvio.app.features.music.data.MusicHttp
import kotlinx.coroutines.CancellationException

/** Plain GETs for feeds and article pages. Publishers answer a phone browser, and often nothing else. */
internal object NewsHttp {
    private const val USER_AGENT =
        "Mozilla/5.0 (iPhone; CPU iPhone OS 18_3 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.3 Mobile/15E148 Safari/604.1"

    /** The response body, or null on any failure or non-2xx answer. */
    suspend fun get(url: String, accept: String = "*/*", maxBytes: Int = 6 * 1024 * 1024): String? =
        try {
            val response = MusicHttp.request(
                method = "GET",
                url = url,
                headers = mapOf(
                    "User-Agent" to USER_AGENT,
                    "Accept" to accept,
                    "Accept-Language" to "en-US,en;q=0.9",
                ),
                maxBytes = maxBytes,
            )
            response.takeIf { it.isSuccessful && it.body.isNotBlank() }?.body
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            null
        }
}
