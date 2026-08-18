package com.yanparker.modelforum

import android.app.Application
import com.yanparker.modelforum.di.AppContainer
import com.yanparker.modelforum.service.NotificationHelper

class App : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        NotificationHelper.createChannels(this)
        container.engine
    }
}