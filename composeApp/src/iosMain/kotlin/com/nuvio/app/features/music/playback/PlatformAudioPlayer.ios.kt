@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nuvio.app.features.music.playback

import kotlinx.cinterop.useContents
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive
import platform.AVFoundation.AVAudioTimePitchAlgorithmTimeDomain
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerItemDidPlayToEndTimeNotification
import platform.AVFoundation.AVPlayerItemStatusFailed
import platform.AVFoundation.AVPlayerTimeControlStatusPaused
import platform.AVFoundation.AVPlayerTimeControlStatusPlaying
import platform.AVFoundation.AVPlayerTimeControlStatusWaitingToPlayAtSpecifiedRate
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.addPeriodicTimeObserverForInterval
import platform.AVFoundation.audioTimePitchAlgorithm
import platform.AVFoundation.currentItem
import platform.AVFoundation.currentTime
import platform.AVFoundation.defaultRate
import platform.AVFoundation.duration
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.rate
import platform.AVFoundation.removeTimeObserver
import platform.AVFoundation.replaceCurrentItemWithPlayerItem
import platform.AVFoundation.seekToTime
import platform.AVFoundation.timeControlStatus
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSNumber
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.MediaPlayer.MPChangePlaybackPositionCommandEvent
import platform.MediaPlayer.MPMediaItemPropertyAlbumTitle
import platform.MediaPlayer.MPMediaItemPropertyArtist
import platform.MediaPlayer.MPMediaItemPropertyPlaybackDuration
import platform.MediaPlayer.MPMediaItemPropertyTitle
import platform.MediaPlayer.MPNowPlayingInfoCenter
import platform.MediaPlayer.MPNowPlayingInfoPropertyElapsedPlaybackTime
import platform.MediaPlayer.MPNowPlayingInfoPropertyPlaybackRate
import platform.MediaPlayer.MPRemoteCommandCenter
import platform.MediaPlayer.MPRemoteCommandHandlerStatusSuccess
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

internal actual class PlatformAudioPlayer {
    actual var listener: PlatformAudioListener? = null

    private val player = AVPlayer()
    private var timeObserver: Any? = null
    private var endObserver: Any? = null
    private var remoteRegistered = false
    private var reportedError = false

    init {
        runCatching {
            AVAudioSession.sharedInstance().setCategory(AVAudioSessionCategoryPlayback, error = null)
            AVAudioSession.sharedInstance().setActive(true, error = null)
        }
        startObserving()
        registerRemoteCommands()
    }

    private fun startObserving() {
        timeObserver = player.addPeriodicTimeObserverForInterval(
            CMTimeMakeWithSeconds(0.25, 600),
            null,
        ) { _ ->
            val item = player.currentItem
            if (item != null && item.status == AVPlayerItemStatusFailed && !reportedError) {
                reportedError = true
                listener?.onError(item.error?.localizedDescription ?: "Playback failed.")
            }
            val status = player.timeControlStatus
            listener?.onState(
                isPlaying = status == AVPlayerTimeControlStatusPlaying,
                isBuffering = status == AVPlayerTimeControlStatusWaitingToPlayAtSpecifiedRate,
            )
            val positionMs = secondsToMs(CMTimeGetSeconds(player.currentTime()))
            val durationMs = item?.duration?.let { secondsToMs(CMTimeGetSeconds(it)) } ?: 0L
            listener?.onProgress(positionMs, durationMs)
        }
    }

    private fun secondsToMs(seconds: Double): Long =
        if (seconds.isNaN() || seconds.isInfinite() || seconds < 0) 0L else (seconds * 1000.0).toLong()

    actual fun load(url: String, headers: Map<String, String>, startPositionMs: Long, autoplay: Boolean) {
        val nsUrl = NSURL.URLWithString(url)
        if (nsUrl == null) {
            listener?.onError("The track address is invalid.")
            return
        }
        reportedError = false
        endObserver?.let { NSNotificationCenter.defaultCenter.removeObserver(it) }
        val options: Map<Any?, Any?>? =
            if (headers.isEmpty()) null else mapOf("AVURLAssetHTTPHeaderFieldsKey" to headers)
        val asset = AVURLAsset.URLAssetWithURL(nsUrl, options)
        val item = AVPlayerItem.playerItemWithAsset(asset)
        item.audioTimePitchAlgorithm = AVAudioTimePitchAlgorithmTimeDomain
        endObserver = NSNotificationCenter.defaultCenter.addObserverForName(
            name = AVPlayerItemDidPlayToEndTimeNotification,
            `object` = item,
            queue = NSOperationQueue.mainQueue,
        ) { _ -> listener?.onEnded() }
        player.replaceCurrentItemWithPlayerItem(item)
        if (startPositionMs > 0) seekTo(startPositionMs)
        if (autoplay) player.play()
    }

    actual fun play() { player.play() }

    actual fun pause() { player.pause() }

    actual fun setRate(rate: Float) {
        player.defaultRate = rate
        if (player.timeControlStatus != AVPlayerTimeControlStatusPaused) player.rate = rate
    }

    actual fun seekTo(positionMs: Long) {
        player.seekToTime(CMTimeMakeWithSeconds(positionMs / 1000.0, 600))
    }

    actual fun stop() {
        player.pause()
        player.replaceCurrentItemWithPlayerItem(null)
        MPNowPlayingInfoCenter.defaultCenter().nowPlayingInfo = null
    }

    actual fun setNowPlaying(info: NowPlayingInfo?, positionMs: Long, isPlaying: Boolean) {
        if (info == null) {
            MPNowPlayingInfoCenter.defaultCenter().nowPlayingInfo = null
            return
        }
        val dict = mutableMapOf<Any?, Any?>(
            MPMediaItemPropertyTitle to info.title,
            MPMediaItemPropertyArtist to info.artist,
            MPMediaItemPropertyPlaybackDuration to NSNumber(double = info.durationMs / 1000.0),
            MPNowPlayingInfoPropertyElapsedPlaybackTime to NSNumber(double = positionMs / 1000.0),
            MPNowPlayingInfoPropertyPlaybackRate to NSNumber(double = if (isPlaying) 1.0 else 0.0),
        )
        info.album?.let { dict[MPMediaItemPropertyAlbumTitle] = it }
        MPNowPlayingInfoCenter.defaultCenter().nowPlayingInfo = dict
    }

    private fun registerRemoteCommands() {
        if (remoteRegistered) return
        remoteRegistered = true
        val center = MPRemoteCommandCenter.sharedCommandCenter()
        fun send(command: RemoteCommand, position: Long = 0L) {
            dispatch_async(dispatch_get_main_queue()) { listener?.onRemoteCommand(command, position) }
        }
        center.playCommand.addTargetWithHandler { _ -> send(RemoteCommand.Play); MPRemoteCommandHandlerStatusSuccess }
        center.pauseCommand.addTargetWithHandler { _ -> send(RemoteCommand.Pause); MPRemoteCommandHandlerStatusSuccess }
        center.togglePlayPauseCommand.addTargetWithHandler { _ -> send(RemoteCommand.Toggle); MPRemoteCommandHandlerStatusSuccess }
        center.nextTrackCommand.addTargetWithHandler { _ -> send(RemoteCommand.Next); MPRemoteCommandHandlerStatusSuccess }
        center.previousTrackCommand.addTargetWithHandler { _ -> send(RemoteCommand.Previous); MPRemoteCommandHandlerStatusSuccess }
        center.changePlaybackPositionCommand.addTargetWithHandler { event ->
            val position = (event as? MPChangePlaybackPositionCommandEvent)?.positionTime ?: 0.0
            send(RemoteCommand.Seek, (position * 1000.0).toLong())
            MPRemoteCommandHandlerStatusSuccess
        }
    }
}
