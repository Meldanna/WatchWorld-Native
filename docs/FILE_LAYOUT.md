# 目录规划（FILE_LAYOUT.md）

> 拍板日期：2026-10-06
> 本文件记录「新文件放哪里」的决策与理由，避免迁移过程中反复搬家。
> 上位文档：`KOTLIN_MIGRATION_PLAN.md` §3 / `BASELINE_COLLECTION.md` §A.1 §C.4 §E.3。

---

## 1. 已核实的工作区事实

```
E:\WatchWorld\                  ← 工作区根，【不是】git 仓库
├── android 工作指南\            ← 三份文档源头（在 git 之外）
├── WatchWorld\                 ← git 仓库根 = 现有 React / Capacitor 版
│   ├── .git\  .gitignore
│   ├── src\  server.ts  worker\
│   ├── android\                ← 现有 Capacitor 的 Android 工程
│   └── .claude\projects\e--WatchWorld\memory\   ← 仓库内记忆索引（MEMORY.md 等）
└── WatchWorld-Native\          ← 本仓（新建独立 git 仓，见 §3）
```

⚠️ 唯一的既有 git 仓库是 `E:\WatchWorld\WatchWorld`。`android 工作指南\` 与该仓无关。

---

## 2. 三个决策（2026-10-06 拍板）

| # | 决策 | 选择 | 理由 |
|---|---|---|---|
| 1 | KMP 原生工程位置 | **方案 A：同级独立仓** `E:\WatchWorld\WatchWorld-Native\` | 不污染现有仓；Gradle 与 Vite 不同仓，避免 IDE 索引互相干扰；现有 `android\`（Capacitor）语义不被混淆 |
| 2 | 基线 fixtures 位置 | **放本仓** `fixtures/` | fixture 是原生版长期回归测试的输入（§8.1），`commonTest` 相对路径直读，不依赖跨仓 |
| 3 | 三份文档 + memory | **复制进本仓** `docs/`、`memory/` | 源头仍留 `android 工作指南\`；本仓留副本便于随代码演进 |

被否决的选项：
- **方案 B（嵌进现有仓 `WatchWorld\native\`）**：需要扩 `WatchWorld\.gitignore`（`.gradle/`、`local.properties`、`*.iml`、`.kotlin/`，其中 `local.properties` 含本机 SDK 路径绝不能提交）；且 `android\` 与新 `androidApp\` 并存易混。
- **方案 C（fixture 留 `WatchWorld\fixtures\`，跨仓相对路径读）**：clone 单个仓即断，不可靠。

> 📌 与 `BASELINE_COLLECTION.md` §A.1 / §C.4 的偏差：原文写死 `E:\WatchWorld\WatchWorld\fixtures\`，
> 本仓改为**放原生仓**。采集时把上游 curl 输出直接写到本仓 `fixtures\` 即可，不影响采集方法。

---

## 3. 目标目录结构

```
WatchWorld-Native\
├── README.md
├── .gitignore
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── local.properties            ← 不提交（含本机 SDK 路径）
├── gradle\libs.versions.toml   ← 版本集中管理（PLAN §2.1）
│
├── docs\                       ← 三份上位文档副本 + 本文件
│   ├── KOTLIN_MIGRATION_PLAN.md
│   ├── MIGRATION_CONSTRAINTS.md
│   ├── BASELINE_COLLECTION.md
│   └── FILE_LAYOUT.md
│
├── memory\
│   └── kotlin-migration.md     ← 迁移进度记忆（对应 §E.3）
│
├── fixtures\                   ← ★ 基线采集资产，纳入 git
│   ├── sse\                    ← §A：三家 Provider 真实报文 + 存档样本
│   ├── theme-baseline\         ← §C：主题截图 + 色值 README
│   ├── tools\                  ← 采集与校验脚本（collect / check / generate / redact，见 2026-10-06 变更）
│   └── baseline-latency.md     ← §B：首 token 延迟基线
│
├── shared\                     ← KMP 共享模块
│   ├── build.gradle.kts
│   └── src\
│       ├── commonMain\kotlin\com\watchworld\
│       │   ├── model\          ← §4：24 个 @Serializable data class
│       │   ├── data\           ← §5：db / repository / migration
│       │   ├── net\            ← §6：流式 / Provider / MCP / WebDAV / 搜索
│       │   ├── domain\         ← 纯业务逻辑（无 UI 无 IO）
│       │   ├── platform\       ← expect 声明
│       │   └── ui\             ← Compose UI（双端共享）
│       ├── commonTest\kotlin\com\watchworld\   ← 单测；fixture 路径 ../../../fixtures
│       ├── androidMain\kotlin\com\watchworld\
│       └── desktopMain\kotlin\com\watchworld\
│
├── desktopApp\                 ← jpackage → .exe
└── androidApp\                 ← assembleRelease → APK
```

**只读参照**：迁移期 `E:\WatchWorld\WatchWorld\` 冻结（见 `MIGRATION_CONSTRAINTS.md` §7.3）。
新功能一律不加到 React 版。

> 2026-10-06 补充：`fixtures\tools\` 是**采集/校验脚本**目录（§4 规则 2：「采集脚本一律落在本仓 fixtures 内」）。
> 其中 `generate-minimal-archive.ts` 需**借 React 仓的 tsx 运行**（用它的 `Storage.exportAllData()` 保证形状同源），
> 但脚本本体属于本仓、对 React 仓**零改动**。

---

## 4. 边界规则（防止越界写文件）

1. 本仓任何文件都不写到 `E:\WatchWorld\WatchWorld\`（现有仓冻结，仅作规格书与视觉对照）。
2. 采集脚本/临时 curl 输出一律落在本仓 `fixtures\` 内，不要把 key、原始报文丢到工作区根。
3. `E:\WatchWorld\`（工作区根）不新增散落文件；根下已有的 `cap-sync-validation.log` / `package-lock.json` 不动。
4. `local.properties`、`.gradle\`、`build\` 一律不提交（见 `.gitignore`）。

---

## 5. 与既有记忆体系的关系

现有仓库内记忆索引在 `WatchWorld\.claude\projects\e--WatchWorld\memory\MEMORY.md`。
本仓的迁移进度记录在 `memory\kotlin-migration.md`（`BASELINE_COLLECTION.md` §E.3 提到的那个）。
**两份不要互相复制**，避免长期分叉：本仓记迁移进度，索引侧最多加一行指向本文件。
