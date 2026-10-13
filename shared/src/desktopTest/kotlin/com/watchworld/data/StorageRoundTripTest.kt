package com.watchworld.data

import com.watchworld.data.db.WwDatabase
import com.watchworld.data.repository.ArchiveRepository
import com.watchworld.data.repository.ArchiveStore
import com.watchworld.data.repository.SessionRepository
import com.watchworld.model.ArchiveV5
import com.watchworld.model.ChatMessage
import com.watchworld.model.ChatSession
import com.watchworld.model.Role
import com.watchworld.model.SessionSummary
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * T07 验收：v5 存档「导入 → SQLite → 读回」的往返，以及 Repository 的三条纪律
 * （写操作盖 `updatedAt`、四张表同事务、关库重开数据还在）。
 *
 * 样本与 [ArchiveRoundTripTest] 相同：`fixtures/sse/archive-v5-{legacy,minimal,rich}.json`。
 */
class StorageRoundTripTest {

    private companion object {
        const val FIXED_NOW = 1_800_000_000_000L
        val FIXTURES = listOf("archive-v5-legacy.json", "archive-v5-minimal.json", "archive-v5-rich.json")

        fun fixtureText(name: String): String {
            val start = File(System.getProperty("user.dir"))
            val file = generateSequence(start) { it.parentFile }
                .map { File(it, "fixtures/sse/$name") }
                .firstOrNull { it.isFile }
                ?: error("Fixture not found from ${start.absolutePath}: fixtures/sse/$name")
            return file.readText()
        }

        /**
         * 库里读回的单体设置「没存过就给默认值」（SettingsRepository 的读接口不返回 null），
         * 所以期望值要按同一规则补齐，才能与 [ArchiveStore.snapshot] 逐字段比较。
         *
         * ⚠️ T14 起显示设置 / 应用偏好的兜底值来自 `data/DefaultData.kt` 的 `DEFAULT_*`
         *    （不再是模型自身的默认值），这里必须跟着改 —— 否则老存档往返会假失败。
         */
        fun withStorageDefaults(archive: ArchiveV5): ArchiveV5 = archive.copy(
            theme = archive.theme ?: "emerald-day",
            uiMode = archive.uiMode ?: "dark",
            displaySettings = archive.displaySettings ?: DEFAULT_DISPLAY_SETTINGS,
            appPreferences = archive.appPreferences ?: DEFAULT_APP_PREFERENCES,
        )
    }

    private fun <T> withDb(block: suspend (WwDatabase) -> T): T {
        val db = WwDatabase.openInMemory()
        try {
            return runBlocking { block(db) }
        } finally {
            db.close()
        }
    }

    // ---------------------------------------------------------------- 存档往返

    /** 核心验收：三份真实样本经 SQLite 往返后，模型与「解析 + 归一化」的结果完全一致。 */
    @Test
    fun realArchivesSurviveDatabaseRoundTrip() {
        for (name in FIXTURES) {
            withDb { db ->
                val store = ArchiveStore(db) { FIXED_NOW }
                val text = fixtureText(name)

                assertTrue(store.importJson(text), "$name 应能导入")

                val expected = withStorageDefaults(ArchiveRepository.parseArchive(text, now = FIXED_NOW))
                val actual = store.snapshot().copy(version = expected.version, exportTime = expected.exportTime)
                assertEquals(expected, actual, "$name 经数据库往返后模型应完全一致")
            }
        }
    }

    /** 库 → 导出 JSON → 再解析，结果仍与库里一致（导出路径没有丢字段）。 */
    @Test
    fun exportFromDatabaseReparsesToTheSameModel() {
        withDb { db ->
            val store = ArchiveStore(db) { FIXED_NOW }
            assertTrue(store.importJson(fixtureText("archive-v5-rich.json")))

            val snapshot = store.snapshot()
            val reparsed = ArchiveRepository.parseArchive(store.exportJson(), now = FIXED_NOW)

            assertEquals(5, reparsed.version)
            assertEquals(FIXED_NOW, reparsed.exportTime)
            assertEquals(snapshot.sessions, reparsed.sessions)
            assertEquals(snapshot.providers, reparsed.providers)
            assertEquals(snapshot.worldDocuments, reparsed.worldDocuments)
        }
    }

    /** 「重启后还在」：文件库写入 → 关库 → 重开，会话与消息都在。内存库证明不了这一条。 */
    @Test
    fun fileBackedDatabaseSurvivesCloseAndReopen() {
        val dir = Files.createTempDirectory("ww-t07-").toFile()
        try {
            val path = File(dir, WwDatabase.FILE_NAME).absolutePath
            val expected = ArchiveRepository.parseArchive(fixtureText("archive-v5-rich.json"), now = FIXED_NOW)

            val first = WwDatabase.open(path)
            try {
                runBlocking { assertTrue(ArchiveStore(first) { FIXED_NOW }.importJson(fixtureText("archive-v5-rich.json"))) }
            } finally {
                first.close()
            }
            assertTrue(File(path).isFile, "库文件必须落在给定路径，而不是当前工作目录")

            val second = WwDatabase.open(path)
            try {
                val sessions = runBlocking { SessionRepository(second) { FIXED_NOW }.all() }
                assertEquals(expected.sessions, sessions, "重开后会话树应与导入时完全一致")
                assertEquals(31, sessions.sumOf { it.messages.size })
            } finally {
                second.close()
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    // ---------------------------------------------------------------- 导入的逐项语义

    /** 存档里没有的分类保持原样；空数组在 JS 里是真值，等于清空该分类。 */
    @Test
    fun partialImportKeepsMissingCategoriesAndEmptyArrayClears() {
        withDb { db ->
            val store = ArchiveStore(db) { FIXED_NOW }
            assertTrue(store.importJson(fixtureText("archive-v5-rich.json")))
            val before = store.snapshot()
            assertTrue(before.providers.isNotEmpty(), "前置：富样本应有 Provider")
            assertTrue(before.sessions.isNotEmpty())

            assertTrue(store.importJson("""{"version": 5, "providers": []}"""))
            val after = store.snapshot()

            assertTrue(after.providers.isEmpty(), "providers: [] 是 JS 真值 → 清空")
            assertEquals(before.sessions, after.sessions, "存档里没有 sessions → 原样保留")
            assertEquals(before.worldDocuments, after.worldDocuments)
            assertEquals(before.theme, after.theme)
        }
    }

    @Test
    fun badArchiveIsRejectedWithoutTouchingTheDatabase() {
        withDb { db ->
            val store = ArchiveStore(db) { FIXED_NOW }
            assertTrue(store.importJson(fixtureText("archive-v5-minimal.json")))
            val before = store.snapshot()

            assertFalse(store.importJson("这不是 JSON"))
            assertFalse(store.importJson("[1, 2, 3]"), "顶层不是对象")
            assertFalse(store.importJson(""))
            assertEquals(before, store.snapshot(), "坏存档不得改动库")
        }
    }

    /**
     * T18：老存档的文档用 `group`（**组名字符串**），导入后必须落到 `groupId`，且 `group` 不再回写。
     *
     * 三条判据缺一不可：
     *  1. 读回来的是 `groupId`（迁移生效）；
     *  2. 库里不再有 `legacyGroup` 残值（写回时被清掉，老 `group` 不会复活）；
     *  3. 导出 JSON 里没有 `group` 键（`storage.ts:370` 的解构丢弃）。
     */
    @Test
    fun legacyWorldDocumentGroupNameMigratesToGroupId() {
        withDb { db ->
            val store = ArchiveStore(db) { FIXED_NOW }
            val legacyArchive = """
                {
                  "version": 5,
                  "worldGroups": [{"id": "g-1", "name": "主世界观"}, {"id": "g-2", "name": "地理"}],
                  "worldDocuments": [
                    {"id": "d1", "title": "旧文档", "group": "地理", "content": "旧", "enabled": true},
                    {"id": "d2", "title": "查无此名", "group": "不存在的分组", "content": "x"},
                    {"id": "d3", "title": "新文档", "groupId": "g-1", "content": "新"}
                  ]
                }
            """.trimIndent()
            assertTrue(store.importJson(legacyArchive))

            val docs = store.snapshot().worldDocuments
            assertEquals(listOf("g-2", null, "g-1"), docs.map { it.groupId })
            assertTrue(docs.all { it.legacyGroup == null }, "老 group 不得停留在模型里")

            // 库里也不该残留：再读一次仍是同一结果（幂等）。
            assertEquals(listOf("g-2", null, "g-1"), store.snapshot().worldDocuments.map { it.groupId })

            // 导出里没有 `group` 键。
            val exported = store.exportJson()
            assertFalse("\"group\"" in exported, "老 group 键不得出现在导出里：$exported")
            assertTrue("\"groupId\"" in exported)
        }
    }

    // ---------------------------------------------------------------- SessionRepository

    /** 所有写操作都在同一事务里盖 `updatedAt`（同步引擎靠它算增量）。 */
    @Test
    fun writesStampUpdatedAt() {
        withDb { db ->
            var clock = 1_000L
            val repo = SessionRepository(db) { clock }

            val saved = repo.save(ChatSession(id = "s1", title = "原标题"))
            assertEquals(1_000L, saved.updatedAt)
            assertEquals(1_000L, repo.byId("s1")?.updatedAt)

            clock = 2_000L
            assertEquals(2_000L, repo.appendMessage("s1", ChatMessage(id = "m1", role = Role.USER, content = "你好")))
            val afterAppend = assertNotNull(repo.byId("s1"))
            assertEquals(2_000L, afterAppend.updatedAt)
            assertEquals(listOf("m1"), afterAppend.messages.map { it.id })

            clock = 3_000L
            repo.rename("s1", "新标题")
            val renamed = assertNotNull(repo.byId("s1"))
            assertEquals("新标题", renamed.title)
            assertEquals(3_000L, renamed.updatedAt)
            assertEquals(1, renamed.messages.size, "改名不得动消息")

            clock = 4_000L
            repo.setPinned("s1", true)
            assertEquals(4_000L, repo.byId("s1")?.updatedAt)

            clock = 5_000L
            repo.moveToGroup("s1", "g2")
            val moved = assertNotNull(repo.byId("s1"))
            assertEquals("g2", moved.groupId)
            assertEquals(5_000L, moved.updatedAt)
        }
    }

    /** 时间线 / 总结的 `null` 与 `[]` 是两种状态，经过拆表后不得合并。 */
    @Test
    fun nullAndEmptyChildListsStayDistinct() {
        withDb { db ->
            val repo = SessionRepository(db) { FIXED_NOW }
            repo.save(ChatSession(id = "null", timelines = null, summaries = null))
            repo.save(ChatSession(id = "empty", timelines = emptyList(), summaries = emptyList()))

            val byNull = assertNotNull(repo.byId("null"))
            assertNull(byNull.timelines)
            assertNull(byNull.summaries)

            val byEmpty = assertNotNull(repo.byId("empty"))
            assertEquals(emptyList(), byEmpty.timelines)
            assertEquals(emptyList(), byEmpty.summaries)
        }
    }

    /** SQLite 不保序：会话与消息的顺序全靠 `sortIndex`。已有会话再保存不得挪到末尾。 */
    @Test
    fun orderIsPreservedAcrossWrites() {
        withDb { db ->
            val repo = SessionRepository(db) { FIXED_NOW }
            val messages = (1..5).map { ChatMessage(id = "m$it", role = Role.USER, content = "#$it") }.reversed()
            repo.save(ChatSession(id = "c"))
            repo.save(ChatSession(id = "a", messages = messages))
            repo.save(ChatSession(id = "b"))

            repo.save(ChatSession(id = "c", title = "改过"))

            val all = repo.all()
            assertEquals(listOf("c", "a", "b"), all.map { it.id }, "已有会话保持原位，新会话追加到末尾")
            assertEquals(messages.map { it.id }, all.first { it.id == "a" }.messages.map { it.id })
        }
    }

    /**
     * 原生头像功能的新字段（窗口 AI 头像文件名）经 blob 往返必须不丢 ——
     * 漏了就是「设了头像关窗口就没了」。2026-10-12 Sonnet 5 补。
     */
    @Test
    fun windowAiAvatarFileNameSurvivesRoundTrip() {
        withDb { db ->
            val repo = SessionRepository(db) { FIXED_NOW }
            repo.save(ChatSession(id = "s1", aiAvatarFileName = "avatar-123.jpg"))
            assertEquals("avatar-123.jpg", repo.byId("s1")?.aiAvatarFileName)

            repo.save(assertNotNull(repo.byId("s1")).copy(aiAvatarFileName = null))
            assertNull(repo.byId("s1")?.aiAvatarFileName)
        }
    }

    /**
     * 窗口用户头像文件名也经 blob 往返 —— 用户 2026-10-12 补充。
     */
    @Test
    fun windowUserAvatarFileNameSurvivesRoundTrip() {
        withDb { db ->
            val repo = SessionRepository(db) { FIXED_NOW }
            repo.save(ChatSession(id = "u1", userAvatarFileName = "avatar-u-1.jpg"))
            assertEquals("avatar-u-1.jpg", repo.byId("u1")?.userAvatarFileName)

            repo.save(assertNotNull(repo.byId("u1")).copy(userAvatarFileName = null))
            assertNull(repo.byId("u1")?.userAvatarFileName)
        }
    }

    /** 删除会话连带删掉它的消息，且不影响其它会话。 */
    @Test
    fun deleteRemovesChildRows() {
        withDb { db ->
            val repo = SessionRepository(db) { FIXED_NOW }
            repo.save(ChatSession(id = "x", messages = listOf(ChatMessage(id = "m1", role = Role.USER, content = "a"))))
            repo.save(ChatSession(id = "y", messages = listOf(ChatMessage(id = "m2", role = Role.USER, content = "b"))))

            repo.delete("x")

            assertNull(repo.byId("x"))
            assertEquals(listOf("y"), repo.all().map { it.id })
            assertEquals(listOf("m2"), repo.all().single().messages.map { it.id })
        }
    }

    /**
     * 子表 id 只保证在会话内唯一：两个会话用同名的消息 id / 总结 id，保存后两边都得读得回来。
     * 单列 `id` 主键下，后写的会话会把前一个会话的同名行 upsert 走（时间线就是这么丢的）。
     */
    @Test
    fun sameChildIdsInDifferentSessionsDoNotCollide() {
        withDb { db ->
            val repo = SessionRepository(db) { FIXED_NOW }
            fun session(id: String) = ChatSession(
                id = id,
                messages = listOf(ChatMessage(id = "m-dup", role = Role.USER, content = "来自 $id")),
                summaries = listOf(SessionSummary(id = "sum-dup", content = "总结 $id", fromFloor = 1, toFloor = 1)),
            )
            repo.save(session("s1"))
            repo.save(session("s2"))

            for (id in listOf("s1", "s2")) {
                val loaded = assertNotNull(repo.byId(id), "$id 应存在")
                assertEquals(listOf("来自 $id"), loaded.messages.map { it.content }, "$id 的消息被别的会话覆盖了")
                assertEquals(listOf("总结 $id"), loaded.summaries.orEmpty().map { it.content }, "$id 的总结被别的会话覆盖了")
            }
            val all = repo.all()
            assertEquals(listOf("s1", "s2"), all.map { it.id })
            assertEquals(listOf(1, 1), all.map { it.messages.size })
            assertEquals(listOf(1, 1), all.map { it.summaries.orEmpty().size })

            // 删一个会话不得连带删掉另一个会话的同名子行
            repo.delete("s1")
            val survivor = assertNotNull(repo.byId("s2"))
            assertEquals(listOf("来自 s2"), survivor.messages.map { it.content })
            assertEquals(listOf("总结 s2"), survivor.summaries.orEmpty().map { it.content })
        }
    }
}
