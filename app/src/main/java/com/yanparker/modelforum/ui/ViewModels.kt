package com.yanparker.modelforum.ui

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yanparker.modelforum.data.db.DiscussionEntity
import com.yanparker.modelforum.data.db.MessageEntity
import com.yanparker.modelforum.data.db.ParticipantEntity
import com.yanparker.modelforum.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ParticipantsViewModel(private val container: AppContainer) : ViewModel() {

    val participants: StateFlow<List<ParticipantEntity>> =
        container.participantRepository.all().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val busy = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)

    fun add(providerId: String, name: String, modelId: String, apiKey: String, colorIndex: Int) {
        viewModelScope.launch {
            busy.value = true
            try {
                container.participantRepository.add(providerId, name, modelId, apiKey, colorIndex)
                message.value = "Участник добавлен"
            } catch (e: Exception) {
                message.value = "Ошибка: ${e.message}"
            } finally {
                busy.value = false
            }
        }
    }

    fun update(id: Long, name: String, modelId: String, colorIndex: Int) {
        viewModelScope.launch {
            container.participantRepository.update(id, name, modelId, colorIndex)
        }
    }

    fun updateKey(id: Long, key: String) {
        viewModelScope.launch {
            container.participantRepository.updateKey(id, key)
        }
    }

    fun toggleEnabled(p: ParticipantEntity) {
        viewModelScope.launch {
            container.participantRepository.setEnabled(p.id, !p.enabled)
        }
    }

    fun delete(p: ParticipantEntity) {
        viewModelScope.launch {
            container.participantRepository.delete(p.id)
        }
    }
}

class ForumListViewModel(private val container: AppContainer) : ViewModel() {

    val discussions: StateFlow<List<DiscussionEntity>> =
        container.discussionRepository.all().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val participants: StateFlow<List<ParticipantEntity>> =
        container.participantRepository.all().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun create(title: String, participantIds: List<Long>, maxMessages: Int, maxTokens: Int, temperature: Double) {
        viewModelScope.launch {
            val id = container.discussionRepository.insert(
                DiscussionEntity(
                    title = title,
                    participantIds = participantIds.joinToString(","),
                    maxMessagesPerModel = maxMessages,
                    maxTokens = maxTokens,
                    temperature = temperature,
                )
            )
            container.engine.startDiscussion(id)
        }
    }

    fun toggle(d: DiscussionEntity) {
        viewModelScope.launch {
            when (d.state) {
                "running" -> container.engine.pause(d.id)
                "paused", "waiting_limits", "idle" -> container.engine.resume(d.id)
                else -> {}
            }
        }
    }

    fun stop(d: DiscussionEntity) {
        viewModelScope.launch { container.engine.stop(d.id) }
    }

    fun delete(d: DiscussionEntity) {
        viewModelScope.launch {
            container.engine.stop(d.id)
            container.discussionRepository.delete(d.id)
        }
    }
}

class ForumViewModel(private val container: AppContainer, private val discussionId: Long) : ViewModel() {

    val discussion: StateFlow<DiscussionEntity> =
        container.discussionRepository.byId(discussionId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DiscussionEntity(title = ""))

    val messages: StateFlow<List<MessageEntity>> =
        container.messageRepository.forDiscussion(discussionId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val participants: StateFlow<List<ParticipantEntity>> =
        container.participantRepository.all().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun start() = viewModelScope.launch { container.engine.startDiscussion(discussionId) }
    fun pause() = viewModelScope.launch { container.engine.pause(discussionId) }
    fun resume() = viewModelScope.launch { container.engine.resume(discussionId) }

    fun stop() {
        viewModelScope.launch { container.engine.stop(discussionId) }
    }
}

class AskViewModel(private val container: AppContainer) : ViewModel() {

    val participants: StateFlow<List<ParticipantEntity>> =
        container.participantRepository.all().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val current = MutableStateFlow<Long?>(null)
    val busy = MutableStateFlow(false)

    fun ask(question: String, ids: List<Long>, judgeId: Long) {
        viewModelScope.launch {
            busy.value = true
            val d = DiscussionEntity(
                title = question.take(60),
                mode = "ask",
                question = question,
                judgeId = judgeId,
                participantIds = ids.joinToString(","),
            )
            val id = container.discussionRepository.insert(d)
            current.value = id
            container.questionEngine.run(id).invokeOnCompletion { busy.value = false }
        }
    }
}

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    val settings = container.appSettings.flow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.yanparker.modelforum.data.prefs.AppSettings())

    fun update(
        intervalMs: Long? = null, maxMessages: Int? = null, maxTokens: Int? = null,
        temperature: Double? = null, dark: Boolean? = null, dynamic: Boolean? = null, resume: Boolean? = null,
    ) {
        viewModelScope.launch {
            container.appSettings.update(
                minRequestIntervalMs = intervalMs,
                maxMessagesPerModel = maxMessages,
                maxTokens = maxTokens,
                temperature = temperature,
                darkTheme = dark,
                dynamicColor = dynamic,
                resumeInterrupted = resume,
            )
        }
    }

    fun export(): String = kotlinx.coroutines.runBlocking {
        buildString {
            val discussions = container.discussionRepository.allOnce()
            val messages = container.messageRepository.allOnce()
            val parts = container.participantRepository.allOnce().associate { it.id to it.name }
            for (d in discussions) {
                append("=== ").append(d.title).append(" [").append(d.mode).append("] ")
                append(java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(d.createdAt)))
                append("\n")
                messages[d.id].forEach { m ->
                    val author = when (m.role) {
                        "user" -> "Вы"
                        "judge" -> "Судья (${parts[m.participantId] ?: "?"})"
                        else -> parts[m.participantId] ?: "?"
                    }
                    append("[").append(author).append("] ").append(m.text).append("\n\n")
                }
            }
        }
    }

    fun share(context: android.content.Context) {
        val text = export()
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Форум ИИ-моделей — история")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(send, "Поделиться историей"))
    }
}

object Factory {
    fun participants(c: AppContainer) = viewModelFactory { ParticipantsViewModel(c) }
    fun forumList(c: AppContainer) = viewModelFactory { ForumListViewModel(c) }
    fun forum(c: AppContainer, id: Long) = viewModelFactory { ForumViewModel(c, id) }
    fun ask(c: AppContainer) = viewModelFactory { AskViewModel(c) }
    fun settings(c: AppContainer) = viewModelFactory { SettingsViewModel(c) }

    private fun <T : ViewModel> viewModelFactory(create: () -> T) =
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <VM : ViewModel> create(modelClass: Class<VM>): VM = create() as VM
        }
}