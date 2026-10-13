package com.watchworld.data.repository

import com.watchworld.data.db.withTransaction
import com.watchworld.data.db.WwDatabase
import com.watchworld.data.db.toEntity
import com.watchworld.data.db.toModel
import com.watchworld.model.ChatMessage
import com.watchworld.model.ChatSession
import com.watchworld.platform.currentTimeMillis

/**
 * 会话（含消息 / 时间线 / 总结）的唯一读写入口。
 *
 * ### 三条纪律，改这个类之前先读（MIGRATION_CONSTRAINTS §5）
 *
 * 1. **UI 不许绕过本类碰 DAO**。DAO 只在 `data/` 内出现。
 * 2. **写操作一律 `suspend`**，并且**在同一事务内**更新 `updatedAt` —— 同步引擎（T19）
 *    靠 `updatedAt` 算增量，晚一步或漏一步就会漏同步。
 * 3. **一个会话横跨四张表**（`sessions` / `messages` / `timelines` / `summaries`），
 *    任何一次写都必须把四张表放在同一个 `withTransaction` 里，否则读者会看到
 *    「标题改了、消息还是旧的」这种中间态。
 *
 * ### 为什么 `updatedAt` 由本类盖，而不是调用方传
 *
 * Web 版是调用方在 `App.tsx` 里 `updatedAt: Date.now()` 的。原生版收上来：
 * 「改会话」和「盖时间戳」拆成两步，就一定会有人只做前一步（T06 的 `version` 消失事故
 * 就是这么来的）。本类的每个写方法**自己盖戳并返回盖好的对象**，调用方拿返回值更新内存副本。
 *
 * @param now 时间戳来源。测试传固定值，让 `updatedAt` 断言可复现。
 */
class SessionRepository(
    private val db: WwDatabase,
    private val now: () -> Long = ::currentTimeMillis,
) {

    /** 全部会话，按存档数组顺序。四条查询一次取完再按 `sessionId` 归并，不做 N+1。 */
    suspend fun all(): List<ChatSession> = db.withTransaction {
        val sessions = db.sessionDao().all()
        if (sessions.isEmpty()) return@withTransaction emptyList()
        val messages = db.messageDao().all().groupBy { it.sessionId }
        val timelines = db.timelineDao().all().groupBy { it.sessionId }
        val summaries = db.summaryDao().all().groupBy { it.sessionId }
        sessions.map { row ->
            row.toModel(
                messages = messages[row.id].orEmpty().map { it.toModel() },
                timelines = timelines[row.id].orEmpty().map { it.toModel() },
                summaries = summaries[row.id].orEmpty().map { it.toModel() },
            )
        }
    }

    /** 单个会话；不存在返回 `null`。 */
    suspend fun byId(id: String): ChatSession? = db.withTransaction {
        val row = db.sessionDao().byId(id) ?: return@withTransaction null
        row.toModel(
            messages = db.messageDao().bySession(id).map { it.toModel() },
            timelines = db.timelineDao().bySession(id).map { it.toModel() },
            summaries = db.summaryDao().bySession(id).map { it.toModel() },
        )
    }

    /**
     * 整棵会话写回（含增删消息），并盖 `updatedAt`。
     *
     * 子表用「先删后插」而不是逐条 diff：会话的消息列表本来就是**权威的全量**，
     * diff 只会引入一类「删掉的版本没删掉」的错。几千条消息全删全插在 SQLite 里是毫秒级，
     * 而这条路径只在「手动保存会话」时走 —— 逐条追加走 [appendMessage]。
     *
     * @return 盖好时间戳的会话；调用方应拿它更新内存里的副本。
     */
    suspend fun save(session: ChatSession): ChatSession = db.withTransaction {
        val stamped = session.copy(updatedAt = now())
        writeSession(stamped, sortIndexOf(stamped.id))
        stamped
    }

    /** 新建会话。与 [save] 等价（id 已存在则覆盖），单独留一个名字是为了让调用点读得懂。 */
    suspend fun create(session: ChatSession): ChatSession = save(session)

    /**
     * 在会话尾部追加一条消息，并在**同一个事务**里刷新会话的 `updatedAt`。
     *
     * 这是聊天主路径用的写操作（PLAN §2.2 把 `messages` 拆出来的直接受益点）：
     * 不重写整个会话的消息数组，只插一行 + 更新一次 `updatedAt`。
     *
     * ⚠️ 按「追加」语义实现：`sortIndex` 取当前条数，因此**同一 id 重复调用会写出重复消息**。
     * 覆盖已有消息请走 [save]。
     */
    suspend fun appendMessage(sessionId: String, message: ChatMessage): Long = db.withTransaction {
        val index = db.messageDao().countForSession(sessionId)
        db.messageDao().upsertAll(listOf(message.toEntity(sessionId, index)))
        val stamp = now()
        db.sessionDao().touch(sessionId, stamp)
        stamp
    }

    /** 删除会话及其消息 / 时间线 / 总结。先删子表再删主表，不依赖 FK 级联是否打开。 */
    suspend fun delete(id: String) = db.withTransaction {
        db.messageDao().deleteForSession(id)
        db.timelineDao().deleteForSession(id)
        db.summaryDao().deleteForSession(id)
        db.sessionDao().delete(id)
    }

    /**
     * 重命名。**不**调用 [ChatSession] 的其它字段，只改标题一列 + 盖戳 ——
     * 侧边栏改个名不该把整个会话（几千条消息）读出来再写回去。
     */
    suspend fun rename(id: String, title: String): Long = db.withTransaction {
        val stamp = now()
        db.sessionDao().rename(id, title, stamp)
        stamp
    }

    /** 置顶切换。语义同 [rename]：单列更新 + 盖戳。 */
    suspend fun setPinned(id: String, pinned: Boolean?): Long = db.withTransaction {
        val stamp = now()
        db.sessionDao().setPinned(id, pinned, stamp)
        stamp
    }

    /** 移动到分组。语义同 [rename]。 */
    suspend fun moveToGroup(id: String, groupId: String): Long = db.withTransaction {
        val stamp = now()
        db.sessionDao().moveToGroup(id, groupId, stamp)
        stamp
    }

    /**
     * 整批替换全部会话。**导入存档专用**。
     *
     * 不做增量合并：`importAllData` 在 Web 版就是「按分类整体接管」，逐条合并会让
     * 「导入后本该消失的会话还留着」。整段在一个事务里，中途失败即整体回滚，不会留下半个存档。
     */
    suspend fun replaceAll(sessions: List<ChatSession>) = db.withTransaction {
        db.messageDao().clear()
        db.timelineDao().clear()
        db.summaryDao().clear()
        db.sessionDao().clear()
        sessions.forEachIndexed { index, session -> writeSession(session, index) }
    }

    private suspend fun writeSession(session: ChatSession, sortIndex: Int) {
        db.sessionDao().upsert(session.toEntity(sortIndex))
        db.messageDao().deleteForSession(session.id)
        db.timelineDao().deleteForSession(session.id)
        db.summaryDao().deleteForSession(session.id)
        if (session.messages.isNotEmpty()) {
            db.messageDao().upsertAll(session.messages.mapIndexed { i, m -> m.toEntity(session.id, i) })
        }
        session.timelines?.takeIf { it.isNotEmpty() }?.let { timelines ->
            db.timelineDao().upsertAll(timelines.mapIndexed { i, t -> t.toEntity(session.id, i) })
        }
        session.summaries?.takeIf { it.isNotEmpty() }?.let { summaries ->
            db.summaryDao().upsertAll(summaries.mapIndexed { i, s -> s.toEntity(session.id, i) })
        }
    }

    /** 已有会话保持原位；新会话追加到末尾（与 Web 版 `setSessions([...sessions, next])` 一致）。 */
    private suspend fun sortIndexOf(sessionId: String): Int =
        db.sessionDao().byId(sessionId)?.sortIndex ?: db.sessionDao().count()
}
