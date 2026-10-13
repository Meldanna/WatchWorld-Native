package com.watchworld.model

import kotlinx.serialization.Serializable

/**
 * `src/types.ts:14 ApiProviderConfig`（11 个必填/常见字段 + 6 个全局默认采样参数）。
 *
 * ⚠️ `type` 有默认值 [ApiProviderType.CUSTOM]：老存档/未知 provider 类型靠 `coerceInputValues`
 *    退回默认值，而不是抛错。
 * ⚠️ `models` 在 legacy fixture 里是必填；这里给空数组默认值，容忍缺字段。
 */
@Serializable
data class ApiProviderConfig(
    val id: String,
    val name: String,
    val type: ApiProviderType = ApiProviderType.CUSTOM,
    val baseUrl: String? = null,
    val apiKey: String? = null,
    val defaultModel: String = "",
    val models: List<String> = emptyList(),
    val isCustom: Boolean? = null,
    val enabled: Boolean? = null,
    val isSystemDefault: Boolean? = null,
    /** 自定义请求头（适配不同中转站）。对应 TS 的 `Record<string, string>`。 */
    val customHeaders: Map<String, String>? = null,
    // ↓ 全局默认采样参数：仅当窗口与顾问都未指定时生效，可被窗口级参数覆盖
    val defaultTemperature: Double? = null,
    val defaultTopP: Double? = null,
    val defaultTopK: Int? = null,
    val defaultFrequencyPenalty: Double? = null,
    val defaultPresencePenalty: Double? = null,
    val defaultMaxTokens: Int? = null,
)

/**
 * `src/types.ts:36 ModelFavorite`（跨 Provider 的模型收藏）。
 * 存储层是复合主键 `(providerId, modelName)`，见 PLAN §5.1。
 */
@Serializable
data class ModelFavorite(
    val providerId: String,
    val modelName: String,
    val addedAt: Long,
)
