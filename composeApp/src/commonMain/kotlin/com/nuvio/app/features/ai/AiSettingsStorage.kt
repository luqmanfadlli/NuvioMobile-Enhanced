package com.nuvio.app.features.ai

/** Tiny per-profile string key/value store used by the AI recommendations settings. */
internal expect object AiSettingsStorage {
    fun load(key: String): String?
    fun save(key: String, value: String)
}
