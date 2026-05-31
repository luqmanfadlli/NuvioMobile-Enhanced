package com.nuvio.app.features.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContent
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.NuvioToastController
import com.nuvio.app.features.debrid.DebridSettingsRepository
import com.nuvio.app.features.debrid.DirectDebridPlayableResult
import com.nuvio.app.features.debrid.DirectDebridPlaybackResolver
import com.nuvio.app.features.debrid.toastMessage
import com.nuvio.app.features.addons.AddonRepository
import com.nuvio.app.features.addons.AddonResource
import com.nuvio.app.features.addons.ManagedAddon
import com.nuvio.app.features.addons.enabledAddons
import com.nuvio.app.features.addons.httpGetTextWithHeaders
import com.nuvio.app.features.details.MetaDetailsRepository
import com.nuvio.app.features.details.MetaScreenSettingsRepository
import com.nuvio.app.features.details.MetaVideo
import com.nuvio.app.features.downloads.DownloadItem
import com.nuvio.app.features.downloads.DownloadsRepository
import com.nuvio.app.features.player.skip.NextEpisodeCard
import com.nuvio.app.features.player.skip.NextEpisodeInfo
import com.nuvio.app.features.player.skip.PlayerNextEpisodeRules
import com.nuvio.app.features.player.skip.SkipIntroButton
import com.nuvio.app.features.player.skip.SkipIntroRepository
import com.nuvio.app.features.player.skip.SkipInterval
import com.nuvio.app.features.streams.BingeGroupCacheRepository
import com.nuvio.app.features.streams.StreamAutoPlayMode
import com.nuvio.app.features.streams.StreamAutoPlaySelector
import com.nuvio.app.features.streams.StreamAutoPlaySource
import com.nuvio.app.features.streams.StreamItem
import com.nuvio.app.features.streams.StreamLinkCacheRepository
import com.nuvio.app.features.streams.StreamsUiState
import com.nuvio.app.features.tmdb.TmdbService
import com.nuvio.app.features.trakt.TraktScrobbleItem
import com.nuvio.app.features.trakt.TraktScrobbleRepository
import com.nuvio.app.features.watched.WatchedRepository
import com.nuvio.app.features.watchprogress.WatchProgressClock
import com.nuvio.app.features.watchprogress.WatchProgressPlaybackSession
import com.nuvio.app.features.watchprogress.WatchProgressRepository
import com.nuvio.app.features.watchprogress.buildPlaybackVideoId
import com.nuvio.app.isIos
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.math.roundToInt

private const val PlaybackProgressPersistIntervalMs = 60_000L
private const val PlayerDoubleTapSeekStepMs = 10_000L
private const val PlayerDoubleTapSeekResetDelayMs = 800L
private const val PlayerLockedOverlayDurationMs = 2_000L
private const val PlayerLeftGestureBoundary = 0.4f
private const val PlayerRightGestureBoundary = 0.6f
private const val PlayerVerticalGestureSensitivity = 1f
private const val PlayerSeekProgressSyncDebounceMs = 700L
/** Hard ceiling for next-episode stream search to prevent hanging forever. */
private const val NEXT_EPISODE_HARD_TIMEOUT_MS = 120_000L
private val PlayerSliderOverlayGap = 12.dp
private val PlayerTimeRowHeight = 36.dp
private val PlayerActionRowHeight = 50.dp

private fun sliderOverlayBottomPadding(metrics: PlayerLayoutMetrics) =
    metrics.sliderBottomOffset +
        metrics.sliderTouchHeight +
        PlayerTimeRowHeight +
        PlayerActionRowHeight +
        PlayerSliderOverlayGap

private enum class PlayerSideGesture {
    Brightness,
    Volume,
}

private enum class PlayerSeekDirection {
    Backward,
    Forward,
}

private enum class PlayerGestureMode {
    HorizontalSeek,
    Brightness,
    Volume,
}

private data class PlayerAccumulatedSeekState(
    val direction: PlayerSeekDirection,
    val baselinePositionMs: Long,
    val amountMs: Long,
)

@Composable
fun PlayerScreen(
    title: String,
    sourceUrl: String,
    sourceAudioUrl: String? = null,
    sourceHeaders: Map<String, String> = emptyMap(),
    sourceResponseHeaders: Map<String, String> = emptyMap(),
    providerName: String,
    streamTitle: String,
    streamSubtitle: String?,
    initialBingeGroup: String? = null,
    pauseDescription: String? = null,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    logo: String? = null,
    poster: String? = null,
    background: String? = null,
    seasonNumber: Int? = null,
    episodeNumber: Int? = null,
    episodeTitle: String? = null,
    episodeThumbnail: String? = null,
    contentType: String? = null,
    videoId: String? = null,
    parentMetaId: String,
    parentMetaType: String,
    providerAddonId: String? = null,
    initialPositionMs: Long = 0L,
    initialProgressFraction: Float? = null,
    torrentSessionId: String? = null,
) {
    // This is a placeholder - the full implementation will be restored from the repository
}
