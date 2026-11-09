package com.nuvio.app.features.music.data

import platform.Foundation.NSUserDefaults

internal actual object MusicStorage {
    private fun fullKey(store: String, key: String) = "music.$store.$key"

    actual fun getString(store: String, key: String): String? =
        NSUserDefaults.standardUserDefaults.stringForKey(fullKey(store, key))

    actual fun putString(store: String, key: String, value: String) {
        NSUserDefaults.standardUserDefaults.setObject(value, forKey = fullKey(store, key))
    }

    actual fun remove(store: String, key: String) {
        NSUserDefaults.standardUserDefaults.removeObjectForKey(fullKey(store, key))
    }

    actual fun clear(store: String) {
        val defaults = NSUserDefaults.standardUserDefaults
        val prefix = "music.$store."
        defaults.dictionaryRepresentation().keys
            .mapNotNull { it as? String }
            .filter { it.startsWith(prefix) }
            .forEach { defaults.removeObjectForKey(it) }
    }
}
