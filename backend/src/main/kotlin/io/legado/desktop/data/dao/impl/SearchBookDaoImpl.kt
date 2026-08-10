package io.legado.desktop.data.dao.impl

import io.legado.desktop.data.SqlExecutor.execute
import io.legado.desktop.data.SqlExecutor.queryList
import io.legado.desktop.data.SqlExecutor.queryOne
import io.legado.desktop.data.SqliteDatabase
import io.legado.desktop.data.dao.SearchBookDao
import io.legado.desktop.data.entities.SearchBook

/** SearchBookDao SQLite 实现（SQL 对照 Legado Room @Query） */
class SearchBookDaoImpl : SearchBookDao {

    private val db get() = SqliteDatabase.get()
    private val lock get() = SqliteDatabase.dbLock

    private fun <T> withLock(block: () -> T): T = synchronized(lock, block)

    override fun getSearchBook(bookUrl: String): SearchBook? = withLock {
        db.queryOne("select * from searchBooks where bookUrl = ?", listOf(bookUrl), SearchBook::class.java)
    }

    override fun getFirstByNameAuthor(name: String, author: String): SearchBook? = withLock {
        val sql = "select * from searchBooks where name = ? and author = ? " +
            "and origin in (select bookSourceUrl from book_sources) order by originOrder limit 1"
        db.queryOne(sql, listOf(name, author), SearchBook::class.java)
    }

    override fun changeSourceByGroup(name: String, author: String, sourceGroup: String): List<SearchBook> =
        withLock {
            db.queryList(
                changeSourceSelect() + " where t1.name = ? and t1.author like '%'||?||'%' " +
                    "and t2.enabled = 1 " + sourceGroupMembershipFilter() + " order by t2.customOrder",
                listOf(name, author, sourceGroup, sourceGroup), SearchBook::class.java
            )
        }

    override fun changeSourceSearch(
        name: String,
        author: String,
        key: String,
        sourceGroup: String,
    ): List<SearchBook> = withLock {
        db.queryList(
            changeSourceSelect() + " where t1.name = ? and t1.author like '%'||?||'%' " +
                sourceGroupMembershipFilter() +
                " and (originName like '%'||?||'%' or t1.latestChapterTitle like '%'||?||'%') " +
                "and t2.enabled = 1 order by t2.customOrder",
            listOf(name, author, sourceGroup, sourceGroup, key, key), SearchBook::class.java
        )
    }

    override fun getEnableHasCover(name: String, author: String): List<SearchBook> = withLock {
        db.queryList(
            changeSourceSelect() + " where t1.name = ? and t1.author = ? " +
                "and t1.coverUrl is not null and t1.coverUrl <> '' and t2.enabled = 1 " +
                "order by t2.customOrder",
            listOf(name, author), SearchBook::class.java
        )
    }

    override fun insert(vararg searchBook: SearchBook): List<Long> = withLock {
        db.execute(
            "INSERT OR REPLACE INTO searchBooks (" +
                "bookUrl, origin, originName, type, name, author, kind, coverUrl, intro, wordCount, " +
                "latestChapterTitle, tocUrl, time, variable, originOrder, chapterWordCountText, " +
                "chapterWordCount, respondTime" +
                ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            searchBook.flatMap { s ->
                listOf(
                    s.bookUrl, s.origin, s.originName, s.type, s.name, s.author, s.kind, s.coverUrl,
                    s.intro, s.wordCount, s.latestChapterTitle, s.tocUrl, s.time, s.variable,
                    s.originOrder, s.chapterWordCountText, s.chapterWordCount, s.respondTime
                )
            }
        )
        searchBook.map { 1L }
    }

    override fun clear(name: String, author: String) {
        withLock {
            db.execute("delete from searchBooks where name = ? and author = ?", listOf(name, author))
        }
    }

    override fun clearExpired(time: Long) {
        withLock {
            db.execute("delete from searchBooks where time < ?", listOf(time))
        }
    }

    override fun update(vararg searchBook: SearchBook) {
        withLock {
            db.execute(
                "UPDATE searchBooks SET origin=?, originName=?, type=?, name=?, author=?, kind=?, " +
                    "coverUrl=?, intro=?, wordCount=?, latestChapterTitle=?, tocUrl=?, time=?, variable=?, " +
                    "originOrder=?, chapterWordCountText=?, chapterWordCount=?, respondTime=? WHERE bookUrl=?",
                searchBook.flatMap { s ->
                    listOf(
                        s.origin, s.originName, s.type, s.name, s.author, s.kind, s.coverUrl, s.intro,
                        s.wordCount, s.latestChapterTitle, s.tocUrl, s.time, s.variable, s.originOrder,
                        s.chapterWordCountText, s.chapterWordCount, s.respondTime, s.bookUrl
                    )
                }
            )
        }
    }

    override fun delete(vararg searchBook: SearchBook) {
        withLock {
            db.execute("DELETE FROM searchBooks WHERE bookUrl = ?", searchBook.map { it.bookUrl })
        }
    }

    // ---- 内部 SQL 片段（对照 Legado SearchBookDao 常量） ----

    private fun changeSourceSelect(): String =
        "select t1.name, t1.author, t1.origin, t1.originName, t1.coverUrl, t1.bookUrl, " +
            "t1.type, t1.time, t1.intro, t1.kind, t1.latestChapterTitle, t1.tocUrl, t1.variable, " +
            "t1.wordCount, t2.customOrder as originOrder, t1.chapterWordCountText, t1.respondTime, " +
            "t1.chapterWordCount " +
            "from searchBooks as t1 inner join book_sources as t2 on t1.origin = t2.bookSourceUrl "

    private fun sourceGroupMembershipFilter(): String =
        "and (\n" +
            "    trim(:sourceGroup, $GROUP_TRIM_CHARACTERS) = ''\n" +
            "    or exists (\n" +
            "        with recursive source_groups(group_name, rest) as (\n" +
            "            select '',\n" +
            "                replace(replace(replace(coalesce(t2.bookSourceGroup, ''), ';', ','), '，', ','), '；', ',') || ','\n" +
            "            union all\n" +
            "            select\n" +
            "                trim(substr(rest, 1, instr(rest, ',') - 1), $GROUP_TRIM_CHARACTERS),\n" +
            "                substr(rest, instr(rest, ',') + 1)\n" +
            "            from source_groups\n" +
            "            where rest <> ''\n" +
            "        )\n" +
            "        select 1\n" +
            "        from source_groups\n" +
            "        where group_name = trim(:sourceGroup, $GROUP_TRIM_CHARACTERS)\n" +
            "    )\n" +
            ")"

    companion object {
        private const val GROUP_TRIM_CHARACTERS =
            "char(9,10,11,12,13,28,29,30,31,32,160,5760,8192,8193,8194,8195,8196," +
                "8197,8198,8199,8200,8201,8202,8232,8233,8239,8287,12288)"
    }
}
