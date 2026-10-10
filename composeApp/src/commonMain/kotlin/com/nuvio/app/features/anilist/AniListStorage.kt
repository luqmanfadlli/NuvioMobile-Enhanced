package com.nuvio.app.features.anilist

internal expect object AniListStorage {
    fun loadPayload(profileId: Int): String?
    fun savePayload(profileId: Int, payload: String)
    fun removeProfile(profileId: Int)
    fun clearAll()
}
