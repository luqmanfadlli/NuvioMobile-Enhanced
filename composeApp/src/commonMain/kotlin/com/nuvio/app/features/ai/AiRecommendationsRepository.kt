package com.nuvio.app.features.ai

import com.nuvio.app.core.storage.AppFileCache
import com.nuvio.app.core.storage.ProfileScopedKey
import com.nuvio.app.features.catalog.CatalogTarget
import com.nuvio.app.features.home.HomeCatalogSection
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.PosterShape
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.absoluteValue
import kotlin.time.Clock

data class AiRecommendationsUiState(
    val sections: List<HomeCatalogSection> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val lastGeneratedAtMs: Long? = null,
)

/**
 * Generates themed home rows from the user's watch history with Gemini.
 *
 * Gemini is only called when the cache is older than [CACHE_TTL_MS], when the user has watched
 * [NEW_TITLES_THRESHOLD]+ new titles since the last run, when the model changed, or on a manual
 * refresh. Every other [refresh] call just restores the cached rows.
 */
object AiRecommendationsRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val json = Json { ignoreUnknownKeys = true }

    private val _uiState = MutableStateFlow(AiRecommendationsUiState())
    val uiState: StateFlow<AiRecommendationsUiState> = _uiState.asStateFlow()

    private var job: Job? = null
    private var lastTasteCount: Int = 0
    private var lastModel: String? = null

    fun onProfileChanged() {
        job?.cancel()
        job = null
        lastTasteCount = 0
        lastModel = null
        _uiState.value = AiRecommendationsUiState()
    }

    fun refresh(force: Boolean = false) {
        val settings = AiSettingsRepository.snapshot()
        if (!settings.isUsable) {
            job?.cancel()
            job = null
            _uiState.value = AiRecommendationsUiState()
            return
        }
        if (job?.isActive == true && !force) return
        job?.cancel()

        job = scope.launch {
            if (_uiState.value.sections.isEmpty()) {
                readCache()?.let { cached ->
                    lastTasteCount = cached.tasteCount
                    lastModel = cached.model
                    _uiState.value = AiRecommendationsUiState(
                        sections = cached.rows.toSections(),
                        lastGeneratedAtMs = cached.generatedAtMs,
                    )
                }
            }

            val profile = AiTasteProfileBuilder.build()
            val now = Clock.System.now().toEpochMilliseconds()
            val lastAt = _uiState.value.lastGeneratedAtMs
            val stale = lastAt == null ||
                now - lastAt > CACHE_TTL_MS ||
                (profile.count - lastTasteCount).absoluteValue >= NEW_TITLES_THRESHOLD ||
                lastModel != settings.model
            if (!force && !stale) return@launch

            if (!profile.isMeaningful) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = if (it.sections.isEmpty()) {
                            "Watch at least $AI_MIN_WATCHED_TITLES titles to get AI recommendations."
                        } else {
                            null
                        },
                    )
                }
                return@launch
            }

            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val rows = generate(profile, settings)
                if (rows.isEmpty()) {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "No recommendations could be matched. Try refreshing.")
                    }
                    return@launch
                }
                val cached = CachedRecommendations(
                    generatedAtMs = now,
                    tasteCount = profile.count,
                    model = settings.model,
                    rows = rows,
                )
                lastTasteCount = profile.count
                lastModel = settings.model
                writeCache(cached)
                _uiState.value = AiRecommendationsUiState(
                    sections = rows.toSections(),
                    lastGeneratedAtMs = now,
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = error.message ?: "AI recommendations failed.")
                }
            }
        }
    }

    private suspend fun generate(
        profile: TasteProfile,
        settings: AiRecommendationSettings,
    ): List<CachedRow> {
        val payload = GeminiClient.generateRows(
            apiKey = settings.apiKey,
            model = settings.model,
            systemPrompt = SYSTEM_PROMPT,
            userPrompt = profile.toPrompt(rowCount = ROW_COUNT, itemsPerRow = ITEMS_PER_ROW),
        )

        val seen = HashSet<String>()
        return payload.rows
            .filter { it.title.isNotBlank() }
            .take(ROW_COUNT)
            .mapNotNull { row ->
                val suggestions = row.items
                    .filter { it.name.isNotBlank() }
                    // Never recommend something the user already watched or saved.
                    .filterNot { normalizeAiTitle(it.name) in profile.excludedNames }
                    .take(ITEMS_PER_ROW + 4)
                val resolved = AiTitleResolver.resolveAll(suggestions)
                    .filterNotNull()
                    // A title should appear in only one row.
                    .filter { seen.add(it.id) }
                    .take(ITEMS_PER_ROW)
                if (resolved.size < MIN_ITEMS_PER_ROW) {
                    null
                } else {
                    CachedRow(
                        title = row.title.trim(),
                        reason = row.reason?.trim()?.takeIf { it.isNotBlank() },
                        items = resolved.map(CachedPreview::from),
                    )
                }
            }
    }

    private fun List<CachedRow>.toSections(): List<HomeCatalogSection> =
        mapIndexed { index, row ->
            val items = row.items.map(CachedPreview::toMetaPreview)
            val key = "ai_row_$index"
            HomeCatalogSection(
                key = key,
                title = row.title,
                subtitle = row.reason ?: "AI recommendations",
                addonName = "AI",
                // Required by the model, but never opened: view-all is disabled for AI rows.
                target = CatalogTarget.Addon(
                    manifestUrl = "ai://recommendations",
                    contentType = items.firstOrNull()?.type ?: "movie",
                    catalogId = key,
                ),
                items = items,
                availableItemCount = items.size,
                hasMore = false,
            )
        }

    private fun cachePath(): String = ProfileScopedKey.of("ai_recommendations") + ".json"

    private fun readCache(): CachedRecommendations? =
        runCatchingNonCancel {
            AppFileCache.read(cachePath())?.let { json.decodeFromString<CachedRecommendations>(it) }
        }

    private fun writeCache(cached: CachedRecommendations) {
        runCatchingNonCancel { AppFileCache.write(cachePath(), json.encodeToString(CachedRecommendations.serializer(), cached)) }
    }

    private const val CACHE_TTL_MS = 24L * 60 * 60 * 1000
    private const val NEW_TITLES_THRESHOLD = 5
    private const val ROW_COUNT = 5
    private const val ITEMS_PER_ROW = 12
    private const val MIN_ITEMS_PER_ROW = 4

    private const val SYSTEM_PROMPT =
        "You are a film and TV recommendation engine inside a streaming app. " +
            "Only suggest real, released titles that exist - never invent or guess titles. " +
            "Give each row a short, specific theme title (max 5 words) and a one-sentence reason " +
            "that references the viewer's taste. Mix well-known and lesser-known picks, vary the " +
            "decades, and use movies and series where they fit the theme. For each item set " +
            "type to exactly \"movie\" or \"series\" and give the original release year."
}

@Serializable
private data class CachedRecommendations(
    val generatedAtMs: Long,
    val tasteCount: Int,
    val model: String,
    val rows: List<CachedRow>,
)

@Serializable
private data class CachedRow(
    val title: String,
    val reason: String? = null,
    val items: List<CachedPreview>,
)

@Serializable
private data class CachedPreview(
    val id: String,
    val type: String,
    val name: String,
    val poster: String? = null,
    val banner: String? = null,
    val description: String? = null,
    val releaseInfo: String? = null,
    val rawReleaseDate: String? = null,
    val popularity: Double? = null,
    val voteCount: Int? = null,
    val imdbRating: String? = null,
) {
    fun toMetaPreview() = MetaPreview(
        id = id,
        type = type,
        name = name,
        poster = poster,
        banner = banner,
        posterShape = PosterShape.Poster,
        description = description,
        releaseInfo = releaseInfo,
        rawReleaseDate = rawReleaseDate,
        popularity = popularity,
        voteCount = voteCount,
        imdbRating = imdbRating,
    )

    companion object {
        fun from(meta: MetaPreview) = CachedPreview(
            id = meta.id,
            type = meta.type,
            name = meta.name,
            poster = meta.poster,
            banner = meta.banner,
            description = meta.description,
            releaseInfo = meta.releaseInfo,
            rawReleaseDate = meta.rawReleaseDate,
            popularity = meta.popularity,
            voteCount = meta.voteCount,
            imdbRating = meta.imdbRating,
        )
    }
}
