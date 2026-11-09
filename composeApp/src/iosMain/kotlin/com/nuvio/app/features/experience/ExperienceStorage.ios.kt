package com.nuvio.app.features.experience

import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSUserDefaults

actual object ExperienceStorage {
    // The Swift tab bar reads this same key and listens for the notification.
    private const val modeKey = "BannerExperienceMode"
    private const val didChangeNotification = "BannerExperienceModeDidChange"

    actual fun load(): String? = NSUserDefaults.standardUserDefaults.stringForKey(modeKey)

    actual fun save(key: String) {
        NSUserDefaults.standardUserDefaults.setObject(key, forKey = modeKey)
        NSNotificationCenter.defaultCenter.postNotificationName(didChangeNotification, null)
    }
}
