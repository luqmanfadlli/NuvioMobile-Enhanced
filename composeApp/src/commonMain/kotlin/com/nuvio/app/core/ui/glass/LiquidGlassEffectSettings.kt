package com.nuvio.app.core.ui.glass

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.roundToInt

@Serializable
internal data class LiquidGlassEffectSettings(
    val cornerRadiusPercent: Float,
    val blurRadiusDp: Float,
    val refractionHeightDp: Float,
    val refractionAmountDp: Float,
    val chromaticAberration: Boolean,
    val depthEffect: Boolean,
    val tintOpacity: Float,
    val affectTabIcons: Boolean = false,
    val affectFloatingBar: Boolean = false,
) {
    fun sanitized(
        defaults: LiquidGlassEffectSettings,
        limits: LiquidGlassEffectLimits,
    ): LiquidGlassEffectSettings = copy(
        cornerRadiusPercent = safeRange(cornerRadiusPercent, 50f, defaults.cornerRadiusPercent),
        blurRadiusDp = safeRange(blurRadiusDp, limits.maxBlurRadiusDp, defaults.blurRadiusDp),
        refractionHeightDp = safeRange(
            refractionHeightDp,
            limits.maxRefractionHeightDp,
            defaults.refractionHeightDp,
        ),
        refractionAmountDp = safeRange(
            refractionAmountDp,
            limits.maxRefractionAmountDp,
            defaults.refractionAmountDp,
        ),
        tintOpacity = safeRange(tintOpacity, 1f, defaults.tintOpacity),
    )

    private fun safeRange(value: Float, maximum: Float, fallback: Float): Float =
        (value.takeIf { it.isFinite() } ?: fallback).coerceIn(0f, maximum)
}

internal data class LiquidGlassEffectLimits(
    val maxBlurRadiusDp: Float,
    val maxRefractionHeightDp: Float,
    val maxRefractionAmountDp: Float,
)

/**
 * These optional sources exist to add refraction targets to the selection lens, not to extend its
 * blur. If either refraction dimension is zero, Kyant's lens effect is a no-op and the sources
 * should not replace normal foreground content.
 */
internal fun LiquidGlassEffectSettings.hasActiveRefraction(): Boolean =
    refractionHeightDp > 0f && refractionAmountDp > 0f

/** Capture tab content only when requested and when refraction is active. */
internal fun LiquidGlassEffectSettings.shouldCaptureTabIcons(): Boolean =
    affectTabIcons && hasActiveRefraction()

/** Capture the floating-bar surface only when requested and when refraction is active. */
internal fun LiquidGlassEffectSettings.shouldCaptureFloatingBar(): Boolean =
    affectFloatingBar && hasActiveRefraction()

internal object LiquidGlassEffectDefaults {
    val floatingBar = LiquidGlassEffectSettings(
        cornerRadiusPercent = 50f,
        blurRadiusDp = 10f,
        refractionHeightDp = 20f,
        refractionAmountDp = 20f,
        chromaticAberration = false,
        depthEffect = false,
        tintOpacity = 0.4f,
    )
    val selectionLens = LiquidGlassEffectSettings(
        cornerRadiusPercent = 50f,
        blurRadiusDp = 0f,
        refractionHeightDp = 12f,
        refractionAmountDp = 20f,
        chromaticAberration = false,
        depthEffect = true,
        tintOpacity = 0f,
        affectTabIcons = true,
        affectFloatingBar = true,
    )

    val floatingBarLimits = LiquidGlassEffectLimits(
        maxBlurRadiusDp = 32f,
        maxRefractionHeightDp = 48f,
        maxRefractionAmountDp = 48f,
    )
    val selectionLensLimits = LiquidGlassEffectLimits(
        maxBlurRadiusDp = 24f,
        maxRefractionHeightDp = 32f,
        maxRefractionAmountDp = 32f,
    )
}

private val liquidGlassSettingsJson = Json {
    ignoreUnknownKeys = true
}

internal fun encodeLiquidGlassEffectSettings(
    settings: LiquidGlassEffectSettings,
    defaults: LiquidGlassEffectSettings,
    limits: LiquidGlassEffectLimits,
): String = liquidGlassSettingsJson.encodeToString(settings.sanitized(defaults, limits))

internal fun decodeLiquidGlassEffectSettings(
    encoded: String?,
    defaults: LiquidGlassEffectSettings,
    limits: LiquidGlassEffectLimits,
): LiquidGlassEffectSettings {
    if (encoded.isNullOrBlank()) return defaults

    val values = runCatching {
        liquidGlassSettingsJson.parseToJsonElement(encoded).jsonObject
    }.getOrNull() ?: return defaults

    return defaults.copy(
        cornerRadiusPercent = values.floatOrDefault("cornerRadiusPercent", defaults.cornerRadiusPercent),
        blurRadiusDp = values.floatOrDefault("blurRadiusDp", defaults.blurRadiusDp),
        refractionHeightDp = values.floatOrDefault("refractionHeightDp", defaults.refractionHeightDp),
        refractionAmountDp = values.floatOrDefault("refractionAmountDp", defaults.refractionAmountDp),
        chromaticAberration = values.booleanOrDefault("chromaticAberration", defaults.chromaticAberration),
        depthEffect = values.booleanOrDefault("depthEffect", defaults.depthEffect),
        tintOpacity = values.floatOrDefault("tintOpacity", defaults.tintOpacity),
        affectTabIcons = values.booleanOrDefault("affectTabIcons", defaults.affectTabIcons),
        affectFloatingBar = values.booleanOrDefault("affectFloatingBar", defaults.affectFloatingBar),
    ).sanitized(defaults, limits)
}

private fun kotlinx.serialization.json.JsonObject.floatOrDefault(
    key: String,
    default: Float,
): Float {
    val value = runCatching { this[key]?.jsonPrimitive?.floatOrNull }.getOrNull()
    return value?.takeIf { it.isFinite() } ?: default
}

private fun kotlinx.serialization.json.JsonObject.booleanOrDefault(
    key: String,
    default: Boolean,
): Boolean = runCatching { this[key]?.jsonPrimitive?.booleanOrNull }.getOrNull() ?: default

/**
 * Normalize glass corner percentages to the integer value used by RoundedCornerShape.
 * Clamp infinities to the supported range and map NaN to the fully rounded default.
 */
internal fun effectiveLiquidGlassCornerRadiusPercent(value: Float): Int =
    (value.takeIf { !it.isNaN() } ?: 50f).coerceIn(0f, 50f).roundToInt()

internal fun migrateLegacyNavBarGlassEffects(
    raw: String?,
): Pair<LiquidGlassEffectSettings, LiquidGlassEffectSettings>? {
    if (!LiquidGlassStyle.isLegacyEncoding(raw)) return null
    val parts = raw!!.split(LiquidGlassStyle.Separator)
    fun int(index: Int): Int? = parts.getOrNull(index)?.toIntOrNull()
    val chromatic = parts.getOrNull(6) == "1"
    val bar = LiquidGlassEffectDefaults.floatingBar.let { d ->
        d.copy(
            blurRadiusDp = int(2)?.toFloat() ?: d.blurRadiusDp,
            refractionHeightDp = int(3)?.toFloat() ?: d.refractionHeightDp,
            refractionAmountDp = int(3)?.toFloat() ?: d.refractionAmountDp,
            tintOpacity = int(4)?.let { it / 100f } ?: d.tintOpacity,
            chromaticAberration = chromatic,
        ).sanitized(d, LiquidGlassEffectDefaults.floatingBarLimits)
    }
    val lens = LiquidGlassEffectDefaults.selectionLens.copy(chromaticAberration = chromatic)
    return bar to lens
}
