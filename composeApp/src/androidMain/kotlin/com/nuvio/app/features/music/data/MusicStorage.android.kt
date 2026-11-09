package com.nuvio.app.features.music.data

import android.content.Context

internal actual object MusicStorage {
    private var appContext: Context? = null

    /** The application context, once [initialize] has run. */
    val applicationContext: Context? get() = appContext

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    private fun prefs(store: String) =
        appContext?.getSharedPreferences("banner_music_$store", Context.MODE_PRIVATE)

    actual fun getString(store: String, key: String): String? = prefs(store)?.getString(key, null)

    actual fun putString(store: String, key: String, value: String) {
        prefs(store)?.edit()?.putString(key, value)?.apply()
    }

    actual fun remove(store: String, key: String) {
        prefs(store)?.edit()?.remove(key)?.apply()
    }

    actual fun clear(store: String) {
        prefs(store)?.edit()?.clear()?.apply()
    }
}
