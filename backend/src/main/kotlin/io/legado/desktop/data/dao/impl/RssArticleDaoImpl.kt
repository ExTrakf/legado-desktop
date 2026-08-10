package io.legado.desktop.data.dao.impl

import io.legado.desktop.data.SqlExecutor.execute
import io.legado.desktop.data.SqlExecutor.queryList
import io.legado.desktop.data.SqlExecutor.queryOne
import io.legado.desktop.data.SqliteDatabase
import io.legado.desktop.data.dao.RssArticleDao
import io.legado.desktop.data.entities.RssArticle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** RssArticleDao SQLite 实现（SQL 对照 Legado Room @Query） */
class RssArticleDaoImpl : RssArticleDao {

    private val db get() = SqliteDatabase.get()
    private val lock get() = SqliteDatabase.dbLock

    private fun <T> withLock(block: () -> T): T = synchronized(lock, block)

    override fun get(origin: String, link: String, sort: String): RssArticle? = withLock {
        db.queryOne("select * from rssArticles where origin = ? and link = ? and sort = ?", listOf(origin, link, sort), RssArticle::class.java)
    }

    override fun getByLink(origin: String, link: String): RssArticle? = withLock {
        db.queryOne("select * from rssArticles where origin = ? and link = ?", listOf(origin, link), RssArticle::class.java)
    }

    override fun flowByOriginSort(origin: String, sort: String): Flow<List<RssArticle>> = flow {
        val sql = "select t1.link, t1.sort, t1.origin, t1.`order`, t1.title, t1.content, " +
            "t1.description, t1.image, t1.`group`, t1.pubDate, t1.variable, t1.type, t1.durPos, " +
            "ifNull(t2.read, 0) as read " +
            "from rssArticles as t1 left join rssReadRecords as t2 on t1.link = t2.record " +
            "where t1.origin = ? and t1.sort = ? order by `order` desc"
        emit(withLock { db.queryList(sql, listOf(origin, sort), RssArticle::class.java) })
    }

    override fun insert(vararg rssArticle: RssArticle) {
        withLock {
            db.execute(
                "INSERT OR REPLACE INTO rssArticles (origin, sort, title, `order`, link, pubDate, " +
                    "description, content, image, `group`, read, variable, type, durPos) " +
                    "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                rssArticle.flatMap { a ->
                    listOf(
                        a.origin, a.sort, a.title, a.order, a.link, a.pubDate, a.description,
                        a.content, a.image, a.group, a.read, a.variable, a.type, a.durPos
                    )
                }
            )
        }
    }

    override fun append(vararg rssArticle: RssArticle) {
        withLock {
            db.execute(
                "INSERT OR IGNORE INTO rssArticles (origin, sort, title, `order`, link, pubDate, " +
                    "description, content, image, `group`, read, variable, type, durPos) " +
                    "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                rssArticle.flatMap { a ->
                    listOf(
                        a.origin, a.sort, a.title, a.order, a.link, a.pubDate, a.description,
                        a.content, a.image, a.group, a.read, a.variable, a.type, a.durPos
                    )
                }
            )
        }
    }

    override fun clearOld(origin: String, sort: String, order: Long) {
        withLock {
            db.execute(
                "delete from rssArticles where origin = ? and sort = ? and `order` < ?",
                listOf(origin, sort, order)
            )
        }
    }

    override fun update(vararg rssArticle: RssArticle) {
        withLock {
            db.execute(
                "UPDATE rssArticles SET title=?, `order`=?, pubDate=?, description=?, content=?, image=?, " +
                    "`group`=?, read=?, variable=?, type=?, durPos=? WHERE origin=? AND link=? AND sort=?",
                rssArticle.flatMap { a ->
                    listOf(
                        a.title, a.order, a.pubDate, a.description, a.content, a.image, a.group,
                        a.read, a.variable, a.type, a.durPos, a.origin, a.link, a.sort
                    )
                }
            )
        }
    }

    override fun updateOrigin(origin: String, oldOrigin: String) {
        withLock {
            db.execute("update rssArticles set origin = ? where origin = ?", listOf(origin, oldOrigin))
        }
    }

    override fun delete(origin: String) {
        withLock {
            db.execute("delete from rssArticles where origin = ?", listOf(origin))
        }
    }
}
