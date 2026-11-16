package com.nuvio.app.features.downloads

import android.content.Context
import android.content.SharedPreferences
import com.nuvio.app.core.storage.ProfileScopedKey
import com.nuvio.app.features.profiles.MAX_PROFILES

internal actual object DownloadsStorage {
    private const val preferencesName = "nuvio_downloads"
    private const val payloadKey = "downloads_payload"
    private const val offlineMetadataKey = "downloads_offline_metadata"
    private const val downloadLocationUriKey = "download_location_uri"

    private var preferences: SharedPreferences? = null

    fun initialize(context: Context) {
        preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    }

    actual fun loadPayload(): String? =
        preferences?.getString(ProfileScopedKey.of(payloadKey), null)

    actual fun savePayload(payload: String) {
        preferences
            ?.edit()
            ?.putString(ProfileScopedKey.of(payloadKey), payload)
            ?.apply()
    }

    actual fun loadOfflineMetadataPayload(): String? =
        preferences?.getString(ProfileScopedKey.of(offlineMetadataKey), null)

    actual fun saveOfflineMetadataPayload(payload: String) {
        preferences?.edit()?.putString(ProfileScopedKey.of(offlineMetadataKey), payload)?.apply()
    }

    actual fun getDownloadLocationUri(): String? =
        preferences?.getString(ProfileScopedKey.of(downloadLocationUriKey), null)

    actual fun setDownloadLocationUri(uri: String?) {
        preferences
            ?.edit()
            ?.run {
                if (uri == null) {
                    remove(ProfileScopedKey.of(downloadLocationUriKey))
                } else {
                    putString(ProfileScopedKey.of(downloadLocationUriKey), uri)
                }
            }
            ?.apply()
    }

    actual fun clearUnfinishedDownloads() {
        val prefs = preferences ?: return
        (1..MAX_PROFILES).forEach { profileId ->
            val key = ProfileScopedKey.of(payloadKey, profileId)
            val payload = prefs.getString(key, null) ?: return@forEach
            val items = DownloadsCodec.decodePayload(payload).items
            val unfinished = items.filter { it.status != DownloadStatus.Completed }
            unfinished.forEach { DownloadsPlatformDownloader.removePartialFile(it.fileName) }
            prefs.edit().putString(
                key,
                DownloadsCodec.encodePayload(items.filter { it.status == DownloadStatus.Completed }),
            ).apply()
        }
    }
}
