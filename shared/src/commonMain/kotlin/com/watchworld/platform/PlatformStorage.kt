package com.watchworld.platform

import androidx.sqlite.SQLiteDriver
import kotlinx.coroutines.CoroutineDispatcher

/*
 * 存储层需要平台回答的三个问题（PLAN §5.5）。目标平台只有 Android + Windows。
 *
 * ⚠️ 故意写成**顶层 expect 函数/属性**，不写 `expect object`：expect/actual 类在 Kotlin 2.4 仍是 Beta，
 * 会产生编译警告，而本仓要求零警告。与 [platformEnv] / [currentTimeMillis] 同一写法。
 * ⚠️ 不要把具体驱动写进 commonMain：`BundledSQLiteDriver` 只在按平台选的 `sqlite-bundled` 产物里。
 */

/**
 * 应用数据目录（**不含**文件名），Windows 上是 `%APPDATA%\WatchWorld`。
 * 只返回路径，**不负责建目录**，方便测试传临时目录绕开。
 */
expect fun appDataDir(): String

/** 创建并返回应用数据目录。只允许 Android / Windows 提供 actual。 */
expect fun ensureAppDataDir(): String

/** 一份新的 SQLite 驱动。每个 `WwDatabase` 实例配一份，不要跨库共用。 */
expect fun createSqliteDriver(): SQLiteDriver

/** Room 执行查询用的调度器。⚠️ 必须是 IO 类：SQLite 调用是阻塞式的。 */
expect val sqliteQueryDispatcher: CoroutineDispatcher
