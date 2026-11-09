package com.nuvio.app.features.sports.compat

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper

/** Wakes the app for a game reminder, and sets the alarms again after the phone restarts. */
class SportsAlarmReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_FIRE = "com.nuvio.app.sports.ALARM"
        const val EXTRA_ID = "id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        // The app may not be running: the hub's stored data and helpers have to be in place first.
        SportsAndroid.initialize(context)
        when (intent.action) {
            ACTION_FIRE -> {
                val id = intent.getStringExtra(EXTRA_ID) ?: return
                val pending = goAsync()
                com.nuvio.app.features.sports.compat.LiveTvRepository.ensureLoaded()
                LiveTvAlarms.fire(id)
                Handler(Looper.getMainLooper()).postDelayed({ runCatching { pending.finish() } }, 8_000)
            }
            else -> LiveTvAlarms.rearmAll() // boot: set the alarms again
        }
    }
}
