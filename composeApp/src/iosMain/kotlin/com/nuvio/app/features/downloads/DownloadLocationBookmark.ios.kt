package com.nuvio.app.features.downloads

import kotlinx.cinterop.BooleanVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.NSURLBookmarkResolutionWithoutUI
import platform.Foundation.create
import platform.posix.memcpy
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Bridges the shared `downloadLocationUri: String?` setting (see [DownloadsSettingsRepository])
 * to iOS's security-scoped bookmark mechanism. A folder picked via `UIDocumentPickerViewController`
 * only stays accessible across app launches if we persist a *bookmark* for it, not just its path —
 * the path itself can silently rot (container UUIDs change, iCloud items get relocated). The
 * bookmark is base64-encoded so it fits the shared, platform-agnostic String-typed storage
 * contract unchanged; only this file knows it's actually opaque bookmark data on iOS.
 */
@OptIn(ExperimentalForeignApi::class, ExperimentalEncodingApi::class)
internal fun createDownloadLocationBookmarkBase64(url: NSURL): String? {
    val startedAccess = url.startAccessingSecurityScopedResource()
    try {
        val data = url.bookmarkDataWithOptions(
            options = 0uL,
            includingResourceValuesForKeys = null,
            relativeToURL = null,
            error = null,
        ) ?: return null
        return Base64.encode(data.toByteArray())
    } finally {
        if (startedAccess) url.stopAccessingSecurityScopedResource()
    }
}

/**
 * Resolves a stored bookmark back into a security-scoped [NSURL]. Callers must bracket their use
 * of the returned URL with `startAccessingSecurityScopedResource()`/`stopAccessingSecurityScopedResource()`.
 * Returns null if the bookmark is missing/corrupt, or if the underlying folder is gone entirely
 * (moved to the trash, external drive unplugged, etc.) — callers should fall back to internal
 * storage rather than fail outright.
 */
@OptIn(ExperimentalForeignApi::class, ExperimentalEncodingApi::class)
internal fun resolveDownloadLocationBookmark(base64: String): NSURL? {
    val data = runCatching { Base64.decode(base64) }.getOrNull()?.toNSData() ?: return null
    return memScoped {
        val isStale = alloc<BooleanVar>()
        isStale.value = false
        val url = NSURL.URLByResolvingBookmarkData(
            data,
            options = NSURLBookmarkResolutionWithoutUI,
            relativeToURL = null,
            bookmarkDataIsStale = isStale.ptr,
            error = null,
        ) ?: return@memScoped null

        if (isStale.value) {
            // The folder moved/was renamed since the bookmark was created; transparently mint a
            // fresh bookmark so future launches keep resolving instead of silently degrading
            // forever once the original bookmark's cached path fully rots.
            createDownloadLocationBookmarkBase64(url)?.let { refreshed ->
                DownloadsSettingsRepository.setDownloadLocationUri(refreshed)
            }
        }
        url
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size <= 0) return ByteArray(0)
    val result = ByteArray(size)
    result.usePinned { pinned -> memcpy(pinned.addressOf(0), bytes, length) }
    return result
}

@OptIn(ExperimentalForeignApi::class)
private fun ByteArray.toNSData(): NSData? {
    if (isEmpty()) return null
    return usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
    }
}
