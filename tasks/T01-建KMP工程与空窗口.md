# T01 · 建 KMP 工程与空窗口

| | |
|---|---|
| 任务号 | T01 |
| 日期 | 2026-10-06 |
| 状态 | ✅ 完工 |
| 阶段 | 阶段 0 · 能跑起来 |
| 上位清单 | [`WORKPLAN.md`](../WORKPLAN.md) §二 T01 |

## 目标与可见效果

- **目标**：搭起 KMP + Compose Multiplatform 的 Gradle 工程骨架，桌面端能起一个窗口
- **可见效果**：`./gradlew :desktopApp:run` 弹出一个标题为 `WatchWorld Native` 的 Windows 窗口
- **验收标准**：窗口能出；版本固定为 Compose MP 1.12.1 / Ktor 3.6.0（PLAN §2.1）

## 改动

| 文件 | 改动 |
|---|---|
| `gradlew` `gradlew.bat` `gradle/wrapper/gradle-wrapper.jar` | 从现有 Capacitor 工程 `android/` 复制（本机无全局 gradle 命令，必须靠 wrapper 自举） |
| `gradle/wrapper/gradle-wrapper.properties` | **新增**，锁 Gradle 8.14.4 |
| `gradle/libs.versions.toml` | **新增**，版本唯一来源：Kotlin 2.4.20 / Compose MP 1.12.1 / material3 1.9.0 / jvmTarget 21 |
| `settings.gradle.kts` | **新增**，`rootProject.name = "WatchWorld-Native"`；include `:shared`（`:androidApp` 留到 T22）；pluginManagement 与 dependencyResolutionManagement 都只放 gradlePluginPortal + google + mavenCentral |
| `build.gradle.kts` | **新增**，四个插件全部 `apply false`，版本只在 catalog 里写一次 |
| `gradle.properties` | **新增**，`-Xmx4g`、`file.encoding=UTF-8`、caching/parallel 开、configuration-cache 暂关 |
| `shared/build.gradle.kts` | **新增**，KMP 模块；只声明 `jvm("desktop")` 一个目标；`jvmToolchain(21)`；commonMain 依赖 Compose 四件套，commonTest 依赖 `kotlin("test")` |
| `shared/src/commonMain/kotlin/com/watchworld/ui/App.kt` | **新增**，根组合 `App()`，当前是占位文案，后续任务逐层替换 |
| `desktopApp/build.gradle.kts` | **新增**，JVM 启动器模块；`mainClass` + `nativeDistributions`（T23 细化）+ **渲染后端开关 `-PwwRender`** |
| `desktopApp/src/main/kotlin/com/watchworld/desktop/Main.kt` | **新增**，`application { Window { App() } }`，窗口 1100×720 |

## 心得

- **桌面目标必须叫 `desktop`，不能叫 `jvm`**。`jvm("desktop")` 才会生成 `desktopMain/` 源集 —— `docs/FILE_LAYOUT.md` §3 和 PLAN §3 都是按 `desktopMain` 写的。若图省事写 `jvm()`，源集就变成 `jvmMain`，以后照文档找文件会找不到。
- **KMP 模块能被普通 `kotlin("jvm")` 模块消费**：`desktopApp` 用 `implementation(project(":shared"))` 就够，不用额外声明属性或 `withJava()`。KMP 插件会给产物打 `org.jetbrains.kotlin.platform.type=jvm` 属性，两边自动对上。
- **不要用 `compose.runtime` 这类 DSL 访问器**（试过，见下）。Compose MP 1.12.1 对它们报 `'runtime: String' is deprecated. Specify dependency directly`。但**也不能**简单地把版本统一成插件版本 —— 见下一条踩坑。
- **Compose 的产物版本是分裂的**，这是本次最大的坑：
  - `compose.runtime` → `org.jetbrains.compose.runtime:runtime:1.12.1`
  - `compose.foundation` / `compose.ui` → 1.12.1
  - `compose.material3` → **`org.jetbrains.compose.material3:material3:1.9.0`** ← 独立版本线！
  
  我一开始把四个都写成 `version.ref = composeMultiplatform`，构建直接挂：`Could not find org.jetbrains.compose.material3:material3:1.12.1`。**正确做法是先让 DSL 跑通一次，再用 `:shared:dependencies` 把真实版本抄下来**，不要凭插件版本猜。
- **`compose.desktop.currentOs` 保留 DSL，不改成写死坐标**。它要按当前操作系统选 `desktop-jvm-windows-x64` 之类的产物，写死就丢掉了跨平台性，而且它**没有**被弃用。
- **Gradle 必须 ≥ 8.14.4**。本机已有 8.14.3 的完整缓存，直接用能离线跑、最省事，但 Kotlin 插件会警告："The minimum supported Gradle version will become Gradle 8.14.4 in Kotlin 2.5.0"。既然升 Kotlin 是迟早的事，且此刻升级只需一次下载、零改动，就当场升到 8.14.4（本机另有 gradle-9.6.0 但只有 `.part` 半包，没用）。
- **`kotlin("test")` 一开始被我放进了 `commonMain`** —— 那是错的，测试库会跟着打进生产产物。已挪到 `commonTest`。这类"能编过但是错"的问题编译器不会提醒。
- **验证 GUI 别只看退出码**。`gradlew run` 是阻塞的，超时被杀也返回"失败"，而成功运行时日志会停在 `> Task :desktopApp:run` 这一行不动。真正可靠的判据是**查窗口标题**：
  ```bash
  MSYS_NO_PATHCONV=1 /c/Windows/System32/tasklist.exe /V /FI "IMAGENAME eq java.exe" | grep -i "WatchWorld Native"
  ```
  ⚠️ Git Bash 会把 `/V` 当成路径改写成 `V:\`，不加 `MSYS_NO_PATHCONV=1` 会静默失效 —— 我因此白跑了一轮"没找到窗口"的假阴性。
- **发现一个本机硬伤**：skiko 在 Windows 上的默认渲染后端（Direct3D）建窗时**硬崩**，退出码 `-1073740791` = `0xC0000409`（STATUS_STACK_BUFFER_OVERRUN），**不打印任何异常、不留 hs_err 文件**。实测 `OPENGL` 与 `SOFTWARE` 都正常。这类崩溃看起来像"代码写错了"，其实只是渲染后端挑环境，别在业务代码里找原因。

## 要点

- **版本只改 `gradle/libs.versions.toml` 一处**。`composeMaterial3` 与 `composeMultiplatform` 是两条独立版本线，升级 Compose MP 时**必须**重新核对 material3 版本，方法：
  ```bash
  ./gradlew :shared:dependencies --configuration desktopCompileClasspath
  ```
- **Kotlin 2.4.20 的兼容边界**：Gradle `7.6.3–9.7.0`，AGP `8.5.2–9.3.1`（查 kotlinlang 的 multiplatform-compatibility-guide）。T22 加 Android 目标时要按这个表选 AGP。
- **Gradle 版本在 `gradle/wrapper/gradle-wrapper.properties`**，本机缓存目录 `~/.gradle/wrapper/dists/`。改版本号后会重新下载整个发行包。
- **`jvmTarget = "21"`**，来自 `libs.versions.toml`；两个模块都用 `jvmToolchain(jvmTarget)`。本机 JDK 是 Temurin 21.0.12，所以不会触发 toolchain 下载。
- **渲染后端开关**：`-PwwRender=opengl|software|direct3d|default`，**不传时为 `opengl`**。值 `default` 表示不加任何 `-Dskiko.renderApi`，用 skiko 原生默认。推导出的 JVM 参数也会被 T23 的 jpackage 打包带上。
- **`compose.desktop.application` 只放在 `desktopApp`**，`shared` 里不放 —— 这样 Android 端不会莫名其妙带进桌面启动器依赖。
- **`App()` 是所有界面的唯一入口**，桌面和（T22 的）Android 都从这里进。两个平台的 `Main` 都只允许出现"平台外壳"代码。
- **`settings.gradle.kts` 里没有 `:androidApp`**，T22 才加。现在 `local.properties` 还不存在（没有 Android 目标就不需要）。
- 工程树（截至 T01，不含 build 产物）：
  ```
  gradlew gradlew.bat gradle/wrapper/* gradle/libs.versions.toml
  settings.gradle.kts build.gradle.kts gradle.properties
  shared/build.gradle.kts
  shared/src/commonMain/kotlin/com/watchworld/ui/App.kt
  desktopApp/build.gradle.kts
  desktopApp/src/main/kotlin/com/watchworld/desktop/Main.kt
  ```

## 验收结果

- [x] `./gradlew :desktopApp:build` 成功，**零警告**（Deprecated Gradle Version 警告已随 8.14.4 消除）
- [x] `./gradlew :desktopApp:run`（裸命令，无环境变量）弹出窗口，`tasklist /V` 可见窗口标题 `WatchWorld Native`
- [x] app 进程内 `AWT-EventQueue-0` 与 `skiko-dispatcher` 线程存活，确认是真的在渲染而非卡死
- [x] 版本固定 Compose MP 1.12.1（Ktor 3.6.0 在 T03 接入时再验证）
- [x] 现有 React 仓 `E:\WatchWorld\WatchWorld\` 零改动

## 遗留问题 / 下一步

- **Ktor 3.6.0 还没引入**，等 T03 接真实 Provider 时再加，届时验证该版本存在。
- **渲染后端待真机确认**：本机默认 D3D 崩溃，已默认切 OPENGL。若在别的机器上 OPENGL 也异常，用 `-PwwRender=software`。若真机 GPU 正常且想要更高帧率，用 `-PwwRender=default` 对比一下。
- `gradle.properties` 里 `org.gradle.configuration-cache=false` 是保守设置，等工程稳定后再考虑打开。
- 窗口尺寸 1100×720 是临时值，T12/T14 做布局时按 React 版实际尺寸改。
- T02 起开始写界面；`App.kt` 现在那两行占位文案会被替换掉。

## 相关红线

**无**（本任务只建骨架，未触碰 `MIGRATION_CONSTRAINTS.md` 的存档字段名 / SSE 语义 / 存储键前缀 / system 字符串）。

唯一需留意的是 PLAN §2.1 的版本锁定：Compose MP 1.12.1 已按锁定的版本落地；Ktor 3.6.0 待 T03。
