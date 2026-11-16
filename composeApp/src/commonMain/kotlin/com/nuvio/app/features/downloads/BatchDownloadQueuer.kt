package com.nuvio.app.features.downloads

import com.nuvio.app.core.logging.InAppLogger
import com.nuvio.app.core.ui.NuvioToastController
import com.nuvio.app.features.debrid.DebridProviders
import com.nuvio.app.features.debrid.DebridSettingsRepository
import com.nuvio.app.features.debrid.DirectDebridPlayableResult
import com.nuvio.app.features.debrid.DirectDebridPlaybackResolver
import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.details.MetaVideo
import com.nuvio.app.features.profiles.ProfileRepository
import com.nuvio.app.features.streams.StreamItem
import com.nuvio.app.features.streams.isResolverDependent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.batch_download_failed_multiple
import nuvio.composeapp.generated.resources.batch_download_failed_single
import org.jetbrains.compose.resources.getString

/** One planned episode (or a movie) with the source chosen for it. */
internal data class BatchQueueItem(
    val season: Int,
    val episode: MetaVideo,
    val stream: StreamItem,
)

/**
 * Turns the sources chosen in the batch download dialog into downloads: resolves debrid links
 * to direct ones, then enqueues each item, in the background.
 */
internal object BatchDownloadQueuer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Parent of every running queue job; replaced when queued work must stop. */
    private var queueJob = SupervisorJob()

    fun queue(meta: MetaDetails, items: List<BatchQueueItem>) {
        val startingProfileId = ProfileRepository.activeProfileId
        scope.launch(queueJob + Dispatchers.Default) {
            var failedCount = 0
            for (item in items) {
                if (ProfileRepository.activeProfileId != startingProfileId) break
                val target = meta.batchTargetFor(item.episode, item.season)
                val streamToEnqueue = when (val resolved = resolveDownloadableStream(item.stream, target)) {
                    is StreamCandidateResolution.Success -> resolved.stream
                    StreamCandidateResolution.MissingApiKey -> {
                        // Only debrid sources need the key; other episodes may use direct links.
                        InAppLogger.warn("Downloads/Batch", "Missing debrid API key, skipping S${target.season}E${target.episode}")
                        failedCount++
                        continue
                    }
                    is StreamCandidateResolution.Failed -> {
                        InAppLogger.warn("Downloads/Batch", "Skipping S${target.season}E${target.episode}: ${resolved.reason}")
                        failedCount++
                        continue
                    }
                }

                val didEnqueue = withContext(Dispatchers.Main) {
                    if (ProfileRepository.activeProfileId != startingProfileId) return@withContext false
                    if (!enqueue(meta, item.episode, target, streamToEnqueue)) failedCount++
                    true
                }
                if (!didEnqueue) break
            }
            if (failedCount > 0 && ProfileRepository.activeProfileId == startingProfileId) {
                val message = if (failedCount == 1) {
                    Res.string.batch_download_failed_single
                } else {
                    Res.string.batch_download_failed_multiple
                }
                withContext(Dispatchers.Main) { NuvioToastController.show(getString(message, failedCount)) }
            }
        }
    }

    /** Stops queue work that hasn't finished, e.g. when the profile changes. */
    fun clearLocalState() {
        queueJob.cancel()
        queueJob = SupervisorJob()
    }

    private fun enqueue(meta: MetaDetails, episode: MetaVideo, target: BatchEpisodeTarget, stream: StreamItem): Boolean {
        val episodeDetails = episode.takeUnless { target.isSingleTitle }
        val result = DownloadsRepository.enqueueFromStream(
            contentType = meta.type,
            videoId = target.streamVideoId,
            parentMetaId = meta.id,
            parentMetaType = meta.type,
            title = meta.name,
            logo = meta.logo,
            poster = meta.poster,
            background = meta.background,
            seasonNumber = target.season,
            episodeNumber = target.episode,
            episodeTitle = episodeDetails?.title,
            episodeThumbnail = episodeDetails?.thumbnail,
            stream = stream,
            showMeta = meta,
        )
        val succeeded = result.isSuccess
        if (!succeeded) InAppLogger.warn("Downloads/Batch", "Enqueue returned ${result.name} for S${target.season}E${target.episode}")
        return succeeded
    }

    private sealed interface StreamCandidateResolution {
        data class Success(val stream: StreamItem) : StreamCandidateResolution
        data object MissingApiKey : StreamCandidateResolution
        data class Failed(val reason: String) : StreamCandidateResolution
    }

    /** Turns a planned source into a direct, downloadable link, resolving debrid links if needed. */
    private suspend fun resolveDownloadableStream(
        candidate: StreamItem,
        target: BatchEpisodeTarget,
    ): StreamCandidateResolution {
        if (candidate.isDownloadableDirectLink()) return StreamCandidateResolution.Success(candidate)
        if (!candidate.isResolverDependent()) return StreamCandidateResolution.Failed("Could not resolve downloadable stream")

        if (DirectDebridPlaybackResolver.shouldResolveToPlayableStream(candidate)) {
            return when (val resolved = DirectDebridPlaybackResolver.resolveToPlayableStream(candidate, target.season, target.episode)) {
                is DirectDebridPlayableResult.MissingApiKey -> StreamCandidateResolution.MissingApiKey
                is DirectDebridPlayableResult.Success -> if (resolved.stream.isDownloadableDirectLink()) {
                    StreamCandidateResolution.Success(resolved.stream)
                } else {
                    StreamCandidateResolution.Failed("Resolved link not downloadable")
                }
                is DirectDebridPlayableResult.Error -> StreamCandidateResolution.Failed("Failed to resolve stream link")
                is DirectDebridPlayableResult.NotCached -> StreamCandidateResolution.Failed("Stream is no longer cached")
                is DirectDebridPlayableResult.Stale -> StreamCandidateResolution.Failed("Stream link is stale")
            }
        }

        val debridSettings = DebridSettingsRepository.snapshot()
        val providerId = DebridProviders.byId(candidate.clientResolve?.service)?.id
            ?: debridSettings.activeResolverProviderId
        val hasMissingKey = debridSettings.apiKeyFor(providerId).isBlank() ||
            !debridSettings.hasResolverProvider ||
            !debridSettings.canResolvePlayableLinks
        return if (hasMissingKey) {
            StreamCandidateResolution.MissingApiKey
        } else {
            StreamCandidateResolution.Failed("Stream resolver not available")
        }
    }

    private fun StreamItem.isDownloadableDirectLink(): Boolean =
        isDownloadableDirectUrl(playableDirectUrl)
}
