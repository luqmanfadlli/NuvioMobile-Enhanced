package com.nuvio.app.features.watching.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal expect object WatchedThresholdStorage {
    val isReady: Boolean
    fun load(): Int?
    fun save(value: Int)
}

object WatchedThresholdSettings {
    const val DEFAULT_PERCENT = 90
    val options: List<Int> = listOf(80, 85, 90, 95, 99)

    private val _percent = MutableStateFlow(DEFAULT_PERCENT)
    val percent: StateFlow<Int> = _percent.asStateFlow()
    private var loaded = false

    fun currentPercent(): Int {
        ensureLoaded()
        return _percent.value
    }

    fun setPercent(value: Int) {
        ensureLoaded()
        val clamped = value.coerceIn(options.first(), options.last())
        if (_percent.value == clamped) return
        _percent.value = clamped
        runCatching { WatchedThresholdStorage.save(clamped) }
    }

    private fun ensureLoaded() {
        if (loaded) return
        if (!runCatching { WatchedThresholdStorage.isReady }.getOrDefault(false)) return
        loaded = true
        _percent.value = runCatching { WatchedThresholdStorage.load() }.getOrNull()
            ?.coerceIn(options.first(), options.last())
            ?: DEFAULT_PERCENT
    }
}
