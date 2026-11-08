package com.nuvio.app.features.player

import co.touchlab.kermit.Logger
import com.nuvio.app.features.debrid.DebridSettingsRepository
import com.nuvio.app.features.streams.QualityFirstAutoPlayPolicy
import com.nuvio.app.features.streams.StreamItem
import com.nuvio.app.features.streams.isSelectableForPlayback
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Watches startup for the currently selected source.
 *
 * Source discovery is intentionally separate from the startup deadline: after
 * the deadline, this monitor uses candidates already available and keeps
 * observing discovery while other add-ons/plugins finish in the background.
 */
internal class DeadLinkStartupMonitor(
    private val scope: CoroutineScope,
    private val timeoutSeconds: () -> Int,
    private val timeoutEnabled: () -> Boolean = { true },
    private val candidateStreams: suspend () -> List<StreamItem>,
    private val onFallback: (StreamItem) -> Unit,
    private val onExhausted: () -> Unit,
    private val sourceDiscoveryLoading: () -> Boolean = { false },
    private val onDeadLink: (StreamItem, String) -> Unit = { _, _ -> },
    private val preserveCandidateOrder: () -> Boolean = { false },
    private val requestedQualityHeight: () -> Int? = { null },
) {
    private val log = Logger.withTag("DeadLinkStartupMonitor")
    private var timer: Job? = null
    private var stabilityJob: Job? = null
    private var firstFrameSeen = false
    private var generation = 0L
    private var currentKey: String? = null
    private val attemptedKeys = linkedSetOf<String>()
    private val deadMarkedKeys = linkedSetOf<String>()
    private var currentStream: StreamItem? = null
    private var fallbackQualityHeight: Int? = null
    private var playbackStarted = false
    private var buffering = false
    private var awaitingActivation = false
    private var exhausted = false
    private var fatalFailureInProgress = false
    private var closed = false

    fun resetForContent() {
        generation++
        cancelTimer()
        cancelStabilityJob()
        firstFrameSeen = false
        currentKey = null
        attemptedKeys.clear()
        deadMarkedKeys.clear()
        currentStream = null
        fallbackQualityHeight = null
        playbackStarted = false
        buffering = false
        awaitingActivation = false
        exhausted = false
        fatalFailureInProgress = false
    }

    /**
     * Called when a stream is selected or becomes the runtime's active stream.
     * A different source always gets a fresh watchdog, even if an earlier source
     * played successfully. A fallback candidate also gets a watchdog while its
     * asynchronous preparation is in progress, so resolver failures cannot end
     * the chain silently.
     */
    fun sourceSelected(stream: StreamItem, identityKeyOverride: String? = null) {
        if (closed) return
        val key = identityKeyOverride ?: stream.playerSourceIdentityKey() ?: return
        currentStream = stream
        if (preserveCandidateOrder() && fallbackQualityHeight == null) {
            // The user's explicit semi-auto selection is authoritative even when
            // the selected stream's filename/title does not expose its resolution.
            fallbackQualityHeight = requestedQualityHeight()
                ?: QualityFirstAutoPlayPolicy.detectQualityHeight(stream)
        }
        if (!timeoutEnabled()) {
            cancelTimer()
            cancelStabilityJob()
            firstFrameSeen = false
            generation++
            currentKey = key
            attemptedKeys += key
            playbackStarted = false
            buffering = false
            awaitingActivation = false
            exhausted = false
            return
        }

        if (currentKey == key) {
            if (fatalFailureInProgress) return
            if (awaitingActivation) {
                // The player has now accepted this candidate. Start its full
                // startup window from activation rather than from dispatch.
                awaitingActivation = false
                cancelTimer()
                generation++
                armTimeout(key, generation)
                return
            }
            if (playbackStarted || stabilityJob?.isActive == true || timer?.isActive == true) return
            if (exhausted) return
            generation++
            armTimeout(key, generation)
            return
        }

        cancelTimer()
        cancelStabilityJob()
        firstFrameSeen = false
        generation++
        currentKey = key
        attemptedKeys += key
        playbackStarted = false
        buffering = false
        awaitingActivation = false
        exhausted = false
        fatalFailureInProgress = false
        if (timeoutEnabled()) armTimeout(key, generation)
    }

    /** Disable fallback monitoring for non-stream playback (e.g. a local file). */
    fun sourceCleared() {
        generation++
        cancelTimer()
        cancelStabilityJob()
        firstFrameSeen = false
        currentKey = null
        currentStream = null
        playbackStarted = false
        buffering = false
        awaitingActivation = false
        exhausted = false
        fatalFailureInProgress = false
    }

    fun playbackStateChanged(
        stream: StreamItem?,
        isPlaying: Boolean,
        isLoading: Boolean,
        identityKeyOverride: String? = null,
        isEnded: Boolean = false,
    ) {
        if (closed) return
        val key = identityKeyOverride ?: stream?.playerSourceIdentityKey() ?: return
        if (key != currentKey) return
        if (isEnded) {
            cancelTimer()
            cancelStabilityJob()
            generation++
            buffering = false
            playbackStarted = true
            log.d { "Playback ended; dead-link monitoring stopped for $key" }
            return
        }
        if (isLoading) {
            // Once playback has been stable, startup fallback is finished for this source.
            if (playbackStarted) return
            if (!buffering) {
                buffering = true
                generation++
                cancelTimer()
                cancelStabilityJob()
                if (timeoutEnabled()) {
                    armTimeout(key, generation)
                    log.w { "Source is buffering; dead-link deadline armed for $key (isPlaying=$isPlaying)" }
                }
            }
            return
        }

        buffering = false
        if (isPlaying) {
            awaitingActivation = false
            fatalFailureInProgress = false
            if (!firstFrameSeen) {
                firstFrameSeen = true
                cancelTimer()
                startStabilityWindow(key)
                log.d { "First frame confirmed; observing source for stability window ($STABILITY_WINDOW_MS ms): $key" }
            } else if (!playbackStarted && stabilityJob?.isActive != true) {
                // Resume the stability window after playback resumes.
                cancelTimer()
                startStabilityWindow(key)
            } else if (playbackStarted) {
                cancelTimer()
            }
        } else if (firstFrameSeen) {
            // A manual pause must not be interpreted as a failed source.
            cancelTimer()
            cancelStabilityJob()
        }
    }

    /**
     * A terminal player/resolver error is stronger evidence than a startup timeout.
     * Advance immediately, but use the same generation-guarded discovery and
     * candidate activation path so stale errors cannot switch a newer source.
     */
    fun playbackFailed(
        stream: StreamItem?,
        identityKeyOverride: String? = null,
    ) {
        if (closed || !timeoutEnabled() || exhausted || fatalFailureInProgress || playbackStarted) return
        val key = identityKeyOverride ?: stream?.playerSourceIdentityKey() ?: return
        if (key != currentKey) return

        fatalFailureInProgress = true
        cancelStabilityJob()
        playbackStarted = false
        buffering = false
        cancelTimer()
        generation++
        val expectedGeneration = generation
        scope.launch {
            findAndActivateFallback(key, expectedGeneration)
        }
    }

    fun settingsChanged() {
        if (closed) return
        val key = currentKey ?: return
        if (!timeoutEnabled()) {
            cancelTimer()
            cancelStabilityJob()
            generation++
            return
        }
        // A settings/share toggle must not restart the startup watchdog for a source
        // that has already rendered a frame. Only an actual in-progress startup or
        // rebuffer should be timed again.
        if ((playbackStarted || firstFrameSeen) && !buffering) return
        cancelTimer()
        generation++
        exhausted = false
        armTimeout(key, generation)
    }

    fun close() {
        closed = true
        generation++
        cancelTimer()
        cancelStabilityJob()
    }

    private fun cancelStabilityJob() {
        stabilityJob?.cancel()
        stabilityJob = null
    }

    private fun startStabilityWindow(key: String) {
        cancelStabilityJob()
        stabilityJob = scope.launch {
            delay(STABILITY_WINDOW_MS)
            if (closed || currentKey != key || buffering || fatalFailureInProgress) return@launch
            playbackStarted = true
            cancelTimer()
            log.i { "Source stable for ${STABILITY_WINDOW_MS}ms; monitoring rests until source changes: $key" }
        }
    }

    private fun cancelTimer() {
        timer?.cancel()
        timer = null
    }

    private fun armTimeout(key: String, expectedGeneration: Long) {
        if (!timeoutEnabled()) return
        timer = scope.launch {
            delay(DeadLinkTimeoutPolicy.normalizeSeconds(timeoutSeconds()).toLong() * 1000L)
            if (!isCurrent(key, expectedGeneration)) return@launch
            findAndActivateFallback(key, expectedGeneration)
        }
    }

    private suspend fun findAndActivateFallback(key: String, expectedGeneration: Long) {
        val failedStream = currentStream
        if (failedStream != null && deadMarkedKeys.add(key)) {
            log.w { "Startup deadline expired; pruning dead source ${failedStream.addonName} · ${failedStream.streamLabel}" }
            onDeadLink(failedStream, key)
        }

        var discoveryFailures = 0
        while (isCurrent(key, expectedGeneration)) {
            val discovered = try {
                candidateStreams()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (!isCurrent(key, expectedGeneration)) return
                val retryDelayMs = when (discoveryFailures++) {
                    0 -> 2_000L
                    1 -> 5_000L
                    2 -> 15_000L
                    else -> 30_000L
                }
                log.w {
                    "Candidate snapshot failed for $key; retrying in ${retryDelayMs}ms: ${error.message}"
                }
                delay(retryDelayMs)
                continue
            }

            if (!isCurrent(key, expectedGeneration)) return

            val debridEnabled = DebridSettingsRepository.snapshot().canResolvePlayableLinks
            val eligibleCandidates = discovered.asSequence()
                // Do not auto-open an external app: it has no in-player startup
                // signal, so the watchdog cannot know whether it succeeded.
                .filter { !it.shouldOpenExternally && it.isSelectableForPlayback(debridEnabled) }
                .distinctBy { it.playerPlaybackDeduplicationKey() }
                .filter { candidate ->
                    val candidateKey = candidate.playerSourceIdentityKey()
                    candidateKey != null && candidateKey !in attemptedKeys
                }
                .toList()
            // Semi-auto preserves provider order within the selected quality tier.
            // While discovery is active, wait for slower providers at that tier before
            // stepping down; never promote a higher tier than the user's selection.
            val semiAuto = preserveCandidateOrder()
            val currentTierHeight = fallbackQualityHeight
            val next = if (semiAuto && currentTierHeight != null) {
                eligibleCandidates.firstOrNull {
                    QualityFirstAutoPlayPolicy.detectQualityHeight(it) == currentTierHeight
                } ?: if (sourceDiscoveryLoading()) {
                    null
                } else {
                    eligibleCandidates.firstOrNull {
                        QualityFirstAutoPlayPolicy.detectQualityHeight(it)?.let { height ->
                            height < currentTierHeight
                        } == true
                    }
                }
            } else if (semiAuto) {
                eligibleCandidates.firstOrNull()
            } else {
                eligibleCandidates.sortedBy(::deadLinkSourcePriority).firstOrNull()
            }

            if (next != null) {
                val nextKey = next.playerSourceIdentityKey()
                if (nextKey == null) continue

                // Mark it attempted before dispatch to prevent duplicate/racing
                // callbacks. Keep a watchdog alive while asynchronous server,
                // YouTube, debrid or P2P preparation is underway.
                attemptedKeys += nextKey
                currentKey = nextKey
                currentStream = next
                if (semiAuto) {
                    QualityFirstAutoPlayPolicy.detectQualityHeight(next)?.let { fallbackQualityHeight = it }
                }
                generation++
                playbackStarted = false
                firstFrameSeen = false
                cancelStabilityJob()
                buffering = false
                awaitingActivation = true
                exhausted = false
                fatalFailureInProgress = false
                timer = null
                armTimeout(nextKey, generation)
                log.i {
                    "Startup timeout reached for $key; falling back to ${next.addonName} · ${next.streamLabel}"
                }
                onFallback(next)
                return
            }

            if (!sourceDiscoveryLoading()) {
                if (!isCurrent(key, expectedGeneration)) return
                exhausted = true
                timer = null
                log.w { "Startup timeout reached for $key; no untried playable source remains" }
                onExhausted()
                return
            }

            // No untried candidate has arrived yet. Keep the original provider
            // jobs running and re-check their partial results; don't mistake an
            // empty, still-loading snapshot for exhaustion.
            delay(CANDIDATE_POLL_INTERVAL_MS)
        }
    }

    private fun isCurrent(key: String, expectedGeneration: Long): Boolean =
        !closed &&
            (!playbackStarted || buffering) &&
            !exhausted &&
            generation == expectedGeneration &&
            currentKey == key

    private companion object {
        const val CANDIDATE_POLL_INTERVAL_MS = 300L
        const val STABILITY_WINDOW_MS = 25_000L
    }
}
