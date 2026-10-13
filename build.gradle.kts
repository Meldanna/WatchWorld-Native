plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    // T07 起：Room KMP 的 KSP 代码生成 + 表结构导出
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
}
