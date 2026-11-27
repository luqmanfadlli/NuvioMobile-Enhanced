package com.nuvio.app.features.player

enum class LegacyBottomButton(val storageId: String) {
    RESIZE("resize"),
    SPEED("speed"),
    SUBTITLES("subtitles"),
    AUDIO("audio"),
    NIGHT_MODE("night_mode"),
    SOURCES("sources"),
    CHAPTERS("chapters"),
    EPISODES("episodes"),
    LIVE_CHANNELS("live_channels"),
    QUALITY("quality"),
}

enum class LegacyTopButton(val storageId: String) {
    SUBMIT_INTRO("submit_intro"),
    EXTERNAL_PLAYER("external_player"),
    SWITCH_ENGINE("switch_engine"),
    PARTY("party"),
    CAST("cast"),
    PICTURE_IN_PICTURE("picture_in_picture"),
    ORIENTATION("orientation"),
    RATE("rate"),
    PLAYBACK_INFO("playback_info"),
    VIDEO_SETTINGS("video_settings"),
    CUSTOMIZE_BUTTONS("customize_buttons"),
    LOCK("lock"),
}

data class PlayerButtonEntry<B : Enum<B>>(
    val button: B,
    val visible: Boolean,
)

data class PlayerButtonLayout<B : Enum<B>>(val entries: List<PlayerButtonEntry<B>>) {
    val visibleButtons: List<B>
        get() = entries.filter { it.visible }.map { it.button }

    fun withVisibility(button: B, visible: Boolean): PlayerButtonLayout<B> =
        PlayerButtonLayout(entries.map { if (it.button == button) it.copy(visible = visible) else it })

    fun moved(fromIndex: Int, toIndex: Int): PlayerButtonLayout<B> {
        if (fromIndex !in entries.indices || toIndex !in entries.indices || fromIndex == toIndex) return this
        val updated = entries.toMutableList()
        updated.add(toIndex, updated.removeAt(fromIndex))
        return PlayerButtonLayout(updated)
    }

    fun encode(storageId: (B) -> String): String = entries.joinToString(",") { entry ->
        "${storageId(entry.button)}:${if (entry.visible) "v" else "h"}"
    }

    companion object {
        fun <B : Enum<B>> allVisible(buttons: List<B>): PlayerButtonLayout<B> =
            PlayerButtonLayout(buttons.map { PlayerButtonEntry(it, visible = true) })

        fun <B : Enum<B>> decode(
            value: String?,
            buttons: List<B>,
            storageId: (B) -> String,
        ): PlayerButtonLayout<B> {
            if (value.isNullOrBlank()) return allVisible(buttons)
            val parsed = value.split(',').mapNotNull { token ->
                val id = token.substringBefore(':').trim()
                val button = buttons.firstOrNull { storageId(it) == id } ?: return@mapNotNull null
                PlayerButtonEntry(button, visible = token.substringAfter(':', "v").trim() != "h")
            }.distinctBy { it.button }
            val missing = buttons.filter { button -> parsed.none { it.button == button } }
                .map { PlayerButtonEntry(it, visible = true) }
            return PlayerButtonLayout(parsed + missing)
        }
    }
}

typealias LegacyBottomButtonLayout = PlayerButtonLayout<LegacyBottomButton>
typealias LegacyTopButtonLayout = PlayerButtonLayout<LegacyTopButton>

object LegacyPlayerButtonLayouts {
    val DefaultBottom: LegacyBottomButtonLayout = PlayerButtonLayout.allVisible(LegacyBottomButton.entries)
    val DefaultTop: LegacyTopButtonLayout = PlayerButtonLayout.allVisible(LegacyTopButton.entries)

    fun encodeBottom(layout: LegacyBottomButtonLayout): String = layout.encode { it.storageId }
    fun encodeTop(layout: LegacyTopButtonLayout): String = layout.encode { it.storageId }

    fun decodeBottom(value: String?): LegacyBottomButtonLayout =
        PlayerButtonLayout.decode(value, LegacyBottomButton.entries) { it.storageId }

    fun decodeTop(value: String?): LegacyTopButtonLayout =
        PlayerButtonLayout.decode(value, LegacyTopButton.entries) { it.storageId }
}
