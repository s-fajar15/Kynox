package com.kynox.gaming

import android.app.Application
import com.kynox.gaming.service.NotificationChannels

class KynoxApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.createAll(this)
        container = AppContainer(this)
    }
}
