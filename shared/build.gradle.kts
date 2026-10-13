val jvmTarget = libs.versions.jvmTarget.get().toInt()

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    // T07 存储层：Room KMP。KSP 只在编译期生成 DAO/表的实现，不进任何源集。
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

kotlin {
    // 桌面目标命名为 desktop，因此源集是 desktopMain / desktopTest
    // （FILE_LAYOUT §3 与 PLAN §3 都按这个命名写；Android 目标在 T22 用 android { } 加进来）
    jvm("desktop")

    jvmToolchain(jvmTarget)

    sourceSets {
        val desktopMain by getting

        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.icons.tabler)
            implementation(libs.compose.ui)

            // T13 Markdown 流式渲染（决策 #2 定案）：核心 + M3 主题模块。
            // 0.42+ 的 StreamingMarkdownState 是追加式的，只重解析不稳定尾部（PLAN §7.3）。
            implementation(libs.compose.markdown)
            implementation(libs.compose.markdown.m3)

            // T03：协议层。ktor-client-core 是 common（引擎无关），
            // 具体引擎按平台在各自源集提供（见 desktopMain 的 okhttp）。
            implementation(libs.ktor.client.core)
            implementation(libs.kotlinx.serialization.json)

            // T07：存储层。room-runtime 是 KMP 产物，SQLiteDriver 接口由它带出来；
            // 驱动实现（bundled）在平台源集，见 desktopMain。
            implementation(libs.androidx.room.runtime)
            implementation(libs.kotlinx.coroutines.core)
        }

        // T04 起：SSE 报文 / v5 存档的解析单测（fixture 路径见 FILE_LAYOUT §3）
        // T07 起：存档 ↔ SQLite 往返单测
        commonTest.dependencies {
            implementation(kotlin("test"))
        }

        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            // 统一 OkHttp（PLAN §2.3）：桌面调通的行为，Android 上几乎不变，T22 沿用
            implementation(libs.ktor.client.okhttp)
            // 自带原生 SQLite 的驱动（PLAN §2.2）。T22 加 Android 时同样用它，行为一致。
            implementation(libs.androidx.sqlite.bundled)
        }

        val desktopTest by getting {
            dependencies {
                implementation(libs.ktor.client.mock)
            }
        }
    }
}

// KSP 的配置名跟着 KMP 目标名走：目标叫 desktop → 配置叫 kspDesktop。
// ⚠️ 不是 kspCommonMainMetadata / kspJvm —— 后者会静默不生成代码，报错却出现在 DAO 用不了的地方。
dependencies {
    add("kspDesktop", libs.androidx.room.compiler)
}

// 把每次的表结构导出到 shared/schemas/*.json（要提交）。将来改表时用它生成 Migration 并写迁移测试。
room {
    schemaDirectory("$projectDir/schemas")
}
