package com.nuvio.app.features.music.data

import com.nuvio.app.features.addons.httpRequestRaw
import io.ktor.http.encodeURLParameter

internal class MusicHttpResponse(
    val status: Int,
    val body: String,
    val headers: Map<String, String>,
) {
    val isSuccessful: Boolean get() = status in 200..299
}

/** The app's shared HTTP stack (Darwin / OkHttp) for the music data layer. */
internal object MusicHttp {
    suspend fun request(
        method: String,
        url: String,
        headers: Map<String, String> = emptyMap(),
        body: String = "",
        followRedirects: Boolean = true,
        maxBytes: Int = 8 * 1024 * 1024,
    ): MusicHttpResponse {
        val response = httpRequestRaw(
            method = method,
            url = url,
            headers = headers,
            body = body,
            followRedirects = followRedirects,
            maxResponseBodyBytes = maxBytes,
        )
        return MusicHttpResponse(response.status, response.body, response.headers)
    }

    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): MusicHttpResponse =
        request("GET", url, headers)
}

/** A tiny stand-in for OkHttp's `HttpUrl.newBuilder().addQueryParameter(..).build()`. */
internal class HttpUrlBuilder(private val base: String) {
    private val params = ArrayList<Pair<String, String>>()

    fun newBuilder(): HttpUrlBuilder = this

    fun addQueryParameter(name: String, value: String): HttpUrlBuilder = apply { params += name to value }

    fun build(): BuiltHttpUrl {
        if (params.isEmpty()) return BuiltHttpUrl(base)
        val query = params.joinToString("&") { (k, v) -> "${k.encodeURLParameter()}=${v.encodeURLParameter()}" }
        return BuiltHttpUrl(base + (if ('?' in base) "&" else "?") + query)
    }
}

internal class BuiltHttpUrl(private val text: String) {
    override fun toString(): String = text
}

internal fun String.toHttpUrl(): HttpUrlBuilder = HttpUrlBuilder(this)
