package com.nuvio.app.features.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.PlatformBackHandler
import com.nuvio.app.core.ui.nuvio
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun PlayerDiscTitlesPanel(
    visible: Boolean,
    titles: List<PlayerDiscTitle>,
    activeIndex: Int,
    onTitleSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(visible) {
        if (visible) listState.scrollToItem((activeIndex - 1).coerceAtLeast(0))
    }

    val tokens = MaterialTheme.nuvio
    val backgroundInteraction = remember { MutableInteractionSource() }
    val panelInteraction = remember { MutableInteractionSource() }

    PlatformBackHandler(enabled = visible, onBack = onDismiss)

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(200)),
        exit = fadeOut(tween(160)),
        modifier = modifier,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.34f))
                .clickable(
                    interactionSource = backgroundInteraction,
                    indication = null,
                    onClick = onDismiss,
                ),
        ) {
            val panelWidth = if (maxWidth < 600.dp) maxWidth * 0.86f else maxWidth * 0.42f
            val panelHeight = maxHeight * 0.74f
            val shape = RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp)

            AnimatedVisibility(
                visible = visible,
                enter = slideInHorizontally(tween(250)) { it },
                exit = slideOutHorizontally(tween(200)) { it },
                modifier = Modifier.align(Alignment.CenterEnd),
            ) {
                Column(
                    modifier = Modifier
                        .width(panelWidth)
                        .height(panelHeight)
                        .playerLiquidGlass(
                            isControl = false,
                            shape = shape,
                            blurRadius = 24.dp,
                            refractionHeight = 10.dp,
                            refractionAmount = 18.dp,
                            tint = PlayerGlassTint,
                            fallback = tokens.colors.surfaceElevated,
                            backdropOpacity = 1f,
                        )
                        .clickable(
                            interactionSource = panelInteraction,
                            indication = null,
                            onClick = {},
                        )
                        .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.End))
                        .padding(16.dp),
                ) {
                    PlayerPanelHeader(
                        title = stringResource(Res.string.player_disc_titles_count, titles.size),
                    ) {
                        PlayerDialogButton(
                            label = stringResource(Res.string.action_close),
                            onClick = onDismiss,
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxHeight(),
                    ) {
                        itemsIndexed(titles, key = { _, title -> title.name }) { index, title ->
                            DiscTitleRow(
                                title = title,
                                isCurrent = index == activeIndex,
                                onClick = { onTitleSelected(index) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscTitleRow(
    title: PlayerDiscTitle,
    isCurrent: Boolean,
    onClick: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val accent = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(12.dp)
    val length = formatPlaybackTime(title.durationMs)
    val kind = stringResource(
        if (title.isFeature) Res.string.player_disc_title_feature else Res.string.player_disc_title_extra,
        length,
    )
    val clips = stringResource(
        if (title.clipCount == 1) Res.string.player_disc_title_clip else Res.string.player_disc_title_clips,
        title.clipCount,
    )
    val audio = title.audioLanguages.takeIf { it.isNotEmpty() }
        ?.let { stringResource(Res.string.player_disc_title_audio, it.joinToString(", ") { lang -> lang.uppercase() }) }
    val subtitles = title.subtitleLanguages.takeIf { it.isNotEmpty() }
        ?.let { stringResource(Res.string.player_disc_title_subtitles, it.joinToString(", ") { lang -> lang.uppercase() }) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.White.copy(alpha = 0.08f))
            .then(if (isCurrent) Modifier.border(2.dp, tokens.colors.focusRing, shape) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(modifier = Modifier.width(28.dp), contentAlignment = Alignment.Center) {
            if (isCurrent) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title.name.substringBeforeLast('.'),
                style = MaterialTheme.typography.titleMedium,
                color = if (isCurrent) accent else tokens.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "$kind · $clips",
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            listOfNotNull(audio, subtitles).forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
