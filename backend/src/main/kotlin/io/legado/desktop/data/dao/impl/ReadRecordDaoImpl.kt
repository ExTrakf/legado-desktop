package io.legado.desktop.data.dao.impl

import io.legado.desktop.data.SqlExecutor.execute
import io.legado.desktop.data.SqlExecutor.queryList
import io.legado.desktop.data.SqlExecutor.queryOne
import io.legado.desktop.data.SqlExecutor.queryValue
import io.legado.desktop.data.SqliteDatabase
import io.legado.desktop.data.dao.ReadRecordDao
import io.legado.desktop.data.entities.ReadRecord
import io.legado.desktop.data.entities.ReadRecordBook
import io.legado.desktop.data.entities.ReadRecordShow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** ReadRecordDao SQLite 实现（SQL 对照 Legado Room @Query；insert 默认实现已含作者合并，不重复实现） */
class ReadRecordDaoImpl : ReadRecordDao {

    private val db get() = SqliteDatabase.get()
    private val lock get() = SqliteDatabase.dbLock

    private fun <T> withLock(block: () -> T): T = synchronized(lock, block)

    override val all: List<ReadRecord> get() = withLock {
        db.queryList("select * from readRecord", emptyList(), ReadRecord::class.java)
    }

    override fun flowBooks(): Flow<List<ReadRecordBook>> = flow {
        val sql = "select distinct bookName, author from readRecord " +
            "order by bookName collate localized, author collate localized"
        emit(withLock { db.queryList(sql, emptyList(), ReadRecordBook::class.java) })
    }

    override val allShow: List<ReadRecordShow> get() = withLock {
        val sql = "select bookName, sum(readTime) as readTime, max(lastRead) as lastRead " +
            "from readRecord group by bookName order by bookName collate localized"
        db.queryList(sql, emptyList(), ReadRecordShow::class.java)
    }

    override val allTime: Long get() = withLock {
        db.queryValue("select sum(readTime) from readRecord", emptyList(), Long::class.java) ?: 0L
    }

    override fun search(searchKey: String): List<ReadRecordShow> = withLock {
        val sql = "select bookName, sum(readTime) as readTime, max(lastRead) as lastRead " +
            "from readRecord where bookName like '%' || ? || '%' group by bookName " +
            "order by bookName collate localized"
        db.queryList(sql, listOf(searchKey), ReadRecordShow::class.java)
    }

    override fun getReadTime(bookName: String): Long? = withLock {
        db.queryValue("select sum(readTime) from readRecord where bookName = ?", listOf(bookName), Long::class.java)
    }

    override fun getRecord(deviceId: String, bookName: String): ReadRecord? = withLock {
        db.queryOne("select * from readRecord where deviceId = ? and bookName = ?", listOf(deviceId, bookName), ReadRecord::class.java)
    }

    override fun getAuthor(deviceId: String, bookName: String): String? = withLock {
        db.queryValue("select author from readRecord where deviceId = ? and bookName = ?", listOf(deviceId, bookName), String::class.java)
    }

    override fun insertRaw(vararg readRecord: ReadRecord) {
        withLock {
            db.execute(
                "INSERT OR REPLACE INTO readRecord (deviceId, bookName, author, readTime, lastRead) " +
                    "VALUES (?,?,?,?,?)",
                readRecord.flatMap { r ->
                    listOf(r.deviceId, r.bookName, r.author, r.readTime, r.lastRead)
                }
            )
        }
    }

    override fun update(vararg record: ReadRecord) {
        withLock {
            db.execute(
                "UPDATE readRecord SET author=?, readTime=?, lastRead=? WHERE deviceId=? AND bookName=?",
                record.flatMap { r ->
                    listOf(r.author, r.readTime, r.lastRead, r.deviceId, r.bookName)
                }
            )
        }
    }

    override fun delete(vararg record: ReadRecord) {
        withLock {
            db.execute(
                "DELETE FROM readRecord WHERE deviceId = ? AND bookName = ?",
                record.flatMap { listOf(it.deviceId, it.bookName) }
            )
        }
    }

    override fun clear() {
        withLock {
            db.execute("delete from readRecord", emptyList())
        }
    }

    override fun deleteByName(bookName: String) {
        withLock {
            db.execute("delete from readRecord where bookName = ?", listOf(bookName))
        }
    }
}
