package com.nuvio.app.features.downloads

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Device-level download preferences. Not synced across profiles/devices: whether the
 * current device is fine using mobile data for downloads, and where downloads are saved,
 * are inherently per-device facts, not per-profile ones.
 */
object DownloadsSettingsRepository {
    private val _allowMobileDataDownloads = MutableStateFlow(false)
    val allowMobileDataDownloads: StateFlow<Boolean> = _allowMobileDataDownloads.asStateFlow()

    private val _downloadLocationUri = MutableStateFlow<String?>(null)
    val downloadLocationUri: StateFlow<String?> = _downloadLocationUri.asStateFlow()

    private var hasLoaded = false

    fun ensureLoaded() {
        if (hasLoaded) return
        hasLoaded = true
        _allowMobileDataDownloads.value = DownloadsSettingsStorage.loadAllowMobileDataDownloads() ?: false
        _downloadLocationUri.value = DownloadsStorage.getDownloadLocationUri()
    }

    fun setAllowMobileDataDownloads(enabled: Boolean) {
        ensureLoaded()
        if (_allowMobileDataDownloads.value == enabled) return
        _allowMobileDataDownloads.value = enabled
        DownloadsSettingsStorage.saveAllowMobileDataDownloads(enabled)
    }

    fun setDownloadLocationUri(uri: String?) {
        ensureLoaded()
        _downloadLocationUri.value = uri
        DownloadsStorage.setDownloadLocationUri(uri)
    }

    // No-op: called from DownloadsRepository.onProfileChanged() for symmetry with the other
    // per-profile repositories it resets, but these settings are device-level (see class doc)
    // and already loaded once for the whole process lifetime, so there's nothing to reload.
    fun onProfileChanged() = Unit
}
