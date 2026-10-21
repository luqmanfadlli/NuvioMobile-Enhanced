package com.nuvio.app.features.ai

import android.content.Context
import android.content.SharedPreferences
import com.nuvio.app.core.storage.ProfileScopedKey

internal actual object AiSettingsStorage {
    private const val preferencesName = "nuvio_ai_settings"

    private var preferences: SharedPreferences? = null

    fun initialize(context: Context) {
        preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    }

    actual fun load(key: String): String? =
        preferences?.getString(ProfileScopedKey.of(key), null)

    actual fun save(key: String, value: String) {
        preferences
            ?.edit()
            ?.putString(ProfileScopedKey.of(key), value)
            ?.apply()
    }
}
