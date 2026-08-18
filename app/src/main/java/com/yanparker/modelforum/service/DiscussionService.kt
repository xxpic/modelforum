package com.yanparker.modelforum.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.yanparker.modelforum.App
import com.yanparker.modelforum.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import androidx.core.content.ContextCompat

/**
 * Foreground-сервис: держит обсуждение живым при выключенном экране.
 * Кнопки в уведомлении управляют движком.
 */
class DiscussionService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as App
        val container = app.container
        val discussionId = intent?.getLongExtra("discussionId", 0L) ?: 0L

        when (intent?.action) {
            ACTION_PAUSE -> if (discussionId != 0L) scope.launch { container.engine.pause(discussionId) }
            ACTION_RESUME -> if (discussionId != 0L) scope.launch { container.engine.resume(discussionId) }
            ACTION_STOP -> {
                if (discussionId != 0L) scope.launch {
                    container.engine.stop(discussionId)
                    stopSelf()
                }
            }
            else -> {
                startForeground(
                    discussionId.toInt(),
                    NotificationHelper.foregroundNotification(
                        this,
                        "Обсуждение в фоне",
                        "Модели продолжают обсуждение. Лимиты снимутся — продолжим сами.",
                        discussionId,
                    ),
                )
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    companion object {
        const val ACTION_PAUSE = "com.yanparker.modelforum.PAUSE"
        const val ACTION_RESUME = "com.yanparker.modelforum.RESUME"
        const val ACTION_STOP = "com.yanparker.modelforum.STOP"

        fun start(context: android.content.Context, discussionId: Long) {
            val intent = Intent(context, DiscussionService::class.java)
                .putExtra("discussionId", discussionId)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: android.content.Context) {
            context.stopService(Intent(context, DiscussionService::class.java))
        }
    }
}