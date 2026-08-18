package com.yanparker.modelforum

import com.yanparker.modelforum.data.provider.parseRetryAfter
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Тест логики минимального интервала между запросами (аналог RequestScheduler
 * без Android-зависимостей, для проверки контракта планировщика).
 */
class SchedulerIntervalTest {

    @Test
    fun `requests are spaced by min interval`() = runTest {
        val interval = 50L
        var last = 0L
        val lock = Mutex()

        suspend fun submit(block: suspend () -> Unit) = lock.withLock {
            val wait = interval - (System.currentTimeMillis() - last)
            if (wait > 0) delay(wait)
            last = System.currentTimeMillis()
            block()
        }

        val times = mutableListOf<Long>()
        repeat(5) {
            submit { times.add(System.currentTimeMillis()) }
        }
        for (i in 1 until times.size) {
            assertTrue("шаг ${times[i] - times[i - 1]} должен быть ≥ interval: ", times[i] - times[i - 1] >= interval)
        }
    }
}

class NextMidnightTest {

    @Test
    fun `next midnight utc is in the future and before tomorrow plus one day`() {
        val now = System.currentTimeMillis()
        val midnight = com.yanparker.modelforum.engine.DiscussionEngine.nextMidnightUtc()
        assertTrue(midnight > now)
        assertTrue(midnight - now < 26 * 60 * 60 * 1000)
    }

    @Test
    fun `retry after respects cap`() {
        assertTrue(parseRetryAfter("99999") <= 600_000)
    }
}