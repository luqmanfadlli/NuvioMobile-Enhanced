package com.nuvio.app.features.sports.compat

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter
import platform.darwin.DISPATCH_TIME_NOW
import platform.darwin.dispatch_semaphore_create
import platform.darwin.dispatch_semaphore_signal
import platform.darwin.dispatch_semaphore_wait
import platform.darwin.dispatch_time

/**
 * iOS cannot wake the app at a set time, so reminders are local notifications scheduled ahead with their text decided at
 * scheduling time (see [scheduleNotificationAt]). Tapping one opens the app through the same `deeplink` user-info key
 * Nuvio's notification delegate already routes to `handleAppUrl`. Live-score alerts that need the app running to read
 * scores are not available when the app is closed.
 */
@OptIn(ExperimentalForeignApi::class)
internal actual object SportsAlarmPlatform {
    private val center get() = UNUserNotificationCenter.currentNotificationCenter()
    @kotlin.concurrent.Volatile private var askedForPermission = false

    // The alarm API cannot carry text, so nothing is scheduled here: see scheduleNotificationAt.
    actual fun scheduleAlarm(id: String, atMs: Long) {}

    actual fun cancelAlarm(id: String) {
        center.removePendingNotificationRequestsWithIdentifiers(listOf(id))
    }

    actual fun scheduleNotificationAt(id: String, atMs: Long, title: String, text: String, openUrl: String?): Boolean {
        val seconds = (atMs - NSDate().timeIntervalSince1970 * 1000.0) / 1000.0
        if (seconds < 1.0) return false
        requestPermissionOnce()
        center.addNotificationRequest(request(id, title, text, openUrl, UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(seconds, repeats = false)), withCompletionHandler = null)
        return true
    }

    actual fun canScheduleExact(): Boolean = true
    actual fun requestExactAlarms() {}

    actual fun notificationsAllowed(): Boolean {
        var allowed = false
        val done = dispatch_semaphore_create(0)
        center.getNotificationSettingsWithCompletionHandler { settings ->
            val status = settings?.authorizationStatus
            allowed = status == UNAuthorizationStatusAuthorized || status == UNAuthorizationStatusProvisional
            dispatch_semaphore_signal(done)
        }
        dispatch_semaphore_wait(done, dispatch_time(DISPATCH_TIME_NOW, 2_000_000_000L))
        return allowed
    }

    /** A notification shown straight away (a live-score alert while the app is open). */
    actual fun notifyReminder(id: String, title: String, text: String, openUrl: String?, important: Boolean) {
        center.addNotificationRequest(request(id, title, text, openUrl, UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(1.0, repeats = false)), withCompletionHandler = null)
    }

    private fun requestPermissionOnce() {
        if (askedForPermission) return
        askedForPermission = true
        center.requestAuthorizationWithOptions(UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge) { _, _ -> }
    }

    private fun request(id: String, title: String, text: String, openUrl: String?, trigger: UNTimeIntervalNotificationTrigger): UNNotificationRequest {
        val content = UNMutableNotificationContent().apply {
            setTitle(title)
            setBody(text)
            setSound(UNNotificationSound.defaultSound)
            if (openUrl != null) setUserInfo(mapOf("deeplink" to openUrl))
        }
        return UNNotificationRequest.requestWithIdentifier(id, content, trigger)
    }
}
