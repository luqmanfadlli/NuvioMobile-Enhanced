package com.nuvio.app

import androidx.compose.runtime.Composable
import com.nuvio.app.features.downloads.DownloadItem
import com.nuvio.app.features.downloads.DownloadsScreen
import com.nuvio.app.navigation.DetailRoute
import com.nuvio.app.navigation.DownloadShowRoute
import com.nuvio.app.navigation.DownloadsRoute
import com.nuvio.app.navigation.DownloadsSettingsRoute
import com.nuvio.app.navigation.NuvioNavigator
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.compose_settings_root_downloads_title
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun DownloadsDestination(
    route: DownloadsRoute,
    navController: NuvioNavigator,
    useNativeNavigation: Boolean,
    onOpenDownload: (DownloadItem) -> Unit,
) {
    val onBack = rememberGuardedPopBackStack(navController, route)
    val downloadsSettingsTitle = stringResource(Res.string.compose_settings_root_downloads_title)
    DownloadsScreen(
        onBack = onBack,
        onOpenDownload = onOpenDownload,
        onNavigateToShow = if (useNativeNavigation) {
            { showId, title -> navController.navigate(DownloadShowRoute(showId, title)) }
        } else {
            null
        },
        onNavigateToMovie = { type, movieId, title ->
            navController.navigate(DetailRoute(type = type, id = movieId, title = title))
        },
        onOpenSettings = {
            navController.navigate(DownloadsSettingsRoute(downloadsSettingsTitle))
        },
    )
}

@Composable
internal fun DownloadShowDestination(
    route: DownloadShowRoute,
    navController: NuvioNavigator,
    onOpenDownload: (DownloadItem) -> Unit,
) {
    val onBack = rememberGuardedPopBackStack(navController, route)
    DownloadsScreen(
        onBack = onBack,
        onOpenDownload = onOpenDownload,
        initialShowId = route.showId,
        onBackFromShow = onBack,
    )
}
