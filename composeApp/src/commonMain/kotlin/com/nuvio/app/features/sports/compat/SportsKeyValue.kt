package com.nuvio.app.features.sports.compat

/** A few remembered flags and strings for the Sports hub (for example "the alerts offer was shown"). */
internal expect object SportsKeyValue {
    fun getString(key: String): String?
    fun putString(key: String, value: String?)
}
