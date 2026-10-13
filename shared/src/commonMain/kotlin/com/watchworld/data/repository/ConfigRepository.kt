package com.watchworld.data.repository

import com.watchworld.data.db.withTransaction
import com.watchworld.data.db.WwDatabase
import com.watchworld.data.db.toEntity
import com.watchworld.data.db.toModel
import com.watchworld.domain.resolveWorldDocumentGroupIds
import com.watchworld.model.Agent
import com.watchworld.model.AgentSkill
import com.watchworld.model.ApiProviderConfig
import com.watchworld.model.ChatGroup
import com.watchworld.model.CustomTheme
import com.watchworld.model.KnowledgeItem
import com.watchworld.model.McpServerConfig
import com.watchworld.model.ModelFavorite
import com.watchworld.model.PromptPreset
import com.watchworld.model.RegexRule
import com.watchworld.model.WorldDocument
import com.watchworld.model.WorldGroup

/**
 * 配置类集合（12 张表）的唯一读写入口。
 *
 * ### 读 / 写是成对的 `xxx()` / `setXxx()`，对齐 Web 版的 `Storage.getXxx/setXxx`
 *
 * Web 版把每个集合整个塞进一个 localStorage 键，增删改最后都落到「整份数组写回去」
 * （`handleSaveProvider`、`handleDeleteProvider` 都是 `Storage.setProviders(下一个数组)`）。
 * 这里保持同一形状：**`setXxx` 是全量替换**，不是增量合并。
 *
 * 于是有个必须知道的后果：`setXxx(list)` 会**删掉库里不在 list 里的条目**。
 * 只想改一条时，别在别处拼一个新数组，用 [transaction] 包住 `xxx() → 改 → setXxx()`。
 * 少了这层事务，两个并发写会互相吃掉对方的改动（Web 版靠单线程 UI 天然免了这个问题，原生版没有）。
 *
 * ### 不在本类里的集合
 *
 * - 会话 / 消息 / 时间线 / 总结 → [SessionRepository]
 * - 主题调色板 / 明暗 / 显示设置 / 偏好 / WebDAV / 草稿这些**单体**设置 → [SettingsRepository]
 */
class ConfigRepository(private val db: WwDatabase) {

    /**
     * 把一段「读改写」放进一个事务。
     *
     * ```kotlin
     * config.transaction {
     *     config.setProviders(config.providers().filterNot { it.id == id })
     * }
     * ```
     */
    suspend fun <T> transaction(block: suspend () -> T): T = db.withTransaction { block() }

    // ---------------- Providers ----------------

    suspend fun providers(): List<ApiProviderConfig> = db.providerDao().all().map { it.toModel() }

    suspend fun setProviders(items: List<ApiProviderConfig>) = db.withTransaction {
        db.providerDao().clear()
        if (items.isNotEmpty()) {
            db.providerDao().upsertAll(items.mapIndexed { i, item -> item.toEntity(i) })
        }
    }

    // ---------------- Agents ----------------

    suspend fun agents(): List<Agent> = db.agentDao().all().map { it.toModel() }

    suspend fun setAgents(items: List<Agent>) = db.withTransaction {
        db.agentDao().clear()
        if (items.isNotEmpty()) {
            db.agentDao().upsertAll(items.mapIndexed { i, item -> item.toEntity(i) })
        }
    }

    // ---------------- Prompts ----------------

    suspend fun prompts(): List<PromptPreset> = db.promptDao().all().map { it.toModel() }

    suspend fun setPrompts(items: List<PromptPreset>) = db.withTransaction {
        db.promptDao().clear()
        if (items.isNotEmpty()) {
            db.promptDao().upsertAll(items.mapIndexed { i, item -> item.toEntity(i) })
        }
    }

    // ---------------- Regex rules ----------------

    suspend fun regexRules(): List<RegexRule> = db.regexRuleDao().all().map { it.toModel() }

    suspend fun setRegexRules(items: List<RegexRule>) = db.withTransaction {
        db.regexRuleDao().clear()
        if (items.isNotEmpty()) {
            db.regexRuleDao().upsertAll(items.mapIndexed { i, item -> item.toEntity(i) })
        }
    }

    // ---------------- Knowledge base ----------------

    suspend fun knowledgeBase(): List<KnowledgeItem> = db.knowledgeDao().all().map { it.toModel() }

    suspend fun setKnowledgeBase(items: List<KnowledgeItem>) = db.withTransaction {
        db.knowledgeDao().clear()
        if (items.isNotEmpty()) {
            db.knowledgeDao().upsertAll(items.mapIndexed { i, item -> item.toEntity(i) })
        }
    }

    // ---------------- MCP servers ----------------

    suspend fun mcpServers(): List<McpServerConfig> = db.mcpServerDao().all().map { it.toModel() }

    suspend fun setMcpServers(items: List<McpServerConfig>) = db.withTransaction {
        db.mcpServerDao().clear()
        if (items.isNotEmpty()) {
            db.mcpServerDao().upsertAll(items.mapIndexed { i, item -> item.toEntity(i) })
        }
    }

    // ---------------- Skills ----------------

    suspend fun skills(): List<AgentSkill> = db.skillDao().all().map { it.toModel() }

    suspend fun setSkills(items: List<AgentSkill>) = db.withTransaction {
        db.skillDao().clear()
        if (items.isNotEmpty()) {
            db.skillDao().upsertAll(items.mapIndexed { i, item -> item.toEntity(i) })
        }
    }

    // ---------------- World groups ----------------

    suspend fun worldGroups(): List<WorldGroup> = db.worldGroupDao().all().map { it.toModel() }

    suspend fun setWorldGroups(items: List<WorldGroup>) = db.withTransaction {
        db.worldGroupDao().clear()
        if (items.isNotEmpty()) {
            db.worldGroupDao().upsertAll(items.mapIndexed { i, item -> item.toEntity(i) })
        }
    }

    // ---------------- World documents ----------------

    /**
     * 世界观文档（含分组）。
     *
     * ⚠️ 这里**必须跑一遍** [resolveWorldDocumentGroupIds]：老存档里的文档用 `group`（**组名字符串**）
     * 而不是 `groupId`，而 Web 版同样是在 `getWorldDocuments()` 读的时候就做这个迁移
     * （`storage.ts:360-373`）。导入路径也走本函数，所以「导入老存档 → 重启」后文档就已经归好组。
     *
     * 迁移是**纯函数、不改库**：老 `group` 只在读的瞬间起作用，写回时由 Mapper 清掉。
     */
    suspend fun worldDocuments(): List<WorldDocument> = resolveWorldDocumentGroupIds(
        db.worldDocumentDao().all().map { it.toModel() },
        db.worldGroupDao().all().map { it.toModel() },
    )

    suspend fun setWorldDocuments(items: List<WorldDocument>) = db.withTransaction {
        db.worldDocumentDao().clear()
        if (items.isNotEmpty()) {
            db.worldDocumentDao().upsertAll(items.mapIndexed { i, item -> item.toEntity(i) })
        }
    }

    // ---------------- Custom themes ----------------

    /**
     * 用户 DIY 主题。
     *
     * ⚠️ 这一组**不进 v5 存档**（Web 版存在独立键 `guanjie_custom_themes`），
     * 所以 [ArchiveStore] 不会碰它，它也就不会随导出/导入消失。这是有意的。
     */
    suspend fun customThemes(): List<CustomTheme> = db.customThemeDao().all().map { it.toModel() }

    suspend fun setCustomThemes(items: List<CustomTheme>) = db.withTransaction {
        db.customThemeDao().clear()
        if (items.isNotEmpty()) {
            db.customThemeDao().upsertAll(items.mapIndexed { i, item -> item.toEntity(i) })
        }
    }

    // ---------------- Model favorites ----------------

    suspend fun modelFavorites(): List<ModelFavorite> = db.modelFavoriteDao().all().map { it.toModel() }

    suspend fun setModelFavorites(items: List<ModelFavorite>) = db.withTransaction {
        db.modelFavoriteDao().clear()
        if (items.isNotEmpty()) {
            db.modelFavoriteDao().upsertAll(items.mapIndexed { i, item -> item.toEntity(i) })
        }
    }

    // ---------------- Chat groups ----------------

    suspend fun groups(): List<ChatGroup> = db.chatGroupDao().all().map { it.toModel() }

    suspend fun setGroups(items: List<ChatGroup>) = db.withTransaction {
        db.chatGroupDao().clear()
        if (items.isNotEmpty()) {
            db.chatGroupDao().upsertAll(items.mapIndexed { i, item -> item.toEntity(i) })
        }
    }
}
