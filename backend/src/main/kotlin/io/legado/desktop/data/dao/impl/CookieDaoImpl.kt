package io.legado.desktop.data.dao.impl

import io.legado.desktop.data.SqlExecutor.execute
import io.legado.desktop.data.SqlExecutor.queryList
import io.legado.desktop.data.SqlExecutor.queryOne
import io.legado.desktop.data.SqliteDatabase
import io.legado.desktop.data.dao.CookieDao
import io.legado.desktop.data.entities.Cookie

/** CookieDao SQLite 实现（SQL 对照 Legado Room @Query） */
class CookieDaoImpl : CookieDao {

    private val db get() = SqliteDatabase.get()
    private val lock get() = SqliteDatabase.dbLock

    private fun <T> withLock(block: () -> T): T = synchronized(lock, block)

    override fun get(url: String): Cookie? = withLock {
        db.queryOne("SELECT * FROM cookies Where url = ?", listOf(url), Cookie::class.java)
    }

    override fun getOkHttpCookies(): List<Cookie> = withLock {
        db.queryList("select * from cookies where url like '%|%'", emptyList(), Cookie::class.java)
    }

    override fun insert(vararg cookie: Cookie) {
        withLock {
            db.execute(
                "INSERT OR REPLACE INTO cookies (url, cookie) VALUES (?,?)",
                cookie.flatMap { listOf(it.url, it.cookie) }
            )
        }
    }

    override fun update(vararg cookie: Cookie) {
        withLock {
            db.execute(
                "UPDATE cookies SET cookie=? WHERE url=?",
                cookie.flatMap { listOf(it.cookie, it.url) }
            )
        }
    }

    override fun delete(url: String) {
        withLock {
            db.execute("delete from cookies where url = ?", listOf(url))
        }
    }

    override fun deleteOkHttp() {
        withLock {
            db.execute("delete from cookies where url like '%|%'", emptyList())
        }
    }
}
