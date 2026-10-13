package com.watchworld.net

import io.ktor.client.HttpClient

/**
 * Ktor `HttpClient` 工厂。
 *
 * ## 引擎
 * 这里**不显式指定引擎**，靠 Ktor 的引擎自动发现从各平台源集取：
 * 当前 `desktopMain` 依赖 `ktor-client-okhttp`，因此解析为 **OkHttp**。
 * T22 加 Android 目标时同样依赖 okhttp（PLAN §2.3），所以两端行为一致。
 *
 * 好处是 `net/` 与 `ui/` 全部留在 commonMain，不需要为一个引擎写 expect/actual。
 * 若哪天需要强制指定引擎或按平台设不同参数，再改成 `expect fun`。
 *
 * ## 为什么**不**装 `HttpTimeout`
 * 流式请求的耗时取决于模型生成多久（可达数分钟）。Ktor 的超时插件会把
 * **整个请求（含读 body）**计入 `requestTimeoutMillis`，一旦装上就容易在长回复中途
 * 把流掐断 —— 那正好是"真流式"最怕的失败模式。
 * 宁可先不设超时，等到有明确需求时再按「只在 connect 阶段计时」的方式加。
 */
object HttpClientFactory {
    fun create(): HttpClient = HttpClient {
        // 非 2xx 不抛异常，交给 ChatStreamClient 读取错误体后自己抛，错误信息更完整。
        expectSuccess = false
    }
}
