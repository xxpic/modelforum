package com.yanparker.modelforum.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.yanparker.modelforum.R

object NotificationHelper {

    const val CHANNEL_DISCUSSION = "discussions"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_DISCUSSION,
            context.getString(R.string.notification_channel_discussion),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.notification_channel_discussion_desc)
        }
        nm.createNotificationChannel(channel)
    }

    fun foregroundNotification(
        context: Context,
        title: String,
        text: String,
        discussionId: Long,
    ): android.app.Notification {
        fun action(action: String, icon: Int, label: String): NotificationCompat.Action {
            val intent = Intent(context, DiscussionService::class.java)
                .setAction(action)
                .putExtra("discussionId", discussionId)
            val pi = PendingIntent.getService(
                context,
                discussionId.toInt() + action.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            return NotificationCompat.Action(icon, label, pi)
        }
        return NotificationCompat.Builder(context, CHANNEL_DISCUSSION)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_popup_discussion)
            .setOngoing(true)
            .addAction(
                NotificationCompat.Action(
                    android.R.drawable.ic_media_pause, "⏸",
                    serviceIntent(context, DiscussionService.ACTION_PAUSE, discussionId),
                )
            )
            .addAction(
                NotificationCompat.Action(
                    android.R.drawable.ic_media_play, "▶",
                    serviceIntent(context, DiscussionService.ACTION_RESUME, discussionId),
                )
            )
            .addAction(
                NotificationCompat.Action(
                    android.R.drawable.ic_menu_close_clear_cancel, "⏹",
                    serviceIntent(context, DiscussionService.ACTION_STOP, discussionId),
                )
            )
            .build()
    }

    fun serviceIntent(context: Context, action: String, discussionId: Long): PendingIntent {
        val intent = Intent(context, DiscussionService::class.java)
            .setAction(action)
            .putExtra("discussionId", discussionId)
        return PendingIntent.getService(
            context,
            discussionId.toInt() + action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}