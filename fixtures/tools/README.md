# WatchWorld 桌面端 E2E 验证脚本(主题系统回归用)

> T12 主题系统(2026-10-08)随任务固化。用途:启动桌面应用后,截取真实窗口并做
> **像素级校验** —— 主题色是否按 oklch 渲染、M3 默认紫是否真的消失。
> 为什么像素校验:快照对拍只能证「字符串等值」,证不了「渲染出来的颜色与 Web 一致」;
> 浏览器是 ground truth(E2E 实测 emerald-600 v4 oklch → `#009966`,与 DevTools 一致)。

## 前提

- 应用经 `./gradlew :desktopApp:run` 启动(窗口标题精确等于 `WatchWorld Native`)。
- 本机 skiko 默认 Direct3D 建窗即崩,`desktopApp` 已默认 OPENGL 后端(`-PwwRender=software` 兜底)。
- 仅 Windows(依赖 Win32 `GetWindowRect` / GDI+ 截屏)。
- 截的是**前台**窗口:脚本已用 `WScript.Shell.AppActivate` 强制前置
  (仅 `SetForegroundWindow` 会被系统前台锁拦掉,截到终端 —— 踩过的坑)。

## 脚本

| 脚本 | 用途 | 产出(落 `build/`,已被 clean 清理) |
|---|---|---|
| `capture-ww.ps1` | 前置窗口 → 截图 → 采样关键位置像素 + 主色直方图。最基础的一步,肉眼走查也用它 | `build/ww-theme-verify2.png` |
| `verify-theme.ps1` | 等窗口出现 → 截图 → 数像素:emerald 主色 / M3 默认紫 / surface 背景;`RESULT: PASS/CHECK` 收尾 | `build/ww-theme-verify.png` |
| `type-and-capture.ps1` | 点击输入框聚焦 → SendKeys 键入 → 截图。让「发送」按钮变绿(启用态)再校验,顺带回归 T03 流式链路 | `build/ww-theme-typed.png` |

## 用法

```bash
# 1) 启动应用(另一终端或后台)
./gradlew :desktopApp:run

# 2) 空界面基础校验(背景 + 主题紫未出现)
powershell -NoProfile -ExecutionPolicy Bypass -File fixtures/tools/verify-theme.ps1

# 3) 键入后再校验(主色出现 + 默认紫 0 像素 → PASS)
powershell -NoProfile -ExecutionPolicy Bypass -File fixtures/tools/type-and-capture.ps1
```

## 判定基准(emerald 主题 / 浅色模式)

| 检查项 | 期望像素色 | 来源 |
|---|---|---|
| 窗口背景 | `#F8FAFC` | `DesignTokens.surface1Light`(M3 默认是紫调白 `#FEF7FF`,出现即失败) |
| 用户气泡 / 启用的发送按钮 | `#009966` | emerald-600 的 **v4 oklch 浏览器渲染值**(≠ v3 hex `#059669`,见 `tasks/T12-主题系统.md` 心得 1) |
| M3 默认紫 `#6750A4` | **0 像素** | 双轨①生效的反向证据 |

换主题/明暗时,先从 `fixtures/theme-baseline/snapshots.json` 取该主题 600 档 oklch 串,
用 `OklchVectorTest` 同款换算(或 DevTools)求出期望 RGB 再改脚本里的比对值。

## 已知边界

- `CopyFromScreen` 截的是屏幕合成结果:窗口被遮挡的部分会截到遮挡物;脚本已做前置,但仍不要在截图时手动覆盖窗口。
- DPI:脚本已 `SetProcessDPIAware`,坐标为物理像素;多显示器跨屏时窗口可能被裁。
- **深色模式不在本目录窗口脚本的覆盖范围内**(脚本按浅色 emerald 判定,且 App 跟随系统主题)。深色改走**无头离屏截图用例**:`shared/src/desktopTest/.../theme/DarkBubbleScreenshotTest.kt` —— 用 `ImageComposeScene` 把 DARK 下的真实 `MessageBubble` 渲染成位图,断言 AI 气泡是约 90% 不透明深灰(不是透明)。不弹窗、不注入键盘、可进 CI。
- SendKeys 会真的把字打进焦点控件:跑 `type-and-capture.ps1` 前确认焦点会被脚本接管。
