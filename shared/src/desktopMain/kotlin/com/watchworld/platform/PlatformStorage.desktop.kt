package com.watchworld.platform

import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import java.io.File

/*
 * 桌面端只面向 **Windows**（项目目标平台 = Android + Windows），因此不做 macOS / Linux 分支。
 */

private const val APP_DIR_NAME = "WatchWorld"

/**
 * `%APPDATA%\WatchWorld`（Roaming，PLAN §5.5）。
 * ⚠️ `APPDATA` 可能为空（被裁剪的环境），退回 `user.home\AppData\Roaming`，而不是拼出 `null\WatchWorld`。
 */
actual fun appDataDir(): String {
    val roaming = System.getenv("APPDATA")?.takeIf { it.isNotBlank() }
        ?: File(System.getProperty("user.home").orEmpty(), "AppData/Roaming").absolutePath
    return File(roaming, APP_DIR_NAME).absolutePath
}

actual fun ensureAppDataDir(): String = File(appDataDir()).apply { mkdirs() }.absolutePath

actual fun createSqliteDriver(): SQLiteDriver = BundledSQLiteDriver()

actual val sqliteQueryDispatcher: CoroutineDispatcher = Dispatchers.IO
