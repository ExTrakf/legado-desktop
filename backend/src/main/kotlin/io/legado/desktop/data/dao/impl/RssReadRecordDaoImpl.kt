package io.legado.desktop.data.dao.impl

import io.legado.desktop.data.SqlExecutor.execute
import io.legado.desktop.data.SqlExecutor.queryList
import io.legado.desktop.data.SqlExecutor.queryOne
import io.legado.desktop.data.SqlExecutor.queryValue
import io.legado.desktop.data.SqliteDatabase
import io.legado.desktop.data.dao.RssReadRecordDao
import io.legado.desktop.data.entities.RssReadRecord

/** RssReadRecordDao SQLite 实现（SQL 对照 Legado Room @Query） */
class RssReadRecordDaoImpl : RssReadRecordDao {

    private val db get() = SqliteDatabase.get()
    private val lock get() = SqliteDatabase.dbLock

    private fun <T> withLock(block: () -> T): T = synchronized(lock, block)

    override fun insertRecord(vararg rssReadRecord: RssReadRecord) {
        withLock {
            db.execute(
                "INSERT OR IGNORE INTO rssReadRecords (record, title, readTime, read, origin, sort, " +
                    "image, type, durPos, pubDate) VALUES (?,?,?,?,?,?,?,?,?,?)",
                rssReadRecord.flatMap { r ->
                    listOf(
                        r.record, r.title, r.readTime, r.read, r.origin, r.sort,
                        r.image, r.type, r.durPos, r.pubDate
                    )
                }
            )
        }
    }

    override fun getRecords(): List<RssReadRecord> = withLock {
        db.queryList("select * from rssReadRecords order by readTime desc", emptyList(), RssReadRecord::class.java)
    }

    override fun getRecordsByOrigin(origin: String): List<RssReadRecord> = withLock {
        db.queryList("select * from rssReadRecords where origin = ? order by readTime desc", listOf(origin), RssReadRecord::class.java)
    }

    override fun getRecord(record: String, origin: String): RssReadRecord? = withLock {
        db.queryOne("select * from rssReadRecords where record = ? and origin = ?", listOf(record, origin), RssReadRecord::class.java)
    }

    override fun update(vararg rssRecord: RssReadRecord) {
        withLock {
            db.execute(
                "UPDATE rssReadRecords SET title=?, readTime=?, read=?, origin=?, sort=?, image=?, " +
                    "type=?, durPos=?, pubDate=? WHERE record=?",
                rssRecord.flatMap { r ->
                    listOf(
                        r.title, r.readTime, r.read, r.origin, r.sort, r.image,
                        r.type, r.durPos, r.pubDate, r.record
                    )
                }
            )
        }
    }

    override val countRecords: Int get() = withLock {
        db.queryValue("select count(1) from rssReadRecords", emptyList(), Int::class.java) ?: 0
    }

    override fun countRecordsByOrigin(origin: String): Int = withLock {
        db.queryValue("select count(1) from rssReadRecords where origin = ?", listOf(origin), Int::class.java) ?: 0
    }

    override fun deleteAllRecord() {
        withLock {
            db.execute("delete from rssReadRecords", emptyList())
        }
    }

    override fun deleteRecordsByOrigin(origin: String) {
        withLock {
            db.execute("delete from rssReadRecords where origin = ?", listOf(origin))
        }
    }
}
