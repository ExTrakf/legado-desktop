package io.legado.desktop

import io.legado.desktop.data.DaoSmokeTest
import io.legado.desktop.data.appDb
import io.legado.desktop.env.DesktopEnv
import io.legado.desktop.utils.LogUtils
import io.legado.desktop.web.HttpServer
import io.legado.desktop.web.WebSocketServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess

/**
 * Legado Desktop 后端入口。
 *
 * 启动顺序：
 * 1. 初始化桌面环境（数据目录、配置）——替代原 Android appCtx
 * 2. 初始化数据层（SQLite）
 * 3. 启动 HTTP/WS 服务（NanoHTTPD）
 *
 * 参数：
 *   --port <port>   监听端口，默认 2323
 *   --host <addr>   监听地址，默认 127.0.0.1
 *   --dao-smoke-test 数据层全量冒烟（24 DAO CRUD），跑完即退出（0=通过）
 *   --api-smoke-test Part5 API 层冒烟（HTTP 全路由 + WebSocket 结果流 + 端到端），跑完即退出（0=通过）
 */
fun main(args: Array<String>) {
    val port = argValue(args, "--port")?.toIntOrNull() ?: 2323
    val host = argValue(args, "--host") ?: "127.0.0.1"

    // 1. 桌面环境（数据目录等）
    DesktopEnv.init()
    println("[legado-desktop] data home: ${DesktopEnv.homeDir}")

    // 1.5 日志文件 + 数据层（SQLite schema v99）
    LogUtils.initFileLog(DesktopEnv.configDir)
    appDb.init()
    println("[legado-desktop] database initialized: ${DesktopEnv.dbFile}")

    // 1.6 DAO 冒烟模式：跑完即退出
    if (args.contains("--dao-smoke-test")) {
        println("== DAO smoke test ==")
        val fails = DaoSmokeTest.run()
        println("== DAO smoke test result: ${if (fails == 0) "PASS" else "FAIL($fails)"} ==")
        exitProcess(if (fails == 0) 0 else 1)
    }

    // 1.7 Part2 配置/网络冒烟模式：跑完即退出
    if (args.contains("--net-smoke-test")) {
        println("== Network smoke test ==")
        val fails = NetSmokeTest.run()
        println("== Network smoke test result: ${if (fails == 0) "PASS" else "FAIL($fails)"} ==")
        exitProcess(if (fails == 0) 0 else 1)
    }

    // 1.8 Part3 规则引擎冒烟模式（AnalyzeRule/AnalyzeUrl/Rhino/JS 源）：跑完即退出
    if (args.contains("--rule-smoke-test")) {
        println("== Rule engine smoke test ==")
        val fails = RuleSmokeTest.run()
        println("== Rule engine smoke test result: ${if (fails == 0) "PASS" else "FAIL($fails)"} ==")
        exitProcess(if (fails == 0) 0 else 1)
    }

    // 1.9 Part4 书源与读书引擎冒烟模式（SourceHelp/CheckSource/jsSource/WebBook/SearchModel）：跑完即退出
    if (args.contains("--source-smoke-test")) {
        println("== Source engine smoke test ==")
        val fails = SourceSmokeTest.run()
        println("== Source engine smoke test result: ${if (fails == 0) "PASS" else "FAIL($fails)"} ==")
        exitProcess(if (fails == 0) 0 else 1)
    }

    // 1.10 Part5 API 层冒烟模式（HTTP 全路由 + WebSocket 结果流 + 端到端）：启动服务后自测，跑完即退出
    if (args.contains("--api-smoke-test")) {
        println("== Part5 API smoke test ==")
        val httpServer = HttpServer(port)
        val wsServer = WebSocketServer(port + 1)
        httpServer.start()
        wsServer.start(30_000) // 与原版一致：通信超时 30s
        println("[legado-desktop] api-smoke-test servers listening on http://$host:$port (ws +1)")
        val fails = ApiSmokeTest.run(httpPort = port, wsPort = port + 1)
        httpServer.stop()
        wsServer.stop()
        println("== Part5 API smoke test result: ${if (fails == 0) "PASS" else "FAIL($fails)"} ==")
        exitProcess(if (fails == 0) 0 else 1)
    }

    // 1.11 Part6 本地书籍/封面图片/备份导入冒烟模式（T6.1 本地解析 + T6.2 封面图片 + T6.3 备份导入）：跑完即退出
    if (args.contains("--local-smoke-test")) {
        println("== Part6 local book smoke test ==")
        val fails = LocalSmokeTest.run()
        println("== Part6 local book smoke test result: ${if (fails == 0) "PASS" else "FAIL($fails)"} ==")
        exitProcess(if (fails == 0) 0 else 1)
    }

    // 1.12 Part7 WebView 引擎层冒烟模式（T7.1 请求配置 + T7.3 JS 桥分发 + T7.2 池 + T7.4 编排，Fake 驱动）：
    // 纯逻辑验证，不依赖真实浏览器；JCEF 接入（T7.0 验证步骤）后追加真实浏览器断言
    if (args.contains("--webview-smoke-test")) {
        println("== Part7 webview smoke test ==")
        val fails = WebViewSmokeTest.run()
        println("== Part7 webview smoke test result: ${if (fails == 0) "PASS" else "FAIL($fails)"} ==")
        exitProcess(if (fails == 0) 0 else 1)
    }

    // 2+3. HTTP/WS 服务
    val server = HttpServer(port)
    val wsServer = WebSocketServer(port + 1)
    server.start()
    wsServer.start(30_000)
    println("[legado-desktop] backend listening on http://$host:$port (ws $host:${port + 1})")

    Runtime.getRuntime().addShutdownHook(
        Thread {
            server.stop()
            wsServer.stop()
            println("[legado-desktop] backend stopped")
        }
    )
    // 阻塞主线程直到被关闭
    Thread.currentThread().join()
}

private fun argValue(args: Array<String>, name: String): String? {
    val idx = args.indexOf(name)
    return if (idx >= 0 && idx + 1 < args.size) args[idx + 1] else null
}
