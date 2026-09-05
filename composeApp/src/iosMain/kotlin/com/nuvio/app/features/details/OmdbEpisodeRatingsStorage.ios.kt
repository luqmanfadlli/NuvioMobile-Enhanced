package com.nuvio.app.features.details

import platform.Foundation.NSUserDefaults

actual object OmdbEpisodeRatingsStorage {
    private const val payloadKey = "omdb_episode_ratings_cache"

    actual fun loadPayload(): String? =
        NSUserDefaults.standardUserDefaults.stringForKey(payloadKey)

    actual fun savePayload(payload: String) {
        NSUserDefaults.standardUserDefaults.setObject(payload, forKey = payloadKey)
    }
}
