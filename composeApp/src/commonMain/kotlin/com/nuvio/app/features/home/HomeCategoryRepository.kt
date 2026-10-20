package com.nuvio.app.features.home

import com.nuvio.app.features.profiles.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.random.Random

const val HOME_CATEGORY_HOME_ID = "home"
const val HOME_CATEGORY_MOVIES_ID = "movies"
const val HOME_CATEGORY_TV_ID = "tv"
const val HOME_CATEGORY_LIVE_TV_ID = "livetv"
const val HOME_CATEGORY_ANIME_ID = "anime"
const val HOME_CATEGORY_DISCOVER_ID = "discover"

@Serializable
enum class HomeCategoryKind {
    /** Shows rows built from the catalogs the user assigned to this category. */
    Catalogs,

    /** Embeds the Live TV screen. */
    LiveTv,

    /** Embeds the Discover / Search screen. */
    Discover,
}

/**
 * One entry of the top navigation bar.
 *
 * [catalogKeys] is the ordered list of home catalog keys (the same keys used by
 * [HomeCatalogSettingsItem.key], including `collection_<id>` keys) that this category shows.
 * `null` means "everything that is enabled in Homescreen settings" and is only used as the
 * default for the built-in Home category so existing users see no change until they edit it.
 */
@Serializable
data class HomeCategory(
    val id: String,
    val title: String,
    val kind: HomeCategoryKind = HomeCategoryKind.Catalogs,
    val enabled: Boolean = true,
    val builtIn: Boolean = false,
    val catalogKeys: List<String>? = emptyList(),
)

data class HomeCategoryEditorRequest(val categoryId: String?)

/** How the optional big numbers behind posters look. */
data class HomeRankSettings(
    /** How many items per row get a number: 5, 10 or 20. */
    val count: Int = 10,
    val accent: Boolean = false,
    val filled: Boolean = false,
)

data class HomeCategoryUiState(
    val categories: List<HomeCategory> = defaultHomeCategories(),
    val selectedId: String = HOME_CATEGORY_HOME_ID,
    /** Last non-Home category the user opened; the "Categories" button returns to it. */
    val lastCategoryId: String? = null,
    /** Catalog keys whose rows show big 1-10 numbers behind the posters. */
    val rankedKeys: List<String> = emptyList(),
    val rank: HomeRankSettings = HomeRankSettings(),
) {
    /** Categories shown in the top bar, in the user's order. */
    val visibleCategories: List<HomeCategory>
        get() = categories.filter { it.enabled }

    /** The active category; falls back to the first visible one if the stored id is gone/hidden. */
    val selected: HomeCategory?
        get() = visibleCategories.firstOrNull { it.id == selectedId } ?: visibleCategories.firstOrNull()
}

@Serializable
private data class StoredHomeCategoriesPayload(
    val version: Int = 1,
    val selectedId: String = HOME_CATEGORY_HOME_ID,
    val lastCategoryId: String? = null,
    val rankedKeys: List<String> = emptyList(),
    val rankCount: Int = 10,
    val rankAccent: Boolean = false,
    val rankFilled: Boolean = false,
    val categories: List<HomeCategory> = emptyList(),
)

fun defaultHomeCategories(): List<HomeCategory> = listOf(
    HomeCategory(
        id = HOME_CATEGORY_HOME_ID,
        title = "Home",
        builtIn = true,
        catalogKeys = null,
    ),
    HomeCategory(id = HOME_CATEGORY_MOVIES_ID, title = "Movies", builtIn = true),
    HomeCategory(id = HOME_CATEGORY_TV_ID, title = "TV Shows", builtIn = true),
    HomeCategory(
        id = HOME_CATEGORY_LIVE_TV_ID,
        title = "Live TV",
        kind = HomeCategoryKind.LiveTv,
        builtIn = true,
        catalogKeys = emptyList(),
    ),
    HomeCategory(id = HOME_CATEGORY_ANIME_ID, title = "Anime", builtIn = true),
    HomeCategory(
        id = HOME_CATEGORY_DISCOVER_ID,
        title = "Discover",
        kind = HomeCategoryKind.Discover,
        builtIn = true,
        catalogKeys = emptyList(),
    ),
)

object HomeCategoryRepository {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _state = MutableStateFlow(HomeCategoryUiState())
    val state: StateFlow<HomeCategoryUiState> = _state.asStateFlow()

    /** Asks the editor sheet to open. [HomeCategoryEditorRequest.categoryId] null = category list. */
    private val _editorRequest = MutableStateFlow<HomeCategoryEditorRequest?>(null)
    val editorRequest: StateFlow<HomeCategoryEditorRequest?> = _editorRequest.asStateFlow()

    /** Whether the category panel above the bottom navigation bar is open. */
    private val _panelOpen = MutableStateFlow(false)
    val panelOpen: StateFlow<Boolean> = _panelOpen.asStateFlow()

    fun setPanelOpen(open: Boolean) {
        _panelOpen.value = open
    }

    fun togglePanel() {
        _panelOpen.value = !_panelOpen.value
    }

    private var loadedProfileId: Int? = null

    fun ensureLoaded() {
        val profileId = ProfileRepository.activeProfileId
        if (loadedProfileId == profileId) return
        loadedProfileId = profileId

        val payload = HomeCatalogSettingsStorage.loadCategoriesPayload().orEmpty().trim()
        val parsed = if (payload.isEmpty()) {
            null
        } else {
            runCatching { json.decodeFromString<StoredHomeCategoriesPayload>(payload) }.getOrNull()
        }
        if (parsed == null || parsed.categories.isEmpty()) {
            _state.value = HomeCategoryUiState()
            return
        }
        _state.value = HomeCategoryUiState(
            categories = parsed.categories,
            selectedId = parsed.selectedId,
            lastCategoryId = parsed.lastCategoryId,
            rankedKeys = parsed.rankedKeys,
            rank = HomeRankSettings(
                count = parsed.rankCount.takeIf { it in setOf(5, 10, 20) } ?: 10,
                accent = parsed.rankAccent,
                filled = parsed.rankFilled,
            ),
        )
    }

    fun select(id: String) {
        ensureLoaded()
        if (_state.value.selectedId == id) return
        update(persist = true) { state ->
            state.copy(
                selectedId = id,
                lastCategoryId = if (id == HOME_CATEGORY_HOME_ID) state.lastCategoryId else id,
            )
        }
    }

    /** Switch to the real Home category (or the first visible one if Home is hidden). */
    fun goHome() {
        ensureLoaded()
        val state = _state.value
        val home = state.visibleCategories.firstOrNull { it.id == HOME_CATEGORY_HOME_ID }
            ?: state.visibleCategories.firstOrNull()
            ?: return
        select(home.id)
        _panelOpen.value = false
    }

    /** Switch to the last non-Home category opened, or the first visible non-Home one. */
    fun restoreLastCategory() {
        ensureLoaded()
        val state = _state.value
        val target = state.visibleCategories.firstOrNull { it.id == state.lastCategoryId && it.id != HOME_CATEGORY_HOME_ID }
            ?: state.visibleCategories.firstOrNull { it.id != HOME_CATEGORY_HOME_ID }
            ?: return
        select(target.id)
    }

    fun setEnabled(id: String, enabled: Boolean) {
        ensureLoaded()
        update { state ->
            // Never allow hiding every category.
            val next = state.categories.map { if (it.id == id) it.copy(enabled = enabled) else it }
            if (next.none { it.enabled }) state else state.copy(categories = next)
        }
    }

    fun move(id: String, delta: Int) {
        ensureLoaded()
        update { state ->
            val index = state.categories.indexOfFirst { it.id == id }
            val target = index + delta
            if (index < 0 || target !in state.categories.indices) {
                state
            } else {
                val mutable = state.categories.toMutableList()
                val item = mutable.removeAt(index)
                mutable.add(target, item)
                state.copy(categories = mutable)
            }
        }
    }

    fun rename(id: String, title: String) {
        ensureLoaded()
        val clean = title.trim()
        if (clean.isEmpty()) return
        update { state ->
            state.copy(categories = state.categories.map { if (it.id == id) it.copy(title = clean) else it })
        }
    }

    fun addCustom(title: String): String? {
        ensureLoaded()
        val clean = title.trim()
        if (clean.isEmpty()) return null
        val id = "custom_" + Random.nextLong().toULong().toString(16)
        update { state ->
            state.copy(categories = state.categories + HomeCategory(id = id, title = clean))
        }
        return id
    }

    fun delete(id: String) {
        ensureLoaded()
        update { state ->
            val target = state.categories.firstOrNull { it.id == id } ?: return@update state
            if (target.builtIn) return@update state
            val next = state.categories.filterNot { it.id == id }
            if (next.none { it.enabled }) state else state.copy(categories = next)
        }
    }

    /** Replace the catalogs assigned to a category (order is preserved). */
    fun setCatalogKeys(id: String, keys: List<String>) {
        ensureLoaded()
        update { state ->
            state.copy(
                categories = state.categories.map {
                    if (it.id == id) it.copy(catalogKeys = keys.distinct()) else it
                },
            )
        }
    }

    fun setRanked(catalogKey: String, ranked: Boolean) {
        ensureLoaded()
        update { state ->
            val next = if (ranked) (state.rankedKeys + catalogKey).distinct() else state.rankedKeys - catalogKey
            state.copy(rankedKeys = next)
        }
    }

    fun setRankCount(count: Int) {
        ensureLoaded()
        update { state -> state.copy(rank = state.rank.copy(count = count)) }
    }

    fun setRankAccent(accent: Boolean) {
        ensureLoaded()
        update { state -> state.copy(rank = state.rank.copy(accent = accent)) }
    }

    fun setRankFilled(filled: Boolean) {
        ensureLoaded()
        update { state -> state.copy(rank = state.rank.copy(filled = filled)) }
    }

    fun resetToDefaults() {
        ensureLoaded()
        update { HomeCategoryUiState() }
    }

    fun requestEditor(categoryId: String? = null) {
        _editorRequest.value = HomeCategoryEditorRequest(categoryId)
    }

    fun consumeEditorRequest() {
        _editorRequest.value = null
    }

    private fun update(
        persist: Boolean = true,
        transform: (HomeCategoryUiState) -> HomeCategoryUiState,
    ) {
        val next = transform(_state.value)
        if (next == _state.value) return
        _state.value = next
        if (persist) persistState(next)
    }

    private fun persistState(state: HomeCategoryUiState) {
        HomeCatalogSettingsStorage.saveCategoriesPayload(
            json.encodeToString(
                StoredHomeCategoriesPayload(
                    selectedId = state.selectedId,
                    lastCategoryId = state.lastCategoryId,
                    rankedKeys = state.rankedKeys,
                    rankCount = state.rank.count,
                    rankAccent = state.rank.accent,
                    rankFilled = state.rank.filled,
                    categories = state.categories,
                ),
            ),
        )
    }
}
