package com.nuvio.app.features.player

import com.nuvio.app.features.streams.QualityFirstAutoPlayPolicy
import com.nuvio.app.features.streams.StreamAutoPlayPreferredQuality
import com.nuvio.app.features.streams.StreamItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DeadLinkStartupMonitorTest {
    @Test
    fun duplicateSourceIdentitiesAreSkipped() = runTest {
        val current = stream("Current", "plugin:current", "https://media.example/current.mp4")
        val duplicate = stream("Duplicate URL", "addon:duplicate", "https://media.example/current.mp4")
        val next = stream("Next", "plugin:next", "https://media.example/next.mp4")
        val fallbacks = mutableListOf<StreamItem>()
        val monitor = newMonitor(
            scope = backgroundScope,
            timeoutSeconds = { 10 },
            candidates = { listOf(current, duplicate, next) },
            onFallback = fallbacks::add,
        )

        monitor.sourceSelected(current)
        runCurrent()
        advanceTimeBy(10_000)
        runCurrent()

        assertEquals(listOf(next), fallbacks)
        monitor.close()
    }

    @Test
    fun installedAddonIsChosenBeforePluginWhenBothAreUntried() = runTest {
        val current = stream("Current plugin", "plugin:current", "https://media.example/current.mp4")
        val plugin = stream("Other plugin", "plugin:other", "https://media.example/plugin.mp4")
        val addon = stream("Installed addon", "addon:one", "https://media.example/addon.mp4")
        val fallbacks = mutableListOf<StreamItem>()
        val monitor = newMonitor(
            scope = backgroundScope,
            timeoutSeconds = { 5 },
            candidates = { listOf(current, plugin, addon) },
            onFallback = fallbacks::add,
        )

        monitor.sourceSelected(current)
        runCurrent()
        advanceTimeBy(5_000)
        runCurrent()

        assertEquals(listOf(addon), fallbacks)
        monitor.close()
    }

    @Test
    fun timeoutDoesNotFallbackBeforeDeadline() = runTest {
        val current = stream("Current", "plugin:current", "https://media.example/current.mp4")
        val next = stream("Next", "plugin:next", "https://media.example/next.mp4")
        val fallbacks = mutableListOf<StreamItem>()
        val monitor = newMonitor(
            scope = backgroundScope,
            timeoutSeconds = { 10 },
            candidates = { listOf(current, next) },
            onFallback = fallbacks::add,
        )

        monitor.sourceSelected(current)
        runCurrent()
        advanceTimeBy(9_999)
        runCurrent()
        assertTrue(fallbacks.isEmpty())

        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(next), fallbacks)
        monitor.close()
    }

    @Test
    fun playbackStartingBeforeDeadlineCancelsFallback() = runTest {
        val current = stream("Current", "plugin:current", "https://media.example/current.mp4")
        val next = stream("Next", "plugin:next", "https://media.example/next.mp4")
        val fallbacks = mutableListOf<StreamItem>()
        val monitor = newMonitor(
            scope = backgroundScope,
            timeoutSeconds = { 10 },
            candidates = { listOf(current, next) },
            onFallback = fallbacks::add,
        )

        monitor.sourceSelected(current)
        runCurrent()
        advanceTimeBy(9_999)
        monitor.playbackStateChanged(current, isPlaying = true, isLoading = false)
        advanceTimeBy(5_000)
        runCurrent()

        assertTrue(fallbacks.isEmpty())
        monitor.close()
    }

    @Test
    fun oldTimerCannotSwitchAfterAnotherSourceIsSelected() = runTest {
        val first = stream("First", "plugin:first", "https://media.example/first.mp4")
        val second = stream("Second", "plugin:second", "https://media.example/second.mp4")
        val third = stream("Third", "plugin:third", "https://media.example/third.mp4")
        val fallbacks = mutableListOf<StreamItem>()
        val monitor = newMonitor(
            scope = backgroundScope,
            timeoutSeconds = { 10 },
            candidates = { listOf(first, second, third) },
            onFallback = fallbacks::add,
        )

        monitor.sourceSelected(first)
        runCurrent()
        advanceTimeBy(5_000)
        monitor.sourceSelected(second)
        runCurrent()

        advanceTimeBy(5_000)
        runCurrent()
        assertTrue(fallbacks.isEmpty())

        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(listOf(third), fallbacks)
        monitor.close()
    }

    @Test
    fun latePlayingEventFromPreviousSourceDoesNotMarkFallbackHealthy() = runTest {
        val first = stream("First", "plugin:first", "https://media.example/first.mp4")
        val second = stream("Second", "plugin:second", "https://media.example/second.mp4")
        val third = stream("Third", "plugin:third", "https://media.example/third.mp4")
        val fallbacks = mutableListOf<StreamItem>()
        lateinit var monitor: DeadLinkStartupMonitor
        monitor = newMonitor(
            scope = backgroundScope,
            timeoutSeconds = { 5 },
            candidates = { listOf(first, second, third) },
            onFallback = { selected ->
                fallbacks += selected
                if (selected == second) {
                    // Simulate a stale callback arriving synchronously while
                    // the old source's fallback callback is still on the stack.
                    monitor.playbackStateChanged(first, isPlaying = true, isLoading = false)
                }
            },
        )

        monitor.sourceSelected(first)
        runCurrent()
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(listOf(second), fallbacks)

        // This mirrors the player's active-stream effect after switchToSource.
        monitor.sourceSelected(second)
        runCurrent()
        advanceTimeBy(5_000)
        runCurrent()

        assertEquals(listOf(second, third), fallbacks)
        monitor.close()
    }

    @Test
    fun exhaustedCandidatesNotifyOnlyOnceUntilContentIsReset() = runTest {
        val current = stream("Only source", "plugin:only", "https://media.example/only.mp4")
        var exhaustedCount = 0
        var candidates = listOf(current)
        val fallbacks = mutableListOf<StreamItem>()
        val monitor = newMonitor(
            scope = backgroundScope,
            timeoutSeconds = { 5 },
            candidates = { candidates },
            onFallback = fallbacks::add,
            onExhausted = { exhaustedCount++ },
        )

        monitor.sourceSelected(current)
        runCurrent()
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(1, exhaustedCount)

        monitor.sourceSelected(current)
        advanceTimeBy(30_000)
        runCurrent()
        assertEquals(1, exhaustedCount)

        candidates = listOf(current, stream("New source", "plugin:new", "https://media.example/new.mp4"))
        monitor.resetForContent()
        monitor.sourceSelected(current)
        runCurrent()
        advanceTimeBy(5_000)
        runCurrent()

        assertEquals(1, exhaustedCount)
        assertEquals(1, fallbacks.size)
        monitor.close()
    }

    @Test
    fun resetForNewContentClearsAttemptedIdentitiesAndPlaybackState() = runTest {
        val first = stream("First", "plugin:first", "https://media.example/first.mp4")
        val second = stream("Second", "plugin:second", "https://media.example/second.mp4")
        val fallbacks = mutableListOf<StreamItem>()
        val monitor = newMonitor(
            scope = backgroundScope,
            timeoutSeconds = { 5 },
            candidates = { listOf(first, second) },
            onFallback = fallbacks::add,
        )

        monitor.sourceSelected(first)
        runCurrent()
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(listOf(second), fallbacks)

        monitor.resetForContent()
        monitor.sourceSelected(second)
        runCurrent()
        advanceTimeBy(5_000)
        runCurrent()

        assertEquals(listOf(second, first), fallbacks)
        monitor.close()
    }

    @Test
    fun updatedTimeoutAppliesToSubsequentSourceAttempts() = runTest {
        val first = stream("First", "plugin:first", "https://media.example/first.mp4")
        val second = stream("Second", "plugin:second", "https://media.example/second.mp4")
        val third = stream("Third", "plugin:third", "https://media.example/third.mp4")
        var timeoutSeconds = 5
        val fallbacks = mutableListOf<StreamItem>()
        val monitor = newMonitor(
            scope = backgroundScope,
            timeoutSeconds = { timeoutSeconds },
            candidates = { listOf(first, second, third) },
            onFallback = fallbacks::add,
        )

        monitor.sourceSelected(first)
        runCurrent() // Capture the first attempt's 5-second timeout.
        timeoutSeconds = 20
        advanceTimeBy(4_999)
        runCurrent()
        assertTrue(fallbacks.isEmpty())
        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(second), fallbacks)

        monitor.sourceSelected(second)
        runCurrent() // The next attempt uses the updated 20-second timeout.
        advanceTimeBy(19_999)
        runCurrent()
        assertEquals(listOf(second), fallbacks)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(second, third), fallbacks)
        monitor.close()
    }

    @Test
    fun nonPlayableCandidatesAreIgnoredSafely() = runTest {
        val current = stream("Current", "plugin:current", "https://media.example/current.mp4")
        val nonPlayable = StreamItem(addonName = "Metadata only", addonId = "plugin:metadata")
        val fallbacks = mutableListOf<StreamItem>()
        var exhaustedCount = 0
        val monitor = newMonitor(
            scope = backgroundScope,
            timeoutSeconds = { 5 },
            candidates = { listOf(current, nonPlayable) },
            onFallback = fallbacks::add,
            onExhausted = { exhaustedCount++ },
        )

        monitor.sourceSelected(current)
        runCurrent()
        advanceTimeBy(5_000)
        runCurrent()

        assertTrue(fallbacks.isEmpty())
        assertEquals(1, exhaustedCount)
        monitor.close()
    }

    @Test
    fun malformedSourceUrlCanFailOverWithoutCrashingTheMonitor() = runTest {
        val malformed = stream("Malformed", "plugin:malformed", "not a valid url")
        val valid = stream("Valid", "plugin:valid", "https://media.example/valid.mp4")
        val fallbacks = mutableListOf<StreamItem>()
        val monitor = newMonitor(
            scope = backgroundScope,
            timeoutSeconds = { 5 },
            candidates = { listOf(malformed, valid) },
            onFallback = fallbacks::add,
        )

        monitor.sourceSelected(malformed)
        runCurrent()
        advanceTimeBy(5_000)
        runCurrent()

        assertEquals(listOf(valid), fallbacks)
        monitor.close()
    }

    @Test
    fun discoversAlternativesWhenSourcesPanelWasNeverOpened() = runTest {
        val current = stream("Dead source", "addon:dead", "https://media.example/dead.mp4")
        val plugin = stream("Plugin alternative", "plugin:alternative", "https://media.example/plugin.mp4")
        val addon = stream("Installed addon alternative", "addon:alternative", "https://media.example/addon.mp4")
        var discoveryCalls = 0
        val fallbacks = mutableListOf<StreamItem>()
        var exhaustedCount = 0

        // No sources are cached before the timeout. Discovery only happens when
        // the watchdog requests it, matching playback without opening Sources.
        val monitor = newMonitor(
            scope = backgroundScope,
            timeoutSeconds = { 5 },
            candidates = {
                discoveryCalls++
                listOf(current, plugin, addon)
            },
            onFallback = fallbacks::add,
            onExhausted = { exhaustedCount++ },
        )

        monitor.sourceSelected(current)
        runCurrent()
        advanceTimeBy(4_999)
        runCurrent()
        assertEquals(0, discoveryCalls)
        assertTrue(fallbacks.isEmpty())

        advanceTimeBy(1)
        runCurrent()
        assertEquals(1, discoveryCalls)
        assertEquals(listOf(addon), fallbacks)
        assertEquals(0, exhaustedCount)

        // Each failed source gets a fresh startup timeout. Attempted sources
        // are excluded, and installed addons remain ahead of plugins.
        monitor.sourceSelected(addon)
        runCurrent()
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(listOf(addon, plugin), fallbacks)
        assertEquals(0, exhaustedCount)
        monitor.close()
    }

    @Test
    fun sourceChangeCancelsSuspendedDiscoveryAndCannotUseStaleResults() = runTest {
        val current = stream("Current", "addon:current", "https://media.example/current.mp4")
        val stale = stream("Stale", "addon:stale", "https://media.example/stale.mp4")
        val manuallySelected = stream("Manual", "addon:manual", "https://media.example/manual.mp4")
        val discoveryStarted = CompletableDeferred<Unit>()
        val completeDiscovery = CompletableDeferred<List<StreamItem>>()
        val fallbacks = mutableListOf<StreamItem>()

        val monitor = DeadLinkStartupMonitor(
            scope = backgroundScope,
            timeoutSeconds = { 5 },
            candidateStreams = {
                discoveryStarted.complete(Unit)
                completeDiscovery.await()
            },
            onFallback = fallbacks::add,
            onExhausted = {},
        )

        monitor.sourceSelected(current)
        runCurrent()
        advanceTimeBy(5_000)
        runCurrent()
        assertTrue(discoveryStarted.isCompleted)

        // Selecting another source cancels the in-flight discovery attempt.
        monitor.sourceSelected(manuallySelected)
        runCurrent()
        completeDiscovery.complete(listOf(current, stale))
        runCurrent()

        assertTrue(fallbacks.isEmpty())
        monitor.close()
    }

    @Test
    fun emptyDiscoveryIsNotExhaustedUntilFetchCompletes() = runTest {
        val current = stream("Only source", "addon:only", "https://media.example/only.mp4")
        val discoveryStarted = CompletableDeferred<Unit>()
        val completeDiscovery = CompletableDeferred<List<StreamItem>>()
        var exhaustedCount = 0

        val monitor = DeadLinkStartupMonitor(
            scope = backgroundScope,
            timeoutSeconds = { 5 },
            candidateStreams = {
                discoveryStarted.complete(Unit)
                completeDiscovery.await()
            },
            onFallback = {},
            onExhausted = { exhaustedCount++ },
        )

        monitor.sourceSelected(current)
        runCurrent()
        advanceTimeBy(5_000)
        runCurrent()
        assertTrue(discoveryStarted.isCompleted)
        assertEquals(0, exhaustedCount)

        completeDiscovery.complete(listOf(current))
        runCurrent()
        assertEquals(1, exhaustedCount)
        monitor.close()
    }

    @Test
    fun discoveryFailureRetriesAndCanStillFallback() = runTest {
        val current = stream("Dead source", "addon:dead", "https://media.example/dead.mp4")
        val next = stream("Alternative", "addon:next", "https://media.example/next.mp4")
        var discoveryCalls = 0
        val fallbacks = mutableListOf<StreamItem>()
        var exhaustedCount = 0
        val monitor = newMonitor(
            scope = backgroundScope,
            timeoutSeconds = { 5 },
            candidates = {
                discoveryCalls++
                if (discoveryCalls == 1) error("temporary discovery failure")
                listOf(current, next)
            },
            onFallback = fallbacks::add,
            onExhausted = { exhaustedCount++ },
        )

        monitor.sourceSelected(current)
        runCurrent()
        advanceTimeBy(5_000)
        runCurrent()

        assertEquals(1, discoveryCalls)
        assertTrue(fallbacks.isEmpty())
        assertEquals(0, exhaustedCount)

        advanceTimeBy(1_999)
        runCurrent()
        assertEquals(1, discoveryCalls)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(2, discoveryCalls)
        assertEquals(listOf(next), fallbacks)
        assertEquals(0, exhaustedCount)
        monitor.close()
    }

    @Test
    fun newlyArrivingCandidateIsTriedWhileOtherProvidersStillLoad() = runTest {
        val current = stream("Current dead link", "addon:current", "https://media.example/current.mp4")
        val next = stream("Late candidate", "addon:late", "https://media.example/late.mp4")
        var loading = true
        var candidates = listOf(current)
        val fallbacks = mutableListOf<StreamItem>()
        var exhaustedCount = 0
        val monitor = newMonitor(
            scope = backgroundScope,
            timeoutSeconds = { 5 },
            candidates = { candidates },
            onFallback = fallbacks::add,
            onExhausted = { exhaustedCount++ },
            sourceDiscoveryLoading = { loading },
        )

        monitor.sourceSelected(current)
        runCurrent()
        advanceTimeBy(5_000)
        runCurrent()
        assertTrue(fallbacks.isEmpty())
        assertEquals(0, exhaustedCount)

        candidates = listOf(current, next)
        advanceTimeBy(300)
        runCurrent()
        assertEquals(listOf(next), fallbacks)
        assertEquals(0, exhaustedCount)
        monitor.close()
    }

    @Test
    fun aDifferentSourceGetsAWatchdogAfterEarlierSourcePlayed() = runTest {
        val first = stream("First", "addon:first", "https://media.example/first.mp4")
        val second = stream("Second", "addon:second", "https://media.example/second.mp4")
        val third = stream("Third", "addon:third", "https://media.example/third.mp4")
        val fallbacks = mutableListOf<StreamItem>()
        val monitor = newMonitor(
            scope = backgroundScope,
            timeoutSeconds = { 5 },
            candidates = { listOf(first, second, third) },
            onFallback = fallbacks::add,
        )

        monitor.sourceSelected(first)
        runCurrent()
        monitor.playbackStateChanged(first, isPlaying = true, isLoading = false)
        monitor.sourceSelected(second)
        runCurrent()
        advanceTimeBy(5_000)
        runCurrent()

        assertEquals(listOf(third), fallbacks)
        monitor.close()
    }

    @Test
    fun fallbackChainContinuesWhenCandidateNeverBecomesActive() = runTest {
        val first = stream("First dead link", "addon:first", "https://media.example/first.mp4")
        val second = stream("Second candidate", "addon:second", "https://media.example/second.mp4")
        val third = stream("Third candidate", "addon:third", "https://media.example/third.mp4")
        val fallbacks = mutableListOf<StreamItem>()
        val markedDead = mutableListOf<StreamItem>()
        val monitor = newMonitor(
            scope = backgroundScope,
            timeoutSeconds = { 5 },
            candidates = { listOf(first, second, third) },
            // Simulate asynchronous preparation failing before activeStream changes.
            onFallback = fallbacks::add,
            onDeadLink = { stream, _ -> markedDead.add(stream) },
        )

        monitor.sourceSelected(first)
        runCurrent()
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(listOf(first), markedDead)
        assertEquals(listOf(second), fallbacks)

        // No sourceSelected(second) event arrives; the activation watchdog must
        // still mark it dead and advance to the next untried source.
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(listOf(second, third), fallbacks)
        assertEquals(listOf(first, second), markedDead)
        monitor.close()
    }

    @Test
    fun disablingTimeoutCancelsCurrentDeadlineAndReenablingRestartsIt() = runTest {
        val current = stream("Current dead link", "addon:current", "https://media.example/current.mp4")
        val next = stream("Next source", "addon:next", "https://media.example/next.mp4")
        var enabled = true
        val fallbacks = mutableListOf<StreamItem>()
        val monitor = newMonitor(scope = backgroundScope, timeoutSeconds = { 5 }, timeoutEnabled = { enabled }, candidates = { listOf(current, next) }, onFallback = fallbacks::add)
        monitor.sourceSelected(current)
        runCurrent()
        advanceTimeBy(2_000)
        enabled = false
        monitor.settingsChanged()
        advanceTimeBy(10_000)
        runCurrent()
        assertTrue(fallbacks.isEmpty())
        enabled = true
        monitor.settingsChanged()
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(listOf(next), fallbacks)
        monitor.close()
    }

    @Test
    fun bufferingWhileTimeoutDisabledIsRecognizedWhenTimeoutIsReenabled() = runTest {
        val current = stream("Current source", "addon:current", "https://media.example/current.mp4")
        val next = stream("Next source", "addon:next", "https://media.example/next.mp4")
        var enabled = true
        val fallbacks = mutableListOf<StreamItem>()
        val monitor = newMonitor(
            scope = backgroundScope,
            timeoutSeconds = { 5 },
            timeoutEnabled = { enabled },
            candidates = { listOf(current, next) },
            onFallback = fallbacks::add,
        )

        monitor.sourceSelected(current)
        runCurrent()
        monitor.playbackStateChanged(current, isPlaying = true, isLoading = false)

        // The guest follows the host, so its local fallback watchdog is suppressed.
        enabled = false
        monitor.settingsChanged()
        monitor.playbackStateChanged(current, isPlaying = false, isLoading = true)
        advanceTimeBy(5_000)
        runCurrent()
        assertTrue(fallbacks.isEmpty())

        // If sharing is disabled, local fallback resumes and recognizes existing buffering.
        enabled = true
        monitor.settingsChanged()
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(listOf(next), fallbacks)
        monitor.close()
    }

    @Test
    fun playIntentWhileStillLoadingDoesNotCancelDeadLinkDeadline() = runTest {
        val current = stream("Buffering dead link", "addon:current", "https://media.example/current.mp4")
        val next = stream("Next source", "addon:next", "https://media.example/next.mp4")
        val fallbacks = mutableListOf<StreamItem>()
        val monitor = newMonitor(
            scope = backgroundScope,
            timeoutSeconds = { 5 },
            candidates = { listOf(current, next) },
            onFallback = fallbacks::add,
        )

        monitor.sourceSelected(current)
        runCurrent()
        // Simulate engines that report play intent while the stream is still buffering.
        monitor.playbackStateChanged(current, isPlaying = true, isLoading = true)
        advanceTimeBy(5_000)
        runCurrent()

        assertEquals(listOf(next), fallbacks)
        monitor.close()
    }

    @Test
    fun startupBufferingBeforeStablePlaybackCanTriggerFallback() = runTest {
        val current = stream("Current dead link", "addon:current", "https://media.example/current.mp4")
        val next = stream("Next source", "addon:next", "https://media.example/next.mp4")
        val fallbacks = mutableListOf<StreamItem>()
        val monitor = newMonitor(scope = backgroundScope, timeoutSeconds = { 5 }, candidates = { listOf(current, next) }, onFallback = fallbacks::add)
        monitor.sourceSelected(current)
        runCurrent()
        monitor.playbackStateChanged(current, isPlaying = true, isLoading = false)
        monitor.playbackStateChanged(current, isPlaying = false, isLoading = true)
        advanceTimeBy(30_000)
        runCurrent()
        assertEquals(listOf(next), fallbacks)
        monitor.close()
    }


    @Test
    fun settingsChangeDoesNotRearmWatchdogForHealthyPlaybackDuringStabilityWindow() = runTest {
        val current = stream("Current", "addon:current", "https://media.example/current.mp4")
        val next = stream("Next", "addon:next", "https://media.example/next.mp4")
        val fallbacks = mutableListOf<StreamItem>()
        val monitor = newMonitor(scope = backgroundScope, timeoutSeconds = { 5 }, candidates = { listOf(current, next) }, onFallback = fallbacks::add)
        monitor.sourceSelected(current)
        runCurrent()
        monitor.playbackStateChanged(current, isPlaying = true, isLoading = false_000)
        advanceTimeBy(2_000)
        monitor.settingsChanged()
        advanceTimeBy(10_000)
        runCurrent()
        assertTrue(fallbacks.isEmpty())
        monitor.close()
    }

    @Test
    fun fallbackChecksDistinctLinksFromTheSameAddonIndividuallyBeforePlugins() = runTest {
        val first = stream("4K BluRay", "addon:one", "https://media.example/4k.mkv")
        val second = stream("1080p BluRay", "addon:one", "https://media.example/1080.mkv")
        val third = stream("720p", "addon:one", "https://media.example/720.mkv")
        val plugin = stream("Plugin 4K", "plugin:one", "https://media.example/plugin.mkv")
        val fallbacks = mutableListOf<StreamItem>()
        val markedDead = mutableListOf<StreamItem>()
        val monitor = newMonitor(
            scope = backgroundScope,
            timeoutSeconds = { 5 },
            // Preserve the quality/order supplied by the stream list within an addon.
            candidates = { listOf(first, second, third, plugin) },
            onFallback = fallbacks::add,
            onDeadLink = { stream, _ -> markedDead.add(stream) },
        )

        monitor.sourceSelected(first)
        runCurrent()
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(listOf(second), fallbacks)
        assertEquals(listOf(first), markedDead)

        monitor.sourceSelected(second)
        runCurrent()
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(listOf(second, third), fallbacks)
        assertEquals(listOf(first, second), markedDead)

        monitor.sourceSelected(third)
        runCurrent()
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(listOf(second, third, plugin), fallbacks)
        assertEquals(listOf(first, second, third), markedDead)
        monitor.close()
    }

    @Test
    fun streamIdentityIsPerAddonEntryButPlaybackTargetDedupesExactUrls() {
        val firstAddonLink = stream("4K", "addon:one", "https://media.example/shared.mkv")
        val sameUrlFromAnotherAddon = stream("4K", "addon:two", "https://media.example/shared.mkv")
        val secondLinkFromFirstAddon = stream("1080p", "addon:one", "https://media.example/1080.mkv")

        assertTrue(firstAddonLink.playerSourceIdentityKey() != sameUrlFromAnotherAddon.playerSourceIdentityKey())
        assertEquals(
            firstAddonLink.playerPlaybackDeduplicationKey(),
            sameUrlFromAnotherAddon.playerPlaybackDeduplicationKey(),
        )
        assertTrue(firstAddonLink.playerSourceIdentityKey() != secondLinkFromFirstAddon.playerSourceIdentityKey())
    }

    @Test
    fun resolvedPlaybackUrlKeepsOriginalIdentityForPruningAndFallback() = runTest {
        val original = StreamItem(
            name = "Original YouTube source",
            addonName = "YouTube addon",
            addonId = "addon:youtube",
        )
        val resolved = original.copy(url = "https://media.example/resolved.mp4")
        val fallback = stream("Fallback", "addon:fallback", "https://media.example/fallback.mp4")
        val originKey = original.playerSourceIdentityKey()!!
        assertTrue(originKey != resolved.playerSourceIdentityKey())

        val dead = mutableListOf<Pair<StreamItem, String>>()
        val fallbacks = mutableListOf<StreamItem>()
        val monitor = DeadLinkStartupMonitor(
            scope = backgroundScope,
            timeoutSeconds = { 5 },
            candidateStreams = { listOf(original, fallback) },
            onFallback = fallbacks::add,
            onExhausted = {},
            onDeadLink = { failed, key -> dead += failed to key },
        )

        monitor.sourceSelected(resolved, originKey)
        runCurrent()
        advanceTimeBy(5_000)
        runCurrent()

        assertEquals(listOf(originKey), dead.map { it.second })
        assertEquals(listOf(fallback), fallbacks)
        monitor.close()
    }

    @Test
    fun changingIdentityForSamePlaybackUrlDoesNotKeepOldWatchdog() = runTest {
        val first = stream("Shared URL", "addon:first", "https://media.example/shared.mp4")
        val second = first.copy(addonName = "Second add-on", addonId = "addon:second")
        val firstKey = first.playerSourceIdentityKey()!!
        val secondKey = second.playerSourceIdentityKey()!!
        val dead = mutableListOf<Pair<StreamItem, String>>()
        val monitor = DeadLinkStartupMonitor(
            scope = backgroundScope,
            timeoutSeconds = { 5 },
            candidateStreams = { emptyList() },
            onFallback = {},
            onExhausted = {},
            onDeadLink = { failed, key -> dead += failed to key },
        )

        monitor.sourceSelected(first, firstKey)
        runCurrent()
        advanceTimeBy(2_000)
        monitor.sourceSelected(second, secondKey)
        runCurrent()
        advanceTimeBy(4_999)
        runCurrent()
        assertTrue(dead.isEmpty())

        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(secondKey), dead.map { it.second })
        monitor.close()
    }

    @Test
    fun semiAutomaticFallbackKeepsQualityCeilingAndWaitsForSelectedTier() = runTest {
        val selected = stream("Selected 1080p", "addon:selected", "https://media.example/selected-1080.mp4")
        val late1080 = stream("Late 1080p", "addon:late", "https://media.example/late-1080.mp4")
        val early720 = stream("Early 720p", "plugin:early", "https://media.example/early-720.mp4")
        val higher2160 = stream("Above selection 2160p", "addon:higher", "https://media.example/higher-2160.mp4")
        var discovered = listOf(selected, early720)
        val fallbacks = mutableListOf<StreamItem>()
        val monitor = newMonitor(
            scope = backgroundScope,
            timeoutSeconds = { 5 },
            candidates = {
                QualityFirstAutoPlayPolicy.rankCandidatesWithFallback(
                    streams = discovered,
                    requestedQuality = StreamAutoPlayPreferredQuality.P1080,
                    debridEnabled = true,
                    p2pEnabled = true,
                )
            },
            onFallback = fallbacks::add,
            sourceDiscoveryLoading = { true },
            preserveCandidateOrder = { true },
        )

        monitor.sourceSelected(selected)
        runCurrent()
        advanceTimeBy(5_000)
        runCurrent()
        assertTrue(fallbacks.isEmpty())

        // A lower tier arrived first, but a slower provider can still return 1080p.
        discovered = listOf(selected, early720, late1080, higher2160)
        advanceTimeBy(300)
        runCurrent()
        assertEquals(listOf(late1080), fallbacks)

        monitor.close()
    }

    @Test
    fun semiAutomaticFallbackStepsDownOnlyAfterDiscoveryCompletes() = runTest {
        val selected = stream("Selected 1080p", "addon:selected", "https://media.example/selected-1080.mp4")
        val lower720 = stream("Fallback 720p", "plugin:lower", "https://media.example/lower-720.mp4")
        var loading = true
        val fallbacks = mutableListOf<StreamItem>()
        val monitor = newMonitor(
            scope = backgroundScope,
            timeoutSeconds = { 5 },
            candidates = {
                QualityFirstAutoPlayPolicy.rankCandidatesWithFallback(
                    streams = listOf(selected, lower720),
                    requestedQuality = StreamAutoPlayPreferredQuality.P1080,
                    debridEnabled = true,
                    p2pEnabled = true,
                )
            },
            onFallback = fallbacks::add,
            sourceDiscoveryLoading = { loading },
            preserveCandidateOrder = { true },
        )

        monitor.sourceSelected(selected)
        runCurrent()
        advanceTimeBy(5_000)
        runCurrent()
        assertTrue(fallbacks.isEmpty())

        loading = false
        advanceTimeBy(300)
        runCurrent()
        assertEquals(listOf(lower720), fallbacks)
        monitor.close()
    }

    @Test
    fun fatalPlaybackErrorAdvancesImmediatelyAfterPlaybackStarted() = runTest {
        val current = stream("Current playing source", "addon:current", "https://media.example/current.mp4")
        val next = stream("Fallback source", "addon:next", "https://media.example/next.mp4")
        val fallbacks = mutableListOf<StreamItem>()
        val monitor = newMonitor(
            scope = backgroundScope,
            timeoutSeconds = { 30 },
            candidates = { listOf(current, next) },
            onFallback = fallbacks::add,
        )

        monitor.sourceSelected(current)
        runCurrent()
        monitor.playbackStateChanged(current, isPlaying = true, isLoading = false)
        monitor.playbackFailed(current)
        runCurrent()

        assertEquals(listOf(next), fallbacks)
        monitor.close()
    }

    @Test
    fun staleFatalPlaybackErrorCannotSwitchTheNewlySelectedSource() = runTest {
        val first = stream("Old source", "addon:first", "https://media.example/first.mp4")
        val second = stream("Current source", "addon:second", "https://media.example/second.mp4")
        val third = stream("Next source", "addon:third", "https://media.example/third.mp4")
        val fallbacks = mutableListOf<StreamItem>()
        val monitor = newMonitor(
            scope = backgroundScope,
            timeoutSeconds = { 30 },
            candidates = { listOf(first, second, third) },
            onFallback = fallbacks::add,
        )

        monitor.sourceSelected(first)
        runCurrent()
        monitor.sourceSelected(second)
        runCurrent()
        monitor.playbackFailed(first)
        runCurrent()

        assertTrue(fallbacks.isEmpty())
        monitor.close()
    }

    @Test
    fun explicitSemiAutoQualityIsUsedWhenSelectedSourceHasNoQualityMetadata() = runTest {
        val selected = stream("Unclassified selected source", "addon:selected", "https://media.example/unknown.mp4")
        val lower720 = stream("720p fallback", "plugin:lower", "https://media.example/720.mp4")
        val higher2160 = stream("2160p above selection", "addon:higher", "https://media.example/2160.mp4")
        val fallbacks = mutableListOf<StreamItem>()
        val monitor = newMonitor(
            scope = backgroundScope,
            timeoutSeconds = { 5 },
            candidates = { listOf(selected, higher2160, lower720) },
            onFallback = fallbacks::add,
            preserveCandidateOrder = { true },
            requestedQualityHeight = { 1080 },
        )

        monitor.sourceSelected(selected)
        runCurrent()
        advanceTimeBy(5_000)
        runCurrent()

        assertEquals(listOf(lower720), fallbacks)
        monitor.close()
    }

    @Test
    fun stablePlaybackIgnoresLaterBufferingAndFatalErrorsUntilSourceChanges() = runTest {
        val current = stream("Current", "addon:current", "https://media.example/current.mp4")
        val next = stream("Next", "addon:next", "https://media.example/next.mp4")
        val afterNext = stream("After next", "addon:after", "https://media.example/after.mp4")
        val fallbacks = mutableListOf<StreamItem>()
        val monitor = newMonitor(scope = backgroundScope, timeoutSeconds = { 5 }, candidates = { listOf(current, next, afterNext) }, onFallback = fallbacks::add)
        monitor.sourceSelected(current)
        runCurrent()
        monitor.playbackStateChanged(current, isPlaying = true, isLoading = false)
        advanceTimeBy(25_000)
        runCurrent()
        monitor.playbackStateChanged(current, isPlaying = false, isLoading = true)
        monitor.playbackFailed(current)
        advanceTimeBy(30_000)
        runCurrent()
        assertTrue(fallbacks.isEmpty())
        monitor.sourceSelected(next)
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(listOf(afterNext), fallbacks)
        monitor.close()
    }

    @Test
    fun monitorRestsAfterTwentyFiveSecondsOfStablePlayback() = runTest {
        val current = stream("Current", "addon:current", "https://media.example/current.mp4")
        val next = stream("Next", "addon:next", "https://media.example/next.mp4")
        val fallbacks = mutableListOf<StreamItem>()
        val monitor = newMonitor(
            scope = backgroundScope,
            timeoutSeconds = { 5 },
            candidates = { listOf(current, next) },
            onFallback = fallbacks::add,
        )
        monitor.sourceSelected(current)
        runCurrent()
        monitor.playbackStateChanged(current, isPlaying = true, isLoading = false_000)
        advanceTimeBy(24_999)
        runCurrent()
        assertTrue(fallbacks.isEmpty())
        advanceTimeBy(1)
        runCurrent()
        monitor.playbackStateChanged(current, isPlaying = false, isLoading = true_000)
        advanceTimeBy(30_000)
        runCurrent()
        assertTrue(fallbacks.isEmpty())
        monitor.close()
    }

    @Test
    fun manualPauseDoesNotTriggerDeadLinkFallback() = runTest {
        val current = stream("Current", "addon:current", "https://media.example/current.mp4")
        val next = stream("Next", "addon:next", "https://media.example/next.mp4")
        val fallbacks = mutableListOf<StreamItem>()
        val monitor = newMonitor(
            scope = backgroundScope,
            timeoutSeconds = { 5 },
            candidates = { listOf(current, next) },
            onFallback = fallbacks::add,
        )
        monitor.sourceSelected(current)
        runCurrent()
        monitor.playbackStateChanged(current, isPlaying = true, isLoading = false_000)
        monitor.playbackStateChanged(current, isPlaying = false, isLoading = false_000)
        advanceTimeBy(30_000)
        runCurrent()
        assertTrue(fallbacks.isEmpty())
        monitor.close()
    }

    @Test
    fun endedPlaybackInvalidatesPendingDeadLinkWatchdog() = runTest {
        val current = stream("Current", "addon:current", "https://media.example/current.mp4")
        val next = stream("Next", "addon:next", "https://media.example/next.mp4")
        val fallbacks = mutableListOf<StreamItem>()
        val monitor = newMonitor(
            scope = backgroundScope,
            timeoutSeconds = { 5 },
            candidates = { listOf(current, next) },
            onFallback = fallbacks::add,
        )
        monitor.sourceSelected(current)
        runCurrent()
        monitor.playbackStateChanged(current, isPlaying = false, isLoading = false, isEnded = true)
        advanceTimeBy(30_000)
        runCurrent()
        assertTrue(fallbacks.isEmpty())
        monitor.close()
    }

    private fun stream(name: String, addonId: String, url: String) =
        StreamItem(name = name, url = url, addonName = name, addonId = addonId)

    private fun newMonitor(
        scope: kotlinx.coroutines.CoroutineScope,
        timeoutSeconds: () -> Int,
        candidates: suspend () -> List<StreamItem>,
        onFallback: (StreamItem) -> Unit,
        onExhausted: () -> Unit = {},
        sourceDiscoveryLoading: () -> Boolean = { false },
        onDeadLink: (StreamItem, String) -> Unit = { _, _ -> },
        timeoutEnabled: () -> Boolean = { true },
        preserveCandidateOrder: () -> Boolean = { false },
        requestedQualityHeight: () -> Int? = { null },
    ) = DeadLinkStartupMonitor(
        scope = scope,
        timeoutSeconds = timeoutSeconds,
        timeoutEnabled = timeoutEnabled,
        candidateStreams = candidates,
        onFallback = onFallback,
        onExhausted = onExhausted,
        sourceDiscoveryLoading = sourceDiscoveryLoading,
        onDeadLink = onDeadLink,
        preserveCandidateOrder = preserveCandidateOrder,
        requestedQualityHeight = requestedQualityHeight,
    )
}
