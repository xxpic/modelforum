package com.yanparker.modelforum.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.yanparker.modelforum.App
import java.util.concurrent.TimeUnit

/**
 * Авто-возобновление «спящих» обсуждений после снятия лимитов.
 * Движок сам продолжает при живом процессе; воркер страхует холодные запуски
 * и принудительно возвращает обсуждения из WAITING_LIMITS в RUNNING.
 */
class ResumeWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as App).container
        val discussions = container.discussionRepository.allOnce()
        val participants = container.participantRepository.allOnce().associateBy { it.id }
        val now = System.currentTimeMillis()

        for (d in discussions) {
            if (d.state != "waiting_limits") continue
            val ids = d.participantIds.split(",").mapNotNull { it.toLongOrNull() }
            if (ids.isEmpty()) continue
            val blocked = ids.any { id ->
                val p = participants[id] ?: return@any false
                p.blockReason != "" && p.blockedUntil > now
            }
            if (!blocked) {
                container.discussionRepository.setState(d.id, "running")
            }
        }
        return Result.success()
    }

    companion object {
        private val NAME = "resume_worker"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ResumeWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}