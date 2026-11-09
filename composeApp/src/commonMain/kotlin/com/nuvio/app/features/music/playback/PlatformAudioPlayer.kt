package com.nuvio.app.features.music.playback

/** Callbacks from the platform audio engine. All are delivered on the main thread. */
interface PlatformAudioListener {
    fun onState(isPlaying: Boolean, isBuffering: Boolean)
    fun onProgress(positionMs: Long, durationMs: Long)
    fun onEnded()
    fun onError(message: String)
    fun onRemoteCommand(command: RemoteCommand, positionMs: Long = 0L)
}

enum class RemoteCommand { Play, Pause, Toggle, Next, Previous, Seek }

/** What the lock screen / control centre shows. */
data class NowPlayingInfo(
    val title: String,
    val artist: String,
    val album: String?,
    val artworkUrl: String?,
    val durationMs: Long,
)

/** One audio engine per app: AVPlayer on iOS, MediaPlayer on Android. */
internal expect class PlatformAudioPlayer() {
    var listener: PlatformAudioListener?

    fun load(url: String, headers: Map<String, String>, startPositionMs: Long, autoplay: Boolean)
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    /** Playback speed, 1.0 being normal; pitch is kept. */
    fun setRate(rate: Float)
    fun stop()
    fun setNowPlaying(info: NowPlayingInfo?, positionMs: Long, isPlaying: Boolean)
}
