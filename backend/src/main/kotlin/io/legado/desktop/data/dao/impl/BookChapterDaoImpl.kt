package io.legado.desktop.data.dao.impl

import io.legado.desktop.data.SqlExecutor.execute
import io.legado.desktop.data.SqlExecutor.queryList
import io.legado.desktop.data.SqlExecutor.queryOne
import io.legado.desktop.data.SqlExecutor.queryValue
import io.legado.desktop.data.SqliteDatabase
import io.legado.desktop.data.dao.BookChapterDao
import io.legado.desktop.data.entities.BookChapter

/** BookChapterDao SQLite 实现（对照 Legado Room @Query） */
class BookChapterDaoImpl : BookChapterDao {

    private val db get() = SqliteDatabase.get()
    private val lock get() = SqliteDatabase.dbLock

    private fun <T> withLock(block: () -> T): T = synchronized(lock, block)

    override fun search(bookUrl: String, key: String): List<BookChapter> = withLock {
        val sql = "SELECT * FROM chapters where bookUrl = ? and title like '%'||?||'%' order by `index`"
        db.queryList(sql, listOf(bookUrl, key), BookChapter::class.java)
    }

    override fun search(bookUrl: String, key: String, start: Int, end: Int): List<BookChapter> = withLock {
        val sql = "SELECT * FROM chapters where bookUrl = ? and `index` >= ? and `index` <= ? " +
            "and title like '%'||?||'%' order by `index`"
        db.queryList(sql, listOf(bookUrl, start, end, key), BookChapter::class.java)
    }

    override fun searchIndexes(bookUrl: String, key: String, start: Int, end: Int): List<Int> = withLock {
        val sql = "SELECT `index` FROM chapters where bookUrl = ? and `index` >= ? and `index` <= ? " +
            "and title like '%'||?||'%' order by `index`"
        db.queryList(sql, listOf(bookUrl, start, end, key), Int::class.java)
    }

    override fun getChapterList(bookUrl: String): List<BookChapter> = withLock {
        db.queryList("select * from chapters where bookUrl = ? order by `index`", listOf(bookUrl), BookChapter::class.java)
    }

    override fun getChapterList(bookUrl: String, start: Int, end: Int): List<BookChapter> = withLock {
        val sql = "select * from chapters where bookUrl = ? and `index` >= ? and `index` <= ? order by `index`"
        db.queryList(sql, listOf(bookUrl, start, end), BookChapter::class.java)
    }

    override fun getChapter(bookUrl: String, index: Int): BookChapter? = withLock {
        db.queryOne("select * from chapters where bookUrl = ? and `index` = ?", listOf(bookUrl, index), BookChapter::class.java)
    }

    override fun getChapter(bookUrl: String, title: String): BookChapter? = withLock {
        db.queryOne("select * from chapters where bookUrl = ? and `title` = ?", listOf(bookUrl, title), BookChapter::class.java)
    }

    override fun getChapterCount(bookUrl: String): Int = withLock {
        db.queryValue("select count(url) from chapters where bookUrl = ?", listOf(bookUrl), Int::class.java) ?: 0
    }

    override fun insert(vararg bookChapter: BookChapter) {
        withLock {
            db.execute(
            "INSERT OR REPLACE INTO chapters (" +
                "url, title, isVolume, baseUrl, bookUrl, `index`, isVip, isPay, resourceUrl, tag, " +
                "wordCount, `start`, `end`, startFragmentId, endFragmentId, variable, imgUrl" +
                ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            bookChapter.flatMap { c ->
                listOf(
                    c.url, c.title, c.isVolume, c.baseUrl, c.bookUrl, c.index, c.isVip, c.isPay,
                    c.resourceUrl, c.tag, c.wordCount, c.start, c.end, c.startFragmentId,
                    c.endFragmentId, c.variable, c.imgUrl
                )
            }
        )
        }
    }

    override fun update(vararg bookChapter: BookChapter) {
        withLock {
            db.execute(
            "UPDATE chapters SET title=?, isVolume=?, baseUrl=?, `index`=?, isVip=?, isPay=?, " +
                "resourceUrl=?, tag=?, wordCount=?, `start`=?, `end`=?, startFragmentId=?, " +
                "endFragmentId=?, variable=?, imgUrl=? WHERE url=? AND bookUrl=?",
            bookChapter.flatMap { c ->
                listOf(
                    c.title, c.isVolume, c.baseUrl, c.index, c.isVip, c.isPay, c.resourceUrl, c.tag,
                    c.wordCount, c.start, c.end, c.startFragmentId, c.endFragmentId, c.variable,
                    c.imgUrl, c.url, c.bookUrl
                )
            }
        )
        }
    }

    override fun updateContentMetadata(bookUrl: String, index: Int, title: String, imgUrl: String?) {
        withLock {
            db.execute(
            "update chapters set title = ?, imgUrl = ? where bookUrl = ? and `index` = ?",
            listOf(title, imgUrl, bookUrl, index)
        )
        }
    }

    override fun delByBook(bookUrl: String) {
        withLock {
            db.execute("delete from chapters where bookUrl = ?", listOf(bookUrl))
        }
    }

    override fun upWordCount(bookUrl: String, url: String, wordCount: String) {
        withLock {
            db.execute("update chapters set wordCount = ? where bookUrl = ? and url = ?", listOf(wordCount, bookUrl, url))
        }
    }
}
