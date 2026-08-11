package io.legado.desktop.help.webView

import io.legado.desktop.env.DesktopEnv
import java.io.File

/**
 * JCEF WebView 引擎接入点（Part 7 T7.0）。
 *
 * 调用 [init] 后 [DesktopWebViewFactory.creator] 指向 JCEF 实现，WebViewPool/BackstageWebView
 * 即可创建真实 Chromium 浏览器。bundle（Chromium natives ~350MB）首次运行下载到 [bundleDir]。
 * 后端默认不启用（保持轻量）；`LEGADO_DESKTOP_ENABLE_JCEF=1` 或显式调用 [init] 启用。
 */
object CefWebView {

    @Volatile
    var initialized: Boolean = false
        private set

    /** bundle 目录：env LEGADO_DESKTOP_JCEF_BUNDLE 优先，否则 <数据目录>/jcef-bundle */
    val bundleDir: File
        get() = System.getenv("LEGADO_DESKTOP_JCEF_BUNDLE")?.let { File(it) }
            ?: File(DesktopEnv.homeDir.toFile(), "jcef-bundle")

    /** bundle 是否已下载（目录含 libcef/jcef natives） */
    fun bundleReady(): Boolean {
        return runCatching {
            bundleDir.listFiles()?.any { it.name.startsWith("libcef") || it.name.startsWith("jcef") } == true
        }.getOrDefault(false)
    }

    /** 初始化 JCEF 并接线工厂（幂等）。会触发 bundle 下载（未就绪时）。 */
    fun init(): Boolean {
        if (initialized) return true
        CefEnv.init(bundleDir)
        DesktopWebViewFactory.creator = { JcefDesktopWebView() }
        initialized = true
        return true
    }
}
