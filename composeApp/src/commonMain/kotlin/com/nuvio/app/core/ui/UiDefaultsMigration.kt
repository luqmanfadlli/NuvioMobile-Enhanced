package com.nuvio.app.core.ui

import com.nuvio.app.features.music.data.MusicStorage

/**
 * Runs a change to saved UI settings once per device. New defaults only reach people who have never saved
 * settings; this is how a changed default also reaches a profile that already has some.
 */
internal object UiDefaultsMigration {
    private const val STORE = "ui_defaults"

    /** Runs [block] the first time only; returns true when it ran. */
    fun applyOnce(name: String, block: () -> Unit): Boolean {
        if (MusicStorage.getString(STORE, name) == "1") return false
        block()
        MusicStorage.putString(STORE, name, "1")
        return true
    }
}
