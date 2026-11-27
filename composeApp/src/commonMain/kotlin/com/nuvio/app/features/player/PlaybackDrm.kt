@file:OptIn(ExperimentalEncodingApi::class)

package com.nuvio.app.features.player

import kotlin.concurrent.Volatile
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

enum class PlaybackDrmScheme { ClearKey, Widevine }

data class PlaybackDrm(
    val scheme: PlaybackDrmScheme,
    val clearKeys: Map<String, String> = emptyMap(),
    val licenseUrl: String? = null,
    val licenseHeaders: Map<String, String> = emptyMap(),
) {
    val hasLocalClearKeys: Boolean
        get() = scheme == PlaybackDrmScheme.ClearKey && clearKeys.isNotEmpty()

    val logDescription: String
        get() = when {
            hasLocalClearKeys -> "clearkey keys=${clearKeys.size}"
            licenseUrl != null -> "${scheme.name.lowercase()} license"
            else -> scheme.name.lowercase()
        }
}

object PlaybackDrmRegistry {
    @Volatile
    private var byUrl: Map<String, PlaybackDrm> = emptyMap()

    fun register(url: String, drm: PlaybackDrm?) {
        val key = url.trim()
        if (key.isEmpty()) return
        val current = byUrl
        if (drm == null) {
            if (key in current) byUrl = current - key
        } else if (current[key] != drm) {
            byUrl = current + (key to drm)
        }
    }

    fun forUrl(url: String?): PlaybackDrm? = url?.trim()?.let { byUrl[it] }
}

internal const val MpvBaseDemuxerLavfOptions = "protocol_whitelist=[file,crypto,data,http,https,tcp,tls]"

internal fun PlaybackDrm?.mpvDemuxerLavfOptions(): String {
    val keys = this?.takeIf { it.hasLocalClearKeys }?.clearKeys.orEmpty()
    return when {
        keys.isEmpty() -> MpvBaseDemuxerLavfOptions
        keys.size == 1 -> "$MpvBaseDemuxerLavfOptions,cenc_decryption_key=${keys.values.first()}"
        else -> keys.entries.joinToString(
            separator = ":",
            prefix = "$MpvBaseDemuxerLavfOptions,cenc_decryption_keys=[",
            postfix = "]",
        ) { (kid, key) -> "$kid=$key" }
    }
}

internal fun PlaybackDrm?.isUnsupportedByMpv(): Boolean = this != null && !hasLocalClearKeys

private val base64UrlNoPadding = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)

internal fun PlaybackDrm.clearKeyLicenseJson(): String =
    clearKeys.entries.joinToString(
        separator = ",",
        prefix = "{\"keys\":[",
        postfix = "],\"type\":\"temporary\"}",
    ) { (kid, key) ->
        val encodedKid = base64UrlNoPadding.encode(kid.hexToBytes())
        val encodedKey = base64UrlNoPadding.encode(key.hexToBytes())
        "{\"kty\":\"oct\",\"kid\":\"$encodedKid\",\"k\":\"$encodedKey\"}"
    }

fun parseKodiDrm(
    licenseType: String?,
    licenseKey: String?,
    parseHeaders: (String) -> Map<String, String> = { emptyMap() },
): PlaybackDrm? {
    val key = licenseKey?.trim().orEmpty()
    if (key.isEmpty()) return null
    val isUrl = key.startsWith("http://", ignoreCase = true) || key.startsWith("https://", ignoreCase = true)
    val scheme = when (licenseType?.trim()?.lowercase()) {
        "clearkey", "org.w3.clearkey" -> PlaybackDrmScheme.ClearKey
        "widevine", "com.widevine.alpha" -> PlaybackDrmScheme.Widevine
        null, "" -> if (isUrl) return null else PlaybackDrmScheme.ClearKey
        else -> return null
    }
    if (isUrl) {
        val parts = key.split('|')
        return PlaybackDrm(
            scheme = scheme,
            licenseUrl = parts.first().trim(),
            licenseHeaders = parts.getOrNull(1)?.takeIf(String::isNotBlank)?.let(parseHeaders).orEmpty(),
        )
    }
    if (scheme != PlaybackDrmScheme.ClearKey) return null
    val keys = parseClearKeys(key.substringBefore('|'))
    return if (keys.isEmpty()) null else PlaybackDrm(PlaybackDrmScheme.ClearKey, clearKeys = keys)
}

internal fun parseClearKeys(value: String): Map<String, String> {
    val trimmed = value.trim()
    val pairs = if (trimmed.startsWith("{")) {
        parseClearKeyJson(trimmed)
    } else {
        trimmed.split(',', ';', ' ').mapNotNull { pair ->
            val separator = pair.indexOf(':')
            if (separator <= 0) null else pair.substring(0, separator) to pair.substring(separator + 1)
        }
    }
    return pairs.mapNotNull { (kid, key) ->
        val normalizedKid = normalizeKeyHex(kid) ?: return@mapNotNull null
        val normalizedKey = normalizeKeyHex(key) ?: return@mapNotNull null
        normalizedKid to normalizedKey
    }.toMap()
}

private fun parseClearKeyJson(value: String): List<Pair<String, String>> = runCatching {
    val root = Json.parseToJsonElement(value) as? JsonObject ?: return emptyList()
    val keys = root["keys"] as? JsonArray
    if (keys != null) {
        keys.mapNotNull { entry ->
            val obj = entry as? JsonObject ?: return@mapNotNull null
            val kid = obj["kid"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val key = obj["k"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            kid to key
        }
    } else {
        root.mapNotNull { (kid, key) -> (key as? JsonPrimitive)?.contentOrNull?.let { kid to it } }
    }
}.getOrDefault(emptyList())

private fun normalizeKeyHex(value: String): String? {
    val raw = value.trim().trim('"')
    val hex = raw.replace("-", "")
    if (hex.length == 32 && hex.all { it.isHexDigit() }) return hex.lowercase()
    val bytes = runCatching {
        Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL).decode(raw.replace('+', '-').replace('/', '_'))
    }.getOrNull() ?: return null
    if (bytes.size != 16) return null
    return bytes.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
}

private fun Char.isHexDigit(): Boolean = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

private fun String.hexToBytes(): ByteArray = ByteArray(length / 2) { index ->
    substring(index * 2, index * 2 + 2).toInt(16).toByte()
}
