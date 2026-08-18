package com.yanparker.modelforum.data.repository

import com.yanparker.modelforum.data.db.AppDatabase
import com.yanparker.modelforum.data.db.ParticipantEntity
import com.yanparker.modelforum.data.key.KeyStorage
import kotlinx.coroutines.flow.Flow

class ParticipantRepository(
    private val dao: com.yanparker.modelforum.data.db.ParticipantDao,
    private val keys: KeyStorage,
) {

    fun all(): Flow<List<ParticipantEntity>> = dao.all()
    suspend fun allOnce(): List<ParticipantEntity> = dao.allOnce()
    suspend fun byId(id: Long): ParticipantEntity? = dao.byId(id)

    suspend fun add(
        providerId: String,
        name: String,
        modelId: String,
        apiKey: String?,
        colorIndex: Int,
        customBaseUrl: String = "",
        customChatPath: String = "",
        customModelsPath: String = "",
        judgePriority: Int = 0,
    ): Long {
        val keyRef = if (apiKey.isNullOrBlank()) "" else keys.putKey(apiKey)
        return dao.insert(
            ParticipantEntity(
                providerId = providerId,
                name = name,
                modelId = modelId,
                keyRef = keyRef,
                customBaseUrl = customBaseUrl,
                customChatPath = customChatPath,
                customModelsPath = customModelsPath,
                colorIndex = colorIndex,
                judgePriority = judgePriority,
            )
        )
    }

    suspend fun update(
        id: Long,
        name: String,
        modelId: String,
        colorIndex: Int,
        customBaseUrl: String = "",
        customChatPath: String = "",
        customModelsPath: String = "",
    ) = dao.updateCustom(id, name, modelId, colorIndex, customBaseUrl, customChatPath, customModelsPath)

    suspend fun updateKey(id: Long, apiKey: String) {
        val p = dao.byId(id) ?: return
        if (p.keyRef.isNotBlank()) keys.removeKey(p.keyRef)
        val ref = keys.putKey(apiKey)
        dao.insert(p.copy(keyRef = ref))
    }

    suspend fun setEnabled(id: Long, enabled: Boolean) = dao.setEnabled(id, enabled)

    suspend fun setBlocked(id: Long, until: Long, reason: String) =
        dao.setBlocked(id, until, reason)

    suspend fun clearBlocked(id: Long) = dao.setBlocked(id, 0, "")

    suspend fun incrementDailyRequests(id: Long, day: String) {
        val p = dao.byId(id) ?: return
        val count = if (p.dailyDay == day) p.dailyRequests + 1 else 1
        dao.setDailyRequests(id, count, day)
    }

    suspend fun delete(id: Long) {
        val p = dao.byId(id) ?: return
        if (p.keyRef.isNotBlank()) keys.removeKey(p.keyRef)
        dao.delete(id)
    }
}

class DiscussionRepository(private val dao: com.yanparker.modelforum.data.db.DiscussionDao) {
    fun all(): Flow<List<com.yanparker.modelforum.data.db.DiscussionEntity>> = dao.all()
    fun active(): Flow<List<com.yanparker.modelforum.data.db.DiscussionEntity>> = dao.active()
    fun byId(id: Long): Flow<com.yanparker.modelforum.data.db.DiscussionEntity?> = dao.byId(id)
    suspend fun byIdOnce(id: Long): com.yanparker.modelforum.data.db.DiscussionEntity? = dao.byIdOnce(id)
    suspend fun allOnce(): List<com.yanparker.modelforum.data.db.DiscussionEntity> = dao.allOnce()
    suspend fun insert(d: com.yanparker.modelforum.data.db.DiscussionEntity): Long = dao.insert(d)
    suspend fun setState(id: Long, state: String) = dao.setState(id, state)
    suspend fun setErrorNote(id: Long, note: String) = dao.setErrorNote(id, note)
    suspend fun delete(id: Long) = dao.delete(id)
}

class MessageRepository(private val dao: com.yanparker.modelforum.data.db.MessageDao) {
    fun forDiscussion(id: Long): Flow<List<com.yanparker.modelforum.data.db.MessageEntity>> = dao.forDiscussion(id)
    suspend fun forDiscussionOnce(id: Long): List<com.yanparker.modelforum.data.db.MessageEntity> = dao.forDiscussionOnce(id)
    suspend fun allOnce(): List<com.yanparker.modelforum.data.db.MessageEntity> = dao.allOnce()
    suspend fun insert(m: com.yanparker.modelforum.data.db.MessageEntity): Long = dao.insert(m)
    suspend fun updateText(id: Long, text: String) = dao.updateText(id, text)
    suspend fun update(id: Long, text: String, status: String, tokens: Long) = dao.update(id, text, status, tokens)
    suspend fun countByParticipant(discussionId: Long, participantId: Long): Int =
        dao.countByParticipant(discussionId, participantId)
    suspend fun deleteForDiscussion(id: Long) = dao.deleteForDiscussion(id)
}