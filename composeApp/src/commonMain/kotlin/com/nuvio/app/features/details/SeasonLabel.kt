package com.nuvio.app.features.details

import androidx.compose.runtime.Composable
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.episodes_season
import nuvio.composeapp.generated.resources.episodes_specials
import org.jetbrains.compose.resources.stringResource

/** "Specials" for season 0, otherwise "Season N". */
@Composable
internal fun seasonLabel(season: Int): String =
    if (season == 0) stringResource(Res.string.episodes_specials)
    else stringResource(Res.string.episodes_season, season)
