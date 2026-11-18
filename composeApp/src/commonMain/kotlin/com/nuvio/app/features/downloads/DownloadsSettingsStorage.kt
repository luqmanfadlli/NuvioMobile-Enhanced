package com.nuvio.app.features.downloads

internal expect object DownloadsSettingsStorage {
    fun loadAllowMobileDataDownloads(): Boolean?
    fun saveAllowMobileDataDownloads(enabled: Boolean)
    fun loadShowDownloadButton(): Boolean?
    fun saveShowDownloadButton(enabled: Boolean)
    fun loadString(key: String): String?
    fun saveString(key: String, value: String)
    fun loadInt(key: String): Int?
    fun saveInt(key: String, value: Int)
}
