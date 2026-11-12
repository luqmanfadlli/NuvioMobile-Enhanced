package com.nuvio.app.features.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import com.nuvio.app.core.i18n.localizedSeasonEpisodeCode
import com.nuvio.app.features.discord.DiscordPlaybackInfo
import com.nuvio.app.features.discord.DiscordRpcConfig
import com.nuvio.app.features.discord.DiscordRpcRepository
import kotlinx.coroutines.flow.distinctUntilChanged

private val imdbIdPattern = Regex("tt\\d{5,}")

@Composable
internal fun PlayerScreenRuntime.BindDiscordRpcEffects() {
    val seasonNumber = activeSeasonNumber
    val episodeNumber = activeEpisodeNumber
    val episodeTitle = activeEpisodeTitle
    val liveTv = isLiveTvPlayback
    val subtitle = remember(seasonNumber, episodeNumber, episodeTitle, liveTv) {
        buildDiscordSubtitle(
            isLiveTv = liveTv,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
            episodeTitle = episodeTitle,
        )
    }
    val currentSubtitle = rememberUpdatedState(subtitle)

    LaunchedEffect(Unit) {
        snapshotFlow { buildDiscordPlaybackInfo(currentSubtitle.value) }
            .distinctUntilChanged()
            .collect { info -> DiscordRpcRepository.updatePlayback(info) }
    }

    DisposableEffect(Unit) {
        onDispose {
            DiscordRpcRepository.clearPlayback()
        }
    }
}

private fun buildDiscordSubtitle(
    isLiveTv: Boolean,
    seasonNumber: Int?,
    episodeNumber: Int?,
    episodeTitle: String?,
): String? {
    if (isLiveTv || seasonNumber == null || episodeNumber == null) return null
    val parts = buildList {
        localizedSeasonEpisodeCode(seasonNumber, episodeNumber)?.let { add(it) }
        episodeTitle?.takeIf { it.isNotBlank() }?.let { add(it) }
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" - ")
}

private fun PlayerScreenRuntime.buildDiscordPlaybackInfo(subtitle: String?): DiscordPlaybackInfo {
    val snapshot = playbackSnapshot
    val displayTitle = if (isLiveTvPlayback) {
        activeStreamTitle.ifBlank { title }
    } else {
        title.ifBlank { activeStreamTitle }
    }
    val imdbId = if (isLiveTvPlayback) {
        null
    } else {
        imdbIdPattern.find(parentMetaId)?.value
            ?: activeVideoId?.let { imdbIdPattern.find(it)?.value }
            ?: listOfNotNull(metaUiState.meta, playerMeta)
                .filter { it.id == parentMetaId }
                .firstNotNullOfOrNull { meta -> meta.imdbId?.let { imdbIdPattern.find(it)?.value } }
    }
    val artworkCandidates = if (isLiveTvPlayback) {
        listOf(activeLogo, poster, background)
    } else {
        listOf(poster, background)
    }.mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
    val artwork = artworkCandidates.firstOrNull { it.length <= DiscordRpcConfig.MAX_ARTWORK_URL_LENGTH && '?' !in it }
        ?: imdbId?.let(DiscordRpcConfig::metahubPosterUrl)
        ?: artworkCandidates.firstOrNull { it.length <= DiscordRpcConfig.MAX_ARTWORK_URL_LENGTH }

    return DiscordPlaybackInfo(
        title = displayTitle,
        subtitle = subtitle,
        artworkUrl = artwork,
        isPlaying = snapshot.isPlaying,
        isLoading = snapshot.isLoading,
        positionMs = snapshot.positionMs,
        durationMs = snapshot.durationMs,
        isLive = isLiveTvPlayback,
        imdbId = imdbId,
    )
}
