package com.nuvio.app.features.simkl

import com.nuvio.app.core.build.AppVersionConfig
import io.ktor.http.encodeURLParameter

internal const val SIMKL_API_BASE_URL = "https://api.simkl.com"

// Simkl AUTH V2 (OAuth 2.0 + PKCE). The authorize page lives on the browser host; every other
// /oauth2/* endpoint is served from the API host. See https://api.simkl.org/api-reference/auth-v2
internal const val SIMKL_AUTHORIZE_URL = "https://simkl.com/oauth2/authorize"
internal const val SIMKL_OAUTH2_TOKEN_PATH = "/oauth2/token"
internal const val SIMKL_OAUTH2_DEVICE_PATH = "/oauth2/device"
internal const val SIMKL_OAUTH2_REVOKE_PATH = "/oauth2/revoke"

/** Nuvio both reads and writes the user's Simkl library, history, ratings and scrobbles. */
internal const val SIMKL_OAUTH2_SCOPE = "media:read media:write"

internal val simklAppVersion: String
    get() = AppVersionConfig.VERSION_NAME.ifBlank { "dev" }

internal fun buildSimklApiUrl(
    path: String,
    query: Map<String, String> = emptyMap(),
): String {
    val normalizedPath = path.trim().let { value ->
        if (value.startsWith('/')) value else "/$value"
    }
    val parameters = linkedMapOf<String, String>().apply {
        putAll(query)
        put("client_id", SimklConfig.CLIENT_ID)
        put("app-name", SimklConfig.APP_NAME)
        put("app-version", simklAppVersion)
    }
    return buildString {
        append(SIMKL_API_BASE_URL)
        append(normalizedPath)
        append('?')
        append(
            parameters.entries.joinToString("&") { (key, value) ->
                "${key.encodeURLParameter()}=${value.encodeURLParameter()}"
            },
        )
    }
}

internal fun simklRequestHeaders(
    accessToken: String? = null,
    contentTypeJson: Boolean = false,
): Map<String, String> = buildMap {
    put("User-Agent", "${SimklConfig.APP_NAME}/$simklAppVersion")
    put("Accept", "application/json")
    accessToken?.trim()?.takeIf(String::isNotBlank)?.let { token ->
        put("Authorization", "Bearer $token")
    }
    if (contentTypeJson) put("Content-Type", "application/json")
}
