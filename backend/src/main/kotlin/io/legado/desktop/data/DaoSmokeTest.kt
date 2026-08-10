package io.legado.desktop.data

import io.legado.desktop.data.entities.AutoTaskRule
import io.legado.desktop.data.entities.Book
import io.legado.desktop.data.entities.BookChapter
import io.legado.desktop.data.entities.BookGroup
import io.legado.desktop.data.entities.BookHighlight
import io.legado.desktop.data.entities.BookSource
import io.legado.desktop.data.entities.Bookmark
import io.legado.desktop.data.entities.Cache
import io.legado.desktop.data.entities.Cookie
import io.legado.desktop.data.entities.DictRule
import io.legado.desktop.data.entities.HighlightRule
import io.legado.desktop.data.entities.HttpTTS
import io.legado.desktop.data.entities.KeyboardAssist
import io.legado.desktop.data.entities.ReadRecord
import io.legado.desktop.data.entities.ReplaceRule
import io.legado.desktop.data.entities.RssArticle
import io.legado.desktop.data.entities.RssReadRecord
import io.legado.desktop.data.entities.RssSource
import io.legado.desktop.data.entities.RssStar
import io.legado.desktop.data.entities.RuleSub
import io.legado.desktop.data.entities.SearchBook
import io.legado.desktop.data.entities.SearchKeyword
import io.legado.desktop.data.entities.Server
import io.legado.desktop.data.entities.TxtTocRule
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking

/**
 * Part 1 数据层全量冒烟（--dao-smoke-test 入口）。
 * 对 24 个 DAO 各做 insert/query/update/delete，并验证关键特性：
 * - collate localized（bookmarks/readRecord/highlights 排序）
 * - IN (:list) 展开（findByIds / getRssSources / deleteByIds / bindChapterUrl）
 * - flow 一次性查询收集
 * - 外键路径（searchBooks → book_sources）
 */
object DaoSmokeTest {

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

        val bookUrl = "https://smoke.test/book-${System.currentTimeMillis()}"
        val sourceUrl = "https://smoke.test/source-${System.currentTimeMillis()}"
        val chapterUrl = "https://smoke.test/ch-${System.currentTimeMillis()}"
        val now = System.currentTimeMillis()

        // ---------- T1.1 已实现 ----------
        check("BookDao: insert/query/update/delete") {
            val dao = appDb.bookDao
            val book = Book(bookUrl = bookUrl, name = "冒烟书", author = "冒烟作者")
            dao.insert(book)
            require(dao.getBook(bookUrl) != null) { "insert 后查询不到" }
            dao.update(book.copy(name = "冒烟书2"))
            require(dao.getBook(bookUrl)?.name == "冒烟书2") { "update 未生效" }
            require(dao.has(bookUrl)) { "has() 应为 true" }
            dao.delete(book)
            require(!dao.has(bookUrl)) { "delete 后仍存在" }
        }

        check("BookChapterDao: insert/query/delete(级联依赖 book)") {
            val dao = appDb.bookChapterDao
            appDb.bookDao.insert(Book(bookUrl = bookUrl, name = "冒烟书", author = "冒烟作者"))
            val ch = BookChapter(url = chapterUrl, title = "第一章", bookUrl = bookUrl, index = 0)
            dao.insert(ch)
            require(dao.getChapter(bookUrl, 0)?.title == "第一章") { "getChapter(index) 查询不到" }
            require(dao.getChapterCount(bookUrl) == 1) { "getChapterCount != 1" }
            dao.updateContentMetadata(bookUrl, 0, "第一章改", null)
            require(dao.getChapter(bookUrl, 0)?.title == "第一章改") { "updateContentMetadata 未生效" }
            dao.delByBook(bookUrl)
            require(dao.getChapterCount(bookUrl) == 0) { "delByBook 未删除" }
            appDb.bookDao.delete(Book(bookUrl = bookUrl))
        }

        check("BookSourceDao: insert/query/update/delete + 视图") {
            val dao = appDb.bookSourceDao
            val src = BookSource(bookSourceUrl = sourceUrl, bookSourceName = "冒烟源", bookSourceType = 0)
            dao.insert(src)
            require(dao.getBookSource(sourceUrl) != null) { "insert 后查询不到" }
            require(dao.has(sourceUrl)) { "has() 应为 true" }
            require(dao.getBookSourcePart(sourceUrl) != null) { "视图 book_sources_part 查询不到" }
            dao.enable(sourceUrl, false)
            require(dao.getBookSource(sourceUrl)?.enabled == false) { "enable(false) 未生效" }
            dao.delete(sourceUrl)
            require(!dao.has(sourceUrl)) { "delete 后仍存在" }
        }

        // ---------- T1.2 规则类 ----------
        check("ReplaceRuleDao: insert/find/update/delete + IN(:list)") {
            val dao = appDb.replaceRuleDao
            val rule = ReplaceRule(name = "冒烟替换", pattern = "a", replacement = "b")
            dao.insert(rule)
            require(dao.findById(rule.id) != null) { "findById 查询不到" }
            require(dao.findByIds(rule.id, rule.id + 1).isNotEmpty()) { "findByIds IN 展开失败" }
            dao.update(rule.copy(pattern = "x"))
            require(dao.findById(rule.id)?.pattern == "x") { "update 未生效" }
            dao.delete(rule)
            require(dao.findById(rule.id) == null) { "delete 后仍存在" }
        }

        check("TxtTocRuleDao: insert/get/update/delete") {
            val dao = appDb.txtTocRuleDao
            val rule = TxtTocRule(name = "冒烟toc", rule = ".*")
            dao.insert(rule)
            require(dao.get(rule.id) != null) { "get 查询不到" }
            dao.update(rule.copy(replacement = "x"))
            require(dao.get(rule.id)?.replacement == "x") { "update 未生效" }
            dao.delete(rule)
            require(dao.get(rule.id) == null) { "delete 后仍存在" }
        }

        check("RuleSubDao: insert/findByUrl/update/delete") {
            val dao = appDb.ruleSubDao
            val sub = RuleSub(name = "冒烟子规则", url = "https://smoke.test/sub")
            dao.insert(sub)
            require(dao.findByUrl(sub.url) != null) { "findByUrl 查询不到" }
            dao.update(sub.copy(name = "改名"))
            require(dao.findByUrl(sub.url)?.name == "改名") { "update 未生效" }
            dao.delete(sub)
            require(dao.findByUrl(sub.url) == null) { "delete 后仍存在" }
        }

        check("DictRuleDao: insert/getByName/update/delete") {
            val dao = appDb.dictRuleDao
            val rule = DictRule(name = "冒烟词典", urlRule = "https://smoke.test/dict")
            dao.insert(rule)
            require(dao.getByName(rule.name) != null) { "getByName 查询不到" }
            dao.update(rule.copy(showRule = "x"))
            require(dao.getByName(rule.name)?.showRule == "x") { "update 未生效" }
            dao.delete(rule)
            require(dao.getByName(rule.name) == null) { "delete 后仍存在" }
        }

        check("HighlightRuleDao: insert/findById/update/delete") {
            val dao = appDb.highlightRuleDao
            val rule = HighlightRule(name = "冒烟高亮", pattern = "x")
            dao.insert(rule)
            require(dao.findById(rule.id) != null) { "findById 查询不到" }
            dao.update(rule.copy(style = "bold"))
            require(dao.findById(rule.id)?.style == "bold") { "update 未生效" }
            dao.delete(rule)
            require(dao.findById(rule.id) == null) { "delete 后仍存在" }
        }

        // ---------- T1.3 书籍类 ----------
        check("BookGroupDao: insert/getByID/update/delete") {
            val dao = appDb.bookGroupDao
            val group = BookGroup(groupId = 1L shl 20, groupName = "冒烟分组")
            dao.insert(group)
            require(dao.getByID(group.groupId) != null) { "getByID 查询不到" }
            dao.update(group.copy(groupName = "改名"))
            require(dao.getByID(group.groupId)?.groupName == "改名") { "update 未生效" }
            dao.delete(group)
            require(dao.getByID(group.groupId) == null) { "delete 后仍存在" }
        }

        check("BookmarkDao: insert/search/delete + collate localized") {
            val dao = appDb.bookmarkDao
            val bm = Bookmark(bookName = "冒烟书", bookAuthor = "冒烟作者", content = "笔记")
            dao.insert(bm)
            require(dao.getByBook("冒烟书", "冒烟作者").isNotEmpty()) { "getByBook 查询不到" }
            require(dao.search("冒烟书", "冒烟作者", "笔记").isNotEmpty()) { "search 查询不到" }
            require(dao.all.isNotEmpty()) { "all(collate localized) 查询失败" }
            dao.update(bm.copy(chapterName = "第一章"))
            require(dao.getByBook("冒烟书", "冒烟作者").first().chapterName == "第一章") { "update 未生效" }
            dao.delete(bm)
            require(dao.getByBook("冒烟书", "冒烟作者").isEmpty()) { "delete 后仍存在" }
        }

        check("SearchBookDao: insert/get/update/delete(外键依赖 book_source)") {
            val dao = appDb.searchBookDao
            appDb.bookSourceDao.insert(
                BookSource(bookSourceUrl = sourceUrl, bookSourceName = "冒烟源", bookSourceType = 0)
            )
            val sb = SearchBook(bookUrl = "https://smoke.test/sb-${now}", origin = sourceUrl, name = "搜索书", author = "作者")
            dao.insert(sb)
            require(dao.getSearchBook(sb.bookUrl) != null) { "getSearchBook 查询不到" }
            dao.update(sb.copy(name = "搜索书2"))
            require(dao.getSearchBook(sb.bookUrl)?.name == "搜索书2") { "update 未生效" }
            dao.clear("搜索书2", sb.author)
            require(dao.getSearchBook(sb.bookUrl) == null) { "clear 未删除" }
            dao.insert(sb)
            dao.delete(sb)
            require(dao.getSearchBook(sb.bookUrl) == null) { "delete 后仍存在" }
            appDb.bookSourceDao.delete(sourceUrl)
        }

        check("ReadRecordDao: insertRaw/get/update/delete + allShow") {
            val dao = appDb.readRecordDao
            val rec = ReadRecord(deviceId = "dev-1", bookName = "冒烟书", author = "作者", readTime = 60)
            dao.insertRaw(rec)
            require(dao.getRecord("dev-1", "冒烟书") != null) { "getRecord 查询不到" }
            require(dao.getAuthor("dev-1", "冒烟书") != null) { "getAuthor 查询不到" }
            dao.update(rec.copy(readTime = 120))
            require(dao.getRecord("dev-1", "冒烟书")?.readTime == 120L) { "update 未生效" }
            require(dao.allShow.isNotEmpty()) { "allShow(collate localized) 查询失败" }
            dao.delete(rec)
            require(dao.getRecord("dev-1", "冒烟书") == null) { "delete 后仍存在" }
        }

        check("CacheDao: insert/get/update/delete + deleteSourceVariables") {
            val dao = appDb.cacheDao
            val cache = Cache(key = "smoke-key", value = "v1", deadline = 0)
            dao.insert(cache)
            require(dao.get("smoke-key") != null) { "get 查询不到" }
            require(dao.get("smoke-key", now) == "v1") { "get(key, now) 查询不到" }
            dao.insert(Cache(key = "sourceVariable_$sourceUrl", value = "{}"))
            dao.deleteSourceVariables(sourceUrl)
            require(dao.get("sourceVariable_$sourceUrl") == null) { "deleteSourceVariables 未生效" }
            dao.delete("smoke-key")
            require(dao.get("smoke-key") == null) { "delete 后仍存在" }
        }

        check("CookieDao: insert/get/update/delete") {
            val dao = appDb.cookieDao
            val cookie = Cookie(url = "https://smoke.test", cookie = "a=1")
            dao.insert(cookie)
            require(dao.get(cookie.url)?.cookie == "a=1") { "get 查询不到" }
            dao.update(cookie.copy(cookie = "a=2"))
            require(dao.get(cookie.url)?.cookie == "a=2") { "update 未生效" }
            dao.delete(cookie.url)
            require(dao.get(cookie.url) == null) { "delete 后仍存在" }
        }

        check("BookHighlightDao: insert/getByBook/update/delete + collate localized") {
            val dao = appDb.bookHighlightDao
            val hl = BookHighlight(bookUrl = bookUrl, bookName = "冒烟书", bookAuthor = "作者", bookText = "划线")
            dao.insert(hl)
            require(dao.getByBook(bookUrl).isNotEmpty()) { "getByBook 查询不到" }
            require(dao.all.isNotEmpty()) { "all(collate localized) 查询失败" }
            dao.pinLayoutTitleLength(bookUrl, hl.chapterUrl, 10)
            dao.bindChapterUrl(listOf(hl.time), "https://smoke.test/chapter")
            require(dao.getByBook(bookUrl).first().chapterUrl == "https://smoke.test/chapter") { "bindChapterUrl 未生效" }
            dao.update(hl.copy(note = "注"))
            require(dao.getByBook(bookUrl).first().note == "注") { "update 未生效" }
            dao.delete(hl)
            require(dao.getByBook(bookUrl).isEmpty()) { "delete 后仍存在" }
        }

        // ---------- T1.4 RSS 类 ----------
        check("RssSourceDao: insert/getByKey/update/delete + IN(:list)") {
            val dao = appDb.rssSourceDao
            val src = RssSource(sourceUrl = sourceUrl, sourceName = "冒烟RSS源")
            dao.insert(src)
            require(dao.getByKey(sourceUrl) != null) { "getByKey 查询不到" }
            require(dao.has(sourceUrl)) { "has() 应为 true" }
            require(dao.getRssSources(sourceUrl, "https://x").isNotEmpty()) { "getRssSources IN 展开失败" }
            dao.enable(sourceUrl, false)
            require(dao.getByKey(sourceUrl)?.enabled == false) { "enable(false) 未生效" }
            dao.update(src.copy(sourceName = "改名"))
            require(dao.getByKey(sourceUrl)?.sourceName == "改名") { "update 未生效" }
            dao.delete(sourceUrl)
            require(!dao.has(sourceUrl)) { "delete 后仍存在" }
        }

        check("RssArticleDao: insert/getByLink/update/delete + flowByOriginSort(read join)") {
            val dao = appDb.rssArticleDao
            val art = RssArticle(origin = sourceUrl, sort = "默认", title = "文章", link = "https://smoke.test/a1")
            dao.insert(art)
            require(dao.getByLink(sourceUrl, art.link) != null) { "getByLink 查询不到" }
            dao.update(art.copy(title = "文章2"))
            require(dao.getByLink(sourceUrl, art.link)?.title == "文章2") { "update 未生效" }
            runBlocking {
                val items = dao.flowByOriginSort(sourceUrl, "默认").toList().flatten()
                require(items.isNotEmpty()) { "flowByOriginSort(read 左连接) 查询不到" }
                require(items.first().link == art.link) { "flowByOriginSort 结果错误" }
            }
            dao.delete(sourceUrl)
            require(dao.getByLink(sourceUrl, art.link) == null) { "delete 后仍存在" }
        }

        check("RssReadRecordDao: insertRecord/get/update/delete") {
            val dao = appDb.rssReadRecordDao
            val rec = RssReadRecord(record = "rec-${now}", title = "记录", origin = sourceUrl)
            dao.insertRecord(rec)
            require(dao.getRecord(rec.record, sourceUrl) != null) { "getRecord 查询不到" }
            dao.update(rec.copy(durPos = 5))
            require(dao.getRecord(rec.record, sourceUrl)?.durPos == 5) { "update 未生效" }
            require(dao.countRecordsByOrigin(sourceUrl) >= 1) { "countRecordsByOrigin 异常" }
            dao.deleteRecordsByOrigin(sourceUrl)
            require(dao.getRecord(rec.record, sourceUrl) == null) { "delete 后仍存在" }
        }

        check("RssStarDao: insert/get/update/delete") {
            val dao = appDb.rssStarDao
            val star = RssStar(origin = sourceUrl, title = "收藏", link = "https://smoke.test/s1", starTime = now)
            dao.insert(star)
            require(dao.get(sourceUrl, star.link) != null) { "get 查询不到" }
            dao.update(star.copy(title = "收藏2"))
            require(dao.get(sourceUrl, star.link)?.title == "收藏2") { "update 未生效" }
            dao.delete(sourceUrl, star.link)
            require(dao.get(sourceUrl, star.link) == null) { "delete 后仍存在" }
        }

        check("SearchKeywordDao: insert/get/update/delete") {
            val dao = appDb.searchKeywordDao
            val kw = SearchKeyword(word = "冒烟词", usage = 1)
            dao.insert(kw)
            require(dao.get(kw.word) != null) { "get 查询不到" }
            dao.update(kw.copy(usage = 2))
            require(dao.get(kw.word)?.usage == 2) { "update 未生效" }
            dao.delete(kw)
            require(dao.get(kw.word) == null) { "delete 后仍存在" }
        }

        // ---------- T1.5 其他 ----------
        check("ServerDao: insert/get/update/delete") {
            val dao = appDb.serverDao
            val srv = Server(name = "冒烟服务器")
            dao.insert(srv)
            require(dao.get(srv.id) != null) { "get 查询不到" }
            dao.update(srv.copy(name = "改名"))
            require(dao.get(srv.id)?.name == "改名") { "update 未生效" }
            dao.delete(srv.id)
            require(dao.get(srv.id) == null) { "delete 后仍存在" }
        }

        check("HttpTTSDao: insert/get/update/delete") {
            val dao = appDb.httpTTSDao
            val tts = HttpTTS(name = "冒烟TTS", url = "https://smoke.test/tts")
            dao.insert(tts)
            require(dao.get(tts.id) != null) { "get 查询不到" }
            require(dao.getName(tts.id) == "冒烟TTS") { "getName 查询不到" }
            dao.update(tts.copy(name = "改名"))
            require(dao.get(tts.id)?.name == "改名") { "update 未生效" }
            dao.delete(tts)
            require(dao.get(tts.id) == null) { "delete 后仍存在" }
        }

        check("AutoTaskRuleDao: upsert/getById/update/delete + IN(:list)") {
            val dao = appDb.autoTaskRuleDao
            val rule = AutoTaskRule(name = "冒烟任务")
            dao.upsert(rule)
            require(dao.getById(rule.id) != null) { "getById 查询不到" }
            dao.update(rule.copy(cron = "*/1 * * * *"))
            require(dao.getById(rule.id)?.cron == "*/1 * * * *") { "update 未生效" }
            dao.updateEnabled(listOf(rule.id), false)
            require(dao.getById(rule.id)?.enable == false) { "updateEnabled 未生效" }
            dao.updateRunState(rule.id, now, "ok", null, "log")
            require(dao.getById(rule.id)?.lastResult == "ok") { "updateRunState 未生效" }
            dao.deleteByIds(listOf(rule.id))
            require(dao.getById(rule.id) == null) { "delete 后仍存在" }
        }

        check("KeyboardAssistsDao: insert/getByType/update/delete") {
            val dao = appDb.keyboardAssistsDao
            val ka = KeyboardAssist(type = 1, key = "k-${now}", value = "v1")
            dao.insert(ka)
            require(dao.getByType(1).isNotEmpty()) { "getByType 查询不到" }
            dao.update(ka.copy(value = "v2"))
            require(dao.getByType(1).first { it.key == ka.key }.value == "v2") { "update 未生效" }
            dao.delete(ka)
            require(dao.getByType(1).none { it.key == ka.key }) { "delete 后仍存在" }
        }

        // ---------- flow 覆盖（各 DAO 至少一个 flow 方法收集成功） ----------
        check("Flow 查询覆盖: flowAll 系列不抛异常") {
            runBlocking {
                appDb.bookSourceDao.flowAll().toList()
                appDb.replaceRuleDao.flowAll().toList()
                appDb.txtTocRuleDao.observeAll().toList()
                appDb.ruleSubDao.flowAll().toList()
                appDb.dictRuleDao.flowAll().toList()
                appDb.highlightRuleDao.flowAll().toList()
                appDb.bookGroupDao.flowAll().toList()
                appDb.bookmarkDao.flowAll().toList()
                appDb.bookHighlightDao.flowByBook(bookUrl).toList()
                appDb.readRecordDao.flowBooks().toList()
                appDb.rssSourceDao.flowAll().toList()
                appDb.rssStarDao.liveAll().toList()
                appDb.searchKeywordDao.flowByUsage().toList()
                appDb.serverDao.observeAll().toList()
                appDb.httpTTSDao.flowAll().toList()
                appDb.autoTaskRuleDao.flowAll().toList()
                appDb.keyboardAssistsDao.flowAll.toList()
            }
        }

        return fail
    }
}
