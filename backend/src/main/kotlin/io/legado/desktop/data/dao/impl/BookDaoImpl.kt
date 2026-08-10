package io.legado.desktop.data.dao.impl

import io.legado.desktop.constant.BookType
import io.legado.desktop.data.SqlExecutor.execute
import io.legado.desktop.data.SqlExecutor.queryList
import io.legado.desktop.data.SqlExecutor.queryOne
import io.legado.desktop.data.SqlExecutor.queryValue
import io.legado.desktop.data.SqliteDatabase
import io.legado.desktop.data.dao.BookDao
import io.legado.desktop.data.entities.Book
import io.legado.desktop.data.entities.BookCacheCleanupSnapshot
import io.legado.desktop.data.entities.BookCacheInfo
import io.legado.desktop.data.entities.BookGroup
import io.legado.desktop.data.entities.BookSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * BookDao SQLite 实现（SQL 对照 Legado Room @Query 翻译）。
 * Flow 方法为一次性查询（桌面端拉取式 API，无需响应式通知）。
 */
class BookDaoImpl : BookDao {

    private val db get() = SqliteDatabase.get()
    private val lock get() = SqliteDatabase.dbLock

    private fun <T> withLock(block: () -> T): T = synchronized(lock, block)

    override fun flowRoot(): Flow<List<Book>> = flow {
        val sql = "select * from books where type & ${BookType.text} > 0 " +
            "and type & ${BookType.local} = 0 " +
            "and ((SELECT sum(groupId) FROM book_groups where groupId > 0) & `group`) = 0 " +
            "and (select show from book_groups where groupId = ${BookGroup.IdNetNone}) != 1"
        emit(withLock { db.queryList(sql, emptyList(), Book::class.java) })
    }

    override fun flowAll(): Flow<List<Book>> = flow {
        emit(withLock { db.queryList("SELECT * FROM books order by durChapterTime desc", emptyList(), Book::class.java) })
    }

    override fun flowAudio(): Flow<List<Book>> = flow {
        emit(withLock { db.queryList("SELECT * FROM books WHERE type & ${BookType.audio} > 0", emptyList(), Book::class.java) })
    }

    override fun flowVideo(): Flow<List<Book>> = flow {
        emit(withLock { db.queryList("SELECT * FROM books WHERE type & ${BookType.video} > 0", emptyList(), Book::class.java) })
    }

    override fun flowLocal(): Flow<List<Book>> = flow {
        emit(withLock { db.queryList("SELECT * FROM books WHERE type & ${BookType.local} > 0", emptyList(), Book::class.java) })
    }

    override fun flowNetNoGroup(): Flow<List<Book>> = flow {
        val sql = "select * from books where type & ${BookType.audio} = 0 " +
            "and type & ${BookType.local} = 0 and type & ${BookType.video} = 0 " +
            "and ((SELECT sum(groupId) FROM book_groups where groupId > 0) & `group`) = 0"
        emit(withLock { db.queryList(sql, emptyList(), Book::class.java) })
    }

    override fun flowLocalNoGroup(): Flow<List<Book>> = flow {
        val sql = "select * from books where type & ${BookType.local} > 0 " +
            "and ((SELECT sum(groupId) FROM book_groups where groupId > 0) & `group`) = 0"
        emit(withLock { db.queryList(sql, emptyList(), Book::class.java) })
    }

    override fun flowByUserGroup(group: Long): Flow<List<Book>> = flow {
        emit(withLock { db.queryList("SELECT * FROM books WHERE (`group` & ?) > 0", listOf(group), Book::class.java) })
    }

    override fun flowSearch(key: String): Flow<List<Book>> = flow {
        val sql = "SELECT * FROM books WHERE name like '%'||?||'%' or author like '%'||?||'%'"
        emit(withLock { db.queryList(sql, listOf(key, key), Book::class.java) })
    }

    override fun flowUpdateError(): Flow<List<Book>> = flow {
        emit(withLock {
            db.queryList("SELECT * FROM books where type & ${BookType.updateError} > 0 order by durChapterTime desc", emptyList(), Book::class.java)
        })
    }

    override fun getBooksByGroup(group: Long): List<Book> = withLock {
        db.queryList("SELECT * FROM books WHERE (`group` & ?) > 0", listOf(group), Book::class.java)
    }

    override fun findByName(vararg names: String): List<Book> = withLock {
        db.queryList("SELECT * FROM books WHERE `name` in (:names)", listOf(names.toList()), Book::class.java)
    }

    override fun getBookByFileName(fileName: String): Book? = withLock {
        db.queryOne("select * from books where originName = ?", listOf(fileName), Book::class.java)
    }

    override val localBookFileNames: List<String> get() = withLock {
        db.queryList("SELECT originName FROM books WHERE type & ${BookType.local} > 0", emptyList(), String::class.java)
    }

    override val localBookAlternateOrigins: List<String> get() = withLock {
        val sql = "SELECT origin FROM books WHERE type & ${BookType.local} > 0 " +
            "AND origin != '${BookType.localTag}'"
        db.queryList(sql, emptyList(), String::class.java)
    }

    override fun getBook(bookUrl: String): Book? = withLock {
        db.queryOne("SELECT * FROM books WHERE bookUrl = ?", listOf(bookUrl), Book::class.java)
    }

    override fun getBook(name: String, author: String): Book? = withLock {
        db.queryOne("SELECT * FROM books WHERE name = ? and author = ?", listOf(name, author), Book::class.java)
    }

    override fun getAllUseBookSource(): List<BookSource> = withLock {
        val sql = "select distinct bs.* from books, book_sources bs " +
            "where origin == bs.bookSourceUrl and origin not like '${BookType.localTag}'"
        db.queryList(sql, emptyList(), BookSource::class.java)
    }

    override fun getBookByOrigin(name: String, origin: String): Book? = withLock {
        db.queryOne("SELECT * FROM books WHERE name = ? and origin = ?", listOf(name, origin), Book::class.java)
    }

    override val noGroupSize: Int get() = withLock {
        db.queryValue("select count(bookUrl) from books where (SELECT sum(groupId) FROM book_groups)", emptyList(), Int::class.java) ?: 0
    }

    override val webBooks: List<Book> get() = withLock {
        db.queryList("SELECT * FROM books where type & ${BookType.local} = 0", emptyList(), Book::class.java)
    }

    override val hasUpdateBooks: List<Book> get() = withLock {
        db.queryList("SELECT * FROM books where type & ${BookType.local} = 0 and canUpdate = 1", emptyList(), Book::class.java)
    }

    override val all: List<Book> get() = withLock {
        db.queryList("SELECT * FROM books", emptyList(), Book::class.java)
    }

    override fun getCacheCleanupBooks(): List<BookCacheInfo> = withLock {
        db.queryList("SELECT bookUrl, name, origin, originName, type FROM books", emptyList(), BookCacheInfo::class.java)
    }

    override fun getCacheCleanupImageBooks(): List<Book> = withLock {
        db.queryList("SELECT * FROM books WHERE (type & ${BookType.image}) > 0", emptyList(), Book::class.java)
    }

    override fun getByTypeOnLine(type: Int): List<Book> = withLock {
        val sql = "SELECT * FROM books where type & ? > 0 and type & ${BookType.local} = 0"
        db.queryList(sql, listOf(type), Book::class.java)
    }

    override val lastReadBook: Book? get() = withLock {
        val sql = "SELECT * FROM books where type & ${BookType.text} > 0 ORDER BY durChapterTime DESC limit 1"
        db.queryOne(sql, emptyList(), Book::class.java)
    }

    override val lastReadBookOnShelf: Book? get() = withLock {
        val sql = "SELECT * FROM books where type & ${BookType.notShelf} = 0 " +
            "ORDER BY (durChapterIndex > 0 OR durChapterPos > 0) DESC, durChapterTime DESC limit 1"
        db.queryOne(sql, emptyList(), Book::class.java)
    }

    override val allBookUrls: List<String> get() = withLock {
        db.queryList("SELECT bookUrl FROM books", emptyList(), String::class.java)
    }

    override fun findExistingBookUrls(bookUrls: List<String>): List<String> = withLock {
        db.queryList("SELECT bookUrl FROM books WHERE bookUrl IN (:bookUrls)", listOf(bookUrls), String::class.java)
    }

    override val allBookCount: Int get() = withLock {
        db.queryValue("SELECT COUNT(*) FROM books", emptyList(), Int::class.java) ?: 0
    }

    override fun flowShelfBookCount(): Flow<Int> = flow {
        emit(withLock {
            db.queryValue("SELECT COUNT(*) FROM books where type & ${BookType.notShelf} = 0", emptyList(), Int::class.java) ?: 0
        })
    }

    override val readingCount: Int get() = withLock {
        val sql = "SELECT count(*) FROM books where (durChapterIndex > 0 OR durChapterPos > 0) " +
            "and type & ${BookType.notShelf} = 0"
        db.queryValue(sql, emptyList(), Int::class.java) ?: 0
    }

    override val minOrder: Int get() = withLock {
        db.queryValue("select min(`order`) from books", emptyList(), Int::class.java) ?: 0
    }

    override val maxOrder: Int get() = withLock {
        db.queryValue("select max(`order`) from books", emptyList(), Int::class.java) ?: 0
    }

    override fun has(bookUrl: String): Boolean = withLock {
        db.queryValue("select exists(select 1 from books where bookUrl = ?)", listOf(bookUrl), Int::class.java) == 1
    }

    override fun has(name: String, author: String): Boolean = withLock {
        db.queryValue("select exists(select 1 from books where name = ? and author = ?)", listOf(name, author), Int::class.java) == 1
    }

    override fun hasFile(fileName: String): Boolean = withLock {
        val sql = "select exists(select 1 from books where type & ${BookType.local} > 0 " +
            "and (originName = ? or (origin != '${BookType.localTag}' and originName = ?)))"
        db.queryValue(sql, listOf(fileName, fileName), Int::class.java) == 1
    }

    override fun insert(vararg book: Book) {
        withLock {
            db.execute(
                "INSERT OR REPLACE INTO books (" +
                    "bookUrl, tocUrl, origin, originName, name, author, kind, customTag, coverUrl, " +
                    "customCoverUrl, intro, customIntro, charset, type, `group`, latestChapterTitle, " +
                    "latestChapterTime, lastCheckTime, lastCheckCount, totalChapterNum, durChapterTitle, " +
                    "durChapterIndex, durVolumeIndex, chapterInVolumeIndex, durChapterPos, durChapterTime, " +
                    "wordCount, canUpdate, `order`, originOrder, variable, readConfig, syncTime" +
                    ") VALUES (" +
                    "?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,? )",
                book.flatMap { b ->
                    listOf(
                        b.bookUrl, b.tocUrl, b.origin, b.originName, b.name, b.author, b.kind, b.customTag,
                        b.coverUrl, b.customCoverUrl, b.intro, b.customIntro, b.charset, b.type, b.group,
                        b.latestChapterTitle, b.latestChapterTime, b.lastCheckTime, b.lastCheckCount,
                        b.totalChapterNum, b.durChapterTitle, b.durChapterIndex, b.durVolumeIndex,
                        b.chapterInVolumeIndex, b.durChapterPos, b.durChapterTime, b.wordCount, b.canUpdate,
                        b.order, b.originOrder, b.variable, b.readConfig, b.syncTime
                    )
                }
            )
        }
    }

    override fun insertIgnore(book: Book): Long = withLock {
        db.execute(
            "INSERT OR IGNORE INTO books (" +
                "bookUrl, tocUrl, origin, originName, name, author, kind, customTag, coverUrl, " +
                "customCoverUrl, intro, customIntro, charset, type, `group`, latestChapterTitle, " +
                "latestChapterTime, lastCheckTime, lastCheckCount, totalChapterNum, durChapterTitle, " +
                "durChapterIndex, durVolumeIndex, chapterInVolumeIndex, durChapterPos, durChapterTime, " +
                "wordCount, canUpdate, `order`, originOrder, variable, readConfig, syncTime" +
                ") VALUES (" +
                "?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,? )",
            listOf(
                book.bookUrl, book.tocUrl, book.origin, book.originName, book.name, book.author,
                book.kind, book.customTag, book.coverUrl, book.customCoverUrl, book.intro, book.customIntro,
                book.charset, book.type, book.group, book.latestChapterTitle, book.latestChapterTime,
                book.lastCheckTime, book.lastCheckCount, book.totalChapterNum, book.durChapterTitle,
                book.durChapterIndex, book.durVolumeIndex, book.chapterInVolumeIndex, book.durChapterPos,
                book.durChapterTime, book.wordCount, book.canUpdate, book.order, book.originOrder,
                book.variable, book.readConfig, book.syncTime
            )
        )
        1L
    }

    override fun update(vararg book: Book) {
        withLock {
            db.execute(
                "UPDATE books SET tocUrl=?, origin=?, originName=?, name=?, author=?, kind=?, customTag=?, " +
                    "coverUrl=?, customCoverUrl=?, intro=?, customIntro=?, charset=?, type=?, `group`=?, " +
                    "latestChapterTitle=?, latestChapterTime=?, lastCheckTime=?, lastCheckCount=?, " +
                    "totalChapterNum=?, durChapterTitle=?, durChapterIndex=?, durVolumeIndex=?, " +
                    "chapterInVolumeIndex=?, durChapterPos=?, durChapterTime=?, wordCount=?, canUpdate=?, " +
                    "`order`=?, originOrder=?, variable=?, readConfig=?, syncTime=? WHERE bookUrl=?",
                book.flatMap { b ->
                    listOf(
                        b.tocUrl, b.origin, b.originName, b.name, b.author, b.kind, b.customTag, b.coverUrl,
                        b.customCoverUrl, b.intro, b.customIntro, b.charset, b.type, b.group,
                        b.latestChapterTitle, b.latestChapterTime, b.lastCheckTime, b.lastCheckCount,
                        b.totalChapterNum, b.durChapterTitle, b.durChapterIndex, b.durVolumeIndex,
                        b.chapterInVolumeIndex, b.durChapterPos, b.durChapterTime, b.wordCount, b.canUpdate,
                        b.order, b.originOrder, b.variable, b.readConfig, b.syncTime, b.bookUrl
                    )
                }
            )
        }
    }

    override fun getReadConfigJson(bookUrl: String): String? = withLock {
        db.queryValue("select readConfig from books where bookUrl = ?", listOf(bookUrl), String::class.java)
    }

    override fun updateReadConfigJson(bookUrl: String, readConfig: String?) {
        withLock {
            db.execute("update books set readConfig = ? where bookUrl = ?", listOf(readConfig, bookUrl))
        }
    }

    override fun delete(vararg book: Book) {
        withLock {
            db.execute(
                "DELETE FROM books WHERE bookUrl = ?",
                book.map { it.bookUrl }
            )
        }
    }

    override fun upProgress(bookUrl: String, pos: Int) {
        withLock {
            db.execute("update books set durChapterPos = ? where bookUrl = ?", listOf(pos, bookUrl))
        }
    }

    override fun updateShelfState(bookUrl: String, type: Int, order: Int) {
        withLock {
            db.execute("update books set type = ?, `order` = ? where bookUrl = ?", listOf(type, order, bookUrl))
        }
    }

    override fun upGroup(oldGroupId: Long, newGroupId: Long) {
        withLock {
            db.execute("update books set `group` = ? where `group` = ?", listOf(newGroupId, oldGroupId))
        }
    }

    override fun removeGroup(group: Long) {
        withLock {
            db.execute("update books set `group` = `group` - ? where `group` & ? > 0", listOf(group, group))
        }
    }

    override fun deleteNotShelfBook() {
        withLock {
            db.execute("delete from books where type & ${BookType.notShelf} > 0", emptyList())
        }
    }

    // 接口默认实现（flowByGroup / getCacheCleanupSnapshot / updatePreservingReadConfig / updateAudioPlayMode / updateAudioPlaySpeed / replace）无需覆盖
}
