package com.watchworld.domain

/**
 * 全量备份的文件名（对标参考版 `App.tsx:1854-1863 handleExportData`）：
 * `watchworld-backup-${ISO 日期}.json`，日期取 **UTC**（`new Date().toISOString().slice(0, 10)`）。
 *
 * ⚠️ 为什么手写公历换算而不是引 `kotlinx-datetime`：这里只需要「年月日」三个数，
 *    为一个文件名引一个库不划算。算法是 Howard Hinnant 的 `civil_from_days`
 *    （`http://howardhinnant.github.io/date_algorithms.html`），纯整数运算、可测。
 */
fun backupFileName(epochMillis: Long): String = "watchworld-backup-${isoDateUtc(epochMillis)}.json"

/** UTC 毫秒时间戳 → `YYYY-MM-DD`（UTC）。 */
fun isoDateUtc(epochMillis: Long): String {
    val millisPerDay = 86_400_000L
    // 向下取整的除法：负数时间戳（1970 之前）不能截断到 0。
    val days = if (epochMillis >= 0) {
        epochMillis / millisPerDay
    } else {
        -((-epochMillis + millisPerDay - 1) / millisPerDay)
    }

    val z = days + 719_468L
    val era = (if (z >= 0) z else z - 146_096L) / 146_097L
    val doe = z - era * 146_097L                                   // [0, 146096]
    val yoe = (doe - doe / 1460 + doe / 36_524 - doe / 146_096) / 365  // [0, 399]
    val y = yoe + era * 400
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)              // [0, 365]
    val mp = (5 * doy + 2) / 153                                   // [0, 11]
    val day = doy - (153 * mp + 2) / 5 + 1                         // [1, 31]
    val month = if (mp < 10) mp + 3 else mp - 9                    // [1, 12]
    val year = if (month <= 2) y + 1 else y

    return "$year-${pad2(month)}-${pad2(day)}"
}

private fun pad2(value: Long): String = if (value < 10) "0$value" else value.toString()
