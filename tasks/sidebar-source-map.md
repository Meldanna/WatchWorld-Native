# 侧边栏功能区源代码地图

> 源码仓库：`e:\WatchWorld\WatchWorld\src\`（React/TypeScript，Capacitor 移动端 Web 应用）
> 生成时间：2026-10-09
> 补全记录：2026-10-10 补入侧边栏功能区 7 个库弹窗（§六–§十二），原 §六–§八 顺延为 §十三–§十五

---

## 一、核心组件总览

### 1.1 侧边栏本体

| 组件 | 文件 | 行数 | 职责 |
|---|---|---|---|
| **SidebarDrawer** | `components/SidebarDrawer.tsx` | 589 行 | 左滑抽屉侧边栏：会话列表 + 底部功能导航网格 |
| **WindowFunctionPanel** | `components/WindowFunctionPanel.tsx` | 235 行 | 输入栏上方的「窗口功能」上拉面板（当前窗口级操作） |
| **Header** | `components/Header.tsx` | 69 行 | 顶栏：☰ 菜单按钮（打开侧边栏）+ 会话标题 + 主题切换 |
| **SettingsHubModal** | `components/SettingsHubModal.tsx` | 183 行 | 侧边栏底部「设置」齿轮点开的二级设置总入口 |
| **ChatInput** | `components/ChatInput.tsx` | ~580 行 | 输入栏组件，内嵌 WindowFunctionPanel |

### 1.2 侧边栏功能区弹窗（底格 + 设置总入口的去向）

| # | 底格 / 入口 | 弹窗组件 | 文件 | 行数 | 章节 |
|---|---|---|---|---|---|
| 1 | MCP | **McpModal** | `components/McpModal.tsx` | 507 行 | [§九](#九mcpmdal--mcp-服务管理) |
| 2 | Skill | **SkillModal** | `components/SkillModal.tsx` | 656 行 | [§十](#十skillmodal--skill-技能库) |
| 3 | 知识库 | **KnowledgeBaseModal** | `components/KnowledgeBaseModal.tsx` | 397 行 | [§十一](#十一knowledgebasemodal--知识库) |
| 4 | 世界观 | **DocumentModal**（库形态） | `components/DocumentModal.tsx` | 1067 行 | [§六](#六documentmodal--世界观库) |
| 5 | 时间线 | **TimelineLibraryModal** | `components/TimelineLibraryModal.tsx` | 274 行 | [§七](#七timelinelibrarymodal--时间线库) |
| 6 | API | **ProviderModal** | `components/ProviderModal.tsx` | 772 行 | [§八](#八providermodal--多-api-接口管理) |
| 7 | 正则库 ⚠️ | **RegexModal**（库形态） | `components/RegexModal.tsx` | 675 行 | [§十二](#十二regexmodal--正则库) |
| 8 | 设置 | **SettingsHubModal** | `components/SettingsHubModal.tsx` | 183 行 | [§五](#五settingshubmodal--设置总入口) |

⚠️ **第 7 格是 Native 特有的**：参考版这一格原本是「备份」，且 `SidebarDrawer` 里的正则入口是死 prop（详见 §十二 开头）。T25 定案删掉备份、改挂正则库。

**窗口功能面板的去向**（窗口级，不占底格）：DocumentModal（窗口形态）、RegexModal（窗口形态）、TimelineModal、SummaryModal、PromptDualBoxModal、SearchModal、WindowApiParamsModal、SessionTextModal —— 见 §三。

---

## 二、SidebarDrawer — 主侧边栏抽屉

**文件**: `components/SidebarDrawer.tsx`

### 2.1 Props 接口 (L34-68)

```ts
interface SidebarDrawerProps {
  isOpen: boolean;                    // 是否展开
  onClose: () => void;                // 关闭回调
  sessions: ChatSession[];            // 全部会话
  groups: ChatGroup[];                // 分组列表
  agents: Agent[];                    // 顾问列表
  theme: ThemeConfig;                 // 当前主题
  uiMode: UiMode;                     // light/dark
  onToggleUiMode: () => void;         // 切换明暗
  activeSessionId: string;            // 当前活跃窗口
  onSelectSession: (id: string) => void;
  onNewSession: (groupId?: string) => void;
  onCreateGroup: (name: string) => void;
  onImportSession: (file: File) => void;
  onDeleteSession: (id: string) => void;
  onOpenGroupModal: () => void;
  onOpenSettingsModal: () => void;        // → API 设置
  onOpenDisplaySettingsModal: () => void;  // → 显示设置
  onOpenTimelineModal: () => void;         // → 时间线库
  onOpenAgentModal: () => void;            // → Agent 管理
  onOpenPromptModal: () => void;           // → 提示词
  onOpenKnowledgeModal: () => void;        // → 知识库
  onOpenRegexModal: () => void;            // → 正则库
  onOpenThemeModal: () => void;            // → 主题
  onOpenSettingsHub: () => void;           // → 设置总入口
  onOpenMcpModal: () => void;              // → MCP
  onOpenSkillModal: () => void;            // → Skill
  onOpenWorldLibrary: () => void;          // → 世界观库
  activeMcpCount: number;                  // MCP 徽章数
  connectedKnowledgeCount: number;         // 知识库徽章数
  activeRegexCount: number;                // 正则徽章数
}
```

### 2.2 UI 结构

```
┌─ Backdrop (L163-167): 半透明遮罩, z-40
│
├─ <aside> (L171-491): 左侧 84%/max 320px, z-50, 滑入动画
│  ├─ Top Header (L182-211): logo「观界」+ 明暗切换 + 关闭按钮
│  ├─ Action Buttons (L213-261): 新建分组 + 导入对话 + 新建分组(桌面)
│  ├─ Search Bar (L263-280): 搜索窗口或消息
│  ├─ Group & Session List (L282-394): 分组折叠 → 窗口列表, 长按/右键删除
│  └─ Bottom Feature Nav Grid (L396-489): 4列网格功能按钮
│     ├─ MCP        → onOpenMcpModal        (Server 图标, emerald)
│     ├─ Skill      → onOpenSkillModal      (Zap 图标, amber)
│     ├─ 知识库     → onOpenKnowledgeModal  (BookOpen 图标, blue)
│     ├─ 世界观     → onOpenWorldLibrary    (Globe 图标, teal)
│     ├─ 时间线     → onOpenTimelineModal   (GitBranch 图标, indigo)
│     ├─ API        → onOpenSettingsModal   (Plug 图标, rose)
│     └─ 设置       → onOpenSettingsHub     (Settings 图标, slate)
│
├─ 新建分组弹窗 (L493-543): isCreateGroupOpen 控制
└─ 窗口操作弹窗 (L546-585): selectedSession 控制 (长按/右键菜单 → 删除)
```

### 2.3 内部状态

| 状态 | 类型 | 作用 |
|---|---|---|
| `searchQuery` | string | 搜索框关键词 |
| `collapsedGroups` | Record<string,bool> | 分组折叠状态 |
| `isCreateGroupOpen` | bool | 新建分组弹窗 |
| `newGroupName` | string | 新分组名称输入 |
| `selectedSession` | ChatSession\|null | 长按/右键选中的窗口（操作弹窗） |
| `longPressTimerRef` | ref | 长按计时器 |
| `longPressTriggeredRef` | ref | 长按是否触发 |
| `importSessionInputRef` | ref | 文件导入 input |

---

## 三、WindowFunctionPanel — 窗口功能上拉面板

**文件**: `components/WindowFunctionPanel.tsx`

### 3.1 Props 接口 (L16-27)

```ts
interface WindowFunctionPanelProps {
  isOpen: boolean;
  onToggle: () => void;
  onOpenDualBoxPrompt?: () => void;     // 双框提示词
  onOpenSearch?: () => void;            // 全文搜索
  onOpenDocument?: () => void;          // 本窗口世界观
  onTriggerRoleAnalysis?: () => void;   // 角色心理分析
  onOpenWindowApiParams?: () => void;   // 窗口API参数
  onOpenSessionText?: () => void;       // 窗口导出删除
  onOpenSummary?: () => void;           // 前文总结
  onOpenWindowRegex?: () => void;       // 本窗口正则
}
```

### 3.2 UI 结构

```
┌─ Toggle Button (L44-58): pill 按钮，齿轮图标 + "窗口功能"（桌面显示文字）
│
└─ Portal → body (L63-231): createPortal 到 body，避免 backdrop-blur 包含块问题
   ├─ Backdrop (L67-69): 黑色半透明, z-40
   └─ 上拉框 (L72-228): bottom-0 滑出, z-45, 3列网格
      ├─ 前文总结        → onOpenSummary          (Sparkles 图标)
      ├─ 双框提示词      → onOpenDualBoxPrompt    (Layers 图标)
      ├─ 全文搜索        → onOpenSearch            (Search 图标)
      ├─ 本窗口世界观    → onOpenDocument          (FileText 图标)
      ├─ 角色心理分析    → onTriggerRoleAnalysis   (BarChart2 图标)
      ├─ 窗口API参数     → onOpenWindowApiParams   (Settings 图标)
      ├─ 窗口导出删除    → onOpenSessionText       (ClipboardPaste 图标)
      └─ 本窗口正则      → onOpenWindowRegex       (Code2 图标)
```

**特殊点**: 角色心理分析点击后会自动调 `onToggle()` 关闭面板（L159），其余功能不自动关闭。

---

## 四、Header — 顶栏

**文件**: `components/Header.tsx`

### 4.1 Props (L7-10)

```ts
interface HeaderProps {
  currentSession: ChatSession;
  uiMode: UiMode;
  onToggleUiMode: () => void;
  onOpenSidebar: () => void;   // ☰ 按钮触发
}
```

### 4.2 UI 结构

```
<header> 固定顶部, z-40, 避开刘海 safe-area-inset-top
├─ Left: ☰ Menu 按钮 (L31-37) → onOpenSidebar
├─ Center: 会话标题 (L40-46)
└─ Right: 明暗切换按钮 (L51-63)
```

---

## 五、SettingsHubModal — 设置总入口

**文件**: `components/SettingsHubModal.tsx`

### 5.1 Props (L16-27)

```ts
interface SettingsHubModalProps {
  isOpen: boolean;
  onClose: () => void;
  theme: ThemeConfig;
  onOpenMessageSettings: () => void;    // 文本设置
  onOpenTheme: () => void;              // 主题
  onOpenPromptLibrary: () => void;      // 提示词库
  onOpenRegex: () => void;              // 正则库
  onOpenAgentManager: () => void;       // Agent 管理
  onOpenPreferences: () => void;        // 默认与偏好
  onOpenBackupSync: () => void;         // 备份与导入导出
}
```

### 5.2 菜单项列表（按渲染顺序）

| 项目 | 图标 | 颜色 | 说明 |
|---|---|---|---|
| Agent 管理 | Bot | indigo | 新建/编辑顾问，绑定 MCP 与 Skill |
| 默认与偏好 | Sliders | cyan | 默认模型、新窗口默认配置 |
| 文本设置 | MessageSquare | emerald | 气泡样式、字体、Token 显示 |
| 主题 | Palette | fuchsia | 配色方案与气泡风格 |
| 提示词库 | Sparkles | violet | 提示词库管理 |
| 正则库 | Code2 | purple | 管理正则规则 |
| 备份与导入导出 | Cloud | sky | WebDAV 同步、全量导出恢复 |

**特殊逻辑**: `handle()` 函数先 `onClose()` 再 `setTimeout(fn, 0)` 延迟打开目标弹窗，避免多层叠压。

---

## 六、DocumentModal — 世界观库

**文件**: `components/DocumentModal.tsx`（1067 行）
**入口**: 侧边栏底部「世界观」格（**库形态**）；窗口功能面板「本窗口世界观」（**窗口形态**，多传 `sessionDocIds` / `onToggleForSession` / `onSetSessionDocs`）

**形态判定**（L90）: `const hasWindowScope = Boolean(sessionDocIds && onToggleForSession);`
—— 传了这套 prop 才有「本窗口是否注入」开关与「全选/全不选」；不传就是纯库，只管分组与文档。
`const UNGROUPED = '__ungrouped__';`（L44，未分组伪分组 ID）

### 6.1 Props 接口 (L25-42)

```ts
interface DocumentModalProps {
  isOpen: boolean;
  onClose: () => void;
  groups: WorldGroup[];
  documents: WorldDocument[];
  onSaveGroup: (group: WorldGroup) => void;
  onDeleteGroup: (groupId: string) => void;
  onSaveDocument: (doc: WorldDocument) => void;
  onDeleteDocument: (docId: string) => void;
  theme: ThemeConfig;
  /**
   * 窗口形态：传入后显示「本窗口是否注入」开关。
   * 不传即为库形态（世界观库入口），只管理分组与文档，不显示开关。
   */
  sessionDocIds?: string[];
  onToggleForSession?: (id: string) => void;
  onSetSessionDocs?: (ids: string[]) => void;
}
```

### 6.2 UI 结构 (L517-1067)

```
┌─ 主弹窗 (L519-527): fixed inset-0 z-[70] 居中, bg-black/80 backdrop-blur-sm
│  └─ 容器 (L523): max-w-4xl, 选中文档时 h-[90vh]，未选中时 max-h-[90vh]
│     ├─ 顶栏 (L528-558): BookOpen 徽章 + 「本窗口世界观 / 世界观库」 + 关闭 X
│     └─ 双栏 (L560-747)
│        ├─ 左栏「分组树」(L566-747)
│        │  ├─ 搜索 + 导入 (L574-600): 「搜索设定文档...」 + UploadCloud 导入按钮
│        │  ├─ 加号新建菜单 (L602-646): 点开弹「新建分组」/「新建文档」两项（z-40 透明遮罩点击收起）
│        │  ├─ 统计行 (L649-688): 窗口形态「开关即本窗口是否注入」/ 库形态「共 N 篇 · M 个分组」
│        │  │   + 窗口形态的「全选」「全不选」 + 全折叠/全展开按钮 (L678-686)
│        │  └─ 树区 (L691-748): p-2 space-y-1.5；未选中文档时 flex-none（按内容收缩），选中后 flex-1
│        │      ├─ 顶层分组 (L697) → renderGroup(g, 0)
│        │      └─ 「未分组」伪分组 (L699-748): 可拖入，dragOver 时 ring-m2-500 高亮
│        └─ 右栏「文档详情」(L749-799): 仅 selectedDocId 非空时渲染
│           ├─ 标题 + 分组名徽章 (未分组时显示「未分组」) + [复制全文 / 编辑 / 删除]
│           └─ 正文: font-mono, whitespace-pre-wrap
├─ 文档表单弹窗 (L803-889): z-[80], max-w-2xl max-h-[90vh]
│  └─ 标题输入 → 分组下拉(flatGroups 带 `父 / 子` 路径, 含「未分组」) → 内容 textarea(h-[45vh] font-mono) → 取消/保存
├─ 分组表单弹窗 (L891-965): z-[80], max-w-md
└─ 上级分组选择器 (L967-1063): z-[80], max-w-md max-h-[70vh]，顶部带「搜索分组...」
   ├─ 「移到顶层（作为独立世界观）」 Star 项 (L1013-1026)
   └─ 候选分组列表 (L1036-1056): paddingLeft = 12 + depth*12，已排除自身与全部后代（防环，L1002-1003）
```

### 6.3 内部状态 (L61-91)

| 状态 | 行 | 作用 |
|---|---|---|
| `selectedDocId` | L62 | 选中的文档；初值 `''`（**库形态刻意不默认选中第一篇**，避免移动端底部自动弹出文档预览，见 L60-61 注释） |
| `search` | L63 | 搜索词，匹配标题 + 正文 |
| `collapsed` | L64 | 各分组折叠状态（键为 groupId 或 `UNGROUPED`） |
| `copied` | L65 | 「复制全文」1.5s 的勾选反馈 |
| `draggingDocId` / `dragOverGroupId` | L66-67 | HTML5 拖拽：正在拖的文档 / 悬停的目标分组 |
| 文档表单 | L70-74 | `isDocFormOpen` / `editingDoc` / `docTitle` / `docGroupId` / `docContent` |
| 分组表单 | L77-79 | `isGroupFormOpen` / `groupName` / `groupParentId` |
| 上级分组选择 | L82-83 | `parentPickerFor` / `parentSearch` |
| `isCreateMenuOpen` | L86 | 左栏加号的新建菜单 |
| `fileInputRef` | L88 | 批量导入 input 引用 |

### 6.4 树结构与推导值

| 符号 | 行 | 说明 |
|---|---|---|
| `childrenOf` | L93-102 | `Map<parentId, WorldGroup[]>`；顶层分组的 key 是 `''` |
| `docsOf` | L103-112 | `Map<groupId \| UNGROUPED, WorldDocument[]>` |
| `descendantDocIds` | L114-124 | 某分组（含全部后代分组）下的所有文档 ID；用 `visited` 防成环 |
| `selfAndDescendantGroupIds` | L125-135 | 自身 + 全部后代分组 ID，供上级选择器防环 |
| `flatGroups` | L138-149 | 扁平化分组列表（`{group, depth, path}`），下拉框与搜索都用它 |
| `allGroupKeys` / `allCollapsed` | L155-161 | 全折叠按钮的状态来源（含 `UNGROUPED`） |
| `groupState` | L171-178 | 组开关状态由组内全部文档推导：**全开才显示为开**，同时给出 `total` 计数 |
| `handleToggleDoc` | L180-183 | 库形态直接 return；窗口形态 `onToggleForSession(doc.id)` |
| `handleToggleGroup` | L186-197 | 组开关级联：整组开 = 并集，整组关 = 从 `sessionDocIds` 里剔除全部后代 ID |
| `renderToggle` | L349-366 | 文档行前的方形开关（默认 16px） |
| `renderDoc` / `renderGroup` | L367-408 / L410-514 | 递归渲染；搜索时本组及后代全无命中就整组隐藏（L417） |

### 6.5 关键功能函数

| 函数 | 行 | 职责 |
|---|---|---|
| `openCreateDoc` / `openEditDoc` | L200-215 | 打开文档表单（可带默认分组 / 回填已有文档） |
| `handleSaveDoc` | L216-232 | 组装 `WorldDocument`：`id` = 原 id 或 `doc-${Date.now()}`；`groupId: docGroupId \|\| undefined`；`enabled` 编辑时保留、新建为 `true`；`tags: []`。保存后自动选中该文档 |
| `openCreateGroup` / `handleSaveGroup` | L234-248 | 分组表单；`id` = `wg-${Date.now()}`，`parentId: groupParentId \|\| null` |
| `handleApplyParent` | L250-255 | 从上级选择器写回 `parentId`（`null` = 顶层） |
| `handleFilesUpload` | L257-281 | 批量导入本地文本：每个文件一篇文档，`id` = `doc-file-${now}-${i}`，标题取文件名去扩展名，`tags: ['导入']`，单文件失败只 `console.error` 不中断 |
| `handleCopyContent` | L282-289 | `navigator.clipboard.writeText(currentDoc.content)`，`copied` 亮 1.5s |
| `toggleCollapse` / `toggleAllCollapse` | L290-300 | 单组折叠 / 全部折叠-展开 |
| 拖拽三件套 | L301-332 | `handleDocumentDragStart` / `handleDocumentDragEnd` / `handleDocumentDrop`（拖到分组或「未分组」上改 `groupId`） |

### 6.6 特殊点 / 坑

1. **主题色走模块变量**：`accent = 'var(--m1-500)'`、`accentSoft = 'var(--m2-soft)'`、`mutedText = 'var(--text-tertiary)'`（L335-337）。注释明确：`--accent-primary` / `--accent-primary-alpha` 是改造前的废弃变量（后者从未定义，等于全透明），不要再引用。
2. **字号跟随界面字体设置**：树里的组名与文档名绑定根节点的 `--chat-font-size`（L338-339 注释），因为字号只落在外层会被按钮样式覆盖。
3. **三层 z 叠放**：主弹窗 `z-[70]` → 三个子弹窗统一 `z-[80]`；加号菜单用一层 `z-40` 透明遮罩收起点击（L613）。
4. **拖拽用原生 HTML5 DnD**（`draggable` + `onDragOver/onDrop`），不是库。

---

## 七、TimelineLibraryModal — 时间线库

**文件**: `components/TimelineLibraryModal.tsx`（274 行）
**入口**: 侧边栏底部「时间线」格
**定位**（L27-31 注释）：**只读浏览** —— 列出所有「有时间线的窗口」及其分支。刻意不做开启/关闭开关；时间线可见性、记忆整理开关属于窗口级操作，在「窗口功能 → 时间线管理」里改。

### 7.1 Props 接口 (L16-25)

```ts
interface TimelineLibraryModalProps {
  isOpen: boolean;
  onClose: () => void;
  sessions: ChatSession[];
  /** 当前打开着的窗口 ID，用于高亮 */
  activeSessionId: string;
  theme: ThemeConfig;
  /** 点某条时间线：切到那个窗口并聚焦到该分支 */
  onJumpToTimeline: (sessionId: string, timelineId: string) => void;
}
```

### 7.2 UI 结构 (L94-274)

```
┌─ 弹窗 (L96-100): fixed inset-0 z-[60] 居中, bg-black/80 backdrop-blur-sm, max-w-2xl max-h-[90vh]
│  ├─ 顶栏 (L103-115): GitBranch 徽章(theme.primaryBg/primaryText) + 「时间线库」
│  │   + 副标题 `{totalTimelines} 条分支 • {groups.length} 次会话` + 关闭 X
│  ├─ 工具条 (L130-155): 搜索框 + 「全部折叠/展开」按钮 (ChevronsDownUp / ChevronsUpDown)
│  └─ 列表区 (L156-262): 按窗口分组
│     ├─ 窗口标题行 (L177-208): 折叠箭头 + 会话标题 + `{timelines.length} 条分支`
│     └─ 时间线列表 (L209-262)
│        └─ 每条 (button): 颜色徽标(codeTag || tag) + 名称 + 「AI 不可见」(visible === false) + 当前聚焦的 Check
└─ 空态: filtered 为空时不渲染分组
```

### 7.3 内部状态与推导值

| 符号 | 行 | 说明 |
|---|---|---|
| `search` / `collapsed` | L40-41 | 搜索词 / 各窗口折叠状态 |
| `groups` | L44-50 | **只保留 `(s.timelines \|\| []).length > 0` 的窗口**，形态 `{session, timelines}` |
| `filtered` | L53-70 | 搜索：窗口标题命中 → 该窗口全部分支都留；否则按 `name / descriptionTag / codeTag / tag` 过滤分支，无命中的窗口整体剔除 |
| `allCollapsed` | L72 | 全折叠按钮状态 |
| `toggleCollapse` / `toggleAll` | L76-85 | 单组 / 全部分组的折叠切换 |
| `totalTimelines` | L92 | 顶栏副标题的分支总数（对所有 `groups` 求和，**不受搜索影响**） |
| `accent` / `accentSoft` / `mutedText` | L88-90 | 同 DocumentModal 的模块变量写法 |

### 7.4 特殊点 / 坑

1. `z-[60]`，比 DocumentModal（`z-[70]`）低一层。
2. 点某条时间线走 `onJumpToTimeline(sessionId, timelineId)` —— **切窗口 + 聚焦分支**，不是本弹窗内部切换。
3. `filtered` 命中窗口标题时不做分支过滤（整窗口全展示），这是刻意的搜索语义。

---

## 八、ProviderModal — 多 API 接口管理

> 参考文中「Ⅲ.」空位即本弹窗（侧边栏底部「API」格）。

**文件**: `components/ProviderModal.tsx`（772 行）
**入口**: 侧边栏底部「API」格 → `SidebarDrawer.tsx` L468-477（`Plug` 图标，rose，`title="API设置"`）→ `onOpenSettingsModal` → `App.tsx` L2282 `setIsProviderModalOpen(true)`
**用途**: 管理多个 OpenAI 兼容 / Gemini / Claude 等 API 提供方 —— 增删改、设为当前会话默认、测连通性、从端点拉模型列表、配自定义请求头。

### 8.1 Props 接口 (L48-56)

```ts
interface ProviderModalProps {
  isOpen: boolean;
  onClose: () => void;
  providers: ApiProviderConfig[];
  activeProviderId: string;
  onSelectProvider: (id: string) => void;
  onSaveProvider: (provider: ApiProviderConfig) => void;
  onDeleteProvider: (id: string) => void;
}
```

### 8.2 UI 结构 (L259-771)

```
┌─ 根容器 (L260-770): fixed inset-0 z-50 居中
│  ├─ Backdrop (L262-269): 仅在 !isCreating && !editingProvider 时才 onClose (L264-268)
│  └─ 主对话框 (L272-769): max-w-xl rounded-2xl p-4 sm:p-5, z-10, max-h-[90vh]
│     ├─ 顶栏 (L274-294): Key 图标 + 「多 API 接口管理与兼容」 + 关闭 X
│     └─ Content Body (L297-768): 二选一
│        ├─[A] 创建/编辑表单 (L297-656) ← isCreating || editingProvider
│        │  └─ 全屏层 (L299-655): fixed inset-0 **z-[80]**，盖住整个主对话框
│        │     ├─ Backdrop (L301-307) → 无条件 setIsCreating(false); setEditingProvider(null)
│        │     └─ 表单卡 (L310-654): max-w-xl z-10（**自身无内边距**，靠内层 form L332 的 p-4 sm:p-5）
│        │        ├─ 表单头 (L312-327): 「新建 API 提供方」/「编辑: 名」+ 关闭 X
│        │        └─ <form onSubmit={handleSaveForm}> (L330-653)
│        │           ├─ 名称 + 类型 两列 grid (L335-368): 接口显示名称*(L340) / 协议类型 select(L354-366)
│        │           ├─ API Base URL (L370-382) + InfoHint(L373)
│        │           ├─ API Key: type=password (L384-396) + InfoHint(L387)
│        │           ├─ 模型配置 (L399-562)
│        │           │  ├─ 默认使用模型输入 + 「获取模型」按钮 (L405-423)
│        │           │  ├─ 拉取错误条 (L426-431) ← fetchModelError
│        │           │  ├─ 模型多选下拉 (L434-509) ← showModelDropdown && fetchedModels.length>0
│        │           │  │  搜索框 + 全选 (L438-457) / checkbox 列表 (L460-481) / 已选计数 + 取消·确认 (L484-506)
│        │           │  ├─ 「点击重新展开」按钮 (L512-522)
│        │           │  └─ 已选模型列表 (L525-561): 每行模型名 + 移除 X (L547-556)
│        │           ├─ 自定义请求头 (L564-632): label+InfoHint(L566-569) + 「新建请求头」(L570-578)
│        │           │   空态 (L581-584) / 行列表 name+value+删除 (L586-631)
│        │           └─ 底部 (L635-652): 取消 + 「保存配置」(submit)
│        └─[B] 列表视图 (L656-768)
│           ├─ 列表头 (L659-668): 「已配置的 API 提供方」+ 「添加接口」→ startCreate
│           └─ providers.map (L670-766) → 单条 (L677-764)
│              ├─ 整条 onClick → onSelectProvider(p.id) (L679)
│              ├─ 行头 (L686-745): 名称 + 「内置预配置」标记(L697-701) + 默认模型(L703-705)
│              │   + 按钮组 [测试连接 Activity(L711-719) / 编辑 Settings(L722-728) / 删除 Trash2(L731-743, 仅 !isSystemDefault)]
│              │   按钮组外层 div 用 stopPropagation 阻断冒泡 (L709)
│              └─ 测试结果 banner (L748-763) ← testResults[p.id] 存在时（绿底 CheckCircle2 / 红底 AlertCircle）
```

### 8.3 内部状态 (L67-93)

| 状态 | 行 | 作用 |
|---|---|---|
| `editingProvider` / `isCreating` | L67-68 | 表单态开关（非空即进入表单视图 [A]） |
| `showKeyMap` | L69 | 密钥明示映射 —— **定义后全文件无调用**（死代码） |
| `testingId` | L72 | 正在测试的 provider id（null = 空闲），驱动转圈与禁用 |
| `testResults` | L73-75 | `Record<id, {success, latencyMs, message}>`，**仅组件 state，不持久化** |
| `lastProviderType` | L78 | 上次保存的协议类型，新建时沿用 |
| 表单 | L79-85 | `name` / `type` / `baseUrl` / `apiKey` / `selectedModels` / `defaultModel` / `customHeaders` |
| 拉模型 | L88-93 | `fetchingModels` / `fetchedModels` / `fetchModelError` / `showModelDropdown` / `modelSearch` / `dropdownRef` |

### 8.4 关键功能函数

| 函数 | 行 | 职责 |
|---|---|---|
| `createHeaderEntry` | L30-34 | 生成带唯一 id 的空 HeaderEntry |
| `headersToEntries` / `entriesToHeaders` | L36-46 | `Record<string,string>` ⇄ `HeaderEntry[]`；名称 trim 后为空则丢弃，全空返回 `undefined` |
| `useEffect`(外部点击) | L96-104 | `mousedown` 落在 `dropdownRef` 外时收起模型下拉 |
| `toggleShowKey` | L108-110 | 切换密钥明示 —— **未被任何 JSX 调用**（死代码） |
| `startCreate` | L112-126 | 清空表单；`type` 取 `lastProviderType`；非 custom 时 `baseUrl` 预填 `https://api.openai.com/v1` (L117) |
| `startEdit` | L128-142 | 回填表单，`customHeaders` 经 `headersToEntries` 还原 |
| `handleSaveForm` | L144-184 | **保存链路**（见下） |
| `handleTestConnection` | L186-191 | **测试链路**：`testProviderConnection(p)` → 写 `testResults[p.id]`，结果不弹窗、渲染成行内 banner |
| `buildTempProvider` | L194-206 | 用当前表单值造一个 `id:'temp'` 的临时 provider，仅供拉模型 |
| `handleFetchModels` | L208-227 | **拉模型链路**（见下） |
| `handleToggleModel` / `handleSelectAll` / `handleConfirmModels` | L229-253 | 单个勾选 / 全选-取消全选 / 确认勾选（若 `defaultModel` 不在已选内则回退为 `selectedModels[0]`）并收起下拉 |
| `filteredModels` | L255-257 | 下拉列表**用这个**（`modelSearch` 过滤后的），不是全量 `fetchedModels` |

**保存链路（输入 → 字段 → 回调）**：表单 state → L144 校验 `name.trim()` → L148 `parsedModels = selectedModels.length>0 ? selectedModels : [defaultModel].filter(Boolean)` → L150 `parsedHeaders = entriesToHeaders(customHeaders)` → **L153-163 baseUrl 补全**：仅 `openai|deepseek|ollama|openrouter|groq|custom` 走补全，先去尾斜杠，若不以 `/chat/completions`、`/v1/chat/completions`、`/v1` 结尾则追加 `/v1`；`gemini` / `claude` 不补 → L165-178 组装对象（`...editingProvider` 展开以保留旧 `default*` 兼容字段，`id` = 原 id 或 `provider-custom-${Date.now()}`，`enabled: true`，`isSystemDefault` 继承，`defaultModel: defaultModel.trim() || parsedModels[0] || ''`）→ `onSaveProvider`。

**拉模型链路**：「获取模型」(L415) → `handleFetchModels`(L208) → `buildTempProvider()`(L214) → `fetchModelsFromProvider(temp)`(L215) → 有 error 写 `fetchModelError`(L219)；空结果写「未获取到任何模型…」(L221)；成功则 `setFetchedModels` + 展开下拉 + 清空搜索(L223-225)。

**删除链路**：L733-737 `confirm()` → `onDeleteProvider(p.id)`。

### 8.5 关键默认值与常量

- 默认协议类型 `'openai'`（`lastProviderType` L78 与 `type` L80 初值）。
- 协议下拉 **7 项**（L359-365）：`gemini` / `openai` / `claude` / `deepseek` / `openrouter` / `groq` / `ollama`。⚠️ **下拉里没有 `custom`**，但 L117 / L154 仍留着 `custom` 分支。
- 保存对象默认：`enabled: true`；`isSystemDefault: editingProvider?.isSystemDefault || false`；`models: parsedModels.length > 0 ? parsedModels : []`。
- 测试状态机：`testingId` = `null`(空闲) / `p.id`(进行中)；派生 `isTesting`(L674)、`testResult`(L673)。

### 8.6 特殊点 / 坑

1. **三层 z 叠放**：根 `z-50` → 主对话框 `z-10` → 表单层**同组件内再开一层 `fixed inset-0 z-[80]`**。进表单态时主对话框的 Header 仍在 DOM 里，只是被盖住。
2. **两个 Backdrop 行为不同**：外层只在「既非新建也非编辑」时才 `onClose()`；表单层无条件关闭表单。
3. **表单不是子组件、也不是 portal**，就是 `isCreating || editingProvider` 的三元分支。
4. **密钥没有显隐切换**：`apiKey` 恒为 `type="password"`。
5. **模型下拉是行内块不是浮层**（`relative z-20` 普通 div），会**撑开表单高度**，靠 mousedown 外部点击关闭。
6. **下拉打开时不隐藏「已选模型列表」**：两者由各自独立条件控制，可同时在 DOM 里。
7. **关闭弹窗不重置状态**：`if (!isOpen) return null`(L106) 只是提前 return，组件实例不卸载 —— `testResults`、表单、`lastProviderType` 都会跨「关-开」保留。
8. **内置预配置不可删**：删除按钮仅在 `!p.isSystemDefault` 时渲染。
9. **`...editingProvider` 展开保存**是为保留旧字段（注释：兼容未迁移的 Web 配置），所以保存对象可能带表单以外的历史字段。
10. **未使用的 import**：`Check` / `Globe` / `Play` / `Eye` / `EyeOff` / `Server`（仅出现在 L8-16 的 import 行）。

---

## 九、McpModal — MCP 服务管理

**文件**: `components/McpModal.tsx`（507 行）
**入口**: 侧边栏底部「MCP」格（徽章数为启用的 MCP 数）

### 9.1 Props 接口 (L44-52)

```ts
interface McpModalProps {
  isOpen: boolean;
  onClose: () => void;
  mcpServers: McpServerConfig[];
  theme: ThemeConfig;
  onSaveMcpServer: (server: McpServerConfig) => void;
  onDeleteMcpServer: (id: string) => void;
  onTestServer: (server: McpServerConfig) => Promise<McpConnectionTestResult>;
}
```

### 9.2 UI 结构 (L154-506)

```
┌─ 弹窗 (L154-162): fixed inset-0 z-50 居中, max-w-xl max-h-[92vh], p-4 sm:p-5
│  ├─ 顶栏 (L163-184): Server 徽章 + 「MCP (Model Context Protocol) 服务管理」
│  │   + 副标题「连接外部 MCP 工具服务器（SSE / Stdio / API），赋能 AI 真实环境操作」+ 关闭 X
│  └─ Content Body (L186-497): 二选一
│     ├─[A] 创建/编辑表单 (L187-362) ← isCreating || editingServer
│     │  └─ 全屏层 (L189): fixed inset-0 **z-[80]** / Backdrop (L192) → 关闭表单
│     │     └─ 表单卡 (L200): max-w-xl max-h-[90vh]
│     │        ├─ 表单头 (L201-218)
│     │        └─ <form onSubmit={handleSave}> (L220-362)
│     │           ├─ 名称 + 协议 grid-cols-3 (L221-249): 服务名称*(L226) / 协议 select(L240-247)
│     │           ├─ 远程 MCP URL* (L252-256) + 提示「手机和正式部署推荐 HTTPS」(L255)
│     │           ├─ Bearer Token（可选）type=password (L257-260)
│     │           ├─ 附加请求头 (L261-327): 「新建请求头」按钮(L266-273) / 空态(L276-279) / 行 name+value+删除(L282-324)
│     │           ├─ 服务功能描述 (L330-341)
│     │           └─ 底部 (L343-361): 取消 + 「保存 MCP 服务」(submit)
│     └─[B] 列表视图 (L365-497)
│        ├─ 列表头 (L367-396): 「已注册 MCP 服务器（N 项，绑定请到 Agent 设置）」
│        │   + 「导入」FileUp(L379-387, 隐藏 input L372-378, accept=.md/.txt) + 「添加 MCP 服务」(L388-394)
│        └─ mcpServers.map (L398-496) → 每条卡 (L402-495)
│           ├─ 行头 (L406-455): 名称 + 类型徽标(`server.type.toUpperCase()`) + `url`(L417-419, font-mono)
│           │   + 按钮组 [测试 Activity(L424-435, 转圈) / 编辑 Edit2(L436-442) / 删除 Trash2(L443-453, confirm 后删)]
│           ├─ 状态行 (L457-478): 「最近测试成功 · N ms」/「正在连接并发现工具…」/「尚未验证连接」+ 错误文案
│           └─ 工具区 (L480-497): Wrench 图标 + 「已发现工具:」+ 工具名 pills
```

### 9.3 内部状态与函数

| 符号 | 行 | 说明 |
|---|---|---|
| `HeaderEntry` 三件套 | L20-42 | `createHeaderEntry` / `headersToEntries` / `entriesToHeaders`（与 ProviderModal 同款，独立实现） |
| `editingServer` / `isCreating` | L63-64 | 表单态开关 |
| `importInputRef` | L65 | 导入 input 引用 |
| 表单 state | L68-73 | `name` / `type` / `url` / `authToken` / `headers` / `description` |
| `testingId` | L74 | 正在测试的 server id |
| `startCreate` | L78-88 | 清空表单进入新建态 |
| `startEdit` | L89-99 | 回填（`headersToEntries(server.headers)`） |
| `handleSave` | L100-123 | 校验 `name` / `url` 非空 → 组装 `McpServerConfig`（`id` = 原 id 或 `mcp-${Date.now()}`，`type` 默认 `streamable_http`，`tools: editingServer?.tools \|\| []`，`status: 'disconnected'`，`lastTestedAt: undefined`）→ `onSaveMcpServer` |
| `handleTest` | L124-135 | `setTestingId(server.id)` → `await onTestServer(server)` → 写回连接结果 |
| `handleImport` | L136-151 | 读文件 → **2MB 上限**（超限 `window.alert`）→ `parseMcpText(text, fileName)` → `onSaveMcpServer`，提示「已导入…默认保持停用」 |

### 9.4 特殊点 / 坑

1. 协议下拉只有 **`streamable_http`（HTTP (Streamable HTTP)）** 与 **`sse`** 两项（L245-247）；标题里的 "Stdio" 只是文案，UI 无此项。
2. URL 占位符随协议变化：`sse` → `https://example.com/sse`，否则 `https://example.com/mcp`（L254）。
3. 导入链路用**原生 `window.alert`** 反馈成功/失败，不是弹窗内提示条。
4. 「已发现工具」来自 `server.tools`，由测试连接时写入（本次 `handleSave` 会保留旧 `tools`）。

---

## 十、SkillModal — Skill 技能库

**文件**: `components/SkillModal.tsx`（656 行）
**入口**: 侧边栏底部「Skill」格

### 10.1 Props 接口 (L23-33)

```ts
interface SkillModalProps {
  isOpen: boolean;
  onClose: () => void;
  skills: AgentSkill[];
  theme: ThemeConfig;
  mode: 'library';
  onSaveSkill: (skill: AgentSkill) => void;
  onDeleteSkill: (id: string) => void;
  /** 是否允许访问 Skill 目录（需要联网）；false 时按钮禁用 */
  onlineSearchAvailable?: boolean;
}
```

### 10.2 UI 结构 (L196-653)

```
┌─ 弹窗 (L196-204): fixed inset-0 z-50 居中, max-w-xl max-h-[92vh]
│  ├─ 顶栏 (L205-226): ⚡ 徽章 + 「Skill 技能库」
│  │   + 副标题「浏览、创建和维护技能；绑定关系在 Agent 设置中管理」+ 关闭 X
│  ├─ Content Body (L228-497): 二选一
│  │  ├─[A] 创建/编辑表单 (L229-364) ← isCreating || editingSkill，**fixed inset-0 z-[80]** 全屏层
│  │  │  └─ 表单卡 (L240): max-w-xl max-h-[90vh]
│  │  │     ├─ 名称 / 图标 / 描述 / 触发词(L480-492 pills) / 所需工具 / 注入指令
│  │  │     └─ 底部: 取消 + 保存
│  │  └─[B] 技能列表 (L365-496)
│  │     ├─ 列表头 (L367-422): 「Skill 配置库（N 项）」+ InfoHint「这里只维护 Skill 配置；请在 Agent 设置中绑定需要的 Skill」
│  │     │   + 「导入」(FileUp, L383-391) + 「目录」(Search, L392-412) + 「新建技能」(L413-419)
│  │     └─ skills.map (L424-495) → 卡片 (L426-…): 图标 + 名称 + 描述 + 触发词数 + [编辑 Edit2 / 删除 Trash2]
│  └─ Skill 目录导入弹窗 (L499-653): 独立一层 **z-[90]**, max-w-2xl max-h-[92vh]
│     ├─ 头部 (L507-525): Search 图标 + 「从 Skill 目录导入」
│     │   + 副标题「来源 agent-skills.md · 先预览 SKILL.md，确认后才写入本地技能库」
│     ├─ 搜索行 (L527-549): 输入框(placeholder「输入关键词，例如 pdf、git、summarize」, Enter 即搜) + 搜索按钮
│     ├─ 结果列表 → 点条目走 openDirectoryPreview
│     └─ 预览区 (L551-632): 标题 + ExternalLink「在目录站查看」(L603)
│         + 加载态 Loader2「正在读取 SKILL.md…」(L610) / 错误 / <pre> 预览正文
│     └─ 底部 (L633-649): 取消 + 「导入到技能库」(disabled 条件 = 无预览正文 / 加载中 / 有错误)
```

### 10.3 内部状态与函数

| 符号 | 行 | 说明 |
|---|---|---|
| `editingSkill` / `isCreating` | L37-38 | 表单态开关 |
| `importInputRef` | L39 | 本地导入 input |
| 目录 8 项 state | L42-50 | `isDirectoryOpen` / `directoryQuery` / `directoryResults` / `directoryBusy` / `directoryError` / `previewEntry` / `previewContent` / `previewLoading` / `previewError` |
| 表单 state | L53-58 | `name` / `icon`(默认 `'⚡'`) / `description` / `keywordsStr` / `toolsNeededStr` / `injection` |
| `startCreate` | L62-72 | 清空表单；默认 `icon='⚡'`、`keywordsStr='分析, 规划, 方案'`、`injection='【已激活专属技能】请按结构化方案输出分步执行指南。'` |
| `startEdit` | L73-83 | 回填，触发词/工具数组用 `join(', ')` 拼成字符串 |
| `handleSave` | L84-112 | 校验 `name` / `injection` 非空 → 逗号/空格切分关键词与工具 → 组装 `AgentSkill`（见下）→ `props.onSaveSkill` |
| `handleImport` | L114-129 | 本地文件导入：**2MB 上限** → `parseSkillText(text, fileName)` → `onSaveSkill`，`window.alert` 反馈 |
| `runDirectorySearch` | L135-153 | 调 `searchAgentSkills(query)`，结果写 `directoryResults` |
| `openDirectoryPreview` | L154-169 | 调 `previewSkillMd(entry)` 取 SKILL.md 正文写入 `previewContent` |
| `confirmDirectoryImport` | L170-195 | 目录条目 + SKILL.md 正文 → `AgentSkill` → `onSaveSkill`（L169 注释：**先预览后导入**；第三方 SKILL.md 只作素材，不自动执行） |

**`handleSave` 落库字段**（L97-107）：

```ts
{
  id: editingSkill?.id || `skill-${Date.now()}`,
  name: name.trim(),
  icon: icon.trim() || '⚡',
  description: description.trim(),
  triggerKeywords: parsedKeywords.length > 0 ? parsedKeywords : ['通用'],
  systemInstructionInjection: injection.trim(),
  systemInstruction: injection.trim(),          // 兼容旧字段，与上面同值
  toolsNeeded: parsedToolsNeeded.length > 0 ? parsedToolsNeeded : undefined,
  isBuiltin: editingSkill?.isBuiltin || false,
}
```

⚠️ 注意：**没有 `tags` 字段**（早前草稿里写的 `tags: parsedTags` 是错的），`systemInstructionInjection` 与 `systemInstruction` 同时写同一个值。

### 10.4 特殊点 / 坑

1. **三层 z 叠放**：主弹窗 `z-50` → 表单纯色层 `z-[80]` → Skill 目录弹窗 `z-[90]`（最高）。
2. 目录导入的搜索是**联网**能力，`onlineSearchAvailable === false` 时按钮应禁用。
3. 关键词/工具用 `/[,，\s]+/` 切分，**中英文逗号与空格都算分隔符**。
4. 本地导入与目录导入都走「拼一个完整 `AgentSkill` 再 `onSaveSkill`」，没有单独的导入标识字段。

---

## 十一、KnowledgeBaseModal — 知识库

**文件**: `components/KnowledgeBaseModal.tsx`（397 行）
**入口**: 侧边栏底部「知识库」格（徽章数为已连接本窗口的条数）

### 11.1 Props 接口 (L20-29)

```ts
interface KnowledgeBaseModalProps {
  isOpen: boolean;
  onClose: () => void;
  knowledgeBase: KnowledgeItem[];
  connectedKnowledgeIds: string[];
  theme: ThemeConfig;
  onSaveKnowledgeItem: (item: KnowledgeItem) => void;
  onDeleteKnowledgeItem: (id: string) => void;
  onToggleConnectToSession: (id: string) => void;
}
```

### 11.2 UI 结构 (L129-397)

```
┌─ 弹窗 (L130-138): fixed inset-0 z-50 居中, **max-w-3xl h-[92vh]**, p-5 sm:p-6
│  ├─ 顶栏 (L139-161)
│  └─ Content Body (L162-397): 二选一
│     ├─[A] 创建/编辑表单 (L164-263) ← isCreating || editingItem
│     │  └─ 全屏层 (L165): fixed inset-0 **z-[80]** / Backdrop(L168) → 关闭表单
│     │     └─ 表单卡 (L176): max-w-2xl max-h-[90vh]
│     │        ├─ 表单头 (L177-194)
│     │        └─ 表单内容 (L195-240): 标题 / 内容 / 标签(逗号分隔)
│     │        └─ 底部 (L241-262): 取消 + 保存
│     └─[B] 列表视图 (L265-397)
│        ├─ 搜索与操作行 (L265-303): 搜索框 + 「导入」UploadCloud(L291) + 「新建」
│        └─ 列表 (L304-396) → 每条卡:
│           ├─ CheckSquare / Square 连接勾选(L330) → onToggleConnectToSession
│           ├─ 标题 + 「已连接本窗口」徽标(emerald)
│           ├─ 标签 #tag pills + 「· N 字」
│           └─ [编辑 Edit2 / 删除 Trash2]
│           └─ 内容预览 (L383)
```

### 11.3 内部状态与函数

| 符号 | 行 | 说明 |
|---|---|---|
| `editingItem` / `isCreating` | L41-42 | 表单态开关 |
| `search` | L43 | 搜索词 |
| 表单 state | L46-48 | `title` / `content` / `tagsStr` |
| `fileInputRef` | L49 | 批量导入 input 引用 |
| `startCreate` | L53-61 | 清空表单，`tagsStr` 默认 `'文档'` |
| `handleFilesUpload` | L62-87 | 批量导入本地文本：**每个文件一条**，`id` = `kb-file-${now}-${i}`，标题取文件名去扩展名，`tags: ['文档', ext]`（扩展名自动入标签，无扩展名则只留「文档」），`updatedAt: now + i` |
| `startEdit` | L88-95 | 回填，`tags` 用 `join(', ')` |
| `handleSave` | L96-117 | 校验 `title` / `content` 非空 → 标签按 `/[,，\s]+/` 切分 → `{ id: 原id 或 kb-${Date.now()}, title, content, tags: 切分结果或 ['文档'], enabled: true, updatedAt: Date.now() }` → `onSaveKnowledgeItem` |
| `filteredItems` | L119-127 | **标题 / 正文 / 标签**三处做包含匹配 |

### 11.4 特殊点 / 坑

1. **容器尺寸与其它库不同**：`max-w-3xl` + `h-[92vh]`（固定高，非 max-h），因为要留出内容预览区。
2. 「是否连接本窗口」在前端是**单条勾选**（`connectedKnowledgeIds`），批量导入的新条目不会自动连接。
3. 导入不写入 `tags` 之外的来源信息，文件名即标题（**不去重、不合并同名**）。

---

## 十二、RegexModal — 正则库

**文件**: `components/RegexModal.tsx`（675 行）
**入口**: **两种形态**（判定见 L71 `hasSessionScope = Boolean(sessionRuleIds && onToggleForSession)`）
- **库形态**：`SettingsHubModal.tsx` L152-162「正则库」→ `onOpenRegex` → `App.tsx` L2484-2487（带 `returnToHubRef.current = true` 的返回链路）→ 实例 L2373-2381，**只传** `isOpen/onClose/rules/theme/onSaveRule/onDeleteRule/onToggleRule`
- **窗口勾选形态**：`WindowFunctionPanel.tsx` L209-224「本窗口正则」→ `App.tsx` L2241 → 实例 L2356-2370，**额外传** `sessionRuleIds` / `onToggleForSession` / `onSetSessionRules`

> ⚠️ **参考版侧边栏底部网格里没有正则格**：`SidebarDrawer.tsx` 声明了 `onOpenRegexModal`(L56/L92) 与 `activeRegexCount`(L67/L100)，但底部 4 列网格（L398-489）**没有渲染正则按钮**，App L2289 的传参永不触发 —— 属死 prop。
> **Native 侧是有这一格的**（T25 定案：删掉参考版的「备份」格，第 7 格改挂正则库）。也就是说这条路是 Native 新增入口，参考版无对应实现可抄，等价于「打开库形态的正则库」。

### 12.1 Props 接口 (L19-35)

```ts
interface RegexModalProps {
  isOpen: boolean;
  onClose: () => void;
  rules: RegexRule[];
  theme: ThemeConfig;
  onSaveRule: (rule: RegexRule) => void;
  onDeleteRule: (id: string) => void;
  onToggleRule: (id: string) => void;
  /**
   * 本窗口当前生效的规则 ID 列表。
   * 只在「本窗口正则」入口传入；侧边栏 / 设置总入口的「正则库」不传这三个 prop，
   * 整份弹窗就退化为纯库（只有新建 / 编辑 / 分组 / 折叠）。
   */
  sessionRuleIds?: string[];
  onToggleForSession?: (id: string) => void;
  onSetSessionRules?: (ids: string[]) => void;
}
```

### 12.2 UI 结构 (L252-674)

```
┌─ Root (L253): fixed inset-0 z-[70] 居中
│  ├─ Backdrop (L255-258) → onClose
│  └─ Dialog (L261-672): max-w-xl rounded-2xl max-h-[92vh]
│     ├─ 顶栏 (L263-290): `.*` 图标块 + 「本窗口正则 / 正则库」(L272) + 「Regex」徽标(L273)
│     │   + 副标题(L277-281) + 关闭 X  ← **本组件没有搜索框**
│     ├─ Content Body (L292-666): 二选一
│     │  ├─[A] 表单弹窗 (L295-473) ← isCreating || editingRule, **嵌在 Dialog 内的 fixed inset-0 z-[80]**
│     │  │  ├─ 遮罩 (L296-299) → closeForm
│     │  │  └─ 表单卡 (L301-472): max-w-xl max-h-[90vh]
│     │  │     ├─ 表单头 (L302-314): 「添加正则规则 / 编辑规则: xxx」+ 关闭 X
│     │  │     └─ <form onSubmit={handleSave}> (L316-471)
│     │  │        ├─ 名称* + 作用范围 select (L320-345)   ← output / input / both
│     │  │        ├─ 分组 input + datalist(regex-group-options) (L347-364)
│     │  │        ├─ Pattern* + Flags 标志 (L366-389)
│     │  │        ├─ Replacement 替换为 (L391-402)
│     │  │        ├─ 规则说明备注 (L404-413)
│     │  │        ├─ 正则测试区「实时沙箱测试效果」(L415-454)
│     │  │        │  「运行测试」按钮(L417-428) / textarea 测试用例(L430-436)
│     │  │        │  / 成功块「匹配命中: N 处 ➔ 替换结果」(L438-447) / 错误块(L449-453)
│     │  │        └─ 底部 (L456-470): 取消(closeForm) / 保存规则(submit)
│     │  └─[B] 规则列表 (L476-665)
│     │     ├─ 工具条 (L477-519)
│     │     │  ├─ 计数文字 (L478-480): 窗口形态「开头的开关即本窗口是否生效」/ 库形态「共 N 条规则」
│     │     │  └─ 按钮组: 「本窗口全选」(L484-492, 仅窗口形态) / 「全不选」(L493-499)
│     │     │     / 「全部折叠·展开」(L502-510) / 「新建规则」(L511-517)
│     │     ├─ 空态 (L521-524): 「暂无正则规则，点击上方「新建规则」添加」
│     │     └─ 分组列表 (L526-663, key=groupName)
│     │        └─ 分组容器 (L530-661)
│     │           ├─ 组标题行 (L532-585): 折叠箭头+组名+组内条数(L533-551)
│     │           │   / 整组开关 ToggleLeft·Right(L554-575) / 「+」组内新建(L577-584)
│     │           └─ 规则卡片 (L587-660, 折叠时不渲染)
│     │              └─ 卡片 (L589-659)
│     │                 ├─ 上排 (L597-648): 行开关(L599) + 规则名(L603-611)
│     │                 │   + 作用域徽标(输出/输入/双向, L612-618) + 描述(L620-624)
│     │                 │   + 右: 编辑 Edit2(L629-635) / 删除 Trash2(L636-646, window.confirm)
│     │                 └─ 下排 pattern→replacement 预览条 (L650-658): /pattern/flags ➔ "replacement" 或 (清除)
│     └─ Footer (L668-671): 「正则规则说明」+ <InfoHint>（支持标准 JS 正则与 $1/$2 分组）
```

### 12.3 内部状态与推导值

| 符号 | 行 | 说明 |
|---|---|---|
| `editingRule` / `isCreating` | L51-52 | 表单态开关 |
| `collapsedGroups` | L53 | 各分组折叠状态 |
| 表单 state | L55-63 | `name` / `pattern` / `flags`(默认 `'g'`) / `replacement` / `scope`(默认 `'output'`) / `description` / `group` |
| 沙箱测试 | L64-68 | `testText`（默认一句带手机号的 AI 回复，L65）/ `testResult` / `testMatches` / `testError` |
| `hasSessionScope` / `rowToggleIsSession` | L71-73 | 形态判定，`rowToggleIsSession` 直接等于它 |
| `knownGroups` | L76-82 | 已有分组名（供 datalist） |
| `groupedRules` | L85-93 | `Map<分组名, RegexRule[]>`；`group` 为空/纯空白归入 `UNGROUPED = '未分组'`(L37) |
| `allCollapsed` | L95-96 | 全折叠按钮状态 |

### 12.4 关键功能函数

| 函数 | 行 | 职责 |
|---|---|---|
| `resetForm` | L100-111 | 清空表单与测试结果（**不重置 `testText`**） |
| `closeForm` | L113-116 | 退出表单态 |
| `startCreate` | L118-123 | 新建态，可带 `defaultGroup`（等于「未分组」时置空）。调用点：工具条 L512、组内 L579 |
| `startEdit` | L125-138 | 回填规则、清空上次测试结果 |
| `handleTestInSandbox` | L140-152 | `testRegexRule(testText, pattern, flags, replacement)`；成功写 result/matchCount，失败写 error 并把 matchCount 置 0 |
| `handleSave` | L154-172 | 校验 `name` / `pattern` → 组装规则 → `onSaveRule` → `closeForm` |
| `toggleGroupCollapse` / `toggleAllGroups` | L174-184 | 单组 / 全部折叠切换 |
| `isGroupOn` | L188-193 | 组状态：窗口形态看是否全部在 `sessionRuleIds`；库形态看是否全部 `enabled` |
| `handleToggleGroup` | L195-210 | 整组开关：窗口形态增删 `sessionRuleIds` 后 `onSetSessionRules`；库形态逐条 `onToggleRule` |
| `renderRowToggle` | L220-250 | 行开关：窗口形态 → `onToggleForSession(id)`；库形态 → `onToggleRule(id)` |

**`handleSave` 落库字段**（L158-168）：`id` = 原 id 或 `regex-${Date.now()}`；`flags: flags || 'g'`；`enabled: editingRule ? editingRule.enabled : true`（编辑保留原值）；`group: group.trim() || undefined`。

### 12.5 特殊点 / 坑

1. **z 层级**：根弹窗 `z-[70]`，表单层 `z-[80]`，两层遮罩都是 `bg-black/80 backdrop-blur-sm`。表单层是**直接嵌在 Dialog 内部**而不是 portal —— 父级 Dialog 带 `animate-in zoom-in-95`（含 transform），`fixed` 会以该祖先为包含块，属潜在坑（`WindowFunctionPanel` 就是走 `createPortal` 规避同类问题）。
2. **同一位置的开关，两种形态语义不同**：窗口形态改「本窗口生效列表」，库形态改全局 `enabled`。
3. **窗口形态里全局禁用的规则**：`disabledInLibrary = rowToggleIsSession && !rule.enabled`(L222) 只把开关降到 0.5 透明度，仍可点、仍会写 `sessionRuleIds`，但因全局 `enabled=false` 实际不参与清洗（「可勾但不生效」）。
4. **不做正则语法预校验**：只校验非空（`required` + L156），非法 pattern 要到「运行测试」或实际清洗时才暴露。
5. **删除用原生 `window.confirm`**(L638)，不是自定义弹窗。
6. **`testText` 不被 `resetForm` 重置**：切换新建/编辑/关闭后样例文本保留上次输入。
7. 本文件**没有导入/粘贴解析入口**（既无 JSON 粘贴，也无文件导入）。

---

## 十三、App.tsx 中的接线关系

**文件**: `App.tsx`

### 13.1 状态声明 (L144-176)

```ts
const [isSidebarOpen, setIsSidebarOpen] = useState(false);     // L144
const [isSettingsHubOpen, setIsSettingsHubOpen] = useState(false); // L159
const [isTimelineLibraryOpen, setIsTimelineLibraryOpen] = useState(false); // L162
const [isRegexLibraryOpen, setIsRegexLibraryOpen] = useState(false); // L152
const [isWorldLibraryOpen, setIsWorldLibraryOpen] = useState(false); // L170
// ... 以及其他所有弹窗 open 状态
```

### 13.2 Header → 打开侧边栏 (L2172)

```tsx
<Header onOpenSidebar={() => setIsSidebarOpen(true)} ... />
```

### 13.3 SidebarDrawer 实例化 (L2266-2297)

```tsx
<SidebarDrawer
  isOpen={isSidebarOpen}
  onClose={() => setIsSidebarOpen(false)}
  // ... 传入 sessions, groups, agents, theme 等数据
  onOpenSettingsHub={() => setIsSettingsHubOpen(true)}   // L2283
  onOpenMcpModal={() => setIsMcpModalOpen(true)}         // L2291
  onOpenSkillModal={() => setIsSkillLibraryOpen(true)}   // L2292
  onOpenKnowledgeModal={() => setIsKnowledgeModalOpen(true)} // L2288
  onOpenWorldLibrary={() => setIsWorldLibraryOpen(true)} // L2293
  onOpenTimelineModal={() => setIsTimelineLibraryOpen(true)} // L2285
  onOpenSettingsModal={() => setIsProviderModalOpen(true)}   // L2282 (API设置)
  onOpenRegexModal={() => setIsRegexLibraryOpen(true)}       // L2289
  onOpenThemeModal={() => setIsThemeModalOpen(true)}         // L2290
  onOpenAgentModal={() => setIsAgentModalOpen(true)}         // L2286
  onOpenPromptModal={() => setIsDualBoxPromptModalOpen(true)} // L2287
  // 徽章计数
  activeMcpCount={activeMcpServers.length}                   // L2294
  connectedKnowledgeCount={connectedKnowledgeItems.length}   // L2295
  activeRegexCount={regexRules.filter(r => r.enabled).length} // L2296
/>
```

### 13.4 WindowFunctionPanel 嵌入 ChatInput (ChatInput.tsx L405-416)

```tsx
<WindowFunctionPanel
  isOpen={isPanelOpen}                           // ChatInput 本地 state
  onToggle={() => setIsPanelOpen(!isPanelOpen)}
  onOpenDualBoxPrompt={onOpenDualBoxPromptModal}
  onOpenSearch={onOpenSearchModal}
  onOpenDocument={onOpenDocumentModal}
  onTriggerRoleAnalysis={onTriggerRoleAnalysis}
  onOpenWindowApiParams={onOpenWindowApiParams}
  onOpenSessionText={onOpenSessionText}
  onOpenSummary={onOpenSummary}
  onOpenWindowRegex={onOpenWindowRegex}
/>
```

### 13.5 导入会话后关闭侧边栏 (L785)

```ts
setIsSidebarOpen(false);
```

---

## 十四、功能区分层架构

```
┌─────────────────────────────────────────────────┐
│                    App.tsx                       │
│  (所有 isXxxOpen 状态 + 弹窗实例化)              │
│                                                  │
│  ┌─ Header ─────────────────────────────┐       │
│  │  ☰ → setIsSidebarOpen(true)          │       │
│  └──────────────────────────────────────┘       │
│                                                  │
│  ┌─ SidebarDrawer ──────────────────────┐       │
│  │  全局功能区（底部4列网格）             │       │
│  │  ├─ MCP / Skill / 知识库 / 世界观     │       │
│  │  ├─ 时间线 / API / 设置              │       │
│  │  会话管理（分组折叠 + 搜索 + CRUD）   │       │
│  └──────────────────────────────────────┘       │
│       ↓ 设置按钮                                │
│  ┌─ SettingsHubModal ───────────────────┐       │
│  │  Agent管理/偏好/文本/主题/提示词/正则/备份│    │
│  └──────────────────────────────────────┘       │
│                                                  │
│  ┌─ ChatInput ──────────────────────────┐       │
│  │  ┌─ WindowFunctionPanel ──────────┐  │       │
│  │  │  窗口级功能（上拉面板）          │  │       │
│  │  │  前文总结/双框提示词/搜索/       │  │       │
│  │  │  世界观/角色分析/API参数/        │  │       │
│  │  │  导出删除/本窗口正则            │  │       │
│  │  └────────────────────────────────┘  │       │
│  └──────────────────────────────────────┘       │
└─────────────────────────────────────────────────┘
```

### 全局级 vs 窗口级功能分界

| 层级 | 入口 | 功能 |
|---|---|---|
| **全局级** | SidebarDrawer 底部网格 | MCP、Skill、知识库、世界观库、时间线库、API设置、设置总入口 |
| **全局级** | SettingsHubModal | Agent管理、偏好、文本设置、主题、提示词库、正则库、备份 |
| **窗口级** | WindowFunctionPanel | 前文总结、双框提示词、全文搜索、本窗口世界观、角色分析、窗口API参数、导出删除、本窗口正则 |

---

## 十五、关键 CSS / 样式模式

- **侧边栏宽度**: `w-[84%] max-w-[320px]`，左侧滑入 `translate-x-0 / -translate-x-full`
- **Z层级**: Backdrop z-40 → Sidebar z-50 → 创建分组弹窗 z-[80] → 设置 z-[60]
- **WindowFunctionPanel**: `createPortal` 到 `document.body`，避免 ChatInput 的 `backdrop-blur-xl` 形成包含块
- **Safe area**: 侧边栏和顶栏都处理了 `env(safe-area-inset-top/bottom)` 刘海和底部横杆
- **主题变量**: 使用 CSS 变量 `var(--surface-elevated)`, `var(--m1-500)`, `var(--text-primary)` 等
- **图标库**: lucide-react
