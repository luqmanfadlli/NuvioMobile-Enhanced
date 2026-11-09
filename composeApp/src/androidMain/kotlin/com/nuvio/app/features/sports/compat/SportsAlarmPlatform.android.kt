package com.nuvio.app.features.sports.compat

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.nuvio.app.features.settings.AppIconPlatform

internal actual object SportsAlarmPlatform {
    internal const val REMINDER_CHANNEL_ID = "sports_reminders"
    private var appContext: Context? = null

    /** The application context once [initialize] ran. */
    internal fun context(): Context? = appContext

    fun initialize(context: Context) {
        if (appContext == null) appContext = context.applicationContext
        ensureChannel()
    }

    private fun pending(context: Context, id: String): PendingIntent {
        val intent = Intent(context, SportsAlarmReceiver::class.java).setAction(SportsAlarmReceiver.ACTION_FIRE).putExtra(SportsAlarmReceiver.EXTRA_ID, id)
        return PendingIntent.getBroadcast(context, id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    actual fun scheduleAlarm(id: String, atMs: Long) {
        val context = appContext ?: return
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        runCatching {
            if (canScheduleExact()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pending(context, id))
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pending(context, id))
        }
    }

    actual fun cancelAlarm(id: String) {
        val context = appContext ?: return
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        runCatching { am.cancel(pending(context, id)) }
    }

    actual fun scheduleNotificationAt(id: String, atMs: Long, title: String, text: String, openUrl: String?): Boolean = false

    actual fun canScheduleExact(): Boolean {
        val context = appContext ?: return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
        return am.canScheduleExactAlarms()
    }

    actual fun requestExactAlarms() {
        val context = appContext ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                    .setData(Uri.parse("package:" + context.packageName))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    actual fun notificationsAllowed(): Boolean {
        val context = appContext ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun ensureChannel() {
        val context = appContext ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (manager.getNotificationChannel(REMINDER_CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(REMINDER_CHANNEL_ID, "Game reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Games you asked to be reminded about, and scores for teams you follow"
                    setShowBadge(true)
                }
            )
        }
    }

    private fun notificationId(id: String): Int = 7400 + (id.hashCode() and 0xffff)

    actual fun notifyReminder(id: String, title: String, text: String, openUrl: String?, important: Boolean) {
        val context = appContext ?: return
        if (!notificationsAllowed()) return
        ensureChannel()
        val launchIntent = Intent().apply {
            component = AppIconPlatform.currentLauncherComponent(context)
            action = if (openUrl != null) Intent.ACTION_VIEW else Intent.ACTION_MAIN
            if (openUrl != null) data = Uri.parse(openUrl)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentIntent = PendingIntent.getActivity(context, id.hashCode(), launchIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
            .setSmallIcon(com.nuvio.app.R.drawable.ic_notification_small)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setPriority(if (important) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(if (important) NotificationCompat.CATEGORY_REMINDER else NotificationCompat.CATEGORY_EVENT)
            .setDefaults(if (important) NotificationCompat.DEFAULT_ALL else 0)
            .setContentIntent(contentIntent)
            .apply { if (openUrl != null) addAction(0, "Open", contentIntent) }
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(notificationId(id), notification) }
    }
}
