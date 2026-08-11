package io.legado.desktop.help.webView

import io.legado.desktop.exception.NoStackTraceException
import io.legado.desktop.help.config.AppConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Stack
import kotlin.math.max
import kotlin.random.Random

/**
 * 浏览器引擎工厂（桌面新增，JCEF 接入点）。
 * 纯逻辑层（WebViewPool/BackstageWebView）通过它创建真实浏览器；
 * --webview-smoke-test 注入 Fake 实现验证纯逻辑；JCEF 接入（T7.0 验证步骤）注入 JCEF 实现。
 */
object DesktopWebViewFactory {

    @Volatile
    var creator: () -> DesktopWebView = {
        throw NoStackTraceException(
            "桌面版 WebView 引擎（JCEF）未接入：请先在 T7.0 验证步骤配置 DesktopWebViewFactory.creator"
        )
    }

    fun create(): DesktopWebView = creator()
}

/**
 * 浏览器实例池（对应原版 help/webView/WebViewPool.kt，桌面等价）。
 * 逻辑逐字保留；浏览器创建/销毁与事件重置走 [DesktopWebView] 抽象（JCEF 实现）。
 */
object WebViewPool {
    const val BLANK_HTML = "about:blank"
    const val DATA_HTML = "data:text/html;charset=utf-8;base64,"
    // 未使用的、已预初始化的WebView池 (使用栈结构，后进先出，复用缓存)
    private val idlePool = Stack<PooledWebView>()
    // 正在使用的WebView集合
    private val inUsePool = mutableMapOf<String, PooledWebView>()

    private var needInitialize = true
    private val CACHED_WEB_VIEW_MAX_NUM = max(AppConfig.threadCount / 10, 5) // 池子总容量（闲置+使用）
    private const val IDLE_TIME_OUT: Long = 5 * 60 * 1000 // 闲置5分钟后销毁
    private const val IDLE_TIME_OUT_LAST: Long = 30 * 60 * 1000 // 最后一个闲置30分钟后销毁
    private val cleanupScope by lazy {
        CoroutineScope(Dispatchers.Default + SupervisorJob())
    }
    private var cleanupJob: Job? = null

    // 获取一个WebView
    @Synchronized
    fun acquire(): PooledWebView {
        val pooledWebView = if (idlePool.isNotEmpty()) {
            idlePool.pop() // 复用闲置实例
        } else {
            if (needInitialize) {
                needInitialize = false
                startCleanupTimer()
            }
            createNewWebView() // 创建新实例
        }
        pooledWebView.apply {
            realWebView.userAgent = AppConfig.userAgent
            isInUse = true
        }
        inUsePool[pooledWebView.id] = pooledWebView
        return pooledWebView
    }

    // 释放WebView回池
    @Synchronized
    fun release(pooledWebView: PooledWebView) {
        if (inUsePool.remove(pooledWebView.id) == null) return
        // 重置WebView状态（桌面版裁剪 Android View 操作：removeView/layoutParams/clearFocus/
        // setOnLongClickListener/setOnScrollChangeListener/setDownloadListener/outlineProvider/
        // clearFormData/clearMatches/clearDisappearingChildren/clearAnimation 等）
        val webView = pooledWebView.realWebView
        webView.run {
            stopLoading()
            removeJavascriptInterface(WebJsExtensions.nameBasic)
            removeJavascriptInterface(WebJsExtensions.nameJava)
            removeJavascriptInterface(WebJsExtensions.nameSource)
            removeJavascriptInterface(WebJsExtensions.nameCache)
            onConsoleMessage = null
            if (idlePool.size >= CACHED_WEB_VIEW_MAX_NUM - inUsePool.size) {
                // 池子已满，直接销毁
                destroy()
                return
            }
            onPageFinished = { url ->
                if (url == BLANK_HTML) {
                    webView.blockNetworkImage = false // 确保允许加载网络图片
                    webView.cacheFirst = false // 重置缓存模式
                    pooledWebView.isInUse = false
                    pooledWebView.lastUseTime = System.currentTimeMillis()
                    idlePool.push(pooledWebView)
                }
            }
            loadUrl(BLANK_HTML)
        }
    }

    private fun createNewWebView(): PooledWebView {
        // 浏览器预初始化（JS 开关/domStorage/mixedContent 等）由引擎工厂实现（JCEF 接入步骤）
        val webView = DesktopWebViewFactory.create()
        return PooledWebView(webView, generateId())
    }

    private fun generateId(): String {
        return "web_${System.currentTimeMillis()}_${Random.nextLong()}"
    }

    /**
     * 测试专用复位（--webview-smoke-test 使用）：清空池并销毁所有实例。
     * 桌面版新增，仅触碰池簿记，不改变任何生产逻辑。
     */
    @Synchronized
    internal fun resetForTest() {
        idlePool.forEach { runCatching { it.realWebView.destroy() } }
        idlePool.clear()
        inUsePool.values.forEach { runCatching { it.realWebView.destroy() } }
        inUsePool.clear()
        cleanupJob?.cancel()
        cleanupJob = null
        needInitialize = true
    }

    // 定时清理闲置过久的WebView
    private fun startCleanupTimer() {
        if (cleanupJob?.isActive == true) return
        cleanupJob = cleanupScope.launch {
            while (true) {
                delay(30_000) // 每30秒执行一次清理
                val now = System.currentTimeMillis()
                val toRemove = mutableListOf<PooledWebView>()
                var shouldCancel = false
                synchronized(this@WebViewPool) {
                    for ((index, pooled) in idlePool.withIndex()) {
                        val timeout = if (index == 0) {
                            IDLE_TIME_OUT_LAST
                        } else {
                            IDLE_TIME_OUT
                        }
                        if (now - pooled.lastUseTime > timeout) {
                            toRemove.add(pooled)
                        }
                    }
                    toRemove.forEach { pooled ->
                        idlePool.remove(pooled)
                        try {
                            pooled.realWebView.destroy()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    if (idlePool.isEmpty()) {
                        shouldCancel = true
                    }
                }
                if (shouldCancel) {
                    needInitialize = true
                    this@launch.cancel()
                }
            }
        }
    }

}
