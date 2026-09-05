package com.nuvio.app.features.details

import android.content.Context
import android.content.SharedPreferences

actual object OmdbSettingsStorage {
    private const val preferencesName = "nuvio_omdb_settings"
    private const val apiKeyKey = "omdb_api_key"

    private var preferences: SharedPreferences? = null

    fun initialize(context: Context) {
        preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    }

    actual fun loadApiKey(): String? =
        preferences?.getString(apiKeyKey, null)

    actual fun saveApiKey(apiKey: String) {
        preferences?.edit()?.putString(apiKeyKey, apiKey)?.apply()
    }
}
