package com.nuvio.app.features.streams

import kotlinx.serialization.json.JsonObject

internal expect object StreamBadgeSettingsStorage {
    fun loadStreamBadgeRules(): String?
    fun saveStreamBadgeRules(rules: String)
    fun loadShowFileSizeBadges(): Boolean?
    fun saveShowFileSizeBadges(enabled: Boolean)
    fun loadShowAddonLogo(): Boolean?
    fun saveShowAddonLogo(enabled: Boolean)
    fun loadShowStreamSearch(): Boolean?
    fun saveShowStreamSearch(enabled: Boolean)
    fun loadShowInPlayer(): Boolean?
    fun saveShowInPlayer(enabled: Boolean)
    fun loadPlayerBadgeSource(): String?
    fun savePlayerBadgeSource(source: String)
    fun loadStreamBackgroundMode(): String?
    fun saveStreamBackgroundMode(mode: String)
    fun loadStreamBadgePlacement(): String?
    fun saveStreamBadgePlacement(placement: String)
    fun loadStreamFormattingEnabled(): Boolean?
    fun saveStreamFormattingEnabled(enabled: Boolean)
    fun loadStreamFormatPreset(): String?
    fun saveStreamFormatPreset(preset: String)
    fun loadStreamFormattingScope(): String?
    fun saveStreamFormattingScope(scope: String)
    fun loadPinnedStreamSources(): String?
    fun savePinnedStreamSources(sources: String)
    fun loadLegacyDebridStreamBadgeRules(): String?
    fun clearLegacyDebridStreamBadgeRules()
    fun exportToSyncPayload(): JsonObject
    fun replaceFromSyncPayload(payload: JsonObject)
}
