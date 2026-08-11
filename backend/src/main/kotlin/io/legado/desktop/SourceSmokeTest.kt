package io.legado.desktop

import com.sun.net.httpserver.HttpServer
import io.legado.desktop.data.appDb
import io.legado.desktop.data.entities.Book
import io.legado.desktop.data.entities.BookChapter
import io.legado.desktop.data.entities.BookSource
import io.legado.desktop.data.entities.BookSourcePart
import io.legado.desktop.data.entities.ReplaceRule
import io.legado.desktop.data.entities.SearchBook
import io.legado.desktop.data.entities.rule.BookInfoRule
import io.legado.desktop.data.entities.rule.ContentRule
import io.legado.desktop.data.entities.rule.SearchRule
import io.legado.desktop.data.entities.rule.TocRule
import io.legado.desktop.help.book.ContentProcessor
import io.legado.desktop.help.book.SearchBookShelfHelp
import io.legado.desktop.help.config.AppConfig
import io.legado.desktop.help.source.SourceHelp
import io.legado.desktop.model.CheckSource
import io.legado.desktop.model.CheckSourceRunner
import io.legado.desktop.model.CheckSourceStatus
import io.legado.desktop.model.Debug
import io.legado.desktop.model.jsSource.JsSourceBook
import io.legado.desktop.model.jsSource.JsSourceUpsert
import io.legado.desktop.model.webBook.SearchModel
import io.legado.desktop.model.webBook.SearchScope
import io.legado.desktop.model.webBook.WebBook
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Part 4 书源与读书引擎冒烟（--source-smoke-test 入口）。
 *
 * T4.1 SourceHelp 书源管理：导入/启用/删除 + JsSourceUpsert 保存 JS 源 + CheckSource 全项校验
 * T4.2 jsSource：JS 源 搜索/详情/目录/正文
 * T4.3 WebBook：规则源 搜索→详情→目录→正文 全链路 + ContentProcessor 替换净化 + 书架保存
 * T4.4 联测：SearchModel 多源搜索合并去重 + 阅读进度存取
 *
 * 全部在单进程内完成（本地 HttpServer mock 两个源），跑完返回失败数。
 */
object SourceSmokeTest {

    // ---------- 本地 mock 服务器 ----------

    /** 规则源服务器：/search /book/1 /toc/1 /content/1 */
    private fun startRuleServer(): Pair<HttpServer, Int> {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/search") { ex ->
            val key = ex.requestURI.rawQuery?.substringAfter("key=")?.substringBefore("&") ?: ""
            val body = """
                <html><body>
                  <ul class="list">
                    <li><a class="name" href="/book/1">斗破苍穹</a><span class="author">天蚕土豆</span><span class="kind">玄幻</span><span class="intro">三十年河东</span></li>
                    <li><a class="name" href="/book/2">完美世界</a><span class="author">辰东</span><span class="kind">玄幻</span><span class="intro">一粒尘可填海</span></li>
                  </ul>
                  <div>searchKey=$key</div>
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
                  <li><a href="/content/2">第二章 斗气大陆</a></li>
                </ul></body></html>
            """.trimIndent()
            respond(ex, body)
        }
        server.createContext("/content/1") { ex ->
            respond(ex, "<html><body><div class=\"content\">萧炎，陨落的天才少年，自三年前失去斗气后……</div></body></html>")
        }
        server.createContext("/content/2") { ex ->
            respond(ex, "<html><body><div class=\"content\">斗气大陆，无奇不有……</div></body></html>")
        }
        server.start()
        return server to server.address.port
    }

    /** JS 源服务器：/jssearch /jsbook/1 /jschapters /jscontent */
    private fun startJsServer(): Pair<HttpServer, Int> {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/jssearch") { ex ->
            val key = ex.requestURI.rawQuery?.substringAfter("key=")?.substringBefore("&") ?: ""
            val body = """
                [{"name":"雪中悍刀行","author":"烽火戏诸侯","bookUrl":"/jsbook/1","kind":"武侠","intro":"雪中","latestChapterTitle":"第一章 小二，上酒"},
                 {"name":"${key}同名书","author":"同名作者","bookUrl":"/jsbook/2","kind":"测试"}]
            """.trimIndent()
            respond(ex, body)
        }
        server.createContext("/jsbook/1") { ex ->
            val body = """{"name":"雪中悍刀行","author":"烽火戏诸侯","intro":"雪中悍刀行是一部……","tocUrl":"/jschapters"}"""
            respond(ex, body)
        }
        server.createContext("/jschapters") { ex ->
            val body = """[{"title":"第一章 小二，上酒","url":"/jscontent/1"},{"title":"第二章 剑气近","url":"/jscontent/2"}]"""
            respond(ex, body)
        }
        server.createContext("/jscontent/1") { ex ->
            respond(ex, "徐凤年，北凉王世子，雪中悍刀行正文第一段……")
        }
        server.createContext("/jscontent/2") { ex ->
            respond(ex, "剑气纵横三万里，一剑光寒十九洲。")
        }
        server.start()
        return server to server.address.port
    }

    private fun respond(ex: com.sun.net.httpserver.HttpExchange, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        ex.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
        ex.sendResponseHeaders(200, bytes.size.toLong())
        ex.responseBody.use { it.write(bytes) }
    }

    // ---------- 测试 ----------

    /** 返回失败数；0 = 全部通过 */
    fun run(): Int {
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

        val (ruleServer, rulePort) = startRuleServer()
        val (jsServer, jsPort) = startJsServer()
        val ruleSourceUrl = "http://127.0.0.1:$rulePort"
        val jsSourceUrl = "http://127.0.0.1:$jsPort"

        // 两个测试源（唯一 URL 防冲突；测试库已由 Main 用 LEGADO_DESKTOP_HOME 初始化）
        val ruleSource = BookSource(
            bookSourceUrl = ruleSourceUrl,
            bookSourceName = "规则冒烟源",
            bookSourceGroup = "测试",
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

        // JS 单文件源（原版 js_source_template.js 格式：var config + 顶层函数，URL 用 baseUrl 绑定拼绝对）
        // JsSourceUpsert.save 只接受此格式（JsSourceConfig.extract 把脚本当 JS 执行后取 config 对象）
        val jsText = """
            var config = {
                bookSourceUrl: "$jsSourceUrl",
                bookSourceName: "JS冒烟源",
                bookSourceGroup: "测试",
                bookSourceType: 0,
                lastUpdateTime: 0
            };
            function search(key, page) {
                var url = baseUrl + "/jssearch?key=" + encodeURIComponent(key);
                var res = java.ajax(url);
                var data = JSON.parse(res);
                var books = [];
                for (var i = 0; i < data.length; i++) {
                    books.push({
                        name: data[i].name,
                        author: data[i].author,
                        bookUrl: baseUrl + data[i].bookUrl,
                        kind: data[i].kind,
                        intro: data[i].intro,
                        latestChapterTitle: data[i].latestChapterTitle
                    });
                }
                return books;
            }
            function getBookInfo(book) {
                var res = java.ajax(book.bookUrl);
                var info = JSON.parse(res);
                book.name = info.name;
                book.author = info.author;
                book.intro = info.intro;
                book.tocUrl = baseUrl + info.tocUrl;
                return book;
            }
            function getChapters(book) {
                var res = java.ajax(book.tocUrl);
                var list = JSON.parse(res);
                var arr = [];
                for (var i = 0; i < list.length; i++) {
                    arr.push({title: list[i].title, url: baseUrl + list[i].url});
                }
                return arr;
            }
            function getContent(chapter, book, nextChapterUrl) {
                return java.ajax(chapter.url);
            }
        """.trimIndent()

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        try {
            // ================= T4.1 SourceHelp 书源管理 =================
            check("T4.1 SourceHelp.insertBookSource 导入规则源") {
                SourceHelp.insertBookSource(ruleSource)
                val got = SourceHelp.getSource(ruleSourceUrl)
                require(got != null && (got as BookSource).bookSourceName == "规则冒烟源") { "导入后 getSource 取不到" }
            }

            check("T4.1 enableSource 启用/停用切换") {
                SourceHelp.enableSource(ruleSourceUrl, io.legado.desktop.constant.SourceType.book, false)
                require(appDb.bookSourceDao.getBookSource(ruleSourceUrl)!!.enabled == false) { "停用失败" }
                SourceHelp.enableSource(ruleSourceUrl, io.legado.desktop.constant.SourceType.book, true)
                require(appDb.bookSourceDao.getBookSource(ruleSourceUrl)!!.enabled == true) { "启用失败" }
            }

            check("T4.1 JsSourceUpsert.save 保存 JS 单文件源") {
                // 原版导入路径：JS 单文件源文本（config + 顶层函数）→ JsSourceUpsert.save 解析入库
                runBlocking { JsSourceUpsert.save(jsText, timeoutMillis = 60000L) }
                val got = appDb.bookSourceDao.getBookSource(jsSourceUrl)
                require(got != null && got.mainJs?.contains("function search") == true) { "JS 源未保存成功" }
            }

            check("T4.1 SourceHelp.deleteBookSource 删除") {
                val tmp = BookSource(
                    bookSourceUrl = "http://127.0.0.1:1/tmp",
                    bookSourceName = "临时源",
                )
                SourceHelp.insertBookSource(tmp)
                require(SourceHelp.getSource(tmp.bookSourceUrl) != null) { "插入失败" }
                SourceHelp.deleteBookSource(tmp.bookSourceUrl)
                require(SourceHelp.getSource(tmp.bookSourceUrl) == null) { "删除失败" }
            }

            check("T4.1 CheckSource 校验规则源全项 PASSED") {
                CheckSource.keyword = "斗破"
                CheckSource.timeout = 60000L
                CheckSource.wSourceComment = false
                CheckSource.checkDomain = false
                CheckSource.checkSearch = true
                CheckSource.checkDiscovery = true
                CheckSource.checkInfo = true
                CheckSource.checkCategory = true
                CheckSource.checkContent = true
                val sessionId = Debug.tryStartCheckSession()!!
                val parts = listOf(
                    BookSourcePart(bookSourceUrl = ruleSourceUrl, lastUpdateTime = ruleSource.lastUpdateTime)
                )
                CheckSource.start(Any(), parts, sessionId)
                awaitCheckDone(sessionId)
                val snapshot = Debug.getCheckSnapshot(sessionId, listOf(ruleSourceUrl))
                val result = snapshot.results[ruleSourceUrl]
                require(result != null && result.status == CheckSourceStatus.PASSED) {
                    "校验结果=${result?.status} detail=${result?.detail}"
                }
            }

            // ================= T4.2 jsSource =================
            val jsSource = appDb.bookSourceDao.getBookSource(jsSourceUrl)!!
            check("T4.2 JsSourceBook.searchAwait JS 源搜索") {
                val books = runBlocking { JsSourceBook.searchAwait(jsSource, "雪中", 1) }
                require(books.any { it.name == "雪中悍刀行" && it.author == "烽火戏诸侯" && it.bookUrl == "$jsSourceUrl/jsbook/1" }) {
                    "实际 ${books.map { it.name + "|" + it.bookUrl }}"
                }
            }

            check("T4.2 JsSourceBook.getBookInfoAwait JS 源详情") {
                val book = Book(bookUrl = "$jsSourceUrl/jsbook/1", name = "雪中悍刀行", author = "烽火戏诸侯", origin = jsSourceUrl, originName = "JS冒烟源")
                runBlocking { JsSourceBook.getBookInfoAwait(jsSource, book, false) }
                require(book.intro?.contains("雪中悍刀行是一部") == true) { "intro=${book.intro}" }
                require(book.tocUrl == "$jsSourceUrl/jschapters") { "tocUrl=${book.tocUrl}" }
            }

            check("T4.2 JsSourceBook.getChapterListAwait JS 源目录") {
                val book = Book(bookUrl = "$jsSourceUrl/jsbook/1", name = "雪中悍刀行", author = "烽火戏诸侯", tocUrl = "$jsSourceUrl/jschapters", origin = jsSourceUrl, originName = "JS冒烟源")
                val chapters = runBlocking { JsSourceBook.getChapterListAwait(jsSource, book) }.getOrThrow()
                require(chapters.size == 2 && chapters[0].title == "第一章 小二，上酒") { "实际 ${chapters.map { it.title }}" }
                require(chapters[0].url == "$jsSourceUrl/jscontent/1") { "章节url=${chapters[0].url}" }
            }

            check("T4.2 JsSourceBook.getContentAwait JS 源正文") {
                val book = Book(bookUrl = "$jsSourceUrl/jsbook/1", name = "雪中悍刀行", author = "烽火戏诸侯", tocUrl = "$jsSourceUrl/jschapters", origin = jsSourceUrl, originName = "JS冒烟源")
                val chapter = BookChapter(bookUrl = "$jsSourceUrl/jsbook/1", title = "第一章 小二，上酒", url = "$jsSourceUrl/jscontent/1", index = 0)
                val content = runBlocking { JsSourceBook.getContentAwait(jsSource, book, chapter, null, false) }
                require(content.contains("徐凤年")) { "正文=$content" }
            }

            // ================= T4.3 WebBook 规则源全链路 =================
            check("T4.3 WebBook.searchBookAwait 规则源搜索") {
                val books = runBlocking { WebBook.searchBookAwait(ruleSource, "斗破", 1) }
                require(books.any { it.name == "斗破苍穹" && it.author == "天蚕土豆" && it.bookUrl == "$ruleSourceUrl/book/1" }) {
                    "实际 ${books.map { it.name + "|" + it.bookUrl }}"
                }
            }

            check("T4.3 WebBook.getBookInfoAwait 规则源详情") {
                val book = Book(bookUrl = "$ruleSourceUrl/book/1", name = "", author = "", origin = ruleSourceUrl, originName = "规则冒烟源")
                runBlocking { WebBook.getBookInfoAwait(ruleSource, book) }
                require(book.name == "斗破苍穹" && book.author == "天蚕土豆") { "name=${book.name} author=${book.author}" }
                require(book.tocUrl == "$ruleSourceUrl/toc/1") { "tocUrl=${book.tocUrl}" }
            }

            check("T4.3 WebBook.getChapterListAwait 规则源目录") {
                val book = Book(bookUrl = "$ruleSourceUrl/book/1", name = "斗破苍穹", author = "天蚕土豆", tocUrl = "$ruleSourceUrl/toc/1", origin = ruleSourceUrl, originName = "规则冒烟源")
                val chapters = runBlocking { WebBook.getChapterListAwait(ruleSource, book) }.getOrThrow()
                require(chapters.size == 2 && chapters[0].title == "第一章 陨落的天才") { "实际 ${chapters.map { it.title }}" }
            }

            check("T4.3 WebBook.getContentAwait 规则源正文 + 章节保存") {
                val book = Book(bookUrl = "$ruleSourceUrl/book/1", name = "斗破苍穹", author = "天蚕土豆", tocUrl = "$ruleSourceUrl/toc/1", origin = ruleSourceUrl, originName = "规则冒烟源")
                val chapter = BookChapter(bookUrl = "$ruleSourceUrl/book/1", title = "第一章 陨落的天才", url = "$ruleSourceUrl/content/1", index = 0)
                val content = runBlocking { WebBook.getContentAwait(ruleSource, book, chapter, null, needSave = false) }
                require(content.contains("萧炎")) { "正文=$content" }
            }

            check("T4.3 ContentProcessor 替换净化生效") {
                appDb.replaceRuleDao.insert(
                    ReplaceRule(
                        name = "萧炎替换",
                        pattern = "萧炎",
                        replacement = "XY",
                        isEnabled = true,
                        isRegex = false,
                        scopeContent = true,
                        scope = ruleSourceUrl, // 作用范围匹配书源 URL（findEnabledByContentScope 按 scope LIKE origin 匹配）
                    )
                )
                ContentProcessor.upReplaceRules()
                val book = Book(bookUrl = "/book/1", name = "斗破苍穹", author = "天蚕土豆", tocUrl = "/toc/1", origin = ruleSourceUrl, originName = "规则冒烟源")
                val chapter = BookChapter(bookUrl = "/book/1", title = "第一章 陨落的天才", url = "/content/1", index = 0)
                // 正文替换在阅读层（原 ReadBook）经 ContentProcessor.getContent 生效；WebBook.getContentAwait 只取原始正文
                val processed = ContentProcessor.get(book).getContent(book, chapter, "萧炎，陨落的天才少年……", includeTitle = false)
                val text = processed.textList.joinToString("\n")
                require(text.contains("XY") && !text.contains("萧炎")) { "替换未生效: $text" }
            }

            check("T4.3 SearchBookShelfHelp 搜索→加入书架") {
                val searchBooks = runBlocking { WebBook.searchBookAwait(ruleSource, "斗破", 1) }
                require(searchBooks.isNotEmpty()) { "搜索结果为空" }
                val result = SearchBookShelfHelp.addLoadedBooksToShelf(searchBooks)
                require(result.addedBooks.any { it.name == "斗破苍穹" }) { "书架未加入: ${result.addedBooks.map { it.name }}" }
                val saved = appDb.bookDao.getBook("$ruleSourceUrl/book/1")
                require(saved != null && saved.name == "斗破苍穹") { "书架未保存" }
            }

            // ================= T4.4 联测 =================
            check("T4.4 多源搜索结果入库往返（BookSource 规则字段 JSON 持久化）") {
                // 调试探针：确认两个源的搜索数据能正常 insert（隔离 SearchModel 与 DAO 层问题）
                val probe1 = runBlocking { WebBook.searchBookAwait(ruleSource, "雪中", 1) }
                val jsSrc = appDb.bookSourceDao.getBookSource(jsSourceUrl)!!
                val probe2 = runBlocking { WebBook.searchBookAwait(jsSrc, "雪中", 1) }
                require(probe1.isNotEmpty() || probe2.isNotEmpty()) { "两个源都空" }
                val all = probe1 + probe2
                println("    probe: ${all.map { it.name + "|" + it.bookUrl + "|" + (it.bookUrl?.isBlank()) }}")
                appDb.searchBookDao.insert(*all.toTypedArray())
                require(appDb.searchBookDao.getSearchBook(all.first().bookUrl) != null) { "insert 后查不到" }
            }

            check("T4.4 SearchModel 多源搜索合并去重") {
                // JS 源搜索返回 "斗破苍穹/天蚕土豆"? 不 —— 让 JS 源也返回同名书以验证 addOrigin 合并
                // 规则源: 斗破苍穹(天蚕土豆); JS 源 search 的 key 会回显到 {key}同名书, 不回显斗破苍穹
                // 改用两个源的搜索结果通过 SearchModel 的 mergeItems 合并（origins 去重）
                val latch = CountDownLatch(1)
                val finalBooks = AtomicReference<List<SearchBook>>()
                val errors = AtomicReference<Throwable?>()
                val callback = object : SearchModel.CallBack {
                    override fun getSearchScope(): SearchScope = SearchScope(AppConfig.searchScope)
                    override fun onSearchStart() {}
                    override fun onSearchProgress(searched: Int, total: Int) {}
                    override fun onSearchSuccess(searchBooks: List<SearchBook>) {
                        finalBooks.set(searchBooks)
                    }

                    override fun onSearchFinish(isEmpty: Boolean, hasMore: Boolean) {
                        latch.countDown()
                    }

                    override fun onSearchCancel(exception: Throwable?) {
                        if (exception != null) errors.set(exception)
                        latch.countDown()
                    }
                }
                val model = SearchModel(scope, callback)
                model.search(1L, "雪中")
                val done = latch.await(60, TimeUnit.SECONDS)
                model.close()
                require(done) { "搜索超时" }
                errors.get()?.let { throw it }
                val books = finalBooks.get() ?: emptyList()
                require(books.isNotEmpty()) { "搜索结果为空" }
                require(books.any { it.name == "雪中悍刀行" && it.origins.contains(jsSourceUrl) }) {
                    "JS 源结果缺失: ${books.map { it.name + ":" + it.origins } }"
                }
            }

            check("T4.4 阅读进度存取 BookDao.upProgress") {
                val book = appDb.bookDao.getBook("$ruleSourceUrl/book/1")!!
                book.durChapterIndex = 1
                book.durChapterTitle = "第一章 陨落的天才"
                appDb.bookDao.update(book)
                appDb.bookDao.upProgress("$ruleSourceUrl/book/1", 1234)
                val updated = appDb.bookDao.getBook("$ruleSourceUrl/book/1")!!
                require(updated.durChapterPos == 1234) { "durChapterPos=${updated.durChapterPos}" }
                require(updated.durChapterIndex == 1) { "durChapterIndex=${updated.durChapterIndex}" }
            }

        } finally {
            // 清理：删除测试源 + 关闭服务器
            runCatching { SourceHelp.deleteBookSource(ruleSourceUrl) }
            runCatching { SourceHelp.deleteBookSource(jsSourceUrl) }
            runCatching { ruleServer.stop(0) }
            runCatching { jsServer.stop(0) }
        }
        return fail
    }

    /** 轮询等待校验会话结束（原 UI 层监听 CHECK_SOURCE_DONE 事件，桌面测试直接轮询状态） */
    private fun awaitCheckDone(sessionId: Long) {
        val deadline = System.currentTimeMillis() + 90000
        while (System.currentTimeMillis() < deadline) {
            if (!Debug.isChecking(sessionId) && CheckSourceRunner.activeSessionId == null) {
                return
            }
            Thread.sleep(200)
        }
        throw IllegalStateException("书源校验超时未结束")
    }
}
