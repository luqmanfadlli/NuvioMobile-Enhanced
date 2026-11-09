package com.nuvio.app.features.music.playback

import com.nuvio.app.features.music.MusicLibrary
import com.nuvio.app.features.music.data.MusicStorage
import com.nuvio.app.features.music.data.YtMusicRepository
import com.nuvio.app.features.music.data.model.Song
import com.nuvio.app.features.music.data.model.durationMillis
import com.nuvio.app.features.music.lastfm.MusicLastFm
import com.nuvio.app.features.music.navidrome.MusicNavidrome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class RepeatMode { Off, All, One }

/** Podcast episodes play through the music engine as songs whose id starts with this. */
const val PODCAST_ID_PREFIX = "podcast:"

data class MusicPlayerState(
    val queue: List<Song> = emptyList(),
    val index: Int = -1,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val isLoading: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffle: Boolean = false,
    val repeat: RepeatMode = RepeatMode.Off,
    val error: String? = null,
    /** Playback speed for podcast episodes (music always plays at 1x). */
    val speed: Float = 1f,
) {
    val song: Song? get() = queue.getOrNull(index)
    val isPodcast: Boolean get() = song?.videoId?.startsWith(PODCAST_ID_PREFIX) == true
    val hasSong: Boolean get() = song != null
    val upNext: List<Song> get() = if (index in queue.indices) queue.drop(index + 1) else emptyList()
}

/**
 * The one music player: a queue, shuffle and repeat, autoplay when the queue runs out, lock-screen
 * controls, and scrobbles to Last.fm / Navidrome.
 */
object MusicPlayer : PlatformAudioListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val engine = PlatformAudioPlayer().also { it.listener = this }

    private val _state = MutableStateFlow(MusicPlayerState(speed = savedSpeed()))
    val state: StateFlow<MusicPlayerState> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var bufferTimeoutJob: Job? = null
    private var inOrderQueue: List<Song> = emptyList()
    private var announcedId: String? = null
    private var scrobbledId: String? = null
    private var consecutiveFailures = 0

    // ---- starting playback -------------------------------------------------------------------

    /** Plays [songs] starting at [startIndex]; with [shuffle] the rest are mixed but the start track plays first. */
    fun play(songs: List<Song>, startIndex: Int = 0, shuffle: Boolean = false) {
        if (songs.isEmpty()) return
        val start = songs[startIndex.coerceIn(songs.indices)]
        inOrderQueue = songs
        val queue = if (shuffle) listOf(start) + songs.filter { it !== start }.shuffled() else songs
        val index = if (shuffle) 0 else startIndex.coerceIn(songs.indices)
        _state.value = _state.value.copy(queue = queue, index = index, shuffle = shuffle, error = null)
        consecutiveFailures = 0
        startCurrent()
    }

    fun playSong(song: Song, context: List<Song> = listOf(song)) {
        val index = context.indexOfFirst { it.videoId == song.videoId }.takeIf { it >= 0 } ?: 0
        play(if (context.isEmpty()) listOf(song) else context, index)
    }

    fun jumpTo(index: Int) {
        if (index !in _state.value.queue.indices) return
        _state.value = _state.value.copy(index = index, error = null)
        startCurrent()
    }

    // ---- transport -----------------------------------------------------------------------------

    fun togglePlay() {
        val s = _state.value
        if (!s.hasSong) return
        if (s.isPlaying) pause() else resume()
    }

    fun resume() {
        if (!_state.value.hasSong) return
        if (_state.value.durationMs == 0L && !_state.value.isLoading) startCurrent() else engine.play()
    }

    fun pause() {
        engine.pause()
    }

    fun seekTo(positionMs: Long) {
        engine.seekTo(positionMs)
        _state.value = _state.value.copy(positionMs = positionMs)
        syncLockScreen()
    }

    /** Jumps forward or back inside the current track, as a podcast's 15 and 30 second buttons do. */
    fun skipBy(deltaMs: Long) {
        val s = _state.value
        if (!s.hasSong) return
        val limit = if (s.durationMs > 0) s.durationMs else Long.MAX_VALUE
        seekTo((s.positionMs + deltaMs).coerceIn(0L, limit))
    }

    /** Sets the speed podcast episodes play at; it is remembered. */
    fun setSpeed(rate: Float) {
        MusicStorage.putString("podcasts", "speed", rate.toString())
        _state.value = _state.value.copy(speed = rate)
        if (_state.value.isPodcast) engine.setRate(rate)
    }

    private fun savedSpeed(): Float = MusicStorage.getString("podcasts", "speed")?.toFloatOrNull()?.coerceIn(0.5f, 3f) ?: 1f

    fun next() = advance(auto = false)

    fun previous() {
        val s = _state.value
        if (!s.hasSong) return
        if (s.positionMs > 3_000L || s.index == 0) {
            seekTo(0L)
            return
        }
        _state.value = s.copy(index = s.index - 1, error = null)
        startCurrent()
    }

    fun toggleShuffle() {
        val s = _state.value
        val current = s.song
        if (!s.shuffle) {
            if (current == null) {
                _state.value = s.copy(shuffle = true)
                return
            }
            inOrderQueue = s.queue
            val rest = s.queue.filterIndexed { i, _ -> i != s.index }
            val head = s.queue.take(s.index)
            // Played tracks stay where they are; what is still to come is mixed.
            _state.value = s.copy(queue = head + current + rest.drop(head.size).shuffled(), shuffle = true)
        } else {
            val restored = inOrderQueue.takeIf { list -> current != null && list.any { it.videoId == current.videoId } } ?: s.queue
            val index = restored.indexOfFirst { it.videoId == current?.videoId }.takeIf { it >= 0 } ?: s.index
            _state.value = s.copy(queue = restored, index = index, shuffle = false)
        }
    }

    fun cycleRepeat() {
        val next = when (_state.value.repeat) {
            RepeatMode.Off -> RepeatMode.All
            RepeatMode.All -> RepeatMode.One
            RepeatMode.One -> RepeatMode.Off
        }
        _state.value = _state.value.copy(repeat = next)
    }

    // ---- queue editing -------------------------------------------------------------------------

    fun playNext(song: Song) {
        val s = _state.value
        if (!s.hasSong) {
            play(listOf(song))
            return
        }
        val at = (s.index + 1).coerceAtMost(s.queue.size)
        _state.value = s.copy(queue = s.queue.toMutableList().also { it.add(at, song) })
    }

    fun addToQueue(song: Song) {
        val s = _state.value
        if (!s.hasSong) play(listOf(song)) else _state.value = s.copy(queue = s.queue + song)
    }

    fun removeFromQueue(index: Int) {
        val s = _state.value
        if (index !in s.queue.indices || index == s.index) return
        _state.value = s.copy(
            queue = s.queue.filterIndexed { i, _ -> i != index },
            index = if (index < s.index) s.index - 1 else s.index,
        )
    }

    fun stop() {
        loadJob?.cancel()
        bufferTimeoutJob?.cancel()
        engine.stop()
        _state.value = MusicPlayerState(shuffle = _state.value.shuffle, repeat = _state.value.repeat, speed = _state.value.speed)
    }

    // ---- internals -----------------------------------------------------------------------------

    private fun advance(auto: Boolean) {
        val s = _state.value
        if (!s.hasSong) return
        when {
            s.index + 1 < s.queue.size -> {
                _state.value = s.copy(index = s.index + 1, error = null)
                startCurrent()
            }
            s.repeat == RepeatMode.All -> {
                _state.value = s.copy(index = 0, error = null)
                startCurrent()
            }
            auto -> continueWithRadio()
            else -> seekTo(0L).also { engine.pause() }
        }
    }

    /** The queue ran out: keep the music going with tracks like the last one, as the catalogue's radio does. */
    private fun continueWithRadio() {
        val last = _state.value.song ?: return
        if (last.localUri != null || last.videoId.startsWith("navidrome:")) {
            _state.value = _state.value.copy(isPlaying = false)
            return
        }
        loadJob?.cancel()
        loadJob = scope.launch {
            val more = YtMusicRepository.radio(last.videoId).getOrNull().orEmpty()
                .filter { candidate -> _state.value.queue.none { it.videoId == candidate.videoId } }
            if (more.isEmpty()) {
                _state.value = _state.value.copy(isPlaying = false)
                return@launch
            }
            val s = _state.value
            _state.value = s.copy(queue = s.queue + more, index = s.queue.size, error = null)
            startCurrent()
        }
    }

    private fun startCurrent() {
        val song = _state.value.song ?: return
        loadJob?.cancel()
        announcedId = null
        scrobbledId = null
        _state.value = _state.value.copy(isLoading = true, isBuffering = true, positionMs = 0L, durationMs = song.durationMillis(), error = null)
        syncLockScreen()
        loadJob = scope.launch {
            MusicStreamResolver.resolve(song).fold(
                onSuccess = { stream ->
                    consecutiveFailures = 0
                    engine.setRate(if (song.videoId.startsWith(PODCAST_ID_PREFIX)) _state.value.speed else 1f)
                    engine.load(stream.url, stream.headers, 0L, autoplay = true)
                    if (!song.videoId.startsWith(PODCAST_ID_PREFIX)) MusicLibrary.recordPlayed(song)
                    _state.value = _state.value.copy(isLoading = false)
                    _state.value.queue.getOrNull(_state.value.index + 1)?.let { next ->
                        scope.launch {
                            MusicStreamResolver.warm(next)
                            if (TorBoxStreamResolver.isConfigured &&
                                StreamSource.current() != StreamSource.YouTube
                            ) TorBoxStreamResolver.warm(next)
                        }
                    }
                    bufferTimeoutJob?.cancel()
                    bufferTimeoutJob = scope.launch {
                        delay(12_000)
                        val s = _state.value
                        if (s.song?.videoId == song.videoId && s.positionMs == 0L && s.isBuffering) {
                            onError("The stream didn't start in time — the server may be blocking this network.")
                        }
                    }
                },
                onFailure = { error ->
                    consecutiveFailures++
                    _state.value = _state.value.copy(isLoading = false, isBuffering = false, isPlaying = false, error = error.message ?: "Couldn't play this track.")
                    if (consecutiveFailures < 4 && _state.value.index + 1 < _state.value.queue.size) {
                        delay(900)
                        advance(auto = true)
                    }
                },
            )
        }
    }

    private fun syncLockScreen() {
        val s = _state.value
        val song = s.song
        engine.setNowPlaying(
            info = song?.let {
                NowPlayingInfo(it.title, it.artist, it.albumName, it.thumbnailUrl, s.durationMs.takeIf { d -> d > 0 } ?: it.durationMillis())
            },
            positionMs = s.positionMs,
            isPlaying = s.isPlaying,
        )
    }

    // ---- PlatformAudioListener ---------------------------------------------------------------

    override fun onState(isPlaying: Boolean, isBuffering: Boolean) {
        val s = _state.value
        if (s.isPlaying == isPlaying && s.isBuffering == (isBuffering || s.isLoading)) return
        _state.value = s.copy(isPlaying = isPlaying, isBuffering = isBuffering || s.isLoading)
        syncLockScreen()
    }

    override fun onProgress(positionMs: Long, durationMs: Long) {
        val s = _state.value
        val song = s.song ?: return
        val duration = if (durationMs > 0) durationMs else s.durationMs
        _state.value = s.copy(positionMs = positionMs, durationMs = duration)
        if (positionMs > 0) bufferTimeoutJob?.cancel()
        if (!s.isPlaying || song.videoId.startsWith(PODCAST_ID_PREFIX)) return
        if (announcedId != song.videoId) {
            announcedId = song.videoId
            scope.launch {
                if (song.videoId.startsWith("navidrome:")) MusicNavidrome.scrobble(song, submission = false)
                MusicLastFm.nowPlaying(song)
            }
        }
        val threshold = MusicLastFm.scrobbleThresholdMs(duration)
        if (threshold != null && positionMs >= threshold && scrobbledId != song.videoId) {
            scrobbledId = song.videoId
            scope.launch {
                if (song.videoId.startsWith("navidrome:")) MusicNavidrome.scrobble(song, submission = true)
                MusicLastFm.scrobble(song)
            }
        }
    }

    override fun onEnded() {
        if (_state.value.repeat == RepeatMode.One) {
            engine.seekTo(0L)
            engine.play()
        } else {
            advance(auto = true)
        }
    }

    override fun onError(message: String) {
        _state.value = _state.value.copy(isPlaying = false, isBuffering = false, error = message)
    }

    override fun onRemoteCommand(command: RemoteCommand, positionMs: Long) {
        when (command) {
            RemoteCommand.Play -> resume()
            RemoteCommand.Pause -> pause()
            RemoteCommand.Toggle -> togglePlay()
            RemoteCommand.Next -> next()
            RemoteCommand.Previous -> previous()
            RemoteCommand.Seek -> seekTo(positionMs)
        }
    }
}
