package io.legado.desktop

import io.legado.desktop.api.HttpApiServer
import io.legado.desktop.data.DaoSmokeTest
import io.legado.desktop.data.appDb
import io.legado.desktop.env.DesktopEnv
import io.legado.desktop.utils.LogUtils
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

    // 2+3. HTTP/WS 服务
    val server = HttpApiServer(host, port)
    server.start()
    println("[legado-desktop] backend listening on http://$host:$port")

    Runtime.getRuntime().addShutdownHook(
        Thread {
            server.stop()
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
