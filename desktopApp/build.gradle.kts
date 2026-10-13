import org.jetbrains.compose.desktop.application.dsl.TargetFormat

val jvmTarget = libs.versions.jvmTarget.get().toInt()

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

// 桌面启动器：只负责「开窗口 + 调用 shared 的 App()」。
// 业务逻辑一律写在 shared，避免桌面端长出 Android 端拿不到的分支。
// jpackage 打包（.exe）在 T23 细化，这里先给出最小可用配置。
dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
}

kotlin {
    jvmToolchain(jvmTarget)
}

compose.desktop {
    application {
        mainClass = "com.watchworld.desktop.MainKt"

        // 渲染后端开关（背景见 memory/gotchas.md「渲染器」）——
        // 本机（Windows 11 + Temurin 21）用 skiko 默认的 Direct3D 后端会在建窗时硬崩，
        // 退出码 -1073740791 = 0xC0000409，且不打印任何异常。实测 OPENGL / SOFTWARE 正常。
        //
        //   ./gradlew :desktopApp:run                     → 默认 opengl（本机可用）
        //   ./gradlew :desktopApp:run -PwwRender=software → 显卡驱动异常时的最保底选择
        //   ./gradlew :desktopApp:run -PwwRender=default  → 回到 skiko 原生默认（真机 GPU 正常时最快）
        val renderApi = providers.gradleProperty("wwRender").getOrElse("opengl")
        if (renderApi != "default") {
            jvmArgs += "-Dskiko.renderApi=${renderApi.uppercase()}"
        }

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "WatchWorld"
            packageVersion = "1.0.0"
        }
    }
}
