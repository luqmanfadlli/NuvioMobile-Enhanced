package com.nuvio.app.features.streams

import co.touchlab.kermit.Logger
import com.nuvio.app.core.logging.InAppLogger
import com.nuvio.app.core.build.AppFeaturePolicy
import com.nuvio.app.features.addons.AddonRepository
import com.nuvio.app.features.addons.buildAddonResourceUrl
import com.nuvio.app.features.addons.enabledAddons
import com.nuvio.app.features.addons.fetchAddonResponseText
import com.nuvio.app.features.debrid.DirectDebridStreamPreparer
import com.nuvio.app.features.debrid.DebridSettingsRepository
import com.nuvio.app.features.debrid.DebridStreamPresentation
import com.nuvio.app.features.debrid.LocalDebridAvailabilityService
import com.nuvio.app.features.details.MetaDetailsRepository
import com.nuvio.app.features.telegram.TelegramIntegrationBridge
import com.nuvio.app.features.player.PlayerSettingsRepository
import com.nuvio.app.features.plugins.PluginRepository
import com.nuvio.app.features.plugins.pluginContentId
import com.nuvio.app.features.plugins.PluginsUiState
import com.nuvio.app.features.servers.ServerStreams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.getString
import kotlinx.coroutines.launch

object StreamsRepository {
    private val log = Logger.withTag("StreamsRepo")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _uiState = MutableStateFlow(StreamsUiState())
    val uiState: StateFlow<StreamsUiState> = _uiState.asStateFlow()

    private var activeJob: Job? = null
    private var activeRequestKey: String? = null

    fun requestToken(
        type: String,
        videoId: String,
        season: Int? = null,
        episode: Int? = null,
        manualSelection: Boolean = false,
        qualitySelectionMode: Boolean = false,
    ): String =
        if (qualitySelectionMode) "$type::$videoId::$season::$episode::$manualSelection::quality-first"
        else "$type::$videoId::$season::$episode::$manualSelection"

    fun load(type: String, videoId: String, parentMetaId: String? = null, season: Int? = null, episode: Int? = null, manualSelection: Boolean = false, qualitySelectionMode: Boolean = false) {
        PluginRepository.setLocalPluginSearchPaused(false)
        load(
            type = type,
            videoId = videoId,
            parentMetaId = parentMetaId,
            season = season,
            episode = episode,
            manualSelection = manualSelection,
            forceRefresh = false,
            qualitySelectionMode = qualitySelectionMode,
        )
    }

    fun reload(type: String, videoId: String, parentMetaId: String? = null, season: Int? = null, episode: Int? = null, manualSelection: Boolean = false, qualitySelectionMode: Boolean = false) {
        PluginRepository.setLocalPluginSearchPaused(false)
        load(
            type = type,
            videoId = videoId,
            parentMetaId = parentMetaId,
            season = season,
            episode = episode,
            manualSelection = manualSelection,
            forceRefresh = true,
            qualitySelectionMode = qualitySelectionMode,
        )
    }

    private fun load(type: String, videoId: String, parentMetaId: String?, season: Int?, episode: Int?, manualSelection: Boolean, forceRefresh: Boolean, qualitySelectionMode: Boolean) {
        val pluginUiState = if (AppFeaturePolicy.pluginsEnabled) {
            PluginRepository.initialize()
            PluginRepository.uiState.value
        } else {
            PluginsUiState(pluginsEnabled = false)
        }
        val requestToken = requestToken(
            type = type,
            videoId = videoId,
            season = season,
            episode = episode,
            manualSelection = manualSelection,
            qualitySelectionMode = qualitySelectionMode,
        )
        val requestKey = "$requestToken::pluginsGrouped=${pluginUiState.groupStreamsByRepository}"
        val currentState = _uiState.value
        if (
            !forceRefresh &&
            activeRequestKey == requestKey &&
            (currentState.groups.isNotEmpty() || currentState.emptyStateReason != null || currentState.isAnyLoading)
        ) {
            log.d { "Skipping stream reload for unchanged request type=$type id=$videoId" }
            return
        }

        activeRequestKey = requestKey
        activeJob?.cancel()
        _uiState.value = StreamsUiState(requestToken = requestToken)

        PlayerSettingsRepository.ensureLoaded()
        val playerSettings = PlayerSettingsRepository.uiState.value
        val debridSettings = DebridSettingsRepository.snapshot()
        val streamBadgeRules = StreamBadgeSettingsRepository.snapshot()
        InAppLogger.info(
            "Streams/StreamsRepository",
            "Loading streams type=$type id=$videoId season=${season ?: -1} episode=${episode ?: -1} " +
                "manual=$manualSelection force=$forceRefresh",
        )
        val autoPlayMode = playerSettings.streamAutoPlayMode
        val isAutoPlayEnabled = !qualitySelectionMode && !manualSelection &&
            autoPlayMode != StreamAutoPlayMode.MANUAL &&
            autoPlayMode != StreamAutoPlayMode.SEMI_AUTOMATIC &&
            !(autoPlayMode == StreamAutoPlayMode.REGEX_MATCH &&
                !StreamAutoPlayPolicy.isRegexSelectionConfigured(playerSettings.streamAutoPlayRegex))

        // Look up persisted binge group when both settings are enabled
        val persistedBingeGroup = if (
            playerSettings.streamAutoPlayPreferBingeGroup &&
            playerSettings.streamAutoPlayReuseBingeGroup
        ) {
            parentMetaId?.let { BingeGroupCacheRepository.get(it) }
        } else null
        val persistedFilename = if (persistedBingeGroup != null) {
            parentMetaId?.let { BingeGroupCacheRepository.getFilename(it) }
        } else null
        val persistedInfoHash = if (persistedBingeGroup != null) {
            parentMetaId?.let { BingeGroupCacheRepository.getInfoHash(it) }
        } else null

        // Enable direct auto-play flow if normal auto-play is enabled,
        // OR if we have a persisted binge group in MANUAL mode
        val bingeGroupDirectFlow = !qualitySelectionMode && !manualSelection &&
            persistedBingeGroup != null &&
            autoPlayMode == StreamAutoPlayMode.MANUAL
        val isDirectAutoPlayFlow = isAutoPlayEnabled || bingeGroupDirectFlow

        if (isDirectAutoPlayFlow) {
            _uiState.value = StreamsUiState(
                requestToken = requestToken,
                isDirectAutoPlayFlow = true,
                autoPlayDecided = true,
                showDirectAutoPlayOverlay = true,
            )
        }

        val embeddedStreams = MetaDetailsRepository.findEmbeddedStreams(videoId)
        if (embeddedStreams.isNotEmpty()) {
            log.d { "Using ${embeddedStreams.size} embedded streams for type=$type id=$videoId" }
            InAppLogger.info("Streams/StreamsRepository", "Using embedded streams count=${embeddedStreams.size} type=$type id=$videoId")
            val group = AddonStreamGroup(
                addonName = embeddedStreams.first().addonName,
                addonId = "embedded",
                streams = embeddedStreams,
                isLoading = false,
            )
            val presentedGroup = StreamBadgePresentation.apply(
                groups = listOf(group),
                rules = streamBadgeRules,
            ).firstOrNull() ?: group
            _uiState.value = StreamsUiState(
                requestToken = requestToken,
                groups = listOf(presentedGroup),
                autoPlayDecided = true,
                activeAddonIds = setOf("embedded"),
                isAnyLoading = false,
            )
            return
        }

        val isNativeServerRequest = ServerStreams.isNativeRequest(videoId)
        val serverSources = ServerStreams.sources(type, videoId, season, episode, forceRefresh)
        val telegramAvailable = !isNativeServerRequest && TelegramIntegrationBridge.isAuthenticated()
        val preferredGroupIds = serverSources.filter { it.preferred }.mapTo(mutableSetOf()) { it.addonId }
        val installedAddons = if (isNativeServerRequest) emptyList() else AddonRepository.uiState.value.addons.enabledAddons()
        val pluginScrapers = if (AppFeaturePolicy.pluginsEnabled && !isNativeServerRequest) {
            PluginRepository.getEnabledScrapersForType(type)
        } else {
            emptyList()
        }
        val pluginProviderGroups = pluginScrapers.toPluginProviderGroups(
            repositories = pluginUiState.repositories,
            groupByRepository = pluginUiState.groupStreamsByRepository,
        )

        if (installedAddons.isEmpty() && pluginProviderGroups.isEmpty() && serverSources.isEmpty() && !telegramAvailable) {
            InAppLogger.warn("Streams/StreamsRepository", "No stream addons or plugin scrapers installed for type=$type id=$videoId")
            _uiState.value = StreamsUiState(
                requestToken = requestToken,
                isAnyLoading = false,
                emptyStateReason = StreamsEmptyStateReason.NoAddonsInstalled,
                autoPlayDecided = true,
            )
            return
        }

        val streamAddons = installedAddons
            .mapNotNull { addon ->
                val manifest = addon.manifest ?: return@mapNotNull null
                if (!manifest.supportsStream(type, videoId)) return@mapNotNull null

                InstalledStreamAddonTarget(
                    addonName = addon.displayTitle.ifBlank { manifest.name },
                    addonId = addon.streamAddonInstanceId(manifest.id),
                    manifest = manifest,
                )
            }

        log.d { "Found ${streamAddons.size} addons for stream type=$type id=$videoId" }
        InAppLogger.info(
            "Streams/StreamsRepository",
            "Found addons=${streamAddons.size} pluginGroups=${pluginProviderGroups.size} pluginScrapers=${pluginScrapers.size} " +
                "for type=$type id=$videoId",
        )

        if (streamAddons.isEmpty() && pluginProviderGroups.isEmpty() && serverSources.isEmpty() && !telegramAvailable) {
            InAppLogger.warn("Streams/StreamsRepository", "No compatible stream addon/plugin for type=$type id=$videoId")
            _uiState.value = StreamsUiState(
                requestToken = requestToken,
                isAnyLoading = false,
                emptyStateReason = StreamsEmptyStateReason.NoCompatibleAddons,
                autoPlayDecided = true,
            )
            return
        }

        // Initialise loading placeholders
        val installedAddonOrder = streamAddons.map { it.addonName }
        val initialGroups = StreamAutoPlaySelector.orderAddonStreams(
            streamAddons.map { addon ->
                AddonStreamGroup(
                    addonName = addon.addonName,
                    addonId = addon.addonId,
                    streams = emptyList(),
                    isLoading = true,
                )
            } + pluginProviderGroups.map { providerGroup ->
                AddonStreamGroup(
                    addonName = providerGroup.addonName,
                    addonId = providerGroup.addonId,
                    streams = emptyList(),
                    isLoading = true,
                )
            } + serverSources.map { it.loadingGroup() } + if (telegramAvailable) {
                listOf(
                    AddonStreamGroup(
                        addonName = "Telegram",
                        addonId = "telegram_native",
                        streams = emptyList(),
                        isLoading = true,
                    ),
                )
            } else {
                emptyList()
            },
            installedAddonOrder,
            preferredGroupIds,
        )
        val isInitiallyLoading = initialGroups.any { it.isLoading }
        _uiState.value = StreamsUiState(
            requestToken = requestToken,
            groups = initialGroups,
            activeAddonIds = initialGroups.map { it.addonId }.toSet(),
            isAnyLoading = isInitiallyLoading,
            emptyStateReason = null,
            isDirectAutoPlayFlow = isDirectAutoPlayFlow,
            autoPlayDecided = true,
            showDirectAutoPlayOverlay = isDirectAutoPlayFlow,
        )

        activeJob = scope.launch {
            val completions = Channel<StreamLoadCompletion>(capacity = Channel.BUFFERED)
            val pluginRemainingByAddonId = pluginProviderGroups
                .associate { it.addonId to it.scrapers.size }
                .toMutableMap()
            val pluginFirstErrorByAddonId = mutableMapOf<String, String>()
            val totalTasks = streamAddons.size +
                pluginProviderGroups.sumOf { it.scrapers.size } +
                serverSources.size +
                if (telegramAvailable) 1 else 0

            val installedAddonNames = installedAddonOrder.toSet()
            val installedAddonIds = streamAddons.map { it.addonId }.toSet()
            val debridAvailabilityJobs = mutableListOf<Job>()
            var autoSelectTriggered = false
            var timeoutElapsed = false
            fun evaluateAutoPlay(bingeGroupOnly: Boolean = false): StreamAutoPlayEvaluation =
                StreamAutoPlaySelector.evaluateAutoPlayStream(
                    streams = _uiState.value.groups.flatMap { it.streams },
                    mode = autoPlayMode,
                    regexPattern = playerSettings.streamAutoPlayRegex,
                    source = playerSettings.streamAutoPlaySource,
                    installedAddonNames = installedAddonNames,
                    selectedAddons = playerSettings.streamAutoPlaySelectedAddons,
                    selectedPlugins = playerSettings.streamAutoPlaySelectedPlugins,
                    preferredBingeGroup = persistedBingeGroup,
                    preferBingeGroupInSelection = persistedBingeGroup != null,
                    bingeGroupOnly = bingeGroupOnly,
                    debridEnabled = debridSettings.canResolvePlayableLinks,
                    activeResolverProviderId = debridSettings.activeResolverProviderId,
                )

            fun settleAutoPlay(evaluation: StreamAutoPlayEvaluation) {
                if (autoSelectTriggered) return
                autoSelectTriggered = true
                _uiState.update { current ->
                    if (evaluation.stream == null) {
                        current.copy(
                            autoPlayStream = null,
                            autoPlayCandidates = emptyList(),
                            isDirectAutoPlayFlow = false,
                            showDirectAutoPlayOverlay = false,
                        )
                    } else {
                        current.copy(
                            autoPlayStream = evaluation.stream,
                            autoPlayCandidates = evaluation.readyStreams,
                        )
                    }
                }
            }

            fun updateAutoPlayAfterStreamsChanged() {
                if (!isDirectAutoPlayFlow || autoSelectTriggered) return

                // Try binge group + filename first (most precise), then infoHash
                if (persistedBingeGroup != null && !timeoutElapsed) {
                    val allStreams = _uiState.value.groups.flatMap { it.streams }
                    // Filename match
                    if (persistedFilename != null) {
                        val nextFilename = run {
                            val extracted = FilenameEpisodeMatcher.extractSeasonEpisode(persistedFilename)
                            if (extracted != null) {
                                FilenameEpisodeMatcher.buildNextFilename(
                                    persistedFilename, extracted.first, extracted.second, season, episode ?: 0
                                ) ?: persistedFilename
                            } else persistedFilename
                        }
                        val bingeAndFilename = allStreams.firstOrNull { stream ->
                            stream.behaviorHints.bingeGroup == persistedBingeGroup &&
                                FilenameEpisodeMatcher.matches(stream.behaviorHints.filename, nextFilename)
                        }
                        if (bingeAndFilename != null) {
                            settleAutoPlay(StreamAutoPlayEvaluation(bingeAndFilename, allStreams))
                            return
                        }
                    }
                    // InfoHash match (fallback when filename didn't match or was null)
                    if (persistedInfoHash != null) {
                        val bingeAndInfoHash = allStreams.firstOrNull { stream ->
                            stream.behaviorHints.bingeGroup == persistedBingeGroup &&
                                stream.infoHash?.equals(persistedInfoHash, ignoreCase = true) == true
                        }
                        if (bingeAndInfoHash != null) {
                            settleAutoPlay(StreamAutoPlayEvaluation(bingeAndInfoHash, allStreams))
                            return
                        }
                    }
                }

                val earlyEvaluation = when {
                    timeoutElapsed -> evaluateAutoPlay()
                    persistedBingeGroup != null -> evaluateAutoPlay(bingeGroupOnly = true)
                    else -> null
                }
                if (earlyEvaluation?.stream != null) {
                    settleAutoPlay(earlyEvaluation)
                    return
                }

                if (
                    _uiState.value.groups.areAutoPlaySourcesLoaded(
                        source = playerSettings.streamAutoPlaySource,
                        installedAddonIds = installedAddonIds,
                    )
                ) {
                    settleAutoPlay(evaluateAutoPlay())
                }
            }

            fun publishCompletion(completion: StreamLoadCompletion) {
                if (completions.trySend(completion).isFailure) {
                    log.d { "Ignoring late stream load completion after channel close" }
                }
            }

            fun presentStreamGroup(group: AddonStreamGroup): AddonStreamGroup {
                val badgeGroup = StreamBadgePresentation.apply(
                    groups = listOf(group),
                    rules = streamBadgeRules,
                ).firstOrNull() ?: group
                return DebridStreamPresentation.apply(
                    groups = listOf(badgeGroup),
                    settings = debridSettings,
                ).firstOrNull() ?: badgeGroup
            }

            fun publishAddonGroup(group: AddonStreamGroup) {
                _uiState.update { current ->
                    val updated = StreamAutoPlaySelector.orderAddonStreams(
                        groups = current.groups.map { currentGroup ->
                            if (currentGroup.addonId == group.addonId) group else currentGroup
                        },
                        installedOrder = installedAddonOrder,
                        preferredGroupIds = preferredGroupIds,
                    )
                    val anyLoading = updated.any { it.isLoading }
                    current.copy(
                        groups = updated,
                        isAnyLoading = anyLoading,
                        emptyStateReason = updated.toEmptyStateReason(anyLoading),
                    )
                }
                updateAutoPlayAfterStreamsChanged()
            }

            fun publishAddonGroupAfterCacheCheck(group: AddonStreamGroup) {
                if (group.addonId !in installedAddonIds || group.streams.isEmpty()) {
                    publishAddonGroup(presentStreamGroup(group))
                    return
                }

                val eligibleGroupIds = setOf(group.addonId)
                val shouldWaitForCacheCheck = LocalDebridAvailabilityService.hasPendingCacheCheck(
                    groups = listOf(group),
                    eligibleGroupIds = eligibleGroupIds,
                )
                if (!shouldWaitForCacheCheck) {
                    publishAddonGroup(presentStreamGroup(group))
                    return
                }

                val checkingGroup = LocalDebridAvailabilityService.markChecking(
                    groups = listOf(group),
                    eligibleGroupIds = eligibleGroupIds,
                ).firstOrNull() ?: group

                val availabilityJob = launch {
                    val availabilityGroup = LocalDebridAvailabilityService.annotateCachedAvailability(
                        groups = listOf(checkingGroup),
                        eligibleGroupIds = eligibleGroupIds,
                    ).firstOrNull() ?: checkingGroup
                    publishAddonGroup(presentStreamGroup(availabilityGroup))
                }
                debridAvailabilityJobs += availabilityJob
            }

            updateAutoPlayAfterStreamsChanged()

            val timeoutJob = if (isDirectAutoPlayFlow) {
                val timeoutSeconds = playerSettings.streamAutoPlayTimeoutSeconds
                val isUnlimitedTimeout = timeoutSeconds == Int.MAX_VALUE
                // Timeout semantics:
                // - 0 (instant): timeoutElapsed immediately, full select on each response
                // - 1-30 (bounded): wait the configured delay, then full select
                // - unlimited (Int.MAX_VALUE): timeoutElapsed immediately, full select on each response,
                //   with 60s hard fallback to stream picker
                if (timeoutSeconds <= 0 || isUnlimitedTimeout) {
                    timeoutElapsed = true
                    // For unlimited: launch a hard 60s fallback to dismiss overlay
                    if (isUnlimitedTimeout) {
                        launch {
                            delay(60_000L)
                            if (!autoSelectTriggered) {
                                settleAutoPlay(evaluateAutoPlay())
                            }
                        }
                    } else {
                        null
                    }
                } else {
                    // Bounded timeout (1-30s)
                    launch {
                        delay(timeoutSeconds * 1_000L)
                        timeoutElapsed = true
                        if (!autoSelectTriggered) {
                            val allStreams = _uiState.value.groups.flatMap { it.streams }
                            if (allStreams.isNotEmpty()) {
                                val evaluation = evaluateAutoPlay()
                                if (evaluation.stream != null || !evaluation.hasPendingDebridCandidate) {
                                    settleAutoPlay(evaluation)
                                }
                            }
                        }
                    }
                }
            } else {
                null
            }

            streamAddons.forEach { addon ->
                launch {
                    val url = buildAddonResourceUrl(
                        manifestUrl = addon.manifest.transportUrl,
                        resource = "stream",
                        type = type,
                        id = videoId,
                    )
                    log.d { "Fetching streams from: $url" }

                    val displayName = addon.addonName
                    InAppLogger.info(
                        "Streams/AddonFetch",
                        "Fetching streams addon=$displayName addonId=${addon.addonId} url=${InAppLogger.redactUrl(url)}",
                    )
                    val group = runCatchingUnlessCancelled {
                        val payload = fetchAddonResponseText(
                            url = url,
                            forceRefresh = forceRefresh,
                        )
                        StreamParser.parse(
                            payload = payload,
                            addonName = displayName,
                            addonId = addon.addonId,
                            addonLogo = addon.manifest.logoUrl,
                        )
                    }.fold(
                        onSuccess = { streams ->
                            log.d { "Got ${streams.size} streams from ${displayName}" }
                            InAppLogger.info("Streams/AddonFetch", "Loaded streams addon=$displayName count=${streams.size}")
                            AddonStreamGroup(
                                addonName = displayName,
                                addonId = addon.addonId,
                                streams = streams,
                                isLoading = false,
                            )
                        },
                        onFailure = { err ->
                            log.w(err) { "Failed to fetch streams from ${displayName}" }
                            InAppLogger.warn("Streams/AddonFetch", "Failed addon=$displayName error=${InAppLogger.throwableSummary(err)}")
                            AddonStreamGroup(
                                addonName = displayName,
                                addonId = addon.addonId,
                                streams = emptyList(),
                                isLoading = false,
                                error = err.message,
                            )
                        },
                    )
                    publishCompletion(StreamLoadCompletion.Addon(group))
                }
            }

            serverSources.forEach { source ->
                launch { publishCompletion(StreamLoadCompletion.Addon(source.load())) }
            }

            if (telegramAvailable) {
                launch {
                    val completion = try {
                        val details = MetaDetailsRepository.peek(type, parentMetaId ?: videoId)
                            ?: MetaDetailsRepository.peek(type, videoId)
                        val searchTitle = details?.name?.takeIf { it.isNotBlank() }
                        val year = details?.releaseInfo
                            ?.let { Regex("\\b(19\\d{2}|20\\d{2})\\b").find(it)?.value?.toIntOrNull() }
                        val selectedChats = TelegramIntegrationBridge.snapshot.value.selectedChatIds
                        val streams = if (searchTitle.isNullOrBlank() || selectedChats.isEmpty()) {
                            emptyList()
                        } else {
                            TelegramIntegrationBridge.resolveStreams(
                                type = type,
                                videoId = videoId,
                                title = searchTitle,
                                year = year,
                                season = season,
                                episode = episode,
                                imdbId = details?.imdbId,
                            )
                        }
                        val noResultReason = when {
                            searchTitle.isNullOrBlank() -> "Media title is not available for Telegram search."
                            selectedChats.isEmpty() -> "No Telegram channels or groups are selected. Configure sources in Settings > Integrations > Telegram."
                            streams.isEmpty() -> "No matching Telegram video was found in the selected sources."
                            else -> null
                        }
                        StreamLoadCompletion.Addon(
                            AddonStreamGroup(
                                addonName = "Telegram",
                                addonId = "telegram_native",
                                streams = streams,
                                isLoading = false,
                                error = noResultReason,
                            ),
                        )
                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        log.w(error) { "Telegram stream lookup failed for $videoId" }
                        StreamLoadCompletion.Addon(
                            AddonStreamGroup(
                                addonName = "Telegram",
                                addonId = "telegram_native",
                                streams = emptyList(),
                                isLoading = false,
                                error = error.message ?: "Telegram stream search failed.",
                            ),
                        )
                    }
                    publishCompletion(completion)
                }
            }

            pluginProviderGroups.forEach { providerGroup ->
                val includeScraperNameInSubtitle = false
                providerGroup.scrapers.forEach { scraper ->
                    launch {
                        InAppLogger.info(
                            "Streams/PluginFetch",
                            "Executing scraper=${scraper.name} provider=${providerGroup.addonName} type=$type id=$videoId season=${season ?: -1} episode=${episode ?: -1}",
                        )
                        val completion = PluginRepository.executeScraper(
                            scraper = scraper,
                            tmdbId = pluginContentId(
                                videoId = videoId,
                                season = season,
                                episode = episode,
                            ),
                            mediaType = type,
                            season = season,
                            episode = episode,
                        ).fold(
                            onSuccess = { results ->
                                InAppLogger.info("Streams/PluginFetch", "Scraper loaded scraper=${scraper.name} provider=${providerGroup.addonName} count=${results.size}")
                                StreamLoadCompletion.PluginScraper(
                                    addonId = providerGroup.addonId,
                                    streams = results.map { result ->
                                        result.toStreamItem(
                                            scraper = scraper,
                                            addonName = providerGroup.addonName,
                                            addonId = providerGroup.addonId,
                                            includeScraperNameInSubtitle = includeScraperNameInSubtitle,
                                        )
                                    },
                                    error = null,
                                )
                            },
                            onFailure = { error ->
                                InAppLogger.warn("Streams/PluginFetch", "Scraper failed scraper=${scraper.name} provider=${providerGroup.addonName} error=${InAppLogger.throwableSummary(error)}")
                                StreamLoadCompletion.PluginScraper(
                                    addonId = providerGroup.addonId,
                                    streams = emptyList(),
                                    error = error.message ?: getString(Res.string.streams_failed_to_load_scraper, scraper.name),
                                )
                            },
                        )
                        publishCompletion(completion)
                    }
                }
            }

            repeat(totalTasks) {
                when (val completion = completions.receive()) {
                    is StreamLoadCompletion.Addon -> {
                        val result = completion.group
                        publishAddonGroupAfterCacheCheck(result)
                    }

                    is StreamLoadCompletion.PluginScraper -> {
                        val remaining = (pluginRemainingByAddonId[completion.addonId] ?: 1) - 1
                        pluginRemainingByAddonId[completion.addonId] = remaining.coerceAtLeast(0)
                        if (!completion.error.isNullOrBlank() && pluginFirstErrorByAddonId[completion.addonId].isNullOrBlank()) {
                            pluginFirstErrorByAddonId[completion.addonId] = completion.error
                        }

                        _uiState.update { current ->
                            val updated = StreamAutoPlaySelector.orderAddonStreams(
                                groups = current.groups.map { group ->
                                    if (group.addonId != completion.addonId) {
                                        group
                                    } else {
                                        val mergedStreams = if (completion.streams.isEmpty()) {
                                            group.streams
                                        } else {
                                            (group.streams + StreamBadgePresentation.applyToStreams(completion.streams, streamBadgeRules))
                                                .sortedForGroupedDisplay()
                                        }
                                        val stillLoading = remaining > 0
                                        val finalError = if (mergedStreams.isEmpty() && !stillLoading) {
                                            pluginFirstErrorByAddonId[completion.addonId]
                                        } else {
                                            null
                                        }
                                        group.copy(
                                            streams = mergedStreams,
                                            isLoading = stillLoading,
                                            error = finalError,
                                        )
                                    }
                                },
                                installedOrder = installedAddonOrder,
                                preferredGroupIds = preferredGroupIds,
                            )
                            val anyLoading = updated.any { it.isLoading }
                            current.copy(
                                groups = updated,
                                isAnyLoading = anyLoading,
                                emptyStateReason = updated.toEmptyStateReason(anyLoading),
                            )
                        }
                        updateAutoPlayAfterStreamsChanged()
                    }

                }
            }

            for (availabilityJob in debridAvailabilityJobs) {
                availabilityJob.join()
            }

            launch {
                DirectDebridStreamPreparer.prepare(
                    streams = _uiState.value.groups
                        .filter { it.addonId in installedAddonIds }
                        .flatMap { it.streams },
                    season = season,
                    episode = episode,
                    playerSettings = playerSettings,
                    installedAddonNames = installedAddonNames,
                ) { original, prepared ->
                    _uiState.update { current ->
                        current.copy(
                            groups = DirectDebridStreamPreparer.replacePreparedStream(
                                groups = current.groups,
                                original = original,
                                prepared = prepared,
                                eligibleGroupIds = installedAddonIds,
                            ),
                        )
                    }
                    updateAutoPlayAfterStreamsChanged()
                }
            }

            if (isDirectAutoPlayFlow && !autoSelectTriggered) {
                settleAutoPlay(evaluateAutoPlay())
            }
            InAppLogger.info(
                "Streams/StreamsRepository",
                "Finished loading streams type=$type id=$videoId groups=${_uiState.value.groups.size} streams=${_uiState.value.groups.sumOf { it.streams.size }}",
            )
            timeoutJob?.cancel()
        }
    }

    fun selectFilter(addonId: String?) {
        _uiState.update { it.copy(selectedFilter = addonId) }
    }

    /**
     * Starts quality-first autoplay from an ordered snapshot of selectable sources.
     * Provider fetches may continue in the background while normal playback fallback
     * advances through the selected candidate list.
     */
    fun beginQualityFirstAutoPlay(
        expectedRequestToken: String,
        candidates: List<StreamItem>,
    ): Boolean {
        if (candidates.isEmpty()) return false
        var started = false
        _uiState.update { current ->
            if (current.requestToken != expectedRequestToken) {
                current
            } else {
                started = true
                current.copy(
                    autoPlayDecided = true,
                    autoPlayStream = candidates.first(),
                    autoPlayCandidates = candidates,
                    isDirectAutoPlayFlow = true,
                    showDirectAutoPlayOverlay = true,
                    overlayMessage = null,
                )
            }
        }
        return started
    }

    fun consumeAutoPlay() {
        activeRequestKey = null
        _uiState.update {
            it.copy(
                autoPlayStream = null,
                autoPlayCandidates = emptyList(),
                isDirectAutoPlayFlow = false,
                showDirectAutoPlayOverlay = false,
            )
        }
    }

    fun skipAutoPlayStream(stream: StreamItem): Boolean {
        var hasNext = false
        _uiState.update { current ->
            val failedIndex = current.autoPlayCandidates.indexOf(stream)
            val remaining = if (failedIndex >= 0) {
                current.autoPlayCandidates.drop(failedIndex + 1)
            } else {
                current.autoPlayCandidates.drop(1)
            }
            hasNext = remaining.isNotEmpty()
            current.copy(
                autoPlayStream = remaining.firstOrNull(),
                autoPlayCandidates = remaining,
                isDirectAutoPlayFlow = remaining.isNotEmpty(),
                showDirectAutoPlayOverlay = remaining.isNotEmpty(),
                overlayMessage = null,
            )
        }
        return hasNext
    }

    fun cancelLoading() {
        PluginRepository.setLocalPluginSearchPaused(true)
        activeJob?.cancel()
        activeJob = null
        _uiState.update { current ->
            if (!current.isAnyLoading && current.groups.none { it.isLoading }) {
                current
            } else {
                val updatedGroups = current.groups.map { group ->
                    if (group.isLoading) group.copy(isLoading = false) else group
                }
                current.copy(
                    groups = updatedGroups,
                    isAnyLoading = false,
                    emptyStateReason = if (updatedGroups.isEmpty()) {
                        current.emptyStateReason
                    } else {
                        updatedGroups.toEmptyStateReason(anyLoading = false)
                    },
                )
            }
        }
    }

    fun clear() {
        PluginRepository.setLocalPluginSearchPaused(true)
        activeJob?.cancel()
        activeJob = null
        activeRequestKey = null
        _uiState.value = StreamsUiState()
    }

    fun setOverlayVisible(visible: Boolean, message: String? = null) {
        _uiState.update { it.copy(showDirectAutoPlayOverlay = visible, overlayMessage = message) }
    }
}
