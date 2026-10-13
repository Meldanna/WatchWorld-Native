rootProject.name = "WatchWorld-Native"

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

// 目标结构见 docs/FILE_LAYOUT.md §3
// androidApp 计划在 T22 加入
include(":shared")
include(":desktopApp")
