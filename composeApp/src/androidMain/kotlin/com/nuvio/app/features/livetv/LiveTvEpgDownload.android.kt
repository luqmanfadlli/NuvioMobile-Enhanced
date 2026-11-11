package com.nuvio.app.features.livetv

/** OkHttp only reads from the socket as fast as the guide is parsed, so the response is streamed directly. */
internal actual suspend fun liveTvStreamGuideFromDisk(
    url: String,
    headers: Map<String, String>,
    onChunk: (bytes: ByteArray, count: Int) -> Boolean,
): Boolean = false
