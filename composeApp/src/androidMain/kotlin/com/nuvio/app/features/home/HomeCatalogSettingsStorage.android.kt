package com.nuvio.app.features.home

import android.content.Context
import android.content.SharedPreferences
import com.nuvio.app.core.storage.ProfileScopedKey

actual object HomeCatalogSettingsStorage {
    private const val preferencesName = "nuvio_home_catalog_settings"
    private const val payloadKey = "catalog_settings_payload"
    private const val categoriesKey = "home_categories_payload"

    private var preferences: SharedPreferences? = null

    fun initialize(context: Context) {
        preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    }

    actual fun loadPayload(): String? =
        preferences?.getString(ProfileScopedKey.of(payloadKey), null)

    actual fun savePayload(payload: String) {
        preferences
            ?.edit()
            ?.putString(ProfileScopedKey.of(payloadKey), payload)
            ?.apply()
    }

    actual fun loadCategoriesPayload(): String? =
        preferences?.getString(ProfileScopedKey.of(categoriesKey), null)

    actual fun saveCategoriesPayload(payload: String) {
        preferences
            ?.edit()
            ?.putString(ProfileScopedKey.of(categoriesKey), payload)
            ?.apply()
    }
}
