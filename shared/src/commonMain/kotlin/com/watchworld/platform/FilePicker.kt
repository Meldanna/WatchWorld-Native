package com.watchworld.platform

/**
 * 本地文件选择：把「挑文本文件并读出内容」抽成平台能力。
 *
 * 桌面端调用会弹出系统模态文件框，必须在 UI 线程调用。
 */
expect fun pickTextFile(extensions: List<String> = listOf("md", "txt")): PickedTextFile?

/** 批量选择文本文件；取消时返回空列表。 */
expect fun pickTextFiles(extensions: List<String> = listOf("md", "txt")): List<PickedTextFile>

/** 选择待发送图片；调用方负责交给 [ImageStore.saveFile] 持久化。 */
expect fun pickImageFiles(): List<PickedImageFile>

/** 保存文本文件到磁盘；用户取消时返回 false。 */
expect fun saveTextFile(suggestedName: String, content: String): Boolean

data class PickedTextFile(val name: String, val content: String)

data class PickedImageFile(val name: String, val mime: String, val bytes: ByteArray)
