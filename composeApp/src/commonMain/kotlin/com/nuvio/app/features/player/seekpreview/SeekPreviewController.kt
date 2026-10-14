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
import kotlin.concurrent.Volatile
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlin.time.TimeSource

private const val FrameMaxWidthPx = 320

private const val MaxCachedFrames = 100

private const val MinBucketMs = 1_000L
private const val MaxBucketMs = 5_000L
private const val BucketsPerTitle = 1_000L

private const val MaxInitialFailures = 3

private const val WarmUpDelayMs = 1_200L
private const val IdleReleaseMs = 30_000L

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

    var frame by mutableStateOf<SeekPreviewFrame?>(null)
        private set

    var isUnavailable by mutableStateOf(false)
        private set

    fun request(positionMs: Long, durationMs: Long) {
        if (isUnavailable || durationMs <= 0L) return
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
        requests.value = null
        scheduleRelease()
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
