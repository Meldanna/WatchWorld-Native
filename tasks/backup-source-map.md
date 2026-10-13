# 备份与导入导出源代码地图

> 参考版：`E:\WatchWorld\WatchWorld\src\components\BackupSyncModal.tsx`（349 行）
> Native：`shared/src/commonMain/kotlin/com/watchworld/ui/settings/BackupSyncDialog.kt`
> 采集时间：2026-10-10。**行号会漂，以符号名为准。**

---

## 一、入口

| 入口 | 参考版 | 行号 |
|---|---|---|
| 设置总入口「备份与导入导出」卡（最后一张，Cloud 图标 sky） | `SettingsHubModal.tsx` 最后一张卡 | `SettingsHubModal.tsx:166-177` |
| 打开方式 | `handle(onOpenBackupSync)` —— **先关设置总入口、再开本弹窗** | `SettingsHubModal.tsx:43-47` |
| 关闭返回 | `closeWithReturn` → 回设置总入口 | `App.tsx:2496-2499`、`:280-287` |

⚠️ Native 侧边栏底部**没有**「备份」格（用户要求删除，第 7 格改挂正则库，见
`SidebarSheet.kt` 的 KDoc）——所以设置卡是本功能的唯一入口。

---

## 二、结构（自上而下，行号指向参考版）

| 段 | 行号 | 参考版规格 |
|---|---|---|
| 容器 | 106-107 | `fixed inset-0 z-[80]` 遮罩 `bg-black/80 backdrop-blur-sm`；卡片 `w-full max-w-xl`(576dp) · `max-h-[90vh] flex flex-col overflow-hidden` · `rounded-2xl` |
| 顶栏 | 109-128 | `p-4 border-b`；徽章 `p-2 rounded-xl` + `HardDrive` 20；标题 `备份与导入导出`（`chat-text-base font-bold`）+ 副标题 `WebDAV 跨设备云同步，以及全量 JSON 的导出与恢复`（`chat-text-xs`）+ ✕ |
| Tab 行 | 130-154 | `flex items-center border-b px-4 pt-2 gap-4 chat-text-xs`；两个 Tab：`WebDAV 云同步`（Cloud 13）/ `导入与导出`（Download 13）；选中 = `border-b-2 border-indigo-600` + 主色文字，未选 = 透明下划线 + slate-500 |
| 正文容器 | 158 / 301 | `flex-1 overflow-y-auto p-4 space-y-3.5`（WebDAV）/ `space-y-4`（导入导出）。**Tab 行在滚动区之外** |

### 2.1 WebDAV 页（157-297）

| 段 | 行号 | 参考版规格 |
|---|---|---|
| 说明卡 | 159-165 | `p-3 rounded-xl bg-indigo-50/60 border border-indigo-200/60 chat-text-xs text-indigo-700`；行 = `Cloud 14` + `WebDAV 同步支持`（font-bold）+ `InfoHint('这是观界客户端的数据同步功能，不是聊天模型可调用的工具。默认纯本地存储；可配置坚果云、Nextcloud、Alist、自建 NAS 等 WebDAV 连接。数据变动时自动防抖同步，启动时自动拉取最新数据，冲突以时间戳较新为准。')` |
| 服务器地址 * | 168-179 | label `chat-text-xs font-semibold mb-1`；input `w-full chat-text-xs font-mono p-2.5 rounded-xl border`；placeholder `https://dav.jianguoyun.com/dav/ 或 https://your-nas/remote.php/dav/files/user/` |
| 用户名 / 账号 * | 182-193 | `grid-cols-1 sm:grid-cols-2 gap-3` 左格；placeholder `如：your_account@gmail.com` |
| 应用密码 / Token * | 194-205 | 右格；`type=password` + `font-mono`；placeholder `坚果云应用专用密码或Token` |
| 自动同步开关 | 208-221 | `flex items-center justify-between p-3 rounded-xl bg-slate-50 border chat-text-xs`；左 `数据变动时自动同步` + `InfoHint('发消息或新建时间线分支后自动防抖同步到云盘')`；右 `input[type=checkbox] accent-indigo-600 w-4 h-4` |
| ⚠️ 危险开关 | 223-236 | **rose 色系卡片**：`bg-rose-50/60 border-rose-200/60`；`在手动备份时包含 API 配置` + `InfoHint('打开后，「立即同步到云端」会把你的 API 密钥与 WebDAV 密码一并上传到远端。建议只在信任的个人云盘使用，或者保持关闭（恢复后需重新填入密钥）。')`；checkbox `accent-rose-600` |
| 结果条 | 238-253 | **有 `testResult.message` 才出现**，`p-2.5 rounded-xl chat-text-xs flex items-center gap-2`；三态配色：success = emerald-50/emerald-300、testing = blue-50/blue-300、error = rose-50/rose-300；图标：testing `RefreshCw animate-spin` 3.5、success `Check`、error `X` |
| 四个按钮 | 255-294 | `flex flex-wrap items-center gap-2 pt-1`：`测试连接`（灰底 `bg-slate-100`）/ `立即同步到云端`（indigo 浅底 + Upload 13）/ `从云端拉取`（emerald 浅底 + Download 13）/ **`保存配置`（`ml-auto`，`bg-indigo-600` 白字实底）** |

### 2.2 导入与导出页（300-345）

| 段 | 行号 | 参考版规格 |
|---|---|---|
| 导出卡 | 302-318 | `p-3.5 rounded-xl bg-slate-50 border chat-text-xs space-y-2`；标题行 = `Download 14 text-indigo-500` + `一键本地数据全量导出 (JSON)`（font-bold）+ `InfoHint('包含所有会话、各分支时间线因果树、楼号记录、世界观设定文档、API 密钥与自定义正则。单文件备份，完全自主掌控。')`；按钮 `立即导出全量 JSON 文件`（`bg-indigo-600` 白字 + Download 14） |
| 导入卡 | 320-343 | 同款结构；标题 = `Upload 14 text-emerald-500` + `导入 JSON 恢复全量数据` + `InfoHint('选择此前导出的备份文件，可将整套时间线数据完整还原至新设备或新浏览器。')`；**隐藏的 `<input type=file accept=".json,application/json">`** + 按钮 `选择文件并恢复`（`bg-slate-200` 实灰底 + Upload 14） |

---

## 三、行为（行号指向参考版）

| 项 | 行号 | 参考版行为 |
|---|---|---|
| Tab 初值 | 36 | `'webdav'` |
| 测试连接 | 45-53 | **三个字段缺一即报错**：`请完整填写 WebDAV 地址、用户名和密码`（不发请求）；否则先置 `testing` 再调 `testWebDavConnection` |
| 立即同步 | 55-68 | 先置 `testing`；`Storage.exportAllData({redactSecrets: !includeApiConfig})` → `pushToWebDav`；**成功后**写回 `{enabled: true, lastSyncTime: Date.now(), syncStatus: 'success'}` |
| 从云端拉取 | 70-83 | 先置 `testing`；成功且有 `remoteData` → `onImportJson`；成功文案 `已从 WebDAV 成功恢复最新数据！页面刷新后生效。`；解析失败 `解析远程数据失败` |
| 保存配置 | 279-293 | **不测连接**：直接写 `{enabled: true, lastSyncTime: Date.now(), syncStatus: 'success'}` + `alert('WebDAV 配置已保存生效！')` |
| 字段改动 | 175 / 189 / 201 | 每次 `onChange` 立即 `onChangeWebDavConfig`（**边输入边落库**，只 `trim()`，不校验） |
| 导出 | `App.tsx:1854-1863` | `Storage.exportAllData()` → Blob → `a.download = watchworld-backup-${new Date().toISOString().slice(0,10)}.json` 并点击 |
| 文件导入 | 85-103 | FileReader 读文本 → `onImportJson`；成功 `alert('数据导入成功！工作台已完整恢复。')` **并 `onClose()`**；失败 `alert('数据格式解析失败，请检查是否为导出的JSON。')`（不关） |
| 导入侧效果 | `App.tsx:1865-1878` | `Storage.importAllData` 成功后**重新读全部 8 类配置**进 React state |

---

## 四、Native 落点

| 关注点 | 文件 |
|---|---|
| 弹窗本体 | `ui/settings/BackupSyncDialog.kt`（`BackupSyncDialog` + `WebDavTab` / `LocalBackupTab` / `BackupTabButton` / `BackupNoticeBar` / `SettingToggleRow` / `BackupActionCard` / `SoftActionButton` / `WwSecondaryAction`） |
| 设置卡入口 | `ui/settings/SettingsHub.kt` 的 `BACKUP_CARD_TITLE`（**不在** `SettingsPage` 枚举里） |
| 弹窗接线 | `ui/App.kt` 的 `backupOpen` + `backupReturnsToSettings` |
| 状态与动作 | `ui/settings/SettingsViewModel.kt`：`BackupNotice` / `BackupNoticeLevel`、`testWebDav` / `pushBackupToWebDav` / `pullBackupFromWebDav` / `markWebDavConfigSaved` / `importData` / `clearImportNotice` |
| 文件名 | `domain/BackupNaming.kt` 的 `backupFileName` / `isoDateUtc` |
| 传输实现 | `net/WebDavClient.kt`（T19，未改动） |

---

## 五、视觉与规格约束

- 外壳 `FeatureDialog`：`maxWidth = 576.dp`（= `max-w-xl`）、`heightFraction = 0.9f`（= `max-h-[90vh]`）；
  **`scrollable = false` + `fixedHeight = true`** —— Tab 行必须定在顶栏之下不随内容滚，
  正文自己 `verticalScroll`。`FeatureDialog` 的 content 不是 `ColumnScope`，
  正文里用 `weight(1f)` 要先自己套一层 `fillMaxSize`/`fillMaxHeight` 的 Column。
- 表单控件走 `WwFormControls`（`WwTextField` / `WwPrimaryButton`）；**边界**：参考版这几个按钮是
  「浅色实底 + 语义色文字」（`bg-indigo-50 text-indigo-600`），不是 `WwSecondaryButton` 的描边样式，
  因此本文件自绘 `SoftActionButton`（底 = 语义色 10% alpha）。
- 结果条的三态配色在原生**统一走主题色**：success/busy 用 `primary`、error 用 `error`，
  不再照搬 emerald/blue 硬编码（项目里 error 系保留 M3 默认，见 `theme-color-map.md`）。
- 图标：`HardDrive` → `Database`（Tabler 子集无 `HardDrive`）；`Download` / `Upload` /
  `Refresh` / `Check` / `X` / `Cloud` 同名可用。
- 状态语义：`BackupNoticeLevel`（SUCCESS / BUSY / ERROR）对标参考版的
  `testResult.status`（`idle | testing | success | error`）——`idle` 在原生用 `notice == null` 表达。

### 与参考版的有意分歧

1. 外壳改用 `FeatureDialog`（项目统一弹窗壳），不是自绘 `fixed inset-0`。
2. `window.alert()` → **页内结果条**（原生没有 alert；导入结果如果弹完就关窗，用户看不清）。
   文案照抄参考版。
3. 导出走平台保存框 `saveTextFile`（原生没有浏览器下载），文件名规则与参考版一致（UTC 日期）。
4. **删掉了旧 `BackupPanel` 里的「粘贴存档 JSON」文本框** —— 参考版没有这个入口，
   它是原生自造的；导入只有「选择文件」一条路。
5. 「保存配置」不再弹 alert，`saveWebDavConfig`（边输入边落库）+ `markWebDavConfigSaved`（点按钮时置 `enabled`）分开。
