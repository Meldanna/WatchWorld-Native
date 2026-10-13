package com.watchworld.ui.sidebar

/**
 * 桌面（Windows / macOS / Linux）：**停靠**。
 *
 * 「windows版本侧边栏固定展开，不影响窗口」—— 桌面窗口够宽，侧边栏直接占一列，
 * 内容区让出宽度，比浮层盖住对话好得多。
 */
actual val defaultSidebarLayout: SidebarLayout = SidebarLayout.DOCKED
