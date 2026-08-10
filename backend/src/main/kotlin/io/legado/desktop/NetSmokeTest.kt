package io.legado.desktop

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.sun.net.httpserver.HttpServer
import io.legado.desktop.data.appDb
import io.legado.desktop.env.DesktopEnv
import io.legado.desktop.help.config.AppConfig
import io.legado.desktop.help.config.LocalConfig
import io.legado.desktop.help.config.SourceConfig
import io.legado.desktop.help.http.CookieManager
import io.legado.desktop.help.http.CookieStore
import io.legado.desktop.help.http.ProxyCredentials
import io.legado.desktop.help.http.ProxyProtocol
import io.legado.desktop.help.http.Socks5SocketFactory
import io.legado.desktop.help.http.getProxyClient
import io.legado.desktop.help.http.newCallResponse
import io.legado.desktop.help.http.newCallStrResponse
import io.legado.desktop.help.http.okHttpClient
import io.legado.desktop.help.http.parseProxyConfig
import io.legado.desktop.utils.NetworkUtils
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.GZIPOutputStream
import kotlin.concurrent.thread

/**
 * Part 2 配置与网络层冒烟（--net-smoke-test 入口）。
 *
 * T2.1 配置系统：AppConfig/LocalConfig/SourceConfig 读写 + config.json 重启保持
 * T2.2 HTTP 客户端：本地 HttpServer 验证 StrResponse / gzip / deflate / brotli 解压 + UA 注入 + 真实 https(br)
 * T2.3 Cookie：Set-Cookie → session(内存) + persistent(DB cookies 表) → CookieStore 读回
 * T2.4 代理：parseProxyConfig 解析 + HTTP 代理真实请求链路 + SOCKS5 握手协议帧
 *
 * 全部在单进程内完成（本地起 HTTP/TCP 服务器），跑完返回失败数。
 */
object NetSmokeTest {

    /** 返回失败数；0 = 全部通过 */
    fun run(): Int {
        var fail = 0
        fun check(name: String, block: () -> Unit) {
            try {
                block()
                println("  ✅ $name")
            } catch (e: Throwable) {
                fail++
                println("  ❌ $name -> ${e.message}")
            }
        }

        // ================= T2.1 配置系统 =================
        check("T2.1 AppConfig: 读写偏好(threadCount/webPort)") {
            val oldThread = AppConfig.threadCount
            val oldPort = AppConfig.webPort
            AppConfig.threadCount = 77
            AppConfig.webPort = 2233
            require(AppConfig.threadCount == 77) { "threadCount 写读不一致" }
            require(AppConfig.webPort == 2233) { "webPort 写读不一致" }
            AppConfig.threadCount = oldThread
            AppConfig.webPort = oldPort
        }

        check("T2.1 LocalConfig: 读写(password/lastBackup)") {
            LocalConfig.password = "smoke-pw"
            require(LocalConfig.password == "smoke-pw") { "password 写读不一致" }
            LocalConfig.lastBackup = 123456789L
            require(LocalConfig.lastBackup == 123456789L) { "lastBackup 写读不一致" }
            LocalConfig.password = null
            require(LocalConfig.password == null) { "password 置空未生效" }
        }

        check("T2.1 SourceConfig: 书源评分 set/get/removeSources") {
            val origin = "https://net.smoke/source-${System.currentTimeMillis()}"
            SourceConfig.setBookScore(origin, "测试书", "作者", 5)
            require(SourceConfig.getBookScore(origin, "测试书", "作者") == 5) { "getBookScore 不一致" }
            require(SourceConfig.getSourceScore(origin) == 5) { "getSourceScore 不一致" }
            SourceConfig.setBookScore(origin, "测试书", "作者", 3)
            require(SourceConfig.getSourceScore(origin) == 3) { "setBookScore 增量逻辑错误" }
            SourceConfig.removeSource(origin)
            require(SourceConfig.getSourceScore(origin) == 0) { "removeSource 未清除评分" }
        }

        check("T2.1 配置重启保持: config.json 落盘并可重新解析") {
            val key = "smoke_key_${System.currentTimeMillis()}"
            DesktopEnv.putPrefString(key, "smoke-value")
            val file = DesktopEnv.configDir.resolve("config.json").toFile()
            require(file.exists() && file.length() > 0) { "config.json 未生成" }
            val text = file.readText()
            require(text.contains(key) && text.contains("smoke-value")) { "config.json 未包含写入值: $text" }
            // 模拟重启：重新 fromJson 解析（DesktopEnv.load 的行为）
            val reloaded: JsonObject = Gson().fromJson(text, JsonObject::class.java)
            require(reloaded.get(key).asString == "smoke-value") { "重启后读不到配置" }
            DesktopEnv.removePref(key)
        }

        // ================= T2.2 HTTP 客户端（本地服务器） =================
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/plain") { ex ->
            val body = "hello plain".toByteArray(Charsets.UTF_8)
            ex.responseHeaders.set("Content-Type", "text/plain; charset=utf-8")
            ex.sendResponseHeaders(200, body.size.toLong())
            ex.responseBody.use { it.write(body) }
        }
        server.createContext("/gzip") { ex ->
            val raw = "hello gzip 中文".toByteArray(Charsets.UTF_8)
            val gz = ByteArrayOutputStream().apply {
                GZIPOutputStream(this).use { it.write(raw) }
            }.toByteArray()
            ex.responseHeaders.set("Content-Type", "text/plain; charset=utf-8")
            ex.responseHeaders.set("Content-Encoding", "gzip")
            ex.sendResponseHeaders(200, gz.size.toLong())
            ex.responseBody.use { it.write(gz) }
        }
        server.createContext("/deflate") { ex ->
            val raw = "hello deflate".toByteArray(Charsets.UTF_8)
            val dz = ByteArrayOutputStream().apply {
                // 与原版 Inflater(true)（raw deflate）匹配
                DeflaterOutputStream(this, Deflater(Deflater.BEST_COMPRESSION, true)).use { it.write(raw) }
            }.toByteArray()
            ex.responseHeaders.set("Content-Type", "text/plain; charset=utf-8")
            ex.responseHeaders.set("Content-Encoding", "deflate")
            ex.sendResponseHeaders(200, dz.size.toLong())
            ex.responseBody.use { it.write(dz) }
        }
        server.createContext("/br") { ex ->
            val brFile = File(System.getProperty("java.io.tmpdir"), "legado-net-test/hello.txt.br")
            if (!brFile.exists()) {
                ex.sendResponseHeaders(404, -1)
                return@createContext
            }
            val br = brFile.readBytes()
            ex.responseHeaders.set("Content-Type", "text/plain; charset=utf-8")
            ex.responseHeaders.set("Content-Encoding", "br")
            ex.sendResponseHeaders(200, br.size.toLong())
            ex.responseBody.use { it.write(br) }
        }
        server.createContext("/ua") { ex ->
            val ua = ex.requestHeaders.getFirst("User-Agent") ?: ""
            val body = ua.toByteArray(Charsets.UTF_8)
            ex.responseHeaders.set("Content-Type", "text/plain; charset=utf-8")
            ex.sendResponseHeaders(200, body.size.toLong())
            ex.responseBody.use { it.write(body) }
        }
        server.createContext("/cookie") { ex ->
            // 返回请求携带的 Cookie 头（验证 loadRequest 注入）
            val cookie = ex.requestHeaders.getFirst("Cookie") ?: ""
            val body = cookie.toByteArray(Charsets.UTF_8)
            ex.responseHeaders.set("Content-Type", "text/plain; charset=utf-8")
            ex.sendResponseHeaders(200, body.size.toLong())
            ex.responseBody.use { it.write(body) }
        }
        server.createContext("/setcookie") { ex ->
            ex.responseHeaders.add("Set-Cookie", "smoke_session=s1; Path=/")
            ex.responseHeaders.add("Set-Cookie", "smoke_db=hello; Path=/; Max-Age=3600")
            val body = "cookie set".toByteArray(Charsets.UTF_8)
            ex.sendResponseHeaders(200, body.size.toLong())
            ex.responseBody.use { it.write(body) }
        }
        server.start()
        val base = "http://127.0.0.1:${server.address.port}"

        check("T2.2 HTTP: 普通文本 StrResponse") {
            val r = runBlocking { okHttpClient.newCallStrResponse { url("$base/plain") } }
            require(r.code() == 200) { "code=${r.code()}" }
            require(r.body == "hello plain") { "body=${r.body}" }
        }

        check("T2.2 HTTP: gzip 解压正确") {
            val r = runBlocking { okHttpClient.newCallStrResponse { url("$base/gzip") } }
            require(r.body == "hello gzip 中文") { "body=${r.body}" }
        }

        check("T2.2 HTTP: deflate 解压正确") {
            val r = runBlocking { okHttpClient.newCallStrResponse { url("$base/deflate") } }
            require(r.body == "hello deflate") { "body=${r.body}" }
        }

        check("T2.2 HTTP: brotli 解压正确") {
            val brFile = File(System.getProperty("java.io.tmpdir"), "legado-net-test/hello.txt.br")
            if (!brFile.exists()) {
                println("    (跳过: 未生成 brotli 测试文件, 请用 node 生成)")
                return@check
            }
            val r = runBlocking { okHttpClient.newCallStrResponse { url("$base/br") } }
            require(r.body == "hello brotli world 你好世界") { "body=${r.body}" }
        }

        check("T2.2 HTTP: User-Agent 自动注入") {
            val r = runBlocking { okHttpClient.newCallStrResponse { url("$base/ua") } }
            require(r.body == AppConfig.userAgent) { "ua=${r.body}" }
        }

        // ================= T2.3 Cookie =================
        check("T2.3 Cookie: Set-Cookie → session(内存) + persistent(DB)") {
            val resp = runBlocking {
                okHttpClient.newCallResponse {
                    url("$base/setcookie")
                    header(CookieManager.cookieJarHeader, "1")
                }
            }
            resp.close()
            val domain = NetworkUtils.getSubDomain("$base/x")
            require(domain == "127.0.0.1") { "subDomain=$domain" }
            // session cookie 存内存
            val session = CookieManager.getSessionCookie(domain)
            require(session?.contains("smoke_session=s1") == true) { "session cookie 未保存: $session" }
            // persistent cookie 存 DB（cookies 表）
            val dbCookie = appDb.cookieDao.get(domain)
            require(dbCookie?.cookie?.contains("smoke_db=hello") == true) { "DB cookie 未保存: ${dbCookie?.cookie}" }
            // CookieStore.getCookie 合并两者
            val merged = CookieStore.getCookie("$base/x")
            require(merged.contains("smoke_db=hello")) { "合并后缺 persistent: $merged" }
            require(merged.contains("smoke_session=s1")) { "合并后缺 session: $merged" }
        }

        check("T2.3 Cookie: loadRequest 请求头合并") {
            // 带 CookieJar 头的请求，拦截器应把已存 cookie 注入请求（/cookie 返回请求携带的 Cookie 头）
            val cookieHeader = runBlocking {
                okHttpClient.newCallStrResponse {
                    url("$base/cookie")
                    header(CookieManager.cookieJarHeader, "1")
                }.body
            }
            require(cookieHeader!!.contains("smoke_db=hello") && cookieHeader.contains("smoke_session=s1")) {
                "请求未携带 cookie: $cookieHeader"
            }
        }

        // ================= T2.4 代理 =================
        check("T2.4 代理: parseProxyConfig 解析(http/socks5/socks4/IPv6/非法)") {
            val c1 = parseProxyConfig("http://127.0.0.1:8080")
            require(c1.protocol == ProxyProtocol.HTTP && c1.host == "127.0.0.1" && c1.port == 8080 && c1.credentials == null) {
                "http 解析错误: $c1"
            }
            val c2 = parseProxyConfig("socks5://user:pass@example.com:1080")
            require(c2.protocol == ProxyProtocol.SOCKS5 && c2.host == "example.com" && c2.port == 1080) {
                "socks5 解析错误: $c2"
            }
            require(c2.credentials?.username == "user" && c2.credentials?.password == "pass") { "socks5 凭据错误" }
            val c3 = parseProxyConfig("socks4://proxy.local:9999")
            require(c3.protocol == ProxyProtocol.SOCKS4 && c3.host == "proxy.local" && c3.port == 9999) {
                "socks4 解析错误: $c3"
            }
            val c4 = parseProxyConfig("http://[::1]:3128")
            require(c4.host == "::1" && c4.port == 3128) { "IPv6 解析错误: $c4" }
            var thrown = false
            try {
                parseProxyConfig("ftp://x:1")
            } catch (e: IllegalArgumentException) {
                thrown = true
            }
            require(thrown) { "非法协议未抛异常" }
        }

        // 目标服务器（直连响应）
        val targetServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        targetServer.createContext("/target") { ex ->
            val body = "TARGET-OK".toByteArray(Charsets.UTF_8)
            ex.responseHeaders.set("Content-Type", "text/plain; charset=utf-8")
            ex.sendResponseHeaders(200, body.size.toLong())
            ex.responseBody.use { it.write(body) }
        }
        targetServer.start()
        val targetBase = "http://127.0.0.1:${targetServer.address.port}"

        // 代理服务器：OkHttp 走 HTTP 代理会发绝对 URI 形式请求，代理加前缀返回
        val proxyServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        proxyServer.createContext("/") { ex ->
            val body = "PROXY-OK:${ex.requestURI}".toByteArray(Charsets.UTF_8)
            ex.responseHeaders.set("Content-Type", "text/plain; charset=utf-8")
            ex.sendResponseHeaders(200, body.size.toLong())
            ex.responseBody.use { it.write(body) }
        }
        proxyServer.start()
        val proxyUrl = "http://127.0.0.1:${proxyServer.address.port}"

        check("T2.4 代理: 直连不走代理(对照)") {
            val r = runBlocking { okHttpClient.newCallStrResponse { url("$targetBase/target") } }
            require(r.body == "TARGET-OK") { "直连结果异常: ${r.body}" }
        }

        check("T2.4 代理: getProxyClient 请求走 HTTP 代理") {
            val proxyClient = getProxyClient(proxyUrl)
            val r = runBlocking { proxyClient.newCallStrResponse { url("$targetBase/target") } }
            require(r.body!!.startsWith("PROXY-OK:")) { "请求未走代理: ${r.body}" }
        }

        check("T2.4 代理: SOCKS5 握手协议帧") {
            val ss = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
            val sockPort = ss.localPort
            val serverError = java.util.concurrent.atomic.AtomicReference<Throwable?>(null)
            val serverThread = thread(name = "socks5-mock") {
                try {
                    val sock = ss.accept()
                    sock.soTimeout = 8000
                    val input = sock.getInputStream()
                    val output = sock.getOutputStream()
                    // greeting: 05 01 02（版本/方法数/用户名密码）
                    val g = ByteArray(3)
                    readFully(input, g)
                    println("    [socks5-mock] greeting=${g.toList()}")
                    require(g[0] == 5.toByte() && g[1] == 1.toByte() && g[2] == 2.toByte()) { "greeting 帧错误: ${g.toList()}" }
                    output.write(byteArrayOf(5, 2)); output.flush()
                    // auth: 01 ulen user plen pass（RFC1929：auth-hdr 第二字节即 ulen）
                    val a = ByteArray(2)
                    readFully(input, a)
                    println("    [socks5-mock] auth-hdr=${a.toList()}")
                    require(a[0] == 1.toByte()) { "auth 版本错误" }
                    val ulen = a[1].toInt() and 0xff
                    val u = ByteArray(ulen); readFully(input, u)
                    val plen = input.read(); val p = ByteArray(plen); readFully(input, p)
                    println("    [socks5-mock] user=${String(u)} pass=${String(p)}")
                    require(String(u) == "user" && String(p) == "pass") { "凭据错误" }
                    output.write(byteArrayOf(1, 0)); output.flush()
                    // connect: 05 01 00 03(域名) hlen host port
                    val h = ByteArray(4)
                    readFully(input, h)
                    println("    [socks5-mock] connect-hdr=${h.toList()}")
                    require(h[0] == 5.toByte() && h[1] == 1.toByte() && h[2] == 0.toByte() && h[3] == 3.toByte()) {
                        "connect 帧错误: ${h.toList()}"
                    }
                    val hlen = input.read(); val host = ByteArray(hlen); readFully(input, host)
                    val prt = ByteArray(2); readFully(input, prt)
                    println("    [socks5-mock] host=${String(host)} port=${(prt[0].toInt() and 0xff) shl 8 or (prt[1].toInt() and 0xff)}")
                    require(String(host) == "target.example.com") { "目标域名错误: ${String(host)}" }
                    output.write(byteArrayOf(5, 0, 0, 1, 0, 0, 0, 0, 0, 0)); output.flush()
                    println("    [socks5-mock] reply success")
                    sock.close()
                } catch (e: Throwable) {
                    serverError.set(e)
                    println("    [socks5-mock] ERROR: $e")
                } finally {
                    runCatching { ss.close() }
                }
            }
            println("    [client] connect socks5 via 127.0.0.1:$sockPort -> target.example.com:80")
            val sf = Socks5SocketFactory("127.0.0.1", sockPort, ProxyCredentials("user", "pass"))
            val socket = sf.createSocket("target.example.com", 80)
            socket.close()
            println("    [client] socket connected + closed")
            serverThread.join(10_000)
            require(!serverThread.isAlive) { "SOCKS5 mock 服务器线程未正常结束" }
            serverError.get()?.let { throw it }
        }

        // ================= T2.2 真实 https（br + SSL） =================
        check("T2.2 HTTP: https 真实请求(example.com, br+SSL+UTF-8)") {
            val r = runBlocking { okHttpClient.newCallStrResponse { url("https://example.com") } }
            require(r.code() == 200) { "code=${r.code()}" }
            require(!r.body.isNullOrBlank()) { "body 为空" }
            require(r.body!!.contains("Example Domain") || r.body!!.contains("example")) { "body 内容异常: ${r.body?.take(80)}" }
        }

        // 清理本地服务器
        server.stop(0)
        targetServer.stop(0)
        proxyServer.stop(0)

        return fail
    }

    private fun readFully(input: java.io.InputStream, buf: ByteArray) {
        var offset = 0
        while (offset < buf.size) {
            val n = input.read(buf, offset, buf.size - offset)
            if (n < 0) throw java.io.EOFException("unexpected EOF")
            offset += n
        }
    }
}
