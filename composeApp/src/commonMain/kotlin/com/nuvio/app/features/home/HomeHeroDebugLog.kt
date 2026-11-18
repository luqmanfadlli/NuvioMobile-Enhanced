package com.nuvio.app.features.home

import co.touchlab.kermit.Logger
import com.nuvio.app.core.logging.InAppLogger

internal object HomeHeroDebugLog {
    private const val TAG = "Home/Hero"
    private val logger = Logger.withTag(TAG)
    private var lastMessages = mutableMapOf<String, String>()

    fun log(event: String, message: String) {
        if (lastMessages[event] == message) return
        lastMessages[event] = message
        val line = "$event $message"
        InAppLogger.info(TAG, line)
        logger.i { line }
    }

    fun heroKeys(preferences: Map<String, Boolean>): String =
        preferences.filterValues { it }.keys.sorted().joinToString(",").ifEmpty { "none" }
}
