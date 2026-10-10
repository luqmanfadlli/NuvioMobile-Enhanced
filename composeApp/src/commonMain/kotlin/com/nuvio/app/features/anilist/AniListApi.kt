package com.nuvio.app.features.anilist

import com.nuvio.app.features.addons.httpRequestRaw
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

private const val ANILIST_MAX_RESPONSE_BYTES = 32 * 1024 * 1024

internal const val ANILIST_MEDIA_FIELDS =
    "id idMal format episodes seasonYear siteUrl bannerImage synonyms " +
        "title { romaji english native } coverImage { extraLarge large } startDate { year } " +
        "relations { edges { relationType node { id format } } }"

internal const val ANILIST_ENTRY_FIELDS =
    "id mediaId status progress score(format: POINT_100) updatedAt media { $ANILIST_MEDIA_FIELDS }"

internal const val ANILIST_VIEWER_QUERY =
    "query { Viewer { id name avatar { large medium } } }"

internal const val ANILIST_COLLECTION_QUERY =
    "query(\$userId: Int) { MediaListCollection(userId: \$userId, type: ANIME) { lists { entries { $ANILIST_ENTRY_FIELDS } } } }"

internal const val ANILIST_SAVE_MUTATION =
    "mutation(\$mediaId: Int, \$status: MediaListStatus, \$progress: Int, \$scoreRaw: Int) { " +
        "SaveMediaListEntry(mediaId: \$mediaId, status: \$status, progress: \$progress, scoreRaw: \$scoreRaw) { $ANILIST_ENTRY_FIELDS } }"

internal const val ANILIST_DELETE_MUTATION =
    "mutation(\$id: Int) { DeleteMediaListEntry(id: \$id) { deleted } }"

internal const val ANILIST_MEDIA_BY_MAL_QUERY =
    "query(\$mal: Int) { Media(idMal: \$mal, type: ANIME) { id } }"

internal const val ANILIST_SEARCH_QUERY =
    "query(\$search: String) { Page(perPage: 10) { media(search: \$search, type: ANIME) { " +
        "id idMal format seasonYear synonyms title { romaji english native } startDate { year } } } }"

internal class AniListApi(
    private val accessToken: () -> String?,
    private val onUnauthorized: () -> Unit,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun query(
        document: String,
        variables: JsonObject = JsonObject(emptyMap()),
        authenticated: Boolean = true,
    ): JsonObject {
        val headers = mutableMapOf(
            "Content-Type" to "application/json",
            "Accept" to "application/json",
        )
        if (authenticated) {
            val token = accessToken() ?: throw AniListApiException(401, "Not authenticated")
            headers["Authorization"] = "Bearer $token"
        }
        val body = buildJsonObject {
            put("query", document)
            put("variables", variables)
        }.toString()
        val response = try {
            httpRequestRaw(
                method = "POST",
                url = AniListConfig.API_URL,
                headers = headers,
                body = body,
                maxResponseBodyBytes = ANILIST_MAX_RESPONSE_BYTES,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            throw AniListApiException(0, error.message ?: "Network error")
        }
        if (response.status == 401) {
            if (authenticated) onUnauthorized()
            throw AniListApiException(401, "Unauthorized")
        }
        if (response.status == 429) {
            val retryAfter = response.headers.entries
                .firstOrNull { it.key.equals("Retry-After", ignoreCase = true) }
                ?.value?.trim()?.toLongOrNull()
            throw AniListApiException(429, "Rate limited", retryAfter)
        }
        val root = runCatching { json.parseToJsonElement(response.body).jsonObject }.getOrNull()
            ?: throw AniListApiException(response.status, "Invalid response")
        val errors = root["errors"] as? JsonArray
        if (!errors.isNullOrEmpty()) {
            val first = errors.first() as? JsonObject
            val status = (first?.get("status") as? JsonPrimitive)?.takeUnless { it is JsonNull }?.intOrNull
                ?: response.status
            if (status == 401 && authenticated) onUnauthorized()
            val message = (first?.get("message") as? JsonPrimitive)?.content ?: "AniList error"
            throw AniListApiException(status, message)
        }
        return root["data"] as? JsonObject
            ?: throw AniListApiException(response.status, "Missing data")
    }
}
