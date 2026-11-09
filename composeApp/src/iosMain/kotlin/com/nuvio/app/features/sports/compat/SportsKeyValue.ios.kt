package com.nuvio.app.features.sports.compat

import platform.Foundation.NSUserDefaults

internal actual object SportsKeyValue {
    private val defaults get() = NSUserDefaults.standardUserDefaults
    actual fun getString(key: String): String? = defaults.stringForKey("sports_hub.$key")
    actual fun putString(key: String, value: String?) { if (value == null) defaults.removeObjectForKey("sports_hub.$key") else defaults.setObject(value, "sports_hub.$key") }
}
