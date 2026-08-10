package io.legado.desktop.data.dao.impl

import io.legado.desktop.data.SqlExecutor.execute
import io.legado.desktop.data.SqlExecutor.queryList
import io.legado.desktop.data.SqlExecutor.queryOne
import io.legado.desktop.data.SqliteDatabase
import io.legado.desktop.data.dao.RssStarDao
import io.legado.desktop.data.entities.RssStar
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** RssStarDao SQLite 实现（SQL 对照 Legado Room @Query） */
class RssStarDaoImpl : RssStarDao {

    private val db get() = SqliteDatabase.get()
    private val lock get() = SqliteDatabase.dbLock

    private fun <T> withLock(block: () -> T): T = synchronized(lock, block)

    override val all: List<RssStar> get() = withLock {
        db.queryList("select * from rssStars order by starTime desc", emptyList(), RssStar::class.java)
    }

    override fun flowGroups(): Flow<List<String>> = flow {
        emit(withLock { db.queryList("select `group` from rssStars group by `group` order by `group`", emptyList(), String::class.java) })
    }

    override fun flowByGroup(group: String): Flow<List<RssStar>> = flow {
        emit(withLock {
            db.queryList("select * from rssStars where `group` = ? order by starTime desc", listOf(group), RssStar::class.java)
        })
    }

    override fun get(origin: String, link: String): RssStar? = withLock {
        db.queryOne("select * from rssStars where origin = ? and link = ?", listOf(origin, link), RssStar::class.java)
    }

    override fun liveAll(): Flow<List<RssStar>> = flow {
        emit(withLock { db.queryList("select * from rssStars order by starTime desc", emptyList(), RssStar::class.java) })
    }

    override fun insert(vararg rssStar: RssStar) {
        withLock {
            db.execute(
                "INSERT OR REPLACE INTO rssStars (origin, sort, title, starTime, link, pubDate, " +
                    "description, content, image, `group`, variable, type, durPos) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                rssStar.flatMap { s ->
                    listOf(
                        s.origin, s.sort, s.title, s.starTime, s.link, s.pubDate, s.description,
                        s.content, s.image, s.group, s.variable, s.type, s.durPos
                    )
                }
            )
        }
    }

    override fun update(vararg rssStar: RssStar) {
        withLock {
            db.execute(
                "UPDATE rssStars SET sort=?, title=?, starTime=?, pubDate=?, description=?, content=?, " +
                    "image=?, `group`=?, variable=?, type=?, durPos=? WHERE origin=? AND link=?",
                rssStar.flatMap { s ->
                    listOf(
                        s.sort, s.title, s.starTime, s.pubDate, s.description, s.content,
                        s.image, s.group, s.variable, s.type, s.durPos, s.origin, s.link
                    )
                }
            )
        }
    }

    override fun updateOrigin(origin: String, oldOrigin: String) {
        withLock {
            db.execute("update rssStars set origin = ? where origin = ?", listOf(origin, oldOrigin))
        }
    }

    override fun delete(origin: String) {
        withLock {
            db.execute("delete from rssStars where origin = ?", listOf(origin))
        }
    }

    override fun delete(origin: String, link: String) {
        withLock {
            db.execute("delete from rssStars where origin = ? and link = ?", listOf(origin, link))
        }
    }

    override fun deleteByGroup(group: String) {
        withLock {
            db.execute("delete from rssStars where `group` = ?", listOf(group))
        }
    }

    override fun deleteAll() {
        withLock {
            db.execute("delete from rssStars", emptyList())
        }
    }
}
