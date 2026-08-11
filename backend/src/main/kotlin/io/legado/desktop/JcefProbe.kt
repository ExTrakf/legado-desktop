package io.legado.desktop

import com.sun.net.httpserver.HttpServer
import me.friwi.jcefmaven.CefAppBuilder
import me.friwi.jcefmaven.impl.progress.ConsoleProgressHandler
import org.cef.CefApp
import org.cef.CefClient
import org.cef.browser.CefBrowser
import org.cef.browser.CefMessageRouter
import org.cef.callback.CefQueryCallback
import org.cef.handler.CefLoadHandler
import org.cef.handler.CefMessageRouterHandlerAdapter
import org.cef.network.CefRequest
import java.awt.Dimension
import java.awt.Frame
import java.io.File
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * T7.0 JCEF 最小探针（临时）：windowed 模式 + 隐藏 AWT 窗口 + 本地 HTTP 加载 + executeJavaScript 取回结果。
 * 验证目标：jcefmaven bundle 下载、隐藏窗口浏览器、CefMessageRouter JS 往返在本机可用。
 * 跑完即退出（0=通过）。
 */
object JcefProbe {

    fun run(bundleDir: File): Int {
        val server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/") { ex ->
            val body = "<html><body>hello-jcef</body></html>".toByteArray(StandardCharsets.UTF_8)
            ex.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
            ex.sendResponseHeaders(200, body.size.toLong())
            ex.responseBody.use { it.write(body) }
        }
        server.start()
        val pageUrl = "http://127.0.0.1:${server.address.port}/"

        val initLatch = CountDownLatch(1)
        val resultLatch = CountDownLatch(1)
        val initErr = AtomicReference<Throwable?>()
        val result = AtomicReference<String?>()
        val resultErr = AtomicReference<Throwable?>()

        val uiThread = Thread {
            try {
                val builder = CefAppBuilder()
                builder.setInstallDir(bundleDir)
                builder.setProgressHandler(ConsoleProgressHandler())
                builder.getCefSettings().windowless_rendering_enabled = false
                builder.addJcefArgs("--disable-gpu", "--no-sandbox", "--disable-software-rasterizer")
                val app = builder.build()
                initLatch.countDown()

                val client: CefClient = app.createClient()

                val router = CefMessageRouter.create(
                    CefMessageRouter.CefMessageRouterConfig(),
                    object : CefMessageRouterHandlerAdapter() {
                        override fun onQuery(
                            browser: CefBrowser?,
                            frame: org.cef.browser.CefFrame?,
                            queryId: Long,
                            request: String,
                            persistent: Boolean,
                            callback: CefQueryCallback?
                        ): Boolean {
                            if (request.startsWith("legado:eval:")) {
                                try {
                                    result.set(URLDecoder.decode(request.removePrefix("legado:eval:"), "UTF-8"))
                                } catch (e: Exception) {
                                    resultErr.set(e)
                                }
                                resultLatch.countDown()
                                callback?.success(null)
                                return true
                            }
                            return false
                        }

                        override fun onQueryCanceled(browser: CefBrowser?, frame: org.cef.browser.CefFrame?, queryId: Long) {}
                    }
                )
                client.addMessageRouter(router)

                client.addLoadHandler(object : CefLoadHandler {
                    override fun onLoadingStateChange(browser: CefBrowser?, isLoading: Boolean, canGoBack: Boolean, canGoForward: Boolean) {
                        println("  [INFO] loadingStateChange isLoading=$isLoading")
                    }

                    override fun onLoadStart(browser: CefBrowser?, frame: org.cef.browser.CefFrame?, transitionType: CefRequest.TransitionType?) {
                        println("  [INFO] onLoadStart frameUrl=${frame?.url}")
                    }

                    override fun onLoadEnd(browser: CefBrowser?, frame: org.cef.browser.CefFrame?, httpStatusCode: Int) {
                        println("  [INFO] onLoadEnd frameUrl=${frame?.url} status=$httpStatusCode")
                        if (frame?.isMain == true) {
                            val js = "document.body.innerText"
                            val code = "try{var __r=eval(" + jsString(js) + ");var __out=(__r===null||__r===undefined)?'null':String(__r);if(window.cefQuery){window.cefQuery({request:'legado:eval:'+encodeURIComponent(__out),onSuccess:function(){},onFailure:function(){}});}else{console.log('no-cefQuery');}}catch(e){if(window.cefQuery){window.cefQuery({request:'legado:eval:'+encodeURIComponent('ERR:'+String(e)),onSuccess:function(){},onFailure:function(){}});}else{console.log('no-cefQuery-err');}}"
                            frame.executeJavaScript(code, browser?.url ?: "", 0)
                        }
                    }

                    override fun onLoadError(browser: CefBrowser?, frame: org.cef.browser.CefFrame?, errorCode: CefLoadHandler.ErrorCode?, errorText: String?, failedUrl: String?) {
                        resultErr.set(RuntimeException("loadError $errorCode $errorText $failedUrl"))
                        resultLatch.countDown()
                    }
                })

                client.addDisplayHandler(object : org.cef.handler.CefDisplayHandlerAdapter() {
                    override fun onConsoleMessage(
                        browser: CefBrowser?,
                        level: org.cef.CefSettings.LogSeverity?,
                        message: String?,
                        source: String?,
                        line: Int
                    ): Boolean {
                        println("  [INFO] console[$level] $message")
                        return true
                    }
                })

                // 隐藏窗口承载浏览器（1x1、无装饰、屏幕外），由 AWT EDT 驱动消息循环
                val frame = Frame("legado-jcef-hidden")
                frame.isUndecorated = true
                frame.setLocation(-10000, -10000)
                frame.size = Dimension(1, 1)
                val browser = client.createBrowser(pageUrl, false, false)
                frame.add(browser.getUIComponent())
                frame.isVisible = true

                val deadline = System.currentTimeMillis() + 30_000
                while (System.currentTimeMillis() < deadline) {
                    Thread.sleep(50)
                    if (resultLatch.count == 0L) break
                }
                println("  [INFO] 等待结束（result=${result.get()} err=${resultErr.get()}）")
                runCatching { frame.dispose() }
                runCatching { browser.close(true) }
                runCatching { client.dispose() }
            } catch (t: Throwable) {
                initErr.set(t)
            } finally {
                server.stop(0)
            }
        }
        uiThread.isDaemon = true
        uiThread.name = "jcef-probe-ui"
        uiThread.start()

        if (!initLatch.await(180, TimeUnit.SECONDS)) {
            println("  [FAIL] JCEF 初始化超时（bundle 下载或启动异常）")
            return 1
        }
        val initErr2 = initErr.get()
        if (initErr2 != null) {
            println("  [FAIL] JCEF 初始化/运行失败: ${initErr2.message}")
            return 1
        }
        println("  [PASS] JCEF 初始化成功（bundle 目录: ${bundleDir.absolutePath}）")

        if (!resultLatch.await(45, TimeUnit.SECONDS)) {
            println("  [FAIL] executeJavaScript 结果超时")
            return 1
        }
        val err2 = resultErr.get()
        if (err2 != null) {
            println("  [FAIL] ${err2.message}")
            return 1
        }
        val got = result.get()
        println("  [INFO] JS 返回: '$got'")
        if (got == "hello-jcef") {
            println("  [PASS] 隐藏窗口加载 HTML + executeJavaScript 取回结果成功")
            return 0
        }
        println("  [FAIL] 期望 'hello-jcef' 实际 '$got'")
        return 1
    }

    /** 生成 JS 字符串字面量（JSON 风格转义），用于嵌入 executeJavaScript */
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
}
