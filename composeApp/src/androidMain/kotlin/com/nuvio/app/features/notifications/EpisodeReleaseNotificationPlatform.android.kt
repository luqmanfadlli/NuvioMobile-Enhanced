package com.nuvio.app.features.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.nuvio.app.core.network.NetworkErrorInterceptor
import com.nuvio.app.core.storage.ProfileScopedKey
import com.nuvio.app.features.settings.AppIconPlatform
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.runBlocking
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.getString
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

internal actual object EpisodeReleaseNotificationPlatform {
    private const val permissionRequestCode = 4607
    private const val platformPreferencesName = "nuvio_episode_release_notifications_platform"
    private const val legacyScheduledIdsKey = "scheduled_episode_release_ids"
    private const val scheduledRequestsKey = "scheduled_episode_release_requests"
    private const val deliveredIdsKey = "delivered_episode_release_ids"
    private const val workTag = "episode_release_notifications"
    private const val backstopWorkName = "episode_release_notifications_backstop"
    private const val backstopIntervalHours = 3L
    private const val alarmRequestCode = 4608
    private const val deliveredRetentionDays = 14L
    private const val backdropLoadTimeoutMs = 8_000L
    private const val exactAlarmPromptedKey = "exact_alarm_access_prompted"
    internal const val alarmAction = "com.nuvio.app.action.EPISODE_RELEASE_NOTIFICATION_ALARM"
    internal const val channelId = "episode_release_notifications"

    @Volatile
    private var appContext: Context? = null
    private val stateMutex = Mutex()
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val requestListSerializer = ListSerializer(EpisodeReleaseNotificationRequest.serializer())
    private var currentActivity: ComponentActivity? = null
    private var pendingPermissionContinuation: kotlin.coroutines.Continuation<Boolean>? = null
    private val httpClient by lazy {
        HttpClient(OkHttp) {
            install(HttpTimeout) {
                requestTimeoutMillis = 15_000
                connectTimeoutMillis = 15_000
                socketTimeoutMillis = 15_000
            }
            engine {
                preconfigured = com.nuvio.app.core.network.SharedOkHttpClient.base
                addInterceptor(NetworkErrorInterceptor())
            }
        }
    }

    fun initialize(context: Context) {
        appContext = context.applicationContext
        ensureNotificationChannel()
    }

    fun bindActivity(activity: ComponentActivity) {
        currentActivity = activity
    }

    fun unbindActivity(activity: ComponentActivity) {
        if (currentActivity === activity) {
            currentActivity = null
        }
    }

    fun handlePermissionRequestResult(
        requestCode: Int,
        grantResults: IntArray,
    ): Boolean {
        if (requestCode != permissionRequestCode) return false
        val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        pendingPermissionContinuation?.resume(granted)
        pendingPermissionContinuation = null
        return true
    }

    actual suspend fun notificationsAuthorized(): Boolean {
        val context = appContext ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionState = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            )
            if (permissionState != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    actual suspend fun requestAuthorization(): Boolean {
        val granted = requestNotificationPermission()
        if (granted) {
            promptExactAlarmAccessIfNeeded()
        }
        return granted
    }

    private suspend fun requestNotificationPermission(): Boolean {
        val context = appContext ?: return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            ensureNotificationChannel()
            return NotificationManagerCompat.from(context).areNotificationsEnabled()
        }

        val permissionState = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        )
        if (permissionState == PackageManager.PERMISSION_GRANTED) {
            ensureNotificationChannel()
            return true
        }

        val activity = currentActivity ?: return false
        return suspendCancellableCoroutine { continuation ->
            pendingPermissionContinuation = continuation
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                permissionRequestCode,
            )
        }
    }

    internal fun canScheduleExactAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
        return alarmManager.canScheduleExactAlarms()
    }

    private fun promptExactAlarmAccessIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val context = appContext ?: return
        if (canScheduleExactAlarms(context)) return
        val prefs = preferences(context)
        if (prefs.getBoolean(exactAlarmPromptedKey, false)) return
        val activity = currentActivity ?: return

        prefs.edit().putBoolean(exactAlarmPromptedKey, true).apply()
        activity.runOnUiThread {
            runCatching {
                activity.startActivity(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = android.net.Uri.parse("package:${activity.packageName}")
                    },
                )
            }
        }
    }

    actual suspend fun scheduleEpisodeReleaseNotifications(requests: List<EpisodeReleaseNotificationRequest>) {
        val context = appContext ?: return
        ensureNotificationChannel()

        stateMutex.withLock {
            withContext(Dispatchers.IO) {
                cancelLegacyWork(context)

                val prefs = preferences(context)
                val key = scopedScheduledRequestsKey()
                val previousIds = loadStoredRequests(context, key)
                    .mapTo(hashSetOf(), EpisodeReleaseNotificationRequest::requestId)
                val deliveredIds = loadDeliveredIds(context)
                val nowEpochMs = System.currentTimeMillis()
                val todayIso = LocalDate.now().toString()

                val retained = requests
                    .distinctBy(EpisodeReleaseNotificationRequest::requestId)
                    .filter { request ->
                        if (request.requestId in deliveredIds) return@filter false
                        val triggerAtEpochMs = triggerAtEpochMs(request.releaseDateIso) ?: return@filter false
                        triggerAtEpochMs > nowEpochMs ||
                            (request.releaseDateIso == todayIso && request.requestId in previousIds)
                    }

                prefs.edit()
                    .putString(key, json.encodeToString(requestListSerializer, retained))
                    .remove(ProfileScopedKey.of(legacyScheduledIdsKey))
                    .commit()
            }
        }

        deliverDueAndScheduleNext(context)
    }

    actual suspend fun clearScheduledEpisodeReleaseNotifications() {
        val context = appContext ?: return
        stateMutex.withLock {
            withContext(Dispatchers.IO) {
                cancelLegacyWork(context)
                preferences(context)
                    .edit()
                    .remove(scopedScheduledRequestsKey())
                    .remove(ProfileScopedKey.of(legacyScheduledIdsKey))
                    .commit()
            }
        }
        deliverDueAndScheduleNext(context)
    }

    internal suspend fun deliverDueAndScheduleNext(context: Context) {
        initialize(context)
        val applicationContext = context.applicationContext

        stateMutex.withLock {
            withContext(Dispatchers.IO) {
                val prefs = preferences(applicationContext)
                val nowEpochMs = System.currentTimeMillis()
                val todayIso = LocalDate.now().toString()
                val deliveredIds = loadDeliveredIds(applicationContext).toMutableSet()
                val scheduleKeys = prefs.all.keys.filter { key -> key.startsWith("${scheduledRequestsKey}_") }

                val dueRequests = mutableListOf<EpisodeReleaseNotificationRequest>()
                val remainingByKey = linkedMapOf<String, List<EpisodeReleaseNotificationRequest>>()
                var nextTriggerAtEpochMs: Long? = null

                scheduleKeys.forEach { key ->
                    val remaining = loadStoredRequests(applicationContext, key).filter { request ->
                        if (request.requestId in deliveredIds) return@filter false
                        val triggerAtEpochMs = triggerAtEpochMs(request.releaseDateIso) ?: return@filter false
                        when {
                            triggerAtEpochMs > nowEpochMs -> {
                                nextTriggerAtEpochMs = minOf(nextTriggerAtEpochMs ?: triggerAtEpochMs, triggerAtEpochMs)
                                true
                            }
                            request.releaseDateIso == todayIso -> {
                                if (dueRequests.none { due -> due.requestId == request.requestId }) {
                                    dueRequests += request
                                }
                                false
                            }
                            else -> false
                        }
                    }
                    remainingByKey[key] = remaining
                }

                scheduleNextAlarm(applicationContext, nextTriggerAtEpochMs)
                updateBackstopWork(applicationContext, hasPending = nextTriggerAtEpochMs != null)

                if (dueRequests.isNotEmpty()) {
                    val authorized = notificationsAuthorized()
                    val notifications = if (authorized) {
                        coroutineScope {
                            dueRequests.map { request ->
                                async {
                                    runCatching { buildNotification(applicationContext, request) }.getOrNull()
                                }
                            }.awaitAll()
                        }
                    } else {
                        dueRequests.map { null }
                    }
                    dueRequests.forEachIndexed { index, request ->
                        notifications[index]?.let { notification ->
                            runCatching {
                                NotificationManagerCompat.from(applicationContext)
                                    .notify(kotlin.math.abs(request.requestId.hashCode()), notification)
                            }
                        }
                        deliveredIds += request.requestId
                        prefs.edit()
                            .putStringSet(deliveredIdsKey, pruneDeliveredIds(deliveredIds))
                            .commit()
                    }
                }

                val editor = prefs.edit()
                remainingByKey.forEach { (key, remaining) ->
                    if (remaining.isEmpty()) {
                        editor.remove(key)
                    } else {
                        editor.putString(key, json.encodeToString(requestListSerializer, remaining))
                    }
                }
                editor
                    .putStringSet(deliveredIdsKey, pruneDeliveredIds(deliveredIds))
                    .commit()
            }
        }
    }

    actual suspend fun showTestNotification(request: EpisodeReleaseNotificationRequest) {
        val context = appContext ?: return
        ensureNotificationChannel()

        val notification = buildNotification(context, request)

        NotificationManagerCompat.from(context)
            .notify(kotlin.math.abs(request.requestId.hashCode()), notification)
    }

    internal suspend fun buildNotification(
        context: Context,
        request: EpisodeReleaseNotificationRequest,
    ): android.app.Notification {
        val pendingIntent = buildPendingIntent(context, request)
        val backdropBitmap = loadBackdropBitmap(request.backdropUrl)
        val appIconBitmap = ContextCompat.getDrawable(
            context,
            AppIconPlatform.currentLauncherIconResource(context),
        )?.toBitmap()

        return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(com.nuvio.app.R.drawable.ic_notification_small)
            .setContentTitle(request.notificationTitle)
            .setContentText(request.notificationBody)
            .setStyle(
                backdropBitmap?.let { bitmap ->
                    NotificationCompat.BigPictureStyle()
                        .bigPicture(bitmap)
                        .bigLargeIcon(appIconBitmap)
                        .setSummaryText(request.notificationBody)
                } ?: NotificationCompat.BigTextStyle().bigText(request.notificationBody),
            )
            .setLargeIcon(appIconBitmap)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .build()
    }

    internal suspend fun loadBackdropBitmap(backdropUrl: String?): Bitmap? {
        val imageUrl = backdropUrl?.trim().takeUnless { it.isNullOrEmpty() } ?: return null
        return runCatching {
            withTimeoutOrNull(backdropLoadTimeoutMs) {
                val bytes: ByteArray = httpClient.get(imageUrl).body()
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        }.getOrNull()
    }

    private fun buildPendingIntent(
        context: Context,
        request: EpisodeReleaseNotificationRequest,
    ): PendingIntent {
        val launchIntent = Intent().apply {
            component = AppIconPlatform.currentLauncherComponent(context)
            action = Intent.ACTION_VIEW
            data = android.net.Uri.parse(request.deepLinkUrl)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context,
            kotlin.math.abs(request.requestId.hashCode()),
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun scheduleNextAlarm(context: Context, triggerAtEpochMs: Long?) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pendingIntent = alarmPendingIntent(context)
        if (triggerAtEpochMs == null) {
            alarmManager.cancel(pendingIntent)
            return
        }

        val canUseExact = canScheduleExactAlarms(context)
        try {
            if (canUseExact) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtEpochMs, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtEpochMs, pendingIntent)
            }
        } catch (error: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtEpochMs, pendingIntent)
        }
    }

    private fun alarmPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, EpisodeReleaseNotificationReceiver::class.java).apply {
            action = alarmAction
        }
        return PendingIntent.getBroadcast(
            context,
            alarmRequestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun updateBackstopWork(context: Context, hasPending: Boolean) {
        runCatching {
            val workManager = WorkManager.getInstance(context)
            if (hasPending) {
                val workRequest = PeriodicWorkRequestBuilder<EpisodeReleaseNotificationWorker>(
                    backstopIntervalHours,
                    TimeUnit.HOURS,
                ).build()
                workManager.enqueueUniquePeriodicWork(
                    backstopWorkName,
                    ExistingPeriodicWorkPolicy.KEEP,
                    workRequest,
                )
            } else {
                workManager.cancelUniqueWork(backstopWorkName)
            }
        }
    }

    private fun cancelLegacyWork(context: Context) {
        runCatching {
            WorkManager.getInstance(context).cancelAllWorkByTag(workTag)
        }
    }

    private fun loadStoredRequests(context: Context, key: String): List<EpisodeReleaseNotificationRequest> {
        val raw = preferences(context).getString(key, null)?.takeIf { it.isNotBlank() } ?: return emptyList()
        return runCatching { json.decodeFromString(requestListSerializer, raw) }.getOrDefault(emptyList())
    }

    private fun loadDeliveredIds(context: Context): Set<String> =
        preferences(context).getStringSet(deliveredIdsKey, emptySet()).orEmpty().toSet()

    private fun pruneDeliveredIds(ids: Set<String>): Set<String> {
        val cutoffIso = LocalDate.now().minusDays(deliveredRetentionDays).toString()
        return ids.filterTo(hashSetOf()) { id ->
            val dateSuffix = id.takeLast(10)
            runCatching { LocalDate.parse(dateSuffix) }.isFailure || dateSuffix >= cutoffIso
        }
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences(platformPreferencesName, Context.MODE_PRIVATE)

    private fun scopedScheduledRequestsKey(): String = ProfileScopedKey.of(scheduledRequestsKey)

    private fun triggerAtEpochMs(releaseDateIso: String): Long? = runCatching {
        LocalDate.parse(releaseDateIso)
            .atTime(EpisodeReleaseNotificationHour, EpisodeReleaseNotificationMinute)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }.getOrNull()

    private fun ensureNotificationChannel() {
        val context = appContext ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return
        val existingChannel = notificationManager.getNotificationChannel(channelId)
        if (existingChannel != null) return

        val channel = NotificationChannel(
            channelId,
            runBlocking { getString(Res.string.notifications_channel_episode_releases_name) },
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = runBlocking { getString(Res.string.notifications_channel_episode_releases_description) }
        }
        notificationManager.createNotificationChannel(channel)
    }
}
