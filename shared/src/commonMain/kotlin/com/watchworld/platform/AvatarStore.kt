package com.watchworld.platform

import java.io.File
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * 头像的归属 —— 决定它落在哪个子目录。
 *
 * 用户 2026-10-12 定的布局：**每个窗口有自己的子文件夹，头像与 message 的图片分开**。
 * 所以头像**不进** [ImageStore]（那边 `images/` 是消息附图的扁平仓库，一图一 JSON），
 * 而是按归属分目录落成真正的图片文件：
 *
 * | 归属 | 路径 |
 * |---|---|
 * | [User] | `<数据目录>/avatars/avatar.<ext>`（全局一个） |
 * | [Session] | `<数据目录>/sessions/<session-id>/avatar.<ext>`（该窗口的 **AI** 头像） |
 * | [SessionUser] | `<数据目录>/sessions/<session-id>/user/avatar.<ext>`（该窗口的**用户**头像） |
 * | [Agent] | `<数据目录>/agents/<agent-id>/avatar.<ext>`（该顾问的默认头像） |
 *
 * ⚠️ [Session] 与 [SessionUser] **必须是两个目录**（用户 2026-10-13 报「窗口头像只能保持一个
 *    上传状态」）：[save] 会清掉目标目录下的上一张，两者同目录就会互相删掉对方的文件。
 */
enum class AvatarOwner { User, Session, SessionUser, Agent }

/**
 * 头像文件存储。
 *
 * 文件名是 `avatar-<毫秒>.<jpg|png>`（扩展名由压缩结果决定，见 [compressImageBytes]）。
 * 带时间戳是**故意的**：Compose 靠「头像来源变了」触发重新读盘，固定名字换图后刷不出来。
 * 读的时候一般不需要名字 —— [read] 的 `fileName` 传 `null` 就按前缀扫目录找
 * （Agent 没有存文件名的字段，走的就是这条路）。
 *
 * 模型里存的 `*AvatarFileName`（`Session.aiAvatarFileName` /
 * `AppPreferences.userAvatarFileName`）只是「这个范围有没有设过头像」的凭据。
 *
 * ⚠️ 这些方法都是**同步阻塞**的（头像最大 256px，几 KB～几十 KB）。调用方在
 * Compose 里要放进 `LaunchedEffect` 并切到 IO 调度器，别在主线程上读盘。
 */
class AvatarStore(private val dataDir: String = appDataDir()) {

    /** 保存（压缩后落盘），返回文件名；该目录下上一张头像会被清掉。 */
    @OptIn(ExperimentalEncodingApi::class)
    fun save(owner: AvatarOwner, key: String, bytes: ByteArray, mime: String): String {
        val compressed = compressImageBytes(bytes, mime, maxDim = MAX_DIM)
        val ext = if (compressed.mime == "image/png") "png" else "jpg"
        val dir = dirOf(owner, key).apply { mkdirs() }
        // 目录里永远只留一张头像。
        avatarFiles(dir).forEach { it.delete() }
        // 名字带时间戳：调用方（Compose）靠「来源变了」重读图片，固定的名字换图后刷不出来。
        val name = "$STEM-${currentTimeMillis()}.$ext"
        File(dir, name).writeBytes(Base64.decode(compressed.dataUrl.substringAfter(',', "")))
        return name
    }

    /** 读头像字节；没有（或读失败）返回 `null`。 */
    fun read(owner: AvatarOwner, key: String, fileName: String? = null): ByteArray? =
        fileOf(owner, key, fileName)?.let { if (it.isFile) runCatching { it.readBytes() }.getOrNull() else null }

    /** 有没有设过头像 —— 不读正文，只判存在。 */
    fun exists(owner: AvatarOwner, key: String, fileName: String? = null): Boolean =
        fileOf(owner, key, fileName)?.isFile == true

    /** 删除该归属下的头像文件；返回是否真的删掉了东西。 */
    fun remove(owner: AvatarOwner, key: String): Boolean {
        val files = avatarFiles(dirOf(owner, key))
        files.forEach { it.delete() }
        return files.isNotEmpty()
    }

    private fun dirOf(owner: AvatarOwner, key: String): File = when (owner) {
        AvatarOwner.User -> File(dataDir, "avatars")
        AvatarOwner.Session -> File(dataDir, "sessions/$key")
        AvatarOwner.SessionUser -> File(dataDir, "sessions/$key/user")
        AvatarOwner.Agent -> File(dataDir, "agents/$key")
    }

    private fun fileOf(owner: AvatarOwner, key: String, fileName: String?): File? {
        val dir = dirOf(owner, key)
        if (!fileName.isNullOrBlank()) return File(dir, fileName)
        return avatarFiles(dir).firstOrNull()
    }

    /** 一个归属目录下的全部头像文件（正常情况下最多一张）。 */
    private fun avatarFiles(dir: File): List<File> =
        dir.listFiles()?.filter { it.isFile && it.name.startsWith(STEM) }.orEmpty()

    private companion object {
        /** 文件主名前缀，实际文件名是 `avatar-<毫秒>.<ext>`。 */
        const val STEM = "avatar-"

        /** 显示尺寸最大 48px，256px 足够高清屏，也不会让存档目录膨胀。 */
        const val MAX_DIM = 256
    }
}

/** 全局默认实例（与 [defaultImageStore] 同一用法）。 */
val defaultAvatarStore: AvatarStore = AvatarStore()
