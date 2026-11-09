package com.nuvio.app.features.sports.compat

/** Small text files the Sports hub keeps on the device (saved feeds, tables, channel matches, what you follow). */
internal expect object LiveTvFiles {
    fun exists(dir: String, name: String): Boolean
    /** When the file was last written (epoch ms), or 0 when it doesn't exist. */
    fun lastModified(dir: String, name: String): Long
    fun readSmall(dir: String, name: String): String?
    fun writeSmall(dir: String, name: String, text: String)
}

internal object LiveTvDirs {
    const val STATE = "state"
    /** The Sports hub's saved feeds, tables and channel matches. */
    const val SPORTS = "sports"
}
