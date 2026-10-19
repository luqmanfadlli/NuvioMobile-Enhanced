package com.nuvio.app.features.home

internal expect object HomeCatalogSettingsStorage {
    fun loadPayload(): String?
    fun savePayload(payload: String)
    fun loadCategoriesPayload(): String?
    fun saveCategoriesPayload(payload: String)
}
