package io.legado.desktop.data

import io.legado.desktop.data.dao.AutoTaskRuleDao
import io.legado.desktop.data.dao.BookChapterDao
import io.legado.desktop.data.dao.BookDao
import io.legado.desktop.data.dao.BookGroupDao
import io.legado.desktop.data.dao.BookHighlightDao
import io.legado.desktop.data.dao.BookSourceDao
import io.legado.desktop.data.dao.BookmarkDao
import io.legado.desktop.data.dao.CacheDao
import io.legado.desktop.data.dao.CookieDao
import io.legado.desktop.data.dao.DictRuleDao
import io.legado.desktop.data.dao.HighlightRuleDao
import io.legado.desktop.data.dao.HttpTTSDao
import io.legado.desktop.data.dao.KeyboardAssistsDao
import io.legado.desktop.data.dao.ReadRecordDao
import io.legado.desktop.data.dao.ReplaceRuleDao
import io.legado.desktop.data.dao.RssArticleDao
import io.legado.desktop.data.dao.RssReadRecordDao
import io.legado.desktop.data.dao.RssSourceDao
import io.legado.desktop.data.dao.RssStarDao
import io.legado.desktop.data.dao.RuleSubDao
import io.legado.desktop.data.dao.SearchBookDao
import io.legado.desktop.data.dao.SearchKeywordDao
import io.legado.desktop.data.dao.ServerDao
import io.legado.desktop.data.dao.TxtTocRuleDao
import io.legado.desktop.data.dao.impl.AutoTaskRuleDaoImpl
import io.legado.desktop.data.dao.impl.BookChapterDaoImpl
import io.legado.desktop.data.dao.impl.BookDaoImpl
import io.legado.desktop.data.dao.impl.BookGroupDaoImpl
import io.legado.desktop.data.dao.impl.BookHighlightDaoImpl
import io.legado.desktop.data.dao.impl.BookSourceDaoImpl
import io.legado.desktop.data.dao.impl.BookmarkDaoImpl
import io.legado.desktop.data.dao.impl.CacheDaoImpl
import io.legado.desktop.data.dao.impl.CookieDaoImpl
import io.legado.desktop.data.dao.impl.DictRuleDaoImpl
import io.legado.desktop.data.dao.impl.HighlightRuleDaoImpl
import io.legado.desktop.data.dao.impl.HttpTTSDaoImpl
import io.legado.desktop.data.dao.impl.KeyboardAssistsDaoImpl
import io.legado.desktop.data.dao.impl.ReadRecordDaoImpl
import io.legado.desktop.data.dao.impl.ReplaceRuleDaoImpl
import io.legado.desktop.data.dao.impl.RssArticleDaoImpl
import io.legado.desktop.data.dao.impl.RssReadRecordDaoImpl
import io.legado.desktop.data.dao.impl.RssSourceDaoImpl
import io.legado.desktop.data.dao.impl.RssStarDaoImpl
import io.legado.desktop.data.dao.impl.RuleSubDaoImpl
import io.legado.desktop.data.dao.impl.SearchBookDaoImpl
import io.legado.desktop.data.dao.impl.SearchKeywordDaoImpl
import io.legado.desktop.data.dao.impl.ServerDaoImpl
import io.legado.desktop.data.dao.impl.TxtTocRuleDaoImpl
import io.legado.desktop.env.DesktopEnv
import io.legado.desktop.help.DefaultData

/**
 * 数据库门面（对应原 Android 版 AppDatabase）。
 * 调用前必须先 init()；DAO 接口与 Legado 保持一致。
 */
object appDb {

    // ---- 24 个 DAO 全部实现（Part 1 完成） ----
    lateinit var bookDao: BookDao
    lateinit var bookChapterDao: BookChapterDao
    lateinit var bookSourceDao: BookSourceDao
    lateinit var bookGroupDao: BookGroupDao
    lateinit var bookHighlightDao: BookHighlightDao
    lateinit var bookmarkDao: BookmarkDao
    lateinit var cacheDao: CacheDao
    lateinit var cookieDao: CookieDao
    lateinit var dictRuleDao: DictRuleDao
    lateinit var highlightRuleDao: HighlightRuleDao
    lateinit var httpTTSDao: HttpTTSDao
    lateinit var keyboardAssistsDao: KeyboardAssistsDao
    lateinit var readRecordDao: ReadRecordDao
    lateinit var replaceRuleDao: ReplaceRuleDao
    lateinit var rssArticleDao: RssArticleDao
    lateinit var rssReadRecordDao: RssReadRecordDao
    lateinit var rssSourceDao: RssSourceDao
    lateinit var rssStarDao: RssStarDao
    lateinit var ruleSubDao: RuleSubDao
    lateinit var searchBookDao: SearchBookDao
    lateinit var searchKeywordDao: SearchKeywordDao
    lateinit var serverDao: ServerDao
    lateinit var txtTocRuleDao: TxtTocRuleDao
    lateinit var autoTaskRuleDao: AutoTaskRuleDao

    /** 事务执行（单连接串行化 + 提交/回滚，等价 Room @Transaction 语义） */
    fun runInTransaction(block: () -> Unit) {
        synchronized(SqliteDatabase.dbLock) {
            val conn = SqliteDatabase.get()
            val prev = conn.autoCommit
            conn.autoCommit = false
            try {
                block()
                conn.commit()
            } catch (e: Throwable) {
                runCatching { conn.rollback() }
                throw e
            } finally {
                conn.autoCommit = prev
            }
        }
    }

    /** 初始化数据库（幂等）。必须在 DesktopEnv.init() 之后调用。 */
    fun init() {
        SqliteDatabase.init(DesktopEnv.dbFile)
        bookDao = BookDaoImpl()
        bookChapterDao = BookChapterDaoImpl()
        bookSourceDao = BookSourceDaoImpl()
        bookGroupDao = BookGroupDaoImpl()
        bookHighlightDao = BookHighlightDaoImpl()
        bookmarkDao = BookmarkDaoImpl()
        cacheDao = CacheDaoImpl()
        cookieDao = CookieDaoImpl()
        dictRuleDao = DictRuleDaoImpl()
        highlightRuleDao = HighlightRuleDaoImpl()
        httpTTSDao = HttpTTSDaoImpl()
        keyboardAssistsDao = KeyboardAssistsDaoImpl()
        readRecordDao = ReadRecordDaoImpl()
        replaceRuleDao = ReplaceRuleDaoImpl()
        rssArticleDao = RssArticleDaoImpl()
        rssReadRecordDao = RssReadRecordDaoImpl()
        rssSourceDao = RssSourceDaoImpl()
        rssStarDao = RssStarDaoImpl()
        ruleSubDao = RuleSubDaoImpl()
        searchBookDao = SearchBookDaoImpl()
        searchKeywordDao = SearchKeywordDaoImpl()
        serverDao = ServerDaoImpl()
        txtTocRuleDao = TxtTocRuleDaoImpl()
        autoTaskRuleDao = AutoTaskRuleDaoImpl()

        // 首次建库：若 keyboardAssists 为空则 seed 默认值（对齐原版 AppDatabase onOpen）
        if (keyboardAssistsDao.all.isEmpty()) {
            keyboardAssistsDao.insert(*DefaultData.keyboardAssists.toTypedArray())
        }
    }
}
