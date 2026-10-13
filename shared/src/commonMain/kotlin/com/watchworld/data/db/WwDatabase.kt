package com.watchworld.data.db

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.watchworld.platform.createSqliteDriver
import com.watchworld.platform.sqliteQueryDispatcher

/**
 * 本地 SQLite 库（PLAN §5.1 的 17 张表）。
 *
 * ### 版本与迁移
 *
 * **v1 → v2（T18）**：`worldDocuments` 新增可空列 `legacyGroup`（老存档的 `group` 组名字符串）。
 * 这条迁移不能省 —— 见「心得：为什么开发期的加列也必须写 Migration」。
 *
 * 从 v2 起，**每次改表都必须走标准流程**（**不要**用 `fallbackToDestructiveMigration`，
 * 那等于删用户数据）：
 *
 * 1. 改实体 → 跑一次 `:shared:compileKotlinDesktop`，`shared/schemas/` 下会多出新版本 JSON；
 * 2. 写 `Migration(old, new)` + 在 [open] 的 builder 上 `.addMigrations(...)`，并用
 *    `MigrationTestHelper` 针对两份 schema 写迁移测试；
 * 3. 两个 schema JSON 都要提交（**它们是迁移测试的输入**，删了就没法验证）。
 *
 * ### ⚠️ 为什么开发期的加列也必须写 Migration（T18 实测踩到）
 *
 * 「库还是 v1、从未发布，直接改表不用迁移」这句话**只对全新的空库成立**。
 * 真实情况是：开发机上 `%APPDATA%\WatchWorld\watchworld.db` 早就是 v1 结构了，
 * 改完实体再启动，Room 校验 identity hash 不通过，直接抛
 * `Room cannot verify the data integrity … Expected identity hash: … found: …` 弹窗崩溃。
 * 判据是 **「本机/用户机上有没有已存在的库文件」**，不是「这个版本发布过没有」。
 *
 * `shared/schemas/` 是**要提交**的资产（`.gitignore` 没有忽略它）。它相当于存档格式的
 * 同级契约：没有它，将来没有任何依据能证明迁移写对了。
 *
 * ### 为什么 DAO 全部是 `abstract fun` 而不是构造函数注入
 *
 * Room 生成实现，这是它唯一支持的写法。**单一入口是 Repository 而不是这里**：
 * DAO 仅供 `data/repository/` 使用，UI 层不得直接拿（CONSTRAINTS §5 第 1 条）。
 */
@Database(
    entities = [
        // 配置类
        ProviderEntity::class,
        AgentEntity::class,
        PromptEntity::class,
        RegexRuleEntity::class,
        KnowledgeEntity::class,
        McpServerEntity::class,
        SkillEntity::class,
        WorldGroupEntity::class,
        WorldDocumentEntity::class,
        CustomThemeEntity::class,
        ModelFavoriteEntity::class,
        ChatGroupEntity::class,
        // 核心
        SessionEntity::class,
        MessageEntity::class,
        TimelineEntity::class,
        SummaryEntity::class,
        // 杂项
        KvEntity::class,
    ],
    version = 2,
)
@TypeConverters(WwConverters::class)
abstract class WwDatabase : RoomDatabase() {

    abstract fun providerDao(): ProviderDao
    abstract fun agentDao(): AgentDao
    abstract fun promptDao(): PromptDao
    abstract fun regexRuleDao(): RegexRuleDao
    abstract fun knowledgeDao(): KnowledgeDao
    abstract fun mcpServerDao(): McpServerDao
    abstract fun skillDao(): SkillDao
    abstract fun worldGroupDao(): WorldGroupDao
    abstract fun worldDocumentDao(): WorldDocumentDao
    abstract fun customThemeDao(): CustomThemeDao
    abstract fun modelFavoriteDao(): ModelFavoriteDao
    abstract fun chatGroupDao(): ChatGroupDao

    abstract fun sessionDao(): SessionDao
    abstract fun messageDao(): MessageDao
    abstract fun timelineDao(): TimelineDao
    abstract fun summaryDao(): SummaryDao

    abstract fun kvDao(): KvDao

    companion object {

        /** 数据库文件名。放在 `appDataDir()` 下。 */
        const val FILE_NAME: String = "watchworld.db"

        /**
         * 打开（必要时创建）指定路径上的库。
         *
         * ⚠️ 参数是**文件全路径**，不是「库名」。KMP 版 Room 在 JVM/Android 上把它当路径用，
         * 传裸名字会在**当前工作目录**下建文件 —— 那就是 `FILE_LAYOUT.md` 说的
         * 「文件跑到别的盘、构建还报成功」那类事故的翻版。
         *
         * ⚠️ **每次 bump 版本都必须在这里 `.addMigrations(...)`**。漏了会以
         * `IllegalStateException: A migration from N to N+1 was required but not found` 崩溃
         * （没有配 `fallbackToDestructiveMigration`，这是有意的）。
         *
         * @param databasePath 由 `appDataDir()` 拼出来（真实运行），或测试给的临时路径。
         */
        fun open(databasePath: String): WwDatabase =
            Room.databaseBuilder<WwDatabase>(name = databasePath)
                .setDriver(createSqliteDriver())
                // SQLite 调用是阻塞式的，必须落在 IO 类调度器上，不能占 UI 线程。
                .setQueryCoroutineContext(sqliteQueryDispatcher)
                .addMigrations(MIGRATION_1_2)
                .build()

        /**
         * 内存库。只给测试用：**关掉就没了**，不能用来验证「重启后还在」。
         * 真实的持久化验证请用 [open] + 临时文件 + `close()` 后重开。
         */
        fun openInMemory(): WwDatabase =
            Room.inMemoryDatabaseBuilder<WwDatabase>()
                .setDriver(createSqliteDriver())
                .setQueryCoroutineContext(sqliteQueryDispatcher)
                .build()
    }
}
