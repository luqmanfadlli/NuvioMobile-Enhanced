package com.nuvio.app.features.trailer

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import io.ktor.http.HttpMethod
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import platform.Foundation.NSURLComponents
import platform.Foundation.NSURLQueryItem

internal object TrailerExtractionPlatform {
    val defaultHeaders: Map<String, String> = mapOf(
        "accept-language" to "en-US,en;q=0.9",
        "user-agent" to
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15 " +
            "(KHTML, like Gecko) Version/17.4 Mobile/15E148 Safari/604.1",
    )

    private val httpClient = HttpClient(Darwin) {
        install(HttpTimeout)
        followRedirects = true
        expectSuccess = false
    }

    suspend fun performRequest(
        url: String,
        method: String,
        headers: Map<String, String>,
        body: String?,
        timeoutMillis: Long,
    ): TrailerRequestResponse = withContext(Dispatchers.Default) {
        val response = httpClient.request(url) {
            this.method = when (method.uppercase()) {
                "POST" -> HttpMethod.Post
                "PUT" -> HttpMethod.Put
                "DELETE" -> HttpMethod.Delete
                else -> HttpMethod.Get
            }
            headers.forEach { (name, value) ->
                header(name, value)
            }
            if (body != null) {
                setBody(body)
            }
            timeout {
                requestTimeoutMillis = timeoutMillis
                connectTimeoutMillis = timeoutMillis
                socketTimeoutMillis = timeoutMillis
            }
        }

        val bodyText = runCatching { response.bodyAsText() }.getOrElse { "" }
        TrailerRequestResponse(
            ok = response.status.isSuccess(),
            status = response.status.value,
            statusText = response.status.description,
            url = response.request.url.toString(),
            body = bodyText,
        )
    }

    suspend fun buildPlaybackSource(
        bestManifest: ManifestCandidate?,
        bestProgressive: StreamCandidate?,
        bestVideo: StreamCandidate?,
        bestAudio: StreamCandidate?,
    ): TrailerPlaybackSource? = withContext(Dispatchers.Default) {
        val compatibleVideo = bestVideo
            ?.takeIf { it.ext.equals("mp4", ignoreCase = true) }
        val compatibleAudio = bestAudio
            ?.takeIf {
                it.ext.equals("m4a", ignoreCase = true) ||
                    it.ext.equals("mp4", ignoreCase = true)
            }

        if (compatibleVideo != null) {
            return@withContext TrailerPlaybackSource(
                videoUrl = resolveReachableUrl(compatibleVideo.url),
                audioUrl = compatibleAudio?.url?.let { resolveReachableUrl(it) },
            )
        }

        val compatibleProgressive = bestProgressive
            ?.takeIf { it.ext.equals("mp4", ignoreCase = true) }
        if (compatibleProgressive != null) {
            return@withContext TrailerPlaybackSource(
                videoUrl = resolveReachableUrl(compatibleProgressive.url),
                audioUrl = null,
            )
        }

        if (bestManifest != null) {
            return@withContext TrailerPlaybackSource(
                videoUrl = bestManifest.manifestUrl,
                audioUrl = null,
            )
        }

        null
    }

    private suspend fun resolveReachableUrl(url: String): String {
        if (!url.contains("googlevideo.com")) return url

        val mnParam = getQueryParameter(url, "mn") ?: return url
        val servers = mnParam.split(',').map { it.trim() }.filter { it.isNotBlank() }
        if (servers.size < 2) return url

        val host = getHost(url) ?: return url
        val candidates = mutableListOf(url)

        servers.forEachIndexed { index, server ->
            val altHost = host
                .replaceFirst(Regex("^rr\\d+---"), "rr${index + 1}---")
                .replaceFirst(Regex("sn-[a-z0-9]+-[a-z0-9]+"), server)
            if (altHost != host) {
                candidates += url.replace(host, altHost)
            }
        }

        if (candidates.size == 1) return candidates.first()

        return coroutineScope {
            val probes = candidates.map { candidate ->
                async {
                    if (isUrlReachable(candidate)) candidate else null
                }
            }
            withTimeoutOrNull(2_000L) {
                probes.awaitAll().firstOrNull { !it.isNullOrBlank() }
            } ?: url
        }
    }

    private suspend fun isUrlReachable(url: String): Boolean {
        val response = runCatching {
            performRequest(
                url = url,
                method = "GET",
                headers = mapOf(
                    "range" to "bytes=0-0",
                    "user-agent" to defaultHeaders.getValue("user-agent"),
                ),
                body = null,
                timeoutMillis = 2_000L,
            )
        }.getOrNull() ?: return false

        return response.status in 200..299
    }

    private fun getHost(url: String): String? {
        val components = NSURLComponents(string = url)
        return components.host
    }

    private fun getQueryParameter(url: String, name: String): String? {
        val components = NSURLComponents(string = url)
        return queryItems(components).firstOrNull { it.name == name }?.value
    }

    private fun queryItems(components: NSURLComponents): List<NSURLQueryItem> {
        return components.queryItems?.mapNotNull { it as? NSURLQueryItem } ?: emptyList()
    }
}
