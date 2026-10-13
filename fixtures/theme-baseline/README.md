# 主题视觉基线

> 对应 `../../docs/BASELINE_COLLECTION.md` §C。
> 用途：Compose 主题重建（PLAN §7.2）时逐项比对。**光读 CSS 无法知道哪个变量控制哪里**，截图 + 色值标注是唯一证据。

---

## 〇、`snapshots.json`：现在是**冻结快照**，不是「React 基准」

2026-10-13 起，原生主题模型已换成**「白天 / 黑夜 × 饱和度分级」**：

- 内置 **20 套** = 白天 7（`*-day`，m1 低饱和 / m2 中饱和）
  + 黑夜 7（`*-night`，m1 中高饱和 / m2 高饱和）
  + 多巴胺 6（`coral` / `honey` / `mint` / `soda` / `grape` / `bubble`，双 HEX 推导）；
- 另有 2 个自定义样本（`custom_test01/02`）验证 `buildScale` 的阶梯与精度。

React 参考版只有 `family`（7 套）+ `dopamine`（6 套）两分法，**没有白天/黑夜，也没有饱和度分级**。
旧 fixture（15 套、键为 `emerald` / `midnight` …）在新模型里查不到那些 id，
对拍必然失败 —— 它从 `c836984` 起就没再更新过。

| | 旧 | 新 |
|---|---|---|
| 生成方 | React 仓 `npx tsx`（`gen-theme-baseline.ts`，**已删除**） | 本侧 `ThemeBaselineGenerator`（desktopTest） |
| 证明的事 | 「和 React 逐字一样」 | 「当前实现没被无意改动」 |
| 改色板后 | — | 对拍变红，逼你显式重生成并 review diff |

**重新生成**（改了 `ThemeCatalog` / `ThemeVars` / `ThemeScale` 里有意的取值之后）：

```bash
./gradlew :shared:desktopTest --tests "com.watchworld.ui.theme.ThemeBaselineGenerator"
git diff fixtures/theme-baseline/snapshots.json   # 逐条确认差异都是你想要的
```

⚠️ 生成器与快照测试是同一枚硬币的两面：跑完生成器，`ThemeSnapshotTest` 必绿
（基准就是实现自己产出的）。**所以绝不能把生成器放进 CI 当验证** ——
「验证」只能由人工 review 那份 diff 承担。

⚠️ 它也**不能证明「与 Web 观感一致」**。验观感要用离屏截图断言：
`DarkBubbleScreenshotTest`（夜晚气泡必须够深）、`SettingsHubScreenshotTest`（行框底色 = 主题模块1）、
`TimelinePanelScreenshotTest`（面板分层带主色倾向）。

---

## 一、截取环境（必须记录，否则无法复现）

- 浏览器 / 版本：
- 分辨率 / 缩放：
- 截取日期：

## 二、截图清单（每套主题 5 张，建议至少 2 套）

| # | 界面 | 覆盖变量 | light | dark | custom |
|---|---|---|---|---|---|
| 1 | 聊天主界面（有消息、含工具栏） | 背景/气泡/文字主副色/边框/强调色/按钮 | [ ] | [ ] | [ ] |
| 2 | 侧边栏抽屉（会话列表、分组） | 面板色/选中态/分割线/图标色 | [ ] | [ ] | [ ] |
| 3 | 输入框区域（模型选择、发送按钮） | 输入框底/占位符色/按钮激活禁用态 | [ ] | [ ] | [ ] |
| 4 | 设置弹窗（推荐 Provider 弹窗） | 弹窗底/遮罩/标题/表单控件/滚动条 | [ ] | [ ] | [ ] |
| 5 | 消息气泡细节（时间/耗时/模型名） | 小字色/角标/次要信息 | [ ] | [ ] | [ ] |

## 三、每张图必须标注（README 是截图的价值所在）

以 `light-chat.png` 为例：

```markdown
## light-chat.png
- 主题名：<主题名>（theme id 从 localStorage omnichat_theme_palette 读）
- 关键色值（DevTools 取计算后的实际值）：
  - 页面背景：#______  ← --______
  - 气泡（助手）：#______  ← --______
  - 气泡（用户）：#______  ← --______
  - 主文字：#______  ← --______
  - 次要文字：#______  ← --______
  - 边框：#______  ← --______
  - 强调色：#______  ← --______
```

**取色方法**：F12 → 选中元素 → Styles 面板找到 `var(--xxx)` 的属性 → 记下变量名与计算后色值。

## 四、可选：CSS 变量快照

嫌手动取色麻烦，可在浏览器控制台导出（脚本见 BASELINE_COLLECTION §C.6），
存为 `css-vars-<主题名>.json`。

⚠️ 快照只给「变量名 → 色值」，**不告诉你变量用在哪儿**，不能替代截图 + 标注，两者都要。
