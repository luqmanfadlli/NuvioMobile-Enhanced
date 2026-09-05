package com.nuvio.app.features.details

internal expect object OmdbSettingsStorage {
    fun loadApiKey(): String?
    fun saveApiKey(apiKey: String)
}
