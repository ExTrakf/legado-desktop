package io.legado.desktop.data.dao.impl

import io.legado.desktop.data.SqlExecutor.execute
import io.legado.desktop.data.SqlExecutor.queryList
import io.legado.desktop.data.SqliteDatabase
import io.legado.desktop.data.dao.BookHighlightDao
import io.legado.desktop.data.entities.BookHighlight
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** BookHighlightDao SQLite 实现（SQL 对照 Legado Room @Query；collate localized 已注册到连接） */
class BookHighlightDaoImpl : BookHighlightDao {

    private val db get() = SqliteDatabase.get()
    private val lock get() = SqliteDatabase.dbLock

    private fun <T> withLock(block: () -> T): T = synchronized(lock, block)

    override val all: List<BookHighlight> get() = withLock {
        db.queryList(
            "select * from highlights order by bookName collate localized, " +
                "bookAuthor collate localized, chapterIndex, chapterPos, time",
            emptyList(), BookHighlight::class.java
        )
    }

    override fun getByBook(bookUrl: String): List<BookHighlight> = withLock {
        db.queryList(
            "select * from highlights where bookUrl = ? order by chapterIndex, chapterPos, time",
            listOf(bookUrl), BookHighlight::class.java
        )
    }

    override fun flowByBook(bookUrl: String): Flow<List<BookHighlight>> = flow {
        emit(withLock {
            db.queryList(
                "select * from highlights where bookUrl = ? order by chapterIndex, chapterPos, time",
                listOf(bookUrl), BookHighlight::class.java
            )
        })
    }

    override fun flowSearch(bookUrl: String, key: String): Flow<List<BookHighlight>> = flow {
        val sql = "select * from highlights where bookUrl = ? and (" +
            "chapterName like '%' || ? || '%' or bookText like '%' || ? || '%' or note like '%' || ? || '%'" +
            ") order by chapterIndex, chapterPos, time"
        emit(withLock { db.queryList(sql, listOf(bookUrl, key, key, key), BookHighlight::class.java) })
    }

    override fun insert(vararg highlight: BookHighlight) {
        withLock {
            db.execute(
                "INSERT OR REPLACE INTO highlights (time, bookUrl, chapterUrl, bookName, bookAuthor, " +
                    "chapterIndex, chapterPos, chapterPosEnd, layoutTitleLength, chapterName, bookText, " +
                    "style, note) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                highlight.flatMap { h ->
                    listOf(
                        h.time, h.bookUrl, h.chapterUrl, h.bookName, h.bookAuthor, h.chapterIndex,
                        h.chapterPos, h.chapterPosEnd, h.layoutTitleLength, h.chapterName, h.bookText,
                        h.style, h.note
                    )
                }
            )
        }
    }

    override fun pinLayoutTitleLength(bookUrl: String, chapterUrl: String, layoutTitleLength: Int) {
        withLock {
            db.execute(
                "update highlights set layoutTitleLength = ? where bookUrl = ? and chapterUrl = ? " +
                    "and layoutTitleLength < 0",
                listOf(layoutTitleLength, bookUrl, chapterUrl)
            )
        }
    }

    override fun bindChapterUrl(times: List<Long>, chapterUrl: String) {
        withLock {
            db.execute(
                "update highlights set chapterUrl = ? where time in (:times) and chapterUrl = ''",
                listOf(chapterUrl, times)
            )
        }
    }

    override fun updateBookMetadata(bookUrl: String, bookName: String, bookAuthor: String) {
        withLock {
            db.execute(
                "update highlights set bookName = ?, bookAuthor = ? where bookUrl = ?",
                listOf(bookName, bookAuthor, bookUrl)
            )
        }
    }

    override fun update(highlight: BookHighlight) {
        withLock {
            db.execute(
                "UPDATE highlights SET bookUrl=?, chapterUrl=?, bookName=?, bookAuthor=?, chapterIndex=?, " +
                    "chapterPos=?, chapterPosEnd=?, layoutTitleLength=?, chapterName=?, bookText=?, style=?, " +
                    "note=? WHERE time=?",
                listOf(
                    highlight.bookUrl, highlight.chapterUrl, highlight.bookName, highlight.bookAuthor,
                    highlight.chapterIndex, highlight.chapterPos, highlight.chapterPosEnd,
                    highlight.layoutTitleLength, highlight.chapterName, highlight.bookText,
                    highlight.style, highlight.note, highlight.time
                )
            )
        }
    }

    override fun delete(vararg highlight: BookHighlight) {
        withLock {
            db.execute("DELETE FROM highlights WHERE time = ?", highlight.map { it.time })
        }
    }
}
