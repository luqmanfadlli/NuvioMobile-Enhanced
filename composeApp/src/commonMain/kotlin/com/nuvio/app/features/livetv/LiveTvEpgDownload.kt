package com.nuvio.app.features.livetv

/**
 * Where the platform's HTTP client cannot slow a download down to the speed of the reader (iOS queues whatever
 * has arrived in memory, which for a large guide is most of the file), the guide is downloaded to a temporary
 * file first and handed to [onChunk] in slices. [onChunk] returns false to stop early.
 *
 * Returns false, having done nothing, on platforms that should read the response directly instead.
 */
internal expect suspend fun liveTvStreamGuideFromDisk(
    url: String,
    headers: Map<String, String>,
    onChunk: (bytes: ByteArray, count: Int) -> Boolean,
): Boolean
