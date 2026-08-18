package com.yanparker.modelforum.di

import android.content.Context
import com.yanparker.modelforum.data.db.AppDatabase
import com.yanparker.modelforum.data.key.KeyStorage
import com.yanparker.modelforum.data.network.HttpClients
import com.yanparker.modelforum.data.prefs.AppSettingsStore
import com.yanparker.modelforum.data.provider.ProviderClient
import com.yanparker.modelforum.data.provider.ProviderPresets
import com.yanparker.modelforum.data.repository.DiscussionRepository
import com.yanparker.modelforum.data.repository.MessageRepository
import com.yanparker.modelforum.data.repository.ParticipantRepository
import com.yanparker.modelforum.engine.DiscussionEngine
import com.yanparker.modelforum.engine.QuestionEngine
import com.yanparker.modelforum.engine.RequestScheduler
import com.yanparker.modelforum.service.DiscussionService
import com.yanparker.modelforum.service.ResumeWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppContainer(context: Context) {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val keyStorage = KeyStorage(context)
    val appSettings = AppSettingsStore(context)
    val database = AppDatabase.build(context)

    val participantRepository = ParticipantRepository(database.participantDao(), keyStorage)
    val discussionRepository = DiscussionRepository(database.discussionDao())
    val messageRepository = MessageRepository(database.messageDao())

    val httpClients = HttpClients()

    val providerClient = ProviderClient(
        okHttp = httpClients.client,
        streamClient = httpClients.streamClient,
        presets = ProviderPresets.all,
    )

    val scheduler = RequestScheduler(appSettings = appSettings, scope = appScope)

    val engine: DiscussionEngine by lazy {
        DiscussionEngine(
            scope = appScope,
            scheduler = scheduler,
            appSettings = appSettings,
            participantRepository = participantRepository,
            discussionRepository = discussionRepository,
            messageRepository = messageRepository,
            keyStorage = keyStorage,
            providerClient = providerClient,
        ).also { it.start() }
    }

    val questionEngine: QuestionEngine by lazy {
        QuestionEngine(
            scope = appScope,
            scheduler = scheduler,
            appSettings = appSettings,
            participantRepository = participantRepository,
            discussionRepository = discussionRepository,
            messageRepository = messageRepository,
            keyStorage = keyStorage,
            providerClient = providerClient,
        )
    }

    init {
        ResumeWorker.schedule(context)
        appScope.launch {
            engine.stateChanges.collect { (id, state) ->
                when (state) {
                    "running", "waiting_limits" -> DiscussionService.start(context, id)
                    "stopped", "done", "paused" -> DiscussionService.stop(context)
                }
            }
        }
    }
}