# UI 主题表面色系统

## 概述

WatchWorld Native 的主题系统采用分层底色设计，通过 `WwSurfaces` 对象统一管理所有弹窗、面板、卡片的表面颜色。系统支持白天/黑夜模式自适应，并接入模块1 (m1) 和模块2 (m2) 的主题配色。

## 层级结构

```
底板层 (Base Layer) - 第0层
  ↓ dialogBase / sheetBase / panelBase / windowBase
  
卡片层 (Card Layer) - 第1层
  ↓ card / cardStrong / cardSelected / accentSoft
  
凹槽层 (Inset Layer) - 第2层
  ↓ inset
```

## 底板类型（第0层）

所有底板在白天模式使用纯白色，黑夜模式使用深色底板。

| 类型 | 用途 | 白天模式 | 黑夜模式 |
|------|------|---------|---------|
| `dialogBase` | Dialog 弹窗底板 | `Color.White` | `surfaceElevated` |
| `sheetBase` | Sheet 侧边栏/底部面板 | `Color.White` | `surfaceElevated` |
| `panelBase` | Panel 功能面板 | `Color.White` | `surfaceElevated` |
| `windowBase` | Window 主窗口背景 | `Color.White` | `surface0` |

### 使用示例

```kotlin
// Dialog 弹窗（容器类 → 12.dp）
Surface(
    color = WwSurfaces.dialogBase,
    shape = RoundedCornerShape(12.dp)
) {
    // 弹窗内容
}

// 主窗口背景
Box(modifier = Modifier.background(WwSurfaces.windowBase)) {
    // 窗口内容
}
```

## 卡片层（第1层）

卡片层接入**模块1 (m1)**，随主题主色变化。

### 配色规则

- **白天模式**：使用 m1-100 色阶，并降低饱和度 70%，形成极浅的主题色
- **黑夜模式**：使用 m1-950 色阶，60% 透明度

| 类型 | 用途 | 配色 |
|------|------|------|
| `card` | 标准卡片底色 | m1-100 (亮) / m1-950/60 (暗) |
| `cardStrong` | 强调卡片（已统一为 card） | 同 `card` |
| `cardSelected` | 选中/聚焦卡片（已统一为 card） | 同 `card` |
| `accentSoft` | 整块高亮底色（已统一为 card） | 同 `card` |

### 使用示例

```kotlin
// 时间线卡片
Card(
    colors = CardDefaults.cardColors(
        containerColor = WwSurfaces.card
    ),
    border = BorderStroke(1.dp, WwSurfaces.cardBorder)
) {
    // 卡片内容
}
```

## 凹槽层（第2层）

凹槽层接入**模块2 (m2)**，随主题辅助色变化。

### 配色规则

- **白天模式**：使用 m2-100 色阶，并降低饱和度 40%，比卡片层更深
- **黑夜模式**：使用 m2-950 色阶，60% 透明度

| 类型 | 用途 | 配色 |
|------|------|------|
| `inset` | 卡片内的输入框、标签框等次级容器 | m2-100 (亮) / m2-950/60 (暗) |

### 使用示例

```kotlin
// 输入框容器（控件类 → 10.dp）
Box(
    modifier = Modifier
        .background(
            color = WwSurfaces.inset,
            shape = RoundedCornerShape(10.dp)
        )
        .border(
            width = 1.dp,
            color = WwSurfaces.cardBorderSubtle,
            shape = RoundedCornerShape(10.dp)
        )
) {
    // 输入控件
}
```

## 圆角规范（两档制，2026-10-12 统一）

全项目圆角只有**两档字面值 + 胶囊**，新代码直接写 `10.dp` / `12.dp`，不要引入其它值：

| 档位 | 用在哪 | 例子 |
|------|--------|------|
| `RoundedCornerShape(10.dp)` | **控件类**：按钮、输入框、搜索框、下拉触发器、列表行背景、小卡片、标签（WwTag 默认值）、图片裁切、弹窗内工具条 | `SearchField`、`WwFormControls`、`SidebarSheet` |
| `RoundedCornerShape(12.dp)` | **容器类**：弹窗、下拉浮层、聊天气泡、面板卡片、输入框大容器、底部抽屉顶角（topStart/topEnd） | `MessageBubble`、`FeatureDialog`、`WindowFunctionSheet` |
| `RoundedCornerShape(100.dp)` | **胶囊**：chip、徽章、圆形/药丸按钮（有意的全圆，不参与两档制） | `TimelineManagerDialog` 的筛选 chip |

要点：

1. 旧值 4/6/8.dp 一律提到 10.dp；14/16/20/24.dp 一律收到 12.dp；显式 `0.dp` 直角也改 10.dp。
2. `DesignTokensDp.RADIUS_SM/MD/LG/XL`（8/12/16/20）是 Web 版 `--radius-*` 的直译存档，**与两档制不完全对齐**（没有 10 这档），新代码不要用它们，直接写字面值。
3. 极小的装饰元素（如 6dp 高的示意条）写 10.dp 会被高度截断成胶囊，属预期效果，不用特殊处理。

### 迁移记录（2026-10-12）

一次性把 4/6/8/14/16/24/0.dp 全部收敛到 10/12 两档，涉及约 20 个文件（WorldPanel、TimelineManagerDialog、SettingsPanels、DualBoxPromptDialog、ChatInputBar 等）；`WwTag` 默认 shape 4.dp → 10.dp。收敛后全项目 `RoundedCornerShape` 字面值只剩 10/12/100 三种。

## 描边颜色

| 类型 | 用途 | 白天模式 | 黑夜模式 |
|------|------|---------|---------|
| `cardBorder` | 卡片/面板主描边 | m1-200 (淡色主题勾线) | 混合主色 45% |
| `cardBorderSubtle` | 次级凹槽描边 | m1-100 (更淡) | 混合主色 35% |

## 白天模式低饱和度策略

为了让颜色在白色底板上更加柔和，系统在白天模式下自动降低饱和度：

| 层级 | 模块 | 降饱和度比例 | 效果 |
|------|------|-------------|------|
| 卡片层 | 模块1 | 70% | 极浅的主题色，柔和 |
| 凹槽层 | 模块2 | 40% | 稍深的辅助色，形成层次对比 |

### 实现原理

通过 `desaturate()` 函数降低 OKLCH 颜色空间的彩度 (Chroma)：

```kotlin
private fun desaturate(oklchString: String, factor: Double): Color? {
    val channels = parseOklchChannels(oklchString) ?: return null
    val newChroma = channels.c * factor
    return OklchColorMath.toSrgbInt(channels.l, newChroma, channels.h)
        .let { Color(it) }
}
```

## 模块配色系统

### 模块1 (m1)
- **角色**：主题主色
- **用途**：用户消息气泡、主按钮、卡片层底色
- **色阶**：11 档（50, 100, 200, 300, 400, 500, 600, 700, 800, 900, 950）

### 模块2 (m2)
- **角色**：主题辅助色（彩度为模块1的 40%）
- **用途**：次级按钮、徽章、凹槽层底色
- **色阶**：11 档（同模块1）

### 预设主题族

- **emerald** - 翠绿色系
- **blue** - 蓝色系
- **violet** - 紫罗兰色系
- **rose** - 玫瑰色系
- **amber** - 琥珀色系
- **cyan** - 青色系
- **slate** - 石板灰色系

## 迁移记录

### T27 底板统一管理（2026-10-12）

**背景**：原先各文件直接使用 `WwTheme.colors.surfaceElevated` 和 `surface0`，缺乏语义化管理。

**改动**：
1. 新增底板四类型：`dialogBase` / `sheetBase` / `panelBase` / `windowBase`
2. 迁移 14 个文件到新底板系统
3. 白天模式统一为纯白底板

**迁移文件**：
- Dialog 类（11处）：TimelineManagerDialog, TimelineGuideDialog, TimelineEditorDialog, FeatureDialog, PreferencesPanels, WwFormControls, InfoHint, WorldPanel (2处), ChatInputBar 下拉菜单
- Sheet 类（2处）：WindowFunctionSheet, SidebarSheet
- Window 类（1处）：ChatInputBar 输入框容器

### T27 卡片层接入模块1（2026-10-12）

**改动**：
1. `card`、`cardStrong`、`cardSelected`、`accentSoft` 统一接入模块1
2. 白天模式降饱和度 70%，让卡片在白底板上更柔和
3. 黑夜模式保持原始饱和度

**原配色方案（已弃用）**：
- `card`：`mix(surfaceElevated, primaryBg, 0.06f)` - 掺入 6% 主色
- `cardStrong`：掺入 12% 主色
- `cardSelected`：掺入 18% 主色
- `accentSoft`：掺入 24% 主色

### T27 凹槽层接入模块2（2026-10-12）

**改动**：
1. `inset` 接入模块2
2. 白天模式降饱和度 40%，比卡片层更深，形成层次
3. 黑夜模式保持原始饱和度

**原配色方案（已弃用）**：
- `inset`：`mix(surface0, primaryBg, 0.10f)` - 掺入 10% 主色

## 模态遮罩（毛玻璃，2026-10-13）

| 用途 | API | 取值 |
|---|---|---|
| 全应用唯一的模态遮罩 | `WwSurfaces.scrim` | 亮色：白 35%；暗色：`surfaceElevated` 35%（半透明，**不压黑**） |
| 遮罩下主内容的模糊半径 | `WwSurfaces.scrimBlurRadius` | 8dp |
| 铺遮罩（含「点外面关闭」+ 模糊登记） | `WwModalScrim(onDismiss = …)` | `ui/components/WwModalScrim.kt` |

- 改前 7 处弹窗各写各的：`Color.Black.copy(alpha = 0.5f)`（侧边栏抽屉 / 窗口功能区）、
  `colorScheme.scrim.copy(alpha = 0.6f / 0.75f / 0.8f / 0.5f)`（时间线三兄弟 / 图片预览 / 功能弹窗）。
- **模糊不在遮罩里做**：弹窗是桌面上的**独立窗口**，拿不到下层像素，`Modifier.blur` 糊自己那层平色等于没糊。
  `WwModalScrim` 在组合里登记计数（`ModalScrimCount`），`App` 读到 > 0 就把主内容整体 `Modifier.blur(…)`。
  所以新弹窗**必须经 `WwModalScrim` 铺遮罩**，否则「别人都糊、它不糊」。
- 验收用例：[`ScrimFrostTest.kt`](../shared/src/desktopTest/kotlin/com/watchworld/ui/theme/ScrimFrostTest.kt)
  （① 遮罩不许把底板压暗；② 桌面端 `Modifier.blur` 真的糊得出渐变，不是 no-op）。
- 两个**不是遮罩**的地方，别顺手改：`ChatInputBar` 里附图缩略图右上角那个 16dp ✕ 圆底是图标衬底；
  `WorldPanel` 的「改父级」弹层是平台默认宽度的小窗，本来就没遮罩。

## 使用纪律

### ✅ 推荐做法

1. **新代码统一走 `WwSurfaces`**，不要直接用 `WwTheme.colors.surfaceElevated`
2. **语义化选择**：
   - 弹窗底板 → `dialogBase`
   - 底板上的卡片 → `card`
   - 卡片内的输入框 → `inset`
3. **描边搭配底色**：
   - 卡片用 `cardBorder`
   - 凹槽用 `cardBorderSubtle`

### ❌ 避免做法

1. 不要混用不同场景的底板（如用 `windowBase` 做弹窗底板）
2. 不要手动计算颜色混合，统一走 `WwSurfaces` 提供的语义化属性
3. 不要在亮色模式手动处理低饱和度，系统已自动处理

## 代码位置

- **主文件**：[WwSurfaces.kt](e:/WatchWorld/WatchWorld-Native/shared/src/commonMain/kotlin/com/watchworld/ui/theme/WwSurfaces.kt)
- **主题配置**：[WwTheme.kt](e:/WatchWorld/WatchWorld-Native/shared/src/commonMain/kotlin/com/watchworld/ui/theme/WwTheme.kt)
- **模块色阶**：[ThemeVars.kt](e:/WatchWorld/WatchWorld-Native/shared/src/commonMain/kotlin/com/watchworld/ui/theme/ThemeVars.kt)
- **角色串解析**：[themeRoles.kt](e:/WatchWorld/WatchWorld-Native/shared/src/commonMain/kotlin/com/watchworld/ui/theme/themeRoles.kt)
- **OKLCH 数学**：[OklchColorMath.kt](e:/WatchWorld/WatchWorld-Native/shared/src/commonMain/kotlin/com/watchworld/ui/theme/OklchColorMath.kt)

## 测试验证

建议在以下场景验证主题效果：

1. **切换主题**：emerald → rose → violet，观察卡片和凹槽的配色变化
2. **切换模式**：白天 ↔ 黑夜，验证底板纯白和低饱和度效果
3. **嵌套层次**：在白底板上放卡片，卡片内放输入框，验证三层对比度
4. **边缘情况**：
   - 自定义主题（用户选色）
   - 极端饱和度主题
   - 灰度主题（slate）

## 未来扩展

可能的优化方向：

1. **第3层**：如需更深层次的嵌套容器，可考虑添加 `insetDeep` 接入更低饱和度的配色
2. **描边变体**：可添加 `cardBorderStrong` 用于强调状态的描边
3. **动态饱和度**：根据用户偏好设置调整降饱和度比例
4. **无障碍模式**：提供高对比度配色方案
