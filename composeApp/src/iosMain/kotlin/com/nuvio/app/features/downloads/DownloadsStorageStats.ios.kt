package com.nuvio.app.features.downloads

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSystemFreeSize
import platform.Foundation.NSFileSystemSize
import platform.Foundation.NSHomeDirectory
import platform.Foundation.NSNumber

actual object DownloadsStoragePlatform {
    @OptIn(ExperimentalForeignApi::class)
    actual fun getDeviceStorageStats(): DeviceStorageStats {
        return runCatching {
            val fileManager = NSFileManager.defaultManager
            val attrs = fileManager.attributesOfFileSystemForPath(NSHomeDirectory(), null)
            val total = (attrs?.get(NSFileSystemSize) as? NSNumber)?.longLongValue ?: 0L
            val free = (attrs?.get(NSFileSystemFreeSize) as? NSNumber)?.longLongValue ?: 0L
            DeviceStorageStats(
                totalBytes = total.coerceAtLeast(0L),
                freeBytes = free.coerceAtLeast(0L),
            )
        }.getOrElse {
            DeviceStorageStats(totalBytes = 0L, freeBytes = 0L)
        }
    }
}
