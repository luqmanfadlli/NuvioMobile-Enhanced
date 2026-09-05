package com.nuvio.app.features.details

import platform.Foundation.NSUserDefaults

actual object OmdbSettingsStorage {
    private const val apiKeyKey = "omdb_api_key"

    actual fun loadApiKey(): String? =
        NSUserDefaults.standardUserDefaults.stringForKey(apiKeyKey)

    actual fun saveApiKey(apiKey: String) {
        NSUserDefaults.standardUserDefaults.setObject(apiKey, forKey = apiKeyKey)
    }
}
