# T19 · WebDAV 同步

| | |
|---|---|
| 任务号 | T19 |
| 日期 | 2026-10-09 |
| 状态 | ✅ 完工（直连 WebDAV 客户端 + 增量同步引擎 + 设置面板 + 启动拉取/防抖推送；图片同步见「遗留问题」） |
| 阶段 | 阶段 6 · 特色功能 |
| 上位清单 | [`WORKPLAN.md`](../WORKPLAN.md) §二 T19 |

## 目标与可见效果

- **目标**：把 `webdavSync.ts`（传输层）与 `syncEngine.ts`（增量语义）迁到原生，并在设置面板里给出可用的 WebDAV 云同步（含**坚果云应用密码**）。
- **可见效果**：
  1. 设置 → 备份与导入导出：填 WebDAV 地址 / 账号 / **应用密码** →「测试连接」能对真实 WebDAV 走通 `PROPFIND → PUT → DELETE`，并回报耗时；
  2. 「立即同步到云端」把当前存档（默认**擦掉** API 密钥与 WebDAV 密码）整份推到 `/guanjie_backup.json`；
  3. 「从云端拉取」把远端整份存档导入本地；
  4. 勾选「数据变动时自动同步」后，**启动自动拉取**远端较新的窗口、**改动后 3s 防抖上传**。
- **验收标准**：
  1. `WebDavClient` 的每个方法与 `webdavSync.ts` 一一对应，方法名不改；原生**不走代理**（无 CORS，直连目标 URL）；
  2. `SyncEngine` 的冲突规则是「时间戳较新者胜」：`pushChanges` 只传本地 `updatedAt` 更大的窗口，`pullChanges` 只拉远端更大的窗口；拉下来的窗口过 `normalizeChatSession`；
  3. 全量推送按 `includeApiConfig` 决定是否 `redactSecrets`；
  4. 零警告 + `:desktopApp:build :shared:desktopTest --rerun-tasks` 全绿，往返测试继续通过。

## 改动

| 文件 | 改动 |
|---|---|
| `shared/.../net/WebDavClient.kt` | **新增**。`WebDavSyncResult` / `WebDavConnectionTestResult` / `SyncManifest`(+`SyncSessionMeta`/`SyncImageMeta`)；`emptyManifest`、`backupFileUrl`、`syncRoot`、`safeFileName`、`urlPath`（internal 纯函数）；`WebDavClient` = `testConnection`（PROPFIND `Depth: 0` → PUT 临时文件 → DELETE）、`pullBackup`、`pushBackup`、`ensureCollections`（MKCOL×3）、`fetchManifest`/`pushManifest`、`pushSessionFile`/`fetchSessionFile`、`pushImageFile`/`fetchImageFile`；私有 `call`（真 HTTP 方法 + Basic 认证）。 |
| `shared/.../data/repository/SyncEngine.kt` | **新增**。`SyncState`（清单 + `ready`）、`SyncOutcome`、`createSyncState`、`isSyncConfigured`、`pushChanges`、`pullChanges`。会话级；图片循环留白（T21）。 |
| `shared/.../ui/settings/SettingsViewModel.kt` | **改**。构造参数新增 `webDavClient: WebDavClient`；状态新增 `webDavConfig`/`webDavStatus`/`webDavBusy`；新增 `saveWebDavConfig`、`testWebDav`、`pushBackupToWebDav`（按 `includeApiConfig` 决定是否擦密钥）、`pullBackupFromWebDav`（拉到即 `importJson` + `load`）。 |
| `shared/.../ui/settings/SettingsPanels.kt` | **改**。`BackupPanel` 扩为 WebDAV 配置表单 + 开关 + 4 个按钮 + 状态文案 + 原有本地导入导出。 |
| `shared/.../ui/settings/SettingsHub.kt` | **改**。签名新增 4 个 WebDAV 回调；BACKUP 页把新参数透给 `BackupPanel`。 |
| `shared/.../ui/App.kt` | **改**。新增 `WebDavClient` 与 `createSyncState()`；`SettingsViewModel` 传入 client；启动 `LaunchedEffect` 里做一次 `pullChanges` 并用 `SessionRepository.replaceAll` 合并；新增 `sessionState.sessions` 变化的 3s 防抖 `pushChanges`；两处 `BackupPanel`/`SettingsHub` 调用接线。 |
| `shared/src/desktopTest/.../net/FakeWebDavServer.kt` | **新增**。进程内假 WebDAV（内存文件 + 方法记录），供两套测试共用。 |
| `shared/src/desktopTest/.../net/WebDavClientTest.kt` | **新增 13 用例**：测试连接三步与 Basic/`Depth` 头、临时文件被清理、配置不全不发请求、目录/上传失败文案、404 视为成功、pull 返回正文、push 落盘、停用/不全被拒、manifest 往返、会话文件往返（`{kind,payload}` 包裹）、MKCOL×3 且 405 被忽略、路径纯函数。 |
| `shared/src/desktopTest/.../data/SyncEngineTest.kt` | **新增 7 用例**：`isSyncConfigured` 分支、首推 + 二次「无改动」、只推更新的窗口、拉取较新窗口并归一化、本地更新时不拉、无清单「无更新」、**拉到的 `updatedAt` 经 `replaceAll` 不被盖**。 |
| `shared/src/desktopTest/.../net/WebDavLiveSmokeTest.kt` | **新增**。`WW_T19_LIVE=1` + `WW_T19_WEBDAV_{URL,USER,PASSWORD}` 才运行的在线 smoke；用独立 `syncPath` 避免覆盖真实备份。 |
| `shared/src/desktopTest/.../ui/settings/SettingsViewModelTest.kt` | **改**。构造补 `WebDavClient(HttpClientFactory.create())`。 |
| `memory/function-map.md` | **改**。新增 §N16；§G 补 Kotlin 列；§B `syncEngine.ts` 标 ✅ T19。 |

## 心得

### 1. 原生版**没有 CORS**，代理必须去掉 —— 而且去掉后是**真方法名**

Web 版所有 WebDAV 请求都发给 `server.ts` 的 `/api/webdav`，靠 `x-webdav-url` /
`x-webdav-method` / `x-webdav-authorization` 三个头把「想对目标做的操作」透传过去
（浏览器直连坚果云会被 CORS 拦下）。桌面端与 Android 都是进程内应用，没有 CORS，
因此这里直接对目标 URL 发 **真正的** `PROPFIND` / `PUT` / `GET` / `DELETE` / `MKCOL`。

Ktor 侧用 `client.request(url) { method = HttpMethod.parse("PROPFIND") }`，
配合 `HttpClientFactory` 那个 `expectSuccess = false` 的 client（非 2xx 不抛，交给我们自己读状态码）。

### 2. Ktor 的 `ByteArray.encodeBase64()` 已废弃，零警告要求下必须换 stdlib

第一版用 `io.ktor.util.encodeBase64()` 拼 Basic 认证，编译直接出
`'fun ByteArray.encodeBase64()' is deprecated. Use Base64.Default.encode() from the Kotlin standard library.`
本仓的验收是**零警告**，所以换成 `kotlin.io.encoding.Base64.encode(bytes)`。
这不是小事：Base64 拼错的话，坚果云/Nextcloud 会返回 401，而 401 在本任务里
被 `testConnection` 解释成「目录访问失败」，排查会绕远路。

### 3. `isConfigured` 漏了 `enabled`，是被用例抓出来的

`pushBackup` / `pullBackup` 的守卫第一版只查了「地址/账号/密码」，把 `config.enabled`
忘了 —— 结果「停用 WebDAV 后仍然会发请求」。`pushAndPullRejectWhenDisabledOrIncomplete`
首跑失败（`ComparisonFailure`）才发现。TS 原文是
`if (!config.enabled || !config.url || !config.username || !config.password)`，
**`enabled` 在最前面**，容易在改写时掉队。

### 4. 拉取合并必须用 `replaceAll`，不能用 `save` —— 否则会「时间戳乒乓」

`SessionRepository.save` 的契约是**自己盖 `updatedAt = now()`**（T07 定的，
为了让调用方不会忘）。但同步拉取要保留**远端的时间戳**，否则：
拉回来 → `save` 把 `updatedAt` 改成本机时间 → 比远端新 → 下一轮 `pushChanges`
又把它推回云端 → 云端时间戳被改成这台机器的时间 → 另一台机器再拉……无限来回。

修法是走 `SessionRepository.replaceAll`（导入专用，**不盖戳**，与 Web 版
`Storage.setSessions` 原样写入一致）。`SyncEngineTest.pulledUpdatedAtSurvivesReplaceAll`
故意注入一个「会盖戳」的 `now` 来钉死这条：
```kotlin
val repository = SessionRepository(db, now = { 999_999L })
...
assertEquals(5000L, stored.updatedAt, "replaceAll 不许盖 updatedAt")
```

### 5. `pushBackup` 成功文案有意去掉本地时间

Web 版是 `同步成功 (${new Date().toLocaleTimeString()})`。commonMain 没有时区 / 本地时间
格式化能力，为一句提示引 `kotlinx-datetime` 不划算，因此原生文案就是 `同步成功`，
时间放在 `WebDavSyncResult.timestamp` 里。这是**有意偏离**，不是漏迁。

### 6. 防抖推送不需要额外状态机，Compose 的 `LaunchedEffect` 天然就是防抖

Web 版是 `useEffect` + `setTimeout(..., 3000)` + cleanup。原生版把它压成一行：
```kotlin
LaunchedEffect(sessionState.sessions, settingsState.webDavConfig) {
    val config = settingsState.webDavConfig ?: return@LaunchedEffect
    if (!isSyncConfigured(config) || !config.autoSync) return@LaunchedEffect
    delay(3000)
    runCatching { pushChanges(config, sessionState.sessions, syncState, webDavClient) }
}
```
键一变 `LaunchedEffect` 就取消上一次协程（`delay` 被打断），等于 3s 防抖。
判据是 `isSyncConfigured`（含 `enabled`）**且** `autoSync` —— 与 Web 版一致。

### 7. 图片同步整体推迟到 T21

`syncEngine.ts` 里有 `pushMissingImages` 与拉图循环，它们依赖 `ImageStore`（T21）。
本任务把**清单字段**（`images`）与**客户端方法**（`pushImageFile`/`fetchImageFile`）都留好了，
`SyncOutcome.images` 恒为 0，T21 接上 `ImageStore` 即可。这样切分的好处是：
增量同步的**冲突规则**（本任务最需要验收的部分）可以完全独立于图片存储先测透。

## 要点

- **原生版 WebDAV 直连、不走 `/api/webdav` 代理**：`x-webdav-*` 三个透传头全部去掉。
  以后要加新方法（如 `COPY`/`MOVE`）直接写真实方法名即可。
- **Basic 认证的 Base64 用 stdlib `kotlin.io.encoding.Base64.encode(...)`**，
  不要用已废弃的 Ktor `encodeBase64()`（零警告要求）。
- **`isConfigured`（pull/push 用）必须含 `config.enabled`**；`testConnection` 不看 `enabled`
  （与 TS 一致，测试连接只要求填齐）。两处口径不同，别合并。
- **路径拼接**：`backupFileUrl` = `url` 去尾斜杠 + `syncPath`（默认 `/guanjie_backup.json`，空前缀补 `/`）；
  `syncRoot` = `url` 去尾斜杠 + `syncRootDir`（默认 `/guanjie/`，补尾斜杠）；
  `safeFileName` 把非 `[A-Za-z0-9._-]` 换成 `_`（会话/图片文件名都过它）。
- **`PROPFIND` 测试连接用 `Depth: 0`**（对齐 `webdavSync.ts`；函数地图里旧写的 `Depth: 1` 已修正）。
  `MKCOL` 在目录已存在时返回 405，**必须忽略**。
- **清单文件名是 `index.json`**，会话是 `windows/<safeFileName(id)>.json`，图片是
  `media/<safeFileName(id)>.json`；文件体是 `{ "kind": "session"|"image", "payload": … }`。
- **拉取结果合并一律走 `SessionRepository.replaceAll`（不盖 `updatedAt`）**，
  绝不能用 `save`（会盖戳 → 时间戳乒乓）。见心得 4。
- **拉下来的会话必须先过 `normalizeChatSession`**（远端可能来自更老版本）；
  `toolCallStepLimit` 从**原始 JSON** 取，与存档导入路径一致。
- **手动全量推送默认擦密钥**：`includeApiConfig != true` → `exportJson(redactSecrets = true)`。
  与 Web 版 `BackupSyncModal` 默认一致。
- **`WebDavConfig` 没配过时 `SettingsRepository.webDavConfig()` 返回 `null`**（不是默认常量）——
  界面用 `config ?: WebDavConfig()` 兜底，但「是否配置过」的判断要用 `null`。
- **`SyncState` 每个应用生命周期只建一份**（`App.kt` 的 `remember { createSyncState() }`），
  清单与「已建目录」标记靠它跨多次同步保留。
- **自动同步的 `LaunchedEffect` 键是 `sessionState.sessions` + `webDavConfig`**：
  改动 → 3s 后推送；键变化会取消上一次，天然防抖。
- **图片同步是 T21 的活**：`SyncEngine` 只做会话；清单 `images` 与客户端
  `pushImageFile`/`fetchImageFile` 已就位，但没有任何调用点。
- **`FakeWebDavServer` 是共用测试资产**（`desktopTest/net/FakeWebDavServer.kt`）：
  `SyncEngineTest` 也用它，不要各自再写一份。

## 验收结果

- [x] `WebDavClient` 方法与 `webdavSync.ts` 一一对应；直连（无代理）。
- [x] 测试连接三步（PROPFIND `Depth: 0` → PUT → DELETE）、Basic 认证、临时文件清理均有断言。
- [x] `pushChanges` 只传更新的窗口、`pullChanges` 只拉更新的窗口；拉取过归一化。
- [x] 全量推送按 `includeApiConfig` 擦密钥。
- [x] 设置面板可填配置、测试、推送、拉取；自动同步开关落库。
- [x] App 启动拉取 + 3s 防抖推送接线；合并走 `replaceAll`。
- [x] `clean` 后 `:desktopApp:build :shared:desktopTest --rerun-tasks` 全绿、**零警告**。
- [x] `:shared:desktopTest` 共 **37 套 / 297 用例 / 0 失败 / 0 错误 / 5 跳过**（4 个在线 smoke + 新增 `WebDavLiveSmokeTest`）。
- [x] T06 `ArchiveRoundTripTest`、T07 `StorageRoundTripTest`、T15 `SettingsViewModelTest` 继续通过。

验证命令与输出：
```
> ./gradlew clean :desktopApp:build :shared:desktopTest --rerun-tasks --console=plain
  → BUILD SUCCESSFUL
  → 日志中匹配 ^w: / ^e: / warning 的行 = 0
  → 合计 37 套 / 297 用例 / 0 失败 / 0 错误 / 5 跳过
    WebDavClientTest 13/0 · SyncEngineTest 7/0 · WebDavLiveSmokeTest 1 跳过
```

## 遗留问题 / 下一步

- **⚠️ 图片同步未做（归 T21）**：`syncEngine.ts` 的 `imageIdsOf` / `pushMissingImages`
  与 `pullChanges` 里的拉图循环全部留白。T21 的 `ImageStore` 就位后：
  ① 在 `pushChanges` 里对每个已推窗口补推缺失图片并累加 `outcome.images`；
  ② 在 `pullChanges` 里补齐清单中已有、本地缺失的图片。
  `WebDavClient.pushImageFile`/`fetchImageFile` 与 `SyncManifest.images` 不需要改。
- **没有真人账户的端到端验收**：本任务只有进程内假上游 + 一个 opt-in 在线 smoke
  （`WW_T19_LIVE=1`）。真机对坚果云的「应用密码」链路需要在有账号的机器上跑一次
  `WebDavLiveSmokeTest`，并肉眼确认设置面板的文案。
- **自动同步的错误没有反馈到界面**：防抖推送用 `runCatching {}` 静默吞掉失败
  （对齐 Web 版 `void pushChanges(...)`），设置面板里的状态文案只在**手动**操作时更新。
  要不要把后台同步失败也写进 `lastError` / 界面提示，属于 UI 打磨。
- **「导出到文件」仍未做**：`BackupPanel` 的「导出数据」目前只 `println` 长度（T15 遗留）。
  文件选择/保存属 T23 或后续 UI 任务。
- **冲突策略只有「时间戳较新者胜」**：与 Web 版一致，**没有三方合并**。
  两台设备同时改同一窗口时会丢一边，这是既有设计，不在本任务范围内。
- **`testWebDavConnection` 的临时文件名带随机后缀**：`Random.nextLong().toString(36)`，
  与 JS 的 `Math.random().toString(36).slice(2)` 形状不同，但只用于一次性写/删，
  不影响任何行为。

## 相关红线

- `MIGRATION_CONSTRAINTS.md` §5：UI 只拿 Repository / ViewModel，写操作 `suspend` ——
  `SettingsViewModel` 与 App 接线遵守；`SessionRepository.replaceAll` 用于合并导入。
- `MIGRATION_CONSTRAINTS.md` §1.1 / §8：存档 `version = 5` 未动；`redactSecrets` 仍是空串 `''`；
  存档往返测试继续通过。
- `MIGRATION_CONSTRAINTS.md` §6：字段名不改 —— `WebDavConfig` 的 `syncPath`/`syncRootDir`/
  `includeApiConfig`/`syncStatus` 原样使用。
- `CONSTRAINTS §3.2`（不缓冲流式）不适用本任务：WebDAV 是普通请求/响应，读写全量文本。
