package com.nuvio.app.features.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.PlatformBackHandler
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.profiles.ProfileRepository
import com.nuvio.app.navigation.PlayerRoute
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.mini_player_close
import nuvio.composeapp.generated.resources.mini_player_expand
import nuvio.composeapp.generated.resources.mini_player_pause
import nuvio.composeapp.generated.resources.mini_player_play
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

val LocalPlayerMiniPresentation = compositionLocalOf<Boolean?> { null }

object MiniPlayerController {
    private var ownedRoute by mutableStateOf<PlayerRoute?>(null)
    private var minimized by mutableStateOf(false)
    internal var hostedRuntime by mutableStateOf<PlayerScreenRuntime?>(null)
        private set
    private val hostedLaunchIds = mutableSetOf<Long>()
    private val placeholderLaunchIds = mutableSetOf<Long>()

    val isActive: Boolean
        get() = minimized && ownedRoute != null

    fun hostedRoute(topPlayerRoute: PlayerRoute?, enabled: Boolean): PlayerRoute? {
        val owned = ownedRoute
        if (topPlayerRoute != null) {
            return if (topPlayerRoute == owned || enabled) topPlayerRoute else null
        }
        return if (minimized) owned else null
    }

    fun isHostedRoute(route: PlayerRoute, topPlayerRoute: PlayerRoute?, enabled: Boolean): Boolean =
        route.launchId in hostedLaunchIds || hostedRoute(topPlayerRoute, enabled) == route

    fun close() {
        minimized = false
    }

    internal fun commit(topPlayerRoute: PlayerRoute?, hostedRoute: PlayerRoute?) {
        if (hostedRoute != null) hostedLaunchIds += hostedRoute.launchId
        val previousOwned = ownedRoute
        if (previousOwned != hostedRoute) {
            ownedRoute = hostedRoute
            if (previousOwned != null && previousOwned.launchId !in placeholderLaunchIds) {
                hostedLaunchIds -= previousOwned.launchId
            }
        }
        if ((hostedRoute == null || topPlayerRoute != null) && minimized) minimized = false
    }

    internal fun onPlaceholderEntered(route: PlayerRoute) {
        placeholderLaunchIds += route.launchId
    }

    internal fun onPlaceholderDisposed(route: PlayerRoute) {
        placeholderLaunchIds -= route.launchId
        if (ownedRoute != route) hostedLaunchIds -= route.launchId
    }

    internal fun requestMinimize(): Boolean {
        if (ownedRoute == null) return false
        minimized = true
        return true
    }

    internal fun attach(runtime: PlayerScreenRuntime) {
        hostedRuntime = runtime
    }

    internal fun detach(runtime: PlayerScreenRuntime) {
        if (hostedRuntime === runtime) hostedRuntime = null
    }
}

internal fun PlayerScreenRuntime.handOffToMiniPlayer(): Boolean {
    if (MiniPlayerController.hostedRuntime !== this || isMiniPresentation) return false
    return MiniPlayerController.requestMinimize()
}

@Composable
internal fun PlayerScreenRuntime.BindMiniPlayerEffects(presentation: Boolean?) {
    val runtime = this
    if (presentation != null) {
        DisposableEffect(runtime) {
            MiniPlayerController.attach(runtime)
            onDispose { MiniPlayerController.detach(runtime) }
        }
    }
    PlatformBackHandler(enabled = presentation == false) {
        if (!handOffToMiniPlayer()) flushWatchProgress()
        args.onBack()
    }
}

@Composable
fun HostedPlayerRoutePlaceholder(route: PlayerRoute) {
    DisposableEffect(route) {
        MiniPlayerController.onPlaceholderEntered(route)
        onDispose { MiniPlayerController.onPlaceholderDisposed(route) }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    )
}

private val MiniPlayerShape = RoundedCornerShape(14.dp)
private val MiniPlayerVideoShape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)
private val MiniPlayerInfoHeight = 52.dp
private val MiniPlayerProgressHeight = 2.dp
private val MiniPlayerEdgeMargin = 12.dp
private val MiniPlayerBottomClearance = 96.dp

@Composable
fun MiniPlayerHost(
    hostedRoute: PlayerRoute?,
    topPlayerRoute: PlayerRoute?,
    onExpand: (PlayerRoute) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (PlayerRoute) -> Unit,
) {
    SideEffect { MiniPlayerController.commit(topPlayerRoute, hostedRoute) }
    val route = hostedRoute ?: return
    val mini = topPlayerRoute == null
    val profileState by ProfileRepository.state.collectAsStateWithLifecycle()
    LaunchedEffect(route, mini, profileState) {
        val runtime = MiniPlayerController.hostedRuntime ?: return@LaunchedEffect
        if (mini && runtime.profileId != ProfileRepository.activeProfileId) {
            MiniPlayerController.close()
        }
    }
    key(route.launchId) {
        MiniPlayerFrame(
            route = route,
            mini = mini,
            onExpand = onExpand,
            modifier = modifier,
            content = content,
        )
    }
}

@Composable
private fun MiniPlayerFrame(
    route: PlayerRoute,
    mini: Boolean,
    onExpand: (PlayerRoute) -> Unit,
    modifier: Modifier,
    content: @Composable (PlayerRoute) -> Unit,
) {
    val colors = MaterialTheme.nuvio.colors
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val runtime = MiniPlayerController.hostedRuntime

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val containerWidthPx = constraints.maxWidth.toFloat()
        val containerHeightPx = constraints.maxHeight.toFloat()
        val cardWidth = if (maxWidth >= 600.dp) 320.dp else maxWidth * 0.58f
        val videoHeight = cardWidth * 9f / 16f
        val cardHeight = videoHeight + MiniPlayerProgressHeight + MiniPlayerInfoHeight
        val cardWidthPx = with(density) { cardWidth.toPx() }
        val cardHeightPx = with(density) { cardHeight.toPx() }
        val videoHeightPx = with(density) { videoHeight.toPx() }
        val marginPx = with(density) { MiniPlayerEdgeMargin.toPx() }
        val topInsetPx = WindowInsets.safeDrawing.getTop(density).toFloat()
        val bottomInsetPx = WindowInsets.safeDrawing.getBottom(density).toFloat()
        val bottomClearancePx = with(density) { MiniPlayerBottomClearance.toPx() }
        val minX = marginPx
        val maxX = (containerWidthPx - cardWidthPx - marginPx).coerceAtLeast(minX)
        val minY = topInsetPx + marginPx
        val maxY = (containerHeightPx - cardHeightPx - bottomInsetPx - bottomClearancePx).coerceAtLeast(minY)
        val offsetX = remember { Animatable(maxX) }
        val offsetY = remember { Animatable(maxY) }
        var dockedToEnd by remember { mutableStateOf(true) }

        LaunchedEffect(minX, maxX, minY, maxY) {
            offsetX.updateBounds(minX, maxX)
            offsetY.updateBounds(minY, maxY)
            offsetX.snapTo(if (dockedToEnd) maxX else minX)
            offsetY.snapTo(offsetY.value.coerceIn(minY, maxY))
        }

        val dragModifier = Modifier.pointerInput(minX, maxX, minY, maxY) {
            val dock: () -> Unit = {
                dockedToEnd = offsetX.value + cardWidthPx / 2f > containerWidthPx / 2f
                scope.launch {
                    offsetX.animateTo(if (dockedToEnd) maxX else minX, tween(durationMillis = 220))
                }
            }
            detectDragGestures(
                onDragEnd = dock,
                onDragCancel = dock,
            ) { change, dragAmount ->
                change.consume()
                scope.launch {
                    offsetX.snapTo(offsetX.value + dragAmount.x)
                    offsetY.snapTo(offsetY.value + dragAmount.y)
                }
            }
        }
        val cardOffset: () -> IntOffset = {
            IntOffset(offsetX.value.roundToInt(), offsetY.value.roundToInt())
        }

        if (mini) {
            Box(
                modifier = Modifier
                    .offset { cardOffset() }
                    .size(width = cardWidth, height = cardHeight)
                    .shadow(16.dp, MiniPlayerShape)
                    .clip(MiniPlayerShape)
                    .background(colors.surfaceElevated),
            )
        }

        Box(
            modifier = if (mini) {
                Modifier
                    .offset { cardOffset() }
                    .size(width = cardWidth, height = videoHeight)
                    .clip(MiniPlayerVideoShape)
            } else {
                Modifier.fillMaxSize()
            },
        ) {
            CompositionLocalProvider(LocalPlayerMiniPresentation provides mini) {
                content(route)
            }
            if (mini) {
                if (runtime != null && runtime.playbackSnapshot.isLoading && runtime.errorMessage == null) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(26.dp),
                        color = Color.White,
                        strokeWidth = 2.dp,
                    )
                }
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .then(dragModifier)
                        .clickable(onClickLabel = stringResource(Res.string.mini_player_expand)) {
                            onExpand(route)
                        },
                )
            }
        }

        if (mini) {
            val snapshot = runtime?.playbackSnapshot
            val durationMs = snapshot?.durationMs ?: 0L
            val progressFraction = if (snapshot != null && durationMs > 0L) {
                (snapshot.positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }
            val title: String
            val subtitle: String?
            if (runtime == null) {
                title = route.title
                subtitle = null
            } else {
                val isLive = runtime.isLiveTvPlayback
                val seasonNumber = runtime.activeSeasonNumber
                val episodeNumber = runtime.activeEpisodeNumber
                title = if (isLive) runtime.activeStreamTitle else runtime.title
                subtitle = if (seasonNumber != null && episodeNumber != null) {
                    listOfNotNull(
                        "S$seasonNumber E$episodeNumber",
                        runtime.activeEpisodeTitle?.takeIf { it.isNotBlank() },
                    ).joinToString(" · ")
                } else if (isLive) {
                    runtime.activeStreamSubtitle?.takeIf { it.isNotBlank() }
                } else {
                    runtime.activeStreamTitle.takeIf { it.isNotBlank() }
                }
            }
            val playing = snapshot != null &&
                (snapshot.isPlaying || (runtime?.shouldPlay == true && snapshot.isLoading))
            Column(
                modifier = Modifier
                    .offset { cardOffset() + IntOffset(0, videoHeightPx.roundToInt()) }
                    .width(cardWidth)
                    .then(dragModifier),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(MiniPlayerProgressHeight)
                        .background(Color.White.copy(alpha = 0.12f)),
                ) {
                    if (durationMs > 0L) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progressFraction)
                                .fillMaxHeight()
                                .background(colors.accent),
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(MiniPlayerInfoHeight)
                        .padding(start = 12.dp, end = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onExpand(route) },
                    ) {
                        Text(
                            text = title,
                            color = colors.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (subtitle != null) {
                            Text(
                                text = subtitle,
                                color = colors.textMuted,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    IconButton(onClick = { runtime?.togglePlayback() }) {
                        Icon(
                            imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = stringResource(
                                if (playing) Res.string.mini_player_pause else Res.string.mini_player_play,
                            ),
                            tint = colors.textPrimary,
                        )
                    }
                    IconButton(onClick = { MiniPlayerController.close() }) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(Res.string.mini_player_close),
                            tint = colors.textPrimary,
                        )
                    }
                }
            }
        }
    }
}
