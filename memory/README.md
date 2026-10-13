# 项目记忆（memory/）

> 存放"改代码时需要快速查"的资料：函数在哪、进度到哪、坑在哪。
> **不复制** `docs/` 里的方案正文；这里只记索引与定位信息。

## 索引

| 文件 | 用途 | 什么时候看 |
|---|---|---|
| [`function-map.md`](function-map.md) | **函数地图**：各大功能的函数 → `文件:行`，含 Kotlin 迁移目标列 | 要改某个功能，先在这里定位 |
| [`theme-color-map.md`](theme-color-map.md) | **主题配色函数地图**：哪个函数/对象管哪个配色、新代码该用哪个 API、配色的坑 | 改任何界面颜色之前先看 |
| [`ui-theme-saturation-system.md`](ui-theme-saturation-system.md) | **主题饱和度系统**：白天/黑夜 × 饱和度分级（20 套内置）、`SatLevel` 选表机制、气泡档位、待办 | 改主题/加主题/调气泡色之前先看 |
| [`ui-theme-surfaces.md`](ui-theme-surfaces.md) | 面板分层底色（底板 / 卡片 / 凹槽 / 描边）的取值口径 | 给弹窗/面板上底色之前先看 |
| [`ui-replication-requirements.md`](ui-replication-requirements.md) | UI 复刻的硬性要求（图标替代、结构对齐、功能一致） | 做 UI 复刻任务之前先看 |
| [`ui-typography-standard.md`](ui-typography-standard.md) | **UI 排版标准**：文字三档（14/16/12，随界面字体大小缩放）、注释收成 InfoHint、内外框垂直内边距 2dp 链 | 改任何界面文字 / 间距之前先看 |
| [`kotlin-migration.md`](kotlin-migration.md) | 迁移前置准备清单一览 | 想知道"还差什么才能开工" |
| [`gotchas.md`](gotchas.md) | 迁移踩坑记录（迁移过程中追加） | 遇到"这地方为什么这么写" |

## 与其它文档的分工

| 想知道 | 去看 |
|---|---|
| 怎么做、表结构、排期 | `docs/KOTLIN_MIGRATION_PLAN.md` |
| 不许动什么（红线） | `docs/MIGRATION_CONSTRAINTS.md` |
| 开工前要采集什么 | `docs/BASELINE_COLLECTION.md` |
| 目录放哪、边界规则 | `docs/FILE_LAYOUT.md` |
| 现在做到哪一步、下一步做什么 | `../WORKPLAN.md` |
| 某个任务当时**改了什么、为什么、要注意什么** | `../tasks/T<号>-*.md`（每个任务一份） |
| 某功能对应哪个函数 | `function-map.md`（本目录） |

## 维护约定

1. 每完成一个任务：写 `../tasks/` 下的任务文档（改动/心得/要点）→ 更新 `../WORKPLAN.md` §一 完成记录与 §二 索引。**WORKPLAN 是唯一的进度权威**。
2. 函数地图在**迁移过程中同步补 Kotlin 目标列**，不要等全做完再补。
3. 踩到一次坑就往 `gotchas.md` 加一条，格式：症状 → 原因 → 对策。
4. 本目录不复制 `docs/` 正文，也不替代 `tasks/` 的任务文档，避免分叉。
5. 与现有 React 仓的 `.claude\projects\e--WatchWorld\memory\MEMORY.md` 不互相复制。

## 三者分工（别搞混）

```
docs/     怎么做、约束、采集要求        ← 方案，基本不动
memory/   查资料：函数在哪、坑在哪      ← 按需追加
WORKPLAN  做到哪、下一步做什么          ← 唯一进度权威
tasks/    每个任务当时怎么做的、为什么  ← 每任务一份，未来修改的助手
```
## UI补全
本项目以"E:\WatchWorld\WatchWorld"为源代码重建，UI 
任务时，不需要看workplan。可以查看"E:\WatchWorld\WatchWorld-Native\WatchWorld_UI_CodeMap.md"与"E:\WatchWorld\WatchWorld-Native\memory\ui-replication-requirements.md"。
ui以复刻为模板，复刻要求看文件，文件提出的要求优先，其它的能抄就抄，格式不兼容的，比如图标（参考侧边栏图标），就修改，达到效果相似。接入功能时，注意源文件的接入相关，做到功能一致。
好的，现在把 UI 复刻流程记住：

## ⚙️ UI 复刻流程（记住！）

### 📂 目录结构
- **源文件**：`E:\WatchWorld\WatchWorld` ← React 版
- **目标文件**：`E:\WatchWorld\WatchWorld-Native` ← Kotlin Compose 版
- **参考**：`WatchWorld_UI_CodeMap.md`
- **要求**：`memory/ui-replication-requirements.md`

### 🔄 复刻步骤
1. **定位**：找对应组件在 `WatchWorld` 中
2. **对照**：参考结构和样式
3. **实现**：在对应路径写 Compose 版
4. **调整**：解决格式差异

### 🔧 关键技巧
- 图标用 `TablerIcons` 系列
- 按钮参考 `InputFeatureButton`
- 状态传递要完整

记住了！现在做 UI 就按这个流程走。