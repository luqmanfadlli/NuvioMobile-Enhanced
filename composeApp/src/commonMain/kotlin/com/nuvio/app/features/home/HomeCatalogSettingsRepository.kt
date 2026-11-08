package com.nuvio.app.features.home

import androidx.compose.ui.text.intl.Locale
import com.nuvio.app.features.addons.ManagedAddon
import com.nuvio.app.features.collection.Collection
import com.nuvio.app.features.collection.CollectionRepository
import com.nuvio.app.features.servers.ServerCatalog
import com.nuvio.app.features.servers.ServerRepository
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.getString

data class HomeCatalogSettingsItem(
    val key: String,
    val defaultTitle: String,
    val addonName: String,
    val customTitle: String = "",
    val enabled: Boolean = true,
    val heroSourceEnabled: Boolean = true,
    val order: Int = 0,
    val isCollection: Boolean = false,
    val collectionId: String? = null,
    val isPinnedToTop: Boolean = false,
    val addonId: String? = null,
    val addonLayoutMode: HomeAddonLayoutMode = HomeAddonLayoutMode.NORMAL,
    val rowPlacement: HomeCatalogRowPlacement = HomeCatalogRowPlacement.NORMAL,
    val isMergedRow: Boolean = false,
    val mergedRowId: String? = null,
) {
    val displayTitle: String
        get() = customTitle.ifBlank { defaultTitle }
}

enum class HomeCatalogRowPlacement(val storageValue: String) {
    NORMAL("normal"),
    PINNED_POSITION("pinned_position"),
    PINNED_TOP("pinned_top"),
    PINNED_BOTTOM("pinned_bottom");

    companion object {
        fun fromStorage(value: String?): HomeCatalogRowPlacement =
            values().firstOrNull { it.storageValue == value } ?: NORMAL
    }
}

enum class HomeAddonLayoutMode(val storageValue: String) {
    NORMAL("normal"),
    LOCKED("locked"),
    PINNED_TOP("pinned_top"),
    PINNED_BOTTOM("pinned_bottom");

    companion object {
        fun fromStorage(value: String?): HomeAddonLayoutMode =
            values().firstOrNull { it.storageValue == value } ?: NORMAL
    }
}

enum class HomeAddonRowOrderPolicy(val storageValue: String) {
    MOVE_ALL_ROWS("move_all_rows"),
    MOVE_UNPINNED_ROWS("move_unpinned_rows"),
    UPCOMING_ROWS_ONLY("upcoming_rows_only");

    companion object {
        fun fromStorage(value: String?): HomeAddonRowOrderPolicy =
            values().firstOrNull { it.storageValue == value } ?: MOVE_UNPINNED_ROWS
    }
}

data class HomeAddonLayoutItem(
    val addonId: String,
    val addonName: String,
    val catalogCount: Int,
    val mode: HomeAddonLayoutMode,
    val order: Int,
    val anchorOrder: Int,
    val anchorKey: String? = null,
)

internal data class HomeAddonLayoutPreference(
    val mode: HomeAddonLayoutMode,
    val order: Int,
    val anchorOrder: Int,
    val anchorKey: String? = null,
)

internal data class HomeCatalogSettingsUiState(
    val heroEnabled: Boolean = true,
    val heroStyle: HomeHeroStyle = HomeHeroStyle.FULL_BLEED,
    val heroTrailerPlaybackEnabled: Boolean = false,
    val heroTrailerStartDelaySeconds: Int = HomeCatalogSettingsRepository.DEFAULT_HERO_TRAILER_START_DELAY_SECONDS,
    val heroTrailerStartUnmuted: Boolean = false,
    val showCatalogType: Boolean = true,
    val hideUnreleasedContent: Boolean = false,
    val hideMergedTitlesWithoutPosters: Boolean = false,
    val mergedRows: List<MergedHomeRowDefinition> = emptyList(),
    val items: List<HomeCatalogSettingsItem> = emptyList(),
    val addonGroups: List<HomeAddonLayoutItem> = emptyList(),
    val addonRowOrderPolicy: HomeAddonRowOrderPolicy = HomeAddonRowOrderPolicy.MOVE_UNPINNED_ROWS,
) {
    val signature: String
        get() = buildString {
            append(heroEnabled)
            append('|')
            append(heroStyle.storageValue)
            append('|')
            append(heroTrailerPlaybackEnabled)
            append('|')
            append(heroTrailerStartDelaySeconds)
            append('|')
            append(heroTrailerStartUnmuted)
            append('|')
            append(showCatalogType)
            append('|')
            append(hideUnreleasedContent)
            append('|')
            append(hideMergedTitlesWithoutPosters)
            append('|')
            append(mergedRows.sortedBy { it.order }.joinToString { row ->
                "${row.id}:${row.title}:${row.sourceKeys.joinToString(",")}:${row.displayMode}:${row.blendMode}:${row.contentType}:${row.hideIncludedRows}:${row.deduplicate}:${row.hideTitlesWithoutPosters}:${row.sortMode}"
            })
            append('|')
            append(
                items.joinToString(separator = "|") { item ->
                    "${item.key}:${item.order}:${item.enabled}:${item.heroSourceEnabled}:${item.customTitle}:${item.rowPlacement}"
                }
            )
            append('|')
            append(addonGroups.joinToString(separator = "|") { group ->
                "${group.addonId}:${group.mode}:${group.order}:${group.anchorOrder}:${group.anchorKey.orEmpty()}"
            })
            append('|')
            append(addonRowOrderPolicy.storageValue)
        }
}

/**
 * Not a user setting: the hero derives this from the layout (portrait -> poster,
 * landscape/tablet -> backdrop). See heroUsesWideArtwork in HomeHeroSection.
 */
internal enum class HomeHeroArtworkSource(
    val storageValue: String,
) {
    BACKDROP("backdrop"),
    POSTER("poster"),
    ;

    companion object {
        fun fromStorageValue(value: String?): HomeHeroArtworkSource =
            entries.firstOrNull { it.storageValue == value } ?: BACKDROP
    }
}

/**
 * Full-bleed keeps the original edge-to-edge hero. Card renders the artwork inside a rounded
 * TMDB-poster-ratio card inset from the screen edges, below the status bar.
 */
internal enum class HomeHeroStyle(
    val storageValue: String,
) {
    FULL_BLEED("full_bleed"),
    CARD("card"),
    ;

    companion object {
        fun fromStorageValue(value: String?): HomeHeroStyle =
            entries.firstOrNull { it.storageValue == value } ?: FULL_BLEED
    }
}

internal data class HomeCatalogPreference(
    val customTitle: String,
    val enabled: Boolean,
    val heroSourceEnabled: Boolean,
    val order: Int,
    val rowPlacement: HomeCatalogRowPlacement = HomeCatalogRowPlacement.NORMAL,
)

internal data class HomeCatalogSettingsSnapshot(
    val heroEnabled: Boolean,
    val heroStyle: HomeHeroStyle,
    val showCatalogType: Boolean,
    val hideUnreleasedContent: Boolean,
    val preferences: Map<String, HomeCatalogPreference>,
    val addonLayouts: Map<String, HomeAddonLayoutPreference> = emptyMap(),
    val addonRowOrderPolicy: HomeAddonRowOrderPolicy = HomeAddonRowOrderPolicy.MOVE_UNPINNED_ROWS,
    val upcomingRowKeys: Set<String> = emptySet(),
    val hideMergedTitlesWithoutPosters: Boolean = false,
    val mergedRows: List<MergedHomeRowDefinition> = emptyList(),
)

@Serializable
private data class StoredHomeCatalogPreference(
    val key: String,
    val customTitle: String = "",
    val enabled: Boolean = true,
    val heroSourceEnabled: Boolean = true,
    val order: Int = 0,
    val rowPlacement: String = HomeCatalogRowPlacement.NORMAL.storageValue,
)

@Serializable
private data class StoredHomeAddonLayoutPreference(
    val addonId: String,
    val mode: String = HomeAddonLayoutMode.NORMAL.storageValue,
    val order: Int = 0,
    val anchorOrder: Int = 0,
    val anchorKey: String? = null,
)

@Serializable
private data class StoredHomeCatalogSettingsPayload(
    val heroEnabled: Boolean = true,
    val heroStyle: String = HomeHeroStyle.FULL_BLEED.storageValue,
    val heroTrailerPlaybackEnabled: Boolean = false,
    val heroTrailerStartDelaySeconds: Int = HomeCatalogSettingsRepository.DEFAULT_HERO_TRAILER_START_DELAY_SECONDS,
    val heroTrailerStartUnmuted: Boolean = false,
    val showCatalogType: Boolean = true,
    val hideUnreleasedContent: Boolean = false,
    val items: List<StoredHomeCatalogPreference> = emptyList(),
    val addonLayouts: List<StoredHomeAddonLayoutPreference> = emptyList(),
    val addonRowOrderPolicy: String = HomeAddonRowOrderPolicy.MOVE_UNPINNED_ROWS.storageValue,
    val upcomingRowKeys: List<String> = emptyList(),
    val hideMergedTitlesWithoutPosters: Boolean = false,
    val mergedRows: List<MergedHomeRowDefinition> = emptyList(),
)

object HomeCatalogSettingsRepository {
    const val HERO_SOURCE_SELECTION_LIMIT = 2

    /** Bounds for the "start hero trailer after N seconds" preference. */
    const val MIN_HERO_TRAILER_START_DELAY_SECONDS = 0
    const val MAX_HERO_TRAILER_START_DELAY_SECONDS = 10
    const val DEFAULT_HERO_TRAILER_START_DELAY_SECONDS = 0

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _uiState = MutableStateFlow(HomeCatalogSettingsUiState())
    internal val uiState: StateFlow<HomeCatalogSettingsUiState> = _uiState.asStateFlow()

    private var hasLoaded = false
    private var definitions: List<HomeCatalogDefinition> = emptyList()
    private var collectionDefinitions: List<CollectionCatalogDefinition> = emptyList()
    private var cloudStreamCatalogItems: List<HomeCatalogSettingsItem> = emptyList()
    private var hideMergedTitlesWithoutPosters = false
    private var mergedRows: List<MergedHomeRowDefinition> = emptyList()
    private var lastCatalogSync: CatalogSyncInput? = null
    private var lastCollectionSync: Pair<List<Collection>, String>? = null
    private val preferencesRef = atomic<Map<String, StoredHomeCatalogPreference>>(emptyMap())
    private var preferences: Map<String, StoredHomeCatalogPreference>
        get() = preferencesRef.value
        set(value) {
            preferencesRef.value = value
        }
    private var heroEnabled = true
    private var heroStyle = HomeHeroStyle.FULL_BLEED
    private var heroTrailerPlaybackEnabled = false
    private var heroTrailerStartDelaySeconds = DEFAULT_HERO_TRAILER_START_DELAY_SECONDS
    private var heroTrailerStartUnmuted = false
    private var showCatalogType = true
    private var hideUnreleasedContent = false
    private var addonLayouts: Map<String, StoredHomeAddonLayoutPreference> = emptyMap()
    private var addonRowOrderPolicy = HomeAddonRowOrderPolicy.MOVE_UNPINNED_ROWS
    private var upcomingRowKeys: Set<String> = emptySet()

    fun onProfileChanged() = clearLocalState()

    fun clearLocalState() {
        hasLoaded = false
        definitions = emptyList()
        collectionDefinitions = emptyList()
        cloudStreamCatalogItems = emptyList()
        hideMergedTitlesWithoutPosters = false
        mergedRows = emptyList()
        lastCatalogSync = null
        lastCollectionSync = null
        preferences = emptyMap()
        heroEnabled = true
        heroStyle = HomeHeroStyle.FULL_BLEED
        heroTrailerPlaybackEnabled = false
        heroTrailerStartDelaySeconds = DEFAULT_HERO_TRAILER_START_DELAY_SECONDS
        heroTrailerStartUnmuted = false
        showCatalogType = true
        hideUnreleasedContent = false
        addonLayouts = emptyMap()
        addonRowOrderPolicy = HomeAddonRowOrderPolicy.MOVE_UNPINNED_ROWS
        upcomingRowKeys = emptySet()
        _uiState.value = HomeCatalogSettingsUiState()
    }

    fun syncCatalogs(addons: List<ManagedAddon>) {
        ensureLoaded()
        val collections = CollectionRepository.collections.value
        val syncInput = CatalogSyncInput(addons, collections, Locale.current.toLanguageTag(), ServerRepository.uiState.value.revision)
        if (lastCatalogSync == syncInput) return
        val previouslySavedKeys = preferences.keys
        definitions = buildHomeCatalogDefinitions(addons)
        collectionDefinitions = buildCollectionDefinitions(collections)
        if (addonRowOrderPolicy == HomeAddonRowOrderPolicy.UPCOMING_ROWS_ONLY) {
            val newlyDiscoveredKeys = definitions.asSequence()
                .map { it.key }
                .filterNot { it in previouslySavedKeys }
                .toSet()
            upcomingRowKeys = upcomingRowKeys + newlyDiscoveredKeys
        } else {
            upcomingRowKeys = emptySet()
        }
        lastCatalogSync = syncInput
        lastCollectionSync = lastCollectionSync?.takeIf {
            it.first == collections && it.second == syncInput.locale
        }
        if (definitions.isEmpty() && collectionDefinitions.isEmpty()) {
            publish()
            return
        }
        normalizePreferences()
        publish()
        persist()
    }

    fun syncCloudStreamCatalogs(sections: List<HomeCatalogSection>) {
        ensureLoaded()
        var nextOrder = (preferences.values.maxOfOrNull(StoredHomeCatalogPreference::order) ?: -1) + 1
        val updatedPreferences = preferences.toMutableMap()
        val nextItems = sections.mapNotNull { section ->
            val target = section.target as? com.nuvio.app.features.catalog.CatalogTarget.CloudStream
                ?: return@mapNotNull null
            val preference = updatedPreferences[section.key] ?: StoredHomeCatalogPreference(
                key = section.key,
                enabled = true,
                heroSourceEnabled = false,
                order = nextOrder++,
            ).also { updatedPreferences[section.key] = it }
            HomeCatalogSettingsItem(
                key = section.key,
                defaultTitle = section.title,
                addonName = section.addonName,
                customTitle = preference.customTitle,
                enabled = preference.enabled,
                heroSourceEnabled = false,
                order = preference.order,
                addonId = "cloudstream:${target.providerId}",
                rowPlacement = HomeCatalogRowPlacement.fromStorage(preference.rowPlacement),
            )
        }
        if (updatedPreferences != preferences) {
            preferences = updatedPreferences.toMap()
            persist()
        }
        if (cloudStreamCatalogItems == nextItems) return
        cloudStreamCatalogItems = nextItems
        publish()
    }

    fun syncCollections(collections: List<Collection>) {
        ensureLoaded()
        val syncInput = collections to Locale.current.toLanguageTag()
        if (lastCollectionSync == syncInput) return
        collectionDefinitions = buildCollectionDefinitions(collections)
        lastCatalogSync = lastCatalogSync?.takeIf {
            it.collections == collections && it.locale == syncInput.second
        }
        lastCollectionSync = syncInput
        normalizePreferences()
        publish()
        persist()
        HomeRepository.applyCurrentSettings()
    }

    /**
     * Canonical source list shared by the editor and save validation. This prevents the
     * dialog from offering a catalog that create/update would subsequently reject.
     */
    internal fun mergedRowSourceKeys(): Set<String> {
        ensureLoaded()
        return eligibleMergedRowSourceKeys()
    }

    private fun eligibleMergedRowSourceKeys(): Set<String> =
        definitions.asSequence()
            .filter { definition ->
                definition.serverConnectionId == null &&
                    !ServerCatalog.isServerKey(definition.key)
            }
            .mapTo(linkedSetOf()) { it.key }

    fun resolveMergedHomeRow(id: String): ResolvedMergedHomeRow? {
        ensureLoaded()
        val row = mergedRows.firstOrNull { it.id == id } ?: return null
        val currentSettings = snapshot()
        val availableDefinitions = row.sourceKeys.mapNotNull { sourceKey ->
            definitions.firstOrNull { definition ->
                definition.key == sourceKey &&
                    definition.serverConnectionId == null &&
                    !ServerCatalog.isServerKey(definition.key)
            }
        }
        val baseTitles = availableDefinitions.map { definition ->
            currentSettings.preferences[definition.key]?.customTitle
                ?.takeIf(String::isNotBlank)
                ?: definition.titleFor(currentSettings.showCatalogType)
        }
        val duplicateTitleCounts = baseTitles.groupingBy { it }.eachCount()
        val sources = availableDefinitions.mapIndexed { index, definition ->
            val baseTitle = baseTitles[index]
            val title = cleanMergedSourceTabTitle(
                if ((duplicateTitleCounts[baseTitle] ?: 0) > 1) {
                    "$baseTitle ${definition.addonName}"
                } else {
                    baseTitle
                },
            )
            ResolvedMergedHomeRowSource(
                definition = definition,
                title = title,
            )
        }
        return ResolvedMergedHomeRow(
            definition = row,
            sources = sources,
            unavailableSourceCount = (row.sourceKeys.distinct().size - sources.size).coerceAtLeast(0),
        )
    }

    internal fun snapshot(): HomeCatalogSettingsSnapshot {
        ensureLoaded()
        return HomeCatalogSettingsSnapshot(
            heroEnabled = heroEnabled,
            heroStyle = heroStyle,
            showCatalogType = showCatalogType,
            hideUnreleasedContent = hideUnreleasedContent,
            hideMergedTitlesWithoutPosters = hideMergedTitlesWithoutPosters,
            mergedRows = mergedRows.sortedBy { it.order },
            addonRowOrderPolicy = addonRowOrderPolicy,
            upcomingRowKeys = upcomingRowKeys,
            preferences = preferences.mapValues { (_, value) ->
                HomeCatalogPreference(
                    customTitle = value.customTitle,
                    enabled = value.enabled,
                    heroSourceEnabled = value.heroSourceEnabled,
                    order = value.order,
                    rowPlacement = HomeCatalogRowPlacement.fromStorage(value.rowPlacement),
                )
            },
            addonLayouts = addonLayouts.mapValues { (_, value) ->
                HomeAddonLayoutPreference(
                    mode = HomeAddonLayoutMode.fromStorage(value.mode),
                    order = value.order,
                    anchorOrder = value.anchorOrder,
                    anchorKey = value.anchorKey,
                )
            },
        )
    }

    fun setHeroEnabled(enabled: Boolean) {
        ensureLoaded()
        heroEnabled = enabled
        publish()
        persist()
        HomeRepository.applyCurrentSettings()
    }

    internal fun setHeroStyle(style: HomeHeroStyle) {
        ensureLoaded()
        if (heroStyle == style) return
        heroStyle = style
        publish()
        persist()
        HomeRepository.applyCurrentSettings()
        HomeCatalogSettingsSyncService.triggerPush()
    }

    fun setHeroTrailerPlaybackEnabled(enabled: Boolean) {
        ensureLoaded()
        if (heroTrailerPlaybackEnabled == enabled) return
        heroTrailerPlaybackEnabled = enabled
        publish()
        persist()
    }

    /**
     * How long the hero waits, after an item becomes the active page, before its trailer starts.
     * Clamped to [MIN_HERO_TRAILER_START_DELAY_SECONDS]..[MAX_HERO_TRAILER_START_DELAY_SECONDS].
     */
    fun setHeroTrailerStartUnmuted(enabled: Boolean) {
        ensureLoaded()
        if (heroTrailerStartUnmuted == enabled) return
        heroTrailerStartUnmuted = enabled
        publish()
        persist()
    }

    fun setHeroTrailerStartDelaySeconds(seconds: Int) {
        ensureLoaded()
        val clamped = seconds.coerceIn(
            MIN_HERO_TRAILER_START_DELAY_SECONDS,
            MAX_HERO_TRAILER_START_DELAY_SECONDS,
        )
        if (heroTrailerStartDelaySeconds == clamped) return
        heroTrailerStartDelaySeconds = clamped
        publish()
        persist()
    }

    fun setShowCatalogType(enabled: Boolean) {
        ensureLoaded()
        if (showCatalogType == enabled) return
        showCatalogType = enabled
        publish()
        persist()
        HomeRepository.applyCurrentSettings()
        HomeCatalogSettingsSyncService.triggerPush()
    }

    fun setHideUnreleasedContent(enabled: Boolean) {
        ensureLoaded()
        if (hideUnreleasedContent == enabled) return
        hideUnreleasedContent = enabled
        publish()
        persist()
        HomeRepository.applyCurrentSettings()
        HomeCatalogSettingsSyncService.triggerPush()
    }

    fun setHideMergedTitlesWithoutPosters(enabled: Boolean) {
        ensureLoaded()
        if (hideMergedTitlesWithoutPosters == enabled) return
        hideMergedTitlesWithoutPosters = enabled
        publish()
        persist()
        HomeRepository.applyCurrentSettings()
        HomeCatalogSettingsSyncService.triggerPush()
    }

    fun setHeroSourceEnabled(key: String, enabled: Boolean) {
        updatePreference(key, pushRemote = false, heroSourcesChanged = true) { preference ->
            if (!enabled) {
                preference.copy(heroSourceEnabled = false)
            } else if (selectedHeroSourceCount(excludingKey = key) >= HERO_SOURCE_SELECTION_LIMIT) {
                preference
            } else {
                preference.copy(heroSourceEnabled = true)
            }
        }
    }

    fun setRowPlacement(key: String, placement: HomeCatalogRowPlacement) {
        ensureLoaded()
        val current = preferences[key] ?: defaultPreferenceForMissingKey(key) ?: return
        if (current.rowPlacement == placement.storageValue) return
        if (placement == HomeCatalogRowPlacement.PINNED_POSITION) {
            val visibleKeys = _uiState.value.items.map { it.key }
            if (key in visibleKeys) persistVisibleOrder(visibleKeys)
        }
        updatePreference(key) { preference ->
            preference.copy(rowPlacement = placement.storageValue)
        }
    }

    fun setEnabled(key: String, enabled: Boolean) {
        updatePreference(key) { preference ->
            preference.copy(enabled = enabled)
        }
    }

    fun setAddonCatalogsEnabled(addonId: String, enabled: Boolean) {
        ensureLoaded()
        if (addonId.isBlank()) return
        val keys = definitions
            .filterNot { definition -> ServerCatalog.isServerKey(definition.key) }
            .filter { definition -> definition.addonGroupId() == addonId }
            .map { definition -> definition.key }
        if (keys.isEmpty()) return

        val updatedPreferences = preferences.toMutableMap()
        var changed = false
        keys.forEach { key ->
            val current = updatedPreferences[key] ?: return@forEach
            if (current.enabled != enabled) {
                updatedPreferences[key] = current.copy(enabled = enabled)
                changed = true
            }
        }
        if (!changed) return
        preferences = updatedPreferences.toMap()
        publish()
        persist()
        HomeRepository.applyCurrentSettings()
        HomeCatalogSettingsSyncService.triggerPush()
    }

    fun setCustomTitle(key: String, title: String) {
        updatePreference(key) { preference ->
            preference.copy(customTitle = title)
        }
    }

    fun createMergedRow(
        title: String,
        sourceKeys: List<String>,
        displayMode: MergedHomeRowDisplayMode,
        deduplicate: Boolean = true,
        contentType: MergedHomeRowContentType = MergedHomeRowContentType.ALL,
        blendMode: MergedHomeRowBlendMode = MergedHomeRowBlendMode.SMART_FRESHNESS_POPULARITY,
        sortMode: MergedHomeRowSortMode = MergedHomeRowSortMode.OFF,
        hideIncludedRows: Boolean = false,
        hideTitlesWithoutPosters: Boolean = false,
    ): String? {
        ensureLoaded()
        val availableSourceKeys = eligibleMergedRowSourceKeys()
        val requestedSources = sourceKeys.distinct()
        if (requestedSources.size !in 2..MAX_MERGED_HOME_ROW_SOURCES) return null
        if (requestedSources.any { it !in availableSourceKeys }) return null
        val validSources = requestedSources
        val safeTitle = title.trim()
        if (safeTitle.isBlank()) return null

        var id: String
        do {
            id = "mr_${kotlin.random.Random.nextLong()}"
        } while (mergedRows.any { it.id == id })
        val row = MergedHomeRowDefinition(
            id = id,
            title = safeTitle,
            sourceKeys = validSources,
            displayMode = displayMode.storageValue,
            deduplicate = deduplicate,
            order = (mergedRows.maxOfOrNull { it.order } ?: -1) + 1,
            contentType = contentType.storageValue,
            blendMode = blendMode.storageValue,
            sortMode = sortMode.storageValue,
            hideIncludedRows = hideIncludedRows,
            hideTitlesWithoutPosters = hideTitlesWithoutPosters,
        )
        mergedRows = mergedRows + row
        val key = mergedHomeRowKey(id)
        val nextHomeOrder = (preferences.values.maxOfOrNull { it.order } ?: -1) + 1
        preferences = preferences + (key to StoredHomeCatalogPreference(
            key = key,
            enabled = true,
            heroSourceEnabled = false,
            order = nextHomeOrder,
        ))
        publish()
        persist()
        HomeRepository.applyCurrentSettings()
        HomeCatalogSettingsSyncService.triggerPush()
        return id
    }

    fun updateMergedRow(
        id: String,
        title: String,
        sourceKeys: List<String>,
        displayMode: MergedHomeRowDisplayMode,
        deduplicate: Boolean,
        contentType: MergedHomeRowContentType? = null,
        blendMode: MergedHomeRowBlendMode? = null,
        sortMode: MergedHomeRowSortMode? = null,
        hideIncludedRows: Boolean? = null,
        hideTitlesWithoutPosters: Boolean? = null,
    ): Boolean {
        ensureLoaded()
        val existing = mergedRows.firstOrNull { it.id == id } ?: return false
        val availableSourceKeys = eligibleMergedRowSourceKeys()
        val requestedSources = sourceKeys.distinct()
        if (requestedSources.size !in 2..MAX_MERGED_HOME_ROW_SOURCES) return false
        if (requestedSources.any { it !in availableSourceKeys }) return false
        val validSources = requestedSources
        val safeTitle = title.trim()
        if (safeTitle.isBlank()) return false
        val updated = existing.copy(
            title = safeTitle,
            sourceKeys = validSources,
            displayMode = displayMode.storageValue,
            deduplicate = deduplicate,
            contentType = contentType?.storageValue ?: existing.contentType,
            blendMode = blendMode?.storageValue ?: existing.blendMode,
            sortMode = sortMode?.storageValue ?: existing.sortMode,
            hideIncludedRows = hideIncludedRows ?: existing.hideIncludedRows,
            hideTitlesWithoutPosters = hideTitlesWithoutPosters ?: existing.hideTitlesWithoutPosters,
        )
        if (updated == existing) return true
        mergedRows = mergedRows.map { if (it.id == id) updated else it }
        publish()
        persist()
        HomeRepository.applyCurrentSettings()
        HomeCatalogSettingsSyncService.triggerPush()
        return true
    }

    fun duplicateMergedRow(id: String): String? {
        ensureLoaded()
        val source = mergedRows.firstOrNull { it.id == id } ?: return null
        var newId: String
        do {
            newId = "mr_${kotlin.random.Random.nextLong().toString()}"
        } while (mergedRows.any { it.id == newId })
        val copy = source.copy(
            id = newId,
            title = "${source.title} (copy)",
            order = (mergedRows.maxOfOrNull { it.order } ?: -1) + 1,
        )
        mergedRows = mergedRows + copy
        val newKey = mergedHomeRowKey(newId)
        val sourcePreference = preferences[mergedHomeRowKey(id)]
        preferences = preferences + (newKey to StoredHomeCatalogPreference(
            key = newKey,
            customTitle = "",
            enabled = sourcePreference?.enabled ?: true,
            heroSourceEnabled = false,
            order = (preferences.values.maxOfOrNull { it.order } ?: -1) + 1,
        ))
        publish()
        persist()
        HomeRepository.applyCurrentSettings()
        HomeCatalogSettingsSyncService.triggerPush()
        return newId
    }

    fun deleteMergedRow(id: String) {
        ensureLoaded()
        if (mergedRows.none { it.id == id }) return
        mergedRows = mergedRows.filterNot { it.id == id }
            .mapIndexed { index, row -> row.copy(order = index) }
        preferences = preferences - mergedHomeRowKey(id)
        publish()
        persist()
        HomeRepository.applyCurrentSettings()
        HomeCatalogSettingsSyncService.triggerPush()
    }

    fun moveMergedRowDefinition(fromIndex: Int, toIndex: Int) {
        ensureLoaded()
        val ordered = mergedRows.sortedBy { it.order }
        if (fromIndex !in ordered.indices || toIndex !in ordered.indices || fromIndex == toIndex) return
        val reordered = ordered.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
        val visibleKeys = _uiState.value.items.map { it.key }.toMutableList()
        val currentMergedKeys = visibleKeys.filter { key -> mergedRows.any { mergedHomeRowKey(it.id) == key } }
        val reorderedMergedKeys = reordered.map { mergedHomeRowKey(it.id) }
        currentMergedKeys.forEachIndexed { index, key ->
            val position = visibleKeys.indexOf(key)
            if (position >= 0) visibleKeys[position] = reorderedMergedKeys.getOrElse(index) { key }
        }
        mergedRows = reordered
        persistVisibleOrder(visibleKeys)
        publish()
        persist()
        HomeRepository.applyCurrentSettings()
        HomeCatalogSettingsSyncService.triggerPush()
    }

    fun mergedRowEnabled(id: String): Boolean {
        ensureLoaded()
        return preferences[mergedHomeRowKey(id)]?.enabled != false
    }

    private fun validMergedRowKey(key: String): Boolean =
        mergedRows.any { mergedHomeRowKey(it.id) == key }



    fun setAddonLayoutMode(addonId: String, mode: HomeAddonLayoutMode) {
        ensureLoaded()
        if (addonId.isBlank()) return
        val addonDefinitions = definitions.filter { definition ->
            !ServerCatalog.isServerKey(definition.key) && definition.addonGroupId() == addonId
        }
        if (addonDefinitions.isEmpty()) return
        val existing = addonLayouts[addonId]
        if (HomeAddonLayoutMode.fromStorage(existing?.mode) == mode && existing != null) return

        val firstOrder = existing?.order ?: _uiState.value.addonGroups
            .firstOrNull { it.addonId == addonId }?.order ?: 0
        val groupKeys = addonDefinitions.mapTo(mutableSetOf(), HomeCatalogDefinition::key)
        val currentRows = _uiState.value.items
        val firstRowOrder = addonDefinitions.minOfOrNull { definition ->
            preferences[definition.key]?.order ?: Int.MAX_VALUE
        }?.takeIf { it != Int.MAX_VALUE } ?: firstOrder
        val currentMode = HomeAddonLayoutMode.fromStorage(existing?.mode)
        val anchorKey = when {
            mode != HomeAddonLayoutMode.LOCKED -> null
            currentMode == HomeAddonLayoutMode.LOCKED -> existing?.anchorKey
            else -> {
                val lastGroupIndex = currentRows.indexOfLast { it.key in groupKeys }
                currentRows.drop((lastGroupIndex + 1).coerceAtLeast(0))
                    .firstOrNull {
                        it.key !in groupKeys &&
                            it.rowPlacement == HomeCatalogRowPlacement.NORMAL &&
                            it.addonLayoutMode == HomeAddonLayoutMode.NORMAL &&
                            !it.isPinnedToTop
                    }?.key
            }
        }
        val anchorOrder = if (mode == HomeAddonLayoutMode.LOCKED && currentMode != HomeAddonLayoutMode.LOCKED) {
            anchorKey?.let { key -> preferences[key]?.order } ?: firstRowOrder
        } else {
            existing?.anchorOrder ?: firstRowOrder
        }

        addonLayouts = addonLayouts + (
            addonId to StoredHomeAddonLayoutPreference(
                addonId = addonId,
                mode = mode.storageValue,
                order = firstOrder,
                anchorOrder = anchorOrder,
                anchorKey = anchorKey,
            )
        )
        publish()
        persist()
        HomeRepository.applyCurrentSettings()
        HomeCatalogSettingsSyncService.triggerPush()
    }

    /** Places this add-on's dynamic rows before a stable row, overriding row-order defaults. */
    fun setAddonAnchorKey(addonId: String, anchorKey: String?) {
        ensureLoaded()
        if (addonId.isBlank()) return
        val group = _uiState.value.addonGroups.firstOrNull { it.addonId == addonId } ?: return
        val anchor = anchorKey?.let { key -> _uiState.value.items.firstOrNull { it.key == key } }
        if (anchorKey != null && (
                anchor == null ||
                    anchor.addonId == addonId ||
                    anchor.rowPlacement != HomeCatalogRowPlacement.NORMAL ||
                    anchor.addonLayoutMode != HomeAddonLayoutMode.NORMAL ||
                    anchor.isPinnedToTop
            )
        ) return
        val current = addonLayouts[addonId]
        val next = StoredHomeAddonLayoutPreference(
            addonId = addonId,
            mode = HomeAddonLayoutMode.LOCKED.storageValue,
            order = current?.order ?: group.order,
            anchorOrder = anchor?.order ?: current?.anchorOrder ?: group.anchorOrder,
            anchorKey = anchorKey,
        )
        if (current == next) return
        addonLayouts = addonLayouts + (addonId to next)
        publish()
        persist()
        HomeRepository.applyCurrentSettings()
        HomeCatalogSettingsSyncService.triggerPush()
    }

    fun moveAddonGroup(fromIndex: Int, toIndex: Int) {
        val groups = _uiState.value.addonGroups
        if (fromIndex !in groups.indices || toIndex !in groups.indices || fromIndex == toIndex) return
        val reordered = groups.map { it.addonId }.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
        saveAddonRowOrder(reordered, addonRowOrderPolicy)
    }

    /** Commit the add-on order and the policy selected in the explicit edit flow. */
    fun saveAddonRowOrder(addonIds: List<String>, policy: HomeAddonRowOrderPolicy) {
        ensureLoaded()
        val groups = _uiState.value.addonGroups
        if (groups.isEmpty()) return
        val knownIds = groups.mapTo(linkedSetOf()) { it.addonId }
        val requestedOrder = addonIds.filter { it in knownIds }.distinct()
        val completeOrder = requestedOrder + groups.map { it.addonId }.filterNot { it in requestedOrder }
        when (policy) {
            HomeAddonRowOrderPolicy.MOVE_ALL_ROWS -> {
                // Option 1 is an explicit reset: every catalog row owned by an add-on in
                // this ordering list joins its group, so clear only those rows' individual
                // pin placements. Other add-ons and collections retain their settings.
                val affectedKeys = definitions.asSequence()
                    .filter { it.addonGroupId() in completeOrder }
                    .map { it.key }
                    .toSet()
                preferences = preferences.mapValues { (key, preference) ->
                    if (key in affectedKeys &&
                        HomeCatalogRowPlacement.fromStorage(preference.rowPlacement) != HomeCatalogRowPlacement.NORMAL
                    ) {
                        preference.copy(rowPlacement = HomeCatalogRowPlacement.NORMAL.storageValue)
                    } else {
                        preference
                    }
                }
            }
            HomeAddonRowOrderPolicy.MOVE_UNPINNED_ROWS -> {
                // Option 2 deliberately preserves every individual row pin.
            }
            HomeAddonRowOrderPolicy.UPCOMING_ROWS_ONLY -> {
                // Option 3 freezes the current visible order and leaves existing pins alone.
                // Newly discovered catalog keys are tracked during the next catalog sync.
                persistVisibleOrder(_uiState.value.items.map { it.key })
            }
        }
        addonRowOrderPolicy = policy
        upcomingRowKeys = emptySet()
        val nextLayouts = addonLayouts.toMutableMap()
        completeOrder.forEachIndexed { index, addonId ->
            nextLayouts[addonId] = StoredHomeAddonLayoutPreference(
                addonId = addonId,
                // The new UI uses group order only; legacy mode/anchor values remain in
                // the stored schema for downgrade compatibility, but are normalized here.
                mode = HomeAddonLayoutMode.NORMAL.storageValue,
                order = index,
                anchorOrder = index,
                anchorKey = null,
            )
        }
        addonLayouts = nextLayouts
        publish()
        persist()
        HomeRepository.applyCurrentSettings()
        HomeCatalogSettingsSyncService.triggerPush()
    }

    fun resetToDefaults() {
        ensureLoaded()
        heroEnabled = true
        heroStyle = HomeHeroStyle.FULL_BLEED
        heroTrailerPlaybackEnabled = false
        heroTrailerStartDelaySeconds = DEFAULT_HERO_TRAILER_START_DELAY_SECONDS
        heroTrailerStartUnmuted = false
        showCatalogType = true
        hideUnreleasedContent = false
        hideMergedTitlesWithoutPosters = false
        mergedRows = emptyList()
        preferences = emptyMap()
        addonLayouts = emptyMap()
        addonRowOrderPolicy = HomeAddonRowOrderPolicy.MOVE_UNPINNED_ROWS
        upcomingRowKeys = emptySet()
        normalizePreferences()
        publish()
        persist()
        HomeRepository.applyCurrentSettings()
        HomeCatalogSettingsSyncService.triggerPush()
    }

    fun moveUp(key: String) {
        move(key = key, direction = -1)
    }

    fun moveDown(key: String) {
        move(key = key, direction = 1)
    }

    fun moveByVisibilityIndex(fromIndex: Int, toIndex: Int, visible: Boolean) {
        ensureLoaded()
        val allItems = _uiState.value.items
        val groupItems = allItems.filter { it.enabled == visible }
        if (fromIndex !in groupItems.indices || toIndex !in groupItems.indices || fromIndex == toIndex) return
        val fromItem = groupItems[fromIndex]
        val toItem = groupItems[toIndex]

        fun pinSection(item: HomeCatalogSettingsItem): HomeCatalogRowPlacement = when {
            item.rowPlacement == HomeCatalogRowPlacement.PINNED_TOP ||
                (item.isCollection && item.isPinnedToTop) -> HomeCatalogRowPlacement.PINNED_TOP
            item.rowPlacement == HomeCatalogRowPlacement.PINNED_BOTTOM -> HomeCatalogRowPlacement.PINNED_BOTTOM
            else -> HomeCatalogRowPlacement.NORMAL
        }
        val fromSection = pinSection(fromItem)
        val toSection = pinSection(toItem)
        if (fromSection != toSection) return
        val start = minOf(fromIndex, toIndex)
        val end = maxOf(fromIndex, toIndex)
        if (groupItems.subList(start, end + 1).any { item ->
            item.rowPlacement == HomeCatalogRowPlacement.PINNED_POSITION || pinSection(item) != fromSection
        }) return

        val reorderedGroup = groupItems.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
        val otherItems = allItems.filter { it.enabled != visible }
        val orderedItems = if (visible) reorderedGroup + otherItems else otherItems + reorderedGroup
        persistVisibleOrder(orderedItems.map { it.key })
        enforcePinnedCollectionsAtTop()
        publish()
        persist()
        HomeRepository.applyCurrentSettings()
        HomeCatalogSettingsSyncService.triggerPush()
    }

    fun moveByIndex(fromIndex: Int, toIndex: Int) {
        ensureLoaded()
        val items = _uiState.value.items
        if (items.isEmpty() || fromIndex !in items.indices || toIndex !in items.indices || fromIndex == toIndex) return
        val fromItem = items[fromIndex]
        val toItem = items[toIndex]
        fun pinSection(item: HomeCatalogSettingsItem): HomeCatalogRowPlacement = when {
            item.rowPlacement == HomeCatalogRowPlacement.PINNED_TOP ||
                (item.isCollection && item.isPinnedToTop) -> HomeCatalogRowPlacement.PINNED_TOP
            item.rowPlacement == HomeCatalogRowPlacement.PINNED_BOTTOM -> HomeCatalogRowPlacement.PINNED_BOTTOM
            else -> HomeCatalogRowPlacement.NORMAL
        }

        val fromPinSection = pinSection(fromItem)
        val toPinSection = pinSection(toItem)
        // Top and bottom pins may be reordered only inside their own section.
        if (fromPinSection != toPinSection) return

        val start = minOf(fromIndex, toIndex)
        val end = maxOf(fromIndex, toIndex)
        // A position-pinned row is an anchor, not merely an unpinned row that
        // avoids add-on grouping. Do not move it or let another row cross it.
        val crossedPinnedBoundary = items.subList(start, end + 1).any { item ->
            item.rowPlacement == HomeCatalogRowPlacement.PINNED_POSITION ||
                pinSection(item) != fromPinSection
        }
        if (crossedPinnedBoundary) return
        val orderedKeys = items.map { it.key }.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
        persistVisibleOrder(orderedKeys)
        enforcePinnedCollectionsAtTop()
        publish()
        persist()
        HomeRepository.applyCurrentSettings()
        HomeCatalogSettingsSyncService.triggerPush()
    }

    private fun persistVisibleOrder(orderedKeys: List<String>) {
        val updatedPreferences = preferences.toMutableMap()
        orderedKeys.forEachIndexed { index, key ->
            val current = updatedPreferences[key] ?: return@forEachIndexed
            updatedPreferences[key] = current.copy(order = index)
        }
        preferences = updatedPreferences
        val mergedOrderByKey = orderedKeys.withIndex().associate { it.value to it.index }
        mergedRows = mergedRows.map { row ->
            mergedOrderByKey[mergedHomeRowKey(row.id)]?.let { row.copy(order = it) } ?: row
        }

        val addonIdByKey = definitions
            .filterNot { ServerCatalog.isServerKey(it.key) }
            .associate { it.key to it.addonGroupId() }
        val firstIndexByAddon = orderedKeys.withIndex().mapNotNull { (index, key) ->
            val addonId = addonIdByKey[key] ?: return@mapNotNull null
            val row = preferences[key] ?: return@mapNotNull null
            if (row.rowPlacement != HomeCatalogRowPlacement.NORMAL.storageValue) return@mapNotNull null
            addonId to index
        }.groupBy({ it.first }, { it.second })
            .mapValues { (_, indexes) -> indexes.minOrNull() ?: Int.MAX_VALUE }
        val updatedLayouts = addonLayouts.toMutableMap()
        firstIndexByAddon.forEach { (addonId, groupOrder) ->
            val existing = updatedLayouts[addonId]
            updatedLayouts[addonId] = StoredHomeAddonLayoutPreference(
                addonId = addonId,
                mode = existing?.mode ?: HomeAddonLayoutMode.NORMAL.storageValue,
                order = groupOrder,
                anchorOrder = if (
                    HomeAddonLayoutMode.fromStorage(existing?.mode) == HomeAddonLayoutMode.LOCKED &&
                    existing?.anchorKey == null
                ) groupOrder else existing?.anchorOrder ?: groupOrder,
                anchorKey = existing?.anchorKey,
            )
        }
        addonLayouts = updatedLayouts
    }

    private fun ensureLoaded() {
        if (hasLoaded) return
        hasLoaded = true

        val payload = HomeCatalogSettingsStorage.loadPayload().orEmpty().trim()
        if (payload.isEmpty()) return

        val parsedPayload = runCatching {
            json.decodeFromString<StoredHomeCatalogSettingsPayload>(payload)
        }.getOrNull()

        if (parsedPayload != null) {
            heroEnabled = parsedPayload.heroEnabled
            heroStyle = HomeHeroStyle.fromStorageValue(parsedPayload.heroStyle)
            heroTrailerPlaybackEnabled = parsedPayload.heroTrailerPlaybackEnabled
            heroTrailerStartDelaySeconds = parsedPayload.heroTrailerStartDelaySeconds.coerceIn(
                MIN_HERO_TRAILER_START_DELAY_SECONDS,
                MAX_HERO_TRAILER_START_DELAY_SECONDS,
            )
            heroTrailerStartUnmuted = parsedPayload.heroTrailerStartUnmuted
            showCatalogType = parsedPayload.showCatalogType
            hideUnreleasedContent = parsedPayload.hideUnreleasedContent
            hideMergedTitlesWithoutPosters = parsedPayload.hideMergedTitlesWithoutPosters
            mergedRows = parsedPayload.mergedRows.filter { it.id.isNotBlank() }
                .map { it.copy(sourceKeys = it.sourceKeys.distinct().take(MAX_MERGED_HOME_ROW_SOURCES)) }
            addonRowOrderPolicy = HomeAddonRowOrderPolicy.fromStorage(parsedPayload.addonRowOrderPolicy)
            upcomingRowKeys = parsedPayload.upcomingRowKeys.toSet()
            preferences = parsedPayload.items.associateBy { it.key }
            addonLayouts = parsedPayload.addonLayouts.associateBy { it.addonId }
            publish()
            return
        }

        val legacyItems = runCatching {
            json.decodeFromString<List<StoredHomeCatalogPreference>>(payload)
        }.getOrDefault(emptyList())

        preferences = legacyItems.associateBy { it.key }
        addonLayouts = emptyMap()
        publish()
    }

    private fun normalizePreferences() {
        val current = preferences
        data class UnifiedEntry(val key: String, val isCollection: Boolean, val isMergedRow: Boolean = false)
        val catalogEntries = definitions.map { UnifiedEntry(it.key, false) }
        val collectionEntries = collectionDefinitions.map { UnifiedEntry(it.key, true) }
        val mergedRowEntries = mergedRows.map { UnifiedEntry(mergedHomeRowKey(it.id), false, true) }
        val allEntries = catalogEntries + collectionEntries + mergedRowEntries
        val knownKeys = allEntries.mapTo(linkedSetOf(), UnifiedEntry::key)
        var nextOrder = (current.values.maxOfOrNull(StoredHomeCatalogPreference::order) ?: -1) + 1

        val orderedEntries = allEntries.mapIndexed { defaultIndex, entry ->
            Triple(
                entry,
                current[entry.key]?.order ?: (nextOrder + defaultIndex),
                defaultIndex,
            )
        }.sortedWith(
            compareBy<Triple<UnifiedEntry, Int, Int>>(
                { it.second },
                { it.third },
            ),
        ).map { it.first }

        val normalized = current
            .filterKeys { it !in knownKeys || it.startsWith("cloudstream:") }
            .toMutableMap()
        var enabledHeroSourceCount = 0
        orderedEntries.forEach { entry ->
            val stored = current[entry.key]
            val heroSourceEnabled = if (entry.isCollection || entry.isMergedRow) {
                false
            } else {
                (stored?.heroSourceEnabled ?: true) &&
                    enabledHeroSourceCount < HERO_SOURCE_SELECTION_LIMIT
            }
            if (heroSourceEnabled) {
                enabledHeroSourceCount += 1
            }
            normalized[entry.key] = StoredHomeCatalogPreference(
                key = entry.key,
                customTitle = stored?.customTitle.orEmpty(),
                enabled = stored?.enabled ?: true,
                heroSourceEnabled = heroSourceEnabled,
                order = stored?.order ?: nextOrder++,
                rowPlacement = stored?.rowPlacement ?: HomeCatalogRowPlacement.NORMAL.storageValue,
            )
        }
        preferences = normalized.toMap()
        enforcePinnedCollectionsAtTop()
    }

    private fun publish() {
        val collectionMap = collectionDefinitions.associateBy { it.key }
        val catalogItems = definitions
            .map { definition ->
                val preference = preferences[definition.key]
                HomeCatalogSettingsItem(
                    key = definition.key,
                    defaultTitle = definition.defaultTitle,
                    addonName = definition.addonName,
                    customTitle = preference?.customTitle.orEmpty(),
                    enabled = preference?.enabled ?: true,
                    heroSourceEnabled = preference?.heroSourceEnabled ?: true,
                    order = preference?.order ?: 0,
                    addonId = if (ServerCatalog.isServerKey(definition.key)) null else definition.addonGroupId(),
                    rowPlacement = HomeCatalogRowPlacement.fromStorage(preference?.rowPlacement),
                    // Add-on ordering is now controlled by the separate Add-ons Row Order section.
                    // Do not let legacy group modes block individual row pinning or reordering.
                    addonLayoutMode = HomeAddonLayoutMode.NORMAL,
                )
            }

        val collectionItems = collectionDefinitions.map { colDef ->
            val preference = preferences[colDef.key]
            HomeCatalogSettingsItem(
                key = colDef.key,
                defaultTitle = colDef.title,
                addonName = colDef.subtitle,
                customTitle = preference?.customTitle.orEmpty(),
                enabled = preference?.enabled ?: true,
                heroSourceEnabled = false,
                order = preference?.order ?: 0,
                isCollection = true,
                collectionId = colDef.collectionId,
                isPinnedToTop = colDef.isPinnedToTop,
                rowPlacement = HomeCatalogRowPlacement.fromStorage(preference?.rowPlacement),
            )
        }

        val mergedItems = mergedRows.map { row ->
            val key = mergedHomeRowKey(row.id)
            val preference = preferences[key]
            HomeCatalogSettingsItem(
                key = key,
                defaultTitle = row.title,
                addonName = "Merged row",
                customTitle = preference?.customTitle.orEmpty(),
                enabled = preference?.enabled ?: true,
                heroSourceEnabled = false,
                order = preference?.order ?: 0,
                isMergedRow = true,
                mergedRowId = row.id,
                rowPlacement = HomeCatalogRowPlacement.fromStorage(preference?.rowPlacement),
            )
        }

        val items = orderHomeCatalogSettingsItems(
            items = catalogItems + collectionItems + cloudStreamCatalogItems + mergedItems,
            addonIdByCatalogKey = definitions
                .filterNot { ServerCatalog.isServerKey(it.key) }
                .associate { definition -> definition.key to definition.addonGroupId() } +
                cloudStreamCatalogItems.mapNotNull { item -> item.addonId?.let { item.key to it } }.toMap(),
            addonLayouts = addonLayouts.mapValues { (_, stored) ->
                HomeAddonLayoutPreference(
                    mode = HomeAddonLayoutMode.fromStorage(stored.mode),
                    order = stored.order,
                    anchorOrder = stored.anchorOrder,
                    anchorKey = stored.anchorKey,
                )
            },
            policy = addonRowOrderPolicy,
            upcomingRowKeys = upcomingRowKeys,
        )
        val addonGroups = definitions
            .filterNot { ServerCatalog.isServerKey(it.key) }
            .groupBy { it.addonGroupId() }
            .map { (addonId, addonDefinitions) ->
                val firstOrder = addonDefinitions.minOfOrNull { definition ->
                    preferences[definition.key]?.order ?: Int.MAX_VALUE
                }?.takeIf { it != Int.MAX_VALUE } ?: 0
                val storedLayout = addonLayouts[addonId]
                HomeAddonLayoutItem(
                    addonId = addonId,
                    addonName = addonDefinitions.first().addonName,
                    catalogCount = addonDefinitions.size,
                    mode = HomeAddonLayoutMode.fromStorage(storedLayout?.mode),
                    order = storedLayout?.order ?: firstOrder,
                    anchorOrder = storedLayout?.anchorOrder ?: firstOrder,
                    anchorKey = storedLayout?.anchorKey,
                )
            }
            .sortedWith(compareBy<HomeAddonLayoutItem> { it.order }.thenBy { it.addonName.lowercase() })

        _uiState.value = HomeCatalogSettingsUiState(
            heroEnabled = heroEnabled,
            heroStyle = heroStyle,
            heroTrailerPlaybackEnabled = heroTrailerPlaybackEnabled,
            heroTrailerStartDelaySeconds = heroTrailerStartDelaySeconds,
            heroTrailerStartUnmuted = heroTrailerStartUnmuted,
            showCatalogType = showCatalogType,
            hideUnreleasedContent = hideUnreleasedContent,
            hideMergedTitlesWithoutPosters = hideMergedTitlesWithoutPosters,
            mergedRows = mergedRows.sortedBy { it.order },
            items = items,
            addonGroups = addonGroups,
            addonRowOrderPolicy = addonRowOrderPolicy,
        )
    }

    private fun persist() {
        HomeCatalogSettingsStorage.savePayload(
            json.encodeToString(
                StoredHomeCatalogSettingsPayload(
                    heroEnabled = heroEnabled,
                    heroStyle = heroStyle.storageValue,
                    heroTrailerPlaybackEnabled = heroTrailerPlaybackEnabled,
                    heroTrailerStartDelaySeconds = heroTrailerStartDelaySeconds,
                    heroTrailerStartUnmuted = heroTrailerStartUnmuted,
                    showCatalogType = showCatalogType,
                    hideUnreleasedContent = hideUnreleasedContent,
                    hideMergedTitlesWithoutPosters = hideMergedTitlesWithoutPosters,
                    mergedRows = mergedRows.sortedBy { it.order },
                    items = preferences.values.sortedBy { it.order },
                    addonLayouts = addonLayouts.values.sortedWith(
                        compareBy<StoredHomeAddonLayoutPreference> { it.order }.thenBy { it.addonId },
                    ),
                    addonRowOrderPolicy = addonRowOrderPolicy.storageValue,
                    upcomingRowKeys = upcomingRowKeys.toList().sorted(),
                ),
            ),
        )
    }

    private fun updatePreference(
        key: String,
        pushRemote: Boolean = true,
        heroSourcesChanged: Boolean = false,
        transform: (StoredHomeCatalogPreference) -> StoredHomeCatalogPreference,
    ) {
        ensureLoaded()
        val current = preferences[key] ?: defaultPreferenceForMissingKey(key) ?: return
        val updated = transform(current)
        if (updated == current) return
        preferences = preferences + (key to updated)
        publish()
        persist()
        HomeRepository.applyCurrentSettings(heroSourcesChanged = heroSourcesChanged)
        if (pushRemote) {
            HomeCatalogSettingsSyncService.triggerPush()
        }
    }

    private fun selectedHeroSourceCount(excludingKey: String? = null): Int {
        val catalogKeys = definitions.mapTo(mutableSetOf()) { it.key }
        return preferences.count { (itemKey, preference) ->
            itemKey != excludingKey && itemKey in catalogKeys && preference.heroSourceEnabled
        }
    }

    private fun move(
        key: String,
        direction: Int,
    ) {
        val currentIndex = _uiState.value.items.indexOfFirst { it.key == key }
        moveByIndex(currentIndex, currentIndex + direction)
    }

    fun exportToSyncPayload(): SyncHomeCatalogPayload {
        ensureLoaded()
        val catalogDefinitionsByKey = definitions.associateBy { it.key }
        val collectionDefinitionsByKey = collectionDefinitions.associateBy { it.key }
        val items = preferences.values.filterNot { ServerCatalog.isServerKey(it.key) }.sortedBy { it.order }.map { pref ->
            val catalogDefinition = catalogDefinitionsByKey[pref.key]
            val collectionDefinition = collectionDefinitionsByKey[pref.key]
            val isCollection = collectionDefinition != null || pref.key.startsWith("collection_")
            if (isCollection) {
                SyncCatalogItem(
                    addonId = "",
                    type = "",
                    catalogId = "",
                    enabled = pref.enabled,
                    order = pref.order,
                    customTitle = pref.customTitle,
                    isCollection = true,
                    collectionId = collectionDefinition?.collectionId ?: pref.key.removePrefix("collection_"),
                    rowPlacement = pref.rowPlacement,
                    key = pref.key,
                )
            } else {
                val legacyParts = pref.key.split(':', limit = 3)
                SyncCatalogItem(
                    addonId = catalogDefinition?.addonIdForSync() ?: legacyParts.getOrElse(0) { "" },
                    type = catalogDefinition?.type ?: legacyParts.getOrElse(1) { "" },
                    catalogId = catalogDefinition?.catalogId ?: legacyParts.getOrElse(2) { "" },
                    enabled = pref.enabled,
                    order = pref.order,
                    customTitle = pref.customTitle,
                    isCollection = false,
                    rowPlacement = pref.rowPlacement,
                    key = pref.key,
                )
            }
        }
        return SyncHomeCatalogPayload(
            showCatalogType = showCatalogType,
            hideUnreleasedContent = hideUnreleasedContent,
            hideMergedTitlesWithoutPosters = hideMergedTitlesWithoutPosters,
            mergedRows = mergedRows.sortedBy { it.order },
            items = items,
            addonRowOrderPolicy = addonRowOrderPolicy.storageValue,
            upcomingRowKeys = upcomingRowKeys.toList().sorted(),
            addonLayouts = addonLayouts.values
                .sortedWith(compareBy<StoredHomeAddonLayoutPreference> { it.order }.thenBy { it.addonId })
                .map { stored ->
                    SyncAddonLayoutPreference(
                        addonId = stored.addonId,
                        mode = stored.mode,
                        order = stored.order,
                        anchorOrder = stored.anchorOrder,
                        anchorKey = stored.anchorKey,
                    )
                },
        )
    }

    fun applyFromRemote(payload: SyncHomeCatalogPayload) {
        ensureLoaded()
        showCatalogType = payload.showCatalogType
        hideUnreleasedContent = payload.hideUnreleasedContent
        hideMergedTitlesWithoutPosters = payload.hideMergedTitlesWithoutPosters
        mergedRows = payload.mergedRows.filter { it.id.isNotBlank() }
            .map { it.copy(sourceKeys = it.sourceKeys.distinct().take(MAX_MERGED_HOME_ROW_SOURCES)) }
        addonRowOrderPolicy = HomeAddonRowOrderPolicy.fromStorage(payload.addonRowOrderPolicy)
        upcomingRowKeys = payload.upcomingRowKeys.toSet()
        payload.addonLayouts
            ?.takeIf { layouts -> layouts.isNotEmpty() || payload.items.isNotEmpty() }
            ?.let { remoteLayouts ->
                addonLayouts = remoteLayouts
                    .filter { it.addonId.isNotBlank() }
                    .associate { layout ->
                        layout.addonId to StoredHomeAddonLayoutPreference(
                            addonId = layout.addonId,
                            mode = HomeAddonLayoutMode.fromStorage(layout.mode).storageValue,
                            order = layout.order,
                            anchorOrder = layout.anchorOrder,
                             anchorKey = layout.anchorKey,
                        )
                    }
            }
        if (payload.items.isNotEmpty()) {
            val existingHeroState = preferences.mapValues { it.value.heroSourceEnabled }
            val remotePreferences = payload.items.associate { item ->
                val key = item.preferenceKey()
                key to StoredHomeCatalogPreference(
                    key = key,
                    customTitle = item.customTitle,
                    enabled = item.enabled,
                    heroSourceEnabled = existingHeroState[key] ?: true,
                    order = item.order,
                    rowPlacement = HomeCatalogRowPlacement.fromStorage(item.rowPlacement).storageValue,
                )
            }
            val remoteKeys = remotePreferences.keys
            val knownKeys = knownPreferenceKeys()
            val preservedPreferences = preferences.filterKeys { key ->
                key !in remoteKeys && (key in knownKeys || key.requiresExplicitSyncKey())
            }
            preferences = preservedPreferences + remotePreferences
            normalizePreferences()
        }
        hasLoaded = true
        publish()
        persist()
        HomeRepository.applyCurrentSettings()
    }

    private fun allOrderedKeys(): List<String> {
        val catalogKeys = definitions.map { it.key }
        val collectionKeys = collectionDefinitions.map { it.key }
        val cloudStreamKeys = cloudStreamCatalogItems.map { it.key }
        val mergedKeys = mergedRows.map { mergedHomeRowKey(it.id) }
        return (catalogKeys + collectionKeys + cloudStreamKeys + mergedKeys)
            .sortedBy { key -> preferences[key]?.order ?: Int.MAX_VALUE }
    }

    private fun enforcePinnedCollectionsAtTop() {
        val orderedKeys = allOrderedKeys()
        if (orderedKeys.isEmpty()) return

        val pinnedCollectionKeys = collectionDefinitions
            .asSequence()
            .filter { it.isPinnedToTop }
            .map { it.key }
            .toSet()
        if (pinnedCollectionKeys.isEmpty()) return

        val pinnedKeys = orderedKeys.filter { it in pinnedCollectionKeys }
        if (pinnedKeys.isEmpty()) return

        val nonPinnedKeys = orderedKeys.filterNot { it in pinnedCollectionKeys }
        val reorderedKeys = pinnedKeys + nonPinnedKeys
        if (reorderedKeys == orderedKeys) return

        val updatedPreferences = preferences.toMutableMap()
        reorderedKeys.forEachIndexed { index, itemKey ->
            val current = updatedPreferences[itemKey] ?: return@forEachIndexed
            updatedPreferences[itemKey] = current.copy(order = index)
        }
        preferences = updatedPreferences

        val addonKeysById = definitions
            .filterNot { ServerCatalog.isServerKey(it.key) }
            .groupBy { it.addonGroupId() }
            .mapValues { (_, addonDefinitions) -> addonDefinitions.mapTo(mutableSetOf(), HomeCatalogDefinition::key) }
        addonLayouts = addonLayouts.mapValues { (addonId, layout) ->
            if (HomeAddonLayoutMode.fromStorage(layout.mode) != HomeAddonLayoutMode.LOCKED) {
                layout
            } else {
                val groupKeys = addonKeysById[addonId].orEmpty()
                val refreshedAnchor = reorderedKeys.indexOfFirst { it in groupKeys }
                if (refreshedAnchor >= 0 && layout.anchorKey == null) layout.copy(anchorOrder = refreshedAnchor) else layout
            }
        }
    }

    private fun defaultPreferenceForMissingKey(key: String): StoredHomeCatalogPreference? {
        val isCollection = collectionDefinitions.any { it.key == key }
        val isCloudStream = cloudStreamCatalogItems.any { it.key == key }
        val isMerged = validMergedRowKey(key)
        val isCatalog = definitions.any { it.key == key } || isCloudStream
        if (!isCollection && !isCatalog && !isMerged) return null

        return StoredHomeCatalogPreference(
            key = key,
            enabled = true,
            heroSourceEnabled = isCatalog && !isCloudStream && !isMerged &&
                selectedHeroSourceCount(excludingKey = key) < HERO_SOURCE_SELECTION_LIMIT,
            order = _uiState.value.items.firstOrNull { it.key == key }?.order
                ?: ((preferences.values.maxOfOrNull { it.order } ?: -1) + 1),
        )
    }

    private fun knownPreferenceKeys(): Set<String> =
        definitions.mapTo(mutableSetOf()) { it.key }.also { keys ->
            keys.addAll(cloudStreamCatalogItems.map { it.key })
            keys.addAll(collectionDefinitions.map { it.key })
            keys.addAll(mergedRows.map { mergedHomeRowKey(it.id) })
        }

    private fun HomeCatalogDefinition.addonIdForSync(): String {
        val suffix = ":$type:$catalogId"
        return key.removeSuffix(suffix)
    }

    private fun HomeCatalogDefinition.addonGroupId(): String = addonIdForSync()

    private fun SyncCatalogItem.preferenceKey(): String =
        key.ifBlank {
            if (isCollection) {
                "collection_$collectionId"
            } else {
                "$addonId:$type:$catalogId"
            }
        }

    private fun String.requiresExplicitSyncKey(): Boolean =
        !startsWith("collection_") && count { it == ':' } > 2
}


private data class HomeAddonOrderUnit<T>(
    val addonId: String?,
    val order: Int,
    val stableIndex: Int,
    val rows: List<T>,
)

private fun <T> reorderAddonUnitsInSlots(
    units: List<HomeAddonOrderUnit<T>>,
    addonOrder: Map<String, Int>,
): List<HomeAddonOrderUnit<T>> {
    val groupSlots = units.indices.filter { units[it].addonId != null }
    if (groupSlots.size < 2) return units
    val groupsInDesiredOrder = groupSlots.map { units[it] }.sortedWith(
        compareBy<HomeAddonOrderUnit<T>> { unit ->
            addonOrder[unit.addonId] ?: unit.order
        }.thenBy { it.addonId.orEmpty() },
    )
    return units.toMutableList().also { reordered ->
        groupSlots.forEachIndexed { index, slot -> reordered[slot] = groupsInDesiredOrder[index] }
    }
}

internal fun orderHomeCatalogSettingsItems(
    items: List<HomeCatalogSettingsItem>,
    addonIdByCatalogKey: Map<String, String>,
    addonLayouts: Map<String, HomeAddonLayoutPreference>,
    policy: HomeAddonRowOrderPolicy = HomeAddonRowOrderPolicy.MOVE_UNPINNED_ROWS,
    upcomingRowKeys: Set<String> = emptySet(),
): List<HomeCatalogSettingsItem> {
    val original = items.withIndex().sortedWith(
        compareBy<IndexedValue<HomeCatalogSettingsItem>> { it.value.order }.thenBy { it.index },
    )
    if (original.isEmpty()) return emptyList()
    val orderedRows = original.map { it.value }
    val rankByKey = original.mapIndexed { rank, indexed -> indexed.value.key to rank }.toMap()
    val groupOrder = orderedRows.mapNotNull { row ->
        addonIdByCatalogKey[row.key]?.let { addonId ->
            addonId to (addonLayouts[addonId]?.order ?: rankByKey[row.key] ?: row.order)
        }
    }.groupBy({ it.first }, { it.second }).mapValues { (addonId, values) ->
        addonLayouts[addonId]?.order ?: values.minOrNull() ?: Int.MAX_VALUE
    }

    fun unitsFor(
        rows: List<HomeCatalogSettingsItem>,
        groupable: (HomeCatalogSettingsItem) -> String?,
    ): List<HomeAddonOrderUnit<HomeCatalogSettingsItem>> {
        val grouped = rows.mapNotNull { row -> groupable(row)?.let { it to row } }
            .groupBy({ it.first }, { it.second })
        val groupedRows = grouped.values.flatten().mapTo(mutableSetOf()) { it.key }
        val units = rows.filterNot { it.key in groupedRows }.map { row ->
            HomeAddonOrderUnit(
                addonId = null,
                order = row.order,
                stableIndex = rankByKey[row.key] ?: Int.MAX_VALUE,
                rows = listOf(row),
            )
        }.toMutableList()
        grouped.forEach { (addonId, groupRows) ->
            units += HomeAddonOrderUnit(
                addonId = addonId,
                order = groupRows.minOfOrNull { it.order } ?: Int.MAX_VALUE,
                stableIndex = groupRows.minOfOrNull { rankByKey[it.key] ?: Int.MAX_VALUE } ?: Int.MAX_VALUE,
                rows = groupRows.sortedWith(
                    compareBy<HomeCatalogSettingsItem> { it.order }
                        .thenBy { rankByKey[it.key] ?: Int.MAX_VALUE },
                ),
            )
        }
        return units.sortedWith(compareBy<HomeAddonOrderUnit<HomeCatalogSettingsItem>> { it.order }
            .thenBy { it.stableIndex })
    }

    fun flatten(units: List<HomeAddonOrderUnit<HomeCatalogSettingsItem>>) = units.flatMap { it.rows }
    val pinnedCollectionTop = orderedRows.filter {
        it.isCollection && (it.isPinnedToTop || it.rowPlacement == HomeCatalogRowPlacement.PINNED_TOP)
    }
    val pinnedCollectionBottom = orderedRows.filter {
        it.isCollection && !it.isPinnedToTop && it.rowPlacement == HomeCatalogRowPlacement.PINNED_BOTTOM
    }
    val groupOrderExcludingCollections = groupOrder

    if (policy == HomeAddonRowOrderPolicy.MOVE_ALL_ROWS) {
        // This mode intentionally moves every catalog from an add-on as one block,
        // including catalog rows that have individual pins. Collection pinning remains
        // independent and keeps its existing behavior.
        val middle = orderedRows.filterNot { it in pinnedCollectionTop || it in pinnedCollectionBottom }
        val units = unitsFor(middle) { row ->
            if (row.isCollection) null else addonIdByCatalogKey[row.key]
        }
        return pinnedCollectionTop + flatten(reorderAddonUnitsInSlots(units, groupOrderExcludingCollections)) +
            pinnedCollectionBottom
    }

    val pinnedTop = orderedRows.filter { row ->
        row !in pinnedCollectionBottom &&
            (row.rowPlacement == HomeCatalogRowPlacement.PINNED_TOP || row.isPinnedToTop)
    }
    val pinnedBottom = orderedRows.filter { row ->
        row !in pinnedCollectionTop && row !in pinnedTop &&
            row.rowPlacement == HomeCatalogRowPlacement.PINNED_BOTTOM
    }
    val middle = orderedRows.filterNot { it in pinnedTop || it in pinnedBottom }

    if (policy == HomeAddonRowOrderPolicy.UPCOMING_ROWS_ONLY) {
        val currentRows = middle.filterNot { it.key in upcomingRowKeys }
        val upcomingRows = middle.filter { it.key in upcomingRowKeys }
        val currentUnits = currentRows.map { row ->
            HomeAddonOrderUnit(
                addonId = null,
                order = row.order,
                stableIndex = rankByKey[row.key] ?: Int.MAX_VALUE,
                rows = listOf(row),
            )
        }
        val upcomingUnits = unitsFor(upcomingRows) { row ->
            if (row.rowPlacement == HomeCatalogRowPlacement.NORMAL && !row.isPinnedToTop && !row.isCollection)
                addonIdByCatalogKey[row.key] else null
        }.sortedWith(
            compareBy<HomeAddonOrderUnit<HomeCatalogSettingsItem>> {
                it.addonId?.let { addonId -> groupOrder[addonId] } ?: Int.MAX_VALUE
            }.thenBy { it.stableIndex },
        )
        return pinnedTop + flatten(currentUnits + upcomingUnits) + pinnedBottom
    }

    val units = unitsFor(middle) { row ->
        if (row.rowPlacement == HomeCatalogRowPlacement.NORMAL && !row.isPinnedToTop && !row.isCollection)
            addonIdByCatalogKey[row.key] else null
    }
    return pinnedTop + flatten(reorderAddonUnitsInSlots(units, groupOrder)) + pinnedBottom
}

internal fun orderHomeCatalogDefinitions(
    definitions: List<HomeCatalogDefinition>,
    snapshot: HomeCatalogSettingsSnapshot,
): List<HomeCatalogDefinition> {
    val original = definitions.withIndex().sortedWith(
        compareBy<IndexedValue<HomeCatalogDefinition>> {
            snapshot.preferences[it.value.key]?.order ?: Int.MAX_VALUE
        }.thenBy { it.index },
    )
    if (original.isEmpty()) return emptyList()
    val orderedRows = original.map { it.value }
    val rankByKey = original.mapIndexed { rank, indexed -> indexed.value.key to rank }.toMap()
    fun addonId(definition: HomeCatalogDefinition): String? {
        if (ServerCatalog.isServerKey(definition.key)) return null
        val suffix = ":" + definition.type + ":" + definition.catalogId
        return definition.key.removeSuffix(suffix)
    }
    fun placement(definition: HomeCatalogDefinition) =
        snapshot.preferences[definition.key]?.rowPlacement ?: HomeCatalogRowPlacement.NORMAL
    val groupOrder = orderedRows.mapNotNull { definition ->
        addonId(definition)?.let { id ->
            id to (snapshot.addonLayouts[id]?.order ?: rankByKey[definition.key] ?: Int.MAX_VALUE)
        }
    }.groupBy({ it.first }, { it.second }).mapValues { (id, values) ->
        snapshot.addonLayouts[id]?.order ?: values.minOrNull() ?: Int.MAX_VALUE
    }

    fun unitsFor(
        rows: List<HomeCatalogDefinition>,
        groupable: (HomeCatalogDefinition) -> String?,
    ): List<HomeAddonOrderUnit<HomeCatalogDefinition>> {
        val grouped = rows.mapNotNull { row -> groupable(row)?.let { it to row } }
            .groupBy({ it.first }, { it.second })
        val groupedKeys = grouped.values.flatten().mapTo(mutableSetOf()) { it.key }
        val units = rows.filterNot { it.key in groupedKeys }.map { row ->
            HomeAddonOrderUnit(
                addonId = null,
                order = snapshot.preferences[row.key]?.order ?: Int.MAX_VALUE,
                stableIndex = rankByKey[row.key] ?: Int.MAX_VALUE,
                rows = listOf(row),
            )
        }.toMutableList()
        grouped.forEach { (id, rowsInGroup) ->
            units += HomeAddonOrderUnit(
                addonId = id,
                order = rowsInGroup.minOfOrNull { snapshot.preferences[it.key]?.order ?: Int.MAX_VALUE } ?: Int.MAX_VALUE,
                stableIndex = rowsInGroup.minOfOrNull { rankByKey[it.key] ?: Int.MAX_VALUE } ?: Int.MAX_VALUE,
                rows = rowsInGroup.sortedWith(
                    compareBy<HomeCatalogDefinition> { snapshot.preferences[it.key]?.order ?: Int.MAX_VALUE }
                        .thenBy { rankByKey[it.key] ?: Int.MAX_VALUE },
                ),
            )
        }
        return units.sortedWith(compareBy<HomeAddonOrderUnit<HomeCatalogDefinition>> { it.order }
            .thenBy { it.stableIndex })
    }

    fun flatten(units: List<HomeAddonOrderUnit<HomeCatalogDefinition>>) = units.flatMap { it.rows }
    val pinnedTop = orderedRows.filter { placement(it) == HomeCatalogRowPlacement.PINNED_TOP }
    val pinnedBottom = orderedRows.filter { placement(it) == HomeCatalogRowPlacement.PINNED_BOTTOM }
    val middle = orderedRows.filterNot { it in pinnedTop || it in pinnedBottom }

    if (snapshot.addonRowOrderPolicy == HomeAddonRowOrderPolicy.MOVE_ALL_ROWS) {
        val units = unitsFor(middle + pinnedTop + pinnedBottom) { addonId(it) }
        return flatten(reorderAddonUnitsInSlots(units, groupOrder))
    }

    if (snapshot.addonRowOrderPolicy == HomeAddonRowOrderPolicy.UPCOMING_ROWS_ONLY) {
        val currentRows = middle.filterNot { it.key in snapshot.upcomingRowKeys }
        val upcomingRows = middle.filter { it.key in snapshot.upcomingRowKeys }
        val currentUnits = currentRows.map { row ->
            HomeAddonOrderUnit(
                addonId = null,
                order = snapshot.preferences[row.key]?.order ?: Int.MAX_VALUE,
                stableIndex = rankByKey[row.key] ?: Int.MAX_VALUE,
                rows = listOf(row),
            )
        }
        val upcomingUnits = unitsFor(upcomingRows) {
            if (placement(it) == HomeCatalogRowPlacement.NORMAL) addonId(it) else null
        }.sortedWith(
            compareBy<HomeAddonOrderUnit<HomeCatalogDefinition>> {
                it.addonId?.let { id -> groupOrder[id] } ?: Int.MAX_VALUE
            }.thenBy { it.stableIndex },
        )
        return pinnedTop + flatten(currentUnits + upcomingUnits) + pinnedBottom
    }

    val units = unitsFor(middle) {
        if (placement(it) == HomeCatalogRowPlacement.NORMAL) addonId(it) else null
    }
    return pinnedTop + flatten(reorderAddonUnitsInSlots(units, groupOrder)) + pinnedBottom
}

internal data class CollectionCatalogDefinition(
    val key: String,
    val collectionId: String,
    val title: String,
    val subtitle: String,
    val isPinnedToTop: Boolean,
)

internal fun visibleCollectionsWithUniqueIds(collections: List<Collection>): List<Collection> =
    collections
        .filter { collection -> collection.folders.isNotEmpty() }
        .distinctBy(Collection::id)

internal fun buildCollectionDefinitions(collections: List<Collection>): List<CollectionCatalogDefinition> =
    visibleCollectionsWithUniqueIds(collections).map { collection ->
        CollectionCatalogDefinition(
            key = "collection_${collection.id}",
            collectionId = collection.id,
            title = collection.title,
            subtitle = runBlocking { getString(Res.string.collections_folder_count, collection.folders.size) },
            isPinnedToTop = collection.pinToTop,
        )
    }

private data class CatalogSyncInput(
    val addons: List<ManagedAddon>,
    val collections: List<Collection>,
    val locale: String,
    val serverRevision: Int,
)
