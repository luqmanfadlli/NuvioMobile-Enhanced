package com.nuvio.app.features.player

enum class PlayerToolbarButton(val storageId: String) {
    NEXT_EPISODE("next_episode"),
    SPEED("speed"),
    RESIZE("resize"),
    SUBTITLES("subtitles"),
    CHAPTERS("chapters"),
    AUDIO("audio"),
    NIGHT_MODE("night_mode"),
    QUALITY("quality"),
    SOURCES("sources"),
    EPISODES("episodes"),
    RATE("rate"),
    EXTERNAL_PLAYER("external_player"),
    PLAYBACK_INFO("playback_info"),
    SWITCH_ENGINE("switch_engine"),
    VIDEO_SETTINGS("video_settings"),
    SUBMIT_INTRO("submit_intro"),
    ;

    companion object {
        fun fromStorageId(value: String): PlayerToolbarButton? =
            entries.firstOrNull { it.storageId == value }
    }
}

enum class PlayerControlBarAlignment(val storageId: String) {
    LEFT("left"),
    CENTER("center"),
    RIGHT("right"),
    ;

    companion object {
        fun fromStorageId(value: String?): PlayerControlBarAlignment =
            entries.firstOrNull { it.storageId.equals(value?.trim(), ignoreCase = true) } ?: LEFT
    }
}

enum class PlayerToolbarPlacement(val storageCode: String) {
    BAR("v"),
    OVERFLOW("o"),
    HIDDEN("h"),
    ;

    companion object {
        fun fromStorageCode(code: String, legacyHiddenMeansOverflow: Boolean): PlayerToolbarPlacement =
            when (code.trim().lowercase()) {
                "o" -> OVERFLOW
                "h" -> if (legacyHiddenMeansOverflow) OVERFLOW else HIDDEN
                else -> BAR
            }
    }
}

data class PlayerToolbarEntry(
    val button: PlayerToolbarButton,
    val placement: PlayerToolbarPlacement = PlayerToolbarPlacement.BAR,
) {
    constructor(button: PlayerToolbarButton, visible: Boolean) : this(
        button = button,
        placement = if (visible) PlayerToolbarPlacement.BAR else PlayerToolbarPlacement.OVERFLOW,
    )

    val visible: Boolean
        get() = placement == PlayerToolbarPlacement.BAR
}

data class PlayerToolbarLayout(
    val entries: List<PlayerToolbarEntry>,
    val alignment: PlayerControlBarAlignment = PlayerControlBarAlignment.LEFT,
) {
    val visibleButtons: List<PlayerToolbarButton>
        get() = entries.filter { it.placement == PlayerToolbarPlacement.BAR }.map { it.button }

    val overflowButtons: List<PlayerToolbarButton>
        get() = entries.filter { it.placement == PlayerToolbarPlacement.OVERFLOW }.map { it.button }

    val hiddenButtons: List<PlayerToolbarButton>
        get() = entries.filter { it.placement == PlayerToolbarPlacement.HIDDEN }.map { it.button }

    val showOverflowArrow: Boolean
        get() = overflowButtons.isNotEmpty()

    fun withPlacement(button: PlayerToolbarButton, placement: PlayerToolbarPlacement): PlayerToolbarLayout =
        copy(entries = entries.map { if (it.button == button) it.copy(placement = placement) else it })

    fun withVisibility(button: PlayerToolbarButton, visible: Boolean): PlayerToolbarLayout =
        withPlacement(button, if (visible) PlayerToolbarPlacement.BAR else PlayerToolbarPlacement.HIDDEN)

    fun withAlignment(newAlignment: PlayerControlBarAlignment): PlayerToolbarLayout =
        copy(alignment = newAlignment)

    fun moved(fromIndex: Int, toIndex: Int): PlayerToolbarLayout {
        if (fromIndex !in entries.indices || toIndex !in entries.indices || fromIndex == toIndex) return this
        val updated = entries.toMutableList()
        updated.add(toIndex, updated.removeAt(fromIndex))
        return copy(entries = updated)
    }

    fun encode(): String {
        val base = entries.joinToString(",") { entry ->
            "${entry.button.storageId}:${entry.placement.storageCode}"
        }
        return "$base;v2;align=${alignment.storageId}"
    }

    companion object {
        val Default = PlayerToolbarLayout(
            listOf(
                PlayerToolbarButton.NEXT_EPISODE,
                PlayerToolbarButton.SPEED,
                PlayerToolbarButton.RESIZE,
                PlayerToolbarButton.SUBTITLES,
                PlayerToolbarButton.CHAPTERS,
                PlayerToolbarButton.NIGHT_MODE,
            ).map { PlayerToolbarEntry(it, PlayerToolbarPlacement.BAR) } +
                listOf(
                    PlayerToolbarButton.AUDIO,
                    PlayerToolbarButton.QUALITY,
                    PlayerToolbarButton.SOURCES,
                    PlayerToolbarButton.EPISODES,
                    PlayerToolbarButton.RATE,
                    PlayerToolbarButton.EXTERNAL_PLAYER,
                    PlayerToolbarButton.PLAYBACK_INFO,
                    PlayerToolbarButton.SWITCH_ENGINE,
                    PlayerToolbarButton.VIDEO_SETTINGS,
                    PlayerToolbarButton.SUBMIT_INTRO,
                ).map { PlayerToolbarEntry(it, PlayerToolbarPlacement.OVERFLOW) },
        )

        fun decode(value: String?): PlayerToolbarLayout {
            if (value.isNullOrBlank()) return Default
            val segments = value.split(';')
            val entriesPart = segments.firstOrNull().orEmpty()
            var isV2 = false
            var alignment = PlayerControlBarAlignment.LEFT
            var legacyOverflowEnabled = true
            for (meta in segments.drop(1)) {
                val trimmed = meta.trim()
                if (trimmed.equals("v2", ignoreCase = true)) {
                    isV2 = true
                    continue
                }
                val key = trimmed.substringBefore('=').trim().lowercase()
                val rawVal = trimmed.substringAfter('=', "").trim()
                when (key) {
                    "align" -> alignment = PlayerControlBarAlignment.fromStorageId(rawVal)
                    "overflow" -> legacyOverflowEnabled = rawVal == "1" || rawVal.equals("true", ignoreCase = true)
                }
            }
            val rawTokens = entriesPart.split(',')
            val hasExplicitOverflowToken = rawTokens.any { token ->
                token.substringAfter(':', "").trim().equals("o", ignoreCase = true)
            }
            val legacyHiddenMeansOverflow = !isV2 && !hasExplicitOverflowToken && legacyOverflowEnabled
            val parsed = rawTokens.mapNotNull { token ->
                val button = PlayerToolbarButton.fromStorageId(token.substringBefore(':').trim())
                    ?: return@mapNotNull null
                val rawCode = token.substringAfter(':', "v")
                PlayerToolbarEntry(
                    button = button,
                    placement = PlayerToolbarPlacement.fromStorageCode(
                        code = rawCode,
                        legacyHiddenMeansOverflow = legacyHiddenMeansOverflow,
                    ),
                )
            }.distinctBy { it.button }
            val missing = Default.entries.filter { default -> parsed.none { it.button == default.button } }
            return PlayerToolbarLayout(
                entries = parsed + missing,
                alignment = alignment,
            )
        }
    }
}
