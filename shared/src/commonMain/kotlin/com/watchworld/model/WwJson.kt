package com.watchworld.model

import kotlinx.serialization.json.Json

/**
 * 全局 JSON 配置（对应 PLAN §4.4「JSON 兼容配置」）。
 *
 * ⚠️ **全局只有这一个实例**：协议层（`net/`，SSE 帧解析）与数据层（`model/`、`data/`）
 * 共用，避免两处配置漂移。
 *
 * 各项的用途（T05 补齐，此前只有前两项）：
 *
 * | 配置 | 作用 | 不这么配会怎样 |
 * |---|---|---|
 * | `ignoreUnknownKeys = true` | 忽略老存档里的多余字段 | 导入老存档直接抛 `SerializationException` |
 * | `isLenient = true` | 容忍不带引号的字面量等宽松写法 | 手工编辑过的存档读不进来 |
 * | `explicitNulls = false` | `null` **不写回** JSON | 三态字段（`streamingEnabled` 等）的「未设置」会被写成显式 `null`，与 TS 的 `undefined` 语义分叉 |
 * | `encodeDefaults = false` | 等于默认值的字段默认不写 | 存档里塞满冗余默认值，与 Web 版导出的形状不一致 |
 * | `coerceInputValues = true` | `null` 或未知 enum 值出现在**有默认值**的字段上时退回默认值 | 老存档里一个新枚举值就让整个导入失败 |
 *
 * ⚠️ **`explicitNulls` 与编码器不一致的坑**：`Json { explicitNulls = false }` 只会把
 * 「因为默认值而省略」和「显式 null 但等于默认」两种情形写成缺字段；**显式传入 `null`
 * 且字段无默认值时仍会写成 `"key": null`**。数据类里所有可空字段都给了 `= null` 默认值，
 * 因此不会踩到；**新增可空字段时必须同样给默认值**，否则三态语义会破。
 *
 * ⚠️ 反过来：`explicitNulls = false` 也意味着**读取时无法区分**「字段缺失」与「字段是 null」。
 * 现有 TS 代码里这两者的处理本来就相同（都是 `undefined`/falsy），所以不构成兼容问题。
 */
val WwJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
    encodeDefaults = false
    coerceInputValues = true
}
