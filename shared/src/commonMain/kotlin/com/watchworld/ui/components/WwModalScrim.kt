package com.watchworld.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.watchworld.ui.theme.WwSurfaces

/**
 * **当前有几个模态遮罩在组合里**：遮罩自己挂载 +1 / 卸载 -1，`App` 读它决定主内容是否模糊。
 *
 * ⚠️ 类型是 `MutableState<Int>` 而不是 `Int`：遮罩挂载 / 卸载必须**立刻**触发主内容重组，
 *    否则弹窗亮起的那一帧主内容还是清晰的、下一帧才糊，会看到一次跳变。
 *
 * ⚠️ 用模块级单例而不是 `CompositionLocal`：取值方（`App` 的主内容 `Column`）与写入方
 *    （各弹窗，两处调用点）分居同一棵组合树的两头，而全应用只有**一个**主窗口，
 *    没有「同一进程里两套主界面」的场景需要隔离。
 *    计数是配对的（`DisposableEffect` 的 `onDispose` 一定跑），组合销毁后归零。
 */
val ModalScrimCount: MutableState<Int> = mutableStateOf(0)

/**
 * 全应用**唯一的模态遮罩**（用户 2026-10-13：「检查遮罩，所有的遮罩，把它们统一款式，
 * 毛玻璃效果，不用压黑」）。
 *
 * ## 它做三件事
 * 1. 铺 [WwSurfaces.scrim]（半透明主题表面色，不压黑）—— 改前 7 个弹窗各写各的黑，
 *    深浅从 0.5 到 0.8 都有；
 * 2. 「点外面关闭」（`indication = null`，不显示涟漪）；
 * 3. **登记自己**（[ModalScrimCount] +1），让主内容知道该做毛玻璃模糊了。
 *
 * ## 为什么模糊不在这里做
 * 这里的每个弹窗都是 `Dialog` = 桌面上的**独立窗口**，窗口里拿不到下层窗口的像素，
 * `Modifier.blur` 只能糊自己那层平色（等于没糊）。所以模糊交给**主窗口**：
 * `App` 读到计数 > 0 就把遮罩下那层主内容整体 `blur`，本组件只负责那层薄雾。
 *
 * ⚠️ 新弹窗一律用它铺遮罩，别再手写 `background(Color.Black.copy(...))` ——
 *    手写的既不统一、也不参与登记（结果就是「别人都糊，它不糊」）。
 *
 * ## 用法
 * ```
 * Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
 *     Box(modifier = Modifier.fillMaxSize()) {
 *         WwModalScrim(onDismiss = onDismiss)
 *         // 面板叠在遮罩之上
 *     }
 * }
 * ```
 */
@Composable
fun WwModalScrim(onDismiss: () -> Unit) {
    DisposableEffect(Unit) {
        ModalScrimCount.value += 1
        onDispose { ModalScrimCount.value -= 1 }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(WwSurfaces.scrim)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
    )
}
