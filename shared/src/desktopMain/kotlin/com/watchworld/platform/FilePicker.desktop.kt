package com.watchworld.platform

import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.net.URLConnection

/**
 * 桌面端文件选择：AWT [FileDialog]（系统原生文件框）。
 *
 * 与参考版 Web 侧 `accept=".md,.txt"` 对齐 —— 传 `extensions` 时用 `FilenameFilter` 过滤。
 * ⚠️ Windows 原生框的过滤并不严格（用户可以把文件名框改成「所有文件」），
 *    真正的兜底是「读进来解析失败就报错」。
 */
actual fun pickTextFile(extensions: List<String>): PickedTextFile? =
    readPickedFiles(showTextFileDialog(extensions, multiple = false)).firstOrNull()

actual fun pickTextFiles(extensions: List<String>): List<PickedTextFile> =
    readPickedFiles(showTextFileDialog(extensions, multiple = true))

actual fun pickImageFiles(): List<PickedImageFile> {
    val files = showTextFileDialog(listOf("png", "jpg", "jpeg", "gif", "webp", "bmp"), multiple = true)
    return files.mapNotNull { file ->
        val mime = URLConnection.guessContentTypeFromName(file.name) ?: return@mapNotNull null
        PickedImageFile(file.name, mime, file.readBytes())
    }
}

private fun showTextFileDialog(extensions: List<String>, multiple: Boolean): List<File> {
    val dialog = FileDialog(null as Frame?, "选择文件", FileDialog.LOAD)
    dialog.isMultipleMode = multiple
    if (extensions.isNotEmpty()) {
        dialog.setFilenameFilter { _, name ->
            val lower = name.lowercase()
            extensions.any { lower.endsWith(".$it") }
        }
    }
    dialog.isVisible = true // 模态阻塞，直到用户选择或取消

    val directory = dialog.directory ?: return emptyList()
    val files = dialog.files?.toList().orEmpty()
    if (files.isNotEmpty()) return files.filter { it.isFile }
    // 个别平台上多选模式会只回填 `file` 单值，这里兜一下。
    val single = dialog.file ?: return emptyList()
    return listOf(File(directory, single)).filter { it.isFile }
}

private fun readPickedFiles(files: List<File>): List<PickedTextFile> =
    files.map { PickedTextFile(name = it.name, content = it.readText()) }

/**
 * 另存为：AWT `FileDialog(SAVE)`，模态、UI 线程调用。
 * 用户 Cancel 时 `dialog.file` 为 null，返回 `false`（不写任何文件）。
 */
actual fun saveTextFile(suggestedName: String, content: String): Boolean {
    val dialog = FileDialog(null as Frame?, "保存文件", FileDialog.SAVE)
    dialog.file = suggestedName
    dialog.isVisible = true // 模态阻塞

    val directory = dialog.directory ?: return false
    val name = dialog.file ?: return false
    val target = File(directory, name)
    // 用户没在框里改后缀、又没写扩展名时，尽量补上建议的扩展名。
    val safeTarget = if (name.contains('.')) target else File(directory, "$name${suggestedName.substringAfterLast('.', "").let { if (it.isNotEmpty()) ".$it" else "" }}")
    safeTarget.parentFile?.mkdirs()
    safeTarget.writeText(content)
    return true
}
