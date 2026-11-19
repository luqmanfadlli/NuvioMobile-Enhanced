package com.nuvio.app.features.watching.domain

import platform.Foundation.NSUserDefaults

internal actual object WatchedThresholdStorage {
    private const val percentKey = "watched_threshold_percent"

    actual val isReady: Boolean = true

    actual fun load(): Int? {
        val defaults = NSUserDefaults.standardUserDefaults
        if (defaults.objectForKey(percentKey) == null) return null
        return defaults.integerForKey(percentKey).toInt()
    }

    actual fun save(value: Int) {
        NSUserDefaults.standardUserDefaults.setInteger(value.toLong(), forKey = percentKey)
    }
}
