package io.legado.desktop.help.webView

import io.legado.desktop.help.CacheManager
import io.legado.desktop.help.WebCacheManager
import io.legado.desktop.utils.GSON
import org.cef.CefSettings
import org.cef.browser.CefBrowser
import org.cef.browser.CefMessageRouter
import org.cef.callback.CefQueryCallback
import org.cef.handler.CefDisplayHandlerAdapter
import org.cef.handler.CefLoadHandlerAdapter
import org.cef.handler.CefMessageRouterHandlerAdapter
import org.cef.handler.CefRequestHandlerAdapter
import org.cef.handler.CefResourceRequestHandler
import org.cef.handler.CefResourceRequestHandlerAdapter
import org.cef.network.CefRequest
import java.awt.Dimension
import java.awt.EventQueue
import java.awt.Frame
import java.net.URLDecoder
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import javax.swing.SwingUtilities

/**
 * JCEF 实现的 [DesktopWebView]（Part 7 T7.0）。
 *
 * 技术要点（2026-08-11 本机验证）：
 * - 非 OSR（windowless_rendering_enabled=false）+ 隐藏 AWT Frame（1x1 无装饰屏幕外）承载浏览器，
 *   由 AWT EDT 驱动 CEF 消息循环（OSR/JOGL 无 GL 表面不推进 load，弃用）
 * - JS 往返：CefMessageRouter（页面注入 window.cefQuery）—— evaluateJavascript 经 __legadoEval
 *   helper eval 后把结果经 cefQuery 送回 Java（结果以 JSON 风格字符串返回，对齐 Android evaluateJavascript）
 * - JS 桥：addJavascriptInterface(bridge, name) → 页面注入 window.<name> Proxy，任意方法调用
 *   经 cefQuery 反射回 Java；WebCacheManager 的内存读（getFromMemory）在页面侧用 _memData 同步对象
 *   （JSBridgeResult 协议要求同步读）
 * - 已知偏差（文档化）：JCEF UA/缓存策略/blockNetworkImage 为进程级，browser 级设置忽略；
 *   loadHtml 用 data: URL（相对资源不解析）；JS 桥方法返回值为异步（原版 addJavascriptInterface 同步）
 */
class JcefDesktopWebView : DesktopWebView {

    private val client = CefEnv.createClient()
    private val awtFrame = Frame("legado-desktop-jcef-hidden")
    private val browser: CefBrowser
    private val pendingEval = ConcurrentHashMap<Long, (String?) -> Unit>()
    private val evalSeq = AtomicLong(0)
    private val jsBridges = ConcurrentHashMap<String, Any>()
    @Volatile
    private var cacheBridgeName: String? = null

    private val navLock = Any()
    private var pendingNav: PendingNav? = null
    @Volatile
    private var isLoading = false
    @Volatile
    private var readyForNav = false

    private val resourceRequestHandler by lazy {
        object : CefResourceRequestHandlerAdapter() {
            override fun onBeforeResourceLoad(
                browser: CefBrowser?,
                frame: org.cef.browser.CefFrame?,
                request: CefRequest?
            ): Boolean {
                val url = request?.url ?: return false
                onResourceLoad?.invoke(url)
                return false // 继续加载（原版 onLoadResource 不取消）
            }
        }
    }

    private val routerHandler by lazy {
        object : CefMessageRouterHandlerAdapter() {
            override fun onQuery(
                browser: CefBrowser?,
                frame: org.cef.browser.CefFrame?,
                queryId: Long,
                request: String,
                persistent: Boolean,
                callback: CefQueryCallback?
            ): Boolean {
                return when {
                    request.startsWith("legado:eval:") -> handleEval(request, callback)
                    request.startsWith("legado:invoke:") -> handleInvoke(request, callback)
                    else -> false
                }
            }

            override fun onQueryCanceled(browser: CefBrowser?, frame: org.cef.browser.CefFrame?, queryId: Long) {}
        }
    }

    override var userAgent: String? = null // 记录（JCEF 全局 UA，桌面忽略 browser 级）
    override var blockNetworkImage: Boolean = false // 记录（JCEF 无等价设置）
    override var cacheFirst: Boolean = false // 记录（JCEF 全局缓存策略）

    override var onConsoleMessage: ((String, String) -> Boolean)? = null
    override var onPageFinished: ((String) -> Unit)? = null
    override var onBeforeBrowse: ((String, Boolean) -> Boolean)? = null
    override var onResourceLoad: ((String) -> Unit)? = null

    init {
        awtFrame.isUndecorated = true
        awtFrame.setLocation(-10000, -10000)
        awtFrame.size = Dimension(1, 1)
        browser = client.createBrowser("about:blank", false, false)
        awtFrame.add(browser.getUIComponent())
        awtFrame.isVisible = true

        client.addLoadHandler(object : CefLoadHandlerAdapter() {
            override fun onLoadingStateChange(browser: CefBrowser?, isLoading: Boolean, canGoBack: Boolean, canGoForward: Boolean) {
                this@JcefDesktopWebView.isLoading = isLoading
                if (!isLoading) {
                    readyForNav = true
                    // 空闲时应用挂起的导航（JCEF 在加载中调用 loadURL 会被丢弃）
                    applyPendingNav()
                }
            }

            override fun onLoadStart(browser: CefBrowser?, frame: org.cef.browser.CefFrame?, transitionType: CefRequest.TransitionType?) {
                injectBridgeJs(browser, frame)
            }

            override fun onLoadEnd(browser: CefBrowser?, frame: org.cef.browser.CefFrame?, httpStatusCode: Int) {
                if (frame?.isMain == true) {
                    readyForNav = true
                    onPageFinished?.invoke(frame.url ?: browser?.url ?: "")
                }
            }
        })

        client.addDisplayHandler(object : CefDisplayHandlerAdapter() {
            override fun onConsoleMessage(
                browser: CefBrowser?,
                level: CefSettings.LogSeverity?,
                message: String?,
                source: String?,
                line: Int
            ): Boolean {
                return onConsoleMessage?.invoke(level?.name ?: "INFO", message ?: "") ?: true
            }
        })

        client.addRequestHandler(object : CefRequestHandlerAdapter() {
            override fun onBeforeBrowse(
                browser: CefBrowser?,
                frame: org.cef.browser.CefFrame?,
                request: CefRequest?,
                isRedirect: Boolean,
                userGesture: Boolean
            ): Boolean {
                val url = request?.url ?: return false
                return onBeforeBrowse?.invoke(url, isRedirect) ?: false
            }

            override fun getResourceRequestHandler(
                browser: CefBrowser?,
                frame: org.cef.browser.CefFrame?,
                request: CefRequest?,
                isNavigation: Boolean,
                isDownload: Boolean,
                requestInitiator: String?,
                disableDefaultHandling: org.cef.misc.BoolRef?
            ): CefResourceRequestHandler? {
                return resourceRequestHandler
            }

            override fun onCertificateError(
                browser: CefBrowser?,
                errorCode: org.cef.handler.CefLoadHandler.ErrorCode?,
                requestURL: String?,
                callback: org.cef.callback.CefCallback?
            ): Boolean {
                // 原版 WebViewClient.onReceivedSslError → handler.proceed()
                callback?.Continue()
                return true
            }
        })

        val router = CefMessageRouter.create(CefMessageRouter.CefMessageRouterConfig(), routerHandler)
        client.addMessageRouter(router)
    }

    // ---------------- 加载 ----------------

    /** 在 CEF/EDT 线程执行浏览器操作（JCEF 的 executeJavaScript 等必须从 CEF UI 线程调用） */
    private fun onCefThread(block: () -> Unit) {
        if (EventQueue.isDispatchThread()) {
            block()
        } else {
            val done = java.util.concurrent.CountDownLatch(1)
            SwingUtilities.invokeLater {
                try {
                    block()
                } catch (_: Throwable) {
                } finally {
                    done.countDown()
                }
            }
            runCatching { done.await(15, TimeUnit.SECONDS) }
        }
    }

    /** 在 CEF/EDT 线程执行并返回值 */
    private fun <T> onCefThreadValue(block: () -> T): T? {
        if (EventQueue.isDispatchThread()) return block()
        var result: T? = null
        val done = java.util.concurrent.CountDownLatch(1)
        SwingUtilities.invokeLater {
            try {
                result = block()
            } catch (_: Throwable) {
            } finally {
                done.countDown()
            }
        }
        runCatching { done.await(15, TimeUnit.SECONDS) }
        return result
    }

    override fun loadUrl(url: String, additionalHeaders: Map<String, String>) {
        val block: () -> Unit = {
            if (additionalHeaders.isEmpty()) {
                browser.loadURL(url)
            } else {
                val request = CefRequest.create()
                request.url = url
                request.method = "GET"
                val headers = LinkedHashMap<String, String>()
                additionalHeaders.forEach { (k, v) -> headers[k] = v }
                request.setHeaderMap(headers)
                browser.loadRequest(request)
            }
        }
        navigate(block, verifyUrl = url)
    }

    override fun loadHtml(html: String, baseUrl: String?, encoding: String) {
        // 已知偏差：用 data: URL 加载（相对资源不解析）；原版 loadDataWithBaseURL
        val enc = encoding.ifBlank { "utf-8" }
        val encoded = java.net.URLEncoder.encode(html, enc).replace("+", "%20")
        navigate(
            { browser.loadURL("data:text/html;charset=$enc,$encoded") },
            verifyUrl = "data:"
        )
    }

    /** 导航：始终挂起待导航，空闲（readyForNav && !isLoading）时应用。
     *  双保险：onLoadingStateChange(false) 即时 flush + 定时重试 flush（覆盖 isLoading 标记延迟送达的竞态）；
     *  应用后核对 browser.url，未命中目标（loadURL 被 CEF 丢弃）则重新应用同一 block。 */
    private fun navigate(block: () -> Unit, verifyUrl: String? = null) {
        synchronized(navLock) { pendingNav = PendingNav(block, verifyUrl) }
        scheduleNavFlush(0)
    }

    private data class PendingNav(val block: () -> Unit, val verifyUrl: String?)

    private val navRetryExecutor by lazy {
        java.util.concurrent.Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "legado-nav-flush").apply { isDaemon = true }
        }
    }

    private fun applyPendingNav() {
        val pending = synchronized(navLock) { pendingNav.also { pendingNav = null } } ?: return
        pending.block()
        pending.verifyUrl?.let { scheduleNavVerify(it, pending.block) }
    }

    private fun scheduleNavFlush(delayMs: Long) {
        runCatching {
            navRetryExecutor.schedule({
                onCefThread {
                    if (!isLoading && readyForNav) {
                        applyPendingNav()
                    } else if (synchronized(navLock) { pendingNav != null }) {
                        // 仍未空闲且有待导航 → 150ms 后重试
                        scheduleNavFlush(150)
                    }
                }
            }, delayMs, java.util.concurrent.TimeUnit.MILLISECONDS)
        }
    }

    /** 应用导航后延迟核对 browser.url；未命中说明 loadURL 被丢弃 → 重新应用 */
    private fun scheduleNavVerify(targetUrl: String, block: () -> Unit) {
        runCatching {
            navRetryExecutor.schedule({
                val current = onCefThreadValue { browser.url ?: "" }
                if (current?.startsWith(targetUrl) != true) {
                    onCefThread(block)
                    scheduleNavVerify(targetUrl, block)
                }
            }, 800, java.util.concurrent.TimeUnit.MILLISECONDS)
        }
    }

    override fun stopLoading() {
        onCefThread {
            browser.stopLoad()
        }
    }

    // ---------------- JS 执行 ----------------

    override fun evaluateJavascript(js: String, onResult: ((String?) -> Unit)?) {
        val memInject = buildString {
            val m = jsBridgeResultPattern.find(js)
            if (m != null) {
                val id = m.groupValues[2]
                val data = CacheManager.getFromMemory(id)?.toString()
                if (data != null) {
                    cacheBridgeName?.let { append("window.$it._memData[").append(jsString(id)).append("]=").append(jsString(data)).append(";") }
                }
            }
        }
        val seq = evalSeq.incrementAndGet()
        if (onResult != null) pendingEval[seq] = onResult
        // 自包含注入 helper（不依赖 onLoadStart 注入，eval 结果经 cefQuery 送回）
        val helper = "window.__legadoEval=function(js,id){try{var r=eval(js);var out=(r===null||r===undefined)?'null':String(r);if(window.cefQuery){window.cefQuery({request:'legado:eval:'+id+':'+encodeURIComponent(out),onSuccess:function(){},onFailure:function(){}});}else{window.__legadoEvalLater=out;}}catch(e){if(window.cefQuery){window.cefQuery({request:'legado:eval:'+id+':'+encodeURIComponent('ERR:'+String(e)),onSuccess:function(){},onFailure:function(){}});}else{window.__legadoEvalLater='ERR:'+String(e);}}};"
        val code = helper + memInject + "window.__legadoEval(" + jsString(js) + "," + seq + ")"
        onCefThread {
            browser.getMainFrame().executeJavaScript(code, browser.url ?: "", 0)
        }
    }

    override fun loadJavaScriptUrl(js: String) {
        evaluateJavascript(js, null)
    }

    private fun handleEval(request: String, callback: CefQueryCallback?): Boolean {
        val rest = request.removePrefix("legado:eval:")
        val sep = rest.indexOf(':')
        if (sep < 0) {
            callback?.success(null)
            return true
        }
        val id = rest.substring(0, sep).toLongOrNull()
        val raw = runCatching { URLDecoder.decode(rest.substring(sep + 1), "UTF-8") }.getOrNull()
        // eval 异常标记为 null（对齐 Android 无结果 → 触发 BackstageWebView 重试/超时）
        val result = if (raw == null || raw.startsWith("ERR:")) "null" else raw
        if (id != null) pendingEval.remove(id)?.invoke(result)
        callback?.success(null)
        return true
    }

    // ---------------- JS 桥 ----------------

    override fun addJavascriptInterface(bridge: Any, name: String) {
        jsBridges[name] = bridge
        if (bridge === WebCacheManager) {
            cacheBridgeName = name
        }
        injectBridgeJs(browser, browser.mainFrame)
    }

    override fun removeJavascriptInterface(name: String) {
        jsBridges.remove(name)
        if (cacheBridgeName == name) cacheBridgeName = null
    }

    private fun handleInvoke(request: String, callback: CefQueryCallback?): Boolean {
        try {
            val payload = request.removePrefix("legado:invoke:")
            val json = String(Base64.getDecoder().decode(payload), Charsets.UTF_8)
            val inv = GSON.fromJson(json, InvokeRequest::class.java)
            val target = inv.n?.let { jsBridges[it] }
            if (target != null && inv.m != null) {
                val args = inv.a ?: emptyList()
                val method = target.javaClass.methods.firstOrNull {
                    it.name == inv.m && it.parameterCount == args.size
                }
                if (method != null) {
                    val converted = args.mapIndexed { i, v ->
                        convertArg(v, method.parameterTypes[i])
                    }.toTypedArray()
                    val result = runCatching { method.invoke(target, *converted) }.getOrNull()
                    callback?.success(if (result != null) result.toString() else null)
                    return true
                }
            }
        } catch (_: Exception) {
        }
        callback?.success(null)
        return true
    }

    private fun convertArg(v: Any?, type: Class<*>): Any? {
        if (v == null) return null
        return when (type) {
            String::class.java -> v.toString()
            Int::class.java -> (v as Number).toInt()
            Long::class.java -> (v as Number).toLong()
            Boolean::class.java -> if (v is Boolean) v else v.toString().toBoolean()
            java.lang.String::class.java -> v.toString()
            else -> {
                if (type.isArray && type.componentType == String::class.java) {
                    @Suppress("UNCHECKED_CAST")
                    (v as? List<*>)?.map { it?.toString() }?.toTypedArray()
                } else {
                    v
                }
            }
        }
    }

    // ---------------- 生命周期 ----------------

    override fun destroy() {
        runCatching { navRetryExecutor.shutdownNow() }
        runCatching { browser.close(true) }
        runCatching { awtFrame.dispose() }
        runCatching { client.dispose() }
    }

    // ---------------- 注入 ----------------

    private fun injectBridgeJs(browser: CefBrowser?, frame: org.cef.browser.CefFrame?) {
        val bridgeNames = jsBridges.keys.joinToString(",") { "'$it'" }
        val cacheName = cacheBridgeName
        val cacheInit = if (cacheName != null) {
            "window.$cacheName={_memData:{},putMemory:function(k,v){this._memData[k]=v;},getFromMemory:function(k){return k in this._memData?this._memData[k]:null;},deleteMemory:function(k){delete this._memData[k];}};"
        } else {
            ""
        }
        val code = buildString {
            append("window.__legadoEval=function(js,id){try{var r=eval(js);var out=(r===null||r===undefined)?'null':String(r);window.cefQuery({request:'legado:eval:'+id+':'+encodeURIComponent(out),onSuccess:function(){},onFailure:function(){}});}catch(e){window.cefQuery({request:'legado:eval:'+id+':'+encodeURIComponent('ERR:'+String(e)),onSuccess:function(){},onFailure:function(){}});}};")
            append(cacheInit)
            append("(function(){var makeProxy=function(name){var p=new Proxy({},{get:function(t,prop){if(prop==='then')return undefined;return function(){var args=Array.prototype.slice.call(arguments);window.cefQuery({request:'legado:invoke:'+btoa(unescape(encodeURIComponent(JSON.stringify({n:name,m:prop,a:args})))),onSuccess:function(){},onFailure:function(){}});};}});window[name]=p;};[")
            append(bridgeNames)
            append("].forEach(makeProxy);})();")
        }
        if (frame != null && frame.isValid) {
            onCefThread {
                frame.executeJavaScript(code, browser?.url ?: "", 0)
            }
        }
    }

    private fun jsString(s: String): String {
        val sb = StringBuilder("\"")
        for (c in s) {
            when (c) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (c < ' ') sb.append("\\u%04x".format(c.code)) else sb.append(c)
            }
        }
        sb.append("\"")
        return sb.toString()
    }

    private data class InvokeRequest(
        val n: String? = null,
        val m: String? = null,
        val a: List<Any?>? = null
    )

    companion object {
        private val jsBridgeResultPattern = Regex("window\\.([A-Za-z0-9]+)\\(['\"]([^'\"]+)['\"],\\s*(true|false)\\s*\\)")
    }
}
