import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("multiplatform") version "2.4.10"
    id("org.jetbrains.compose") version "1.11.1"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10"
    kotlin("plugin.serialization") version "2.4.10"
}

group = "io.legado.desktop"
version = "0.1.0"

kotlin {
    // Part 7：只启用 Desktop (JVM) target（引擎依赖全部 JVM 专属，iOS/Web 不可行）
    jvm("desktop")

    sourceSets {
        val desktopMain by getting

        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.materialIconsExtended)
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
        }

        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.11.0")
        }
    }
}

compose.desktop {
    application {
        mainClass = "io.legado.desktop.app.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Dmg, TargetFormat.Deb)
            packageName = "LegadoDesktop"
            packageVersion = "0.1.0"
            // 打包运行时 jlink 默认只含基础模块，必须显式补 java.net.http
            // （前端 ApiClient/SearchClient 用 java.net.http.HttpClient / WebSocket，缺则 NoClassDefFoundError）
            modules(
                "java.base",
                "java.datatransfer",
                "java.xml",
                "java.prefs",
                "java.desktop",
                "java.logging",
                "jdk.crypto.ec",
                "java.net.http",
            )
        }
    }
}
