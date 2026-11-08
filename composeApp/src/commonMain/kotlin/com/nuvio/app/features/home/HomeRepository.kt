package com.nuvio.app.features.home

import com.nuvio.app.features.addons.ManagedAddon
import com.nuvio.app.features.addons.AddonRepository
import com.nuvio.app.features.addons.enabledAddons
import com.nuvio.app.features.catalog.CatalogTarget
import com.nuvio.app.features.catalog.fetchCatalogPage
import com.nuvio.app.features.cloudstream.CloudStreamPluginItem
import com.nuvio.app.features.cloudstream.CloudStreamRepository
import com.nuvio.app.features.cloudstream.toMetaPreview
import com.nuvio.app.core.poster.CustomPosterUrlRepository
import com.nuvio.app.core.poster.reapplyCustomPosterUrls
import com.nuvio.app.core.poster.withCustomPosterUrls
import com.nuvio.app.features.collection.Collection
import com.nuvio.app.features.collection.CollectionRepository
import com.nuvio.app.features.collection.CollectionSource
import com.nuvio.app.features.collection.TmdbCollectionSourceResolver
import com.nuvio.app.features.collection.catalogRouteKey
import com.nuvio.app.features.collection.findCollectionCatalog
import com.nuvio.app.features.servers.ServerCatalog
import com.nuvio.app.features.trakt.TraktPublicListSourceResolver
import com.nuvio.app.features.watchprogress.CurrentDateProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.absoluteValue
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.TimeSource

object HomeRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var activeJob: Job? = null
    private var activeRequestKey: String? = null
    private var currentRequestKey: String? = null
    private var currentDefinitions: List<HomeCatalogDefinition> = emptyList()
    private var cachedSections: Map<String, HomeCatalogSection> = emptyMap()
    private var cachedCollectionHeroItems: List<MetaPreview> = emptyList()
    private var collectionHeroJob: Job? = null
    private var collectionHeroRequestKey: String? = null
    private var lastPublishedCatalogHeroEmpty: Boolean = true
    private var lastErrorMessage: String? = null
    private var lastPublishedEntries: List<Pair<String, HomeCatalogSection>> = emptyList()
    private var preloadedSnapshot: HomeSnapshot? = null
    private var snapshotGeneration = 0
    private var snapshotSaveJob: Job? = null
    private var lastRefreshAddonSignature: List<String>? = null
    private var collectionHeroResolving: Boolean = false
    private var cloudStreamGeneration = 0L
    private var cachedCloudStreamSections: List<HomeCatalogSection> = emptyList()
    private var cloudStreamHomeJob: Job? = null
    private var cloudStreamHomeRequestKey: String? = null
    private var cloudStreamHomeCompletedKey: String? = null

    fun preloadSnapshot() {
        val profileKey = HomeSnapshotStore.currentProfileKey() ?: return
        preloadedSnapshot = HomeSnapshotStore.load(profileKey)
    }

    fun refresh(addons: List<ManagedAddon>, force: Boolean = false) {
        val activeAddons = addons.enabledAddons()
        lastRefreshAddonSignature = buildAddonCatalogRefreshSignature(addons)
        val requests = buildHomeCatalogDefinitions(activeAddons)
        currentDefinitions = requests
        val requestCacheKeys = requests.mapTo(mutableSetOf(), HomeCatalogDefinition::cacheKey)
        cachedSections = cachedSections.filterKeys(requestCacheKeys::contains)
        val requestKey = requests.joinToString(separator = "|", transform = HomeCatalogDefinition::cacheKey)
        currentRequestKey = requestKey
        refreshCloudStreamHomeSections()

        if (!force && activeRequestKey == requestKey && _uiState.value.isLoading) return
        activeRequestKey = requestKey
        snapshotGeneration++

        if (requests.isEmpty()) {
            activeJob?.cancel()
            activeJob = null
            activeRequestKey = null
            cachedSections = emptyMap()
            lastErrorMessage = null
            publishCurrentState(
                isLoading = false,
                requestKey = requestKey,
            )
            ensureCollectionHeroFallback(
                addons = activeAddons,
                forceRefresh = force,
                refreshSources = true,
                requestKey = requestKey,
            )
            return
        }

        activeJob?.cancel()
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        activeJob = scope.launch {
            val prioritizedRequests = prioritizeDefinitions(
                definitions = requests,
                snapshot = HomeCatalogSettingsRepository.snapshot(),
            )
            val loadedSections = linkedMapOf<String, HomeCatalogSection>().apply {
                putAll(cachedSections)
            }
            var firstErrorMessage: String? = null
            var publishedOnce = false
            var lastPublishMark = TimeSource.Monotonic.markNow()
            val fetchPermits = Semaphore(HOME_CATALOG_MAX_CONCURRENT_FETCHES)
            val completions = Channel<Pair<HomeCatalogDefinition, Result<HomeCatalogSection>>>(Channel.UNLIMITED)

            prioritizedRequests.forEach { request ->
                launch {
                    val result = fetchPermits.withPermit {
                        runCatching {
                            withTimeoutOrNull(HOME_CATALOG_FETCH_TIMEOUT_MS) {
                                request.toSection(forceRefresh = force)
                            } ?: throw IllegalStateException("Catalog request timed out: ${request.defaultTitle}")
                        }
                    }
                    completions.trySend(request to result)
                }
            }

            repeat(prioritizedRequests.size) {
                val (request, result) = completions.receive()
                if (activeRequestKey != requestKey) return@launch

                result.getOrNull()?.let { section -> loadedSections[request.cacheKey] = section }
                if (firstErrorMessage == null) {
                    firstErrorMessage = result.exceptionOrNull()?.message
                }
                cachedSections = loadedSections.toMap()
                lastErrorMessage = firstErrorMessage
                if (!publishedOnce || lastPublishMark.elapsedNow().inWholeMilliseconds >= HOME_CATALOG_PUBLISH_THROTTLE_MS) {
                    publishedOnce = true
                    lastPublishMark = TimeSource.Monotonic.markNow()
                    publishCurrentState(
                        isLoading = true,
                        requestKey = requestKey,
                    )
                }
            }

            if (activeRequestKey != requestKey) return@launch

            cachedSections = loadedSections.toMap()
            lastErrorMessage = firstErrorMessage
            activeRequestKey = null
            publishCurrentState(
                isLoading = false,
                requestKey = requestKey,
            )
            scheduleSnapshotSave()
            ensureCollectionHeroFallback(
                addons = activeAddons,
                forceRefresh = force,
                refreshSources = true,
                requestKey = requestKey,
            )
        }
    }

    private fun refreshCloudStreamHomeSections() {
        val generation = cloudStreamGeneration
        CloudStreamRepository.initialize()
        val state = CloudStreamRepository.uiState.value
        val hiddenProviderIds = state.hiddenHomeCatalogProviderIds
        val plugins = if (state.hideAllHomeCatalogs) emptyList() else state.plugins
            .filter(CloudStreamPluginItem::isRunnable)
            .filterNot { it.metadata.id.value in hiddenProviderIds }
        val runnableProviderIds = plugins.mapTo(mutableSetOf()) { it.metadata.id.value }
        val requestKey = buildString {
            append(state.hideAllHomeCatalogs).append('|')
            append(hiddenProviderIds.sorted().joinToString(",")).append('|')
            plugins.sortedBy { it.metadata.id.value }.forEach { plugin ->
                append(plugin.metadata.id.value).append(':')
                append(plugin.metadata.version).append(':')
                append(plugin.installedVersion).append(':')
                append(plugin.enabled).append(':')
                append(plugin.verified).append('|')
            }
        }
        cachedCloudStreamSections = if (state.hideAllHomeCatalogs) emptyList() else
            cachedCloudStreamSections.filter { section ->
                val target = section.target as? CatalogTarget.CloudStream
                target == null || (target.providerId in runnableProviderIds && target.providerId !in hiddenProviderIds)
            }
        if (
            cloudStreamHomeRequestKey == requestKey &&
            (cloudStreamHomeJob?.let { !it.isCompleted } == true || cloudStreamHomeCompletedKey == requestKey)
        ) return
        cloudStreamHomeJob?.cancel()
        cloudStreamHomeRequestKey = requestKey
        if (plugins.isEmpty()) {
            HomeCatalogSettingsRepository.syncCloudStreamCatalogs(emptyList())
            cloudStreamHomeCompletedKey = requestKey
            cloudStreamHomeJob = null
            publishCurrentState(isLoading = _uiState.value.isLoading, requestKey = currentRequestKey)
            return
        }
        publishCurrentState(isLoading = _uiState.value.isLoading, requestKey = currentRequestKey)
        lateinit var loadJob: Job
        loadJob = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val providerResults = coroutineScope {
                    val pluginSemaphore = Semaphore(3)
                    plugins.map { plugin ->
                        async {
                            pluginSemaphore.withPermit {
                                try {
                                    Result.success(
                                        CloudStreamRepository.getMainPage(plugin.metadata.id.value, 1).getOrThrow()
                                            .mapNotNull { (sectionName, items) ->
                                                if (items.isEmpty()) return@mapNotNull null
                                                val contentType = items.firstOrNull()?.type?.nuvioType ?: "other"
                                                HomeCatalogSection(
                                                    key = "cloudstream:${plugin.metadata.id.value}:${sectionName}",
                                                    title = sectionName,
                                                    subtitle = plugin.metadata.name,
                                                    addonName = plugin.metadata.name,
                                                    target = CatalogTarget.CloudStream(
                                                        providerId = plugin.metadata.id.value,
                                                        sectionName = sectionName,
                                                        contentType = contentType,
                                                    ),
                                                    items = items.map { it.toMetaPreview() },
                                                    availableItemCount = items.size,
                                                    hasMore = true,
                                                )
                                            },
                                    )
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (error: Throwable) {
                                    Result.failure(error)
                                }
                            }
                        }
                    }.awaitAll()
                }
                val allProvidersSucceeded = providerResults.all { it.isSuccess }
                val sections = providerResults.mapNotNull { it.getOrNull() }.flatten()
                if (generation != cloudStreamGeneration || cloudStreamHomeRequestKey != requestKey) return@launch

                val latestState = CloudStreamRepository.uiState.value
                val latestRunnableIds = if (latestState.hideAllHomeCatalogs) emptySet() else
                    latestState.plugins.filter(CloudStreamPluginItem::isRunnable).mapTo(mutableSetOf()) { it.metadata.id.value }
                val visibleSections = if (latestState.hideAllHomeCatalogs) emptyList() else
                    sections.filter { section ->
                        val target = section.target as? CatalogTarget.CloudStream
                        target == null || (
                            target.providerId in latestRunnableIds &&
                                target.providerId !in latestState.hiddenHomeCatalogProviderIds
                        )
                    }
                HomeCatalogSettingsRepository.syncCloudStreamCatalogs(visibleSections)
                cachedCloudStreamSections = visibleSections
                cloudStreamHomeCompletedKey = requestKey.takeIf { allProvidersSucceeded }
                publishCurrentState(isLoading = _uiState.value.isLoading, requestKey = currentRequestKey)
            } finally {
                if (cloudStreamHomeJob === loadJob) cloudStreamHomeJob = null
            }
        }
        cloudStreamHomeJob = loadJob
        loadJob.start()
    }

    fun applyCurrentSettings(heroSourcesChanged: Boolean = false) {
        refreshCloudStreamHomeSections()
        publishCurrentState(
            isLoading = _uiState.value.isLoading,
            requestKey = currentRequestKey,
            keepVisibleHeroWhilePending = !heroSourcesChanged,
        )
        ensureCollectionHeroFallback(
            addons = AddonRepository.uiState.value.addons.enabledAddons(),
            forceRefresh = false,
            refreshSources = false,
            requestKey = currentRequestKey,
        )
    }

    fun clear() {
        cloudStreamGeneration++
        cloudStreamHomeJob?.cancel()
        cloudStreamHomeJob = null
        cloudStreamHomeRequestKey = null
        cloudStreamHomeCompletedKey = null
        cachedCloudStreamSections = emptyList()
        activeJob?.cancel()
        activeJob = null
        activeRequestKey = null
        currentRequestKey = null
        currentDefinitions = emptyList()
        cachedSections = emptyMap()
        cachedCollectionHeroItems = emptyList()
        collectionHeroJob?.cancel()
        collectionHeroJob = null
        collectionHeroRequestKey = null
        lastPublishedCatalogHeroEmpty = true
        lastErrorMessage = null
        lastPublishedEntries = emptyList()
        snapshotSaveJob?.cancel()
        snapshotSaveJob = null
        lastRefreshAddonSignature = null
        collectionHeroResolving = false
        _uiState.value = HomeUiState()
        restoreSnapshot()
    }

    private fun restoreSnapshot() {
        val profileKey = HomeSnapshotStore.currentProfileKey() ?: return
        val generation = ++snapshotGeneration
        val preloaded = preloadedSnapshot?.takeIf { it.profileKey == profileKey }
        preloadedSnapshot = null
        if (preloaded != null) {
            applySnapshot(preloaded)
            return
        }
        scope.launch {
            val loaded = HomeSnapshotStore.load(profileKey) ?: return@launch
            if (generation != snapshotGeneration || cachedSections.isNotEmpty()) return@launch
            applySnapshot(loaded)
        }
    }

    private fun applySnapshot(snapshot: HomeSnapshot) {
        if (snapshot.sections.isEmpty()) return
        cachedSections = snapshot.sections.toMap()
        val hidePosterlessTitles = HomeCatalogSettingsRepository.snapshot().hideMergedTitlesWithoutPosters
        val visibleSections = snapshot.sections.mapNotNull { (_, section) ->
            val visibleItems = if (hidePosterlessTitles) {
                filterMergedHomeItems(
                    items = section.items,
                    contentType = MergedHomeRowContentType.ALL,
                    hideTitlesWithoutPosters = true,
                )
            } else {
                section.items
            }
            section.copy(items = visibleItems).takeIf { it.items.isNotEmpty() || it.hasMore }
        }
        val visibleHeroItems = if (hidePosterlessTitles) {
            filterMergedHomeItems(
                items = snapshot.heroItems,
                contentType = MergedHomeRowContentType.ALL,
                hideTitlesWithoutPosters = true,
            )
        } else {
            snapshot.heroItems
        }
        _uiState.value = HomeUiState(
            isLoading = true,
            heroItems = visibleHeroItems,
            sections = visibleSections,
        )
    }

    private fun scheduleSnapshotSave() {
        val profileKey = HomeSnapshotStore.currentProfileKey() ?: return
        val entries = lastPublishedEntries
        if (entries.isEmpty()) return
        val heroItems = _uiState.value.heroItems
        snapshotSaveJob?.cancel()
        snapshotSaveJob = scope.launch {
            delay(HOME_SNAPSHOT_SAVE_DELAY_MS)
            HomeSnapshotStore.save(
                profileKey = profileKey,
                savedAtMillis = Clock.System.now().toEpochMilliseconds(),
                sections = entries,
                heroItems = heroItems,
            )
        }
    }

    private fun publishCurrentState(
        isLoading: Boolean,
        requestKey: String?,
        keepVisibleHeroWhilePending: Boolean = true,
    ) {
        if (currentDefinitions.isEmpty() && cachedSections.isNotEmpty()) return
        val snapshot = HomeCatalogSettingsRepository.snapshot()
        val preferences = snapshot.preferences
        val todayIsoDate = if (snapshot.hideUnreleasedContent) CurrentDateProvider.todayIsoDate() else null
        CustomPosterUrlRepository.ensureLoaded()
        val posterPattern = CustomPosterUrlRepository.patternForScreen(com.nuvio.app.core.poster.CustomPosterScreen.HOME)
        fun HomeCatalogSection.withReleaseFilter(): HomeCatalogSection =
            if (todayIsoDate == null) this else filterReleasedItems(todayIsoDate)
        fun HomeCatalogSection.withPosterOverlay(): HomeCatalogSection =
            copy(items = items.reapplyCustomPosterUrls(posterPattern))
        fun HomeCatalogSection.withGlobalPosterFilter(): HomeCatalogSection =
            if (!snapshot.hideMergedTitlesWithoutPosters) this
            else copy(
                items = filterMergedHomeItems(
                    items = items,
                    contentType = MergedHomeRowContentType.ALL,
                    hideTitlesWithoutPosters = true,
                ),
            )

        val hiddenIncludedSourceKeys = snapshot.mergedRows
            .asSequence()
            .filter { row -> row.hideIncludedRows && preferences[mergedHomeRowKey(row.id)]?.enabled != false }
            .flatMap { it.sourceKeys.asSequence() }
            .toSet()

        val entries = orderHomeCatalogDefinitions(currentDefinitions, snapshot)
            .mapNotNull { definition ->
                val preference = preferences[definition.key]
                if (preference?.enabled == false || definition.key in hiddenIncludedSourceKeys) return@mapNotNull null

                val section = cachedSections[definition.cacheKey]
                    ?.withPosterOverlay()
                    ?.withReleaseFilter()
                    ?.withGlobalPosterFilter()
                    ?: return@mapNotNull null
                if (section.items.isEmpty()) return@mapNotNull null
                val customTitle = preference?.customTitle.orEmpty()
                definition.cacheKey to section.copy(
                    title = customTitle.ifBlank { definition.titleFor(snapshot.showCatalogType) },
                )
            }
        if (entries.isNotEmpty() || currentDefinitions.isNotEmpty()) {
            lastPublishedEntries = entries
        }
        val visibleCatalogSectionsByKey = entries.associate { (_, section) -> section.key to section }
        val sourceSectionsByKey = currentDefinitions.mapNotNull { definition ->
            val section = visibleCatalogSectionsByKey[definition.key]
                ?: cachedSections[definition.cacheKey]
                    ?.withPosterOverlay()
                    ?.withReleaseFilter()
                    ?.withGlobalPosterFilter()
                ?: return@mapNotNull null
            if (section.items.isEmpty() && !section.hasMore) return@mapNotNull null
            val customTitle = preferences[definition.key]?.customTitle.orEmpty()
            definition.key to section.copy(
                title = customTitle.ifBlank { definition.titleFor(snapshot.showCatalogType) },
            )
        }.toMap()

        val mergedSections = snapshot.mergedRows
            .sortedBy { it.order }
            .mapNotNull { mergedRow ->
                val resolvedRow = HomeCatalogSettingsRepository.resolveMergedHomeRow(mergedRow.id)
                    ?: return@mapNotNull null
                val row = resolvedRow.definition
                val rowKey = mergedHomeRowKey(row.id)
                if (preferences[rowKey]?.enabled == false) return@mapNotNull null
                val sourceSections = resolvedRow.sources.mapNotNull { source ->
                    sourceSectionsByKey[source.definition.key]?.copy(title = source.title)
                }.map { it.withGlobalPosterFilter() }
                if (sourceSections.isEmpty()) return@mapNotNull null
                val displayMode = row.resolvedDisplayMode()
                val contentType = row.contentTypeForDisplay()
                val mergedItems = mergeHomeRowItems(
                    sourceItems = sourceSections.map(HomeCatalogSection::items),
                    deduplicate = row.deduplicate,
                    maxItems = HOME_MERGED_ROW_ITEM_LIMIT,
                    blendMode = row.resolvedBlendMode(),
                    sortMode = MergedHomeRowSortMode.OFF,
                    contentType = contentType,
                    hideTitlesWithoutPosters = snapshot.hideMergedTitlesWithoutPosters,
                )
                val hasMore = sourceSections.any(HomeCatalogSection::hasMore)
                if (mergedItems.isEmpty() && !hasMore) return@mapNotNull null
                val sourceTabs = when (displayMode) {
                    MergedHomeRowDisplayMode.SOURCE_TABS -> buildList {
                        add(HomeCatalogTab("all", "All", mergedItems, mergedItems.size, hasMore))
                        sourceSections.forEach { source ->
                            val sourceItems = filterMergedHomeItems(
                                items = source.items,
                                contentType = contentType,
                                hideTitlesWithoutPosters = snapshot.hideMergedTitlesWithoutPosters,
                            )
                            add(
                                HomeCatalogTab(
                                    key = source.key,
                                    title = cleanMergedSourceTabTitle(source.title),
                                    items = sourceItems,
                                    availableItemCount = sourceItems.size,
                                    hasMore = source.hasMore,
                                ),
                            )
                        }
                    }
                    MergedHomeRowDisplayMode.TYPE_TABS -> buildMergedHomeTypeTabs(
                        sourceItems = sourceSections.map(HomeCatalogSection::items),
                        allItems = mergedItems,
                        deduplicate = row.deduplicate,
                        blendMode = row.resolvedBlendMode(),
                        sortMode = MergedHomeRowSortMode.OFF,
                        hideTitlesWithoutPosters = snapshot.hideMergedTitlesWithoutPosters,
                        hasMore = hasMore,
                        maxItems = HOME_MERGED_ROW_ITEM_LIMIT,
                    )
                    MergedHomeRowDisplayMode.UNIFIED_CAROUSEL -> emptyList()
                }
                val customTitle = preferences[rowKey]?.customTitle.orEmpty()
                HomeCatalogSection(
                    key = rowKey,
                    title = customTitle.ifBlank { row.title },
                    subtitle = if (resolvedRow.unavailableSourceCount > 0 || sourceSections.size < row.sourceKeys.size) {
                        "Merged row · ${sourceSections.size}/${row.sourceKeys.size} sources available"
                    } else {
                        "Merged row"
                    },
                    addonName = "Merged row",
                    target = CatalogTarget.MergedRow(rowId = row.id),
                    items = mergedItems,
                    availableItemCount = mergedItems.size,
                    hasMore = hasMore,
                    isMergedRow = true,
                    displayMode = row.displayMode,
                    sourceTabs = sourceTabs,
                    supportsViewAll = true,
                )
            }
        val cloudStreamSections = cachedCloudStreamSections.mapNotNull { section ->
            val preference = preferences[section.key]
            if (preference?.enabled == false) return@mapNotNull null
            section.copy(
                items = section.items.reapplyCustomPosterUrls(posterPattern),
            ).withReleaseFilter().withGlobalPosterFilter().copy(
                title = preference?.customTitle?.takeIf { it.isNotBlank() } ?: section.title,
            )
        }
        val configuredOrder = HomeCatalogSettingsRepository.uiState.value.items
            .mapIndexed { index, item -> item.key to index }
            .toMap()
        val sections = (entries.map { (_, section) -> section } + mergedSections + cloudStreamSections)
            .sortedBy { section -> configuredOrder[section.key] ?: Int.MAX_VALUE }

        val catalogHeroItems = if (snapshot.heroEnabled) {
            val heroRandom = Random((requestKey?.hashCode() ?: 0).absoluteValue + 1)
            currentDefinitions
                .filter { definition -> preferences[definition.key]?.heroSourceEnabled != false }
                .mapNotNull { definition -> cachedSections[definition.cacheKey] }
                .map { section ->
                    section.withPosterOverlay()
                        .withReleaseFilter()
                        .withGlobalPosterFilter()
                }
                .flatMap { section -> section.items }
                .distinctBy { item -> "${item.type}:${item.id}" }
                .shuffled(heroRandom)
                .take(HOME_HERO_ITEM_LIMIT)
        } else {
            emptyList()
        }
        lastPublishedCatalogHeroEmpty = snapshot.heroEnabled && catalogHeroItems.isEmpty()
        val visibleHeroItems = _uiState.value.heroItems
        val selectedHeroItems = if (snapshot.heroEnabled) {
            if (
                keepVisibleHeroWhilePending &&
                visibleHeroItems.isNotEmpty() &&
                isHeroResolutionPending(isLoading)
            ) {
                visibleHeroItems
            } else {
                catalogHeroItems.ifEmpty { cachedCollectionHeroItems }
            }
        } else {
            emptyList()
        }
        val heroItems = if (snapshot.hideMergedTitlesWithoutPosters) {
            filterMergedHomeItems(
                items = selectedHeroItems,
                contentType = MergedHomeRowContentType.ALL,
                hideTitlesWithoutPosters = true,
            )
        } else {
            selectedHeroItems
        }

        _uiState.value = HomeUiState(
            isLoading = isLoading,
            heroItems = heroItems,
            sections = sections,
            errorMessage = if (sections.isEmpty()) lastErrorMessage else null,
        )
    }

    private fun isHeroResolutionPending(isLoading: Boolean): Boolean {
        if (isLoading || collectionHeroResolving) return true
        val refreshSignature = lastRefreshAddonSignature ?: return false
        val addons = AddonRepository.uiState.value.addons
        if (addons.enabledAddons().any { addon -> addon.manifest == null && addon.isRefreshing }) return true
        return refreshSignature != buildAddonCatalogRefreshSignature(addons)
    }

    private suspend fun HomeCatalogDefinition.toSection(forceRefresh: Boolean): HomeCatalogSection {
        serverConnectionId?.let { connectionId -> return toServerSection(connectionId) }
        CustomPosterUrlRepository.ensureLoaded()
        val pattern = CustomPosterUrlRepository.patternForScreen(com.nuvio.app.core.poster.CustomPosterScreen.HOME)
        val page = fetchCatalogPage(
            manifestUrl = manifestUrl,
            type = type,
            catalogId = catalogId,
            maxItems = HOME_CATALOG_PREVIEW_FETCH_LIMIT,
            forceRefresh = forceRefresh,
        )
        val items = if (pattern.isNotBlank()) {
            page.items.withCustomPosterUrls(pattern)
        } else {
            page.items
        }
        if (items.isEmpty()) {
            return HomeCatalogSection(
                key = key,
                title = defaultTitle,
                subtitle = addonName,
                addonName = addonName,
                target = CatalogTarget.Addon(
                    manifestUrl = manifestUrl,
                    contentType = type,
                    catalogId = catalogId,
                    supportsPagination = supportsPagination,
                ),
                items = emptyList(),
                availableItemCount = 0,
                hasMore = false,
            )
        }

        return HomeCatalogSection(
            key = key,
            title = defaultTitle,
            subtitle = addonName,
            addonName = addonName,
            target = CatalogTarget.Addon(
                manifestUrl = manifestUrl,
                contentType = type,
                catalogId = catalogId,
                supportsPagination = supportsPagination,
            ),
            items = items,
            availableItemCount = page.rawItemCount,
            hasMore = supportsPagination && page.nextSkip != null,
        )
    }

    private suspend fun HomeCatalogDefinition.toServerSection(connectionId: String): HomeCatalogSection {
        val target = CatalogTarget.Server(connectionId, catalogId, type)
        val page = ServerCatalog.page(target, skip = 0, limit = HOME_CATALOG_PREVIEW_FETCH_LIMIT)
        return HomeCatalogSection(
            key = key,
            title = defaultTitle,
            subtitle = addonName,
            addonName = addonName,
            target = target,
            items = page.items,
            availableItemCount = page.rawItemCount,
            hasMore = page.nextSkip != null,
        )
    }

    private fun ensureCollectionHeroFallback(
        addons: List<ManagedAddon>,
        forceRefresh: Boolean,
        refreshSources: Boolean,
        requestKey: String?,
    ) {
        if (!lastPublishedCatalogHeroEmpty) return
        val snapshot = HomeCatalogSettingsRepository.snapshot()
        if (!snapshot.heroEnabled) return
        val collections = enabledCollectionsForHero(snapshot)
        if (collections.isEmpty()) {
            cachedCollectionHeroItems = emptyList()
            collectionHeroRequestKey = null
            return
        }

        val nextRequestKey = collectionHeroRequestKey(
            collections = collections,
            addons = addons,
            snapshot = snapshot,
            requestKey = requestKey,
        )
        if (!refreshSources && collectionHeroRequestKey == nextRequestKey) return

        collectionHeroJob?.cancel()
        collectionHeroRequestKey = nextRequestKey
        cachedCollectionHeroItems = emptyList()
        collectionHeroResolving = true
        publishCurrentState(
            isLoading = _uiState.value.isLoading,
            requestKey = requestKey,
        )

        collectionHeroJob = scope.launch {
            val sources = collectionHeroSources(collections)
            val sourceResults = sources.map { source ->
                async {
                    runCatching {
                        source.resolveCollectionHeroItems(
                            addons = addons,
                            forceRefresh = forceRefresh,
                        )
                    }.getOrDefault(emptyList())
                }
            }.awaitAll()
            val random = Random((nextRequestKey.hashCode()).absoluteValue + 7)
            cachedCollectionHeroItems = roundRobinCollectionHeroItems(sourceResults)
                .distinctBy { item -> item.stableKey() }
                .shuffled(random)
                .take(HOME_HERO_ITEM_LIMIT)
            collectionHeroResolving = false
            publishCurrentState(
                isLoading = _uiState.value.isLoading,
                requestKey = requestKey,
            )
        }
    }

    private fun enabledCollectionsForHero(snapshot: HomeCatalogSettingsSnapshot): List<Collection> {
        val preferences = snapshot.preferences
        return CollectionRepository.collections.value
            .filter { collection ->
                collection.folders.isNotEmpty() &&
                    preferences["collection_${collection.id}"]?.enabled != false
            }
            .sortedBy { collection ->
                preferences["collection_${collection.id}"]?.order ?: Int.MAX_VALUE
            }
    }

    private fun collectionHeroSources(collections: List<Collection>): List<CollectionSource> =
        collections
            .flatMap { collection -> collection.folders }
            .flatMap { folder -> folder.resolvedSources }
            .take(HOME_COLLECTION_HERO_SOURCE_LIMIT)

    private suspend fun CollectionSource.resolveCollectionHeroItems(
        addons: List<ManagedAddon>,
        forceRefresh: Boolean,
    ): List<MetaPreview> {
        val page = when {
            isTmdb -> TmdbCollectionSourceResolver.resolve(source = this, page = 1)
            isTrakt -> TraktPublicListSourceResolver.resolve(source = this, page = 1)
            else -> {
                val catalogSource = addonCatalogSource() ?: return emptyList()
                val resolvedCatalog = addons.findCollectionCatalog(catalogSource) ?: return emptyList()
                fetchCatalogPage(
                    manifestUrl = resolvedCatalog.addon.manifestUrl,
                    type = catalogSource.type,
                    catalogId = catalogSource.catalogId,
                    genre = catalogSource.genre,
                    maxItems = HOME_COLLECTION_HERO_SOURCE_ITEM_LIMIT,
                    forceRefresh = forceRefresh,
                )
            }
        }
        val items = page.items
        return if (HomeCatalogSettingsRepository.snapshot().hideUnreleasedContent) {
            items.filterReleasedItems(CurrentDateProvider.todayIsoDate())
        } else {
            items
        }
    }

    private fun roundRobinCollectionHeroItems(sourceResults: List<List<MetaPreview>>): List<MetaPreview> {
        val iterators = sourceResults.filter { it.isNotEmpty() }.map { it.iterator() }
        if (iterators.isEmpty()) return emptyList()
        val merged = mutableListOf<MetaPreview>()
        var hasMore = true
        while (hasMore && merged.size < HOME_COLLECTION_HERO_SOURCE_LIMIT * HOME_COLLECTION_HERO_SOURCE_ITEM_LIMIT) {
            hasMore = false
            iterators.forEach { iterator ->
                if (iterator.hasNext()) {
                    merged.add(iterator.next())
                    hasMore = true
                }
            }
        }
        return merged
    }

    private fun collectionHeroRequestKey(
        collections: List<Collection>,
        addons: List<ManagedAddon>,
        snapshot: HomeCatalogSettingsSnapshot,
        requestKey: String?,
    ): String = buildString {
        append(requestKey.orEmpty())
        append("|hideUnreleased=")
        append(snapshot.hideUnreleasedContent)
        append("|collections=")
        collections.forEach { collection ->
            val preference = snapshot.preferences["collection_${collection.id}"]
            append(collection.id)
            append(":")
            append(preference?.order ?: Int.MAX_VALUE)
            append(":")
            collection.folders.forEach { folder ->
                append(folder.id)
                append("[")
                folder.resolvedSources.forEach { source ->
                    append(collectionSourceKey(source))
                    append(",")
                }
                append("]")
            }
            append(";")
        }
        append("|addons=")
        addons.forEach { addon ->
            append(addon.manifest?.id.orEmpty())
            append(":")
            append(addon.manifestUrl)
            append(":")
            append(addon.manifest?.catalogs?.size ?: 0)
            append(";")
        }
    }

    private fun collectionSourceKey(source: CollectionSource): String =
        source.catalogRouteKey()
}

private const val HOME_HERO_ITEM_LIMIT = 8
private const val HOME_COLLECTION_HERO_SOURCE_LIMIT = 6
private const val HOME_COLLECTION_HERO_SOURCE_ITEM_LIMIT = 8
private const val HOME_CATALOG_MAX_CONCURRENT_FETCHES = 6
private const val HOME_CATALOG_FETCH_TIMEOUT_MS = 30_000L
private const val HOME_CATALOG_PREVIEW_FETCH_LIMIT = 18
private const val HOME_CATALOG_PUBLISH_THROTTLE_MS = 200L
private const val HOME_SNAPSHOT_SAVE_DELAY_MS = 1_500L

private fun prioritizeDefinitions(
    definitions: List<HomeCatalogDefinition>,
    snapshot: HomeCatalogSettingsSnapshot,
): List<HomeCatalogDefinition> {
    val orderedDefinitions = definitions.sortedBy { definition ->
        snapshot.preferences[definition.key]?.order ?: Int.MAX_VALUE
    }
    val (priority, remainder) = orderedDefinitions.partition { definition ->
        val preference = snapshot.preferences[definition.key]
        if (preference == null) {
            true
        } else {
            preference.enabled || (snapshot.heroEnabled && preference.heroSourceEnabled)
        }
    }
    return priority + remainder
}
