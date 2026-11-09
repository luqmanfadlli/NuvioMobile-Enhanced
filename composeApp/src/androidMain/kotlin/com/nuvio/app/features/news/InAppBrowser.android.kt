package com.nuvio.app.features.news

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
internal actual fun rememberInAppBrowser(): (url: String) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { url ->
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }
}
