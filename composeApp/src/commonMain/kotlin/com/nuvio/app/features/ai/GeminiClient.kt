package com.nuvio.app.features.ai

import com.nuvio.app.features.addons.httpPostJsonWithHeaders
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

@Serializable
internal data class AiRowsPayload(val rows: List<AiRow> = emptyList())

@Serializable
internal data class AiRow(
    /** One of the [AiRowKind] keys: for_you, theme, mood, hidden_gem. */
    val kind: String = "theme",
    val title: String = "",
    val reason: String? = null,
    val items: List<AiSuggestion> = emptyList(),
)

@Serializable
internal data class AiSuggestion(
    val name: String = "",
    val year: Int? = null,
    val type: String = "movie",
)

/** Minimal Gemini REST client (generateContent with JSON-schema constrained output). */
internal object GeminiClient {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun generateRows(
        apiKey: String,
        model: String,
        systemPrompt: String,
        userPrompt: String,
    ): AiRowsPayload {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/${model.trim()}:generateContent"
        val body = buildJsonObject {
            putJsonObject("systemInstruction") {
                putJsonArray("parts") { addJsonObject { put("text", systemPrompt) } }
            }
            putJsonArray("contents") {
                addJsonObject {
                    put("role", "user")
                    putJsonArray("parts") { addJsonObject { put("text", userPrompt) } }
                }
            }
            putJsonObject("generationConfig") {
                put("temperature", 0.9)
                put("maxOutputTokens", 16384)
                put("responseMimeType", "application/json")
                put("responseSchema", rowsSchema())
            }
        }.toString()

        // The key goes in a header (not the URL) so it never ends up in logs.
        val raw = httpPostJsonWithHeaders(
            url = url,
            body = body,
            headers = mapOf("x-goog-api-key" to apiKey),
        )
        val text = json.parseToJsonElement(raw).jsonObject["candidates"]
            ?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("content")?.jsonObject
            ?.get("parts")?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("text")?.jsonPrimitive?.contentOrNull
            ?: error("Gemini returned no content")
        return json.decodeFromString<AiRowsPayload>(text)
    }

    private fun rowsSchema(): JsonObject = buildJsonObject {
        put("type", "OBJECT")
        putJsonObject("properties") {
            putJsonObject("rows") {
                put("type", "ARRAY")
                putJsonObject("items") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {
                        putJsonObject("kind") {
                            put("type", "STRING")
                            putJsonArray("enum") {
                                add("for_you")
                                add("theme")
                                add("mood")
                                add("hidden_gem")
                            }
                        }
                        putJsonObject("title") { put("type", "STRING") }
                        putJsonObject("reason") { put("type", "STRING") }
                        putJsonObject("items") {
                            put("type", "ARRAY")
                            putJsonObject("items") {
                                put("type", "OBJECT")
                                putJsonObject("properties") {
                                    putJsonObject("name") { put("type", "STRING") }
                                    putJsonObject("year") { put("type", "INTEGER") }
                                    putJsonObject("type") { put("type", "STRING") }
                                }
                                putJsonArray("required") { add("name"); add("type") }
                            }
                        }
                    }
                    putJsonArray("required") { add("title"); add("items") }
                }
            }
        }
        putJsonArray("required") { add("rows") }
    }
}
