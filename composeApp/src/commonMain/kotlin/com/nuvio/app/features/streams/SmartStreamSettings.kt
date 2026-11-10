package com.nuvio.app.features.streams

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SmartSortMode(val label: String, val description: String) {
    BEST_MATCH("Best match", "Best for this device first · recommended"),
    HIGHEST_QUALITY("Highest quality", "4K first, then HDR, then 1080p"),
    SMALLEST_FILE("Smallest file", "Starts faster and uses less data"),
    LARGEST_FILE("Largest file", "Usually the highest bitrate"),
    ADDON_ORDER("Addon order", "Grouped by addon, like before"),
}

enum class SmartResolutionLimit(val label: String) {
    AUTO("Auto"),
    P1080("1080p"),
    P4K("4K"),
}

enum class SmartMobileData(val label: String) {
    SAVE_DATA("Save data"),
    BEST_QUALITY("Best quality"),
}

enum class SmartHdrFilter(val label: String) {
    DOLBY_VISION("Dolby Vision"),
    HDR10("HDR10 / HDR10+"),
    SDR("SDR"),
}

data class SmartStreamFilters(
    val resolutions: Set<SmartResolution> = emptySet(),
    val hdr: Set<SmartHdrFilter> = emptySet(),
    val codecs: Set<SmartCodec> = emptySet(),
    val audio: Set<SmartAudio> = emptySet(),
    val sources: Set<SmartSource> = emptySet(),
    /** Largest file to show in GB, or null for any size. */
    val maxSizeGb: Int? = null,
    val instantOnly: Boolean = false,
    val hideLowQuality: Boolean = true,
) {
    /** Number of filters the user turned on (hiding low quality is the default, so it doesn't count). */
    val activeCount: Int
        get() = resolutions.size + hdr.size + codecs.size + audio.size + sources.size +
            (if (maxSizeGb != null) 1 else 0) + (if (instantOnly) 1 else 0) + (if (!hideLowQuality) 1 else 0)
}

data class SmartStreamSettings(
    val sortMode: SmartSortMode = SmartSortMode.BEST_MATCH,
    val rememberChoices: Boolean = true,
    val filters: SmartStreamFilters = SmartStreamFilters(),
    val resolutionLimit: SmartResolutionLimit = SmartResolutionLimit.AUTO,
    val mobileData: SmartMobileData = SmartMobileData.SAVE_DATA,
)

internal expect object SmartStreamSettingsStorage {
    fun load(): String?
    fun save(value: String)
}

object SmartStreamSettingsRepository {
    private val _state = MutableStateFlow(decode(runCatching { SmartStreamSettingsStorage.load() }.getOrNull()))
    val state: StateFlow<SmartStreamSettings> = _state.asStateFlow()

    fun update(transform: (SmartStreamSettings) -> SmartStreamSettings) {
        val next = transform(_state.value)
        if (next == _state.value) return
        _state.value = next
        runCatching { SmartStreamSettingsStorage.save(encode(next)) }
    }

    /** Called when the streams screen closes: without "Use for every title", the next title starts fresh. */
    fun onStreamsScreenClosed() {
        val current = _state.value
        if (current.rememberChoices) return
        update { it.copy(sortMode = SmartSortMode.BEST_MATCH, filters = SmartStreamFilters()) }
    }

    internal fun encode(settings: SmartStreamSettings): String {
        val keepChoices = settings.rememberChoices
        val sort = if (keepChoices) settings.sortMode else SmartSortMode.BEST_MATCH
        val f = if (keepChoices) settings.filters else SmartStreamFilters()
        return listOf(
            "sort=${sort.name}",
            "remember=${if (settings.rememberChoices) 1 else 0}",
            "limit=${settings.resolutionLimit.name}",
            "mobile=${settings.mobileData.name}",
            "res=${f.resolutions.joinToString(",") { it.name }}",
            "hdr=${f.hdr.joinToString(",") { it.name }}",
            "codec=${f.codecs.joinToString(",") { it.name }}",
            "audio=${f.audio.joinToString(",") { it.name }}",
            "source=${f.sources.joinToString(",") { it.name }}",
            "maxgb=${f.maxSizeGb ?: ""}",
            "instant=${if (f.instantOnly) 1 else 0}",
            "lowq=${if (f.hideLowQuality) 1 else 0}",
        ).joinToString(";")
    }

    internal fun decode(value: String?): SmartStreamSettings {
        if (value.isNullOrBlank()) return SmartStreamSettings()
        val map = value.split(';').mapNotNull { part ->
            val index = part.indexOf('=')
            if (index <= 0) null else part.substring(0, index) to part.substring(index + 1)
        }.toMap()
        return SmartStreamSettings(
            sortMode = enumOr(map["sort"], SmartSortMode.BEST_MATCH),
            rememberChoices = map["remember"] != "0",
            resolutionLimit = enumOr(map["limit"], SmartResolutionLimit.AUTO),
            mobileData = enumOr(map["mobile"], SmartMobileData.SAVE_DATA),
            filters = SmartStreamFilters(
                resolutions = enumSet<SmartResolution>(map["res"]),
                hdr = enumSet<SmartHdrFilter>(map["hdr"]),
                codecs = enumSet<SmartCodec>(map["codec"]),
                audio = enumSet<SmartAudio>(map["audio"]),
                sources = enumSet<SmartSource>(map["source"]),
                maxSizeGb = map["maxgb"]?.toIntOrNull(),
                instantOnly = map["instant"] == "1",
                hideLowQuality = map["lowq"] != "0",
            ),
        )
    }

    private inline fun <reified T : Enum<T>> enumOr(value: String?, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == value } ?: fallback

    private inline fun <reified T : Enum<T>> enumSet(value: String?): Set<T> {
        if (value.isNullOrBlank()) return emptySet()
        val names = value.split(',').toSet()
        return enumValues<T>().filter { it.name in names }.toSet()
    }
}
