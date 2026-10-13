package com.watchworld.net

import com.watchworld.platform.platformEnv

/** 硬编码的 Provider 配置。 */
data class HardcodedChatProviderConfig(
    val name: String,
    val baseUrl: String,
    val model: String,
    val apiKey: String,
) {
    val chatCompletionsUrl: String get() = baseUrl.trimEnd('/') + "/chat/completions"
    val isConfigured: Boolean get() = apiKey.isNotBlank()
}

/**
 * T03 的**硬编码 Provider 配置** —— 绕过存档与设置界面（WORKPLAN §三 T03）。
 *
 * ## 为什么 baseUrl / model 写死，而 apiKey 只从环境变量取
 * 「配置写死在代码里」是为了先不看存储层；但 **API Key 不能写进代码**，
 * 否则一旦提交就是密钥泄露。所以：
 * - `baseUrl` / `model` 写死默认值（可被环境变量覆盖，方便换服务商）；
 * - `apiKey` **只能**来自环境变量，默认空 —— 空则 UI 明确报错（见 `ChatStreamClient`）。
 *
 * ```bash
 * # 用 DeepSeek（默认，OpenAI 兼容）：
 * set WW_T03_API_KEY=sk-xxxx          # Windows cmd
 * export WW_T03_API_KEY=sk-xxxx       # Git Bash
 *
 * # 换成别的 OpenAI 兼容服务：
 * export WW_T03_BASE_URL=https://api.moonshot.cn/v1
 * export WW_T03_MODEL=moonshot-v1-8k
 * ```
 *
 * ⚠️ **T15 落地设置界面 / T05 落地存档后，本文件应删除** —— 配置改由
 * `Storage` 的 `providers` 提供（对应 React 版 `DEFAULT_PROVIDERS` + `Storage.getProviders`）。
 * 它和 `MockChatStream` 一样是**刻意的临时产物**。
 */
object HardcodedChatProvider {

    fun resolve(): HardcodedChatProviderConfig = HardcodedChatProviderConfig(
        name = "DeepSeek (OpenAI 兼容)",
        baseUrl = platformEnv("WW_T03_BASE_URL")?.takeIf { it.isNotBlank() }
            ?: "https://api.deepseek.com/v1",
        model = platformEnv("WW_T03_MODEL")?.takeIf { it.isNotBlank() }
            ?: "deepseek-chat",
        apiKey = platformEnv("WW_T03_API_KEY").orEmpty(),
    )
}
