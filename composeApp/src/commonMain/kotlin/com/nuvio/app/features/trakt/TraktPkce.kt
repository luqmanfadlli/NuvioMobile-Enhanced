package com.nuvio.app.features.trakt

import io.ktor.http.encodeURLParameter

internal data class TraktPkceParameters(
    val verifier: String,
    val challenge: String,
)

private const val BASE64_URL_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
private const val TRAKT_AUTHORIZE_URL = "https://auth.trakt.tv/oauth/authorize"

internal expect fun traktSecureRandomBytes(size: Int): ByteArray
internal expect fun traktSha256(input: ByteArray): ByteArray

internal fun base64UrlNoPadding(input: ByteArray): String {
    val output = StringBuilder((input.size * 4 + 2) / 3)
    var index = 0
    while (index < input.size) {
        val b0 = input[index++].toInt() and 0xff
        val hasB1 = index < input.size
        val b1 = if (hasB1) input[index++].toInt() and 0xff else 0
        val hasB2 = index < input.size
        val b2 = if (hasB2) input[index++].toInt() and 0xff else 0

        output.append(BASE64_URL_ALPHABET[b0 ushr 2])
        output.append(BASE64_URL_ALPHABET[((b0 and 0x03) shl 4) or (b1 ushr 4)])
        if (hasB1) output.append(BASE64_URL_ALPHABET[((b1 and 0x0f) shl 2) or (b2 ushr 6)])
        if (hasB2) output.append(BASE64_URL_ALPHABET[b2 and 0x3f])
    }
    return output.toString()
}

internal fun traktPkceChallenge(verifier: String): String =
    base64UrlNoPadding(traktSha256(verifier.encodeToByteArray()))

internal fun createTraktPkceParameters(): TraktPkceParameters {
    val verifier = base64UrlNoPadding(traktSecureRandomBytes(32))
    return TraktPkceParameters(verifier, traktPkceChallenge(verifier))
}

internal fun buildTraktAuthorizationUrl(
    clientId: String,
    redirectUri: String,
    state: String,
    challenge: String,
): String {
    val query = listOf(
        "response_type=code",
        "client_id=${clientId.encodeURLParameter()}",
        "redirect_uri=${redirectUri.encodeURLParameter()}",
        "state=${state.encodeURLParameter()}",
        "code_challenge=${challenge.encodeURLParameter()}",
        "code_challenge_method=S256",
    ).joinToString("&")
    return "$TRAKT_AUTHORIZE_URL?$query"
}
