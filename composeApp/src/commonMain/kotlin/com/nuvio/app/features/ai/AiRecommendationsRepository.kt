package com.nuvio.app.features.ai

import com.nuvio.app.core.storage.AppFileCache
import com.nuvio.app.core.storage.ProfileScopedKey
import com.nuvio.app.features.catalog.CatalogTarget
import com.nuvio.app.features.home.HomeCatalogSection
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.PosterShape
import com.nuvio.app.features.tmdb.TmdbMetadataService
import com.nuvio.app.features.tmdb.TmdbSettingsRepository
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
 * Generates home rows from the user's watch history: one Gemini call for the For you, themed,
 * mood and hidden-gem rows, plus TMDB "more like this" rows for recently watched titles.
 *
 * Gemini is only called when the cache is older than [CACHE_TTL_MS], when the user has watched
 * [NEW_TITLES_THRESHOLD]+ new titles since the last run, when the model or row plan changed, or on
 * a manual refresh. Row types, row count and titles per row are applied to the cached rows
 * without calling Gemini.
 */
object AiRecommendationsRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val json = Json { ignoreUnknownKeys = true }

    private val _uiState = MutableStateFlow(AiRecommendationsUiState())
    val uiState: StateFlow<AiRecommendationsUiState> = _uiState.asStateFlow()

    private var job: Job? = null
    private var lastTasteCount: Int = 0
    private var lastModel: String? = null
    private var lastPlan: String? = null
    /** Every row generated so far, before row types, row count and titles per row are applied. */
    private var allRows: List<CachedRow> = emptyList()

    fun onProfileChanged() {
        job?.cancel()
        job = null
        lastTasteCount = 0
        lastModel = null
        lastPlan = null
        allRows = emptyList()
        _uiState.value = AiRecommendationsUiState()
    }

    /** Re-applies the row settings to the rows already generated. Makes no Gemini call. */
    fun applySettings() {
        if (allRows.isEmpty()) return
        publish(generatedAtMs = _uiState.value.lastGeneratedAtMs)
    }

    fun refresh(force: Boolean = false) {
        val settings = AiSettingsRepository.snapshot()
        if (!settings.isUsable) {
            job?.cancel()
            job = null
            allRows = emptyList()
            _uiState.value = AiRecommendationsUiState()
            return
        }
        if (job?.isActive == true && !force) return
        job?.cancel()

        job = scope.launch {
            if (allRows.isEmpty()) {
                readCache()?.let { cached ->
                    lastTasteCount = cached.tasteCount
                    lastModel = cached.model
                    lastPlan = cached.plan
                    allRows = cached.rows
                    _uiState.update { it.copy(lastGeneratedAtMs = cached.generatedAtMs) }
                }
            }
            // Reshuffle what is shown on every visit, even when nothing is regenerated.
            if (allRows.isNotEmpty()) publish(generatedAtMs = _uiState.value.lastGeneratedAtMs)

            val profile = AiTasteProfileBuilder.build()
            val now = Clock.System.now().toEpochMilliseconds()
            val lastAt = _uiState.value.lastGeneratedAtMs
            val plan = rowPlan(settings)
            val stale = lastAt == null ||
                now - lastAt > CACHE_TTL_MS ||
                (profile.count - lastTasteCount).absoluteValue >= NEW_TITLES_THRESHOLD ||
                lastModel != settings.model ||
                lastPlan != plan
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
                    plan = plan,
                    rows = rows,
                )
                lastTasteCount = profile.count
                lastModel = settings.model
                lastPlan = plan
                writeCache(cached)
                allRows = rows
                publish(generatedAtMs = now)
                _uiState.update { it.copy(isLoading = false, errorMessage = null) }
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
            userPrompt = profile.toPrompt() + "\n\n" + rowPlanPrompt(settings.rowCount, poolSize(settings.itemsPerRow)),
        )

        val seen = HashSet<String>()
        val geminiRows = payload.rows
            .filter { it.title.isNotBlank() }
            .sortedBy { kindOrder(it.rowKind()) }

        val pool = poolSize(settings.itemsPerRow)
        val result = mutableListOf<CachedRow>()
        val counts = HashMap<AiRowKind, Int>()
        for (row in geminiRows) {
            val kind = row.rowKind()
            val cap = when (kind) {
                AiRowKind.ForYou -> 1
                AiRowKind.Themed -> settings.rowCount
                AiRowKind.Mood -> MOOD_ROW_COUNT
                AiRowKind.HiddenGems -> 1
                AiRowKind.BecauseWatched -> 0
            }
            val used = counts[kind] ?: 0
            if (used >= cap) continue
            val cachedRow = toCachedRow(row, kind, profile, seen, pool) ?: continue
            counts[kind] = used + 1
            result += cachedRow
        }

        result += becauseYouWatchedRows(profile, seen, pool)
        return result
    }

    private suspend fun toCachedRow(
        row: AiRow,
        kind: AiRowKind,
        profile: TasteProfile,
        seen: MutableSet<String>,
        itemsPerRow: Int,
    ): CachedRow? {
        val suggestions = row.items
            .filter { it.name.isNotBlank() }
            // Never recommend something the user already watched or saved.
            .filterNot { normalizeAiTitle(it.name) in profile.excludedNames }
            .take(itemsPerRow + 4)
        val unique = AiTitleResolver.resolveAll(suggestions)
            .filterNotNull()
            .distinctBy { it.id }
            // A title should appear in only one row.
            .filter { it.id !in seen }
        val picked = if (kind == AiRowKind.HiddenGems) {
            // Prefer under-the-radar titles; fall back to everything if too few qualify.
            unique.filter { (it.voteCount ?: 0) < HIDDEN_GEM_MAX_VOTES }
                .takeIf { it.size >= MIN_ITEMS_PER_ROW } ?: unique
        } else {
            unique
        }
        val resolved = picked.take(itemsPerRow)
        if (resolved.size < MIN_ITEMS_PER_ROW) return null
        seen += resolved.map { it.id }
        return CachedRow(
            kind = kind.key,
            title = row.title.trim(),
            reason = row.reason?.trim()?.takeIf { it.isNotBlank() },
            items = resolved.map(CachedPreview::from),
        )
    }

    /**
     * One row per recently watched title, filled with TMDB's "more like this" results for it.
     * Uses the same exclusion and no-duplicate rules as the Gemini rows.
     */
    private suspend fun becauseYouWatchedRows(
        profile: TasteProfile,
        seen: MutableSet<String>,
        itemsPerRow: Int,
    ): List<CachedRow> {
        val tmdbSettings = TmdbSettingsRepository.snapshot()
        if (!tmdbSettings.enabled) return emptyList()

        return profile.watched
            .filter { !it.id.isNullOrBlank() }
            .take(BECAUSE_ROW_COUNT)
            .mapNotNull { seed ->
                val related = (1..2).flatMap { page ->
                    TmdbMetadataService.fetchMoreLikeThisPage(
                        itemId = seed.id.orEmpty(),
                        itemType = seed.type,
                        page = page,
                        settings = tmdbSettings,
                    ).items
                }
                val resolved = related
                    .filterNot { normalizeAiTitle(it.name) in profile.excludedNames }
                    .filter { it.id !in seen }
                    .distinctBy { it.id }
                    .take(itemsPerRow)
                if (resolved.size < MIN_ITEMS_PER_ROW) return@mapNotNull null
                seen += resolved.map { it.id }
                CachedRow(
                    kind = AiRowKind.BecauseWatched.key,
                    title = "Because you watched ${seed.name}",
                    items = resolved.map(CachedPreview::from),
                )
            }
    }

    /** Pushes the cached rows, filtered by the current row settings, to the UI. */
    private fun publish(generatedAtMs: Long?) {
        val settings = AiSettingsRepository.snapshot()
        _uiState.update {
            it.copy(
                sections = visibleSections(settings),
                lastGeneratedAtMs = generatedAtMs,
            )
        }
    }

    private fun visibleSections(settings: AiRecommendationSettings): List<HomeCatalogSection> {
        val enabledKeys = settings.enabledRowKinds.map { it.key }.toSet()
        val enabled = allRows.filter { it.kind in enabledKeys }
        val forYou = enabled.filter { it.kind == AiRowKind.ForYou.key }
        // A different random set of themed rows each time, and the rest in shuffled order.
        val themed = enabled.filter { it.kind == AiRowKind.Themed.key }.shuffled().take(settings.rowCount)
        val others = enabled.filter {
            it.kind != AiRowKind.ForYou.key && it.kind != AiRowKind.Themed.key
        }
        val ordered = forYou + (themed + others).shuffled()
        return ordered.mapIndexed { index, row -> row.toSection(index, settings.itemsPerRow) }
    }

    private fun CachedRow.toSection(index: Int, itemsPerRow: Int): HomeCatalogSection {
        // A different random subset of the pool each time the rows are shown.
        val shown = this.items.shuffled().take(itemsPerRow).map(CachedPreview::toMetaPreview)
        val key = "ai_row_$index"
        return HomeCatalogSection(
            key = key,
            title = title,
            subtitle = reason ?: defaultSubtitle(),
            addonName = "AI",
            // Required by the model, but never opened: view-all is disabled for AI rows.
            target = CatalogTarget.Addon(
                manifestUrl = "ai://recommendations",
                contentType = shown.firstOrNull()?.type ?: "movie",
                catalogId = key,
            ),
            items = shown,
            availableItemCount = shown.size,
            hasMore = false,
        )
    }

    private fun CachedRow.defaultSubtitle(): String = when (AiRowKind.fromKey(kind)) {
        AiRowKind.ForYou -> "Picked for your taste"
        AiRowKind.BecauseWatched -> "Similar titles from TMDB"
        else -> "AI recommendations"
    }

    private fun AiRow.rowKind(): AiRowKind =
        AiRowKind.fromKey(kind)?.takeIf { it != AiRowKind.BecauseWatched } ?: AiRowKind.Themed

    private fun kindOrder(kind: AiRowKind): Int = when (kind) {
        AiRowKind.ForYou -> 0
        AiRowKind.Themed -> 1
        AiRowKind.Mood -> 2
        AiRowKind.HiddenGems -> 3
        AiRowKind.BecauseWatched -> 4
    }

    /** How many titles to keep per row. Shown rows are a random sample of this pool. */
    private fun poolSize(itemsPerRow: Int): Int = (itemsPerRow + POOL_EXTRA).coerceAtMost(POOL_MAX)

    /** What decides whether a cached generation is still valid. */
    private fun rowPlan(settings: AiRecommendationSettings): String =
        "${settings.rowCount}x${settings.itemsPerRow}"

    private fun rowPlanPrompt(rowCount: Int, itemsPerRow: Int): String = buildString {
        appendLine("Return these rows. Give every row a \"kind\" field with one of these values:")
        appendLine("- Exactly one row with kind \"for_you\": broad, appealing picks across all of the viewer's taste.")
        appendLine("- $rowCount rows with kind \"theme\": specific themed rows.")
        appendLine("- $MOOD_ROW_COUNT rows with kind \"mood\": built around a mood or occasion, such as \"Late-night thrillers\" or \"Easy weekend comfort\".")
        appendLine("- One row with kind \"hidden_gem\": lesser-known, under-the-radar titles, not widely known blockbusters.")
        appendLine("Each row has $itemsPerRow titles.")
        append("Vary your picks: avoid the most obvious choices and do not repeat the same well-known titles.")
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
    private const val BECAUSE_ROW_COUNT = 3
    private const val MOOD_ROW_COUNT = 2
    private const val MIN_ITEMS_PER_ROW = 4
    private const val HIDDEN_GEM_MAX_VOTES = 2000
    private const val POOL_EXTRA = 10
    private const val POOL_MAX = 40

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
    val plan: String = "",
    val rows: List<CachedRow>,
)

@Serializable
private data class CachedRow(
    val kind: String = AiRowKind.Themed.key,
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
