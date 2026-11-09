package com.nuvio.app.features.news

import androidx.compose.runtime.Composable

/** Opens a web page inside the app (an in-app browser sheet) rather than sending you to another app. */
@Composable
internal expect fun rememberInAppBrowser(): (url: String) -> Unit
