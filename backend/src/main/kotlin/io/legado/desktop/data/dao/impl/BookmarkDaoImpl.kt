package io.legado.desktop.data.dao.impl

import io.legado.desktop.data.SqlExecutor.execute
import io.legado.desktop.data.SqlExecutor.queryList
import io.legado.desktop.data.SqliteDatabase
import io.legado.desktop.data.dao.BookmarkDao
import io.legado.desktop.data.entities.Bookmark
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** BookmarkDao SQLite 实现（SQL 对照 Legado Room @Query；collate localized 已注册到连接） */
class BookmarkDaoImpl : BookmarkDao {

    private val db get() = SqliteDatabase.get()
    private val lock get() = SqliteDatabase.dbLock

    private fun <T> withLock(block: () -> T): T = synchronized(lock, block)

    override val all: List<Bookmark> get() = withLock {
        db.queryList(allSql(), emptyList(), Bookmark::class.java)
    }

    override fun flowAll(): Flow<List<Bookmark>> = flow {
        emit(withLock { db.queryList(allSql(), emptyList(), Bookmark::class.java) })
    }

    override fun flowByBook(bookName: String, bookAuthor: String): Flow<List<Bookmark>> = flow {
        val sql = "select * from bookmarks where bookName = ? and bookAuthor = ? order by chapterIndex"
        emit(withLock { db.queryList(sql, listOf(bookName, bookAuthor), Bookmark::class.java) })
    }

    override fun flowSearch(bookName: String, bookAuthor: String, key: String): Flow<List<Bookmark>> = flow {
        emit(withLock { search(bookName, bookAuthor, key) })
    }

    override fun getByBook(bookName: String, bookAuthor: String): List<Bookmark> = withLock {
        db.queryList(
            "select * from bookmarks where bookName = ? and bookAuthor = ? order by chapterIndex",
            listOf(bookName, bookAuthor), Bookmark::class.java
        )
    }

    override fun search(bookName: String, bookAuthor: String, key: String): List<Bookmark> = withLock {
        val sql = "SELECT * FROM bookmarks where bookName = ? and bookAuthor = ? " +
            "and (chapterName like '%'||?||'%' or content like '%'||?||'%') order by chapterIndex"
        db.queryList(sql, listOf(bookName, bookAuthor, key, key), Bookmark::class.java)
    }

    override fun insert(vararg bookmark: Bookmark) {
        withLock {
            db.execute(
                "INSERT OR REPLACE INTO bookmarks (time, bookName, bookAuthor, chapterIndex, chapterPos, " +
                    "chapterName, bookText, content) VALUES (?,?,?,?,?,?,?,?)",
                bookmark.flatMap { b ->
                    listOf(
                        b.time, b.bookName, b.bookAuthor, b.chapterIndex, b.chapterPos,
                        b.chapterName, b.bookText, b.content
                    )
                }
            )
        }
    }

    override fun update(bookmark: Bookmark) {
        withLock {
            db.execute(
                "UPDATE bookmarks SET bookName=?, bookAuthor=?, chapterIndex=?, chapterPos=?, " +
                    "chapterName=?, bookText=?, content=? WHERE time=?",
                listOf(
                    bookmark.bookName, bookmark.bookAuthor, bookmark.chapterIndex, bookmark.chapterPos,
                    bookmark.chapterName, bookmark.bookText, bookmark.content, bookmark.time
                )
            )
        }
    }

    override fun delete(vararg bookmark: Bookmark) {
        withLock {
            db.execute("DELETE FROM bookmarks WHERE time = ?", bookmark.map { it.time })
        }
    }

    // ---- 内部 SQL ----

    private fun allSql(): String =
        "select * from bookmarks order by bookName collate localized, " +
            "bookAuthor collate localized, chapterIndex, chapterPos"
}
