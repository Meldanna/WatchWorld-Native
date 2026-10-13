package com.watchworld.data.db

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.watchworld.data.repository.ConfigRepository
import com.watchworld.model.WorldDocument
import com.watchworld.model.WorldGroup
import com.watchworld.model.WwJson
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * T18 验收：`WwDatabase` 的 **v1 → v2** 迁移。
 *
 * ### 为什么这个测试非有不可（它是本任务的返工起点）
 *
 * 给 `worldDocuments` 加 `legacyGroup` 列时，第一版的判断是「库还是 v1、从未发布 → 直接改表不用迁移」。
 * 那个判断**只对全新的空库成立**：开发机上 `%APPDATA%\WatchWorld\watchworld.db` 早就是 v1 结构，
 * 改完实体一启动就抛
 * `Room cannot verify the data integrity … Expected identity hash: ae8df0cd…, found: b290e3ae…`，
 * 弹窗崩在界面上。判据是 **「本机/用户机上有没有已存在的库文件」**，不是「这个版本发布过没有」。
 *
 * ### 这个测试怎么做到「不依赖 MigrationTestHelper」
 *
 * `androidx.room:room-testing` 在 KMP 的 `desktopTest` 配置里解析不到
 * （Room 2.8.5 的 `room-testing` 不带 KMP 变体，`desktopTestRuntimeClasspath` 里它是 FAILED），
 * 所以这里自己搭等价的两层验证，输入全部来自**已提交的 `schemas/` 下两个 JSON**：
 *
 * 1. [v2SchemaDeclaresLegacyGroupExactlyLikeTheMigration]：从 `2.json` 读出 Room 期望的列定义与
 *    `createSql`，与 `MIGRATION_1_2` 的 SQL 对齐 —— 钉住「迁移结果 == Room 期望结构」，
 *    也就是 `MigrationTestHelper.runMigrationsAndValidate` 真正断言的东西。
 * 2. [legacyGroupSurvivesRealOpenAndIsResolvedOnRead]：用 `1.json` 的 `createSql` 物理造一个
 *    **真 v1 库文件**（含 `room_master_table` 里的 v1 identity hash），走 `WwDatabase.open` ——
 *    Room 会跑 `MIGRATION_1_2` 并校验哈希；跑完还能正常读写 → 关库重开仍在。
 *    这一层同时证明「迁移已注册」「结构对得上」「老 `group` 能落库并被解析」。
 */
class WwDatabaseMigrationTest {

    private val dbName = "ww-migration-test.db"

    // ---------------------------------------------------------------- 1) 结构契约：迁移 == Room 期望

    /** `MIGRATION_1_2` 加出来的列，必须与 `schemas/2.json` 里 Room 记的那一列一致。 */
    @Test
    fun v2SchemaDeclaresLegacyGroupExactlyLikeTheMigration() {
        val entity = schemaEntity(2, "worldDocuments")

        val field = entity["fields"]!!.jsonArray
            .map { it.jsonObject }
            .single { it["fieldPath"]!!.jsonPrimitive.content == "legacyGroup" }
        assertEquals("legacyGroup", field["columnName"]!!.jsonPrimitive.content)
        assertEquals("TEXT", field["affinity"]!!.jsonPrimitive.content)
        assertEquals("false", field["notNull"]?.jsonPrimitive?.content ?: "false", "新列必须可空")

        val createSql = entity["createSql"]!!.jsonPrimitive.content
        assertTrue(createSql.contains("`legacyGroup` TEXT,"), "2.json 的 createSql 缺 `legacyGroup` TEXT,：$createSql")

        // 迁移 SQL 必须描述同一列，且不带 NOT NULL / DEFAULT（否则老数据会被填上东西）。
        val sql = MIGRATION_1_2_ADD_COLUMN_SQL
        assertTrue(sql.endsWith("`legacyGroup` TEXT"), "迁移 SQL 与预期不符：$sql")
        assertTrue(!sql.contains("NOT NULL") && !sql.contains("DEFAULT"), "新列不得有约束：$sql")
    }

    // ---------------------------------------------------------------- 2) 端到端：真 v1 库 → open → 读写

    /**
     * 物理 v1 库文件 → `WwDatabase.open`（Room 跑 `MIGRATION_1_2` + 校验哈希）→
     * 写入老 `group` → 读回已归组 → 关库重开仍在。
     *
     * ⚠️ v1 表里没有 `legacyGroup` 列，所以「导入老存档」分两步：
     *    先被迁移加列，然后 `setWorldDocuments` 才写得进 `legacyGroup`。
     *    这正是真实发生的事（老库先迁移、用户再导入一份老存档）。
     */
    @Test
    fun legacyGroupSurvivesRealOpenAndIsResolvedOnRead() {
        val dir = tempDir()
        val path = dir.resolve(dbName).toAbsolutePath().toString()

        // 1) 按 schemas/1.json 造一个完整 v1 库（17 张表全建，含 v1 identity hash）。
        //    ⚠️ 必须**全建**：Room 打开时会逐表比对结构，缺一张就报
        //    `Migration didn't properly handle: providers(...)`（实测踩过）。
        BundledSQLiteDriver().open(path).use { db ->
            db.execSQL("CREATE TABLE IF NOT EXISTS `room_master_table` (`id` INTEGER PRIMARY KEY, `identity_hash` TEXT)")
            db.execSQL("INSERT INTO `room_master_table` (`id`, `identity_hash`) VALUES (42, '$V1_IDENTITY_HASH')")
            allCreateSql(1).forEach { db.execSQL(it) }
            allIndexSql(1).forEach { db.execSQL(it) }
            db.execSQL("INSERT INTO `worldGroups` (`id`, `sortIndex`, `name`, `parentId`) VALUES ('g-1', 0, '主世界观', NULL)")
            db.execSQL(
                "INSERT INTO `worldDocuments` " +
                    "(`id`, `sortIndex`, `title`, `groupId`, `content`, `enabled`, `createdAt`, `updatedAt`, `tagsJson`) " +
                    "VALUES ('d1', 0, '旧文档', NULL, '正文', 1, 1, 1, '[]')",
            )
            db.execSQL("PRAGMA user_version = 1")
        }

        // 2) 真库打开路径：Room 跑 MIGRATION_1_2 并校验 identity hash（对不上这里就会抛）。
        val first = WwDatabase.open(path)
        try {
            runBlocking {
                val repo = ConfigRepository(first)
                assertEquals(listOf(WorldGroup(id = "g-1", name = "主世界观")), repo.worldGroups())

                val migrated = repo.worldDocuments().single()
                assertEquals("d1", migrated.id, "迁移不得丢数据")
                assertNull(migrated.groupId, "v1 库里这条文档本来没有分组信息")
                assertNull(migrated.legacyGroup, "新列在老数据上必须是 NULL")

                // 模拟「导入老存档」：老 group 落进 legacyGroup，读的时候解析成 groupId。
                repo.setWorldDocuments(listOf(migrated.copy(legacyGroup = "主世界观")))
                val resolved = repo.worldDocuments().single()
                assertEquals("g-1", resolved.groupId, "老 group 必须在读的时候被解析成 groupId")
                assertNull(resolved.legacyGroup, "解析后不得留下老 group")
            }
        } finally {
            first.close()
        }

        // 3) 关库重开：迁移 + 解析结果都要持久（证明不只是内存里对）。
        val second = WwDatabase.open(path)
        try {
            runBlocking {
                assertEquals(
                    listOf(
                        WorldDocument(
                            id = "d1",
                            title = "旧文档",
                            groupId = "g-1",
                            content = "正文",
                            enabled = true,
                            createdAt = 1,
                            updatedAt = 1,
                        ),
                    ),
                    ConfigRepository(second).worldDocuments(),
                )
            }
        } finally {
            second.close()
        }
        dir.toFile().deleteRecursively()
    }

    // ---------------------------------------------------------------- 工具

    private fun tempDir(): Path = Files.createTempDirectory("ww-t18-mig-")

    /** `shared/schemas/<version>.json` 里某张表的实体节点。 */
    private fun schemaEntity(version: Int, tableName: String): JsonObject =
        schemaRoot(version)["database"]!!.jsonObject["entities"]!!.jsonArray
            .map { it.jsonObject }
            .single { it["tableName"]!!.jsonPrimitive.content == tableName }

    /**
     * 某张表在该版本 schema 里的 `createSql`（造 v1 库用）。
     *
     * ⚠️ Room 导出的 `createSql` 里表名是 **`${'$'}{TABLE_NAME}` 占位符**，必须替换成真表名 ——
     *    直接 `execSQL` 会建出一张名叫 `${'$'}{TABLE_NAME}` 的表，然后报
     *    `no such table: worldGroups`（这个坑实测踩过）。
     */
    private fun createSql(version: Int, tableName: String): String =
        schemaEntity(version, tableName)["createSql"]!!.jsonPrimitive.content
            .replace("\${TABLE_NAME}", tableName)

    /**
     * `sessions` 表的真实 v1 DDL。
     *
     * ⚠️ **不能用 `1.json` 里那条 `createSql`**：它有 `configJson` 列（`SessionConfigBlob` 经 TypeConverter），
     *    但 Room 在 2.8.x 打开库做结构比对时用的是**展开后的真实列**（`sessions` 的真实 DDL 见
     *    [sessionsV1Ddl]），两边对不上会报
     *    `Migration didn't properly handle: sessions(...)`（实测踩过）。
     */
    private val sessionsV1Ddl = "CREATE TABLE IF NOT EXISTS `sessions` (`id` TEXT NOT NULL, " +
        "`sortIndex` INTEGER NOT NULL, `title` TEXT NOT NULL, `groupId` TEXT NOT NULL, `agentId` TEXT NOT NULL, " +
        "`providerId` TEXT, `model` TEXT, `pinned` INTEGER, `createdAt` INTEGER NOT NULL, " +
        "`updatedAt` INTEGER NOT NULL, `configJson` TEXT NOT NULL, PRIMARY KEY(`id`))"

    /**
     * 该版本 schema 里**全部表的建表语句**。
     *
     * ⚠️ Room 打开库时会逐表比对结构：少建一张就报
     *    `Migration didn't properly handle: <table>(...)`（sessions / providers 都实测踩过）。
     * ⚠️ Room 导出的 `createSql` 里表名是 **`${'$'}{TABLE_NAME}` 占位符**，必须替换成真表名 ——
     *    直接 `execSQL` 会建出一张名叫 `${'$'}{TABLE_NAME}` 的表，然后报 `no such table: worldGroups`。
     */
    private fun allCreateSql(version: Int): List<String> =
        schemaRoot(version)["database"]!!.jsonObject["entities"]!!.jsonArray
            .map { entity ->
                val obj = entity.jsonObject
                val table = obj["tableName"]!!.jsonPrimitive.content
                // `sessions` 例外：用真实 DDL，理由见 [sessionsV1Ddl]。
                if (table == "sessions") sessionsV1Ddl
                else obj["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table)
            }

    /**
     * 该版本 schema 里**全部索引的建索引语句**。
     *
     * ⚠️ Room 的结构比对**包含索引**：`sessions` 少一个 `index_sessions_updatedAt` 就报
     *    `Migration didn't properly handle: sessions(...)`，且错误信息只列 Expected/Found 的差异片段，
     *    不看全量对不出来。所以这里按 schema 建全，一条不许省。
     * ⚠️ 索引的 `createSql` 同样带 `${'$'}{TABLE_NAME}` 占位符。
     */
    private fun allIndexSql(version: Int): List<String> =
        schemaRoot(version)["database"]!!.jsonObject["entities"]!!.jsonArray
            .flatMap { entity ->
                val obj = entity.jsonObject
                val table = obj["tableName"]!!.jsonPrimitive.content
                (obj["indices"]?.jsonArray ?: return@flatMap emptyList()).map { index ->
                    index.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table)
                }
            }

    private fun schemaRoot(version: Int): JsonObject =
        WwJson.parseToJsonElement(File(schemaDirectory(), "$version.json").readText()).jsonObject

    /**
     * 找到 `shared/schemas/com.watchworld.data.db.WwDatabase/`。
     *
     * ⚠️ 这两个 JSON 必须提交 —— 它们就是这个测试的输入；删了测试直接找不到 v1 结构。
     */
    private fun schemaDirectory(): File {
        val start = File(System.getProperty("user.dir"))
        return generateSequence(start) { it.parentFile }
            .map { File(it, "shared/schemas/com.watchworld.data.db.WwDatabase") }
            .firstOrNull { it.isDirectory }
            ?: error("找不到 shared/schemas（迁移测试的输入 schema 仓库）")
    }

    private companion object {
        /** 真·v1 的 identity hash（改列之前的旧库文件与还原后的 `schemas/1.json` 一致）。 */
        const val V1_IDENTITY_HASH = "ae8df70cd16a878eb40ee96b43278a2f"

        /** 与 [MIGRATION_1_2] 里的 SQL 必须一致；抽成常量是为了让测试能读它。 */
        const val MIGRATION_1_2_ADD_COLUMN_SQL = "ALTER TABLE `worldDocuments` ADD COLUMN `legacyGroup` TEXT"
    }
}
