package io.legado.desktop

import com.script.rhino.RhinoScriptEngine
import com.sun.net.httpserver.HttpServer
import io.legado.desktop.data.entities.BookSource
import io.legado.desktop.model.SharedJsScope
import io.legado.desktop.model.analyzeRule.AnalyzeRule
import io.legado.desktop.model.analyzeRule.AnalyzeUrl
import io.legado.desktop.model.analyzeRule.RuleData
import io.legado.desktop.model.jsSource.JsSourceEngine
import java.net.InetSocketAddress
import java.net.URLDecoder

/**
 * Part 3 规则引擎冒烟（--rule-smoke-test 入口）。
 *
 * T3.1 AnalyzeRule 基础：JSoup/XPath/JSONPath/Regex 四种解析器 + 复合规则 + 变量
 * T3.2 AnalyzeUrl：搜索 URL 构造（{{key}}/{{page}}/{{js}}/@js/options）+ 真实 HTTP 请求
 * T3.3 Rhino 集成：RhinoScriptEngine 执行 / java 绑定 / SharedJsScope crypto / JsSourceEngine mainJs
 * T3.4 联测：规则源（HTML）与纯 JS 源各跑通一次全链路
 *
 * 全部在单进程内完成（本地 HttpServer mock），跑完返回失败数。
 */
object RuleSmokeTest {

    private val HTML = """
        <html><body>
          <div class="title">斗破苍穹</div>
          <ul>
            <li class="item"><a class="book" href="/book/1">书名一</a></li>
            <li class="item"><a class="book" href="/book/2">书名二</a></li>
            <li class="item"><a class="book" href="/book/3">书名三</a></li>
          </ul>
        </body></html>
    """.trimIndent()

    private val JSON = """{"code":0,"books":[{"name":"A","author":"甲"},{"name":"B","author":"乙"},{"name":"C","author":"丙"}]}"""

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

        val source = BookSource(
            bookSourceUrl = "https://rule-smoke.local",
            bookSourceName = "规则冒烟源",
            header = """{"User-Agent":"RuleSmokeUA/1.0"}"""
        )

        fun newRule(): AnalyzeRule {
            val rule = AnalyzeRule(ruleData = RuleData(), source = source)
            rule.setContent(HTML, "https://rule-smoke.local/")
            return rule
        }

        // ================= T3.1 AnalyzeRule 基础 =================
        check("T3.1 CSS 文本提取 @css:div.title@text") {
            val v = newRule().getString("@css:div.title@text")
            require(v == "斗破苍穹") { "期望'斗破苍穹' 实际'$v'" }
        }

        check("T3.1 CSS 属性列表 @css:a.book@href") {
            val list = newRule().getStringList("@css:a.book@href")!!
            require(list == listOf("/book/1", "/book/2", "/book/3")) { "实际 $list" }
        }

        check("T3.1 CSS 多元素文本列表 @css:li.item@text") {
            val list = newRule().getStringList("@css:li.item@text")!!
            require(list == listOf("书名一", "书名二", "书名三")) { "实际 $list" }
        }

        check("T3.1 XPath 文本 @XPath://div[@class='title']/text()") {
            val v = newRule().getString("@XPath://div[@class='title']/text()")
            require(v == "斗破苍穹") { "期望'斗破苍穹' 实际'$v'" }
        }

        check("T3.1 XPath 属性列表 @XPath://a/@href") {
            val list = newRule().getStringList("@XPath://a/@href")!!
            require(list == listOf("/book/1", "/book/2", "/book/3")) { "实际 $list" }
        }

        check("T3.1 JSONPath 列表 @Json:$.books[*].name") {
            val rule = AnalyzeRule(ruleData = RuleData(), source = source)
            rule.setContent(JSON)
            val list = rule.getStringList("@Json:$.books[*].name")!!
            require(list == listOf("A", "B", "C")) { "实际 $list" }
        }

        check("T3.1 JSONPath 自动识别 $.books[0].name") {
            val rule = AnalyzeRule(ruleData = RuleData(), source = source)
            rule.setContent(JSON)
            val v = rule.getString("$.books[0].name")
            require(v == "A") { "期望'A' 实际'$v'" }
        }

        check("T3.1 复合规则 @css:div.title@text@js:...") {
            val v = newRule().getString("@css:div.title@text@js:result.trim() + '!'")
            require(v == "斗破苍穹!") { "期望'斗破苍穹!' 实际'$v'" }
        }

        check("T3.1 变量存取 put + @get:{var}") {
            val rule = newRule()
            rule.put("myVar", "hello-var")
            val v = rule.getString("@get:{myVar}")
            require(v == "hello-var") { "期望'hello-var' 实际'$v'" }
        }

        // ================= T3.2 AnalyzeUrl（本地 mock 服务器） =================
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val port = server.address.port
        // GET 回显：q / page / path / UA
        server.createContext("/search") { ex ->
            val q = ex.requestURI.query ?: ""
            val ua = ex.requestHeaders.getFirst("User-Agent") ?: ""
            val body = "PATH=/search Q=$q UA=$ua"
            val bytes = body.toByteArray(Charsets.UTF_8)
            ex.responseHeaders.set("Content-Type", "text/plain; charset=utf-8")
            ex.sendResponseHeaders(200, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
        }
        // POST JSON 回显 body
        server.createContext("/post") { ex ->
            val body = ex.requestBody.readBytes().toString(Charsets.UTF_8)
            val bytes = "PATH=/post BODY=$body".toByteArray(Charsets.UTF_8)
            ex.responseHeaders.set("Content-Type", "text/plain; charset=utf-8")
            ex.sendResponseHeaders(200, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
        }
        // 任意路径回显 PATH（@js 重写用）
        server.createContext("/") { ex ->
            val path = ex.requestURI.path
            val bytes = "PATH=$path".toByteArray(Charsets.UTF_8)
            ex.sendResponseHeaders(200, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            check("T3.2 URL 构造 {{key}}/{{page}} + 真实 GET 请求") {
                val au = AnalyzeUrl(
                    "http://127.0.0.1:$port/search?q={{key}}&page={{page}}",
                    key = "三体",
                    page = 2,
                    baseUrl = "",
                    source = source
                )
                val body = au.getStrResponse().body ?: ""
                require(body.contains("q=三体")) { "body=$body" }
                require(body.contains("page=2")) { "body=$body" }
                require(body.contains("PATH=/search")) { "body=$body" }
            }

            check("T3.2 {{js}} 表达式内嵌") {
                val au = AnalyzeUrl(
                    "http://127.0.0.1:$port/search?w={{key + '++'}}",
                    key = "abc",
                    baseUrl = "",
                    source = source
                )
                val body = au.getStrResponse().body ?: ""
                require(body.contains("w=abc++")) { "body=$body" }
            }

            check("T3.2 @js: 整段重写 URL") {
                val au = AnalyzeUrl(
                    "http://127.0.0.1:$port/alpha@js:result.replace('/alpha', '/beta')",
                    baseUrl = "",
                    source = source
                )
                val body = au.getStrResponse().body ?: ""
                require(body.contains("PATH=/beta")) { "body=$body" }
            }

            check("T3.2 POST JSON body（,{options}）") {
                val au = AnalyzeUrl(
                    "http://127.0.0.1:$port/post,{\"method\":\"POST\",\"body\":{\"q\":\"{{key}}\"}}",
                    key = "测试书",
                    baseUrl = "",
                    source = source
                )
                val body = au.getStrResponse().body ?: ""
                require(body.contains("PATH=/post")) { "body=$body" }
                require(body.contains("测试书")) { "body=$body" }
            }

            check("T3.2 headerMap 注入（书源 User-Agent）") {
                val au = AnalyzeUrl(
                    "http://127.0.0.1:$port/search?q={{key}}",
                    key = "x",
                    baseUrl = "",
                    source = source
                )
                val body = au.getStrResponse().body ?: ""
                require(body.contains("UA=RuleSmokeUA/1.0")) { "body=$body" }
            }
        } finally {
            server.stop(0)
        }

        // ================= T3.3 Rhino 集成 =================
        check("T3.3 RhinoScriptEngine 基础算术") {
            val v = RhinoScriptEngine.eval("1 + 2 * 3")
            require((v as? Number)?.toDouble() == 7.0) { "实际 $v" }
        }

        check("T3.3 Rhino 函数定义与调用") {
            val v = RhinoScriptEngine.eval("(function(){ return 'he' + 'llo'; })()")
            require(v.toString() == "hello") { "实际 $v" }
        }

        check("T3.3 AnalyzeRule.evalJS java 绑定调用") {
            val rule = newRule()
            val v = rule.evalJS("java.base64Encode('abc')")
            require(v.toString() == "YWJj") { "实际 $v" }
        }

        check("T3.3 SharedJsScope CryptoJS（cryptojs.min.js 资源）") {
            val scope = SharedJsScope.getCryptoScope(this, null)
            require(scope != null) { "getCryptoScope 返回 null（/scripts/cryptojs.min.js 缺失？）" }
            val v = RhinoScriptEngine.eval("CryptoJS.MD5('abc').toString()", scope)
            require(v.toString() == "900150983cd24fb0d6963f7d28e17f72") { "MD5('abc') 实际 $v" }
        }

        check("T3.3 JsSourceEngine mainJs 顶层函数调用") {
            val jsSource = BookSource(
                bookSourceUrl = "https://js-source.local",
                bookSourceName = "JS源",
                mainJs = "function add(a, b) { return a + b; }"
            )
            val engine = JsSourceEngine(jsSource)
            val v = engine.callFunction("add", listOf("a" to 40, "b" to 2))
            require((v?.toDoubleOrNull() ?: 0.0) == 42.0) { "add(40,2) 实际 $v" }
        }

        check("T3.3 JsSourceEngine NativeObject 归一化 JSON") {
            val jsSource = BookSource(
                bookSourceUrl = "https://js-source2.local",
                bookSourceName = "JS源2",
                mainJs = "function info() { return { name: 'x', n: 1 }; }"
            )
            val engine = JsSourceEngine(jsSource)
            val v = engine.callFunction("info", emptyList())
            require(v != null && v.contains("\"name\":\"x\"") && v.contains("\"n\":1")) { "实际 $v" }
        }

        check("T3.3 callFunctionIfExists 缺失函数返回 null") {
            val jsSource = BookSource(
                bookSourceUrl = "https://js-source3.local",
                bookSourceName = "JS源3",
                mainJs = "function onlyA() { return 'a'; }"
            )
            val engine = JsSourceEngine(jsSource)
            val v = engine.callFunctionIfExists("notExist", emptyList())
            require(v == null) { "实际 $v" }
        }

        // ================= T3.4 联测：规则源 + JS 源全链路 =================
        val server2 = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val port2 = server2.address.port
        server2.createContext("/search") { ex ->
            val query = ex.requestURI.query ?: ""
            val params = query.split("&").mapNotNull { kv ->
                val idx = kv.indexOf('=')
                if (idx < 0) null else kv.substring(0, idx) to URLDecoder.decode(kv.substring(idx + 1), "UTF-8")
            }.toMap()
            val q = params["q"] ?: ""
            val html = """<html><body><ul>
                <li class="item"><a class="book" href="/book/1">$q-甲</a></li>
                <li class="item"><a class="book" href="/book/2">$q-乙</a></li>
              </ul></body></html>"""
            val bytes = html.toByteArray(Charsets.UTF_8)
            ex.responseHeaders.set("Content-Type", "text/html; charset=utf-8")
            ex.sendResponseHeaders(200, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
        }
        server2.createContext("/jssearch") { ex ->
            val body = """{"books":[{"name":"JS书1","author":"作者A"},{"name":"JS书2","author":"作者B"}]}"""
            val bytes = body.toByteArray(Charsets.UTF_8)
            ex.responseHeaders.set("Content-Type", "application/json; charset=utf-8")
            ex.sendResponseHeaders(200, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
        }
        server2.start()
        try {
            check("T3.4 联测-规则源：搜索→请求→解析 全链路") {
                val au = AnalyzeUrl(
                    "http://127.0.0.1:$port2/search?q={{key}}&page={{page}}",
                    key = "斗破",
                    page = 1,
                    baseUrl = "",
                    source = source
                )
                val body = au.getStrResponse().body ?: ""
                val rule = AnalyzeRule(ruleData = RuleData(), source = source)
                rule.setContent(body)
                val names = rule.getStringList("@css:li.item a.book@text")!!
                require(names == listOf("斗破-甲", "斗破-乙")) { "实际 $names" }
                // 链接列表（相对地址补全）
                val urls = rule.getStringList("@css:li.item a.book@href")!!
                require(urls.isNotEmpty() && urls.all { it.startsWith("/book/") }) { "实际 $urls" }
            }

            check("T3.4 联测-JS源：getSearchBooks + java.ajax 全链路") {
                val jsSource = BookSource(
                    bookSourceUrl = "https://js-e2e.local",
                    bookSourceName = "JS端到端",
                    mainJs = """
                        function getSearchBooks() {
                            var html = java.ajax('http://127.0.0.1:$port2/jssearch');
                            var obj = JSON.parse(html);
                            return obj.books;
                        }
                    """.trimIndent()
                )
                val engine = JsSourceEngine(jsSource)
                val v = engine.callFunction("getSearchBooks", listOf("key" to "测试"))
                require(v != null && v.contains("JS书1") && v.contains("JS书2")) { "实际 $v" }
            }
        } finally {
            server2.stop(0)
        }

        return fail
    }
}
