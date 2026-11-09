package com.nuvio.app.features.music.playback

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.nuvio.app.features.music.data.MusicStorage

internal actual class PlatformAudioPlayer {
    actual var listener: PlatformAudioListener? = null

    private val handler = Handler(Looper.getMainLooper())
    private var media: MediaPlayer? = null
    private var prepared = false
    private var wantPlay = false
    private var pendingSeek = 0L
    private var rate = 1f

    private val ticker = object : Runnable {
        override fun run() {
            val player = media
            if (player != null && prepared) {
                listener?.onState(isPlaying = player.isPlaying, isBuffering = false)
                listener?.onProgress(player.currentPosition.toLong(), player.duration.toLong().coerceAtLeast(0L))
            }
            handler.postDelayed(this, 250)
        }
    }

    actual fun load(url: String, headers: Map<String, String>, startPositionMs: Long, autoplay: Boolean) {
        val context = MusicStorage.applicationContext ?: run {
            listener?.onError("Audio isn't ready yet.")
            return
        }
        release()
        prepared = false
        wantPlay = autoplay
        pendingSeek = startPositionMs
        val player = MediaPlayer()
        media = player
        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build(),
        )
        player.setOnPreparedListener {
            prepared = true
            applyRate()
            if (pendingSeek > 0) it.seekTo(pendingSeek.toInt())
            if (wantPlay) it.start()
        }
        player.setOnCompletionListener { listener?.onEnded() }
        player.setOnErrorListener { _, what, extra ->
            listener?.onError("Playback failed ($what/$extra).")
            true
        }
        runCatching {
            player.setDataSource(context, Uri.parse(url), headers)
            player.prepareAsync()
        }.onFailure { listener?.onError(it.message ?: "Playback failed.") }
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    actual fun play() {
        wantPlay = true
        if (prepared) media?.start()
    }

    actual fun pause() {
        wantPlay = false
        if (prepared) media?.pause()
    }

    actual fun seekTo(positionMs: Long) {
        if (prepared) media?.seekTo(positionMs.toInt()) else pendingSeek = positionMs
    }

    actual fun setRate(rate: Float) {
        this.rate = rate
        applyRate()
    }

    private fun applyRate() {
        val player = media ?: return
        if (!prepared) return
        val wasPlaying = player.isPlaying
        runCatching { player.playbackParams = player.playbackParams.setSpeed(rate) }
        if (!wasPlaying) runCatching { player.pause() }
    }

    actual fun stop() {
        release()
    }

    actual fun setNowPlaying(info: NowPlayingInfo?, positionMs: Long, isPlaying: Boolean) = Unit

    private fun release() {
        handler.removeCallbacks(ticker)
        media?.let { runCatching { it.reset(); it.release() } }
        media = null
        prepared = false
    }
}
