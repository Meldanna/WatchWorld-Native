package com.watchworld.data.db

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection

/**
 * Room KMP 版的「事务」入口。
 *
 * ⚠️ `androidx.room.withTransaction` 是 **Android 专属**（room-ktx），commonMain 里解析不到。
 * KMP 的等价写法是 `useWriterConnection { it.immediateTransaction { ... } }`；
 * 块内调用的 DAO 会复用同一条写连接，因此仍在同一个事务里。嵌套调用由 Room 用 savepoint 处理。
 */
suspend fun <T> WwDatabase.withTransaction(block: suspend () -> T): T =
    useWriterConnection { transactor -> transactor.immediateTransaction { block() } }
