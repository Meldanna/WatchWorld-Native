package com.watchworld.platform

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 头像文件的落盘布局（用户 2026-10-12 定的规格）：
 * **每个窗口/顾问有自己的子文件夹，头像与 message 的 `images/` 分开**。
 *
 * 这些用例钉的正是布局本身 —— 存储从「塞进 ImageStore 的扁平仓库」改成
 * 「按归属分目录的真图片文件」时，唯一会静默出错的地方就是路径。
 */
class AvatarStoreTest {

    /** 一段解不出来的字节：`compressImageBytes` 会原样退回，所以能直接断言往返相等。 */
    private val rawBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 1, 2, 3, 4)

    private fun withStore(block: (AvatarStore, File) -> Unit) {
        val dir = Files.createTempDirectory("ww-avatar-").toFile()
        try {
            block(AvatarStore(dir.absolutePath), dir)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun sessionAvatarLandsInTheWindowOwnFolder() = withStore { store, dir ->
        val name = store.save(AvatarOwner.Session, "s1", rawBytes, "image/png")

        assertTrue(name.startsWith("avatar-"), "文件名带时间戳前缀，实际是 $name")
        assertTrue(File(dir, "sessions/s1/$name").isFile, "窗口头像要落在 sessions/<窗口 id>/ 下")
        assertContentEquals(rawBytes, store.read(AvatarOwner.Session, "s1", name))
        assertTrue(store.exists(AvatarOwner.Session, "s1", name))
    }

    @Test
    fun userAndAgentAvatarsDoNotShareFolders() = withStore { store, dir ->
        val user = store.save(AvatarOwner.User, "", rawBytes, "image/png")
        val agent = store.save(AvatarOwner.Agent, "a1", rawBytes, "image/png")

        assertTrue(File(dir, "avatars/$user").isFile)
        assertTrue(File(dir, "agents/a1/$agent").isFile)
        // 各自的目录里只有自己的那张；读错归属要读不到。
        assertNull(store.read(AvatarOwner.Agent, "a1", user))
        assertNull(store.read(AvatarOwner.User, "", agent))
    }

    /** Agent 没有存文件名的字段：不传名字也要按前缀扫得到（消息区就靠这条）。 */
    @Test
    fun agentAvatarReadsWithoutKnowingTheFileName() = withStore { store, _ ->
        store.save(AvatarOwner.Agent, "a1", rawBytes, "image/png")

        assertContentEquals(rawBytes, store.read(AvatarOwner.Agent, "a1"))
        assertTrue(store.exists(AvatarOwner.Agent, "a1"))
    }

    /** 重存不许留下第二张：否则 `read(fileName = null)` 扫到哪张全看目录顺序。 */
    @Test
    fun savingAgainLeavesExactlyOneFile() = withStore { store, dir ->
        store.save(AvatarOwner.User, "", rawBytes, "image/png")
        val second = store.save(AvatarOwner.User, "", rawBytes, "image/jpeg")

        val files = File(dir, "avatars").listFiles()!!.filter { it.isFile }
        assertEquals(1, files.size, "同一归属只该留一张头像：${files.map { it.name }}")
        assertContentEquals(rawBytes, store.read(AvatarOwner.User, "", second))
    }

    @Test
    fun removeDeletesAndThenReadsAsMissing() = withStore { store, _ ->
        store.save(AvatarOwner.Session, "s1", rawBytes, "image/png")

        assertTrue(store.remove(AvatarOwner.Session, "s1"))
        assertNull(store.read(AvatarOwner.Session, "s1"))
        assertFalse(store.exists(AvatarOwner.Session, "s1"))
        assertFalse(store.remove(AvatarOwner.Session, "s1"), "已经没东西了，不该再报「删掉了」")
    }

    @Test
    fun missingAvatarNeverThrows() = withStore { store, _ ->
        assertNull(store.read(AvatarOwner.Session, "不存在的窗口"))
        assertNull(store.read(AvatarOwner.Agent, "不存在的顾问", "avatar-1.jpg"))
        assertFalse(store.exists(AvatarOwner.User, ""))
        assertNotNull(store.save(AvatarOwner.User, "", rawBytes, "image/png"), "空名字的用户头像仍要能存")
    }
}
