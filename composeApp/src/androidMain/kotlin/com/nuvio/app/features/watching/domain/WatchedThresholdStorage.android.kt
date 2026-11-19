package com.nuvio.app.features.watching.domain

import android.content.Context
import android.content.SharedPreferences

internal actual object WatchedThresholdStorage {
    private const val preferencesName = "nuvio_watched_threshold"
    private const val percentKey = "watched_threshold_percent"

    private var preferences: SharedPreferences? = null

    fun initialize(context: Context) {
        preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    }

    actual val isReady: Boolean
        get() = preferences != null

    actual fun load(): Int? = preferences?.takeIf { it.contains(percentKey) }?.getInt(percentKey, 0)

    actual fun save(value: Int) {
        preferences?.edit()?.putInt(percentKey, value)?.apply()
    }
}
