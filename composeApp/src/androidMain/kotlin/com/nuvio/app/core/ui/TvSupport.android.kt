package com.nuvio.app.core.ui

import android.app.UiModeManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import java.io.File

@Composable
internal actual fun rememberIsTelevision(): Boolean {
    val context = LocalContext.current
    return remember(context) { context.isTelevision() }
}

@Composable
internal actual fun rememberRequestPlatformFocus(): () -> Unit {
    val view = LocalView.current
    return remember(view) { { if (!view.hasFocus()) view.requestFocus() } }
}

private fun Context.isTelevision(): Boolean {
    val modeManager = getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
    if (modeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) return true
    val pm = packageManager
    if (pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) || pm.hasSystemFeature("amazon.hardware.fire_tv")) return true
    // Debug builds on a phone or emulator: create files/force_tv to try the TV layout and remote focus.
    val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    return debuggable && File(filesDir, "force_tv").exists()
}
