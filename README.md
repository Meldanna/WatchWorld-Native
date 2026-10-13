# WatchWorld-Native

观界 WatchWorld 的 Kotlin + Compose Multiplatform 原生版。
目标产物：**Android APK + Windows 桌面 `.exe`**（放弃 WebView / 浏览器版）。

## 从这里开始

- **要做的事 / 做到哪了** → [`WORKPLAN.md`](WORKPLAN.md)（项目要求 + 完成记录 + 任务清单）
- **改代码先查函数在哪** → [`memory/function-map.md`](memory/function-map.md)
- **某任务当时改了什么、为什么** → [`tasks/`](tasks/)（每个任务一份文档）

迁移的动机、方案与约束见 `docs/`：

| 文档 | 内容 |
|---|---|
| [`docs/KOTLIN_MIGRATION_PLAN.md`](docs/KOTLIN_MIGRATION_PLAN.md) | 技术方案（怎么做） |
| [`docs/MIGRATION_CONSTRAINTS.md`](docs/MIGRATION_CONSTRAINTS.md) | 约束清单（不许动什么）— **冲突时以此为准** |
| [`docs/BASELINE_COLLECTION.md`](docs/BASELINE_COLLECTION.md) | 迁移前必须采集的基线资产 |
| [`docs/FILE_LAYOUT.md`](docs/FILE_LAYOUT.md) | 目录规划与边界规则 |

## 状态

P0 前置准备（基线采集）尚未开始。**任务清单与进度见 [`WORKPLAN.md`](WORKPLAN.md)**。

## 与现有 React 版的关系

`../WatchWorld/` 是现有 React / Capacitor 版，迁移期间**冻结**：
只作规格书（TS 代码是规格，不是参考实现）与视觉对照，不加新功能。
