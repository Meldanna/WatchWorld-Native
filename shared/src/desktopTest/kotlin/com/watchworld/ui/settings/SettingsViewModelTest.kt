package com.watchworld.ui.settings

import com.watchworld.data.db.WwDatabase
import com.watchworld.data.repository.ArchiveStore
import com.watchworld.data.repository.ConfigRepository
import com.watchworld.data.repository.SessionRepository
import com.watchworld.data.repository.SettingsRepository
import com.watchworld.model.Agent
import com.watchworld.model.ApiProviderConfig
import com.watchworld.model.ApiProviderType
import com.watchworld.model.RegexRule
import com.watchworld.model.UiMode
import com.watchworld.net.HttpClientFactory
import com.watchworld.net.ProviderApi
import com.watchworld.net.WebDavClient
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * T15 验收：[SettingsViewModel] 的 CRUD + 主题/明暗/偏好写回。
 *
 * 所有写操作走 Repository → SQLite（in-memory），读回后状态必须一致。
 */
class SettingsViewModelTest {

    private fun withVm(block: suspend (SettingsViewModel, WwDatabase) -> Unit) {
        val db = WwDatabase.openInMemory()
        val http = HttpClientFactory.create()
        try {
            val vm = SettingsViewModel(
                ConfigRepository(db),
                SettingsRepository(db),
                SessionRepository(db),
                ArchiveStore(db),
                WebDavClient(http),
                ProviderApi(http),
            )
            runBlocking { block(vm, db) }
        } finally {
            http.close()
            db.close()
        }
    }

    @Test
    fun loadReturnsEmptyStateForFreshDatabase() = withVm { vm, _ ->
        vm.load()
        val s = vm.state.value
        assertFalse(s.loading)
        assertTrue(s.providers.isEmpty())
        assertTrue(s.agents.isEmpty())
        assertEquals("emerald-day", s.themePalette)
        assertEquals("dark", s.uiModeRaw)
    }

    @Test
    fun saveAndDeleteProvider() = withVm { vm, _ ->
        vm.load()
        val p = ApiProviderConfig(id = "p1", name = "Test", type = ApiProviderType.OPENAI)
        vm.saveProvider(p)
        assertEquals(1, vm.state.value.providers.size)
        assertEquals("Test", vm.state.value.providers[0].name)

        vm.saveProvider(p.copy(name = "Updated"))
        assertEquals(1, vm.state.value.providers.size)
        assertEquals("Updated", vm.state.value.providers[0].name)

        vm.deleteProvider("p1")
        assertTrue(vm.state.value.providers.isEmpty())
    }

    @Test
    fun saveAndDeleteAgent() = withVm { vm, _ ->
        vm.load()
        val a = Agent(id = "a1", name = "Bot")
        vm.saveAgent(a)
        assertEquals(1, vm.state.value.agents.size)
        vm.deleteAgent("a1")
        assertTrue(vm.state.value.agents.isEmpty())
    }

    @Test
    fun toggleRegexRule() = withVm { vm, _ ->
        vm.load()
        val r = RegexRule(id = "r1", name = "test", pattern = "a", replacement = "b", enabled = false)
        vm.saveRegexRule(r)
        assertFalse(vm.state.value.regexRules[0].enabled)
        vm.toggleRegexRule("r1")
        assertTrue(vm.state.value.regexRules[0].enabled)
        vm.toggleRegexRule("r1")
        assertFalse(vm.state.value.regexRules[0].enabled)
    }

    @Test
    fun setThemePaletteAndUiMode() = withVm { vm, _ ->
        vm.load()
        vm.setThemePalette("violet-day")
        assertEquals("violet-day", vm.state.value.themePalette)

        vm.setUiMode(UiMode.LIGHT)
        assertEquals("light", vm.state.value.uiModeRaw)

        // Verify persistence: reload
        vm.load()
        assertEquals("violet-day", vm.state.value.themePalette)
        assertEquals("light", vm.state.value.uiModeRaw)
    }

    @Test
    fun importExportRoundTrip() = withVm { vm, _ ->
        vm.load()
        val p = ApiProviderConfig(id = "p1", name = "Export", type = ApiProviderType.OPENAI)
        vm.saveProvider(p)

        val json = vm.exportData()
        assertTrue(json.contains("Export"))

        // Clear and reimport
        vm.deleteProvider("p1")
        assertTrue(vm.state.value.providers.isEmpty())

        val ok = vm.importData(json)
        assertTrue(ok)
        assertEquals(1, vm.state.value.providers.size)
        assertEquals("Export", vm.state.value.providers[0].name)
    }
}
