package com.nuvio.app.features.experience

import android.content.Context
import android.content.SharedPreferences

actual object ExperienceStorage {
    private const val preferencesName = "banner_experience"
    private const val modeKey = "mode"
    private var preferences: SharedPreferences? = null

    fun initialize(context: Context) {
        preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    }

    actual fun load(): String? = preferences?.getString(modeKey, null)

    actual fun save(key: String) {
        preferences?.edit()?.putString(modeKey, key)?.apply()
    }
}
