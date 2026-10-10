package com.nuvio.app.features.anilist

import com.nuvio.app.core.storage.ProfileScopedKey
import platform.Foundation.NSUserDefaults

internal actual object AniListStorage {
    private const val PAYLOAD_KEY = "anilist_payload"

    actual fun loadPayload(profileId: Int): String? =
        NSUserDefaults.standardUserDefaults.stringForKey(ProfileScopedKey.of(PAYLOAD_KEY, profileId))

    actual fun savePayload(profileId: Int, payload: String) {
        NSUserDefaults.standardUserDefaults.setObject(payload, forKey = ProfileScopedKey.of(PAYLOAD_KEY, profileId))
    }

    actual fun removeProfile(profileId: Int) {
        NSUserDefaults.standardUserDefaults.removeObjectForKey(ProfileScopedKey.of(PAYLOAD_KEY, profileId))
    }

    actual fun clearAll() {
        val defaults = NSUserDefaults.standardUserDefaults
        defaults.dictionaryRepresentation().keys
            .filterIsInstance<String>()
            .filter { it.startsWith("${PAYLOAD_KEY}_") }
            .forEach { defaults.removeObjectForKey(it) }
    }
}
