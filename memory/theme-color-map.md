# 主题配色函数地图（theme-color-map.md）

> **用途**：要改某个界面的颜色时，先在这里定位「这个颜色由哪个函数/对象决定、在哪个文件」。
> **来源基准**：`E:\WatchWorld\WatchWorld`（React 版 = 规格书）+ `E:\WatchWorld\WatchWorld-Native\shared\src\commonMain\kotlin\com\watchworld\ui\theme\`
> **Kotlin 侧为唯一权威**：本表列的行号采集于 2026-10-09，行号会漂，以符号名为准。

---

## 0. 一句话规则（新代码照这个写）

| 你要画什么 | 用哪个 API |
|---|---|
| 弹窗 / 面板**底板** | `WwTheme.colors.surfaceElevated` |
| 底板上的一层**卡片 / 分组块** | `WwSurfaces.card`（弱）· `WwSurfaces.cardStrong`（强） |
| 卡片内部的**次级凹槽**（数值框、标签框） | `WwSurfaces.inset` |
| **选中 / 聚焦**态的卡片 | `WwSurfaces.cardSelected` |
| 整块**高亮**（如「当前激活」） | `WwSurfaces.accentSoft` |
| 卡片**描边** | `WwSurfaces.cardBorder`；更弱的用 `cardBorderSubtle` |
| 正文 / 次级文字 | `MaterialTheme.colorScheme.onSurface` / `onSurfaceVariant` |
| **主色**（图标、强调文字、选中徽章底） | `MaterialTheme.colorScheme.primary` / `WwTheme.colors.primaryBg` |
| 分支专属色（时间线 7 色板） | `timelineAccent(colorId)`（`ui/timeline/TimelineEditorDialog.kt`） |

⚠️ **不要再**用 `MaterialTheme.colorScheme.surfaceVariant` 当卡片底 —— 它是纯中性灰（见 §3「坑」）。

---

## 1. 分层：谁决定什么

```
index.css 的 :root / html.dark          ← 静态 design token（只随明暗变）
        │
        ├─ DesignTokens        （WwTheme.kt）   surface0..3 / textPrimary.. / border / accent
        │
theme.ts 的 FAMILY_SCALES / buildScale   ← 主题色阶（随主题 id 变）
        │
        ├─ ThemeCatalog        （ThemeCatalog.kt）  FAMILY_SCALES / L_LADDER / C_BELL / builtInThemes
        ├─ ThemeScale          （ThemeScale.kt）    buildScale / resolveTheme / toCssNumber
        ├─ ThemeVars           （ThemeVars.kt）     resolveThemeVars  → m1[11] / m2[11] / bubble / bubbleDark
        ├─ CssColors           （CssColors.kt）     oklch/hex 字符串 → Compose Color
        │
        └─ 角色层（决定「主色是哪一档」）
           ├─ ThemeRoles       （themeRoles.kt）    STANDARD / MIDNIGHT 两套类名串
           ├─ parseRoleClass   （themeRoles.kt）    类名串 → 「明暗 → Color?」两维映射
           ├─ MaterialColorScheme（MaterialColorScheme.kt）  → M3 ColorScheme（喂 MaterialTheme）
           └─ buildWwColors    （WwTheme.kt）       → WwColors（WwTheme.colors 的类型）
```

**双轨（用户已拍板，T12）**：
① 主题 → M3 `ColorScheme` → `MaterialTheme`，让 `Button` / `OutlinedTextField` / `Surface` / `Switch` 自动跟随；
② 新代码用 `WwTheme.colors`（`LocalWwColors`），字段语义与 Web 版的类名一一对应。

---

## 2. 函数 → 管什么配色

### 2.1 `ui/theme/WwTheme.kt`

| 符号 | 管什么 |
|---|---|
| `DesignTokens` | 静态 token 的**真值**（亮/暗两套）：`surface0..3`、`surfaceElevated`、`textPrimary/Secondary/Tertiary`、`borderSubtle/Default`、`accentPrimary/Secondary` |
| `DesignTokensDp` | 圆角与间距（`RADIUS_*` / `SPACE_*`）。**不是颜色** |
| `WwTypographyTokens` | 字号（`chat-text-*`）。**不是颜色** |
| `WwColors` | UI 层看到的**当前配色总集**：角色色（`primaryBg` / `userBubble` / `aiBubble` / `badge*` / `m1Soft` / `m2Soft`）+ token 投影（`surface*` / `text*` / `border*`）+ `material` + `typography` |
| `WwTheme(palette, customThemes, chatFontSize, uiMode)` | 根组合入口；组装 vars → scheme → `WwColors`，并 `CompositionLocalProvider` 两维（`LocalUiMode` / `LocalWwColors`） |
| `WwTheme.colors` | 惯用取色入口（= `LocalWwColors.current`） |
| `LocalWwColors` / `LocalUiMode` | 两个 `staticCompositionLocalOf`；`LocalWwColors` 未包裹会**直接 error** |
| `buildWwColors` | 角色串解引用 + token 择边 + 气泡解析。**改 `WwColors` 字段含义就改这里** |
| `colorMixSoft` | `--m1-soft` / `--m2-soft` 等价物（500 档 15% alpha） |

### 2.2 `ui/theme/WwSurfaces.kt` ★ T24 新增

| 符号 | 管什么 | 实现 |
|---|---|---|
| `card` | 底板上的卡片底（弱） | `surfaceElevated` 掺 6% `primaryBg` |
| `cardStrong` | 主卡片底（强） | 掺 12% |
| `inset` | 卡片内的次级凹槽 | `surface0` 掺 10% |
| `cardSelected` | 选中 / 聚焦卡片底 | 掺 18% |
| `accentSoft` | 整块高亮底 | 掺 24% |
| `cardBorder` | 卡片描边 | `borderDefault` 掺 45% `primaryBg` |
| `cardBorderSubtle` | 次级描边 | `borderSubtle` 掺 35% |
| `mix(base, overlay, ratio)` | 私有；`ratio = 0` 取 base、`1` 取 overlay | `androidx.compose.ui.graphics.lerp` |

**为什么需要它**：见 §3 坑 ①。**全部取值都是 `@Composable`**，不许缓存成顶层常量。

### 2.3 `ui/theme/MaterialColorScheme.kt`

| 符号 | 管什么 |
|---|---|
| `MaterialColorSchemeFactory.build(vars, cfg, mode)` | 由主题色阶生成 M3 `ColorScheme`。主档位**不硬编码**：light 取 `primaryBg` 的档、dark 取 `primaryHover` 的档 |
| `extractShade(roleClass)` | `bg-m1-700` → `700` |
| `LightTokens` / `DarkTokens` | M3 里那几个「不属于主题色」的槽位（background / surface / outline…）投影自 `DesignTokens`；**error 系保留 M3 默认**（Web 版未主题化错误色） |

### 2.4 `ui/theme/themeRoles.kt` + `ThemeCatalog.kt` + `ThemeVars.kt` + `ThemeScale.kt` + `CssColors.kt` + `OklchColorMath.kt`

| 符号 | 管什么 |
|---|---|
| `ThemeRoles.STANDARD` / `.MIDNIGHT` | 两套角色串（**类名字符串**，不是颜色）。改主色档位手感就改这里 |
| `StandardRoles` | 7 字段载体（`primaryBg` / `primaryHover` / `primaryText` / `userBubble` / `accentBadge` / `badgeBg` / `badgeText`） |
| `parseRoleClass(clazz)` | 类名串 → 「明暗 → Color?」；支持 `bg\|text\|border-m1\|2-N`、`/NN` alpha、`dark:`、`hover:` |
| `shadeToColor(module, shade, alpha)` | 档位 + alpha → Color。依赖 `roleCurrentVars` 这个组合期上下文 |
| `FAMILY_SCALES` | 7 族 × 11 档的 oklch 表（**Tailwind v4**，与 v3 的 hex 同名不同色） |
| `L_LADDER` / `C_BELL` | `buildScale` 的亮度阶梯与彩度钟形分布 |
| `buildScale(hex)` | hex → 11 档 oklch（自定义/多巴胺主题用） |
| `resolveTheme(theme, customThemes)` | 主题 id → `ThemeConfig`（查不到回退 emerald） |
| `resolveThemeVars(theme)` | `ThemeConfig` → 24 个 CSS 变量字符串（`m1[11]` / `m2[11]` / `bubble` / `bubbleDark`） |
| `CssColors.parse` / `.parseStrict` | 解析 hex / `oklch()` / `rgb()`，含 alpha 语义（无单位 = 比例、`%` = 百分数） |
| `OklchColorMath` | oklch ↔ sRGB 正向/逆向矩阵（`theme.ts` 逐字） |

### 2.5 面板级的专属色

| 符号 | 位置 | 管什么 |
|---|---|---|
| `timelineAccent(colorId)` | `ui/timeline/TimelineEditorDialog.kt` | 时间线 7 色板（indigo/emerald/amber/rose/cyan/purple/teal）→ Compose `Color`。⚠️ `TIMELINE_COLORS` 里存的是 **Tailwind class 串**，Compose 解释不了，必须走这个映射 |

---

## 3. 坑（踩过一次就别再踩）

### ① 弹窗里的卡片曾经是「灰白块」——`surfaceVariant` 是中性灰

- **症状**：时间线管理器 / 窗口功能弹层的卡片铺出来是一块块灰白，与主题色完全脱节。
- **原因**：M3 的 `colorScheme.surfaceVariant` 在本体系里投影自 `--surface-3`（亮 `#E2E8F0`），是**纯中性**的。Web 参考版虽然也写 `bg-slate-100`，但它的卡片自带 `border-m1-500/30` 与 `from-indigo-50/50` 渐变，观感才不灰 —— 只把底色搬过来就丢了那层。
- **对策**：卡片一律走 `WwSurfaces.*`（把主色按极低比例混进表面色）。回归由 `TimelinePanelScreenshotTest` 守：判据是**通道差**（emerald 下 G 显著高于 R/B），不依赖具体色值，改回灰色立刻失败。

### ② 分层底色必须**彼此可区分**

`card` / `cardStrong` / `inset` 如果混出同一个色，叠在一起就看不出层级。测试里有相等断言。

### ③ 颜色不许缓存成常量

`WwSurfaces` 的每个字段都是 `@Composable get()`，因为值依赖当前 `WwTheme.colors`。写成顶层 `val` 会在组合外取到错误（或崩溃）的值。

### ④ Tailwind v4 ≠ v3

`emerald-600` 在 v4 是 `oklch(…)` → `#009966`；v3 是 `#059669`。**浏览器才是 ground truth**，对拍以 T12 的快照为准。

### ⑤ `CssColors` 的 alpha 语义

`oklch(… / 0.9)` 是 **90% 不透明**，不是 0.9%。T12 曾把这个当百分数，导致深色 AI 气泡近乎全透明（`DarkBubbleScreenshotTest` 就是为这条加的）。

---

## 4. 回归测试在哪

| 测试 | 守什么 |
|---|---|
| `shared/src/desktopTest/.../ui/theme/` 6 个类 + `ThemeSnapshotCompat` | 快照对拍：**22 套**主题 × 24 变量逐字相等（fixture 由 `ThemeBaselineGenerator` 从本侧实现导出，见 `fixtures/theme-baseline/README.md`）；oklch→sRGB 向量 |
| `DarkBubbleScreenshotTest` | **暗色气泡必须明显比亮色气泡深**、且与深底可区分（离屏截图 + 像素断言） |
| `SettingsHubScreenshotTest` | 设置入口行框底色 == `WwSurfaces.accentSoft`（主题模块1）、且 != 中性灰 `surface`；截图落 `shared/build/t28-screenshots/` |
| `TimelinePanelScreenshotTest` | T24 面板分层底色带主色倾向；截图落 `shared/build/t24-screenshots/` |

---

## 变更记录

| 日期 | 改动 |
|---|---|
| 2026-10-09 | 新建。T24 面板配色统一：新增 `WwSurfaces`，时间线三弹窗与 `WindowFunctionSheet` 从 `surfaceVariant`/`surface` 迁到分层主题色；补 `TimelinePanelScreenshotTest` |
