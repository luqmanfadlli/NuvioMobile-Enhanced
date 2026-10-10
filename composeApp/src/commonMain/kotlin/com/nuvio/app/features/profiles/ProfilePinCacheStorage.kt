package com.nuvio.app.features.profiles

internal expect object ProfilePinCacheStorage {
    fun loadPayload(profileIndex: Int): String?
    fun savePayload(profileIndex: Int, payload: String)
    fun removePayload(profileIndex: Int): Boolean
}