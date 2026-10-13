package com.watchworld.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

/*
 * 配置类表的 DAO。
 *
 * ### 为什么每张表只有 `all` / `replaceAll` / `clear` 三个方法
 *
 * 这是**照着 Web 版的用法来的**，不是偷懒。看 `App.tsx` 的 handler：`handleSaveProvider`
 * 与 `handleDeleteProvider` 最后都落到 `Storage.setProviders(下一个完整数组)` ——
 * 增、删、改在 Web 版里是同一件事：**整份集合写回去**。原生版照做，
 * 「读出来 → 改 → 整份写回」这条路径完全一致，也就不存在两版行为漂移。
 *
 * 需要「只改一条」时，用 [com.watchworld.data.repository.ConfigRepository.transaction] 包住
 * `getXxx() → 改 → setXxx()`：整段在一个事务里跑，不会与并发写打架。
 * （Web 版的 React state 就是这个「读改写」的持有者，原生版由 ViewModel 或这个事务承担。）
 *
 * ### 顺序
 *
 * 每个 `all()` 都带 `ORDER BY sortIndex`。见 `ConfigEntities.kt` 顶部关于 `sortIndex` 的说明 ——
 * SQLite 不保证行序，忘了排序就是「字段都对、顺序变了」。
 */

@Dao
interface ProviderDao {
    @Query("SELECT * FROM providers ORDER BY sortIndex")
    suspend fun all(): List<ProviderEntity>

    @Upsert
    suspend fun upsertAll(rows: List<ProviderEntity>)

    @Query("DELETE FROM providers")
    suspend fun clear()
}

@Dao
interface AgentDao {
    @Query("SELECT * FROM agents ORDER BY sortIndex")
    suspend fun all(): List<AgentEntity>

    @Upsert
    suspend fun upsertAll(rows: List<AgentEntity>)

    @Query("DELETE FROM agents")
    suspend fun clear()
}

@Dao
interface PromptDao {
    @Query("SELECT * FROM prompts ORDER BY sortIndex")
    suspend fun all(): List<PromptEntity>

    @Upsert
    suspend fun upsertAll(rows: List<PromptEntity>)

    @Query("DELETE FROM prompts")
    suspend fun clear()
}

@Dao
interface RegexRuleDao {
    @Query("SELECT * FROM regexRules ORDER BY sortIndex")
    suspend fun all(): List<RegexRuleEntity>

    @Upsert
    suspend fun upsertAll(rows: List<RegexRuleEntity>)

    @Query("DELETE FROM regexRules")
    suspend fun clear()
}

@Dao
interface KnowledgeDao {
    @Query("SELECT * FROM knowledgeBase ORDER BY sortIndex")
    suspend fun all(): List<KnowledgeEntity>

    @Upsert
    suspend fun upsertAll(rows: List<KnowledgeEntity>)

    @Query("DELETE FROM knowledgeBase")
    suspend fun clear()
}

@Dao
interface McpServerDao {
    @Query("SELECT * FROM mcpServers ORDER BY sortIndex")
    suspend fun all(): List<McpServerEntity>

    @Upsert
    suspend fun upsertAll(rows: List<McpServerEntity>)

    @Query("DELETE FROM mcpServers")
    suspend fun clear()
}

@Dao
interface SkillDao {
    @Query("SELECT * FROM skills ORDER BY sortIndex")
    suspend fun all(): List<SkillEntity>

    @Upsert
    suspend fun upsertAll(rows: List<SkillEntity>)

    @Query("DELETE FROM skills")
    suspend fun clear()
}

@Dao
interface WorldGroupDao {
    @Query("SELECT * FROM worldGroups ORDER BY sortIndex")
    suspend fun all(): List<WorldGroupEntity>

    @Upsert
    suspend fun upsertAll(rows: List<WorldGroupEntity>)

    @Query("DELETE FROM worldGroups")
    suspend fun clear()
}

@Dao
interface WorldDocumentDao {
    @Query("SELECT * FROM worldDocuments ORDER BY sortIndex")
    suspend fun all(): List<WorldDocumentEntity>

    @Upsert
    suspend fun upsertAll(rows: List<WorldDocumentEntity>)

    @Query("DELETE FROM worldDocuments")
    suspend fun clear()
}

@Dao
interface CustomThemeDao {
    @Query("SELECT * FROM customThemes ORDER BY sortIndex")
    suspend fun all(): List<CustomThemeEntity>

    @Upsert
    suspend fun upsertAll(rows: List<CustomThemeEntity>)

    @Query("DELETE FROM customThemes")
    suspend fun clear()
}

@Dao
interface ModelFavoriteDao {
    @Query("SELECT * FROM modelFavorites ORDER BY sortIndex")
    suspend fun all(): List<ModelFavoriteEntity>

    @Upsert
    suspend fun upsertAll(rows: List<ModelFavoriteEntity>)

    @Query("DELETE FROM modelFavorites")
    suspend fun clear()
}

@Dao
interface ChatGroupDao {
    // `groups` 加反引号：GROUP 是 SQL 关键字，虽然复数形式本身不保留，显式标注更省事。
    @Query("SELECT * FROM `groups` ORDER BY sortIndex")
    suspend fun all(): List<ChatGroupEntity>

    @Upsert
    suspend fun upsertAll(rows: List<ChatGroupEntity>)

    @Query("DELETE FROM `groups`")
    suspend fun clear()
}
