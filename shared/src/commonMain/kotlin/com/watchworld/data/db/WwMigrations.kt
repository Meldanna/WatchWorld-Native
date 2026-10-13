package com.watchworld.data.db

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/*
 * 库结构迁移。房间版本历史与「为什么开发期也要写迁移」见 [WwDatabase] 的 KDoc。
 *
 * ⚠️ 迁移里**只写结构变化**，不写业务规则：
 * 老存档 `group`（组名字符串）→ `groupId`（组 ID）的解析是 `domain/WorldLibrary.resolveWorldDocumentGroupIds`
 * 在读的时候做的，这里只负责让那一列**存在**（否则老 `group` 根本没有地方落）。
 */

/**
 * v1 → v2（T18）：`worldDocuments` 新增可空列 `legacyGroup`。
 *
 * 老存档的文档用 `group`（组名字符串）而不是 `groupId`；这个列是它的落库位置，
 * `ConfigRepository.worldDocuments()` 读到后立刻解析成 `groupId`
 * （对齐 `storage.ts:360-373` 的 `getWorldDocuments`）。
 *
 * 语义完全由 `ALTER TABLE … ADD COLUMN` 完成：新列全部是 `NULL`，
 * 已有数据一个字节都不动 —— 老库里的文档本来就都带 `groupId`（或未分组），
 * 迁移后行为不变。
 *
 * ⚠️ 刻意**不用** `RoomDatabase.Builder.fallbackToDestructiveMigration`：那等于删用户数据。
 * ⚠️ 列声明必须与 `schemas/2.json` 的 `createSql` 逐字对齐：
 *    `legacyGroup TEXT`（可空、无默认值、无 NOT NULL）。
 */
internal val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `worldDocuments` ADD COLUMN `legacyGroup` TEXT")
    }
}
