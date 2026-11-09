package com.nuvio.app.features.sports.compat

import com.nuvio.app.features.sports.SportsLiveWatch
import com.nuvio.app.features.sports.SportsReminders

/** One alarm receiver for the Sports hub: the id says what it's for. Game reminders start with [SPORTS], your teams' kick-offs (live score) with [LIVE]. */
internal object LiveTvAlarms {
    const val SPORTS = "sport:"
    const val LIVE = "live:"

    /** An alarm went off (or the app started and a time had passed). */
    fun fire(id: String) {
        when {
            id.startsWith(SPORTS) -> SportsReminders.fire(id.removePrefix(SPORTS))
            id.startsWith(LIVE) -> SportsLiveWatch.fromAlarm(id.removePrefix(LIVE))
        }
    }

    /** The phone restarted: every alarm set again. */
    fun rearmAll() {
        SportsReminders.start()
    }
}
