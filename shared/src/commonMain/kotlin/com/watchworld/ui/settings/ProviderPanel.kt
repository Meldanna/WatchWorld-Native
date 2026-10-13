package com.watchworld.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.watchworld.model.ApiProviderConfig
import com.watchworld.model.ApiProviderType
import com.watchworld.net.ProviderModelsResult
import com.watchworld.net.ProviderTestResult
import com.watchworld.platform.currentTimeMillis
import com.watchworld.ui.components.InfoHint
import com.watchworld.ui.theme.WwPad
import com.watchworld.ui.theme.WwSize
import com.watchworld.ui.theme.WwType
import com.watchworld.ui.components.WwPrimaryButton
import com.watchworld.ui.components.WwSecondaryButton
import com.watchworld.ui.components.WwSelectField
import com.watchworld.ui.components.WwTag
import com.watchworld.ui.components.WwTextField
import com.watchworld.ui.components.dashedRoundedBorder
import com.watchworld.ui.theme.WwSurfaces
import compose.icons.TablerIcons
import compose.icons.tablericons.Activity
import compose.icons.tablericons.AlertCircle
import compose.icons.tablericons.CircleCheck
import compose.icons.tablericons.Eye
import compose.icons.tablericons.EyeOff
import compose.icons.tablericons.Key
import compose.icons.tablericons.Plus
import compose.icons.tablericons.Refresh
import compose.icons.tablericons.Settings
import compose.icons.tablericons.Trash
import kotlinx.coroutines.launch

/**
 * Provider 配置面板（对标 Web 版 `ProviderModal.tsx`）。
 *
 * ### 结构（对齐参考版的两层）
 * - **列表层**：本 Panel 的正文，由调用点的 [FeatureDialog] 提供外壳（参考版主对话框 `z-50`）。
 *   每个 Provider 一张卡：名称 + 「内置预配置」+ 默认模型 + 「测试连接 / 配置详情 / 删除」，
 *   卡内展示该 Provider 最近一次测试结果。
 * - **表单层**：新建/编辑是**叠在列表之上的独立弹层**（参考版 `z-[80]`），
 *   底部是固定操作区（取消 / 保存配置），表单再长也不跟着滚。
 *   ⚠️ 这样点遮罩只会关掉表单、回到列表，不会把整个弹窗连表单一起关掉（参考版 L264-268 的守卫）。
 *
 * ⚠️ 与 Web 版的差异：
 * 1. Web 经 `/api/…` 代理发请求，这里由 [ProviderTestResult] / [ProviderModelsResult]
 *    的回调（`SettingsViewModel` → `net/ProviderApi.kt`）直接对目标 URL 发 HTTP。
 * 2. 协议下拉**不含** `custom`（参考版只有 7 项）。枚举里的 `CUSTOM` 保留，只为兼容存量数据与展示。
 */
@Composable
fun ProviderPanel(
    items: List<ApiProviderConfig>,
    onSave: (ApiProviderConfig) -> Unit,
    onDelete: (String) -> Unit,
    onTestConnection: suspend (ApiProviderConfig) -> ProviderTestResult,
    onFetchModels: suspend (ApiProviderConfig) -> ProviderModelsResult,
) {
    val scope = rememberCoroutineScope()

    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ApiProviderConfig?>(null) }
    var confirmingDelete by remember { mutableStateOf<ApiProviderConfig?>(null) }

    // ── 表单状态 ──
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(ApiProviderType.OPENAI) }
    var baseUrl by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var models by remember { mutableStateOf<List<String>>(emptyList()) }
    var headers by remember { mutableStateOf<List<HeaderEntry>>(emptyList()) }
    var showKey by remember { mutableStateOf(false) }

    // ── 拉取模型状态 ──
    var fetching by remember { mutableStateOf(false) }
    var fetched by remember { mutableStateOf<List<String>>(emptyList()) }
    var fetchError by remember { mutableStateOf<String?>(null) }
    var showFetched by remember { mutableStateOf(false) }
    var modelSearch by remember { mutableStateOf("") }

    // ── 测试连接状态 ──
    var testingId by remember { mutableStateOf<String?>(null) }
    var testResults by remember { mutableStateOf<Map<String, ProviderTestResult>>(emptyMap()) }

    fun resetForm() {
        name = ""; type = ApiProviderType.OPENAI; baseUrl = ""; apiKey = ""
        models = emptyList(); headers = emptyList()
        showKey = false; fetched = emptyList(); fetchError = null
        showFetched = false; modelSearch = ""
    }

    fun startCreate() {
        resetForm()
        creating = true
        editing = null
    }

    fun startEdit(p: ApiProviderConfig) {
        name = p.name; type = p.type; baseUrl = p.baseUrl.orEmpty(); apiKey = p.apiKey.orEmpty()
        models = p.models
        headers = p.customHeaders.orEmpty().map { (k, v) -> HeaderEntry(k, v) }
        showKey = false; fetched = emptyList(); fetchError = null
        showFetched = false; modelSearch = ""
        creating = false
        editing = p
    }

    fun closeForm() {
        creating = false
        editing = null
        resetForm()
    }

    fun currentFormProvider(id: String): ApiProviderConfig = ApiProviderConfig(
        id = id,
        name = name,
        type = type,
        baseUrl = normalizeProviderBaseUrl(baseUrl.trim(), type),
        apiKey = apiKey.trim().ifBlank { null },
        models = models,
        // 「默认模型」不再是可填字段：取已勾选的第一条。
        defaultModel = models.firstOrNull().orEmpty(),
        customHeaders = headersToMap(headers),
    )

    fun saveForm() {
        if (name.isBlank()) return
        onSave(
            (editing ?: ApiProviderConfig(id = "provider-custom-${currentTimeMillis()}", name = "")).copy(
                name = name.trim(),
                type = type,
                baseUrl = normalizeProviderBaseUrl(baseUrl.trim(), type),
                apiKey = apiKey.trim().ifBlank { null },
                models = models,
                // 用户去掉了「默认模型」字段：有勾选取第一条，没勾选则保留原值（不清空老配置）。
                defaultModel = models.firstOrNull() ?: editing?.defaultModel.orEmpty(),
                enabled = true,
                customHeaders = headersToMap(headers),
            ),
        )
        closeForm()
    }

    // ── 列表层（本 Panel 正文）──
    // 卡片间距由容器负责（cardGap 6dp），卡片自身不加垂直外边距（避免相邻两卡叠加成 8dp）。
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(WwSize.cardGap),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("已配置的 API 提供方", fontSize = WwType.hint, lineHeight = WwSize.compactTextLineHeight, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            WwTag(
                text = "添加接口",
                shape = RoundedCornerShape(10.dp),
                background = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                leadingIcon = TablerIcons.Plus,
                onClick = { startCreate() },
            )
        }
        items.forEach { provider ->
            val isTesting = testingId == provider.id
            val result = testResults[provider.id]
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = WwSurfaces.card,
                border = BorderStroke(1.dp, WwSurfaces.cardBorder),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = WwPad.card)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(TablerIcons.Key, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(6.dp))
                                Text(provider.name.ifBlank { provider.id }, fontSize = WwType.body, lineHeight = WwSize.compactTextLineHeight)
                                if (provider.isSystemDefault == true) {
                                    Spacer(Modifier.width(6.dp))
                                    Text("· 内置预配置", fontSize = WwType.hint, lineHeight = WwSize.compactTextLineHeight, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            Text(
                                if (provider.models.isEmpty()) "未选择模型"
                                else "已选 ${provider.models.size} 个模型",
                                fontSize = WwType.hint,
                                lineHeight = WwSize.compactTextLineHeight,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        WwSecondaryButton(
                            text = if (isTesting) "测试中..." else "测试连接",
                            onClick = {
                                testingId = provider.id
                                scope.launch {
                                    val outcome = onTestConnection(provider)
                                    testResults = testResults + (provider.id to outcome)
                                    testingId = null
                                }
                            },
                        )
                                                Spacer(Modifier.width(4.dp))
                        IconButton(onClick = { startEdit(provider) }, modifier = Modifier.size(26.dp)) {
                            Icon(TablerIcons.Settings, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        // 内置预配置不可删（参考版 L731 的 `!isSystemDefault` 守卫）。
                        if (provider.isSystemDefault != true) {
                            IconButton(onClick = { confirmingDelete = provider }, modifier = Modifier.size(26.dp)) {
                                Icon(TablerIcons.Trash, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    result?.let {
                        Spacer(Modifier.height(WwSize.cardListGap))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (it.success) TablerIcons.CircleCheck else TablerIcons.AlertCircle,
                                null,
                                Modifier.size(14.dp),
                                tint = if (it.success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                it.message,
                                fontSize = WwType.hint,
                                color = if (it.success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
        if (items.isEmpty()) {
            Text("还没有配置任何 API 提供方，点击「添加接口」开始。", fontSize = WwType.hint, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    // ── 表单层：叠在列表之上的独立弹层 ──
    if (creating || editing != null) {
        FeatureDialog(
            title = if (creating) "新建 API 提供方" else "编辑：${editing?.name.orEmpty()}",
            onDismiss = { closeForm() },
            maxWidth = 576.dp,
            heightFraction = 0.9f,
            footer = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.weight(1f))
                    WwSecondaryButton("取消", onClick = { closeForm() })
                    WwPrimaryButton(
                        text = "保存配置",
                        onClick = { saveForm() },
                        enabled = name.isNotBlank(),
                    )
                }
            },
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(WwSize.cardListGap),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    WwTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = "接口显示名称 *",
                        placeholder = "例如：自建 DeepSeek、我的 Ollama...",
                        modifier = Modifier.weight(2f),
                    )
                    // 参考版下拉是 7 项（gemini/openai/claude/deepseek/openrouter/groq/ollama），
                    // 没有 `custom` —— 自建端点直接选「OpenAI 兼容」。
                    // ⚠️ 必须给权重：不然它内部的 `fillMaxWidth` 会把左边的名称框挤成竖排。
                    WwSelectField(
                        current = typeLabel(type),
                        options = SELECTABLE_PROVIDER_TYPES.map(::typeLabel),
                        onSelect = { picked ->
                            SELECTABLE_PROVIDER_TYPES.firstOrNull { typeLabel(it) == picked }?.let { type = it }
                        },
                        label = "接口协议类型",
                        modifier = Modifier.weight(1f),
                    )
                }

                WwTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = "API Base URL (端点地址)",
                    placeholder = "例如：https://api.openai.com 或 http://localhost:11434",
                    hint = "OpenAI/Claude 兼容协议会自动补全到 /v1；Gemini 可置空（默认走官方接口）。",
                    fontSize = WwType.hint,
                    monospace = true,
                )

                WwTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = "API Key (密钥)",
                    placeholder = "sk-...",
                    hint = "所有密钥仅保存在本地，不会传到任何第三方数据库。",
                    fontSize = WwType.hint,
                    monospace = true,
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailing = {
                        Icon(
                            imageVector = if (showKey) TablerIcons.EyeOff else TablerIcons.Eye,
                            contentDescription = if (showKey) "隐藏密钥" else "显示密钥",
                            modifier = Modifier.size(15.dp).clickable { showKey = !showKey },
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )

                // 「默认模型」字段已按用户要求移除：模型取自己勾选的那批（第一条即默认）。
                // 这里只留「获取模型」入口 —— 它是模型多选区的数据来源。
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "可用模型",
                        fontSize = WwType.hint,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    WwSecondaryButton(
                        text = if (fetching) "拉取中..." else "获取模型",
                        onClick = {
                            fetching = true
                            fetchError = null
                            fetched = emptyList()
                            showFetched = false
                            val temp = currentFormProvider("temp")
                            scope.launch {
                                val result = onFetchModels(temp)
                                fetching = false
                                fetchError = result.error
                                fetched = result.models
                                showFetched = result.models.isNotEmpty()
                                modelSearch = ""
                            }
                        },
                    )
                }

                fetchError?.let {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(TablerIcons.AlertCircle, null, Modifier.size(13.dp), tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(6.dp))
                        Text(it, fontSize = WwType.hint, color = MaterialTheme.colorScheme.error)
                    }
                }

                if (showFetched && fetched.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = WwSurfaces.inset,
                        border = BorderStroke(1.dp, WwSurfaces.cardBorderSubtle),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = WwPad.card),
                            verticalArrangement = Arrangement.spacedBy(WwSize.cardListGap),
                        ) {
                            WwTextField(
                                value = modelSearch,
                                onValueChange = { modelSearch = it },
                                placeholder = "搜索 ${fetched.size} 个模型...",
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = models.size == fetched.size,
                                    onCheckedChange = { checked -> models = if (checked) fetched else emptyList() },
                                )
                                Text("全选 / 取消全选", fontSize = WwType.hint)
                                Spacer(Modifier.weight(1f))
                                Text(
                                    "已选 ${models.size} / ${fetched.size}",
                                    fontSize = WwType.hint,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            val filtered = fetched.filter { it.contains(modelSearch, ignoreCase = true) }
                            Column(
                                modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp).verticalScroll(rememberScrollState()),
                            ) {
                                if (filtered.isEmpty()) {
                                    Text(
                                        "无匹配模型",
                                        fontSize = WwType.hint,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                filtered.forEach { model ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = model in models,
                                            onCheckedChange = { checked ->
                                                models = if (checked) models + model else models - model
                                            },
                                        )
                                        Text(model, fontSize = WwType.body, fontFamily = FontFamily.Monospace)
                                    }
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                Spacer(Modifier.weight(1f))
                                WwSecondaryButton("收起", onClick = { showFetched = false })
                                WwPrimaryButton("确认", onClick = { showFetched = false })
                            }
                        }
                    }
                }

                if (models.isNotEmpty() && !showFetched) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            "可用模型列表（${models.size} 个）",
                            fontSize = WwType.hint,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        models.forEach { model ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    model,
                                    fontSize = WwType.body,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.weight(1f),
                                )
                                Icon(
                                    TablerIcons.Trash,
                                    contentDescription = "移除此模型",
                                    modifier = Modifier.size(14.dp).clickable { models = models - model },
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(WwSize.cardListGap)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "自定义请求头 (Headers)",
                            fontSize = WwType.hint,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(6.dp))
                        InfoHint("适配聚合站、OneAPI 和自建中转认证。名称与值分别填写，无需写 JSON。")
                        Spacer(Modifier.weight(1f))
                        WwTag(
                            text = "新建请求头",
                            shape = RoundedCornerShape(10.dp),
                            background = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            leadingIcon = TablerIcons.Plus,
                            onClick = { headers = headers + HeaderEntry() },
                        )
                    }

                    if (headers.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .dashedRoundedBorder(WwSurfaces.cardBorderSubtle)
                                .padding(horizontal = 12.dp, vertical = WwPad.card),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "暂无自定义请求头，需要时点击“新建请求头”添加",
                                fontSize = WwType.hint,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        headers.forEachIndexed { index, entry ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                WwTextField(
                                    value = entry.name,
                                    onValueChange = { value ->
                                        headers = headers.toMutableList().also { it[index] = entry.copy(name = value) }
                                    },
                                    placeholder = "请求头名称，例如 HTTP-Referer",
                                    fontSize = WwType.hint,
                                    monospace = true,
                                    modifier = Modifier.weight(1f),
                                )
                                WwTextField(
                                    value = entry.value,
                                    onValueChange = { value ->
                                        headers = headers.toMutableList().also { it[index] = entry.copy(value = value) }
                                    },
                                    placeholder = "请求头值",
                                    fontSize = WwType.hint,
                                    monospace = true,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(
                                    onClick = { headers = headers.filterIndexed { i, _ -> i != index } },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        TablerIcons.Trash,
                                        contentDescription = "删除请求头",
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ── 删除确认（参考版用 `confirm()`，这里换成同语义的确认框）──
    confirmingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { confirmingDelete = null },
            title = { Text("删除 API 接口") },
            text = { Text("确认删除「${target.name.ifBlank { target.id }}」？该操作不可撤销。") },
            confirmButton = {
                WwTag(
                    text = "删除",
                    onClick = {
                        onDelete(target.id)
                        confirmingDelete = null
                    },
                    background = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                )
            },
            dismissButton = {
                WwTag(
                    text = "取消",
                    onClick = { confirmingDelete = null },
                    background = WwSurfaces.inset,
                )
            },
        )
    }
}

private data class HeaderEntry(val name: String = "", val value: String = "")

private fun headersToMap(entries: List<HeaderEntry>): Map<String, String>? {
    val map = linkedMapOf<String, String>()
    entries.forEach { entry ->
        val key = entry.name.trim()
        if (key.isNotEmpty()) map[key] = entry.value.trim()
    }
    return map.ifEmpty { null }
}

private val V1_API_TYPES: Set<ApiProviderType> = setOf(
    ApiProviderType.OPENAI,
    ApiProviderType.CLAUDE,
    ApiProviderType.DEEPSEEK,
    ApiProviderType.OLLAMA,
    ApiProviderType.OPENROUTER,
    ApiProviderType.GROQ,
    ApiProviderType.CUSTOM,
)

/**
 * 协议下拉里可选的 7 项（`ProviderModal.tsx:359-365` 逐项对齐）。
 * ⚠️ **不含 `CUSTOM`** —— 参考版下拉只有这 7 项，自建端点选「OpenAI 兼容」即可。
 * 枚举里的 `CUSTOM` 只为兼容存量数据保留（[typeLabel] 仍能显示它）。
 */
private val SELECTABLE_PROVIDER_TYPES: List<ApiProviderType> = listOf(
    ApiProviderType.GEMINI,
    ApiProviderType.OPENAI,
    ApiProviderType.CLAUDE,
    ApiProviderType.DEEPSEEK,
    ApiProviderType.OPENROUTER,
    ApiProviderType.GROQ,
    ApiProviderType.OLLAMA,
)

/**
 * OpenAI / Claude 兼容协议统一使用 `/v1` API 根路径；移除误填的具体 API 端点。
 */
internal fun normalizeProviderBaseUrl(raw: String, type: ApiProviderType): String? {
    if (raw.isBlank()) return null
    var url = raw.trimEnd('/')
    if (type !in V1_API_TYPES) return url
    val endpointSuffixes = when (type) {
        ApiProviderType.CLAUDE -> listOf("/models", "/messages")
        else -> listOf("/models", "/chat/completions")
    }
    endpointSuffixes.firstOrNull(url::endsWith)?.let { suffix ->
        url = url.removeSuffix(suffix)
    }
    if (!url.endsWith("/v1")) {
        url += "/v1"
    }
    return url
}

private fun typeLabel(type: ApiProviderType): String = when (type) {
    ApiProviderType.GEMINI -> "Google Gemini"
    ApiProviderType.OPENAI -> "OpenAI 兼容"
    ApiProviderType.CLAUDE -> "Claude 兼容"
    ApiProviderType.DEEPSEEK -> "DeepSeek 深度求索"
    ApiProviderType.OPENROUTER -> "OpenRouter 汇聚"
    ApiProviderType.GROQ -> "Groq 极速"
    ApiProviderType.OLLAMA -> "Ollama 本地/自建"
    ApiProviderType.CUSTOM -> "自定义 (OpenAI 兼容)"
}
