# WatchWorld 主题饱和度系统

> 更新日期：2026-10-13（原文描述的是**已废弃**的运行时自适应算法，见文末「历史」）
> 相关代码：`ui/theme/ThemeCatalog.kt` · `ui/theme/ThemeVars.kt` · `ui/theme/WwSurfaces.kt`

## 一、它要解决的问题

同一套主题色，在黑底和白底上的观感**要求相反**：

- 低饱和色（灰绿、淡青）在**黑底上几乎看不见** → 夜晚需要更浓；
- 高饱和色（紫、粉、橙）在**白底上过于刺眼** → 白天需要更淡。

所以「饱和度」不是全局一个系数，而是**按主题 × 明暗**分成几档。

## 二、实现方式：预生成色表 + 按级别选表（**不是**运行时算）

每个内置主题带一个 `ThemeSource.SaturationLevel(family, m1Level, m2Level)`：

```kotlin
sealed interface ThemeSource {
    data class Custom(val m1: String, val m2: String) : ThemeSource          // 双 HEX 推导
    data class SaturationLevel(                                               // 预设主题
        val family: ThemeFamily, val m1Level: SatLevel, val m2Level: SatLevel
    ) : ThemeSource
}

enum class SatLevel { LOW, MID, MID_HIGH, HIGH }
```

`resolveThemeVars` 按级别**查表**（`ThemeVars.kt`）：

| `SatLevel` | 取哪张表 | emerald-500 的 C |
|---|---|---|
| `LOW` | `FAMILY_SCALES_LOW_SAT` | 0.085 |
| `MID` | `FAMILY_SCALES` 的 C **× 0.7**（现算） | 0.119 |
| `MID_HIGH` | `FAMILY_SCALES_MID_HIGH_SAT` | 0.205 |
| `HIGH` | `FAMILY_SCALES`（Tailwind 原表） | 0.170 |

> ⚠️ 三张手写表（`FAMILY_SCALES` / `_LOW_SAT` / `_MID_HIGH_SAT`）**L 与 H 逐值完全相同，只有 C 不同**。
> `MID` 没有独立表，是 `HIGH` 的 C × 0.7 现算。

## 三、内置主题怎么分组（20 套）

| 组 | 套数 | `m1Level` | `m2Level` | 意图 |
|---|---|---|---|---|
| 白天 `*-day` | 7 | `LOW` | `MID` | 白底要淡 |
| 黑夜 `*-night` | 7 | `MID_HIGH` | `HIGH` | 黑底要浓 |
| 多巴胺 | 6 | — | — | 走 `Custom`（双 HEX 撞色），不参与饱和度分级 |

多巴胺 = `coral` / `honey` / `mint` / `soda` / `grape` / `bubble`。

⚠️ **SLATE 三个 family** 的三张表**逐值相同**（灰阶 C 只有 0.003–0.046），
所以 `midnight` / `slate-day` / `slate-night` 的级别参数**不起作用** —— 待办。

## 四、界面层：`WwSurfaces` 只负责「取哪一档」

`WwSurfaces` **不做任何饱和度运算**，只是从已解析好的 `vars.m1` / `vars.m2` 里取档：

```kotlin
private fun m1Shade(index: Int): Color {
    val cssColor = WwTheme.colors.vars.m1.getOrNull(index) ?: return WwTheme.colors.primaryBg
    return CssColors.parse(cssColor) ?: WwTheme.colors.primaryBg
}
```

档位约定（[m1/m2] 索引 0–10 对应 50–950）：

| 角色 | 亮色 | 暗色 | 含义 |
|---|---|---|---|
| `dialogBase` / `sheetBase` / `panelBase` | 纯白 | `surfaceElevated` | 底板（不含主题色） |
| `windowBase` | 纯白 | `surface0` | 主窗口背景 |
| `card` / `cardStrong` / `cardSelected` / `accentSoft` | m1 **1**（100 档） | m1 **9**（900 档） | 底板上的卡片/框 |
| `inset` | m2 **1**（100 档） | m2 **8**（800 档） | 卡片内的次级凹槽 |
| `cardBorder` / `panelBorder` | m2 **2**（200 档） | m2 5 掺中性 | 描边 |
| 气泡 `--m1-bubble` / `-dark` | m1 **2** | m1 **10** | 见下 |

## 五、气泡色的规则（2026-10-13 改）

气泡**不再**是旧模型里的固定值（预设 `#ffffff`、暗色 `oklch(20.8% … / 0.9)`），
改为**主色派生档**：

- 亮色气泡 = `m1[2]`（200 档）
- 暗色气泡 = `m1[10]`（950 档，最浓）

**为什么**：旧代码暗色气泡取的是 `m1[3]`（300 档，L≈84.5%）—— 一块**浅**色，
夜晚模式下比周围还亮、与深底脱节（用户截图实证）。
`DarkBubbleScreenshotTest` 断言「暗色气泡必须明显比亮色气泡深」，守着这条。

用户气泡另走角色串 `ThemeRoles.STANDARD.userBubble`
（`bg-m1-200 … dark:bg-m1-900`），与 AI 气泡同样在夜晚取深档。

## 六、怎么验证

| 测试 | 守什么 |
|---|---|
| `ThemeSnapshotTest` | 22 套 × 24 变量逐字对拍（fixture 由 `ThemeBaselineGenerator` 从本侧导出） |
| `DarkBubbleScreenshotTest` | 暗色气泡比亮色明显深、且与深底可区分 |
| `SettingsHubScreenshotTest` | 设置行框底色 = `WwSurfaces.accentSoft`（模块1），不是中性灰 |
| `TimelinePanelScreenshotTest` | 面板分层带主色倾向（通道差断言） |

## 七、待办

1. **`m2Level` 全组同值**（白天 7 套全是 `MID`、夜晚 7 套全是 `HIGH`）→ m2 只由两个全局参数决定，
   饱和度分级在 m2 上形同虚设。
2. **SLATE 三表相同**，灰阶无色度可分 → 该族的级别参数无效。
3. 气泡档位 `m1[2]` / `m1[10]` 是临时定案，**未经用户视觉确认**。

---

## 历史（已废弃的方案，勿照此实现）

本文档旧版（2025-01-31 / v2.0）描述的是一个**运行时自适应算法**：
计算主题的平均彩度（`getThemeSaturation()`），低于 0.15 判为低饱和、
夜间把彩度 ×1.8（m1）/ ×2.0（m2）；高饱和主题白天 ×0.5 / ×0.4，并配一个
`saturate(oklchString, factor)` 助手。

**这套函数在当前代码里不存在**（`getThemeSaturation` / `saturate` 全仓无定义），
已被「预生成色表 + `SatLevel` 选表」取代；旧文里的 `m2 = m1 × 0.4` 也已在
2026-10-13 作为废案（`ThemeSource.Family`，从未被构造）一并删除。
