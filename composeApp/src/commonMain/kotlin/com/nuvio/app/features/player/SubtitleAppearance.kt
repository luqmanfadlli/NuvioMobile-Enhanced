package com.nuvio.app.features.player

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class SubtitleAppearance(
    val fontId: String = "default",
    val backgroundRadius: Int = 0,
    val shadowEnabled: Boolean = false,
    val shadowOpacity: Int = 75,
    val shadowSize: Int = 2,
    // Softens only the outline layer; zero keeps the existing crisp outline.
    val outlineBlur: Int = 0,
) {
    fun normalized(): SubtitleAppearance = copy(
        fontId = fontId.takeIf { it in BuiltInSubtitleFonts || it.matches(Regex("custom-[a-f0-9-]{36}")) } ?: "default",
        backgroundRadius = backgroundRadius.coerceIn(0, 24),
        shadowOpacity = shadowOpacity.coerceIn(0, 100),
        shadowSize = shadowSize.coerceIn(1, 8),
        outlineBlur = outlineBlur.coerceIn(SUBTITLE_OUTLINE_BLUR_MIN, SUBTITLE_OUTLINE_BLUR_MAX),
    )
}

const val SUBTITLE_OUTLINE_BLUR_MIN = 0
const val SUBTITLE_OUTLINE_BLUR_MAX = 10

internal val BuiltInSubtitleFonts = listOf("default", "sans", "serif", "mono")
private val subtitleAppearanceJson = Json { ignoreUnknownKeys = true }

internal fun decodeSubtitleAppearance(value: String?): SubtitleAppearance =
    value?.let { runCatching { subtitleAppearanceJson.decodeFromString<SubtitleAppearance>(it).normalized() }.getOrNull() }
        ?: SubtitleAppearance()

internal fun SubtitleAppearance.encode(): String = subtitleAppearanceJson.encodeToString(normalized())

internal val SubtitleStyleState.usesStyledOverlay: Boolean
    get() = appearance.fontId != "default" || backgroundColor.alpha > 0f || appearance.shadowEnabled ||
        (outlineEnabled && appearance.outlineBlur > SUBTITLE_OUTLINE_BLUR_MIN)

internal expect fun supportsNativeSubtitleBlur(): Boolean

object SubtitleRendering {
    fun supportsTextOverlay(codec: String): Boolean = codec.lowercase() in setOf(
        "subrip", "srt", "webvtt", "text", "mov_text", "microdvd", "mpl2", "subviewer", "subviewer1", "sami", "realtext",
    )
}

internal fun SubtitleStyleState.resetAppearance(): SubtitleStyleState = SubtitleStyleState.DEFAULT.copy(
    stripSdh = stripSdh,
    useForcedSubtitles = useForcedSubtitles,
    showOnlyPreferredLanguages = showOnlyPreferredLanguages,
)
