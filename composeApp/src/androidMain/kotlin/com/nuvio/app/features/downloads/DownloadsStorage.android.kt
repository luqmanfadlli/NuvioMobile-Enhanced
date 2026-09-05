package com.nuvio.app.features.downloads

import android.content.Context
import android.content.SharedPreferences
import com.nuvio.app.core.storage.ProfileScopedKey

internal actual object DownloadsStorage {
    private const val preferencesName = "nuvio_downloads"
    private const val payloadKey = "downloads_payload"
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
}
