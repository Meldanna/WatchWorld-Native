package com.watchworld.ui.session

import com.watchworld.data.db.WwDatabase
import com.watchworld.data.repository.ConfigRepository
import com.watchworld.data.repository.SessionRepository
import com.watchworld.data.repository.SettingsRepository
import com.watchworld.model.ApiProviderConfig
import com.watchworld.model.ApiProviderType
import com.watchworld.model.Role
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * T14 验收：会话管理（列表 / 分组 / 置顶 / 新建 / 切换 / 重命名 / 清空 / 删除 / 移动分组）
 * 全部经 Repository 落库，且**关库重开后还在**。
 */
class SessionViewModelTest {

    private companion object {
        var clock = 1_800_000_000_000L
        fun tick(): Long {
            clock += 1_000
            return clock
        }
    }

    private fun <T> withDb(block: suspend (WwDatabase) -> T): T {
        val db = WwDatabase.openInMemory()
        try {
            return runBlocking { block(db) }
        } finally {
            db.close()
        }
    }

    private suspend fun seedProvider(config: ConfigRepository) {
        config.setProviders(
            listOf(
                ApiProviderConfig(
                    id = "provider-openai",
                    name = "OpenAI",
                    type = ApiProviderType.OPENAI,
                    defaultModel = "gpt-4o-mini",
                ),
            ),
        )
    }

    private fun viewModel(db: WwDatabase) = SessionViewModel(
        sessions = SessionRepository(db) { tick() },
        settings = SettingsRepository(db),
        config = ConfigRepository(db),
        now = { tick() },
    )

    @Test
    fun newSessionPersistsWithDefaultsFromPreferencesAndBecomesActive() = withDb { db ->
        val config = ConfigRepository(db)
        seedProvider(config)
        val vm = viewModel(db)
        vm.load()

        val created = vm.newSession()

        assertEquals("新对话", created.title)
        assertEquals("provider-openai", created.providerId)
        assertEquals("gpt-4o-mini", created.model)
        // DEFAULT_APP_PREFERENCES：联网与流式默认开启，工具步数 0 = 无限。
        assertEquals(true, created.webSearchEnabled)
        assertEquals(true, created.streamingEnabled)
        assertEquals(0, created.toolCallStepLimit)
        assertEquals(created.id, vm.state.value.activeSessionId)
        // 落库了：换一个 ViewModel 重新读，会话还在。
        assertEquals(listOf(created.id), viewModel(db).let { it.load(); it.state.value.sessions.map { s -> s.id } })
    }

    @Test
    fun renamePinAndMoveToGroupAllUpdateUpdatedAtAndPersist() = withDb { db ->
        val vm = viewModel(db)
        vm.load()
        val created = vm.newSession()
        val group = assertNotNull(vm.createGroup("编程开发"))

        vm.rename(created.id, "  改个名字  ")
        vm.setPinned(created.id, true)
        vm.moveToGroup(created.id, group.id)

        val stored = assertNotNull(SessionRepository(db).byId(created.id))
        assertEquals("改个名字", stored.title)
        assertEquals(true, stored.pinned)
        assertEquals(group.id, stored.groupId)
        assertTrue(stored.updatedAt > created.updatedAt, "每次写都要盖 updatedAt")
        // 内存里的副本与库里一致（否则侧边栏会显示旧标题）。
        assertEquals(stored, vm.state.value.sessions.first { it.id == created.id })
    }

    @Test
    fun renameIgnoresBlankTitlesAndUnknownGroups() = withDb { db ->
        val vm = viewModel(db)
        vm.load()
        val created = vm.newSession()
        val originalGroup = created.groupId

        vm.rename(created.id, "   ")
        vm.moveToGroup(created.id, "group-does-not-exist")

        val stored = assertNotNull(SessionRepository(db).byId(created.id))
        assertEquals("新对话", stored.title)
        assertEquals(originalGroup, stored.groupId)
    }

    @Test
    fun clearEmptiesMessagesButKeepsTheSessionRow() = withDb { db ->
        val vm = viewModel(db)
        vm.load()
        val created = vm.newSession()
        val repository = SessionRepository(db)
        repository.appendMessage(
            created.id,
            com.watchworld.model.ChatMessage(id = "m-1", role = Role.USER, content = "在吗"),
        )
        vm.load()

        vm.clear(created.id)

        val stored = assertNotNull(repository.byId(created.id))
        assertTrue(stored.messages.isEmpty(), "清空后不该再有消息")
        assertEquals(created.id, stored.id)
    }

    @Test
    fun deletingTheActiveSessionFallsBackToAnotherOne() = withDb { db ->
        val vm = viewModel(db)
        vm.load()
        val first = vm.newSession()
        val second = vm.newSession()
        vm.select(second.id)

        vm.delete(second.id)

        val remaining = vm.state.value.sessions.map { it.id }
        assertEquals(listOf(first.id), remaining)
        assertEquals(first.id, vm.state.value.activeSessionId)
        assertNull(SessionRepository(db).byId(second.id))
        // 活动会话选择也落库了（MVP：重启后回到同一个窗口）。
        assertEquals(first.id, SettingsRepository(db).activeSessionId())
    }

    @Test
    fun deletingTheLastSessionCreatesAReplacement() = withDb { db ->
        val vm = viewModel(db)
        vm.load()
        val only = vm.newSession()

        vm.delete(only.id)

        val sessions = vm.state.value.sessions
        assertEquals(1, sessions.size)
        assertTrue(sessions.none { it.id == only.id })
        assertEquals(sessions.first().id, vm.state.value.activeSessionId)
    }

    @Test
    fun selectingAnUnknownSessionIsIgnored() = withDb { db ->
        val vm = viewModel(db)
        vm.load()
        val created = vm.newSession()

        vm.select("session-does-not-exist")

        assertEquals(created.id, vm.state.value.activeSessionId)
    }

    @Test
    fun loadFallsBackToTheFirstSessionWhenTheStoredActiveIdIsStale() = withDb { db ->
        val settings = SettingsRepository(db)
        settings.setActiveSessionId("session-welcome")
        val vm = viewModel(db)

        vm.load()
        val created = vm.newSession()
        vm.load()

        assertEquals(created.id, vm.state.value.activeSessionId)
    }
}
