package com.nuvio.app.features.player

import com.nuvio.app.core.logging.InAppLogger
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val AutoSubtitleLogTag = "AutoSubtitle"

private fun PlayerScreenRuntime.hasRealSubtitleSelection(): Boolean =
    (selectedSubtitleIndex != -1 || selectedAddonSubtitleId != null) && !isAutoSubtitleShowing

private fun PlayerScreenRuntime.pickAutoShowSubtitleTrackIndex(): Int? {
    if (subtitleTracks.isEmpty()) return null

    val preferredLanguage = normalizeLanguageCode(playerSettingsUiState.preferredSubtitleLanguage)
    val targets = if (
        preferredLanguage == SubtitleLanguageOption.NONE || preferredLanguage == SubtitleLanguageOption.FORCED
    ) {
        emptyList()
    } else {
        resolvePreferredSubtitleLanguageTargets(
            preferredSubtitleLanguage = playerSettingsUiState.preferredSubtitleLanguage,
            secondaryPreferredSubtitleLanguage = playerSettingsUiState.secondaryPreferredSubtitleLanguage,
            deviceLanguages = DeviceLanguagePreferences.preferredLanguageCodes(),
        )
    }
    targets.forEach { target ->
        subtitleTracks.firstOrNull { SubtitleLanguageMatching.matchesLanguageCode(it.language, target) }
            ?.let { return it.index }
    }
    return (subtitleTracks.firstOrNull { it.isSelected } ?: subtitleTracks.first()).index
}

private fun PlayerScreenRuntime.activateAutoSubtitleIfNeeded() {
    if (isAutoSubtitleShowing) {
        InAppLogger.debug(AutoSubtitleLogTag, "activate: skipped, already showing")
        return
    }
    if (hasRealSubtitleSelection()) {
        InAppLogger.debug(AutoSubtitleLogTag, "activate: skipped, real selection already active")
        return
    }
    val index = pickAutoShowSubtitleTrackIndex()
    if (index == null) {
        InAppLogger.debug(AutoSubtitleLogTag, "activate: no track to show (tracks=${subtitleTracks.size})")
        return
    }
    playerController?.selectSubtitleTrack(index)
    selectedSubtitleIndex = index
    isAutoSubtitleShowing = true
    InAppLogger.debug(AutoSubtitleLogTag, "activate: turned on index=$index")
}

private fun PlayerScreenRuntime.deactivateAutoSubtitleIfNeeded() {
    if (!isAutoSubtitleShowing) return
    if (autoSubtitleRewindWatermarkMs != null || isAutoSubtitleMuteActive) {
        InAppLogger.debug(
            AutoSubtitleLogTag,
            "deactivate: skipped, still needed (watermark=$autoSubtitleRewindWatermarkMs, mute=$isAutoSubtitleMuteActive)",
        )
        return
    }
    playerController?.selectSubtitleTrack(-1)
    selectedSubtitleIndex = -1
    isAutoSubtitleShowing = false
    InAppLogger.debug(AutoSubtitleLogTag, "deactivate: turned off")
}

internal fun PlayerScreenRuntime.notifyRewindOccurred(fromPositionMs: Long) {
    InAppLogger.debug(AutoSubtitleLogTag, "notifyRewindOccurred: fromPositionMs=$fromPositionMs")
    if (!playerSettingsUiState.autoShowSubtitlesOnRewindEnabled) {
        InAppLogger.debug(AutoSubtitleLogTag, "notifyRewindOccurred: skipped, setting disabled")
        return
    }
    if (hasRealSubtitleSelection()) {
        InAppLogger.debug(AutoSubtitleLogTag, "notifyRewindOccurred: skipped, real selection active")
        return
    }
    val safeFrom = fromPositionMs.coerceAtLeast(0L)
    autoSubtitleRewindWatermarkMs = maxOf(autoSubtitleRewindWatermarkMs ?: safeFrom, safeFrom)
    InAppLogger.debug(AutoSubtitleLogTag, "notifyRewindOccurred: watermark now $autoSubtitleRewindWatermarkMs")
    activateAutoSubtitleIfNeeded()
}

internal fun PlayerScreenRuntime.checkAutoSubtitleRewindWatermark(currentPositionMs: Long) {
    val watermark = autoSubtitleRewindWatermarkMs ?: return
    if (currentPositionMs >= watermark) {
        InAppLogger.debug(
            AutoSubtitleLogTag,
            "checkAutoSubtitleRewindWatermark: reached (current=$currentPositionMs, watermark=$watermark)",
        )
        autoSubtitleRewindWatermarkMs = null
        deactivateAutoSubtitleIfNeeded()
    }
}

internal fun PlayerScreenRuntime.notifyVolumeLevelForAutoSubtitle(level: PlayerAudioLevel) {
    if (!playerSettingsUiState.autoShowSubtitlesOnMuteEnabled) {
        wasAutoSubtitleVolumeMuted = level.isMuted
        return
    }
    if (level.isMuted == wasAutoSubtitleVolumeMuted) return
    wasAutoSubtitleVolumeMuted = level.isMuted
    autoSubtitleMuteActivationJob?.cancel()
    if (level.isMuted && hasRealSubtitleSelection()) return
    isAutoSubtitleMuteActive = level.isMuted
    autoSubtitleMuteActivationJob = scope.launch {
        delay(16)
        if (isAutoSubtitleMuteActive) activateAutoSubtitleIfNeeded() else deactivateAutoSubtitleIfNeeded()
    }
}

internal fun PlayerScreenRuntime.clearAutoSubtitleState() {
    autoSubtitleMuteActivationJob?.cancel()
    autoSubtitleMuteActivationJob = null
    isAutoSubtitleShowing = false
    autoSubtitleRewindWatermarkMs = null
    isAutoSubtitleMuteActive = false
}
