# T07 · Room 存储层

| | |
|---|---|
| 任务号 | T07 |
| 日期 | 2026-10-07 |
| 状态 | ✅ 完工（三处已知缺口见「遗留问题」） |
| 阶段 | 阶段 3 · 让数据落地 |
| 上位清单 | [`WORKPLAN.md`](../WORKPLAN.md) §二 T07 |

## 目标与可见效果

- **目标**：Room 表 + DAO + Repository，把 T06 的「v5 JSON ↔ 内存」接到 SQLite 上。
- **可见效果**：**会话重启后还在**。
- **验收标准**：`messages` 从 `sessions` 拆出；写操作走 `suspend`，同事务更新 `updatedAt`；UI 不直接碰 DAO。

## 改动

| 文件 | 改动 |
|---|---|
| `gradle/libs.versions.toml` | **新增** Room 2.8.5 / KSP 2.3.12 / sqlite-bundled 2.7.1（探针工程实测过这一组合与 Kotlin 2.4.20 兼容）。 |
| `shared/build.gradle.kts` | 接入 `ksp` + `androidx.room` 插件；commonMain 加 `room-runtime` + coroutines，desktopMain 加 `sqlite-bundled`；`add("kspDesktop", room-compiler)`；`room { schemaDirectory("$projectDir/schemas") }`。 |
| `shared/schemas/com.watchworld.data.db.WwDatabase/1.json` | **新增**（Room 自动导出，**要提交**）。17 张表的结构契约，将来写 Migration 的依据。 |
| `shared/.../data/StorageKeys.kt` | **新增**。`storage.ts:38-60` 的 21 个键原样保留，并给出每个键在原生版的落点（表 or `kv` 行键）——**唯一权威表**。 |
| `shared/.../data/db/ConfigEntities.kt` + `ConfigDaos.kt` | **新增**。12 张配置类表（providers / agents / prompts / regexRules / knowledgeBase / mcpServers / skills / worldGroups / worldDocuments / customThemes / modelFavorites / groups）及 DAO。 |
| `shared/.../data/db/SessionEntities.kt` + `SessionDaos.kt` | **新增**。`sessions`（会话配置 blob）/ `messages` / `timelines` / `summaries` 四表 + `kv` 杂项表；子表都带 `sortIndex` 与 `ON DELETE CASCADE`。 |
| `shared/.../data/db/Mappers.kt` | **新增**。模型 ↔ 实体的双向映射；`timelines`/`summaries` 的 null 与 `[]` 由 blob 里的两个布尔决定。 |
| `shared/.../data/db/WwConverters.kt` | **新增**。JSON 列（可空/非空各一份）+ 7 个枚举的手写 `when` 转换。 |
| `shared/.../data/db/WwDatabase.kt` | **新增**。`@Database(version = 1)`，`open(databasePath)`（**文件全路径**）与 `openInMemory()`（仅测试）。 |
| `shared/.../data/db/WwTransaction.kt` | **新增**。`WwDatabase.withTransaction {}` = `useWriterConnection { it.immediateTransaction { } }`。 |
| `shared/.../data/repository/SessionRepository.kt` | **新增**。会话树唯一读写入口：每个写方法自己盖 `updatedAt` 并在同一事务里写四张表；`replaceAll` 给导入用。 |
| `shared/.../data/repository/ConfigRepository.kt` | **新增**。12 个集合的 `xxx()` / `setXxx()`（全量替换，对齐 `Storage.getXxx/setXxx`）+ `transaction {}`。 |
| `shared/.../data/repository/SettingsRepository.kt` | **新增**。`kv` 表上的单体设置：主题、明暗、显示设置、应用偏好、WebDAV、当前会话/Provider、草稿；读接口不抛错、没存过给默认值。 |
| `shared/.../data/repository/ArchiveStore.kt` | **新增**。v5 存档 ↔ SQLite 的桥：`importJson`（整段一个事务；按 JS 真值规则逐分类判断）/ `snapshot` / `exportJson`。格式规则全部复用 T06 的 `ArchiveRepository`。 |
| `shared/.../platform/PlatformStorage.kt` + `desktopMain/.../PlatformStorage.desktop.kt` | **新增** expect/actual：`appDataDir()`（`%APPDATA%\WatchWorld`）、`createSqliteDriver()`（`BundledSQLiteDriver`）、`sqliteQueryDispatcher`（`Dispatchers.IO`）。 |
| `shared/src/desktopTest/.../data/StorageRoundTripTest.kt` | **新增 10 个用例**：两会话同名消息/总结 id 互不覆盖、三份真实存档经库往返、库→导出→再解析、**文件库关库重开**、部分导入/空数组清空、坏存档不动库、写操作盖 `updatedAt`、子表 null/[] 两态、顺序保持、删除连带子表。 |

## 心得

### 1. 时间线主键只能是 `(sessionId, id)` —— 真实样本抓出来的

最初 `TimelineEntity` 用 `@PrimaryKey val id`。合成用例全过，但 `realArchivesSurviveDatabaseRoundTrip`
与 `fileBackedDatabaseSurvivesCloseAndReopen` 在 rich 样本上失败：第一个会话的 `timelines` 读回来是 `[]`。

原因：**时间线 id 只在会话内唯一**。rich 存档里两个会话都有 `timeline-main`，`writeSession` 逐个 `@Upsert`，
第二个会话把第一个会话的同名行**改写**成自己的 `sessionId`，于是第一个会话「丢」了时间线 —— 不报错。
修法是复合主键 `primaryKeys = ["sessionId", "id"]`。因为库还在 `version = 1`、从未发布，直接改表无需 Migration。

> 普适教训：从 Web 版继承的 id **只保证在它原来的容器里唯一**。拆表前要逐个确认「这个 id 的唯一范围是全局还是父对象内」。
> 消息与总结 id 同样只被当作会话内唯一，三张子表一并改为复合主键。

### 2. Room KMP 没有 `withTransaction`

`androidx.room.withTransaction` 在 room-ktx 里、**只有 Android**，commonMain 解析不到。KMP 写法是
`useWriterConnection { it.immediateTransaction { ... } }`，块内的 DAO 调用复用同一条写连接。封装在
`WwTransaction.kt`，上层只写 `db.withTransaction {}`，与 Android 习惯一致。

### 3. 平台层用顶层 `expect fun` / `expect val`，不用 `expect object`

expect/actual 类在 Kotlin 2.4 仍是 Beta，会出编译警告，破坏零警告要求。与 T03 的 `platformEnv`、T06 的 `currentTimeMillis` 同一写法。

### 4. 导入要回到原始 JSON 树判断「分类在不在」

`ArchiveV5` 解码后「缺字段」和「空数组」都成了 `emptyList()`。而 Web 版 `importAllData` 是 `if (data.xxx)`：
缺字段保持原样、`[]`（JS 真值）清空。所以 `ArchiveStore` 再看一次原始 `JsonObject`，按 JS 真值规则决定哪些分类要写。

### 5. `updatedAt` 由 Repository 盖，不由调用方传

Web 版在 `App.tsx` 里手写 `updatedAt: Date.now()`。拆成两步就一定有人漏一步（T06 `version` 消失同类）。
`SessionRepository` 的每个写方法自己盖戳、在同一事务里写、并返回盖好的值。

### 6. 「重启后还在」只能用文件库证明

内存库关掉就没了，证明不了可见效果。`fileBackedDatabaseSurvivesCloseAndReopen` 用临时目录里的真实文件，
写入 → `close()` → 重开 → 比对，并断言库文件确实落在给定路径（而不是当前工作目录）。

## 要点

- **`Room.databaseBuilder(name = …)` 传的是文件全路径**，不是库名。传裸名会在当前工作目录建文件。用 `appDataDir()` + `WwDatabase.FILE_NAME` 拼。
- **KSP 配置名是 `kspDesktop`**（跟 KMP 目标名 `desktop` 走）。写成 `kspJvm` / `kspCommonMainMetadata` 会静默不生成代码。
- **`shared/schemas/` 要提交**。下一次改表起必须 bump `version` + 写 `Migration` + `MigrationTestHelper` 测试；**禁止** `fallbackToDestructiveMigration`。
- **`messages` / `timelines` / `summaries` 三张子表的主键都是 `(sessionId, id)`**，见「心得 1」。DAO 里没有只按 `id` 定位子表行的查询（全部按 `sessionId` 或整表）；将来新增按行更新/删除的查询必须同时带 `sessionId`。`sameChildIdsInDifferentSessionsDoNotCollide` 守住这条。
- **子表顺序只靠 `sortIndex`**（SQLite 不保序，消息又没有可靠的排序字段）。已有会话再保存保持原位，新会话追加到末尾。
- **`timelines` / `summaries` 的 null 与 `[]` 是两态**，由 `SessionConfigBlob` 的 `timelinesNull` / `summariesNull` 承载，不由子表行数推断。
- **`ConfigRepository.setXxx` 是全量替换**：会删掉库里不在列表中的条目。读改写要包在 `transaction {}` 里，否则并发写会互吃。
- **`kv` 值的存法**：标量原样字符串，对象走 `WwJson`。`kv` 格式不对外（不进存档、不进同步）。
- **存储键前缀 `omnichat_` / `guanjie_` 混用是历史遗留**，一个都不许改（CONSTRAINTS §6）。
- **`ArchiveStore.importJson` 整段一个事务**，中途失败整体回滚 —— 这是相对 Web 版唯一有意的行为增强。
- **不进存档的东西**（`customThemes`、`modelFavorites`、当前会话/Provider、草稿）导入导出都不碰，与 `exportAllData` 的 17 个键一致。
- **平台层三个新 expect**：`appDataDir` / `createSqliteDriver` / `sqliteQueryDispatcher`。T22 加 Android 目标时必须补 `actual`，否则编译不过。
- **`ChatStreamClientTest` 的帧间隔用例在高负载下偶发失败**（本次一次观测到 `[295, 328, 314, 312, 488, 123]`，紧接着重跑通过）。它是计时测试，被并行编译/KSP 抢 CPU 时会抖；不是本任务引入的回归。

## 验收结果

- [x] `messages` / `timelines` / `summaries` 从 `sessions` 拆出，四表同事务写入。
- [x] 写操作全部 `suspend`，同事务盖 `updatedAt`（`writesStampUpdatedAt`）。
- [x] DAO 只在 `data/` 内出现，对外只暴露四个 Repository / `ArchiveStore`。
- [x] **会话重启后还在**：文件库关库重开，会话树与 31 条消息完全一致（`fileBackedDatabaseSurvivesCloseAndReopen`）。
- [x] 三份真实存档（legacy / minimal / rich）经 SQLite 往返模型逐一相等。
- [x] 两个会话用同名消息 id / 总结 id，保存、读回、删除其一后互不影响（`sameChildIdsInDifferentSessionsDoNotCollide`）。
- [x] `clean` 后 `:desktopApp:build :shared:desktopTest --rerun-tasks` 全绿、**零警告**；T06 的 `ArchiveRoundTripTest` 18/18。

验证命令与输出：

```
> .\gradlew.bat :shared:desktopTest --console=plain
  → 首跑：StorageRoundTripTest 2 失败（rich 样本第一个会话 timelines 读回 []）→ 修复时间线复合主键
  → 二跑：ChatStreamClientTest 帧间隔偶发失败 [295, 328, 314, 312, 488, 123]
  → 三跑：BUILD SUCCESSFUL
> .\gradlew.bat clean
  → BUILD SUCCESSFUL in 2s
> .\gradlew.bat :desktopApp:build :shared:desktopTest --rerun-tasks --console=plain
  → BUILD SUCCESSFUL in 43s，19 actionable tasks: 19 executed（子表改复合主键后重跑）
  → 日志中匹配 ^w: / warning / deprecat 的行 = 0
  → ArchiveRoundTripTest 18/0 失败 · StorageRoundTripTest 10/0 · ArchiveV5ModelTest 8/0
    ChatStreamClientTest 2/0 · SseProviderAdapterTest 6/0 · SplitBlocksTest 4/0 · DeepSeekLiveSmokeTest 1（跳过）
  → 合计 49 tests / 1 skipped / 0 failed
```

schema 核对：`1.json` 中 `messages` / `timelines` / `summaries` 的 `createSql` 均为 `PRIMARY KEY(\`sessionId\`, \`id\`)`。

## 遗留问题 / 下一步

- **`defaultData.ts` 尚未迁移** → 全新安装（库里什么都没有）时，`SettingsRepository` 给出的**显示设置与应用偏好默认值与 Web 版不同**：Web 版用 `DEFAULT_DISPLAY_SETTINGS`（七个字段全 `true`）/ `DEFAULT_APP_PREFERENCES`，本仓用模型自身默认值（全 `false` 等）。字面量默认值（`emerald` / `dark` / `provider-gemini` / `session-welcome`）已对齐。补 `data/DefaultData.kt` 归 T15（或谁先用到谁迁）。
- **API 密钥明文存储**：`providers.apiKey`、WebDAV `password`、MCP token 都以明文落在 SQLite 文件里（与 Web 版 localStorage 同级，但仍是缺口）。加密/系统凭据库方案待定。
- **Android 的 `actual` 推迟到 T22**：`appDataDir` / `createSqliteDriver` / `sqliteQueryDispatcher` 目前只有 desktop 实现。
- 存储层尚未接 UI：会话列表/侧边栏读库归 T14，设置弹窗归 T15。

## 相关红线

- `MIGRATION_CONSTRAINTS.md` §5：UI 不碰 DAO、写操作 `suspend`、同事务更新 `updatedAt` —— 本任务核心，由 `SessionRepository` 与测试钉住。
- `MIGRATION_CONSTRAINTS.md` §6：`omnichat_*` / `guanjie_*` 存储键原样保留，见 `StorageKeys.kt`。
- `MIGRATION_CONSTRAINTS.md` §1.3：可空三态经过拆表不被拍平（JSON 列 null ≠ `[]`、`timelinesNull`/`summariesNull`）。
- `MIGRATION_CONSTRAINTS.md` §8 自检：存档往返测试仍然通过（T06 18/18 + 本任务库往返 10/10）。
