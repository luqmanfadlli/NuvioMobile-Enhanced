package com.nuvio.app.features.downloads

import androidx.compose.runtime.Composable
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.downloads_episode_label
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun localizedEpisodeLabel(season: Int, episode: Int, title: String): String =
    stringResource(Res.string.downloads_episode_label, season, episode, title)
