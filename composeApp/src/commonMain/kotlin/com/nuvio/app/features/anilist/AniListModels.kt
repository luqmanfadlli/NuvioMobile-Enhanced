package com.nuvio.app.features.anilist

import com.nuvio.app.features.tracking.TrackingListStatus
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

internal const val ANILIST_STATUS_CURRENT = "CURRENT"
internal const val ANILIST_STATUS_PLANNING = "PLANNING"
internal const val ANILIST_STATUS_COMPLETED = "COMPLETED"
internal const val ANILIST_STATUS_DROPPED = "DROPPED"
internal const val ANILIST_STATUS_PAUSED = "PAUSED"
internal const val ANILIST_STATUS_REPEATING = "REPEATING"

internal const val ANILIST_WATCHING_KEY = "anilist:watching"
internal const val ANILIST_PLANNING_KEY = "anilist:planning"
internal const val ANILIST_COMPLETED_KEY = "anilist:completed"
internal const val ANILIST_PAUSED_KEY = "anilist:paused"
internal const val ANILIST_DROPPED_KEY = "anilist:dropped"
internal const val ANILIST_STATUS_GROUP = "anilist:status"

internal val AniListStatusKeys: Map<String, String> = linkedMapOf(
    ANILIST_STATUS_CURRENT to ANILIST_WATCHING_KEY,
    ANILIST_STATUS_PLANNING to ANILIST_PLANNING_KEY,
    ANILIST_STATUS_COMPLETED to ANILIST_COMPLETED_KEY,
    ANILIST_STATUS_PAUSED to ANILIST_PAUSED_KEY,
    ANILIST_STATUS_DROPPED to ANILIST_DROPPED_KEY,
)

internal fun aniListStatusKey(status: String): String =
    if (status == ANILIST_STATUS_REPEATING) ANILIST_WATCHING_KEY else AniListStatusKeys[status] ?: ANILIST_PLANNING_KEY

internal fun aniListStatusFor(key: String): String? =
    AniListStatusKeys.entries.firstOrNull { it.value == key }?.key

internal fun TrackingListStatus.toAniListStatus(): String = when (this) {
    TrackingListStatus.WATCHING -> ANILIST_STATUS_CURRENT
    TrackingListStatus.PLAN_TO_WATCH -> ANILIST_STATUS_PLANNING
    TrackingListStatus.ON_HOLD -> ANILIST_STATUS_PAUSED
    TrackingListStatus.COMPLETED -> ANILIST_STATUS_COMPLETED
    TrackingListStatus.DROPPED -> ANILIST_STATUS_DROPPED
}

internal fun String.toTrackingListStatus(): TrackingListStatus = when (this) {
    ANILIST_STATUS_CURRENT, ANILIST_STATUS_REPEATING -> TrackingListStatus.WATCHING
    ANILIST_STATUS_COMPLETED -> TrackingListStatus.COMPLETED
    ANILIST_STATUS_PAUSED -> TrackingListStatus.ON_HOLD
    ANILIST_STATUS_DROPPED -> TrackingListStatus.DROPPED
    else -> TrackingListStatus.PLAN_TO_WATCH
}

@Serializable
internal data class AniListSession(
    val accessToken: String,
    val expiresAtEpochMs: Long? = null,
    val userId: Int? = null,
    val userName: String? = null,
    val avatarUrl: String? = null,
)

@Serializable
internal data class AniListEntry(
    val entryId: Int,
    val mediaId: Int,
    val idMal: Int? = null,
    val status: String,
    val progress: Int = 0,
    val scoreRaw: Int = 0,
    val updatedAtSeconds: Long = 0L,
    val title: String,
    val poster: String? = null,
    val banner: String? = null,
    val format: String? = null,
    val episodes: Int? = null,
    val year: Int? = null,
    val siteUrl: String? = null,
    val synonyms: List<String> = emptyList(),
    val prequelIds: List<Int> = emptyList(),
    val prequelTvIds: List<Int> = emptyList(),
) {
    val isMovie: Boolean
        get() = format == "MOVIE"

    val contentId: String
        get() = idMal?.let { "mal:$it" } ?: "anilist:$mediaId"

    val contentType: String
        get() = if (isMovie) "movie" else "series"

    val providerItemId: String
        get() = "anilist:$mediaId"
}

@Serializable
internal data class AniListStoredState(
    val session: AniListSession? = null,
    val entries: List<AniListEntry> = emptyList(),
    val syncedAtEpochMs: Long? = null,
)

internal enum class AniListError {
    MISSING_CLIENT_ID,
    INVALID_CALLBACK,
    AUTHORIZATION_EXPIRED,
    SIGN_IN_FAILED,
    SYNC_FAILED,
    RATE_LIMITED,
}

internal data class AniListAuthUiState(
    val isAuthenticated: Boolean = false,
    val isAwaitingAuthorization: Boolean = false,
    val isWorking: Boolean = false,
    val userName: String? = null,
    val avatarUrl: String? = null,
    val error: AniListError? = null,
)

internal data class AniListSyncState(
    val entries: List<AniListEntry> = emptyList(),
    val hasLoaded: Boolean = false,
    val isLoading: Boolean = false,
    val error: AniListError? = null,
    val syncedAtEpochMs: Long? = null,
)

internal class AniListApiException(
    val status: Int,
    message: String,
    val retryAfterSeconds: Long? = null,
) : Exception(message)

internal sealed interface AniListAuthCallback {
    data object NotAniList : AniListAuthCallback
    data object Invalid : AniListAuthCallback
    data class Token(val accessToken: String, val expiresInSeconds: Long?) : AniListAuthCallback
}

private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

private fun JsonObject.str(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.contentOrNull?.takeIf { it.isNotBlank() }

private fun JsonObject.int(key: String): Int? =
    (this[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.intOrNull

private fun JsonObject.long(key: String): Long? =
    (this[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.longOrNull

internal fun JsonObject.toAniListEntry(): AniListEntry? {
    val media = obj("media") ?: return null
    val mediaId = media.int("id") ?: int("mediaId") ?: return null
    val titles = media.obj("title")
    val title = titles?.str("english") ?: titles?.str("romaji") ?: titles?.str("native") ?: return null
    val synonyms = buildList {
        titles?.str("romaji")?.let(::add)
        titles?.str("english")?.let(::add)
        titles?.str("native")?.let(::add)
        (media["synonyms"] as? JsonArray)?.forEach { element ->
            (element as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }?.let(::add)
        }
    }.distinct()
    val prequels = (media.obj("relations")?.get("edges") as? JsonArray).orEmpty()
        .mapNotNull { it as? JsonObject }
        .filter { it.str("relationType") == "PREQUEL" }
        .mapNotNull { it.obj("node") }
    val cover = media.obj("coverImage")
    return AniListEntry(
        entryId = int("id") ?: return null,
        mediaId = mediaId,
        idMal = media.int("idMal"),
        status = str("status") ?: ANILIST_STATUS_PLANNING,
        progress = int("progress") ?: 0,
        scoreRaw = int("score") ?: 0,
        updatedAtSeconds = long("updatedAt") ?: 0L,
        title = title,
        poster = cover?.str("extraLarge") ?: cover?.str("large"),
        banner = media.str("bannerImage"),
        format = media.str("format"),
        episodes = media.int("episodes")?.takeIf { it > 0 },
        year = media.int("seasonYear") ?: media.obj("startDate")?.int("year"),
        siteUrl = media.str("siteUrl"),
        synonyms = synonyms,
        prequelIds = prequels.mapNotNull { it.int("id") },
        prequelTvIds = prequels
            .filter { it.str("format") == "TV" || it.str("format") == "TV_SHORT" }
            .mapNotNull { it.int("id") },
    )
}
