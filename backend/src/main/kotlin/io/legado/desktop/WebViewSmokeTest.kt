package io.legado.desktop

import io.legado.desktop.data.entities.BookSource
import io.legado.desktop.help.CacheManager
import io.legado.desktop.help.http.BackstageWebView
import io.legado.desktop.help.webView.DesktopWebView
import io.legado.desktop.help.webView.DesktopWebViewFactory
import io.legado.desktop.help.webView.WebJsExtensions
import io.legado.desktop.help.webView.WebViewPool
import io.legado.desktop.help.webView.toWebViewRequestConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Part 7 引擎层冒烟（--webview-smoke-test 入口）。
 *
 * 覆盖（纯逻辑，用 Fake [DesktopWebView] 确定性驱动，不依赖真实浏览器）：
 *  - T7.1 WebViewRequestConfig：UA 精确/忽略大小写/默认 + additionalHeaders 排除 UA/CookieJar
 *  - T7.3 WebJsExtensions.request()：funName 分发（run/evalJS、错误 funName）+ CacheManager + JSBridgeResult 往返
 *  - T7.2 WebViewPool：acquire/release 复用、池满销毁、复位
 *  - T7.4 BackstageWebView：html+js 结果 / sourceRegex 资源匹配 / overrideUrlRegex 跳转拦截 / 超时
 *
 * JCEF 真实浏览器（offscreen 加载/executeJavaScript/JS 桥）断言在 T7.0 验证步骤
 * （JCEF 依赖与 bundle 下载后）追加，本次仅验证纯逻辑与接线。
 *
 * 全部在单进程内完成，跑完返回失败数。
 */
object WebViewSmokeTest {

    private fun waitUntil(timeoutMs: Long = 8000, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(20)
        }
        return condition()
    }

    /** 返回失败数；0 = 全部通过 */
    fun run(): Int {
        var fail = 0
        fun check(name: String, block: () -> Unit) {
            try {
                block()
                println("  [PASS] $name")
            } catch (e: Throwable) {
                fail++
                println("  [FAIL] $name -> ${e.message}")
            }
        }

        // ================= T7.1 WebViewRequestConfig =================
        check("T7.1 精确 UA + additionalHeaders 排除 UA/CookieJar") {
            val cfg = mapOf(
                "User-Agent" to "MyUA/1.0",
                "CookieJar" to "should-not-send",
                "X-Custom" to "abc"
            ).toWebViewRequestConfig("DefaultUA")
            require(cfg.userAgent == "MyUA/1.0") { "UA 应取精确匹配，实际 ${cfg.userAgent}" }
            require("X-Custom" in cfg.additionalHeaders) { "X-Custom 应保留" }
            require("User-Agent" !in cfg.additionalHeaders) { "UA 不应进 additionalHeaders" }
            require("CookieJar" !in cfg.additionalHeaders) { "CookieJar 不应进 additionalHeaders" }
            require(cfg.additionalHeaders.size == 1) { "additionalHeaders 应仅剩 X-Custom，实际 ${cfg.additionalHeaders}" }
        }

        check("T7.1 忽略大小写 UA / 空 UA 回落默认") {
            val cfg1 = mapOf("user-agent" to "LowerUA").toWebViewRequestConfig("DefaultUA")
            require(cfg1.userAgent == "LowerUA") { "忽略大小写 UA 未命中，实际 ${cfg1.userAgent}" }
            val cfg2 = mapOf("User-Agent" to "").toWebViewRequestConfig("DefaultUA")
            require(cfg2.userAgent == "DefaultUA") { "空 UA 应回落默认，实际 ${cfg2.userAgent}" }
            val cfg3 = (null as Map<String, String>?).toWebViewRequestConfig("DefaultUA")
            require(cfg3.userAgent == "DefaultUA") { "null header 应回落默认，实际 ${cfg3.userAgent}" }
        }

        // ================= T7.3 WebJsExtensions.request() 分发 =================
        val source = BookSource(
            bookSourceUrl = "https://webview-smoke.local",
            bookSourceName = "WebView冒烟源"
        )

        check("T7.3 WebJsExtensions request('run') 执行 JS 并回写 JSBridgeResult(true)") {
            val fake = FakeDesktopWebView()
            val ext = WebJsExtensions(source, fake)
            ext.request("run", arrayOf("'hello'", null, null, null, null, null), "wj-run")
            require(waitUntil { CacheManager.getFromMemory("wj-run") != null }) { "run 结果未写入 CacheManager" }
            require(CacheManager.getFromMemory("wj-run").toString() == "hello") {
                "期望 hello 实际 ${CacheManager.getFromMemory("wj-run")}"
            }
            require(fake.jsCalls.any { it.contains("window.${WebJsExtensions.JSBridgeResult}('wj-run', true);") }) {
                "应回调 JSBridgeResult(true)，实际 ${fake.jsCalls}"
            }
        }

        check("T7.3 WebJsExtensions request('getStringAwait') 分发") {
            val fake = FakeDesktopWebView()
            val ext = WebJsExtensions(source, fake)
            ext.setContent(
                "<html><body><div class=\"t\">ok</div></body></html>",
                "https://webview-smoke.local/"
            )
            ext.request("getStringAwait", arrayOf("@css:div.t@text", null, null, null, null, null), "wj-getstr")
            require(waitUntil { CacheManager.getFromMemory("wj-getstr") != null }) { "getStringAwait 结果未写入" }
            require(CacheManager.getFromMemory("wj-getstr").toString() == "ok") {
                "期望 ok 实际 ${CacheManager.getFromMemory("wj-getstr")}"
            }
            require(fake.jsCalls.any { it.contains("window.${WebJsExtensions.JSBridgeResult}('wj-getstr', true);") }) {
                "应回调 JSBridgeResult(true)，实际 ${fake.jsCalls}"
            }
        }

        check("T7.3 WebJsExtensions request('webViewGetSourceAwait') 缺 sourceRegex 走错误路径") {
            val fake = FakeDesktopWebView()
            val ext = WebJsExtensions(source, fake)
            ext.request(
                "webViewGetSourceAwait",
                arrayOf("html", "url", "js", null, null, null),
                "wj-wvgs"
            )
            require(waitUntil { CacheManager.getFromMemory("wj-wvgs") != null }) { "错误结果未写入" }
            require(CacheManager.getFromMemory("wj-wvgs").toString() == "error sourceRegex null") {
                "期望 error sourceRegex null 实际 ${CacheManager.getFromMemory("wj-wvgs")}"
            }
            require(fake.jsCalls.any { it.contains("window.${WebJsExtensions.JSBridgeResult}('wj-wvgs', false);") }) {
                "错误路径应回调 JSBridgeResult(false)，实际 ${fake.jsCalls}"
            }
        }

        check("T7.3 WebJsExtensions 未知 funName 走错误路径") {
            val fake = FakeDesktopWebView()
            val ext = WebJsExtensions(source, fake)
            ext.request("nonexistent", arrayOf("a", null, null, null, null, null), "wj-bad")
            require(waitUntil { CacheManager.getFromMemory("wj-bad") != null }) { "错误结果未写入" }
            require(CacheManager.getFromMemory("wj-bad").toString() == "error funName") {
                "期望 error funName 实际 ${CacheManager.getFromMemory("wj-bad")}"
            }
        }

        // ================= T7.4 BackstageWebView 编排（Fake 驱动）=================
        check("T7.4 BackstageWebView html+js 返回 JS 结果") {
            WebViewPool.resetForTest()
            val fake = FakeDesktopWebView()
            fake.jsResultHandler = { _, cb -> cb?.invoke("\"hello\"") }
            DesktopWebViewFactory.creator = { fake }
            val body = runBlocking {
                val bsv = BackstageWebView(
                    url = "https://example.com/page",
                    html = "<html><body>hello</body></html>",
                    javaScript = "document.body.innerText",
                    delayTime = 0
                )
                // getStrResponse 在独立线程池运行，避免 runBlocking 单线程事件循环被 busy-wait 阻塞
                val deferred = CoroutineScope(Dispatchers.Default).async { bsv.getStrResponse() }
                require(waitUntil { fake.loadHtmlCalls.isNotEmpty() }) { "未 loadHtml" }
                fake.onPageFinished?.invoke("https://example.com/page")
                withTimeout(8000) { deferred.await().body }
            }
            require(body == "hello") { "期望 hello 实际 $body" }
        }

        check("T7.4 BackstageWebView sourceRegex 资源匹配返回资源 URL") {
            WebViewPool.resetForTest()
            val fake = FakeDesktopWebView()
            DesktopWebViewFactory.creator = { fake }
            val body = runBlocking {
                val bsv = BackstageWebView(
                    url = "https://example.com/page",
                    sourceRegex = "https://cdn\\.example\\.com/.+\\.mp4",
                    delayTime = 0
                )
                val deferred = CoroutineScope(Dispatchers.Default).async { bsv.getStrResponse() }
                require(waitUntil { fake.loadUrlCalls.isNotEmpty() }) { "未 loadUrl" }
                fake.onResourceLoad?.invoke("https://cdn.example.com/video.mp4")
                withTimeout(8000) { deferred.await().body }
            }
            require(body == "https://cdn.example.com/video.mp4") { "期望资源 URL，实际 $body" }
        }

        check("T7.4 BackstageWebView overrideUrlRegex 跳转拦截返回目标 URL") {
            WebViewPool.resetForTest()
            val fake = FakeDesktopWebView()
            DesktopWebViewFactory.creator = { fake }
            val body = runBlocking {
                val bsv = BackstageWebView(
                    url = "https://example.com/page",
                    overrideUrlRegex = "https://target\\.example\\.com/\\?.*",
                    delayTime = 0
                )
                val deferred = CoroutineScope(Dispatchers.Default).async { bsv.getStrResponse() }
                require(waitUntil { fake.loadUrlCalls.isNotEmpty() }) { "未 loadUrl" }
                val handled = fake.onBeforeBrowse?.invoke("https://target.example.com/?key=1", false) == true
                require(handled) { "onBeforeBrowse 应返回 true（已拦截）" }
                withTimeout(8000) { deferred.await().body }
            }
            require(body == "https://target.example.com/?key=1") { "期望目标 URL，实际 $body" }
        }

        check("T7.4 BackstageWebView 加载无结果超时抛 TimeoutCancellationException") {
            WebViewPool.resetForTest()
            val fake = FakeDesktopWebView()
            DesktopWebViewFactory.creator = { fake }
            val threw = runBlocking {
                val bsv = BackstageWebView(
                    url = "https://example.com/page",
                    javaScript = "1+1",
                    timeout = 800
                )
                try {
                    bsv.getStrResponse()
                    false
                } catch (e: Throwable) {
                    e is TimeoutCancellationException
                }
            }
            require(threw) { "应抛 TimeoutCancellationException" }
        }

        // ================= T7.2 WebViewPool（Fake 驱动）=================
        check("T7.2 WebViewPool acquire 复用 / 池满销毁 / 复位") {
            WebViewPool.resetForTest()
            var created = 0
            DesktopWebViewFactory.creator = { created++; FakeDesktopWebView() }
            val pooled = (1..6).map { WebViewPool.acquire() }
            require(created == 6) { "空池应创建 6 个实例，实际 $created" }
            var destroyed = 0
            pooled.forEach { pw ->
                WebViewPool.release(pw)
                if ((pw.realWebView as FakeDesktopWebView).destroyed) {
                    destroyed++
                } else {
                    pw.realWebView.onPageFinished?.invoke(WebViewPool.BLANK_HTML)
                }
            }
            require(destroyed == 1) { "池容量 5，释放 6 个应销毁 1 个，实际 $destroyed" }
            val reused = WebViewPool.acquire()
            require(created == 6) { "池中有闲置实例不应新建（created=$created）" }
            require(!(reused.realWebView as FakeDesktopWebView).destroyed) { "复用实例不应已销毁" }
            WebViewPool.resetForTest()
        }

        // ================= JCEF 真实浏览器（后置）=================
        println("  [SKIP] JCEF 真实浏览器断言（offscreen 加载/executeJavaScript/JS 桥）在 T7.0 验证步骤追加，本次跳过")

        return fail
    }

    /**
     * 确定性 Fake 浏览器（--webview-smoke-test 专用）：
     * 记录调用、可手动触发页面/资源/导航事件、可设定 evaluateJavascript 返回值。
     */
    class FakeDesktopWebView : DesktopWebView {
        override var userAgent: String? = null
        override var blockNetworkImage: Boolean = false
        override var cacheFirst: Boolean = false
        override var onConsoleMessage: ((String, String) -> Boolean)? = null
        override var onPageFinished: ((String) -> Unit)? = null
        override var onBeforeBrowse: ((String, Boolean) -> Boolean)? = null
        override var onResourceLoad: ((String) -> Unit)? = null

        val loadUrlCalls = CopyOnWriteArrayList<String>()
        val loadHtmlCalls = CopyOnWriteArrayList<Triple<String, String?, String>>()
        val jsCalls = CopyOnWriteArrayList<String>()
        val jsBridgeRegistered = CopyOnWriteArrayList<String>()
        val jsBridgeRemoved = CopyOnWriteArrayList<String>()

        @Volatile
        var destroyed: Boolean = false

        /** 设定 evaluateJavascript 行为：js -> onResult（默认返回 null） */
        @Volatile
        var jsResultHandler: ((String, ((String?) -> Unit)?) -> Unit)? = null

        override fun loadUrl(url: String, additionalHeaders: Map<String, String>) {
            loadUrlCalls.add(url)
        }

        override fun loadHtml(html: String, baseUrl: String?, encoding: String) {
            loadHtmlCalls.add(Triple(html, baseUrl, encoding))
        }

        override fun stopLoading() {}

        override fun evaluateJavascript(js: String, onResult: ((String?) -> Unit)?) {
            jsCalls.add(js)
            jsResultHandler?.invoke(js, onResult)
        }

        override fun loadJavaScriptUrl(js: String) {
            jsCalls.add("javascript:$js")
        }

        override fun addJavascriptInterface(bridge: Any, name: String) {
            jsBridgeRegistered.add(name)
        }

        override fun removeJavascriptInterface(name: String) {
            jsBridgeRemoved.add(name)
        }

        override fun destroy() {
            destroyed = true
        }
    }
}
