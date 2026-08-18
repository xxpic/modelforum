package com.yanparker.modelforum.engine

import com.yanparker.modelforum.data.prefs.AppSettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Глобальная очередь запросов к API. Гарантирует минимальный интервал между
 * запросами (защита от RPM-лимитов бесплатных тарифов) и единую точку
 * управления нагрузкой.
 */
class RequestScheduler(
    private val appSettings: AppSettingsStore,
    scope: CoroutineScope,
) {
    private val lock = Mutex()
    private var lastRequestAt = 0L
    private var interval = 3000L

    init {
        scope.launch {
            appSettings.flow.collect { s -> interval = s.minRequestIntervalMs }
        }
    }

    suspend fun <T> submit(block: suspend () -> T): T = lock.withLock {
        val wait = interval - (System.currentTimeMillis() - lastRequestAt)
        if (wait > 0) delay(wait)
        lastRequestAt = System.currentTimeMillis()
        try {
            block()
        } finally {
            lastRequestAt = System.currentTimeMillis()
        }
    }
}