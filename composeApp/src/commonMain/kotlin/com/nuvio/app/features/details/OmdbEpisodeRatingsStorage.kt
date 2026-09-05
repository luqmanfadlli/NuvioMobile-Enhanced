package com.nuvio.app.features.details

internal expect object OmdbEpisodeRatingsStorage {
    fun loadPayload(): String?
    fun savePayload(payload: String)
}
