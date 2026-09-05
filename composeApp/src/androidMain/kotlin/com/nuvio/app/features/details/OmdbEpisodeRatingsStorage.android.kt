package com.nuvio.app.features.details

import android.content.Context
import android.content.SharedPreferences

actual object OmdbEpisodeRatingsStorage {
    private const val preferencesName = "nuvio_omdb_episode_ratings"
    private const val payloadKey = "omdb_episode_ratings_cache"

    private var preferences: SharedPreferences? = null

    fun initialize(context: Context) {
        preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    }

    actual fun loadPayload(): String? =
        preferences?.getString(payloadKey, null)

    actual fun savePayload(payload: String) {
        preferences
            ?.edit()
            ?.putString(payloadKey, payload)
            ?.apply()
    }
}
