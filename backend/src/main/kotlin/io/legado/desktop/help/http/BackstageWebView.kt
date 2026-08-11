package io.legado.desktop.help.http

import io.legado.desktop.data.appDb
import io.legado.desktop.data.entities.BaseSource
import io.legado.desktop.exception.NoStackTraceException
import io.legado.desktop.help.CacheManager
import io.legado.desktop.help.WebCacheManager
import io.legado.desktop.help.config.AppConfig
import io.legado.desktop.help.coroutine.Coroutine
import io.legado.desktop.help.webView.DesktopHandler
import io.legado.desktop.help.webView.DesktopWebView
import io.legado.desktop.help.webView.PooledWebView
import io.legado.desktop.help.webView.WebJsExtensions
import io.legado.desktop.help.webView.WebJsExtensions.Companion.getInjectionString
import io.legado.desktop.help.webView.WebJsExtensions.Companion.nameCache
import io.legado.desktop.help.webView.WebJsExtensions.Companion.nameJava
import io.legado.desktop.help.webView.WebJsExtensions.Companion.nameSource
import io.legado.desktop.help.webView.WebViewRequestConfig
import io.legado.desktop.help.webView.WebViewPool
import io.legado.desktop.help.webView.toWebViewRequestConfig
import io.legado.desktop.model.Debug
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.apache.commons.text.StringEscapeUtils
import java.lang.ref.WeakReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 后台webView（对应原版 help/http/BackstageWebView.kt，桌面等价）。
 * 编排逻辑逐字保留；Android WebView/WebViewClient/WebChromeClient 替换为 [DesktopWebView]
 * 抽象（JCEF 实现），Handler(Looper.getMainLooper()) 替换为 [DesktopHandler]（桌面单线程调度）。
 * 原版 onReceivedSslError → handler.proceed() 由 JCEF 实现内置放行。
 */
class BackstageWebView(
    private val url: String? = null,
    private val html: String? = null,
    private val encode: String? = null,
    private val tag: String? = null,
    private val headerMap: HashMap<String, String>? = null,
    private val sourceRegex: String? = null,
    private val overrideUrlRegex: String? = null,
    private val javaScript: String? = null,
    private var delayTime: Long = 0,
    private val cacheFirst: Boolean = false,
    private val timeout: Long? = null,
    private val result: String? = null,
    private val isRule: Boolean = false
) {

    private val mHandler = DesktopHandler()
    private var callback: Callback? = null
    private var pooledWebView: PooledWebView? = null

    suspend fun getStrResponse(): StrResponse = withTimeout(timeout ?: 60000L) {
        suspendCancellableCoroutine { block ->
            block.invokeOnCancellation {
                mHandler.post {
                    destroy()
                }
            }
            callback = object : Callback() {
                override fun onResult(response: StrResponse) {
                    if (!block.isCompleted) {
                        block.resume(response)
                    }
                }

                override fun onError(error: Throwable) {
                    if (!block.isCompleted)
                        block.resumeWithException(error)
                }
            }
            if (javaScript == null && delayTime == 0L) {
                delayTime = 900L
            }
            mHandler.post {
                try {
                    load()
                } catch (error: Throwable) {
                    destroy()
                    block.resumeWithException(error)
                }
            }
        }
    }

    private fun getEncoding(): String {
        return encode ?: "utf-8"
    }

    private fun load() {
        val requestConfig = headerMap.toWebViewRequestConfig(AppConfig.userAgent)
        val webView = createWebView(requestConfig)
        try {
            when {
                !html.isNullOrEmpty() -> {
                    if (isRule) {
                        webView.addJavascriptInterface(WebCacheManager, nameCache)
                        tag?.let { key ->
                            appDb.bookSourceDao.getBookSource(key)?.let {
                                webView.addJavascriptInterface(it as BaseSource, nameSource)
                                val webJsExtensions = WebJsExtensions(it, webView)
                                webView.addJavascriptInterface(webJsExtensions, nameJava)
                            }
                        }
                    }
                    result?.let {
                        CacheManager.put("webview_result", it)
                    }
                    webView.loadHtml(html, url, getEncoding())
                }

                else -> if (requestConfig.additionalHeaders.isEmpty()) {
                    webView.loadUrl(url!!)
                } else {
                    webView.loadUrl(url!!, requestConfig.additionalHeaders)
                }
            }
        } catch (e: Exception) {
            callback?.onError(e)
            destroy()
        }
    }

    private fun createWebView(requestConfig: WebViewRequestConfig): DesktopWebView {
        val pooledWebView = WebViewPool.acquire()
        this.pooledWebView = pooledWebView
        val webView = pooledWebView.realWebView
        webView.blockNetworkImage = true
        webView.userAgent = requestConfig.userAgent
        webView.cacheFirst = cacheFirst
        tag?.takeIf { it.isNotBlank() }?.let { sourceTag ->
            webView.onConsoleMessage = { level, message ->
                Debug.log(sourceTag, "$level: $message", true)
                true
            }
        }
        if (sourceRegex.isNullOrBlank() && overrideUrlRegex.isNullOrBlank()) {
            webView.onBeforeBrowse = { reqUrl, isRedirectFlag ->
                htmlWebViewClient.isRedirect = htmlWebViewClient.isRedirect || isRedirectFlag
                false
            }
            webView.onPageFinished = { finishedUrl ->
                htmlWebViewClient.onPageFinished(finishedUrl)
            }
        } else {
            webView.onBeforeBrowse = { reqUrl, _ ->
                snifferWebClient.shouldOverrideUrlLoading(reqUrl)
            }
            webView.onResourceLoad = { resUrl ->
                snifferWebClient.onLoadResource(resUrl)
            }
            webView.onPageFinished = { finishedUrl ->
                snifferWebClient.onPageFinished(finishedUrl)
            }
        }
        return webView
    }

    private fun destroy() {
        pooledWebView?.let { WebViewPool.release(it) }
        pooledWebView = null
        mHandler.shutdown()
    }

    private fun getJs(): String {
        javaScript?.let {
            if (it.isNotEmpty()) {
                return it
            }
        }
        return JS
    }

    private fun setCookie(url: String) {
        tag?.let {
            Coroutine.async(context = IO) {
                val cookie = CookieStore.getCookie(url)
                CookieStore.setCookie(it, cookie)
            }
        }
    }

    private val htmlWebViewClient by lazy { HtmlWebViewClient() }
    private val snifferWebClient by lazy { SnifferWebClient() }

    private inner class HtmlWebViewClient {

        private var runnable: EvalJsRunnable? = null
        var isRedirect = false

        fun onPageFinished(url: String) {
            setCookie(url)
            result?.let {
                pooledWebView?.realWebView
                    ?.evaluateJavascript("window.result = $nameCache.getFromMemory('webview_result')", null)
            }
            val runnable = runnable ?: EvalJsRunnable(
                pooledWebView?.realWebView,
                url,
                getJs()
            ).also {
                runnable = it
            }
            mHandler.removeCallbacks(runnable)
            mHandler.postDelayed(runnable, 100L + delayTime)
        }

        private inner class EvalJsRunnable(
            webView: DesktopWebView?,
            private val url: String,
            mJavaScript: String
        ) : Runnable {
            private var retry = 0
            private val intervals = listOf(200L, 400L, 600L, 800L, 1000L)
            private val mWebView: WeakReference<DesktopWebView?> = WeakReference(webView)
            private val jsStr = if (isRule) {
                "$getInjectionString\n$mJavaScript"
            } else mJavaScript
            override fun run() {
                mWebView.get()?.evaluateJavascript(jsStr) {
                    if (pooledWebView != null) {
                        handleResult(it ?: "null")
                    }
                }
            }

            private fun handleResult(result: String) = Coroutine.async {
                if (result.isNotEmpty() && result != "null") {
                    val content = StringEscapeUtils.unescapeJson(result)
                        .replace(quoteRegex, "")
                    try {
                        val response = buildStrResponse(content)
                        callback?.onResult(response)
                    } catch (e: Exception) {
                        callback?.onError(e)
                    }
                    mHandler.post {
                        destroy()
                    }
                    return@async
                }
                if (retry > 30) {
                    callback?.onError(NoStackTraceException("js执行超时"))
                    mHandler.post {
                        destroy()
                    }
                    return@async
                }
                val nextDelay = if (retry < intervals.size) {
                    intervals[retry]
                } else {
                    intervals.last()
                }
                retry++
                mHandler.postDelayed(this@EvalJsRunnable, nextDelay)
            }

            private fun buildStrResponse(content: String): StrResponse {
                if (!isRedirect) {
                    return StrResponse(url, content)
                }
                val originUrl = this@BackstageWebView.url ?: url
                val originResponse = Response.Builder()
                    .code(302)
                    .request(Request.Builder().url(originUrl).build())
                    .protocol(Protocol.HTTP_1_1)
                    .message("Found")
                    .build()
                val response = Response.Builder()
                    .code(200)
                    .request(Request.Builder().url(url).build())
                    .protocol(Protocol.HTTP_1_1)
                    .message("OK")
                    .priorResponse(originResponse)
                    .build()
                return StrResponse(response, content)
            }
        }

    }

    private inner class SnifferWebClient {

        fun shouldOverrideUrlLoading(requestUrl: String): Boolean {
            overrideUrlRegex?.let {
                if (requestUrl.matches(it.toRegex())) {
                    try {
                        val response = StrResponse(url!!, requestUrl)
                        callback?.onResult(response)
                    } catch (e: Exception) {
                        callback?.onError(e)
                    }
                    destroy()
                    return true
                }
            }
            return false
        }

        fun onLoadResource(resUrl: String) {
            sourceRegex?.let {
                if (resUrl.matches(it.toRegex())) {
                    try {
                        val response = StrResponse(url!!, resUrl)
                        callback?.onResult(response)
                    } catch (e: Exception) {
                        callback?.onError(e)
                    }
                    destroy()
                }
            }
        }

        fun onPageFinished(url: String) {
            setCookie(url)
            if (!javaScript.isNullOrEmpty()) {
                val runnable = LoadJsRunnable(pooledWebView?.realWebView, javaScript)
                mHandler.postDelayed(runnable, 100L + delayTime)
            }
        }

        private inner class LoadJsRunnable(
            webView: DesktopWebView?,
            private val mJavaScript: String?
        ) : Runnable {
            private val mWebView: WeakReference<DesktopWebView?> = WeakReference(webView)
            override fun run() {
                mWebView.get()?.loadJavaScriptUrl(mJavaScript ?: return)
            }
        }

    }

    companion object {
        const val JS = "document.documentElement.outerHTML"
        private val quoteRegex = "^\"|\"$".toRegex()
    }

    abstract class Callback {
        abstract fun onResult(response: StrResponse)
        abstract fun onError(error: Throwable)
    }
}
