package com.watchworld.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.watchworld.domain.RegexTestResult
import com.watchworld.domain.parseMcpText
import com.watchworld.domain.parseSkillText
import com.watchworld.domain.testRegexRule
import com.watchworld.model.Agent
import com.watchworld.model.AgentSkill
import com.watchworld.model.AppPreferences
import com.watchworld.model.BuiltinThemePalette
import com.watchworld.model.FileAccess
import com.watchworld.model.KnowledgeItem
import com.watchworld.model.McpServerConfig
import com.watchworld.model.McpServerStatus
import com.watchworld.model.McpTransportType
import com.watchworld.model.MessageDisplaySettings
import com.watchworld.model.RegexRule
import com.watchworld.model.RegexScope
import com.watchworld.model.ThemePalette
import com.watchworld.model.UiMode
import com.watchworld.net.McpConnectionTestResult
import com.watchworld.platform.AvatarOwner
import com.watchworld.platform.currentTimeMillis
import com.watchworld.platform.defaultAvatarStore
import com.watchworld.platform.pickImageFiles
import com.watchworld.platform.pickTextFile
import com.watchworld.platform.pickTextFiles
import com.watchworld.ui.components.AvatarImage
import com.watchworld.ui.components.AvatarSource
import com.watchworld.ui.components.InfoHint
import com.watchworld.ui.components.WwTag
import com.watchworld.ui.components.SearchField
import com.watchworld.ui.components.WwPrimaryButton
import com.watchworld.ui.components.WwRangeSlider
import com.watchworld.ui.components.WwSecondaryButton
import com.watchworld.ui.components.WwSelectField
import com.watchworld.ui.components.WwSwitch
import com.watchworld.ui.components.WwTextField
import com.watchworld.ui.components.dashedRoundedBorder
import com.watchworld.ui.theme.WwPad
import com.watchworld.ui.theme.WwSize
import com.watchworld.ui.theme.WwSurfaces
import com.watchworld.ui.theme.WwType
import compose.icons.TablerIcons
import compose.icons.tablericons.Activity
import compose.icons.tablericons.AlertCircle
import compose.icons.tablericons.Check
import compose.icons.tablericons.ChevronDown
import compose.icons.tablericons.ChevronRight
import compose.icons.tablericons.Edit
import compose.icons.tablericons.FileImport
import compose.icons.tablericons.Lock
import compose.icons.tablericons.Plus
import compose.icons.tablericons.Settings
import compose.icons.tablericons.Square
import compose.icons.tablericons.SquareCheck
import compose.icons.tablericons.Tool
import compose.icons.tablericons.ToggleLeft
import compose.icons.tablericons.ToggleRight
import compose.icons.tablericons.Trash
import compose.icons.tablericons.UserCheck
import compose.icons.tablericons.Upload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AgentPanel(
    items: List<Agent>,
    onSave: (Agent) -> Unit,
    onDelete: (String) -> Unit,
    mcpServers: List<McpServerConfig> = emptyList(),
    skills: List<AgentSkill> = emptyList(),
    activeAgentId: String? = null,
    onSelectAgent: ((String) -> Unit)? = null,
) {
    var editing by remember { mutableStateOf<Agent?>(null) }
    var creating by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf<Agent?>(null) }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(WwSize.cardListGap)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("可用顾问列表", fontSize = WwType.hint, lineHeight = WwSize.compactTextLineHeight,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            WwSecondaryButton("新建顾问", onClick = {
                // 参考版 `startCreate`：新 id = `agent-custom-${Date.now()}`，默认头像 🤖 / 标签「自定义」。
                editing = Agent(id = "agent-custom-${currentTimeMillis()}", avatar = "🤖", tags = listOf("自定义"))
                creating = true
            }, leadingIcon = TablerIcons.Plus)
        }
        items.forEach { agent ->
            val selected = agent.id == activeAgentId
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { onSelectAgent?.invoke(agent.id) },
                shape = RoundedCornerShape(10.dp),
                color = if (selected) WwSurfaces.cardSelected else WwSurfaces.card,
                border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) WwSurfaces.cardBorder else WwSurfaces.cardBorderSubtle),
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = WwPad.card), verticalAlignment = Alignment.Top) {
                    // 头像：上传过图片就画图，否则用 emoji（结构性徽章，按标准 §一豁免 #2 不跟三档）。
                    AvatarImage(
                        source = AvatarSource.Agent(agent.id, agent.avatar.ifBlank { "🤖" }),
                        size = 24.dp,
                        modifier = Modifier.padding(end = 10.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(agent.name.ifBlank { agent.id }, fontSize = WwType.body, lineHeight = WwSize.compactTextLineHeight,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            agent.tags.orEmpty().forEach { tag -> Text(" · $tag", fontSize = WwType.hint, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                        Text(agent.description, fontSize = WwType.hint, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    if (selected) {
                        // 参考版：20dp 圆形徽章（`w-5 h-5 rounded-full bg(--m2-soft)` + 主色 Check 13）。
                        Surface(shape = RoundedCornerShape(10.dp), color = WwSurfaces.accentSoft, modifier = Modifier.size(20.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(TablerIcons.Check, "当前 Agent", Modifier.size(13.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    IconButton(onClick = { editing = agent; creating = false }, Modifier.size(28.dp)) { Icon(TablerIcons.Edit, "编辑", Modifier.size(14.dp)) }
                    // 内置顾问不给删除入口（参考版 `!agent.isBuiltin` 才渲染）。
                    if (agent.isBuiltin != true) IconButton(onClick = { confirmingDelete = agent }, Modifier.size(28.dp)) { Icon(TablerIcons.Trash, "删除", Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
    editing?.let { initial ->
        AgentEditorDialog(initial, creating, mcpServers, skills, { editing = null }) { saved -> onSave(saved); editing = null }
    }
    confirmingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { confirmingDelete = null },
            title = { Text("删除顾问") },
            text = { Text("确认删除顾问「${target.name.ifBlank { target.id }}」？") },
            confirmButton = {
                WwTag(
                    text = "删除",
                    onClick = { onDelete(target.id); confirmingDelete = null },
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

/**
 * 顾问的新建 / 编辑表单（对标参考版 `AgentModal.tsx:171-463` 的第二个弹层）。
 *
 * ⚠️ 参考版把表单**又套了一层 `fixed inset-0 z-[80]`**，在列表弹窗之内再开一个弹窗；
 *    原生按项目统一规格改用 `FeatureDialog`（`memory/ui-replication-requirements.md` §七
 *    的三段式：固定顶栏 + 滚动内容 + 固定底栏）。字段与顺序与参考版逐项一致。
 */
@Composable
private fun AgentEditorDialog(
    initial: Agent,
    creating: Boolean,
    mcpServers: List<McpServerConfig>,
    skills: List<AgentSkill>,
    onDismiss: () -> Unit,
    onSave: (Agent) -> Unit,
) {
    var avatar by remember(initial.id) { mutableStateOf(initial.avatar.ifBlank { "🤖" }) }
    var name by remember(initial.id) { mutableStateOf(initial.name) }
    var description by remember(initial.id) { mutableStateOf(initial.description) }
    var prompt by remember(initial.id) { mutableStateOf(initial.systemPrompt) }
    var temperature by remember(initial.id) { mutableStateOf(initial.temperature.toFloat()) }
    var tags by remember(initial.id) { mutableStateOf(initial.tags.orEmpty().joinToString(", ")) }
    var memory by remember(initial.id) { mutableStateOf(initial.memory.orEmpty()) }
    var access by remember(initial.id) { mutableStateOf(initial.fileAccess ?: FileAccess.NONE) }
    var mcpIds by remember(initial.id) { mutableStateOf(initial.mcpServerIds.orEmpty()) }
    var skillIds by remember(initial.id) { mutableStateOf(initial.skillIds.orEmpty()) }

    // Agent 头像图片：`agents/<id>/` 下的一张图，**与 emoji 分开存** ——
    // 图片在就画图片、不在就退回 emoji 输入框里的那个字符（谁都不用改模型的 `avatar` 字段）。
    var hasAvatarImage by remember(initial.id) { mutableStateOf(false) }
    var avatarVersion by remember(initial.id) { mutableStateOf(0) }
    val avatarScope = rememberCoroutineScope()
    LaunchedEffect(initial.id, avatarVersion) {
        hasAvatarImage = withContext(Dispatchers.IO) {
            defaultAvatarStore.exists(AvatarOwner.Agent, initial.id)
        }
    }

    FeatureDialog(
        title = if (creating) "新建专属顾问" else "编辑顾问：${initial.name}",
        subtitle = "切换不同角色设定或创建专属顾问",
        icon = TablerIcons.UserCheck,
        onDismiss = onDismiss,
        maxWidth = 576.dp,
        heightFraction = 0.9f,
        footer = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Spacer(Modifier.weight(1f))
                WwSecondaryButton("取消", onClick = onDismiss)
                WwPrimaryButton(
                    text = "保存顾问",
                    enabled = name.isNotBlank(),
                    onClick = {
                        onSave(
                            initial.copy(
                                name = name.trim(),
                                avatar = avatar.trim().ifBlank { "🤖" },
                                description = description.trim(),
                                systemPrompt = prompt.trim(),
                                temperature = temperature.toDouble(),
                                // 参考版 `split(/[,，\s]+/)`；空则回退「自定义」（`AgentModal.tsx:95-98,107`）。
                                tags = parseTagList(tags).ifEmpty { listOf("自定义") },
                                memory = memory.trim().ifBlank { null },
                                fileAccess = access,
                                mcpServerIds = mcpIds,
                                skillIds = skillIds,
                            ),
                        )
                    },
                )
            }
        },
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(WwSize.cardListGap)) {
            // 头像（1/4）+ 顾问名称（3/4）：参考版 `grid-cols-4` + `col-span-3`。
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                WwTextField(avatar, { avatar = it }, label = "头像", modifier = Modifier.width(88.dp))
                WwTextField(
                    name, { name = it },
                    label = "顾问名称 *",
                    placeholder = "例如：世界观推演师、剧情评估师...",
                    modifier = Modifier.weight(1f),
                )
            }
            // 头像图片（可选）：有图片时消息区与顾问列表都画图片，emoji 只作为没有图片时的兜底。
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                AvatarImage(
                    source = AvatarSource.Agent(initial.id, avatar),
                    size = 40.dp,
                    reloadToken = avatarVersion,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "顾问头像图片（可选，优先于左侧 emoji）",
                    fontSize = WwType.hint,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                WwSecondaryButton(
                    text = "上传",
                    leadingIcon = TablerIcons.Upload,
                    onClick = {
                        // 文件框是模态的：在点击回调（UI 线程）里同步挑完，再交给协程落盘。
                        val picked = pickImageFiles().firstOrNull()
                        if (picked != null) {
                            avatarScope.launch {
                                withContext(Dispatchers.IO) {
                                    defaultAvatarStore.save(AvatarOwner.Agent, initial.id, picked.bytes, picked.mime)
                                }
                                avatarVersion++
                            }
                        }
                    },
                )
                if (hasAvatarImage) {
                    Spacer(Modifier.width(8.dp))
                    WwSecondaryButton(
                        text = "移除",
                        onClick = {
                            avatarScope.launch {
                                withContext(Dispatchers.IO) { defaultAvatarStore.remove(AvatarOwner.Agent, initial.id) }
                                avatarVersion++
                            }
                        },
                    )
                }
            }
            WwTextField(
                description, { description = it },
                label = "简介说明",
                placeholder = "简述该顾问的专业特长与适用场景...",
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("系统提示词 (System Prompt) *", fontSize = WwType.hint, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(4.dp))
                InfoHint("决定回答风格与思考链路")
            }
            WwTextField(
                prompt, { prompt = it },
                placeholder = "你是一名资深的... 在回答时请遵循以下原则...",
                singleLine = false, maxLines = 6, minHeight = 112.dp, monospace = true,
            )
            // 发散度滑杆 + 标签：参考版 `grid-cols-2 gap-3 items-center`。
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("发散度: ${formatTemperature(temperature)}", fontSize = WwType.hint, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    // 参考版 `<input type="range" min=0 max=1.5 step=0.05>`：细轨道 + 单圆点，**无刻度点**。
                    // 走公共的 `WwRangeSlider`（不是 M3 `Slider` —— 后者 `steps != 0` 会画一排刻度圆点）。
                    WwRangeSlider(
                        value = temperature,
                        range = 0f..1.5f,
                        step = 0.05f,
                        onValueChange = { temperature = it },
                    )
                }
                WwTextField(tags, { tags = it }, label = "标签 (逗号分隔)", placeholder = "如：世界观, 剧情, 角色", modifier = Modifier.weight(1f))
            }

            // ── 记忆与权限（参考版 `pt-1 border-t` 的分区块）──
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(TablerIcons.Lock, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(4.dp))
                Text("记忆与文件权限", fontSize = WwType.hint, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                WwSelectField(
                    current = fileAccessLabel(access),
                    options = FileAccess.entries.map(::fileAccessLabel),
                    onSelect = { picked -> FileAccess.entries.firstOrNull { fileAccessLabel(it) == picked }?.let { access = it } },
                    modifier = Modifier.width(190.dp),
                )
            }
            WwTextField(
                memory, { memory = it },
                placeholder = "该 Agent 的长期记忆（会作为系统提示词的一部分发送）：用户偏好、已确认的世界观事实、角色关系进展…",
                singleLine = false, maxLines = 5, minHeight = 88.dp,
            )
            AgentBindingChips(
                title = "绑定 MCP（创建窗口时默认启用）",
                entries = mcpServers.map { server ->
                    // 参考版 `modelReady`：已连接且发现到工具才标「可用」（`AgentModal.tsx:374-375`）。
                    val ready = server.status == McpServerStatus.CONNECTED && server.tools.isNotEmpty()
                    server.id to (server.name + if (ready) " · 可用" else " · 未就绪")
                },
                selected = mcpIds,
                emptyText = "暂无可绑定的 MCP 服务",
                onToggle = { mcpIds = toggleAgentId(mcpIds, it) },
            )
            AgentBindingChips(
                title = "绑定 Skill（创建窗口时默认启用）",
                entries = skills.map { it.id to it.name },
                selected = skillIds,
                emptyText = "暂无可绑定的 Skill",
                onToggle = { skillIds = toggleAgentId(skillIds, it) },
            )
        }
    }
}

/** 可绑定项的多选 chip 组（参考版 `AgentModal.tsx:364-432`）：选中 = 主色浅底 + 主色描边 + 主色文字。 */
@Composable
private fun AgentBindingChips(
    title: String,
    entries: List<Pair<String, String>>,
    selected: List<String>,
    emptyText: String,
    onToggle: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(WwSize.cardListGap)) {
        Text(title, fontSize = WwType.hint, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (entries.isEmpty()) {
            Text(emptyText, fontSize = WwType.hint, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                entries.forEach { (id, label) ->
                    val on = id in selected
                    WwTag(
                        text = label,
                        shape = RoundedCornerShape(10.dp),
                        background = if (on) WwSurfaces.accentSoft else WwSurfaces.inset,
                        contentColor = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        border = BorderStroke(1.dp, if (on) MaterialTheme.colorScheme.primary else WwSurfaces.cardBorderSubtle),
                        onClick = { onToggle(id) },
                    )
                }
            }
        }
    }
}

private fun toggleAgentId(ids: List<String>, id: String): List<String> = if (id in ids) ids - id else ids + id

/** 发散度文案（参考版直接把 number 打进模板串，渲染成 `0.7` / `1.05`）。 */
private fun formatTemperature(value: Float): String {
    val scaled = kotlin.math.round(value * 100f).toInt()
    return "${scaled / 100}.${(scaled % 100).toString().padStart(2, '0')}"
}

/** 参考版 `<select>` 的三个选项文案（`AgentModal.tsx:346-348`）。 */
private fun fileAccessLabel(access: com.watchworld.model.FileAccess): String = when (access) {
    com.watchworld.model.FileAccess.NONE -> "不可读写文件"
    com.watchworld.model.FileAccess.READ -> "只读文件"
    com.watchworld.model.FileAccess.READWRITE -> "可读写（可主动写入记忆）"
}



/**
 * 正则规则面板（对标 Web 版 `RegexModal.tsx`，675 行）——**一个组件、两种形态**：
 *
 * - **窗口形态**：传入 [sessionRuleIds] / [onToggleForSession] / [onSetSessionRules]，
 *   行首开关与分组标题上的开关都表示「本窗口是否生效」（参考版 L27-34、L70-73、L482-501）。
 * - **库形态**：三个 session 参数都不传，开关即全局 `enabled`。
 *
 * 两种形态共有：分组折叠 + 整组开关 + 逐条开关 + 作用域徽标 + 「pattern → replacement」预览条
 * + 新建/编辑/删除 + 表单内的实时沙箱测试（走 [com.watchworld.domain.testRegexRule]，会**暴露**语法错误）。
 *
 * ⚠️ 参考版的窗口形态**不是**精简版：`RegexModal` 在窗口入口同样传了
 *    `onSaveRule`/`onDeleteRule`，新建/编辑/删除两种形态都能用（`App.tsx:2356-2369`）。
 *    原生旧版的 `WindowRegexPanel` 把窗口形态做成了「只能拨开关」，已按参考版合并进本面板并删除。
 */
@Composable
fun RegexPanel(
    rules: List<RegexRule>,
    onSave: (RegexRule) -> Unit,
    onDelete: (String) -> Unit,
    onToggle: (String) -> Unit,
    /** 窗口形态：本窗口生效的规则 id（`null` = 库形态）。 */
    sessionRuleIds: List<String>? = null,
    /** 窗口形态：单条规则加入 / 移出本窗口。 */
    onToggleForSession: ((String) -> Unit)? = null,
    /** 窗口形态：整组加入 / 移出、全选 / 全不选。 */
    onSetSessionRules: ((List<String>) -> Unit)? = null,
) {
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<RegexRule?>(null) }
    var confirmingDelete by remember { mutableStateOf<RegexRule?>(null) }
    var collapsedGroups by remember { mutableStateOf<Set<String>>(emptySet()) }

    // 参考版 L71-73：只有窗口入口才提供「本窗口生效」这套交互。
    val sessionIds = sessionRuleIds.orEmpty()
    val windowScope = sessionRuleIds != null && onToggleForSession != null
    /** 一行开关代表什么：窗口形态看「在不在本窗口」，库形态看全局 `enabled`。 */
    fun isOn(rule: RegexRule): Boolean = if (windowScope) rule.id in sessionIds else rule.enabled

    // ── 表单状态 ──
    var name by remember { mutableStateOf("") }
    var pattern by remember { mutableStateOf("") }
    var flags by remember { mutableStateOf("") }
    var replacement by remember { mutableStateOf("") }
    var scope by remember { mutableStateOf(RegexScope.OUTPUT) }
    var description by remember { mutableStateOf("") }
    var group by remember { mutableStateOf("") }
    var scopeMenuOpen by remember { mutableStateOf(false) }

    // ── 沙箱测试状态 ──
    var testText by remember { mutableStateOf(DEFAULT_REGEX_TEST_TEXT) }
    var testResult by remember { mutableStateOf<RegexTestResult?>(null) }

    fun resetForm() {
        name = ""; pattern = ""; flags = "g"; replacement = ""
        scope = RegexScope.OUTPUT; description = ""; group = ""
        testResult = null
        // 样例文本**不重置**：切来切去时保留上一次粘贴的样本（参考版 L100-111 同样如此）。
    }

    fun startCreate(defaultGroup: String? = null) {
        resetForm()
        group = defaultGroup.orEmpty()
        creating = true
        editing = null
    }

    fun startEdit(r: RegexRule) {
        name = r.name; pattern = r.pattern; flags = r.flags; replacement = r.replacement
        scope = r.scope; description = r.description.orEmpty(); group = r.group.orEmpty()
        testResult = null
        creating = false
        editing = r
    }

    fun closeForm() {
        creating = false
        editing = null
        resetForm()
    }

    fun saveForm() {
        if (name.isBlank() || pattern.isBlank()) return
        val base = editing ?: RegexRule(id = "regex-${currentTimeMillis()}")
        onSave(
            base.copy(
                name = name.trim(),
                pattern = pattern,
                flags = flags.ifBlank { "g" },
                replacement = replacement,
                scope = scope,
                description = description.trim().ifBlank { null },
                group = group.trim().ifBlank { null },
                // 编辑保留原开关状态；新建默认关闭（与参考版 `editingRule ? editingRule.enabled : true` 对齐——
                // 参考版新建即启用，这里跟随参考版）。
                enabled = editing?.enabled ?: true,
            ),
        )
        closeForm()
    }

    // 分组：`group` 为空 / 纯空白 → 「未分组」（参考版 `UNGROUPED = '未分组'`）。
    val grouped = remember(rules) {
        rules.groupBy { it.group?.trim().orEmpty().ifEmpty { UNGROUPED_REGEX_GROUP } }
            .toSortedMap(compareBy { if (it == UNGROUPED_REGEX_GROUP) "￿" else it })
    }
    val knownGroups = remember(rules) {
        rules.mapNotNull { it.group?.trim() }.filter { it.isNotEmpty() }.distinct().sorted()
    }
    val allCollapsed = grouped.isNotEmpty() && grouped.keys.all { it in collapsedGroups }

    Column(Modifier.fillMaxWidth()) {
        // ── 工具条（参考版 L477-519）──
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (windowScope) {
                // 窗口形态不再写「开头的开关即本窗口是否生效」那句（用户 2026-10-12：不要这句话）；
                // 留个占位把右侧按钮推到右边。
                Spacer(Modifier.weight(1f))
            } else {
                Text(
                    "共 ${rules.size} 条规则 · 启用 ${rules.count { it.enabled }}",
                    fontSize = WwType.hint,
                    lineHeight = WwSize.compactTextLineHeight,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
            if (windowScope) {
                // 参考版 L482-501：窗口形态独有的两个入口。
                WwSecondaryButton(
                    text = "本窗口全选",
                    onClick = { onSetSessionRules?.invoke(rules.filter { it.enabled }.map { it.id }) },
                )
                WwSecondaryButton(text = "全不选", onClick = { onSetSessionRules?.invoke(emptyList()) })
            }
            WwSecondaryButton(
                text = if (allCollapsed) "全部展开" else "全部折叠",
                leadingIcon = if (allCollapsed) TablerIcons.ChevronRight else TablerIcons.ChevronDown,
                onClick = { collapsedGroups = if (allCollapsed) emptySet() else grouped.keys.toSet() },
            )
            WwSecondaryButton(
                text = "新建规则",
                leadingIcon = TablerIcons.Plus,
                onClick = { startCreate() },
            )
        }
        Spacer(Modifier.height(WwSize.cardListGap))

        if (rules.isEmpty()) {
            Text(
                "暂无正则规则，点击上方「新建规则」添加",
                fontSize = WwType.hint,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(vertical = WwSize.emptyStateVerticalPadding).wrapContentWidth(Alignment.CenterHorizontally),
            )
        }

        grouped.forEach { (groupName, groupRules) ->
            val collapsed = groupName in collapsedGroups
            val groupOn = groupRules.all { isOn(it) }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = WwSurfaces.card,
                border = BorderStroke(1.dp, WwSurfaces.cardBorder),
                modifier = Modifier.fillMaxWidth().padding(bottom = WwSize.cardListGap),
            ) {
                Column(Modifier.fillMaxWidth().padding(vertical = WwPad.card)) {
                    // ── 组标题行：折叠 + 组名 + 条数 + 整组开关 + 组内新建 ──
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clickable {
                                collapsedGroups = if (collapsed) collapsedGroups - groupName
                                else collapsedGroups + groupName
                            }
                            .padding(horizontal = 10.dp, vertical = WwSize.listRowVerticalPadding),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (collapsed) TablerIcons.ChevronRight else TablerIcons.ChevronDown,
                            contentDescription = if (collapsed) "展开" else "折叠",
                            modifier = Modifier.size(13.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            groupName,
                            fontSize = WwType.body,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            groupRules.size.toString(),
                            fontSize = WwType.hint,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(6.dp))
                        // 整组开关（参考版 L554-575）：窗口形态作用到本窗口，库形态逐条对齐全局开关。
                        RegexToggleButton(
                            on = groupOn,
                            iconSize = 15.dp,
                            iconPadding = 2.dp,
                            description = when {
                                windowScope && groupOn -> "整组已在本窗口生效（点击整组移出）"
                                windowScope -> "整组在本窗口未生效（点击整组加入）"
                                groupOn -> "整组已启用（点击整组禁用）"
                                else -> "整组已禁用（点击整组启用）"
                            },
                        ) {
                            if (windowScope) {
                                val ids = groupRules.map { it.id }
                                val next = if (groupOn) sessionIds.filter { it !in ids }
                                else (sessionIds + ids).distinct()
                                onSetSessionRules?.invoke(next)
                            } else {
                                groupRules.forEach { rule -> if (rule.enabled != !groupOn) onToggle(rule.id) }
                            }
                        }
                        IconButton(
                            onClick = { startCreate(groupName) },
                            modifier = Modifier.size(26.dp),
                        ) {
                            Icon(
                                TablerIcons.Plus,
                                contentDescription = "在「$groupName」中新建规则",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    if (!collapsed) {
                        groupRules.forEach { rule ->
                            RegexRuleRow(
                                rule = rule,
                                checked = isOn(rule),
                                windowScope = windowScope,
                                onToggle = {
                                    // windowScope 已蕴含 onToggleForSession != null（布尔 val 的类型收窄）。
                                    if (windowScope) onToggleForSession.invoke(rule.id)
                                    else onToggle(rule.id)
                                },
                                onEdit = { startEdit(rule) },
                                onDelete = { confirmingDelete = rule },
                            )
                        }
                    }
                }
            }
        }

        // ── 底部说明（参考版 L668-671）──
        Spacer(Modifier.height(WwSize.cardListGap))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("正则规则说明", fontSize = WwType.hint, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(6.dp))
            InfoHint("支持全部标准 JavaScript 正则语法与分组替换；替换串里可用 \$1、\$2 引用分组捕获。")
        }
    }

    // ── 表单层：叠在列表之上 ──
    if (creating || editing != null) {
        FeatureDialog(
            title = if (creating) "添加正则规则" else "编辑规则：${editing?.name.orEmpty()}",
            onDismiss = { closeForm() },
            maxWidth = 576.dp,
            heightFraction = 0.9f,
            footer = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.weight(1f))
                    WwSecondaryButton("取消", onClick = { closeForm() })
                    WwPrimaryButton(
                        text = "保存规则",
                        onClick = { saveForm() },
                        enabled = name.isNotBlank() && pattern.isNotBlank(),
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
                        label = "规则名称 *",
                        modifier = Modifier.weight(2f),
                    )
                    WwSelectField(
                        current = regexScopeLabel(scope),
                        options = RegexScope.entries.map(::regexScopeLabel),
                        onSelect = { picked ->
                            RegexScope.entries.firstOrNull { regexScopeLabel(it) == picked }?.let { scope = it }
                        },
                        label = "作用范围",
                        modifier = Modifier.weight(1f),
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(WwSize.cardListGap)) {
                    WwTextField(
                        value = group,
                        onValueChange = { group = it },
                        label = "分组",
                        hint = if (knownGroups.isEmpty()) {
                            "可留空；留空即归入「$UNGROUPED_REGEX_GROUP」；分组用于把规则归档、整组开关。"
                        } else {
                            "已有分组：" + knownGroups.joinToString("、")
                        },
                    )
                    if (knownGroups.isNotEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        ) {
                            knownGroups.forEach { known ->
                                WwTag(
                                    text = known,
                                    shape = RoundedCornerShape(10.dp),
                                    contentColor = MaterialTheme.colorScheme.onSurface,
                                    border = BorderStroke(1.dp, WwSurfaces.cardBorderSubtle),
                                    onClick = { group = known },
                                )
                            }
                        }
                    }
                }

                WwTextField(
                    value = pattern,
                    onValueChange = { pattern = it },
                    label = "Pattern *",
                    placeholder = "例如：\\d{11}",
                    fontSize = WwType.hint,
                    monospace = true,
                )
                WwTextField(
                    value = flags,
                    onValueChange = { flags = it },
                    label = "Flags（g / i / m / s）",
                    placeholder = "g",
                    fontSize = WwType.hint,
                    monospace = true,
                )
                WwTextField(
                    value = replacement,
                    onValueChange = { replacement = it },
                    label = "替换为",
                    hint = "留空即删除匹配内容",
                    placeholder = "支持 \$1 / \$2 分组引用",
                    fontSize = WwType.hint,
                    monospace = true,
                )
                WwTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = "规则说明备注",
                    singleLine = false,
                    maxLines = 3,
                    minHeight = 56.dp,
                )

                // ── 实时沙箱测试 ──
                Column(verticalArrangement = Arrangement.spacedBy(WwSize.cardListGap)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "实时沙箱测试效果",
                            fontSize = WwType.hint,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.weight(1f))
                        WwSecondaryButton(
                            text = "运行测试",
                            onClick = { testResult = testRegexRule(testText, pattern, flags, replacement) },
                        )
                    }
                    WwTextField(
                        value = testText,
                        onValueChange = { testText = it },
                        label = "测试用例文本",
                        singleLine = false,
                        maxLines = 4,
                        minHeight = 64.dp,
                        fontSize = WwType.hint,
                        monospace = true,
                    )
                    testResult?.let { result ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = WwSurfaces.inset,
                            border = BorderStroke(
                                1.dp,
                                if (result.success) WwSurfaces.cardBorderSubtle else MaterialTheme.colorScheme.error,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = WwPad.card)) {
                                if (result.success) {
                                    Text(
                                        "匹配命中: ${result.matchCount} 处",
                                        fontSize = WwType.hint,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Spacer(Modifier.height(WwSize.cardListGap))
                                    Text(
                                        text = if (result.result.isEmpty()) "（替换后为空）" else result.result,
                                        fontSize = WwType.hint,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                } else {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            TablerIcons.AlertCircle,
                                            null,
                                            Modifier.size(13.dp),
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            result.error.orEmpty(),
                                            fontSize = WwType.hint,
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ── 删除确认 ──
    confirmingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { confirmingDelete = null },
            title = { Text("删除正则规则") },
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

/**
 * 参考版的行首 / 整组方形开关（`RegexModal.tsx:220-250`、`:554-575`）：
 * 选中 = 主色浅底 + 主色描边 + `ToggleRight`；未选 = 凹槽底 + 默认描边 + `ToggleLeft`。
 *
 * ⚠️ [dimmed] 必须排在 [onClick] **前面**：`onClick` 是最后一位，调用点才能用尾随 lambda。
 *
 * @param dimmed 参考版 L234：窗口形态下全局已禁用的规则，开关按 50% 透明画出来
 *   （仍可点击 —— 加入本窗口与全局启用是两件事）。
 */
@Composable
private fun RegexToggleButton(
    on: Boolean,
    iconSize: Dp,
    iconPadding: Dp,
    description: String,
    dimmed: Boolean = false,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .alpha(if (dimmed) 0.5f else 1f)
            .background(if (on) WwSurfaces.accentSoft else WwSurfaces.inset, shape)
            .border(
                width = 1.dp,
                color = if (on) MaterialTheme.colorScheme.primary else WwSurfaces.cardBorderSubtle,
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(iconPadding),
    ) {
        Icon(
            imageVector = if (on) TablerIcons.ToggleRight else TablerIcons.ToggleLeft,
            contentDescription = description,
            modifier = Modifier.size(iconSize),
            tint = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 单条规则：开关 + 名称 + 作用域徽标 + 备注 + 「pattern → replacement」预览条 + 编辑删除。 */
@Composable
private fun RegexRuleRow(
    rule: RegexRule,
    checked: Boolean,
    windowScope: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = WwSize.compactFrameVerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RegexToggleButton(
            on = checked,
            iconSize = 18.dp,
            iconPadding = 4.dp,
            // 参考版 L222/L234：窗口形态下，全局已禁用的规则开关变淡（但仍可加入本窗口）。
            dimmed = windowScope && !rule.enabled,
            description = when {
                windowScope && checked -> "本窗口已生效（点击移出本窗口）"
                windowScope -> "本窗口未生效（点击加入本窗口）"
                checked -> "已启用（点击禁用）"
                else -> "已禁用（点击启用）"
            },
            onClick = onToggle,
        )
        Column(
            modifier = Modifier.weight(1f).clickable(onClick = onEdit).padding(vertical = WwSize.boxVerticalPadding),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    rule.name.ifBlank { rule.pattern.take(20) },
                    fontSize = WwType.body,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(6.dp))
                WwTag(text = regexScopeLabel(rule.scope))
            }
            rule.description?.takeIf { it.isNotBlank() }?.let { desc ->
                Text(
                    desc,
                    fontSize = WwType.hint,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // `pattern → replacement` 预览条（参考版 L650-658）。
            Text(
                text = "/${rule.pattern}/${rule.flags} ➔ " +
                    if (rule.replacement.isEmpty()) "(清除)" else "\"${rule.replacement}\"",
                fontSize = WwType.hint,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onEdit, modifier = Modifier.size(26.dp)) {
            Icon(
                TablerIcons.Edit,
                contentDescription = "编辑",
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(26.dp)) {
            Icon(
                TablerIcons.Trash,
                contentDescription = "删除",
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** 参考版 `UNGROUPED = '未分组'`。 */
private const val UNGROUPED_REGEX_GROUP = "未分组"

/** 参考版新建规则时的默认测试样例（`RegexModal.tsx:65`）。 */
private const val DEFAULT_REGEX_TEST_TEXT =
    "好的！这是一段用于测试正则表达式清洗的AI回复。电话是 13812345678。"

private fun regexScopeLabel(scope: RegexScope): String = when (scope) {
    RegexScope.OUTPUT -> "输出"
    RegexScope.INPUT -> "输入"
    RegexScope.BOTH -> "双向"
}

/**
 * 知识库面板（对标 Web 版 `KnowledgeBaseModal.tsx`，397 行）。
 *
 * 容器是 `h-[92vh]` 固定高（调用点传 `fixedHeight = true`）：顶栏与工具条定住、列表自己滚。
 * 宽屏下参考版是**两列**网格（`md:grid-cols-2`），这里按两列排。
 *
 * ⚠️ 整张卡可点 = 「连接 / 断开本窗口」（参考版 L317），编辑与删除按钮在卡片内部，
 *    Compose 里内层 `clickable` 会吃掉事件，不需要 DOM 那套 `stopPropagation`。
 * ⚠️ 「连接本窗口」只写 `Session.connectedKnowledgeIds`，**不碰**资料的全局 `enabled` ——
 *    真正参与注入要两者同时成立（`domain/selectConnectedKnowledge`）。
 * ⚠️ 开关**只在窗口形态显示**（[sessionIds] 传了才算窗口形态）：库形态（侧边栏）不显示，
 *    卡片不带选中描边、也无「已连接」徽标 —— 与 [RegexPanel] / [com.watchworld.ui.world.WorldPanel]
 *    的「一个组件、两种形态」同一套定案（用户 2026-10-10：窗口状态不影响库，除非用了编辑）。
 *    开关样式沿用知识库自己的 `Square / SquareCheck`，**不**换成世界观的 `ToggleLeft/Right`。
 */
@Composable
fun KnowledgePanel(
    items: List<KnowledgeItem>,
    onSave: (KnowledgeItem) -> Unit,
    onDelete: (String) -> Unit,
    /** 窗口形态：本窗口连接的 id（`null` = 库形态，不显示连接开关）。 */
    sessionIds: List<String>? = null,
    /** 窗口形态：单篇资料连接 / 断开本窗口。 */
    onToggleForSession: ((String) -> Unit)? = null,
) {
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<KnowledgeItem?>(null) }
    var confirmingDelete by remember { mutableStateOf<KnowledgeItem?>(null) }
    var importNotice by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }

    // 库形态下没有「连接」概念：connectedIds 恒空，卡片不带开关与选中态。
    val connectedIds = sessionIds.orEmpty()
    val windowScope = sessionIds != null && onToggleForSession != null

    // ── 表单状态 ──
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var tagsStr by remember { mutableStateOf("") }

    fun resetForm() {
        title = ""; content = ""; tagsStr = "文档"
    }

    fun startCreate() {
        resetForm()
        creating = true
        editing = null
    }

    fun startEdit(k: KnowledgeItem) {
        title = k.title; content = k.content; tagsStr = k.tags.joinToString(", ")
        creating = false
        editing = k
    }

    fun closeForm() {
        creating = false
        editing = null
        resetForm()
    }

    fun saveForm() {
        if (title.isBlank() || content.isBlank()) return
        val base = editing ?: KnowledgeItem(id = "kb-${currentTimeMillis()}", title = "")
        onSave(
            base.copy(
                title = title.trim(),
                content = content.trim(),
                tags = parseTagList(tagsStr),
                enabled = true,
                updatedAt = currentTimeMillis(),
            ),
        )
        closeForm()
    }

    val needle = query.trim().lowercase()
    val filtered = remember(items, needle) {
        if (needle.isEmpty()) {
            items
        } else {
            items.filter { item ->
                item.title.lowercase().contains(needle) ||
                    item.content.lowercase().contains(needle) ||
                    item.tags.any { it.lowercase().contains(needle) }
            }
        }
    }

    Column(Modifier.fillMaxWidth()) {
        // ── 工具条：搜索 + 导入 + 新建 ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = "搜索标题、正文或标签...",
                modifier = Modifier.weight(1f),
            )
            WwSecondaryButton(
                text = "导入文档",
                onClick = {
                    importNotice = null
                    val picked = pickTextFiles()
                    if (picked.isEmpty()) return@WwSecondaryButton
                val now = currentTimeMillis()
                var added = 0
                picked.forEachIndexed { index, file ->
                    if (file.content.isBlank()) return@forEachIndexed
                    val ext = file.name.substringAfterLast('.', "")
                    onSave(
                        KnowledgeItem(
                            id = "kb-file-$now-$index",
                            title = file.name.substringBeforeLast('.', file.name).ifBlank { file.name },
                            content = file.content,
                            tags = listOf("文档", ext).filter { it.isNotBlank() },
                            enabled = true,
                            updatedAt = now + index,
                        ),
                    )
                    added++
                }
                importNotice = if (added > 0) {
                    "已导入 $added 篇资料。"
                } else {
                    "所选文件都是空的，没有导入。"
                }
            })
            WwPrimaryButton(text = "新建文档", onClick = { startCreate() })
        }

        importNotice?.let {
            Spacer(Modifier.height(WwSize.cardListGap))
            Text(it, fontSize = WwType.hint, lineHeight = WwSize.compactTextLineHeight, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(WwSize.cardListGap))

        if (filtered.isEmpty()) {
            Text(
                text = if (items.isEmpty()) {
                    "知识库暂无内容，点击右上角「新建文档」导入你的第一篇资料"
                } else {
                    "没有匹配的资料"
                },
                fontSize = WwType.hint,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(vertical = WwSize.emptyStateVerticalPadding).wrapContentWidth(Alignment.CenterHorizontally),
            )
        }

        // ── 两列网格（参考版 `md:grid-cols-2 gap-2.5`）──
        filtered.chunked(2).forEach { pair ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                pair.forEach { item ->
                    KnowledgeCard(
                        item = item,
                        connected = item.id in connectedIds,
                        // 只有窗口形态有「连接本窗口」；库形态卡片不带开关也不可点。
                        showConnect = windowScope,
                        onToggleConnect = { onToggleForSession?.invoke(item.id) },
                        onEdit = { startEdit(item) },
                        onDelete = { confirmingDelete = item },
                        modifier = Modifier.weight(1f),
                    )
                }
                // 落单的那张卡不要拉满整行。
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }

    // ── 表单层：叠在列表之上 ──
    if (creating || editing != null) {
        FeatureDialog(
            title = if (creating) "新建知识文档" else "编辑：${editing?.title.orEmpty()}",
            onDismiss = { closeForm() },
            maxWidth = 672.dp,
            heightFraction = 0.9f,
            footer = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.weight(1f))
                    WwSecondaryButton("取消", onClick = { closeForm() })
                    WwPrimaryButton(
                        text = "保存文档",
                        onClick = { saveForm() },
                        enabled = title.isNotBlank() && content.isNotBlank(),
                    )
                }
            },
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(WwSize.cardListGap),
            ) {
                WwTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = "标题 *",
                    placeholder = "例如：世界观总纲、角色心理档案",
                )
                WwTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = "内容 *",
                    placeholder = "在此输入 Markdown 格式的资料正文...",
                    singleLine = false,
                    maxLines = 14,
                    minHeight = 220.dp,
                    fontSize = WwType.hint,
                    monospace = true,
                )
                WwTextField(
                    value = tagsStr,
                    onValueChange = { tagsStr = it },
                    label = "标签",
                    placeholder = "逗号分隔，例如：文档, 世界观",
                    hint = "逗号、中文逗号或空格都算分隔符；留空则记为「文档」。",
                )
            }
        }
    }

    // ── 删除确认 ──
    confirmingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { confirmingDelete = null },
            title = { Text("删除知识文档") },
            text = { Text("确认从知识库删除「${target.title.ifBlank { target.id }}」？该操作不可撤销。") },
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

/**
 * 知识库卡片。窗口形态：连接勾选 + 标题/徽标 + 标签与字数 + 编辑删除 + 两行内容预览。
 * 库形态：不显示连接勾选、去掉「选中」外观与整卡点击（`showConnect = false`）。
 */
@Composable
private fun KnowledgeCard(
    item: KnowledgeItem,
    connected: Boolean,
    showConnect: Boolean,
    onToggleConnect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = if (showConnect) modifier.clickable(onClick = onToggleConnect) else modifier,
        shape = RoundedCornerShape(10.dp),
        color = if (showConnect && connected) WwSurfaces.cardSelected else WwSurfaces.card,
        border = BorderStroke(
            width = if (showConnect && connected) 2.dp else 1.dp,
            color = if (showConnect && connected) WwSurfaces.cardBorder else WwSurfaces.cardBorderSubtle,
        ),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = WwPad.card)) {
            Row(verticalAlignment = Alignment.Top) {
                if (showConnect) {
                    Icon(
                        imageVector = if (connected) TablerIcons.SquareCheck else TablerIcons.Square,
                        contentDescription = if (connected) "断开本窗口" else "连接本窗口",
                        modifier = Modifier.size(18.dp),
                        tint = if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = item.title.ifBlank { item.id },
                            fontSize = WwType.body,
                            color = if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (showConnect && connected) {
                            Spacer(Modifier.width(6.dp))
                            WwTag(
                                text = "已连接本窗口",
                                background = WwSurfaces.accentSoft,
                                contentColor = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        item.tags.forEach { tag ->
                            WwTag(text = "#$tag")
                        }
                        Text(
                            "· ${item.content.length} 字",
                            fontSize = WwType.hint,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.width(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(26.dp)) {
                        Icon(
                            TablerIcons.Edit,
                            contentDescription = "编辑",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(26.dp)) {
                        Icon(
                            TablerIcons.Trash,
                            contentDescription = "删除",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            Spacer(Modifier.height(WwSize.cardListGap))
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = WwSurfaces.inset,
                border = BorderStroke(1.dp, WwSurfaces.cardBorderSubtle),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = item.content,
                    fontSize = WwType.hint,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                )
            }
        }
    }
}

/** 标签串 → 列表：逗号（中英）、分号与空格都算分隔符；空则回退 `['文档']`。 */
private fun parseTagList(raw: String): List<String> {
    val parsed = raw.split(Regex("[,，;；\\s]+")).map { it.trim() }.filter { it.isNotEmpty() }
    return parsed.ifEmpty { listOf("文档") }
}

/**
 * MCP 服务管理面板（对标 Web 版 `McpModal.tsx`，507 行）。
 *
 * ### 结构（对齐参考版的两层）
 * - **列表层**：本 Panel 的正文。工具条是「已注册 N 项 + 导入 + 添加」，每张卡是
 *   名称 + 协议徽标 + URL + 「测试连接 / 编辑 / 删除」，卡内还有状态行与「已发现工具」列表。
 * - **表单层**：新建/编辑叠在列表之上（参考版 `z-[80]`），底部固定操作区。
 *
 * ⚠️ 测试结果由调用点落库（`status` / `tools` / `lastLatencyMs` / `lastError`），
 *    Panel 只负责转圈与展示 —— 所以列表读的是持久化字段，不是本地临时结果。
 */
@Composable
fun McpPanel(
    items: List<McpServerConfig>,
    onSave: (McpServerConfig) -> Unit,
    onDelete: (String) -> Unit,
    onTestServer: suspend (McpServerConfig) -> McpConnectionTestResult,
) {
    val scope = rememberCoroutineScope()

    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<McpServerConfig?>(null) }
    var testingId by remember { mutableStateOf<String?>(null) }
    var confirmingDelete by remember { mutableStateOf<McpServerConfig?>(null) }
    var importError by remember { mutableStateOf<String?>(null) }
    var importNotice by remember { mutableStateOf<String?>(null) }

    // ── 表单状态 ──
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(McpTransportType.STREAMABLE_HTTP) }
    var url by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var headers by remember { mutableStateOf<List<McpHeaderEntry>>(emptyList()) }

    fun resetForm() {
        name = ""; type = McpTransportType.STREAMABLE_HTTP; url = ""; token = ""
        description = ""; headers = emptyList()
    }

    fun startCreate() {
        resetForm()
        creating = true
        editing = null
    }

    fun startEdit(m: McpServerConfig) {
        name = m.name; type = m.type; url = m.url.orEmpty(); token = m.authToken.orEmpty()
        description = m.description.orEmpty()
        headers = m.headers.orEmpty().map { (k, v) -> McpHeaderEntry(k, v) }
        creating = false
        editing = m
    }

    fun closeForm() {
        creating = false
        editing = null
        resetForm()
    }

    fun saveForm() {
        if (name.isBlank() || url.isBlank()) return
        val base = editing ?: McpServerConfig(id = "mcp-${currentTimeMillis()}", name = "")
        onSave(
            base.copy(
                name = name.trim(),
                type = type,
                url = url.trim(),
                authToken = token.trim().ifBlank { null },
                description = description.trim().ifBlank { null },
                headers = headers.toHeaderMap(),
            ),
        )
        closeForm()
    }

    // ── 列表层 ──
    // 卡片间距由容器负责（cardGap 6dp），卡片自身不加垂直外边距。
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(WwSize.cardGap),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                "已注册 MCP 服务器（${items.size} 项，绑定请到 Agent 设置）",
                fontSize = WwType.hint,
                lineHeight = WwSize.compactTextLineHeight,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            WwSecondaryButton(
                text = "导入",
                leadingIcon = TablerIcons.FileImport,
                onClick = {
                    importError = null
                    importNotice = null
                    // 系统文件框是模态的，必须留在 UI 线程上调（见 platform/FilePicker.kt）。
                    val picked = pickTextFile()
                    if (picked != null) {
                        runCatching { parseMcpText(picked.content, picked.name) }
                            .onSuccess { imported ->
                                onSave(imported)
                                importNotice = "已导入 MCP 服务「${imported.name}」，默认保持停用。"
                            }
                            .onFailure { importError = it.message ?: "导入失败：文件格式不正确。" }
                    }
                },
            )
            Spacer(Modifier.width(6.dp))
            WwTag(
                text = "添加 MCP 服务",
                shape = RoundedCornerShape(10.dp),
                background = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                leadingIcon = TablerIcons.Plus,
                onClick = { startCreate() },
            )
        }

        importNotice?.let {
            Spacer(Modifier.height(WwSize.cardListGap))
            Text(it, fontSize = WwType.hint, lineHeight = WwSize.compactTextLineHeight, color = MaterialTheme.colorScheme.primary)
        }
        importError?.let {
            Spacer(Modifier.height(WwSize.cardListGap))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(TablerIcons.AlertCircle, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(6.dp))
                Text(it, fontSize = WwType.hint, lineHeight = WwSize.compactTextLineHeight, color = MaterialTheme.colorScheme.error)
            }
        }
        items.forEach { server ->
            val isTesting = testingId == server.id
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
                                Text(
                                    server.name.ifBlank { server.id },
                                    fontSize = WwType.body,
                                    lineHeight = WwSize.compactTextLineHeight,
                                )
                                Spacer(Modifier.width(6.dp))
                                WwTag(
                                    text = server.type.name,
                                    shape = RoundedCornerShape(10.dp),
                                    background = WwSurfaces.inset,
                                    border = BorderStroke(1.dp, WwSurfaces.cardBorderSubtle),
                                )
                            }
                            Text(
                                server.url.orEmpty(),
                                fontSize = WwType.hint,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                                                WwSecondaryButton(
                            text = if (isTesting) "测试中…" else "测试连接",
                            onClick = {
                                testingId = server.id
                                scope.launch {
                                    try {
                                        onTestServer(server)
                                    } finally {
                                        testingId = null
                                    }
                                }
                            },
                            enabled = !isTesting,
                            leadingIcon = TablerIcons.Activity,
                        )
                        Spacer(Modifier.width(4.dp))
                        WwTag(
                            text = "编辑",
                            onClick = { startEdit(server) },
                            background = WwSurfaces.inset,
                        )
                        WwTag(
                            text = "删除",
                            onClick = { confirmingDelete = server },
                            background = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }

                    // ── 状态行（参考版 L457-478）──
                    Spacer(Modifier.height(WwSize.cardListGap))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val statusColor = when {
                            isTesting -> MaterialTheme.colorScheme.primary
                            server.status == McpServerStatus.CONNECTED -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Text(
                            text = when {
                                isTesting -> "正在连接并发现工具…"
                                server.status == McpServerStatus.CONNECTED ->
                                    "最近测试成功" + (server.lastLatencyMs?.let { " · $it ms" } ?: "")
                                else -> "尚未验证连接"
                            },
                            fontSize = WwType.hint,
                            lineHeight = WwSize.compactTextLineHeight,
                            color = statusColor,
                        )
                        server.lastError?.let { error ->
                            Spacer(Modifier.width(8.dp))
                            Text(
                                error,
                                fontSize = WwType.hint,
                                lineHeight = WwSize.compactTextLineHeight,
                                color = MaterialTheme.colorScheme.error,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }

                    // ── 已发现工具（参考版 L480-497）──
                    if (server.tools.isNotEmpty()) {
                        Spacer(Modifier.height(WwSize.cardListGap))
                        HorizontalDivider(color = WwSurfaces.cardBorderSubtle)
                        Spacer(Modifier.height(WwSize.cardListGap))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                TablerIcons.Tool,
                                null,
                                Modifier.size(11.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "已发现工具:",
                                fontSize = WwType.hint,
                                lineHeight = WwSize.compactTextLineHeight,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(WwSize.cardListGap))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        ) {
                            server.tools.forEach { tool ->
                                WwTag(
                                    text = tool.name.ifBlank { "未命名工具" },
                                    shape = RoundedCornerShape(10.dp),
                                    background = WwSurfaces.inset,
                                    border = BorderStroke(1.dp, WwSurfaces.cardBorderSubtle),
                                    contentColor = MaterialTheme.colorScheme.onSurface,
                                    monospace = true,
                                )
                            }
                        }
                    }
                }
            }
        }

        if (items.isEmpty()) {
            Spacer(Modifier.height(WwSize.cardListGap))
            Text(
                "还没有注册 MCP 服务，点击「添加 MCP 服务」开始。",
                fontSize = WwType.hint,
                lineHeight = WwSize.compactTextLineHeight,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    // ── 表单层：叠在列表之上 ──
    if (creating || editing != null) {
        FeatureDialog(
            title = if (creating) "新建 MCP 服务" else "编辑：${editing?.name.orEmpty()}",
            onDismiss = { closeForm() },
            maxWidth = 576.dp,
            heightFraction = 0.9f,
            footer = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.weight(1f))
                    WwSecondaryButton("取消", onClick = { closeForm() })
                    WwPrimaryButton(
                        text = "保存 MCP 服务",
                        onClick = { saveForm() },
                        enabled = name.isNotBlank() && url.isNotBlank(),
                    )
                }
            },
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(WwSize.cardListGap),
            ) {
                // 服务名称（2/3）+ 连接协议（1/3）：参考版 `grid-cols-3 gap-2` + `col-span-2`。
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    WwTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = "服务名称 *",
                        placeholder = "例如：本地文件系统 MCP、GitHub MCP...",
                        modifier = Modifier.weight(2f),
                    )
                    WwSelectField(
                        current = mcpTypeLabel(type),
                        options = McpTransportType.entries.map(::mcpTypeLabel),
                        onSelect = { picked ->
                            McpTransportType.entries.firstOrNull { mcpTypeLabel(it) == picked }?.let { type = it }
                        },
                        label = "连接协议",
                        modifier = Modifier.weight(1f),
                    )
                }
                WwTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = "远程 MCP URL *",
                    placeholder = if (type == McpTransportType.SSE) "https://example.com/sse"
                    else "https://example.com/mcp",
                    hint = "手机和正式部署推荐 HTTPS。localhost 在手机上指向手机自身。",
                    fontSize = WwType.hint,
                    monospace = true,
                )
                WwTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = "Bearer Token（可选）",
                    fontSize = WwType.hint,
                    monospace = true,
                    visualTransformation = PasswordVisualTransformation(),
                )

                // 附加请求头：标题行 + 「新建请求头」，下面是虚线空态或行列表。
                Column(verticalArrangement = Arrangement.spacedBy(WwSize.cardListGap)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "附加请求头",
                            fontSize = WwType.hint,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.weight(1f))
                        WwTag(
                            text = "新建请求头",
                            shape = RoundedCornerShape(10.dp),
                            background = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            leadingIcon = TablerIcons.Plus,
                            onClick = { headers = headers + McpHeaderEntry() },
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
                                "暂无附加请求头，需要时点击“新建请求头”添加",
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
                                    placeholder = "请求头名称，例如 X-Workspace",
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

                WwTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = "服务功能描述",
                    placeholder = "简述该服务为大模型带来的工具扩展能力...",
                )
            }
        }
    }

    // ── 删除确认 ──
    confirmingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { confirmingDelete = null },
            title = { Text("删除 MCP 服务") },
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

/** 表单里的请求头行（参考版用 id 去重，这里按位置编辑即可）。 */
private data class McpHeaderEntry(val name: String = "", val value: String = "")

private fun List<McpHeaderEntry>.toHeaderMap(): Map<String, String>? {
    val map = linkedMapOf<String, String>()
    forEach { entry ->
        val key = entry.name.trim()
        if (key.isNotEmpty()) map[key] = entry.value.trim()
    }
    return map.ifEmpty { null }
}

private fun mcpTypeLabel(type: McpTransportType): String = when (type) {
    McpTransportType.STREAMABLE_HTTP -> "HTTP (Streamable HTTP)"
    McpTransportType.SSE -> "SSE"
}

/**
 * Skill 技能库面板（对标 Web 版 `SkillModal.tsx` 的库形态，656 行）。
 *
 * 库形态只管技能本身（表单六字段 + 本地导入）；「哪个 Agent 用哪些 Skill」在 Agent 设置里。
 *
 * ⚠️ 参考版还有第三层「从 Skill 目录导入」（联网搜索 → 预览 SKILL.md → 确认导入，
 *    `z-[90]` 弹窗）。那一层依赖联网开关，**本轮未做**，需要时按 `SkillModal.tsx:499-653` 补。
 */
@Composable
fun SkillPanel(
    items: List<AgentSkill>,
    onSave: (AgentSkill) -> Unit,
    onDelete: (String) -> Unit,
) {
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<AgentSkill?>(null) }
    var confirmingDelete by remember { mutableStateOf<AgentSkill?>(null) }
    var importNotice by remember { mutableStateOf<String?>(null) }
    var importError by remember { mutableStateOf<String?>(null) }

    // ── 表单状态 ──
    var name by remember { mutableStateOf("") }
    var icon by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var keywordsStr by remember { mutableStateOf("") }
    var toolsNeededStr by remember { mutableStateOf("") }
    var injection by remember { mutableStateOf("") }

    fun resetForm() {
        name = ""
        icon = "⚡"
        description = ""
        keywordsStr = "分析, 规划, 方案"
        toolsNeededStr = ""
        injection = "【已激活专属技能】请按结构化方案输出分步执行指南。"
    }

    fun startCreate() {
        resetForm()
        creating = true
        editing = null
    }

    fun startEdit(s: AgentSkill) {
        name = s.name
        icon = s.icon
        description = s.description
        keywordsStr = s.triggerKeywords.joinToString(", ")
        toolsNeededStr = s.toolsNeeded.orEmpty().joinToString(", ")
        injection = s.systemInstructionInjection
        creating = false
        editing = s
    }

    fun closeForm() {
        creating = false
        editing = null
        resetForm()
    }

    fun saveForm() {
        if (name.isBlank() || injection.isBlank()) return
        val base = editing ?: AgentSkill(id = "skill-${currentTimeMillis()}")
        onSave(
            base.copy(
                name = name.trim(),
                icon = icon.trim().ifBlank { "⚡" },
                description = description.trim(),
                triggerKeywords = parseKeywordList(keywordsStr).ifEmpty { listOf("通用") },
                systemInstructionInjection = injection.trim(),
                // 两个字段同义，保持同步（参考版也是写同一个值）。
                systemInstruction = injection.trim(),
                toolsNeeded = parseKeywordList(toolsNeededStr).ifEmpty { null },
            ),
        )
        closeForm()
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(WwSize.cardGap)) {
        // ── 工具条 ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Skill 配置库（${items.size} 项）",
                    fontSize = WwType.hint,
                    lineHeight = WwSize.compactTextLineHeight,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(4.dp))
                InfoHint("这里只维护 Skill 配置；请在 Agent 设置中绑定需要的 Skill")
            }
                        Spacer(Modifier.weight(1f))
            WwSecondaryButton(
                text = "导入",
                onClick = {
                    importNotice = null
                    importError = null
                    val picked = pickTextFile() ?: return@WwSecondaryButton
                    runCatching { parseSkillText(picked.content, picked.name) }
                        .onSuccess { imported ->
                            onSave(imported)
                            importNotice = "已导入技能「${imported.name}」。"
                        }
                        .onFailure { importError = it.message ?: "导入失败：文件格式不正确。" }
                },
                leadingIcon = TablerIcons.FileImport,
            )
            WwTag(
                text = "新建技能",
                onClick = { startCreate() },
                background = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                leadingIcon = TablerIcons.Plus,
            )
        }

        importNotice?.let {
            Spacer(Modifier.height(WwSize.cardListGap))
            Text(it, fontSize = WwType.hint, lineHeight = WwSize.compactTextLineHeight, color = MaterialTheme.colorScheme.primary)
        }
        importError?.let {
            Spacer(Modifier.height(WwSize.cardListGap))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(TablerIcons.AlertCircle, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(6.dp))
                Text(it, fontSize = WwType.hint, lineHeight = WwSize.compactTextLineHeight, color = MaterialTheme.colorScheme.error)
            }
        }
        if (items.isEmpty()) {
            Text(
                "还没有任何技能，点击「新建技能」或从本地文件导入。",
                fontSize = WwType.hint,
                lineHeight = WwSize.compactTextLineHeight,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        items.forEach { skill ->
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = WwSurfaces.card,
                border = BorderStroke(1.dp, WwSurfaces.cardBorder),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = WwPad.card),
                    verticalAlignment = Alignment.Top,
                ) {
                    if (skill.icon.isNotBlank()) {
                        Text(skill.icon, fontSize = WwType.title)
                        Spacer(Modifier.width(10.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                skill.name.ifBlank { skill.id },
                                fontSize = WwType.body,
                                lineHeight = WwSize.compactTextLineHeight,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            // 参考版只在这条信息真的有用时才显示（>3 个触发词）。
                            if (skill.triggerKeywords.size > 3) {
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "共 ${skill.triggerKeywords.size} 个触发词",
                                    fontSize = WwType.hint,
                                    lineHeight = WwSize.compactTextLineHeight,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (skill.description.isNotBlank()) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                skill.description,
                                fontSize = WwType.hint,
                                lineHeight = WwSize.compactTextLineHeight,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (skill.triggerKeywords.isNotEmpty()) {
                            Spacer(Modifier.height(WwSize.cardListGap))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            ) {
                                skill.triggerKeywords.forEach { keyword ->
                                    WwTag(
                                        text = keyword,
                                        shape = RoundedCornerShape(10.dp),
                                        background = WwSurfaces.inset,
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.width(4.dp))
                    IconButton(onClick = { startEdit(skill) }, modifier = Modifier.size(26.dp)) {
                        Icon(
                            TablerIcons.Edit,
                            contentDescription = "编辑",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { confirmingDelete = skill }, modifier = Modifier.size(26.dp)) {
                        Icon(
                            TablerIcons.Trash,
                            contentDescription = "删除",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }

    // ── 表单层：叠在列表之上 ──
    if (creating || editing != null) {
        FeatureDialog(
            title = if (creating) "新建技能" else "编辑：${editing?.name.orEmpty()}",
            onDismiss = { closeForm() },
            maxWidth = 576.dp,
            heightFraction = 0.9f,
            footer = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.weight(1f))
                    WwSecondaryButton("取消", onClick = { closeForm() })
                    WwPrimaryButton(
                        text = "保存技能",
                        onClick = { saveForm() },
                        enabled = name.isNotBlank() && injection.isNotBlank(),
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
                        label = "技能名称 *",
                        placeholder = "例如：方案规划",
                        modifier = Modifier.weight(1f),
                    )
                    WwTextField(
                        value = icon,
                        onValueChange = { icon = it },
                        label = "图标",
                        placeholder = "⚡",
                        modifier = Modifier.width(96.dp),
                    )
                }
                WwTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = "技能描述",
                    placeholder = "一句话说明这个技能做什么",
                    singleLine = false,
                    maxLines = 3,
                    minHeight = 56.dp,
                )
                WwTextField(
                    value = keywordsStr,
                    onValueChange = { keywordsStr = it },
                    label = "触发词",
                    placeholder = "分析, 规划, 方案",
                    hint = "逗号 / 中文逗号 / 空格分隔；用户消息命中即激活该技能。留空记为「通用」。",
                )
                WwTextField(
                    value = toolsNeededStr,
                    onValueChange = { toolsNeededStr = it },
                    label = "所需工具（可选）",
                    hint = "填 MCP 工具名，提示这个技能需要哪些外部能力；留空表示不需要。",
                )
                WwTextField(
                    value = injection,
                    onValueChange = { injection = it },
                    label = "系统指令（注入文本）*",
                    hint = "命中触发词时，这段文本会被拼进 system 提示词。",
                    singleLine = false,
                    maxLines = 10,
                    minHeight = 160.dp,
                )
            }
        }
    }

    // ── 删除确认 ──
    confirmingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { confirmingDelete = null },
            title = { Text("删除技能") },
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

/** 触发词 / 工具名解析：逗号（中英）与空格都算分隔符。 */
private fun parseKeywordList(raw: String): List<String> =
    raw.split(Regex("[,，\\s]+")).map { it.trim() }.filter { it.isNotEmpty() }

@Composable
fun PreferencesPanel(
    ds: MessageDisplaySettings, ap: AppPreferences,
    palette: ThemePalette, uiModeRaw: String,
    onSaveDs: (MessageDisplaySettings) -> Unit,
    onSaveAp: (AppPreferences) -> Unit,
    onSetTheme: (ThemePalette) -> Unit,
    onSetUiMode: (UiMode) -> Unit,
) {
    Text("偏好设置", fontSize = WwType.title)
    Spacer(Modifier.height(WwSize.cardListGap))
    Text("主题", fontSize = WwType.title)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        BuiltinThemePalette.entries.take(7).forEach { p ->
            val id = p.name.lowercase()
            WwTag(
                text = id,
                onClick = { onSetTheme(id) },
                enabled = id != palette,
                background = if (id == palette) MaterialTheme.colorScheme.primary else WwSurfaces.inset,
                contentColor = if (id == palette) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
    Row(modifier = Modifier.fillMaxWidth().padding(top = WwSize.cardListGap), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        BuiltinThemePalette.entries.drop(7).forEach { p ->
            val id = p.name.lowercase()
            WwTag(
                text = id,
                onClick = { onSetTheme(id) },
                enabled = id != palette,
                background = if (id == palette) MaterialTheme.colorScheme.primary else WwSurfaces.inset,
                contentColor = if (id == palette) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
    Spacer(Modifier.height(WwSize.cardListGap))
    Text("明暗模式", fontSize = WwType.title)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WwTag(
            text = "浅色",
            onClick = { onSetUiMode(UiMode.LIGHT) },
            enabled = uiModeRaw != "light",
            background = if (uiModeRaw == "light") MaterialTheme.colorScheme.primary else WwSurfaces.inset,
            contentColor = if (uiModeRaw == "light") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        )
        WwTag(
            text = "深色",
            onClick = { onSetUiMode(UiMode.DARK) },
            enabled = uiModeRaw != "dark",
            background = if (uiModeRaw == "dark") MaterialTheme.colorScheme.primary else WwSurfaces.inset,
            contentColor = if (uiModeRaw == "dark") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        )
    }
    Spacer(Modifier.height(WwSize.cardListGap))
    Text("显示设置", fontSize = WwType.title)
    SwitchRow("显示模型名", ds.showModelName) { onSaveDs(ds.copy(showModelName = it)) }
    SwitchRow("显示消息时间", ds.showMessageTime) { onSaveDs(ds.copy(showMessageTime = it)) }
    SwitchRow("显示耗时", ds.showLatency) { onSaveDs(ds.copy(showLatency = it)) }
    SwitchRow("显示会话Token", ds.showSessionTokens) { onSaveDs(ds.copy(showSessionTokens = it)) }
    SwitchRow("显示消息Token", ds.showMessageTokens) { onSaveDs(ds.copy(showMessageTokens = it)) }
    Spacer(Modifier.height(WwSize.cardListGap))
    Text("应用偏好", fontSize = WwType.title)
    SwitchRow("联网搜索默认开", ap.onlineSearchEnabled == true) { onSaveAp(ap.copy(onlineSearchEnabled = it)) }
    SwitchRow("流式输出默认开", ap.streamingEnabled == true) { onSaveAp(ap.copy(streamingEnabled = it)) }
}

/** 设置 → 主题（独立子面板） */
@Composable
private fun SwitchRow(label: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = WwSize.boxVerticalPadding), Arrangement.SpaceBetween, Alignment.CenterVertically) {
        Text(label, fontSize = WwType.body, lineHeight = WwSize.compactTextLineHeight)
        WwSwitch(checked = checked, onCheckedChange = onToggle)
    }
}




