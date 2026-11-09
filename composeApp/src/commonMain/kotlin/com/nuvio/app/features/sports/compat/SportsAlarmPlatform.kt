package com.nuvio.app.features.sports.compat

/**
 * What game reminders need from the device: an alarm that wakes the app at kick-off time, and a notification. Android
 * implements both. iOS schedules local notifications instead (it cannot be woken at a set time).
 */
internal expect object SportsAlarmPlatform {
    /** Wakes the app at [atMs] and reports [id] to [LiveTvAlarms.fire] (exact when allowed). */
    fun scheduleAlarm(id: String, atMs: Long)
    fun cancelAlarm(id: String)
    /**
     * For platforms that cannot wake the app at a set time (iOS): schedules the notification itself, with its text decided now.
     * Returns false on platforms that use [scheduleAlarm] instead (Android), which build the text when the alarm fires.
     */
    fun scheduleNotificationAt(id: String, atMs: Long, title: String, text: String, openUrl: String?): Boolean
    /** Whether exact alarms are allowed (Android 12+ asks the person first). */
    fun canScheduleExact(): Boolean
    /** Opens the system page where exact alarms are allowed. */
    fun requestExactAlarms()
    fun notificationsAllowed(): Boolean
    /** A reminder notification; tapping it opens [openUrl] in the app, or the app itself when null. */
    fun notifyReminder(id: String, title: String, text: String, openUrl: String?, important: Boolean)
}
