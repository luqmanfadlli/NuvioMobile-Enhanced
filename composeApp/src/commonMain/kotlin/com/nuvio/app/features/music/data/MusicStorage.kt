package com.nuvio.app.features.music.data

/** Small string key-value store for the music section's settings (NSUserDefaults / SharedPreferences). */
internal expect object MusicStorage {
    fun getString(store: String, key: String): String?
    fun putString(store: String, key: String, value: String)
    fun remove(store: String, key: String)
    fun clear(store: String)
}
