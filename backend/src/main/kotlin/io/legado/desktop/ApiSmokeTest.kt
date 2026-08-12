package io.legado.desktop

import com.google.gson.JsonObject
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.legado.desktop.api.controller.BookSourceController
import io.legado.desktop.data.entities.Book
import io.legado.desktop.data.entities.BookSource
import io.legado.desktop.data.entities.ReplaceRule
import io.legado.desktop.data.entities.RssSource
import io.legado.desktop.data.entities.rule.BookInfoRule
import io.legado.desktop.data.entities.rule.ContentRule
import io.legado.desktop.data.entities.rule.SearchRule
import io.legado.desktop.data.entities.rule.TocRule
import io.legado.desktop.help.config.AppConfig
import io.legado.desktop.utils.GSON
import io.legado.desktop.utils.fromJsonArray
import io.legado.desktop.utils.fromJsonObject
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.Base64
import java.util.Random

/**
 * Part 5 API 层冒烟（--api-smoke-test 入口，服务已由 Main 启动在 httpPort/wsPort）。
 *
 * T5.1 HTTP 路由整合：全路由可达、CORS、令牌保护、404
 * T5.2 书源/RSS/替换规则 API：增删改查 curl 等价
 * T5.3 书籍 API：书架/目录/正文/进度/阅读配置
 * T5.4 WebSocket：searchBook 结果流 / bookSourceDebug / rssSourceDebug
 * T5.5 端到端：导入源 → WS 搜索 → 加书架 → 目录 → 正文 → 进度
 *
 * 全部在单进程内完成（本地 HttpServer mock 书源 + RSS 源），跑完返回失败数。
 */
object ApiSmokeTest {

    private const val API_TOKEN = "api-smoke-token"

    // ---------- 本地 mock 服务器 ----------
    private fun startMockServer(): Pair<HttpServer, Int> {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/search") { ex ->
            val body = """
                <html><body>
                  <ul class="list">
                    <li><a class="name" href="/book/1">斗破苍穹</a><span class="author">天蚕土豆</span><span class="kind">玄幻</span><span class="intro">三十年河东</span></li>
                  </ul>
                </body></html>
            """.trimIndent()
            respond(ex, body)
        }
        server.createContext("/book/1") { ex ->
            val body = """
                <html><body>
                  <h1 class="title">斗破苍穹</h1>
                  <span class="author">天蚕土豆</span>
                  <div class="intro">三十年河东，三十年河西，莫欺少年穷！</div>
                  <a class="toc" href="/toc/1">目录</a>
                </body></html>
            """.trimIndent()
            respond(ex, body)
        }
        server.createContext("/toc/1") { ex ->
            val body = """
                <html><body><ul class="toc">
                  <li><a href="/content/1">第一章 陨落的天才</a></li>
                </ul></body></html>
            """.trimIndent()
            respond(ex, body)
        }
        server.createContext("/content/1") { ex ->
            respond(ex, "<html><body><div class=\"content\">萧炎，陨落的天才少年，自三年前失去斗气后……</div></body></html>")
        }
        server.createContext("/rss.xml") { ex ->
            val body = """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0">
                  <channel>
                    <title>冒烟订阅源</title>
                    <link>http://127.0.0.1:0</link>
                    <description>test</description>
                    <item>
                      <title>文章一</title>
                      <link>http://127.0.0.1:0/rss-article/1</link>
                      <description>RSS内容一</description>
                    </item>
                  </channel>
                </rss>
            """.trimIndent()
            respond(ex, body)
        }
        server.start()
        return server to server.address.port
    }

    private fun respond(ex: HttpExchange, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        ex.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
        ex.sendResponseHeaders(200, bytes.size.toLong())
        ex.responseBody.use { it.write(bytes) }
    }

    // ---------- HTTP 客户端 ----------
    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    private data class HttpResult(
        val status: Int,
        val body: String,
        val headers: Map<String, String> = emptyMap(),
    )

    private fun http(
        method: String,
        url: String,
        body: String? = null,
        token: String? = null,
    ): HttpResult {
        val builder = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(60))
        when (method) {
            "GET" -> builder.GET()
            "OPTIONS" -> builder.method("OPTIONS", HttpRequest.BodyPublishers.noBody())
            else -> builder.POST(HttpRequest.BodyPublishers.ofString(body ?: "", Charsets.UTF_8))
        }
        builder.header("Content-Type", "application/json; charset=utf-8")
        if (token != null) {
            builder.header("x-legado-token", token)
        }
        val resp = client.send(builder.build(), HttpResponse.BodyHandlers.ofString(Charsets.UTF_8))
        val respHeaders = resp.headers().map().entries.associate { (k, v) -> k.lowercase() to v.joinToString(",") }
        return HttpResult(resp.statusCode(), resp.body(), respHeaders)
    }

    private fun body(url: String, token: String? = null): String = http("GET", url, token = token).body

    private fun post(url: String, bodyJson: String, token: String? = null): String =
        http("POST", url, bodyJson, token).body

    private fun isSuccess(body: String): Boolean =
        GSON.fromJsonObject<JsonObject>(body).getOrNull()?.get("isSuccess")?.asBoolean ?: false

    private fun dataString(body: String): String =
        GSON.fromJsonObject<JsonObject>(body).getOrNull()?.get("data")?.asString ?: ""

    // ---------- 极简 WebSocket 客户端（raw socket，完整控制握手头与帧） ----------
    private data class WsFrame(val opcode: Int, val payload: ByteArray)

    private class TestWsClient(
        private val host: String,
        private val port: Int,
    ) {
        private lateinit var socket: Socket
        private lateinit var input: InputStream
        private lateinit var output: OutputStream

        fun handshake(path: String, protocol: String? = null) {
            socket = Socket(host, port)
            input = socket.getInputStream()
            output = socket.getOutputStream()
            val keyBytes = ByteArray(16)
            Random().nextBytes(keyBytes)
            val key = Base64.getEncoder().encodeToString(keyBytes)
            val req = buildString {
                append("GET $path HTTP/1.1\r\n")
                append("Host: $host:$port\r\n")
                append("Upgrade: websocket\r\n")
                append("Connection: Upgrade\r\n")
                append("Sec-WebSocket-Key: $key\r\n")
                append("Sec-WebSocket-Version: 13\r\n")
                if (protocol != null) {
                    append("Sec-WebSocket-Protocol: $protocol\r\n")
                }
                append("\r\n")
            }
            output.write(req.toByteArray(Charsets.UTF_8))
            output.flush()
            val statusLine = readStatusLine()
            if (!statusLine.contains(" 101 ")) {
                throw IOException("WebSocket 握手失败: $statusLine")
            }
            // 消费剩余响应头（直到空行），否则会被当成帧解析
            while (true) {
                val line = readStatusLine()
                if (line.isEmpty()) break
            }
        }

        private fun readStatusLine(): String {
            val sb = StringBuilder()
            while (true) {
                val c = input.read()
                if (c < 0) throw IOException("握手期间连接关闭")
                if (c == '\n'.code) return sb.toString().trimEnd('\r')
                sb.append(c.toChar())
            }
        }

        fun sendText(text: String) {
            val payload = text.toByteArray(Charsets.UTF_8)
            val mask = byteArrayOf(0x12, 0x34, 0x56, 0x78)
            output.write(0x81) // FIN + text
            val len = payload.size
            when {
                len < 126 -> output.write(0x80 or len)
                len < 65536 -> {
                    output.write(0x80 or 126)
                    output.write(len shr 8)
                    output.write(len and 0xFF)
                }
                else -> {
                    output.write(0x80 or 127)
                    for (i in 7 downTo 0) output.write(((len.toLong() shr (i * 8)) and 0xFF).toInt())
                }
            }
            output.write(mask)
            for (i in payload.indices) {
                output.write(payload[i].toInt() xor mask[i % 4].toInt())
            }
            output.flush()
        }

        private fun readFrame(timeoutMs: Int): WsFrame {
            socket.soTimeout = timeoutMs
            val b0 = input.read()
            if (b0 < 0) throw IOException("连接已关闭")
            val opcode = b0 and 0x0F
            val b1 = input.read()
            if (b1 < 0) throw IOException("连接已关闭")
            val masked = b1 and 0x80 != 0
            var len = b1 and 0x7F
            if (len == 126) {
                len = (input.read() shl 8) or input.read()
            } else if (len == 127) {
                var longLen = 0L
                for (i in 0 until 8) longLen = (longLen shl 8) or input.read().toLong()
                len = longLen.toInt()
            }
            val maskKey = if (masked) ByteArray(4).also { readFully(it) } else null
            val payload = ByteArray(len)
            readFully(payload)
            if (maskKey != null) {
                for (i in payload.indices) {
                    payload[i] = (payload[i].toInt() xor maskKey[i % 4].toInt()).toByte()
                }
            }
            return WsFrame(opcode, payload)
        }

        private fun readFully(buf: ByteArray) {
            var off = 0
            while (off < buf.size) {
                val n = input.read(buf, off, buf.size - off)
                if (n < 0) throw IOException("连接已关闭")
                off += n
            }
        }

        private fun sendPong(payload: ByteArray) {
            output.write(0x8A) // pong
            output.write(payload.size)
            output.write(payload)
            output.flush()
        }

        /**
         * 读取文本帧直到收到 close 帧；ping 自动回 pong。
         * 返回 [texts, closeReason]。
         */
        fun collectUntilClose(timeoutMs: Int): Pair<MutableList<String>, String> {
            val texts = mutableListOf<String>()
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                val remaining = (deadline - System.currentTimeMillis()).toInt().coerceAtLeast(100)
                val frame = readFrame(remaining)
                when (frame.opcode) {
                    0x1 -> texts.add(String(frame.payload, Charsets.UTF_8))
                    0x8 -> return texts to String(frame.payload, Charsets.UTF_8)
                    0x9 -> sendPong(frame.payload)
                    else -> Unit
                }
            }
            throw IOException("等待 close 帧超时")
        }

        fun close() {
            runCatching { socket.close() }
        }
    }

    // ---------- 测试 ----------

    /** 返回失败数；0 = 全部通过 */
    fun run(httpPort: Int, wsPort: Int): Int {
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

        AppConfig.jsSourceApiToken = API_TOKEN
        val token = API_TOKEN
        val wsProtocol = BookSourceController.jsSourceWebSocketProtocol(token)!!
        val base = "http://127.0.0.1:$httpPort"
        val (mock, mockPort) = startMockServer()
        val ruleSourceUrl = "http://127.0.0.1:$mockPort"

        val ruleSource = BookSource(
            bookSourceUrl = ruleSourceUrl,
            bookSourceName = "API冒烟规则源",
            bookSourceGroup = "测试",
            enabled = true,
            header = """{"User-Agent":"SmokeUA/1.0"}""",
            searchUrl = "/search?key={{key}}&page={{page}}",
            ruleSearch = SearchRule(
                bookList = "@css:ul.list li",
                name = "@css:a.name@text",
                author = "@css:span.author@text",
                bookUrl = "@css:a.name@href",
                kind = "@css:span.kind@text",
                intro = "@css:span.intro@text",
            ),
            ruleBookInfo = BookInfoRule(
                name = "@css:h1.title@text",
                author = "@css:span.author@text",
                intro = "@css:div.intro@text",
                tocUrl = "@css:a.toc@href",
            ),
            ruleToc = TocRule(
                chapterList = "@css:ul.toc li",
                chapterName = "@css:a@text",
                chapterUrl = "@css:a@href",
            ),
            ruleContent = ContentRule(
                content = "@css:div.content@text",
            ),
        )

        try {
            // ================= T5.1 HTTP 路由整合 =================
            check("T5.1 GET /api/health 存活检查") {
                val resp = body("$base/api/health")
                require(isSuccess(resp)) { "health=$resp" }
                require(resp.contains("legado-desktop-backend")) { "service 缺失" }
            }

            check("T5.1 OPTIONS 预检 CORS 头") {
                val builder = HttpRequest.newBuilder(URI("$base/api/health"))
                    .timeout(Duration.ofSeconds(60))
                    .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                builder.header("Origin", "http://localhost:5173")
                val resp = client.send(builder.build(), HttpResponse.BodyHandlers.ofString(Charsets.UTF_8))
                val respHeaders = resp.headers().map().entries.associate { (k, v) -> k.lowercase() to v.joinToString(",") }
                require(resp.statusCode() == 200) { "status=${resp.statusCode()}" }
                require(respHeaders["access-control-allow-methods"]?.contains("GET") == true) {
                    "缺少 Access-Control-Allow-Methods: $respHeaders"
                }
                require(respHeaders["access-control-allow-origin"]?.contains("localhost") == true) {
                    "缺少 Access-Control-Allow-Origin: $respHeaders"
                }
            }

            check("T5.1 未知路由返回 isSuccess:false") {
                val resp = body("$base/nonexistent")
                require(!isSuccess(resp)) { "404 响应=$resp" }
            }

            check("T5.1 书源写路由无令牌被拒绝") {
                val resp = post("$base/saveBookSource", GSON.toJson(ruleSource))
                require(!isSuccess(resp)) { "应被拒绝: $resp" }
            }

            check("T5.1 HTTP 日志读路由无令牌被拒绝") {
                val resp = body("$base/getHttpLogs")
                require(!isSuccess(resp)) { "应被拒绝: $resp" }
            }

            // ================= T5.2 书源 API =================
            check("T5.2 POST /saveBookSource 保存书源") {
                val resp = post("$base/saveBookSource", GSON.toJson(ruleSource), token)
                require(isSuccess(resp)) { "save=$resp" }
            }

            check("T5.2 GET /getBookSources 源列表") {
                val resp = body("$base/getBookSources", token)
                require(isSuccess(resp) && resp.contains("API冒烟规则源")) { "list=$resp" }
            }

            check("T5.2 GET /getBookSource 单源查询") {
                val resp = body("$base/getBookSource?url=${uriEncode(ruleSourceUrl)}", token)
                require(isSuccess(resp) && resp.contains("API冒烟规则源")) { "get=$resp" }
            }

            check("T5.2 POST /saveBookSources 批量保存") {
                val resp = post("$base/saveBookSources", GSON.toJson(listOf(ruleSource)), token)
                require(isSuccess(resp)) { "batch=$resp" }
            }

            // ================= T5.4 WebSocket：searchBook 结果流 =================
            check("T5.4 WS /searchBook 多源搜索结果流") {
                val ws = TestWsClient("127.0.0.1", wsPort)
                try {
                    ws.handshake("/searchBook", "legado, $wsProtocol")
                    ws.sendText("""{"key":"斗破"}""")
                    val (texts, reason) = ws.collectUntilClose(30_000)
                    require(texts.isNotEmpty()) { "未收到任何结果帧" }
                    val received = texts.any { text ->
                        val list = GSON.fromJsonArray<JsonObject>(text).getOrNull()
                        list != null && list.any { it.get("name")?.asString == "斗破苍穹" }
                    }
                    require(received) { "结果帧未包含斗破苍穹: ${texts.joinToString("|")}" }
                    require(reason.contains("Search finish") || reason.isBlank()) { "close reason=$reason" }
                } finally {
                    ws.close()
                }
            }

            // ================= T5.5 端到端闭环 =================
            check("T5.5 POST /saveBook 加书架") {
                val book = Book(
                    bookUrl = "$ruleSourceUrl/book/1",
                    name = "斗破苍穹",
                    author = "天蚕土豆",
                    origin = ruleSourceUrl,
                    originName = "API冒烟规则源",
                )
                val resp = post("$base/saveBook", GSON.toJson(book), token)
                require(isSuccess(resp)) { "saveBook=$resp" }
            }

            check("T5.5 GET /getBookshelf 书架") {
                val resp = body("$base/getBookshelf", token)
                require(isSuccess(resp) && resp.contains("斗破苍穹")) { "bookshelf=$resp" }
            }

            check("T5.5 POST /saveBookProgress 阅读进度") {
                val resp = post(
                    "$base/saveBookProgress",
                    """{"name":"斗破苍穹","author":"天蚕土豆","durChapterIndex":0,"durChapterPos":42,"durChapterTime":0,"durChapterTitle":"第一章 陨落的天才"}""",
                    token
                )
                require(isSuccess(resp)) { "progress=$resp" }
            }

            check("T5.5 GET /getChapterList 目录（触发 refreshToc）") {
                val resp = body("$base/getChapterList?url=${uriEncode("$ruleSourceUrl/book/1")}", token)
                require(isSuccess(resp) && resp.contains("第一章 陨落的天才")) { "toc=$resp" }
            }

            check("T5.5 GET /getBookContent 正文") {
                val resp = body(
                    "$base/getBookContent?url=${uriEncode("$ruleSourceUrl/book/1")}&index=0",
                    token
                )
                require(isSuccess(resp) && resp.contains("萧炎")) { "content=$resp" }
            }

            check("T5.5 进度已落库") {
                val book = io.legado.desktop.data.appDb.bookDao.getBook("$ruleSourceUrl/book/1")
                require(book != null && book.durChapterPos == 42) { "durChapterPos=${book?.durChapterPos}" }
            }

            check("T5.5 POST /deleteBook 删书") {
                val book = Book(bookUrl = "$ruleSourceUrl/book/1", name = "斗破苍穹", author = "天蚕土豆")
                val resp = post("$base/deleteBook", GSON.toJson(book), token)
                require(isSuccess(resp)) { "deleteBook=$resp" }
                val shelf = body("$base/getBookshelf", token)
                require(!shelf.contains("斗破苍穹")) { "删除后仍在书架: $shelf" }
            }

            // ================= T5.4 WebSocket：书源调试 =================
            check("T5.4 WS /bookSourceDebug 步骤日志流") {
                val ws = TestWsClient("127.0.0.1", wsPort)
                try {
                    ws.handshake("/bookSourceDebug", "legado, $wsProtocol")
                    ws.sendText("""{"tag":"$ruleSourceUrl","key":"斗破"}""")
                    val (texts, reason) = ws.collectUntilClose(60_000)
                    require(texts.isNotEmpty()) { "未收到任何调试日志" }
                    require(texts.joinToString("\n").contains("搜索")) { "缺少搜索步骤日志" }
                    require(reason.contains("调试结束")) { "close reason=$reason" }
                } finally {
                    ws.close()
                }
            }

            // ================= T5.2 RSS API + 订阅源调试 =================
            val rssSource = RssSource(
                sourceUrl = "$ruleSourceUrl/rss.xml",
                sourceName = "API冒烟订阅源",
                enabled = true,
            )
            check("T5.2 POST /saveRssSource 保存订阅源") {
                val resp = post("$base/saveRssSource", GSON.toJson(rssSource), token)
                require(isSuccess(resp)) { "saveRss=$resp" }
            }

            check("T5.2 GET /getRssSources /getRssSource") {
                val list = body("$base/getRssSources", token)
                require(isSuccess(list) && list.contains("API冒烟订阅源")) { "rssList=$list" }
                val one = body("$base/getRssSource?url=${uriEncode("$ruleSourceUrl/rss.xml")}", token)
                require(isSuccess(one)) { "rssOne=$one" }
            }

            check("T5.4 WS /rssSourceDebug 步骤日志流") {
                val ws = TestWsClient("127.0.0.1", wsPort)
                try {
                    ws.handshake("/rssSourceDebug", "legado, $wsProtocol")
                    ws.sendText("""{"tag":"$ruleSourceUrl/rss.xml"}""")
                    val (texts, reason) = ws.collectUntilClose(60_000)
                    require(texts.isNotEmpty()) { "未收到任何调试日志" }
                    require(reason.contains("调试结束")) { "close reason=$reason" }
                } finally {
                    ws.close()
                }
            }

            check("T5.2 POST /deleteRssSources 删除订阅源") {
                val resp = post("$base/deleteRssSources", GSON.toJson(listOf(rssSource)), token)
                require(isSuccess(resp)) { "delRss=$resp" }
            }

            // ================= T5.2 替换规则 API =================
            // 注：原版 ReplaceRuleController.saveRule/delete 成功路径不 setData（isSuccess 恒 false），
            // 客户端以 getReplaceRules 列表核对生效状态（忠于原版，不改业务逻辑）
            check("T5.2 POST /saveReplaceRule 保存 + GET /getReplaceRules 核对") {
                val rule = ReplaceRule(
                    name = "冒烟替换",
                    pattern = "萧炎",
                    replacement = "XY",
                    isEnabled = true,
                    isRegex = false,
                )
                val saved = post("$base/saveReplaceRule", GSON.toJson(rule), token)
                require(saved.contains("isSuccess")) { "saveRule 响应非 ReturnData: $saved" }
                val list = body("$base/getReplaceRules", token)
                require(isSuccess(list) && list.contains("冒烟替换")) { "ruleList=$list" }
            }

            check("T5.2 POST /testReplaceRule 替换测试") {
                val payload = """{"rule":${GSON.toJson(
                    ReplaceRule(
                        name = "测试",
                        pattern = "萧炎",
                        replacement = "XY",
                        isEnabled = true,
                        isRegex = false,
                    )
                )},"text":"萧炎，陨落的天才"}"""
                val resp = post("$base/testReplaceRule", payload, token)
                require(isSuccess(resp) && dataString(resp).contains("XY")) { "test=$resp" }
            }

            check("T5.2 POST /deleteReplaceRule 删除生效") {
                // 原版 delete 按主键 id 删除：必须把库里的完整规则（含 id）回传
                val rule = io.legado.desktop.data.appDb.replaceRuleDao.all.first { it.name == "冒烟替换" }
                val resp = post("$base/deleteReplaceRule", GSON.toJson(rule), token)
                require(resp.contains("isSuccess")) { "delRule=$resp" }
                val list = body("$base/getReplaceRules", token)
                require(!list.contains("冒烟替换")) { "删除后仍存在: $list" }
            }

            // ================= T5.2 批量删除书源 =================
            check("T5.2 POST /deleteBookSources 批量删除") {
                val resp = post("$base/deleteBookSources", GSON.toJson(listOf(ruleSource)), token)
                require(isSuccess(resp)) { "delSources=$resp" }
            }

            // ================= T5.3 阅读配置 =================
            check("T5.3 POST /saveReadConfig + GET /getReadConfig") {
                val cfg = """{"fontSize":20,"lineHeight":1.5}"""
                val saved = post("$base/saveReadConfig", cfg, token)
                require(isSuccess(saved)) { "saveCfg=$saved" }
                val got = body("$base/getReadConfig", token)
                require(isSuccess(got) && got.contains("fontSize")) { "getCfg=$got" }
            }

            // ================= T5.2 HTTP 日志 =================
            check("T5.2 GET /getHttpLogs 日志列表") {
                val resp = body("$base/getHttpLogs?limit=5", token)
                require(isSuccess(resp) && resp.contains("logs")) { "logs=$resp" }
            }

            check("T5.2 GET /getHttpLog 缺 id 报错") {
                val resp = body("$base/getHttpLog", token)
                require(!isSuccess(resp)) { "should fail: $resp" }
            }

            // ============ T7.7/T7.8 前端契约：令牌设置 + Cookie 管理 + 分组 ============
            check("T7.8 GET /getBookGroups 分组列表") {
                val resp = body("$base/getBookGroups", token)
                require(isSuccess(resp) && resp.contains("data")) { "groups=$resp" }
            }

            check("T7.8 POST /setJsSourceToken 运行时设置令牌") {
                val resp = post("$base/setJsSourceToken", """{"token":"runtime-token"}""")
                require(isSuccess(resp)) { "setToken=$resp" }
                require(AppConfig.jsSourceApiToken == "runtime-token") { "token=${AppConfig.jsSourceApiToken}" }
                // 还原，避免影响后续令牌校验断言
                post("$base/setJsSourceToken", """{"token":"$token"}""")
                require(AppConfig.jsSourceApiToken == token) { "token restore failed: ${AppConfig.jsSourceApiToken}" }
            }

            check("T7.8 GET /getCookies 无令牌被拒绝") {
                val resp = body("$base/getCookies")
                require(!isSuccess(resp)) { "应被拒绝: $resp" }
            }

            check("T7.8 GET /getCookies 初始为空") {
                val resp = body("$base/getCookies", token)
                require(isSuccess(resp) && resp.contains("[]")) { "cookies=$resp" }
            }

            check("T7.8 POST /setCookie + GET /getCookies 生效") {
                val set = post(
                    "$base/setCookie",
                    """{"url":"$ruleSourceUrl","cookie":"session=x; theme=dark"}""",
                    token
                )
                require(isSuccess(set)) { "setCookie=$set" }
                val list = body("$base/getCookies", token)
                require(isSuccess(list) && list.contains("theme") && list.contains("dark")) { "cookies=$list" }
            }

            check("T7.8 POST /clearCookies 删除单个") {
                val resp = post("$base/clearCookies", """{"url":"$ruleSourceUrl"}""", token)
                require(isSuccess(resp)) { "clear=$resp" }
                val list = body("$base/getCookies", token)
                require(!list.contains("theme")) { "删除后仍存在: $list" }
            }

            check("T7.8 POST /clearCookies 清空全部") {
                post("$base/setCookie", """{"url":"$ruleSourceUrl","cookie":"a=1"}""", token)
                val resp = post("$base/clearCookies", """{}""", token)
                require(isSuccess(resp)) { "clearAll=$resp" }
                val list = body("$base/getCookies", token)
                require(!list.contains("a=1")) { "清空后仍存在: $list" }
            }

        } finally {
            // 清理：删除测试源 + 关闭 mock + 还原令牌
            runCatching { io.legado.desktop.help.source.SourceHelp.deleteBookSource(ruleSourceUrl) }
            runCatching { mock.stop(0) }
            runCatching { AppConfig.jsSourceApiToken = null }
        }
        return fail
    }

    private fun uriEncode(url: String): String =
        java.net.URLEncoder.encode(url, StandardCharsets.UTF_8.toString())
}
