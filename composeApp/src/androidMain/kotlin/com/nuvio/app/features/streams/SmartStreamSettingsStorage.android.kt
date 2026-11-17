package com.nuvio.app.features.streams

import android.content.Context
import android.content.SharedPreferences
import com.nuvio.app.core.storage.ProfileScopedKey

internal actual object SmartStreamSettingsStorage {
    private const val preferencesName = "nuvio_smart_stream_settings"
    private const val settingsKey = "smart_stream_sort_filter_settings"

    private var preferences: SharedPreferences? = null

    fun initialize(context: Context) {
        preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
        SmartStreamDeviceContext.context = context.applicationContext
    }

    actual fun load(): String? = preferences?.getString(ProfileScopedKey.of(settingsKey), null)

    actual fun save(value: String) {
        preferences?.edit()?.putString(ProfileScopedKey.of(settingsKey), value)?.apply()
    }
}
