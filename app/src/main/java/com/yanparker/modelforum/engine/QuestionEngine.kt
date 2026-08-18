package com.yanparker.modelforum.engine

import com.yanparker.modelforum.data.db.DiscussionEntity
import com.yanparker.modelforum.data.db.MessageEntity
import com.yanparker.modelforum.data.key.KeyStorage
import com.yanparker.modelforum.data.network.ApiException
import com.yanparker.modelforum.data.network.ChatMessage
import com.yanparker.modelforum.data.network.ChatRequest
import com.yanparker.modelforum.data.prefs.AppSettingsStore
import com.yanparker.modelforum.data.provider.ProviderClient
import com.yanparker.modelforum.data.repository.DiscussionRepository
import com.yanparker.modelforum.data.repository.MessageRepository
import com.yanparker.modelforum.data.repository.ParticipantRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout

/**
 * Движок «вопроса» (режим 2): все участники думают параллельно (≤3 за раз),
 * судья анализирует их ответы и выдаёт итог.
 */
class QuestionEngine(
    private val scope: CoroutineScope,
    private val scheduler: RequestScheduler,
    private val appSettings: AppSettingsStore,
    private val participantRepository: ParticipantRepository,
    private val discussionRepository: DiscussionRepository,
    private val messageRepository: MessageRepository,
    private val keyStorage: KeyStorage,
    private val providerClient: ProviderClient,
) {
    private val active = java.util.concurrent.ConcurrentHashMap<Long, kotlinx.coroutines.Job>()
    private val parallelism = Semaphore(3)

    fun run(discussionId: Long): kotlinx.coroutines.Job {
        val existing = active[discussionId]
        if (existing != null && existing.isActive) return existing
        val job = scope.launch {
            try {
                ask(discussionId)
            } finally {
                active.remove(discussionId)
            }
        }
        active[discussionId] = job
        return job
    }

    private suspend fun ask(discussionId: Long) {
        val d = discussionRepository.byIdOnce(discussionId) ?: return
        discussionRepository.setState(discussionId, "running")

        val ids = d.participantIds.split(",").mapNotNull { it.toLongOrNull() }
        val participants = participantRepository.allOnce()
            .filter { it.id in ids || ids.isEmpty() }
            .filter { it.enabled }

        val settings = appSettings.flow.first()
        val request = ChatRequest(
            model = "",
            messages = listOf(ChatMessage("system", Prompts.askSystem("")), ChatMessage("user", d.question)),
            temperature = settings.temperature,
            maxTokens = settings.maxTokens,
        )

        val results = mutableMapOf<ParticipantRef, String>()
        coroutineScope {
            val jobs = participants.map { p ->
                async {
                    scheduler.submit {
                        parallelism.withPermit {
                            val r = callModel(p, request.copy(model = p.modelId), d)
                            if (r != null) results[ParticipantRef(p.id)] = r
                        }
                    }
                }
            }
            jobs.awaitAll()
        }
        delay(200)

        val winning = results.entries
        val judgeId = d.judgeId
        val judge = participants.firstOrNull { it.id == judgeId }
        if (judge != null && winning.isNotEmpty()) {
            val answers = winning.map { (ref, text) ->
                val name = participants.firstOrNull { it.id == ref.id }?.name ?: "Модель"
                name to text
            }
            val judgeRequest = ChatRequest(
                model = judge.modelId,
                messages = listOf(
                    ChatMessage("system", Prompts.forumSystem("Судья").replaceFirst(judge.name, judge.name)),
                    ChatMessage("user", Prompts.judgePrompt(d.question, answers)),
                ),
                temperature = settings.temperature,
                maxTokens = settings.maxTokens,
            )
            val key = keyStorage.getKey(judge.keyRef)
            if (key != null) {
                try {
                    val resp = withTimeout(180_000) {
                        scheduler.submit { providerClient.chat(key, providerClient.presetFor(judge.providerId, judge.customBaseUrl, judge.customChatPath, judge.customModelsPath), judgeRequest) }
                    }
                    val text = resp.choices.firstOrNull()?.message?.content.orEmpty()
                    messageRepository.insert(
                        MessageEntity(discussionId = d.id, participantId = judge.id, role = "judge", text = text, status = "done")
                    )
                } catch (e: Exception) {
                    discussionRepository.setErrorNote(d.id, "Судья не ответил: ${e.message}")
                }
            }
        } else if (winning.isEmpty()) {
            discussionRepository.setErrorNote(d.id, "Ни одна модель не ответила — проверьте ключи и лимиты")
        }
        discussionRepository.setState(discussionId, "done")
    }

    private data class ParticipantRef(val id: Long)

    private suspend fun callModel(p: com.yanparker.modelforum.data.db.ParticipantEntity, request: ChatRequest, d: DiscussionEntity): String? {
        val key = keyStorage.getKey(p.keyRef) ?: run {
            messageRepository.insert(
                MessageEntity(discussionId = d.id, participantId = p.id, text = "⚠️ Ключ не найден", status = "failed")
            )
            return null
        }
        return try {
            val resp = withTimeout(180_000) {
                scheduler.submit { providerClient.chat(key, providerClient.presetFor(p.providerId, p.customBaseUrl, p.customChatPath, p.customModelsPath), request) }
            }
            val text = resp.choices.firstOrNull()?.message?.content.orEmpty()
            messageRepository.insert(
                MessageEntity(discussionId = d.id, participantId = p.id, text = text, status = "done")
            )
            participantRepository.incrementDailyRequests(p.id, DiscussionEngine.today())
            text
        } catch (e: ApiException.NoBalance) {
            messageRepository.insert(MessageEntity(discussionId = d.id, participantId = p.id, text = "⛔ Лимит/баланс исчерпан", status = "failed"))
            null
        } catch (e: ApiException.RateLimited) {
            messageRepository.insert(MessageEntity(discussionId = d.id, participantId = p.id, text = "⏳ Запрос отклонён: лимит запросов", status = "failed"))
            null
        } catch (e: Exception) {
            messageRepository.insert(MessageEntity(discussionId = d.id, participantId = p.id, text = "❌ ${e.message ?: "ошибка"}", status = "failed"))
            null
        }
    }
}