package com.nuvio.app.features.sports

internal actual object SportsLivePlatform {
    actual val supported: Boolean = false
    actual fun show(title: String, text: String, openUrl: String?): Boolean = false
    actual fun stop() {}
    actual fun openNotificationSettings() {}
}
