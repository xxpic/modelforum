package com.yanparker.modelforum

import android.app.Application
import android.util.Log
import com.yanparker.modelforum.di.AppContainer
import com.yanparker.modelforum.service.NotificationHelper
import java.io.File

class App : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val file = File(filesDir, "crash_last.txt")
                file.writeText(
                    "${throwable.javaClass.name}: ${throwable.message}\n\n" +
                        throwable.stackTrace.joinToString("\n") { "    at $it" }
                )
            } catch (_: Exception) {
            }
            Log.e("ModelForum", "Uncaught crash in ${thread.name}", throwable)
            android.os.Process.killProcess(android.os.Process.myPid())
        }
        super.onCreate()
        container = AppContainer(this)
        NotificationHelper.createChannels(this)
    }
}