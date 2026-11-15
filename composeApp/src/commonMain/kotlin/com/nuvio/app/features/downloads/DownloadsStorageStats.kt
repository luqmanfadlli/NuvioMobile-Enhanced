package com.nuvio.app.features.downloads

data class DeviceStorageStats(
    val totalBytes: Long,
    val freeBytes: Long,
) {
    val usedBytes: Long get() = (totalBytes - freeBytes).coerceAtLeast(0L)
}

expect object DownloadsStoragePlatform {
    fun getDeviceStorageStats(): DeviceStorageStats
}
