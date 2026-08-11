package io.legado.desktop.help.webView

import me.friwi.jcefmaven.CefAppBuilder
import me.friwi.jcefmaven.impl.progress.ConsoleProgressHandler
import org.cef.CefApp
import org.cef.CefClient
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * JCEF 全局初始化（Part 7 T7.0）。
 *
 * 结论（2026-08-11 本机验证）：OSR 离屏模式（JOGL）无 GL 表面时 load 事件不推进；
 * 改为 windowless_rendering_enabled=false + 隐藏 AWT Frame（1x1 无装饰屏幕外）承载浏览器，
 * 由 AWT EDT 自动驱动 CEF 消息循环（jcefmaven 初始化挂在 AWT-EventQueue-0 上）。
 *
 * CefApp 进程级单例，只初始化一次；bundle（Chromium natives）首次运行下载到 bundleDir。
 */
object CefEnv {

    @Volatile
    private var cefApp: CefApp? = null

    private val lock = Any()

    /** 初始化（幂等）。bundle 目录需可写且持久（避免重复下载）。 */
    fun init(bundleDir: File) {
        synchronized(lock) {
            if (cefApp != null) return
            val latch = CountDownLatch(1)
            val err = AtomicReference<Throwable?>()
            val t = Thread {
                try {
                    val builder = CefAppBuilder()
                    builder.setInstallDir(bundleDir)
                    builder.setProgressHandler(ConsoleProgressHandler())
                    // 非 OSR：OSR(JOGL) 在无 GL 表面时不推进 load（T7.0 探针验证）
                    builder.getCefSettings().windowless_rendering_enabled = false
                    builder.addJcefArgs(
                        "--disable-gpu",
                        "--no-sandbox",
                        "--disable-software-rasterizer",
                        "--disable-extensions",
                        "--disable-plugins"
                    )
                    cefApp = builder.build()
                } catch (e: Throwable) {
                    err.set(e)
                } finally {
                    latch.countDown()
                }
            }
            t.isDaemon = true
            t.name = "jcef-init"
            t.start()
            if (!latch.await(180, TimeUnit.SECONDS)) {
                throw IllegalStateException("JCEF 初始化超时（bundle 下载或启动异常）")
            }
            val e = err.get()
            if (e != null) {
                throw IllegalStateException("JCEF 初始化失败: ${e.message}", e)
            }
        }
    }

    fun createClient(): CefClient {
        return cefApp?.createClient()
            ?: throw IllegalStateException("JCEF 未初始化，请先调用 CefEnv.init()")
    }

    val isInitialized: Boolean get() = cefApp != null
}
