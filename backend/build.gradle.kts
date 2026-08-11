plugins {
    kotlin("jvm") version "2.4.10"
    application
}

group = "io.legado.desktop"
version = "0.1.0"

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("io.legado.desktop.MainKt")
    // OSR（离屏渲染）模式需要 JOGL 的 --add-exports（JCEF/jcefmaven 要求，JDK16+）
    applicationDefaultJvmArgs = listOf(
        "--add-exports", "java.base/java.lang=ALL-UNNAMED",
        "--add-exports", "java.desktop/sun.awt=ALL-UNNAMED",
        "--add-exports", "java.desktop/sun.java2d=ALL-UNNAMED",
    )
}

// 版本对齐 legado gradle/libs.versions.toml（2026-08-10 snapshot）
dependencies {
    // Kotlin 标准库 / 协程
    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.11.0")

    // HTTP/WS 服务（与 legado 一致：NanoHTTPD）
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    implementation("org.nanohttpd:nanohttpd-websocket:2.3.1")

    // 网络层（与 legado 一致）
    implementation("com.squareup.okhttp3:okhttp:5.4.0")
    implementation("org.brotli:dec:0.1.2")

    // 规则引擎
    implementation("org.jsoup:jsoup:1.16.2") // 不要升级，legado 有破坏性变更说明
    implementation("com.jayway.jsonpath:json-path:3.0.0")
    implementation("cn.wanghaomiao:JsoupXpath:2.5.3")

    // 本地书籍解析（原版 vendored 库依赖）
    implementation("xmlpull:xmlpull:1.1.3.1")
    implementation("net.sf.kxml:kxml2:2.3.0")

    // JSON
    implementation("com.google.code.gson:gson:2.14.0")

    // 数据层：SQLite（替代 Android Room）
    implementation("org.xerial:sqlite-jdbc:3.50.3.0")

    // legado fork 的 Rhino JS 引擎（Maven Central 无此版本，本地 third_party）
    implementation(files("third_party/maven/org/htmlunit/htmlunit-core-js/5.3.0-legado.3/htmlunit-core-js-5.3.0-legado.3.jar"))

    // 工具
    implementation("org.json:json:20240303")
    implementation("cn.hutool:hutool-crypto:5.8.22")
    implementation("org.apache.commons:commons-text:1.13.1")
    implementation("com.github.liuyueyi.quick-chinese-transfer:quick-transfer-core:0.2.17")

    // WebView 引擎（JCEF，Part 7 T7.0）：chromiumembedded/java-cef 的 Maven 制品（natives 首次运行下载）
    implementation("me.friwi:jcefmaven:146.0.10")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
