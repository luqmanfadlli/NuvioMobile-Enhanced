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

internal const val SUBTITLE_BLUR_REFERENCE_HEIGHT_PX = 720f
internal const val SUBTITLE_BLUR_REFERENCE_WIDTH_PX = 1280f
internal const val SUBTITLE_BLUR_SIGMA_FACTOR = 0.8493218f
internal const val SUBTITLE_BLUR_OUTSET_SIGMAS = 3f

internal fun subtitleOutlineBlurSigmaXPx(level: Int, videoWidthPx: Float): Float {
    val safeVideoWidth = videoWidthPx.takeIf { it.isFinite() && it > 0f }
        ?: SUBTITLE_BLUR_REFERENCE_WIDTH_PX
    return level.coerceIn(SUBTITLE_OUTLINE_BLUR_MIN, SUBTITLE_OUTLINE_BLUR_MAX) *
        SUBTITLE_BLUR_SIGMA_FACTOR *
        safeVideoWidth / SUBTITLE_BLUR_REFERENCE_WIDTH_PX
}

internal fun subtitleOutlineBlurSigmaYPx(level: Int, videoHeightPx: Float): Float {
    val safeVideoHeight = videoHeightPx.takeIf { it.isFinite() && it > 0f }
        ?: SUBTITLE_BLUR_REFERENCE_HEIGHT_PX
    return level.coerceIn(SUBTITLE_OUTLINE_BLUR_MIN, SUBTITLE_OUTLINE_BLUR_MAX) *
        SUBTITLE_BLUR_SIGMA_FACTOR *
        safeVideoHeight / SUBTITLE_BLUR_REFERENCE_HEIGHT_PX
}

internal data class SubtitleVideoBoundsPx(
    val width: Float,
    val height: Float,
)

internal fun subtitleVideoBoundsPx(
    containerWidthPx: Float,
    containerHeightPx: Float,
    videoAspectRatio: Float,
    resizeMode: PlayerResizeMode,
): SubtitleVideoBoundsPx {
    val width = containerWidthPx.takeIf { it.isFinite() && it > 0f } ?: 0f
    val height = containerHeightPx.takeIf { it.isFinite() && it > 0f } ?: 0f
    if (width == 0f || height == 0f || !videoAspectRatio.isFinite() || videoAspectRatio <= 0f) {
        return SubtitleVideoBoundsPx(width, height)
    }

    val containerAspectRatio = width / height
    return when (resizeMode) {
        PlayerResizeMode.Fit -> if (containerAspectRatio > videoAspectRatio) {
            SubtitleVideoBoundsPx(height * videoAspectRatio, height)
        } else {
            SubtitleVideoBoundsPx(width, width / videoAspectRatio)
        }

        PlayerResizeMode.Fill -> SubtitleVideoBoundsPx(width, height)

        PlayerResizeMode.Zoom -> if (containerAspectRatio > videoAspectRatio) {
            SubtitleVideoBoundsPx(width, width / videoAspectRatio)
        } else {
            SubtitleVideoBoundsPx(height * videoAspectRatio, height)
        }
    }
}

internal val BuiltInSubtitleFonts = listOf("default", "sans", "serif", "mono")
private val subtitleAppearanceJson = Json { ignoreUnknownKeys = true }

internal fun decodeSubtitleAppearance(value: String?): SubtitleAppearance =
    value?.let { runCatching { subtitleAppearanceJson.decodeFromString<SubtitleAppearance>(it).normalized() }.getOrNull() }
        ?: SubtitleAppearance()

internal fun SubtitleAppearance.encode(): String = subtitleAppearanceJson.encodeToString(normalized())

internal val SubtitleStyleState.requiresComposeStyledOverlay: Boolean
    get() = appearance.fontId != "default" || backgroundColor.alpha > 0f || appearance.shadowEnabled

internal val SubtitleStyleState.usesStyledOverlay: Boolean
    get() = requiresComposeStyledOverlay ||
        (outlineEnabled && outlineWidth > 0 && appearance.outlineBlur > SUBTITLE_OUTLINE_BLUR_MIN)

internal val SubtitleStyleState.mpvNativeOutlineBlurLevel: Int
    get() = if (outlineEnabled && outlineWidth > 0) {
        appearance.outlineBlur.coerceIn(SUBTITLE_OUTLINE_BLUR_MIN, SUBTITLE_OUTLINE_BLUR_MAX)
    } else {
        SUBTITLE_OUTLINE_BLUR_MIN
    }

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
