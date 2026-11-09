package com.nuvio.app.features.sports.compat

import android.content.Context

/** Gives the Sports hub's Android helpers the application context. Called once from the application class and from the alarm receiver. */
object SportsAndroid {
    fun initialize(context: Context) {
        val app = context.applicationContext
        LiveTvFiles.initialize(app)
        SportsKeyValue.initialize(app)
        SportsAlarmPlatform.initialize(app)
    }
}
