package com.nuvio.app.features.streams

import platform.Foundation.NSUserDefaults

internal actual object SmartStreamSettingsStorage {
    private const val key = "smart_stream_sort_filter_settings"

    actual fun load(): String? = NSUserDefaults.standardUserDefaults.stringForKey(key)

    actual fun save(value: String) {
        NSUserDefaults.standardUserDefaults.setObject(value, forKey = key)
    }
}
