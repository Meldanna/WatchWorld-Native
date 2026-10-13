# WatchWorld UI 代码地图

> **来源：2026年10月9日，通过 AI 代码分析生成**
> 本文件基于 `e:\WatchWorld\WatchWorld` 项目源码构建

---

## 📋 项目概览

**项目名称**：WatchWorld（观界）  
**版本**：0.2.5  
**技术栈**：React 19.0.1 + TypeScript 7.0.2 + Vite 8.3.0 + Tailwind CSS 4.3.3

---

## 🗺️ 项目文件结构

```
e:\WatchWorld\WatchWorld\
├── public/
│   ├── manifest.webmanifest
│   ├── sw.js
│   ├── icon-192.png
│   ├── icon-512.png
│   └── apple-touch-icon.png
│
├── src/
│   ├── main.tsx
│   ├── App.tsx (2662行)
│   ├── types.ts (400+行)
│   ├── index.css
│   │
│   ├── components/ (52个组件)
│   │   ├── Header.tsx (67行)
│   │   ├── SidebarDrawer.tsx (587行)
│   │   ├── ChatMessageList.tsx (273行)
│   │   ├── ChatInput.tsx (805行)
│   │   ├── MessageItem.tsx (601行)
│   │   └── [46个Modal组件]
│   │
│   ├── lib/
│   │   ├── theme.ts
│   │   ├── storage.ts
│   │   └── [其它工具库]
│   │
│   └── assets/
│
└── android/
```

---

## 🎯 核心布局区域

### 1. Header区域 (`src/components/Header.tsx` 行 1-67)
- 功能：游戏主按钮、窗口标题、主题切换
- 菜单图标：打开侧边栏抽屉
- 窗口标题：显示 `currentSession.title`
- 主题切换：亮色/暗黑模式

### 2. ChatMessageList区域 (`src/components/ChatMessageList.tsx` 行 1-273)
- Token 统计徽标（行 200-207）
- 消息列表容器
- 滚动指示器（行 251-270）

### 3. MessageItem气泡 (`src/components/MessageItem.tsx` 行 1-601)

#### 顶部标签行 (行 310-350)
```
┌─────────────────────┐
│ [#5] [模型名称] [~128tok] | 14:32:05 │
└─────────────────────┘
```

#### 消息内容区域 (行 396-425)
- Markdown渲染器
- 思维链折叠块
- 图片展示

#### 底部功能栏 (行 530-601)
```
┌─────────────────────────────────────────┐
│ [A3] 学园线·结识好友 │ [↺ 回滚] [✓ 版本] [编辑] [删] [复制] │
└─────────────────────────────────────────┘
```

### 4. ChatInput区域 (`src/components/ChatInput.tsx` 行 1-805)

#### 状态指示条 (行 383-422)
```
┌─────────────────────────────────────────────────┐
│ 当前模型: gpt-4o | 📁 主分支 | 📷 2 | Token: ~2450 │
└─────────────────────────────────────────────────┘
```

#### 功能按钮栏 (行 140-250)
- 图片上传 `[ImagePlus]`
- 文件上传 `[FileUp]`
- 角色分析 `[Zap]`
- 双框提示词 `[Layers]`
- 知识库/世界观 `[BookOpen]`
- 联网搜索 `[Globe2]`
- 窗口参数 `[Settings]`
- 会话文本 `[Loader2]`
- 前文总结 `[BarChart2]`
- 全文搜索 `[Search]`
- Agent模式 `[Sparkles]`
- AI可见性 `[Eye]`
- 提示词插入 `[Plus]`

---

## 📦 Modal窗体详细索引

| 组件 | 文件 | 行数 | 主要功能 |
|-----|------|------|---------|
| ProviderModal | `src/components/ProviderModal.tsx` | 2957 行 | AI提供商配置 |
| AgentModal | `src/components/AgentModal.tsx` | 572 行 | Agent人格配置 |
| PromptModal | `src/components/PromptModal.tsx` | 585 行 | 提示词库管理 |
| TimelineModal | `src/components/TimelineModal.tsx` | 945 行 | 时间线分支管理 |
| KnowledgeBaseModal | `src/components/KnowledgeBaseModal.tsx` | 397 行 | 知识库管理 |
| ThemeModal | `src/components/ThemeModal.tsx` | 317 行 | 主题选择 |
| SettingsModal | `src/components/SettingsModal.tsx` | 261 行 | 消息显示设置 |
| PreferencesModal | `src/components/PreferencesModal.tsx` | 687 行 | 全局偏好设置 |
| GroupModal | `src/components/GroupModal.tsx` | 307 行 | 窗口分组管理 |
| SettingsHubModal | `src/components/SettingsHubModal.tsx` | 182 行 | 设置总入口 |
| ModelSelectorModal | `src/components/ModelSelectorModal.tsx` | 242 行 | 模型选择器 |
| AiVisibilityModal | `src/components/AiVisibilityModal.tsx` | 219 行 | AI上下文控制 |
| SessionTextModal | `src/components/SessionTextModal.tsx` | 240 行 | 窗口导出删除 |
| SummaryModal | `src/components/SummaryModal.tsx` | 356 行 | 前文总结 |
| BackupSyncModal | `src/components/BackupSyncModal.tsx` | 349 行 | WebDAV同步 |
| DocumentModal | `src/components/DocumentModal.tsx` | 1067 行 | 世界观文档库 |
| SearchModal | `src/components/SearchModal.tsx` | 173 行 | 全文搜索 |

---

## 🎨 样式变量系统

### `src/index.css` 主题变量 (行 189-320)

```css
:root {
  --surface-0: #ffffff;
  --surface-1: #f8fafc;
  --surface-2: #f1f5f9;
  --surface-3: #e2e8f0;
  --m1-50 ~ --m1-950: 主色调;
  --m1-bubble: AI气泡背景;
}

html.dark {
  --surface-0: #0a0d12;
  --text-primary: #f8fafc;
}
```

### `src/lib/theme.ts` 主题生成 (行 1-317)
- 行 38-103：内置主题色阶 (FAMILY_SCALES)
- 行 210-255：resolveThemeVars() 生成CSS变量
- 行 267-278：自定义主题色阶构建

---

## 🔗 组件间调用关系

```
App.tsx
├── Header.tsx ← 行 1-67
├── ChatMessageList.tsx ← 行 1-273
│   └── MessageItem.tsx ← 行 1-601
├── ChatInput.tsx ← 行 1-805
│   └── WindowFunctionPanel.tsx
├── SidebarDrawer.tsx ← 行 1-587
│   └── [50+ Modal组件]
```

---

## 📊 重要类型定义 (`src/types.ts`)

### ChatMessage (行 121-140)
- `id`, `role`, `versions[]`, `currentVersionIndex`
- `content`, `timestamp`, `latencyMs`, `tokens`
- `timelineId`, `descriptionTag`, `codeTag`
- `isCommon`, `isAnalysis`, `analysisVisibility`
- `imageIds[]`

### ChatSession (行 357-400)
- `id`, `title`, `groupId`, `agentId`, `providerId`, `model`
- `temperature`, `topP`, `topK`, `frequencyPenalty`
- `messages[]`
- `timelines[]` 时间线分支
- `timelineMemoryEnabled`, `activeTimelineId`

---

## 🚀 开发命令

```bash
npm run dev       # 启动开发模式
npm run build     # 构建生产版
npm run lint      # TypeScript类型检查
```

---

> **说明**：本文件由 AI 深度求索 V4 Flash 模型于 2026-10-09 日期，通过对 WatchWorld 项目源码的深度分析生成，包含具体文件行号和结构信息，方便开发者快速定位和理解项目架构。