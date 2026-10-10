package com.nuvio.app.features.anilist

import android.content.Context
import android.content.SharedPreferences
import com.nuvio.app.core.storage.ProfileScopedKey

internal actual object AniListStorage {
    private const val PREFERENCES_NAME = "nuvio_anilist"
    private const val PAYLOAD_KEY = "anilist_payload"

    private var preferences: SharedPreferences? = null

    fun initialize(context: Context) {
        preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    }

    actual fun loadPayload(profileId: Int): String? =
        preferences?.getString(ProfileScopedKey.of(PAYLOAD_KEY, profileId), null)

    actual fun savePayload(profileId: Int, payload: String) {
        preferences?.edit()?.putString(ProfileScopedKey.of(PAYLOAD_KEY, profileId), payload)?.apply()
    }

    actual fun removeProfile(profileId: Int) {
        preferences?.edit()?.remove(ProfileScopedKey.of(PAYLOAD_KEY, profileId))?.apply()
    }

    actual fun clearAll() {
        preferences?.edit()?.clear()?.apply()
    }
}
