package io.legado.desktop.help.webView

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 桌面版主线程调度器（对应原版 Handler(Looper.getMainLooper())）。
 * 原版依赖 Android 主线程 looper 做 post/postDelayed/removeCallbacks；
 * 桌面版用单线程调度器等价模拟该队列语义（BackstageWebView 的 retry/delay 逻辑保持不变）。
 * JCEF 接入后浏览器调用由 [DesktopWebView] 实现自行分发到 CEF UI 线程。
 */
class DesktopHandler(
    name: String = "desktop-webview-handler"
) {
    private val executor = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, name).apply { isDaemon = true }
    }
    private val futures = ConcurrentHashMap<Runnable, ScheduledFuture<*>>()
    private val shutdown = AtomicBoolean(false)

    fun post(r: Runnable) {
        postDelayed(r, 0)
    }

    fun postDelayed(r: Runnable, delayMillis: Long) {
        if (shutdown.get()) return
        removeCallbacks(r)
        runCatching {
            val future = executor.schedule(
                {
                    runCatching { r.run() }
                    futures.remove(r)
                },
                delayMillis,
                TimeUnit.MILLISECONDS
            )
            futures[r] = future
        }
    }

    fun removeCallbacks(r: Runnable) {
        futures.remove(r)?.cancel(false)
    }

    fun removeCallbacksAndMessages() {
        futures.keys.forEach { removeCallbacks(it) }
    }

    fun shutdown() {
        if (shutdown.compareAndSet(false, true)) {
            executor.shutdownNow()
        }
    }
}
