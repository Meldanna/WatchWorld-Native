package com.watchworld.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

/*
 * 核心表的 DAO。
 *
 * 与配置类表不同，这几张表的写操作**不能**一律「整份替换」：
 * `messages` 在重度会话里是几千行，每次追加一条都重写整个会话的消息是 T07 明确要避免的
 * （PLAN §2.2：这正是把 messages 从 sessions 拆出来的原因）。所以这里按行给方法。
 */

@Dao
interface SessionDao {

    @Query("SELECT * FROM sessions ORDER BY sortIndex")
    suspend fun all(): List<SessionEntity>

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun byId(id: String): SessionEntity?

    /**
     * ⚠️ 只写 `sessions` 一张表。会话内的消息/时间线/总结由各自的 DAO 负责 ——
     * 调用方必须把三张表放在**同一个事务**里改，否则会读到「标题更新了、消息还是旧的」的中间态。
     * [com.watchworld.data.repository.SessionRepository] 已经这么做了，别绕过它直接用本 DAO。
     */
    @Upsert
    suspend fun upsert(row: SessionEntity)

    @Upsert
    suspend fun upsertAll(rows: List<SessionEntity>)

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM sessions")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM sessions")
    suspend fun count(): Int

    /**
     * 会话头字段的**单列更新**。每一条都把 `updatedAt` 一起带上 ——
     * 这就是 CONSTRAINTS §5 第 2 条的「写操作在同一事务内更新 `updatedAt`」，
     * 同步引擎靠它判断增量。把它们拆成「改字段」+「touch」两次调用就破坏了这个约定。
     */
    @Query("UPDATE sessions SET title = :title, updatedAt = :updatedAt WHERE id = :id")
    suspend fun rename(id: String, title: String, updatedAt: Long)

    @Query("UPDATE sessions SET pinned = :pinned, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setPinned(id: String, pinned: Boolean?, updatedAt: Long)

    @Query("UPDATE sessions SET groupId = :groupId, updatedAt = :updatedAt WHERE id = :id")
    suspend fun moveToGroup(id: String, groupId: String, updatedAt: Long)

    /**
     * 只刷 `updatedAt`。给「子表变了、会话头没变」的场景用（例如追加一条消息）：
     * 与子表的 INSERT 在同一个事务里执行，对同步引擎而言就是一次会话变更。
     */
    @Query("UPDATE sessions SET updatedAt = :updatedAt WHERE id = :id")
    suspend fun touch(id: String, updatedAt: Long)

    @Query("SELECT updatedAt FROM sessions WHERE id = :id")
    suspend fun updatedAt(id: String): Long?
}

@Dao
interface MessageDao {

    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY sortIndex")
    suspend fun bySession(sessionId: String): List<MessageEntity>

    /** 导出/导入用：一次取全表，避免按会话逐次查询。调用方自行按 `sessionId` 分组。 */
    @Query("SELECT * FROM messages ORDER BY sessionId, sortIndex")
    suspend fun all(): List<MessageEntity>

    @Query("SELECT COUNT(*) FROM messages WHERE sessionId = :sessionId")
    suspend fun countForSession(sessionId: String): Int

    @Upsert
    suspend fun upsertAll(rows: List<MessageEntity>)

    @Query("DELETE FROM messages WHERE sessionId = :sessionId")
    suspend fun deleteForSession(sessionId: String)

    @Query("DELETE FROM messages")
    suspend fun clear()
}

@Dao
interface TimelineDao {

    @Query("SELECT * FROM timelines WHERE sessionId = :sessionId ORDER BY sortIndex")
    suspend fun bySession(sessionId: String): List<TimelineEntity>

    @Query("SELECT * FROM timelines ORDER BY sessionId, sortIndex")
    suspend fun all(): List<TimelineEntity>

    @Upsert
    suspend fun upsertAll(rows: List<TimelineEntity>)

    @Query("DELETE FROM timelines WHERE sessionId = :sessionId")
    suspend fun deleteForSession(sessionId: String)

    @Query("DELETE FROM timelines")
    suspend fun clear()
}

@Dao
interface SummaryDao {

    @Query("SELECT * FROM summaries WHERE sessionId = :sessionId ORDER BY sortIndex")
    suspend fun bySession(sessionId: String): List<SummaryEntity>

    @Query("SELECT * FROM summaries ORDER BY sessionId, sortIndex")
    suspend fun all(): List<SummaryEntity>

    @Upsert
    suspend fun upsertAll(rows: List<SummaryEntity>)

    @Query("DELETE FROM summaries WHERE sessionId = :sessionId")
    suspend fun deleteForSession(sessionId: String)

    @Query("DELETE FROM summaries")
    suspend fun clear()
}

/**
 * `kv` 的 DAO。
 *
 * ⚠️ `key` 是 SQL 关键字，@Query 里**必须**自己加反引号（Room 只会给它生成的
 * 实体列名加，不会改写你手写的 SQL）。`value` 不是关键字，可以不写。
 */
@Dao
interface KvDao {

    @Query("SELECT value FROM kv WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Query("SELECT * FROM kv")
    suspend fun all(): List<KvEntity>

    @Upsert
    suspend fun upsert(row: KvEntity)

    @Query("DELETE FROM kv WHERE `key` = :key")
    suspend fun delete(key: String)

    @Query("DELETE FROM kv")
    suspend fun clear()
}
