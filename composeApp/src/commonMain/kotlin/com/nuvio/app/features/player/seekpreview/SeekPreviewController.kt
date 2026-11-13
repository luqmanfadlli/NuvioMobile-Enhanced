package com.nuvio.app.features.player.seekpreview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import com.nuvio.app.core.logging.InAppLogger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.concurrent.Volatile
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlin.time.TimeSource
import kotlin.time.TimeSource.Monotonic.ValueTimeMark

private const val FrameMaxWidthPx = 320

private const val MaxCachedFrames = 100

private const val MinBucketMs = 1_000L
private const val MaxBucketMs = 5_000L
private const val BucketsPerTitle = 1_000L

private const val MaxInitialFailures = 3

private const val WarmUpDelayMs = 1_200L
private const val IdleReleaseMs = 30_000L

private const val ThumbnailAskMs = 80L
private const val ThumbnailFallbackMs = 15_000L

private const val WarmAttempts = 3
private const val WarmRetryMs = 11_000L

private const val PrefetchCount = 48L
private const val PrefetchMinStepMs = 30_000L
private const val PrefetchDelayMs = 3_000L
private const val PrefetchGapMs = 250L
private const val PrefetchStepTimeoutMs = 20_000L

private const val LogTag = "Player/SeekPreview"

internal class SeekPreviewFrame(val positionMs: Long, val bitmap: ImageBitmap)

@Stable
internal class SeekPreviewController(
    private val url: String,
    private val headers: Map<String, String>,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    private val decodeDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val requests = MutableStateFlow<Long?>(null)

    private val cache = LinkedHashMap<Long, ImageBitmap>()
    private var worker: Job? = null
    private var warmJob: Job? = null
    private var releaseJob: Job? = null
    private var scrubbing = false
    private var consecutiveFailures = 0
    private var hasSucceeded = false

    @Volatile
    private var source: SeekPreviewFrameSource? = null
    private var sourceOpened = false

    private var connectionGeneration = 0

    private var feed: SeekPreviewFeed? = null
    private var feedOpened = false
    private var feedFailed = false
    private var feedWanted: Long? = null
    private var feedAskedAt: ValueTimeMark? = null
    private var feedAskJob: Job? = null
    private var feedDurationMs = 0L
    private var feedFallbackJob: Job? = null
    private var feedWarming = false
    private var feedAnchorMs = 0L
    private val feedCache = LinkedHashMap<Long, ImageBitmap>()
    private var feedArrived: CompletableDeferred<Unit>? = null
    private var prefetchJob: Job? = null
    private var disposed = false

    var frame by mutableStateOf<SeekPreviewFrame?>(null)
        private set

    var isUnavailable by mutableStateOf(false)
        private set

    fun request(positionMs: Long, durationMs: Long) {
        if (isUnavailable || durationMs <= 0L) return
        if (openFeed() != null) {
            scrubbing = true
            warmJob?.cancel()
            prefetchJob?.cancel()
            feedWarming = false
            feedDurationMs = durationMs
            wantFeed(positionMs.coerceIn(0L, durationMs))
            return
        }
        scrubbing = true
        warmJob?.cancel()
        releaseJob?.cancel()
        val bucket = bucketFor(positionMs.coerceIn(0L, durationMs), durationMs)
        val cached = cache.remove(bucket)?.also { cache[bucket] = it }
        if (cached != null) {
            frame = SeekPreviewFrame(bucket, cached)
        } else {
            nearestCached(bucket)?.let { frame = it }
        }
        requests.value = bucket
        if (worker == null) worker = scope.launch { decodeRequests() }
    }

    fun warmUp(positionMs: Long, durationMs: Long) {
        if (isUnavailable || durationMs <= 0L || scrubbing) return
        if (openFeed() != null) {
            val target = positionMs.coerceIn(0L, durationMs)
            feedDurationMs = durationMs
            feedAnchorMs = target
            warmJob?.cancel()
            warmJob = scope.launch {
                if (!hasSucceeded) delay(WarmUpDelayMs)
                repeat(WarmAttempts) {
                    if (scrubbing || feed == null) return@launch
                    feedWarming = true
                    feed?.want(target, frame?.positionMs)
                    if (hasSucceeded) return@launch
                    delay(WarmRetryMs)
                    if (hasSucceeded) return@launch
                }
            }
            return
        }
        releaseJob?.cancel()
        warmJob?.cancel()
        warmJob = scope.launch {
            delay(WarmUpDelayMs)
            if (scrubbing || isUnavailable) return@launch
            val bucket = bucketFor(positionMs.coerceIn(0L, durationMs), durationMs)
            cache[bucket]?.let { frame = SeekPreviewFrame(bucket, it) }
            requests.value = bucket
            if (worker == null) worker = scope.launch { decodeRequests() }
            scheduleRelease()
        }
    }

    fun endScrub() {
        scrubbing = false
        if (feed != null) {
            feedWanted?.let { feedAnchorMs = it }
            feedWanted = null
            feedAskJob?.cancel()
            feedAskJob = null
            if (hasSucceeded) startPrefetch()
            return
        }
        requests.value = null
        scheduleRelease()
    }

    private fun openFeed(): SeekPreviewFeed? {
        if (feedFailed) return null
        if (!feedOpened) {
            feedOpened = true
            feed = openSeekPreviewFeed(url, headers, ::onFeedThumbnail)
            if (feed == null) feedFailed = true
            InAppLogger.info(
                LogTag,
                "native thumbnails=${feed != null} headers=${headers.size} url=${InAppLogger.redactUrl(url)}",
            )
        }
        return feed
    }

    private fun wantFeed(positionMs: Long) {
        feedWanted = positionMs
        nearestFeed(positionMs)?.let { near ->
            if (isCloser(near.positionMs, positionMs)) frame = near
        }
        if (feedAskJob?.isActive == true) return
        val waitMs = feedAskedAt?.let { ThumbnailAskMs - it.elapsedNow().inWholeMilliseconds } ?: 0L
        if (waitMs <= 0L) {
            askFeed()
        } else {
            feedAskJob = scope.launch {
                delay(waitMs)
                askFeed()
            }
        }
    }

    private fun askFeed() {
        feedAskJob = null
        feedAskedAt = TimeSource.Monotonic.markNow()
        val wanted = feedWanted ?: return
        feed?.want(wanted, frame?.positionMs)
        if (!hasSucceeded && feedFallbackJob == null) {
            feedFallbackJob = scope.launch {
                delay(ThumbnailFallbackMs)
                if (!hasSucceeded) onFeedThumbnail(-1L, null, "no thumbnail after ${ThumbnailFallbackMs}ms")
            }
        }
    }

    private fun onFeedThumbnail(positionMs: Long, bitmap: ImageBitmap?, error: String?) {
        if (disposed || feed == null) return
        if (bitmap == null) {
            InAppLogger.info(LogTag, "no native thumbnails: $error")
            feedFailed = true
            feed?.close()
            feed = null
            feedAskJob?.cancel()
            feedAskJob = null
            feedFallbackJob?.cancel()
            warmJob?.cancel()
            prefetchJob?.cancel()
            feedArrived = null
            feedCache.clear()
            val wanted = feedWanted
            feedWanted = null
            if (scrubbing && wanted != null) {
                request(wanted, feedDurationMs)
            } else if (feedDurationMs > 0L) {
                warmUp(feedAnchorMs, feedDurationMs)
            }
            return
        }
        hasSucceeded = true
        feedFallbackJob?.cancel()
        feedCache.remove(positionMs)
        feedCache[positionMs] = bitmap
        while (feedCache.size > MaxCachedFrames) {
            feedCache.remove(feedCache.keys.first())
        }
        feedArrived?.complete(Unit)
        val wanted = feedWanted
        if (wanted != null) {
            if (isCloser(positionMs, wanted)) frame = SeekPreviewFrame(positionMs, bitmap)
        } else if (feedWarming) {
            feedWarming = false
            frame = SeekPreviewFrame(positionMs, bitmap)
        }
        if (!scrubbing) startPrefetch()
    }

    private fun isCloser(positionMs: Long, wantedMs: Long): Boolean {
        val shown = frame ?: return true
        return abs(positionMs - wantedMs) <= abs(shown.positionMs - wantedMs)
    }

    private fun nearestFeed(positionMs: Long): SeekPreviewFrame? {
        var bestKey: Long? = null
        var bestDistance = Long.MAX_VALUE
        for (key in feedCache.keys) {
            val distance = abs(key - positionMs)
            if (distance < bestDistance) {
                bestDistance = distance
                bestKey = key
            }
        }
        val key = bestKey ?: return null
        val bitmap = feedCache[key] ?: return null
        return SeekPreviewFrame(key, bitmap)
    }

    private fun startPrefetch() {
        if (disposed || scrubbing || feed == null || feedDurationMs <= 0L) return
        if (prefetchJob?.isActive == true) return
        val durationMs = feedDurationMs
        val anchorMs = feedAnchorMs.coerceIn(0L, durationMs)
        val stepMs = (durationMs / PrefetchCount).coerceAtLeast(PrefetchMinStepMs)
        prefetchJob = scope.launch {
            delay(PrefetchDelayMs)
            for (target in prefetchTargets(anchorMs, durationMs, stepMs)) {
                if (scrubbing || feed == null) return@launch
                val near = nearestFeed(target)
                if (near != null && abs(near.positionMs - target) < stepMs / 2) continue
                val arrived = CompletableDeferred<Unit>()
                feedArrived = arrived
                feed?.want(target, null)
                withTimeoutOrNull(PrefetchStepTimeoutMs) { arrived.await() }
                if (feedArrived === arrived) feedArrived = null
                delay(PrefetchGapMs)
            }
        }
    }

    private fun prefetchTargets(anchorMs: Long, durationMs: Long, stepMs: Long): Sequence<Long> = sequence {
        yield(anchorMs)
        var offset = stepMs
        while (anchorMs + offset <= durationMs || anchorMs - offset >= 0L) {
            if (anchorMs + offset <= durationMs) yield(anchorMs + offset)
            if (anchorMs - offset >= 0L) yield(anchorMs - offset)
            offset += stepMs
        }
    }

    private fun scheduleRelease() {
        releaseJob?.cancel()
        releaseJob = scope.launch {
            delay(IdleReleaseMs)
            if (scrubbing) return@launch
            requests.value = null
            releaseConnection()
        }
    }

    private fun releaseConnection() {
        connectionGeneration++
        source?.cancel()
        CoroutineScope(decodeDispatcher).launch {
            val activeSource = source ?: return@launch
            source = null
            sourceOpened = false
            runCatching { activeSource.close() }
            InAppLogger.debug(LogTag, "connection released")
        }
    }

    fun dispose() {
        disposed = true
        feed?.close()
        feed = null
        scope.cancel()
        source?.cancel()
        CoroutineScope(decodeDispatcher).launch {
            runCatching { source?.close() }
            source = null
        }
    }

    private suspend fun decodeRequests() {
        requests.filterNotNull().collect { bucket ->
            if (cache.containsKey(bucket) && source != null) return@collect
            val started = TimeSource.Monotonic.markNow()
            val generation = connectionGeneration
            val bitmap = try {
                withContext(decodeDispatcher) { decodeFrame(bucket) }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                if (generation != connectionGeneration) return@collect
                onFailure(bucket, started.elapsedNow().inWholeMilliseconds, error)
                return@collect
            }
            val elapsedMs = started.elapsedNow().inWholeMilliseconds
            if (bitmap == null) {
                onFailure(bucket, elapsedMs, null)
                return@collect
            }
            InAppLogger.debug(LogTag, "frame at ${bucket}ms ${bitmap.width}x${bitmap.height} in ${elapsedMs}ms")
            hasSucceeded = true
            consecutiveFailures = 0
            cache[bucket] = bitmap
            while (cache.size > MaxCachedFrames) {
                cache.remove(cache.keys.first())
            }
            val latest = requests.value
            val shown = frame
            if (latest == bucket || shown == null || shown.positionMs != latest) {
                frame = if (latest == bucket) {
                    SeekPreviewFrame(bucket, bitmap)
                } else {
                    latest?.let(::nearestCached) ?: SeekPreviewFrame(bucket, bitmap)
                }
            }
        }
    }

    private fun decodeFrame(positionMs: Long): ImageBitmap? {
        if (!sourceOpened) {
            sourceOpened = true
            source = openSeekPreviewFrameSource(url, headers)
            InAppLogger.info(
                LogTag,
                "source=${source?.let { it::class.simpleName } ?: "none"} headers=${headers.size} " +
                    "url=${InAppLogger.redactUrl(url)}",
            )
        }
        val activeSource = source ?: throw UnsupportedSeekPreviewSource
        return activeSource.frameAt(positionMs, FrameMaxWidthPx)
    }

    private fun onFailure(positionMs: Long, elapsedMs: Long, error: Throwable?) {
        consecutiveFailures++
        val reason = error?.let { "${it::class.simpleName}: ${it.message}" } ?: "no frame"
        InAppLogger.warn(LogTag, "frame at ${positionMs}ms failed after ${elapsedMs}ms: $reason")
        if (error === UnsupportedSeekPreviewSource) {
            isUnavailable = true
        } else if (!hasSucceeded && consecutiveFailures >= MaxInitialFailures) {
            InAppLogger.warn(LogTag, "disabled for this stream after $consecutiveFailures failures")
            isUnavailable = true
        }
        if (isUnavailable) {
            frame = null
            worker?.cancel()
            releaseConnection()
        }
    }

    private fun nearestCached(bucket: Long): SeekPreviewFrame? {
        var bestKey: Long? = null
        var bestDistance = Long.MAX_VALUE
        for (key in cache.keys) {
            val distance = abs(key - bucket)
            if (distance < bestDistance) {
                bestDistance = distance
                bestKey = key
            }
        }
        val key = bestKey ?: return null
        val bitmap = cache[key] ?: return null
        return SeekPreviewFrame(key, bitmap)
    }

    private object UnsupportedSeekPreviewSource : RuntimeException("no frame source for this stream")
}

internal fun bucketFor(positionMs: Long, durationMs: Long): Long {
    val bucketMs = (durationMs / BucketsPerTitle).coerceIn(MinBucketMs, MaxBucketMs)
    return ((positionMs + bucketMs / 2) / bucketMs * bucketMs).coerceAtMost(durationMs)
}

@Composable
internal fun rememberSeekPreviewController(
    url: String?,
    headers: Map<String, String>,
): SeekPreviewController? {
    val controller = remember(url, headers) {
        url?.let { SeekPreviewController(it, headers) }
    }
    DisposableEffect(controller) {
        onDispose { controller?.dispose() }
    }
    return controller
}
