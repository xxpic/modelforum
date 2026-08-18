package com.yanparker.modelforum.engine

import com.yanparker.modelforum.data.db.DiscussionEntity
import com.yanparker.modelforum.data.db.MessageEntity
import com.yanparker.modelforum.data.db.ParticipantEntity
import com.yanparker.modelforum.data.key.KeyStorage
import com.yanparker.modelforum.data.network.ApiException
import com.yanparker.modelforum.data.network.ChatMessage
import com.yanparker.modelforum.data.network.ChatRequest
import com.yanparker.modelforum.data.prefs.AppSettingsStore
import com.yanparker.modelforum.data.provider.ProviderClient
import com.yanparker.modelforum.data.repository.DiscussionRepository
import com.yanparker.modelforum.data.repository.MessageRepository
import com.yanparker.modelforum.data.repository.ParticipantRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * Движок дискуссий (режим 1). Модели обсуждают тему по кругу с лёгкой
 * случайностью, могут менять тему; при лимитах (429/402) обсуждение уходит
 * в WAITING_LIMITS и автоматически продолжается после снятия ограничений.
 */
class DiscussionEngine(
    private val scope: CoroutineScope,
    private val scheduler: RequestScheduler,
    private val appSettings: AppSettingsStore,
    private val participantRepository: ParticipantRepository,
    private val discussionRepository: DiscussionRepository,
    private val messageRepository: MessageRepository,
    private val keyStorage: KeyStorage,
    private val providerClient: ProviderClient,
) {
    private val activeJobs = ConcurrentHashMap<Long, Job>()

    /** События смены состояния: (discussionId, state). Для фонового сервиса и UI. */
    val stateChanges = kotlinx.coroutines.flow.MutableSharedFlow<Pair<Long, String>>(extraBufferCapacity = 64)

    private suspend fun emitState(id: Long, state: String) {
        stateChanges.tryEmit(id to state)
    }

    fun start() {
        scope.launch {
            discussionRepository.active().collectLatest { list ->
                for (d in list) ensureJob(d)
                activeJobs.keys.retainAll(list.map { it.id })
            }
        }
    }

    private fun ensureJob(d: DiscussionEntity) {
        val existing = activeJobs[d.id]
        if (existing != null && existing.isActive) return
        activeJobs[d.id] = scope.launch { runLoop(d.id) }
    }

    // ---------- Управление из UI ----------

    suspend fun startDiscussion(id: Long) {
        val d = discussionRepository.byIdOnce(id) ?: return
        val participants = participantRepository.allOnce()
        val ids = if (d.participantIds.isBlank()) {
            participants.filter { it.enabled }.joinToString(",") { it.id.toString() }
        } else d.participantIds
discussionRepository.insert(d.copy(participantIds = ids))
        runCatching { emitState(id, "running") }
        discussionRepository.setState(id, "running")
    }

    suspend fun pause(id: Long) {
        runCatching { emitState(id, "paused") }
        discussionRepository.setState(id, "paused")
    }

    suspend fun resume(id: Long) {
        runCatching { emitState(id, "running") }
        discussionRepository.setState(id, "running")
    }

    suspend fun stop(id: Long) {
        runCatching { emitState(id, "stopped") }
        discussionRepository.setState(id, "stopped")
        activeJobs[id]?.cancel()
    }

    // ---------- Главный цикл ----------

    private suspend fun runLoop(discussionId: Long) {
        try {
            while (true) {
                val d = discussionRepository.byIdOnce(discussionId) ?: return
                when (d.state) {
                    "idle", "stopped", "done" -> return
                    "paused" -> {
                        delay(500)
                        continue
                    }
                    "waiting_limits" -> {
                        waitUntilUnblocked(discussionId)
                        continue
                    }
                }

                val participants = participantRepository.allOnce()
                    .filter { it.id.toString() in d.participantIds.split(",") }
                    .filter { it.enabled }

                if (participants.isEmpty()) {
                    runCatching { emitState(discussionId, "done") }
                    discussionRepository.setState(discussionId, "done")
                    return
                }

                clearExpiredBlocks(participants)

                val active = participants.filter { it.blockedUntil <= System.currentTimeMillis() }
                if (active.isEmpty()) {
                    runCatching { emitState(discussionId, "waiting_limits") }
                    discussionRepository.setState(discussionId, "waiting_limits")
                    continue
                }

                val budget = d.maxMessagesPerModel
                val eligible = active.filter { p ->
                    messageRepository.countByParticipant(discussionId, p.id) < budget
                }
                if (eligible.isEmpty()) {
                    runCatching { emitState(discussionId, "done") }
                    discussionRepository.setState(discussionId, "done")
                    return
                }

                val next = pickNext(eligible, discussionId)
                makeTurn(d, next)

                if (!keepGoing(discussionId)) return
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            runCatching { discussionRepository.setErrorNote(discussionId, "Сбой движка: ${e.message}") }
        } finally {
            activeJobs.remove(discussionId)
        }
    }

    private suspend fun keepGoing(discussionId: Long): Boolean =
        discussionRepository.byIdOnce(discussionId)?.state == "running"

    /** Сбрасывает истёкшие лимиты участников. */
    private suspend fun clearExpiredBlocks(participants: List<ParticipantEntity>) {
        val now = System.currentTimeMillis()
        for (p in participants) {
            if (p.blockReason != "" && p.blockedUntil > 0 && p.blockedUntil <= now) {
                participantRepository.clearBlocked(p.id)
            }
        }
    }

    /** Ожидание снятия лимитов: таймер до ближайшего разблокирования. */
    private suspend fun waitUntilUnblocked(discussionId: Long) {
        while (true) {
            val d = discussionRepository.byIdOnce(discussionId) ?: return
            if (d.state == "stopped" || d.state == "paused") return
            if (d.state != "waiting_limits") return
            val participants = participantRepository.allOnce()
                .filter { it.id.toString() in d.participantIds.split(",") }
            val blocked = participants.filter {
                it.blockReason != "" && it.blockedUntil > System.currentTimeMillis()
            }
            if (blocked.isEmpty()) {
                runCatching { emitState(discussionId, "running") }
                    discussionRepository.setState(discussionId, "running")
                return
            }
            val next = blocked.minOf { it.blockedUntil }
            delay((next - System.currentTimeMillis()).coerceIn(1_000, 60_000))
        }
    }

    private suspend fun pickNext(
        eligible: List<ParticipantEntity>,
        discussionId: Long,
    ): ParticipantEntity {
        val counts = eligible.associateWith { p ->
            messageRepository.countByParticipant(discussionId, p.id)
        }
        val min = counts.values.min()
        val leaders = counts.filterValues { it == min }.keys.toList()
        return if (leaders.size == 1) leaders.first()
        else leaders[Random.nextInt(leaders.size)]
    }

    // ---------- Ход участника ----------

    private suspend fun makeTurn(d: DiscussionEntity, p: ParticipantEntity) {
        val key = keyStorage.getKey(p.keyRef)
        if (key == null) {
            messageRepository.insert(
                MessageEntity(discussionId = d.id, participantId = p.id, text = "⚠️ Ключ не найден", status = "failed")
            )
            return
        }

        val settings = kotlinx.coroutines.flow.first(appSettings.flow)
        val context = buildContext(d, settings.contextTrimChars)
        val messages = listOf(
            ChatMessage("system", Prompts.forumSystem(p.name)),
            ChatMessage("user", context),
        )
        val request = ChatRequest(
            model = p.modelId,
            messages = messages,
            temperature = settings.temperature,
            maxTokens = settings.maxTokens,
        )

        var messageId = 0L
        try {
            val sb = StringBuilder()
            var lastFlush = System.currentTimeMillis()
            messageId = messageRepository.insert(
                MessageEntity(discussionId = d.id, participantId = p.id, status = "streaming")
            )

            try {
                val result = scheduler.submit {
                    providerClient.chatStream(
                        key = key,
                        preset = providerClient.presetFor(p.providerId, p.customBaseUrl, p.customChatPath, p.customModelsPath),
                        request = request,
                        onDelta = { chunk ->
                            sb.append(chunk)
                            if (System.currentTimeMillis() - lastFlush >= 150) {
                                lastFlush = System.currentTimeMillis()
                                messageRepository.updateText(messageId, sb.toString())
                            }
                        },
                    )
                }
                messageRepository.update(messageId, sb.toString(), "done", 0)
                participantRepository.incrementDailyRequests(p.id, today())
            } catch (e: ApiException) {
                when (e) {
                    is ApiException.RateLimited -> {
                        messageRepository.update(messageId, sb.toString(), "interrupted", 0)
                        participantRepository.setBlocked(p.id, System.currentTimeMillis() + e.retryAfterMs, "rate")
                        runCatching { emitState(d.id, "waiting_limits") }
                    discussionRepository.setState(d.id, "waiting_limits")
                    }
                    is ApiException.NoBalance -> {
                        messageRepository.update(messageId, sb.toString(), "failed", 0)
                        participantRepository.setBlocked(p.id, nextMidnightUtc(), "balance")
                    }
                    else -> {
                        messageRepository.update(messageId, sb.toString(), "failed", 0)
                    }
                }
            }
        } catch (e: CancellationException) {
            if (messageId != 0L) {
                runCatching { messageRepository.update(messageId, "", "interrupted", 0) }
            }
            throw e
        }
    }

    private suspend fun buildContext(d: DiscussionEntity, trimChars: Int): String {
        val messages = messageRepository.forDiscussionOnce(d.id)
        val names = participantRepository.allOnce().associate { it.id to it.name }
        val lines = mutableListOf<Pair<String, String>>()
        for (m in messages) {
            if (m.status == "failed") continue
            if (m.status == "interrupted") continue
            if (m.role == "user") {
                lines.add("Вы" to m.text)
            } else {
                lines.add((names[m.participantId] ?: "Участник") to m.text)
            }
        }
        val (kept, note) = Prompts.trimContext(d.title, lines, trimChars)
        return Prompts.forumContext(d.title, kept, note)
    }

    companion object {
        fun today(): String = LocalDate.now(ZoneOffset.UTC).toString()

        fun nextMidnightUtc(): Long {
            val tomorrow = LocalDate.now(ZoneOffset.UTC).plusDays(1)
            return tomorrow.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        }
    }
}