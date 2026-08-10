package io.legado.desktop.data.dao.impl

import io.legado.desktop.data.SqlExecutor.execute
import io.legado.desktop.data.SqlExecutor.queryList
import io.legado.desktop.data.SqlExecutor.queryOne
import io.legado.desktop.data.SqlExecutor.queryValue
import io.legado.desktop.data.SqliteDatabase
import io.legado.desktop.data.dao.HttpTTSDao
import io.legado.desktop.data.entities.HttpTTS
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** HttpTTSDao SQLite 实现（SQL 对照 Legado Room @Query；仅数据存取，功能不移植） */
class HttpTTSDaoImpl : HttpTTSDao {

    private val db get() = SqliteDatabase.get()
    private val lock get() = SqliteDatabase.dbLock

    private fun <T> withLock(block: () -> T): T = synchronized(lock, block)

    override val all: List<HttpTTS> get() = withLock {
        db.queryList("select * from httpTTS order by name", emptyList(), HttpTTS::class.java)
    }

    override fun flowAll(): Flow<List<HttpTTS>> = flow {
        emit(withLock { db.queryList("select * from httpTTS order by name", emptyList(), HttpTTS::class.java) })
    }

    override val count: Int get() = withLock {
        db.queryValue("select count(*) from httpTTS", emptyList(), Int::class.java) ?: 0
    }

    override fun get(id: Long): HttpTTS? = withLock {
        db.queryOne("select * from httpTTS where id = ?", listOf(id), HttpTTS::class.java)
    }

    override fun getName(id: Long): String? = withLock {
        db.queryValue("select name from httpTTS where id = ?", listOf(id), String::class.java)
    }

    override fun insert(vararg httpTTS: HttpTTS) {
        withLock {
            db.execute(
                "INSERT OR REPLACE INTO httpTTS (id, name, url, contentType, pauseDuration, concurrentRate, " +
                    "loginUrl, loginUi, header, jsLib, enabledCookieJar, loginCheckJs, lastUpdateTime) " +
                    "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                httpTTS.flatMap { t ->
                    listOf(
                        t.id, t.name, t.url, t.contentType, t.pauseDuration, t.concurrentRate,
                        t.loginUrl, t.loginUi, t.header, t.jsLib, t.enabledCookieJar, t.loginCheckJs,
                        t.lastUpdateTime
                    )
                }
            )
        }
    }

    override fun delete(vararg httpTTS: HttpTTS) {
        withLock {
            db.execute("DELETE FROM httpTTS WHERE id = ?", httpTTS.map { it.id })
        }
    }

    override fun update(vararg httpTTS: HttpTTS) {
        withLock {
            db.execute(
                "UPDATE httpTTS SET name=?, url=?, contentType=?, pauseDuration=?, concurrentRate=?, " +
                    "loginUrl=?, loginUi=?, header=?, jsLib=?, enabledCookieJar=?, loginCheckJs=?, " +
                    "lastUpdateTime=? WHERE id=?",
                httpTTS.flatMap { t ->
                    listOf(
                        t.name, t.url, t.contentType, t.pauseDuration, t.concurrentRate,
                        t.loginUrl, t.loginUi, t.header, t.jsLib, t.enabledCookieJar, t.loginCheckJs,
                        t.lastUpdateTime, t.id
                    )
                }
            )
        }
    }

    override fun deleteDefault() {
        withLock {
            db.execute("delete from httpTTS where id < 0", emptyList())
        }
    }
}
