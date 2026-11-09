package com.nuvio.app.features.sports

import com.nuvio.app.features.addons.httpGetTextWithHeaders
import platform.Foundation.NSLocale
import platform.Foundation.NSLocaleCountryCode
import platform.Foundation.NSLocaleLanguageCode
import platform.Foundation.NSThread
import platform.Foundation.currentLocale

/**
 * iOS side of the Sports hub: plain HTTP GETs of the public score/schedule feeds (through the app's
 * shared HTTP client) and the device locale. Mirrors the Android implementation.
 */
internal actual object SportsPlatform {
    private val feedHeaders = mapOf(
        "Accept" to "application/json",
        "User-Agent" to "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 " +
            "(KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1",
    )

    actual suspend fun get(url: String): String = httpGetTextWithHeaders(url, feedHeaders)

    actual fun countryCode(): String =
        (NSLocale.currentLocale.objectForKey(NSLocaleCountryCode) as? String).orEmpty().uppercase()

    actual fun languageCode(): String =
        (NSLocale.currentLocale.objectForKey(NSLocaleLanguageCode) as? String)?.lowercase()?.ifBlank { null } ?: "en"

    actual fun pause(ms: Long) {
        if (ms > 0) NSThread.sleepForTimeInterval(ms / 1000.0)
    }

    actual fun foldAccents(text: String): String = SportsAccents.fold(text)
}
