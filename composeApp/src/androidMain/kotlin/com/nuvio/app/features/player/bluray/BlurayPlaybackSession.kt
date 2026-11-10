@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.nuvio.app.features.player.bluray

import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.source.MediaSource
import com.nuvio.app.features.player.PlayerDiscTitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

internal class BlurayPlaybackSession(
    private val sourceUrl: String,
    private val metadataFactory: DataSource.Factory,
) {
    private val mutex = Mutex()
    private var resolved = false
    private var failureProbed = false
    private var languagesLoaded = false
    private val languages = HashMap<Int, Pair<List<String>, List<String>>>()

    @Volatile
    var disc: BlurayDisc? = null
        private set

    @Volatile
    var activeIndex: Int = 0
        private set

    @Volatile
    var featurePositionMs: Long = 0L
        private set

    val hasTitles: Boolean get() = (disc?.titles?.size ?: 0) > 1

    val isExtraPlaying: Boolean
        get() {
            val current = disc ?: return false
            return current.titles.size > 1 && activeIndex != current.featureIndex
        }

    val canProbeAfterFailure: Boolean get() = disc == null && !failureProbed

    suspend fun resolveBeforePlayback(): BlurayDisc? = mutex.withLock {
        if (resolved) return@withLock disc
        resolved = true
        if (!BlurayDiscResolver.isCandidate(sourceUrl)) return@withLock null
        val timeoutMs = when {
            BlurayIsoImage.isLikelyIsoUrl(sourceUrl) -> ISO_TIMEOUT_MS
            BlurayDiscResolver.isDiscEntryUrl(sourceUrl) -> ENTRY_TIMEOUT_MS
            else -> FOLDER_TIMEOUT_MS
        }
        val found = withTimeoutOrNull(timeoutMs) {
            runInterruptible(Dispatchers.IO) {
                runCatching { BlurayDiscResolver.resolveBeforePlayback(sourceUrl, metadataFactory) }
                    .onFailure { Log.w(TAG, "disc resolve failed: ${it.javaClass.simpleName}: ${it.message}") }
                    .getOrNull()
            }
        } ?: BlurayDiscResolver.singleClipDisc(sourceUrl).takeIf { BlurayDiscResolver.isLikelyBdavM2tsUrl(sourceUrl) }
        adopt(found)
        found
    }

    suspend fun probeAfterFailure(): BlurayDisc? = mutex.withLock {
        if (disc != null || failureProbed) return@withLock null
        failureProbed = true
        resolved = true
        val found = withTimeoutOrNull(ISO_TIMEOUT_MS) {
            runInterruptible(Dispatchers.IO) {
                runCatching { BlurayDiscResolver.probeAfterFailure(sourceUrl, metadataFactory) }
                    .onFailure { Log.w(TAG, "disc probe failed: ${it.javaClass.simpleName}: ${it.message}") }
                    .getOrNull()
            }
        }
        adopt(found)
        found
    }

    private fun adopt(found: BlurayDisc?) {
        if (found == null) return
        disc = found
        activeIndex = found.featureIndex
        featurePositionMs = 0L
        Log.i(TAG, "playing Blu-ray title ${found.feature.name} (${found.titles.size} titles)")
    }

    fun mediaSource(playbackFactory: DataSource.Factory, mediaItem: MediaItem): MediaSource? {
        val current = disc ?: return null
        val title = current.titles.getOrNull(activeIndex) ?: current.feature
        return BlurayMediaSources.create(current, title, playbackFactory, metadataFactory, mediaItem)
    }

    fun select(index: Int, currentPositionMs: Long): Long? {
        val current = disc ?: return null
        if (index !in current.titles.indices || index == activeIndex) return null
        if (!isExtraPlaying) featurePositionMs = currentPositionMs.coerceAtLeast(0L)
        activeIndex = index
        return if (index == current.featureIndex) featurePositionMs else 0L
    }

    fun returnToFeature(): Long? {
        val current = disc ?: return null
        if (!isExtraPlaying) return null
        activeIndex = current.featureIndex
        return featurePositionMs
    }

    fun titles(): List<PlayerDiscTitle> {
        val current = disc ?: return emptyList()
        if (current.titles.size < 2) return emptyList()
        return current.titles.mapIndexed { index, title ->
            val (audio, subtitles) = synchronized(languages) { languages[index] } ?: (emptyList<String>() to emptyList())
            PlayerDiscTitle(
                name = title.name,
                durationMs = title.durationMs,
                clipCount = title.clipUris.size,
                isFeature = index == current.featureIndex,
                audioLanguages = audio,
                subtitleLanguages = subtitles,
            )
        }
    }

    suspend fun loadLanguages(onTitleLoaded: () -> Unit) {
        val current = disc ?: return
        if (current.titles.size < 2) return
        mutex.withLock {
            if (languagesLoaded) return
            languagesLoaded = true
        }
        val byClip = HashMap<String, Pair<List<String>, List<String>>>()
        current.titles.forEachIndexed { index, title ->
            val clip = title.clipUris.firstOrNull() ?: return@forEachIndexed
            val result = byClip[clip] ?: runInterruptible(Dispatchers.IO) {
                runCatching { BlurayDiscResolver.titleLanguages(title, metadataFactory) }
                    .getOrDefault(emptyList<String>() to emptyList())
            }.also { byClip[clip] = it }
            synchronized(languages) { languages[index] = result }
            onTitleLoaded()
        }
    }

    private companion object {
        const val TAG = "BlurayPlayback"
        const val FOLDER_TIMEOUT_MS = 8_000L
        const val ENTRY_TIMEOUT_MS = 20_000L
        const val ISO_TIMEOUT_MS = 45_000L
    }
}
