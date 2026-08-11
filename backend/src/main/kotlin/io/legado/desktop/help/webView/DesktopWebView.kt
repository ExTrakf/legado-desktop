package io.legado.desktop.help.webView

/**
 * 桌面 WebView 抽象（浏览器引擎适配层）。
 *
 * 对应原版 android.webkit.WebView；桌面版由 JCEF（Chromium Embedded Framework）实现，
 * 见本包与 docs/WEBVIEW-COMPOSE-PLAN.md T7.0（JCEF 接入与 offscreen 验证在独立步骤完成，
 * 本文件先定义契约，供 WebViewPool / BackstageWebView / WebJsExtensions 等纯逻辑编译与接线）。
 *
 * 线程约束：实现类需自行将调用分发到浏览器消息线程（如 CEF UI 线程），
 * 事件回调（onConsoleMessage/onPageFinished/onBeforeBrowse/onResourceLoad）在浏览器线程触发，
 * evaluateJavascript 的 onResult 也在浏览器线程回调。
 *
 * 与原版差异（等价裁剪）：桌面版无 Android View 概念（无 onPause/onResume/clearFocus 等）。
 */
interface DesktopWebView {

    // ---- 设置（对应 WebSettings）----
    var userAgent: String?
    var blockNetworkImage: Boolean

    /** true = 优先缓存（LOAD_CACHE_ELSE_NETWORK），false = 默认（LOAD_DEFAULT） */
    var cacheFirst: Boolean

    // ---- 事件回调（对应 WebChromeClient / WebViewClient）----
    /** 控制台日志（level 如 "INFO"）；返回是否消费该消息 */
    var onConsoleMessage: ((level: String, message: String) -> Boolean)?
    /** 页面加载完成 */
    var onPageFinished: ((url: String) -> Unit)?
    /** 主框架导航（对应 shouldOverrideUrlLoading）；返回 true 表示已拦截处理，不再继续加载 */
    var onBeforeBrowse: ((url: String, isRedirect: Boolean) -> Boolean)?
    /** 资源加载（对应 onLoadResource） */
    var onResourceLoad: ((url: String) -> Unit)?

    // ---- 加载 ----
    fun loadUrl(url: String, additionalHeaders: Map<String, String> = emptyMap())
    fun loadHtml(html: String, baseUrl: String?, encoding: String)
    fun stopLoading()

    // ---- JS 执行（对应 evaluateJavascript；onResult 返回 JS 结果字符串或 null）----
    fun evaluateJavascript(js: String, onResult: ((String?) -> Unit)?)
    /** 执行 javascript: URL（原版 loadUrl("javascript:...")，桌面由引擎 executeJavaScript 等价实现） */
    fun loadJavaScriptUrl(js: String)

    // ---- JS 桥（对应 addJavascriptInterface；桌面版由引擎注入桥实现）----
    fun addJavascriptInterface(bridge: Any, name: String)
    fun removeJavascriptInterface(name: String)

    // ---- 生命周期 ----
    fun destroy()
}
