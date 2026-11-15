package com.nuvio.app.features.downloads

import android.content.Context
import android.os.StatFs
import java.io.File

actual object DownloadsStoragePlatform {
    private var appContext: Context? = null
    fun initialize(context: Context) { appContext = context.applicationContext }

    actual fun getDeviceStorageStats(): DeviceStorageStats {
        val context = appContext ?: return DeviceStorageStats(0L, 0L)
        DownloadsSettingsRepository.ensureLoaded()
        if (!DownloadsSettingsRepository.downloadLocationUri.value.isNullOrBlank()) return DeviceStorageStats(0L, 0L)
        return runCatching {
            val path = context.filesDir
            val stat = StatFs(path.path)
            val blockSize = stat.blockSizeLong
            val totalBlocks = stat.blockCountLong
            val availableBlocks = stat.availableBlocksLong
            DeviceStorageStats(
                totalBytes = (totalBlocks * blockSize).coerceAtLeast(0L),
                freeBytes = (availableBlocks * blockSize).coerceAtLeast(0L),
            )
        }.getOrElse { DeviceStorageStats(0L, 0L) }
    }
}
