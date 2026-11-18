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
    private const val PREFERRED_RESOLUTION_KEY = "preferred_download_resolution"
    private val _allowMobileDataDownloads = MutableStateFlow(false)
    val allowMobileDataDownloads: StateFlow<Boolean> = _allowMobileDataDownloads.asStateFlow()

    private val _showDownloadButton = MutableStateFlow(true)
    val showDownloadButton: StateFlow<Boolean> = _showDownloadButton.asStateFlow()

    private val _downloadLocationUri = MutableStateFlow<String?>(null)
    val downloadLocationUri: StateFlow<String?> = _downloadLocationUri.asStateFlow()

    private val _preferredResolution = MutableStateFlow(DownloadPreferredResolution.DEFAULT)
    val preferredResolution: StateFlow<DownloadPreferredResolution> = _preferredResolution.asStateFlow()

    private val _concurrencyLimits = MutableStateFlow(
        DownloadConcurrencyLimit.entries.associateWith { it.defaultValue },
    )
    val concurrencyLimits: StateFlow<Map<DownloadConcurrencyLimit, Int>> = _concurrencyLimits.asStateFlow()

    private var hasLoaded = false

    fun ensureLoaded() {
        if (hasLoaded) return
        hasLoaded = true
        _allowMobileDataDownloads.value = DownloadsSettingsStorage.loadAllowMobileDataDownloads() ?: false
        _showDownloadButton.value = DownloadsSettingsStorage.loadShowDownloadButton() ?: true
        _preferredResolution.value = DownloadPreferredResolution.fromStorage(DownloadsSettingsStorage.loadString(PREFERRED_RESOLUTION_KEY))
        _concurrencyLimits.value = DownloadConcurrencyLimit.entries.associateWith { limit ->
            DownloadsSettingsStorage.loadInt(limit.storageKey)?.let(limit::clamp) ?: limit.defaultValue
        }
        _downloadLocationUri.value = DownloadsStorage.getDownloadLocationUri()
    }

    fun setAllowMobileDataDownloads(enabled: Boolean) {
        ensureLoaded()
        if (_allowMobileDataDownloads.value == enabled) return
        _allowMobileDataDownloads.value = enabled
        DownloadsSettingsStorage.saveAllowMobileDataDownloads(enabled)
    }

    fun setShowDownloadButton(enabled: Boolean) {
        ensureLoaded()
        if (_showDownloadButton.value == enabled) return
        _showDownloadButton.value = enabled
        DownloadsSettingsStorage.saveShowDownloadButton(enabled)
    }

    fun setPreferredResolution(resolution: DownloadPreferredResolution) {
        ensureLoaded()
        if (_preferredResolution.value == resolution) return
        _preferredResolution.value = resolution
        DownloadsSettingsStorage.saveString(PREFERRED_RESOLUTION_KEY, resolution.name)
    }

    fun concurrencyLimit(limit: DownloadConcurrencyLimit): Int {
        ensureLoaded()
        return _concurrencyLimits.value.getValue(limit)
    }

    fun setConcurrencyLimit(limit: DownloadConcurrencyLimit, count: Int) {
        ensureLoaded()
        val clamped = limit.clamp(count)
        if (_concurrencyLimits.value[limit] == clamped) return
        _concurrencyLimits.value = _concurrencyLimits.value + (limit to clamped)
        DownloadsSettingsStorage.saveInt(limit.storageKey, clamped)
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
