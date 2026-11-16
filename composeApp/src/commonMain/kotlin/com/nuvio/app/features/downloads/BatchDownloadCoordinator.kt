package com.nuvio.app.features.downloads

import com.nuvio.app.features.debrid.DebridStreamFacts
import com.nuvio.app.features.debrid.DebridStreamMetadata
import com.nuvio.app.features.debrid.DebridStreamPreferences
import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.details.MetaVideo
import com.nuvio.app.features.details.findEmbeddedStreams
import com.nuvio.app.features.details.normalizeSeasonNumber
import com.nuvio.app.features.details.seasonSortKey
import com.nuvio.app.features.streams.StreamItem
import com.nuvio.app.features.streams.StreamSourceLoader
import com.nuvio.app.features.streams.StreamSourcePrefillSelector
import com.nuvio.app.features.streams.runCatchingUnlessCancelled
import com.nuvio.app.features.watched.releasedEpisodesForSeason
import com.nuvio.app.features.watchprogress.CurrentDateProvider
import com.nuvio.app.features.watchprogress.buildPlaybackVideoId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

enum class EpisodePlanStatus {
    AlreadyDownloaded,
    AlreadyInProgress,
    Loading,
    Ready,
    NoSourceFound,
}

data class PlannedEpisodeItem(
    val episode: MetaVideo,
    val status: EpisodePlanStatus,
    val streamCandidate: StreamItem? = null,
    val allAvailableStreams: List<StreamItem> = emptyList(),
    val resolutionLabel: String? = null,
    val sizeBytes: Long? = null,
)

sealed interface BatchDialogStep {
    data object EpisodeSelection : BatchDialogStep
    data class SourceSelection(val seasonIndex: Int) : BatchDialogStep
}

data class PlannedSeasonEpisodes(
    val season: Int,
    val episodes: List<PlannedEpisodeItem> = emptyList(),
    val isPlanning: Boolean = false,
) {
    val readyToDownloadCount: Int
        get() = episodes.count { it.status == EpisodePlanStatus.Ready && it.streamCandidate != null }
}

data class BatchDownloadPlan(
    val meta: MetaDetails,
    val availableSeasons: List<Int> = emptyList(),
    val episodesBySeason: Map<Int, List<MetaVideo>> = emptyMap(),
    val selectedEpisodeKeys: Set<String> = emptySet(),
    val step: BatchDialogStep = BatchDialogStep.EpisodeSelection,
    /** Seasons with a selected episode, whose sources are picked one season per step. */
    val seasonsToSource: List<Int> = availableSeasons,
    /** The season whose episodes the picker currently lists. */
    val visibleSeason: Int = availableSeasons.firstOrNull() ?: 0,
    val seasonPlans: Map<Int, PlannedSeasonEpisodes> = emptyMap(),
    val isSingleTitle: Boolean = false,
) {
    val totalSelectedCount: Int
        get() = selectedEpisodeKeys.size

    fun isSeasonSelected(season: Int): Boolean =
        episodesBySeason[season].orEmpty().any { it.batchKey(season) in selectedEpisodeKeys }

}

/** Result of [BatchDownloadCoordinator.queuePlan]. */
enum class BatchQueueResult {
    Queued,
    NothingToQueue,
}

fun batchEpisodeKey(season: Int, episode: Int?): String = "${season}_${episode ?: 0}"

fun MetaVideo.batchKey(season: Int): String = batchEpisodeKey(season, episode)

/** Returns the existing non-failed download for this logical episode. */
private fun List<DownloadItem>.downloadFor(logicalKey: String): DownloadItem? =
    firstOrNull { it.logicalContentKey == logicalKey && it.status != DownloadStatus.Failed }

private fun findEpisodeDownload(downloads: List<DownloadItem>, metaId: String, episode: MetaVideo, season: Int): DownloadItem? =
    downloads.downloadFor(buildLogicalKey(
        parentMetaId = metaId,
        seasonNumber = episode.episode?.let { episode.season ?: season },
        episodeNumber = episode.episode,
    ))

fun isBatchEpisodeDownloaded(
    currentDownloads: List<DownloadItem>,
    metaId: String,
    episode: MetaVideo,
    season: Int,
): Boolean = findEpisodeDownload(currentDownloads, metaId, episode, season)
    .let { it?.status == DownloadStatus.Completed }

internal fun isBatchEpisodeUnavailable(currentDownloads: List<DownloadItem>, metaId: String, episode: MetaVideo, season: Int): Boolean =
    findEpisodeDownload(currentDownloads, metaId, episode, season) != null

private fun isBatchEpisodeInProgress(
    currentDownloads: List<DownloadItem>,
    metaId: String,
    episode: MetaVideo,
    season: Int,
): Boolean = findEpisodeDownload(currentDownloads, metaId, episode, season)
    .let { it != null && it.status != DownloadStatus.Completed }

/** Short quality label for a stream, e.g. "1080p" or "WEB-DL"; null when nothing is known. */
internal fun DebridStreamFacts.qualityBadge(): String? =
    resolution.label.takeUnless { it == "Unknown" }
        ?: quality.name.replace("_", "-").takeUnless { it == "UNKNOWN" }

/** The ids needed to load and download one planned episode (or a movie). */
internal data class BatchEpisodeTarget(
    val season: Int?,
    val episode: Int?,
    val streamVideoId: String,
) {
    val isSingleTitle: Boolean get() = episode == null
}

internal fun MetaDetails.batchTargetFor(video: MetaVideo, planSeason: Int): BatchEpisodeTarget {
    val episode = video.episode
        ?: return BatchEpisodeTarget(
            season = null,
            episode = null,
            streamVideoId = video.id.takeIf { it.isNotBlank() } ?: id,
        )
    val season = video.season ?: planSeason
    val streamVideoId = video.id.takeIf { it.isNotBlank() } ?: buildPlaybackVideoId(
        parentMetaId = id,
        seasonNumber = season,
        episodeNumber = episode,
    )
    return BatchEpisodeTarget(season = season, episode = episode, streamVideoId = streamVideoId)
}

/**
 * Plans and queues downloads from the batch download dialog: several episodes of a series,
 * or a single title such as a movie.
 */
object BatchDownloadCoordinator {
    /** How many episodes may look up sources at once, across all seasons of the open plan. */
    private const val SOURCE_LOAD_PARALLELISM = 2
    private const val SOURCE_LOAD_TIMEOUT_MS = 30_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _activePlan = MutableStateFlow<BatchDownloadPlan?>(null)
    val activePlan: StateFlow<BatchDownloadPlan?> = _activePlan.asStateFlow()

    /**
     * Shared by every season's source lookups: each lookup queries every installed addon and
     * debrid service, so a large season must not fan out into hundreds of parallel requests.
     */
    private val sourceLoadPermits = Semaphore(SOURCE_LOAD_PARALLELISM)

    /** Parent of the open plan's source loading; cancelled when the plan closes. */
    private var planJob: Job? = null


    fun openPlan(
        meta: MetaDetails,
        season: Int = 1,
    ) {
        closePlan()
        planJob = SupervisorJob(scope.coroutineContext[Job])

        val isMovie = meta.type.equals("movie", ignoreCase = true)
        val hasEpisodes = meta.videos.any { it.season != null || it.episode != null }
        val isSingleTitle = isMovie || !hasEpisodes || meta.videos.count { it.episode != null } <= 1

        if (isSingleTitle) {
            val singleVideo = meta.videos.firstOrNull { it.episode != null }
                ?: meta.videos.firstOrNull()
                ?: MetaVideo(
                    id = meta.id,
                    title = meta.name,
                    thumbnail = meta.background ?: meta.poster,
                    season = null,
                    episode = null,
                )
            val targetSeason = singleVideo.season ?: season
            _activePlan.value = BatchDownloadPlan(
                meta = meta,
                availableSeasons = listOf(targetSeason),
                episodesBySeason = mapOf(targetSeason to listOf(singleVideo)),
                selectedEpisodeKeys = setOf(batchEpisodeKey(targetSeason, singleVideo.episode)),
                step = BatchDialogStep.SourceSelection(seasonIndex = 0),
                seasonsToSource = listOf(targetSeason),
                visibleSeason = targetSeason,
                isSingleTitle = true,
            )
            loadSourcesForSeason(targetSeason)
            return
        }

        val allSeasonNumbers = meta.videos
            .mapNotNull { it.season }
            .distinct()
            .sortedBy(::seasonSortKey)
            .ifEmpty { listOf(season) }

        val todayIsoDate = CurrentDateProvider.todayIsoDate()
        val episodesBySeason = allSeasonNumbers.associateWith { s ->
            meta.releasedEpisodesForSeason(s, todayIsoDate)
                .filter { it.episode != null }
                .distinctBy { Pair(normalizeSeasonNumber(it.season), it.episode) }
                .sortedBy { it.episode }
        }

        _activePlan.value = BatchDownloadPlan(
            meta = meta,
            availableSeasons = allSeasonNumbers,
            episodesBySeason = episodesBySeason,
            seasonsToSource = allSeasonNumbers,
            visibleSeason = if (season in allSeasonNumbers) season else allSeasonNumbers.first(),
        )
    }

    fun showSeason(season: Int) {
        _activePlan.update { it?.copy(visibleSeason = season) }
    }

    fun toggleEpisodeSelection(season: Int, episodeNumber: Int) {
        val key = batchEpisodeKey(season, episodeNumber)
        _activePlan.update { current ->
            current?.copy(
                selectedEpisodeKeys = if (key in current.selectedEpisodeKeys) {
                    current.selectedEpisodeKeys - key
                } else {
                    current.selectedEpisodeKeys + key
                },
            )
        }
    }

    fun selectAllForSeason(season: Int) {
        updateSeasonSelection(season) { _, isUnavailable -> !isUnavailable }
    }

    fun selectUnwatchedForSeason(
        season: Int,
        isEpisodeWatched: (MetaVideo) -> Boolean,
    ) {
        updateSeasonSelection(season) { episode, isUnavailable ->
            !isUnavailable && !isEpisodeWatched(episode)
        }
    }

    fun deselectAllForSeason(season: Int) {
        updateSeasonSelection(season) { _, _ -> false }
    }

    /** Sets each episode of [season] selected or not according to [shouldSelect]. */
    private fun updateSeasonSelection(
        season: Int,
        shouldSelect: (episode: MetaVideo, isUnavailable: Boolean) -> Boolean,
    ) {
        DownloadsRepository.ensureLoaded()
        val currentDownloads = DownloadsRepository.uiState.value.items
        _activePlan.update { current ->
            if (current == null) return@update null
            val keys = current.selectedEpisodeKeys.toMutableSet()
            for (episode in current.episodesBySeason[season].orEmpty()) {
                val key = batchEpisodeKey(season, episode.episode ?: continue)
                val unavailable = isBatchEpisodeUnavailable(currentDownloads, current.meta.id, episode, season)
                if (shouldSelect(episode, unavailable)) keys += key else keys -= key
            }
            current.copy(selectedEpisodeKeys = keys)
        }
    }

    fun continueToSourceSelection() {
        val plan = _activePlan.value ?: return
        val seasonsToSource = plan.availableSeasons.filter(plan::isSeasonSelected)
        if (seasonsToSource.isEmpty()) return

        _activePlan.update {
            it?.copy(
                seasonsToSource = seasonsToSource,
                step = BatchDialogStep.SourceSelection(seasonIndex = 0),
            )
        }
        loadSourcesForSeason(seasonsToSource.first())
    }

    private fun loadSourcesForSeason(season: Int) {
        val plan = _activePlan.value ?: return
        val parentJob = planJob ?: return
        val existingPlan = plan.seasonPlans[season]
        if (existingPlan?.isPlanning == true) return

        DownloadsRepository.ensureLoaded()
        val currentDownloads = DownloadsRepository.uiState.value.items
        val previousItems = existingPlan?.episodes.orEmpty().associateBy { it.episode }
        val initialItems = buildInitialPlannedItems(plan, season, currentDownloads, previousItems)
        if (initialItems.isEmpty()) return
        val episodesToLoad = initialItems.filter { it.status == EpisodePlanStatus.Loading }.map { it.episode }
        updateSeasonPlan(plan.meta.id, season) {
            PlannedSeasonEpisodes(season = season, episodes = initialItems, isPlanning = episodesToLoad.isNotEmpty())
        }
        if (episodesToLoad.isEmpty()) return

        launchSourceLoading(plan, parentJob, season, episodesToLoad)
    }

    private fun buildInitialPlannedItems(
        plan: BatchDownloadPlan,
        season: Int,
        currentDownloads: List<DownloadItem>,
        previousItems: Map<MetaVideo, PlannedEpisodeItem>,
    ): List<PlannedEpisodeItem> = plan.episodesBySeason[season].orEmpty().filter { episode ->
        plan.isSingleTitle || episode.batchKey(season) in plan.selectedEpisodeKeys
    }.map { episode ->
            val isDownloaded = isBatchEpisodeDownloaded(currentDownloads, plan.meta.id, episode, season)
            val inProgress = isBatchEpisodeInProgress(currentDownloads, plan.meta.id, episode, season)
            val previous = previousItems[episode]
            when {
                isDownloaded -> PlannedEpisodeItem(episode, EpisodePlanStatus.AlreadyDownloaded)
                inProgress -> PlannedEpisodeItem(episode, EpisodePlanStatus.AlreadyInProgress)
                previous != null -> previous
                else -> PlannedEpisodeItem(episode, EpisodePlanStatus.Loading)
            }
        }

    private fun launchSourceLoading(
        plan: BatchDownloadPlan,
        parentJob: Job,
        season: Int,
        episodesToLoad: List<MetaVideo>,
    ) {
        scope.launch(parentJob) {
            val preferredResolution = DownloadsSettingsRepository.preferredResolution.value
            episodesToLoad.map { episode ->
                launch(Dispatchers.Default) {
                    sourceLoadPermits.withPermit {
                        val target = plan.meta.batchTargetFor(episode, season)
                        val streams = runCatchingUnlessCancelled {
                            StreamSourceLoader.loadStreams(
                                type = plan.meta.type,
                                videoId = target.streamVideoId,
                                season = target.season,
                                episode = target.episode,
                                embeddedStreams = plan.meta.findEmbeddedStreams(target.streamVideoId),
                                timeoutMs = SOURCE_LOAD_TIMEOUT_MS,
                            )
                        }.getOrElse { emptyList() }
                        val selection = StreamSourcePrefillSelector.selectForBatch(streams, preferredResolution)

                        updateEpisode(plan.meta.id, season, episode) { item ->
                            if (item.status == EpisodePlanStatus.AlreadyDownloaded) item
                            else if (selection == null) {
                                item.copy(allAvailableStreams = streams, status = EpisodePlanStatus.NoSourceFound)
                            } else {
                                item.withSelectedStream(selection.stream, selection.facts, selection.sizeBytes)
                                    .copy(allAvailableStreams = streams)
                            }
                        }
                    }
                }
            }.joinAll()
            updateSeasonPlan(plan.meta.id, season) { it?.copy(isPlanning = false) }
            // Re-run to include episodes selected while the source requests were in flight.
            loadSourcesForSeason(season)
        }
    }

    fun overrideEpisodeStream(season: Int, episode: MetaVideo, stream: StreamItem) {
        val plan = _activePlan.value ?: return
        val facts = DebridStreamMetadata.facts(stream, DebridStreamPreferences())
        updateEpisode(plan.meta.id, season, episode) {
            it.withSelectedStream(stream, facts, StreamSourcePrefillSelector.sizeBytes(stream))
        }
    }

    private fun PlannedEpisodeItem.withSelectedStream(stream: StreamItem, facts: DebridStreamFacts, sizeBytes: Long? = null) = copy(
        streamCandidate = stream,
        resolutionLabel = facts.qualityBadge(),
        sizeBytes = sizeBytes ?: StreamSourcePrefillSelector.sizeBytes(stream),
        status = EpisodePlanStatus.Ready,
    )

    /**
     * Updates one season of the open plan. Ignored when the open plan is for another title,
     * so late results from a closed plan can't land in a new one.
     */
    private fun updateSeasonPlan(
        metaId: String,
        season: Int,
        transform: (PlannedSeasonEpisodes?) -> PlannedSeasonEpisodes?,
    ) {
        _activePlan.update { current ->
            if (current == null || current.meta.id != metaId) return@update current
            val updated = transform(current.seasonPlans[season]) ?: return@update current
            current.copy(seasonPlans = current.seasonPlans + (season to updated))
        }
    }

    private fun updateEpisode(
        metaId: String,
        season: Int,
        episode: MetaVideo,
        transform: (PlannedEpisodeItem) -> PlannedEpisodeItem,
    ) {
        updateSeasonPlan(metaId, season) { seasonPlan ->
            seasonPlan?.copy(
                episodes = seasonPlan.episodes.map { if (it.episode == episode) transform(it) else it },
            )
        }
    }

    fun goToSeasonIndex(index: Int) {
        val plan = _activePlan.value ?: return
        if (index !in plan.seasonsToSource.indices) return
        val season = plan.seasonsToSource[index]
        _activePlan.update { it?.copy(step = BatchDialogStep.SourceSelection(seasonIndex = index)) }
        loadSourcesForSeason(season)
    }

    fun goToEpisodeSelection() {
        _activePlan.update { it?.copy(step = BatchDialogStep.EpisodeSelection) }
    }

    /** Closes the dialog and queues every planned episode that has a source. */
    fun queuePlan(): BatchQueueResult {
        val plan = _activePlan.value ?: return BatchQueueResult.NothingToQueue
        val itemsToQueue = plan.seasonsToSource
            .flatMap { season ->
                plan.seasonPlans[season]?.episodes.orEmpty().mapNotNull { item ->
                    val key = item.episode.batchKey(season)
                    if (!plan.isSingleTitle && key !in plan.selectedEpisodeKeys) return@mapNotNull null
                    item.streamCandidate?.let { stream -> BatchQueueItem(season, item.episode, stream) }
                }
            }
            .sortedWith(compareBy({ it.episode.season ?: 0 }, { it.episode.episode ?: 0 }))

        closePlan()
        if (itemsToQueue.isEmpty()) return BatchQueueResult.NothingToQueue

        BatchDownloadQueuer.queue(plan.meta, itemsToQueue)
        return BatchQueueResult.Queued
    }

    fun closePlan() {
        planJob?.cancel()
        planJob = null
        _activePlan.value = null
    }

    fun clearLocalState() {
        closePlan()
        BatchDownloadQueuer.clearLocalState()
    }
}
