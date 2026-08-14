import org.gradle.kotlin.dsl.invoke

plugins {
    id("java")
    id("org.jetbrains.intellij") version "1.17.2"
}

group = "org.plugin.window"
version = "1.0.0"

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
        // 兼容的 IDE 版本范围
        sinceBuild.set("233")
        untilBuild.set("262.*")
    }
}