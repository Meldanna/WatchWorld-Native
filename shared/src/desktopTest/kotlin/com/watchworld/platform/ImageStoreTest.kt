package com.watchworld.platform

import com.watchworld.model.WwJson
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue


/**
 * T21 验收：图片存储的增删查与统计功能。
 * 使用内存实现的 ImageBlobStore，完全不触碰磁盘。
 */
class ImageStoreTest {

    /** 内存实现的 ImageBlobStore，用于测试不依赖磁盘。 */
    private class MemoryBlobStore : ImageBlobStore {
        private val store = mutableMapOf<String, String>()
        override suspend fun put(id: String, json: String) {
            store[id] = json
        }

        override suspend fun get(id: String): String? = store[id]

        override suspend fun remove(id: String) {
            store.remove(id)
        }

        override suspend fun listIds(): List<String> = store.keys.toList()
    }

    private val store = ImageStore(MemoryBlobStore())

    @Test
    fun putAndGetImage() = runBlocking {
        val image = StoredImage(
            id = "img-test-1",
            dataUrl = "data:image/png;base64,ABC123",
            mime = "image/png",
            width = 100,
            height = 100,
            bytes = 1000,
            createdAt = 1L
        )
        store.put(image)

        val loaded = store.get("img-test-1")
        assertNotNull(loaded)
        assertEquals("img-test-1", loaded.id)
        assertEquals("data:image/png;base64,ABC123", loaded.dataUrl)
        assertEquals("image/png", loaded.mime)
        assertEquals(100, loaded.width)
        assertEquals(100, loaded.height)
    }

    @Test
    fun getNonExistentImageReturnsNull() = runBlocking {
        val result = store.get("non-existent")
        assertNull(result)
    }

    @Test
    fun removeImage() = runBlocking {
        val image = StoredImage(
            id = "img-to-remove",
            dataUrl = "data:image/jpeg;base64,DEF456",
            mime = "image/jpeg",
            width = 200,
            height = 150,
            bytes = 2000,
            createdAt = 2L
        )
        store.put(image)
        assertNotNull(store.get("img-to-remove"))

        store.remove("img-to-remove")
        assertNull(store.get("img-to-remove"))
    }

    @Test
    fun listAllImages() = runBlocking {
        store.put(StoredImage(id = "img-1", dataUrl = "data:image/png;base64,1", mime = "image/png", width = 10, height = 10, bytes = 100, createdAt = 1L))
        store.put(StoredImage(id = "img-2", dataUrl = "data:image/png;base64,2", mime = "image/png", width = 20, height = 20, bytes = 200, createdAt = 2L))
        store.put(StoredImage(id = "img-3", dataUrl = "data:image/png;base64,3", mime = "image/png", width = 30, height = 30, bytes = 300, createdAt = 3L))

        val all = store.listAll()
        assertEquals(3, all.size)
        assertTrue(all.any { it.id == "img-1" })
        assertTrue(all.any { it.id == "img-2" })
        assertTrue(all.any { it.id == "img-3" })
    }

    @Test
    fun usageReturnsCorrectStats() = runBlocking {
        store.put(StoredImage(id = "img-1", dataUrl = "data:image/png;base64,x", mime = "image/png", width = 10, height = 10, bytes = 100, createdAt = 1L))
        store.put(StoredImage(id = "img-2", dataUrl = "data:image/png;base64,y", mime = "image/png", width = 20, height = 20, bytes = 200, createdAt = 2L))
        store.put(StoredImage(id = "img-3", dataUrl = "data:image/png;base64,z", mime = "image/png", width = 30, height = 30, bytes = 300, createdAt = 3L))

        val usage = store.usage()
        assertEquals(3, usage.count)
        assertEquals(600, usage.bytes)
    }

    @Test
    fun pruneOrphansRemovesUnusedImages(): Unit = runBlocking {
        store.put(StoredImage(id = "img-used", dataUrl = "data:image/png;base64,1", mime = "image/png", width = 10, height = 10, bytes = 100, createdAt = 1L))
        store.put(StoredImage(id = "img-orphan-1", dataUrl = "data:image/png;base64,2", mime = "image/png", width = 10, height = 10, bytes = 100, createdAt = 2L))
        store.put(StoredImage(id = "img-orphan-2", dataUrl = "data:image/png;base64,3", mime = "image/png", width = 10, height = 10, bytes = 100, createdAt = 3L))

        val pruned = store.pruneOrphans(listOf("img-used"))
        assertEquals(2, pruned)

        assertNull(store.get("img-orphan-1"))
        assertNull(store.get("img-orphan-2"))
        assertNotNull(store.get("img-used"))
    }

    @Test
    fun getManyReturnsCorrectMap() = runBlocking {
        store.put(StoredImage(id = "img-1", dataUrl = "data:image/png;base64,a", mime = "image/png", width = 10, height = 10, bytes = 100, createdAt = 1L))
        store.put(StoredImage(id = "img-2", dataUrl = "data:image/png;base64,b", mime = "image/png", width = 20, height = 20, bytes = 200, createdAt = 2L))

        val result = store.getMany(listOf("img-1", "img-2", "img-3"))
        assertEquals(2, result.size)
        assertEquals("data:image/png;base64,a", result["img-1"]?.dataUrl)
        assertEquals("data:image/png;base64,b", result["img-2"]?.dataUrl)
    }

    @Test
    fun getManyFiltersEmptyAndDuplicates() = runBlocking {
        store.put(StoredImage(id = "img-1", dataUrl = "data:image/png;base64,1", mime = "image/png", width = 10, height = 10, bytes = 100, createdAt = 1L))

        val result = store.getMany(listOf("", "img-1", "img-1", "img-2"))
        assertEquals(1, result.size)
        assertTrue(result.containsKey("img-1"))
    }

    @Test
    fun removeManyRemovesMultipleImages() = runBlocking {
        store.put(StoredImage(id = "img-1", dataUrl = "data:image/png;base64,1", mime = "image/png", width = 10, height = 10, bytes = 100, createdAt = 1L))
        store.put(StoredImage(id = "img-2", dataUrl = "data:image/png;base64,2", mime = "image/png", width = 20, height = 20, bytes = 200, createdAt = 2L))
        store.put(StoredImage(id = "img-3", dataUrl = "data:image/png;base64,3", mime = "image/png", width = 30, height = 30, bytes = 300, createdAt = 3L))

        store.removeMany(listOf("img-1", "img-3"))

        assertNull(store.get("img-1"))
        assertNotNull(store.get("img-2"))
        assertNull(store.get("img-3"))
    }

    @Test
    fun dataUrlBytesCalculatesCorrectly() {
        // ⚠️ 期望值按**规格**算，不按直觉：`imageStore.ts:62` 是
        //    `Math.round(((dataUrl.length - comma - 1) * 3) / 4)` —— 只是体积估算，
        //    不做 base64 填充修正。3 个字符 → round(2.25) = 2（不是 3）。
        assertEquals(2, dataUrlBytes("data:image/png;base64,ABC"))
        assertEquals(4, dataUrlBytes("data:image/png;base64,ABCDE"))
        // 没有逗号时按整串长度算（TS 同一行分支）。
        assertEquals("no-comma".length, dataUrlBytes("no-comma"))
    }
}