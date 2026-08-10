pluginManagement {
    repositories {
        // 国内镜像（快）；网络受限时取消注释 aliyun 即可
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        maven("https://maven.aliyun.com/repository/public")
        mavenCentral()
        // JsoupXpath 等部分库在 jitpack（无国内镜像，保持官方）
        maven("https://jitpack.io")
    }
}

rootProject.name = "legado-desktop-backend"
