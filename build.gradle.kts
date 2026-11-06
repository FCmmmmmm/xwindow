import org.gradle.kotlin.dsl.invoke

plugins {
    id("java")
    id("org.jetbrains.intellij") version "1.17.2"
}

group = "org.plugin.window"
version = "1.0.1"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.cronutils:cron-utils:9.2.1")
}

intellij {
    // 指定测试环境使用 GoLand 的版本
    version.set("2024.2")
    type.set("GO") // GO 代表 GoLand
}



java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks {
    patchPluginXml {
        // 只声明下限：从 233 起的所有新版本 IDE 都允许加载
        sinceBuild.set("233")
        // 显式置空 = 不写入 until-build 属性（不再有版本上限），无需随 IDE 发版手动改
        untilBuild.set(provider { null })
    }
}