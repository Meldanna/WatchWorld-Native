package com.watchworld.ui.sidebar

/**
 * 侧边栏的两种形态（用户 2026-10-13 定案）。
 *
 * 同一份面板内容，两种摆法 —— 桌面与移动端的诉求正好相反：
 * - [DOCKED]（桌面 / Windows）：**停靠**成主布局里的一列，把内容区挤窄。
 *   侧边栏始终可见、不盖住任何面板（用户原话：「windows版本侧边栏固定展开，不影响窗口」）。
 * - [DRAWER]（Android）：**抽屉**浮在内容之上，带毛玻璃遮罩、点遮罩收起。
 *   （用户原话：「Android版侧边栏默认收起，展开时会遮住大部分面板」—— 窄屏上这是没法避免的，
 *   所以保持浮层，只把默认状态定成收起。）
 *
 * 形态由平台定（见 [defaultSidebarLayout]），**不按窗口宽度自适应**：
 * 用户是按平台描述的诉求，桌面把窗口拖窄也仍然停靠。
 */
enum class SidebarLayout {
    /** 停靠：布局里的一列，不遮挡内容。 */
    DOCKED,

    /** 抽屉：浮层 + 遮罩，展开时盖住大部分内容。 */
    DRAWER,
}

/**
 * 平台默认的侧边栏形态：**桌面停靠、其余（Android）抽屉**。
 *
 * 按 `platform/` 的老规矩用 `expect/actual`（当前仓库只有 `desktopMain` 实现，
 * 与 `platformEnv` 等一样，Android 源集建起来时补 actual）。
 */
expect val defaultSidebarLayout: SidebarLayout

/**
 * 该形态下侧边栏**初始是否展开**：停靠默认展开（「固定展开」），抽屉默认收起（「默认收起」）。
 *
 * ⚠️ 只管**初始值**：`App` 用 `remember` 存着它，用户在界面上收起 / 展开后不再回读。
 *    要跨启动记住状态的话得落 kv（目前没做，用户没要求）。
 */
val SidebarLayout.startsExpanded: Boolean
    get() = this == SidebarLayout.DOCKED
