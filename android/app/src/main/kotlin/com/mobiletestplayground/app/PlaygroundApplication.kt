package com.mobiletestplayground.app

import android.app.Application
import com.mobiletestplayground.app.scenarios.notifications.NotificationChannels
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class PlaygroundApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationChannels.register(this)
    }
}
