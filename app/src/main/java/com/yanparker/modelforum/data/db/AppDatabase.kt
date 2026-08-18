package com.yanparker.modelforum.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import android.content.Context
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "participants")
data class ParticipantEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val providerId: String,
    val name: String,
    val modelId: String,
    val keyRef: String = "",
    val customBaseUrl: String = "",
    val customChatPath: String = "",
    val customModelsPath: String = "",
    val colorIndex: Int = 0,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val blockedUntil: Long = 0L,
    val blockReason: String = "",
    val dailyRequests: Long = 0L,
    val dailyDay: String = "",
    val judgePriority: Int = 0,
)

@Entity(tableName = "discussions")
data class DiscussionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val mode: String = "forum",
    val state: String = "idle",
    val question: String = "",
    val judgeId: Long = 0,
    val participantIds: String = "",
    val maxMessagesPerModel: Int = 15,
    val maxTokens: Int = 800,
    val temperature: Double = 0.7,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val errorNote: String = "",
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val discussionId: Long,
    val participantId: Long = 0,
    val role: String = "assistant",
    val text: String = "",
    val status: String = "done",
    val tokens: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

@Dao
interface ParticipantDao {
    @Query("SELECT * FROM participants ORDER BY createdAt")
    fun all(): Flow<List<ParticipantEntity>>

    @Query("SELECT * FROM participants")
    suspend fun allOnce(): List<ParticipantEntity>

    @Query("SELECT * FROM participants WHERE id = :id")
    suspend fun byId(id: Long): ParticipantEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(p: ParticipantEntity): Long

    @Query("UPDATE participants SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("UPDATE participants SET name = :name, modelId = :modelId, colorIndex = :colorIndex, customBaseUrl = :baseUrl, customChatPath = :chatPath, customModelsPath = :modelsPath WHERE id = :id")
    suspend fun updateCustom(id: Long, name: String, modelId: String, colorIndex: Int, baseUrl: String, chatPath: String, modelsPath: String)

    @Query(
        "UPDATE participants SET blockedUntil = :until, blockReason = :reason WHERE id = :id"
    )
    suspend fun setBlocked(id: Long, until: Long, reason: String)

    @Query("UPDATE participants SET dailyRequests = :count, dailyDay = :day WHERE id = :id")
    suspend fun setDailyRequests(id: Long, count: Long, day: String)

    @Query("DELETE FROM participants WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface DiscussionDao {
    @Query("SELECT * FROM discussions ORDER BY updatedAt DESC")
    fun all(): Flow<List<DiscussionEntity>>

    @Query("SELECT * FROM discussions WHERE state IN ('running', 'waiting_limits')")
    fun active(): Flow<List<DiscussionEntity>>

    @Query("SELECT * FROM discussions")
    suspend fun allOnce(): List<DiscussionEntity>

    @Query("SELECT * FROM discussions WHERE id = :id")
    fun byId(id: Long): Flow<DiscussionEntity?>

    @Query("SELECT * FROM discussions WHERE id = :id")
    suspend fun byIdOnce(id: Long): DiscussionEntity?

    @Insert
    suspend fun insert(d: DiscussionEntity): Long

    @Query("UPDATE discussions SET state = :state, updatedAt = :ts WHERE id = :id")
    suspend fun setState(id: Long, state: String, ts: Long = System.currentTimeMillis())

    @Query("UPDATE discussions SET errorNote = :note, updatedAt = :ts WHERE id = :id")
    suspend fun setErrorNote(id: Long, note: String, ts: Long = System.currentTimeMillis())

    @Query("DELETE FROM discussions WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE discussionId = :discussionId ORDER BY createdAt, id")
    fun forDiscussion(discussionId: Long): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE discussionId = :discussionId ORDER BY createdAt, id")
    suspend fun forDiscussionOnce(discussionId: Long): List<MessageEntity>

    @Query("SELECT * FROM messages ORDER BY createdAt, id")
    suspend fun allOnce(): List<MessageEntity>

    @Insert
    suspend fun insert(m: MessageEntity): Long

    @Query("UPDATE messages SET text = :text WHERE id = :id")
    suspend fun updateText(id: Long, text: String)

    @Query("UPDATE messages SET text = :text, status = :status, tokens = :tokens WHERE id = :id")
    suspend fun update(id: Long, text: String, status: String, tokens: Long)

    @Query("SELECT COUNT(*) FROM messages WHERE discussionId = :discussionId AND participantId = :participantId")
    suspend fun countByParticipant(discussionId: Long, participantId: Long): Int

    @Query("DELETE FROM messages WHERE discussionId = :discussionId")
    suspend fun deleteForDiscussion(discussionId: Long)
}

@Database(
    entities = [ParticipantEntity::class, DiscussionEntity::class, MessageEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun participantDao(): ParticipantDao
    abstract fun discussionDao(): DiscussionDao
    abstract fun messageDao(): MessageDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "model_forum.db").build()
    }
}