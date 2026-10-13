# Web 版 UI 结构参照（原版 React/TypeScript）

> 本文件由模型在分析 `E:\WatchWorld\WatchWorld` 源代码后撰写，供 Native 迁移时对照。
> 最后更新：2026-10-08

---

## 一、整体布局（App.tsx）

```
┌─────────────────────────────────────┐
│  Header（顶栏·固定）                  │
│  [☰ 侧边栏]  [会话标题]       [🌙]  │
├─────────────────────────────────────┤
│                                     │
│  ChatMessageList（消息列表·flex-1）   │
│  ← 占满剩余高度，可滚动              │
│                                     │
├─────────────────────────────────────┤
│  ChatInput（输入区·底部固定）         │
│  [模型选择] [功能按钮栏] [文本框]     │
│  [时间线选择]       [发送/停止]       │
└─────────────────────────────────────┘
    ↑ 左滑出                ↑ 弹窗层
 SidebarDrawer          各种 Modal
```

- 容器：`flex flex-col w-full h-[100dvh]` 三段式
- **侧边栏和设置是弹窗/覆盖层，不替换聊天区**

---

## 二、Header（顶栏）

- **左侧**：☰ 菜单按钮（打开侧边栏）+ 当前会话标题（截断）
- **右侧**：🌙/☀ 深浅色切换
- 固定不滚动，有 `safe-area-inset-top` 适配刘海

---

## 三、SidebarDrawer（左侧抽屉侧边栏）

从左侧滑出覆盖在主界面上方，分三个区域：

### 3.1 顶部操作栏
- **新建窗口** (+) / **搜索** / **新建分组** / **导入存档** / **深浅色切换**

### 3.2 中部：会话列表（按分组折叠）
- 每个 ChatGroup 可折叠/展开
- 每条显示 Agent 头像 + 标题 + 置顶标记
- 长按弹出操作弹窗（删除）

### 3.3 底部：功能导航网格（4列 × 2行 = 8 入口）

| 位置 | Web 图标(Lucide) | 名称 | 打开弹窗 |
|------|------------------|------|----------|
| 1 | Server | MCP | McpModal |
| 2 | Zap | Skill | SkillModal |
| 3 | BookOpen | 知识库 | KnowledgeBaseModal |
| 4 | Globe | 世界观 | DocumentModal（纯库） |
| 5 | GitBranch | 时间线 | TimelineLibraryModal |
| 6 | Plug | API | ProviderModal |
| 7 | _(空/备份)_ | _(Web无备份入口在此)_ | |
| 8 | Settings | 设置 | SettingsHubModal |

带徽章计数：MCP 激活数、知识库连接数、正则激活数。

---

## 四、ChatInput（底部输入区）

### 4.1 快捷功能按钮行
- 模型选择下拉（按 Provider 分组，可拖拽排序）
- Agent 切换 / 时间线选择器 / AI 上下文可见性 / 联网搜索开关
- 图片上传 / **窗口功能** 按钮 → 展开 WindowFunctionPanel

### 4.2 WindowFunctionPanel（从底部上滑的 3 列网格）

| 图标 | 名称 | 弹窗 |
|------|------|------|
| Sparkles | 前文总结 | SummaryModal |
| Layers | 双框提示词 | PromptDualBoxModal |
| Search | 全文搜索 | SearchModal |
| FileText | 本窗口世界观 | DocumentModal（窗口勾选） |
| BarChart2 | 角色心理分析 | 直接触发 AI |
| Settings | 窗口 API 参数 | WindowApiParamsModal |
| ClipboardPaste | 窗口导出删除 | SessionTextModal |
| Code2 | 本窗口正则 | RegexModal（窗口勾选） |

### 4.3 文本输入
- `<textarea>` 自适应高度（max 200px）
- 支持粘贴/拖拽图片，图片预览缩略图行
- 发送 / 停止二态按钮

---

## 五、SettingsHubModal（设置总入口）

从侧边栏「设置」进入，列表式导航：

| 图标 | 名称 | 目标弹窗 |
|------|------|----------|
| Bot | Agent 管理 | AgentModal |
| Sliders | 默认与偏好 | PreferencesModal |
| MessageSquare | 文本与显示 | SettingsModal |
| Palette | 主题 | ThemeModal |
| Sparkles | 提示词库 | PromptModal |
| Code2 | 正则库 | RegexModal（纯库） |
| Cloud | 备份与导入导出 | BackupSyncModal |

---

## 六、全部弹窗清单（按功能域分组）

| 功能域 | 弹窗组件 | 入口 |
|--------|---------|------|
| **API 与模型** | ProviderModal | 侧边栏 |
| | ModelSelectorModal | 输入区 |
| | WindowApiParamsModal | 窗口功能 |
| **Agent 与提示词** | AgentModal | 侧边栏/设置 |
| | PromptDualBoxModal | 窗口功能 |
| | PromptModal | 设置Hub |
| **会话管理** | GroupModal | 侧边栏 |
| | AiVisibilityModal | 输入区 |
| | SearchModal | 窗口功能 |
| | SessionTextModal | 窗口功能 |
| | SummaryModal | 窗口功能 |
| **知识与世界观** | KnowledgeBaseModal | 侧边栏 |
| | DocumentModal | 侧边栏(纯库)/窗口功能(窗口勾选) |
| **时间线** | TimelineModal | 窗口功能 |
| | TimelineLibraryModal | 侧边栏 |
| **工具扩展** | McpModal | 侧边栏 |
| | SkillModal | 侧边栏 |
| | RegexModal | 设置Hub(纯库)/窗口功能(窗口勾选) |
| **外观与偏好** | ThemeModal | 设置Hub |
| | SettingsModal | 设置Hub |
| | PreferencesModal | 设置Hub |
| | SettingsHubModal | 侧边栏(入口页) |
| **数据管理** | BackupSyncModal | 设置Hub |

---

## 七、设计体系要点

- **主题**：双色（m1 主色 + m2 辅色），支持自定义，深/浅色模式
- **字号**：`--chat-font-size` CSS 变量驱动 `chat-text-xs/sm/base/lg`
- **Design Token**：`surface-0~3`、`text-primary/secondary/tertiary`、`border-subtle/default`
- **圆角**：`radius-sm(8px)` → `radius-pill(999px)`
- **框架**：React + TypeScript + Tailwind CSS v4 + Lucide 图标
- **存储**：localStorage + IndexedDB（图片）

---

## 八、Native 迁移心得与注意

### 布局
- **侧边栏和设置必须是覆盖层**，不能替代聊天区。Web 版所有弹窗都是 `fixed inset-0 z-[60]` 叠在主内容上方。
- Native 版用 `Box` 叠层 + 半透明遮罩实现（点遮罩关闭），设置用 `Dialog(usePlatformDefaultWidth = false)` 全屏。
- TopBar 用 Material3 `TopAppBar`，结构对标 `Header.tsx`。

### 图标
- Web 版用 **Lucide Icons**（线条风格、圆角、2px 描边）。
- Compose MP **没有** Lucide 库；`material-icons-extended` 在 1.12.x 版已从仓库移除（最新只到 1.7.3）。
- **替代方案**：`br.com.devsrsouza.compose.icons:tabler-icons:1.1.1`（Tabler Icons，和 Lucide 几乎同款线条风格）。
- ⚠️ Tabler Icons 每个图标是 `TablerIcons` 的**扩展属性**，必须单独 import：
  ```kotlin
  import compose.icons.TablerIcons
  import compose.icons.tablericons.Menu   // 每个图标单独 import
  Icon(TablerIcons.Menu, "菜单")
  ```
- ⚠️ Tabler 的图标名和 Lucide 不完全一致，需要逐个核对。JAR 内可用 `jar tf xxx.jar | grep 关键词` 搜索。

### 图标对照表（Web Lucide → Native Tabler）

| Web (Lucide) | Native (Tabler) | 用途 |
|---|---|---|
| Menu | TablerIcons.Menu | 侧边栏菜单 |
| Sun / Moon | TablerIcons.Sun / .Moon | 明暗切换 |
| Server | TablerIcons.Server | MCP |
| Zap | TablerIcons.Bolt | Skill |
| BookOpen | TablerIcons.Book | 知识库 |
| Globe | TablerIcons.World | 世界观 |
| GitBranch | TablerIcons.GitBranch | 时间线 |
| Plug | TablerIcons.Plug | API |
| FileUp / CloudUpload | TablerIcons.Cloud | 备份 |
| Settings | TablerIcons.Settings | 设置 |

### 依赖踩坑
- `org.jetbrains.compose.material:material-icons-extended` 在 Compose MP 1.12.x 中**不存在**（1.7.3 是最后版本）。别浪费时间试。
- `org.jetbrains.compose.material:material:1.12.1` 虽然在 classpath（被 material3 传递拉入），但其中**不包含 Icons 类**。
- `compose-icons` 库是正经 KMP 库（有 metadata + desktop + ios 变体），放 `commonMain` 没问题。

### 弹窗分「纯库」和「窗口勾选」两种模式
- RegexModal 和 DocumentModal 各出现两次：侧边栏/设置入口打开的是**纯库模式**（只管理规则/文档），窗口功能打开的是**窗口勾选模式**（带本窗口启用开关）。
- Native 迁移时可以用同一个 Composable 加 `mode` 参数区分。
